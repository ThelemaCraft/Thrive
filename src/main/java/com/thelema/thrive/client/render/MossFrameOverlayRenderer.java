package com.thelema.thrive.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelema.thelemalib.data.tool.LevelMapClient;
import com.thelema.thelemalib.item.ItemCustomData;
import com.thelema.thrive.generic.block.MossFrameEmpty;
import com.thelema.thrive.util.Flags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Map;

@EventBusSubscriber(value = Dist.CLIENT)
public class MossFrameOverlayRenderer {

    private static BakedModel frameModel = null;

    // 渲染世界中方块
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // 从客户端缓存获取同步过来的 Map<BlockPos, Short>
        Map<BlockPos, Short> markers = LevelMapClient.common(
                Level.OVERWORLD.location().toString(),
                MossFrameEmpty.KEY
        );
        if (markers == null || markers.isEmpty()) return;

        // 获取框架模型
        BakedModel model = mc.getModelManager().getModel(
                new ModelResourceLocation(ResourceLocation.parse("thrive:moss_frame_empty"), ""));

        PoseStack ps = event.getPoseStack();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        for (Map.Entry<BlockPos, Short> entry : markers.entrySet()) {
            // 只渲染状态为 1（显示）的方块，跳过 0（无）和 2（隐藏）
            if (entry.getValue() != MossFrameEmpty.STATE_MARKED) continue;
            BlockPos pos = entry.getKey();
            if (mc.level.getBlockState(pos).isAir()) continue;

            ps.pushPose();
            ps.translate(pos.getX(), pos.getY(), pos.getZ());
            ps.scale(1.02f, 1.02f, 1.02f);
            ps.translate(-0.01f, -0.01f, -0.01f);

            mc.getBlockRenderer().getModelRenderer().renderModel(
                    ps.last(),
                    mc.renderBuffers().bufferSource().getBuffer(RenderType.cutout()),
                    null, model,
                    1.0f, 1.0f, 1.0f,
                    LevelRenderer.getLightColor(mc.level, pos),
                    OverlayTexture.NO_OVERLAY
            );
            ps.popPose();
        }
        ps.popPose();
        mc.renderBuffers().bufferSource().endBatch(RenderType.cutout());
    }


    // 渲染物品栏物品的苔藓块背景
    @SubscribeEvent
    public static void registerItemDecorations(RegisterItemDecorationsEvent event) {

        IItemDecorator decorator = (guiGraphics, font, itemStack, x, y) -> {
            if (itemStack.isEmpty()) return false;
            if (!ItemCustomData.get(itemStack).getBoolean(Flags.CONTAINED_MOSS_FRAME)) {
                return false;
            }

            guiGraphics.blit(
                    ResourceLocation.parse("minecraft:textures/block/moss_block.png"),
                    x, y,           // 左上角坐标
                    0, 0,           // 纹理 UV 起始（如果你的 PNG 正好是 16×16，就写 0,0）
                    16, 16,         // 绘制尺寸
                    16, 16          // 纹理总尺寸
            );

            return true;
        };

        // 为所有物品注册
        for (Item item : BuiltInRegistries.ITEM) {
            event.register(item, decorator);
        }
    }
}