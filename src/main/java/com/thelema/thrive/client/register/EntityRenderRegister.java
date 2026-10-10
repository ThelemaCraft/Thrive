package com.thelema.thrive.client.register;

import com.thelema.thrive.client.render.entity.*;
import com.thelema.thrive.register.EntityRegister;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber
@OnlyIn(Dist.CLIENT)
public class EntityRenderRegister {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityRegister.MOSSY_ZOMBIE.get(), MossyZombieRenderer::new);
        event.registerEntityRenderer(EntityRegister.PRIMED_LANTERN_BERRY.get(), PrimedLanternBerryRenderer::new);
        event.registerEntityRenderer(EntityRegister.LANTERN_BERRY_CREEPER.get(), LanternBerryCreeperRenderer::new);
        event.registerEntityRenderer(EntityRegister.GRASS_LAND_SHEEP.get(), GrassLandSheepRenderer::new);
        event.registerEntityRenderer(EntityRegister.PRIMED_SUN_BERRY.get(), PrimedSunBerryRenderer::new);
        event.registerEntityRenderer(EntityRegister.SUN.get(), SunRenderer::new);
    }
}
