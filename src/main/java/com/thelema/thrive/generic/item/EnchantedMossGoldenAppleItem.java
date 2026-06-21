package com.thelema.thrive.generic.item;

import com.thelema.thrive.util.Flags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

public class EnchantedMossGoldenAppleItem extends Item {

    public EnchantedMossGoldenAppleItem() {
        super(new Properties()
                .food(Foods.ENCHANTED_GOLDEN_APPLE)
                .rarity(Rarity.EPIC)
                .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        );
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        living.getPersistentData().putBoolean(Flags.MOSS_CALLUS, true);
        return super.finishUsingItem(stack, level, living);
    }


}