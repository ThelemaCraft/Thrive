package com.thelema.thrive.generic.blockentity.other;

import com.thelema.thrive.register.ItemRegister;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** 无序 3 元组配方：三种花蜜  一种蜂蜜。 */
public class GardenHiveRecipes {

    static final Map<List<Item>, Item> RECIPES = new HashMap<>();

    /** 排序规范化，消除顺序差异。 */
    static List<Item> key(Item... items) {
        return Stream.of(items)
                .sorted(Comparator.comparing(Item::getDescriptionId))
                .toList();
    }

    static void add(Item a, Item b, Item c, Item honey) {
        RECIPES.put(key(a, b, c), honey);
    }

    public static @Nullable Item resolve(Item a, Item b, Item c) {
        return RECIPES.get(key(a, b, c));
    }

    static {
        add(Items.DANDELION, Items.POPPY, Items.CORNFLOWER,
                ItemRegister.GARDEN_HONEY.get());
        // 后续配方一行一条
    }
}
