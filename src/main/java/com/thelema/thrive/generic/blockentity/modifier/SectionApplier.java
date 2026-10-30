package com.thelema.thrive.generic.blockentity.modifier;

import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Set;

@FunctionalInterface
public interface SectionApplier {
    void apply(ChunkAccess chunk, int sectionIndex, Set<Integer> units);
}