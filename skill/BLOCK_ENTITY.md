# 添加方块 + 方块实体

## 步骤

**1. 方块类** — `generic/block/Xxx.java`

```java
public class Xxx extends Block implements EntityBlock {

    public Xxx() {
        super(Properties.of()
                .strength(2.0f)
                .sound(SoundType.STONE));
    }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new XxxBlockEntity(pos, state);
    }

    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (type != BlockEntityRegister.XXX.get()) return null;
        return (lvl, pos, st, be) -> XxxBlockEntity.tick(lvl, pos, st, (XxxBlockEntity) be);
    }
}
```

不用 `BaseEntityBlock`——它默认 `INVISIBLE` 渲染、强推 `codec()`、提供 GUI 钩子，是给箱子/熔炉那种内容方块用的。普通方块 + BE 用 `Block implements EntityBlock`。

**2. 方块实体类** — `generic/blockentity/XxxBlockEntity.java`

```java
public class XxxBlockEntity extends BlockEntity {

    public XxxBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegister.XXX.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, XxxBlockEntity be) {
        // 逻辑
    }
}
```

**3. 注册**

```java
// register/BlockRegister.java
public static final DeferredHolder<Block, Block> XXX;

static {
    XXX = registerWithItem("xxx", Xxx::new);
}
```

```java
// register/BlockEntityRegister.java
public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XxxBlockEntity>> XXX;

static {
    XXX = BLOCK_ENTITY.register("xxx", () -> BlockEntityType.Builder
            .of(XxxBlockEntity::new, BlockRegister.XXX.get())
            .build(null));
}
```

**4. 资源文件** — 同 `BLOCK.md`。

## 客户端同步

需要客户端看到状态变化时：

```java
@Override
public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    CompoundTag tag = super.getUpdateTag(registries);
    // 填要同步的字段
    return tag;
}

@Override
public Packet<ClientGamePacketListener> getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
}
```

服务端修改后主动推送：

```java
level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
```

**漏了 `getUpdatePacket` 的症状**：客户端不刷新，重进世界才看到。因为默认返回 `null`，`sendBlockUpdated` 发不出去。

## NBT 持久化

```java
@Override
protected void saveAdditional(CompoundTag tag, HolderLookup.Provider regs) {
    super.saveAdditional(tag, regs);
    // 写字段
}

@Override
protected void loadAdditional(CompoundTag tag, HolderLookup.Provider regs) {
    super.loadAdditional(tag, regs);
    // 读字段
}
```

## 方块侧交互

```java
@Override
protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                          BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
    if (level.isClientSide) return ItemInteractionResult.SUCCESS;
    if (!(level.getBlockEntity(pos) instanceof XxxBlockEntity be))
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    // ...
    return ItemInteractionResult.SUCCESS;
}

@Override
public void onRemove(BlockState state, Level level, BlockPos pos,
                     BlockState newState, boolean moved) {
    if (!level.isClientSide && state.getBlock() != newState.getBlock()
            && level.getBlockEntity(pos) instanceof XxxBlockEntity be) {
        // 掉落、清理
    }
    super.onRemove(state, level, pos, newState, moved);
}
```

## 渲染器

**类** — `client/render/blockentity/XxxRenderer.java`

```java
public class XxxRenderer implements BlockEntityRenderer<XxxBlockEntity> {

    public XxxRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(XxxBlockEntity be, float partialTicks, PoseStack ms,
                       MultiBufferSource buffer, int light, int overlay) {
        // 渲染逻辑
    }
}
```

**注册** — `client/register/BlockEntityRenderRegister.java`

```java
event.registerBlockEntityRenderer(BlockEntityRegister.XXX.get(), XxxRenderer::new);
```

## 清单

| 文件 | 路径 |
|---|---|
| 方块类 | `generic/block/Xxx.java` |
| BE 类 | `generic/blockentity/XxxBlockEntity.java` |
| 方块注册 | `register/BlockRegister.java` |
| BE 注册 | `register/BlockEntityRegister.java` |
| 渲染器（可选） | `client/render/blockentity/XxxRenderer.java` |
| 渲染器注册（可选） | `client/register/BlockEntityRenderRegister.java` |
| 资源 | 同 `BLOCK.md` |