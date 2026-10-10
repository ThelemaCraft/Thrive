package com.thelema.thrive.generic.block;

import com.thelema.thrive.generic.entity.PrimedLanternBerry;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber
@SuppressWarnings("NullableProblems")
public class LanternBerry extends Block {

    public LanternBerry() {
        super(Properties.of()
                .instabreak()
                .sound(SoundType.SWEET_BERRY_BUSH)
                .pushReaction(PushReaction.DESTROY)
                .lightLevel(state -> 15));
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return 15;
    }

    // ====== 点燃核心 ======
    public static void ignite(Level level, BlockPos pos, @Nullable LivingEntity igniter) {
        if (!level.isClientSide) {
            PrimedLanternBerry entity = new PrimedLanternBerry(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, igniter);
            level.addFreshEntity(entity);
            level.playSound(null, pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(igniter, GameEvent.PRIME_FUSE, pos);
        }
    }

    // ====== 火矢命中 ======
    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide && projectile.isOnFire()) {
            BlockPos pos = hit.getBlockPos();
            ignite(level, pos, projectile.getOwner() instanceof LivingEntity ? (LivingEntity) projectile.getOwner() : null);
            level.removeBlock(pos, false);
        }
    }

    // ====== 打火石/火焰弹右键 ======
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }

        if (!level.isClientSide) {
            ignite(level, pos, player);
            level.removeBlock(pos, false);

            if (stack.is(Items.FLINT_AND_STEEL)) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            } else {
                stack.consume(1, player);
            }
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    // ====== 骨粉催熟 ======
    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.CAVE_VINES) && level.getBiome(pos).is(ResourceLocation.parse("thrive:moss"))) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);

            if (belowState.isAir()) {
                if (!level.isClientSide) {
                    event.setCanceled(true);
                    level.setBlockAndUpdate(below, BlockRegister.LANTERN_BERRY.get().defaultBlockState());
                    level.playSound(null, below, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);

                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                below.getX() + 0.5, below.getY() + 0.5, below.getZ() + 0.5,
                                10, 0.5, 0.5, 0.5, 0.1);
                        serverLevel.sendParticles(ParticleTypes.GLOW,
                                below.getX() + 0.5, below.getY() + 0.5, below.getZ() + 0.5,
                                5, 0.3, 0.3, 0.3, 0.05);
                    }
                    level.sendBlockUpdated(below, belowState, level.getBlockState(below), 3);
                }
            }
        }
    }
}