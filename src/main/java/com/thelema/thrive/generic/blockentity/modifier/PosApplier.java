package com.thelema.thrive.generic.blockentity.modifier;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import net.minecraft.core.BlockPos;

@FunctionalInterface
public interface PosApplier {
    void apply(BiomeCoreBlockEntity be, BlockPos pos);
}