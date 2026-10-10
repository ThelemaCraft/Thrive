package com.thelema.thrive.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelema.thrive.client.render.blockentity.other.RotatorRenderer;
import com.thelema.thrive.client.render.blockentity.other.SurfaceRenderer;
import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;

public class BiomeCoreBlockEntityRenderer implements BlockEntityRenderer<BiomeCoreBlockEntity> {

    private final ItemRenderer itemRenderer;

    public BiomeCoreBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(BiomeCoreBlockEntity be, float partialTicks, PoseStack ms,
                       MultiBufferSource buffer, int light, int overlay) {
        SurfaceRenderer.render(be.surface, partialTicks, ms, buffer, light, overlay,
                itemRenderer, 1.12f);
        RotatorRenderer.render(be.rotator, partialTicks, ms, buffer, light, overlay,
                itemRenderer);
    }
}