# 添加方块（无 BE）

## 步骤

**1. 方块类** — `generic/block/Xxx.java`

```java
public class Xxx extends Block {
    public Xxx() {
        super(Properties.of()
                .strength(2.0f)
                .sound(SoundType.STONE));
    }
}
```

无特殊逻辑时可不建类，直接在注册器里 `new Block(...)`。

**2. 注册** — `register/BlockRegister.java`

```java
public static final DeferredHolder<Block, Block> XXX;

static {
        XXX = registerWithItem("xxx", Xxx::new);
        }
```

`registerWithItem` 自动注册对应的 `BlockItem`。

**3. 资源文件**

| 类型 | 路径 |
|---|---|
| 方块状态 | `assets/thrive/blockstates/xxx.json` |
| 方块模型 | `assets/thrive/models/block/xxx.json` |
| 物品模型 | `assets/thrive/models/item/xxx.json` |
| 纹理 | `assets/thrive/textures/block/xxx.png` |
| 语言 | `assets/thrive/lang/{en_us,zh_cn}.json` |

方块状态（无变体）：

```json
{ "variants": { "": { "model": "thrive:block/xxx" } } }
```

方块模型（完整立方体）：

```json
{
  "parent": "minecraft:block/cube_all",
  "textures": { "all": "thrive:block/xxx" }
}
```

物品模型：

```json
{ "parent": "thrive:block/xxx" }
```

语言：

```json
"block.thrive.xxx": "Xxx"
```

## 渲染层 render_type

模型 JSON 顶层字段，决定方块的**渲染通道**。不写时由加载器推断，自定义方块常被推断错（例如植物变成实心黑块），**建议显式指定**。

```json
{
  "parent": "minecraft:block/cross",
  "render_type": "minecraft:cutout",
  "textures": { "cross": "thrive:block/xxx" }
}
```

| `render_type` | 行为 | 典型方块 |
|---|---|---|
| `solid` | 完全不透明，忽略 alpha | 石头、泥土、木板 |
| `cutout` | Alpha 测试：alpha=0 的像素丢弃，其余不透明；无 mipmap | 玻璃、树苗、花草、门、栏杆、梯子 |
| `cutout_mipped` | 同 `cutout`，但纹理启用 mipmap，远处不闪烁 | 树叶、藤蔓、大型蕨类 |
| `cutout_mipped_all` | 同 `cutout_mipped`，物品形态也启用 mipmap | 需要物品栏清晰的树叶 |
| `translucent` | 半透明混合，参与排序 | 染色玻璃、冰、水、史莱姆方块 |
| `tripwire` | 绊线专用（渲染到天气目标） | 绊线 |

选择规则：

- 纹理**只有全透或全不透**（alpha 仅 0 / 255）→ `cutout`；面积大且远距离可见 → `cutout_mipped`
- 纹理有**半透明像素**（0 < alpha < 255）→ `translucent`
- 全不透明 → 不写（默认 `solid`）
- 植物/树苗误用 `solid` → 镂空处变黑；误用 `translucent` → 交叉面排序闪烁，性能更差

## Properties 常用项

```java
Properties.of()
        .strength(2.0f, 6.0f)         // 硬度、抗爆
        .sound(SoundType.STONE)
        .mapColor(MapColor.STONE)
        .requiresCorrectToolForDrops()
        .noCollission()
        .noOcclusion()
        .lightLevel(s -> 15)
        .randomTicks();
```

## 方块状态属性

需要变体（朝向、开关、水位……）时：

```java
public static final BooleanProperty LIT = BooleanProperty.create("lit");

public Xxx() {
        super(Properties.of());
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }

@Override
protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
        }
```

blockstate JSON 按属性组合枚举：

```json
{
  "variants": {
    "lit=false": { "model": "thrive:block/xxx" },
    "lit=true":  { "model": "thrive:block/xxx_on" }
  }
}
```

## 覆盖常用方法

```java
@Override
public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
    return Shapes.box(0, 0, 0, 1, 0.5, 1);
}

@Override
protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                          BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
    // 返回 PASS_TO_DEFAULT_BLOCK_INTERACTION 表示不处理
    return ItemInteractionResult.SUCCESS;
}

@Override
public void onRemove(BlockState state, Level level, BlockPos pos,
                     BlockState newState, boolean moved) {
    if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
        // 掉落、清理
    }
    super.onRemove(state, level, pos, newState, moved);
}
```

## 半透明方块

玻璃、冰、染色玻璃、云、自定义透明方块，都需要"看得见但透光"。这件事横跨**三个层**，缺一不可。先讲清每个方法干什么，再给完整写法。

### 三个基类

