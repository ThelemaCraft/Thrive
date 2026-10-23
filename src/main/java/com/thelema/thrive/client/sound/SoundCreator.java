package com.thelema.thrive.client.sound;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.entity.GardenBee;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = ThriveMod.MOD_ID, value = Dist.CLIENT)
public class SoundCreator {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof GardenBee bee) {
            Minecraft.getInstance().getSoundManager().play(new GardenBeeSoundInstance(bee));
        }
    }

}
