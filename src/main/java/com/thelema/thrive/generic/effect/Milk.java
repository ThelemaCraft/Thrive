package com.thelema.thrive.generic.effect;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.register.EffectRegister;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.EffectCure;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

import java.util.Set;

@EventBusSubscriber(modid = ThriveMod.MOD_ID)
public class Milk extends MobEffect {

    private static boolean isInternalUpdate = false;

    public Milk() {
        super(MobEffectCategory.NEUTRAL, 0xFFFFFFFF);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        // 牛奶自身不可被牛奶清除
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        if (isInternalUpdate) return;

        int totalCost = 0;
        // 遍历身上所有的效果，累加需要消耗的牛奶时间
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            if (instance.getEffect().value() != this && instance.getCures().contains(EffectCures.MILK)) {
                totalCost += calculateCost(instance);
            }
        }

        // 如果确实清除了效果，扣除累加的时间
        if (totalCost > 0) {
            entity.removeEffectsCuredBy(EffectCures.MILK);
            tryConsumeMilk(entity, totalCost);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity().level().isClientSide()) return;
        if (isInternalUpdate) return;

        MobEffectInstance added = event.getEffectInstance();
        if (added == null || added.getEffect().value() instanceof Milk) return;

        if (event.getEntity().hasEffect(EffectRegister.MILK) && added.getCures().contains(EffectCures.MILK)) {
            int cost = calculateCost(added);
            if (tryConsumeMilk(event.getEntity(), cost)) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        }
    }

    /**
     * 计算抵消该效果需要消耗的牛奶时间。
     * 普通效果：1 tick 抵消 1 tick。
     * 瞬间效果：1 点数值抵消 400 tick (20秒)。
     */
    private static int calculateCost(MobEffectInstance instance) {
        MobEffect effect = instance.getEffect().value();
        if (effect.isInstantenous()) {
            return getInstantEffectValue(instance) * 400; // 微调：1点数值 = 20s = 400tick
        } else {
            return instance.getDuration();
        }
    }

    /**
     * 获取瞬间效果的数值。
     * 原版瞬间治疗 I 级为 4，II 级为 8；瞬间伤害 I 级为 6，II 级为 12。
     * 自定义瞬间效果默认以等级 (amplifier + 1) 作为数值。
     */
    private static int getInstantEffectValue(MobEffectInstance instance) {
        int amp = instance.getAmplifier() + 1;
        // 用 Holder.is(Holder) 比较，或者直接比较 value()
        if (instance.getEffect().is(MobEffects.HEAL)) {
            return 4 * amp;
        }
        if (instance.getEffect().is(MobEffects.HARM)) {
            return 6 * amp;
        }
        return amp;
    }

    /**
     * 扣除牛奶指定时间。
     * @return 是否成功扣除（如果身上没牛奶则返回 false）
     */
    public static boolean tryConsumeMilk(LivingEntity entity, int cost) {
        MobEffectInstance milk = entity.getEffect(EffectRegister.MILK);
        if (milk == null) return false;

        int newDuration = milk.getDuration() - cost;

        entity.removeEffect(EffectRegister.MILK);

        if (newDuration > 0) {
            isInternalUpdate = true;
            entity.addEffect(new MobEffectInstance(
                    milk.getEffect(),
                    newDuration,
                    milk.getAmplifier(),
                    milk.isAmbient(),
                    milk.isVisible(),
                    milk.showIcon()
            ));
            isInternalUpdate = false;
        }

        return true;
    }
}