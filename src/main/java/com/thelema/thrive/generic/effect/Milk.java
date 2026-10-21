package com.thelema.thrive.generic.effect;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCure;
import net.neoforged.neoforge.common.EffectCures;

/**
 * 牛奶：每 tick 清除实体身上"能被原版奶桶清除"的 buff。
 *
 * 清除条件直接复用奶桶的判定：{@code activeEffects} 中 cure 集合包含
 * {@link EffectCures#MILK} 的效果（即 {@link LivingEntity#removeEffectsCuredBy} 会移除的那些）。
 *
 * 牛奶自身通过覆写 {@link #fillEffectCures} 不加入任何 cure，
 * 因此它不会被奶桶清除，也不会在自身 tick 时把自己清掉。
 */
public class Milk extends MobEffect {

    public Milk() {
        super(MobEffectCategory.BENEFICIAL, 0xFFFFFFFF);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        // 不加入 EffectCures.MILK —— 牛奶效果免疫奶桶。
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            entity.getActiveEffects()
                    .stream()
                    .filter(inst -> inst.getCures().contains(EffectCures.MILK))
                    .map(MobEffectInstance::getEffect)
                    .toList()   // 先收集，再删除，避免边遍历边改集合
                    .forEach(entity::removeEffect);
        }
        return true;
    }
}
