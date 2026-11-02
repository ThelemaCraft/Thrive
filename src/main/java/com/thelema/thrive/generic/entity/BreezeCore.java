package com.thelema.thrive.generic.entity;

import com.thelema.thrive.generic.tool.Damage;
import com.thelema.thrive.register.constant.DamageTypeConstant;
import com.thelema.thrive.register.constant.TagConstant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

public class BreezeCore extends Mob implements GeoEntity {

    public static final int R = 9;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private BlockPos wanderTarget;
    private int wanderCooldown;

    /**
     * 旋风音循环倒计时。0 = 重新随机，>0 = 递减。
     */
    private int soundTick = 0;

    public BreezeCore(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.moveControl = new FlyingMoveControl(this, 20, true);
    }

    // ───── GeckoLib 动画 ─────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "controller", 3, this::animate));
    }

    private <E extends GeoAnimatable> PlayState animate(AnimationState<E> event) {
        event.getController().setAnimation(RawAnimation.begin().thenLoop("rotation"));
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void customServerAiStep() {
        Vec3 center = this.getBoundingBox().getCenter();
        boolean damageTick = this.tickCount % 20 == 0;

        Holder<DamageType> windHolder = damageTick
                ? level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypeConstant.WIND_GROUND)
                : null;

        for (Entity e : this.level().getEntitiesOfClass(
                Entity.class, this.getBoundingBox().inflate(R))) {

            if (e == this || e.isSpectator() ||
                    (e instanceof ServerPlayer p && p.isCreative())) continue;

            Vec3 diff = center.subtract(e.position());
            double dist = diff.length();
            if (dist > R) continue;

            // ── 引力（线性衰减）──
            double att = 1.0 - 0.7 * (dist / R);
            Vec3 force = diff.normalize().scale(0.6 * att).add(0, 0.15 * att, 0);

            Vec3 delta = e.getDeltaMovement().add(force);
            if (delta.length() > 3.0) delta = delta.normalize().scale(3.0);

            e.setDeltaMovement(delta);
            e.fallDistance = 0;

            if (e instanceof Player player) player.hurtMarked = true;
            else e.hasImpulse = true;

            // ── 风场伤害 ──
            if (damageTick && e instanceof LivingEntity living
                    && living.getType().is(TagConstant.CLOUD)) {
                Damage.hurt(living, new DamageSource(windHolder, this, this), 2.0F);
            }
        }

        // ── 游荡 ──
        if (wanderTarget == null
                || --wanderCooldown <= 0
                || blockPosition().distSqr(wanderTarget) < 16) {
            int tx = blockPosition().getX() + random.nextInt(33) - 16;
            int ty = Mth.clamp(blockPosition().getY() + random.nextInt(17) - 8,
                    level().getMinBuildHeight() + 1,
                    level().getMaxBuildHeight() - 2);
            int tz = blockPosition().getZ() + random.nextInt(33) - 16;
            wanderTarget = new BlockPos(tx, ty, tz);
            wanderCooldown = 80 + random.nextInt(80);
        }

        this.moveControl.setWantedPosition(
                wanderTarget.getX() + 0.5,
                wanderTarget.getY() + 0.5,
                wanderTarget.getZ() + 0.5,
                0.5);
    }

    // 拉动机制
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    // ───── 风声 ─────
    @Override
    public void tick() {
        // 与原版 Breeze.tick 一致：每 1~80 tick 随机播一次旋风音
        this.soundTick = this.soundTick == 0
                ? this.random.nextIntBetweenInclusive(1, 80)
                : this.soundTick - 1;
        if (this.soundTick == 0) {
            this.playWhirlSound();
        }
        super.tick();
    }

    public void playWhirlSound() {
        float pitch = 0.7F + 0.4F * this.random.nextFloat();
        float volume = 0.8F + 0.2F * this.random.nextFloat();
        this.level().playLocalSound(this, SoundEvents.BREEZE_WHIRL, this.getSoundSource(), volume, pitch);
    }

    // ───── 声音 ─────

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BREEZE_IDLE_AIR;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BREEZE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BREEZE_DEATH;
    }

    // ───── 摔落免伤 ─────

    @Override
    public boolean causeFallDamage(float d, float m, DamageSource s) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState s, BlockPos p) {
    }

    // ───── 属性 ─────

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FLYING_SPEED, 0.5);
    }
}