package com.thelema.thrive.generic.block;

import com.mojang.serialization.MapCodec;
import com.thelema.thelemalib.tip.ToolTip;
import com.thelema.thrive.generic.blockentity.MossFluidTankBlockEntity;
import com.thelema.thrive.register.BlockEntityRegister;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@EventBusSubscriber
public class MossFluidTankBlock extends BaseEntityBlock {

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                BlockEntityRegister.MOSS_FLUID_TANK.get(),
                (be, side) -> be
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                BlockEntityRegister.MOSS_FLUID_TANK.get(),
                (be, side) -> be
        );
    }

    public MossFluidTankBlock() {
        super(Block.Properties.of()
                .strength(0.5F)
                .sound(SoundType.GLASS)
                .noOcclusion()
        );
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new MossFluidTankBlock());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MossFluidTankBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MossFluidTankBlockEntity tank)) {
            return super.useItemOn(held, state, level, pos, player, hand, hit);
        }

        // 1. 优先处理流体交互（桶装液体）
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, null)) {
            return ItemInteractionResult.SUCCESS;
        }

        ItemStack heldItem = player.getItemInHand(hand);

        // 2. 如果手中有物品，尝试放入
        if (!heldItem.isEmpty()) {
            ItemStack remaining = ((IItemHandler) tank).insertItem(0, heldItem, false);
            if (remaining.getCount() != heldItem.getCount()) {
                player.setItemInHand(hand, remaining);
                return ItemInteractionResult.SUCCESS;
            }
        }
        // 3. 如果手中没有物品，尝试取出物品
        else {
            ItemStack extracted = ((IItemHandler) tank).extractItem(0, 64, false);
            if (!extracted.isEmpty()) {
                player.setItemInHand(hand, extracted);
                return ItemInteractionResult.SUCCESS;
            }
        }

        return super.useItemOn(held, state, level, pos, player, hand, hit);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;  // 告诉游戏这个方块需要随机刻
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof MossFluidTankBlockEntity tank) {
            tank.triggerItemEffect();
        }
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity be, ItemStack tool) {
        if (level instanceof ServerLevel serverLevel && be instanceof MossFluidTankBlockEntity tank) {
            // 生成带数据的掉落物
            ItemStack drop = new ItemStack(this);
            // 保存方块实体的完整数据
            CompoundTag tag = tank.saveWithFullMetadata(serverLevel.registryAccess());
            // 直接设置 BLOCK_ENTITY_DATA 组件（原版放置时会自动应用）
            drop.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
            // 掉落物品
            popResource(level, pos, drop);
            // 移除方块，不触发额外掉落
            level.removeBlock(pos, false);
        } else {
            super.playerDestroy(level, player, pos, state, be, tool);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(stack, context, list, flag);

        // 获取存储的数据
        CompoundTag data = null;
        if (stack.has(DataComponents.BLOCK_ENTITY_DATA)) {
            CustomData beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (beData != null) data = beData.copyTag();
        }
        if (data == null) return;

        // 解析流体
        FluidStack fluid = data.contains("fluid")
                ? FluidStack.parse(context.registries(), data.getCompound("fluid")).orElse(FluidStack.EMPTY)
                : FluidStack.EMPTY;
        // 解析物品
        ItemStack stored = ItemStack.EMPTY;
        if (data.contains("inventory")) {
            ItemStackHandler inv = new ItemStackHandler(1);
            inv.deserializeNBT(context.registries(), data.getCompound("inventory"));
            stored = inv.getStackInSlot(0);
        }

        new ToolTip(list)
                .trans("tooltip.thrive.moss_fluid_tank.fluid").color(ChatFormatting.GRAY).text(": ")
                .is(!fluid.isEmpty())
                    .color(ChatFormatting.WHITE)
                    .add(fluid.getHoverName().copy().withStyle(style -> style.withColor(IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid) & 0xFFFFFF)))
                    .text(" (" + fluid.getAmount() + " mB)")
                .or()
                    .trans("tooltip.thrive.moss_fluid_tank.empty").color(ChatFormatting.DARK_GRAY)
                    .enter()

                .trans("tooltip.thrive.moss_fluid_tank.item").color(ChatFormatting.GRAY).text(": ")
                .is(!stored.isEmpty())
                    .color(ChatFormatting.WHITE)
                    .add(stored.getHoverName().copy())
                .or()
                    .trans("tooltip.thrive.moss_fluid_tank.empty").color(ChatFormatting.DARK_GRAY)
                .enter()
                .build();
    }
}