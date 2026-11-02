package com.thelema.thrive.mixin;

import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LightEngine.class)
public interface LightEngineMixin {
    @Accessor("chunkSource") LightChunkGetter getChunkSource();
    @Accessor("storage")     LayerLightSectionStorage<?> getStorage();

    @Invoker("enqueueIncrease") void invokeEnqueueIncrease(long pos, long entry);
    @Invoker("enqueueDecrease") void invokeEnqueueDecrease(long pos, long entry);
}