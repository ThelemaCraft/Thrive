package com.thelema.thrive.generic.blockentity;

import com.thelema.thrive.generic.blockentity.other.GardenHiveRecipes;
import com.thelema.thrive.generic.entity.GardenBee;
import com.thelema.thrive.register.BlockEntityRegister;
import com.thelema.thrive.register.EntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class GardenHiveBlockEntity extends BlockEntity {

    /** 满酿进度基数。honeyCount=0 时 = 6000 tick = 5min。 */
    public static final int BREW_MAX_PROGRESS = 6000;
    /** 巢内蜜蜂上限 */
    public static final int MAX_BEES = 3;

    /** 花粉原材料 */
    public final Item[] nectar = new Item[3];
    /** 蜂蜜容器 */
    public final Item[] honey = new Item[3];
    /** 酿蜜进度 */
    public int brewProgress = 0;
    /** 巢内蜜蜂快照 */
    public final List<BeeRecord> occupants = new ArrayList<>();

    public GardenHiveBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegister.GARDEN_HIVE.get(), pos, state);
    }

    // ==================== tick ====================

    public static void tick(Level level, BlockPos pos, BlockState state, GardenHiveBlockEntity be) {
        if (level.isClientSide) return;

        // 酿造
        if (be.isBrewing()) {
            be.brewProgress += be.honeyCount() + 1;
            if (be.brewProgress >= BREW_MAX_PROGRESS) {
                be.brewProgress = 0;
                be.finishBrew();
            }
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
            return;   // 酿造中不放出蜜蜂
        }

        // 出巢：白天 + 不酿造 → 释放所有
        if (!level.isNight() && !be.isBrewing() && !be.occupants.isEmpty()) {
            be.releaseBees(level, pos);
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // ==================== 状态 ====================

    /** 是否在酿造：nectar 满 + honey == 0。 */
    public boolean isBrewing() {
        if (nectar[0] == null || nectar[1] == null || nectar[2] == null) return false;
        return honeyCount() == 0;
    }

    public int honeyCount() {
        int c = 0;
        for (Item i : honey) if (i != null) c++;
        return c;
    }

    void finishBrew() {
        Item result = GardenHiveRecipes.resolve(nectar[0], nectar[1], nectar[2]);
        if (result == null) result = Items.HONEY_BOTTLE;
        // 一次灌满所有空槽
        for (int i = 0; i < 3; i++) honey[i] = result;
        Arrays.fill(nectar, null);
    }

    // ==================== 破坏 ====================

    public void emptyAllLivingFromHive(@Nullable Player player) {
        List<BeeRecord> copy = new ArrayList<>(occupants);
        occupants.clear();
        setChanged();

        for (BeeRecord record : copy) {
            GardenBee bee = null;
            if (level != null) {
                bee = EntityRegister.GARDEN_BEE.get().create(level);
            }
            if (bee == null) continue;

            bee.setTame(true, false);
            bee.setOwnerUUID(record.ownerUUID());
            bee.favoriteFlower = record.favoriteFlower();
            bee.carriedPollen = record.carriedPollen();
            bee.home = null;                    // 家没了

            // 激怒
            if (player != null) {
                bee.setTarget(player);
                bee.startPersistentAngerTimer();
            }

            bee.moveTo(worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                    worldPosition.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
            level.addFreshEntity(bee);
        }
    }

    // ==================== 蜜蜂出 ====================

    /** 释放所有巢内蜜蜂到 home 附近。 */
    public void releaseBees(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);
        for (BeeRecord r : new ArrayList<>(occupants)) {
            GardenBee bee = EntityRegister.GARDEN_BEE.get().create(level);
            if (bee == null) continue;
            bee.setTame(true, false);
            bee.setOwnerUUID(r.ownerUUID());
            bee.favoriteFlower = r.favoriteFlower();
            bee.carriedPollen = r.carriedPollen();
            bee.home = pos;
            bee.moveTo(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                    level.random.nextFloat() * 360F, 0F);
            level.addFreshEntity(bee);
        }
        occupants.clear();
        setChanged();
    }

    // ==================== 取蜜 ====================

    /** 玩家手持玻璃瓶时取出蜂蜜。成功消耗一个玻璃瓶，塞回成品。 */
    public boolean takeHoney(Player player, ItemStack held) {
        if (!held.is(Items.GLASS_BOTTLE)) return false;

        for (int i = 0; i < 3; i++) {
            if (honey[i] == null) continue;

            ItemStack result = new ItemStack(honey[i]);
            honey[i] = null;

            if (!player.getAbilities().instabuild) held.shrink(1);
            if (!player.getInventory().add(result)) player.drop(result, false);

            player.level().playSound(null, worldPosition, SoundEvents.BOTTLE_FILL,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            setChanged();
            player.level().sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            return true;
        }
        return false;
    }

    // ==================== NBT ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.saveAdditional(tag, regs);

        CompoundTag n = new CompoundTag();
        for (int i = 0; i < 3; i++)
            if (nectar[i] != null)
                n.putString("s" + i, BuiltInRegistries.ITEM.getKey(nectar[i]).toString());
        tag.put("Nectar", n);

        CompoundTag h = new CompoundTag();
        for (int i = 0; i < 3; i++)
            if (honey[i] != null)
                h.putString("s" + i, BuiltInRegistries.ITEM.getKey(honey[i]).toString());
        tag.put("Honey", h);

        ListTag list = new ListTag();
        for (BeeRecord r : occupants) list.add(r.save());
        tag.put("Occupants", list);

        tag.putInt("BrewProgress", brewProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.loadAdditional(tag, regs);

        CompoundTag n = tag.getCompound("Nectar");
        for (int i = 0; i < 3; i++)
            if (n.contains("s" + i))
                nectar[i] = BuiltInRegistries.ITEM.get(ResourceLocation.parse(n.getString("s" + i)));

        CompoundTag h = tag.getCompound("Honey");
        for (int i = 0; i < 3; i++)
            if (h.contains("s" + i))
                honey[i] = BuiltInRegistries.ITEM.get(ResourceLocation.parse(h.getString("s" + i)));

        occupants.clear();
        ListTag list = tag.getList("Occupants", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
            occupants.add(BeeRecord.load(list.getCompound(i)));

        brewProgress = tag.getInt("BrewProgress");
    }

    // ==================== 同步 ====================

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider regs) {
        return super.getUpdateTag(regs);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ==================== 蜜蜂快照 ====================

    public record BeeRecord(UUID beeId,
                            @Nullable Item favoriteFlower,
                            @Nullable Item carriedPollen,
                            @Nullable UUID ownerUUID) {

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Bee", beeId);
            if (favoriteFlower != null)
                t.putString("Fav", BuiltInRegistries.ITEM.getKey(favoriteFlower).toString());
            if (carriedPollen != null)
                t.putString("Pollen", BuiltInRegistries.ITEM.getKey(carriedPollen).toString());
            if (ownerUUID != null)
                t.putUUID("Owner", ownerUUID);
            return t;
        }

        static BeeRecord load(CompoundTag t) {
            return new BeeRecord(
                    t.getUUID("Bee"),
                    t.contains("Fav")
                            ? BuiltInRegistries.ITEM.get(ResourceLocation.parse(t.getString("Fav")))
                            : null,
                    t.contains("Pollen")
                            ? BuiltInRegistries.ITEM.get(ResourceLocation.parse(t.getString("Pollen")))
                            : null,
                    t.hasUUID("Owner") ? t.getUUID("Owner") : null);
        }
    }
}