# 添加实体

以 `GardenBee`（原版模型，`generic/entity/GardenBee.java`）和 `LanternBerryCreeper`（GeckoLib 动画实体）为例。

## 一、两类实体

| 类型 | 基类 | 渲染器 | 模型加载 |
|---|---|---|---|
| 原版模型 | `Animal` / `Monster` / `PathfinderMob` | `MobRenderer` | **必须注册 `LayerDefinition`** |
| GeckoLib | 同左 + `GeoAnimatable` | `GeoEntityRenderer<T>` | GeckoLib 自动 |

**判断**：`.geo.json` → GeckoLib；`CubeListBuilder` 代码拼 → 原版模型。

原版模型最常踩的坑：忘注册 `LayerDefinition`，启动报 `No model for layer thrive:xxx#main`。

## 二、注册 EntityType

`register/EntityRegister.java`：

```java
@EventBusSubscriber
public class EntityRegister {

    static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ThriveMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<XxxEntity>> XXX;

    static {
        XXX = ENTITIES.register("xxx", () -> EntityType.Builder
                .of(XxxEntity::new, MobCategory.MONSTER)
                .sized(0.6f, 1.8f)
                .clientTrackingRange(10)
                .build("xxx"));
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
```

`MobCategory` 决定生成规则和全局上限：

| 类别 | 上限 | 例子 |
|---|---|---|
| `MONSTER` | ~70 | 僵尸、骷髅 |
| `CREATURE` | ~10 | 动物 |
| `AMBIENT` | ~15 | 蝙蝠 |
| `MISC` | 无 | 掉落物 |

## 三、注册属性

```java
@SubscribeEvent
public static void registerAttributes(EntityAttributeCreationEvent event) {
    event.put(XXX.get(), XxxEntity.createAttributes().build());
}
```

```java
public static AttributeSupplier.Builder createAttributes() {
    return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 30.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25);
}
```

常见属性默认值：`MAX_HEALTH` 20、`MOVEMENT_SPEED` 0.7、`ATTACK_DAMAGE` 2、`FOLLOW_RANGE` 16。

## 四、生成蛋、生成规则（可选）

```java
public static final DeferredHolder<Item, Item> XXX_SPAWN_EGG =
        ItemRegister.ITEM.register("xxx_spawn_egg", () -> new DeferredSpawnEggItem(
                XXX, 0x00FF00, 0xFF0000, new Item.Properties()));
```

```java
@EventBusSubscriber
public class EntitySpawnRegister {
    @SubscribeEvent
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(
                EntityRegister.XXX.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                XxxEntity::checkXxxSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
```

`SpawnPlacements.Type`：`ON_GROUND` / `IN_WATER` / `NO_RESTRICTIONS`（三选一，否则日志 warning）。

群系 spawner 数据在 `data/thrive/worldgen/biome/<biome>.json` 的 `spawners` 数组，不加就不自然生成。

## 五、原版模型实体 — GardenBee

**参考实现**：`generic/entity/GardenBee.java`、`client/model/entity/GardenBeeModel.java`、`client/render/entity/GardenBeeRenderer.java`。

它涵盖：飞行、驯服、攻击、跨端同步、自定义 AI Goal。复制原版 `BeeModel` 只改泛型即可。

要点速查：

| 能力 | 关键 |
|---|---|
| 飞行 | `FlyingMoveControl` + `FlyingPathNavigation` + `checkFallDamage` 空覆写 |
| 驯服 | `extends TamableAnimal`，`mobInteract` 里 `tame(player)` |
| 繁殖继承主人 | `getBreedOffspring` 里 `child.setOwnerUUID(...)` |
| 避免误伤主人 | `setTarget` 覆写，过滤 `getOwnerUUID()` |
| 协同作战 | `OwnerHurtByTargetGoal` + `OwnerHurtTargetGoal` |
| 采蜜/归巢 | 独立 `Goal`，优先级调度 |
| 跨端字段 | 走同步位，见第八章 |

## 六、模型类 + 渲染器 + 客户端注册

**模型类**（`client/model/entity/GardenBeeModel.java`）：

- 复制原版 `BeeModel`，把 `<T extends Bee>` 改成 `<T extends GardenBee>`
- 声明 `ModelLayerLocation LAYER_LOCATION`
- `createBodyLayer()` 返回 `LayerDefinition`
- `setupAnim` 调用实体暴露的方法（`getRollAmount` / `isAngry` / `hasPollen` 等）

**渲染器**（`client/render/entity/GardenBeeRenderer.java`）：

```java
public GardenBeeRenderer(EntityRendererProvider.Context context) {
    super(context, new GardenBeeModel<>(context.bakeLayer(GardenBeeModel.LAYER_LOCATION)), 0.4F);
}
```

**构造签名必须是 `(EntityRendererProvider.Context)`**——多参数版本无法当 `EntityRendererProvider` 用。

**客户端注册**（`client/register/EntityRenderRegister.java`）：

