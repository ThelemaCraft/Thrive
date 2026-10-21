package com.thelema.thrive.generic.entity;

import com.thelema.thrive.generic.blockentity.GardenHiveBlockEntity;
import com.thelema.thrive.register.BlockRegister;
import com.thelema.thrive.register.EntityRegister;
import com.thelema.thrive.register.PoiRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.AirRandomPos;
import net.minecraft.world.entity.ai.util.HoverRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class GardenBee extends Animal implements FlyingAnimal {

    private static final EntityDataAccessor<Boolean> DATA_HAS_POLLEN =
            SynchedEntityData.defineId(GardenBee.class, EntityDataSerializers.BOOLEAN);

    /** 携带的花粉。采蜜完成后写入。 */
    public boolean carriedPollen;

    /** 认领的蜂巢。归巢目标。null = 无家。 */
    @Nullable public BlockPos home;

    /** 找新巢的冷却。 */
    public int remainingCooldownBeforeLocatingNewHive;

    /** 找新花的冷却。 */
    public int remainingCooldownBeforeLocatingNewFlower = 20 + this.random.nextInt(40);

    /** 出巢后的拒入倒计时。 */
    public int stayOutOfHiveCountdown;

    /** 回巢寻路 Goal，Locate 需要用到它的黑名单。 */
    GoToHiveGoal goToHiveGoal;


    public GardenBee(EntityType<? extends GardenBee> type, Level level) {
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
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HAS_POLLEN, false);
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
        this.goalSelector.addGoal(0, new PanicGoal(this, 1.5));
        this.goalSelector.addGoal(1, new EnterHiveGoal());
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        // 被任何花吸引
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.25, stack -> stack.is(ItemTags.FLOWERS), false));
        this.goalSelector.addGoal(4, new PollinateGoal());
        this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.25));
        this.goalSelector.addGoal(5, new LocateHiveGoal());
        this.goToHiveGoal = new GoToHiveGoal();
        this.goalSelector.addGoal(5, this.goToHiveGoal);
        this.goalSelector.addGoal(7, new NightRestGoal());
        this.goalSelector.addGoal(8, new WanderGoal());
        this.goalSelector.addGoal(9, new FloatGoal(this));
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isFlying() {
        return !this.onGround();
    }

    // ==================== 繁殖 ====================

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.FLOWERS);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return EntityRegister.GARDEN_BEE.get().create(level);
    }

    // ==================== 交互与转化 ====================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 蜜脾转化为士兵蜂
        if (stack.is(Items.HONEYCOMB)) {
            if (!this.level().isClientSide) {
                GardenSoldierBee soldier = EntityRegister.GARDEN_SOLDIER_BEE.get().create(this.level());
                if (soldier != null) {
                    soldier.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
                    // 转化时默认认当前玩家为主人
                    soldier.setTame(true, false);
                    soldier.setOwnerUUID(player.getUUID());
                    soldier.setHealth(this.getHealth());

                    this.level().addFreshEntity(soldier);
                    this.discard();

                    this.level().playSound(null, this.blockPosition(), SoundEvents.BEE_POLLINATE, this.getSoundSource(), 1.0F, 1.0F);
                    this.level().broadcastEntityEvent(soldier, (byte) 7); // 爱心粒子

                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    // ==================== Tick / 回巢判定 ====================

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            if (this.stayOutOfHiveCountdown > 0) this.stayOutOfHiveCountdown--;
            if (this.remainingCooldownBeforeLocatingNewHive > 0) this.remainingCooldownBeforeLocatingNewHive--;
            if (this.remainingCooldownBeforeLocatingNewFlower > 0) this.remainingCooldownBeforeLocatingNewFlower--;
            this.entityData.set(DATA_HAS_POLLEN, this.carriedPollen);
        }
    }

    /** 原版 Bee#wantsToEnterHive 的简化版：带粉、天黑、下雨就回家。 */
    public boolean wantsToEnterHive() {
        if (this.stayOutOfHiveCountdown > 0) return false;
        return this.carriedPollen || this.level().isNight() || this.level().isRaining();
    }

    /** 目标蜂巢是否还有空位。 */
    public boolean doesHiveHaveSpace(BlockPos pos) {
        return this.level().getBlockEntity(pos) instanceof GardenHiveBlockEntity hive
                && hive.occupantCount < GardenHiveBlockEntity.MAX_BEES;
    }

    public boolean hasPollen() {
        return this.entityData.get(DATA_HAS_POLLEN);
    }

    /** 原版 Bee#isTooFarAway：离家超过 32 格就不追了。 */
    boolean isTooFarAway(BlockPos pos) {
        return !pos.closerToCenterThan(this.position(), 32);
    }

    /** 原版 Bee#pathfindRandomlyTowards：远距离时朝巢的大致方向随机探路。 */
    void pathfindRandomlyTowards(BlockPos pos) {
        Vec3 target = Vec3.atBottomCenterOf(pos);
        int yOffset = 0;
        BlockPos here = this.blockPosition();
        int dy = (int) target.y - here.getY();
        if (dy > 2) yOffset = 4;
        else if (dy < -2) yOffset = -4;

        int xzRange = 6;
        int yRange = 8;
        int manhattan = here.distManhattan(pos);
        if (manhattan < 15) {
            xzRange = manhattan / 2;
            yRange = manhattan / 2;
        }

        Vec3 random = AirRandomPos.getPosTowards(this, xzRange, yRange, yOffset, target,
                (float) (Math.PI / 10));
        if (random != null) {
            this.getNavigation().setMaxVisitedNodesMultiplier(0.5F);
            this.getNavigation().moveTo(random.x, random.y, random.z, 1.0);
        }
    }

    // ==================== NBT ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("CarriedPollen", this.carriedPollen);
        tag.putInt("CannotEnterHiveTicks", this.stayOutOfHiveCountdown);
        if (home != null) tag.putLong("Home", home.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.carriedPollen = tag.getBoolean("CarriedPollen");
        this.stayOutOfHiveCountdown = tag.getInt("CannotEnterHiveTicks");
        if (tag.contains("Home")) home = BlockPos.of(tag.getLong("Home"));
    }

    // ==================== 音效 ====================

    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.BEE_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.BEE_DEATH; }
    @Override protected float getSoundVolume() { return 0.4F; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) {}

    // ==================== 内置 Goal ====================

    /** 采蜜 Goal：完全复刻原版的悬停手感，无偏好，见花就采。 */
    class PollinateGoal extends Goal {
        static final int POLLINATE_TICKS = 400; // 原版 10 秒
        static final int MAX_TICKS = 600;
        static final double ARRIVE_TO_FLOWER = 1.0;
        static final double SNAP_RADIUS = 0.1;

        @Nullable BlockPos targetFlower;
        @Nullable Vec3 hoverPos;
        int pollinateTicks;
        int totalTicks;
        boolean done;

        public PollinateGoal() { this.setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (GardenBee.this.remainingCooldownBeforeLocatingNewFlower > 0) return false;
            if (GardenBee.this.carriedPollen) return false;
            if (GardenBee.this.level().isNight()) return false;   // 晚上回巢，不采蜜
            if (GardenBee.this.level().isRaining()) return false;
            if (findFlower() == null) {
                GardenBee.this.remainingCooldownBeforeLocatingNewFlower =
                        20 + GardenBee.this.random.nextInt(40);
                return false;
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (done || targetFlower == null || GardenBee.this.carriedPollen) return false;
            if (GardenBee.this.level().isNight()) return false;   // 天黑立刻收工
            if (GardenBee.this.level().isRaining()) return false;
            return GardenBee.this.level().getBlockState(targetFlower).is(BlockTags.FLOWERS);
        }

        @Override
        public void start() {
            hoverPos = null;
            pollinateTicks = POLLINATE_TICKS;
            totalTicks = 0;
            done = false;
        }

        @Override
        public void stop() {
            targetFlower = null;
            hoverPos = null;
            // 原版：采完（或中断）后 200 tick 再找下一朵
            GardenBee.this.remainingCooldownBeforeLocatingNewFlower = 200;
        }

        @Override
        public void tick() {
            if (targetFlower == null) return;
            if (++totalTicks > MAX_TICKS) { done = true; return; }

            Vec3 center = Vec3.atBottomCenterOf(targetFlower).add(0, 0.6, 0);

            // 【修复1】距离大于 1 格时，使用 navigation 寻路，绕过障碍物
            if (GardenBee.this.position().distanceTo(center) > 1.0) {
                hoverPos = center;
                // 每 20 tick 刷新一次路径，防止每次 tick 都寻路卡顿
                if (totalTicks % 20 == 0) {
                    GardenBee.this.getNavigation().moveTo(center.x, center.y, center.z, 0.5);
                }
                return;
            }

            // 到了附近，自然悬停
            if (hoverPos == null) hoverPos = center;
            boolean atHover = GardenBee.this.position().distanceTo(hoverPos) <= SNAP_RADIUS;
            boolean setPos = true;

            if (atHover) {
                if (GardenBee.this.getRandom().nextInt(25) == 0) {
                    double ox = (GardenBee.this.getRandom().nextFloat() * 2 - 1) * 0.33;
                    double oz = (GardenBee.this.getRandom().nextFloat() * 2 - 1) * 0.33;
                    hoverPos = new Vec3(center.x + ox, center.y, center.z + oz);
                    GardenBee.this.getNavigation().stop();
                } else {
                    setPos = false;
                }
                GardenBee.this.getLookControl().setLookAt(center.x, center.y, center.z);
            }

            if (setPos) GardenBee.this.getMoveControl().setWantedPosition(hoverPos.x, hoverPos.y, hoverPos.z, 0.35);

            if (--pollinateTicks <= 0) {
                GardenBee.this.carriedPollen = true;
                GardenBee.this.playSound(SoundEvents.BEE_POLLINATE, 1.0F, 1.0F);
                done = true;
            }
        }

        @Nullable
        private BlockPos findFlower() {
            BlockPos origin = GardenBee.this.blockPosition();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            int r = 5;
            for (int dx = -r; dx <= r; dx++)
                for (int dy = -r; dy <= r; dy++)
                    for (int dz = -r; dz <= r; dz++) {
                        p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        if (!GardenBee.this.level().isLoaded(p)) continue;
                        if (GardenBee.this.level().getBlockState(p).is(BlockTags.FLOWERS)) {
                            double d = p.distSqr(origin);
                            if (d < bestDist) { bestDist = d; best = p.immutable(); }
                        }
                    }
            if (best != null) targetFlower = best;
            return best;
        }
    }

    /** 原版 BeeEnterHiveGoal：到巢边且没满，直接消失进巢。 */
    class EnterHiveGoal extends Goal {
        public EnterHiveGoal() { this.setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (GardenBee.this.home == null) return false;
            if (!GardenBee.this.wantsToEnterHive()) return false;
            if (!GardenBee.this.home.closerToCenterThan(GardenBee.this.position(), 2.0)) return false;

            if (!(GardenBee.this.level().getBlockEntity(GardenBee.this.home) instanceof GardenHiveBlockEntity hive)) {
                GardenBee.this.home = null;
                return false;
            }
            if (hive.occupantCount >= GardenHiveBlockEntity.MAX_BEES) {
                GardenBee.this.home = null;
                GardenBee.this.remainingCooldownBeforeLocatingNewHive = 200;
                return false;
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            if (GardenBee.this.level().getBlockEntity(GardenBee.this.home) instanceof GardenHiveBlockEntity hive
                    && hive.occupantCount < GardenHiveBlockEntity.MAX_BEES) {
                hive.occupantCount++;
                if (GardenBee.this.carriedPollen) GardenBee.this.carriedPollen = false;
                hive.setChanged();
                GardenBee.this.level().sendBlockUpdated(hive.getBlockPos(), hive.getBlockState(), hive.getBlockState(), 3);
                GardenBee.this.discard();
            }
        }
    }

    /** 原版 BeeLocateHiveGoal：没有家时找附近有空位的蜂巢。 */
    class LocateHiveGoal extends Goal {
        @Override
        public boolean canUse() {
            return GardenBee.this.remainingCooldownBeforeLocatingNewHive == 0
                    && GardenBee.this.home == null
                    && GardenBee.this.wantsToEnterHive();
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            GardenBee.this.remainingCooldownBeforeLocatingNewHive = 200;

            List<BlockPos> hives = findNearbyHivesWithSpace();
            for (BlockPos pos : hives) {
                if (!goToHiveGoal.isTargetBlacklisted(pos)) {
                    GardenBee.this.home = pos;
                    return;
                }
            }
            if (!hives.isEmpty()) {
                goToHiveGoal.clearBlacklist();
                GardenBee.this.home = hives.get(0);
            }
        }

        private List<BlockPos> findNearbyHivesWithSpace() {
            if (!(GardenBee.this.level() instanceof ServerLevel server)) return List.of();
            BlockPos origin = GardenBee.this.blockPosition();
            return server.getPoiManager()
                    .getInRange(poi -> poi.is(PoiRegister.GARDEN_HIVE), origin, 20, PoiManager.Occupancy.ANY)
                    .map(PoiRecord::getPos)
                    .filter(GardenBee.this::doesHiveHaveSpace)
                    .sorted(Comparator.comparingDouble(p -> p.distSqr(origin)))
                    .toList();
        }
    }

    /** 原版 BeeGoToHiveGoal 的简化版：飞向蜂巢，超时/卡住就拉黑换家。 */
    class GoToHiveGoal extends Goal {
        static final int MAX_TRAVELLING_TICKS = 600;
        static final int MAX_BLACKLISTED = 3;

        final List<BlockPos> blacklisted = new ArrayList<>();
        int travellingTicks;

        public GoToHiveGoal() { this.setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (GardenBee.this.home == null) return false;
            if (!GardenBee.this.wantsToEnterHive()) return false;
            if (GardenBee.this.home.closerToCenterThan(GardenBee.this.position(), 2.0)) return false;
            if (GardenBee.this.level().getBlockState(GardenBee.this.home).getBlock()
                    != BlockRegister.GARDEN_HIVE.get()) return false;
            return !isTargetBlacklisted(GardenBee.this.home);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            this.travellingTicks = 0;
        }

        @Override
        public void stop() {
            GardenBee.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            BlockPos home = GardenBee.this.home;
            if (home == null) return;

            if (++this.travellingTicks > MAX_TRAVELLING_TICKS) {
                dropAndBlacklistHive();
                return;
            }
            if (GardenBee.this.getNavigation().isInProgress()) return;

            if (!home.closerToCenterThan(GardenBee.this.position(), 16)) {
                if (GardenBee.this.isTooFarAway(home)) {
                    dropHive();
                } else {
                    GardenBee.this.pathfindRandomlyTowards(home);
                }
            } else if (!pathfindDirectlyTowards(home)) {
                dropAndBlacklistHive();
            }
        }

        private boolean pathfindDirectlyTowards(BlockPos pos) {
            GardenBee.this.getNavigation().setMaxVisitedNodesMultiplier(10.0F);
            GardenBee.this.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 2, 1.0);
            Path path = GardenBee.this.getNavigation().getPath();
            return path != null && path.canReach();
        }

        boolean isTargetBlacklisted(BlockPos pos) {
            return this.blacklisted.contains(pos);
        }

        void clearBlacklist() {
            this.blacklisted.clear();
        }

        private void dropAndBlacklistHive() {
            if (GardenBee.this.home != null) {
                this.blacklisted.add(GardenBee.this.home);
                while (this.blacklisted.size() > MAX_BLACKLISTED) this.blacklisted.remove(0);
            }
            dropHive();
        }

        void dropHive() {
            GardenBee.this.home = null;
            GardenBee.this.remainingCooldownBeforeLocatingNewHive = 200;
        }
    }

    /** 夜晚无法回巢时原地悬停，别到处乱飞。 */
    class NightRestGoal extends Goal {
        NightRestGoal() { this.setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            return GardenBee.this.level().isNight();
        }

        @Override
        public boolean canContinueToUse() {
            return GardenBee.this.level().isNight();
        }

        @Override
        public void start() {
            GardenBee.this.getNavigation().stop();
            GardenBee.this.setNoGravity(true);
        }

        @Override
        public void stop() {
            GardenBee.this.setNoGravity(false);
        }

        @Override
        public void tick() {
            GardenBee.this.getNavigation().stop();
            GardenBee.this.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** 飞行游荡 Goal */
    class WanderGoal extends Goal {
        WanderGoal() { this.setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() { return GardenBee.this.getNavigation().isDone() && GardenBee.this.random.nextInt(10) == 0; }
        @Override public boolean canContinueToUse() { return GardenBee.this.getNavigation().isInProgress(); }

        @Override
        public void start() {
            Vec3 vec3 = this.findPos();
            if (vec3 != null) GardenBee.this.getNavigation().moveTo(GardenBee.this.getNavigation().createPath(BlockPos.containing(vec3), 1), 1.0);
        }

        @Nullable
        private Vec3 findPos() {
            Vec3 dir;
            BlockPos home = GardenBee.this.home;
            if (home != null && !home.closerToCenterThan(GardenBee.this.position(), 22)) {
                dir = Vec3.atCenterOf(home).subtract(GardenBee.this.position()).normalize();
            } else {
                dir = GardenBee.this.getViewVector(0.0F);
            }

            Vec3 pos = HoverRandomPos.getPos(GardenBee.this, 8, 7, dir.x, dir.z, (float) (Math.PI / 2), 3, 1);
            return pos != null ? pos : AirAndWaterRandomPos.getPos(GardenBee.this, 8, 4, -2, dir.x, dir.z, (float) (Math.PI / 2));
        }
    }
}