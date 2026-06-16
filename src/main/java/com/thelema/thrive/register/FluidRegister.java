package com.thelema.thrive.register;

import com.thelema.thelemalib.fluid.FluidCreator;
import com.thelema.thelemalib.fluid.RegisteredFluid;
import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.fluid.FireExplodeOfBerryJuice;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.bus.api.IEventBus;

public class FluidRegister {

    public static RegisteredFluid GLOWING_BERRY_JUICE;

    public static void register(IEventBus bus) {
        GLOWING_BERRY_JUICE = FluidCreator.create(ThriveMod.MOD_ID, "glowing_berry_juice")
                .useWaterTexture()
                .tintColor(0xFFFFD700)
                .fogColor(0xFFFFD700)
                .onEntityInside(FireExplodeOfBerryJuice::juiceInsideEffect)
                .lightLevel(15)
                .viscosity(1500)
                .density(2000)
                .temperature(350)
                .canConvertToSource(false)
                .bucketFillSound(SoundEvents.BUCKET_FILL)
                .bucketEmptySound(SoundEvents.BUCKET_EMPTY)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .register(bus);
    }
}
