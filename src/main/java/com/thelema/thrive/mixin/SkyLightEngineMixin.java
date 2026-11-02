package com.thelema.thrive.mixin;

import com.thelema.thrive.generic.block.other.CloudLightHelper;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkyLightEngine.class)
public abstract class SkyLightEngineMixin {

    @Inject(method = "getLowestSourceY(III)I", at = @At("RETURN"), cancellable = true)
    private void thrive$cloudAsSkySource(int x, int z, int def, CallbackInfoReturnable<Integer> cir) {
        LightChunkGetter cg = ((LightEngineMixin) this).getChunkSource();
        int low = CloudLightHelper.cloudLowestY(cg.getLevel(), x, z);
        if (low != CloudLightHelper.NO_CLOUD) cir.setReturnValue(Math.min(cir.getReturnValue(), low));
    }

    @Inject(method = "updateSourcesInColumn", at = @At("HEAD"), cancellable = true)
    private void thrive$rewriteColumn(int x, int z, int lowestY, CallbackInfo ci) {
        LightEngineMixin acc = (LightEngineMixin) this;
        LayerLightSectionStorageInvoker sinv = (LayerLightSectionStorageInvoker) acc.getStorage();

        BlockGetter level = acc.getChunkSource().getLevel();

        int topY = level.getMaxBuildHeight() - 1;
        int bottomY = level.getMinBuildHeight();
        boolean directSky = true;

        for (int y = topY; y >= bottomY; y--) {
            long sectionPos = SectionPos.asLong(x >> 4, y >> 4, z >> 4);
            if (!sinv.invokeStoringLightForSection(sectionPos)) continue;   // 跳过未启用存储的 section

            BlockPos p = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(p);

            if (state.is(BlockRegister.CLOUD.get()) || state.is(BlockRegister.WISPY_CLOUD.get())) directSky = true;
            else if (state.getLightBlock(level, p) > 0) directSky = false;

            long packed = p.asLong();
            int desired = directSky ? 15 : 0;
            int current = sinv.invokeGetStoredLevel(packed);
            if (current == desired) continue;

            sinv.invokeSetStoredLevel(packed, desired);
            if (desired > current) {
                acc.invokeEnqueueIncrease(packed,
                        LightEngine.QueueEntry.increaseSkipOneDirection(desired, false, Direction.UP));
            } else {
                acc.invokeEnqueueDecrease(packed,
                        LightEngine.QueueEntry.decreaseAllDirections(current));
            }
        }
        ci.cancel();
    }
}