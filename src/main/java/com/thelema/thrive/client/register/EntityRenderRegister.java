package com.thelema.thrive.client.register;

import com.thelema.thrive.client.model.entity.GardenBeeModel;
import com.thelema.thrive.client.model.entity.GardenSoldierBeeModel;
import com.thelema.thrive.client.render.entity.*;
import com.thelema.thrive.generic.entity.GardenBee;
import com.thelema.thrive.register.EntityRegister;
import net.minecraft.client.renderer.entity.BeeRenderer;
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
        event.registerEntityRenderer(EntityRegister.GARDEN_BEE.get(), GardenBeeRenderer::new);
        event.registerEntityRenderer(EntityRegister.GARDEN_SOLDIER_BEE.get(), GardenSoldierBeeRenderer::new);
        event.registerEntityRenderer(EntityRegister.PRIMED_SUN_BERRY.get(), PrimedSunBerryRenderer::new);
        event.registerEntityRenderer(EntityRegister.SUN.get(), SunRenderer::new);
        event.registerEntityRenderer(EntityRegister.GARDEN_PILLAGER.get(), GardenPillagerRenderer::new);
        event.registerEntityRenderer(EntityRegister.GARDEN_WITCH.get(), GardenWitchRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GardenBeeModel.LAYER_LOCATION, GardenBeeModel::createBodyLayer);
        event.registerLayerDefinition(GardenSoldierBeeModel.LAYER_LOCATION, GardenSoldierBeeModel::createBodyLayer);
    }

}
