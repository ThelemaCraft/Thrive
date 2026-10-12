# 添加实体

以 `GardenBee`（原版模型，自定义模型类）和 `LanternBerryCreeper`（GeckoLib 动画实体）为例。
模型、材质、动画 JSON 由开发者提供，本文只讲代码。

## 一、两类实体

| 类型 | 基类 | 渲染器 | 模型加载 |
|---|---|---|---|
| 原版模型 | 继承 `Animal` / `Monster` / `PathfinderMob` 等 | 继承 `MobRenderer` | **必须手动注册 `LayerDefinition`** |
| GeckoLib | 同左 + `implements GeoAnimatable` | 继承 `GeoEntityRenderer<T>` | GeckoLib 自动加载，不用注册 |

**判断**：模型是 `.geo.json`（Blockbench GeckoLib 导出）→ GeckoLib。模型是代码里 `CubeListBuilder` 拼的（复制原版模型微调）→ 原版模型。

**原版模型实体最容易踩的坑**：忘了注册 `LayerDefinition`，游戏启动时报 `No model for layer thrive:xxx#main`。GeckoLib 没这个问题，因为它内部自己管模型。

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
                .of(XxxEntity::new, MobCategory.MONSTER)   // 或 MISC / AMBIENT / CREATURE
                .sized(0.6f, 1.8f)                          // 宽、高（碰撞箱）
                .clientTrackingRange(10)                    // 客户端追踪距离（区块），默认 10
                .fireImmune()                               // 可选
                .build("xxx"));
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
```

**`MobCategory` 决定生成规则和全局上限**：

| 类别 | 上限 | 例子 |
|---|---|---|
| `MONSTER` | ~70 | 僵尸、骷髅 |
| `CREATURE` | ~10 | 动物 |
| `AMBIENT` | ~15 | 蝙蝠 |
| `MISC` | 无 | 掉落物、TNT |

**`build("xxx")` 的字符串**建议和注册名一致，写错不报错但会混乱。

## 三、注册属性

`EntityRegister` 里加：

```java
@SubscribeEvent
public static void registerAttributes(EntityAttributeCreationEvent event) {
    event.put(XXX.get(), XxxEntity.createAttributes().build());
}
```

实体类里：

```java
public static AttributeSupplier.Builder createAttributes() {
    return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 30.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25);
}
```

**常见属性**：

| 属性 | 默认 |
|---|---|
| `MAX_HEALTH` | 20 |
| `MOVEMENT_SPEED` | 0.7（玩家 0.1 左右） |
| `ATTACK_DAMAGE` | 2 |
| `ARMOR` | 0 |
| `FOLLOW_RANGE` | 16 |

## 四、注册生成蛋（可选）

```java
public static final DeferredHolder<Item, Item> XXX_SPAWN_EGG =
        ItemRegister.ITEM.register("xxx_spawn_egg", () -> new DeferredSpawnEggItem(
                XXX, 0x00FF00, 0xFF0000, new Item.Properties()));
```

参数：实体注册项、底色、高光色（都是 RGB 整数）。

## 五、生成规则（可选）

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

`SpawnPlacements.Type`：

| 值 | 位置要求 |
|---|---|
| `ON_GROUND` | 脚下方块实心 |
| `IN_WATER` | 在水中 |
| `NO_RESTRICTIONS` | 不检查（**必填之一，否则日志报 warning**） |

**群系 spawner 数据**在 `data/thrive/worldgen/biome/<biome>.json` 的 `spawners` 数组里：

```json
"monster": [
  { "type": "thrive:xxx", "weight": 100, "minCount": 1, "maxCount": 4 }
]
```

**不加进 biome 的 spawner 里，实体不会自然生成。**

## 六、原版模型实体

以 `GardenBee extends Animal implements NeutralMob, FlyingAnimal` 为例。模型复制原版蜜蜂，只改泛型约束。

### 6.1 实体类骨架

`generic/entity/GardenBee.java`：

```java
public class GardenBee extends Animal implements NeutralMob, FlyingAnimal {

    public static final float FLAP_DEGREES_PER_TICK = 120.32113F;
    public static final int TICKS_PER_FLAP = Mth.ceil(1.4959966F);

    @Nullable public BlockPos home;          // 自定义字段

