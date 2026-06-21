package com.thelema.thrive.generic.world.feature.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

import java.util.List;

public record OreDepositConfig(
        List<Block> targetBlocks,
        List<Block> oreBlocks,
        List<Block> rawBlocks,
        int oreWeight,
        int rawWeight,
        float replaceChance
) implements FeatureConfiguration {
    public static final Codec<OreDepositConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BuiltInRegistries.BLOCK.byNameCodec().listOf().fieldOf("targetBlocks").forGetter(OreDepositConfig::targetBlocks),
                    BuiltInRegistries.BLOCK.byNameCodec().listOf().fieldOf("oreBlocks").forGetter(OreDepositConfig::oreBlocks),
                    BuiltInRegistries.BLOCK.byNameCodec().listOf().fieldOf("rawBlocks").forGetter(OreDepositConfig::rawBlocks),
                    Codec.INT.fieldOf("oreWeight").forGetter(OreDepositConfig::oreWeight),
                    Codec.INT.fieldOf("rawWeight").forGetter(OreDepositConfig::rawWeight),
                    Codec.FLOAT.fieldOf("replaceChance").forGetter(OreDepositConfig::replaceChance)
            ).apply(instance, OreDepositConfig::new)
    );
}