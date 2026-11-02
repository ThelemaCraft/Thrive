package com.thelema.thrive.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.entity.BreezeCore;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class BreezeCoreWindLayer extends GeoRenderLayer<BreezeCore> {

    private static final ResourceLocation WIND =
            ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, "textures/entity/breeze_core/wind.png");

    /** 原版偏移量单位是像素，乘这个系数放大到肉眼可见。 */
    private static final float WOBBLE = 4.0F;

    public BreezeCoreWindLayer(GeoEntityRenderer<BreezeCore> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, BreezeCore entity, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        float t = entity.tickCount + partialTick;
        float f = t * (float) Math.PI * -0.1F;

        // 原版 BreezeModel.setupAnim 的偏移公式：上 cos/sin·0.6，中 sin/cos·0.8
        float outX = Mth.cos(f) * 0.6F * WOBBLE / 16F;
        float outZ = Mth.sin(f) * 0.6F * WOBBLE / 16F;
        float inX  = Mth.sin(f) * 0.8F * WOBBLE / 16F;
        float inZ  = Mth.cos(f) * 0.8F * WOBBLE / 16F;

        float cy = 1.0F;

        // 外层：边长 5，三轴同向旋转
        float ox = (t * 0.8F) % 360F;
        float oy = (t * 1.5F) % 360F;
        float oz = (t * 1.1F) % 360F;

        poseStack.pushPose();
        poseStack.translate(0, cy, 0);
        poseStack.mulPose(Axis.XP.rotationDegrees(ox));
        poseStack.mulPose(Axis.YP.rotationDegrees(oy));
        poseStack.mulPose(Axis.ZP.rotationDegrees(oz));
        drawShell(poseStack,
                bufferSource.getBuffer(RenderType.breezeWind(WIND, (t * 0.02F) % 1.0F, 0F)),
                2.5F, -2.5F, 2.5F, outX, outZ);
        poseStack.popPose();

        // 内层：边长 3，三轴反向 + 更快
        float ix = (-t * 1.3F) % 360F;
        float iy = (-t * 2.0F) % 360F;
        float iz = (-t * 0.9F) % 360F;

        poseStack.pushPose();
        poseStack.translate(0, cy, 0);
        poseStack.mulPose(Axis.XP.rotationDegrees(ix));
        poseStack.mulPose(Axis.YP.rotationDegrees(iy));
        poseStack.mulPose(Axis.ZP.rotationDegrees(iz));
        drawShell(poseStack,
                bufferSource.getBuffer(RenderType.breezeWind(WIND, (-t * 0.014F + 0.5F) % 1.0F, 0F)),
                1.5F, -1.5F, 1.5F, inX, inZ);
        poseStack.popPose();
    }

    /** 6 面立方体壳，UV 从左上角开始，按 1/8 scale 截取。 */
    private static void drawShell(PoseStack poseStack, VertexConsumer vc,
                                  float half, float yMin, float yMax,
                                  float offX, float offZ) {
        PoseStack.Pose pose = poseStack.last();

        // 128×128 纹理，每块 16 像素 → 一块占 1/8 UV
        float scale = 1.0F / 8.0F;
        float maxU  = (half * 2) * scale;         // 边长对应的 UV 宽度
        float maxV  = (yMax - yMin) * scale;       // 高度对应的 UV 高度

        // +X 面（U 沿 Z，V 沿 Y）
        vert(pose, vc, half + offX, yMin, -half + offZ, 0,    maxV, 1, 0, 0);
        vert(pose, vc, half + offX, yMin,  half + offZ, maxU, maxV, 1, 0, 0);
        vert(pose, vc, half + offX, yMax,  half + offZ, maxU, 0,    1, 0, 0);
        vert(pose, vc, half + offX, yMax, -half + offZ, 0,    0,    1, 0, 0);

        // -X 面
        vert(pose, vc, -half + offX, yMin,  half + offZ, 0,    maxV, -1, 0, 0);
        vert(pose, vc, -half + offX, yMin, -half + offZ, maxU, maxV, -1, 0, 0);
        vert(pose, vc, -half + offX, yMax, -half + offZ, maxU, 0,    -1, 0, 0);
        vert(pose, vc, -half + offX, yMax,  half + offZ, 0,    0,    -1, 0, 0);

        // +Z 面（U 沿 X，V 沿 Y）
        vert(pose, vc,  half + offX, yMin, half + offZ, maxU, maxV, 0, 0, 1);
        vert(pose, vc, -half + offX, yMin, half + offZ, 0,    maxV, 0, 0, 1);
        vert(pose, vc, -half + offX, yMax, half + offZ, 0,    0,    0, 0, 1);
        vert(pose, vc,  half + offX, yMax, half + offZ, maxU, 0,    0, 0, 1);

        // -Z 面
        vert(pose, vc, -half + offX, yMin, -half + offZ, maxU, maxV, 0, 0, -1);
        vert(pose, vc,  half + offX, yMin, -half + offZ, 0,    maxV, 0, 0, -1);
        vert(pose, vc,  half + offX, yMax, -half + offZ, 0,    0,    0, 0, -1);
        vert(pose, vc, -half + offX, yMax, -half + offZ, maxU, 0,    0, 0, -1);

        // +Y 顶面（U 沿 X，V 沿 Z）
        vert(pose, vc,  half + offX, yMax,  half + offZ, 0,     0,     0, 1, 0);
        vert(pose, vc, -half + offX, yMax,  half + offZ, maxU, 0,     0, 1, 0);
        vert(pose, vc, -half + offX, yMax, -half + offZ, maxU, maxU, 0, 1, 0);
        vert(pose, vc,  half + offX, yMax, -half + offZ, 0, maxU, 0, 1, 0);

        // -Y 底面
        vert(pose, vc,  half + offX, yMin, -half + offZ, 0, maxU, 0, -1, 0);
        vert(pose, vc, -half + offX, yMin, -half + offZ, maxU, maxU, 0, -1, 0);
        vert(pose, vc, -half + offX, yMin,  half + offZ, maxU, 0,     0, -1, 0);
        vert(pose, vc,  half + offX, yMin,  half + offZ, 0,     0,     0, -1, 0);
    }

    private static void vert(PoseStack.Pose pose, VertexConsumer vc,
                             float x, float y, float z, float u, float v,
                             float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(1F, 1F, 1F, 1F)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(pose, nx, ny, nz);
    }
}