    public GardenBee(EntityType<? extends GardenBee> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.FLYING_SPEED, 0.6F)
                .add(Attributes.MOVEMENT_SPEED, 0.3F);
    }
}
```

### 6.2 模型类

`client/model/entity/GardenBeeModel.java`。**复制原版 `BeeModel`，把 `<T extends Bee>` 改成 `<T extends GardenBee>`**，几何体和动画逻辑 1:1 保留。

```java
@OnlyIn(Dist.CLIENT)
public class GardenBeeModel<T extends GardenBee> extends AgeableListModel<T> {

    /** 关键：声明 LayerLocation，注册和烘焙都用它。 */
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, "garden_bee"), "main");

    private final ModelPart bone;
    private final ModelPart rightWing;
    // ... 与 BeeModel 同名字段

    public GardenBeeModel(ModelPart root) {
        super(false, 24.0F, 0.0F);
        this.bone = root.getChild("bone");
        // ... 与 BeeModel 同
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // ... 与 BeeModel 同
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // ... 与 BeeModel 同
    }

    @Override protected Iterable<ModelPart> headParts() { return ImmutableList.of(); }
    @Override protected Iterable<ModelPart> bodyParts() { return ImmutableList.of(this.bone); }
}
```

**方法名对照**（原版 `BeeModel` 方法名 → NeoForge 1.21 映射）：

| 原版名 | 现名 |
|---|---|
| `prepareMobModel` | `prepareMobModel`（`AgeableListModel` 已定义） |
| `setupAnim` | `setupAnim` |

**需要暴露的实体字段**：模型会调 `entity.getRollAmount(partialTick)`、`entity.hasStung()`、`entity.isAngry()`、`entity.hasNectar()`、`entity.onGround()`。实体类必须提供这些方法或继承到。

### 6.3 渲染器

`client/render/entity/GardenBeeRenderer.java`：

```java
@OnlyIn(Dist.CLIENT)
public class GardenBeeRenderer extends MobRenderer<GardenBee, GardenBeeModel<GardenBee>> {

    private static final ResourceLocation BEE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/bee/bee.png");
    private static final ResourceLocation NECTAR_BEE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/bee/bee_nectar.png");
    private static final ResourceLocation ANGRY_BEE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/bee/bee_angry.png");
    private static final ResourceLocation ANGRY_NECTAR_BEE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/bee/bee_angry_nectar.png");

    public GardenBeeRenderer(EntityRendererProvider.Context context) {
        super(context, new GardenBeeModel<>(context.bakeLayer(GardenBeeModel.LAYER_LOCATION)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(GardenBee bee) {
        if (bee.isAngry()) {
            return bee.hasNectar() ? ANGRY_NECTAR_BEE_TEXTURE : ANGRY_BEE_TEXTURE;
        }
        return bee.hasNectar() ? NECTAR_BEE_TEXTURE : BEE_TEXTURE;
    }
}
```

**构造签名必须是 `(EntityRendererProvider.Context)`**——事件只传 Context，多参数版本无法当 `EntityRendererProvider` 用。

### 6.4 客户端注册（两个事件，缺一不可）

`client/register/EntityRenderRegister.java`：

```java
@EventBusSubscriber(modid = ThriveMod.MOD_ID, value = Dist.CLIENT)
public class EntityRenderRegister {

    /** ① 注册模型层定义。少了这一步 = bakeLayer 抛 No model for layer。 */
    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GardenBeeModel.LAYER_LOCATION, GardenBeeModel::createBodyLayer);
    }

    /** ② 注册渲染器。 */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityRegister.GARDEN_BEE.get(), GardenBeeRenderer::new);
    }
}
```

**两个事件必须在同一个类、同一个事件总线、同一个 `Dist.CLIENT` 范围内。** 顺序由 NeoForge 保证：`RegisterLayerDefinitions` 一定先于 `RegisterRenderers` 触发。分开两个类也能跑，但没必要。

### 6.5 为什么 GeckoLib 不用这一步

`GeoEntityRenderer` 内部自己加载 `.geo.json`，不走原版 `LayerDefinition` 机制，所以从 GeckoLib 切到原版模型时最容易忘这一步。

## 七、GeckoLib 集成

### 7.1 实体类

```java
public class XxxEntity extends Monster implements GeoAnimatable {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public XxxEntity(EntityType<? extends XxxEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 3, this::animate));
    }

    private <E extends GeoAnimatable> PlayState animate(AnimationState<E> event) {
        if (event.isMoving()) {
            event.getController().setAnimation(RawAnimation.begin().thenLoop("animation.xxx.walk"));
        } else {
            event.getController().setAnimation(RawAnimation.begin().thenLoop("animation.xxx.idle"));
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public double getTick(Object o) {
        return this.tickCount;
    }
}
```

**参数说明**：

| 位置 | 值 | 含义 |
|---|---|---|
| `AnimationController` 第二参 | `"controller"` | 控制器名，同一实体多个控制器时区分 |
| 第三参 | `3` | 过渡时长（tick） |
| 第四参 | `this::animate` | 状态回调 |

**`RawAnimation.begin().thenLoop("...")`** 里的字符串要和动画 JSON 里的 `animations` key 完全一致。

### 7.2 渲染器

`client/render/entity/XxxRenderer.java`：

```java
public class XxxRenderer extends GeoEntityRenderer<XxxEntity> {

    public XxxRenderer(EntityRendererProvider.Context context) {
        super(context, new XxxModel());
    }
}
```

`client/model/entity/XxxModel.java`：

```java
public class XxxModel extends GeoModel<XxxEntity> {

    @Override
    public ResourceLocation getModelResource(XxxEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("thrive", "geo/xxx.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(XxxEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("thrive", "textures/entity/xxx.png");
    }

    @Override
    public ResourceLocation getAnimationResource(XxxEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("thrive", "animations/xxx.animation.json");
    }
}
```

**三个路径是惯例**，你只要往对应目录放文件：

```
assets/thrive/geo/xxx.geo.json
assets/thrive/textures/entity/xxx.png
assets/thrive/animations/xxx.animation.json
```

### 7.3 注册渲染器

```java
@EventBusSubscriber(modid = ThriveMod.MOD_ID, value = Dist.CLIENT)
public class EntityRenderRegister {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityRegister.XXX.get(), XxxRenderer::new);
    }
}
```

**GeckoLib 实体不需要 `registerLayerDefinitions`。**

## 八、实体 NBT

`Entity` 的 `saveWithoutId` / `load` 自动管基础字段。需要自定义字段时才 override：

```java
@Override
protected void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putInt("MyField", myField);
}

