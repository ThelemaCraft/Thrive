package com.thelema.thrive.generic.blockentity.modifier.moss;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import com.thelema.thrive.generic.blockentity.modifier.BiomeModifier;
import com.thelema.thrive.generic.blockentity.modifier.Ring;
import com.thelema.thrive.generic.blockentity.modifier.Succession;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.thelema.thrive.generic.blockentity.modifier.BiomeUtil.*;

/**
 * 任意群系 → Moss。与 Garden 定位一致：从 Moss 群系出发向外扩散，
 * 不破坏已有 Moss 地形，仅影响范围内的非 Moss 区域。
 */
public class Moss implements BiomeModifier {

    static final int PHASE_PREPARE = 0;
    static final int PHASE_LAKE = 1;
    static final int PHASE_DECO = 2;
    static final int PHASE_REWRITE = 3;

    private static final Succession ANY_TO_MOSS = new Succession(null, MossHelper.TARGET);

    @Override
    public boolean canUse(ServerLevel level, BiomeCoreBlockEntity be) {
        return level.getBiome(be.getBlockPos()).is(MossHelper.TARGET);
    }

    @Override
    public boolean tick(ServerLevel level, BiomeCoreBlockEntity be) {
        switch (getPhase(be)) {
            case PHASE_PREPARE -> {
                if (forPos(be, Ring.DEFAULT, (b, p) -> {
                    clear(level, p);
                    surface(level, p);
                })) nextPhase(be);
                return false;
            }
            case PHASE_LAKE -> {
                if (forPos(be, Ring.FAST_I, MossHelper.lake(t -> true))) nextPhase(be);
                return false;
            }
            case PHASE_DECO -> {
                if (forPos(be, Ring.DEFAULT, (b, p) -> {
                    if (level.getBiome(p).is(MossHelper.TARGET)) return;
                    MossHelper.deco(level, p);
                })) nextPhase(be);
                return false;
            }
            case PHASE_REWRITE -> {
                if (forBS(be, Ring.DEFAULT, ANY_TO_MOSS)) nextPhase(be);
                return false;
            }
            default -> {
                replaceStructure(level, be, MossHelper.TREE_MOSS);
                return true;
            }
        }
    }

    /**
     * 清树、花草、蕨、藤蔓。跳过原 moss 区域。
     */
    static void clear(ServerLevel level, BlockPos p) {
        if (level.getBiome(p).is(MossHelper.TARGET)) return;
        BlockState s = level.getBlockState(p);
        if (s.isAir() || !s.getFluidState().isEmpty()) return;
        boolean plant = s.is(BlockTags.REPLACEABLE_BY_TREES)
                || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS)
                || s.is(Blocks.MOSS_CARPET)
                || s.is(Blocks.CAVE_VINES) || s.is(Blocks.CAVE_VINES_PLANT)
                || s.is(BlockRegister.LANTERN_BERRY.get());
        boolean tree = s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES);
        if (!plant && !tree) return;
        set(level, p, Blocks.AIR.defaultBlockState());
        markDirty(level, p);
    }

    /**
     * 上方是空气的自然地形 → MOSS_BLOCK。跳过原 moss 区域。
     */
    static void surface(ServerLevel level, BlockPos p) {
        if (level.getBiome(p).is(MossHelper.TARGET)) return;
        BlockState s = level.getBlockState(p);
        if (s.isAir() || !s.getFluidState().isEmpty()) return;
        if (!MossHelper.isNaturalTerrain(s)) return;
        if (!level.getBlockState(p.above()).isAir()) return;
        set(level, p, Blocks.MOSS_BLOCK.defaultBlockState());
        markDirty(level, p);
    }
}