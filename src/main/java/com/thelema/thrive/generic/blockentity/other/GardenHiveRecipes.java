package com.thelema.thrive.generic.blockentity.other;

import com.thelema.thrive.register.ItemRegister;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 无序三元组配方：三种花 → 一种蜂蜜。允许同种花重复。 */
public class GardenHiveRecipes {

    public record Recipe(Item a, Item b, Item c, Item honey) {
        boolean matches(Item x, Item y, Item z) {
            Item[] pool = { a, b, c };
            for (Item in : new Item[]{ x, y, z }) {
                if (!consume(pool, in)) return false;
            }
            return true;
        }
        private static boolean consume(Item[] pool, Item target) {
            for (int i = 0; i < pool.length; i++) {
                if (pool[i] == target) { pool[i] = null; return true; }
            }
            return false;
        }
    }

    /** 三朵同种花 → 对应的纯蜜。 */
    private static Recipe pure(Item flower, Item honey) {
        return new Recipe(flower, flower, flower, honey);
    }

    public static final List<Recipe> RECIPE = List.of(
            // 纯蜜配方
            pure(Items.DANDELION,            ItemRegister.DANDELION_HONEY.get()),
            pure(Items.POPPY,                ItemRegister.POPPY_HONEY.get()),
            pure(Items.BLUE_ORCHID,          ItemRegister.BLUE_ORCHID_HONEY.get()),
            pure(Items.ALLIUM,               ItemRegister.ALLIUM_HONEY.get()),
            pure(Items.AZURE_BLUET,          ItemRegister.AZURE_BLUET_HONEY.get()),
            pure(Items.RED_TULIP,            ItemRegister.RED_TULIP_HONEY.get()),
            pure(Items.ORANGE_TULIP,         ItemRegister.ORANGE_TULIP_HONEY.get()),
            pure(Items.WHITE_TULIP,          ItemRegister.WHITE_TULIP_HONEY.get()),
            pure(Items.PINK_TULIP,           ItemRegister.PINK_TULIP_HONEY.get()),
            pure(Items.OXEYE_DAISY,          ItemRegister.OXEYE_DAISY_HONEY.get()),
            pure(Items.CORNFLOWER,           ItemRegister.CORNFLOWER_HONEY.get()),
            pure(Items.LILY_OF_THE_VALLEY,   ItemRegister.LILY_OF_THE_VALLEY_HONEY.get()),
            pure(Items.WITHER_ROSE,          ItemRegister.WITHER_ROSE_HONEY.get()),
            pure(Items.TORCHFLOWER,          ItemRegister.TORCHFLOWER_HONEY.get()),
            pure(Items.SUNFLOWER,            ItemRegister.SUNFLOWER_HONEY.get()),
            pure(Items.LILAC,                ItemRegister.LILAC_HONEY.get()),
            pure(Items.PEONY,                ItemRegister.PEONY_HONEY.get()),
            pure(Items.ROSE_BUSH,            ItemRegister.ROSE_BUSH_HONEY.get()),
            pure(Items.PITCHER_PLANT,        ItemRegister.PITCHER_PLANT_HONEY.get()),
            pure(Items.FLOWERING_AZALEA_LEAVES, ItemRegister.FLOWERING_AZALEA_LEAVES_HONEY.get()),
            pure(Items.FLOWERING_AZALEA,     ItemRegister.FLOWERING_AZALEA_HONEY.get()),
            pure(Items.MANGROVE_PROPAGULE,   ItemRegister.MANGROVE_PROPAGULE_HONEY.get()),
            pure(Items.CHERRY_LEAVES,        ItemRegister.CHERRY_LEAVES_HONEY.get()),
            pure(Items.PINK_PETALS,          ItemRegister.PINK_PETALS_HONEY.get()),
            pure(Items.CHORUS_FLOWER,        ItemRegister.CHORUS_FLOWER_HONEY.get()),
            pure(Items.SPORE_BLOSSOM,        ItemRegister.SPORE_BLOSSOM_HONEY.get())
    );

    /** 无对应配方 → 返回 null，调用方用原版蜂蜜瓶兜底。 */
    @Nullable
    public static Item resolve(Item a, Item b, Item c) {
        for (Recipe r : RECIPE) {
            if (r.matches(a, b, c)) return r.honey();
        }
        return null;
    }
}
