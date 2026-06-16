package com.thelema.thrive.generic.effect;

import com.thelema.thelemalib.damage.DamageQueue;
import com.thelema.thelemalib.item.ItemCustomData;
import com.thelema.thrive.register.EffectRegister;
import com.thelema.thrive.util.Flags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber
public class BeeryJuiceEffect extends MobEffect {

    public BeeryJuiceEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFD700);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide && entity.tickCount % 80 == 0) {
            // 回血（需要持久化标记）
            if (entity.getPersistentData().getBoolean(Flags.MOSS_CALLUS)) {
                entity.heal(1.0F);
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();

        // 1. 浆果汁持有者受到火伤 + 50%
        if (target.hasEffect(EffectRegister.BEERY_JUICE)) {
            if (event.getSource().is(DamageTypes.ON_FIRE)
                    || event.getSource().is(DamageTypes.IN_FIRE)
                    || event.getSource().is(DamageTypes.LAVA)
                    || event.getSource().is(DamageTypes.HOT_FLOOR)) {
                event.setAmount(event.getAmount() * 1.5F);
            }
        }

        // 2. 攻击者有浆果汁且着火 → 点燃目标 5 秒 + 2 点火伤（用 DamageQueue 避免免疫）
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && attacker.hasEffect(EffectRegister.BEERY_JUICE)
                && attacker.isOnFire()) {

            // 点燃 5 秒
            target.setRemainingFireTicks(target.getRemainingFireTicks() + 100);

            // 添加 2 点火焰伤害到队列，并加速处理，立刻生效
            DamageQueue.hurt(target, 2.0F, DamageTypes.IN_FIRE.location(), null);
            DamageQueue.accelerate(target, 1);
        }
    }


}