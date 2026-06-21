package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.world.feature.GiantGlowBerryPlacerFeature;
import com.thelema.thrive.generic.world.feature.OreDepositFeature;
import com.thelema.thrive.generic.world.feature.config.OreDepositConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class FeatureRegister {

    static DeferredRegister<Feature<?>> FEATURE = DeferredRegister.create(Registries.FEATURE, ThriveMod.MOD_ID);
    public static void register(IEventBus bus){
        FEATURE.register(bus);
    }

    public static final Supplier<Feature<NoneFeatureConfiguration>> GIANT_GLOW_BERRY =
            FEATURE.register("giant_glowing_berry",
                    () -> new GiantGlowBerryPlacerFeature(NoneFeatureConfiguration.CODEC));

    public static final Supplier<Feature<OreDepositConfig>> ORE_DEPOSIT =
            FEATURE.register("ore_deposit",
                    () -> new OreDepositFeature(OreDepositConfig.CODEC));

}
