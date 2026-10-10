package com.thelema.thrive.generic.entity;

import com.thelema.thrive.generic.entity.PrimedLanternBerry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

public class LanternBerryCreeper extends Creeper implements GeoAnimatable {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LanternBerryCreeper(EntityType<? extends Creeper> entityType, Level level) {
        super(entityType, level);
    }

    // ==================== GeckoLib ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 3, this::animate));
    }

    private <E extends GeoAnimatable> PlayState animate(AnimationState<E> event) {
        if (event.isMoving()) {
            event.getController().setAnimation(RawAnimation.begin().thenLoop("animation.creeper.walk"));
        } else {
            event.getController().setAnimation(RawAnimation.begin().thenLoop("animation.creeper.idle"));
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public double getTick(Object o) {
        return this.tickCount;
    }

    // ==================== 着火点燃 ====================

    @Override
    public void tick() {
        if (isAlive() && isOnFire()) ignite();
        super.tick();
    }

    // ==================== 火焰死亡也炸 ====================

    @Override
    public void die(DamageSource source) {
        if (this.isOnFire() || source.is(DamageTypeTags.IS_FIRE)) {
            fireExplode();
        }
        super.die(source);
    }

    // ==================== 爆炸：爆燃 ====================

    /** 由 CreeperMixin 在父类 explodeCreeper 的 HEAD 处调用，替代原版爆炸。 */
    public void fireExplode() {
        if (!this.level().isClientSide) {
            this.dead = true;
            this.playSound(SoundEvents.CREEPER_DEATH, 2.0F, 1.0F);
            this.gameEvent(GameEvent.ENTITY_DIE);

            PrimedLanternBerry.deflagrate(
                    this.level(),
                    this.blockPosition(),
                    this,
                    this
            );

            this.discard();
        }
    }
}