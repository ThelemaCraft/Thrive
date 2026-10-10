package com.thelema.thrive.client.register;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.client.particle.FlameExplosionParticle;
import com.thelema.thrive.register.ParticleRegister;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = ThriveMod.MOD_ID, value = Dist.CLIENT)
public class ParticleProviderRegister {

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                ParticleRegister.FLAME_EXPLOSION.get(),
                FlameExplosionParticle.Provider::new
        );
    }
}