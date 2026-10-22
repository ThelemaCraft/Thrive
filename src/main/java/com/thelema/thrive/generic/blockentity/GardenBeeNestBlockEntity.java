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
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;

public class GardenBeeNestBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BEES = "bees";
    static final List<String> IGNORED_BEE_TAGS = Arrays.asList(
            "Air", "ArmorDropChances", "ArmorItems", "Brain", "CanPickUpLoot", "DeathTime",
            "FallDistance", "FallFlying", "Fire", "HandDropChances", "HandItems", "HurtByTimestamp",
            "HurtTime", "LeftHanded", "Motion", "NoGravity", "OnGround", "PortalCooldown", "Pos",
            "Rotation", "SleepingX", "SleepingY", "SleepingZ", "CannotEnterHiveTicks",
            "TicksSincePollination", "CropsGrownSincePollination", "hive_pos", "Passengers", "leash", "UUID"
    );
    public static final int MAX_OCCUPANTS = 3;
    public static final int MAX_HONEY = 5;
    public static final int BREW_TIME = 2400; // 2分钟
    public static final int FLOWER_SCAN_RADIUS = 3;

    private final List<GardenBeeNestBlockEntity.BeeData> stored = Lists.newArrayList();
    @Nullable
    private BlockPos savedFlowerPos;
    public int nectarLevel = 0;
    private final Item[] honey = new Item[5];
    private int progress = 0;
    public ItemStack potion = ItemStack.EMPTY;
    public int potionRemaining = 0;

    public GardenBeeNestBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegister.GARDEN_BEE_NEST.get(), pos, blockState);
    }

    public int getHoneyCount() {
        int count = 0;
        for (Item item : this.honey) {
            if (item != null) {
                count++;
            }
        }
        return count;
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
        this.stored
                .removeIf(p_330138_ -> releaseOccupant(this.level, this.worldPosition, state, p_330138_.toOccupant(), list, releaseStatus, this.savedFlowerPos));
        if (!list.isEmpty()) {
            super.setChanged();
        }

        return list;
    }

    @VisibleForDebug
    public int getOccupantCount() {
        return this.stored.size();
    }

    public static int getHoneyLevel(BlockState state) {
        return state.getValue(BeehiveBlock.HONEY_LEVEL);
    }

    @VisibleForDebug
    public boolean isSedated() {
        return this.level != null && CampfireBlock.isSmokeyPos(this.level, this.getBlockPos());
    }

    public void addOccupant(Entity occupant) {
        if (this.stored.size() < MAX_OCCUPANTS) {
            occupant.stopRiding();
            occupant.ejectPassengers();

            // 蜜蜂带回了花蜜，增加普通字段
            if (occupant instanceof GardenBee bee && bee.hasNectar() && this.nectarLevel < 5) {
                this.nectarLevel++;
                bee.dropOffNectar();
            }

            this.storeBee(GardenBeeNestBlockEntity.Occupant.of(occupant));
            if (this.level != null) {
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

    private static boolean releaseOccupant(
            Level level,
            BlockPos pos,
            BlockState state,
            GardenBeeNestBlockEntity.Occupant occupant,
            @Nullable List<Entity> storedInHives,
            GardenBeeNestBlockEntity.BeeReleaseStatus releaseStatus,
            @Nullable BlockPos storedFlowerPos
    ) {
        if ((level.isNight() || level.isRaining()) && releaseStatus != GardenBeeNestBlockEntity.BeeReleaseStatus.EMERGENCY) {
            return false;
        } else {
            Direction direction = state.getValue(BeehiveBlock.FACING);
            BlockPos blockpos = pos.relative(direction);
            boolean flag = !level.getBlockState(blockpos).getCollisionShape(level, blockpos).isEmpty();
            if (flag && releaseStatus != GardenBeeNestBlockEntity.BeeReleaseStatus.EMERGENCY) {
                return false;
            } else {
                Entity entity = occupant.createEntity(level, pos);
                if (entity != null) {
                    if (entity instanceof GardenBee bee) {
                        if (storedFlowerPos != null && !bee.hasSavedFlowerPos() && level.random.nextFloat() < 0.9F) {
                            bee.setSavedFlowerPos(storedFlowerPos);
                        }

                        if (releaseStatus == GardenBeeNestBlockEntity.BeeReleaseStatus.HONEY_DELIVERED) {
                            bee.dropOffNectar();
                            if (state.is(BlockTags.BEEHIVES, p_202037_ -> p_202037_.hasProperty(BeehiveBlock.HONEY_LEVEL))) {
                                int i = getHoneyLevel(state);
                                if (i < 5) {
                                    int j = level.random.nextInt(100) == 0 ? 2 : 1;
                                    if (i + j > 5) {
                                        j--;
                                    }

                                    level.setBlockAndUpdate(pos, state.setValue(BeehiveBlock.HONEY_LEVEL, Integer.valueOf(i + j)));
                                }
                            }
                        }

                        if (storedInHives != null) {
                            storedInHives.add(bee);
                        }

                        // 【新增】淬毒逻辑：向蜂巢请求药水
                        if (level.getBlockEntity(pos) instanceof GardenBeeNestBlockEntity hive) {
                            bee.stingerPotionContents = hive.tryConsumePotionFromContainer();
                        }

                        float f = entity.getBbWidth();
                        double d3 = flag ? 0.0 : 0.55 + (double) (f / 2.0F);
                        double d0 = (double) pos.getX() + 0.5 + d3 * (double) direction.getStepX();
                        double d1 = (double) pos.getY() + 0.5 - (double) (entity.getBbHeight() / 2.0F);
                        double d2 = (double) pos.getZ() + 0.5 + d3 * (double) direction.getStepZ();
                        entity.moveTo(d0, d1, d2, entity.getYRot(), entity.getXRot());
                    }

                    level.playSound(null, pos, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(entity, level.getBlockState(pos)));
                    return level.addFreshEntity(entity);
                } else {
                    return false;
                }
            }
        }
    }

    private boolean hasSavedFlowerPos() {
        return this.savedFlowerPos != null;
    }

    private static void tickOccupants(
            Level level, BlockPos pos, BlockState state, List<GardenBeeNestBlockEntity.BeeData> data, @Nullable BlockPos savedFlowerPos
    ) {
        boolean flag = false;
        Iterator<GardenBeeNestBlockEntity.BeeData> iterator = data.iterator();

        while (iterator.hasNext()) {
            GardenBeeNestBlockEntity.BeeData beehiveblockentity$beedata = iterator.next();
            if (beehiveblockentity$beedata.tick()) {
                GardenBeeNestBlockEntity.BeeReleaseStatus beehiveblockentity$beereleasestatus = beehiveblockentity$beedata.hasNectar()
                        ? GardenBeeNestBlockEntity.BeeReleaseStatus.HONEY_DELIVERED
                        : GardenBeeNestBlockEntity.BeeReleaseStatus.BEE_RELEASED;
                if (releaseOccupant(
                        level, pos, state, beehiveblockentity$beedata.toOccupant(), null, beehiveblockentity$beereleasestatus, savedFlowerPos
                )) {
                    flag = true;
                    iterator.remove();
                }
            }
        }

        if (flag) {
            setChanged(level, pos, state);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GardenBeeNestBlockEntity beehive) {
        // 1. 保持原版的倒计时释放逻辑
        tickOccupants(level, pos, state, beehive.stored, beehive.savedFlowerPos);

        // 2. 酿蜜逻辑

        if (beehive.nectarLevel >= 5 && beehive.getOccupantCount() > 0 && beehive.honeyCount() < MAX_HONEY) {
            beehive.progress++;
            if (beehive.progress >= BREW_TIME) {
                beehive.progress = 0;
                beehive.finishBrew(level, pos, state);
            }
            // 每 20 tick 同步一次进度条（如果客户端需要渲染进度的话）
            if (beehive.progress % 20 == 0) {
                level.sendBlockUpdated(pos, state, state, 3);
            }
        } else {
            if (beehive.progress != 0) {
                beehive.progress = 0;
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }

        // 3. 环境音
        if (!beehive.stored.isEmpty() && level.getRandom().nextDouble() < 0.005) {
            level.playSound(null, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    // 酿蜜完成
    private void finishBrew(Level level, BlockPos pos, BlockState state) {
        List<Flower> flowers = collectFlowers(level, pos);
        Item result = pickHoney(flowers);

        for (int i = 0; i < MAX_HONEY; i++) {
            if (honey[i] == null) {
                honey[i] = result;
                break;
            }
        }

        this.nectarLevel = 0; // 清空花蜜
        releaseOneBee(level, pos);

        // 【关键】如果有第一瓶蜂蜜产出，改变方块外观
        if (honeyCount() > 0 && !state.getValue(GardenBeeNest.HAS_HONEY)) {
            level.setBlock(pos, state.setValue(GardenBeeNest.HAS_HONEY, true), 3);
        }

        setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
    }

    private void releaseOneBee(Level level, BlockPos pos) {
        if (this.stored.isEmpty()) return;
        this.stored.remove(0); // 消耗一只
        level.playSound(null, pos, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);

        GardenBee bee = EntityRegister.GARDEN_BEE.get().create(level);
        if (bee != null) {
            bee.setHivePos(pos);
            bee.setStayOutOfHiveCountdown(400);
            bee.moveTo(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
            level.addFreshEntity(bee);
        }
        setChanged();
    }

    private Item pickHoney(List<Flower> flowers) {
        Item best = null;
        double bestWorstDist = Double.MAX_VALUE;

        for (GardenHiveRecipes.Recipe recipe : GardenHiveRecipes.RECIPE) {
            Map<Item, Integer> need = new HashMap<>();
            need.merge(recipe.a(), 1, Integer::sum);
            need.merge(recipe.b(), 1, Integer::sum);
            need.merge(recipe.c(), 1, Integer::sum);

            double worstDist = 0.0;
            for (Flower flower : flowers) {
                int left = need.getOrDefault(flower.item(), 0);
                if (left <= 0) continue;

                need.put(flower.item(), left - 1);
                worstDist = Math.max(worstDist, flower.dist());
                if (need.values().stream().allMatch(v -> v == 0)) break;
            }

            boolean complete = need.values().stream().allMatch(v -> v == 0);
            if (!complete) continue;

            if (worstDist < bestWorstDist) {
                bestWorstDist = worstDist;
                best = recipe.honey();
            }
        }
        return best != null ? best : Items.HONEY_BOTTLE;
    }

    private static List<Flower> collectFlowers(Level level, BlockPos pos) {
        List<Flower> found = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (int dx = -FLOWER_SCAN_RADIUS; dx <= FLOWER_SCAN_RADIUS; dx++)
            for (int dy = -FLOWER_SCAN_RADIUS; dy <= FLOWER_SCAN_RADIUS; dy++)
                for (int dz = -FLOWER_SCAN_RADIUS; dz <= FLOWER_SCAN_RADIUS; dz++) {
                    p.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!level.isLoaded(p)) continue;

                    BlockState state = level.getBlockState(p);
                    Item flower = null;

                    if (state.getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock pot) {
                        var content = pot.getPotted();
                        if (content != net.minecraft.world.level.block.Blocks.AIR) flower = content.asItem();
                    } else if (state.is(BlockTags.FLOWERS) && !state.is(BlockTags.LEAVES)) {
                        if (state.hasProperty(net.minecraft.world.level.block.DoublePlantBlock.HALF)
                                && state.getValue(net.minecraft.world.level.block.DoublePlantBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
                            continue;
                        }
                        flower = state.getBlock().asItem();
                    }

                    if (flower == null || flower == Items.AIR) continue;
                    found.add(new Flower(p.distSqr(pos), flower));
                }

        found.sort(Comparator.comparingDouble(Flower::dist));
        return found;
    }

    private record Flower(double dist, Item item) {}

    public int honeyCount() {
        int count = 0;
        for (Item item : honey) if (item != null) count++;
        return count;
    }

    public boolean take(Player player, ItemStack held) {
        // 1. 玻璃瓶取蜜
        if (held.is(Items.GLASS_BOTTLE)) {
            for (int i = 0; i < MAX_HONEY; i++) {
                if (honey[i] != null) {
                    ItemStack result = new ItemStack(honey[i]);
                    honey[i] = null;

                    if (!player.getAbilities().instabuild) held.shrink(1);
                    if (!player.getInventory().add(result)) player.drop(result, false);

                    player.level().playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);

                    // 如果蜂蜜被取空了，更新方块状态
                    if (honeyCount() == 0) {
                        BlockState state = this.getBlockState();
                        if (state.hasProperty(GardenBeeNest.HAS_HONEY)) {
                            player.level().setBlock(worldPosition, state.setValue(GardenBeeNest.HAS_HONEY, false), 3);
                        }
                    }

                    setChanged();
                    player.level().sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                    return true;
                }
            }
        }
        // 2. 剪刀剪蜜脾
        else if (held.is(Items.SHEARS)) {
            for (int i = 0; i < MAX_HONEY; i++) {
                if (honey[i] != null) {
                    honey[i] = null;
                    ItemStack combs = new ItemStack(Items.HONEYCOMB, 1);
                    if (!player.getInventory().add(combs)) player.drop(combs, false);

                    player.level().playSound(null, worldPosition, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
                    if (!player.getAbilities().instabuild) {
                        held.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                    }

                    // 如果蜂蜜被取空了，更新方块状态
                    if (honeyCount() == 0) {
                        BlockState state = this.getBlockState();
                        if (state.hasProperty(GardenBeeNest.HAS_HONEY)) {
                            player.level().setBlock(worldPosition, state.setValue(GardenBeeNest.HAS_HONEY, false), 3);
                        }
                    }

                    setChanged();
                    player.level().sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                    return true;
                }
            }
        }
        return false;
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

        // 读取蜂蜜进度和产物
        this.progress = tag.getInt("Progress");
        Arrays.fill(this.honey, null);
        CompoundTag h = tag.getCompound("Honey");
        for (int i = 0; i < MAX_HONEY; i++) {
            if (h.contains("s" + i)) {
                this.honey[i] = BuiltInRegistries.ITEM.get(ResourceLocation.parse(h.getString("s" + i)));
            }
        }

        if (tag.contains("Potion")) {
            this.potion = ItemStack.parse(registries, tag.getCompound("Potion")).orElse(ItemStack.EMPTY);
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

        // 保存蜂蜜进度和产物
        tag.putInt("Progress", this.progress);
        CompoundTag h = new CompoundTag();
        for (int i = 0; i < MAX_HONEY; i++) {
            if (honey[i] != null) {
                h.putString("s" + i, BuiltInRegistries.ITEM.getKey(honey[i]).toString());
            }
        }
        tag.put("Honey", h);

        if (!this.potion.isEmpty()) {
            tag.put("Potion", this.potion.save(registries));
            tag.putInt("PotionRemaining", this.potionRemaining);
        }
    }

    // 核心逻辑：尝试从上方容器吸取滞留药水
    private PotionContents tryConsumePotionFromContainer() {
        if (this.level == null) return null;

        // 如果当前药水剩余次数大于 0，直接用当前的
        if (this.potionRemaining > 0 && !this.potion.isEmpty()) {
            this.potionRemaining--;
            return this.potion.get(DataComponents.POTION_CONTENTS);
        }

        // 否则，去上方找容器
        BlockPos abovePos = this.getBlockPos().above();
        if (this.level.getBlockEntity(abovePos) instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (stack.is(Items.LINGERING_POTION)) {
                    // 找到了！
                    PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
                    // 扣除物品（保留 1 个空瓶？这里直接消耗掉）
                    stack.shrink(1);

                    // 记录并重置使用次数
                    this.potion = new ItemStack(Items.LINGERING_POTION); // 只记录类型
                    this.potionRemaining = 9; // 一瓶用 9 次
                    this.setChanged();
                    this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
                    return contents;
                }
            }
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
        // 读取原版数据
        List<net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant> list =
                componentInput.getOrDefault(DataComponents.BEES, List.of());
        // 转换并存入你的列表
        list.forEach(occ -> this.storeBee(new GardenBeeNestBlockEntity.Occupant(occ.entityData(), occ.ticksInHive(), occ.minTicksInHive())));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        // ✅ 转换为你需要的原版类型
        List<net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant> vanillaList =
                this.getBees().stream()
                        .map(occ -> new net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant(occ.entityData(), occ.ticksInHive(), occ.minTicksInHive()))
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

    static class BeeData {
        private final GardenBeeNestBlockEntity.Occupant occupant;
        private int ticksInHive;

        BeeData(GardenBeeNestBlockEntity.Occupant occupant) {
            this.occupant = occupant;
            this.ticksInHive = occupant.ticksInHive();
        }

        public boolean tick() {
            return this.ticksInHive++ > this.occupant.minTicksInHive;
        }

        public GardenBeeNestBlockEntity.Occupant toOccupant() {
            return new GardenBeeNestBlockEntity.Occupant(this.occupant.entityData, this.ticksInHive, this.occupant.minTicksInHive);
        }

        public boolean hasNectar() {
            return this.occupant.entityData.getUnsafe().getBoolean("HasNectar");
        }
    }

    public static enum BeeReleaseStatus {
        HONEY_DELIVERED,
        BEE_RELEASED,
        EMERGENCY;
    }

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
            CompoundTag compoundtag = new CompoundTag();
            compoundtag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityRegister.GARDEN_BEE.get()).toString());
            return new GardenBeeNestBlockEntity.Occupant(CustomData.of(compoundtag), ticksInHive, 600);
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
