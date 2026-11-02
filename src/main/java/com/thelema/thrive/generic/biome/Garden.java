package com.thelema.thrive.generic.biome;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.register.constant.BiomeConstant;
import com.thelema.thrive.register.EntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

        // 3. 每天 20 次机会，每次 15% 概率触发（一天内有 96% 概率会来）
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
            if (!level.getBiome(origin).is(BiomeConstant.GARDEN)) continue;

            // 忽略树叶，避免锚点落在树冠上
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ());
            return new BlockPos(origin.getX(), y, origin.getZ());
        }
        return null;
    }

    private static void spawnPatrolGroup(ServerLevel level, BlockPos anchor, ServerPlayer target) {
        // Witch
        BlockPos witchPos = findPos(level, anchor, EntityRegister.GARDEN_WITCH.get());
        if (witchPos != null) {
            spawn(EntityRegister.GARDEN_WITCH.get().create(level), level, witchPos, target);
        }

        // Pillager 3~5 只
        int count = 3 + ThriveMod.R.nextInt(3);
        for (int i = 0; i < count; i++) {
            BlockPos p = findPos(level, anchor, EntityRegister.GARDEN_PILLAGER.get());
            if (p != null) {
                spawn(EntityRegister.GARDEN_PILLAGER.get().create(level), level, p, target);
            }
        }
    }

    /**
     * 在锚点 ±12 格内找一个规则认可的地面位置，最多试 24 次。
     * 用 MobSpawnType.EVENT 调用规则，跳过光照检查，白天也能刷；
     * ON_GROUND 约束会拦掉水面、半空、悬崖外的位置。
     */
    private static BlockPos findPos(ServerLevel level, BlockPos anchor, EntityType<?> type) {
        for (int i = 0; i < 24; i++) {
            BlockPos base = anchor.offset(
                    ThriveMod.R.nextInt(25) - 12, 0,
                    ThriveMod.R.nextInt(25) - 12);
            if (!level.hasChunkAt(base)) continue;

            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ());
            BlockPos pos = new BlockPos(base.getX(), y, base.getZ());

            if (SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.EVENT, pos, level.random)) {
                return pos;
            }
        }
        return null;
    }

    /** 把一只已创建的实体放到合法位置并加入世界 */
    private static void spawn(Mob mob, ServerLevel level, BlockPos pos, ServerPlayer target) {
        if (mob == null) return;
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, ThriveMod.R.nextFloat() * 360f, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        mob.setTarget(target);
        if (mob.getAttribute(Attributes.FOLLOW_RANGE) instanceof AttributeInstance attr) {
            attr.setBaseValue(128.0D);
        }
        level.addFreshEntity(mob);
    }
}