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
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class LanternBerryCreeperRenderer extends GeoEntityRenderer<LanternBerryCreeper> {

    public LanternBerryCreeperRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new LanternBerryCreeperModel());
        addRenderLayer(new CreeperPowerLayer<>(this));
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
        float overlay = (int) (swell * 10.0F) % 2 == 0 ? 0.0F : Mth.clamp(swell, 0.5F, 1.0F);
        return LivingEntityRenderer.getOverlayCoords(entity, overlay);
    }

    // ====== 3. 渲染类型（透明） ======
    @Override
    public RenderType getRenderType(LanternBerryCreeper entity, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    public static class CreeperPowerLayer<T extends LanternBerryCreeper> extends GeoRenderLayer<T> {
        // 直接复用原版高压苦力怕的闪电纹理
        private static final ResourceLocation POWER_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");

        public CreeperPowerLayer(GeoRenderer<T> entityRendererIn) {
            super(entityRendererIn);
        }

        @Override
        public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {

            // 如果没有高压充能，直接跳过
            if (!animatable.isPowered()) {
                return;
            }

            // 原版的 UV 流动算法
            float f = (float) animatable.tickCount + partialTick;
            // 使用 energySwirl 渲染类型，实现闪电的流动感
            RenderType energyRenderType = RenderType.energySwirl(POWER_LOCATION, f * 0.01F % 1.0F, f * 0.01F % 1.0F);

            // 稍微放大一点点（原版是用一样的模型叠加，这里为了防 Z-Fighting 放大 1.02 倍）
            poseStack.pushPose();
            poseStack.scale(1.02F, 1.02F, 1.02F);

            // 重新渲染模型，并强制应用 energySwirl 材质
            this.getRenderer().reRender(
                    bakedModel,
                    poseStack,
                    bufferSource,
                    animatable,
                    energyRenderType,
                    bufferSource.getBuffer(energyRenderType),
                    partialTick,
                    packedLight,
                    packedOverlay,
                    0xFFFFFFFF // 颜色：纯白
            );

            poseStack.popPose();
        }
    }
}