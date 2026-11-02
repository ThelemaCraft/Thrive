package com.thelema.thrive.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thelema.thrive.generic.entity.projectile.WindBlade;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class WindBladeRenderer extends EntityRenderer<WindBlade> {

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath("thrive", "textures/entity/wind_blade.png");

    /** 像素 (5,5) 在 -0.5~0.5 的 quad 坐标中。 */
    private static final float PIVOT_X = -0.5F + 5F / 16F;   // -0.125
    private static final float PIVOT_Y =  0.5F - 5F / 16F;   //  0.125

    public WindBladeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(WindBlade entity, float yaw, float partialTick,
                       PoseStack pose, MultiBufferSource buf, int light) {
        pose.pushPose();

        // 1. 侧面面向敌人（利用实体 Yaw 旋转 + 90 度侧立）
        pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot() + 90.0F));

        float t = entity.tickCount + partialTick;
        float angle = t * 60.0F;

        // 3. 偏心旋转轴
        pose.translate(PIVOT_X, PIVOT_Y, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        pose.translate(-PIVOT_X, -PIVOT_Y, 0);

        // 4. 渲染 1x1 平面
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(TEX));

        vc.addVertex(m, -0.5F, -0.5F, 0).setColor(255, 255, 255, 255).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);
        vc.addVertex(m,  0.5F, -0.5F, 0).setColor(255, 255, 255, 255).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);
        vc.addVertex(m,  0.5F,  0.5F, 0).setColor(255, 255, 255, 255).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);
        vc.addVertex(m, -0.5F,  0.5F, 0).setColor(255, 255, 255, 255).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 0, 1);

        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(WindBlade entity) {
        return TEX;
    }
}