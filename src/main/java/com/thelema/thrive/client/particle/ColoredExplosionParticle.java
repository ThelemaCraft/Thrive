package com.thelema.thrive.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

public class ColoredExplosionParticle extends TextureSheetParticle {

    private final SpriteSet spriteSet;

    public ColoredExplosionParticle(ClientLevel level, double x, double y, double z,
                                    double xSpeed, double ySpeed, double zSpeed,
                                    SpriteSet spriteSet, int color) {
        super(level, x, y, z);
        this.spriteSet = spriteSet;

        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;

        // 颜色：从 int 拆出 R/G/B，归一化到 0~1
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        this.setColor(r, g, b);

        this.lifetime = 10 + this.random.nextInt(6);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.quadSize = 0.8F + this.random.nextFloat() * 0.4F;

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

    /** ParticleProvider：将 SpriteSet 和颜色传入 Particle 构造。 */
    public record Provider(SpriteSet spriteSet) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            // 默认颜色（亮黄），可被外部覆盖
            return new ColoredExplosionParticle(level, x, y, z,
                    xSpeed, ySpeed, zSpeed, spriteSet, 0xFFFF33);
        }
    }
}