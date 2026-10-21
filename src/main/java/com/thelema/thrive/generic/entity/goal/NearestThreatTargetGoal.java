package com.thelema.thrive.generic.entity.goal;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * 主动锁定"已经把我设为目标"的敌人：取最近的一个当目标。
 */
public class NearestThreatTargetGoal extends Goal {

    static final int SCAN_INTERVAL = 10;

    final Mob mob;
    final Predicate<LivingEntity> filter;
    int scanCooldown;
    int scanTimer;

    public NearestThreatTargetGoal(Mob mob, Predicate<LivingEntity> filter) {
        this.mob = mob;
        this.filter = filter;
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
        if (nearest != null) mob.setTarget(nearest);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive()
                && mob.distanceToSqr(target) <= followRangeSqr();
    }

    @Override
    public void stop() {
        LivingEntity target = mob.getTarget();
        if (target instanceof Mob m && m.getTarget() == mob) {
            mob.setTarget(null);
        }
    }

    @Override
    public void tick() {
        if (--scanTimer > 0) return;
        scanTimer = SCAN_INTERVAL;
        LivingEntity nearest = findNearest();
        if (nearest != null) mob.setTarget(nearest);
    }

    private LivingEntity findNearest() {
        double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        List<LivingEntity> threats = mob.level().getEntitiesOfClass(
                LivingEntity.class,
                mob.getBoundingBox().inflate(range),
                e -> e != mob
                        && e.isAlive()
                        && e instanceof Mob m && m.getTarget() == mob
                        && mob.canAttack(e)
                        && this.filter.test(e));

        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : threats) {
            double d = mob.distanceToSqr(e);
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        return nearest;
    }

    private double followRangeSqr() {
        double r = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        return r * r;
    }
}