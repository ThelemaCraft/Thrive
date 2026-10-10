package com.thelema.thrive.generic.effect;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

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
            if (entity.getRemainingFireTicks() >= 20){
                entity.playSound(SoundEvents.FIRE_EXTINGUISH);
            }
        }
        return true;
    }

    @Override
    public void onMobHurt(LivingEntity living, int amplifier, DamageSource damageSource, float amount) {
        // 如果是火焰伤害，减半
        if (damageSource.is(DamageTypeTags.IS_FIRE)) {
            amount = amount * 0.5f;
        }
        super.onMobHurt(living, amplifier, damageSource, amount);
    }

}