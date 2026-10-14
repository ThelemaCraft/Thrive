package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.item.BiomeTwig;
import com.thelema.thrive.generic.item.HungerSnack;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ItemRegister {

    public static final DeferredRegister<Item> ITEM = DeferredRegister.createItems(ThriveMod.MOD_ID);

    public static void register(IEventBus bus) {
        ITEM.register(bus);
    }

    private static DeferredHolder<Item, Item> simpleItem(String name) {
        return ITEM.register(name, () -> new Item(new Item.Properties()));
    }

    public static final DeferredHolder<Item, Item> FRESH_GRASS;
    public static final DeferredHolder<Item, Item> FRESH_GRASS_PASTE;
    public static final DeferredHolder<Item, Item> HUNGER_SNACK;
    public static final DeferredHolder<Item, Item> FRESH_GRASS_CAKE;
    public static final DeferredHolder<Item, Item> BIOME_TWIG;
    public static final DeferredHolder<Item, Item> RAMPANT_MOSS;
    public static final DeferredHolder<Item, Item> GARDEN_BEE_SPAWN_EGG;

    // 每种原版花对应的蜂蜜
    public static final DeferredHolder<Item, Item> DANDELION_HONEY;
    public static final DeferredHolder<Item, Item> POPPY_HONEY;
    public static final DeferredHolder<Item, Item> BLUE_ORCHID_HONEY;
    public static final DeferredHolder<Item, Item> ALLIUM_HONEY;
    public static final DeferredHolder<Item, Item> AZURE_BLUET_HONEY;
    public static final DeferredHolder<Item, Item> RED_TULIP_HONEY;
    public static final DeferredHolder<Item, Item> ORANGE_TULIP_HONEY;
    public static final DeferredHolder<Item, Item> WHITE_TULIP_HONEY;
    public static final DeferredHolder<Item, Item> PINK_TULIP_HONEY;
    public static final DeferredHolder<Item, Item> OXEYE_DAISY_HONEY;
    public static final DeferredHolder<Item, Item> CORNFLOWER_HONEY;
    public static final DeferredHolder<Item, Item> LILY_OF_THE_VALLEY_HONEY;
    public static final DeferredHolder<Item, Item> WITHER_ROSE_HONEY;
    public static final DeferredHolder<Item, Item> TORCHFLOWER_HONEY;
    public static final DeferredHolder<Item, Item> SUNFLOWER_HONEY;
    public static final DeferredHolder<Item, Item> LILAC_HONEY;
    public static final DeferredHolder<Item, Item> PEONY_HONEY;
    public static final DeferredHolder<Item, Item> ROSE_BUSH_HONEY;
    public static final DeferredHolder<Item, Item> PITCHER_PLANT_HONEY;
    public static final DeferredHolder<Item, Item> FLOWERING_AZALEA_LEAVES_HONEY;
    public static final DeferredHolder<Item, Item> FLOWERING_AZALEA_HONEY;
    public static final DeferredHolder<Item, Item> MANGROVE_PROPAGULE_HONEY;
    public static final DeferredHolder<Item, Item> CHERRY_LEAVES_HONEY;
    public static final DeferredHolder<Item, Item> PINK_PETALS_HONEY;
    public static final DeferredHolder<Item, Item> CHORUS_FLOWER_HONEY;
    public static final DeferredHolder<Item, Item> SPORE_BLOSSOM_HONEY;

    // 物品
    static {

        FRESH_GRASS = simpleItem("fresh_grass");

        FRESH_GRASS_PASTE = ITEM.register("fresh_grass_paste",
                () -> new Item(new Item.Properties()
                        .food(new FoodProperties.Builder()
                                .nutrition(5)
                                .saturationModifier(0.6F)
                                .effect(() -> new MobEffectInstance(MobEffects.HUNGER, 2400, 4), 1.0F)
                                .build())));
        HUNGER_SNACK = ITEM.register("hunger_snack", HungerSnack::new);
        FRESH_GRASS_CAKE = ITEM.register("fresh_grass_cake", () -> new Item(new Item.Properties()
                .food(new FoodProperties.Builder()
                        .nutrition(5)
                        .saturationModifier(0.6F)
                        .effect(() -> new MobEffectInstance(EffectRegister.APPETITE, 24000, 0), 1.0F)
                        .build())));

        BIOME_TWIG = ITEM.register("biome_twig", BiomeTwig::new);

        // 方块物品，堆叠上限 1
        RAMPANT_MOSS = ITEM.register("rampant_moss", () -> new BlockItem(BlockRegister.RAMPANT_MOSS.get(),
                new Item.Properties().stacksTo(1)));

        GARDEN_BEE_SPAWN_EGG = ITEM.register("garden_bee_spawn_egg", () -> new DeferredSpawnEggItem(
                EntityRegister.GARDEN_BEE,
                0xFFD700,   // 底：金黄色
                0xFFFFFF,   // 高光：白
                new Item.Properties()));


        // 花朵蜂蜜
        DANDELION_HONEY = simpleItem("dandelion_honey");
        POPPY_HONEY = simpleItem("poppy_honey");
        BLUE_ORCHID_HONEY = simpleItem("blue_orchid_honey");
        ALLIUM_HONEY = simpleItem("allium_honey");
        AZURE_BLUET_HONEY = simpleItem("azure_bluet_honey");
        RED_TULIP_HONEY = simpleItem("red_tulip_honey");
        ORANGE_TULIP_HONEY = simpleItem("orange_tulip_honey");
        WHITE_TULIP_HONEY = simpleItem("white_tulip_honey");
        PINK_TULIP_HONEY = simpleItem("pink_tulip_honey");
        OXEYE_DAISY_HONEY = simpleItem("oxeye_daisy_honey");
        CORNFLOWER_HONEY = simpleItem("cornflower_honey");
        LILY_OF_THE_VALLEY_HONEY = simpleItem("lily_of_the_valley_honey");
        WITHER_ROSE_HONEY = simpleItem("wither_rose_honey");
        TORCHFLOWER_HONEY = simpleItem("torchflower_honey");
        SUNFLOWER_HONEY = simpleItem("sunflower_honey");
        LILAC_HONEY = simpleItem("lilac_honey");
        PEONY_HONEY = simpleItem("peony_honey");
        ROSE_BUSH_HONEY = simpleItem("rose_bush_honey");
        PITCHER_PLANT_HONEY = simpleItem("pitcher_plant_honey");
        FLOWERING_AZALEA_LEAVES_HONEY = simpleItem("flowering_azalea_leaves_honey");
        FLOWERING_AZALEA_HONEY = simpleItem("flowering_azalea_honey");
        MANGROVE_PROPAGULE_HONEY = simpleItem("mangrove_propagule_honey");
        CHERRY_LEAVES_HONEY = simpleItem("cherry_leaves_honey");
        PINK_PETALS_HONEY = simpleItem("pink_petals_honey");
        CHORUS_FLOWER_HONEY = simpleItem("chorus_flower_honey");
        SPORE_BLOSSOM_HONEY = simpleItem("spore_blossom_honey");
    }
}

