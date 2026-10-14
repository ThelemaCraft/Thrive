package com.thelema.thrive.generic.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;

/** 花园掠夺者。原版掠夺者模板，携带少量药水箭。 */
public class GardenPillager extends Pillager {

    /** 剩余药水箭数量。 */
    public int potionArrows = 1;

    /** 药水箭携带的药水 id（字符串形式）。 */
    public String potionId = "thrive:super_strong_harming";

    public GardenPillager(EntityType<? extends GardenPillager> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Pillager.createAttributes()
                .add(Attributes.MAX_HEALTH, 30.0);
    }

    // ==================== NBT ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("PotionArrows", potionArrows);
        tag.putString("PotionId", potionId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("PotionArrows")) potionArrows = tag.getInt("PotionArrows");
        if (tag.contains("PotionId")) potionId = tag.getString("PotionId");
    }
}