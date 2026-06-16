package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.effect.BeeryJuiceEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class EffectRegister {

    static final DeferredRegister<MobEffect> EFFECT = DeferredRegister.create(Registries.MOB_EFFECT,ThriveMod.MOD_ID);
    public static void register(IEventBus bus){
        EFFECT.register(bus);
    }

    public static final DeferredHolder<MobEffect, BeeryJuiceEffect> BEERY_JUICE =
            EFFECT.register("berry_juice", BeeryJuiceEffect::new);


}
