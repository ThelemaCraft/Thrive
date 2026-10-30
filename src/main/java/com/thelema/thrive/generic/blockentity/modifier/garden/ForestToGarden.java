package com.thelema.thrive.generic.blockentity.modifier.garden;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import com.thelema.thrive.generic.blockentity.modifier.BiomeModifier;
import com.thelema.thrive.generic.blockentity.modifier.Ring;
import com.thelema.thrive.generic.blockentity.modifier.Succession;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

import static com.thelema.thrive.generic.blockentity.modifier.BiomeUtil.*;

public class ForestToGarden implements BiomeModifier {

    static final ResourceKey<Biome> SOURCE = ResourceKey.create(
            Registries.BIOME, ResourceLocation.withDefaultNamespace("forest"));

    private static final Succession FOREST_TO_GARDEN = new Succession(SOURCE, GardenHelper.TARGET);

    @Override
    public boolean canUse(ServerLevel level, BiomeCoreBlockEntity be) {
        return level.getBiome(be.getBlockPos()).is(SOURCE);
    }

    @Override
    public boolean tick(ServerLevel level, BiomeCoreBlockEntity be) {
        int phase = getPhase(be);
        if (phase >= GardenHelper.PHASE_DONE) {
            replaceStructure(level, be, GardenHelper.TREE_GARDEN);
            return true;
        }

        if (phase == GardenHelper.PHASE_REWRITE) {
            boolean done = forBS(be, Ring.DEFAULT, FOREST_TO_GARDEN);
            if (done) nextPhase(be);
            return false;
        }

        boolean done = forPos(be, Ring.DEFAULT, (b, p) -> {
            if (level.getBiome(p).is(GardenHelper.TARGET)) return;
            switch (phase) {
                case GardenHelper.PHASE_PREPARE -> GardenHelper.clear(level, p);
                case GardenHelper.PHASE_GARDEN -> GardenHelper.garden(level, be, p);
                case GardenHelper.PHASE_BONE_MEAL -> GardenHelper.boneMeal(level, be, p);
            }
        });

        if (done) nextPhase(be);
        return false;
    }
}