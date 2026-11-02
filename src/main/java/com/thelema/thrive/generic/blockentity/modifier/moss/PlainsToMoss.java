package com.thelema.thrive.generic.blockentity.modifier.moss;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import com.thelema.thrive.generic.blockentity.modifier.BiomeModifier;
import com.thelema.thrive.generic.blockentity.modifier.Ring;
import com.thelema.thrive.generic.blockentity.modifier.Succession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

import static com.thelema.thrive.generic.blockentity.modifier.BiomeUtil.*;
import static com.thelema.thrive.generic.blockentity.modifier.moss.MossHelper.lake;

public class PlainsToMoss implements BiomeModifier {

    static final int PHASE_REWRITE = 0;
    static final int PHASE_SURFACE = 1;
    static final int PHASE_LAKE = 2;
    static final int PHASE_DECO = 3;
    static final int PHASE_DONE = 4;

    static final ResourceKey<Biome> SOURCE = ResourceKey.create(
            Registries.BIOME, ResourceLocation.withDefaultNamespace("plains"));

    static final Set<Block> REMOVE = Set.of(
            Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN,
            Blocks.DANDELION, Blocks.POPPY, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER,
            Blocks.AZURE_BLUET, Blocks.ORANGE_TULIP, Blocks.PINK_TULIP,
            Blocks.RED_TULIP, Blocks.WHITE_TULIP,
            Blocks.OAK_SAPLING, Blocks.OAK_LOG, Blocks.OAK_LEAVES,
            Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, Blocks.BIRCH_SAPLING);

    static final Set<Block> REPLACEABLE = Set.of(
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL,
            Blocks.STONE, Blocks.SAND, Blocks.GRAVEL, Blocks.CLAY,
            Blocks.MYCELIUM, Blocks.SNOW_BLOCK);

    private static final Succession PLAINS_TO_MOSS = new Succession(SOURCE, MossHelper.TARGET);

    @Override
    public boolean canUse(ServerLevel level, BiomeCoreBlockEntity be) {
        return level.getBiome(be.getBlockPos()).is(SOURCE);
    }

    @Override
    public boolean tick(ServerLevel level, BiomeCoreBlockEntity be) {
        int phase = getPhase(be);

        if (phase >= PHASE_DONE) {
            replaceStructure(level, be, MossHelper.TREE_MOSS);
            return true;
        }

        boolean done = switch (phase) {
            case PHASE_REWRITE -> forBS(be, Ring.FAST_I, PLAINS_TO_MOSS);
            case PHASE_SURFACE -> forPos(be, Ring.FAST_I, (b, p) -> surface(level, p));
            case PHASE_LAKE    -> forPos(be, Ring.DEFAULT, lake(p -> true));
            case PHASE_DECO    -> forPos(be, Ring.FAST_I, (b, p) -> MossHelper.deco(level, p));
            default -> true;
        };

        if (done) nextPhase(be);
        return false;
    }

    static void surface(ServerLevel level, BlockPos p) {
        if (!level.getBiome(p).is(MossHelper.TARGET)) return;
        BlockState s = level.getBlockState(p);
        if (s.isAir()) return;
        if (REMOVE.contains(s.getBlock())) {
            set(level, p, Blocks.AIR.defaultBlockState());
            markDirty(level, p);
            return;
        }
        if (!REPLACEABLE.contains(s.getBlock())) return;
        if (level.getBlockState(p.above()).isAir()
                || level.getBlockState(p.below()).isAir()) {
            set(level, p, Blocks.MOSS_BLOCK.defaultBlockState());
            markDirty(level, p);
        }
    }
}