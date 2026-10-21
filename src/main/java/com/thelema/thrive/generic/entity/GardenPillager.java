package com.thelema.thrive.generic.entity;

import com.thelema.thrive.register.PotionRegister;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/** 花园掠夺者。原版掠夺者模板，携带少量药水箭。 */
public class GardenPillager extends Pillager {

    /** 可能的药水箭种类；每次补箭时随机抽一种。 */
    public enum ArrowChoice {
        SLOWNESS(PotionRegister.SUPER_LONG_SLOWNESS),   // 缓慢 I，超长
        POISON(PotionRegister.SUPER_LONG_POISON),       // 剧毒 I，超长
        WEAKNESS(PotionRegister.SUPER_LONG_WEAKNESS),   // 虚弱 I，超长
        HARMING(Potions.STRONG_HARMING);   // 瞬发伤害 II

        private final Holder<Potion> potion;

        ArrowChoice(Holder<Potion> potion) {
            this.potion = potion;
        }

        /** 造一支这种药水箭。 */
        public ItemStack createArrow() {
            ItemStack stack = new ItemStack(Items.TIPPED_ARROW, 1);
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(this.potion));
            return stack;
        }
    }

    public GardenPillager(EntityType<? extends GardenPillager> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Pillager.createAttributes()
                .add(Attributes.MAX_HEALTH, 30.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // 优先级 1：有女巫就走过去交易（高于原版的 HoldGround(2) / 弩(3)）
        this.goalSelector.addGoal(1, new RestockFromWitchGoal());
    }

    /** 生成时自带一支随机药水箭。 */
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        this.getInventory().addItem(randomArrow());
        return data;
    }

    // ==================== 行为 ====================

    /** 首先选容器的箭类物品，否则返回一个保底普通箭*/
    @Override
    public ItemStack getProjectile(ItemStack weaponStack) {
        if (weaponStack.is(Items.CROSSBOW)) {
            SimpleContainer inv = this.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && CrossbowItem.ARROW_ONLY.test(s)) {
                    return s;   // 让 draw 去 split(1)
                }
            }
            // 没药水箭了就射普通箭兜底；想要药水箭得去找花园女巫换
            return new ItemStack(Items.ARROW);
        }
        return super.getProjectile(weaponStack);
    }

    private ItemStack randomArrow() {
        ArrowChoice[] choices = ArrowChoice.values();
        return choices[this.random.nextInt(choices.length)].createArrow();
    }

    /**
     * 覆写死亡掉落：掉落未射出的药水箭。
     */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);

        // 获取掠夺者的物品栏（原版 5 格空间）
        SimpleContainer inv = this.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                // 掉落物品
                this.spawnAtLocation(stack);
                // 清空槽位，防止重复掉落
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    // ==================== 内置 Goal ====================

    /**
     * 找花园女巫补箭：箭打空、且附近有女巫时走过去，站定 20 tick 后补一支随机药水箭。
     * 单方面行为，女巫不参与。
     */
    class RestockFromWitchGoal extends Goal {

        static final double SEARCH_RANGE = 32.0;
        static final double ARRIVE_DIST = 2.5;
        static final int TRADE_DELAY = 20;
        static final double SPEED = 1.0;
        static final int TRADE_PARTICLES = 5;

        @Nullable private GardenWitch witch;
        private int tradeTimer;

        RestockFromWitchGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            // 背包没箭 + 弩上没箭 + 找到了女巫
            boolean noArrows = !existArrowInCont() && !existArrowInCrossBow();
            witch = findNearbyWitch(SEARCH_RANGE);
            return noArrows && witch != null;
        }

        @Override
        public boolean canContinueToUse() {
            // 有箭了，结束补货
            if (existArrowInCont() || existArrowInCrossBow()) return false;
            // 女巫没了
            if (witch == null || !witch.isAlive()) return false;
            // 太远了
            return GardenPillager.this.distanceToSqr(witch) <= SEARCH_RANGE * SEARCH_RANGE * 2.25;
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
            GardenPillager.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (witch == null) return;

            GardenPillager.this.getLookControl().setLookAt(witch, 30.0F, 30.0F);

            if (GardenPillager.this.distanceToSqr(witch) > ARRIVE_DIST * ARRIVE_DIST) {
                tradeTimer = 0;
                if (GardenPillager.this.getNavigation().isDone()) {
                    moveToWitch();
                }
                return;
            }

            GardenPillager.this.getNavigation().stop();
            if (++tradeTimer >= TRADE_DELAY) {
                tradeTimer = 0;
                if (!GardenPillager.this.isSilent()) {
                    GardenPillager.this.level().playSound(null,
                            GardenPillager.this.getX(), GardenPillager.this.getY(), GardenPillager.this.getZ(),
                            SoundEvents.VILLAGER_YES, GardenPillager.this.getSoundSource(),
                            1.0F, 0.8F + GardenPillager.this.getRandom().nextFloat() * 0.4F);
                }
                restockArrow();
                spawnTradeParticles();
            }
        }

        private void spawnTradeParticles() {
            for (int i = 0; i < TRADE_PARTICLES; i++) {
                double dx = GardenPillager.this.getRandom().nextGaussian() * 0.02;
                double dy = GardenPillager.this.getRandom().nextGaussian() * 0.02;
                double dz = GardenPillager.this.getRandom().nextGaussian() * 0.02;
                GardenPillager.this.level().addParticle(ParticleTypes.HAPPY_VILLAGER,
                        GardenPillager.this.getRandomX(1.0),
                        GardenPillager.this.getRandomY() + 1.0,
                        GardenPillager.this.getRandomZ(1.0),
                        dx, dy, dz);
            }
        }

        private void moveToWitch() {
            if (witch != null) GardenPillager.this.getNavigation().moveTo(witch, SPEED);
        }

        /** 容器里还有能被弩射出去的箭吗。 */
        private boolean existArrowInCont() {
            SimpleContainer inv = GardenPillager.this.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && CrossbowItem.ARROW_ONLY.test(s)) {
                    return true; // 有箭
                }
            }
            return false; // 没箭
        }

        /** 弩上是否已经装填了箭矢 */
        private boolean existArrowInCrossBow() {
            ItemStack mainHand = GardenPillager.this.getMainHandItem();
            if (mainHand.is(Items.CROSSBOW)) {
                var charged = mainHand.get(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES);
                return charged != null && !charged.isEmpty();
            }

            ItemStack offHand = GardenPillager.this.getOffhandItem();
            if (offHand.is(Items.CROSSBOW)) {
                var charged = offHand.get(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES);
                return charged != null && !charged.isEmpty();
            }

            return false;
        }

        @Nullable
        private GardenWitch findNearbyWitch(double range) {
            AABB box = GardenPillager.this.getBoundingBox().inflate(range);
            GardenWitch best = null;
            double bestSqr = Double.MAX_VALUE;
            for (GardenWitch w : GardenPillager.this.level().getEntitiesOfClass(GardenWitch.class, box)) {
                if (!w.isAlive()) continue;
                double d = GardenPillager.this.distanceToSqr(w);
                if (d < bestSqr) {
                    bestSqr = d;
                    best = w;
                }
            }
            return best;
        }

        /** 固定补一支随机药水箭。 */
        private void restockArrow() {
            GardenPillager.this.getInventory().addItem(GardenPillager.this.randomArrow());
        }
    }
}
