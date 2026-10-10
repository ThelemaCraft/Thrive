package com.thelema.thrive.generic.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/**
 * 疯长的苔藓块：群系转换的钥匙。
 * 摆到群系核心表面就启动对应的转换器（见 ConvertorRegistry），本身暂无行为。
 */
public class RampantMoss extends Block {

    public RampantMoss() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_GREEN)
                .strength(0.1F)
                .sound(SoundType.MOSS));
    }
}
