package com.thelema.thrive.generic.blockentity.convertor;

import com.thelema.thrive.register.BlockRegister;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * 转换器表：key 是驱动它的特殊物品 Item，不是物品栈——取 key 用 stack.getItem()。
 * 群系核心表面摆着哪个物品，就跑哪个转换器。
 */
public class ConvertorRegistry {

    public static final Map<Item, Convertor> MAP = new HashMap<>();

    static {
        MAP.put(BlockRegister.RAMPANT_MOSS.get().asItem(), new PlainsToMossConvertor());
        // 后续钥匙一行一条
    }
}
