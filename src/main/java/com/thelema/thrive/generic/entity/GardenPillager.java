package com.thelema.thrive.generic.entity;

import com.thelema.thrive.generic.entity.goal.RestockFromWitchGoal;
import com.thelema.thrive.register.PotionRegister;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
/** 花园掠夺者。原版掠夺者模板，携带少量药水箭。 */
public class GardenPillager extends Pillager {

    /** 可能的药水箭种类；每次补箭时随机抽一种。 */
    public enum ArrowChoice {
        SLOWNESS(PotionRegister.SUPER_LONG_SLOWNESS),   // 缓慢 I，超长
        POISON(PotionRegister.SUPER_LONG_POISON),       // 剧毒 I，超长
        WEAKNESS(PotionRegister.SUPER_LONG_WEAKNESS),   // 虚弱 I，超长
        HARMING(PotionRegister.SUPER_STRONG_HARMING);   // 瞬发伤害 III

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
        this.goalSelector.addGoal(1, new RestockFromWitchGoal(this, arrowRestockAmount()));
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

    /** 一次找女巫交易补几支箭。普通版 1 支。 */
    protected int arrowRestockAmount() {
        return 1;
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
}