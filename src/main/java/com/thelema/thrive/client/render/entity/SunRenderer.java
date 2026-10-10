package com.thelema.thrive.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thelema.thrive.generic.entity.Sun;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

public class SunRenderer extends EntityRenderer<Sun> {

    private static final double RING_ROTATION_SPEED = 0.05;

    public SunRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(Sun entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        float age = entity.tickCount + partialTick;

        // ===== 火焰环：整齐划一 =====
        renderFlameRing(poseStack, buffer, 10.0, 45.0, age, 24);
        renderFlameRing(poseStack, buffer, 20.0, -45.0, age, 48);

        // ===== 4 个光扇：每个独立参数 =====
        for (int i = 0; i < 4; i++) {
            // 用 entity id + index 派生稳定随机，不同实体不同、同一实体每帧一致
            Random rand = new Random(entity.getId() * 7919L + i * 131L + 17L);

            Vector3f axis = new Vector3f(
                    rand.nextFloat() * 2 - 1,
                    rand.nextFloat() * 2 - 1,
                    rand.nextFloat() * 2 - 1
            ).normalize();

            float phase     = rand.nextFloat() * (float) (Math.PI * 2);   // 起始角
            float spreadDeg = 50F + rand.nextFloat() * 40F;               // 张角 50~90°
            float radius    = 24F + rand.nextFloat() * 16F;               // 半径 24~40
            float speedMul  = 0.7F + rand.nextFloat() * 0.6F;             // 转速倍率 0.7~1.3
            boolean reverse = rand.nextBoolean();                          // 慢旋转方向

            renderFan(poseStack, buffer, axis, age, phase, spreadDeg, radius, speedMul, reverse);
        }

        // ===== 太阳本体 =====
        renderSunBillboard(poseStack, buffer);

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** 整齐的火焰环 */
    private void renderFlameRing(PoseStack poseStack, MultiBufferSource buffer,
                                 double radius, double tiltDeg, float age, int count) {
        double tilt = Math.toRadians(tiltDeg);
        double sinT = Math.sin(tilt);
        double cosT = Math.cos(tilt);

        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Quaternionf camRot = this.entityRenderDispatcher.cameraOrientation();

        for (int i = 0; i < count; i++) {
            double baseAngle = (i / (double) count) * Math.PI * 2;
            double theta = baseAngle + age * RING_ROTATION_SPEED;

            double x = radius * Math.cos(theta);
            double z0 = radius * Math.sin(theta);
            double y = -z0 * sinT;
            double z = z0 * cosT;

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(camRot);

            Matrix4f mat = poseStack.last().pose();
            float s = 0.45F;

            consumer.addVertex(mat, -s, -s, 0).setColor(1F, 0.55F, 0.15F, 1F);
            consumer.addVertex(mat, s, -s, 0).setColor(1F, 0.55F, 0.15F, 1F);
            consumer.addVertex(mat, s, s, 0).setColor(1F, 0.55F, 0.15F, 1F);
            consumer.addVertex(mat, -s, s, 0).setColor(1F, 0.55F, 0.15F, 1F);

            poseStack.popPose();
        }
    }

    /** 太阳本体 */
    private void renderSunBillboard(PoseStack poseStack, MultiBufferSource buffer) {
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f mat = poseStack.last().pose();

        float glow = 5.5F;
        float glowZ = 0.05F;
        consumer.addVertex(mat, -glow, -glow, glowZ).setColor(1F, 0.85F, 0.35F, 0.35F);
        consumer.addVertex(mat, glow, -glow, glowZ).setColor(1F, 0.85F, 0.35F, 0.35F);
        consumer.addVertex(mat, glow, glow, glowZ).setColor(1F, 0.85F, 0.35F, 0.35F);
        consumer.addVertex(mat, -glow, glow, glowZ).setColor(1F, 0.85F, 0.35F, 0.35F);

        float core = 3.0F;
        float coreZ = -0.05F;
        consumer.addVertex(mat, -core, -core, coreZ).setColor(1F, 1F, 0.85F, 1F);
        consumer.addVertex(mat, core, -core, coreZ).setColor(1F, 1F, 0.85F, 1F);
        consumer.addVertex(mat, core, core, coreZ).setColor(1F, 1F, 0.85F, 1F);
        consumer.addVertex(mat, -core, core, coreZ).setColor(1F, 1F, 0.85F, 1F);

        poseStack.popPose();
    }

    /**
     * 光扇：每个扇形独立相位 / 张角 / 半径 / 转速 / 边缘抖动
     */
    private void renderFan(PoseStack poseStack, MultiBufferSource buffer, Vector3f axis,
                           float age, float phase, float spreadDeg, float radius,
                           float speedMul, boolean reverse) {
        poseStack.pushPose();

        // 快速绕轴旋转：每个扇形起始相位不同
        float fastAngle = phase + (float) (age * Math.PI * 2.0 / 200.0) * speedMul;
        poseStack.mulPose(new Quaternionf().rotationAxis(fastAngle, axis));

        // 慢速 Z 旋转：方向也可以不同
        float slowAngle = (float) (age * Math.PI * 2.0 / 400.0) * (reverse ? -1F : 1F);
        poseStack.mulPose(Axis.ZP.rotation(slowAngle));

        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f mat = poseStack.last().pose();

        int segments = 16;
        float halfAngle = spreadDeg * 0.5F;

        // 边缘抖动的种子：由相位和半径派生，每个扇形不同但每帧稳定
        Random edgeRand = new Random(
                Float.floatToIntBits(phase) * 31L + Float.floatToIntBits(radius)
        );

        // 预先算好每个顶点半径，边不齐才像自然光
        float[] r = new float[segments + 1];
        for (int i = 0; i <= segments; i++) {
            r[i] = radius * (0.82F + edgeRand.nextFloat() * 0.36F);
        }

        for (int i = 0; i < segments; i++) {
            float a1 = (float) Math.toRadians(-halfAngle + i * (halfAngle * 2 / segments));
            float a2 = (float) Math.toRadians(-halfAngle + (i + 1) * (halfAngle * 2 / segments));

            float x1 = (float) Math.cos(a1) * r[i];
            float y1 = (float) Math.sin(a1) * r[i];
            float x2 = (float) Math.cos(a2) * r[i + 1];
            float y2 = (float) Math.sin(a2) * r[i + 1];

            // 顶点透明度也加轻微抖动，边缘更软
            float alphaTip1 = 0F;
            float alphaTip2 = 0F;

            consumer.addVertex(mat, 0, 0, 0).setColor(1F, 0.95F, 0.7F, 0.75F);
            consumer.addVertex(mat, x1, y1, 0).setColor(1F, 0.95F, 0.7F, alphaTip1);
            consumer.addVertex(mat, x2, y2, 0).setColor(1F, 0.95F, 0.7F, alphaTip2);
            consumer.addVertex(mat, x2, y2, 0).setColor(1F, 0.95F, 0.7F, alphaTip2);
        }

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(Sun entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}