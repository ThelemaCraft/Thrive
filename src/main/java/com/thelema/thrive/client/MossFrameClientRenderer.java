package com.thelema.thrive.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelema.thelemalib.data.tool.LevelMapClientCache;
import com.thelema.thelemalib.data.tool.MapTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import static com.thelema.thrive.util.Flags.CONTAINED_MOSS_FRAME;

@EventBusSubscriber(value = Dist.CLIENT)
public class MossFrameClientRenderer {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();

        CompoundTag cached = LevelMapClientCache.get(CONTAINED_MOSS_FRAME);
        if (cached == null || cached.isEmpty()) return;

        HolderLookup.Provider provider = mc.level.registryAccess();
        MapTag<BlockPos, Boolean> map = MapTag.wrap(cached, provider);

        // 获取烘焙模型
        BakedModel model = mc.getModelManager().getModel(
                new ModelResourceLocation(
                        ResourceLocation.parse("thrive:moss_frame_empty"), ""));


        PoseStack ps = event.getPoseStack();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        for (BlockPos pos : map.keySet()) {
            if (mc.level.getBlockState(pos).isAir()) continue;
            ps.pushPose();
            ps.translate(pos.getX(), pos.getY(), pos.getZ());
            ps.scale(1.02f, 1.02f, 1.02f);
            ps.translate(-0.01f, -0.01f, -0.01f);
            mc.getBlockRenderer().getModelRenderer().renderModel(
                    ps.last(),
                    mc.renderBuffers().bufferSource().getBuffer(RenderType.cutout()),
                    null,
                    model,
                    1.0f, 1.0f, 1.0f,
                    0xF000F0,
                    OverlayTexture.NO_OVERLAY
            );
            ps.popPose();
        }
        ps.popPose();
        mc.renderBuffers().bufferSource().endBatch(RenderType.cutout());
    }
}