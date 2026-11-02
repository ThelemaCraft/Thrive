package com.thelema.thrive.generic.block.other;

import com.thelema.thrive.register.BlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

import java.util.concurrent.ConcurrentHashMap;

public final class CloudLightHelper {

    private static final ConcurrentHashMap<Long, Integer> CACHE = new ConcurrentHashMap<>();
    private static final long CLEAN_INTERVAL_MS = 10 * 60 * 1000L;   // 10 分钟
    private static final int  MAX_SIZE          = 200_000;           // 约 20 MB 上限

    private static volatile long lastClean = System.currentTimeMillis();

    public static final int NO_CLOUD = Integer.MIN_VALUE;

    private CloudLightHelper() {}

    private static long key(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }

    /** 周期性清理：超过 10 分钟或超容量时整表清空。 */
    private static void maybeClean() {
        long now = System.currentTimeMillis();
        if (now - lastClean > CLEAN_INTERVAL_MS || CACHE.size() > MAX_SIZE) {
            CACHE.clear();
            lastClean = now;
        }
    }

    public static int cloudLowestY(BlockGetter level, int x, int z) {
        maybeClean();

        long k = key(x, z);
        Integer cached = CACHE.get(k);
        if (cached != null) return cached;

        int topY = level.getMaxBuildHeight() - 1;
        int bottomY = level.getMinBuildHeight();

        int cloudY = NO_CLOUD;
        for (int y = topY; y >= bottomY; y--) {
            if (level.getBlockState(new BlockPos(x, y, z)).is(BlockRegister.CLOUD.get())) {
                cloudY = y;
                break;
            }
        }
        int result;
        if (cloudY == NO_CLOUD) {
            result = NO_CLOUD;
        } else {
            int lowest = cloudY;
            for (int y = cloudY - 1; y >= bottomY; y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (level.getBlockState(p).getLightBlock(level, p) > 0) break;
                lowest = y;
            }
            result = lowest;
        }
        CACHE.put(k, result);
        return result;
    }

    public static void invalidate(int x, int z) {
        CACHE.remove(key(x, z));
    }
}