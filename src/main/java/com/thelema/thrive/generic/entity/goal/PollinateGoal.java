package com.thelema.thrive.generic.entity.goal;

import com.thelema.thrive.generic.entity.GardenBee;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;

/** 照搬原版 BeePollinateGoal。 */
public class PollinateGoal extends Goal {

    static final int MIN_POLLINATION_TICKS = 400;
    static final int MAX_POLLINATING_TICKS = 600;
    static final double ARRIVAL_THRESHOLD = 0.1;
    static final int POSITION_CHANGE_CHANCE = 25;
    static final float SPEED_MODIFIER = 0.35F;
    static final float HOVER_HEIGHT_WITHIN_FLOWER = 0.6F;
    static final float HOVER_POS_OFFSET = 0.33333334F;

    final GardenBee bee;

    @Nullable BlockPos flowerPos;
    private int successfulPollinatingTicks;
    private int lastSoundPlayedTick;
    private boolean pollinating;
    @Nullable private Vec3 hoverPos;
    private int pollinatingTicks;

    public PollinateGoal(GardenBee bee) {
        this.bee = bee;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (bee.carriedPollen != null) return false;
        if (bee.getTarget() != null) return false;
        if (bee.level().isRaining()) return false;

        Optional<BlockPos> optional = findNearbyFlower();
        if (optional.isPresent()) {
            flowerPos = optional.get();
            bee.getNavigation().moveTo(
                    (double) flowerPos.getX() + 0.5,
                    (double) flowerPos.getY() + 0.5,
                    (double) flowerPos.getZ() + 0.5,
                    1.2F);
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (!pollinating) return false;
        if (flowerPos == null) return false;
        if (bee.getTarget() != null) return false;
        if (bee.level().isRaining()) return false;
        if (hasPollinatedLongEnough()) return bee.getRandom().nextFloat() < 0.2F;
        if (bee.tickCount % 20 == 0 && !isFlowerValid(flowerPos)) {
            flowerPos = null;
            return false;
        }
        return true;
    }

    private boolean hasPollinatedLongEnough() {
        return successfulPollinatingTicks > MIN_POLLINATION_TICKS;
    }

    @Override
    public void start() {
        successfulPollinatingTicks = 0;
        pollinatingTicks = 0;
        lastSoundPlayedTick = 0;
        pollinating = true;
    }

    @Override
    public void stop() {
        // 采够了就交货
        if (hasPollinatedLongEnough() && flowerPos != null) {
            BlockState s = bee.level().getBlockState(flowerPos);
            Item flowerItem = s.getBlock().asItem();
            bee.carriedPollen = flowerItem == Items.AIR ? bee.favoriteFlower : flowerItem;
        }
        pollinating = false;
        bee.getNavigation().stop();
        flowerPos = null;
        hoverPos = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (flowerPos == null) return;

        pollinatingTicks++;
        if (pollinatingTicks > MAX_POLLINATING_TICKS) {
            flowerPos = null;
            return;
        }

        Vec3 center = Vec3.atBottomCenterOf(flowerPos).add(0, HOVER_HEIGHT_WITHIN_FLOWER, 0);

        // 距离花 > 1 格：飞过去
        if (center.distanceTo(bee.position()) > 1.0) {
            hoverPos = center;
            setWantedPos();
            return;
        }

        // 接近花
        if (hoverPos == null) hoverPos = center;

        boolean atHover = bee.position().distanceTo(hoverPos) <= ARRIVAL_THRESHOLD;
        boolean setPos = true;

        if (atHover) {
            if (bee.getRandom().nextInt(POSITION_CHANGE_CHANCE) == 0) {
                hoverPos = new Vec3(
                        center.x + getOffset(),
                        center.y,
                        center.z + getOffset());
                bee.getNavigation().stop();
            } else {
                setPos = false;
            }
            bee.getLookControl().setLookAt(center.x, center.y, center.z);
        }

        if (setPos) setWantedPos();

        // ← 关键：不管 atHover，接近花就计数
        successfulPollinatingTicks++;

        if (bee.getRandom().nextFloat() < 0.05F
                && successfulPollinatingTicks > lastSoundPlayedTick + 60) {
            lastSoundPlayedTick = successfulPollinatingTicks;
            bee.playSound(SoundEvents.BEE_POLLINATE, 1.0F, 1.0F);
        }
    }

    private void setWantedPos() {
        if (hoverPos == null) return;
        bee.getMoveControl().setWantedPosition(
                hoverPos.x, hoverPos.y, hoverPos.z, SPEED_MODIFIER);
    }

    private float getOffset() {
        return (bee.getRandom().nextFloat() * 2.0F - 1.0F) * HOVER_POS_OFFSET;
    }

    private Optional<BlockPos> findNearbyFlower() {
        BlockPos origin = bee.blockPosition();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDist = 25.0;
        int r = 5;
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!bee.level().isLoaded(p)) continue;
                    BlockState s = bee.level().getBlockState(p);
                    if (!s.is(BlockTags.FLOWERS)) continue;
                    if (s.getBlock().asItem() != bee.favoriteFlower) continue;   // ← 加这行
                    double d = p.distSqr(origin);
                    if (d < bestDist) { bestDist = d; best = p.immutable(); }
                }
        return Optional.ofNullable(best);
    }

    private boolean isFlowerValid(BlockPos pos) {
        return bee.level().isLoaded(pos) && bee.level().getBlockState(pos).is(BlockTags.FLOWERS);
    }
}