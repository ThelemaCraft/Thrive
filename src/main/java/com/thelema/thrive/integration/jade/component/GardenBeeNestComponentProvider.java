package com.thelema.thrive.integration.jade.component;

import com.thelema.thrive.integration.jade.data.GardenBeeNestDataProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum GardenBeeNestComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();

        tooltip.add(Component.translatable("jade.thrive.garden_bee_nest.bees", tag.getInt("Bees"), 3));
        tooltip.add(Component.translatable("jade.thrive.garden_bee_nest.nectar", tag.getInt("Nectar"), 5));
        tooltip.add(Component.translatable("jade.thrive.garden_bee_nest.honey", tag.getInt("Honey"), 5));

        // 【新增】如果 NBT 里有 Progress，说明正在酿蜜，才显示第四行
        if (tag.contains("Progress")) {
            int progress = tag.getInt("Progress");
            int maxProgress = tag.getInt("MaxProgress");
            // 计算百分比
            int percent = (int) ((float) progress / maxProgress * 100);

            tooltip.add(Component.translatable("jade.thrive.garden_bee_nest.progress", percent));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return GardenBeeNestDataProvider.INSTANCE.getUid();
    }
}