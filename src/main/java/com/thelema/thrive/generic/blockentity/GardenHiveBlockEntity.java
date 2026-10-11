package com.thelema.thrive.generic.blockentity;

import com.thelema.thrive.generic.blockentity.other.GardenHiveRecipes;
import com.thelema.thrive.register.BlockEntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GardenHiveBlockEntity extends BlockEntity {

    /** 酿一组蜜所需 tick。 */
    static final int BREW_TICKS = 400;

    public final Item[] nectar = new Item[3];
    public final Item[] honey  = new Item[3];
    public final List<BeeRecord> occupants = new ArrayList<>();
    public int brewTimer = BREW_TICKS;

    public GardenHiveBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegister.GARDEN_HIVE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GardenHiveBlockEntity be) {
        if (level.isClientSide) return;

        if (be.nectar[0] == null || be.nectar[1] == null || be.nectar[2] == null) return;

        int speed = be.occupants.size();
        if (speed <= 0) return;   // 无蜜蜂停滞

        be.brewTimer -= speed;
        if (be.brewTimer > 0) return;

        Item result = GardenHiveRecipes.resolve(be.nectar[0], be.nectar[1], be.nectar[2]);
        if (result != null) {
            for (int i = 0; i < 3; i++) {
                if (be.honey[i] == null) be.honey[i] = result;   // 有空位才装
                be.nectar[i] = null;
            }
        } else {
            // 无配方，丢
            java.util.Arrays.fill(be.nectar, null);
        }
        be.brewTimer = BREW_TICKS;
        be.setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
    }

    /** 取出一瓶蜂蜜，装进空槽。 */
    public boolean takeHoney(Player player) {
        for (int i = 0; i < 3; i++) {
            if (honey[i] != null) {
                ItemStack stack = new ItemStack(honey[i]);
                honey[i] = null;
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                return true;
            }
        }
        return false;
    }

    // ==================== NBT ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.saveAdditional(tag, regs);

        CompoundTag n = new CompoundTag();
        for (int i = 0; i < 3; i++)
            if (nectar[i] != null) n.putString("s" + i, key(nectar[i]));
        tag.put("Nectar", n);

        CompoundTag h = new CompoundTag();
        for (int i = 0; i < 3; i++)
            if (honey[i] != null) h.putString("s" + i, key(honey[i]));
        tag.put("Honey", h);

        ListTag list = new ListTag();
        for (BeeRecord r : occupants) list.add(r.save());
        tag.put("Occupants", list);

        tag.putInt("BrewTimer", brewTimer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.loadAdditional(tag, regs);

        CompoundTag n = tag.getCompound("Nectar");
        for (int i = 0; i < 3; i++)
            if (n.contains("s" + i)) nectar[i] = parse(n.getString("s" + i));

        CompoundTag h = tag.getCompound("Honey");
        for (int i = 0; i < 3; i++)
            if (h.contains("s" + i)) honey[i] = parse(h.getString("s" + i));

        occupants.clear();
        ListTag list = tag.getList("Occupants", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) occupants.add(BeeRecord.load(list.getCompound(i)));

        brewTimer = tag.getInt("BrewTimer");
    }

    static String key(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }
    static @Nullable Item parse(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }

    /** 蜜蜂在蜂巢中的数据快照。 */
    public record BeeRecord(UUID beeId, @Nullable Item favoriteFlower,
                            @Nullable Item carriedPollen, @Nullable UUID ownerUUID) {

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Bee", beeId);
            if (favoriteFlower != null) t.putString("Fav", key(favoriteFlower));
            if (carriedPollen != null) t.putString("Pollen", key(carriedPollen));
            if (ownerUUID != null) t.putUUID("Owner", ownerUUID);
            return t;
        }

        static BeeRecord load(CompoundTag t) {
            return new BeeRecord(
                    t.getUUID("Bee"),
                    t.contains("Fav") ? parse(t.getString("Fav")) : null,
                    t.contains("Pollen") ? parse(t.getString("Pollen")) : null,
                    t.hasUUID("Owner") ? t.getUUID("Owner") : null);
        }
    }
}
