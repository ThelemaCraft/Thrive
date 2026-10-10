package com.thelema.thrive.generic.blockentity.tool;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import java.util.Random;

/**
 * 物品在方块表面的位姿载体。
 * 参考 Create 的 TransportedItemStack，去掉皮带/风扇/锁相关。
 */
public class TransportedItemStack {

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