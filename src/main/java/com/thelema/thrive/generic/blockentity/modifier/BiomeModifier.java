package com.thelema.thrive.generic.blockentity.modifier;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 群系修改器接口
 */
public interface BiomeModifier {

    boolean canUse(ServerLevel level, BiomeCoreBlockEntity be);

    /**
     * 本 tick 处理。返回 true = 本次 forPos 遍历结束，BE 自行决定是否收尾。
     */
    boolean tick(ServerLevel level, BiomeCoreBlockEntity be);

}