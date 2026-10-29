package com.thelema.thrive.generic.blockentity.modifier;

import com.thelema.thrive.generic.block.SunBerry;
import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import com.thelema.thrive.register.BiomeRegister;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 苔藓 → 太阳浆果。
 * 四阶段错位：
 *   ring-1 清理树、杜鹃、花草、苔藓、藤蔓
 *   ring-3 地表往下 4 格 + 侧面暴露的泥土类换果皮；清水
 *   ring-5 挖湖洞 + 补湖底/侧壁（果皮）
 *   ring-7 灌果汁
 *   ring-8 随机放太阳/灯笼浆果
 */
public class MossToSunBerry implements BiomeModifier {

    static final int TICKS_PER_RING = 20;
    static final int MAX_RING = 64;

    static final int CLEAR_DELAY = 1;
    static final int SURFACE_DELAY = 3;
    static final int LAKE_DIG_DELAY = 5;
    static final int LAKE_FILL_DELAY = 7;
    static final int PLANT_DELAY = 8;

    static final int TREE_FREE_RADIUS = 7;

    static final ResourceKey<Biome> SOURCE = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath("thrive", "moss"));
    static final ResourceKey<Biome> TARGET = BiomeRegister.SUNBERRY;

    static final ResourceLocation TREE_SUNBERRY =
            ResourceLocation.fromNamespaceAndPath("thrive", "biome_core_tree_sunberry");

    // 湖
    static final int LAKE_TILE = 32;
    static final int LAKE_INNER = 20;
    static final int LAKE_DEPTH = 3;

    // 植被
    static final int SUN_BERRY_CHANCE = 512;
    static final int LANTERN_BERRY_CHANCE = 64;

    /** 每列的地表高度。挖湖时用，避免挖完后重新查 heightmap 失真。 */
    private final Map<Long, Integer> lakeTop = new HashMap<>();

    @Override
    public boolean canUse(ServerLevel level, BiomeCoreBlockEntity be) {
        return level.getBiome(be.getBlockPos()).is(SOURCE);
    }

    @Override
    public void tick(ServerLevel level, BiomeCoreBlockEntity be) {
        long tick = tick(be);
        int ring = (int) (tick / TICKS_PER_RING);

        if (tick % TICKS_PER_RING == 0) {
            if (ring <= MAX_RING) rewriteBiome(level, be, ring, SOURCE, TARGET);
            if (ring >= CLEAR_DELAY && ring - CLEAR_DELAY <= MAX_RING)
                processClear(level, be, ring - CLEAR_DELAY);
            if (ring >= SURFACE_DELAY && ring - SURFACE_DELAY <= MAX_RING)
                processSurface(level, be, ring - SURFACE_DELAY);
            if (ring >= LAKE_DIG_DELAY && ring - LAKE_DIG_DELAY <= MAX_RING)
                processLakeDig(level, be, ring - LAKE_DIG_DELAY);
            if (ring >= LAKE_FILL_DELAY && ring - LAKE_FILL_DELAY <= MAX_RING)
                processLakeFill(level, be, ring - LAKE_FILL_DELAY);
            if (ring >= PLANT_DELAY && ring - PLANT_DELAY <= MAX_RING)
                processPlant(level, be, ring - PLANT_DELAY);
        }
        if (tick % 10 == 0) flushDirty(level, be);

        if (ring >= MAX_RING + PLANT_DELAY + 1) {
            flushDirty(level, be);
            be.resetConversion();
            // replaceStructure(level, be, TREE_SUNBERRY);
            return;
        }
        advanceTick(be);
    }

    // ─────────────────── 阶段 1：清理 ───────────────────

    void processClear(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        BlockState air = Blocks.AIR.defaultBlockState();

        forRing(level, be, r, (p, s) -> {
            if (s.isAir()) return;
            if (!isMossOrSunberry(level, p)) return;

            // 两种橡树：原木 + 六面树皮变体 + 树叶
            boolean oak = s.is(Blocks.OAK_LOG) || s.is(Blocks.OAK_WOOD)
                    || s.is(Blocks.DARK_OAK_LOG) || s.is(Blocks.DARK_OAK_WOOD)
                    || s.is(Blocks.OAK_LEAVES) || s.is(Blocks.DARK_OAK_LEAVES);

            // 杜鹃树
            boolean azalea = s.is(Blocks.AZALEA_LEAVES) || s.is(Blocks.FLOWERING_AZALEA_LEAVES)
                    || s.is(Blocks.AZALEA) || s.is(Blocks.FLOWERING_AZALEA);

            // 植物：树苗、花、草、蕨、苔藓地毯、洞穴藤蔓、灯笼浆果
            boolean plant = s.is(BlockTags.SAPLINGS)
                    || s.is(BlockTags.FLOWERS)
                    || s.is(BlockTags.REPLACEABLE_BY_TREES)
                    || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS)
                    || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN)
                    || s.is(Blocks.MOSS_CARPET)
                    || s.is(Blocks.CAVE_VINES) || s.is(Blocks.CAVE_VINES_PLANT)
                    || s.is(BlockRegister.LANTERN_BERRY.get());

            if (!oak && !azalea && !plant) return;

            set(level, p, air);
            markDirty(be, p);
        });
    }

    // ─────────────────── 阶段 2：地表 ───────────────────

    /** 清水；泥土类：地表往下 4 格内 或 侧面暴露 → 换果皮。 */
    void processSurface(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        BlockState peel = BlockRegister.LANTERN_BERRY_PEEL.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        forRing(level, be, r, (p, s) -> {
            if (!isMossOrSunberry(level, p)) return;

            // 清液体
            if (!s.getFluidState().isEmpty()) {
                set(level, p, air);
                markDirty(be, p);
                return;
            }

            // 只换泥土类
            if (!s.is(Blocks.GRASS_BLOCK) && !s.is(BlockTags.DIRT) && !s.is(Blocks.MOSS_BLOCK)) return;

            // 侧面暴露
            boolean sideExposed = false;
            for (Direction d : Direction.values()) {
                if (level.getBlockState(p.relative(d)).isAir()) {
                    sideExposed = true;
                    break;
                }
            }

            // 距地表 4 格内（含地表方块，往下 4 格）
            int topY = level.getHeightmapPos(Heightmap.Types.OCEAN_FLOOR, p).getY();
            boolean nearSurface = p.getY() >= topY - 4 && p.getY() <= topY;

            if (!sideExposed && !nearSurface) return;

            set(level, p, peel);
            markDirty(be, p);
        });
    }

    // ─────────────────── 阶段 3：湖泊 ───────────────────

    /** 收集本圈的湖列，记录每列原始 topY 并挖洞。 */
    void processLakeDig(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        if (r <= TREE_FREE_RADIUS) return;

        BlockState air = Blocks.AIR.defaultBlockState();

        Set<Long> cols = collectLakeColumns(level, be, r);

        for (long col : cols) {
            int x = (int) (col >> 32);
            int z = (int) col;
            int topY = level.getHeightmapPos(Heightmap.Types.OCEAN_FLOOR, new BlockPos(x, 0, z)).getY();
            lakeTop.put(col, topY);

            // 挖洞
            for (int y = topY - 1; y >= topY - LAKE_DEPTH; y--) {
                BlockPos q = new BlockPos(x, y, z);
                set(level, q, air);
                markDirty(be, q);
            }
        }
    }

    /** 用记录的 topY 补湖底和侧壁（果皮），不重新查 heightmap。 */
    void processLakeFill(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        if (r <= TREE_FREE_RADIUS) return;

        BlockState peel = BlockRegister.LANTERN_BERRY_PEEL.get().defaultBlockState();
        BlockState juice = FluidRegister.LANTERN_BERRY_JUICE_STILL.get()
                .defaultFluidState().createLegacyBlock();   // 提到循环外，只建一次

        Set<Long> cols = collectLakeColumns(level, be, r);

        for (long col : cols) {
            Integer topY = lakeTop.get(col);
            if (topY == null) continue;
            int x = (int) (col >> 32);
            int z = (int) col;

            // 湖底
            BlockPos bottom = new BlockPos(x, topY - LAKE_DEPTH - 1, z);
            set(level, bottom, peel);
            markDirty(be, bottom);

            // 侧壁
            for (int y = topY - 1; y >= topY - LAKE_DEPTH; y--) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos n = new BlockPos(x, y, z).relative(d);
                    if (isLakePos(n)) continue;
                    BlockState ns = level.getBlockState(n);
                    if (ns.isAir() || !ns.getFluidState().isEmpty()) continue;
                    set(level, n, peel);
                    markDirty(be, n);
                }
            }

            // ★ 灌果汁：底和侧壁都已就位，此刻灌 source 方块
            for (int y = topY - 1; y >= topY - LAKE_DEPTH; y--) {
                BlockPos q = new BlockPos(x, y, z);
                set(level, q, juice);
                markDirty(be, q);
            }
        }
    }

    // ─────────────────── 阶段 4：植被 ───────────────────

    void processPlant(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        if (r <= TREE_FREE_RADIUS) return;

        BlockState lantern = BlockRegister.LANTERN_BERRY.get().defaultBlockState();
        RandomSource random = level.random;

        forRing(level, be, r, (p, s) -> {
            if (!isMossOrSunberry(level, p)) return;
            if (!s.is(BlockRegister.LANTERN_BERRY_PEEL.get())) return;

            BlockPos above = p.above();
            if (!level.getBlockState(above).isAir()) return;
            if (!level.canSeeSky(above)) return;

            // 太阳浆果：稀缺，3×3×3，core = above.above()
            if (random.nextInt(SUN_BERRY_CHANCE) == 0) {
                if (SunBerry.tryPlaceSunBerry(level, above.above())) {
                    markDirty(be, above);
                    return;
                }
            }

            // 灯笼浆果：常见单方块
            if (random.nextInt(LANTERN_BERRY_CHANCE) == 0) {
                set(level, above, lantern);
                markDirty(be, above);
            }
        });
    }

    // ─────────────────── 公共 ───────────────────

    /** 收集本圈的湖列，按 (x, z) 去重。 */
    private Set<Long> collectLakeColumns(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        Set<Long> cols = new HashSet<>();
        forRing(level, be, r, (p, s) -> {
            if (!isLakePos(p)) return;
            if (!isMossOrSunberry(level, p)) return;
            cols.add(((long) p.getX() << 32) | (p.getZ() & 0xFFFFFFFFL));
        });
        return cols;
    }

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