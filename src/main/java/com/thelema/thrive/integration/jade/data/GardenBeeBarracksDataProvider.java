package com.thelema.thrive.integration.jade.data;

import com.thelema.thrive.generic.blockentity.GardenBeeBarracksBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum GardenBeeBarracksDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof GardenBeeBarracksBlockEntity barracks)) return;
        if (accessor.getLevel().isClientSide) return;

        tag.putInt("Bees", barracks.storedBees.size());

        if (barracks.ownerUUID == null) {
            tag.putString("Owner", "");
        } else if (accessor.getLevel().getServer() != null) {
            ServerPlayer owner = accessor.getLevel().getServer().getPlayerList().getPlayer(barracks.ownerUUID);
            tag.putString("Owner", owner != null ? owner.getName().getString() : barracks.ownerUUID.toString());
        }
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath("thrive", "garden_bee_barracks");
    }
}
