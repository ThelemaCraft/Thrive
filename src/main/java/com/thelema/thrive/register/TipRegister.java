package com.thelema.thrive.register;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;


@EventBusSubscriber
public class TipRegister {

    @SubscribeEvent
    public static void extra(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

    }

}
