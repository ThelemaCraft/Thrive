package com.thelema.thrive.generic.blockentity.convertor;

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

import java.util.*;
import java.util.function.BiConsumer;

/**
 * 一条转换路径的完整逻辑。
 * BE 只负责调度（按 id 找到本转换器、调用 tick）。
 */
public interface BiomeModifier {

    /**决定能不能运行*/
    boolean canUse(ServerLevel level, BiomeCoreBlockEntity be);

    /** tick */
    void tick(ServerLevel level, BiomeCoreBlockEntity be);

    /** 读本转换已运行的 tick 数。存 data，读档续跑。 */
    default long tick(BiomeCoreBlockEntity be) { return be.data.getLong("tick"); }

    /** 本 tick 结束前调用，把 tick 数 +1。 */
    default void advanceTick(BiomeCoreBlockEntity be) {
        be.data.putLong("tick", be.data.getLong("tick") + 1);
    }

    /** 第 r 环（切比雪夫壳），y 覆盖 [minY, maxY]。 */
    default void forRing(ServerLevel level, BiomeCoreBlockEntity be, int r,
                         BiConsumer<BlockPos, BlockState> action) {
        if (r < 0) return;
        BlockPos c = be.getBlockPos();
        int minY = be.minY(), maxY = be.maxY();
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                for (int y = minY; y <= maxY; y++) {
                    BlockPos p = new BlockPos(c.getX() + dx, y, c.getZ() + dz);
                    action.accept(p, level.getBlockState(p));
                }
            }
    }

    /** setBlock + 光照刷新。 */
    default void set(ServerLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, 3);
        level.getLightEngine().checkBlock(p);
    }

    /** 标记该方块所在 chunk 需要重发。 */
    default void markDirty(BiomeCoreBlockEntity be, BlockPos p) {
        be.dirty.add(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
    }

    /** 把第 r 环涉及的群系单元从 source 改成 target。 */
    default void rewriteBiome(ServerLevel level, BiomeCoreBlockEntity be, int r,
                              ResourceKey<Biome> source, ResourceKey<Biome> target) {
        Holder<Biome> targetBiome = level.registryAccess()
                .lookupOrThrow(Registries.BIOME).getOrThrow(target);

        Map<Long, Map<Integer, Set<Integer>>> touched = new HashMap<>();

        forRing(level, be, r, (p, s) -> {
            long ck = ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
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
            ChunkAccess chunk = level.getChunk(cp.x, cp.z, ChunkStatus.FULL, false);
            if (chunk == null) continue;
            for (var se : ce.getValue().entrySet()) {
                rewriteSection(level, chunk, se.getKey(), se.getValue(), source, targetBiome);
            }
        }
    }

    /** section 级群系改写。内部细节。 */
    default void rewriteSection(ServerLevel level, ChunkAccess chunk, int sectionIndex,
                                Set<Integer> units, ResourceKey<Biome> source, Holder<Biome> target) {
        LevelChunkSection section = chunk.getSection(sectionIndex);
        var old = section.getBiomes();

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
            return prev.unwrapKey().map(key -> key.equals(source)).orElse(false)
                    ? target : prev;
        }, sampler, qx, qy, qz);

        chunk.setUnsaved(true);
    }

    /** 重发所有标记过的 chunk。 */
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

    /** 从模板里找 BiomeCore 的相对坐标。找不到返回 null。 */
    default BlockPos findCoreOffset(StructureTemplate template) {
        List<StructureTemplate.StructureBlockInfo> list = template.filterBlocks(
                BlockPos.ZERO,
                new StructurePlaceSettings(),
                BlockRegister.BIOME_CORE.get());
        return list.isEmpty() ? null : list.get(0).pos();
    }

    /**
     * 用 toId 结构替换 fromId 结构，两者核心方块对齐。
     * 起点分别由各自 NBT 中 BiomeCore 的相对位置反推。
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

        // 保存旧数据
        CompoundTag surfaceNbt = new CompoundTag();
        be.surface.save(surfaceNbt, level.registryAccess());

        // 清空旧 BE 物品，防止 placeInWorld 覆盖时 onRemove 掉落
        be.surface.item = null;
        be.rotator.item = ItemStack.EMPTY;

        // 放置新结构
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

        // 刷新光照 + 标记 chunk 重发
        Vec3i toSize = to.getSize();
        BlockPos toEnd = toOrigin.offset(toSize.getX() - 1, toSize.getY() - 1, toSize.getZ() - 1);
        for (BlockPos p : BlockPos.betweenClosed(toOrigin, toEnd)) {
            level.getLightEngine().checkBlock(p);
            be.dirty.add(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
        }
    }
}