package com.thelema.thrive.client.render.effect;

import com.thelema.thrive.register.EffectRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public class BlindingRenderer {

    /** 平滑强度：0 = 无效果，1 = 完全白雾。 */
    private static float smoothed = 0F;

    /** 每 tick 变化量：1/60 → 3 秒到 1.0。 */
    private static final float RATE = 1f / 60f;

    private static final float TARGET_NEAR = 0.5F;
    private static final float TARGET_FAR  = 12.0F;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            smoothed = 0F;
            return;
        }

        MobEffectInstance inst = player.getEffect(EffectRegister.BLINDING);
        float target = inst == null
                ? 0F
                : Math.min(1.0F, 1.0F + inst.getAmplifier() * 0.3F);

        if (smoothed < target) smoothed = Math.min(target, smoothed + RATE);
        else if (smoothed > target) smoothed = Math.max(target, smoothed - RATE);
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (smoothed < 0.001F) return;
        if (!(event.getCamera().getEntity() instanceof Player)) return;

        event.setRed(Mth.lerp(smoothed, event.getRed(), 1.0F));
        event.setGreen(Mth.lerp(smoothed, event.getGreen(), 1.0F));
        event.setBlue(Mth.lerp(smoothed, event.getBlue(), 1.0F));
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (smoothed < 0.001F) return;
        if (!(event.getCamera().getEntity() instanceof Player)) return;

        float near = Mth.lerp(smoothed, event.getNearPlaneDistance(), TARGET_NEAR);
        float far  = Mth.lerp(smoothed, event.getFarPlaneDistance(),  TARGET_FAR);

        event.setNearPlaneDistance(near);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }
}