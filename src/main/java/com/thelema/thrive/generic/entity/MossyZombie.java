package com.thelema.thrive.generic.entity;

import com.thelema.thrive.generic.entity.goal.PickupJuiceGoal;
import com.thelema.thrive.register.EffectRegister;
import com.thelema.thrive.register.FluidRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

import java.util.ArrayList;
import java.util.List;

public class MossyZombie extends Zombie {

    // ==================== 常量 ====================

    private static final int REGEN_INTERVAL = 20;
    private static final int REGEN_DURATION = 40;
    private static final int REGEN_AMP = 3;         // IV
    private static final int REGEN_AMP_BUFFED = 7;  // VIII

    private static final int JUICE_INTERVAL = 20;
    private static final int JUICE_COOLDOWN = 20;
    private static final int PICKUP_H = 8;

    private static final float ELITE_CHANCE = 1 / 6f;      // 16.6% 精英
    private static final float ARMOR_CHANCE = 0.7f;
    private static final float OFFHAND_CHANCE = 0.6f;
    private static final float MAINHAND_CHANCE = 0.7f;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /**
     * [档次][部位]：皮革、铁、锁链、钻石、下界合金。
     */
    private static final Item[][] ARMOR = {
            {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
            {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
            {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
            {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
            {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS},
    };

    private static final Item[] TIER_STONE = {Items.STONE_SWORD, Items.STONE_AXE, Items.STONE_HOE};
    private static final Item[] TIER_IRON = {Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_HOE};
    private static final Item[] TIER_DIAMOND = {Items.DIAMOND_SWORD, Items.DIAMOND_AXE};

    // ==================== 字段 ====================

    /**
     * 放出浆果汁后的冷却。> 0 时不许装桶。
     */
    public int juiceCooldown;

    /**
     * 最近一次放出源头的位置。找回时优先查这里。
     */
    public BlockPos lastJuicePos;

    // ==================== 构造与属性 ====================

    public MossyZombie(EntityType<? extends MossyZombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(3, new PickupJuiceGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 30.0);
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    // ==================== tick ====================

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            tickRegen();
            tickBucket();
        }
    }

    private void tickRegen() {
        if (!isAlive()) return;

        if (getRemainingFireTicks() > 0) {
            removeEffect(MobEffects.REGENERATION);
            return;
        }

        if (tickCount % REGEN_INTERVAL == 0) {
            int amp = hasEffect(EffectRegister.LANTERN_BERRY_JUICE)
                    ? REGEN_AMP_BUFFED : REGEN_AMP;
            addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_DURATION, amp, true, true));
        }
    }

    private void tickBucket() {
        // 空桶：冷却后扫源头
        if (getOffhandItem().is(Items.BUCKET)) {
            if (juiceCooldown > 0) juiceCooldown--;
            else tryPickupJuice();
            return;
        }

        // 持桶：定期放置
        if (tickCount % JUICE_INTERVAL == 0
                && getOffhandItem().is(FluidRegister.LANTERN_BERRY_JUICE_BUCKET.get())
                && !isInWater()
                && !isInLava()) {
            if (tryPlaceJuice()) {
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BUCKET));
                juiceCooldown = JUICE_COOLDOWN;
            }
        }
    }

    // ==================== 装备生成 ====================

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        if (random.nextFloat() < ELITE_CHANCE) {
            rollMainHand(random);
            rollOffhand(random);
            rollArmor(random);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0f);
    }

    private void rollMainHand(RandomSource random) {
        if (random.nextFloat() >= MAINHAND_CHANCE) return;
        int roll = random.nextInt(100);
        Item[] pool = roll < 40 ? TIER_STONE : roll < 80 ? TIER_IRON : TIER_DIAMOND;
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(pool[random.nextInt(pool.length)]));
    }

    private void rollOffhand(RandomSource random) {
        if (random.nextFloat() >= OFFHAND_CHANCE) return;
        setItemSlot(EquipmentSlot.OFFHAND,
                new ItemStack(FluidRegister.LANTERN_BERRY_JUICE_BUCKET.get()));
    }

    private void rollArmor(RandomSource random) {
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            if (random.nextFloat() < ARMOR_CHANCE) {
                setItemSlot(ARMOR_SLOTS[i], new ItemStack(pickArmor(random, i)));
            }
        }
    }

    /**
     * 护甲池：皮革/铁/锁链各 10，钻石 2，下界合金 1（总 33）。
     */
    private static Item pickArmor(RandomSource random, int slotIdx) {
        int roll = random.nextInt(33);
        int tier = roll < 10 ? 0 : roll < 20 ? 1 : roll < 30 ? 2 : roll < 32 ? 3 : 4;
        return ARMOR[tier][slotIdx];
    }

    // ==================== 经验 ====================

    public int computeXp() {
        int xp = 20 + armorXp() * 2 + mainHandXp() * 2;
        if (getOffhandItem().is(FluidRegister.LANTERN_BERRY_JUICE_BUCKET.get())) xp *= 2;
        return xp;
    }

    private int armorXp() {
        int sum = 0;
        for (EquipmentSlot s : ARMOR_SLOTS) sum += tierXp(getItemBySlot(s));
        return sum;
    }

    private int mainHandXp() {
        return tierXp(getMainHandItem());
    }

    /**
     * 装备档位经验：皮革 1、铁 2、锁链 3、钻石 4、下界合金 5。
     */
    private static int tierXp(ItemStack s) {
        if (s.isEmpty()) return 0;
        Item i = s.getItem();
        if (i == Items.NETHERITE_HELMET || i == Items.NETHERITE_CHESTPLATE
                || i == Items.NETHERITE_LEGGINGS || i == Items.NETHERITE_BOOTS) return 5;
        if (i == Items.DIAMOND_HELMET || i == Items.DIAMOND_CHESTPLATE
                || i == Items.DIAMOND_LEGGINGS || i == Items.DIAMOND_BOOTS
                || i == Items.DIAMOND_SWORD || i == Items.DIAMOND_AXE) return 4;
        if (i == Items.CHAINMAIL_HELMET || i == Items.CHAINMAIL_CHESTPLATE
                || i == Items.CHAINMAIL_LEGGINGS || i == Items.CHAINMAIL_BOOTS) return 3;
        if (i == Items.IRON_HELMET || i == Items.IRON_CHESTPLATE
                || i == Items.IRON_LEGGINGS || i == Items.IRON_BOOTS
                || i == Items.IRON_SWORD || i == Items.IRON_AXE || i == Items.IRON_HOE) return 2;
        if (i == Items.LEATHER_HELMET || i == Items.LEATHER_CHESTPLATE
                || i == Items.LEATHER_LEGGINGS || i == Items.LEATHER_BOOTS
                || i == Items.STONE_SWORD || i == Items.STONE_AXE || i == Items.STONE_HOE) return 1;
        return 0;
    }

    // ==================== 浆果汁桶 ====================

    private boolean tryPlaceJuice() {
        List<LivingEntity> targets = new ArrayList<>();
        targets.add(this);
        targets.addAll(level().getEntitiesOfClass(Monster.class,
                getBoundingBox().inflate(PICKUP_H), e -> e != this && e.isAlive()));

        for (LivingEntity target : targets) {
            if (target.hasEffect(EffectRegister.LANTERN_BERRY_JUICE)) continue;
            BlockPos at = target.blockPosition();
            if (!level().getBlockState(at.below()).isSolid()) continue;
            if (!level().getBlockState(at).canBeReplaced()) continue;

            FluidState still = FluidRegister.LANTERN_BERRY_JUICE_STILL.get().defaultFluidState();
            level().setBlock(at, still.createLegacyBlock(), 3);
            lastJuicePos = at;
            return true;
        }
        return false;
    }

    private void tryPickupJuice() {
        if (lastJuicePos != null && pickupJuiceAt(lastJuicePos)) return;
        // 近距离直接装，避免和 Goal 抢
        BlockPos origin = blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(
                origin.offset(-2, -2, -2), origin.offset(2, 2, 2))) {
            if (pickupJuiceAt(p)) return;
        }
    }

    public boolean pickupJuiceAt(BlockPos p) {
        FluidState fs = level().getFluidState(p);
        if (fs.getType() != FluidRegister.LANTERN_BERRY_JUICE_STILL.get() || !fs.isSource()) return false;
        level().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        setItemSlot(EquipmentSlot.OFFHAND,
                new ItemStack(FluidRegister.LANTERN_BERRY_JUICE_BUCKET.get()));
        lastJuicePos = null;
        return true;
    }

    @SubscribeEvent
    public static void onXpDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof MossyZombie zombie) {
            event.setDroppedExperience(zombie.computeXp());
        }
    }
}