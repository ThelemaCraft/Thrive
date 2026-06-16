package com.thelema.thrive.generic.block;

import com.thelema.thelemalib.level.Knockback;
import com.thelema.thrive.generic.fluid.FireExplodeOfBerryJuice;
import com.thelema.thrive.register.BlockRegister;
import com.thelema.thrive.register.EffectRegister;
import com.thelema.thrive.util.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber
public class GiantGlowingBerry extends ColoredFallingBlock {

    public GiantGlowingBerry() {
        super(new ColorRGBA(0xFF6600), Block.Properties.of()
                .instabreak()
                .sound(SoundType.SWEET_BERRY_BUSH)
                .lightLevel(state -> 15));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        // 检查火焰环境
        if (!level.isClientSide && FireExplodeOfBerryJuice.checkFireEnv(level, pos)) {
            ServerLevel sl = (ServerLevel) level;
            // 立刻爆炸
            FireExplodeOfBerryJuice.forceExplode(sl, pos, false);
            // 清除自己
            level.destroyBlock(pos, false);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getServer().getTickCount() % 5 == 0){
            // 检查火焰环境
            if (FireExplodeOfBerryJuice.checkFireEnv(level, pos)){
                // 立刻爆炸
                FireExplodeOfBerryJuice.forceExplode(level, pos, false);
                // 清除自己
                level.destroyBlock(pos, false);
            }
        }
        super.tick(state, level, pos, random);
    }

    @SubscribeEvent
    public static void onPistonPush(PistonEvent.Pre event) {
        Level level = (Level) event.getLevel();

        BlockPos pos = event.getFaceOffsetPos();
        BlockState state = level.getBlockState(pos);

        if (state.is(BlockRegister.GIANT_GLOWING_BERRY.get())){
            // 手动掉落物品
            Block.popResource(level, pos, new ItemStack(state.getBlock()));
            // 移除方块（不触发掉落事件）
            level.removeBlock(pos, false);
        }

    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return 15;
    }

