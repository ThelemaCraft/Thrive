package com.thelema.thrive.mixin;

import com.thelema.thrive.generic.tool.Damage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractArrow.class)
public class AbstractArrowMixin {

    // 让箭矢能每 tick都能对目标造成伤害， 可以进行快速连射
    @Redirect(
            method = "onHitEntity",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurt"
                            + "(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean thrive$queuePhysical(Entity entity, DamageSource ds, float amount) {
        if (entity.level().isClientSide) return entity.hurt(ds, amount);
        if (!(entity instanceof LivingEntity le)) return entity.hurt(ds, amount);

        Damage.hurt(le, ds, amount);
        return true;   // 骗原版：走击退 / 音效 / setArrowCount / doPostHurtEffects / discard
    }
}