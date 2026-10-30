package com.thelema.thrive.generic.blockentity.modifier.garden;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import com.thelema.thrive.generic.blockentity.modifier.BiomeModifier;
import com.thelema.thrive.generic.blockentity.modifier.Ring;
import com.thelema.thrive.generic.blockentity.modifier.Succession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import static com.thelema.thrive.generic.blockentity.modifier.BiomeUtil.*;

public class Garden implements BiomeModifier {

    private static final Succession TO_GARDEN = new Succession(null, GardenHelper.TARGET);

    @Override
    public boolean canUse(ServerLevel level, BiomeCoreBlockEntity be) {
        return level.getBiome(be.getBlockPos()).is(GardenHelper.TARGET);
    }

    @Override
    public boolean tick(ServerLevel level, BiomeCoreBlockEntity be) {
        int phase = getPhase(be);
        if (phase >= GardenHelper.PHASE_DONE) {
            replaceStructure(level, be, GardenHelper.TREE_GARDEN);
            return true;
        }

        if (phase == GardenHelper.PHASE_REWRITE) {
            boolean done = forBS(be, Ring.DEFAULT, TO_GARDEN);
            if (done) nextPhase(be);
            return false;
        }

        boolean done = forPos(be, Ring.DEFAULT, (b, p) -> {
            if (level.getBiome(p).is(GardenHelper.TARGET)) return;  // B 方案：入口判
            switch (phase) {
                case GardenHelper.PHASE_PREPARE -> {
                    GardenHelper.clear(level, p);
                    surface(level, p);
                }
                case GardenHelper.PHASE_GARDEN -> GardenHelper.garden(level, be, p);
                case GardenHelper.PHASE_BONE_MEAL -> GardenHelper.boneMeal(level, be, p);
            }
        });
        if (done) nextPhase(be);
        return false;
    }

    // ───── 地表改造 ─────

    void surface(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir() || !s.getFluidState().isEmpty()) return;
        if (!isNaturalTerrain(s)) return;

        // ① 上方空气 → 草
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX(), p.getZ());
        if (p.getY() >= surfaceY - 1 && level.getBlockState(p.above()).isAir()) {
            set(level, p, Blocks.GRASS_BLOCK.defaultBlockState());
            markDirty(level, p);
            return;
        }

        // ② 侧面裸漏 → 泥土
        if (hasAirSide(level, p)) {
            set(level, p, Blocks.DIRT.defaultBlockState());
            markDirty(level, p);
            return;
        }
        // ③ 深层沙/砂岩，仅地表下 SAND_DEPTH 格内
        int depth = level.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX(), p.getZ()) - p.getY();
        if (depth < 1 || depth > GardenHelper.SAND_DEPTH) return;
        if (s.is(BlockTags.SAND)) {
            set(level, p, Blocks.DIRT.defaultBlockState());
            markDirty(level, p);
        } else if (s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)) {
            set(level, p, Blocks.STONE.defaultBlockState());
            markDirty(level, p);
        }
    }

    static boolean hasAirSide(ServerLevel level, BlockPos p) {
        for (Direction d : Direction.Plane.HORIZONTAL)
            if (level.getBlockState(p.relative(d)).isAir()) return true;
        return false;
    }

    static boolean isNaturalTerrain(BlockState s) {
        Block b = s.getBlock();
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND)
                || s.is(BlockTags.BASE_STONE_OVERWORLD)
                || s.is(BlockTags.BASE_STONE_NETHER)
                || s.is(BlockTags.TERRACOTTA)
                || b == Blocks.END_STONE
                || s.is(BlockTags.SNOW) || s.is(BlockTags.ICE)
                || b == Blocks.GRAVEL || b == Blocks.CLAY || b == Blocks.MUD
                || b == Blocks.CALCITE || b == Blocks.DRIPSTONE_BLOCK
                || b == Blocks.MAGMA_BLOCK || b == Blocks.OBSIDIAN
                || b == Blocks.SOUL_SAND || b == Blocks.SOUL_SOIL
                || b == Blocks.BONE_BLOCK || b == Blocks.SCULK
                || b == Blocks.SCULK_CATALYST;
    }
}