package com.thelema.thrive.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelema.thrive.generic.entity.PrimedSunBerry;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class PrimedSunBerryRenderer extends EntityRenderer<PrimedSunBerry> {

    private final BlockRenderDispatcher blockRenderer;

    public PrimedSunBerryRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(PrimedSunBerry entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        // 爆炸前膨胀
        int fuse = entity.getFuse();
        float scale = 1.0F;
        if ((float) fuse - partialTick + 1.0F < 10.0F) {
            float f = 1.0F - ((float) fuse - partialTick + 1.0F) / 10.0F;
            f = Mth.clamp(f, 0.0F, 1.0F);
            f *= f;
            f *= f;
            scale = 1.0F + f * 0.3F;
        }

        // 整体放大 3 倍，渲染成一个大方块
        float s = 3.0F * scale;
        poseStack.scale(s, s, s);
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        boolean flash = fuse / 5 % 2 == 0;
        int overlay = flash
                ? OverlayTexture.pack(OverlayTexture.u(1.0F), 10)
                : OverlayTexture.NO_OVERLAY;

        blockRenderer.renderSingleBlock(
                BlockRegister.LANTERN_BERRY.get().defaultBlockState(),
                poseStack, buffer, packedLight, overlay);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PrimedSunBerry entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}