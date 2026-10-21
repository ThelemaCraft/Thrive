package com.thelema.thrive.generic.entity.goal;

import java.util.EnumSet;

import com.thelema.thrive.generic.entity.GardenPillager;
import com.thelema.thrive.generic.entity.GardenWitch;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * 花园掠夺者的"找女巫补箭"Goal。
 *
 * <p>箭打空、且附近有花园女巫时启动：走到女巫身边，站定 {@link #TRADE_DELAY} tick，
 * 播一声村民交易音效，然后往自己容器里放一支随机种类的药水箭。
 *
 * <p>不需要女巫那边配合——这是单方面的行为，女巫完全不知道被"交易"了。
 *
 * <p>和 {@code RangedCrossbowAttackGoal} 不冲突：那个 Goal 要求有攻击目标，
 * 而找女巫时掠夺者通常没有目标；两者也都只是抢 MOVE / LOOK。
 * 一旦锁定敌人（{@code canContinueToUse} 判到 target != null）就立刻放弃补箭去打人。
 */
public class RestockFromWitchGoal extends Goal {

    /** 找女巫的半径。 */
    private static final double SEARCH_RANGE = 16.0;
    /** 走到这么近就算到位。 */
    static final double ARRIVE_DIST = 2.5;
    /** 到位后站定这么久再"交易"，别一贴脸就响。 */
    static final int TRADE_DELAY = 20;
    /** 追击速度。 */
    static final double SPEED = 1.0;
    /** 成交时撒几颗绿色粒子（原版村民是 5）。 */
    static final int TRADE_PARTICLES = 5;

    private final GardenPillager pillager;
    /** 当前锁定的女巫；为 null 即没找到。 */
    @Nullable
    private GardenWitch witch;
    /** 已经在女巫身边待了多久。 */
    private int tradeTimer;
    /** 一次交易补几支箭，由实体构造时传入。 */
    private final int arrowCount;

    public RestockFromWitchGoal(GardenPillager pillager, int arrowCount) {
        this.pillager = pillager;
        this.arrowCount = Math.max(1, arrowCount);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** 交易倒计时要按真实 tick 走。 */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (!needsPotionArrow()) return false;
        if (pillager.getTarget() != null) return false;       // 有敌人就先打人
        witch = findNearbyWitch(SEARCH_RANGE);
        return witch != null;
    }

    @Override
    public boolean canContinueToUse() {
        // 箭补上了 / 女巫没了 / 敌人来了 -> 退出
        if (!needsPotionArrow()) return false;
        if (pillager.getTarget() != null) return false;
        if (witch == null || !witch.isAlive()) return false;
        // 女巫跑太远就重新找（走散的情况）
        return pillager.distanceToSqr(witch) <= SEARCH_RANGE * SEARCH_RANGE * 2.25;
    }

    @Override
    public void start() {
        tradeTimer = 0;
        moveToWitch();
    }

    @Override
    public void stop() {
        witch = null;
        tradeTimer = 0;
        pillager.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (witch == null) return;

        pillager.getLookControl().setLookAt(witch, 30.0F, 30.0F);

        if (pillager.distanceToSqr(witch) > ARRIVE_DIST * ARRIVE_DIST) {
            tradeTimer = 0;
            // 路径走完了还没到（被挡住 / 走偏了）就重新寻路
            if (pillager.getNavigation().isDone()) {
                moveToWitch();
            }
            return;
        }

        // 到位了：站定，倒计时结束就成交
        pillager.getNavigation().stop();
        if (++tradeTimer >= TRADE_DELAY) {
            tradeTimer = 0;
            if (!pillager.isSilent()) {
                pillager.level().playSound(null,
                        pillager.getX(), pillager.getY(), pillager.getZ(),
                        SoundEvents.VILLAGER_YES, pillager.getSoundSource(),
                        1.0F, 0.8F + pillager.getRandom().nextFloat() * 0.4F);
            }
            restockArrows();
            spawnTradeParticles();
        }
    }

    /**
     * 绿色交易粒子。照抄原版村民成交的表现
     * （{@code AbstractVillager#addParticlesAroundSelf} 的 HAPPY_VILLAGER）。
     */
    private void spawnTradeParticles() {
        for (int i = 0; i < TRADE_PARTICLES; i++) {
            double dx = pillager.getRandom().nextGaussian() * 0.02;
            double dy = pillager.getRandom().nextGaussian() * 0.02;
            double dz = pillager.getRandom().nextGaussian() * 0.02;
            pillager.level().addParticle(ParticleTypes.HAPPY_VILLAGER,
                    pillager.getRandomX(1.0),
                    pillager.getRandomY() + 1.0,
                    pillager.getRandomZ(1.0),
                    dx, dy, dz);
        }
    }

    private void moveToWitch() {
        if (witch == null) return;
        pillager.getNavigation().moveTo(witch, SPEED);
    }

    // ==================== 药水箭相关====================

    /** 容器里还有能被弩射出去的箭吗。 */
    private boolean needsPotionArrow() {
        SimpleContainer inv = pillager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && CrossbowItem.ARROW_ONLY.test(s)) {
                return false;
            }
        }
        return true;
    }

    /** 找最近的花园女巫。 */
    @Nullable
    private GardenWitch findNearbyWitch(double range) {
        AABB box = pillager.getBoundingBox().inflate(range);
        GardenWitch best = null;
        double bestSqr = Double.MAX_VALUE;
        for (GardenWitch w : pillager.level().getEntitiesOfClass(GardenWitch.class, box)) {
            if (!w.isAlive()) continue;
            double d = pillager.distanceToSqr(w);
            if (d < bestSqr) {
                bestSqr = d;
                best = w;
            }
        }
        return best;
    }

    /** 交易成交：随机抽 {@link #arrowCount} 支药水箭放进容器，每支独立随机。 */
    private void restockArrows() {
        GardenPillager.ArrowChoice[] choices = GardenPillager.ArrowChoice.values();
        for (int i = 0; i < arrowCount; i++) {
            GardenPillager.ArrowChoice choice = choices[pillager.getRandom().nextInt(choices.length)];
            pillager.getInventory().addItem(choice.createArrow());
        }
    }
}