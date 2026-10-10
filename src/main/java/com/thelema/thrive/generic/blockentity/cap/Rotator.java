package com.thelema.thrive.generic.blockentity.cap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 浮空旋转展示能力。
 * 物品绕 Y 轴匀速旋转，位置固定（不浮动）。
 */
public class Rotator {

    public final BlockEntity owner;

    public ItemStack item = ItemStack.EMPTY;
    public float rotation;
    public float prevRotation;

    /** 悬浮高度，方块中心往上多少格 */
    public float height = 1.25f;

    /** 每秒旋转角度 */
    public float degreesPerSecond = 90f;

    public Rotator(BlockEntity owner) {
        this.owner = owner;
    }

    public boolean isEmpty() {
        return item.isEmpty();
    }

    public void set(ItemStack stack) {
        this.item = stack.copyWithCount(1);
        this.rotation = 0;
        this.prevRotation = 0;
        owner.setChanged();
        sync();
    }

    public void clear() {
        this.item = ItemStack.EMPTY;
        owner.setChanged();
        sync();
    }

    /** 每 tick 推进旋转角度。插值由渲染器用 prevRotation → rotation 完成。 */
    public void tick() {
        if (item.isEmpty()) return;
        prevRotation = rotation;
        rotation = (rotation + degreesPerSecond / 20f) % 360f;
    }

    public void dropItem() {
        Level level = owner.getLevel();
        if (item.isEmpty() || level == null || level.isClientSide) return;
        Block.popResource(level, owner.getBlockPos().above(), item);
        item = ItemStack.EMPTY;
        owner.setChanged();
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!item.isEmpty()) {
            tag.put("RotatorItem", item.save(registries));
            tag.putFloat("RotatorAngle", rotation);
        } else {
            tag.putBoolean("RotatorEmpty", true);
        }
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("RotatorItem")) {
            item = ItemStack.parse(registries, tag.getCompound("RotatorItem")).orElse(ItemStack.EMPTY);
            rotation = tag.getFloat("RotatorAngle");
            prevRotation = rotation;
        } else if (tag.contains("RotatorEmpty")) {
            item = ItemStack.EMPTY;
        }
    }

    private void sync() {
        Level level = owner.getLevel();
        if (level == null || level.isClientSide) return;
        BlockPos pos = owner.getBlockPos();
        level.sendBlockUpdated(pos, owner.getBlockState(), owner.getBlockState(), 3);
    }
}