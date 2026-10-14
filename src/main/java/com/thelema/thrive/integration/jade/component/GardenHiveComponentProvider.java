package com.thelema.thrive.integration.jade.component;

import com.thelema.thrive.integration.jade.data.GardenHiveDataProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

public enum GardenHiveComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();

        // 蜜蜂数
        tooltip.add(Component.translatable("jade.thrive.garden_hive.bees",
                tag.getInt("Bees"), 3));

        // 花粉槽
        tooltip.add(Component.translatable("jade.thrive.garden_hive.nectar"));
        appendSlots(tooltip, tag, "N");

        // 蜂蜜槽
        tooltip.add(Component.translatable("jade.thrive.garden_hive.honey"));
        appendSlots(tooltip, tag, "H");

        // 进度
        if (tag.contains("Progress")) {
            int cur = tag.getInt("Progress");
            int max = tag.getInt("ProgressMax");
            int pct = max > 0 ? cur * 100 / max : 0;
            tooltip.add(Component.translatable("jade.thrive.garden_hive.progress", pct));
        }
    }

    private static void appendSlots(ITooltip tooltip, CompoundTag tag, String prefix) {
        IElementHelper elements = IElementHelper.get();
        for (int i = 0; i < 3; i++) {
            String key = prefix + i;
            if (tag.contains(key)) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString(key)));
                IElement icon = elements.item(new ItemStack(item), 0.5f).size(
                        new net.minecraft.world.phys.Vec2(10, 10));
                icon.message(null);
                tooltip.append(icon);
            } else {
                tooltip.append(Component.literal("—"));
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return GardenHiveDataProvider.INSTANCE.getUid();
    }
}