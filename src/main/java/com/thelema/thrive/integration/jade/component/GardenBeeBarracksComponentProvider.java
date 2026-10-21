package com.thelema.thrive.integration.jade.component;

import com.thelema.thrive.generic.blockentity.GardenBeeBarracksBlockEntity;
import com.thelema.thrive.integration.jade.data.GardenBeeBarracksDataProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum GardenBeeBarracksComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();

        tooltip.add(Component.translatable("jade.thrive.garden_bee_barracks.bees",
                tag.getInt("Bees"), GardenBeeBarracksBlockEntity.MAX_BEES));

        String owner = tag.getString("Owner");
        if (owner.isEmpty()) {
            tooltip.add(Component.translatable("jade.thrive.garden_bee_barracks.owner.none"));
        } else {
            tooltip.add(Component.translatable("jade.thrive.garden_bee_barracks.owner", owner));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return GardenBeeBarracksDataProvider.INSTANCE.getUid();
    }
}
