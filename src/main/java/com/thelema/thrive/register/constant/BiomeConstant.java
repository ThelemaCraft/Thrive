package com.thelema.thrive.register.constant;

import com.thelema.thrive.ThriveMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public interface BiomeConstant {

    /** 花园群系的 ResourceKey */
    ResourceKey<Biome> GARDEN = biome("garden");
    ResourceKey<Biome> MOSS = biome("moss");
    ResourceKey<Biome> SUNBERRY = biome("sunberry");

    static ResourceKey<Biome> biome(String name){
        return ResourceKey.create(Registries.BIOME,ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, name));
    }
}
