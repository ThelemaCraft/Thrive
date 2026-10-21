package com.thelema.thrive.generic.biome;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.entity.GardenPillager;
import com.thelema.thrive.generic.entity.GardenWitch;
import com.thelema.thrive.register.BiomeRegister;
import com.thelema.thrive.register.EntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = ThriveMod.MOD_ID)
public class Garden {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();

        // 每 1200 tick（现实 1 分钟，游戏内 1/20 天）检查一次
        if (level.getServer().getTickCount() % 1200 != 0) return;

        // 计算当前天数
        long day = level.getDayTime() / 24000L;

        // 是否是第 10, 20, 30... 天。并且时间刚好到达这一天的开始（清晨）
        boolean isMassiveSpawn = day > 0 && day % 10 == 0 && level.getDayTime() % 24000L == 0;

        // 如果不是大刷新，那么日常有 15% 概率触发（20次 * 0.15 = 平均每天3次）
        if (!isMassiveSpawn) {
            if (ThriveMod.R.nextFloat() > 0.15f) return;
        }

        // 随便抓一个玩家作为坐标锚点
        var players = event.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        ServerPlayer anchor = players.get(ThriveMod.R.nextInt(players.size()));

        // 在玩家周围 64~128 格搜索花园群系
        BlockPos spawnPos = findGardenBiomeNearby(anchor.serverLevel(), anchor.blockPosition());
        if (spawnPos == null) return;

        // 刷新
        spawnPatrolGroup(anchor.serverLevel(), spawnPos, isMassiveSpawn);

        // 大刷新额外刷一波
        if (isMassiveSpawn) {
            BlockPos secondPos = findGardenBiomeNearby(anchor.serverLevel(), anchor.blockPosition());
            if (secondPos != null) spawnPatrolGroup(anchor.serverLevel(), secondPos, true);
        }
    }

    private static BlockPos findGardenBiomeNearby(ServerLevel level, BlockPos center) {
        for (int i = 0; i < 15; i++) {
            double angle = ThriveMod.R.nextDouble() * Math.PI * 2;
            int dist = 64 + ThriveMod.R.nextInt(64); // 64~128格
            int dx = (int) (Math.cos(angle) * dist);
            int dz = (int) (Math.sin(angle) * dist);
            BlockPos origin = center.offset(dx, 0, dz);

            if (!level.hasChunkAt(origin)) continue;
            // 核心：只需要生成点在花园群系即可
            if (!level.getBiome(origin).is(BiomeRegister.GARDEN)) continue;

            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, origin.getX(), origin.getZ());
            return new BlockPos(origin.getX(), y, origin.getZ());
        }
        return null;
    }

    private static void spawnPatrolGroup(ServerLevel level, BlockPos spawnPos, boolean isMassive) {
        GardenWitch witch = EntityRegister.GARDEN_WITCH.get().create(level);
        if (witch != null) {
            witch.moveTo(spawnPos, ThriveMod.R.nextFloat() * 360f, 0);
            witch.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.NATURAL, null);
            level.addFreshEntity(witch);
        }

        // 普通情况 3~5 只；大刷新 5~8 只
        int count = (isMassive ? 5 : 3) + ThriveMod.R.nextInt(isMassive ? 4 : 3);
        for (int i = 0; i < count; i++) {
            BlockPos p = spawnPos.offset(ThriveMod.R.nextInt(8) - 4, 0, ThriveMod.R.nextInt(8) - 4);
            GardenPillager pillager = EntityRegister.GARDEN_PILLAGER.get().create(level);
            if (pillager != null) {
                pillager.moveTo(p, ThriveMod.R.nextFloat() * 360f, 0);
                pillager.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.NATURAL, null);
                level.addFreshEntity(pillager);
            }
        }
    }
}