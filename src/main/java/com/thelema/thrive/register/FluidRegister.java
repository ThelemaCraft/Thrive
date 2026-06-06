package com.thelema.thrive.register;

import com.thelema.thelemalib.fluid.FluidCreator;
import com.thelema.thelemalib.fluid.RegisteredFluid;
import com.thelema.thrive.ThriveMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;

public class FluidRegister {

    public static RegisteredFluid GLOWING_BERRY_JUICE;

    public static void register(IEventBus bus) {
        GLOWING_BERRY_JUICE = FluidCreator.create(ThriveMod.MOD_ID, "glowing_berry_juice")
                .useWaterTexture()
                .color(0xFFFFFF00)          // 纯黄色
                .density(2000)              // 比水重，会下沉
                .viscosity(1500)            // 比水粘稠，流动稍慢（可选）
                .temperature(350)           // 微热（可选）
                .canConvertToSource(false)  // 不可无限水源（可选，默认false）
                .defaultSounds()            // 桶装填/倒出/蒸发音效
                // .dripstone(...) 不添加，因为没有对应的炼药锅
                .fogColor(0xFFFFFF00)       // 水下金黄雾色
                .register(bus);
    }
}
