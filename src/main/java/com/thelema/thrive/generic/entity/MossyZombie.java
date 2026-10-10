package com.thelema.thrive.generic.entity;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

public class MossyZombie extends Zombie {

    public MossyZombie(EntityType<? extends MossyZombie> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        // 每秒，给予 2s 生命恢复 IV
        if (isAlive() && tickCount % 20 == 0 && getRemainingFireTicks() <= 0){
            addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION,
                    40,
                    3,
                    true,
                    true
            ));
        }
        // 被火伤害失效
        if (isAlive() && getRemainingFireTicks() > 0){
            removeEffect(MobEffects.REGENERATION);
        }
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }
}