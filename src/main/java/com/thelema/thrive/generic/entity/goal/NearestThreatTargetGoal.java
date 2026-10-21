package com.thelema.thrive.generic.entity.goal;

import com.thelema.thrive.generic.entity.GardenWitch;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;

/**
 * 主动锁定"已经把我设为目标"的敌人：取最近的一个当目标。
 *
 * <p>原版 {@code HurtByTargetGoal} 是"被打了才还手"，对上慢吞吞逼近的铁傀儡会吃亏。
 * 这里直接扫描周围 {@code FOLLOW_RANGE} 内所有 {@code getTarget() == 自己} 的 Mob，
 * 谁近锁谁，先手反制；同时解决了"多个敌人时还盯着远处那个"的问题。
 */
public class NearestThreatTargetGoal extends Goal {

    /** 重扫间隔（tick）。 */
    static final int SCAN_INTERVAL = 10;

    final GardenWitch witch;
    int scanCooldown;
    int scanTimer;

    public NearestThreatTargetGoal(GardenWitch witch) {
        this.witch = witch;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }
        scanCooldown = SCAN_INTERVAL;
        return findNearest() != null;
    }

    @Override
    public void start() {
        scanTimer = SCAN_INTERVAL;
        LivingEntity nearest = findNearest();
        if (nearest != null) witch.setTarget(nearest);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = witch.getTarget();
        return target != null && target.isAlive()
                && witch.distanceToSqr(target) <= followRangeSqr();
    }

    @Override
    public void stop() {
        // 目标交给别的 Goal 或自然失效；只清掉自己锁定的那一个
        LivingEntity target = witch.getTarget();
        if (target instanceof Mob mob && mob.getTarget() == witch) {
            witch.setTarget(null);
        }
    }

    @Override
    public void tick() {
        if (--scanTimer > 0) return;
        scanTimer = SCAN_INTERVAL;
        LivingEntity nearest = findNearest();
        if (nearest != null) witch.setTarget(nearest);
    }

    /** 当前是否有敌人把攻击目标锁定为自己。 */
    private LivingEntity findNearest() {
        double range = witch.getAttributeValue(Attributes.FOLLOW_RANGE);
        List<LivingEntity> threats = witch.level().getEntitiesOfClass(
                LivingEntity.class,
                witch.getBoundingBox().inflate(range),
                e -> e != witch
                        && e.isAlive()
                        && e instanceof Mob mob
                        && mob.getTarget() == witch
                        && witch.canAttack(e));

        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : threats) {
            double d = witch.distanceToSqr(e);
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        return nearest;
    }

    private double followRangeSqr() {
        double r = witch.getAttributeValue(Attributes.FOLLOW_RANGE);
        return r * r;
    }
}
