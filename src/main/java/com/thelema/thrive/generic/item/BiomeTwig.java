package com.thelema.thrive.generic.item;

import net.minecraft.world.item.Item;

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

}
