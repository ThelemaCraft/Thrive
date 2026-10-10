package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ParticleRegister {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, ThriveMod.MOD_ID);

    /** 火焰色爆炸粒子。true = 受"粒子效果：最少"设置影响。 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLAME_EXPLOSION =
            PARTICLE_TYPES.register("flame_explosion", () -> new SimpleParticleType(true));

    public static void register(IEventBus bus) {
        PARTICLE_TYPES.register(bus);
    }
}