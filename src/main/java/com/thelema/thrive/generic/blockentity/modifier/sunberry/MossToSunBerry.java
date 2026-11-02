package com.thelema.thrive.generic.blockentity.modifier.sunberry;

import com.thelema.thrive.generic.block.SunBerry;
import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import com.thelema.thrive.generic.blockentity.modifier.BiomeModifier;
import com.thelema.thrive.generic.blockentity.modifier.Ring;
import com.thelema.thrive.generic.blockentity.modifier.Succession;
import com.thelema.thrive.register.constant.BiomeConstant;
import com.thelema.thrive.register.BlockRegister;
import com.thelema.thrive.register.FluidRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;

import static com.thelema.thrive.generic.blockentity.modifier.BiomeUtil.*;

public class MossToSunBerry implements BiomeModifier {

    static final int TICKS_PER_RING = 20;
    static final int MAX_RING = 64;
    static final int PHASE_REWRITE = 0;
    static final int PHASE_CLEAR = 1;
    static final int PHASE_SURFACE = 2;
    static final int PHASE_LAKE_DIG = 3;
    static final int PHASE_LAKE_FILL = 4;
    static final int PHASE_PLANT = 5;
    static final int PHASE_DONE = 6;

    static final int TREE_FREE_RADIUS = 7;

