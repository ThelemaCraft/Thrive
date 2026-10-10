package com.thelema.thrive.generic.block;

import com.thelema.thrive.generic.blockentity.SunBerryBlockEntity;
import com.thelema.thrive.generic.entity.PrimedSunBerry;
import com.thelema.thrive.register.BlockRegister;
import com.thelema.thrive.register.SoundRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class SunBerry extends Block implements EntityBlock {

    public static final BooleanProperty CORE = BooleanProperty.create("core");

    public SunBerry() {
        super(Properties.of()
                .instabreak()
                .sound(SoundType.SWEET_BERRY_BUSH)
                .pushReaction(PushReaction.DESTROY)
                .lightLevel(state -> 15));
        this.registerDefaultState(this.stateDefinition.any().setValue(CORE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CORE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SunBerryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        // 结构：从 (clicked.x-1, clicked.y, clicked.z-1) 到 (clicked.x+1, clicked.y+2, clicked.z+1)
        for (BlockPos p : BlockPos.betweenClosed(clicked.offset(-1, 0, -1), clicked.offset(1, 2, 1))) {
            if (!level.getBlockState(p).canBeReplaced(context)) {
                return null;
            }
        }
        return this.defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;

        BlockPos core = pos.offset(0, 1, 0);

        for (BlockPos p : BlockPos.betweenClosed(core.offset(-1, -1, -1), core.offset(1, 1, 1))) {
            BlockState s = this.defaultBlockState();
            if (p.equals(core)) {
                s = s.setValue(CORE, true);
            }
            if (!p.equals(pos)) {
                level.setBlock(p, s, 3);
            } else {
                level.setBlock(p, s, 3);
            }
        }

        // corePos 仍然保留，供其他逻辑（点燃、破坏）定位
        for (BlockPos p : BlockPos.betweenClosed(core.offset(-1, -1, -1), core.offset(1, 1, 1))) {
            if (level.getBlockEntity(p) instanceof SunBerryBlockEntity be) {
                be.setCorePos(core);
            }
        }
    }

    @Nullable
    public static BlockPos getCorePos(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SunBerryBlockEntity be) {
            return be.getCorePos();
        }
        return null;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos core = getCorePos(level, pos);
            if (core != null) {
                for (BlockPos p : BlockPos.betweenClosed(core.offset(-1, -1, -1), core.offset(1, 1, 1))) {
                    if (!p.equals(pos) && level.getBlockState(p).is(this)) {
                        level.removeBlock(p, false);
                    }
                }
            }
            if (!player.isCreative()) {
                Block.popResource(level, pos, new ItemStack(BlockRegister.SUN_BERRY.get().asItem()));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide) {
            BlockPos core = getCorePos(level, pos);
            if (core == null) core = pos;

            for (BlockPos p : BlockPos.betweenClosed(core.offset(-1, -1, -1), core.offset(1, 1, 1))) {
                if (level.getBlockState(p).is(this)) {
                    level.removeBlock(p, false);
                }
            }

            ignite(level, core, player);

            if (stack.is(Items.FLINT_AND_STEEL)) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            } else {
                stack.consume(1, player);
            }
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide && projectile.isOnFire()) {
            BlockPos pos = hit.getBlockPos();
            BlockPos core = getCorePos(level, pos);
            if (core == null) core = pos;

            for (BlockPos p : BlockPos.betweenClosed(core.offset(-1, -1, -1), core.offset(1, 1, 1))) {
                if (level.getBlockState(p).is(this)) {
                    level.removeBlock(p, false);
                }
            }

            ignite(level, core, projectile.getOwner() instanceof LivingEntity le ? le : null);
        }
    }

    public static void ignite(Level level, BlockPos core, @Nullable LivingEntity igniter) {
        if (!level.isClientSide) {
            PrimedSunBerry entity = new PrimedSunBerry(
                    level, core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5, igniter);
            level.addFreshEntity(entity);
            level.playSound(null, core, SoundRegister.SUN_BERRY_IGNITE.get(),
                    SoundSource.BLOCKS, 1.5F, 1.5F);
            level.gameEvent(igniter, GameEvent.PRIME_FUSE, core);
        }
    }
}