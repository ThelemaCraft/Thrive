package com.thelema.thrive.generic.entity.goal;

import com.thelema.thrive.generic.block.GardenHive;
import com.thelema.thrive.generic.blockentity.GardenHiveBlockEntity;
import com.thelema.thrive.generic.entity.GardenBee;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** 已驯服的花园蜜蜂，主动去把附近的普通蜂巢转化为花园蜂巢。 */
public class ConvertHiveGoal extends Goal {

    static final int SEARCH_RADIUS = 20;
    static final double ARRIVE_DIST = 2.0;

    final GardenBee bee;
    @Nullable BlockPos target;

    public ConvertHiveGoal(GardenBee bee) {
        this.bee = bee;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (bee.favoriteFlower == null) return false;
        if (bee.getTarget() != null) return false;
        target = findHive();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null
                && bee.favoriteFlower != null
                && bee.getTarget() == null
                && bee.level().getBlockState(target).is(BlockTags.BEEHIVES);
    }

    @Override
    public void stop() {
        target = null;
        bee.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (target == null) return;

        if (bee.blockPosition().closerThan(target, ARRIVE_DIST)) {
            convert(target);
            target = null;
            return;
        }

        if (bee.tickCount % 20 == 0) {
            bee.getNavigation().moveTo(
                    target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
        }
    }

    @Nullable
    private BlockPos findHive() {
        BlockPos origin = bee.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
            if (bee.level().getBlockState(p).is(BlockTags.BEEHIVES)) return p.immutable();
        }
        return null;
    }

    private void convert(BlockPos pos) {
        var level = bee.level();
        BlockState oldState = level.getBlockState(pos);

        // 1. 读取旧蜂巢 occupants（1.21 无公开 getter，走 NBT："bees"  entity_data）
        List<CompoundTag> beeTags = new ArrayList<>();
        if (level.getBlockEntity(pos) instanceof BeehiveBlockEntity old) {
            CompoundTag saved = old.saveWithoutMetadata(level.registryAccess());
            ListTag bees = saved.getList("bees", Tag.TAG_COMPOUND);
            for (int i = 0; i < bees.size(); i++) {
                CompoundTag data = bees.getCompound(i).getCompound("entity_data");
                if (!data.isEmpty()) beeTags.add(data);
            }
        }

        // 2. 替换方块，保留 FACING
        Direction facing = oldState.hasProperty(BeehiveBlock.FACING)
                ? oldState.getValue(BeehiveBlock.FACING)
                : Direction.NORTH;
        level.setBlockAndUpdate(pos, BlockRegister.GARDEN_HIVE.get().defaultBlockState()
                .setValue(GardenHive.FACING, facing));

        // 3. 把旧 occupant 转成 BeeRecord 写入新 BE
        if (level.getBlockEntity(pos) instanceof GardenHiveBlockEntity hive) {
            for (CompoundTag tag : beeTags) {
                UUID id = tag.hasUUID("UUID") ? tag.getUUID("UUID") : UUID.randomUUID();
                hive.occupants.add(new GardenHiveBlockEntity.BeeRecord(id, null, null, null));
            }
            hive.setChanged();
        }

        level.playSound(null, pos, SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
