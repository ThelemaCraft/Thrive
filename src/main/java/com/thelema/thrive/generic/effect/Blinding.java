package com.thelema.thrive.generic.effect;

import com.thelema.thrive.register.EffectRegister;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

@EventBusSubscriber
public class Blinding extends MobEffect {

    public Blinding() {
        super(MobEffectCategory.HARMFUL, 0xFFFFD700);

    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity attacker = event.getEntity();
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null) return;

        MobEffectInstance effect = target.getEffect(EffectRegister.BLINDING);
        if (effect == null) return;

        // 每级降低索敌范围：基础 16 → 带效果后每级 -4
        double baseRange = attacker.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        double reduced = Math.max(2.0, baseRange - 4.0 * (effect.getAmplifier() + 1));

        if (attacker.distanceTo(target) > reduced) {
            event.setCanceled(true);
        }
    }
}