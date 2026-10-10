package com.thelema.thrive.generic.blockentity.convertor;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiConsumer;

/**
 * 一条转换路径的完整逻辑。
 * 不关心起始群系和目标群系——具体怎么做，全在 tick 里。
 * BE 只负责调度（按表面物品找到本转换器、调用 tick、读写存档）。
 */
public interface Convertor {

    /** 唯一抽象方法 → 函数式接口，简单路径可用 lambda 替代。 */
    void tick(ServerLevel level, BiomeCoreBlockEntity be);

    /** 读本转换已运行的 tick 数。存 data，读档续跑。 */
    default long tick(BiomeCoreBlockEntity be) { return be.data.getLong("tick"); }

    /** 本 tick 结束前调用，把 tick 数 +1。 */
    default void advanceTick(BiomeCoreBlockEntity be) {
        be.data.putLong("tick", be.data.getLong("tick") + 1);
    }

    /**
     * 第 r 环（切比雪夫壳），y 覆盖 [minY, maxY]。
     */
    default void forRing(ServerLevel level, BiomeCoreBlockEntity be, int r,
                         BiConsumer<BlockPos, BlockState> action) {
        if (r < 0) return;
        BlockPos c = be.getBlockPos();
        int minY = be.minY(), maxY = be.maxY();
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                for (int y = minY; y <= maxY; y++) {
                    BlockPos p = new BlockPos(c.getX() + dx, y, c.getZ() + dz);
                    action.accept(p, level.getBlockState(p));
                }
            }
    }

    /** setBlock + 光照刷新。 */
    default void set(ServerLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, 3);
        level.getLightEngine().checkBlock(p);
    }

    /** 标记该方块所在 chunk 需要重发。 */
    default void markDirty(BiomeCoreBlockEntity be, BlockPos p) {
        be.dirty.add(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
    }
}
