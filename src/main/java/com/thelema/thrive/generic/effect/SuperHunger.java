package com.thelema.thrive.generic.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;

public class SuperHunger extends MobEffect {

    public SuperHunger() {
        super(MobEffectCategory.HARMFUL, 0xFF006400);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity living, int amplifier) {
        if (living instanceof ServerPlayer player) {
            FoodData foodData = player.getFoodData();
            foodData.setFoodLevel(Math.max(0, foodData.getFoodLevel() - 1));
            foodData.setSaturation(Math.max(0, foodData.getSaturationLevel() - 1));
        } else {
            living.hurt(living.damageSources().starve(), 1.0F);
        }
        return true;
    }
}
