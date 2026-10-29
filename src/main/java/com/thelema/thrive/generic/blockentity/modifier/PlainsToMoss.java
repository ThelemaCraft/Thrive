package com.thelema.thrive.generic.blockentity.modifier;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import com.thelema.thrive.generic.feature.Lake;
import com.thelema.thrive.register.BlockRegister;
import com.thelema.thrive.register.FluidRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.Set;

public class PlainsToMoss implements BiomeModifier {

    static final int TICKS_PER_RING = 20;   // 1 秒 1 环
    static final int MAX_RING = 64;         // 4 chunk 半径

    static final ResourceKey<Biome> SOURCE = ResourceKey.create(
            Registries.BIOME, ResourceLocation.withDefaultNamespace("plains"));
    static final ResourceKey<Biome> TARGET = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath("thrive", "moss"));

    static final ResourceLocation TREE_MOSS =
            ResourceLocation.fromNamespaceAndPath("thrive", "biome_core_tree_moss");

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

    /** moss_vegetation 权重表：苔藓地毯 25、短草 50、高草 10、杜鹃 7、开花杜鹃 4。 */
    static final Block[] VEGETATION = {
            Blocks.MOSS_CARPET, Blocks.MOSS_CARPET, Blocks.MOSS_CARPET, Blocks.MOSS_CARPET,
            Blocks.MOSS_CARPET, Blocks.MOSS_CARPET, Blocks.MOSS_CARPET, Blocks.MOSS_CARPET,
            Blocks.MOSS_CARPET, Blocks.MOSS_CARPET, Blocks.MOSS_CARPET, Blocks.MOSS_CARPET,
            Blocks.MOSS_CARPET, Blocks.MOSS_CARPET, Blocks.MOSS_CARPET, Blocks.MOSS_CARPET,
            Blocks.MOSS_CARPET, Blocks.MOSS_CARPET, Blocks.MOSS_CARPET, Blocks.MOSS_CARPET,
            Blocks.MOSS_CARPET, Blocks.MOSS_CARPET, Blocks.MOSS_CARPET, Blocks.MOSS_CARPET,
            Blocks.MOSS_CARPET,
            Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS,
            Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS,
            Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS,
            Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS,
            Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS,
            Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS,
            Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS,
            Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS,
            Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS,
            Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS,
            Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS,
            Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS,
            Blocks.SHORT_GRASS, Blocks.SHORT_GRASS,
            Blocks.TALL_GRASS, Blocks.TALL_GRASS, Blocks.TALL_GRASS, Blocks.TALL_GRASS,
            Blocks.TALL_GRASS, Blocks.TALL_GRASS, Blocks.TALL_GRASS, Blocks.TALL_GRASS,
            Blocks.TALL_GRASS, Blocks.TALL_GRASS,
            Blocks.AZALEA, Blocks.AZALEA, Blocks.AZALEA, Blocks.AZALEA,
            Blocks.AZALEA, Blocks.AZALEA, Blocks.AZALEA,
            Blocks.FLOWERING_AZALEA, Blocks.FLOWERING_AZALEA,
            Blocks.FLOWERING_AZALEA, Blocks.FLOWERING_AZALEA
    };

    @Override
    public boolean canUse(ServerLevel level, BiomeCoreBlockEntity be) {
        return level.getBiome(be.getBlockPos()).is(SOURCE);
    }

    @Override
    public void tick(ServerLevel level, BiomeCoreBlockEntity be) {
        long tick = tick(be);
        int ring = (int) (tick / TICKS_PER_RING);

        if (tick % TICKS_PER_RING == 0) {
            rewriteBiome(level, be, Math.min(ring, MAX_RING), SOURCE, TARGET);
            if (ring >= 1) processSurface(level, be, Math.min(ring - 1, MAX_RING));
            if (ring >= 2) processLake(level, be, Math.min(ring - 2, MAX_RING));
            if (ring >= 3) processDeco(level, be, Math.min(ring - 3, MAX_RING));
        }

        if (tick % 10 == 0) flushDirty(level, be);

        if (ring >= MAX_RING + 3) {
            be.resetConversion();
            replaceStructure(level, be, TREE_MOSS);
            flushDirty(level, be);
            return;
        }

        advanceTick(be);
    }

    // ─────────────────── 阶段 2：表面 ───────────────────

    void processSurface(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        BlockState moss = Blocks.MOSS_BLOCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        forRing(level, be, r, (p, s) -> {
            if (!level.getBiome(p).is(TARGET)) return;
            if (s.isAir()) return;

            if (REMOVE.contains(s.getBlock())) {
                set(level, p, air);
                markDirty(be, p);
                return;
            }
            if (!REPLACEABLE.contains(s.getBlock())) return;

            if (level.getBlockState(p.above()).isAir()
                    || level.getBlockState(p.below()).isAir()) {
                set(level, p, moss);
                markDirty(be, p);
            }
        });
    }

    // ─────────────────── 阶段 3：湖泊 ───────────────────

    void processLake(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        RandomSource random = level.random;
        if (random.nextInt(4) != 0) return;

        BlockPos c = be.getBlockPos();
        int minY = be.minY(), maxY = be.maxY();

        int side = r * 2 + 1;
        int pick = random.nextInt(side * 4);
        int dx, dz;
        if (pick < side)                  { dx = -r;              dz = -r + pick; }
        else if (pick < side * 2)         { dx = -r + (pick - side);   dz = r; }
        else if (pick < side * 3)         { dx = r;               dz = r - (pick - side * 2); }
        else                              { dx = r - (pick - side * 3); dz = -r; }

        int x = c.getX() + dx;
        int z = c.getZ() + dz;

        BlockPos surface = null;
        for (int y = maxY; y >= minY; y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (level.getBlockState(p).is(Blocks.MOSS_BLOCK)
                    && level.getBlockState(p.above()).isAir()) {
                surface = p.above();
                break;
            }
        }
        if (surface == null) return;

        Lake.placeAt(level, surface, random,
                FluidRegister.LANTERN_BERRY_JUICE_BLOCK.get().defaultBlockState(),
                Blocks.MOSS_BLOCK.defaultBlockState());
    }

    // ─────────────────── 阶段 4：装饰 ───────────────────

    void processDeco(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        RandomSource random = level.random;

        forRing(level, be, r, (p, s) -> {
            if (!level.getBiome(p).is(TARGET)) return;
            if (!s.isAir()) return;

            BlockState above = level.getBlockState(p.above());
            BlockState below = level.getBlockState(p.below());

            if (below.is(Blocks.MOSS_BLOCK)) {
                if (random.nextInt(72) == 0) placeTree(level, random, p);
                else placeVegetation(level, random, be, p);
            } else if (above.is(Blocks.MOSS_BLOCK)) {
                if (random.nextInt(5) == 0) placeCaveVine(level, random, be, p);
            }
        });
    }

    void placeTree(ServerLevel level, RandomSource random, BlockPos p) {
        var feature = level.registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(ResourceKey.create(Registries.CONFIGURED_FEATURE,
                        ResourceLocation.withDefaultNamespace("azalea_tree")));
        if (feature == null) return;
        feature.place(level, level.getChunkSource().getGenerator(), random, p);
    }

    void placeVegetation(ServerLevel level, RandomSource random, BiomeCoreBlockEntity be, BlockPos p) {
        Block veg = VEGETATION[random.nextInt(VEGETATION.length)];

        if (veg == Blocks.TALL_GRASS) {
            BlockPos above = p.above();
            if (!level.getBlockState(above).isAir()) return;
            set(level, p, Blocks.TALL_GRASS.defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
            set(level, above, Blocks.TALL_GRASS.defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            markDirty(be, p);
            return;
        }
        set(level, p, veg.defaultBlockState());
        markDirty(be, p);
    }

    void placeCaveVine(ServerLevel level, RandomSource random, BiomeCoreBlockEntity be, BlockPos ceiling) {
        int length = 1 + random.nextInt(8);
        boolean berries = random.nextInt(5) == 0;
        BlockPos last = ceiling;

        for (int i = 0; i < length; i++) {
            BlockPos pos = ceiling.below(i);
            if (!level.getBlockState(pos).isAir()) break;
            BlockState vine = (i == 0 ? Blocks.CAVE_VINES_PLANT : Blocks.CAVE_VINES)
                    .defaultBlockState()
                    .setValue(CaveVines.BERRIES, berries);
            set(level, pos, vine);
            markDirty(be, pos);
            last = pos;
        }

        if (random.nextInt(3) == 0) {
            BlockPos below = last.below();
            if (level.getBlockState(below).isAir()) {
                set(level, below, BlockRegister.LANTERN_BERRY.get().defaultBlockState());
                markDirty(be, below);
            }
        }
    }
}