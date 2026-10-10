package com.thelema.thrive.generic.item;

import com.thelema.thrive.generic.entity.GrassLandSheep;
import com.thelema.thrive.register.EffectRegister;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public class HungerSnack extends Item {

    public HungerSnack() {
        super(new Item.Properties()
                .food(new FoodProperties.Builder()
                        .nutrition(5)
                        .saturationModifier(0.6F)
                        .effect(() -> new MobEffectInstance(EffectRegister.SUPER_HUNGER, 2400, 0), 1.0F)
                        .build()));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!(target instanceof GrassLandSheep sheep)) {
            return InteractionResult.PASS;
        }

        if (!sheep.isAlive()) {
            return InteractionResult.PASS;
        }

        if (!player.level().isClientSide) {
            // 触发羊的超级饥饿效果，激活 DevourGrassGoal
            sheep.addEffect(new MobEffectInstance(EffectRegister.SUPER_HUNGER, 2400, 0));

            // 消耗物品
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            // 普通吃东西音效
            sheep.level().playSound(sheep, sheep.getOnPos(), SoundEvents.CAT_EAT, SoundSource.AMBIENT, 1.0f, 1.0f);

        }

        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
}
