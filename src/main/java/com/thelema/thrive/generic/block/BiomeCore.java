package com.thelema.thrive.generic.block;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import com.thelema.thrive.generic.blockentity.convertor.ConvertorRegistry;
import com.thelema.thrive.generic.blockentity.tool.Surface;
import com.thelema.thrive.register.BlockEntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class BiomeCore extends Block implements EntityBlock {

    public BiomeCore() {
        super(Properties.of()
                .mapColor(MapColor.PLANT)
                .strength(5.0f)
                .sound(SoundType.STONE));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BiomeCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (type != BlockEntityRegister.THRIVE_CORE.get()) return null;
        return (lvl, pos, st, be) -> BiomeCoreBlockEntity.tick(lvl, pos, st, (BiomeCoreBlockEntity) be);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean moved) {
        // 转换跑着的时候钥匙锁死在表面，破坏方块也拿不回来
        if (!level.isClientSide && state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof BiomeCoreBlockEntity be
                && !ConvertorRegistry.MAP.containsKey(be.surface.getStack().getItem())) {
            be.surface.dropItem();
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof BiomeCoreBlockEntity be))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        Surface surface = be.surface;

        // 转换跑着的时候钥匙锁定：拿不走，也换不下来
        if (!surface.isEmpty() && ConvertorRegistry.MAP.containsKey(surface.getStack().getItem()))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        // 空手取出
        if (stack.isEmpty()) {
            if (surface.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            ItemStack taken = surface.getStack().copy();
            surface.clear();
            if (!player.getInventory().add(taken)) player.drop(taken, false);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    SoundSource.BLOCKS, 0.75F, 1.0F);
            return ItemInteractionResult.SUCCESS;
        }

        // 手上有物品：表面空则摆上，有则把旧的换下来。空表面取到的是 EMPTY，后面两步是空操作
        ItemStack taken = surface.getStack().copy();
        surface.set(stack.copy(), player.getDirection());
        if (!player.isCreative()) stack.setCount(0);
        if (!player.getInventory().add(taken)) player.drop(taken, false);

        return ItemInteractionResult.SUCCESS;
    }
}