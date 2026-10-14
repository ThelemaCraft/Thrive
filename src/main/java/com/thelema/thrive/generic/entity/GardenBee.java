package com.thelema.thrive.generic.entity;

import com.thelema.thrive.generic.blockentity.GardenHiveBlockEntity;
import com.thelema.thrive.generic.entity.goal.PollinateGoal;
import com.thelema.thrive.generic.entity.goal.ReturnHomeGoal;
import com.thelema.thrive.register.BlockRegister;
import com.thelema.thrive.register.EntityRegister;
import com.thelema.thrive.register.PoiRegister;
import com.thelema.thrive.register.TagRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
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
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.HoverRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GardenBee extends TamableAnimal implements NeutralMob, FlyingAnimal {

    public static final int ATTACK_INTERVAL = 40;
    public static final int LUNGE_TICKS = 10;
    private static final int IMPACT_TICK = 5;

    private static final EntityDataAccessor<Integer> DATA_REMAINING_ANGER_TIME =
            SynchedEntityData.defineId(GardenBee.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ROLLING =
            SynchedEntityData.defineId(GardenBee.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HAS_POLLEN =
            SynchedEntityData.defineId(GardenBee.class, EntityDataSerializers.BOOLEAN);

    /** 自然生成时随机选择的常见花。 */
    static final List<Item> COMMON_FLOWERS = List.of(
            Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID, Items.ALLIUM,
            Items.AZURE_BLUET, Items.RED_TULIP, Items.ORANGE_TULIP, Items.WHITE_TULIP,
            Items.PINK_TULIP, Items.OXEYE_DAISY, Items.CORNFLOWER, Items.LILY_OF_THE_VALLEY
    );

    /** 喜好花。生成/繁殖时定死，不再改变。 */
    public Item favoriteFlower;

    /** 携带的花粉。采蜜完成后写入。null = 无。 */
    @Nullable public Item carriedPollen;

    /** 认领的蜂巢。归巢目标。null = 无家。 */
    @Nullable public BlockPos home;

    /** 攻击冷却（tick）。> 0 时 doHurtTarget 直接返回。 */
    private int attackCooldown;

    /** 俯冲动画剩余 tick。> 0 时处于撅屁股状态。 */
    private int lungeTicks;

    /** 本次俯冲的待结算目标。动画峰值时结算，之后清空。 */
    @Nullable private Entity pendingTarget;

    /** 中立生物记仇目标 UUID。NeutralMob 需要。 */
    @Nullable private UUID persistentAngerTarget;

    /** home 扫描计时器。归零时扫一次，然后重置为 200。 */
    private int homeSearchTimer = 20 + this.random.nextInt(180);

    /** 当前俯冲插值量 0~1。 */
    public float rollAmount;

    /** 上一 tick 的 rollAmount，用于 getRollAmount 插值。 */
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
        this.goalSelector.addGoal(0, new MeleeAttackGoal(this, 1.4, true));
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.25,
                stack -> stack.getItem() == favoriteFlower, false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.25));
        this.goalSelector.addGoal(5, new PollinateGoal(this));
        this.goalSelector.addGoal(6, new ReturnHomeGoal(this));
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
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 96.0);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    // ==================== 生成 ====================

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        if (this.favoriteFlower == null) {
            this.favoriteFlower = COMMON_FLOWERS.get(this.random.nextInt(COMMON_FLOWERS.size()));
        }
        return result;
    }

    // ==================== 繁殖 ====================

    @Override
    public boolean isFood(ItemStack stack) {
        return isTame() && stack.is(ItemTags.FLOWERS);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        GardenBee child = EntityRegister.GARDEN_BEE.get().create(level);
        if (child == null) return null;

        UUID owner = this.getOwnerUUID();
        if (owner == null && otherParent instanceof TamableAnimal t) owner = t.getOwnerUUID();
        if (owner != null) {
            child.setOwnerUUID(owner);
            child.setTame(true, false);
        }

        // 偏好：附近花 > 父母各 50% > 蒲公英兜底
        Item pref = pickNearbyFlower(level, this.blockPosition());
        if (pref == null) {
            pref = this.random.nextBoolean()
                    ? this.favoriteFlower
                    : (otherParent instanceof GardenBee other ? other.favoriteFlower : null);
        }
        child.favoriteFlower = pref != null ? pref : Items.DANDELION;

        return child;
    }

    /** 扫描附近的花，随机挑一种。 */
    @Nullable
    private static Item pickNearbyFlower(Level level, BlockPos center) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        List<Item> found = new ArrayList<>();
        int r = 8;
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.isLoaded(p)) continue;
                    BlockState s = level.getBlockState(p);
                    if (!s.is(BlockTags.FLOWERS)) continue;
                    Item i = s.getBlock().asItem();
                    if (i != Items.AIR) found.add(i);
                }
        if (found.isEmpty()) return null;
        return found.get(level.random.nextInt(found.size()));
    }

    @Override
    public boolean isFlying() {
        return !this.onGround();
    }

    // ==================== 驯服 ====================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 未驯服 + 手持偏好花 → 驯服
        if (!this.isTame() && stack.getItem() == favoriteFlower) {
            if (!this.level().isClientSide) {
                this.tame(player);
                this.setTarget(null);
                this.setRemainingPersistentAngerTime(0);
                this.level().broadcastEntityEvent(this, (byte) 7);
                if (!player.isCreative()) stack.shrink(1);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // 已驯服 + 手持任意蜂蜜 → 清除 home
        UUID o = getOwnerUUID();
        if (isTame() && o != null && o.equals(player.getUUID()) && stack.is(TagRegister.HONEY)) {
            if (!this.level().isClientSide) {
                this.home = null;
                this.homeSearchTimer = 200;
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
                    if (super.doHurtTarget(this.pendingTarget)) {
                        this.playSound(SoundEvents.BEE_STING, 1.0F, 1.0F);
                    }
                }
                this.pendingTarget = null;
            }

            this.entityData.set(DATA_ROLLING, this.lungeTicks > 0);

            // 找家
            if (this.home == null && --this.homeSearchTimer <= 0) {
                this.homeSearchTimer = 200;
                BlockPos found = this.findHome();
                if (found != null) this.home = found;
            }
            this.entityData.set(DATA_HAS_POLLEN, this.carriedPollen != null);
        }

        this.updateRollAmount();
    }

    @Nullable
    private BlockPos findHome() {
        if (!(this.level() instanceof ServerLevel server)) return null;
        BlockPos origin = this.blockPosition();

        return server.getPoiManager()
                .getInRange(
                        poi -> poi.is(PoiRegister.GARDEN_HIVE),
                        origin, 20, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos)
                .filter(p -> this.level().getBlockEntity(p) instanceof GardenHiveBlockEntity hive
                        && hive.occupants.size() < GardenHiveBlockEntity.MAX_BEES)
                .min(Comparator.comparingDouble(p -> p.distSqr(origin)))
                .orElse(null);
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

    /** 客户端可见的"是否携带花粉"。 */
    public boolean hasPollen() {
        return this.entityData.get(DATA_HAS_POLLEN);
    }

    // ==================== 音效 ====================

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

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
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

        // 兜底：没有偏好就给一个
        if (favoriteFlower == null) {
            favoriteFlower = COMMON_FLOWERS.get(this.random.nextInt(COMMON_FLOWERS.size()));
        }
    }

    // ==================== 飞行游荡 ====================

    class WanderGoal extends Goal {
        private static final int WANDER_THRESHOLD = 22;

        WanderGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return GardenBee.this.getNavigation().isDone()
                    && GardenBee.this.random.nextInt(10) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return GardenBee.this.getNavigation().isInProgress();
        }

        @Override
        public void start() {
            Vec3 vec3 = this.findPos();
            if (vec3 != null) {
                GardenBee.this.getNavigation().moveTo(
                        GardenBee.this.getNavigation().createPath(BlockPos.containing(vec3), 1), 1.0);
            }
        }

        @Nullable
        private Vec3 findPos() {
            Vec3 dir;
            BlockPos home = GardenBee.this.home;
            if (home != null
                    && !home.closerThan(GardenBee.this.blockPosition(), WANDER_THRESHOLD)) {
                dir = Vec3.atCenterOf(home).subtract(GardenBee.this.position()).normalize();
            } else {
                dir = GardenBee.this.getViewVector(0.0F);
            }

            Vec3 pos = HoverRandomPos.getPos(GardenBee.this, 8, 7,
                    dir.x, dir.z, (float) (Math.PI / 2), 3, 1);
            return pos != null ? pos : AirAndWaterRandomPos.getPos(
                    GardenBee.this, 8, 4, -2, dir.x, dir.z, (float) (Math.PI / 2));
        }
    }
}