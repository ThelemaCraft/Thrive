package com.thelema.thrive.world.biome;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.*;
import com.thelema.thrive.ThriveMod;

public class TBiomes {

    public static final ResourceKey<Biome> MOSS_LAND = biome("moss_land");
    public static final ResourceKey<Biome> FLOWER_SEA = biome("flower_sea");

    private static ResourceKey<Biome> biome(String name) {
        return ResourceKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, name));
    }


}