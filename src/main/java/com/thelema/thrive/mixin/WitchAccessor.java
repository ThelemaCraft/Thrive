package com.thelema.thrive.mixin;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Witch.class)
public interface WitchAccessor {

    @Accessor("usingTime")
    void thrive$setUsingTime(int value);

    @Accessor("SPEED_MODIFIER_DRINKING")
    static AttributeModifier thrive$getDrinkingModifier() {
        throw new AssertionError();
    }
}