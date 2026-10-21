package com.thelema.thrive.generic.entity.goal;

import com.thelema.thrive.generic.entity.GardenWitch;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 女巫风筝 AI：保持"安全且在攻击范围内"的距离，边跑边投掷。
 *
 * <p>走村民那套：{@code LandRandomPos.getPosAway} 选一个真实可站立的点，
 * 交给 {@code PathNavigation} 走 A*；只有路径走完、威胁移动较多或安全距离被突破时才重选，
 * 避免每 tick 重算 A*。
 *
 * <p>距离策略：想停在 {@link #KEEP}（安全 + 投掷范围内）。
 * 目标逼近到 {@link #FLEE_AT} 以内 -> 快速后撤；目标跑出 {@link #HOLD_FAR} -> 追击。
 */
public class KitingGoal extends Goal {

    /** 想保持的距离：安全（近战够不到）且在投掷范围内。 */
    static final double KEEP = 9.5;
    /** 目标近于此就后撤。 */
    static final double FLEE_AT = 8.0;
    /** 目标远于此就追击。 */
    static final double HOLD_FAR = 11.5;
    /** 这个距离内才投掷 / 才盯人。 */
    static final double ATTACK_RANGE = 12.0;
    static final double ATTACK_SQR = ATTACK_RANGE * ATTACK_RANGE;
    /** 逃跑选点半径 / 纵向范围：小一点，保证退完还在攻击范围内。 */
    static final int FLEE_RADIUS = 4;
    static final int FLEE_Y = 2;
    /** 逃跑 / 追击速度系数。 */
    static final double FLEE_SPEED = 1.5;
    static final double CHASE_SPEED = 1.1;
    /** 决策间隔：0.5 秒（目标接近时反应更快）。 */
    static final int DECIDE = 10;
    /** 投掷间隔。 */
    static final int ATTACK_INTERVAL = 40;

    final GardenWitch witch;

    int decideTimer;
    int attackCooldown;
    /** 当前是不是"后撤"模式。 */
    boolean fleeing;
    /** 上次下发移动指令时威胁的位置，用来判断要不要重算 A*。 */
    Vec3 anchor;

    public KitingGoal(GardenWitch witch) {
        this.witch = witch;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** 让计时器按真实 tick 走（移动本身由每 tick 的 navigation 驱动）。 */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = witch.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        decideTimer = 0;
        attackCooldown = 0;
        fleeing = false;
        anchor = null;
        witch.getNavigation().stop();
    }

    @Override
    public void stop() {
        witch.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = witch.getTarget();
        if (target == null) return;

        double distSqr = witch.distanceToSqr(target);
        double dist = Math.sqrt(distSqr);

        // 只在攻击范围内盯人
        if (dist <= ATTACK_RANGE) {
            witch.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        // 投掷
        if (--attackCooldown <= 0) {
            attackCooldown = ATTACK_INTERVAL;
            if (!witch.isDrinkingPotion() && distSqr <= ATTACK_SQR)
                witch.performRangedAttack(target, 1.0F);
        }

        if (--decideTimer <= 0) {
            decideTimer = DECIDE;
            decide(target, dist);
        }
    }

    private void decide(LivingEntity target, double dist) {
        boolean closing = isClosing(target);
        boolean shouldFlee = dist < FLEE_AT || (dist < HOLD_FAR && closing);
        boolean shouldChase = !shouldFlee && dist > HOLD_FAR;

        if (shouldFlee) {
            // 路径走完、刚切换到逃跑、或威胁已经明显移动 -> 重选逃跑点
            boolean needNew = !fleeing
                    || witch.getNavigation().isDone()
                    || anchor == null
                    || target.position().distanceToSqr(anchor) > 9.0;
            if (needNew) flee(target);
            fleeing = true;
        } else if (shouldChase) {
            boolean needNew = fleeing
                    || witch.getNavigation().isDone()
                    || anchor == null
                    || target.position().distanceToSqr(anchor) > 9.0;
            if (needNew) {
                witch.getNavigation().moveTo(target, CHASE_SPEED);
                anchor = target.position();
            }
            fleeing = false;
        } else {
            witch.getNavigation().stop();   // 距离合适 -> 站桩投掷
            fleeing = false;
            anchor = null;
        }
    }

    /** 选一个远离目标、真实可站立的坐标交给导航。 */
    private void flee(LivingEntity target) {
        Vec3 away = LandRandomPos.getPosAway(witch, FLEE_RADIUS, FLEE_Y, target.position());
        if (away != null) {
            witch.getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
        } else {
            // 兜底：直接朝反方向挪一点
            Vec3 back = witch.position().subtract(target.position());
            if (back.lengthSqr() > 1.0E-6) {
                Vec3 p = witch.position().add(back.normalize().scale(6.0));
                witch.getNavigation().moveTo(p.x, p.y, p.z, FLEE_SPEED);
            }
        }
        anchor = target.position();
    }

    /** 目标是不是正在朝自己移动（速度方向指向自己）。 */
    private boolean isClosing(LivingEntity target) {
        Vec3 vel = target.getDeltaMovement();
        if (vel.horizontalDistanceSqr() < 1.0E-4) return false;
        Vec3 toWitch = new Vec3(
                witch.getX() - target.getX(), 0.0, witch.getZ() - target.getZ());
        return toWitch.dot(new Vec3(vel.x, 0.0, vel.z)) > 0.0;
    }
}
