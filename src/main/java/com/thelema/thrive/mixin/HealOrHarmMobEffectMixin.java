package com.thelema.thrive.mixin;

import com.thelema.thrive.generic.effect.Milk;
import com.thelema.thrive.generic.tool.Damage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
public class HealOrHarmMobEffectMixin {

    // 1. 拦截瞬间效果（喷溅/滞留药水砸中时触发）
    @Inject(method = "applyInstantenousEffect", at = @At("HEAD"), cancellable = true)
    private void thrive$interceptInstant(Entity source, Entity indirectSource, LivingEntity livingEntity, int amplifier, double health, CallbackInfo ci) {
        if (Milk.tryConsumeMilk(livingEntity, (int) (health * 400))) {
            ci.cancel(); // 牛奶成功格挡，直接取消，不执行后面的伤害/治疗
        }
    }


    // 2. 你之前的伤害队列逻辑（如果牛奶没拦截，走到这里就进你的队列）
    @Redirect(
            method = "applyInstantenousEffect",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    )
    private boolean thrive$queueDamage(LivingEntity target, DamageSource ds, float amount) {
        if (target.level().isClientSide) return target.hurt(ds, amount);
        Damage.hurt(target, ds, amount);
        return true;
    }

    // 3. 瞬间伤害，tick调用，进队列逻辑
    @Redirect(
            method = "applyEffectTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    )
    private boolean thrive$queueTick(LivingEntity target, DamageSource ds, float amount) {
        if (target.level().isClientSide) return target.hurt(ds, amount);
        Damage.hurt(target, ds, amount);
        return true;
    }
}