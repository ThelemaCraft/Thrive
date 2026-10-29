package com.thelema.thrive.generic.blockentity.modifier.garden;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import com.thelema.thrive.generic.blockentity.modifier.BiomeModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class Garden implements BiomeModifier {

    @Override
    public boolean canUse(ServerLevel level, BiomeCoreBlockEntity be) {
        return level.getBiome(be.getBlockPos()).is(GardenHelper.TARGET);
    }

    @Override
    public void tick(ServerLevel level, BiomeCoreBlockEntity be) {
        long tick = tick(be);
        int ring = (int) (tick / GardenHelper.TICKS_PER_RING);

        if (tick % GardenHelper.TICKS_PER_RING == 0) {
            if (ring <= GardenHelper.MAX_RING) {
                rewriteBiomeAll(level, be, ring, GardenHelper.TARGET);
            }
            int r1 = ring - GardenHelper.CLEAR_DELAY;
            if (r1 >= 0 && r1 <= GardenHelper.MAX_RING) {
                GardenHelper.processClear(this, level, be, r1, b -> true);
                processSurface(level, be, r1);
            }
            int r2 = ring - GardenHelper.GARDEN_DELAY;
            if (r2 >= 0 && r2 <= GardenHelper.MAX_RING) {
                GardenHelper.processGarden(this, level, be, r2, b -> true);
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

    /** 暴露的自然地形 → 草地。 */
    void processSurface(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        forRing(level, be, r, (p, s) -> {
            if (s.isAir()) return;
            if (!s.getFluidState().isEmpty()) return;
            if (!isNaturalTerrain(s)) return;
            if (!isExposed(level, p)) return;
            set(level, p, grass);
            markDirty(be, p);
        });
    }

    static boolean isExposed(ServerLevel level, BlockPos p) {
        for (Direction d : Direction.values()) {
            BlockState ns = level.getBlockState(p.relative(d));
            if (ns.isAir()) return true;
            if (!ns.canOcclude()) return true;
        }
        return false;
    }

    static boolean isNaturalTerrain(BlockState s) {
        Block b = s.getBlock();
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND)
                || s.is(BlockTags.BASE_STONE_OVERWORLD)
                || s.is(BlockTags.BASE_STONE_NETHER)
                || s.is(BlockTags.TERRACOTTA)
                || s.is(BlockTags.SNOW) || s.is(BlockTags.ICE)
                || b == Blocks.GRAVEL || b == Blocks.CLAY || b == Blocks.MUD
                || b == Blocks.CALCITE || b == Blocks.DRIPSTONE_BLOCK
                || b == Blocks.MAGMA_BLOCK || b == Blocks.OBSIDIAN
                || b == Blocks.SOUL_SAND || b == Blocks.SOUL_SOIL
                || b == Blocks.BONE_BLOCK || b == Blocks.SCULK
                || b == Blocks.SCULK_CATALYST;
    }
}