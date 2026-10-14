package com.thelema.thrive.integration.jade.data;

import com.thelema.thrive.generic.blockentity.GardenHiveBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import net.minecraft.resources.ResourceLocation;

public enum GardenHiveDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof GardenHiveBlockEntity hive)) return;

        tag.putInt("Bees", hive.occupants.size());

        for (int i = 0; i < 3; i++) {
            if (hive.nectar[i] != null)
                tag.putString("N" + i, BuiltInRegistries.ITEM.getKey(hive.nectar[i]).toString());
            if (hive.honey[i] != null)
                tag.putString("H" + i, BuiltInRegistries.ITEM.getKey(hive.honey[i]).toString());
        }

        if (hive.isBrewing()) {
            tag.putInt("Progress", hive.brewProgress);
            tag.putInt("ProgressMax", GardenHiveBlockEntity.BREW_MAX_PROGRESS);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath("thrive", "garden_hive");
    }
}