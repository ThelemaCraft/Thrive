package com.thelema.thrive.generic.entity;

import com.thelema.thrive.register.EntityRegister;
import com.thelema.thrive.register.SoundRegister;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class PrimedSunBerry extends Entity implements TraceableEntity {

    private static final EntityDataAccessor<Integer> DATA_FUSE_ID =
            SynchedEntityData.defineId(PrimedSunBerry.class, EntityDataSerializers.INT);
    private static final int DEFAULT_FUSE_TIME = 80;
    public static final String TAG_FUSE = "fuse";

    @Nullable
    private LivingEntity owner;

    public PrimedSunBerry(EntityType<? extends PrimedSunBerry> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public PrimedSunBerry(Level level, double x, double y, double z, @Nullable LivingEntity owner) {
        this(EntityRegister.PRIMED_SUN_BERRY.get(), level);
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.setFuse(DEFAULT_FUSE_TIME);
        this.owner = owner;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FUSE_ID, DEFAULT_FUSE_TIME);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public void tick() {
        int fuse = this.getFuse() - 1;
        this.setFuse(fuse);

        if (!this.level().isClientSide) {
            this.setRemainingFireTicks(20);
        }

        if (fuse <= 0) {
            if (!this.level().isClientSide) {
                Sun sun = new Sun(EntityRegister.SUN.get(), this.level());
                sun.setPos(this.getX(), this.getY(), this.getZ());
                this.level().addFreshEntity(sun);

                this.level().playSound(null, this.blockPosition(),
                        SoundRegister.SUN_BERRY_DEFLAGRATE.get(),
                        SoundSource.BLOCKS, 64.0F, 1.0F);
            }
            this.discard();
            return;
        }

        if (this.level().isClientSide) {
            for (int i = 0; i < 6; i++) {
                double theta = this.random.nextDouble() * Math.PI * 2;
                double phi = Math.acos(2 * this.random.nextDouble() - 1);
                double r = Math.cbrt(this.random.nextDouble()) * 5.0;

                double dx = Math.sin(phi) * Math.cos(theta) * r;
                double dy = Math.cos(phi) * r;
                double dz = Math.sin(phi) * Math.sin(theta) * r;

                this.level().addParticle(
                        ParticleTypes.FLAME,
                        this.getX() + dx,
                        this.getY() + 1.5 + dy,
                        this.getZ() + dz,
                        0, 0.02, 0);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putShort(TAG_FUSE, (short) this.getFuse());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.setFuse(compound.getShort(TAG_FUSE));
    }

    @Nullable
    public LivingEntity getOwner() {
        return this.owner;
    }

    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof PrimedSunBerry berry) {
            this.owner = berry.owner;
        }
    }

    public void setFuse(int fuse) {
        this.entityData.set(DATA_FUSE_ID, fuse);
    }

    public int getFuse() {
        return this.entityData.get(DATA_FUSE_ID);
    }
}