# 添加实体

以 `MossyZombie`（普通实体）和 `LanternBerryCreeper`（GeckoLib 动画实体）为例。
模型、材质、动画 JSON 由开发者提供，本文只讲代码。

## 一、两类实体

| 类型 | 基类 | 渲染器 |
|---|---|---|
| 普通（用原版模型） | 继承某个原版实体 | 继承原版渲染器，或自定义 |
| GeckoLib（geo 模型 + 动画） | 继承某个原版实体 + `implements GeoAnimatable` | 继承 `GeoEntityRenderer<T>` |

**判断**：实体用原版 Mob 模型（僵尸、骷髅、羊等）→ 普通。实体用 `.geo.json` 自定义模型 → GeckoLib。

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
    return Monster.createMonsterAttributes()      // 或 Mob.createMobAttributes()
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

**只在特定群系生成**：

```java
@EventBusSubscriber
public class EntitySpawnRegister {

    @SubscribeEvent
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(
                EntityRegister.XXX.get(),
                SpawnPlacements.Type.ON_GROUND,                    // 生成位置类型
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,         // 高度图
                XxxEntity::checkXxxSpawnRules,                     // 自定义条件
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

## 六、GeckoLib 集成

### 6.1 实体类

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

### 6.2 渲染器

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

### 6.3 注册渲染器

`client/register/EntityRenderRegister.java`：

```java
@EventBusSubscriber(modid = ThriveMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class EntityRenderRegister {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityRegister.XXX.get(), XxxRenderer::new);
    }
}
```

**`value = Dist.CLIENT` 必须有**——渲染器是纯客户端概念。

## 七、实体 NBT

不需要手动序列化——`Entity` 的 `saveWithoutId` / `load` 自动管基础字段。需要自定义字段时才 override：

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

## 八、常见坑

| 坑 | 症状 |
|---|---|
| 属性没注册 | 实体生成后 0 血、瞬间死 |
| `SpawnPlacements` 没注册 | 日志报 warning "spawn entry was found but not registered" |
| 未加进 biome spawner | 完全不自然生成 |
| GeckoLib 渲染器没注册 | 实体不可见 / 紫色方块 |
| `.geo.json` 路径错 | 模型变默认立方体 |
| 动画名和 JSON key 不符 | 无动画 / 报错 |
| `getTick` 返回 `this.tickCount` 之外 | 动画速率异常 |
| 生成蛋缺 `stacksTo(1)` | 不是致命，但和原版不一致 |

## 九、清单

一个实体完整实现，涉及文件：

```
register/EntityRegister.java                  EntityType 注册 + 属性注册
generic/entity/XxxEntity.java                 实体类
register/EntitySpawnRegister.java             生成规则（可选）
register/ItemRegister.java                    生成蛋（可选）
client/model/entity/XxxModel.java             GeckoLib 模型（GeckoLib 实体）
client/render/entity/XxxRenderer.java         渲染器
client/register/EntityRenderRegister.java     渲染器注册
assets/thrive/geo/xxx.geo.json                模型（你提供）
assets/thrive/animations/xxx.animation.json   动画（你提供）
assets/thrive/textures/entity/xxx.png         材质（你提供）
assets/thrive/lang/{en_us,zh_cn}.json         语言
```

**普通实体不需要 GeckoLib 三个文件**——模型走原版系统。