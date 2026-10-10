package com.thelema.thrive.generic.blockentity.convertor;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import com.thelema.thrive.generic.world.feature.Lake;
import com.thelema.thrive.register.BlockRegister;
import com.thelema.thrive.register.FluidRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PlainsToMossConvertor implements Convertor {

    static final int TICKS_PER_RING = 20;   // 1 秒 1 环
    static final int MAX_RING = 64;         // 4 chunk 半径

    static final ResourceKey<Biome> SOURCE = ResourceKey.create(
            Registries.BIOME, ResourceLocation.withDefaultNamespace("plains"));
    static final ResourceKey<Biome> TARGET = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath("thrive", "moss"));

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
    public void tick(ServerLevel level, BiomeCoreBlockEntity be) {
        long tick = be.convertTick;
        int ring = (int) (tick / TICKS_PER_RING);

        // 每环只处理一次
        if (tick % TICKS_PER_RING == 0) {
            processBiome(level, be, Math.min(ring, MAX_RING));
            if (ring >= 1) processSurface(level, be, Math.min(ring - 1, MAX_RING));
            if (ring >= 2) processLake(level, be, Math.min(ring - 2, MAX_RING));
            if (ring >= 3) processDeco(level, be, Math.min(ring - 3, MAX_RING));
        }

        if (tick % 10 == 0) flushDirty(level, be);

        if (ring >= MAX_RING + 3) {
            flushDirty(level, be);
            be.rotator.clear();
            be.convertTick = 0;
            be.activeConvertor = null;
            be.activeSource = null;
            return;
        }

        be.convertTick++;
    }

    /** 该位置是否已经是目标群系。本转换自己的判断，框架不管群系。 */
    boolean isTarget(ServerLevel level, BlockPos p) {
        return level.getBiome(p).unwrapKey().map(k -> k.equals(TARGET)).orElse(false);
    }

    // ─────────────────── 阶段 1：群系 ───────────────────

    void processBiome(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        Holder<Biome> targetBiome = level.registryAccess()
                .lookupOrThrow(Registries.BIOME).getOrThrow(TARGET);

        // (chunk, section) → 需要改的单元下标集合
        Map<Long, Map<Integer, Set<Integer>>> touched = new HashMap<>();

        forRing(level, be, r, (p, s) -> {
            long ck = ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
            int si = (p.getY() - level.getMinBuildHeight()) >> 4;
            int j = (p.getX() & 15) >> 2;
            int k = (p.getY() & 15) >> 2;
            int l = (p.getZ() & 15) >> 2;
            int unitIdx = (j * 4 + k) * 4 + l;

            touched.computeIfAbsent(ck, x -> new HashMap<>())
                   .computeIfAbsent(si, x -> new HashSet<>())
                   .add(unitIdx);

            markDirty(be, p);
        });

        for (var ce : touched.entrySet()) {
            ChunkPos cp = new ChunkPos(ce.getKey());
            ChunkAccess chunk = level.getChunk(cp.x, cp.z, ChunkStatus.FULL, false);
            if (chunk == null) continue;
            for (var se : ce.getValue().entrySet()) {
                rewriteSection(level, chunk, se.getKey(), se.getValue(), targetBiome);
            }
        }
    }

    /** 把 section 里 units 指定位置的群系改成 target，其余保留旧值。 */
    void rewriteSection(ServerLevel level, ChunkAccess chunk, int sectionIndex,
                        Set<Integer> units, Holder<Biome> target) {
        LevelChunkSection section = chunk.getSection(sectionIndex);
        var old = section.getBiomes();

        // 缓存旧的 64 个单元（4×4×4）
        Holder<Biome>[] cache = new Holder[64];
        for (int j = 0; j < 4; j++)
            for (int k = 0; k < 4; k++)
                for (int l = 0; l < 4; l++)
                    cache[(j * 4 + k) * 4 + l] = old.get(j, k, l);

        int qx = chunk.getPos().getMinBlockX() >> 2;
        int qy = SectionPos.sectionToBlockCoord(
                chunk.getSectionYFromSectionIndex(sectionIndex)) >> 2;
        int qz = chunk.getPos().getMinBlockZ() >> 2;

        var sampler = level.getChunkSource().randomState().sampler();

        section.fillBiomesFromNoise((x, y, z, s) -> {
            int j = x - qx, k = y - qy, l = z - qz;
            if (j < 0 || j >= 4 || k < 0 || k >= 4 || l < 0 || l >= 4) return target;
            int idx = (j * 4 + k) * 4 + l;
            if (!units.contains(idx)) return cache[idx];
            Holder<Biome> prev = cache[idx];
            return prev.unwrapKey().map(key -> key.equals(SOURCE)).orElse(false)
                    ? target : prev;
        }, sampler, qx, qy, qz);

        chunk.setUnsaved(true);
    }

    // ─────────────────── 阶段 2：表面 ───────────────────

    void processSurface(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        BlockState moss = Blocks.MOSS_BLOCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        forRing(level, be, r, (p, s) -> {
            if (!isTarget(level, p)) return;
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

    /** 每环 1/4 概率在环上随机放一个湖。 */
    void processLake(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        RandomSource random = level.random;
        if (random.nextInt(4) != 0) return;

        BlockPos c = be.getBlockPos();
        int minY = be.minY(), maxY = be.maxY();

        // 在环上随机选一个 (dx, dz)
        int side = r * 2 + 1;
        int pick = random.nextInt(side * 4);
        int dx, dz;
        if (pick < side)                  { dx = -r;              dz = -r + pick; }
        else if (pick < side * 2)         { dx = -r + (pick - side);   dz = r; }
        else if (pick < side * 3)         { dx = r;               dz = r - (pick - side * 2); }
        else                              { dx = r - (pick - side * 3); dz = -r; }

        int x = c.getX() + dx;
        int z = c.getZ() + dz;

        // 从 maxY 往下找第一个苔藓块表面
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
            if (!isTarget(level, p)) return;
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

    /** 从天花板往下挂藤蔓；尾部 1/3 概率再挂一个灯笼浆果。 */
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

    // ─────────────────── 重发 ───────────────────

    void flushDirty(ServerLevel level, BiomeCoreBlockEntity be) {
        if (be.dirty.isEmpty()) return;
        for (long key : be.dirty) {
            ChunkPos cp = new ChunkPos(key);
            ChunkAccess chunk = level.getChunk(cp.x, cp.z, ChunkStatus.FULL, false);
            if (!(chunk instanceof LevelChunk levelChunk)) continue;
            var players = level.getChunkSource().chunkMap.getPlayers(cp, false);
            for (ServerPlayer player : players) {
                player.connection.send(new ClientboundLevelChunkWithLightPacket(
                        levelChunk, level.getLightEngine(), null, null));
            }
        }
        be.dirty.clear();
    }
}