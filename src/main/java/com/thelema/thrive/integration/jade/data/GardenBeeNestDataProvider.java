package com.thelema.thrive.integration.jade.data;

import com.thelema.thrive.generic.blockentity.GardenBeeNestBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum GardenBeeNestDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof GardenBeeNestBlockEntity hive)) return;

        tag.putInt("Bees", hive.getOccupantCount());
        tag.putInt("Nectar", hive.nectarLevel);
        tag.putInt("Honey", hive.honeyCount());

        // 【新增】只有正在酿蜜（progress > 0）时，才发送进度数据
        if (hive.getProgress() > 0) {
            tag.putInt("Progress", hive.getProgress());
            tag.putInt("MaxProgress", GardenBeeNestBlockEntity.BREW_TIME);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath("thrive", "garden_bee_nest");
    }
}