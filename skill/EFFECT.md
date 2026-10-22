# 添加状态效果

## 一、注册

`register/EffectRegister.java`：

```java
public class EffectRegister {

    static final DeferredRegister<MobEffect> EFFECT =
            DeferredRegister.create(Registries.MOB_EFFECT, ThriveMod.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> ILLUMINANT =
            EFFECT.register("illuminant", Illuminant::new);
    public static final DeferredHolder<MobEffect, Appetite> APPETITE =
            EFFECT.register("appetite", Appetite::new);

    public static void register(IEventBus bus) {
        EFFECT.register(bus);
    }
}
```

直接静态赋值，不用 `static {}` 块。主类调 `EffectRegister.register(bus)`。

`DeferredHolder<MobEffect, Xxx>` 是注册表 holder：`get()` 拿到的是注册后的实例，`getId()`/`getKey()` 是注册名。判“实体是否带此效果”时，传的是 `DeferredHolder`（它实现了 `Holder<MobEffect>`），不是效果实例本身。

## 二、命名

不加 `Effect` 后缀，除非与其它类冲突。

| 效果 | 类名 |
|---|---|
| 光源 | `Illuminant` |
| 食欲 | `Appetite` |
| 炫目 | `Blinding` |
| 超级饥饿 | `SuperHunger` |

冲突时才加后缀，如 `LanternBerryJuiceEffect`。

## 三、效果类

`generic/effect/Xxx.java`：

```java
public class Xxx extends MobEffect {

    public Xxx() {
        super(MobEffectCategory.BENEFICIAL, 0xFFFFA500);
    }
}
```

### 3.1 构造器参数的含义

```java
protected MobEffect(MobEffectCategory category, int color)
protected MobEffect(MobEffectCategory category, int color, ParticleOptions particle)
```

| 参数 | 含义 | 作用 |
|---|---|---|
| `category` | `BENEFICIAL` / `HARMFUL` / `NEUTRAL` | 决定 `isBeneficial()` 返回值；影响 HUD 边框颜色、部分 AI/逻辑判断。**不是**决定图标颜色。 |
| `color` | `0xRRGGBB` | **粒子颜色**。默认构造器会用它生成 `ENTITY_EFFECT` 颜色粒子；环境效果（ambient）alpha 为 38，否则 255。**不影响** HUD 图标。 |
| `particle` | `ParticleOptions` | 可选的固定粒子。传入后不再根据 ambient/color 动态生成。 |

`MobEffectCategory` 只有三个值：`BENEFICIAL`、`HARMFUL`、`NEUTRAL`。`isBeneficial()` 仅在 `BENEFICIAL` 时为 true。

### 3.2 纯数据效果（无逻辑）

```java
public class Appetite extends MobEffect {
    public Appetite() {
        super(MobEffectCategory.BENEFICIAL, 0xFFFFA500);
    }
}
```

不重写任何回调 → `applyEffectTick` 永不执行，效果只作为“标记/属性修改载体/其他代码查询用”。适合“我要判断实体有没有这个效果，但效果本身不做任何事”的场景。

### 3.3 每 tick 逻辑：`shouldApplyEffectTickThisTick` 与 `applyEffectTick`

```java
@Override
public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return true;
}

@Override
public boolean applyEffectTick(LivingEntity entity, int amplifier) {
    // ...
    return true;
}
```

#### `shouldApplyEffectTickThisTick(int duration, int amplifier)` —— 调度门控

- **作用**：每 tick 被调用一次，决定本 tick 是否执行 `applyEffectTick`。默认 `return false`，不重写则 `applyEffectTick` **永不执行**。
- **`duration` 的含义**：**剩余时间**，每 tick 减 1。想“每 20 tick 触发”写 `duration % 20 == 0`。
- **`amplifier`**：效果倍率，`0` = I 级，`1` = II 级。可用于让高等级效果更频繁触发。

#### `applyEffectTick(LivingEntity entity, int amplifier)` —— 实际逻辑

- **作用**：仅当 `shouldApplyEffectTickThisTick` 返回 `true` 时，本 tick 被调用。默认 `return true`。
- **返回值是存活判定**：返回 `false` → **立即调用 `entity.removeEffect(effect)`**，

- 正常情况必须返回 `true`；只有确实想让效果提前结束时才返回 `false`。

#### 频率控制示例

| 需求 | `shouldApplyEffectTickThisTick` |
|---|---|
| 每 tick | `return true;` |
| 每 2 tick | `return duration % 2 == 0;` |
| 每 20 tick（约每秒） | `return duration % 20 == 0;` |
| 高等级更快 | `return duration % Math.max(1, 20 >> amplifier) == 0;` |
| 仅 II 级及以上生效 | `return amplifier >= 1 && duration % 20 == 0;` |
| 前 100 tick 不触发 | `return duration < 100 && duration % 20 == 0;` |

