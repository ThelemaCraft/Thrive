package com.thelema.thrive.register;

import com.mojang.serialization.Codec;
import com.thelema.thrive.ThriveMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ItemCompRegister {

    static final DeferredRegister.DataComponents DATA_COMP =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ThriveMod.MOD_ID);
    public static void register(IEventBus bus){
        DATA_COMP.register(bus);
    }

    //public static final Supplier<DataComponentType<Boolean>> WITH_MOSS_CALLUS;

    static {
        /*WITH_MOSS_CALLUS = DATA_COMP.registerComponentType("with_moss_callus", builder -> builder
                        .persistent(Codec.BOOL)
                        .networkSynchronized(ByteBufCodecs.BOOL));
*/
    }
}
