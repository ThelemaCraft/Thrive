package com.thelema.thrive.generic.block;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import com.thelema.thrive.generic.blockentity.cap.Surface;
import com.thelema.thrive.register.BlockEntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.Vec3;
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
        if (type != BlockEntityRegister.BIOME_CORE.get()) return null;
        return (lvl, pos, st, be) -> BiomeCoreBlockEntity.tick(lvl, pos, st, (BiomeCoreBlockEntity) be);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean moved) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof BiomeCoreBlockEntity be) {
            be.resetConversion();
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

        if (player.isShiftKeyDown() && !be.rotator.isEmpty()) {
            terminate(level, pos, be, player);
            return ItemInteractionResult.SUCCESS;
        }

        Surface surface = be.surface;

        // 有物品 → 尝试取下
        if (!surface.isEmpty()) {
            ItemStack taken = surface.getStack().copy();
            if (!giveToSelectedSlot(player, taken)) return ItemInteractionResult.FAIL;  // 背包满，不动
            surface.clear();
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    SoundSource.BLOCKS, 0.75F, 1.0F);
            return ItemInteractionResult.SUCCESS;
        }

        // 空 → 放上去
        if (!stack.isEmpty()) {
            surface.set(stack.copy(), player.getDirection());
            if (!player.isCreative()) stack.setCount(0);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM,
                    SoundSource.BLOCKS, 0.75F, 1.0F);
            be.checkTimer = 100;
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 优先塞入当前选中槽，能堆叠就堆叠，否则交给背包。全部失败返回 false，不做任何修改。 */
    private static boolean giveToSelectedSlot(Player player, ItemStack stack) {
        int slot = player.getInventory().selected;
        ItemStack cur = player.getInventory().getItem(slot);

        if (cur.isEmpty()) {
            player.getInventory().setItem(slot, stack);
            return true;
        }
        if (ItemStack.isSameItemSameComponents(cur, stack)
                && cur.getCount() + stack.getCount() <= cur.getMaxStackSize()) {
            cur.grow(stack.getCount());
            return true;
        }
        return player.getInventory().add(stack);
    }

    private static void terminate(Level level, BlockPos pos, BiomeCoreBlockEntity be, Player player) {
        ServerLevel sl = (ServerLevel) level;

        sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5, pos.getY() + be.rotator.height, pos.getZ() + 0.5,
                30, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS, 1.0F, 1.0F);

        Vec3 dir = player.position().subtract(Vec3.atCenterOf(pos)).normalize();
        player.push(dir.x * 1.5, 0.4, dir.z * 1.5);
        player.hurtMarked = true;

        be.resetConversion();
    }
}