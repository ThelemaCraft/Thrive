package com.thelema.thrive.client.register;

import com.thelema.thrive.client.render.blockentity.BiomeCoreBlockEntityRenderer;
import com.thelema.thrive.client.render.blockentity.SunBerryBlockEntityRenderer;
import com.thelema.thrive.register.BlockEntityRegister;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber
@OnlyIn(Dist.CLIENT)
public class BlockEntityRenderRegister {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockEntityRegister.SUN_BERRY.get(), SunBerryBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(BlockEntityRegister.BIOME_CORE.get(), BiomeCoreBlockEntityRenderer::new);
    }

}
