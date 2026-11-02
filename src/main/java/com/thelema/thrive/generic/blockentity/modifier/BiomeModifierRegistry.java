package com.thelema.thrive.generic.blockentity.modifier;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.blockentity.modifier.garden.ForestToGarden;
import com.thelema.thrive.generic.blockentity.modifier.garden.Garden;
import com.thelema.thrive.generic.blockentity.modifier.moss.Moss;
import com.thelema.thrive.generic.blockentity.modifier.moss.PlainsToMoss;
import com.thelema.thrive.generic.blockentity.modifier.sunberry.MossToSunBerry;
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
    public static void register(String namespace, String name, Item item, BiomeModifier modifier) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, name);
        BY_ID.put(id, modifier);
        ITEM_TO_ID_LIST.computeIfAbsent(item, k -> new ArrayList<>()).add(id);
    }

    static void register(String name, Item item, BiomeModifier modifier){
        register(ThriveMod.MOD_ID, name, item, modifier);
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
        register("plains_to_moss", Items.MOSS_BLOCK, new PlainsToMoss());

        register("moss", Items.MOSS_BLOCK, new Moss());

        register("forest_to_garden", Items.HONEY_BLOCK, new ForestToGarden());

        register("garden", Items.HONEY_BLOCK, new Garden());

//        register(
//                ResourceLocation.fromNamespaceAndPath("thrive", "moss_to_sunberry"),
//                BlockRegister.SUN_BERRY.get().asItem(),
//                new MossToSunBerry());

    }
}