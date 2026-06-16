package com.thelema.thrive;

import com.thelema.thrive.register.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mod(ThriveMod.MOD_ID)
public class ThriveMod {

    public static final String MOD_ID = "thrive";

    public static final Logger LOGGER = LogUtils.getLogger();

    static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    public static void schedule(int ticks, Runnable task) {
        scheduler.schedule(() -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(task); // 主线程执行
            }
        }, ticks * 50L, TimeUnit.MILLISECONDS);
    }

    public ThriveMod(IEventBus bus, ModContainer cont) {


        ItemRegister.register(bus);
        BlockRegister.register(bus);
        BlockEntityRegister.register(bus);
        FluidRegister.register(bus);
        FeatureRegister.register(bus);
        TabRegister.register(bus);
        ItemCompRegister.register(bus);
        EffectRegister.register(bus);



        

        bus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {

        event.enqueueWork(() -> {

            RegionRegister.register();
            TipRegister.register();
            SurfaceRuleRegister.register();
        });

    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

}
