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

直接静态赋值，不用 `static {}` 块。

主类调 `EffectRegister.register(bus)`。

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

`MobEffectCategory`：`BENEFICIAL` / `HARMFUL` / `NEUTRAL`。第二个参数是粒子颜色，`0xRRGGBB`。

### 3.1 纯数据效果（无逻辑）

```java
public class Appetite extends MobEffect {
    public Appetite() {
        super(MobEffectCategory.BENEFICIAL, 0xFFFFA500);
    }
}
```

### 3.2 每 tick 逻辑

```java
@Override
public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return true;                    // 每 tick
    // return duration % 20 == 0;   // 每秒
}

@Override
public boolean applyEffectTick(LivingEntity entity, int amplifier) {
    // ...
    return true;
}
```

### 3.3 事件监听

写在效果类里，`@EventBusSubscriber` + `@SubscribeEvent`：

```java
@EventBusSubscriber(modid = ThriveMod.MOD_ID)
public class LanternBerryJuiceEffect extends MobEffect {

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        // ...
    }
}
```

静态方法，不依赖实例。判"目标是否带此效果"用 `entity.getEffect(EffectRegister.XXX)`。

## 四、材质

每个效果需要一张 **16×16 PNG**：

```
assets/thrive/textures/mob_effect/<注册名>.png
```

**必须 16×16**，与原版一致。效果图标渲染时是把整张 sprite 画进 **16×16** 的格子
（`AbstractContainerScreen` 里的 `guiGraphics.blit(x, y, 0, 16, 16, sprite)`），
所以 18×18 会被缩到 16×16，反而发糊；其它尺寸同理。

想要原版物品的图标，直接从 `minecraft_1.21.1_client.jar`（`neoformruntime/artifacts/` 下）
提取 `assets/minecraft/textures/item/<物品>.png` 复制过来即可，本身就是 16×16，逐字节一致。

| 效果 | 文件 |
|---|---|
| 光源 | `assets/thrive/textures/mob_effect/illuminant.png`（原版灯笼） |
| 食欲 | `assets/thrive/textures/mob_effect/appetite.png` |
| 牛奶 | `assets/thrive/textures/mob_effect/milk.png`（原版奶桶） |

**缺失症状**：紫黑格（missing texture），不崩但难看。

图标颜色由 PNG 自己决定，和 `MobEffectCategory` 的颜色值无关——那个值是粒子色。

## 五、语言文件

`assets/thrive/lang/{en_us,zh_cn}.json`：

```json
"effect.thrive.illuminant": "光源",
"effect.thrive.appetite": "食欲"
```

键格式：`effect.<命名空间>.<注册名>`。

## 六、清单

| 文件 | 改动 |
|---|---|
| `register/EffectRegister.java` | `EFFECT.register("xxx", Xxx::new)` |
| `generic/effect/Xxx.java` | 继承 `MobEffect`，按需覆写 tick / 事件 |
| `assets/thrive/textures/mob_effect/xxx.png` | 18×18 图标 |
| `assets/thrive/lang/{en_us,zh_cn}.json` | `effect.thrive.xxx` |
| `ThriveMod.java` | `EffectRegister.register(bus)` |

## 七、常见坑

| 坑 | 症状 |
|---|---|
| 忘了 `shouldApplyEffectTickThisTick` | `applyEffectTick` 永不执行 |
| 用 `tickCount` 代替 `duration` | 判定不对 |
| `@EventBusSubscriber` 挂在非注册类 | 事件不触发，静默 |
| 事件里 `entity.getEffect(...)` 判空漏写 | NPE |
| 语言键缺命名空间 | 显示原始 key |
| 材质非 16×16 | 缩放发糊（18×18 也会被缩） |
| 材质没放 | 紫黑格 |