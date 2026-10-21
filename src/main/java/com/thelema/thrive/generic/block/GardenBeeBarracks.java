package com.thelema.thrive.generic.block;

import com.thelema.thrive.generic.blockentity.GardenBeeBarracksBlockEntity;
import com.thelema.thrive.register.BlockEntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.player.Player;
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