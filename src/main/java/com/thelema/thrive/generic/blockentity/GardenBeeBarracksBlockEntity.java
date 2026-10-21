package com.thelema.thrive.generic.blockentity;

import com.thelema.thrive.generic.entity.GardenSoldierBee;
import com.thelema.thrive.register.BlockEntityRegister;
import com.thelema.thrive.register.EntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GardenBeeBarracksBlockEntity extends BlockEntity {

    public static final int MAX_BEES = 6;
    
    /** 存储内部士兵蜂的完整 NBT 数据 */
    public final List<CompoundTag> storedBees = new ArrayList<>();
    
    /** 兵营归属者的 UUID */
    @Nullable public UUID ownerUUID = null;
    
    /** 5 秒冷却（拒绝入住） */
    public int cooldownTicks = 0;
    
    /** 巡逻计时器（5分钟 = 6000 tick） */
    public int patrolTimer = 6000;

    /** 本次破坏是否使用了精准采集。playerWillDestroy 写入，onRemove 读取。 */
    public transient boolean silkHarvest = false;

    public GardenBeeBarracksBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegister.GARDEN_BEE_BARRACKS.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GardenBeeBarracksBlockEntity be) {
        if (level.isClientSide) return;

        if (be.cooldownTicks > 0) be.cooldownTicks--;

        // 1. 被动回血：每 20 tick (1秒)，内部所有受伤的蜜蜂恢复 2 点血
        if (!be.storedBees.isEmpty() && level.getGameTime() % 20 == 0) {
            boolean changed = false;
            for (CompoundTag beeTag : be.storedBees) {
                float health = beeTag.contains("Health") ? beeTag.getFloat("Health") : 20.0F;
                if (health > 0 && health < 20.0F) {
                    beeTag.putFloat("Health", Math.min(20.0F, health + 2.0F));
                    changed = true;
                }
            }
            if (changed) be.setChanged();
        }

        // 2. 常驻巡逻机制：非雨天，每隔 20 tick 检查外界是否有存活的巡逻蜂
        if (!level.isRaining() && be.cooldownTicks <= 0 && !be.storedBees.isEmpty() && level.getGameTime() % 20 == 0) {
            boolean hasPatroller = false;
            // 搜索周围 64 格内是否存在这只兵营的巡逻蜂
            List<GardenSoldierBee> outsideBees = level.getEntitiesOfClass(GardenSoldierBee.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(64.0));

            for (GardenSoldierBee b : outsideBees) {
                if (b.isAlive() && b.isPatroller && b.barracksPos != null && b.barracksPos.equals(pos)) {
                    hasPatroller = true;
                    break;
                }
            }

            // 如果没有巡逻蜂，放出一只并强行指派
            if (!hasPatroller) {
                CompoundTag tag = be.storedBees.remove(0);
                GardenSoldierBee bee = be.spawnBeeFromNBT(level, pos, tag, false);
                if (bee != null) {
                    bee.isPatroller = true;
                    bee.barracksPos = pos;
                }
                level.playSound(null, pos, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    // ==================== 入住与阵营逻辑 ====================

    /** 兵营归属者（或空）是否可以操作。 */
    public boolean canOperate(Player player) {
        return ownerUUID == null || ownerUUID.equals(player.getUUID());
    }

    /**
     * 士兵蜂尝试入住兵营。
     * 返回 true 表示成功入住，实体将被 discard()。
     */
    public boolean tryAcceptBee(GardenSoldierBee bee) {
        if (cooldownTicks > 0 || storedBees.size() >= MAX_BEES) return false;

        UUID beeOwner = bee.getOwnerUUID();

        // 处理阵营与野生吸纳
        if (this.ownerUUID == null) {
            if (beeOwner != null) {
                // 无主兵营被第一只驯服蜜蜂打上印记
                this.ownerUUID = beeOwner;
                assimilateWildBees(beeOwner);
            }
        } else {
            // 已有主兵营，只接受同阵营的蜜蜂
            if (!this.ownerUUID.equals(beeOwner)) return false;
        }

        // 保存实体完整 NBT
        CompoundTag tag = new CompoundTag();
        bee.saveWithoutId(tag);
        storedBees.add(tag);
        
        setChanged();
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    /** 将兵营内部的野生蜂全部归属为指定玩家 */
    private void assimilateWildBees(UUID newOwner) {
        for (CompoundTag tag : storedBees) {
            tag.putUUID("Owner", newOwner);
            tag.putBoolean("Tame", true); // 强制变为驯服状态
        }
    }

    // ==================== 释放与报信逻辑 ====================

    /** 收到通风报信：释放全部，并让所有蜜蜂进入暴怒战斗状态 */
    public void triggerAlarm(Level level, BlockPos pos, @Nullable LivingEntity target) {
        if (storedBees.isEmpty()) return;

        while (!storedBees.isEmpty()) {
            GardenSoldierBee bee = spawnBeeFromNBT(level, pos, storedBees.remove(0), false);
            if (bee != null && target != null && target.isAlive()) {
                bee.setTarget(target);
                // 删掉 bee.setRemainingPersistentAngerTime(400);
            }
        }
        level.playSound(null, pos, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.5F, 0.8F);
        setChanged();
    }

    /** 破坏方块时释放。breaker 非空则激怒（精准采集走不到这里）。 */
    public void releaseAll(Level level, BlockPos pos, @Nullable LivingEntity breaker) {
        if (storedBees.isEmpty()) return;

        while (!storedBees.isEmpty()) {
            GardenSoldierBee bee = spawnBeeFromNBT(level, pos, storedBees.remove(0), true);
            if (bee != null && breaker != null && bee.canAttackType(breaker.getType()) && !bee.isOwnedBy(breaker)) {
                bee.setTarget(breaker);
                // 删掉 bee.setRemainingPersistentAngerTime(400);
            }
        }
        level.playSound(null, pos, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);
        setChanged();
    }

    @Nullable
    private GardenSoldierBee spawnBeeFromNBT(Level level, BlockPos pos, CompoundTag tag, boolean scatter) {
        GardenSoldierBee bee = EntityRegister.GARDEN_SOLDIER_BEE.get().create(level);
        if (bee != null) {
            bee.load(tag); // 读取包含血量在内的所有数据
            double xOffset = scatter ? level.random.nextDouble() * 1.5 - 0.75 : 0.5;
            double zOffset = scatter ? level.random.nextDouble() * 1.5 - 0.75 : 0.5;
            bee.moveTo(pos.getX() + xOffset, pos.getY() + 1.0, pos.getZ() + zOffset, level.random.nextFloat() * 360F, 0F);
            level.addFreshEntity(bee);
        }
        return bee;
    }

    // ==================== 物品 NBT ====================

    /** 精准采集掉落的物品通过这个组件保存归属与内部蜜蜂。 */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (storedBees.isEmpty() && ownerUUID == null) return;
        if (this.level == null) return;

        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, this.level.registryAccess());
        // BLOCK_ENTITY_DATA 的 codec 要求带 id
        tag.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(this.getType()).toString());
        components.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
    }

    // ==================== NBT ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.saveAdditional(tag, regs);
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putInt("Cooldown", cooldownTicks);
        tag.putInt("PatrolTimer", patrolTimer);

        ListTag list = new ListTag();
        for (CompoundTag beeTag : storedBees) {
            list.add(beeTag.copy());
        }
        tag.put("StoredBees", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.loadAdditional(tag, regs);
        if (tag.hasUUID("Owner")) ownerUUID = tag.getUUID("Owner");
        cooldownTicks = tag.getInt("Cooldown");
        patrolTimer = tag.contains("PatrolTimer") ? tag.getInt("PatrolTimer") : 6000;

        storedBees.clear();
        ListTag list = tag.getList("StoredBees", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            storedBees.add(list.getCompound(i));
        }
    }
}