### 3.4 即时效果

瞬间治疗、瞬间伤害这类“只执行一次”的效果，应重写 `isInstantenous` 和`applyInstantenousEffect`：

```java
public class InstantHeal extends MobEffect {
    public InstantHeal() {
        super(MobEffectCategory.BENEFICIAL, 0xF82423);
    }

    @Override
    public boolean isInstantenous() {
        return true;  // 返回true，表明在使用效果时，不创建持续效果实例，直接调用一次applyInstantenousEffect
    }

    @Override
    public void applyInstantenousEffect(@Nullable Entity source, @Nullable Entity indirectSource,
                                        LivingEntity entity, int amplifier, double health) {
        // 只执行一次的逻辑
    }
}
```

- `MobEffect.applyInstantenousEffect` 默认会调用 `applyEffectTick`，所以重写`applyEffectTick`也行，但建议用applyInstantenousEffect。

## 四、效果生命周期回调（`MobEffect` 上可重写的方法）

这些是 `MobEffect` 提供、由 `MobEffectInstance` / `LivingEntity` 转发调用的回调。理解“何时触发、传什么参数”是写效果的关键。

### 4.1 `onEffectAdded(LivingEntity entity, int amplifier)`

- **触发时机**：效果**首次**被添加到实体时（实体原本没有同种效果）。
- **不触发**：实体已有同种效果，再次施加（即使等级更高、时长更长）。
- **amplifier**：本次添加实例的 amplifier。
- **典型用途**：初始化只属于这次效果周期的状态（自定义计时器、播放一次性音效）。
- **注意**：`MobEffectEvent.Added` 事件不等于它。

### 4.1.1 `withSoundOnAdded(SoundEvent sound)`
- **触发时机**：与onEffectAdded一致。
- **作用**：设置首次添加触发的音效。

### 4.2 `onEffectStarted(LivingEntity entity, int amplifier)`

- **触发时机**：**每次** `addEffect` 都调用，包括**首次添加**、刷新时长、提升等级、甚至新实例被 `update` 拒绝的情况。
- **amplifier**：**新传入实例**的 amplifier，不是当前生效实例的 amplifier。新实例被拒绝时，这里得到的也是新实例的 amplifier。
- **典型用途**：“每次施加效果时都要执行”的逻辑，如播放音效、提示。

### 4.3 `onEffectStarted` vs `onEffectAdded` 速查

| 场景 | `onEffectAdded` | `onEffectStarted` |
|---|---|---|
| 首次添加 | ✅ | ✅ |
| 同种效果刷新时长（update 返回 true） | ❌ | ✅ |
| 同种效果提升等级（update 返回 true） | ❌ | ✅ |
| 同种效果新实例被拒绝（update 返回 false） | ❌ | ✅ |
| `forceAddEffect` 首次 | ✅ | ❌（`forceAddEffect` 不调 `onEffectStarted`） |

### 4.4 `onMobRemoved(LivingEntity entity, int amplifier, Entity.RemovalReason reason)`

- **触发时机**：实体被移除（`KILLED` 或 `DISCARDED`）时，对每个生效效果调用一次。
- **不走** `MobEffectEvent.Remove`，直接清空 `activeEffects`。
- **典型用途**：清理效果添加时创建的外部状态。

### 4.5 `onMobHurt(LivingEntity entity, int amplifier, DamageSource source, float amount)`

- **触发时机**：实体受伤、伤害结算完成、血量已扣之后。
- **amount**：最终伤害值（0值可能是被盾牌格挡，而虚弱药水这样的伤害为0的情况，不会触发onMobHurt）。
- **典型用途**：反伤、受伤时触发效果、记录伤害。
- **注意**：这是**效果定义**上的回调；如果需要更早/更细粒度的伤害控制，用 `LivingIncomingDamageEvent` / `LivingDamageEvent.Pre/Post`。

### 4.6 `onEffectRemoved` 相关

`MobEffect` 本身没有 `onEffectRemoved` 回调；移除逻辑在 `LivingEntity.onEffectRemoved(instance)` 里，负责：

- `effectsDirty = true`
- 服务端：`effect.removeAttributeModifiers(attributes)` + `refreshDirtyAttributes()`
- 同步给乘客 `ClientboundRemoveMobEffectPacket`

若要在效果被移除时执行自定义逻辑，监听 `MobEffectEvent.Remove`（可取消）。

## 五、`MobEffectEvent` 事件一览

所有子类在 `NeoForge.EVENT_BUS` 上 post。

