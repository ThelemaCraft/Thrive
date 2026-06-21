package com.thelema.thrive.generic.world.feature;

import com.mojang.serialization.Codec;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class GiantGlowBerryPlacerFeature extends Feature<NoneFeatureConfiguration> {

    public GiantGlowBerryPlacerFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();

        BlockPos target = context.origin().below();
        if (level.isEmptyBlock(target) || level.getBlockState(target).canBeReplaced()) {
            level.setBlock(target, BlockRegister.GIANT_GLOWING_BERRY.get().defaultBlockState(), 3);
            return true;
        }
        return false;
    }


}