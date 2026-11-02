package com.thelema.thrive.register.constant;

import com.thelema.thrive.ThriveMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

public interface TagConstant {

    // 物品
    TagKey<Item> HONEY = item("honey");

    // 实体
    TagKey<EntityType<?>> CLOUD = entity("cloud");

    // 伤害
    TagKey<DamageType> BLUNT = damage("blunt");
    TagKey<DamageType> PIERCING = damage("piercing");
    TagKey<DamageType> SLASHING = damage("slashing");
    TagKey<DamageType> WING = damage("wind");



    static TagKey<Item> item(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, name));
    }

    static TagKey<EntityType<?>> entity(String name){
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, name));
    }

    static TagKey<DamageType> damage(String name) {
        return TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, name));
    }
}