| 事件 | 触发时机 | 可取消 | 关键方法 |
|---|---|---|---|
| `MobEffectEvent.Applicable` | `addEffect` 开头，检查能否应用 | 否（用 `Result`） | `getEffectInstance()`、`getEffectSource()`、`getApplicationResult()`、`setResult(Result)` |
| `MobEffectEvent.Added` | **每次** `addEffect`，不论是否已有同种效果 | 否 | `getEffectInstance()`（新实例）、`getOldEffectInstance()`（可能 null）、`getEffectSource()` |
| `MobEffectEvent.Remove` | 显式 `removeEffect`、`applyEffectTick` 返回 false、cure 移除、`removeAllEffects` | 是 | `getEffect()`、`getCure()`（可能 null）、`getEffectInstance()`（可能 null） |
| `MobEffectEvent.Expired` | 持续时间降到 0 自然过期 | 是 | `getEffectInstance()` |

### 5.1 `Applicable` 的 `Result`

| `Result` | 含义 |
|---|---|
| `APPLY` | 强制应用，跳过 `canBeAffected` |
| `DEFAULT` | 用 `entity.canBeAffected(instance)` 的结果 |
| `DO_NOT_APPLY` | 强制不应用 |

`getApplicationResult()` 就是按上表算出的最终布尔值。

### 5.2 移除路径对照

| 移除原因 | 触发点 | 事件 | 可取消 |
|---|---|---|---|
| `applyEffectTick` 返回 `false` | `MobEffectInstance.tick()` 内 `entity.removeEffect` | `Remove` | 是 |
| 持续时间降到 0 | `LivingEntity.tickEffects()` 中 `!instance.tick(...)` | `Expired` | 是 |
| `removeEffect(...)` 显式调用 | `LivingEntity.removeEffect` | `Remove` | 是 |
| `removeAllEffects` / `removeEffectsCuredBy` | 对应方法 | `Remove`（cure 非 null） | 是 |
| 死亡/丢弃 | `triggerOnDeathMobEffects` | **不走事件**，直接 `activeEffects.clear()` | 否 |

## 六、同种效果的合并规则（`MobEffectInstance.update`）

同种 `MobEffect` 在实体身上永远只有一个“当前生效”的 `MobEffectInstance`。再次施加时，由 `update` 决定怎么合并：

- **新等级更高**：
  - 新时长更短 → 旧实例压入 `hiddenEffect` 链，当前替换为新等级/时长；高等级结束后旧实例恢复。
  - 新时长不更短 → 直接替换等级和时长，**旧实例直接丢弃**。
- **新等级相同**：新时长更长才刷新时长；不更长则不更新时长。
- **新等级更低**：新时长更长时放入 `hiddenEffect`（递归更新），当前等级不变。
- `ambient` / `visible` / `showIcon` 变化也可能让 `update` 返回 `true`，即使等级和时长都没变。

`isInfiniteDuration()` 为 true 时视为“无限长”，`isShorterDurationThan` 会正确处理。

## 七、属性修改器

### 7.1 `MobEffect.addAttributeModifier`

```java
addAttributeModifier(Holder<Attribute> attribute, ResourceLocation id,
                     double amount, AttributeModifier.Operation operation)
```

- 向 `attributeModifiers` Map 放入 `AttributeTemplate(id, amount, operation)`。
- **同一个 `Holder<Attribute>` 键会被覆盖**，所以一个效果对同一属性只能有一个修改器。
- `id` 必须**全局唯一**，用于去重/移除。建议格式 `thrive:effect.<效果名>.<属性>`。
- 返回 `this`，支持链式。

NeoForge 重载：

```java
addAttributeModifier(Holder<Attribute> attribute, ResourceLocation id,
                     AttributeModifier.Operation operation,
                     Int2DoubleFunction curve)
```

允许自定义“效果等级 → 修改值”的曲线。内部 `AttributeTemplate(id, curve.apply(0), operation, curve)`。

### 7.2 `AttributeTemplate.create(int level)`

- `curve != null`：`new AttributeModifier(id, curve.apply(level), operation)`，**不再乘 `(level + 1)`**。
- `curve == null`：`new AttributeModifier(id, amount * (level + 1), operation)`，默认线性。
- `level` 传入的是 amplifier，0 表示 I 级。

### 7.3 `AttributeInstance` 的 permanent vs transient

`LivingEntity.onEffectAdded` / `onEffectUpdated(forced=true)` 调用 `MobEffect.addAttributeModifiers`，内部用 `AttributeInstance.addPermanentModifier`：

| 方法 | 进 `permanentModifiers` | 随实体存档 | 用途 |
|---|---|---|---|
| `addPermanentModifier` | ✅ | ✅ | 效果属性修改器（当前用法） |
| `addTransientModifier` | ❌ | ❌ | 临时 modifier，如冲刺、细雪减速 |

