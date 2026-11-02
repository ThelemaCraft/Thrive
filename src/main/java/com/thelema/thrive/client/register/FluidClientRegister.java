package com.thelema.thrive.client.register;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.register.FluidRegister;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = ThriveMod.MOD_ID)
public class FluidClientRegister {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(
                    FluidRegister.LANTERN_BERRY_JUICE_STILL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(
                    FluidRegister.LANTERN_BERRY_JUICE_FLOWING.get(), RenderType.translucent());
        });
    }

    @SubscribeEvent
    public static void register(RegisterClientExtensionsEvent event) {

        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                // 先用水贴图确保能跑
                return ResourceLocation.withDefaultNamespace("block/water_still");
            }
            @Override
            public ResourceLocation getFlowingTexture() {
                return ResourceLocation.withDefaultNamespace("block/water_flow");
            }
            @Override
            public int getTintColor() {
                return 0xFFFFDD33;
            }
            @Override
            public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance,
                                        float partialTick, float nearDistance, float farDistance, FogShape shape) {
                if (mode != FogRenderer.FogMode.FOG_TERRAIN) return;

                RenderSystem.setShaderFogColor(1.0F, 1.0F, 0.2F, 1.0F);

                // 雾距离：越近越"浓"
                RenderSystem.setShaderFogStart(0.5F);
                RenderSystem.setShaderFogEnd(6.0F);
            }

        }, FluidRegister.LANTERN_BERRY_JUICE_TYPE.get());
    }
}