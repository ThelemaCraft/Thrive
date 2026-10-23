package com.thelema.thrive.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.HugeExplosionSeedParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ExplosionEmitterParticle extends HugeExplosionSeedParticle {

    private final ParticleOptions particleToSpawn;

    protected ExplosionEmitterParticle(ClientLevel level, double x, double y, double z, ParticleOptions particleToSpawn) {
        super(level, x, y, z);
        this.particleToSpawn = particleToSpawn;
    }

    @Override
    public void tick() {
        for (int i = 0; i < 6; i++) {
            double d0 = this.x + (this.random.nextDouble() - this.random.nextDouble()) * 4.0;
            double d1 = this.y + (this.random.nextDouble() - this.random.nextDouble()) * 4.0;
            double d2 = this.z + (this.random.nextDouble() - this.random.nextDouble()) * 4.0;
            
            // 使用传入的粒子类型
            this.level.addParticle(
                    this.particleToSpawn, 
                    d0, d1, d2,
                    (float)this.age / (float)this.lifetime, 0.0, 0.0
            );
        }

        this.age++;
        if (this.age == this.lifetime) {
            this.remove();
        }
    }

    @OnlyIn(Dist.CLIENT)
    public record Provider(ParticleOptions particleToSpawn) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new ExplosionEmitterParticle(level, x, y, z, particleToSpawn);
        }
    }
}