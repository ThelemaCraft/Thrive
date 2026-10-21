package com.thelema.thrive.integration.jade.component;

import com.thelema.thrive.generic.blockentity.GardenHiveBlockEntity;
import com.thelema.thrive.integration.jade.data.GardenHiveDataProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum GardenHiveComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();

        // 蜜蜂数：2/3
        tooltip.add(Component.translatable("jade.thrive.garden_hive.bees",
                tag.getInt("Bees"), GardenHiveBlockEntity.MAX_BEES));

        // 花粉：3/5
        tooltip.add(Component.translatable("jade.thrive.garden_hive.nectar",
                tag.getInt("Nectar"), GardenHiveBlockEntity.MAX_NECTAR));

        // 蜂蜜：1/5
        tooltip.add(Component.translatable("jade.thrive.garden_hive.honey",
                tag.getInt("Honey"), GardenHiveBlockEntity.MAX_HONEY));

    }

    @Override
    public ResourceLocation getUid() {
        return GardenHiveDataProvider.INSTANCE.getUid();
    }
}