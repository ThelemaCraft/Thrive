package com.thelema.thrive.generic.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.server.level.ServerLevel;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class LanternBerryCreeper extends Creeper implements GeoAnimatable {

    // ==================== 数据同步 ====================
    private static final EntityDataAccessor<Integer> DATA_SWELL_DIR =
            SynchedEntityData.defineId(LanternBerryCreeper.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_IS_POWERED =
            SynchedEntityData.defineId(LanternBerryCreeper.class, EntityDataSerializers.BOOLEAN);

    // ==================== GeckoLib ====================
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int oldSwell;
    private int swell;
    private int maxSwell = 30;

    public LanternBerryCreeper(EntityType<? extends Creeper> entityType, Level level) {
        super(entityType, level);
    }

    // ==================== 数据同步 ====================
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SWELL_DIR, -1);
        builder.define(DATA_IS_POWERED, false);
    }

    // ==================== GeckoLib 动画 ====================
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

    // ==================== 膨胀逻辑（完全接管） ====================
    @Override
    public void tick() {
        if (this.isAlive()) {
            this.oldSwell = this.swell;

            if (isOnFire()){
                ignite();
            }

            if (this.isIgnited()) {
                this.setSwellDir(1);
            }

            int i = this.getSwellDir();
            if (i > 0 && this.swell == 0) {
                this.playSound(SoundEvents.CREEPER_PRIMED, 1.0F, 0.5F);
                this.gameEvent(GameEvent.PRIME_FUSE);
            }

            this.swell += i;
            if (this.swell < 0) {
                this.swell = 0;
            }

            if (this.swell >= this.maxSwell) {
                this.swell = this.maxSwell;
                this.fireExplode();
            }
        }
        super.tick();
    }

    @Override
    public void die(DamageSource source) {
        if (this.isOnFire() || source.is(DamageTypeTags.IS_FIRE)) {
            fireExplode();
        }
        super.die(source);
    }

    // ==================== 爆炸：爆燃 ====================
    private void fireExplode() {
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

    // ==================== 状态访问器 ====================
    public int getSwellDir() {
        return this.entityData.get(DATA_SWELL_DIR);
    }

    public void setSwellDir(int state) {
        this.entityData.set(DATA_SWELL_DIR, state);
    }

    public boolean isPowered() {
        return this.entityData.get(DATA_IS_POWERED);
    }

    public void setPowered(boolean powered) {
        this.entityData.set(DATA_IS_POWERED, powered);
    }

    // ==================== 闪电充能 ====================
    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
        super.thunderHit(level, lightning);
        this.setPowered(true);
    }

    // ==================== 膨胀进度（用于 GeckoLib 和渲染） ====================
    public float getSwelling(float partialTicks) {
        return net.minecraft.util.Mth.lerp(partialTicks, (float) this.oldSwell, (float) this.swell) / (float) (this.maxSwell - 2);
    }

    // ==================== NBT 持久化 ====================
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.isPowered()) {
            compound.putBoolean("powered", true);
        }
        compound.putShort("Fuse", (short) this.maxSwell);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setPowered(compound.getBoolean("powered"));
        if (compound.contains("Fuse", 99)) {
            this.maxSwell = compound.getShort("Fuse");
        }
    }

}