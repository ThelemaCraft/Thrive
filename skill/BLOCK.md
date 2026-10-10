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

## 加入创造标签

`register/TabRegister.java` 对应标签的 `displayItems` 生成器：

```java
output.accept(BlockRegister.XXX.get());
```