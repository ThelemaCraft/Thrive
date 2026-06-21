package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.item.EnchantedMossGoldenAppleItem;
import com.thelema.thrive.generic.item.GlowingBerryJuiceBottleItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ItemRegister {

    static final DeferredRegister<Item> ITEM = DeferredRegister.createItems(ThriveMod.MOD_ID);

    public static void register(IEventBus bus) {
        ITEM.register(bus);
    }

    private static DeferredHolder<Item, Item> simpleItem(String name) {
        return ITEM.register(name, () -> new Item(new Item.Properties()));
    }

    // 物品
    public static final DeferredHolder<Item, Item> GLOWING_BERRY_JUICE_BOTTLE;
    public static final DeferredHolder<Item, Item> MOSS_CALLUS;
    public static final DeferredHolder<Item, Item> ENCHANTED_MOSS_GOLDEN_APPLE;


    static {
        GLOWING_BERRY_JUICE_BOTTLE = ITEM.register("glowing_berry_juice_bottle", GlowingBerryJuiceBottleItem::new);
        MOSS_CALLUS = simpleItem("moss_callus");
        ENCHANTED_MOSS_GOLDEN_APPLE = ITEM.register("enchanted_moss_golden_apple", EnchantedMossGoldenAppleItem::new);

    }
}
