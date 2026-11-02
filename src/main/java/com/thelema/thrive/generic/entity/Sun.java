package com.thelema.thrive.generic.entity;

import com.thelema.thrive.generic.tool.Damage;
import com.thelema.thrive.register.EffectRegister;
import com.thelema.thrive.register.constant.DamageTypeConstant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.List;

@EventBusSubscriber
public class Sun extends Entity {

    /** 总生命周期：20 秒 */
    private static final int LIFETIME = 400;
    /** 伤害间隔：每 4 tick 一次 */
    private static final int DAMAGE_INTERVAL = 4;
    /** 每次伤害 2 点 */
    private static final float DAMAGE_AMOUNT = 2.0f;
    /** 每次附加燃烧：100 tick = 5 秒 */
    private static final int FIRE_TICKS_PER_HIT = 100;
    /** 燃烧上限：400 tick = 20 秒 */
    private static final int FIRE_TICKS_MAX = 400;
    /** 伤害与粒子扩散半径 */
    private static final double RADIUS = 32.0;
    /** 每 20 tick 尝试生成火焰方块，每次尝试 32 个采样点 */
    private static final int FIRE_SPAWN_ATTEMPTS = 32;

    private boolean positioned = false;
    private double targetY;

    public Sun(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) return;

        int age = this.tickCount;

        // 首次 tick 计算目标高度，此后平滑逼近
        if (!positioned) {
            targetY = computeTargetY();
            positioned = true;
        }
        double dy = targetY - this.getY();
        if (Math.abs(dy) > 0.05) {
            this.setPos(this.getX(), this.getY() + dy / 8.0, this.getZ());
        } else if (dy != 0) {
            this.setPos(this.getX(), targetY, this.getZ());
        }

        if (age > 0 && age % DAMAGE_INTERVAL == 0) {
            applyDamage();
        }

        if (age > 0 && age % 20 == 0) {
            spawnFire();
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.FIRE_AMBIENT, SoundSource.HOSTILE, 4.0F, 0.8F);
        }

        if (age >= LIFETIME) {
            this.discard();
        }
    }

    /**
     * 地板到天花板距离 ≤ 30：升到中点（gap/2）。
     * > 30：升到地板上方 15。
     */
    private double computeTargetY() {
        Level level = this.level();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int startY = (int) Math.floor(this.getY());

        int groundY = minY;
        for (int y = startY; y >= minY; y--) {
            BlockPos p = BlockPos.containing(this.getX(), y, this.getZ());
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.isSolidRender(level, p)) {
                groundY = y;
                break;
            }
        }

        int ceilingY = maxY;
        for (int y = startY + 1; y < maxY; y++) {
            BlockPos p = BlockPos.containing(this.getX(), y, this.getZ());
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.isSolidRender(level, p)) {
                ceilingY = y;
                break;
            }
        }

        int gap = ceilingY - groundY;
        int rise = gap <= 30 ? gap / 2 : 15;
        return groundY + rise;
    }

    private void applyDamage() {
        if (!(this.level() instanceof ServerLevel sl)) return;

        Vec3 center = this.position();
        AABB area = new AABB(
                center.x - RADIUS, center.y - RADIUS, center.z - RADIUS,
                center.x + RADIUS, center.y + RADIUS, center.z + RADIUS);

        List<LivingEntity> targets = sl.getEntitiesOfClass(LivingEntity.class, area);
        for (LivingEntity target : targets) {
            if (target.is(this)) continue;
            if (target.position().distanceTo(center) > RADIUS) continue;

            Damage.hurt(target, DamageTypeConstant.SUN, DAMAGE_AMOUNT);

            int newFire = Math.min(target.getRemainingFireTicks() + FIRE_TICKS_PER_HIT, FIRE_TICKS_MAX);
            target.setRemainingFireTicks(newFire);

            target.addEffect(new MobEffectInstance(
                    EffectRegister.BLINDING, 60, 2, false, false, true));
        }
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath("thrive", "sun")))) {
            return;
        }

        LivingEntity entity = event.getEntity();
        float newDamage = event.getAmount();

        if (entity.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            newDamage *= 0.1F;
        }

        var registry = entity.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var fireProtHolder = registry.getOrThrow(Enchantments.FIRE_PROTECTION);
        int fireProtLevel = EnchantmentHelper.getEnchantmentLevel(fireProtHolder, entity);

        if (fireProtLevel > 0) {
            float reduction = Math.min(0.9F, fireProtLevel * 0.08F);
            newDamage *= (1.0F - reduction);
        }

        event.setAmount(newDamage);
    }

    private void spawnFire() {
        if (!(this.level() instanceof ServerLevel sl)) return;

        Vec3 center = this.position();
        int minY = sl.getMinBuildHeight();
        int maxY = sl.getMaxBuildHeight();

        for (int i = 0; i < FIRE_SPAWN_ATTEMPTS; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2;
            double dist = this.random.nextDouble() * RADIUS;
            int x = (int) Math.floor(center.x + Math.cos(angle) * dist);
            int z = (int) Math.floor(center.z + Math.sin(angle) * dist);

            int startY = (int) Math.floor(center.y + RADIUS);
            BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos(x, startY, z);

            for (int dy = 0; dy < (int) (RADIUS * 2); dy++) {
                BlockPos p = mut.below(dy);
                if (p.getY() < minY || p.getY() >= maxY) break;

                BlockState at = sl.getBlockState(p);
                // isAir 已排除水/岩浆等非空气方块
                if (!at.isAir()) continue;

                BlockPos below = p.below();
                BlockState belowState = sl.getBlockState(below);
                if (!belowState.isSolidRender(sl, below)) continue;

                sl.setBlockAndUpdate(p, Blocks.FIRE.defaultBlockState());
                break;
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.tickCount = tag.getInt("Age");
        this.positioned = tag.getBoolean("Positioned");
        this.targetY = tag.getDouble("TargetY");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.tickCount);
        tag.putBoolean("Positioned", this.positioned);
        tag.putDouble("TargetY", this.targetY);
    }
}