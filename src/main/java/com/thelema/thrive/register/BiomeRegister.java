package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public class BiomeRegister {

    /** 花园群系的 ResourceKey */
    public static final ResourceKey<Biome> GARDEN = biome("garden");
    public static final ResourceKey<Biome> MOSS = biome("moss");


    private static ResourceKey<Biome> biome(String name){
        return ResourceKey.create(Registries.BIOME,ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, name));
    }
}
