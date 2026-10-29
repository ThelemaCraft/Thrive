package com.thelema.thrive.generic.blockentity.modifier;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * 一条群系转换路径的完整逻辑。
 * <p>
 * BE 只负责调度（按 id 找到本转换器、调用 {@link #tick}），
 * 具体逻辑——改群系、换方块、收尾替换结构——都在这里。
 * <p>
 * 转换按切比雪夫环（{@link #forRing}）逐圈推进。子类负责错位多个阶段
 * （清理 / 造景 / 骨粉……），避免后一圈覆盖前一圈的产物。
 */
public interface BiomeModifier {

    // ============================================================
    // 生命周期
    // ============================================================

    /** 能否开始运行。BE 在每次准备触发转换时调用。 */
    boolean canUse(ServerLevel level, BiomeCoreBlockEntity be);

    /** 每 tick 调一次。子类通常按 {@code tick / TICKS_PER_RING} 算当前圈号。 */
    void tick(ServerLevel level, BiomeCoreBlockEntity be);

    /** 读本转换已运行的 tick 数。存 data，读档续跑。 */
    default long tick(BiomeCoreBlockEntity be) { return be.data.getLong("tick"); }

    /** 本 tick 结束前调用，把 tick 数 +1。 */
    default void advanceTick(BiomeCoreBlockEntity be) {
        be.data.putLong("tick", be.data.getLong("tick") + 1);
    }

    // ============================================================
    // 遍历
    // ============================================================

    /**
     * 遍历第 r 环（切比雪夫壳），y 覆盖 {@code [be.minY(), be.maxY()]}。
     * <p>
     * 环上共 8r 点（r=0 时 1 点），按上下两条边 + 左右两条边枚举，
     * 避免 `(2r+1)²` 全扫再过滤。
     */
    default void forRing(ServerLevel level, BiomeCoreBlockEntity be, int r,
                         BiConsumer<BlockPos, BlockState> action) {
        if (r < 0) return;
        BlockPos c = be.getBlockPos();
        int minY = be.minY(), maxY = be.maxY();

        if (r == 0) {
            for (int y = minY; y <= maxY; y++) {
                BlockPos p = new BlockPos(c.getX(), y, c.getZ());
                action.accept(p, level.getBlockState(p));
            }
            return;
        }

        // 上下两条边（含四个角）
        for (int dx = -r; dx <= r; dx++) {
            int x = c.getX() + dx;
            for (int y = minY; y <= maxY; y++) {
                BlockPos pTop = new BlockPos(x, y, c.getZ() - r);
                action.accept(pTop, level.getBlockState(pTop));
                BlockPos pBot = new BlockPos(x, y, c.getZ() + r);
                action.accept(pBot, level.getBlockState(pBot));
            }
        }
        // 左右两条边（不含角，避免重复）
        for (int dz = -r + 1; dz <= r - 1; dz++) {
            int z = c.getZ() + dz;
            for (int y = minY; y <= maxY; y++) {
                BlockPos pLeft = new BlockPos(c.getX() - r, y, z);
                action.accept(pLeft, level.getBlockState(pLeft));
                BlockPos pRight = new BlockPos(c.getX() + r, y, z);
                action.accept(pRight, level.getBlockState(pRight));
            }
        }
    }

    // ============================================================
    // 方块操作
    // ============================================================

    /**
     * setBlock + 光照刷新。
     * <p>
     * flags = 3：{@code UPDATE_NEIGHBORS | UPDATE_CLIENTS}，触发邻居更新并发送给客户端。
     */
    default void set(ServerLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, 3);
        level.getLightEngine().checkBlock(p);
    }

    /** 标记该方块所在 chunk 需要重发，下个 {@link #flushDirty} 时统一发。 */
    default void markDirty(BiomeCoreBlockEntity be, BlockPos p) {
        be.dirty.add(chunkKey(p));
    }

    /** {@link BlockPos} → chunk 长整数 key。 */
    private static long chunkKey(BlockPos p) {
        return ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
    }

    // ============================================================
    // 群系改写
    // ============================================================

    /**
     * 把第 r 环涉及的群系单元从 {@code source} 改成 {@code target}。
     * <p>
     * 只替换当前群系等于 {@code source} 的单元，用于"源群系已知"的场景。
     */
    default void rewriteBiome(ServerLevel level, BiomeCoreBlockEntity be, int r,
                              ResourceKey<Biome> source, ResourceKey<Biome> target) {
        doRewriteBiome(level, be, r, target,
                prev -> prev.unwrapKey().map(k -> k.equals(source)).orElse(false));
    }

    /**
     * 把第 r 环涉及的群系单元无条件改成 {@code target}。
     * <p>
     * 用于"源群系任意"的场景（如通用 Garden）。
     */
    default void rewriteBiomeAll(ServerLevel level, BiomeCoreBlockEntity be, int r,
                                 ResourceKey<Biome> target) {
        doRewriteBiome(level, be, r, target, prev -> true);
    }

    /**
     * 群系改写核心实现。
     * <p>
     * 分两步：
     * <ol>
     *   <li>扫一遍环上的方块，按 (chunk, section, 4×4×4 unit) 三级归类到 {@code touched}；</li>
     *   <li>逐 (chunk, section) 调 {@link #doRewriteSection}，用缓存保留未命中单元。</li>
     * </ol>
     * 归类是为了把同 section 的改写合并成一次 {@code fillBiomesFromNoise}，
     * 否则每个 unit 单独写代价太高。
     *
     * @param shouldReplace 判断"这个旧单元该不该被换成 target"
     */
    private void doRewriteBiome(ServerLevel level, BiomeCoreBlockEntity be, int r,
                                ResourceKey<Biome> target,
                                Predicate<Holder<Biome>> shouldReplace) {
        Holder<Biome> targetBiome = level.registryAccess()
                .lookupOrThrow(Registries.BIOME).getOrThrow(target);

        Map<Long, Map<Integer, Set<Integer>>> touched = new HashMap<>();
        forRing(level, be, r, (p, s) -> {
            long ck = chunkKey(p);
            int si = (p.getY() - level.getMinBuildHeight()) >> 4;
            int j = (p.getX() & 15) >> 2;
            int k = (p.getY() & 15) >> 2;
            int l = (p.getZ() & 15) >> 2;
            touched.computeIfAbsent(ck, x -> new HashMap<>())
                   .computeIfAbsent(si, x -> new HashSet<>())
                   .add((j * 4 + k) * 4 + l);
            markDirty(be, p);
        });

        for (var ce : touched.entrySet()) {
            ChunkPos cp = new ChunkPos(ce.getKey());
            // 第 4 参 false：不触发同步加载，未加载的 chunk 直接跳过
            ChunkAccess chunk = level.getChunk(cp.x, cp.z, ChunkStatus.FULL, false);
            if (chunk == null) continue;
            for (var se : ce.getValue().entrySet()) {
                doRewriteSection(level, chunk, se.getKey(), se.getValue(),
                        targetBiome, shouldReplace);
            }
        }
    }

    /**
     * section 级群系改写。
     * <p>
     * 一个 section 有 4×4×4 = 64 个 4-bit unit，{@code fillBiomesFromNoise} 是全量填充，
     * 所以先用 {@code cache} 抄出旧值，回调里对不在 {@code units} 中的单元返回缓存。
     *
     * @param units 命中的单元索引，编码 {@code (j*4 + k)*4 + l}
     */
    private void doRewriteSection(ServerLevel level, ChunkAccess chunk, int sectionIndex,
                                  Set<Integer> units, Holder<Biome> target,
                                  Predicate<Holder<Biome>> shouldReplace) {
        LevelChunkSection section = chunk.getSection(sectionIndex);
        var old = section.getBiomes();

        Holder<Biome>[] cache = new Holder[64];
        for (int j = 0; j < 4; j++)
            for (int k = 0; k < 4; k++)
                for (int l = 0; l < 4; l++)
                    cache[(j * 4 + k) * 4 + l] = old.get(j, k, l);

        // 本 section 在 4×4×4 unit 坐标系下的原点
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
            return shouldReplace.test(cache[idx]) ? target : cache[idx];
        }, sampler, qx, qy, qz);

        chunk.setUnsaved(true);
    }

    // ============================================================
    // 网络同步
    // ============================================================

    /** 重发所有标记过的 chunk。未加载的 chunk 跳过（玩家看到旧数据也无所谓）。 */
    default void flushDirty(ServerLevel level, BiomeCoreBlockEntity be) {
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

    // ============================================================
    // 结构替换
    // ============================================================

    /** 从模板里找 BiomeCore 的相对坐标。找不到返回 null。 */
    default BlockPos findCoreOffset(StructureTemplate template) {
        List<StructureTemplate.StructureBlockInfo> list = template.filterBlocks(
                BlockPos.ZERO,
                new StructurePlaceSettings(),
                BlockRegister.BIOME_CORE.get());
        return list.isEmpty() ? null : list.get(0).pos();
    }

    /**
     * 用 {@code toId} 结构替换当前 BE 所在位置的结构，两者核心方块对齐。
     * <p>
     * 步骤：
     * <ol>
     *   <li>用当前 BE 的坐标反推旧结构起点；</li>
     *   <li>保存 surface 数据，清空物品——避免 placeInWorld 覆盖时触发 onRemove 掉落；</li>
     *   <li>placeInWorld 放置新结构；</li>
     *   <li>把 surface 数据恢复到新的 BE；</li>
     *   <li>刷新光照 + 标记 chunk 重发。</li>
     * </ol>
     */
    default void replaceStructure(ServerLevel level, BiomeCoreBlockEntity be,
                                  ResourceLocation toId) {
        StructureTemplateManager manager = level.getStructureManager();
        StructureTemplate to = manager.get(toId).orElse(null);
        if (to == null) return;

        BlockPos corePos = be.getBlockPos();
        BlockPos toCore = findCoreOffset(to);
        if (toCore == null) return;
        BlockPos toOrigin = corePos.subtract(toCore);

        // 保存旧数据，随后清空物品
        CompoundTag surfaceNbt = new CompoundTag();
        be.surface.save(surfaceNbt, level.registryAccess());
        be.surface.item = null;
        be.rotator.item = ItemStack.EMPTY;

        // flags = 50 = UPDATE_ALL_SUPPRESS_DROPS（全量更新 + 不触发掉落）
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false);
        to.placeInWorld(level, toOrigin, toOrigin, settings, level.random, 50);

        // 数据恢复到新 BE
        if (level.getBlockEntity(corePos) instanceof BiomeCoreBlockEntity newBe) {
            newBe.surface.load(surfaceNbt, level.registryAccess());
            newBe.setChanged();
        }

        // 刷新光照 + 标脏
        Vec3i toSize = to.getSize();
        BlockPos toEnd = toOrigin.offset(toSize.getX() - 1, toSize.getY() - 1, toSize.getZ() - 1);
        for (BlockPos p : BlockPos.betweenClosed(toOrigin, toEnd)) {
            level.getLightEngine().checkBlock(p);
            markDirty(be, p);
        }
    }
}