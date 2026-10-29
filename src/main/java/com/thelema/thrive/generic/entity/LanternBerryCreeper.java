package com.thelema.thrive.generic.entity;

import com.thelema.thrive.generic.block.SunBerry;
import com.thelema.thrive.generic.blockentity.SunBerryBlockEntity;
import com.thelema.thrive.register.BlockRegister;
import com.thelema.thrive.register.EffectRegister;
import com.thelema.thrive.register.FluidRegister;
import com.thelema.thrive.register.ItemRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

public class LanternBerryCreeper extends Creeper implements GeoAnimatable {

    private int grow = 0;
    private int bloomTimer = 6000;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LanternBerryCreeper(EntityType<? extends Creeper> entityType, Level level) {
        super(entityType, level);
    }

    // ==================== GeckoLib ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 3, this::animate));
    }

    private <E extends GeoAnimatable> PlayState animate(AnimationState<E> event) {
        if (event.isMoving()) {
            event.getController().setAnimation(RawAnimation.begin().thenLoop("animation.creeper.walk"));
        } else {
            event.getController().setAnimation(RawAnimation.begin().thenLoop("animation.creeper.idle"));
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object o) {
        return tickCount;
    }

    // ==================== 果汁buff的保护机制 ====================

    @Override
    public void ignite() {
        // 如果在果汁中，无法被点燃（打火石、自身着火等均无效）
        if (hasEffect(EffectRegister.LANTERN_BERRY_JUICE)) {
            return;
        }
        super.ignite();
    }

    @Override
    public int getSwellDir() {
        // 如果在果汁中，强制设定膨胀值为 -1。
        // 原版 tick() 会执行 swell += getSwellDir()，即每 tick 让膨胀值下降。
        if (hasEffect(EffectRegister.LANTERN_BERRY_JUICE)) {
            return -1;
        }
        return super.getSwellDir();
    }

    // ==================== 着火点燃 ====================

    @Override
    public void tick() {
        if (isAlive() && isOnFire()) ignite();
        super.tick();

        if (!level().isClientSide()) {
            if (grow >= 64) {
                // 1. 利用计时器初始值，只在刚达到 64 的第一 tick 清空一次 AI
                if (bloomTimer == 6000) {
                    goalSelector.getAvailableGoals().clear();
                    targetSelector.getAvailableGoals().clear();
                    setTarget(null);
                    getNavigation().stop();
                }

                // 2. 倒计时
                bloomTimer--;

                // 3. 时间到，寻找位置放置
                if (bloomTimer <= 0) {
                    BlockPos current = blockPosition();
                    for (int dy = 0; dy <= 1; dy++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            for (int dz = -2; dz <= 2; dz++) {
                                BlockPos targetPos = current.offset(dx, dy, dz);
                                if (SunBerry.tryPlaceSunBerry(level(), targetPos)) {
                                    discard(); // 放置成功后移除自身
                                    return;
                                }
                            }
                        }
                    }
                    // 如果 5 分钟到了，但周围没有空间，就每 10s 重试
                    bloomTimer = 200;
                }
            }
        }
    }

    // ==================== 火焰死亡也炸 ====================

    @Override
    public void die(DamageSource source) {
        if (isOnFire() || source.is(DamageTypeTags.IS_FIRE)) {
            fireExplode();
        }
        super.die(source);
    }

    // ==================== 爆炸：爆燃 ====================

    /** 由 CreeperMixin 在父类 explodeCreeper 的 HEAD 处调用，替代原版爆炸。 */
    public void fireExplode() {
        if (!level().isClientSide) {
            dead = true;

            gameEvent(GameEvent.ENTITY_DIE);

            discard();

            PrimedLanternBerry.deflagrate(
                    level(),
                    blockPosition(),
                    this,
                    this,
                    20.0F + grow * 3
            );

        }
    }

    // ==================== 死亡掉落 ====================

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        // 保留原版的掉落（比如高压掉落头颅）
        super.dropCustomDeathLoot(level, source, recentlyHit);

        // 判断：如果死因是火伤，或者死的时候处于着火状态，则不掉落
        if (source.is(DamageTypeTags.IS_FIRE) || isOnFire()) {
            return;
        }

        // 否则掉落 1 个灯笼浆果
        // 注意：把 LANTERN_BERRY 替换为你实际注册的物品/方块
        spawnAtLocation(BlockRegister.LANTERN_BERRY.get());
    }

    // ==================== 右键交互 ====================

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        // 判断：必须是高压状态，且手里拿着灯笼浆果
        if (isPowered() && itemstack.is(BlockRegister.LANTERN_BERRY.get().asItem())) {
            // 如果 grow 还没满 64
            if (grow < 64) {
                grow++;

                // 播放进食/使用音效
                playSound(SoundEvents.GENERIC_EAT, 0.5F, random.nextFloat() * 0.1F + 0.9F);

                // 扣除物品（如果不是创造模式）
                if (!player.getAbilities().instabuild) {
                    itemstack.shrink(1);
                }

                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }

        // 如果不是上述情况，走原版逻辑（比如用打火石点燃）
        return super.mobInteract(player, hand);
    }

    // ==================== NBT 存档 ====================

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Grow", grow);
        compound.putInt("BloomTimer", bloomTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        grow = compound.getInt("Grow");
        bloomTimer = compound.getInt("BloomTimer");
    }
}