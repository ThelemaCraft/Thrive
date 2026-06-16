package com.thelema.thrive.generic.item;

import net.minecraft.world.food.Foods;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public class MossGoldenAppleItem extends Item {

    public MossGoldenAppleItem() {
        super(new Properties()
                .food(Foods.GOLDEN_APPLE)
                .rarity(Rarity.RARE)
        );
    }
}