```java
@EventBusSubscriber(modid = ThriveMod.MOD_ID, value = Dist.CLIENT)
public class EntityRenderRegister {

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GardenBeeModel.LAYER_LOCATION, GardenBeeModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityRegister.GARDEN_BEE.get(), GardenBeeRenderer::new);
    }
}
```

两个事件都要。少 `RegisterLayerDefinitions` → `bakeLayer` 抛 `No model for layer`。

## 七、GeckoLib 集成

**实体**：`extends Monster implements GeoAnimatable`

```java
private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

@Override
public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    controllers.add(new AnimationController<>(this, "controller", 3, this::animate));
}

@Override public AnimatableInstanceCache getAnimatableInstanceCache() { return this.cache; }
@Override public double getTick(Object o) { return this.tickCount; }
```

**渲染器**：`extends GeoEntityRenderer<XxxEntity>`，构造里 `super(context, new XxxModel())`。

**模型**：`extends GeoModel<XxxEntity>`，三个路径：

```
assets/thrive/geo/xxx.geo.json
assets/thrive/textures/entity/xxx.png
assets/thrive/animations/xxx.animation.json
```

**注册**：只 `registerRenderers`，**不需要** `registerLayerDefinitions`。

## 八、跨端字段同步

普通 `public Item carriedPollen;` 只在服务端有值。客户端渲染器读它永远 `null`。

**解法**：改用同步位。

```java
private static final EntityDataAccessor<Boolean> DATA_HAS_POLLEN =
        SynchedEntityData.defineId(GardenBee.class, EntityDataSerializers.BOOLEAN);

@Override
protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(DATA_HAS_POLLEN, false);
}

public boolean hasPollen() {
    return this.entityData.get(DATA_HAS_POLLEN);
}
```

服务端写，双端读：

```java
if (!this.level().isClientSide) {
    this.entityData.set(DATA_HAS_POLLEN, this.carriedPollen != null);
}
```

渲染器读 `bee.hasPollen()`，不读 `bee.carriedPollen`。

**判据**：仅服务端 AI 内部用 → 不同步；客户端渲染/UI 要读 → 同步。同步字段只用 `Boolean` / `Integer` / `Byte`。

**同步 ≠ 持久化**：`SynchedEntityData` 只在实体存活期间传播，存档靠 `addAdditionalSaveData`。

## 九、NBT

```java
@Override
public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    if (favoriteFlower != null)
        tag.putString("FavoriteFlower", BuiltInRegistries.ITEM.getKey(favoriteFlower).toString());
    if (home != null)
        tag.putLong("Home", home.asLong());
}

@Override
public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    if (tag.contains("FavoriteFlower"))
        favoriteFlower = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("FavoriteFlower")));
    if (tag.contains("Home"))
        home = BlockPos.of(tag.getLong("Home"));
}
```

Item 走 `getKey(...).toString()` / `ResourceLocation.parse(...)`。BlockPos 走 `asLong()` / `BlockPos.of(long)`。

## 十、常见坑

| 坑 | 症状 |
|---|---|
| 属性没注册 | 0 血、瞬间死 |
| 原版模型没注册 LayerDefinition | `No model for layer thrive:xxx#main` |
| 跨端字段没同步 | 渲染器读不到值 |
| 渲染器构造签名带多余参数 | 无法当 `EntityRendererProvider` 用 |
| 飞行实体不覆写 `checkFallDamage` | 落地掉血 |
| 飞行游荡用 `moveControl` | 蜜蜂呆立/打转 |
| 宠物 `setTarget` 没过滤主人 | 主人打宠物 → 反击主人 |
| `getTick` 返回非 `this.tickCount` | 动画速率异常 |
| 生成蛋缺 `stacksTo(1)` | 和原版不一致 |
| GeckoLib 忘注册渲染器 | 实体不可见 |
| 动画名和 JSON key 不符 | 无动画 |

## 十一、清单

### 原版模型实体

```
register/EntityRegister.java                  EntityType + 属性
generic/entity/XxxEntity.java                 实体类
register/EntitySpawnRegister.java             生成规则（可选）
register/ItemRegister.java                    生成蛋（可选）
client/model/entity/XxxModel.java             模型类 + LAYER_LOCATION
client/render/entity/XxxRenderer.java         渲染器
client/register/EntityRenderRegister.java     两个事件
assets/thrive/textures/entity/xxx.png         材质
assets/thrive/lang/{en_us,zh_cn}.json         语言
```

### GeckoLib 实体

```
register/EntityRegister.java                  EntityType + 属性
generic/entity/XxxEntity.java                 实体类 + GeoAnimatable
client/model/entity/XxxModel.java             GeoModel
client/render/entity/XxxRenderer.java         GeoEntityRenderer
client/register/EntityRenderRegister.java     只 registerRenderers
assets/thrive/geo/xxx.geo.json                模型
assets/thrive/animations/xxx.animation.json   动画
assets/thrive/textures/entity/xxx.png         材质
assets/thrive/lang/{en_us,zh_cn}.json         语言
```

## 十一、更多案例

**飞行 + 驯服 + 攻击 + 中立生物 + 繁殖 + 自定义 Goal + 跨端同步 + NBT + 音效**：`generic/entity/GardenBee.java`。