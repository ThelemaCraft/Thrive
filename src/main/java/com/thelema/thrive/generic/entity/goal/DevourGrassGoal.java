package com.thelema.thrive.generic.entity.goal;

import com.thelema.thrive.generic.entity.GrassLandSheep;
import com.thelema.thrive.register.BlockRegister;
import com.thelema.thrive.register.EffectRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public class DevourGrassGoal extends Goal {

    private static final int SCAN_RADIUS = 16;   // 冲多远找草
    private static final int DEVOUR_RADIUS = 2;  // 每 tick 破坏周围多大范围
    private static final double SPEED = 2.0;     // 移动速度倍率

    private final GrassLandSheep sheep;
    private BlockPos targetPos;

    public DevourGrassGoal(GrassLandSheep sheep) {
        this.sheep = sheep;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return sheep.hasEffect(EffectRegister.SUPER_HUNGER);
    }

    @Override
    public boolean canContinueToUse() {
        return sheep.hasEffect(EffectRegister.SUPER_HUNGER);
    }

    @Override
    public void start() {
        sheep.setCustomName(Component.translatable("entity.thrive.grass_land_sheep.title"));
        pickNewTarget();
    }

    @Override
    public void tick() {
        // 每 tick 撕碎周围的草
        devourAround();

        // 动画 / 音效持续，节流避免刷屏
        if (sheep.tickCount % 4 == 0) {
            sheep.handleEntityEvent((byte) 10);
        }

        // 到达或前方没草了，立刻重选
        if (targetPos == null
                || sheep.blockPosition().closerThan(targetPos, 2.0)
                || !isValidTarget()) {
            pickNewTarget();
        }

        // 定期刷新路径
        if (sheep.tickCount % 5 == 0 && targetPos != null) {
            sheep.getNavigation().moveTo(
                targetPos.getX() + 0.5,
                targetPos.getY(),
                targetPos.getZ() + 0.5,
                SPEED
            );
        }
    }

    @Override
    public void stop() {
        targetPos = null;
        sheep.getNavigation().stop();
    }

    // ===== 内部逻辑 =====

    private boolean isValidTarget() {
        if (targetPos == null) return false;
        Level level = sheep.level();
        for (BlockPos pos : BlockPos.betweenClosed(
                targetPos.offset(-1, -1, -1),
                targetPos.offset(1, 1, 1))) {
            if (isGrass(level.getBlockState(pos))) return true;
        }
        return false;
    }

    /**
     * 找一个有草的方向冲；找不到也照样冲，绝不停下
     */
    private void pickNewTarget() {
        Level level = sheep.level();
        BlockPos center = sheep.blockPosition();
        RandomSource random = sheep.getRandom();

        for (int attempt = 0; attempt < 24; attempt++) {
            int dx = random.nextInt(SCAN_RADIUS * 2 + 1) - SCAN_RADIUS;
            int dz = random.nextInt(SCAN_RADIUS * 2 + 1) - SCAN_RADIUS;
            if (dx * dx + dz * dz < 25) continue; // 太近没意义

            for (int dy = -3; dy <= 3; dy++) {
                BlockPos check = center.offset(dx, dy, dz);
                if (isGrass(level.getBlockState(check))) {
                    targetPos = check.immutable();
                    moveToTarget();
                    return;
                }
            }
        }

        // 没草也要冲，保持运动状态
        int dx = random.nextInt(SCAN_RADIUS * 2 + 1) - SCAN_RADIUS;
        int dz = random.nextInt(SCAN_RADIUS * 2 + 1) - SCAN_RADIUS;
        targetPos = center.offset(dx, 0, dz);
        moveToTarget();
    }

    private void moveToTarget() {
        if (targetPos == null) return;
        sheep.getNavigation().moveTo(
            targetPos.getX() + 0.5,
            targetPos.getY(),
            targetPos.getZ() + 0.5,
            SPEED
        );
    }

    /**
     * 撕碎羊周围所有的草
     */
    private void devourAround() {
        Level level = sheep.level();
        if (level.isClientSide) return;

        BlockPos center = sheep.blockPosition();
        boolean ate = false;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-DEVOUR_RADIUS, -DEVOUR_RADIUS, -DEVOUR_RADIUS),
                center.offset(DEVOUR_RADIUS, 1, DEVOUR_RADIUS))) {
            if (isGrass(level.getBlockState(pos))) {
                level.destroyBlock(pos, false);
                ate = true;
            }
        }

        if (ate) {
            sheep.heal(10.0F);

            var attr = sheep.getAttribute(Attributes.MAX_HEALTH);
            if (attr != null && attr.getBaseValue() < 50.0) {
                attr.setBaseValue(Math.min(attr.getBaseValue() + 1.0, 50.0));
            }
            sheep.updateGrowthScale();
        }
    }

    private boolean isGrass(BlockState state) {
        return state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS);
    }
}