| 基类 | 提供什么 | 适合 |
|---|---|---|
| `Block` | 全不透明默认行为，所有方法自己写 | 完全自定义，或根本不需要 `skipRendering` 优化 |
| `HalfTransparentBlock` | **`skipRendering`：相邻同方块时跳过对面渲染** | **绝大多数半透明方块（推荐）** |
| `TransparentBlock` | 上者 + `getVisualShape` 返回 `Shapes.empty()`（视觉完全不可见）+ `propagatesSkylightDown`、`getShadeBrightness` 已写 + `codec` | 玻璃这种"几乎看不见本体"的方块 |

**`HalfTransparentBlock.skipRendering` 是关键**：

```java
@Override
protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
    return adjacent.is(this) ? true : super.skipRendering(state, adjacent, side);
}
```

含义：**当前方块与相邻方块是同类时，不绘制朝向对方的那一面**。一整片云/玻璃挨在一起，内部交界面全部被跳过——面数减半、无 z-fighting、无内部明暗错乱。`TransparentBlock` 继承自 `HalfTransparentBlock`，所以玻璃也有这个行为，但云块不需要它的"视觉不可见"，**直接用 `HalfTransparentBlock` 最贴切**。

`TransparentBlock.getVisualShape` 返回 `Shapes.empty()` 会让方块**在视觉上不产生任何遮挡形状**——玻璃从外面看是透明的一片，这是刻意的玻璃语义。云块有材质，绝不能继承它。

### Properties 层：两个开关

```java
BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL)
        .noOcclusion()   // 不剔除相邻面、不让实体窒息、不挡视线
        // 想要"能穿过去"才加 .noCollission()，与 occlusion 无关
```

| 方法 | 作用 | 不加会怎样 |
|---|---|---|
| `noOcclusion()` | 不剔除相邻面的渲染、不窒息实体、不挡视线 | 相邻方块面向它的那一面被剔除，从外面看不到云后面的世界；实体站在里面会窒息 |
| `noCollission()` | **无碰撞盒**，实体能穿过 | 只影响物理碰撞，与光照、渲染无关 |

**没有 `lightBlock(0)` 这种 API**。`BlockBehaviour.Properties` 不提供光照穿透开关。光照穿透完全靠 `Block` 的方法重写，见下。

### 方法层：三个重写

```java
public class Cloud extends HalfTransparentBlock {

    public Cloud() {
        super(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL).noOcclusion());
    }

    // ① 天空光可穿过这一格继续向下传播
    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    // ② 不产生环境光遮蔽（AO），方块周围没有暗角
    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    // ③ skipRendering 已由 HalfTransparentBlock 提供，无需重写
}
```

| 方法 | 影响 | 不重写的后果 |
|---|---|---|
| `propagatesSkylightDown` | 天空光能否穿过本格继续向下 | 云块下方变暗，天光在云层停住 |
| `getShadeBrightness` | 相邻面的 AO 亮度，`1.0F` = 无暗角 | 云块四周出现阴影，视觉上像实心块 |
| `skipRendering`（父类已给） | 相邻同方块时跳过交界面渲染 | 云层内部面数翻倍，z-fighting |

### 渲染层：模型 JSON

半透明的**混合**无法用 Java 方法表达，只能写在模型里：

```json
{
  "parent": "minecraft:block/cube_all",
  "render_type": "translucent",
  "textures": { "all": "thrive:block/cloud" }
}
```

`render_type: translucent` 开启 alpha 混合。少了它，纹理有 alpha 也画成不透明——这是"我明明写了半透明图片，游戏里却全不透明"的最常见原因。

### 完整四件套对照

| 目标 | 落点 |
|---|---|
| 不剔除相邻面、不窒息、不挡视线 | `Properties.noOcclusion()` |
| 天空光穿透 | 重写 `propagatesSkylightDown` → `true` |
| 无暗角 | 重写 `getShadeBrightness` → `1.0F` |
| 相邻同方块合并渲染 | 继承 `HalfTransparentBlock`（已带 `skipRendering`） |
| 半透明混合 | 模型 `render_type: translucent` |
| 实体能穿过（可选） | `Properties.noCollission()` |

### 常见坑

| 坑 | 症状 |
|---|---|
| 只写 `render_type: translucent` | 视觉半透明，但不透光，下方发暗 |
| 只重写 `propagatesSkylightDown` | 透光，但纹理画成实心，看不到"半透" |
| 继承 `Block` 而非 `HalfTransparentBlock` | 云层内部面数翻倍、z-fighting，堆叠时性能差 |
| 误用 `TransparentBlock` | 云块整体视觉不可见（`getVisualShape` 返回空） |
| 以为 `noOcclusion` = 无碰撞 | 云能踩住但会窒息/剔除面；要穿过去得加 `noCollission` |
| 试图找 `Properties.lightBlock(0)` | API 不存在，光照穿透靠方法重写 |