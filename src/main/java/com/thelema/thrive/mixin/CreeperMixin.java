package com.thelema.thrive.mixin;

import com.thelema.thrive.generic.entity.LanternBerryCreeper;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
public abstract class CreeperMixin {

    /**
     * 苦力怕的 explodeCreeper，是私有方法，子类无法重写。
     * 所以，就让其执行，但是我直接用Mixin加了一个类型条件，满足则走我们自己的爆燃效果。 */
    @Inject(method = "explodeCreeper", at = @At("HEAD"), cancellable = true)
    private void thrive$deflagrateLanternBerry(CallbackInfo ci) {
        if ((Object) this instanceof LanternBerryCreeper creeper) {
            creeper.fireExplode();
            ci.cancel();
        }
    }
}