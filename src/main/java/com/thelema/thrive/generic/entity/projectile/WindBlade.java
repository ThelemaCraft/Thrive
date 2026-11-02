package com.thelema.thrive.generic.entity.projectile;

import com.thelema.thrive.register.EntityRegister;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class WindBlade extends Projectile {

    private static final float DAMAGE   = 4.0F;
    private static final int   MAX_LIFE = 60;

    public WindBlade(EntityType<? extends WindBlade> type, Level level) {
        super(type, level);
    }

    public WindBlade(Level level, LivingEntity shooter) {
        super(EntityRegister.WIND_BLADE.get(), level);
        this.setOwner(shooter);
    }

    /** 从右前方 1.5 格处召唤，并赋予初速度 */
    public static WindBlade shoot(Level level, LivingEntity shooter, float velocity) {
        WindBlade blade = new WindBlade(level, shooter);

        Vec3 look = shooter.getLookAngle();
        // 计算水平面的右向量（Y=0，避免俯仰视角导致侧偏上/下）
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();

        // 视线前方 1.5 格，右侧 0.5 格，高度微微下移 0.1 格
        Vec3 origin = shooter.getEyePosition()
                .add(look.scale(1.5))
                .add(right.scale(0.5))
                .subtract(0, 0.1, 0);

        blade.setPos(origin.x, origin.y, origin.z);
        blade.shoot(look.x, look.y, look.z, velocity, 0.0F);

        level.addFreshEntity(blade);
        return blade;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) { }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) { }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) { }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > MAX_LIFE) { discard(); return; }

        Vec3 motion = getDeltaMovement();
        if (motion.lengthSqr() > 1e-6) {
            setYRot((float)(Mth.atan2(motion.z, motion.x) * 180.0 / Math.PI) - 90.0F);
            setXRot(0.0F);
        }

        Vec3 start = position();
        Vec3 end = start.add(motion);

        // 实体命中优先（与原版箭逻辑一致）
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                this, start, end,
                getBoundingBox().expandTowards(motion).inflate(1.0D),
                this::canHitEntity, 0.3D); // 0.3 是容差，参考原版
        if (entityHit != null) {
            onHitEntity(entityHit);
            return;
        }

        // 方块命中
        BlockHitResult blockHit = level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            onHitBlock(blockHit);
            return;
        }

        setPos(end.x, end.y, end.z);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level().isClientSide) return;

        LivingEntity owner = getOwner() instanceof LivingEntity le ? le : null;
        DamageSource ds = damageSources().mobProjectile(this, owner);

        if (hit.getEntity() instanceof LivingEntity target) {
            target.hurt(ds, DAMAGE);
            Vec3 dir = getDeltaMovement().normalize();
            target.push(dir.x * 0.6, 0.3, dir.z * 0.6);
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!level().isClientSide) discard();
    }
}