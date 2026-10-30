package com.thelema.thrive.generic.blockentity.modifier;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Set;

/**
 * SectionApplier 的标准实现：把命中 unit 的群系改写为 target。
 * <p>
 * {@code source} 为 null 时无脑替换（跳过已是 target 的），否则只替换等于 source 的。
 * 高频路径上建 {@code static final} 单例，不要每 tick new。
 */
public final class Succession implements SectionApplier {

    private final ResourceKey<Biome> source;   // null = 任意源
    private final ResourceKey<Biome> target;

    public Succession(ResourceKey<Biome> source, ResourceKey<Biome> target) {
        this.source = source;
        this.target = target;
    }

    @Override
    public void apply(ChunkAccess chunk, int sectionIndex, Set<Integer> units) {
        if (!(chunk instanceof LevelChunk lc)) return;
        ServerLevel level = (ServerLevel) lc.getLevel();
        Holder<Biome> t = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(target);
        BiomeUtil.applyBiomeSection(chunk, sectionIndex, units, t,
                source == null
                        ? prev -> !prev.unwrapKey().map(k -> k.equals(target)).orElse(false)
                        : prev -> prev.unwrapKey().map(k -> k.equals(source)).orElse(false));
    }
}