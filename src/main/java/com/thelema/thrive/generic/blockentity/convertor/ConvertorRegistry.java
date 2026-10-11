package com.thelema.thrive.generic.blockentity.convertor;

import com.thelema.thrive.generic.blockentity.convertor.Convertor;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

public class ConvertorRegistry {

    /** id → 转换器。 */
    public static final Map<ResourceLocation, Convertor> BY_ID = new HashMap<>();

    /** 物品 + 源群系 → id。需要群系条件的转换。 */
    private static final Map<Item, Map<ResourceKey<Biome>, ResourceLocation>> ROUTE = new HashMap<>();

    /** 物品 → id。**无群系条件**的通用转换（纯装饰器）。优先于 ROUTE。 */
    private static final Map<Item, ResourceLocation> UNIVERSAL = new HashMap<>();

    /** 需群系条件。 */
    public static void register(ResourceLocation id, Item item,
                                ResourceKey<Biome> source, Convertor convertor) {
        BY_ID.put(id, convertor);
        ROUTE.computeIfAbsent(item, k -> new HashMap<>()).put(source, id);
    }

    /** 无群系条件。 */
    public static void registerUniversal(ResourceLocation id, Item item, Convertor convertor) {
        BY_ID.put(id, convertor);
        UNIVERSAL.put(item, id);
    }

    public static Convertor get(ResourceLocation id) {
        return BY_ID.get(id);
    }

    /** 路由：通用优先，其次按源群系。 */
    public static ResourceLocation route(Item item, ResourceKey<Biome> source) {
        ResourceLocation universal = UNIVERSAL.get(item);
        if (universal != null) return universal;

        Map<ResourceKey<Biome>, ResourceLocation> m = ROUTE.get(item);
        return m == null ? null : m.get(source);
    }

    static {
        // 有群系条件
        register(
                ResourceLocation.fromNamespaceAndPath("thrive", "plains_to_moss"),
                BlockRegister.RAMPANT_MOSS.get().asItem(),
                ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("plains")),
                new PlainsToMossConvertor());

        register(
                ResourceLocation.fromNamespaceAndPath("thrive", "forest_to_garden"),
                Items.HONEY_BLOCK,   // 你的花园触发物品
                ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("forest")),
                new ForestToGarden());
    }
}