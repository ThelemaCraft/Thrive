package com.thelema.thrive.mixin;

import com.thelema.thrive.generic.entity.GardenWitch;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Witch.class)
public abstract class WitchMixin {

    @ModifyArg(
            method = "registerGoals",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/goal/RangedAttackGoal;<init>"
                            + "(Lnet/minecraft/world/entity/monster/RangedAttackMob;DIF)V"),
            index = 2)
    private int thrive$gardenWitchFasterThrow(int original) {
        return (Object) this instanceof GardenWitch ? 40 : original;
    }
}