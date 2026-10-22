package com.thelema.thrive.generic.effect;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.register.EffectRegister;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
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

    // 内部更新标记：防止 tryConsumeMilk 重新添加效果时触发拦截事件
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
        // 尝试清除身上已有的可被牛奶治愈的效果
        // removeEffectsCuredBy 会返回 boolean，代表是否清除了至少一个效果
        if (entity.removeEffectsCuredBy(EffectCures.MILK)) {
            // 如果确实清除了效果，扣除 30 秒时间
            tryConsumeMilk(entity);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        // 1. 只在服务端处理，避免客户端刷屏和幽灵拦截
        if (event.getEntity().level().isClientSide()) return;

        // 2. 如果是内部扣时间重新添加触发的，直接放行
        if (isInternalUpdate) return;

        MobEffectInstance added = event.getEffectInstance();

        // 3. 如果是牛奶本身，放行
        if (added == null || added.getEffect().value() instanceof Milk) return;

        // 4. 实体有牛奶，且新加的效果可被牛奶清除 -> 拦截并扣时间
        if (event.getEntity().hasEffect(EffectRegister.MILK) && added.getCures().contains(EffectCures.MILK)) {
            if (tryConsumeMilk(event.getEntity())) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        }
    }

    /**
     * 扣除牛奶 600 tick 时间。
     * @return 是否成功扣除（如果身上没牛奶则返回 false）
     */
    public static boolean tryConsumeMilk(LivingEntity entity) {
        MobEffectInstance milk = entity.getEffect(EffectRegister.MILK);
        if (milk == null) return false;

        int newDuration = milk.getDuration() - 400;

        // 原版 update() 无法缩短时间，必须先移除
        entity.removeEffect(EffectRegister.MILK);

        if (newDuration > 0) {
            isInternalUpdate = true; // 开启标记
            entity.addEffect(new MobEffectInstance(
                    milk.getEffect(),
                    newDuration,
                    milk.getAmplifier(),
                    milk.isAmbient(),
                    milk.isVisible(),
                    milk.showIcon()
            ));
            isInternalUpdate = false; // 关闭标记
        }

        return true;
    }
}