package com.thelema.thrive.client.register;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.client.renderer.BiomeColors;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = ThriveMod.MOD_ID)
public class BlockColorRegister {

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> {
                    if (level != null && pos != null) {
                        return BiomeColors.getAverageGrassColor(level, pos);
                    }
                    return 0xFFFFFF;
                },
                BlockRegister.GRASS_BLOCK.get()
        );
    }
}