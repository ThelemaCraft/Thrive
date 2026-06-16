package com.thelema.thrive.register;


import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.blockentity.MossFluidTankBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class BlockEntityRegister {

    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ThriveMod.MOD_ID);

    public static void register(IEventBus bus){
        BLOCK_ENTITY.register(bus);
    }

    private static <T extends BlockEntity> Supplier<BlockEntityType<T>> simple(String name, BlockEntityType.BlockEntitySupplier<T> supplier, Supplier<Block> blockSupplier) {
        return BLOCK_ENTITY.register(name, () -> BlockEntityType.Builder.of(supplier, blockSupplier.get()).build(null));
    }

    public static final Supplier<BlockEntityType<MossFluidTankBlockEntity>> MOSS_FLUID_TANK;

    static {

        MOSS_FLUID_TANK = simple("moss_fluid_tank", MossFluidTankBlockEntity::new, BlockRegister.MOSS_FLUID_TANK);



    }


}
