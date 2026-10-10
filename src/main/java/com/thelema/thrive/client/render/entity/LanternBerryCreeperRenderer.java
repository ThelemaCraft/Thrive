package com.thelema.thrive.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelema.thrive.client.model.entity.LanternBerryCreeperModel;
import com.thelema.thrive.generic.entity.LanternBerryCreeper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class LanternBerryCreeperRenderer extends GeoEntityRenderer<LanternBerryCreeper> {

    public LanternBerryCreeperRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new LanternBerryCreeperModel());
    }

    // ====== 1. 膨胀缩放（在渲染前应用） ======
    @Override
    public void preRender(PoseStack poseStack, LanternBerryCreeper entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick,
                          int packedLight, int packedOverlay, int color) {
        super.preRender(poseStack, entity, model, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay, color);

        // 膨胀缩放（复制自 Creeper Overhaul）
        float swell = entity.getSwelling(partialTick);
        float f1 = 1.0F + Mth.sin(swell * 100.0F) * swell * 0.01F;
        swell = Mth.clamp(swell, 0.0F, 1.0F);
        swell *= swell;
        swell *= swell;
        float scaleXZ = (1.0F + swell * 0.4F) * f1;
        float scaleY = (1.0F + swell * 0.1F) / f1;
        poseStack.scale(scaleXZ, scaleY, scaleXZ);
    }

    // ====== 2. 黑白闪烁（Overlay） ======
    @Override
    public int getPackedOverlay(LanternBerryCreeper entity, float u, float partialTick) {
        // 复制 Creeper Overhaul 的闪烁逻辑
        float swell = entity.getSwelling(partialTick);
        float overlay = (int)(swell * 10.0F) % 2 == 0 ? 0.0F : Mth.clamp(swell, 0.5F, 1.0F);
        return LivingEntityRenderer.getOverlayCoords(entity, overlay);
    }

    // ====== 3. 渲染类型（透明） ======
    @Override
    public RenderType getRenderType(LanternBerryCreeper entity, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }
}