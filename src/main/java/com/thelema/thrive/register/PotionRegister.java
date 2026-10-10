package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PotionRegister {

    static final DeferredRegister<Potion> POTION = DeferredRegister.create(Registries.POTION, ThriveMod.MOD_ID);

    public static void register(IEventBus bus){
        POTION.register(bus);
    }

    static {

    }

}
