package com.thelema.thrive.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelema.thrive.generic.block.SunBerry;
import com.thelema.thrive.generic.blockentity.SunBerryBlockEntity;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class SunBerryBlockEntityRenderer implements BlockEntityRenderer<SunBerryBlockEntity> {

    private final BlockRenderDispatcher blockRenderer;

    public SunBerryBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(SunBerryBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!be.getBlockState().getValue(SunBerry.CORE)) return;

        poseStack.pushPose();
        poseStack.translate(-1.0F, -1.0F, -1.0F);
        poseStack.scale(3.0F, 3.0F, 3.0F);

        blockRenderer.renderSingleBlock(
                BlockRegister.LANTERN_BERRY.get().defaultBlockState(),
                poseStack, buffer,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(SunBerryBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}