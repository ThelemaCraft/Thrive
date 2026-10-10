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

    /** 平滑强度：0 = 无效果，1 = 完全白雾。每 tick 向目标逼近。 */
    private static float smoothed = 0F;

    // 淡入慢一点、淡出快一点，视觉上更自然
    private static final float FADE_IN_RATE  = 0.08F; // ~37 tick 到 95%
    private static final float FADE_OUT_RATE = 0.15F; // ~20 tick 到 95%

    // 完全生效时的目标雾参数
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
        float target = 0F;
        if (inst != null) {
            // amplifier 0 → 1.0；1 → 1.3；2 → 1.6 ...（上限 1.0）
            target = Math.min(1.0F, 1.0F + inst.getAmplifier() * 0.3F);
        }

        float rate = (target > smoothed) ? FADE_IN_RATE : FADE_OUT_RATE;
        smoothed += (target - smoothed) * rate;

        // 足够接近时直接吸附，避免无限逼近残留小数值
        if (Math.abs(target - smoothed) < 0.002F) {
            smoothed = target;
        }
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (smoothed < 0.001F) return;
        if (!(event.getCamera().getEntity() instanceof Player)) return;

        // 当前雾色 → 白色，按 smoothed 比例插值
        event.setRed(Mth.lerp(smoothed, event.getRed(), 1.0F));
        event.setGreen(Mth.lerp(smoothed, event.getGreen(), 1.0F));
        event.setBlue(Mth.lerp(smoothed, event.getBlue(), 1.0F));
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (smoothed < 0.001F) return;
        if (!(event.getCamera().getEntity() instanceof Player)) return;

        // 原版雾参数 → 目标值，按 smoothed 比例插值
        float near = Mth.lerp(smoothed, event.getNearPlaneDistance(), TARGET_NEAR);
        float far  = Mth.lerp(smoothed, event.getFarPlaneDistance(),  TARGET_FAR);

        event.setNearPlaneDistance(near);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }
}