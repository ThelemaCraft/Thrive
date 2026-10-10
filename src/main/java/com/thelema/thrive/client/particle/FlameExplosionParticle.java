package com.thelema.thrive.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class FlameExplosionParticle extends TextureSheetParticle {

    /** 火焰色 0xFFA525，改这里换色 */
    private static final float R = 1.0F;
    private static final float G = 0.647F;
    private static final float B = 0.145F;

    private final SpriteSet spriteSet;

    public FlameExplosionParticle(ClientLevel level, double x, double y, double z,
                                  double xSpeed, double ySpeed, double zSpeed,
                                  SpriteSet spriteSet) {
        super(level, x, y, z);
        this.spriteSet = spriteSet;

        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;

        this.setColor(R, G, B);

        this.lifetime = 10 + this.random.nextInt(6);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.quadSize = 0.6F + this.random.nextFloat() * 0.4F;

        this.setSpriteFromAge(spriteSet);
    }

    @Override
    public void tick() {
        this.setSpriteFromAge(spriteSet);
        super.tick();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet spriteSet) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new FlameExplosionParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, spriteSet);
        }
    }
}