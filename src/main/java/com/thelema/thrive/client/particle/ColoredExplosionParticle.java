package com.thelema.thrive.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.HugeExplosionParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ColoredExplosionParticle extends HugeExplosionParticle {

    public ColoredExplosionParticle(ClientLevel level, double x, double y, double z,
                                    double quadSizeMultiplier, SpriteSet spriteSet,
                                    float r, float g, float b) {
        // HugeExplosionParticle 把 xSpeed 当作大小乘数
        super(level, x, y, z, quadSizeMultiplier, spriteSet);

        // 原版 HugeExplosionParticle 在 super() 里已经给 rCol/gCol/bCol 赋了随机灰度值 (0.4 ~ 1.0)
        // 我们直接拿它当亮度乘数
        float brightness = this.rCol;

        // 目标颜色 × 随机亮度
        this.setColor(r * brightness, g * brightness, b * brightness);
    }

    @OnlyIn(Dist.CLIENT)
    public record Provider(SpriteSet spriteSet, float r, float g, float b) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            // 注意：原版把 xSpeed 传给了 quadSizeMultiplier
            return new ColoredExplosionParticle(level, x, y, z, xSpeed, spriteSet, r, g, b);
        }
    }
}