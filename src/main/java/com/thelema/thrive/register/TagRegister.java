package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class TagRegister {

    public static final TagKey<Item> HONEY = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, "honey"));

}
