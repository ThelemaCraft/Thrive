package com.thelema.thrive.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thelema.thrive.generic.blockentity.MossFluidTankBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

@OnlyIn(Dist.CLIENT)
public class MossFluidTankRender implements BlockEntityRenderer<MossFluidTankBlockEntity> {

    public MossFluidTankRender(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(MossFluidTankBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {


        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        // ----- 渲染物品 -----
        ItemStack stack = be.getStackInSlot(0);
        if (!stack.isEmpty()) {
            poseStack.pushPose();
            // 固定位置：方块正中心 (x=0.5, y=0.5, z=0.5)
            poseStack.translate(0.5, 0.5, 0.5);
            // 缩放大小（可根据需要调整，0.5f 合适）
            poseStack.scale(0.5f, 0.5f, 0.5f);
            // 缓慢旋转（保留）
            float angle = (System.currentTimeMillis() % 3600) / 10f;
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));

            ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
            itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, buffer, be.getLevel(), 0);
            poseStack.popPose();
        }
        RenderSystem.depthMask(false);

        // 渲染流体
        FluidStack fluid = be.getFluidInTank(0);
        if (fluid.isEmpty()) return;

        float fillRatio = (float) fluid.getAmount() / MossFluidTankBlockEntity.CAPACITY;
        if (fillRatio <= 0.001f) return;


        // 获取流体纹理扩展
        IClientFluidTypeExtensions clientExt = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite still = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(clientExt.getStillTexture(fluid));
        if (still == null) return;

        // 获取颜色并转为 RGB
        int color = clientExt.getTintColor(fluid);
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = 0.9f;   // 轻微半透明，让液体更有质感

        // 液体绘制范围（留出边框，避免与玻璃面重合产生Z-fighting）
        float minX = 0.02f, maxX = 0.98f;
        float minZ = 0.02f, maxZ = 0.98f;
        float minY = 0.02f;
        float maxY = 0.02f + fillRatio * 0.96f;

        poseStack.pushPose();
        // 注意：BlockEntityRenderer 已经平移到方块位置，无需额外 translate
        VertexConsumer builder = buffer.getBuffer(RenderType.translucent());
        // 绘制立方
        drawFluidBox(builder, poseStack, still, r, g, b, a, packedLight, packedOverlay,
                minX, minY, minZ, maxX, maxY, maxZ);
        poseStack.popPose();


    }

    // 绘制立方
    private void drawFluidBox(VertexConsumer builder, PoseStack poseStack, TextureAtlasSprite sprite,
                              float r, float g, float b, float a, int packedLight, int packedOverlay,
                              float x1, float y1, float z1, float x2, float y2, float z2) {
        addTexturedFace(builder, poseStack, sprite, Direction.DOWN, r, g, b, a, packedLight, packedOverlay, x1, y1, z1, x2, y1, z2);
        addTexturedFace(builder, poseStack, sprite, Direction.UP,   r, g, b, a, packedLight, packedOverlay, x1, y2, z1, x2, y2, z2);
        addTexturedFace(builder, poseStack, sprite, Direction.NORTH, r, g, b, a, packedLight, packedOverlay, x1, y1, z1, x2, y2, z1);
        addTexturedFace(builder, poseStack, sprite, Direction.SOUTH, r, g, b, a, packedLight, packedOverlay, x1, y1, z2, x2, y2, z2);
        addTexturedFace(builder, poseStack, sprite, Direction.WEST,  r, g, b, a, packedLight, packedOverlay, x1, y1, z1, x1, y2, z2);
        addTexturedFace(builder, poseStack, sprite, Direction.EAST,  r, g, b, a, packedLight, packedOverlay, x2, y1, z1, x2, y2, z2);
    }

    // 绘制一个面
    private void addTexturedFace(VertexConsumer builder, PoseStack poseStack, TextureAtlasSprite sprite,
                                 Direction face, float r, float g, float b, float a,
                                 int packedLight, int packedOverlay,
                                 float x1, float y1, float z1, float x2, float y2, float z2) {
        PoseStack.Pose pose = poseStack.last();
        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();

        float minU = sprite.getU0();
        float maxU = sprite.getU1();
        float minV = sprite.getV0();
        float maxV = sprite.getV1();

        switch (face) {
            case DOWN -> {

                builder.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(maxU, maxV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x2, y1, z1).setColor(r, g, b, a).setUv(minU, maxV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x2, y1, z2).setColor(r, g, b, a).setUv(minU, minV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x1, y1, z2).setColor(r, g, b, a).setUv(maxU, minV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
            }
            case UP -> {
                builder.addVertex(pose, x1, y2, z1).setColor(r, g, b, a).setUv(maxU, maxV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x1, y2, z2).setColor(r, g, b, a).setUv(maxU, minV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(minU, minV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x2, y2, z1).setColor(r, g, b, a).setUv(minU, maxV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
            }
            case NORTH -> {
                builder.addVertex(pose, x1, y2, z1).setColor(r, g, b, a).setUv(maxU, maxV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x2, y2, z1).setColor(r, g, b, a).setUv(minU, maxV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x2, y1, z1).setColor(r, g, b, a).setUv(minU, minV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(maxU, minV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
            }
            case SOUTH -> {
                builder.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(minU, maxV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x1, y2, z2).setColor(r, g, b, a).setUv(maxU, maxV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x1, y1, z2).setColor(r, g, b, a).setUv(maxU, minV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x2, y1, z2).setColor(r, g, b, a).setUv(minU, minV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
            }
            case WEST -> {
                builder.addVertex(pose, x1, y2, z2).setColor(r, g, b, a).setUv(maxU, maxV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x1, y2, z1).setColor(r, g, b, a).setUv(minU, maxV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(minU, minV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x1, y1, z2).setColor(r, g, b, a).setUv(maxU, minV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
            }
            case EAST -> {
                builder.addVertex(pose, x2, y2, z1).setColor(r, g, b, a).setUv(maxU, maxV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(minU, maxV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x2, y1, z2).setColor(r, g, b, a).setUv(minU, minV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
                builder.addVertex(pose, x2, y1, z1).setColor(r, g, b, a).setUv(maxU, minV).setLight(packedLight).setOverlay(packedOverlay).setNormal(pose, nx, ny, nz);
            }
        }
    }
}