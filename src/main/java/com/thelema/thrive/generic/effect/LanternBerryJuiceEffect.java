package com.thelema.thrive.generic.effect;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.register.EffectRegister;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = ThriveMod.MOD_ID)
public class LanternBerryJuiceEffect extends MobEffect {

    public LanternBerryJuiceEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFD700);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            entity.setRemainingFireTicks(0);
        }
        return true;
    }

    /**
     * 火伤减免：消耗 buff 剩余时间换取当次伤害减免。
     *
     * 减免比例 R = clamp(1 - D/100, 0.6, 1.0)
     *   D=0   → 1.0
     *   D=20  → 0.8
     *   D≥40  → 0.6
     *
     * 消耗 tick = min(20, ceil(D × R))
     *   D=20, R=0.8 → 16 tick
     *   D≥40, R=0.6 → 20 tick（上限 1 秒）
     *
     * 剩余时间不足时，按剩余比例缩减实际减免。
     *
     *  火伤 D	减免 R	消耗 tick	实际伤害
     *  4	    0.96	96	            0.16
     *  10	    0.90	225	            1.0
     *  20	    0.80	400	            4.0
     *  40	    0.60	600	            16.0
     *  100	    0.60	1500	        40.0
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypeTags.IS_FIRE)) return;

        LivingEntity entity = event.getEntity();
        MobEffectInstance inst = entity.getEffect(EffectRegister.LANTERN_BERRY_JUICE);
        if (inst == null || inst.getDuration() <= 0) return;

        float D = event.getAmount();
        float R = Math.max(0.6f, Math.min(1.0f, 1.0f - D / 100f));

        int want = (int) Math.ceil(D * R * 25);
        int have = inst.getDuration();

        // 时间不足：按比例缩减免
        float actualR = want <= have ? R : R * (float) have / want;
        int actualConsume = Math.min(want, have);

        event.setAmount(D * (1.0f - actualR));

        int newDuration = have - actualConsume;
        entity.removeEffect(EffectRegister.LANTERN_BERRY_JUICE);
        if (newDuration > 0) {
            entity.addEffect(new MobEffectInstance(
                    EffectRegister.LANTERN_BERRY_JUICE,
                    newDuration,
                    inst.getAmplifier(),
                    inst.isAmbient(),
                    inst.isVisible(),
                    inst.showIcon()));
        }
    }
}