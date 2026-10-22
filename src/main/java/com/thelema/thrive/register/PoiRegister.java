package com.thelema.thrive.register;

import com.google.common.collect.ImmutableSet;
import com.thelema.thrive.ThriveMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PoiRegister {

    static final DeferredRegister<PoiType> POI =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, ThriveMod.MOD_ID);

    public static final DeferredHolder<PoiType, PoiType> GARDEN_BEE_NEST =
            POI.register("garden_bee_nest", () -> new PoiType(
                    ImmutableSet.copyOf(BlockRegister.GARDEN_BEE_NEST.get().getStateDefinition().getPossibleStates()),
                    0, 1));   // 0 票、1 格



    public static void register(IEventBus bus) {
        POI.register(bus);
    }
}