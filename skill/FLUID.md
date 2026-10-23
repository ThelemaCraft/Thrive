# 添加流体

## 流体标签的废弃
- 在 NeoForge 1.21.1 中，**绝对不要给自定义流体加 `#minecraft:water` 等标签**，因为底层判定已被重定向为只认 `WATER_TYPE` 等流体类型，加标签不仅无效还会引发行为割裂，必须通过 Mixin 拦截特定逻辑。

## 一、五个注册对象

| 对象 | 注册表 | 命名 | DeferredRegister |
|---|---|---|---|
| `FluidType` | `NeoForgeRegistries.FLUID_TYPES` | `<name>_type` | `FluidRegister.FLUID_TYPES` |
| `Source` | `Registries.FLUID` | `<name>_still` | `FluidRegister.FLUIDS` |
| `Flowing` | `Registries.FLUID` | `<name>_flowing` | `FluidRegister.FLUIDS` |
| `LiquidBlock` | `Registries.BLOCK` | `<name>_block` | `BlockRegister.BLOCK` |
| `BucketItem` | `Registries.ITEM` | `<name>_bucket` | `ItemRegister.ITEM` |

**每个注册表全项目只有一个 `DeferredRegister`。** 流体块和桶分别往 `BlockRegister.BLOCK`、`ItemRegister.ITEM` 注册。

## 二、注册器骨架

`register/FluidRegister.java`：

```java
@EventBusSubscriber(modid = ThriveMod.MOD_ID)
public class FluidRegister {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, ThriveMod.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, ThriveMod.MOD_ID);

    // ─── 流体：xxx ───

    public static final DeferredHolder<FluidType, FluidType> XXX_TYPE =
            FLUID_TYPES.register("xxx_type", () -> new FluidType(
                    FluidType.Properties.create()
                            .density(2000)
                            .temperature(350)
                            .canConvertToSource(false)
                            .lightLevel(15)
                            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> XXX_STILL =
            FLUIDS.register("xxx_still", () -> new BaseFlowingFluid.Source(xxxProps()) {
                @Override public int getTickDelay(LevelReader level) { return 20; }
            });

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> XXX_FLOWING =
            FLUIDS.register("xxx_flowing", () -> new BaseFlowingFluid.Flowing(xxxProps()) {
                @Override public int getTickDelay(LevelReader level) { return 20; }
            });

    public static final DeferredHolder<Block, LiquidBlock> XXX_BLOCK =
            BlockRegister.BLOCK.register("xxx_block", () -> new LiquidBlock(
                    XXX_STILL.get(),
                    Block.Properties.ofFullCopy(Blocks.WATER).noLootTable()
            ) {
                @Override
                public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
                    super.entityInside(state, level, pos, entity);
                    if (entity.tickCount % 5 != 0 || !(entity instanceof LivingEntity living)) return;
                    living.addEffect(new MobEffectInstance(EffectRegister.XXX, 2400));
                }

                @Override
                public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
                    return state.getFluidState().getFluidType().getLightLevel();
                }
            });

    public static final DeferredHolder<Item, BucketItem> XXX_BUCKET =
            ItemRegister.ITEM.register("xxx_bucket", () -> new BucketItem(
                    XXX_STILL.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private static BaseFlowingFluid.Properties xxxProps() {
        return new BaseFlowingFluid.Properties(XXX_TYPE, XXX_STILL, XXX_FLOWING)
                .block(XXX_BLOCK)
                .bucket(XXX_BUCKET)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2);
    }

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
    }
}
```

## 三、客户端扩展

`client/register/ClientFluidRegister.java`：

```java
@EventBusSubscriber(modid = ThriveMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientFluidRegister {

    @SubscribeEvent
    public static void register(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return ResourceLocation.fromNamespaceAndPath("thrive", "block/xxx_still");
            }
            @Override
            public ResourceLocation getFlowingTexture() {
                return ResourceLocation.fromNamespaceAndPath("thrive", "block/xxx_flow");
            }
            @Override
            public int getTintColor() {
                return 0xFFFF33;
            }
            @Override
            public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance,
                                        float partialTick, float nearDistance, float farDistance, FogShape shape) {
                if (mode != FogRenderer.FogMode.FOG_TERRAIN) return;
                RenderSystem.setShaderFogColor(1.0F, 1.0F, 0.2F, 1.0F);
                RenderSystem.setShaderFogStart(0.5F);
                RenderSystem.setShaderFogEnd(6.0F);
            }
        }, FluidRegister.XXX_TYPE.get());
    }
}
```

**客户端扩展走 `RegisterClientExtensionsEvent`**——NeoForge 1.21 的标准方式，替换旧版 `FluidType.initializeClient`。

## 四、流体交互

`register/FluidInteractionRegister.java`：

```java
@EventBusSubscriber(modid = ThriveMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class FluidInteractionRegister {

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FluidInteractionRegistry.addInteraction(
                NeoForgeMod.LAVA_TYPE.value(),
                new FluidInteractionRegistry.InteractionInformation(
                    FluidRegister.XXX_TYPE.get(),
                    (fluidState) -> fluidState.isSource()
                        ? Blocks.OBSIDIAN.defaultBlockState()
                        : Blocks.COBBLESTONE.defaultBlockState()
                )
            );
        });
    }
}
```

