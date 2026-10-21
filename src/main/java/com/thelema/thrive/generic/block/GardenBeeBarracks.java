package com.thelema.thrive.generic.block;

import com.thelema.thrive.generic.blockentity.GardenBeeBarracksBlockEntity;
import com.thelema.thrive.register.BlockEntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class GardenBeeBarracks extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public GardenBeeBarracks() {
        super(Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5f)
                .sound(SoundType.WOOD));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GardenBeeBarracksBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != BlockEntityRegister.GARDEN_BEE_BARRACKS.get()) return null;
        return (lvl, pos, st, be) -> GardenBeeBarracksBlockEntity.tick(lvl, pos, st, (GardenBeeBarracksBlockEntity) be);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return handleInteraction(state, level, pos, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        ItemInteractionResult res = handleInteraction(state, level, pos, player);
        return res == ItemInteractionResult.SUCCESS ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }

    private ItemInteractionResult handleInteraction(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof GardenBeeBarracksBlockEntity barracks)) 
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        // 有主兵营只允许归属者操作
        if (!barracks.canOperate(player)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (player.isShiftKeyDown()) {
            // 潜行右键：强制释放全部，5秒冷却
            barracks.releaseAllBees(level, pos);
        } else {
            // 普通右键：释放一只
            barracks.releaseOneBee(level, pos);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GardenBeeBarracksBlockEntity barracks) {
            if (EnchantmentHelper.hasTag(player.getMainHandItem(), EnchantmentTags.PREVENTS_BEE_SPAWNS_WHEN_MINING)) {
                // 精准采集：不吐蜂，让战利品表把 BE 数据写进掉落物
                barracks.silkHarvest = true;
            } else {
                barracks.releaseAll(level, pos, player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof GardenBeeBarracksBlockEntity barracks
                && !barracks.silkHarvest) {
            // 爆炸、活塞、setblock 等非玩家破坏：安静地吐出
            barracks.releaseAll(level, pos, null);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}