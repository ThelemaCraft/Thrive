package com.thelema.thrive.generic.item;

import com.thelema.thrive.register.EffectRegister;
import com.thelema.thrive.register.FluidRegister;
import com.thelema.thrive.register.ItemRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber
public class GlowingBerryJuiceBottleItem extends Item {
    public GlowingBerryJuiceBottleItem() {
        super(new Properties()
                .stacksTo(16)
                .food(new FoodProperties.Builder()
                        .nutrition(2)
                        .saturationModifier(1.0f)
                        .alwaysEdible()
                        .fast()
                        .usingConvertsTo(Items.GLASS_BOTTLE)
                        .effect(() -> new MobEffectInstance(EffectRegister.BEERY_JUICE, 600), 1.0f)
                        .build()
                )
        );
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.HONEY_DRINK;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.HONEY_DRINK;
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack heldItem = event.getItemStack();
        if (!heldItem.is(Items.GLASS_BOTTLE)) return;

        Level level = event.getLevel();
        // 射线检测（客户端和服务端共用）
        BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hitResult.getType() == HitResult.Type.MISS) return;

        BlockPos pos = hitResult.getBlockPos();
        FluidState fluidState = level.getFluidState(pos);
        Fluid stillFluid = FluidRegister.GLOWING_BERRY_JUICE.stillFluid().get();

        // 只允许源方块
        if (!fluidState.isSource() || !fluidState.is(stillFluid)) return;

        // 关键：无论客户端还是服务端，都取消原版事件，阻止客户端预测
        event.setCanceled(true);

        // 客户端：到此结束，不执行实际修改
        if (level.isClientSide) return;

        // ---------- 服务端实际逻辑 ----------
        // 播放音效
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, player.getSoundSource(), 1.0F, 1.0F);

        // 1/3 概率消耗流体源
        if (level.random.nextFloat() < 1.0f / 3.0f) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }

        // 替换或添加自定义瓶子
        ItemStack juiceBottle = new ItemStack(ItemRegister.GLOWING_BERRY_JUICE_BOTTLE.get());
        // 再次获取手中物品（防止被其他操作改变）
        ItemStack currentHeld = player.getItemInHand(event.getHand());
        if (currentHeld.getCount() == 1) {
            player.setItemInHand(event.getHand(), juiceBottle);
        } else {
            currentHeld.shrink(1);
            if (!player.getInventory().add(juiceBottle)) {
                player.drop(juiceBottle, false);
            }
        }
        // 同步物品栏
        player.containerMenu.broadcastChanges();
        if (player instanceof ServerPlayer serverPlayer){
            serverPlayer.server.tell(new TickTask(2, () -> player.containerMenu.broadcastChanges()));
        }

    }
}