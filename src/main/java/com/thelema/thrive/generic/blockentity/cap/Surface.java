package com.thelema.thrive.generic.blockentity.cap;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Random;

/**
 * 表面放物品的能力组件。
 * 任何 BlockEntity 加一个字段就能获得，不需要继承。
 */
public class Surface {

    public final BlockEntity owner;

    /** 被展示的物品及位姿。null = 空。 */
    public TransportedItemStack item;

    public Surface(BlockEntity owner) {
        this.owner = owner;
    }

    public ItemStack getStack() {
        return item == null ? ItemStack.EMPTY : item.stack;
    }

    public boolean isEmpty() {
        return item == null || item.stack.isEmpty();
    }

    // ===== 状态变更 =====

    public void set(ItemStack stack, Direction from) {
        TransportedItemStack tis = new TransportedItemStack(stack.copy());
        tis.insertedFrom = from;
        tis.beltPosition = from.getAxis().isVertical() ? 0.5f : 0f;
        tis.prevBeltPosition = tis.beltPosition;
        this.item = tis;
        owner.setChanged();
        sync();
    }

    public void clear() {
        this.item = null;
        owner.setChanged();
        sync();
    }

    /** 改表面物品的数量并同步。 */
    public void setCount(int count) {
        item.stack.setCount(count);
        owner.setChanged();
        sync();
    }

    // ===== 每 tick 推进位姿 =====

    public void tick() {
        if (item == null) return;

        item.prevBeltPosition = item.beltPosition;
        item.prevSideOffset = item.sideOffset;

        float diff = 0.5f - item.beltPosition;
        if (diff > 1 / 512f) {
            if (diff > 1 / 32f && !TransportedItemStack.isItemUpright(item.stack))
                item.angle += 1;
            item.beltPosition += diff / 4f;
        }
        item.sideOffset = item.getTargetSideOffset();
    }

    // ===== 掉落 =====

    public void dropItem() {
        Level level = owner.getLevel();
        if (item == null || level == null || level.isClientSide) return;
        Block.popResource(level, owner.getBlockPos(), item.stack);
        item = null;
        owner.setChanged();
    }

    // ===== NBT =====

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (item != null) {
            tag.put("SurfaceItem", item.serializeNBT(registries));
        } else {
            tag.putBoolean("SurfaceItemEmpty", true);   // 显式标记空
        }
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("SurfaceItem")) {
            item = TransportedItemStack.read(tag.getCompound("SurfaceItem"), registries);
        } else if (tag.contains("SurfaceItemEmpty")) {
            item = null;   // 客户端按空处理
        }
        // 若两者都没有：读档/初始状态，保持当前
    }

    // ===== 同步 =====

    /**
     * BE 数据直发给追踪此位置的玩家。
     * sendBlockUpdated 走方块变更管线，方块状态没变时不保证发 BE 包——取出物品正是这种情况。
     */
    private void sync() {
        if (!(owner.getLevel() instanceof ServerLevel level)) return;
        Packet<ClientGamePacketListener> packet = owner.getUpdatePacket();
        if (packet == null) return;
        ChunkPos cp = new ChunkPos(owner.getBlockPos());
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(cp, false)) {
            player.connection.send(packet);
        }
    }

    /**
     * 物品在方块表面的位姿载体。
     * 参考 Create 的 TransportedItemStack，去掉皮带/风扇/锁相关。
     */
    public static class TransportedItemStack {

        private static final Random R = new Random();

        public ItemStack stack;
        public float beltPosition;      // 距中心的偏移：0 = 方块边缘，0.5 = 中心
        public float prevBeltPosition;
        public float sideOffset;        // 横向摆动
        public float prevSideOffset;
        public int angle;               // 旋转角
        public Direction insertedFrom;  // 从哪边滑入

        public TransportedItemStack(ItemStack stack) {
            this.stack = stack;
            boolean upright = isItemUpright(stack);
            this.angle = upright ? 180 : R.nextInt(360);
            this.sideOffset = this.prevSideOffset = getTargetSideOffset();
            this.insertedFrom = Direction.UP;
            this.beltPosition = 0.5f;
            this.prevBeltPosition = 0.5f;
        }

        public float getTargetSideOffset() {
            return (angle - 180) / (360 * 3f);
        }

        public static boolean isItemUpright(ItemStack stack) {
            if (stack.getItem() instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                return block instanceof DirectionalBlock
                        || block instanceof HorizontalDirectionalBlock;
            }
            return false;
        }

        public TransportedItemStack copy() {
            TransportedItemStack c = new TransportedItemStack(stack.copy());
            c.beltPosition = beltPosition;
            c.prevBeltPosition = prevBeltPosition;
            c.sideOffset = sideOffset;
            c.prevSideOffset = prevSideOffset;
            c.angle = angle;
            c.insertedFrom = insertedFrom;
            return c;
        }

        public CompoundTag serializeNBT(HolderLookup.Provider registries) {
            CompoundTag nbt = new CompoundTag();
            nbt.put("Item", stack.saveOptional(registries));
            nbt.putFloat("Pos", beltPosition);
            nbt.putFloat("PrevPos", prevBeltPosition);
            nbt.putFloat("Offset", sideOffset);
            nbt.putFloat("PrevOffset", prevSideOffset);
            nbt.putInt("Angle", angle);
            nbt.putInt("InDirection", insertedFrom.get3DDataValue());
            return nbt;
        }

        public static TransportedItemStack read(CompoundTag nbt, HolderLookup.Provider registries) {
            ItemStack stack = ItemStack.parseOptional(registries, nbt.getCompound("Item"));
            TransportedItemStack tis = new TransportedItemStack(stack);
            tis.beltPosition = nbt.getFloat("Pos");
            tis.prevBeltPosition = nbt.getFloat("PrevPos");
            tis.sideOffset = nbt.getFloat("Offset");
            tis.prevSideOffset = nbt.getFloat("PrevOffset");
            tis.angle = nbt.getInt("Angle");
            tis.insertedFrom = Direction.from3DDataValue(nbt.getInt("InDirection"));
            return tis;
        }
    }
}
