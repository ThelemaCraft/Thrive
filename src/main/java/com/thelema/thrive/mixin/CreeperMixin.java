package com.thelema.thrive.mixin;

import com.thelema.thrive.generic.entity.LanternBerryCreeper;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
public abstract class CreeperMixin {

    /** 父类膨胀到 maxSwell 时会调 explodeCreeper()。若是我们的苦力怕，走爆燃。 */
    @Inject(method = "explodeCreeper", at = @At("HEAD"), cancellable = true)
    private void thrive$deflagrateLanternBerry(CallbackInfo ci) {
        if ((Object) this instanceof LanternBerryCreeper creeper) {
            creeper.fireExplode();
            ci.cancel();
        }
    }
}