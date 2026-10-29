package com.thelema.thrive.generic.blockentity.modifier;

import com.thelema.thrive.generic.blockentity.modifier.garden.ForestToGarden;
import com.thelema.thrive.generic.blockentity.modifier.garden.Garden;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.*;

public class BiomeModifierRegistry {

    /** id → 修改器。 */
    public static final Map<ResourceLocation, BiomeModifier> BY_ID = new HashMap<>();

    /**
     * 物品 → 候选修改器 id 列表，按注册顺序排列。
     * 路由时依次调用 canUse，先注册者优先。
     */
    private static final Map<Item, List<ResourceLocation>> ITEM_TO_ID_LIST = new HashMap<>();

    /**
     * 注册。同一物品可注册多个修改器，调用顺序即 canUse 的尝试顺序。
     * 若想保持"通用优先"，先注册通用修改器即可。
     */
    public static void register(ResourceLocation id, Item item, BiomeModifier modifier) {
        BY_ID.put(id, modifier);
        ITEM_TO_ID_LIST.computeIfAbsent(item, k -> new ArrayList<>()).add(id);
    }

    public static BiomeModifier get(ResourceLocation id) {
        return BY_ID.get(id);
    }

    /** 该物品的候选修改器 id（按注册顺序，不可变视图）。 */
    public static List<ResourceLocation> getCandidates(Item item) {
        List<ResourceLocation> list = ITEM_TO_ID_LIST.get(item);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    static {
        register(
                ResourceLocation.fromNamespaceAndPath("thrive", "plains_to_moss"),
                Items.MOSS_BLOCK,
                new PlainsToMoss());

        register(
                ResourceLocation.fromNamespaceAndPath("thrive", "forest_to_garden"),
                Items.HONEY_BLOCK,
                new ForestToGarden());

        register(
                ResourceLocation.fromNamespaceAndPath("thrive", "garden"),
                Items.HONEY_BLOCK,
                new Garden());

        register(
                ResourceLocation.fromNamespaceAndPath("thrive", "moss_to_sunberry"),
                BlockRegister.SUN_BERRY.get().asItem(),
                new MossToSunBerry());
    }
}