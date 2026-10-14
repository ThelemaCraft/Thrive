package com.thelema.thrive.generic.entity.goal;

import com.thelema.thrive.generic.blockentity.GardenHiveBlockEntity;
import com.thelema.thrive.generic.entity.GardenBee;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/** 夜/雨归巢。2 格内卸粉、进巢。满员时在家附近徘徊。 */
public class ReturnHomeGoal extends Goal {

    static final double ENTER_DIST = 2.0;
    static final int WANDER_RADIUS = 3;
    static final int WANDER_COOLDOWN = 40;

    final GardenBee bee;
    @Nullable private Vec3 wanderTarget;
    private int wanderCooldown;

    public ReturnHomeGoal(GardenBee bee) {
        this.bee = bee;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (bee.home == null) return false;
        if (!(bee.level().getBlockEntity(bee.home) instanceof GardenHiveBlockEntity)) {
            bee.home = null;
            return false;
        }
        return bee.level().isNight() || bee.level().isRaining();
    }

    @Override
    public boolean canContinueToUse() {
        if (bee.home == null) return false;
        if (!(bee.level().getBlockEntity(bee.home) instanceof GardenHiveBlockEntity)) {
            bee.home = null;
            return false;
        }
        return bee.level().isNight() || bee.level().isRaining();
    }

    @Override
    public void start() {
        wanderTarget = null;
        wanderCooldown = 0;
    }

    @Override
    public void stop() {
        wanderTarget = null;
        wanderCooldown = 0;
        bee.getNavigation().stop();
    }

    @Override
    public void tick() {
        BlockPos home = bee.home;
        if (home == null) return;

        if (!(bee.level().getBlockEntity(home) instanceof GardenHiveBlockEntity hive)) {
            bee.home = null;
            return;
        }

        // 2 格内
        if (home.closerToCenterThan(bee.position(), ENTER_DIST)) {

            // 卸粉（有空槽就卸）
            if (bee.carriedPollen != null) {
                for (int i = 0; i < 3; i++) {
                    if (hive.nectar[i] == null) {
                        hive.nectar[i] = bee.carriedPollen;
                        bee.carriedPollen = null;
                        hive.setChanged();
                        bee.level().sendBlockUpdated(home, hive.getBlockState(),
                                hive.getBlockState(), 3);
                        break;
                    }
                }
            }

            // 有位置 → 进巢消失
            if (hive.occupants.size() < GardenHiveBlockEntity.MAX_BEES) {
                hive.occupants.add(new GardenHiveBlockEntity.BeeRecord(
                        bee.getUUID(), bee.favoriteFlower, bee.carriedPollen, bee.getOwnerUUID()));
                hive.setChanged();
                bee.level().sendBlockUpdated(home, hive.getBlockState(),
                        hive.getBlockState(), 3);
                bee.discard();
                return;
            }

            // 满员 → 家附近徘徊，不丢 home
            wander(home);
            return;
        }

        // 远处 → 飞向巢
        if (bee.getNavigation().isInProgress()) return;

        Path path = bee.getNavigation().createPath(home.getX(), home.getY(), home.getZ(), 2);
        if (path == null) {
            wander(home);   // ← 改：不删 home，附近徘徊
            return;
        }
        bee.getNavigation().moveTo(path, 1.0);
    }

    /** 满员时在家附近绕圈。 */
    private void wander(BlockPos home) {
        if (wanderCooldown > 0) {
            wanderCooldown--;
        } else if (wanderTarget == null || bee.position().distanceToSqr(wanderTarget) < 1.0) {
            double x = home.getX() + 0.5 + (bee.getRandom().nextDouble() * 2 - 1) * WANDER_RADIUS;
            double y = home.getY() + 1.0 + (bee.getRandom().nextDouble() * 2 - 1);
            double z = home.getZ() + 0.5 + (bee.getRandom().nextDouble() * 2 - 1) * WANDER_RADIUS;
            wanderTarget = new Vec3(x, y, z);
            wanderCooldown = WANDER_COOLDOWN;
        }

        if (wanderTarget != null) {
            bee.getMoveControl().setWantedPosition(
                    wanderTarget.x, wanderTarget.y, wanderTarget.z, 0.4);
        }
    }
}