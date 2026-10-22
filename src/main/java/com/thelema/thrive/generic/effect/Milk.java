package com.thelema.thrive.generic.effect;

import java.util.Set;

import com.thelema.thrive.register.EffectRegister;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.EffectCure;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

@EventBusSubscriber
public class Milk extends MobEffect {

    public Milk() {
        super(MobEffectCategory.NEUTRAL, 0xFFFFFFFF);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        // 牛奶效果不加入任何 cure，所以自身不会被牛奶（或奶桶）清除
    }

    /**
     * 拦截实体获得新效果的事件
     */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity entity = event.getEntity();
        MobEffectInstance added = event.getEffectInstance();
        if (added == null) return;

        // 1. 如果新加的效果不能被牛奶清除，或者是牛奶本身，直接放行
        if (!added.getCures().contains(EffectCures.MILK) || added.getEffect() instanceof Milk) {
            return;
        }

        // 2. 检查实体身上是否有牛奶效果
        MobEffectInstance milkEffect = entity.getEffect(EffectRegister.MILK); // 确保换成你实际的注册类

        if (milkEffect != null) {
            // 3. 消耗 30 秒（600 tick）的持续时间
            int newDuration = milkEffect.getDuration() - 600;

            if (newDuration > 0) {
                // 更新剩余时间（保留原有的等级、粒子等属性）
                milkEffect.update(new MobEffectInstance(
                        milkEffect.getEffect(),
                        newDuration,
                        milkEffect.getAmplifier(),
                        milkEffect.isAmbient(),
                        milkEffect.isVisible(),
                        milkEffect.showIcon()
                ));
            } else {
                // 如果时间不够扣了，直接移除牛奶效果
                entity.removeEffect(EffectRegister.MILK);
            }

            // 4. 清除刚刚想要加上的 buff
            entity.removeEffect(added.getEffect());
}
    }
}