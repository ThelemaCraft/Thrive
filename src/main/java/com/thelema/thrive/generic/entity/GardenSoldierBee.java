package com.thelema.thrive.generic.entity;

import com.thelema.thrive.generic.blockentity.GardenBeeBarracksBlockEntity;
import com.thelema.thrive.register.EntityRegister;
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
import net.minecraft.world.entity.ai.util.HoverRandomPos;
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
import java.util.List;
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

    /** 是否被指派为报信蜂（有同伴在打同一个目标时自动产生）。 */
    private boolean messenger;
    /** 报信后的冷却，防止来回反复报信。 */
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
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_REMAINING_ANGER_TIME, 0);
        builder.define(DATA_ROLLING, false);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level) {
            @Override
            public boolean isStableDestination(BlockPos pos) {
                return !this.level.getBlockState(pos.below()).isAir();
            }
        };
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
        this.goalSelector.addGoal(8, new SoldierWanderGoal(this));

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(
                this, Mob.class, 10, true, false,
                target -> target instanceof Enemy && !(target instanceof Creeper)));
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

    // ==================== 繁殖禁止 ====================
    @Override public boolean isFood(ItemStack stack) { return false; }
    @Override public boolean canFallInLove() { return false; }
    @Nullable @Override public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) { return null; }

    // ==================== 驯服与交互 ====================
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

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && this.isTame()) {
            if (this.isOwnedBy(target)) return;
            if (target instanceof TamableAnimal t && t.isTame() && this.getOwnerUUID() != null && this.getOwnerUUID().equals(t.getOwnerUUID())) return;
        }
        super.setTarget(target);
        if (target != null) this.maybeBecomeMessenger(target);
    }

    /** 有同伴在打同一个目标时，由离兵营最近的那只转职报信蜂。 */
    private void maybeBecomeMessenger(LivingEntity target) {
        if (this.level().isClientSide || this.messenger || this.reportCooldown > 0) return;

        List<GardenSoldierBee> squad = new java.util.ArrayList<>();
        squad.add(this);
        boolean allyFighting = false;
        for (GardenSoldierBee other : this.level().getEntitiesOfClass(
                GardenSoldierBee.class, this.getBoundingBox().inflate(24.0))) {
            if (other == this || other.getTarget() != target) continue;
            if (other.messenger) return;          // 已经有人在报信了
            if (other.reportCooldown > 0) continue; // 刚报过信，换人
            allyFighting = true;
            squad.add(other);
        }
        if (!allyFighting) return; // 单挑时不当信使

        GardenSoldierBee nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (GardenSoldierBee bee : squad) {
            BlockPos barracks = bee.findBarracks(false);
            if (barracks == null) continue;
            double dist = bee.blockPosition().distSqr(barracks);
            if (dist < bestDist) {
                bestDist = dist;
                nearest = bee;
            }
        }
        if (nearest != null) nearest.messenger = true;
    }

    /** 最近的、正把我当目标的怪。 */
    @Nullable
    private Mob findThreat() {
        Mob nearest = null;
        double best = Double.MAX_VALUE;
        for (Mob mob : this.level().getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(16.0))) {
            if (mob.getTarget() != this) continue;
            double dist = mob.distanceToSqr(this);
            if (dist < best) {
                best = dist;
                nearest = mob;
            }
        }
        return nearest;
    }

    /** 最近的可用兵营。needSpace 时要求还有空位，否则满员也可以（用来报警）。 */
    @Nullable
    private BlockPos findBarracks(boolean needSpace) {
        if (!(this.level() instanceof ServerLevel server)) return null;
        UUID owner = this.getOwnerUUID();
        BlockPos origin = this.blockPosition();
        return server.getPoiManager()
                .getInRange(poi -> poi.is(PoiRegister.GARDEN_BEE_BARRACKS), origin, 32, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos)
                .filter(p -> server.getBlockEntity(p) instanceof GardenBeeBarracksBlockEntity b
                        && (b.ownerUUID == null || b.ownerUUID.equals(owner))
                        && (!needSpace || b.storedBees.size() < GardenBeeBarracksBlockEntity.MAX_BEES))
                .min(Comparator.comparingDouble(p -> p.distSqr(origin)))
                .orElse(null);
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
            if (this.messenger && (this.getTarget() == null || !this.getTarget().isAlive())) {
                this.messenger = false;
            }

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

    // ==================== NBT 与 中立机制 ====================
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); this.addPersistentAngerSaveData(tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); this.readPersistentAngerSaveData(this.level(), tag); }
    
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

            // 每 20 tick 检查一次：有怪已经锁定我，就抢先手
            if (this.tickCount % 20 == 0 && this.getTarget() == null) {
                Mob threat = this.findThreat();
                if (threat instanceof Enemy && this.canAttackType(threat.getType())) {
                    this.setTarget(threat);
                }
            }
        }
    }

    // ==================== 内部 Goal ====================

    /** 1. 残血避战：有怪锁定自己就全速后撤。 */
    static class SoldierRetreatGoal extends Goal {
        private final GardenSoldierBee bee;
        @Nullable private Mob threat;

        public SoldierRetreatGoal(GardenSoldierBee bee) { this.bee = bee; this.setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (bee.getHealth() >= bee.getMaxHealth() * 0.3F) return false;
            this.threat = bee.findThreat();
            return this.threat != null;
        }

        @Override
        public boolean canContinueToUse() {
            return bee.getHealth() < bee.getMaxHealth() * 0.6F
                    && threat != null && threat.isAlive() && threat.getTarget() == bee;
        }

        @Override
        public void stop() { this.threat = null; }

        @Override
        public void tick() {
            if (threat == null) return;
            Vec3 away = bee.position().subtract(threat.position()).normalize();
            Vec3 dest = bee.position().add(away.scale(4.0));
            bee.getMoveControl().setWantedPosition(dest.x, dest.y, dest.z, 1.2);
        }
    }

    /** 2. 安全回血：如果没被打，去旁边花盆采花回血 */
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

    /** 3.5 报信：同伴在打同一个目标时，我飞回兵营拉援军。 */
    static class SoldierReportGoal extends Goal {
        private final GardenSoldierBee bee;
        @Nullable private BlockPos barracksPos;

        public SoldierReportGoal(GardenSoldierBee bee) { this.bee = bee; this.setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (!bee.messenger || bee.reportCooldown > 0) return false;
            LivingEntity target = bee.getTarget();
            if (target == null || !target.isAlive()) return false;
            this.barracksPos = bee.findBarracks(false);
            return this.barracksPos != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (!bee.messenger || barracksPos == null) return false;
            LivingEntity target = bee.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void stop() { bee.getNavigation().stop(); }

        @Override
        public void tick() {
            LivingEntity target = bee.getTarget();
            if (target == null || barracksPos == null) return;

            if (bee.blockPosition().closerThan(barracksPos, 2.0)) {
                if (bee.level().getBlockEntity(barracksPos) instanceof GardenBeeBarracksBlockEntity barracks) {
                    barracks.triggerAlarm(bee.level(), barracksPos, target);
                }
                bee.messenger = false;
                bee.reportCooldown = 600;
                bee.getNavigation().stop();
            } else if (!bee.getNavigation().isInProgress()) {
                bee.getNavigation().moveTo(barracksPos.getX(), barracksPos.getY(), barracksPos.getZ(), 1.2);
            }
        }
    }

    /** 4. 天黑/下雨且无目标时回兵营。 */
    static class ReturnToBarracksGoal extends Goal {
        private final GardenSoldierBee bee;
        @Nullable private BlockPos targetBarracks;

        public ReturnToBarracksGoal(GardenSoldierBee bee) { this.bee = bee; this.setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (bee.isPassenger()) return false;
            if (!bee.level().isNight() && !bee.level().isRaining()) return false;
            if (bee.getTarget() != null) return false;
            this.targetBarracks = bee.findBarracks(true);
            return this.targetBarracks != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (targetBarracks == null) return false;
            return bee.level().getBlockEntity(targetBarracks) instanceof GardenBeeBarracksBlockEntity b
                    && b.storedBees.size() < GardenBeeBarracksBlockEntity.MAX_BEES;
        }

        @Override
        public void stop() { this.targetBarracks = null; }

        @Override
        public void tick() {
            if (targetBarracks == null) return;
            if (bee.blockPosition().closerThan(targetBarracks, 2.0)) {
                if (bee.level().getBlockEntity(targetBarracks) instanceof GardenBeeBarracksBlockEntity barracks
                        && barracks.tryAcceptBee(bee)) {
                    bee.discard();
                }
            } else if (!bee.getNavigation().isInProgress()) {
                bee.getNavigation().moveTo(targetBarracks.getX(), targetBarracks.getY(), targetBarracks.getZ(), 1.2);
            }
        }
    }

    /** 4. 阵地游荡 */
    static class SoldierWanderGoal extends Goal {
        private final GardenSoldierBee bee;
        public SoldierWanderGoal(GardenSoldierBee bee) { this.bee = bee; this.setFlags(EnumSet.of(Flag.MOVE)); }

        @Override public boolean canUse() { return bee.getNavigation().isDone() && bee.getRandom().nextInt(10) == 0; }
        @Override public boolean canContinueToUse() { return bee.getNavigation().isInProgress(); }

        @Override
        public void start() {
            Vec3 dir = bee.getViewVector(0.0F);
            Vec3 pos = HoverRandomPos.getPos(bee, 8, 7, dir.x, dir.z, (float) (Math.PI / 2), 3, 1);
            if (pos == null) pos = AirAndWaterRandomPos.getPos(bee, 8, 4, -2, dir.x, dir.z, (float) (Math.PI / 2));
            if (pos != null) bee.getNavigation().moveTo(bee.getNavigation().createPath(BlockPos.containing(pos), 1), 1.0);
        }
    }
}