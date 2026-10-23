package com.thelema.thrive.generic.entity;

import com.thelema.thrive.generic.blockentity.GardenBeeNestBlockEntity;
import com.thelema.thrive.register.BlockRegister;
import com.thelema.thrive.register.EntityRegister;
import com.thelema.thrive.register.PoiRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
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
import net.minecraft.util.valueproviders.UniformInt;
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
import net.minecraft.world.item.alchemy.PotionContents;
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
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/**
 * 花园蜜蜂实体。
 * 继承了 TamableAnimal（可驯服），实现了 NeutralMob（中立反击）、FlyingAnimal（飞行）。
 * 具有采蜜、回巢、战斗突刺、药水淬毒等复杂行为。
 */
public class GardenBee extends TamableAnimal implements NeutralMob, FlyingAnimal {

    public static final float FLAP_DEGREES_PER_TICK = 120.32113F;
    public static final int TICKS_PER_FLAP = Mth.ceil(1.4959966F);
    public static final int ATTACK_INTERVAL = 40; // 攻击冷却：2秒
    public static final int LUNGE_TICKS = 10;     // 突刺前摇：0.5秒
    /** 每次喂蜜脾的驯服概率。 */
    public static final float TAME_CHANCE = 0.25F;

    private static final int IMPACT_TICK = 5;     // 突刺第5 tick 结算伤害
    private static final int FLAG_ROLL = 2;       // 翻滚状态标志位
    private static final int FLAG_HAS_STUNG = 4;  // 蛰人状态（本类已不使用，保留以防报错）
    private static final int FLAG_HAS_NECTAR = 8; // 携带花蜜标志位
    private static final int COOLDOWN_BEFORE_LOCATING_NEW_HIVE = 200;
    private static final int COOLDOWN_BEFORE_LOCATING_NEW_FLOWER = 200;

    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID =
            SynchedEntityData.defineId(GardenBee.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_REMAINING_ANGER_TIME =
            SynchedEntityData.defineId(GardenBee.class, EntityDataSerializers.INT);

    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);

    @Nullable private UUID persistentAngerTarget;
    private float rollAmount;
    private float rollAmountO;
    private int stayOutOfHiveCountdown;
    private int numCropsGrownSincePollination;
    private int underWaterTicks;

    int ticksWithoutNectarSinceExitingHive;
    int remainingCooldownBeforeLocatingNewHive;
    int remainingCooldownBeforeLocatingNewFlower = Mth.nextInt(this.random, 20, 60);

    // 战斗状态
    int attackCooldown;
    private int lungeTicks;
    @Nullable private Entity pendingTarget;

    @Nullable BlockPos savedFlowerPos;
    @Nullable BlockPos hivePos;

    // 核心 Goal 引用
    GardenBeePollinateGoal beePollinateGoal;
    GardenBeeGoToHiveGoal goToHiveGoal;

    /** 蜂巢里的滞留药水会给针刺附毒；由蜂巢 BE 在出巢时写入。 */
    @Nullable public PotionContents stingerPotionContents;