@Override
protected void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    myField = tag.getInt("MyField");
}
```

## 九、常见坑

| 坑 | 症状 |
|---|---|
| 属性没注册 | 实体生成后 0 血、瞬间死 |
| `SpawnPlacements` 没注册 | 日志报 warning "spawn entry was found but not registered" |
| 未加进 biome spawner | 完全不自然生成 |
| **原版模型没注册 LayerDefinition** | **启动报 `No model for layer thrive:xxx#main`** |
| GeckoLib 渲染器没注册 | 实体不可见 / 紫色方块 |
| `.geo.json` 路径错 | 模型变默认立方体 |
| 动画名和 JSON key 不符 | 无动画 / 报错 |
| `getTick` 返回 `this.tickCount` 之外 | 动画速率异常 |
| 渲染器构造签名带多余参数 | 无法当 `EntityRendererProvider` 用，注册时编译错 |
| 生成蛋缺 `stacksTo(1)` | 不是致命，但和原版不一致 |

## 十、清单

### 原版模型实体

```
register/EntityRegister.java                  EntityType 注册 + 属性注册
generic/entity/XxxEntity.java                 实体类
register/EntitySpawnRegister.java             生成规则（可选）
register/ItemRegister.java                    生成蛋（可选）
client/model/entity/XxxModel.java             模型类 + LAYER_LOCATION + createBodyLayer
client/render/entity/XxxRenderer.java         渲染器
client/register/EntityRenderRegister.java     registerLayerDefinitions + registerRenderers
assets/thrive/textures/entity/xxx.png         材质
assets/thrive/lang/{en_us,zh_cn}.json         语言
```

### GeckoLib 实体

```
register/EntityRegister.java                  EntityType 注册 + 属性注册
generic/entity/XxxEntity.java                 实体类 + GeoAnimatable
register/EntitySpawnRegister.java             生成规则（可选）
register/ItemRegister.java                    生成蛋（可选）
client/model/entity/XxxModel.java             GeoModel
client/render/entity/XxxRenderer.java         GeoEntityRenderer
client/register/EntityRenderRegister.java     只 registerRenderers
assets/thrive/geo/xxx.geo.json                模型（你提供）
assets/thrive/animations/xxx.animation.json   动画（你提供）
assets/thrive/textures/entity/xxx.png         材质（你提供）
assets/thrive/lang/{en_us,zh_cn}.json         语言
```