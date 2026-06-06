package com.thelema.thrive;

import com.thelema.thrive.register.FluidRegister;
import com.thelema.thrive.world.biome.region.TRegions;
import com.thelema.thrive.world.biome.surface.TSurfaceRules;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(ThriveMod.MOD_ID)
public class ThriveMod {

    public static final String MOD_ID = "thrive";

    public static final Logger LOGGER = LogUtils.getLogger();


    public ThriveMod(IEventBus bus, ModContainer cont) {


        FluidRegister.register(bus);



        bus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {

        event.enqueueWork(() -> {

            TRegions.init();

            TSurfaceRules.init();
        });

    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

}
