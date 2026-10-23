package com.thelema.thrive.client.register;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.client.particle.ColoredExplosionParticle;
import com.thelema.thrive.client.particle.ExplosionEmitterParticle;
import com.thelema.thrive.register.ParticleRegister;
import net.minecraft.client.particle.SplashParticle;
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
                sprites -> new ColoredExplosionParticle.Provider(sprites, 1.0F, 0.647F, 0.145F)
        );
        event.registerSpriteSet(
                ParticleRegister.JUICE_SPLASH.get(),
                SplashParticle.Provider::new
        );
        event.registerSpecial(
                ParticleRegister.JUICE_EXPLOSION_EMITTER.get(),
                new ExplosionEmitterParticle.Provider(ParticleRegister.FLAME_EXPLOSION.get())
        );
    }
}