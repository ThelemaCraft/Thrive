package com.thelema.thrive.client.register;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.register.BlockRegister;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = ThriveMod.MOD_ID)
public class ItemColorRegister {

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> 0x91BD59,   // 平原草色，静态值
                BlockRegister.GRASS_BLOCK.get().asItem()
        );
    }
}