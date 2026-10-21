package com.thelema.thrive.generic.entity;

import com.thelema.thrive.generic.entity.goal.KitingGoal;
import com.thelema.thrive.generic.entity.goal.NearestThreatTargetGoal;
import com.thelema.thrive.mixin.WitchAccessor;
import com.thelema.thrive.register.PotionRegister;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;


public class GardenWitch extends Witch {

    /**
     * 女巫掉落池：包含所有能被玩家的蜂蜜升级的原版高级药水
     */
    private static final List<Holder<Potion>> DROP_POOL = List.of(
            // 增益类
            Potions.LONG_NIGHT_VISION, Potions.LONG_INVISIBILITY,
            Potions.LONG_LEAPING, Potions.STRONG_LEAPING,
            Potions.LONG_FIRE_RESISTANCE,
            Potions.LONG_SWIFTNESS, Potions.STRONG_SWIFTNESS,
            Potions.LONG_WATER_BREATHING,
            Potions.LONG_REGENERATION, Potions.STRONG_REGENERATION,
            Potions.LONG_STRENGTH, Potions.STRONG_STRENGTH,
            Potions.LONG_SLOW_FALLING,
            Potions.LUCK,

            // 负面/双刃剑类
            Potions.LONG_POISON, Potions.STRONG_POISON,
            Potions.LONG_SLOWNESS, Potions.STRONG_SLOWNESS,
            Potions.LONG_TURTLE_MASTER, Potions.STRONG_TURTLE_MASTER,
            Potions.STRONG_HEALING, Potions.STRONG_HARMING,
            Potions.LONG_WEAKNESS,

            Potions.WEAVING, Potions.OOZING, Potions.INFESTED, Potions.WIND_CHARGED
    );

    public GardenWitch(EntityType<? extends GardenWitch> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();                                                // 原版基础行为 + 目标选择
        this.goalSelector.removeAllGoals(g -> g instanceof RangedAttackGoal); // 远程行为换成新战斗 AI
        this.goalSelector.addGoal(-1, new KitingGoal(this));                  // 风筝：唯一控制 MOVE/LOOK
        this.targetSelector.addGoal(0, new NearestThreatTargetGoal(this, e -> true));    // 先手锁定最近的攻击者
    }

    // ==================== 喝增益药 ====================

    @Override
    public void aiStep() {
        if (!this.level().isClientSide && this.isAlive() && !this.isDrinkingPotion()) {
            Holder<Potion> choice = pickBuffPotion();
            if (choice != null) startDrink(choice);
        }
        super.aiStep();
    }

    private Holder<Potion> pickBuffPotion() {
        // 1 水肺：水下 + 无
        if (this.isEyeInFluid(FluidTags.WATER) && !this.hasEffect(MobEffects.WATER_BREATHING))
            return PotionRegister.SUPER_LONG_WATER_BREATHING;

        // 2 抗火：无
        if (!this.hasEffect(MobEffects.FIRE_RESISTANCE))
            return PotionRegister.SUPER_LONG_FIRE_RESISTANCE;

        // 3 迅捷 III：目标 != null + 无
        if (this.getTarget() != null && !this.hasEffect(MobEffects.MOVEMENT_SPEED))
            return PotionRegister.SUPER_STRONG_SWIFTNESS;

        // 4 隐身：目标 != null + 无
        if (this.getTarget() != null && !this.hasEffect(MobEffects.INVISIBILITY))
            return PotionRegister.SUPER_LONG_INVISIBILITY;

        // 5 治疗：血 < 30%
        if (this.getHealth() < this.getMaxHealth() * 0.3F)
            return PotionRegister.SUPER_STRONG_HEALING;

        // 6 恢复：无
        if (!this.hasEffect(MobEffects.REGENERATION))
            return PotionRegister.SUPER_LONG_REGENERATION;

        return null;
    }

    private void startDrink(Holder<Potion> potion) {
        ItemStack stack;
        if (potion == PotionRegister.SUPER_LONG_INVISIBILITY) {
            stack = new ItemStack(Items.POTION);
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(
                    Optional.empty(), Optional.empty(),
                    List.of(new MobEffectInstance(MobEffects.INVISIBILITY, 38400, 2, false, false))));
        } else {
            stack = PotionContents.createItemStack(Items.POTION, potion);
        }

        this.setItemSlot(EquipmentSlot.MAINHAND, stack);
        ((WitchAccessor) this).thrive$setUsingTime(8);
        this.setUsingItem(true);

        if (!this.isSilent()) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.WITCH_DRINK, this.getSoundSource(),
                    1.0F, 0.8F + this.getRandom().nextFloat() * 0.4F);
        }
    }


    // ==================== 投掷 ====================

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        if (this.isDrinkingPotion()) return;

        ItemStack potion = pickPotion(target);
        if (potion.isEmpty()) return;

        Vec3 dv = target.getDeltaMovement();
        double dx = target.getX() + dv.x - this.getX();
        double dy = target.getEyeY() - 1.1F - this.getY();
        double dz = target.getZ() + dv.z - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        ThrownPotion thrown = new ThrownPotion(this.level(), this);
        thrown.setItem(potion);
        thrown.setXRot(thrown.getXRot() + 20.0F);
        thrown.shoot(dx, dy + dist * 0.2, dz, 0.75F, 2.0F);

        if (!this.isSilent()) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.WITCH_THROW, this.getSoundSource(),
                    1.0F, 0.8F + this.random.nextFloat() * 0.4F);
        }
        this.level().addFreshEntity(thrown);
    }

    private ItemStack pickPotion(LivingEntity target) {
        if (this.random.nextFloat() < 0.5F) {
            Holder<Potion> debuff = pickDebuff(target);
            if (debuff != null)
                return PotionContents.createItemStack(Items.SPLASH_POTION, debuff);
        }

        boolean lingering = this.random.nextFloat() < 0.5F;
        return PotionContents.createItemStack(
                lingering ? Items.LINGERING_POTION : Items.SPLASH_POTION,
                PotionRegister.SUPER_STRONG_HARMING);
    }

    @Nullable
    private Holder<Potion> pickDebuff(LivingEntity target) {
        if (distanceToSqr(target) > 64.0 && !target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))
            return PotionRegister.SUPER_LONG_SLOWNESS;
        if (!target.hasEffect(MobEffects.POISON))
            return PotionRegister.SUPER_LONG_POISON;
        if (!target.hasEffect(MobEffects.WEAKNESS))
            return PotionRegister.SUPER_LONG_WEAKNESS;
        return null;
    }

    /**
     * 覆写死亡掉落：女巫随机掉落次高级药水。
     * <p>
     * 80% 概率掉落滞留药水，10% 喷溅，10% 普通。
     * 掉落种类从 DROP_POOL 中随机抽取，用于配合玩家的蜂蜜进行再升级。
     */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);

        // 1. 决定药水形态
        float roll = this.random.nextFloat();
        Item potionItem;
        if (roll < 0.8F) {
            potionItem = Items.LINGERING_POTION; // 80%
        } else if (roll < 0.9F) {
            potionItem = Items.SPLASH_POTION;    // 10%
        } else {
            potionItem = Items.POTION;           // 10%
        }

        // 2. 从池子随机抽一种药水并生成物品
        Holder<Potion> potion = DROP_POOL.get(this.random.nextInt(DROP_POOL.size()));
        ItemStack stack = PotionContents.createItemStack(potionItem, potion);

        // 3. 掉落
        this.spawnAtLocation(stack);
    }

}