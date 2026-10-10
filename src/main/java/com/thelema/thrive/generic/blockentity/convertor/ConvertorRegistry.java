package com.thelema.thrive.generic.blockentity.convertor;

import com.thelema.thrive.register.BlockRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.Map;

public class ConvertorRegistry {

    /** 物品 → (源群系 → 转换器)。同一物品在不同源群系触发不同转换。 */
    public static final Map<Item, Map<ResourceKey<Biome>, Convertor>> MAP = new HashMap<>();

    public static void register(Item item, ResourceKey<Biome> source, Convertor convertor) {
        MAP.computeIfAbsent(item, k -> new HashMap<>()).put(source, convertor);
    }

    static {
        register(BlockRegister.RAMPANT_MOSS.get().asItem(),
                ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("plains")),
                new PlainsToMossConvertor());
        // 后续：register(item, ResourceKey.create(..., "forest"), new ForestToMossConvertor());
    }
}