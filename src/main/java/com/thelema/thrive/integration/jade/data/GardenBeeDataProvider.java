package com.thelema.thrive.integration.jade.data;

import com.thelema.thrive.generic.entity.GardenBee;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;

public enum GardenBeeDataProvider implements IServerDataProvider<EntityAccessor> {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        if (!(accessor.getEntity() instanceof GardenBee bee)) return;
        if (bee.favoriteFlower != null) {
            tag.putString("FavoriteFlower",
                    BuiltInRegistries.ITEM.getKey(bee.favoriteFlower).toString());
        }
        if (bee.home != null) {
            tag.putLong("Home", bee.home.asLong());
        }
        if (bee.carriedPollen != null) {
            tag.putString("Pollen",
                    BuiltInRegistries.ITEM.getKey(bee.carriedPollen).toString());
        }
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath("thrive", "garden_bee");
    }
}