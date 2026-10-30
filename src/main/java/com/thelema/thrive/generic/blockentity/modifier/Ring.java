package com.thelema.thrive.generic.blockentity.modifier;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import net.minecraft.core.BlockPos;

public final class Ring implements PosFinder {

    /** 默认 1 秒 1 环，4 chunk 半径，为了中心也能正常覆盖，使用 5 chunk半径，区块边界由ring保证了。 */
    public static final Ring DEFAULT = new Ring(20, 80);
    public static final Ring FAST_I = new Ring(10, 80);
    public static final Ring FAST_II = new Ring(5, 80);
    public static final Ring FAST_III = new Ring(2, 80);
    public static final Ring FAST_IV = new Ring(1, 80);

    private final int ticksPerRing;
    private final int maxRing;

    public Ring(int ticksPerRing, int maxRing) {
        this.ticksPerRing = ticksPerRing;
        this.maxRing = maxRing;
    }

    @Override
    public boolean find(BiomeCoreBlockEntity be, PosApplier apply) {
        long tick = BiomeUtil.getTick(be);
        if (tick % ticksPerRing != 0) return false;
        int r = (int) (tick / ticksPerRing);
        if (r > maxRing) return true;
        ring(be, r, apply);
        return r == maxRing;
    }

    /**
     * 遍历第 r 环（切比雪夫壳），y 覆盖 {@code [minY(be), maxY(be)]}。
     * <p>
     * r=0 时 1 列；r>0 时 8r 列。按上下两条边（含四角）+ 左右两条边（不含角）枚举。
     */
    public static void ring(BiomeCoreBlockEntity be, int r, PosApplier apply) {
        if (r < 0) return;
        BlockPos c = be.getBlockPos();
        int minY = BiomeUtil.minY(be), maxY = BiomeUtil.maxY(be);

        if (r == 0) {
            for (int y = minY; y <= maxY; y++)
                apply.apply(be, new BlockPos(c.getX(), y, c.getZ()));
            return;
        }

        for (int dx = -r; dx <= r; dx++) {
            int x = c.getX() + dx;
            for (int y = minY; y <= maxY; y++) {
                apply.apply(be, new BlockPos(x, y, c.getZ() - r));
                apply.apply(be, new BlockPos(x, y, c.getZ() + r));
            }
        }
        for (int dz = -r + 1; dz <= r - 1; dz++) {
            int z = c.getZ() + dz;
            for (int y = minY; y <= maxY; y++) {
                apply.apply(be, new BlockPos(c.getX() - r, y, z));
                apply.apply(be, new BlockPos(c.getX() + r, y, z));
            }
        }
    }
}