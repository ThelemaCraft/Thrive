package com.thelema.thrive.mixin;

import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LayerLightSectionStorage.class)
public interface LayerLightSectionStorageInvoker {
    @Invoker("setStoredLevel") void invokeSetStoredLevel(long pos, int level);
    @Invoker("getStoredLevel") int  invokeGetStoredLevel(long pos);
    @Invoker("storingLightForSection") boolean invokeStoringLightForSection(long sectionPos);
}