package com.thelema.thrive.generic.entity.goal;

import com.thelema.thrive.generic.entity.MossyZombie;
import com.thelema.thrive.register.FluidRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FluidState;

/**
 * 空桶苔藓僵尸的寻源 Goal。
 * 附近有浆果汁源头时，寻路过去装桶。
 */
public class PickupJuiceGoal extends Goal {

    private static final int SEARCH_H = 16;
    private static final int SEARCH_V = 4;
    private static final double ARRIVE_DIST = 2.0;

    private final MossyZombie zombie;
    private BlockPos target;

    public PickupJuiceGoal(MossyZombie zombie) {
        this.zombie = zombie;
    }

    @Override
    public boolean canUse() {
        if (!zombie.getOffhandItem().is(Items.BUCKET)) return false;
        if (zombie.juiceCooldown > 0) return false;
        target = findJuice();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null
                && zombie.getOffhandItem().is(Items.BUCKET)
                && zombie.blockPosition().closerThan(target, SEARCH_H);
    }

    @Override
    public void start() {
        moveToTarget();
    }

    @Override
    public void tick() {
        if (target == null) return;

        if (zombie.blockPosition().closerThan(target, ARRIVE_DIST)) {
            zombie.pickupJuiceAt(target);
            target = null;
            return;
        }

        // 每 20 tick 重寻路，避免卡住
        if (zombie.tickCount % 20 == 0) {
            moveToTarget();
        }
    }

    @Override
    public void stop() {
        target = null;
        zombie.getNavigation().stop();
    }

    private void moveToTarget() {
        zombie.getNavigation().moveTo(
                target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
    }

    private BlockPos findJuice() {
        BlockPos origin = zombie.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(
                origin.offset(-SEARCH_H, -SEARCH_V, -SEARCH_H),
                origin.offset(SEARCH_H, SEARCH_V, SEARCH_H))) {
            FluidState fs = zombie.level().getFluidState(p);
            if (fs.getType() == FluidRegister.LANTERN_BERRY_JUICE_STILL.get() && fs.isSource()) {
                return p.immutable();
            }
        }
        return null;
    }
}