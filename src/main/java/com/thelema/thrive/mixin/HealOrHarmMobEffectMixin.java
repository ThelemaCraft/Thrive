package com.thelema.thrive.mixin;

import com.thelema.thrive.generic.tool.Damage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
public class HealOrHarmMobEffectMixin {

    @Redirect(
            method = "applyInstantenousEffect",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hurt"
                            + "(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean thrive$queueDamage(LivingEntity target, DamageSource ds, float amount) {
        if (target.level().isClientSide) return target.hurt(ds, amount);
        Damage.hurt(target, ds, amount);
        return true;
    }
}