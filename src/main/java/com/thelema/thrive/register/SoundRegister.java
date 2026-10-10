package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class SoundRegister {

    public static final DeferredRegister<SoundEvent> SOUND =
            DeferredRegister.create(Registries.SOUND_EVENT, ThriveMod.MOD_ID);

    public static void register(IEventBus bus) {
        SOUND.register(bus);
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> SUN_BERRY_IGNITE =
            SOUND.register("sun_berry_ignite", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, "sun_berry_ignite")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SUN_BERRY_DEFLAGRATE =
            SOUND.register("sun_berry_deflagrate", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, "sun_berry_deflagrate")));
}