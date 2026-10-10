package com.thelema.thrive.client.render.blockentity.other;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelema.thrive.generic.blockentity.cap.Surface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Surface 方块上的物品渲染工具。
 * 不继承、不注册，纯静态方法。
 */
public class SurfaceRenderer {

    public static void render(Surface surface, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay,
                              ItemRenderer itemRenderer, float itemHeight) {

        if (surface.isEmpty()) return;

        BlockEntity owner = surface.owner;
        Level level = owner.getLevel();
        BlockPos pos = owner.getBlockPos();

        if (level != null) {
            light = LevelRenderer.getLightColor(level, pos.above());
        }

        Surface.TransportedItemStack tis = surface.item;
        Vec3 itemPosition = Vec3.atCenterOf(pos);

        ms.pushPose();
        ms.translate(0.5f, itemHeight, 0.5f);

        float offset = Mth.lerp(partialTicks, tis.prevBeltPosition, tis.beltPosition);
        float sideOffset = Mth.lerp(partialTicks, tis.prevSideOffset, tis.sideOffset);

        if (tis.insertedFrom.getAxis().isHorizontal()) {
            Vec3 offsetVec = Vec3.atLowerCornerOf(
                    tis.insertedFrom.getOpposite().getNormal()
            ).scale(0.5f - offset);
            ms.translate(offsetVec.x, offsetVec.y, offsetVec.z);

            boolean alongX = tis.insertedFrom.getClockWise().getAxis() == Direction.Axis.X;
            if (!alongX) sideOffset *= -1;
            ms.translate(alongX ? sideOffset : 0, 0, alongX ? 0 : sideOffset);
        }

        renderItem(ms, buffer, light, overlay, tis.stack, tis.angle, itemPosition, itemRenderer);

        ms.popPose();
    }

    private static void renderItem(PoseStack ms, MultiBufferSource buffer, int light, int overlay,
                                   ItemStack stack, int angle, Vec3 itemPosition,
                                   ItemRenderer itemRenderer) {

        int count = Mth.log2(stack.getCount()) / 2;
        BakedModel bakedModel = itemRenderer.getModel(stack, null, null, 0);
        boolean blockItem = bakedModel.isGui3d();
        boolean upright = Surface.TransportedItemStack.isItemUpright(stack);
        Random r = new Random(0);

        ms.pushPose();
        ms.mulPose(Axis.YP.rotationDegrees(angle));

        if (upright) {
            Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 diff = itemPosition.subtract(cameraPos);
            float yRot = (float) (Mth.atan2(diff.x, diff.z) + Math.PI);
            ms.mulPose(Axis.YP.rotation(yRot));
            ms.translate(0, 3 / 32f, -1 / 16f);
        }

        for (int i = 0; i <= count; i++) {
            ms.pushPose();

            if (blockItem) {
                ms.translate(r.nextFloat() * .0625f * i, 0, r.nextFloat() * .0625f * i);
            }

            ms.scale(0.5f, 0.5f, 0.5f);

            if (!blockItem && !upright) {
                ms.translate(0, -3 / 16f, 0);
                ms.mulPose(Axis.XP.rotationDegrees(90));
            }

            itemRenderer.render(stack, ItemDisplayContext.FIXED, false,
                    ms, buffer, light, overlay, bakedModel);
            ms.popPose();

            if (!upright) {
                if (!blockItem) ms.mulPose(Axis.YP.rotationDegrees(10));
                ms.translate(0, blockItem ? 1 / 64d : 1 / 32d, 0);
            } else {
                ms.translate(0, 0, -1 / 16f);
            }
        }

        ms.popPose();
    }
}