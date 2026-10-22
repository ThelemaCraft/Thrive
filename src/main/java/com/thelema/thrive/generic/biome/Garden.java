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

    // 记录上一次触发的游戏天数，确保同一天内只触发一次
    private static long lastSpawnDay = -1;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();

        // 每 1200 tick（现实 1 分钟，游戏内 1/20 天）检查一次
        if (level.getServer().getTickCount() % 1200 != 0) return;

        long currentDay = level.getDayTime() / 24000L;

        // 1. 每 3 天来一次
        if (currentDay <= 0 || currentDay % 3 != 0) return;

        // 2. 每天最多只能来一次
        if (lastSpawnDay == currentDay) return;

        // 3. 时间段不一定：每天 20 次机会，每次 15% 概率触发（一天内有 96% 概率会来）
        if (ThriveMod.R.nextFloat() > 0.15f) return;

        // 标记今天已经刷过了
        lastSpawnDay = currentDay;

        // 随便抓一个玩家作为坐标锚点
        var players = event.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        ServerPlayer anchor = players.get(ThriveMod.R.nextInt(players.size()));

        // 在玩家周围 64~128 格搜索花园群系
        BlockPos spawnPos = findGardenBiomeNearby(anchor.serverLevel(), anchor.blockPosition());
        if (spawnPos == null) return;

        // 4. 10% 的概率生成 4-5 次同一时间点的波次
        int waves = 1;
        if (ThriveMod.R.nextFloat() < 0.10f) {
            waves = 4 + ThriveMod.R.nextInt(2); // 4 或 5 波
        }

        // 在同一位置连续生成
        for (int i = 0; i < waves; i++) {
            spawnPatrolGroup(anchor.serverLevel(), spawnPos, anchor);
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

    private static void spawnPatrolGroup(ServerLevel level, BlockPos spawnPos, ServerPlayer targetPlayer) {
        GardenWitch witch = EntityRegister.GARDEN_WITCH.get().create(level);
        if (witch != null) {
            witch.moveTo(spawnPos, ThriveMod.R.nextFloat() * 360f, 0);
            witch.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.NATURAL, null);
            witch.setTarget(targetPlayer);

            // 确保仇恨范围足够大，跨越 64~128 格追踪玩家
            if (witch.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) instanceof net.minecraft.world.entity.ai.attributes.AttributeInstance attr) {
                attr.setBaseValue(128.0D);
            }
            level.addFreshEntity(witch);
        }

        // 每次固定生成 3~5 只掠夺者
        int count = 3 + ThriveMod.R.nextInt(3);
        for (int i = 0; i < count; i++) {
            BlockPos p = spawnPos.offset(ThriveMod.R.nextInt(8) - 4, 0, ThriveMod.R.nextInt(8) - 4);
            GardenPillager pillager = EntityRegister.GARDEN_PILLAGER.get().create(level);
            if (pillager != null) {
                pillager.moveTo(p, ThriveMod.R.nextFloat() * 360f, 0);
                pillager.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.NATURAL, null);
                pillager.setTarget(targetPlayer);

                if (pillager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) instanceof net.minecraft.world.entity.ai.attributes.AttributeInstance attr) {
                    attr.setBaseValue(128.0D);
                }
                level.addFreshEntity(pillager);
            }
        }
    }
}