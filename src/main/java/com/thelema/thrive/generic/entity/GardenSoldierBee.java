package com.thelema.thrive.generic.entity;

import com.thelema.thrive.generic.blockentity.GardenBeeBarracksBlockEntity;
import com.thelema.thrive.generic.entity.goal.NearestThreatTargetGoal;
import com.thelema.thrive.register.PoiRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.UUID;

public class GardenSoldierBee extends TamableAnimal implements NeutralMob, FlyingAnimal {

    public static final int ATTACK_INTERVAL = 40;
    public static final int LUNGE_TICKS = 10;
    private static final int IMPACT_TICK = 5;

    private static final EntityDataAccessor<Integer> DATA_REMAINING_ANGER_TIME =
            SynchedEntityData.defineId(GardenSoldierBee.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ROLLING =
            SynchedEntityData.defineId(GardenSoldierBee.class, EntityDataSerializers.BOOLEAN);

    private int attackCooldown;
    private int lungeTicks;
    @Nullable private Entity pendingTarget;
    @Nullable private UUID persistentAngerTarget;

    // ==================== 兵营调度相关 ====================
    public boolean isPatroller = false;
    @Nullable public BlockPos barracksPos = null;
    private int reportCooldown;

    public float rollAmount;
    public float rollAmountO;

    public GardenSoldierBee(EntityType<? extends GardenSoldierBee> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.lookControl = new LookControl(this);
        this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, 16.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.8)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_REMAINING_ANGER_TIME, 0);
        builder.define(DATA_ROLLING, false);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(false);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new SoldierRetreatGoal(this));
        this.goalSelector.addGoal(0, new SoldierReportGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.4, true));
        this.goalSelector.addGoal(2, new FloatGoal(this));
        this.goalSelector.addGoal(3, new SoldierRegenGoal(this));
        this.goalSelector.addGoal(4, new ReturnToBarracksGoal(this));
        this.goalSelector.addGoal(8, new SoldierPatrolGoal(this));

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(
                this, Mob.class, 10, true, false,
                target -> target instanceof Enemy && !(target instanceof Creeper)));
        this.targetSelector.addGoal(5, new NearestThreatTargetGoal(
                this, e -> e instanceof Enemy && !(e instanceof Creeper)));
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    public boolean isFlying() {
        return !this.onGround();
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        return type != EntityType.CREEPER && super.canAttackType(type);
    }

    // ==================== 友军保护 ====================
    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isTame() && target instanceof TamableAnimal t && t.isTame()) {
            if (this.isOwnedBy(target)) return false;
            if (this.getOwnerUUID() != null && this.getOwnerUUID().equals(t.getOwnerUUID())) return false;
        }
        return super.canAttack(target);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && this.isTame()) {
            if (this.isOwnedBy(target)) return;
            if (target instanceof TamableAnimal t && t.isTame() && this.getOwnerUUID() != null && this.getOwnerUUID().equals(t.getOwnerUUID())) return;
        }
        super.setTarget(target);
    }

    // ==================== 繁殖禁止 ====================
    @Override public boolean isFood(ItemStack stack) { return false; }
    @Override public boolean canFallInLove() { return false; }
    @Nullable @Override public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) { return null; }

    // ==================== 驯服 ====================
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!this.isTame() && stack.is(ItemTags.FLOWERS)) {
            if (!this.level().isClientSide) {
                this.tame(player);
                this.setTarget(null);
                this.setRemainingPersistentAngerTime(0);
                this.level().broadcastEntityEvent(this, (byte) 7);
                if (!player.isCreative()) stack.shrink(1);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    // ==================== 攻击与 Tick ====================
    @Override
    public boolean doHurtTarget(Entity target) {
        if (this.attackCooldown > 0) return false;
        this.pendingTarget = target;
        this.attackCooldown = ATTACK_INTERVAL;
        this.lungeTicks = LUNGE_TICKS;
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            if (this.reportCooldown > 0) this.reportCooldown--;

            if (this.attackCooldown > 0) this.attackCooldown--;
            if (this.lungeTicks > 0) this.lungeTicks--;

            if (this.pendingTarget != null && this.lungeTicks == LUNGE_TICKS - IMPACT_TICK) {
                if (this.pendingTarget.isAlive() && this.pendingTarget.distanceToSqr(this) < 9.0) {
                    if (super.doHurtTarget(this.pendingTarget)) {
                        this.playSound(SoundEvents.BEE_STING, 1.0F, 1.0F);
                    }
                }
                this.pendingTarget = null;
            }
            this.entityData.set(DATA_ROLLING, this.lungeTicks > 0);
        }
        this.updateRollAmount();
    }

    private void updateRollAmount() {
        this.rollAmountO = this.rollAmount;
        if (this.entityData.get(DATA_ROLLING)) {
            this.rollAmount = Math.min(1.0F, this.rollAmount + 0.2F);
        } else {
            this.rollAmount = Math.max(0.0F, this.rollAmount - 0.24F);
        }
    }

    public float getRollAmount(float partialTick) {
        return Mth.lerp(partialTick, this.rollAmountO, this.rollAmount);
    }

    // ==================== NeutralMob 恢复 ====================
    @Override public int getRemainingPersistentAngerTime() { return this.entityData.get(DATA_REMAINING_ANGER_TIME); }
    @Override public void setRemainingPersistentAngerTime(int time) { this.entityData.set(DATA_REMAINING_ANGER_TIME, time); }
    @Nullable @Override public UUID getPersistentAngerTarget() { return this.persistentAngerTarget; }
    @Override public void setPersistentAngerTarget(@Nullable UUID target) { this.persistentAngerTarget = target; }
    @Override public void startPersistentAngerTimer() { this.setRemainingPersistentAngerTime(400); }

    @Override
    public void customServerAiStep() {
        super.customServerAiStep();
        if (!this.level().isClientSide) {
            this.updatePersistentAnger((ServerLevel) this.level(), false);
        }
    }

    // ==================== NBT ====================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.addPersistentAngerSaveData(tag);
        tag.putBoolean("IsPatroller", this.isPatroller);
        if (this.barracksPos != null) tag.putLong("BarracksPos", this.barracksPos.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.readPersistentAngerSaveData(this.level(), tag);
        this.isPatroller = tag.getBoolean("IsPatroller");
        if (tag.contains("BarracksPos")) this.barracksPos = BlockPos.of(tag.getLong("BarracksPos"));
    }

    // ==================== 内部 Goal ====================

    static class SoldierRetreatGoal extends Goal {
        private final GardenSoldierBee bee;

        public SoldierRetreatGoal(GardenSoldierBee bee) { this.bee = bee; this.setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (bee.getHealth() >= bee.getMaxHealth() * 0.3F) return false;
            LivingEntity target = bee.getTarget();
            return target instanceof Mob mob && mob.getTarget() == bee;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = bee.getTarget();
            return bee.getHealth() < bee.getMaxHealth() * 0.6F
                    && target instanceof Mob mob && mob.isAlive() && mob.getTarget() == bee;
        }

        @Override
        public void tick() {
            LivingEntity target = bee.getTarget();
            if (target == null) return;
            Vec3 away = bee.position().subtract(target.position()).normalize();
            Vec3 dest = bee.position().add(away.scale(4.0));
            bee.getMoveControl().setWantedPosition(dest.x, dest.y, dest.z, 1.2);
        }
    }

    static class SoldierRegenGoal extends Goal {
        private final GardenSoldierBee bee;
        @Nullable private BlockPos targetFlower;
        @Nullable private Vec3 hoverPos;
        private int healTicks;

        public SoldierRegenGoal(GardenSoldierBee bee) { this.bee = bee; this.setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (bee.getHealth() >= bee.getMaxHealth() || bee.level().isRaining()) return false;
            LivingEntity target = bee.getTarget();
            if (target instanceof Mob mob && mob.getTarget() == bee) return false;
            this.targetFlower = findNearbyFlower();
            return this.targetFlower != null;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = bee.getTarget();
            if (target instanceof Mob mob && mob.getTarget() == bee) return false;
            return targetFlower != null && bee.getHealth() < bee.getMaxHealth();
        }

        @Override public void start() { this.hoverPos = null; this.healTicks = 60; }
        @Override public void stop() { this.targetFlower = null; this.hoverPos = null; }

        @Override
        public void tick() {
            if (targetFlower == null) return;
            Vec3 center = Vec3.atBottomCenterOf(targetFlower).add(0, 0.6, 0);

            if (bee.position().distanceTo(center) > 1.0) {
                this.hoverPos = center;
                bee.getMoveControl().setWantedPosition(center.x, center.y, center.z, 0.5);
                return;
            }

            if (this.hoverPos == null) this.hoverPos = center;
            boolean atHover = bee.position().distanceTo(hoverPos) <= 0.1;
            boolean setPos = true;

            if (atHover) {
                if (bee.getRandom().nextInt(25) == 0) {
                    double ox = (bee.getRandom().nextFloat() * 2 - 1) * 0.33;
                    double oz = (bee.getRandom().nextFloat() * 2 - 1) * 0.33;
                    this.hoverPos = new Vec3(center.x + ox, center.y, center.z + oz);
                    bee.getNavigation().stop();
                } else {
                    setPos = false;
                }
                bee.getLookControl().setLookAt(center.x, center.y, center.z);
            }

            if (setPos) bee.getMoveControl().setWantedPosition(hoverPos.x, hoverPos.y, hoverPos.z, 0.35);

            this.healTicks--;
            if (this.healTicks <= 0) {
                bee.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 100, 0));
                bee.playSound(SoundEvents.BEE_POLLINATE, 1.0F, 1.0F);
                this.healTicks = 60;
            }
        }

        @Nullable
        private BlockPos findNearbyFlower() {
            BlockPos origin = bee.blockPosition();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            int r = 8;
            for (int dx = -r; dx <= r; dx++)
                for (int dy = -r; dy <= r; dy++)
                    for (int dz = -r; dz <= r; dz++) {
                        p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        if (!bee.level().isLoaded(p)) continue;
                        if (bee.level().getBlockState(p).is(BlockTags.FLOWERS)) return p.immutable();
                    }
            return null;
        }
    }

    static class SoldierReportGoal extends Goal {
        private final GardenSoldierBee bee;

        public SoldierReportGoal(GardenSoldierBee bee) { this.bee = bee; this.setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (!bee.isPatroller || bee.reportCooldown > 0) return false;
            LivingEntity target = bee.getTarget();
            if (target == null || !target.isAlive()) return false;

            if (bee.barracksPos == null || !(bee.level().getBlockEntity(bee.barracksPos) instanceof GardenBeeBarracksBlockEntity b)) {
                return false;
            }
            return !b.storedBees.isEmpty();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = bee.getTarget();
            return target != null && target.isAlive() && bee.barracksPos != null;
        }

        @Override
        public void stop() { bee.getNavigation().stop(); }

        @Override
        public void tick() {
            LivingEntity target = bee.getTarget();
            if (target == null || bee.barracksPos == null) return;

            if (bee.blockPosition().closerThan(bee.barracksPos, 2.0)) {
                if (bee.level().getBlockEntity(bee.barracksPos) instanceof GardenBeeBarracksBlockEntity barracks) {
                    barracks.triggerAlarm(bee.level(), bee.barracksPos, target);
                }
                bee.isPatroller = false;
                bee.reportCooldown = 600;
                bee.getNavigation().stop();
            } else if (!bee.getNavigation().isInProgress()) {
                bee.getNavigation().moveTo(bee.barracksPos.getX(), bee.barracksPos.getY(), bee.barracksPos.getZ(), 1.2);
            }
        }
    }

    static class ReturnToBarracksGoal extends Goal {
        private final GardenSoldierBee bee;
        @Nullable private BlockPos targetBarracks;

        public ReturnToBarracksGoal(GardenSoldierBee bee) {
            this.bee = bee;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (bee.isPassenger()) return false;

            boolean isRaining = bee.level().isRaining();
            boolean hasNoTarget = bee.getTarget() == null;

            if (!isRaining && !hasNoTarget) return false;
            if (bee.isPatroller && !isRaining) return false;

            this.targetBarracks = bee.barracksPos;

            if (this.targetBarracks == null && bee.level() instanceof ServerLevel server) {
                UUID owner = bee.getOwnerUUID();
                BlockPos origin = bee.blockPosition();
                this.targetBarracks = server.getPoiManager()
                        .getInRange(poi -> poi.is(PoiRegister.GARDEN_BEE_BARRACKS), origin, 64, PoiManager.Occupancy.ANY)
                        .map(PoiRecord::getPos)
                        .filter(p -> server.getBlockEntity(p) instanceof GardenBeeBarracksBlockEntity b
                                && (b.ownerUUID == null || b.ownerUUID.equals(owner))
                                && b.storedBees.size() < GardenBeeBarracksBlockEntity.MAX_BEES)
                        .min(Comparator.comparingDouble(p -> p.distSqr(origin)))
                        .orElse(null);

                if (this.targetBarracks != null) {
                    bee.barracksPos = this.targetBarracks;
                }
            }

            if (this.targetBarracks == null) return false;

            return bee.level().getBlockEntity(this.targetBarracks) instanceof GardenBeeBarracksBlockEntity b
                    && b.storedBees.size() < GardenBeeBarracksBlockEntity.MAX_BEES;
        }

        @Override
        public boolean canContinueToUse() {
            if (targetBarracks == null) return false;
            if (bee.getTarget() != null && !bee.level().isRaining()) return false;

            return bee.level().getBlockEntity(targetBarracks) instanceof GardenBeeBarracksBlockEntity b
                    && b.storedBees.size() < GardenBeeBarracksBlockEntity.MAX_BEES;
        }

        @Override
        public void stop() {
            this.targetBarracks = null;
            bee.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (targetBarracks == null) return;

            if (bee.blockPosition().closerThan(targetBarracks, 2.0)) {
                if (bee.level().getBlockEntity(targetBarracks) instanceof GardenBeeBarracksBlockEntity barracks
                        && barracks.tryAcceptBee(bee)) {
                    bee.isPatroller = false;
                    bee.discard();
                }
            } else if (!bee.getNavigation().isInProgress()) {
                bee.getNavigation().moveTo(targetBarracks.getX(), targetBarracks.getY(), targetBarracks.getZ(), 1.2);
            }
        }
    }

    static class SoldierPatrolGoal extends Goal {
        private final GardenSoldierBee bee;
        private int patrolTimer = 0;

        public SoldierPatrolGoal(GardenSoldierBee bee) { this.bee = bee; this.setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            return bee.isPatroller && bee.barracksPos != null && bee.getTarget() == null;
        }

        @Override
        public void start() { this.patrolTimer = 0; }

        @Override
        public void tick() {
            if (this.patrolTimer-- <= 0) {
                this.patrolTimer = 100;
                double angle = bee.getRandom().nextDouble() * Math.PI * 2;
                double radius = 32 + bee.getRandom().nextDouble() * 32;
                assert bee.barracksPos != null;
                double tx = bee.barracksPos.getX() + Math.cos(angle) * radius;
                double tz = bee.barracksPos.getZ() + Math.sin(angle) * radius;
                double ty = bee.barracksPos.getY() + 2 + bee.getRandom().nextDouble() * 4;
                bee.getNavigation().moveTo(tx, ty, tz, 1.0);
            }
        }
    }
}