package com.thelema.thrive.generic.blockentity;

import com.thelema.thrive.register.BlockEntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class SunBerryBlockEntity extends BlockEntity {

    @Nullable
    private BlockPos corePos;

    public SunBerryBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegister.SUN_BERRY.get(), pos, state);
    }

    public boolean isCore() {
        return corePos != null && corePos.equals(worldPosition);
    }

    @Nullable
    public BlockPos getCorePos() {
        return corePos;
    }

    public void setCorePos(BlockPos corePos) {
        this.corePos = corePos;
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (corePos != null) {
            tag.putLong("CorePos", corePos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("CorePos")) {
            corePos = BlockPos.of(tag.getLong("CorePos"));
        }
    }
}