package com.thelema.thrive.client.sound;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.client.sound.GardenBeeSoundInstance;
import com.thelema.thrive.generic.entity.GardenBee;
import com.thelema.thrive.generic.entity.GardenSoldierBee;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = ThriveMod.MOD_ID, value = Dist.CLIENT)
public class ClientSoundEvents {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide) return;

        if (event.getEntity() instanceof GardenBee bee) {
            Minecraft.getInstance().getSoundManager()
                    .play(new GardenBeeSoundInstance(bee, false, null));
        } else if (event.getEntity() instanceof GardenSoldierBee bee) {

            Minecraft.getInstance().getSoundManager()
                    .play(new GardenBeeSoundInstance(bee, bee.getTarget() != null, () -> bee.getTarget() != null));
        }
    }
}
