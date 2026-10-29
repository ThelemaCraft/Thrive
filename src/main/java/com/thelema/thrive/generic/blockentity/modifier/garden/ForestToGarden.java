package com.thelema.thrive.generic.blockentity.modifier.garden;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import com.thelema.thrive.generic.blockentity.modifier.BiomeModifier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

import java.util.function.Predicate;

public class ForestToGarden implements BiomeModifier {

    static final ResourceKey<Biome> SOURCE = ResourceKey.create(
            Registries.BIOME, ResourceLocation.withDefaultNamespace("forest"));

    @Override
    public boolean canUse(ServerLevel level, BiomeCoreBlockEntity be) {
        return level.getBiome(be.getBlockPos()).is(SOURCE);
    }

    @Override
    public void tick(ServerLevel level, BiomeCoreBlockEntity be) {
        long tick = tick(be);
        int ring = (int) (tick / GardenHelper.TICKS_PER_RING);
        var inRange = (Predicate<Holder<Biome>>) b -> b.is(SOURCE) || b.is(GardenHelper.TARGET);

        if (tick % GardenHelper.TICKS_PER_RING == 0) {
            if (ring <= GardenHelper.MAX_RING) {
                rewriteBiome(level, be, ring, SOURCE, GardenHelper.TARGET);
            }
            int r1 = ring - GardenHelper.CLEAR_DELAY;
            if (r1 >= 0 && r1 <= GardenHelper.MAX_RING) {
                GardenHelper.processClear(this, level, be, r1, inRange);
            }
            int r2 = ring - GardenHelper.GARDEN_DELAY;
            if (r2 >= 0 && r2 <= GardenHelper.MAX_RING) {
                GardenHelper.processGarden(this, level, be, r2, inRange);
            }
            int r3 = ring - GardenHelper.BONE_MEAL_DELAY;
            if (r3 >= 0 && r3 <= GardenHelper.MAX_RING) {
                GardenHelper.processBoneMeal(this, level, be, r3);
            }
        }
        if (tick % 10 == 0) flushDirty(level, be);
        if (ring >= GardenHelper.MAX_RING + GardenHelper.BONE_MEAL_DELAY + 1) {
            flushDirty(level, be);
            be.resetConversion();
            replaceStructure(level, be, GardenHelper.TREE_GARDEN);
            return;
        }
        advanceTick(be);
    }
}