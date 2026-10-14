package com.thelema.thrive.integration.jade.component;

import com.thelema.thrive.integration.jade.data.GardenBeeDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

public enum GardenBeeComponentProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();

        // 喜好：文字 + 空格 + 图标
        if (tag.contains("FavoriteFlower")) {
            Item flower = BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse(tag.getString("FavoriteFlower")));

            IElementHelper elements = IElementHelper.get();
            IElement icon = elements.item(new ItemStack(flower), 0.5f);
            icon.message(null);

            tooltip.add(Component.translatable("jade.thrive.garden_bee.favorite",
                    flower.getDescription()));
            tooltip.append(Component.literal(" "));
            tooltip.append(icon);
        }

        // 蜂巢位置
        if (tag.contains("Home")) {
            BlockPos home = BlockPos.of(tag.getLong("Home"));
            tooltip.add(Component.translatable("jade.thrive.garden_bee.home",
                    home.getX(), home.getY(), home.getZ()));
        }

        // 花粉：图标 + 空格 + 文字
        if (tag.contains("Pollen")) {
            Item pollen = BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse(tag.getString("Pollen")));

            IElementHelper elements = IElementHelper.get();
            IElement icon = elements.item(new ItemStack(pollen), 0.5f);
            icon.message(null);

            tooltip.add(icon);
            tooltip.append(Component.literal(" "));
            tooltip.append(Component.translatable("jade.thrive.garden_bee.pollen",
                    pollen.getDescription()));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return GardenBeeDataProvider.INSTANCE.getUid();
    }
}