package com.thelema.thrive.generic.item;

import com.thelema.thelemalib.item.ItemCustomData;
import com.thelema.thrive.register.ItemRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 群系新枝：橡树树苗右键群系核心转化而来。
 * 物品栈中记录来源核心的坐标，供后续仪式定位对应的群系核心。
 */
public class BiomeTwig extends Item {

    // 物品栈中记录群系核心坐标的键，存的是 BlockPos.asLong()
    public static final String BIOME_CORE_POS = "BiomeCorePos";

    public BiomeTwig() {
        super(new Item.Properties());
    }

    // 生成一根记录了核心坐标的新枝；带坐标的新枝附魔光效，便于和普通新枝区分
    public static ItemStack of(BlockPos corePos) {
        ItemStack stack = new ItemStack(ItemRegister.BIOME_TWIG.get());
        ItemCustomData.update(stack, tag -> tag.putLong(BIOME_CORE_POS, corePos.asLong()));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }
}
