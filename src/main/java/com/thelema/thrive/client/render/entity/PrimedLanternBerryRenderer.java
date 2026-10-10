package com.thelema.thrive.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelema.thrive.generic.entity.PrimedLanternBerry;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class PrimedLanternBerryRenderer extends EntityRenderer<PrimedLanternBerry> {
    private final BlockRenderDispatcher blockRenderer;

    public PrimedLanternBerryRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(PrimedLanternBerry entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        // 1. 提高到方块中心（参考TNT）
        poseStack.translate(0.0F, 0.5F, 0.0F);

        // 2. 爆炸前膨胀（参考TNT）
        int fuse = entity.getFuse();
        if ((float) fuse - partialTick + 1.0F < 10.0F) {
            float f = 1.0F - ((float) fuse - partialTick + 1.0F) / 10.0F;
            f = Mth.clamp(f, 0.0F, 1.0F);
            f *= f;
            f *= f;
            float scale = 1.0F + f * 0.3F;
            poseStack.scale(scale, scale, scale);
        }

        // 3. 绕Y轴旋转（参考TNT）
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.translate(-0.5F, -0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));

        // 4. 渲染方块 + 闪烁效果（参考TNT）
        boolean flash = fuse / 5 % 2 == 0;
        int overlay = flash ? OverlayTexture.pack(OverlayTexture.u(1.0F), 10) : OverlayTexture.NO_OVERLAY;
        blockRenderer.renderSingleBlock(
                BlockRegister.LANTERN_BERRY.get().defaultBlockState(),
                poseStack, buffer, packedLight, overlay
        );

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PrimedLanternBerry entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}