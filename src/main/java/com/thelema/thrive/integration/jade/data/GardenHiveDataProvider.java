package com.thelema.thrive.integration.jade.data;

import com.thelema.thrive.generic.blockentity.GardenHiveBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum GardenHiveDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof GardenHiveBlockEntity hive)) return;

        tag.putInt("Bees", hive.occupantCount);
        tag.putInt("Nectar", hive.nectarLevel);
        tag.putInt("Honey", hive.honeyCount());

    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath("thrive", "garden_hive");
    }
}