package com.thelema.thrive.generic.entity;

import com.thelema.thrive.register.EntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.HoverRandomPos;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

public class GardenBee extends TamableAnimal implements NeutralMob, FlyingAnimal {

    public static final float TAME_CHANCE = 0.33F;
    public static final int ATTACK_INTERVAL = 40;
    public static final int LUNGE_TICKS = 10;
    private static final int IMPACT_TICK = 5;

    private static final EntityDataAccessor<Integer> DATA_REMAINING_ANGER_TIME =
            SynchedEntityData.defineId(GardenBee.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ROLLING =
            SynchedEntityData.defineId(GardenBee.class, EntityDataSerializers.BOOLEAN);

    @Nullable public Item favoriteFlower;
    @Nullable public Item carriedPollen;
    @Nullable public BlockPos home;

    private int attackCooldown;
    private int lungeTicks;
    @Nullable private Entity pendingTarget;
    @Nullable private UUID persistentAngerTarget;

    public float rollAmount;
    public float rollAmountO;

    public GardenBee(EntityType<? extends GardenBee> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.lookControl = new LookControl(this);
        this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, 16.0F);
    }

    // ==================== 基础 ====================

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
        this.goalSelector.addGoal(0, new MeleeAttackGoal(this, 1.4, true));
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(8, new WanderGoal());

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(
                this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(5, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    // ==================== 繁殖 ====================

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.FLOWERS);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        GardenBee child = EntityRegister.GARDEN_BEE.get().create(level);
        if (child != null && this.isTame()) {
            child.setTame(true, false);
            child.setOwnerUUID(this.getOwnerUUID());
        }
        return child;
    }

    @Override
    public boolean isFlying() {
        return !this.onGround();
    }

    // ==================== 驯服 ====================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!this.isTame() && stack.is(ItemTags.FLOWERS)) {
            if (!this.level().isClientSide) {
                if (this.random.nextFloat() < TAME_CHANCE) {
                    this.tame(player);
                    this.favoriteFlower = stack.getItem();
                    this.setTarget(null);
                    this.setRemainingPersistentAngerTime(0);
                    this.level().broadcastEntityEvent(this, (byte) 7);
                } else {
                    this.level().broadcastEntityEvent(this, (byte) 6);
                }
                if (!player.isCreative()) stack.shrink(1);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    /** 主人、同主人宠物都不设为攻击目标。 */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && this.isTame()) {
            UUID myOwner = this.getOwnerUUID();
            if (myOwner != null) {
                if (myOwner.equals(target.getUUID())) return;
                if (target instanceof TamableAnimal t && t.isTame()
                        && myOwner.equals(t.getOwnerUUID())) return;
            }
        }
        super.setTarget(target);
    }

    // ==================== 攻击 ====================

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
            if (this.attackCooldown > 0) this.attackCooldown--;
            if (this.lungeTicks > 0) this.lungeTicks--;

            if (this.pendingTarget != null && this.lungeTicks == LUNGE_TICKS - IMPACT_TICK) {
                if (this.pendingTarget.isAlive() && this.pendingTarget.distanceToSqr(this) < 9.0) {
                    super.doHurtTarget(this.pendingTarget);
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

    // ==================== NeutralMob ====================

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.entityData.get(DATA_REMAINING_ANGER_TIME);
    }

    @Override
    public void setRemainingPersistentAngerTime(int time) {
        this.entityData.set(DATA_REMAINING_ANGER_TIME, time);
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID target) {
        this.persistentAngerTarget = target;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(400);
    }

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
        if (favoriteFlower != null)
            tag.putString("FavoriteFlower", BuiltInRegistries.ITEM.getKey(favoriteFlower).toString());
        if (carriedPollen != null)
            tag.putString("CarriedPollen", BuiltInRegistries.ITEM.getKey(carriedPollen).toString());
        if (home != null)
            tag.putLong("Home", home.asLong());
        this.addPersistentAngerSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("FavoriteFlower"))
            favoriteFlower = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("FavoriteFlower")));
        if (tag.contains("CarriedPollen"))
            carriedPollen = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("CarriedPollen")));
        if (tag.contains("Home"))
            home = BlockPos.of(tag.getLong("Home"));
        this.readPersistentAngerSaveData(this.level(), tag);
    }

    // ==================== 飞行游荡 ====================

    class WanderGoal extends Goal {
        WanderGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return GardenBee.this.navigation.isDone() && GardenBee.this.random.nextInt(10) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return GardenBee.this.navigation.isInProgress();
        }

        @Override
        public void start() {
            Vec3 pos = this.findPos();
            if (pos != null) {
                GardenBee.this.navigation.moveTo(
                        GardenBee.this.navigation.createPath(BlockPos.containing(pos), 1), 1.0);
            }
        }

        @Nullable
        private Vec3 findPos() {
            Vec3 dir = GardenBee.this.getViewVector(0.0F);
            Vec3 pos = HoverRandomPos.getPos(GardenBee.this, 8, 7,
                    dir.x, dir.z, (float) (Math.PI / 2), 3, 1);
            return pos != null ? pos : AirAndWaterRandomPos.getPos(
                    GardenBee.this, 8, 4, -2, dir.x, dir.z, (float) (Math.PI / 2));
        }
    }
}