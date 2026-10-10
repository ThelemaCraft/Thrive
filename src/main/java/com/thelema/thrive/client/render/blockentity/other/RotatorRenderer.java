package com.thelema.thrive.client.render.blockentity.other;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelema.thrive.generic.blockentity.cap.Rotator;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Rotator 的渲染工具。静态方法，不继承、不注册。
 * 渲染逻辑抄自 ItemEntityRenderer，去掉上下浮动。
 */
public class RotatorRenderer {

    public static void render(Rotator rotator, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay,
                              ItemRenderer itemRenderer) {
        if (rotator.isEmpty()) return;

        BlockEntity owner = rotator.owner;
        Level level = owner.getLevel();

        if (level != null) {
            light = LevelRenderer.getLightColor(level, owner.getBlockPos().above());
        }

        ItemStack stack = rotator.item;
        BakedModel model = itemRenderer.getModel(stack, level, null, 0);
        boolean gui3d = model.isGui3d();

        // 模型自身的 ground 变换 y 缩放，用来决定最终的垂直微调
        float scaleY = model.getTransforms().getTransform(ItemDisplayContext.GROUND).scale.y();

        float angle = Mth.lerp(partialTicks, rotator.prevRotation, rotator.rotation);

        ms.pushPose();
        ms.translate(0.5f, rotator.height + 0.25f * scaleY, 0.5f);
        ms.mulPose(Axis.YP.rotationDegrees(angle));

        renderStack(itemRenderer, ms, buffer, light, overlay, stack, model, gui3d);

        ms.popPose();
    }

    /** 按数量叠放渲染。抄 ItemEntityRenderer.renderMultipleFromCount，去掉可散布判断。 */
    private static void renderStack(ItemRenderer itemRenderer, PoseStack ms, MultiBufferSource buffer,
                                    int light, int overlay, ItemStack stack, BakedModel model, boolean gui3d) {
        int count = getRenderedAmount(stack.getCount());
        float sx = model.getTransforms().ground.scale.x();
        float sy = model.getTransforms().ground.scale.y();
        float sz = model.getTransforms().ground.scale.z();

        // 非 gui3d（扁平物品）整体先平移，让叠放居中
        if (!gui3d) {
            ms.translate(-0.0F * (count - 1) * 0.5F * sx,
                         -0.0F * (count - 1) * 0.5F * sy,
                         -0.09375F * (count - 1) * 0.5F * sz);
        }

        RandomSource random = RandomSource.create(0);

        for (int i = 0; i < count; i++) {
            ms.pushPose();

            if (i > 0) {
                if (gui3d) {
                    float dx = (random.nextFloat() * 2f - 1f) * 0.15f;
                    float dy = (random.nextFloat() * 2f - 1f) * 0.15f;
                    float dz = (random.nextFloat() * 2f - 1f) * 0.15f;
                    ms.translate(dx, dy, dz);
                } else {
                    float dx = (random.nextFloat() * 2f - 1f) * 0.15f * 0.5f;
                    float dy = (random.nextFloat() * 2f - 1f) * 0.15f * 0.5f;
                    ms.translate(dx, dy, 0f);
                }
            }

            itemRenderer.render(stack, ItemDisplayContext.GROUND, false,
                    ms, buffer, light, overlay, model);
            ms.popPose();

            if (!gui3d) {
                ms.translate(0f, 0f, 0.09375f * sz);
            }
        }
    }

    private static int getRenderedAmount(int count) {
        if (count <= 1) return 1;
        if (count <= 16) return 2;
        if (count <= 32) return 3;
        if (count <= 48) return 4;
        return 5;
    }
}