因此效果属性修改器会随实体 NBT 保存；重进世界后由 `readAdditionalSaveData` 重新加载。

`AttributeInstance.getValue()` 计算顺序：base → 加 `ADD_VALUE` → 加 `ADD_MULTIPLIED_BASE`（基于 base）→ 乘 `ADD_MULTIPLIED_TOTAL` → `sanitizeValue`。

### 7.4 `blendDurationTicks`

`MobEffect.setBlendDuration(int)` 设置混合持续时间，`MobEffectInstance.BlendState` 用它做 HUD 渐隐/渐显：

- `blendDuration == 0` → factor 直接 1。
- 否则每 tick 向目标（剩余时间 > blendDuration ? 1 : 0）逼近，步长 `1/blendDuration`。

**纯客户端 HUD 效果**，与效果逻辑、服务端无关。

## 八、材质

每个效果需要一张 **16×16 PNG**：

```
assets/thrive/textures/mob_effect/<注册名>.png
```

**必须 16×16**，与原版一致。效果图标渲染时是把整张 sprite 画进 **16×16** 的格子（`AbstractContainerScreen` 里的 `guiGraphics.blit(x, y, 0, 16, 16, sprite)`），18×18 会被缩到 16×16，反而发糊。

想要原版物品的图标，直接从 `minecraft_1.21.1_client.jar`（`neoformruntime/artifacts/` 下）提取 `assets/minecraft/textures/item/<物品>.png` 复制过来即可，本身就是 16×16。

| 效果 | 文件 |
|---|---|
| 光源 | `assets/thrive/textures/mob_effect/illuminant.png`（原版灯笼） |
| 食欲 | `assets/thrive/textures/mob_effect/appetite.png` |
| 牛奶 | `assets/thrive/textures/mob_effect/milk.png`（原版奶桶） |

**缺失症状**：紫黑格（missing texture），不崩但难看。

**图标颜色由 PNG 自己决定**，和构造器里的颜色值无关——那个值是粒子色。

## 九、语言文件

`assets/thrive/lang/{en_us,zh_cn}.json`：

```json
"effect.thrive.illuminant": "光源",
"effect.thrive.appetite": "食欲"
```

键格式：`effect.<命名空间>.<注册名>`。

## 十、清单

| 文件 | 改动 |
|---|---|
| `register/EffectRegister.java` | `EFFECT.register("xxx", Xxx::new)` |
| `generic/effect/Xxx.java` | 继承 `MobEffect`，按需覆写 tick / 事件 / 属性修改器 |
| `assets/thrive/textures/mob_effect/xxx.png` | 16×16 图标 |
| `assets/thrive/lang/{en_us,zh_cn}.json` | `effect.thrive.xxx` |
| `ThriveMod.java` | `EffectRegister.register(bus)` |

## 十一、常见坑

| 坑 | 症状 |
|---|---|
| 忘了 `shouldApplyEffectTickThisTick` | `applyEffectTick` 永不执行 |
| `applyEffectTick` 返回 `false` | 立即 `removeEffect`，走 `Remove` 事件（可取消）；不是等下一 tick |
| 以为 `applyEffectTick` 返回 false 走 `Expired` | 实际走 `Remove`；`Expired` 只用于持续时间耗尽 |
| 在 `onEffectStarted` 里假设 amplifier 是当前生效等级 | 新实例被拒绝时仍用新实例 amplifier 触发 |
| 混淆 `onEffectAdded` 和 `onEffectStarted` | 刷新效果时预期执行的回调没触发，或只应执行一次的逻辑被重复执行 |
| 以为 `MobEffectEvent.Added` 只在首次 post | 每次 `addEffect` 都 post，监听时需自己判 `getOldEffectInstance() == null` |
| 属性修改器 ID 不唯一 | 属性加成被其它效果/系统覆盖，表现异常 |
| 用 `addTransientModifier` 加效果属性修改器 | 不存档，重进世界后消失；应用 `MobEffect.addAttributeModifier`（走 `addPermanentModifier`） |
| 即时效果未继承 `InstantenousMobEffect` | 逻辑在特定路径下不执行，或执行时机不符合预期 |
| 死亡时在 `MobEffectEvent.Remove` 里做清理 | 死亡/丢弃不走该事件，清理逻辑不执行；应重写 `onMobRemoved` |
| `@EventBusSubscriber` 挂在非注册类 | 事件不触发，静默 |
| 事件里 `entity.getEffect(...)` 判空漏写 | NPE |
| 语言键缺命名空间 | 显示原始 key |
| 材质非 16×16 | 缩放发糊（18×18 也会被缩） |
| 材质没放 | 紫黑格 |