    static final ResourceKey<Biome> SOURCE = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath("thrive", "moss"));
    static final ResourceKey<Biome> TARGET = BiomeConstant.SUNBERRY;
    static final ResourceLocation TREE_SUNBERRY =
            ResourceLocation.fromNamespaceAndPath("thrive", "biome_core_tree_sunberry");

    static final int LAKE_TILE = 32;
    static final int LAKE_INNER = 20;
    static final int LAKE_DEPTH = 3;

    static final int SUN_BERRY_CHANCE = 512;
    static final int LANTERN_BERRY_CHANCE = 64;

    /** 湖列 → 挖洞前的 topY。phase 3 写、phase 4 读。 */
    private final Map<Long, Integer> lakeTop = new HashMap<>();

    private static final Succession MOSS_TO_SUN_BERRY = new Succession(SOURCE, TARGET);

    @Override
    public boolean canUse(ServerLevel level, BiomeCoreBlockEntity be) {
        return level.getBiome(be.getBlockPos()).is(SOURCE);
    }

    @Override
    public boolean tick(ServerLevel level, BiomeCoreBlockEntity be) {
        int phase = getPhase(be);
        if (phase >= PHASE_DONE) {
            replaceStructure(level, be, TREE_SUNBERRY);
            return true;
        }
        if (phase == PHASE_REWRITE) {
            boolean done = forBS(be, Ring.DEFAULT, MOSS_TO_SUN_BERRY);
            if (done) nextPhase(be);
            return false;
        }
        boolean done = forPos(be, Ring.DEFAULT, (b, p) -> {
            switch (phase) {
                case PHASE_CLEAR -> clear(level, p);
                case PHASE_SURFACE -> surface(level, p);
                case PHASE_LAKE_DIG -> lakeDig(level, be, p);
                case PHASE_LAKE_FILL -> lakeFill(level, be, p);
                case PHASE_PLANT -> plant(level, be, p);
            }
        });
        if (done) nextPhase(be);
        return false;
    }

    static Holder<Biome> target(ServerLevel level) {
        return level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(TARGET);
    }

    static boolean withinFreeRadius(BiomeCoreBlockEntity be, BlockPos p) {
        BlockPos c = be.getBlockPos();
        return Math.max(Math.abs(p.getX() - c.getX()), Math.abs(p.getZ() - c.getZ())) <= TREE_FREE_RADIUS;
    }

    // ─── 阶段 1：清理 ───
    void clear(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir()) return;
        if (!isMossOrSunberry(level, p)) return;

        boolean oak = s.is(Blocks.OAK_LOG) || s.is(Blocks.OAK_WOOD)
                || s.is(Blocks.DARK_OAK_LOG) || s.is(Blocks.DARK_OAK_WOOD)
                || s.is(Blocks.OAK_LEAVES) || s.is(Blocks.DARK_OAK_LEAVES);
        boolean azalea = s.is(Blocks.AZALEA_LEAVES) || s.is(Blocks.FLOWERING_AZALEA_LEAVES)
                || s.is(Blocks.AZALEA) || s.is(Blocks.FLOWERING_AZALEA);
        boolean plant = s.is(BlockTags.SAPLINGS)
                || s.is(BlockTags.FLOWERS)
                || s.is(BlockTags.REPLACEABLE_BY_TREES)
                || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS)
                || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN)
                || s.is(Blocks.MOSS_CARPET)
                || s.is(Blocks.CAVE_VINES) || s.is(Blocks.CAVE_VINES_PLANT)
                || s.is(BlockRegister.LANTERN_BERRY.get());
        if (!oak && !azalea && !plant) return;

        set(level, p, Blocks.AIR.defaultBlockState());
        markDirty(level, p);
    }

    // ─── 阶段 2：地表 ───
    void surface(ServerLevel level, BlockPos p) {
        if (!isMossOrSunberry(level, p)) return;
        BlockState s = level.getBlockState(p);

        if (!s.getFluidState().isEmpty()) {
            set(level, p, Blocks.AIR.defaultBlockState());
            markDirty(level, p);
            return;
        }
        if (!s.is(Blocks.GRASS_BLOCK) && !s.is(BlockTags.DIRT) && !s.is(Blocks.MOSS_BLOCK)) return;

        boolean sideExposed = false;
        for (Direction d : Direction.values()) {
            if (level.getBlockState(p.relative(d)).isAir()) {
                sideExposed = true;
                break;
            }
        }
        int topY = level.getHeightmapPos(Heightmap.Types.OCEAN_FLOOR, p).getY();
        boolean nearSurface = p.getY() >= topY - 4 && p.getY() <= topY;
        if (!sideExposed && !nearSurface) return;

        set(level, p, BlockRegister.LANTERN_BERRY_PEEL.get().defaultBlockState());
        markDirty(level, p);
    }

    // ─── 阶段 3：挖湖 ───
    void lakeDig(ServerLevel level, BiomeCoreBlockEntity be, BlockPos p) {
        if (withinFreeRadius(be, p)) return;
        if (!isLakePos(p)) return;
        if (!isMossOrSunberry(level, p)) return;
        long col = ((long) p.getX() << 32) | (p.getZ() & 0xFFFFFFFFL);
        if (lakeTop.containsKey(col)) return;  // 该列已挖
        int topY = level.getHeightmapPos(Heightmap.Types.OCEAN_FLOOR, p).getY();
        lakeTop.put(col, topY);
        for (int y = topY - 1; y >= topY - LAKE_DEPTH; y--) {
            BlockPos q = new BlockPos(p.getX(), y, p.getZ());
            set(level, q, Blocks.AIR.defaultBlockState());
            markDirty(level, q);
        }
    }

    // ─── 阶段 4：补壁 + 灌汁 ───
    void lakeFill(ServerLevel level, BiomeCoreBlockEntity be, BlockPos p) {
        if (withinFreeRadius(be, p)) return;
        if (!isLakePos(p)) return;
        long col = ((long) p.getX() << 32) | (p.getZ() & 0xFFFFFFFFL);
        Integer topY = lakeTop.get(col);
        if (topY == null) return;
        if (p.getY() != topY - 1) return;  // 每列只在锚点 y 触发一次

        BlockState peel = BlockRegister.LANTERN_BERRY_PEEL.get().defaultBlockState();
        BlockState juice = FluidRegister.LANTERN_BERRY_JUICE_STILL.get()
                .defaultFluidState().createLegacyBlock();

        // 湖底
        BlockPos bottom = new BlockPos(p.getX(), topY - LAKE_DEPTH - 1, p.getZ());
        set(level, bottom, peel);
        markDirty(level, bottom);

        // 侧壁
        for (int y = topY - 1; y >= topY - LAKE_DEPTH; y--) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = new BlockPos(p.getX(), y, p.getZ()).relative(d);
                if (isLakePos(n)) continue;
                BlockState ns = level.getBlockState(n);
                if (ns.isAir() || !ns.getFluidState().isEmpty()) continue;
                set(level, n, peel);
                markDirty(level, n);
            }
        }

        // 灌汁：底壁已就位，此刻灌 source 方块
        for (int y = topY - 1; y >= topY - LAKE_DEPTH; y--) {
            BlockPos q = new BlockPos(p.getX(), y, p.getZ());
            set(level, q, juice);
            markDirty(level, q);
        }
    }

    // ─── 阶段 5：植被 ───
    void plant(ServerLevel level, BiomeCoreBlockEntity be, BlockPos p) {
        if (withinFreeRadius(be, p)) return;
        if (!isMossOrSunberry(level, p)) return;
        BlockState s = level.getBlockState(p);
        if (!s.is(BlockRegister.LANTERN_BERRY_PEEL.get())) return;

        BlockPos above = p.above();
        if (!level.getBlockState(above).isAir()) return;
        if (!level.canSeeSky(above)) return;

        RandomSource random = level.random;
        if (random.nextInt(SUN_BERRY_CHANCE) == 0) {
            if (SunBerry.tryPlaceSunBerry(level, above.above())) {
                markDirty(level, above);
                return;
            }
        }
        if (random.nextInt(LANTERN_BERRY_CHANCE) == 0) {
            set(level, above, BlockRegister.LANTERN_BERRY.get().defaultBlockState());
            markDirty(level, above);
        }
    }

    // ─── 公共 ───
    static boolean isLakePos(BlockPos p) {
        int lx = Math.floorMod(p.getX(), LAKE_TILE) - LAKE_TILE / 2;
        int lz = Math.floorMod(p.getZ(), LAKE_TILE) - LAKE_TILE / 2;
        if (Math.abs(lx) > LAKE_INNER / 2 || Math.abs(lz) > LAKE_INNER / 2) return false;

        int tx = Math.floorDiv(p.getX(), LAKE_TILE);
        int tz = Math.floorDiv(p.getZ(), LAKE_TILE);
        long seed = (long) tx * 0x9E3779B97F4A7C15L ^ (long) tz * 0xC2B2AE3D27D4EB4FL;
        seed ^= (seed >>> 33);
        seed *= 0xFF51AFD7ED558CCDL;
        seed ^= (seed >>> 33);
        return (seed & 3L) == 0;
    }

    static boolean isMossOrSunberry(ServerLevel level, BlockPos p) {
        Holder<Biome> biome = level.getBiome(p);
        return biome.is(SOURCE) || biome.is(TARGET);
    }
}