package com.thelema.thrive.generic.entity;

import com.google.common.collect.Lists;
import com.thelema.thrive.generic.blockentity.GardenBeeBestBlockEntity;
import com.thelema.thrive.register.BlockRegister;
import com.thelema.thrive.register.EntityRegister;
import com.thelema.thrive.register.PoiRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.AirRandomPos;
import net.minecraft.world.entity.ai.util.HoverRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GardenBee extends TamableAnimal implements NeutralMob, FlyingAnimal {
    public static final float FLAP_DEGREES_PER_TICK = 120.32113F;
    public static final int TICKS_PER_FLAP = Mth.ceil(1.4959966F);
    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(GardenBee.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_REMAINING_ANGER_TIME = SynchedEntityData.defineId(GardenBee.class, EntityDataSerializers.INT);
    private static final int FLAG_ROLL = 2;
    private static final int FLAG_HAS_STUNG = 4;
    private static final int FLAG_HAS_NECTAR = 8;
    private static final int STING_DEATH_COUNTDOWN = 1200;
    private static final int TICKS_BEFORE_GOING_TO_KNOWN_FLOWER = 2400;
    private static final int TICKS_WITHOUT_NECTAR_BEFORE_GOING_HOME = 3600;
    private static final int MIN_ATTACK_DIST = 4;
    private static final int MAX_CROPS_GROWABLE = 10;
    private static final int POISON_SECONDS_NORMAL = 10;
    private static final int POISON_SECONDS_HARD = 18;
    private static final int TOO_FAR_DISTANCE = 32;
    private static final int HIVE_CLOSE_ENOUGH_DISTANCE = 2;
    private static final int PATHFIND_TO_HIVE_WHEN_CLOSER_THAN = 16;
    private static final int HIVE_SEARCH_DISTANCE = 20;
    public static final String TAG_CROPS_GROWN_SINCE_POLLINATION = "CropsGrownSincePollination";
    public static final String TAG_CANNOT_ENTER_HIVE_TICKS = "CannotEnterHiveTicks";
    public static final String TAG_TICKS_SINCE_POLLINATION = "TicksSincePollination";
    public static final String TAG_HAS_STUNG = "HasStung";
    public static final String TAG_HAS_NECTAR = "HasNectar";
    public static final String TAG_FLOWER_POS = "flower_pos";
    public static final String TAG_HIVE_POS = "hive_pos";
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    @Nullable
    private UUID persistentAngerTarget;
    private float rollAmount;
    private float rollAmountO;
    private int timeSinceSting;
    int ticksWithoutNectarSinceExitingHive;
    private int stayOutOfHiveCountdown;
    private int numCropsGrownSincePollination;
    private static final int COOLDOWN_BEFORE_LOCATING_NEW_HIVE = 200;
    int remainingCooldownBeforeLocatingNewHive;
    private static final int COOLDOWN_BEFORE_LOCATING_NEW_FLOWER = 200;
    int remainingCooldownBeforeLocatingNewFlower = Mth.nextInt(this.random, 20, 60);
    @Nullable
    BlockPos savedFlowerPos;
    @Nullable
    BlockPos hivePos;
    GardenBee.BeePollinateGoal beePollinateGoal;
    GardenBee.BeeGoToHiveGoal goToHiveGoal;
    private GardenBee.BeeGoToKnownFlowerGoal goToKnownFlowerGoal;
    private int underWaterTicks;

    public GardenBee(EntityType<? extends GardenBee> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.lookControl = new GardenBee.BeeLookControl(this);
        this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, 16.0F);
        this.setPathfindingMalus(PathType.COCOA, -1.0F);
        this.setPathfindingMalus(PathType.FENCE, -1.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS_ID, (byte)0);
        builder.define(DATA_REMAINING_ANGER_TIME, 0);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getBlockState(pos).isAir() ? 10.0F : 0.0F;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new GardenBee.BeeAttackGoal(this, 1.4F, true));
        this.goalSelector.addGoal(1, new GardenBee.BeeEnterHiveGoal());
        this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.25D, 10.0F, 2.0F));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.25, p_335831_ -> p_335831_.is(ItemTags.BEE_FOOD), false));
        this.beePollinateGoal = new GardenBee.BeePollinateGoal();
        this.goalSelector.addGoal(4, this.beePollinateGoal);
        this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.25));
        this.goalSelector.addGoal(5, new GardenBee.BeeLocateHiveGoal());
        this.goToHiveGoal = new GardenBee.BeeGoToHiveGoal();
        this.goalSelector.addGoal(5, this.goToHiveGoal);
        this.goToKnownFlowerGoal = new GardenBee.BeeGoToKnownFlowerGoal();
        this.goalSelector.addGoal(6, this.goToKnownFlowerGoal);
        this.goalSelector.addGoal(7, new GardenBee.BeeGrowCropGoal());
        this.goalSelector.addGoal(8, new GardenBee.BeeWanderGoal());
        this.goalSelector.addGoal(9, new FloatGoal(this));
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(1, new GardenBee.BeeHurtByOtherGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new GardenBee.BeeBecomeAngryTargetGoal(this));
        this.targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        // 如果目标是主人，或者目标是主人的其他宠物，就不打
        if (target instanceof TamableAnimal pet && pet.isOwnedBy(owner)) {
            return false;
        }
        return super.wantsToAttack(target, owner);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        // 如果拿着蜜脾，且这只蜜蜂还没被驯服
        if (itemstack.is(Items.HONEYCOMB) && !this.isTame()) {
            if (!player.getAbilities().instabuild) {
                itemstack.shrink(1); // 消耗一个蜜脾（创造模式不消耗）
            }

            // 调用 TamableAnimal 自带的驯服方法
            this.tame(player);

            // 播放爱心粒子效果（7 是 TamableAnimal 里定义的爱心的 byte 事件）
            this.level().broadcastEntityEvent(this, (byte) 7);

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 如果已经驯服了，且拿着蜜脾，可以给蜜蜂回血或进入繁殖状态
        if (itemstack.is(Items.HONEYCOMB) && this.isTame() && this.isOwnedBy(player)) {
            if (this.getHealth() < this.getMaxHealth()) {
                if (!player.getAbilities().instabuild) {
                    itemstack.shrink(1);
                }
                this.heal(2.0F); // 回复 1 颗心
                this.level().broadcastEntityEvent(this, (byte) 7); // 也可以播爱心
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }

        // 2. 切换跟随/巡逻模式
        if (this.isTame() && this.isOwnedBy(player) && itemstack.isEmpty()) {
            // 翻转状态
            boolean nowSitting = !this.isOrderedToSit();
            this.setOrderedToSit(nowSitting);
            this.navigation.stop(); // 停止当前寻路
            this.setTarget(null);   // 清除目标

            if (nowSitting) {
                player.displayClientMessage(Component.translatable("entity.thrive.garden_bee.patrol"), true);
            } else {
                player.displayClientMessage(Component.translatable("entity.thrive.garden_bee.follow"), true);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.hasHive()) {
            compound.put("hive_pos", NbtUtils.writeBlockPos(this.getHivePos()));
        }

        if (this.hasSavedFlowerPos()) {
            compound.put("flower_pos", NbtUtils.writeBlockPos(this.getSavedFlowerPos()));
        }

        compound.putBoolean("HasNectar", this.hasNectar());
        compound.putBoolean("HasStung", this.hasStung());
        compound.putInt("TicksSincePollination", this.ticksWithoutNectarSinceExitingHive);
        compound.putInt("CannotEnterHiveTicks", this.stayOutOfHiveCountdown);
        compound.putInt("CropsGrownSincePollination", this.numCropsGrownSincePollination);
        this.addPersistentAngerSaveData(compound);
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        this.hivePos = NbtUtils.readBlockPos(compound, "hive_pos").orElse(null);
        this.savedFlowerPos = NbtUtils.readBlockPos(compound, "flower_pos").orElse(null);
        super.readAdditionalSaveData(compound);
        this.setHasNectar(compound.getBoolean("HasNectar"));
        this.setHasStung(compound.getBoolean("HasStung"));
        this.ticksWithoutNectarSinceExitingHive = compound.getInt("TicksSincePollination");
        this.stayOutOfHiveCountdown = compound.getInt("CannotEnterHiveTicks");
        this.numCropsGrownSincePollination = compound.getInt("CropsGrownSincePollination");
        this.readPersistentAngerSaveData(this.level(), compound);
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        DamageSource damagesource = this.damageSources().sting(this);
        boolean flag = entity.hurt(damagesource, (float)((int)this.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        if (flag) {
            if (this.level() instanceof ServerLevel serverlevel) {
                EnchantmentHelper.doPostAttackEffects(serverlevel, entity, damagesource);
            }

            if (entity instanceof LivingEntity livingentity) {
                livingentity.setStingerCount(livingentity.getStingerCount() + 1);
                int i = 0;
                if (this.level().getDifficulty() == Difficulty.NORMAL) {
                    i = 10;
                } else if (this.level().getDifficulty() == Difficulty.HARD) {
                    i = 18;
                }

                if (i > 0) {
                    livingentity.addEffect(new MobEffectInstance(MobEffects.POISON, i * 20, 0), this);
                }
            }

            this.playSound(SoundEvents.BEE_STING, 1.0F, 1.0F);
        }

        return flag;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.hasNectar() && this.getCropsGrownSincePollination() < 10 && this.random.nextFloat() < 0.05F) {
            for (int i = 0; i < this.random.nextInt(2) + 1; i++) {
                this.spawnFluidParticle(
                        this.level(), this.getX() - 0.3F, this.getX() + 0.3F, this.getZ() - 0.3F, this.getZ() + 0.3F, this.getY(0.5), ParticleTypes.FALLING_NECTAR
                );
            }
        }

        this.updateRollAmount();
    }

    private void spawnFluidParticle(
            Level level, double startX, double endX, double startZ, double endZ, double posY, ParticleOptions particleOption
    ) {
        level.addParticle(
                particleOption,
                Mth.lerp(level.random.nextDouble(), startX, endX),
                posY,
                Mth.lerp(level.random.nextDouble(), startZ, endZ),
                0.0,
                0.0,
                0.0
        );
    }

    void pathfindRandomlyTowards(BlockPos pos) {
        Vec3 vec3 = Vec3.atBottomCenterOf(pos);
        int i = 0;
        BlockPos blockpos = this.blockPosition();
        int j = (int)vec3.y - blockpos.getY();
        if (j > 2) {
            i = 4;
        } else if (j < -2) {
            i = -4;
        }

        int k = 6;
        int l = 8;
        int i1 = blockpos.distManhattan(pos);
        if (i1 < 15) {
            k = i1 / 2;
            l = i1 / 2;
        }

        Vec3 vec31 = AirRandomPos.getPosTowards(this, k, l, i, vec3, (float) (Math.PI / 10));
        if (vec31 != null) {
            this.navigation.setMaxVisitedNodesMultiplier(0.5F);
            this.navigation.moveTo(vec31.x, vec31.y, vec31.z, 1.0);
        }
    }

    @Nullable
    public BlockPos getSavedFlowerPos() {
        return this.savedFlowerPos;
    }

    public boolean hasSavedFlowerPos() {
        return this.savedFlowerPos != null;
    }

    public void setSavedFlowerPos(BlockPos savedFlowerPos) {
        this.savedFlowerPos = savedFlowerPos;
    }

    @VisibleForDebug
    public int getTravellingTicks() {
        return Math.max(this.goToHiveGoal.travellingTicks, this.goToKnownFlowerGoal.travellingTicks);
    }

    @VisibleForDebug
    public List<BlockPos> getBlacklistedHives() {
        return this.goToHiveGoal.blacklistedTargets;
    }

    private boolean isTiredOfLookingForNectar() {
        return this.ticksWithoutNectarSinceExitingHive > 3600;
    }

    boolean wantsToEnterHive() {
        if (this.stayOutOfHiveCountdown <= 0 && !this.beePollinateGoal.isPollinating() && !this.hasStung() && this.getTarget() == null) {
            boolean flag = this.isTiredOfLookingForNectar() || this.level().isRaining() || this.level().isNight() || this.hasNectar();
            return flag && !this.isHiveNearFire();
        } else {
            return false;
        }
    }

    public void setStayOutOfHiveCountdown(int stayOutOfHiveCountdown) {
        this.stayOutOfHiveCountdown = stayOutOfHiveCountdown;
    }

    public float getRollAmount(float partialTick) {
        return Mth.lerp(partialTick, this.rollAmountO, this.rollAmount);
    }

    private void updateRollAmount() {
        this.rollAmountO = this.rollAmount;
        if (this.isRolling()) {
            this.rollAmount = Math.min(1.0F, this.rollAmount + 0.2F);
        } else {
            this.rollAmount = Math.max(0.0F, this.rollAmount - 0.24F);
        }
    }

    @Override
    protected void customServerAiStep() {

        if (this.isInWaterOrBubble()) {
            this.underWaterTicks++;
        } else {
            this.underWaterTicks = 0;
        }

        if (this.underWaterTicks > 20) {
            this.hurt(this.damageSources().drown(), 1.0F);
        }

        if (!this.hasNectar()) {
            this.ticksWithoutNectarSinceExitingHive++;
        }

        if (!this.level().isClientSide) {
            this.updatePersistentAnger((ServerLevel)this.level(), false);
        }
    }

    public void resetTicksWithoutNectarSinceExitingHive() {
        this.ticksWithoutNectarSinceExitingHive = 0;
    }

    private boolean isHiveNearFire() {
        if (this.hivePos == null) {
            return false;
        } else {
            BlockEntity blockentity = this.level().getBlockEntity(this.hivePos);
            return blockentity instanceof GardenBeeBestBlockEntity && ((GardenBeeBestBlockEntity)blockentity).isFireNearby();
        }
    }

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
        this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    private boolean doesHiveHaveSpace(BlockPos hivePos) {
        BlockEntity blockentity = this.level().getBlockEntity(hivePos);
        return blockentity instanceof GardenBeeBestBlockEntity ? !((GardenBeeBestBlockEntity)blockentity).isFull() : false;
    }

    @VisibleForDebug
    public boolean hasHive() {
        return this.hivePos != null;
    }

    @Nullable
    @VisibleForDebug
    public BlockPos getHivePos() {
        return this.hivePos;
    }

    @VisibleForDebug
    public GoalSelector getGoalSelector() {
        return this.goalSelector;
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        //DebugPackets.sendBeeInfo(this);
    }

    int getCropsGrownSincePollination() {
        return this.numCropsGrownSincePollination;
    }

    private void resetNumCropsGrownSincePollination() {
        this.numCropsGrownSincePollination = 0;
    }

    void incrementNumCropsGrownSincePollination() {
        this.numCropsGrownSincePollination++;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            if (this.stayOutOfHiveCountdown > 0) {
                this.stayOutOfHiveCountdown--;
            }

            if (this.remainingCooldownBeforeLocatingNewHive > 0) {
                this.remainingCooldownBeforeLocatingNewHive--;
            }

            if (this.remainingCooldownBeforeLocatingNewFlower > 0) {
                this.remainingCooldownBeforeLocatingNewFlower--;
            }

            boolean isAggressive = this.isAngry() || (this.isTame() && this.getTarget() != null);
            boolean flag = isAggressive && !this.hasStung() && this.getTarget() != null && this.getTarget().distanceToSqr(this) < 4.0;
            this.setRolling(flag);
            if (this.tickCount % 20 == 0 && !this.isHiveValid()) {
                this.hivePos = null;
            }
        }
    }

    boolean isHiveValid() {
        if (!this.hasHive()) {
            return false;
        } else if (this.isTooFarAway(this.hivePos)) {
            return false;
        } else {
            BlockEntity blockentity = this.level().getBlockEntity(this.hivePos);
            return blockentity instanceof GardenBeeBestBlockEntity;
        }
    }

    public boolean hasNectar() {
        return this.getFlag(8);
    }

    void setHasNectar(boolean hasNectar) {
        if (hasNectar) {
            this.resetTicksWithoutNectarSinceExitingHive();
        }

        this.setFlag(8, hasNectar);
    }

    public boolean hasStung() {
        return this.getFlag(4);
    }

    private void setHasStung(boolean hasStung) {
        this.setFlag(4, hasStung);
    }

    private boolean isRolling() {
        return this.getFlag(2);
    }

    private void setRolling(boolean isRolling) {
        this.setFlag(2, isRolling);
    }

    boolean isTooFarAway(BlockPos pos) {
        return !this.closerThan(pos, 32);
    }

    private void setFlag(int flagId, boolean value) {
        if (value) {
            this.entityData.set(DATA_FLAGS_ID, (byte)(this.entityData.get(DATA_FLAGS_ID) | flagId));
        } else {
            this.entityData.set(DATA_FLAGS_ID, (byte)(this.entityData.get(DATA_FLAGS_ID) & ~flagId));
        }
    }

    private boolean getFlag(int flagId) {
        return (this.entityData.get(DATA_FLAGS_ID) & flagId) != 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.FLYING_SPEED, 0.6F)
                .add(Attributes.MOVEMENT_SPEED, 0.3F)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected PathNavigation createNavigation(Level p_level) {
        FlyingPathNavigation flyingpathnavigation = new FlyingPathNavigation(this, p_level) {
            @Override
            public boolean isStableDestination(BlockPos p_27947_) {
                return !this.level.getBlockState(p_27947_.below()).isAir();
            }

            @Override
            public void tick() {
                if (!GardenBee.this.beePollinateGoal.isPollinating()) {
                    super.tick();
                }
            }
        };
        flyingpathnavigation.setCanOpenDoors(false);
        flyingpathnavigation.setCanFloat(false);
        flyingpathnavigation.setCanPassDoors(true);
        return flyingpathnavigation;
    }

    /**
     * Checks if the parameter is an item which this animal can be fed to breed it (wheat, carrots or seeds depending on the animal type)
     */
    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.BEE_FOOD);
    }

    boolean isFlowerValid(BlockPos pos) {
        return this.level().isLoaded(pos) && this.level().getBlockState(pos).is(BlockTags.FLOWERS);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.BEE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BEE_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Nullable
    public GardenBee getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return EntityRegister.GARDEN_BEE.get().create(level);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isFlapping() {
        return this.isFlying() && this.tickCount % TICKS_PER_FLAP == 0;
    }

    @Override
    public boolean isFlying() {
        return !this.onGround();
    }

    public void dropOffNectar() {
        this.setHasNectar(false);
        this.resetNumCropsGrownSincePollination();
    }

    /**
     * Called when the entity is attacked.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            if (!this.level().isClientSide) {
                this.beePollinateGoal.stopPollinating();
            }

            return super.hurt(source, amount);
        }
    }

    @Override
    @Deprecated
    protected void jumpInLiquid(TagKey<Fluid> fluidTag) {
        this.jumpInLiquidInternal();
    }

    private void jumpInLiquidInternal() {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.01, 0.0));
    }

    @Override
    public void jumpInFluid(net.neoforged.neoforge.fluids.FluidType type) {
        this.jumpInLiquidInternal();
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, (double)(0.5F * this.getEyeHeight()), (double)(this.getBbWidth() * 0.2F));
    }

    boolean closerThan(BlockPos pos, int distance) {
        return pos.closerThan(this.blockPosition(), (double)distance);
    }

    public void setHivePos(BlockPos hivePos) {
        this.hivePos = hivePos;
    }

    abstract class BaseBeeGoal extends Goal {
        public abstract boolean canBeeUse();

        public abstract boolean canBeeContinueToUse();

        @Override
        public boolean canUse() {
            return this.canBeeUse() && !GardenBee.this.isAngry();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canBeeContinueToUse() && !GardenBee.this.isAngry();
        }
    }

    class BeeAttackGoal extends MeleeAttackGoal {
        BeeAttackGoal(PathfinderMob mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
            super(mob, speedModifier, followingTargetEvenIfNotSeen);
        }

        @Override
        public boolean canUse() {
            return super.canUse() && GardenBee.this.isAngry();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && GardenBee.this.isAngry();
        }
    }

    static class BeeBecomeAngryTargetGoal extends NearestAttackableTargetGoal<Player> {
        BeeBecomeAngryTargetGoal(GardenBee mob) {
            super(mob, Player.class, 10, true, false, mob::isAngryAt);
        }

        @Override
        public boolean canUse() {
            return this.beeCanTarget() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            boolean flag = this.beeCanTarget();
            if (flag && this.mob.getTarget() != null) {
                return super.canContinueToUse();
            } else {
                this.targetMob = null;
                return false;
            }
        }

        private boolean beeCanTarget() {
            GardenBee bee = (GardenBee)this.mob;
            return bee.isAngry() && !bee.hasStung();
        }
    }

    class BeeEnterHiveGoal extends GardenBee.BaseBeeGoal {
        @Override
        public boolean canBeeUse() {
            if (GardenBee.this.hasHive()
                    && GardenBee.this.wantsToEnterHive()
                    && GardenBee.this.hivePos.closerToCenterThan(GardenBee.this.position(), 2.0)
                    && GardenBee.this.level().getBlockEntity(GardenBee.this.hivePos) instanceof GardenBeeBestBlockEntity beehiveblockentity) {
                if (!beehiveblockentity.isFull()) {
                    return true;
                }

                GardenBee.this.hivePos = null;
            }

            return false;
        }

        @Override
        public boolean canBeeContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            if (GardenBee.this.level().getBlockEntity(GardenBee.this.hivePos) instanceof GardenBeeBestBlockEntity beehiveblockentity) {
                beehiveblockentity.addOccupant(GardenBee.this);
            }
        }
    }

    @VisibleForDebug
    public class BeeGoToHiveGoal extends GardenBee.BaseBeeGoal {
        public static final int MAX_TRAVELLING_TICKS = 600;
        int travellingTicks = GardenBee.this.level().random.nextInt(10);
        private static final int MAX_BLACKLISTED_TARGETS = 3;
        final List<BlockPos> blacklistedTargets = Lists.newArrayList();
        @Nullable
        private Path lastPath;
        private static final int TICKS_BEFORE_HIVE_DROP = 60;
        private int ticksStuck;

        BeeGoToHiveGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canBeeUse() {
            return GardenBee.this.hivePos != null
                    && !GardenBee.this.hasRestriction()
                    && GardenBee.this.wantsToEnterHive()
                    && !this.hasReachedTarget(GardenBee.this.hivePos)
                    && GardenBee.this.level().getBlockState(GardenBee.this.hivePos).is(BlockRegister.GARDEN_BEE_NEST);
        }

        @Override
        public boolean canBeeContinueToUse() {
            return this.canBeeUse();
        }

        @Override
        public void start() {
            this.travellingTicks = 0;
            this.ticksStuck = 0;
            super.start();
        }

        @Override
        public void stop() {
            this.travellingTicks = 0;
            this.ticksStuck = 0;
            GardenBee.this.navigation.stop();
            GardenBee.this.navigation.resetMaxVisitedNodesMultiplier();
        }

        @Override
        public void tick() {
            if (GardenBee.this.hivePos != null) {
                this.travellingTicks++;
                if (this.travellingTicks > this.adjustedTickDelay(600)) {
                    this.dropAndBlacklistHive();
                } else if (!GardenBee.this.navigation.isInProgress()) {
                    if (!GardenBee.this.closerThan(GardenBee.this.hivePos, 16)) {
                        if (GardenBee.this.isTooFarAway(GardenBee.this.hivePos)) {
                            this.dropHive();
                        } else {
                            GardenBee.this.pathfindRandomlyTowards(GardenBee.this.hivePos);
                        }
                    } else {
                        boolean flag = this.pathfindDirectlyTowards(GardenBee.this.hivePos);
                        if (!flag) {
                            this.dropAndBlacklistHive();
                        } else if (this.lastPath != null && GardenBee.this.navigation.getPath().sameAs(this.lastPath)) {
                            this.ticksStuck++;
                            if (this.ticksStuck > 60) {
                                this.dropHive();
                                this.ticksStuck = 0;
                            }
                        } else {
                            this.lastPath = GardenBee.this.navigation.getPath();
                        }
                    }
                }
            }
        }

        private boolean pathfindDirectlyTowards(BlockPos pos) {
            GardenBee.this.navigation.setMaxVisitedNodesMultiplier(10.0F);
            GardenBee.this.navigation.moveTo((double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), 2, 1.0);
            return GardenBee.this.navigation.getPath() != null && GardenBee.this.navigation.getPath().canReach();
        }

        boolean isTargetBlacklisted(BlockPos pos) {
            return this.blacklistedTargets.contains(pos);
        }

        private void blacklistTarget(BlockPos pos) {
            this.blacklistedTargets.add(pos);

            while (this.blacklistedTargets.size() > 3) {
                this.blacklistedTargets.remove(0);
            }
        }

        void clearBlacklist() {
            this.blacklistedTargets.clear();
        }

        private void dropAndBlacklistHive() {
            if (GardenBee.this.hivePos != null) {
                this.blacklistTarget(GardenBee.this.hivePos);
            }

            this.dropHive();
        }

        private void dropHive() {
            GardenBee.this.hivePos = null;
            GardenBee.this.remainingCooldownBeforeLocatingNewHive = 200;
        }

        private boolean hasReachedTarget(BlockPos pos) {
            if (GardenBee.this.closerThan(pos, 2)) {
                return true;
            } else {
                Path path = GardenBee.this.navigation.getPath();
                return path != null && path.getTarget().equals(pos) && path.canReach() && path.isDone();
            }
        }
    }

    public class BeeGoToKnownFlowerGoal extends GardenBee.BaseBeeGoal {
        private static final int MAX_TRAVELLING_TICKS = 600;
        int travellingTicks = GardenBee.this.level().random.nextInt(10);

        BeeGoToKnownFlowerGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canBeeUse() {
            return GardenBee.this.savedFlowerPos != null
                    && !GardenBee.this.hasRestriction()
                    && this.wantsToGoToKnownFlower()
                    && GardenBee.this.isFlowerValid(GardenBee.this.savedFlowerPos)
                    && !GardenBee.this.closerThan(GardenBee.this.savedFlowerPos, 2);
        }

        @Override
        public boolean canBeeContinueToUse() {
            return this.canBeeUse();
        }

        @Override
        public void start() {
            this.travellingTicks = 0;
            super.start();
        }

        @Override
        public void stop() {
            this.travellingTicks = 0;
            GardenBee.this.navigation.stop();
            GardenBee.this.navigation.resetMaxVisitedNodesMultiplier();
        }

        @Override
        public void tick() {
            if (GardenBee.this.savedFlowerPos != null) {
                this.travellingTicks++;
                if (this.travellingTicks > this.adjustedTickDelay(600)) {
                    GardenBee.this.savedFlowerPos = null;
                } else if (!GardenBee.this.navigation.isInProgress()) {
                    if (GardenBee.this.isTooFarAway(GardenBee.this.savedFlowerPos)) {
                        GardenBee.this.savedFlowerPos = null;
                    } else {
                        GardenBee.this.pathfindRandomlyTowards(GardenBee.this.savedFlowerPos);
                    }
                }
            }
        }

        private boolean wantsToGoToKnownFlower() {
            return GardenBee.this.ticksWithoutNectarSinceExitingHive > 2400;
        }
    }

    class BeeGrowCropGoal extends GardenBee.BaseBeeGoal {
        static final int GROW_CHANCE = 30;

        @Override
        public boolean canBeeUse() {
            if (GardenBee.this.getCropsGrownSincePollination() >= 10) {
                return false;
            } else {
                return GardenBee.this.random.nextFloat() < 0.3F ? false : GardenBee.this.hasNectar() && GardenBee.this.isHiveValid();
            }
        }

        @Override
        public boolean canBeeContinueToUse() {
            return this.canBeeUse();
        }

        @Override
        public void tick() {
            if (GardenBee.this.random.nextInt(this.adjustedTickDelay(30)) == 0) {
                for (int i = 1; i <= 2; i++) {
                    BlockPos blockpos = GardenBee.this.blockPosition().below(i);
                    BlockState blockstate = GardenBee.this.level().getBlockState(blockpos);
                    Block block = blockstate.getBlock();
                    BlockState blockstate1 = null;
                    if (blockstate.is(BlockTags.BEE_GROWABLES)) {
                        if (block instanceof CropBlock) {
                            CropBlock cropblock = (CropBlock)block;
                            if (!cropblock.isMaxAge(blockstate)) {
                                blockstate1 = cropblock.getStateForAge(cropblock.getAge(blockstate) + 1);
                            }
                        } else if (block instanceof StemBlock) {
                            int k = blockstate.getValue(StemBlock.AGE);
                            if (k < 7) {
                                blockstate1 = blockstate.setValue(StemBlock.AGE, Integer.valueOf(k + 1));
                            }
                        } else if (blockstate.is(Blocks.SWEET_BERRY_BUSH)) {
                            int j = blockstate.getValue(SweetBerryBushBlock.AGE);
                            if (j < 3) {
                                blockstate1 = blockstate.setValue(SweetBerryBushBlock.AGE, Integer.valueOf(j + 1));
                            }
                        } else if (blockstate.is(Blocks.CAVE_VINES) || blockstate.is(Blocks.CAVE_VINES_PLANT)) {
                            ((BonemealableBlock)blockstate.getBlock()).performBonemeal((ServerLevel) GardenBee.this.level(), GardenBee.this.random, blockpos, blockstate);
                        }

                        if (blockstate1 != null) {
                            GardenBee.this.level().levelEvent(2011, blockpos, 15);
                            GardenBee.this.level().setBlockAndUpdate(blockpos, blockstate1);
                            GardenBee.this.incrementNumCropsGrownSincePollination();
                        }
                    }
                }
            }
        }
    }

    class BeeHurtByOtherGoal extends HurtByTargetGoal {
        BeeHurtByOtherGoal(GardenBee mob) {
            super(mob);
        }

        @Override
        public boolean canContinueToUse() {
            return GardenBee.this.isAngry() && super.canContinueToUse();
        }

        @Override
        protected void alertOther(Mob mob, LivingEntity target) {
            if (mob instanceof GardenBee && this.mob.hasLineOfSight(target)) {
                mob.setTarget(target);
            }
        }
    }

    class BeeLocateHiveGoal extends GardenBee.BaseBeeGoal {
        @Override
        public boolean canBeeUse() {
            return GardenBee.this.remainingCooldownBeforeLocatingNewHive == 0 && !GardenBee.this.hasHive() && GardenBee.this.wantsToEnterHive();
        }

        @Override
        public boolean canBeeContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            GardenBee.this.remainingCooldownBeforeLocatingNewHive = 200;
            List<BlockPos> list = this.findNearbyHivesWithSpace();
            if (!list.isEmpty()) {
                for (BlockPos blockpos : list) {
                    if (!GardenBee.this.goToHiveGoal.isTargetBlacklisted(blockpos)) {
                        GardenBee.this.hivePos = blockpos;
                        return;
                    }
                }

                GardenBee.this.goToHiveGoal.clearBlacklist();
                GardenBee.this.hivePos = list.get(0);
            }
        }

        private List<BlockPos> findNearbyHivesWithSpace() {
            BlockPos blockpos = GardenBee.this.blockPosition();
            PoiManager poimanager = ((ServerLevel) GardenBee.this.level()).getPoiManager();
            Stream<PoiRecord> stream = poimanager.getInRange(
                    poi -> poi.value() == PoiRegister.GARDEN_BEE_NEST.get(),
                    blockpos, 20, PoiManager.Occupancy.ANY);
            return stream.map(PoiRecord::getPos)
                    .filter(GardenBee.this::doesHiveHaveSpace)
                    .sorted(Comparator.comparingDouble(pos -> pos.distSqr(blockpos)))
                    .collect(Collectors.toList());
        }
    }

    class BeeLookControl extends LookControl {
        BeeLookControl(Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (!GardenBee.this.isAngry()) {
                super.tick();
            }
        }

        @Override
        protected boolean resetXRotOnTick() {
            return !GardenBee.this.beePollinateGoal.isPollinating();
        }
    }

    class BeePollinateGoal extends GardenBee.BaseBeeGoal {
        private static final int MIN_POLLINATION_TICKS = 400;
        private static final int MIN_FIND_FLOWER_RETRY_COOLDOWN = 20;
        private static final int MAX_FIND_FLOWER_RETRY_COOLDOWN = 60;
        private final Predicate<BlockState> VALID_POLLINATION_BLOCKS = p_28074_ -> {
            if (p_28074_.hasProperty(BlockStateProperties.WATERLOGGED) && p_28074_.getValue(BlockStateProperties.WATERLOGGED)) {
                return false;
            } else if (p_28074_.is(BlockTags.FLOWERS)) {
                return p_28074_.is(Blocks.SUNFLOWER) ? p_28074_.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER : true;
            } else {
                return false;
            }
        };
        private static final double ARRIVAL_THRESHOLD = 0.1;
        private static final int POSITION_CHANGE_CHANCE = 25;
        private static final float SPEED_MODIFIER = 0.35F;
        private static final float HOVER_HEIGHT_WITHIN_FLOWER = 0.6F;
        private static final float HOVER_POS_OFFSET = 0.33333334F;
        private int successfulPollinatingTicks;
        private int lastSoundPlayedTick;
        private boolean pollinating;
        @Nullable
        private Vec3 hoverPos;
        private int pollinatingTicks;
        private static final int MAX_POLLINATING_TICKS = 600;

        BeePollinateGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canBeeUse() {
            if (GardenBee.this.remainingCooldownBeforeLocatingNewFlower > 0) {
                return false;
            } else if (GardenBee.this.hasNectar()) {
                return false;
            } else if (GardenBee.this.level().isRaining()) {
                return false;
            } else {
                Optional<BlockPos> optional = this.findNearbyFlower();
                if (optional.isPresent()) {
                    GardenBee.this.savedFlowerPos = optional.get();
                    GardenBee.this.navigation
                            .moveTo(
                                    (double) GardenBee.this.savedFlowerPos.getX() + 0.5,
                                    (double) GardenBee.this.savedFlowerPos.getY() + 0.5,
                                    (double) GardenBee.this.savedFlowerPos.getZ() + 0.5,
                                    1.2F
                            );
                    return true;
                } else {
                    GardenBee.this.remainingCooldownBeforeLocatingNewFlower = Mth.nextInt(GardenBee.this.random, 20, 60);
                    return false;
                }
            }
        }

        @Override
        public boolean canBeeContinueToUse() {
            if (!this.pollinating) {
                return false;
            } else if (!GardenBee.this.hasSavedFlowerPos()) {
                return false;
            } else if (GardenBee.this.level().isRaining()) {
                return false;
            } else if (this.hasPollinatedLongEnough()) {
                return GardenBee.this.random.nextFloat() < 0.2F;
            } else if (GardenBee.this.tickCount % 20 == 0 && !GardenBee.this.isFlowerValid(GardenBee.this.savedFlowerPos)) {
                GardenBee.this.savedFlowerPos = null;
                return false;
            } else {
                return true;
            }
        }

        private boolean hasPollinatedLongEnough() {
            return this.successfulPollinatingTicks > 400;
        }

        boolean isPollinating() {
            return this.pollinating;
        }

        void stopPollinating() {
            this.pollinating = false;
        }

        @Override
        public void start() {
            this.successfulPollinatingTicks = 0;
            this.pollinatingTicks = 0;
            this.lastSoundPlayedTick = 0;
            this.pollinating = true;
            GardenBee.this.resetTicksWithoutNectarSinceExitingHive();
        }

        @Override
        public void stop() {
            if (this.hasPollinatedLongEnough()) {
                GardenBee.this.setHasNectar(true);
            }

            this.pollinating = false;
            GardenBee.this.navigation.stop();
            GardenBee.this.remainingCooldownBeforeLocatingNewFlower = 200;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            this.pollinatingTicks++;
            if (this.pollinatingTicks > 600) {
                GardenBee.this.savedFlowerPos = null;
            } else {
                Vec3 vec3 = Vec3.atBottomCenterOf(GardenBee.this.savedFlowerPos).add(0.0, 0.6F, 0.0);
                if (vec3.distanceTo(GardenBee.this.position()) > 1.0) {
                    this.hoverPos = vec3;
                    this.setWantedPos();
                } else {
                    if (this.hoverPos == null) {
                        this.hoverPos = vec3;
                    }

                    boolean flag = GardenBee.this.position().distanceTo(this.hoverPos) <= 0.1;
                    boolean flag1 = true;
                    if (!flag && this.pollinatingTicks > 600) {
                        GardenBee.this.savedFlowerPos = null;
                    } else {
                        if (flag) {
                            boolean flag2 = GardenBee.this.random.nextInt(25) == 0;
                            if (flag2) {
                                this.hoverPos = new Vec3(vec3.x() + (double)this.getOffset(), vec3.y(), vec3.z() + (double)this.getOffset());
                                GardenBee.this.navigation.stop();
                            } else {
                                flag1 = false;
                            }

                            GardenBee.this.getLookControl().setLookAt(vec3.x(), vec3.y(), vec3.z());
                        }

                        if (flag1) {
                            this.setWantedPos();
                        }

                        this.successfulPollinatingTicks++;
                        if (GardenBee.this.random.nextFloat() < 0.05F && this.successfulPollinatingTicks > this.lastSoundPlayedTick + 60) {
                            this.lastSoundPlayedTick = this.successfulPollinatingTicks;
                            GardenBee.this.playSound(SoundEvents.BEE_POLLINATE, 1.0F, 1.0F);
                        }
                    }
                }
            }
        }

        private void setWantedPos() {
            GardenBee.this.getMoveControl().setWantedPosition(this.hoverPos.x(), this.hoverPos.y(), this.hoverPos.z(), 0.35F);
        }

        private float getOffset() {
            return (GardenBee.this.random.nextFloat() * 2.0F - 1.0F) * 0.33333334F;
        }

        private Optional<BlockPos> findNearbyFlower() {
            return this.findNearestBlock(this.VALID_POLLINATION_BLOCKS, 5.0);
        }

        private Optional<BlockPos> findNearestBlock(Predicate<BlockState> predicate, double distance) {
            BlockPos blockpos = GardenBee.this.blockPosition();
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            for (int i = 0; (double)i <= distance; i = i > 0 ? -i : 1 - i) {
                for (int j = 0; (double)j < distance; j++) {
                    for (int k = 0; k <= j; k = k > 0 ? -k : 1 - k) {
                        for (int l = k < j && k > -j ? j : 0; l <= j; l = l > 0 ? -l : 1 - l) {
                            blockpos$mutableblockpos.setWithOffset(blockpos, k, i - 1, l);
                            if (blockpos.closerThan(blockpos$mutableblockpos, distance)
                                    && predicate.test(GardenBee.this.level().getBlockState(blockpos$mutableblockpos))) {
                                return Optional.of(blockpos$mutableblockpos);
                            }
                        }
                    }
                }
            }

            return Optional.empty();
        }
    }

    class BeeWanderGoal extends Goal {
        private static final int WANDER_THRESHOLD = 22;

        BeeWanderGoal() {
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
            Vec3 vec3 = this.findPos();
            if (vec3 != null) {
                GardenBee.this.navigation.moveTo(GardenBee.this.navigation.createPath(BlockPos.containing(vec3), 1), 1.0);
            }
        }

        @Nullable
        private Vec3 findPos() {
            Vec3 vec3;
            if (GardenBee.this.isHiveValid() && !GardenBee.this.closerThan(GardenBee.this.hivePos, 22)) {
                Vec3 vec31 = Vec3.atCenterOf(GardenBee.this.hivePos);
                vec3 = vec31.subtract(GardenBee.this.position()).normalize();
            } else {
                vec3 = GardenBee.this.getViewVector(0.0F);
            }

            int i = 8;
            Vec3 vec32 = HoverRandomPos.getPos(GardenBee.this, 8, 7, vec3.x, vec3.z, (float) (Math.PI / 2), 3, 1);
            return vec32 != null ? vec32 : AirAndWaterRandomPos.getPos(GardenBee.this, 8, 4, -2, vec3.x, vec3.z, (float) (Math.PI / 2));
        }
    }
}