    public GardenBee(EntityType<? extends GardenBee> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.lookControl = new GardenBeeLookControl(this);
        // 设置寻路惩罚：蜜蜂会躲避火、水等危险方块
        this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, 16.0F);
        this.setPathfindingMalus(PathType.COCOA, -1.0F);
        this.setPathfindingMalus(PathType.FENCE, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.6F)
                .add(Attributes.MOVEMENT_SPEED, 0.3F)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS_ID, (byte) 0);
        builder.define(DATA_REMAINING_ANGER_TIME, 0);
    }

    @Override
    protected void registerGoals() {
        // 优先级从 0 到 9 依次降低
        this.goalSelector.addGoal(0, new GardenBeeFleeGoal(this)); // 残血逃跑
        this.goalSelector.addGoal(0, new GardenBeeAttackGoal(this)); // 战斗突刺
        this.goalSelector.addGoal(1, new GardenBeeEnterHiveGoal(this)); // 入巢
        this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.25D, 10.0F, 2.0F)); // 跟随主人
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0)); // 繁殖
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.25, stack -> stack.is(ItemTags.BEE_FOOD), false)); // 被花吸引
        this.beePollinateGoal = new GardenBeePollinateGoal(this);
        this.goalSelector.addGoal(4, this.beePollinateGoal); // 采蜜
        this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.25)); // 跟随幼崽
        this.goalSelector.addGoal(5, new GardenBeeLocateHiveGoal(this)); // 寻找蜂巢
        this.goToHiveGoal = new GardenBeeGoToHiveGoal(this);
        this.goalSelector.addGoal(5, this.goToHiveGoal); // 飞向蜂巢
        this.goalSelector.addGoal(6, new GardenBeeGoToKnownFlowerGoal(this)); // 飞向已知花朵
        this.goalSelector.addGoal(7, new GardenBeeGrowCropGoal(this)); // 催熟作物
        this.goalSelector.addGoal(8, new GardenBeeWanderGoal(this)); // 闲逛
        this.goalSelector.addGoal(9, new FloatGoal(this)); // 浮水

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this)); // 主人被攻击
        this.targetSelector.addGoal(1, new OwnerHurtTargetGoal(this)); // 主人攻击别人
        this.targetSelector.addGoal(1, new GardenBeeHurtByOtherGoal(this).setAlertOthers()); // 被攻击反击
        this.targetSelector.addGoal(2, new GardenBeeBecomeAngryTargetGoal(this)); // 中立愤怒锁定
        this.targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, true)); // 愤怒时间重置
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level) {
            @Override
            public boolean isStableDestination(BlockPos pos) {
                return !this.level.getBlockState(pos.below()).isAir();
            }

            @Override
            public void tick() {
                // 采蜜悬停时，暂停寻路系统的接管，防止它把蜜蜂拽走
                if (!GardenBee.this.beePollinateGoal.isPollinating()) {
                    super.tick();
                }
            }
        };
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(false);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getBlockState(pos).isAir() ? 10.0F : 0.0F;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.BEE_FOOD);
    }

    // ==================== 交互与驯服 ====================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean honeycomb = stack.is(Items.HONEYCOMB);

        // 1. 用蜜脾驯服：每次 25%
        if (honeycomb && !this.isTame()) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            if (!this.level().isClientSide) this.tryTame(player);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 2. 用蜜脾回血
        if (honeycomb && this.isTame() && this.isOwnedBy(player) && this.getHealth() < this.getMaxHealth()) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            this.heal(2.0F);
            this.level().broadcastEntityEvent(this, (byte) 7);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 3. 空手右键切换巡逻/跟随模式
        if (this.isTame() && this.isOwnedBy(player) && stack.isEmpty()) {
            boolean sitting = !this.isOrderedToSit();
            this.setOrderedToSit(sitting);
            this.getNavigation().stop();
            this.setTarget(null); // 清除当前目标
            player.displayClientMessage(Component.translatable(
                    sitting ? "entity.thrive.garden_bee.patrol" : "entity.thrive.garden_bee.follow"), true);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    /** 单次驯服尝试：25% 成功；成功播爱心，失败播烟雾。 */
    private void tryTame(Player player) {
        if (this.random.nextFloat() >= TAME_CHANCE) {
            this.level().broadcastEntityEvent(this, (byte) 6);
            return;
        }
        this.tame(player);
        this.getNavigation().stop();
        this.setTarget(null);
        this.level().broadcastEntityEvent(this, (byte) 7);
    }

    /**
     * 统一的“队友”判定：自己、任何花园蜂、主人、主人的其它宠物、同队玩家。
     * 所有索敌/反击/补刀路径都必须先过这里。
     */
    public boolean isAlly(@Nullable Entity entity) {
        if (entity == null) return false;
        if (entity == this || entity instanceof GardenBee) return true;

        UUID owner = this.getOwnerUUID();
        if (owner != null) {
            if (entity.getUUID().equals(owner)) return true;
            if (entity instanceof OwnableEntity pet && owner.equals(pet.getOwnerUUID())) return true;
        }
        return this.isAlliedTo(entity);
    }

    /** 统一拦截：队友永远不会成为攻击目标；如果记仇对象是队友，顺手清空愤怒。 */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && this.isAlly(target)) {
            if (target.getUUID().equals(this.getPersistentAngerTarget())) {
                this.stopBeingAngry();
            }
            return;
        }
        super.setTarget(target);
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return !this.isAlly(target) && super.wantsToAttack(target, owner);
    }

    /** 目标一旦变成队友，立刻放弃并清空愤怒。 */
    private void clearAllyAnger() {
        LivingEntity target = this.getTarget();
        if (target != null && this.isAlly(target)) {
            this.stopBeingAngry();
        }

        UUID angryAt = this.getPersistentAngerTarget();
        if (angryAt == null) return;

        if (angryAt.equals(this.getOwnerUUID())) {
            this.stopBeingAngry();
            return;
        }
        if (this.level() instanceof ServerLevel server) {
            Entity entity = server.getEntity(angryAt);
            if (entity != null && this.isAlly(entity)) {
                this.stopBeingAngry();
            }
        }
    }

    // ==================== 战斗 ====================

    /**
     * 拦截原版伤害结算，改为“预约突刺”。
     * 如果冷却好了，记录目标并开始突刺前摇，返回 true 告诉 AI 已经执行了动作。
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        if (this.isAlly(target)) return false;
        if (this.attackCooldown > 0) return false;
        this.pendingTarget = target;
        this.attackCooldown = ATTACK_INTERVAL;
        this.lungeTicks = LUNGE_TICKS;
        this.getNavigation().stop();
        return true;
    }

    /**
     * 真正造成伤害的方法。在前摇到达 IMPACT_TICK 时由 aiStep 调用。
     * 包含淬毒效果结算。
     */
    private void actualHurtTarget(Entity target) {
        if (this.isAlly(target)) return;
        DamageSource source = this.damageSources().sting(this);
        boolean hurt = target.hurt(source, (float) ((int) this.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        if (!hurt) return;

        if (this.level() instanceof ServerLevel serverLevel) {
            EnchantmentHelper.doPostAttackEffects(serverLevel, target, source);
        }
        // 结算淬毒效果
        if (this.stingerPotionContents != null && target instanceof LivingEntity living) {
            for (MobEffectInstance effect : this.stingerPotionContents.getAllEffects()) {
                living.addEffect(new MobEffectInstance(
                        effect.getEffect(), effect.getDuration() / 4, effect.getAmplifier()), this);
            }
        }
        this.playSound(SoundEvents.BEE_STING, 1.0F, 1.0F);
    }

    // ==================== tick ====================

    @Override
    public void tick() {
        super.tick();
        // 携带花蜜时，掉落粒子
        if (this.hasNectar() && this.getCropsGrownSincePollination() < 10 && this.random.nextFloat() < 0.05F) {
            for (int i = 0; i < this.random.nextInt(2) + 1; i++) {
                this.spawnFluidParticle(this.level(),
                        this.getX() - 0.3F, this.getX() + 0.3F,
                        this.getZ() - 0.3F, this.getZ() + 0.3F,
                        this.getY(0.5), ParticleTypes.FALLING_NECTAR);
            }
        }
        this.updateRollAmount();
    }

    private void spawnFluidParticle(Level level, double startX, double endX, double startZ,
                                    double endZ, double y, ParticleOptions particle) {
        level.addParticle(particle,
                Mth.lerp(level.random.nextDouble(), startX, endX), y,
                Mth.lerp(level.random.nextDouble(), startZ, endZ), 0.0, 0.0, 0.0);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) return; // 仅服务端执行逻辑

        // 更新各类倒计时
        if (this.stayOutOfHiveCountdown > 0) this.stayOutOfHiveCountdown--;
        if (this.remainingCooldownBeforeLocatingNewHive > 0) this.remainingCooldownBeforeLocatingNewHive--;
        if (this.remainingCooldownBeforeLocatingNewFlower > 0) this.remainingCooldownBeforeLocatingNewFlower--;
        if (this.attackCooldown > 0) this.attackCooldown--;
        if (this.lungeTicks > 0) this.lungeTicks--;

        // 突刺冲刺与伤害结算
        if (this.lungeTicks > 0) {
            // 突刺冲刺：给蜜蜂施加向目标方向的物理加速度
            if (this.pendingTarget != null) {
                Vec3 dir = this.pendingTarget.position().subtract(this.position()).normalize();
                // 0.15 为冲刺力度，可以微调
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5).add(dir.scale(0.15)));
                this.getLookControl().setLookAt(this.pendingTarget);
            }

            // 第 5 tick 造成伤害，判定范围放大到 4 格（平方 16.0），防止冲刺时脱靶
            if (this.pendingTarget != null && this.lungeTicks == LUNGE_TICKS - IMPACT_TICK) {
                if (this.pendingTarget.isAlive() && this.pendingTarget.distanceToSqr(this) < 16.0) {
                    this.actualHurtTarget(this.pendingTarget);
                }
                this.pendingTarget = null; // 结算完毕
            }
        }

        // 突刺期间播放翻滚动画
        this.setRolling(this.lungeTicks > 0);

        // 血量不满且有花蜜时，消耗花蜜回血
        if (this.getHealth() < this.getMaxHealth() && this.hasNectar()) {
            this.dropOffNectar();
            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 0));
            this.playSound(SoundEvents.BEE_POLLINATE, 1.0F, 1.0F);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HEART,
                        this.getX(), this.getY() + 0.5, this.getZ(), 5, 0.5, 0.5, 0.5, 0.1);
            }
        }

        // 定期检查家是否存在，不存在就清空
        if (this.tickCount % 20 == 0 && !this.isHiveValid()) {
            this.hivePos = null;
        }
    }

    @Override
    protected void customServerAiStep() {
        // 溺水伤害
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
        this.updatePersistentAnger((ServerLevel) this.level(), false);
        this.clearAllyAnger();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) return false;
        if (!this.level().isClientSide) this.beePollinateGoal.stopPollinating();
        return super.hurt(source, amount);
    }

    // ==================== 状态 ====================

    public boolean hasNectar() {
        return this.getFlag(FLAG_HAS_NECTAR);
    }

    void setHasNectar(boolean value) {
        if (value) this.resetTicksWithoutNectarSinceExitingHive();
        this.setFlag(FLAG_HAS_NECTAR, value);
    }

    public boolean hasStung() {
        return this.getFlag(FLAG_HAS_STUNG);
    }

    private void setHasStung(boolean value) {
        this.setFlag(FLAG_HAS_STUNG, value);
    }

    private boolean isRolling() {
        return this.getFlag(FLAG_ROLL);
    }

    private void setRolling(boolean value) {
        this.setFlag(FLAG_ROLL, value);
    }

    private void setFlag(int flag, boolean value) {
        byte flags = this.entityData.get(DATA_FLAGS_ID);
        this.entityData.set(DATA_FLAGS_ID, (byte) (value ? flags | flag : flags & ~flag));
    }

    private boolean getFlag(int flag) {
        return (this.entityData.get(DATA_FLAGS_ID) & flag) != 0;
    }

    public float getRollAmount(float partialTick) {
        return Mth.lerp(partialTick, this.rollAmountO, this.rollAmount);
    }

    private void updateRollAmount() {
        this.rollAmountO = this.rollAmount;
        this.rollAmount = this.isRolling()
                ? Math.min(1.0F, this.rollAmount + 0.2F)
                : Math.max(0.0F, this.rollAmount - 0.24F);
    }

    public void dropOffNectar() {
        this.setHasNectar(false);
        this.resetNumCropsGrownSincePollination();
    }

    void resetTicksWithoutNectarSinceExitingHive() {
        this.ticksWithoutNectarSinceExitingHive = 0;
    }

    int getCropsGrownSincePollination() {
        return this.numCropsGrownSincePollination;
    }

    void incrementNumCropsGrownSincePollination() {
        this.numCropsGrownSincePollination++;
    }

    private void resetNumCropsGrownSincePollination() {
        this.numCropsGrownSincePollination = 0;
    }

    // ==================== 蜂巢 ====================

    public void setStayOutOfHiveCountdown(int ticks) {
        this.stayOutOfHiveCountdown = ticks;
    }

    public void setHivePos(BlockPos pos) {
        this.hivePos = pos;
    }

    boolean wantsToEnterHive() {
        if (this.stayOutOfHiveCountdown > 0
                || this.beePollinateGoal.isPollinating()
                || this.hasStung()
                || this.getTarget() != null) {
            return false;
        }
        boolean wants = this.ticksWithoutNectarSinceExitingHive > 3600
                || this.level().isRaining()
                || this.level().isNight()
                || this.hasNectar();
        return wants && !this.isHiveNearFire();
    }

    boolean doesHiveHaveSpace(BlockPos pos) {
        return this.level().getBlockEntity(pos) instanceof GardenBeeNestBlockEntity hive && !hive.isFull();
    }

    boolean isHiveValid() {
        if (this.hivePos == null || this.isTooFarAway(this.hivePos)) return false;
        return this.level().getBlockEntity(this.hivePos) instanceof GardenBeeNestBlockEntity;
    }

    private boolean isHiveNearFire() {
        if (this.hivePos == null) return false;
        BlockEntity be = this.level().getBlockEntity(this.hivePos);
        return be instanceof GardenBeeNestBlockEntity hive && hive.isFireNearby();
    }

    // ==================== 工具 ====================

    boolean isTooFarAway(BlockPos pos) {
        return !this.closerThan(pos, 32);
    }

    boolean closerThan(BlockPos pos, int distance) {
        return pos.closerThan(this.blockPosition(), distance);
    }

    void pathfindRandomlyTowards(BlockPos pos) {
        Vec3 target = Vec3.atBottomCenterOf(pos);
        BlockPos here = this.blockPosition();
        int yOffset = 0;
        int dy = (int) target.y - here.getY();
        if (dy > 2) yOffset = 4;
        else if (dy < -2) yOffset = -4;

        int xz = 6;
        int y = 8;
        int manhattan = here.distManhattan(pos);
        if (manhattan < 15) {
            xz = manhattan / 2;
            y = manhattan / 2;
        }

        Vec3 random = AirRandomPos.getPosTowards(this, xz, y, yOffset, target,
                (float) (Math.PI / 10));
        if (random != null) {
            this.getNavigation().setMaxVisitedNodesMultiplier(0.5F);
            this.getNavigation().moveTo(random.x, random.y, random.z, 1.0);
        }
    }

    boolean isFlowerValid(BlockPos pos) {
        return this.level().isLoaded(pos) && this.level().getBlockState(pos).is(BlockTags.FLOWERS);
    }

    @Nullable
    public BlockPos getSavedFlowerPos() {
        return this.savedFlowerPos;
    }

    public boolean hasSavedFlowerPos() {
        return this.savedFlowerPos != null;
    }

    public void setSavedFlowerPos(BlockPos pos) {
        this.savedFlowerPos = pos;
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
        this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    // ==================== NBT ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.hivePos != null) tag.put("hive_pos", NbtUtils.writeBlockPos(this.hivePos));
        if (this.savedFlowerPos != null) tag.put("flower_pos", NbtUtils.writeBlockPos(this.savedFlowerPos));

        tag.putBoolean("HasNectar", this.hasNectar());
        tag.putBoolean("HasStung", this.hasStung());
        tag.putInt("TicksSincePollination", this.ticksWithoutNectarSinceExitingHive);
        tag.putInt("CannotEnterHiveTicks", this.stayOutOfHiveCountdown);
        tag.putInt("CropsGrownSincePollination", this.numCropsGrownSincePollination);
        this.addPersistentAngerSaveData(tag);

        // 保存淬毒药水效果
        if (this.stingerPotionContents != null) {
            PotionContents.CODEC.encodeStart(NbtOps.INSTANCE, this.stingerPotionContents)
                    .result().ifPresent(data -> tag.put("StingerPotion", data));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        this.hivePos = NbtUtils.readBlockPos(tag, "hive_pos").orElse(null);
        this.savedFlowerPos = NbtUtils.readBlockPos(tag, "flower_pos").orElse(null);
        super.readAdditionalSaveData(tag);

        this.setHasNectar(tag.getBoolean("HasNectar"));
        this.setHasStung(tag.getBoolean("HasStung"));
        this.ticksWithoutNectarSinceExitingHive = tag.getInt("TicksSincePollination");
        this.stayOutOfHiveCountdown = tag.getInt("CannotEnterHiveTicks");
        this.numCropsGrownSincePollination = tag.getInt("CropsGrownSincePollination");
        this.readPersistentAngerSaveData(this.level(), tag);

        // 读取淬毒药水效果
        if (tag.contains("StingerPotion")) {
            PotionContents.CODEC.parse(NbtOps.INSTANCE, tag.get("StingerPotion"))
                    .result().ifPresent(contents -> this.stingerPotionContents = contents);
        }
    }

    // ==================== 其它 ====================

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
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
    @Override
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

    @Override
    @Deprecated
    protected void jumpInLiquid(TagKey<Fluid> fluidTag) {
        this.jumpInLiquidInternal();
    }

    @Override
    public void jumpInFluid(FluidType type) {
        this.jumpInLiquidInternal();
    }

    private void jumpInLiquidInternal() {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.01, 0.0));
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.5F * this.getEyeHeight(), this.getBbWidth() * 0.2F);
    }

    // ==================== 内部 Goal ====================

    static class GardenBeeAttackGoal extends MeleeAttackGoal {
        private final GardenBee bee;

        GardenBeeAttackGoal(GardenBee bee) {
            super(bee, 1.4F, true);
            this.bee = bee;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && canFight();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && canFight();
        }

        @Override
        protected boolean canPerformAttack(LivingEntity target) {
            if (this.bee.isAlly(target)) return false;
            // 如果处于冷却，拒绝
            if (this.bee.attackCooldown > 0) {
                return false;
            }
            // 距离在 4 格内（平方16.0）且有视线，直接起手！
            return this.bee.distanceToSqr(target) < 16.0
                    && this.bee.hasLineOfSight(target);
        }

        private boolean canFight() {
            // 愤怒且血量大于 30% 才会战斗
            return this.bee.isAngry() && this.bee.getHealth() > this.bee.getMaxHealth() * 0.3F;
        }
    }

    static class GardenBeeHurtByOtherGoal extends HurtByTargetGoal {
        private final GardenBee bee;

        GardenBeeHurtByOtherGoal(GardenBee bee) {
            super(bee);
            this.bee = bee;
        }

        @Override
        public boolean canContinueToUse() {
            return this.bee.isAngry() && super.canContinueToUse();
        }

        @Override
        protected void alertOther(Mob mob, LivingEntity target) {
            if (this.bee.isAlly(target)) return;
            if (mob instanceof GardenBee other && other.hasLineOfSight(target)) {
                mob.setTarget(target);
            }
        }
    }

    static class GardenBeeWanderGoal extends Goal {
        static final int WANDER_THRESHOLD = 22;
        private final GardenBee bee;

        GardenBeeWanderGoal(GardenBee bee) {
            this.bee = bee;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.bee.getNavigation().isDone() && this.bee.getRandom().nextInt(10) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return this.bee.getNavigation().isInProgress();
        }

        @Override
        public void start() {
            Vec3 pos = this.findPos();
            if (pos != null) {
                this.bee.getNavigation().moveTo(
                        this.bee.getNavigation().createPath(BlockPos.containing(pos), 1), 1.0);
            }
        }

        @Nullable
        private Vec3 findPos() {
            Vec3 direction;
            // 如果离家太远，就向家飞；否则顺着视线方向闲逛
            if (this.bee.isHiveValid() && !this.bee.closerThan(this.bee.hivePos, WANDER_THRESHOLD)) {
                direction = Vec3.atCenterOf(this.bee.hivePos).subtract(this.bee.position()).normalize();
            } else {
                direction = this.bee.getViewVector(0.0F);
            }

            Vec3 pos = HoverRandomPos.getPos(this.bee, 8, 7,
                    direction.x, direction.z, (float) (Math.PI / 2), 3, 1);
            return pos != null ? pos : AirAndWaterRandomPos.getPos(this.bee, 8, 4, -2,
                    direction.x, direction.z, (float) (Math.PI / 2));
        }
    }

    /** 花园蜂 Goal 的公共门面：愤怒时统一停用。 */
    abstract static class GardenBeeBaseGoal extends Goal {
        protected final GardenBee bee;

        protected GardenBeeBaseGoal(GardenBee bee) {
            this.bee = bee;
        }

        protected abstract boolean canBeeUse();

        protected abstract boolean canBeeContinueToUse();

        @Override
        public final boolean canUse() {
            return !this.bee.isAngry() && this.canBeeUse();
        }

        @Override
        public final boolean canContinueToUse() {
            return !this.bee.isAngry() && this.canBeeContinueToUse();
        }
    }

    static class GardenBeeLocateHiveGoal extends GardenBeeBaseGoal {
        GardenBeeLocateHiveGoal(GardenBee bee) {
            super(bee);
        }

        @Override
        protected boolean canBeeUse() {
            return this.bee.remainingCooldownBeforeLocatingNewHive == 0
                    && this.bee.hivePos == null
                    && this.bee.wantsToEnterHive();
        }

        @Override
        protected boolean canBeeContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            this.bee.remainingCooldownBeforeLocatingNewHive = 200;
            List<BlockPos> hives = findNearbyHivesWithSpace();
            // 避开黑名单里的蜂巢（之前卡住过的）
            for (BlockPos pos : hives) {
                if (!this.bee.goToHiveGoal.isTargetBlacklisted(pos)) {
                    this.bee.hivePos = pos;
                    return;
                }
            }
            if (!hives.isEmpty()) {
                this.bee.goToHiveGoal.clearBlacklist();
                this.bee.hivePos = hives.get(0);
            }
        }

        private List<BlockPos> findNearbyHivesWithSpace() {
            BlockPos origin = this.bee.blockPosition();
            PoiManager poiManager = ((ServerLevel) this.bee.level()).getPoiManager();
            return poiManager.getInRange(
                            poi -> poi.value() == PoiRegister.GARDEN_BEE_NEST.get(),
                            origin, 20, PoiManager.Occupancy.ANY)
                    .map(PoiRecord::getPos)
                    .filter(this.bee::doesHiveHaveSpace)
                    .sorted(Comparator.comparingDouble(pos -> pos.distSqr(origin)))
                    .toList();
        }
    }

    static class GardenBeeBecomeAngryTargetGoal extends NearestAttackableTargetGoal<Player> {
        private final GardenBee bee;

        GardenBeeBecomeAngryTargetGoal(GardenBee bee) {
            super(bee, Player.class, 10, true, false,
                    target -> bee.isAngryAt(target) && !bee.isAlly(target));
            this.bee = bee;
        }

        @Override
        public boolean canUse() {
            return this.bee.isAngry() && !this.bee.hasStung() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            if (this.bee.isAngry() && !this.bee.hasStung() && this.mob.getTarget() != null) {
                return super.canContinueToUse();
            }
            this.targetMob = null;
            return false;
        }
    }

    static class GardenBeeEnterHiveGoal extends GardenBeeBaseGoal {
        GardenBeeEnterHiveGoal(GardenBee bee) {
            super(bee);
        }

        @Override
        protected boolean canBeeUse() {
            if (this.bee.hivePos == null || !this.bee.wantsToEnterHive()) return false;
            if (!this.bee.hivePos.closerToCenterThan(this.bee.position(), 2.0)) return false;
            if (!(this.bee.level().getBlockEntity(this.bee.hivePos) instanceof GardenBeeNestBlockEntity hive)) {
                this.bee.hivePos = null;
                return false;
            }
            if (!hive.isFull()) return true;
            this.bee.hivePos = null;
            return false;
        }

        @Override
        protected boolean canBeeContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            if (this.bee.level().getBlockEntity(this.bee.hivePos) instanceof GardenBeeNestBlockEntity hive) {
                hive.addOccupant(this.bee);
            }
        }
    }

    static class GardenBeeGoToKnownFlowerGoal extends GardenBeeBaseGoal {
        static final int MAX_TRAVELLING_TICKS = 600;
        int travellingTicks = this.bee.level().random.nextInt(10);

        GardenBeeGoToKnownFlowerGoal(GardenBee bee) {
            super(bee);
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        protected boolean canBeeUse() {
            return this.bee.savedFlowerPos != null
                    && !this.bee.hasRestriction()
                    && this.bee.ticksWithoutNectarSinceExitingHive > 2400
                    && this.bee.isFlowerValid(this.bee.savedFlowerPos)
                    && !this.bee.closerThan(this.bee.savedFlowerPos, 2);
        }

        @Override
        protected boolean canBeeContinueToUse() {
            return canBeeUse();
        }

        @Override
        public void start() {
            this.travellingTicks = 0;
        }

        @Override
        public void stop() {
            this.travellingTicks = 0;
            this.bee.getNavigation().stop();
            this.bee.getNavigation().resetMaxVisitedNodesMultiplier();
        }

        @Override
        public void tick() {
            if (this.bee.savedFlowerPos == null) return;
            if (++this.travellingTicks > this.adjustedTickDelay(MAX_TRAVELLING_TICKS)) {
                this.bee.savedFlowerPos = null;
            } else if (!this.bee.getNavigation().isInProgress()) {
                if (this.bee.isTooFarAway(this.bee.savedFlowerPos)) {
                    this.bee.savedFlowerPos = null;
                } else {
                    this.bee.pathfindRandomlyTowards(this.bee.savedFlowerPos);
                }
            }
        }
    }

    static class GardenBeeLookControl extends LookControl {
        private final GardenBee bee;

        GardenBeeLookControl(GardenBee bee) {
            super(bee);
            this.bee = bee;
        }

        @Override
        public void tick() {
            // 愤怒时不要乱看，盯着目标
            if (!this.bee.isAngry()) super.tick();
        }

        @Override
        protected boolean resetXRotOnTick() {
            return !this.bee.beePollinateGoal.isPollinating();
        }
    }

    static class GardenBeeGrowCropGoal extends GardenBeeBaseGoal {
        static final int MAX_CROPS_GROWN = 10;
        static final int GROW_CHANCE = 30;

        GardenBeeGrowCropGoal(GardenBee bee) {
            super(bee);
        }

        @Override
        protected boolean canBeeUse() {
            return this.bee.getCropsGrownSincePollination() < MAX_CROPS_GROWN
                    && this.bee.getRandom().nextFloat() >= 0.3F
                    && this.bee.hasNectar()
                    && this.bee.isHiveValid();
        }

        @Override
        protected boolean canBeeContinueToUse() {
            return canBeeUse();
        }

        @Override
        public void tick() {
            if (this.bee.getRandom().nextInt(this.adjustedTickDelay(GROW_CHANCE)) != 0) return;
            // 尝试催熟下方 1~2 格的作物
            for (int i = 1; i <= 2; i++) {
                BlockPos pos = this.bee.blockPosition().below(i);
                BlockState state = this.bee.level().getBlockState(pos);
                Block block = state.getBlock();
                BlockState grown = null;

                if (state.is(BlockTags.BEE_GROWABLES)) {
                    if (block instanceof CropBlock crop) {
                        if (!crop.isMaxAge(state)) {
                            grown = crop.getStateForAge(crop.getAge(state) + 1);
                        }
                    } else if (block instanceof StemBlock) {
                        int age = state.getValue(StemBlock.AGE);
                        if (age < 7) grown = state.setValue(StemBlock.AGE, age + 1);
                    } else if (state.is(Blocks.SWEET_BERRY_BUSH)) {
                        int age = state.getValue(SweetBerryBushBlock.AGE);
                        if (age < 3) grown = state.setValue(SweetBerryBushBlock.AGE, age + 1);
                    } else if (state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT)) {
                        ((BonemealableBlock) block).performBonemeal(
                                (ServerLevel) this.bee.level(), this.bee.getRandom(), pos, state);
                    }

                    if (grown != null) {
                        this.bee.level().levelEvent(2011, pos, 15); // 绿色粒子效果
                        this.bee.level().setBlockAndUpdate(pos, grown);
                        this.bee.incrementNumCropsGrownSincePollination();
                    }
                }
            }
        }
    }

    static class GardenBeeGoToHiveGoal extends GardenBeeBaseGoal {
        static final int MAX_TRAVELLING_TICKS = 600;
        static final int MAX_BLACKLISTED_TARGETS = 3;
        static final int TICKS_BEFORE_HIVE_DROP = 60;

        int travellingTicks = this.bee.level().random.nextInt(10);
        final List<BlockPos> blacklistedTargets = new ArrayList<>();
        private int ticksStuck;
        @Nullable private Path lastPath;

        GardenBeeGoToHiveGoal(GardenBee bee) {
            super(bee);
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        protected boolean canBeeUse() {
            return this.bee.hivePos != null
                    && !this.bee.hasRestriction()
                    && this.bee.wantsToEnterHive()
                    && !hasReachedTarget(this.bee.hivePos)
                    && this.bee.level().getBlockState(this.bee.hivePos).is(BlockRegister.GARDEN_BEE_NEST);
        }

        @Override
        protected boolean canBeeContinueToUse() {
            return canBeeUse();
        }

        @Override
        public void start() {
            this.travellingTicks = 0;
            this.ticksStuck = 0;
        }

        @Override
        public void stop() {
            this.travellingTicks = 0;
            this.ticksStuck = 0;
            this.bee.getNavigation().stop();
            this.bee.getNavigation().resetMaxVisitedNodesMultiplier();
        }

        @Override
        public void tick() {
            if (this.bee.hivePos == null) return;

            if (++this.travellingTicks > this.adjustedTickDelay(MAX_TRAVELLING_TICKS)) {
                dropAndBlacklistHive();
                return;
            }
            if (this.bee.getNavigation().isInProgress()) return;

            if (!this.bee.closerThan(this.bee.hivePos, 16)) {
                if (this.bee.isTooFarAway(this.bee.hivePos)) {
                    dropHive();
                } else {
                    this.bee.pathfindRandomlyTowards(this.bee.hivePos);
                }
                return;
            }

            if (!pathfindDirectlyTowards(this.bee.hivePos)) {
                dropAndBlacklistHive();
                return;
            }
            Path path = this.bee.getNavigation().getPath();
            // 检测是否卡在同一个路径上（未移动）
            if (this.lastPath != null && path != null && path.sameAs(this.lastPath)) {
                if (++this.ticksStuck > TICKS_BEFORE_HIVE_DROP) {
                    dropHive();
                    this.ticksStuck = 0;
                }
            } else {
                this.lastPath = path;
            }
        }

        boolean isTargetBlacklisted(BlockPos pos) {
            return this.blacklistedTargets.contains(pos);
        }

        void clearBlacklist() {
            this.blacklistedTargets.clear();
        }

        private boolean pathfindDirectlyTowards(BlockPos pos) {
            this.bee.getNavigation().setMaxVisitedNodesMultiplier(10.0F);
            this.bee.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 2, 1.0);
            Path path = this.bee.getNavigation().getPath();
            return path != null && path.canReach();
        }

        private void dropAndBlacklistHive() {
            if (this.bee.hivePos != null) {
                this.blacklistedTargets.add(this.bee.hivePos);
                while (this.blacklistedTargets.size() > MAX_BLACKLISTED_TARGETS) {
                    this.blacklistedTargets.remove(0);
                }
            }
            dropHive();
        }

        private void dropHive() {
            this.bee.hivePos = null;
            this.bee.remainingCooldownBeforeLocatingNewHive = 200;
        }

        private boolean hasReachedTarget(BlockPos pos) {
            if (this.bee.closerThan(pos, 2)) return true;
            Path path = this.bee.getNavigation().getPath();
            return path != null && path.getTarget().equals(pos) && path.canReach() && path.isDone();
        }
    }

    /** 低血量时逃离正在锁定自己的怪物。 */
    static class GardenBeeFleeGoal extends AvoidEntityGoal<Mob> {
        private final GardenBee bee;

        GardenBeeFleeGoal(GardenBee bee) {
            super(bee, Mob.class,
                    mob -> mob instanceof Mob attacker && attacker.getTarget() == bee,
                    16.0F, 1.6D, 2.0D,
                    EntitySelector.NO_CREATIVE_OR_SPECTATOR::test);
            this.bee = bee;
        }

        @Override
        public boolean canUse() {
            return this.bee.getHealth() <= this.bee.getMaxHealth() * 0.3F && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.bee.getHealth() <= this.bee.getMaxHealth() * 0.6F && super.canContinueToUse();
        }
    }

    static class GardenBeePollinateGoal extends GardenBeeBaseGoal {
        static final int MIN_POLLINATION_TICKS = 400;
        static final int MAX_POLLINATING_TICKS = 600;
        static final double ARRIVAL_THRESHOLD = 0.1;
        static final int POSITION_CHANGE_CHANCE = 25;
        static final float SPEED_MODIFIER = 0.35F;
        static final float HOVER_HEIGHT_WITHIN_FLOWER = 0.6F;
        static final float HOVER_POS_OFFSET = 0.33333334F;

        static final Predicate<BlockState> VALID_POLLINATION_BLOCKS = state -> {
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && state.getValue(BlockStateProperties.WATERLOGGED)) {
                return false;
            }
            if (!state.is(BlockTags.FLOWERS)) return false;
            return !state.is(Blocks.SUNFLOWER)
                    || state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
        };

        private int successfulPollinatingTicks;
        private int lastSoundPlayedTick;
        private boolean pollinating;
        @Nullable private Vec3 hoverPos;
        private int pollinatingTicks;

        GardenBeePollinateGoal(GardenBee bee) {
            super(bee);
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        protected boolean canBeeUse() {
            if (this.bee.remainingCooldownBeforeLocatingNewFlower > 0) return false;
            if (this.bee.hasNectar()) return false;
            if (this.bee.level().isRaining()) return false;

            Optional<BlockPos> flower = this.findNearbyFlower();
            if (flower.isEmpty()) {
                this.bee.remainingCooldownBeforeLocatingNewFlower =
                        Mth.nextInt(this.bee.getRandom(), 20, 60);
                return false;
            }

            this.bee.savedFlowerPos = flower.get();
            this.bee.getNavigation().moveTo(
                    this.bee.savedFlowerPos.getX() + 0.5,
                    this.bee.savedFlowerPos.getY() + 0.5,
                    this.bee.savedFlowerPos.getZ() + 0.5,
                    1.2F);
            return true;
        }

        @Override
        protected boolean canBeeContinueToUse() {
            if (!this.pollinating) return false;
            if (this.bee.savedFlowerPos == null) return false;
            if (this.bee.level().isRaining()) return false;
            if (this.hasPollinatedLongEnough()) return this.bee.getRandom().nextFloat() < 0.2F;
            if (this.bee.tickCount % 20 == 0 && !this.bee.isFlowerValid(this.bee.savedFlowerPos)) {
                this.bee.savedFlowerPos = null;
                return false;
            }
            return true;
        }

        @Override
        public void start() {
            this.successfulPollinatingTicks = 0;
            this.pollinatingTicks = 0;
            this.lastSoundPlayedTick = 0;
            this.pollinating = true;
            this.bee.resetTicksWithoutNectarSinceExitingHive();
        }

        @Override
        public void stop() {
            if (this.hasPollinatedLongEnough()) {
                this.bee.setHasNectar(true);
            }
            this.pollinating = false;
            this.bee.getNavigation().stop();
            this.bee.remainingCooldownBeforeLocatingNewFlower = 200;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (++this.pollinatingTicks > MAX_POLLINATING_TICKS) {
                this.bee.savedFlowerPos = null;
                return;
            }

            Vec3 center = Vec3.atBottomCenterOf(this.bee.savedFlowerPos)
                    .add(0.0, HOVER_HEIGHT_WITHIN_FLOWER, 0.0);

            if (center.distanceTo(this.bee.position()) > 1.0) {
                this.hoverPos = center;
                setWantedPos();
                return;
            }

            if (this.hoverPos == null) this.hoverPos = center;

            boolean arrived = this.bee.position().distanceTo(this.hoverPos) <= ARRIVAL_THRESHOLD;
            boolean move = true;
            if (arrived) {
                // 到达后，小概率随机偏移悬停位置，增加真实感
                if (this.bee.getRandom().nextInt(POSITION_CHANGE_CHANCE) == 0) {
                    this.hoverPos = new Vec3(
                            center.x() + getOffset(), center.y(), center.z() + getOffset());
                    this.bee.getNavigation().stop();
                } else {
                    move = false;
                }
                this.bee.getLookControl().setLookAt(center.x(), center.y(), center.z());
            }

            if (move) setWantedPos();

            this.successfulPollinatingTicks++;
            if (this.bee.getRandom().nextFloat() < 0.05F
                    && this.successfulPollinatingTicks > this.lastSoundPlayedTick + 60) {
                this.lastSoundPlayedTick = this.successfulPollinatingTicks;
                this.bee.playSound(SoundEvents.BEE_POLLINATE, 1.0F, 1.0F);
            }
        }

        boolean isPollinating() {
            return this.pollinating;
        }

        void stopPollinating() {
            this.pollinating = false;
        }

        private boolean hasPollinatedLongEnough() {
            return this.successfulPollinatingTicks > MIN_POLLINATION_TICKS;
        }

        private void setWantedPos() {
            this.bee.getMoveControl().setWantedPosition(
                    this.hoverPos.x(), this.hoverPos.y(), this.hoverPos.z(), SPEED_MODIFIER);
        }

        private float getOffset() {
            return (this.bee.getRandom().nextFloat() * 2.0F - 1.0F) * HOVER_POS_OFFSET;
        }

        private Optional<BlockPos> findNearbyFlower() {
            return findNearestBlock(VALID_POLLINATION_BLOCKS, 5.0);
        }

        /**
         * 螺旋扫描寻找最近的方块。
         * 注意：必须返回 immutable 的 BlockPos，否则下一次扫描会覆盖坐标。
         */
        private Optional<BlockPos> findNearestBlock(Predicate<BlockState> predicate, double distance) {
            BlockPos origin = this.bee.blockPosition();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

            for (int i = 0; i <= distance; i = i > 0 ? -i : 1 - i) {
                for (int j = 0; j < distance; j++) {
                    for (int k = 0; k <= j; k = k > 0 ? -k : 1 - k) {
                        for (int l = k < j && k > -j ? j : 0; l <= j; l = l > 0 ? -l : 1 - l) {
                            pos.setWithOffset(origin, k, i - 1, l);
                            if (origin.closerThan(pos, distance)
                                    && predicate.test(this.bee.level().getBlockState(pos))) {
                                return Optional.of(pos.immutable()); // 🔥 必须 immutable！
                            }
                        }
                    }
                }
            }
            return Optional.empty();
        }
    }
}