    // 剪刀剪下
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(Items.SHEARS) && !level.isClientSide) {
            // 播放剪刀剪切声音
            player.playSound(SoundEvents.CAVE_VINES_PICK_BERRIES); // 或用 SoundEvents.SHEAR
            // 掉落方块自身
            popResource(level, pos, new ItemStack(this));
            // 移除方块
            level.removeBlock(pos, false);
            // 消耗剪刀耐久（可选）
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            return ItemInteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    // 挖掘，投射物命中，等等都会造成爆炸
    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        BlockPos pos = hit.getBlockPos();
        level.destroyBlock(pos, false);
        if (level instanceof ServerLevel sl){
            // 投射物命中，带火
            if (projectile.isOnFire()){
                // 立刻爆炸
                FireExplodeOfBerryJuice.forceExplode(sl, pos, false);
                // 清除方块
                sl.destroyBlock(pos, false);
            }else {
                berryBlast(sl, pos);
            }
        }
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        boolean b = super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
        if (b && level instanceof ServerLevel sl){
            berryBlast(sl, pos);
        }
        return b;
    }

    @Override
    public void onDestroyedByPushReaction(BlockState state, Level level, BlockPos pos, Direction pushDirection, FluidState fluid) {
        super.onDestroyedByPushReaction(state, level, pos, pushDirection, fluid);
        if (level instanceof ServerLevel sl) berryBlast(sl, pos);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        super.fallOn(level, state, pos, entity, fallDistance);
        if (level instanceof ServerLevel sl) berryBlast(sl, pos);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (level instanceof ServerLevel sl) berryBlast(sl, pos);
    }

    @Override
    public void onLand(Level level, BlockPos pos, BlockState state, BlockState replaceableState, FallingBlockEntity fallingBlock) {
        // 移除掉落实体，防止自动放置方块
        fallingBlock.discard();
        // 清除当前位置的方块（如果存在）
        if (level.getBlockState(pos).is(this)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        // 触发爆炸（无伤害、有击退、发光）
        if (level instanceof ServerLevel sl) {
            berryBlast(sl, pos);
        }
    }

    public static void berryBlast(ServerLevel level, BlockPos pos) {
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        float radius = 5.0F;
        level.destroyBlock(pos, false);
        level.sendParticles(ParticleTypes.EXPLOSION, cx, cy, cz, 4, 1.0, 1.0, 1.0, 0.0);
        level.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 0.5F);

        // 保留击退
        List<Entity> affectedEntities = Knockback.point(level, pos, radius, 1.8, e -> true);

        // 对周围实体施加浆果汁buff & 治愈标记实体
        for (Entity entity : affectedEntities) {
            if (entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(EffectRegister.BEERY_JUICE, 2400, 0));
                if (living.getPersistentData().getBoolean(Flags.MOSS_CALLUS)) {
                    living.heal(4.0F);
                }
            }
        }

        growMoss(level, pos, radius);
        fertilizeMossCrops(level, pos, radius);
    }

    private static void growMoss(ServerLevel level, BlockPos center, float radius) {
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        // 1. 收集范围内所有原生的苔藓块（转化前）
        List<BlockPos> nativeMoss = new ArrayList<>();
        for (int dx = -5; dx <= 5; dx++)
            for (int dy = -5; dy <= 5; dy++)
                for (int dz = -5; dz <= 5; dz++) {
                    mpos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (mpos.distSqr(center) <= radius * radius && level.getBlockState(mpos).is(Blocks.MOSS_BLOCK)) {
                        nativeMoss.add(mpos.immutable());
                    }
                }

        // 如果没有原生苔藓块，不进行任何操作（转化也无意义）
        if (nativeMoss.isEmpty()) return;

        // 2. 转化石头/泥土类方块为苔藓
        for (int dx = -5; dx <= 5; dx++)
            for (int dy = -5; dy <= 5; dy++)
                for (int dz = -5; dz <= 5; dz++) {
                    mpos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (mpos.distSqr(center) <= radius * radius && canConvertToMoss(level.getBlockState(mpos))) {
                        level.setBlock(mpos, Blocks.MOSS_BLOCK.defaultBlockState(), 3);
                    }
                }

        // 3. 从原生苔藓块中随机选一个进行骨粉催熟（无视上方阻挡）
        BlockPos target = nativeMoss.get(level.getRandom().nextInt(nativeMoss.size()));
        BlockState targetState = level.getBlockState(target);
        if (targetState.getBlock() instanceof BonemealableBlock growable) {
            growable.performBonemeal(level, level.getRandom(), target, targetState);
            level.sendBlockUpdated(target, targetState, level.getBlockState(target), 3);
        }
    }

    // 只转化石头和泥土类方块，不包含空气
    private static boolean canConvertToMoss(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.STONE
                || block == Blocks.COBBLESTONE
                || block == Blocks.DIRT
                || block == Blocks.COARSE_DIRT
                || block == Blocks.ROOTED_DIRT
                || block == Blocks.GRASS_BLOCK
                || block == Blocks.PODZOL
                || block == Blocks.MYCELIUM;
    }

    private static void fertilizeMossCrops(ServerLevel level, BlockPos center, float radius) {
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int x = -5; x <= 5; x++)
            for (int y = -5; y <= 5; y++)
                for (int z = -5; z <= 5; z++) {
                    mpos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (mpos.distSqr(center) <= radius * radius) {
                        BlockState below = level.getBlockState(mpos.below());
                        if (below.getBlock() instanceof MossPlantingBlock) {
                            BlockState crop = level.getBlockState(mpos);
                            if (crop.getBlock() instanceof BonemealableBlock b && b.isValidBonemealTarget(level, mpos, crop))
                                b.performBonemeal(level, level.getRandom(), mpos, crop);
                        }
                    }
                }
    }

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.CAVE_VINES)) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);

            if (belowState.isAir() || belowState.canBeReplaced()) {
                if (!level.isClientSide) {
                    // 取消原版骨粉行为
                    event.setCanceled(true);

                    // 直接设置方块并强制通知客户端
                    level.setBlockAndUpdate(below, BlockRegister.GIANT_GLOWING_BERRY.get().defaultBlockState());
                    level.playSound(null, below, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);

                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                below.getX() + 0.5, below.getY() + 0.5, below.getZ() + 0.5,
                                10, 0.5, 0.5, 0.5, 0.1);
                        serverLevel.sendParticles(ParticleTypes.GLOW,
                                below.getX() + 0.5, below.getY() + 0.5, below.getZ() + 0.5,
                                5, 0.3, 0.3, 0.3, 0.05);
                    }

                    // 额外同步一次，确保客户端不会残留旧方块
                    level.sendBlockUpdated(below, belowState, level.getBlockState(below), 3);

                }
            }
        }
    }

}
