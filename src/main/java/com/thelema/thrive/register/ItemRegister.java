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
    }
}