**标准做法**：

- 注册在 **`LAVA_TYPE`** 上，跟原版"岩浆遇水"一致
- 谓词判断"相邻是哪个 Type"，结果改的是**岩浆自己的位置**
- 用 `enqueueWork` 包住（`INTERACTIONS` 是非线程安全 HashMap）

**不写对水的 interaction，流体与水自然不反应。**

## 五、资源文件

### 5.1 清单

| 文件 | 路径 |
|---|---|
| 方块状态 | `assets/thrive/blockstates/xxx_block.json` |
| 方块模型 | `assets/thrive/models/block/xxx.json` |
| 静态纹理 | `assets/thrive/textures/block/xxx_still.png` |
| 流动纹理 | `assets/thrive/textures/block/xxx_flow.png` |
| 桶模型 | `assets/thrive/models/item/xxx_bucket.json` |
| 桶纹理 | `assets/thrive/textures/item/xxx_bucket.png` |
| 语言 | `assets/thrive/lang/{en_us,zh_cn}.json` |

### 5.2 方块状态 — `assets/thrive/blockstates/xxx_block.json`

```json
{
  "variants": {
    "": {
      "model": "thrive:block/xxx"
    }
  }
}
```

**空键 `""`**：流体外观由 `LiquidBlockRenderer` 按 `FluidState` 渲染，不走 `level` 变体。原版水方块状态（`assets/minecraft/blockstates/water.json`）就是这个写法。

### 5.3 方块模型 — `assets/thrive/models/block/xxx.json`

```json
{
  "textures": {
    "particle": "thrive:block/xxx_still"
  }
}
```

此模型**只负责粒子**（走路、撞碎时飞出的碎块），流体**实际外观**由 `FluidType` 客户端纹理渲染。

### 5.4 桶模型 — `assets/thrive/models/item/xxx_bucket.json`

```json
{
  "parent": "minecraft:item/generated",
  "textures": { "layer0": "thrive:item/xxx_bucket" }
}
```

### 5.5 语言

`en_us.json`：

```json
"fluid_type.thrive.xxx_type": "Xxx",
"block.thrive.xxx_block": "Xxx",
"item.thrive.xxx_bucket": "Xxx Bucket"
```

`zh_cn.json`：

```json
"fluid_type.thrive.xxx_type": "Xxx",
"block.thrive.xxx_block": "Xxx",
"item.thrive.xxx_bucket": "Xxx 桶"
```

`fluid_type.thrive.xxx_type` 是**流体方块名的来源**（`LiquidBlockRenderer` 用 `fluidState.getType()` 取描述键）。`block.thrive.xxx_block` 也建议写——某些界面按流体而非方块取名。

## 六、参数速查

### 6.1 流速 — `getTickDelay`

覆写 `Source` 和 `Flowing` 的 `getTickDelay(LevelReader)`，**两者必须一致**：

| 流体 | 值 | 表现 |
|---|---|---|
| 水 | 5 | 每 0.25 秒动一次 |
| **推荐中间值** | **20** | **每 1 秒动一次** |
| 岩浆 | 30 | 每 1.5 秒动一次 |

### 6.2 扩散范围 — `BaseFlowingFluid.Properties`

| 参数 | 水 | 岩浆 | 作用 |
|---|---|---|---|
| `slopeFindDistance` | 4 | 2 | 向下找坡的最大水平距离 |
| `levelDecreasePerBlock` | 1 | 2 | 每水平扩散一格损失的液面高度 |

想铺得远 → 抄水（4 / 1）。想流得短 → 抄岩浆（2 / 2）。

### 6.3 `FluidType.Properties` 关键字段

| 字段 | 值 | 说明 |
|---|---|---|
| `density` | 水=1000，岩浆=3000 | >0 下沉，<0 上升 |
| `temperature` | 水=300 | 影响音效与部分交互 |
| `canConvertToSource` | 水=true，岩浆=false | 能否无限再生 |
| `lightLevel` | 0~15 | **仅属性**，方块发光见 6.4 |
| `viscosity` | — | NeoForge 1.21 未接入 tickDelay，流速见 6.1 |

### 6.4 方块发光 — `LiquidBlock.getLightEmission`

```java
@Override
public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
    return state.getFluidState().getFluidType().getLightLevel();
}
```

原版 `LiquidBlock` 默认返回 0，**必须覆写**才能读 `FluidType.lightLevel`。

## 七、复用原版水纹理

客户端扩展里返回：

```java
ResourceLocation.withDefaultNamespace("block/water_still")   // 静态
ResourceLocation.withDefaultNamespace("block/water_flow")    // 流动
```

`withDefaultNamespace` = `minecraft` 命名空间。

**同时**：

- 删 `textures/block/xxx_still.png` 和 `_flow.png`
- 把 `models/block/xxx.json` 的 `particle` 指向 `minecraft:block/water_still`
