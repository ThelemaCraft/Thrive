package com.thelema.thrive.generic.blockentity;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thelema.thrive.generic.block.GardenBeeNest;
import com.thelema.thrive.generic.blockentity.other.GardenHiveRecipes;
import com.thelema.thrive.generic.entity.GardenBee;
import com.thelema.thrive.register.BlockEntityRegister;
import com.thelema.thrive.register.EntityRegister;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 花园蜂巢方块实体。
 * 核心职责：
 * 1. 管理巢内蜜蜂（添加入巢、倒计时释放、紧急驱逐）。
 * 2. 酿蜜逻辑（花蜜积累、配方匹配、成品产出）。
 * 3. 滞留药水淬毒（从上方容器抽取药水，分发给出巢的蜜蜂）。
 * 4. 数据持久化（蜜蜂数据、酿蜜进度、药水剩余）。
 */
public class GardenBeeNestBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BEES = "bees";
    // 蜜蜂存档时需要清理的无用标签（避免数据臃肿或冲突）
    static final List<String> IGNORED_BEE_TAGS = Arrays.asList(
            "Air", "ArmorDropChances", "ArmorItems", "Brain", "CanPickUpLoot", "DeathTime",
            "FallDistance", "FallFlying", "Fire", "HandDropChances", "HandItems", "HurtByTimestamp",
            "HurtTime", "LeftHanded", "Motion", "NoGravity", "OnGround", "PortalCooldown", "Pos",
            "Rotation", "SleepingX", "SleepingY", "SleepingZ", "CannotEnterHiveTicks",
            "TicksSincePollination", "CropsGrownSincePollination", "hive_pos", "Passengers", "leash", "UUID"
    );
    public static final int MAX_OCCUPANTS = 3;
    public static final int MAX_NECTAR = 5;
    public static final int MAX_HONEY = 5;
    public static final int BREW_TIME = 2400; // 酿造一瓶蜂蜜需要 2 分钟

    private final List<GardenBeeNestBlockEntity.BeeData> stored = Lists.newArrayList();
    @Nullable private BlockPos savedFlowerPos;
    
    /** 花蜜量，达到 MAX_NECTAR 时开始酿造 */
    public int nectarLevel = 0;
    /** 成品蜂蜜数组（存有物品即代表有蜜） */
    private final Item[] honey = new Item[MAX_HONEY];
    /** 酿造进度 */
    private int progress = 0;
    
    /** 滞留药水的效果缓存 */
    @Nullable private PotionContents potion;
    /** 药水剩余使用次数（一瓶可用 9 次） */
    private int potionRemaining = 0;

    public GardenBeeNestBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegister.GARDEN_BEE_NEST.get(), pos, blockState);
    }

    public int getProgress() {
        return this.progress;
    }

    @Override
    public void setChanged() {
        if (this.isFireNearby() && this.level != null) {
            this.emptyAllLivingFromHive(null, this.level.getBlockState(this.getBlockPos()), BeeReleaseStatus.EMERGENCY);
        }
        super.setChanged();
    }

    public boolean isFireNearby() {
        if (this.level == null) return false;
        for (BlockPos blockpos : BlockPos.betweenClosed(this.worldPosition.offset(-1, -1, -1), this.worldPosition.offset(1, 1, 1))) {
            if (this.level.getBlockState(blockpos).getBlock() instanceof FireBlock) return true;
        }
        return false;
    }

    public boolean isEmpty() {
        return this.stored.isEmpty();
    }

    public boolean isFull() {
        return this.stored.size() >= MAX_OCCUPANTS;
    }

    /** 巢内所有蜜蜂都属于该玩家（空巢不算）。 */
    public boolean isOwnedBy(Player player) {
        if (this.stored.isEmpty()) return false;
        for (BeeData data : this.stored) {
            if (!data.isOwnedBy(player)) return false;
        }
        return true;
    }

    /** 巢内是否有该玩家的蜜蜂。 */
    public boolean hasOwnedBee(Player player) {
        for (BeeData data : this.stored) {
            if (data.isOwnedBy(player)) return true;
        }
        return false;
    }

    /** 该玩家在巢内的蜜蜂是否全部处于“自由行动”。 */
    public boolean allOwnedSitting(Player player) {
        boolean found = false;
        for (BeeData data : this.stored) {
            if (!data.isOwnedBy(player)) continue;
            found = true;
            if (!data.isSitting()) return false;
        }
        return found;
    }

    /** 只修改该玩家的蜜蜂的 Sitting 状态，不影响别人的蜂。 */
    public void setOwnedSitting(Player player, boolean sitting) {
        boolean changed = false;
        for (BeeData data : this.stored) {
            if (!data.isOwnedBy(player)) continue;
            data.setSitting(sitting);
            changed = true;
        }
        if (changed) this.setChanged();
    }

    /** 把该玩家在巢内的蜜蜂全部放出来（用于“跟随”模式）。 */
    public int releaseOwnedBees(Player player) {
        if (this.level == null) return 0;

        List<Entity> released = Lists.newArrayList();
        this.stored.removeIf(data -> data.isOwnedBy(player)
                && releaseOccupant(this.level, this.worldPosition, this.getBlockState(),
                        data.toOccupant(), released, BeeReleaseStatus.EMERGENCY, this.savedFlowerPos));

        for (Entity entity : released) {
            if (entity instanceof GardenBee bee) {
                bee.setOrderedToSit(false);
                bee.setStayOutOfHiveCountdown(400);
                bee.setTarget(null);
            }
        }
        if (!released.isEmpty()) {
            this.setChanged();
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
        return released.size();
    }

    /**
     * 释放蜂巢内的所有蜜蜂。
     * @param player 激怒目标（如果非空且没被烟熏，蜜蜂会攻击该玩家）
     */
    public void emptyAllLivingFromHive(@Nullable Player player, BlockState state, GardenBeeNestBlockEntity.BeeReleaseStatus releaseStatus) {
        List<Entity> list = this.releaseAllOccupants(state, releaseStatus);
        if (player != null) {
            for (Entity entity : list) {
                if (entity instanceof GardenBee bee) {
                    if (player.position().distanceToSqr(entity.position()) <= 16.0) {
                        if (!this.isSedated()) {
                            bee.setTarget(player);
                        } else {
                            bee.setStayOutOfHiveCountdown(400);
                        }
                    }
                }
            }
        }
    }

    private List<Entity> releaseAllOccupants(BlockState state, GardenBeeNestBlockEntity.BeeReleaseStatus releaseStatus) {
        List<Entity> list = Lists.newArrayList();
        // 通过迭代器移除符合条件的蜜蜂
        this.stored.removeIf(data -> releaseOccupant(this.level, this.worldPosition, state, data.toOccupant(), list, releaseStatus, this.savedFlowerPos));
        if (!list.isEmpty()) {
            super.setChanged();
        }
        return list;
    }

    @VisibleForDebug
    public int getOccupantCount() {
        return this.stored.size();
    }

    @VisibleForDebug
    public boolean isSedated() {
        return this.level != null && CampfireBlock.isSmokeyPos(this.level, this.getBlockPos());
    }

    /**
     * 添加蜜蜂入巢。
     * 如果蜜蜂带有花蜜，则增加 nectarLevel。
     */
    public void addOccupant(Entity occupant) {
        if (this.stored.size() < MAX_OCCUPANTS) {
            occupant.stopRiding();
            occupant.ejectPassengers();

            if (occupant instanceof GardenBee bee && bee.hasNectar() && this.nectarLevel < MAX_NECTAR) {
                this.nectarLevel++;
                bee.dropOffNectar();
            }

            this.storeBee(GardenBeeNestBlockEntity.Occupant.of(occupant));
            
            if (this.level != null) {
                // 有 50% 概率用蜜蜂记忆里的花位置更新巢的记忆
                if (occupant instanceof GardenBee bee && bee.hasSavedFlowerPos() && (!this.hasSavedFlowerPos() || this.level.random.nextBoolean())) {
                    this.savedFlowerPos = bee.getSavedFlowerPos();
                }
                BlockPos blockpos = this.getBlockPos();
                this.level.playSound(null, blockpos.getX(), blockpos.getY(), blockpos.getZ(), SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 1.0F);
                this.level.gameEvent(GameEvent.BLOCK_CHANGE, blockpos, GameEvent.Context.of(occupant, this.getBlockState()));
            }
            occupant.discard();
            super.setChanged();
        }
    }

    public void storeBee(GardenBeeNestBlockEntity.Occupant occupant) {
        this.stored.add(new GardenBeeNestBlockEntity.BeeData(occupant));
    }

    /**
     * 将蜜蜂实体生成到世界中。
     * @return 如果成功生成返回 true（调用方将从存储列表中移除它）
     */
    private static boolean releaseOccupant(
            Level level, BlockPos pos, BlockState state, GardenBeeNestBlockEntity.Occupant occupant,
            @Nullable List<Entity> storedInHives, GardenBeeNestBlockEntity.BeeReleaseStatus releaseStatus,
            @Nullable BlockPos storedFlowerPos
    ) {
        // 如果是夜晚或下雨，且不是紧急释放（如被破坏），则延迟出巢
        if ((level.isNight() || level.isRaining()) && releaseStatus != GardenBeeNestBlockEntity.BeeReleaseStatus.EMERGENCY) {
            return false;
        }

        Direction direction = state.getValue(BeehiveBlock.FACING);
        BlockPos blockpos = pos.relative(direction);
        boolean isBlocked = !level.getBlockState(blockpos).getCollisionShape(level, blockpos).isEmpty();
        
        // 如果出口被堵住且不是紧急释放，则无法出巢
        if (isBlocked && releaseStatus != GardenBeeNestBlockEntity.BeeReleaseStatus.EMERGENCY) {
            return false;
        }

        Entity entity = occupant.createEntity(level, pos);
        if (entity instanceof GardenBee bee) {
            // 传递花的位置（90% 概率）
            if (storedFlowerPos != null && !bee.hasSavedFlowerPos() && level.random.nextFloat() < 0.9F) {
                bee.setSavedFlowerPos(storedFlowerPos);
            }

            // 如果是采完蜜回来被放出的，清理它的花蜜状态
            if (releaseStatus == GardenBeeNestBlockEntity.BeeReleaseStatus.HONEY_DELIVERED) {
                bee.dropOffNectar();
            }

            if (storedInHives != null) {
                storedInHives.add(bee);
            }

            // 淬毒逻辑：如果蜂巢有药水，就喂给这只刚出巢的蜜蜂
            if (level.getBlockEntity(pos) instanceof GardenBeeNestBlockEntity hive) {
                bee.stingerPotionContents = hive.tryConsumePotionFromContainer();
            }

            // 计算生成坐标（如果是堵住的，就生成在正中心，否则生成在出口前方）
            float f = entity.getBbWidth();
            double d3 = isBlocked ? 0.0 : 0.55 + (double) (f / 2.0F);
            double d0 = (double) pos.getX() + 0.5 + d3 * (double) direction.getStepX();
            double d1 = (double) pos.getY() + 0.5 - (double) (entity.getBbHeight() / 2.0F);
            double d2 = (double) pos.getZ() + 0.5 + d3 * (double) direction.getStepZ();
            entity.moveTo(d0, d1, d2, entity.getYRot(), entity.getXRot());

            level.playSound(null, pos, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(entity, level.getBlockState(pos)));
            return level.addFreshEntity(entity);
        }
        return false;
    }

    private boolean hasSavedFlowerPos() {
        return this.savedFlowerPos != null;
    }

    /**
     * 倒计时处理：如果巢内蜜蜂待够了时间，就将其释放。
     */
    private static void tickOccupants(
            Level level, BlockPos pos, BlockState state, List<GardenBeeNestBlockEntity.BeeData> data, @Nullable BlockPos savedFlowerPos
    ) {
        boolean changed = false;
        Iterator<GardenBeeNestBlockEntity.BeeData> iterator = data.iterator();

        while (iterator.hasNext()) {
            GardenBeeNestBlockEntity.BeeData beeData = iterator.next();
            if (beeData.tick()) {
                GardenBeeNestBlockEntity.BeeReleaseStatus status = beeData.hasNectar()
                        ? GardenBeeNestBlockEntity.BeeReleaseStatus.HONEY_DELIVERED
                        : GardenBeeNestBlockEntity.BeeReleaseStatus.BEE_RELEASED;
                
                if (releaseOccupant(level, pos, state, beeData.toOccupant(), null, status, savedFlowerPos)) {
                    changed = true;
                    iterator.remove();
                }
            }
        }

        if (changed) {
            setChanged(level, pos, state);
        }
    }

    /**
     * 蜂巢的服务端 Tick 逻辑。
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, GardenBeeNestBlockEntity beehive) {
        // 1. 更新蜜蜂倒计时，到时间就放出
        tickOccupants(level, pos, state, beehive.stored, beehive.savedFlowerPos);

        // 2. 酿蜜进度更新
        if (beehive.nectarLevel >= MAX_NECTAR && beehive.getOccupantCount() > 0 && beehive.honeyCount() < MAX_HONEY) {
            beehive.progress++;
            if (beehive.progress >= BREW_TIME) {
                beehive.progress = 0;
                beehive.finishBrew(level, pos, state);
            }
            // 每秒钟同步一次进度（供客户端/Jade显示）
            if (beehive.progress % 20 == 0) {
                level.sendBlockUpdated(pos, state, state, 3);
            }
        } else {
            if (beehive.progress != 0) {
                beehive.progress = 0;
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }

        // 3. 播放环境音效
        if (!beehive.stored.isEmpty() && level.getRandom().nextDouble() < 0.005) {
            level.playSound(null, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /**
     * 酿蜜完成：根据附近的花提取配方产出物品，消耗花蜜，并放出一只蜜蜂。
     */
    private void finishBrew(Level level, BlockPos pos, BlockState state) {
        Item result = GardenHiveRecipes.pickHoney(level, pos);

        // 将产物存入空槽位
        for (int i = 0; i < MAX_HONEY; i++) {
            if (honey[i] == null) {
                honey[i] = result;
                break;
            }
        }

        this.nectarLevel = 0; // 清空花蜜
        releaseOneBee(level, pos); // 消耗并释放一只蜜蜂

        // 如果之前没有蜂蜜，现在产出了，就更新方块视觉状态
        if (honeyCount() > 0 && !state.getValue(GardenBeeNest.HAS_HONEY)) {
            level.setBlock(pos, state.setValue(GardenBeeNest.HAS_HONEY, true), 3);
        }

        setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
    }

    private void releaseOneBee(Level level, BlockPos pos) {
        if (this.stored.isEmpty()) return;
        this.stored.remove(0);
        level.playSound(null, pos, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);

        GardenBee bee = EntityRegister.GARDEN_BEE.get().create(level);
        if (bee != null) {
            bee.setHivePos(pos);
            bee.setStayOutOfHiveCountdown(400); // 出巢后 20 秒内不准进巢
            bee.moveTo(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
            level.addFreshEntity(bee);
        }
        setChanged();
    }

    public int honeyCount() {
        int count = 0;
        for (Item item : honey) if (item != null) count++;
        return count;
    }

    /**
     * 玩家取蜜逻辑。
     * @return 如果成功取走一瓶蜂蜜返回 true。
     */
    public boolean take(Player player, ItemStack held) {
        boolean bottle = held.is(Items.GLASS_BOTTLE);
        boolean shears = held.is(Items.SHEARS);
        if (!bottle && !shears) return false;

        for (int i = 0; i < MAX_HONEY; i++) {
            if (this.honey[i] == null) continue;

            Item produced = this.honey[i];
            this.honey[i] = null;

            if (bottle) {
                if (!player.getAbilities().instabuild) held.shrink(1);
                this.give(player, new ItemStack(produced));
                player.level().playSound(null, this.worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                this.give(player, new ItemStack(Items.HONEYCOMB));
                player.level().playSound(null, this.worldPosition, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (!player.getAbilities().instabuild) {
                    held.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                }
            }

            // 如果取空了，关闭方块的“有蜂蜜”状态
            if (this.honeyCount() == 0) {
                BlockState state = this.getBlockState();
                if (state.hasProperty(GardenBeeNest.HAS_HONEY)) {
                    player.level().setBlock(this.worldPosition, state.setValue(GardenBeeNest.HAS_HONEY, false), 3);
                }
            }

            this.setChanged();
            player.level().sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            return true;
        }
        return false;
    }

    private void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.stored.clear();
        if (tag.contains("bees")) {
            GardenBeeNestBlockEntity.Occupant.LIST_CODEC
                    .parse(NbtOps.INSTANCE, tag.get("bees"))
                    .resultOrPartial(p -> LOGGER.error("Failed to parse bees: '{}'", p))
                    .ifPresent(l -> l.forEach(this::storeBee));
        }
        this.savedFlowerPos = NbtUtils.readBlockPos(tag, "flower_pos").orElse(null);

        this.progress = tag.getInt("Progress");
        Arrays.fill(this.honey, null);
        CompoundTag h = tag.getCompound("Honey");
        for (int i = 0; i < MAX_HONEY; i++) {
            if (h.contains("s" + i)) {
                this.honey[i] = BuiltInRegistries.ITEM.get(ResourceLocation.parse(h.getString("s" + i)));
            }
        }

        if (tag.contains("Potion")) {
            this.potion = PotionContents.CODEC.parse(NbtOps.INSTANCE, tag.get("Potion"))
                    .result().orElse(null);
            this.potionRemaining = tag.getInt("PotionRemaining");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("bees", GardenBeeNestBlockEntity.Occupant.LIST_CODEC.encodeStart(NbtOps.INSTANCE, this.getBees()).getOrThrow());
        if (this.hasSavedFlowerPos()) {
            tag.put("flower_pos", NbtUtils.writeBlockPos(this.savedFlowerPos));
        }

        tag.putInt("Progress", this.progress);
        CompoundTag h = new CompoundTag();
        for (int i = 0; i < MAX_HONEY; i++) {
            if (honey[i] != null) {
                h.putString("s" + i, BuiltInRegistries.ITEM.getKey(honey[i]).toString());
            }
        }
        tag.put("Honey", h);

        if (this.potion != null) {
            PotionContents.CODEC.encodeStart(NbtOps.INSTANCE, this.potion)
                    .result().ifPresent(data -> tag.put("Potion", data));
            tag.putInt("PotionRemaining", this.potionRemaining);
        }
    }

    /**
     * 尝试从上方容器提取滞留药水。如果当前药水还有剩余次数，优先使用。
     */
    @Nullable
    private PotionContents tryConsumePotionFromContainer() {
        if (this.level == null) return null;

        // 1. 如果当前药水还有剩余次数，直接扣除并返回效果
        if (this.potionRemaining > 0 && this.potion != null) {
            this.potionRemaining--;
            return this.potion;
        }

        // 2. 上方没有容器则返回
        BlockPos above = this.getBlockPos().above();
        if (!(this.level.getBlockEntity(above) instanceof Container container)) return null;

        // 3. 遍历容器寻找滞留药水
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.is(Items.LINGERING_POTION)) continue;

            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents == null) continue;

            // 找到药水，消耗物品并重置使用次数
            stack.shrink(1);
            this.potion = contents;
            this.potionRemaining = 9;
            this.setChanged();
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
            return contents;
        }
        return null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        this.stored.clear();
        // 从物品 Component 中读取蜜蜂数据
        List<BeehiveBlockEntity.Occupant> list = componentInput.getOrDefault(DataComponents.BEES, List.of());
        list.forEach(occ -> this.storeBee(new GardenBeeNestBlockEntity.Occupant(
                occ.entityData(), occ.ticksInHive(), occ.minTicksInHive())));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        // 将蜜蜂数据转化为原版的 BeehiveBlockEntity.Occupant 格式存入 Component
        List<BeehiveBlockEntity.Occupant> vanillaList = this.getBees().stream()
                .map(occ -> new BeehiveBlockEntity.Occupant(
                        occ.entityData(), occ.ticksInHive(), occ.minTicksInHive()))
                .toList();
        components.set(DataComponents.BEES, vanillaList);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("bees");
    }

    private List<GardenBeeNestBlockEntity.Occupant> getBees() {
        return this.stored.stream().map(GardenBeeNestBlockEntity.BeeData::toOccupant).toList();
    }

    /**
     * 内部记录蜜蜂时间的包装类。
     */
    static class BeeData {
        private GardenBeeNestBlockEntity.Occupant occupant;
        private int ticksInHive;

        BeeData(GardenBeeNestBlockEntity.Occupant occupant) {
            this.occupant = occupant;
            this.ticksInHive = occupant.ticksInHive();
        }

        /** @return 如果蜜蜂已经待够了时间，返回 true */
        public boolean tick() {
            return this.ticksInHive++ > this.occupant.minTicksInHive;
        }

        public GardenBeeNestBlockEntity.Occupant toOccupant() {
            return new GardenBeeNestBlockEntity.Occupant(this.occupant.entityData, this.ticksInHive, this.occupant.minTicksInHive);
        }

        public boolean hasNectar() {
            return this.occupant.entityData.getUnsafe().getBoolean("HasNectar");
        }

        public boolean isOwnedBy(Player player) {
            CompoundTag tag = this.occupant.entityData().copyTag();
            return tag.hasUUID("Owner") && tag.getUUID("Owner").equals(player.getUUID());
        }

        public boolean isSitting() {
            return this.occupant.entityData().copyTag().getBoolean("Sitting");
        }

        public void setSitting(boolean sitting) {
            CompoundTag tag = this.occupant.entityData().copyTag();
            if (sitting) tag.putBoolean("Sitting", true);
            else tag.remove("Sitting");
            this.occupant = new GardenBeeNestBlockEntity.Occupant(
                    CustomData.of(tag), this.occupant.ticksInHive(), this.occupant.minTicksInHive());
        }
    }

    public static enum BeeReleaseStatus {
        HONEY_DELIVERED, // 带回花蜜后出巢
        BEE_RELEASED,    // 待够时间自然出巢
        EMERGENCY;       // 被破坏或起火紧急放出
    }

    /**
     * 蜜蜂数据的不可变记录（Record）。
     * 包含实体 NBT、在巢内时间、最短停留时间。
     */
    public static record Occupant(CustomData entityData, int ticksInHive, int minTicksInHive) {
        public static final Codec<GardenBeeNestBlockEntity.Occupant> CODEC = RecordCodecBuilder.create(
                p_337984_ -> p_337984_.group(
                                CustomData.CODEC.optionalFieldOf("entity_data", CustomData.EMPTY).forGetter(GardenBeeNestBlockEntity.Occupant::entityData),
                                Codec.INT.fieldOf("ticks_in_hive").forGetter(GardenBeeNestBlockEntity.Occupant::ticksInHive),
                                Codec.INT.fieldOf("min_ticks_in_hive").forGetter(GardenBeeNestBlockEntity.Occupant::minTicksInHive)
                        )
                        .apply(p_337984_, GardenBeeNestBlockEntity.Occupant::new)
        );
        public static final Codec<List<GardenBeeNestBlockEntity.Occupant>> LIST_CODEC = CODEC.listOf();
        public static final StreamCodec<ByteBuf, GardenBeeNestBlockEntity.Occupant> STREAM_CODEC = StreamCodec.composite(
                CustomData.STREAM_CODEC,
                GardenBeeNestBlockEntity.Occupant::entityData,
                ByteBufCodecs.VAR_INT,
                GardenBeeNestBlockEntity.Occupant::ticksInHive,
                ByteBufCodecs.VAR_INT,
                GardenBeeNestBlockEntity.Occupant::minTicksInHive,
                GardenBeeNestBlockEntity.Occupant::new
        );

        public static GardenBeeNestBlockEntity.Occupant of(Entity entity) {
            CompoundTag compoundtag = new CompoundTag();
            entity.save(compoundtag);
            GardenBeeNestBlockEntity.IGNORED_BEE_TAGS.forEach(compoundtag::remove);
            boolean flag = compoundtag.getBoolean("HasNectar");
            return new GardenBeeNestBlockEntity.Occupant(CustomData.of(compoundtag), 0, flag ? 2400 : 600);
        }

        public static GardenBeeNestBlockEntity.Occupant create(int ticksInHive) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityRegister.GARDEN_BEE.get()).toString());
            return new GardenBeeNestBlockEntity.Occupant(CustomData.of(tag), ticksInHive, 600);
        }

        @Nullable
        public Entity createEntity(Level level, BlockPos pos) {
            CompoundTag compoundtag = this.entityData.copyTag();
            GardenBeeNestBlockEntity.IGNORED_BEE_TAGS.forEach(compoundtag::remove);
            Entity entity = EntityType.loadEntityRecursive(compoundtag, level, p_331097_ -> p_331097_);
            if (entity instanceof GardenBee bee) {
                entity.setNoGravity(true);
                bee.setHivePos(pos);
                setBeeReleaseData(this.ticksInHive, bee);
                return entity;
            } else {
                return null;
            }
        }

        /** 根据在巢内的时间来增加年龄/减少求爱时间 */
        private static void setBeeReleaseData(int ticksInHive, GardenBee bee) {
            int i = bee.getAge();
            if (i < 0) {
                bee.setAge(Math.min(0, i + ticksInHive));
            } else if (i > 0) {
                bee.setAge(Math.max(0, i - ticksInHive));
            }
            bee.setInLoveTime(Math.max(0, bee.getInLoveTime() - ticksInHive));
        }
    }
}