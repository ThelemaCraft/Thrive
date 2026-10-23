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