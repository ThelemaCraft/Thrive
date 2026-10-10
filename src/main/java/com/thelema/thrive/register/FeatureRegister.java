package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.world.feature.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class FeatureRegister {

    static DeferredRegister<Feature<?>> FEATURE = DeferredRegister.create(Registries.FEATURE, ThriveMod.MOD_ID);

    public static void register(IEventBus bus) {
        FEATURE.register(bus);
    }

    public static final DeferredHolder<Feature<?>, Lake> LAKE =
            FEATURE.register("lake", () -> new Lake(Lake.LakeConfig.CODEC));
}

