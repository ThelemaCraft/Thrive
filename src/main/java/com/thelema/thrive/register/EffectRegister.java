package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.effect.Appetite;
import com.thelema.thrive.generic.effect.Blinding;
import com.thelema.thrive.generic.effect.LanternBerryJuiceEffect;
import com.thelema.thrive.generic.effect.SuperHunger;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class EffectRegister {


    static final DeferredRegister<MobEffect> EFFECT = DeferredRegister.create(Registries.MOB_EFFECT, ThriveMod.MOD_ID);

    public static void register(IEventBus bus) {
        EFFECT.register(bus);
    }

    public static final DeferredHolder<MobEffect, LanternBerryJuiceEffect> LANTERN_BERRY_JUICE = EFFECT.register("lantern_berry_juice", LanternBerryJuiceEffect::new);
    public static final DeferredHolder<MobEffect, SuperHunger> SUPER_HUNGER = EFFECT.register("super_hunger", SuperHunger::new);
    public static final DeferredHolder<MobEffect, Appetite> APPETITE = EFFECT.register("appetite", Appetite::new);
    public static final DeferredHolder<MobEffect, Blinding> BLINDING = EFFECT.register("blinding", Blinding::new);

}
