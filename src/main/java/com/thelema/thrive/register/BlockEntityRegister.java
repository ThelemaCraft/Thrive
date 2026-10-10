package com.thelema.thrive.register;


import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.blockentity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BlockEntityRegister {

    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ThriveMod.MOD_ID);

    public static void register(IEventBus bus) {
        BLOCK_ENTITY.register(bus);
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BiomeCoreBlockEntity>> THRIVE_CORE;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SunBerryBlockEntity>> SUN_BERRY;

    static {

        SUN_BERRY = BLOCK_ENTITY.register("sun_berry", () -> BlockEntityType.Builder
                .of(SunBerryBlockEntity::new, BlockRegister.SUN_BERRY.get())
                .build(null));

        THRIVE_CORE = BLOCK_ENTITY.register("thrive_core", () -> BlockEntityType.Builder
                .of(BiomeCoreBlockEntity::new, BlockRegister.BIOME_CORE.get())
                .build(null));
    }


}
