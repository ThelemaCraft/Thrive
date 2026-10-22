package com.thelema.thrive.generic.block;

import com.mojang.serialization.MapCodec;
import com.thelema.thrive.generic.blockentity.GardenBeeNestBlockEntity;
import com.thelema.thrive.generic.entity.GardenBee;
import com.thelema.thrive.register.BlockEntityRegister;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

public class GardenBeeNest extends BaseEntityBlock {
    public static final MapCodec<GardenBeeNest> CODEC = simpleCodec(GardenBeeNest::new);

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty HAS_HONEY = BooleanProperty.create("has_honey");
    public static final int MAX_HONEY_LEVELS = 5;
    private static final int SHEARED_HONEYCOMB_COUNT = 3;

    public GardenBeeNest() {
        this(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_YELLOW)
                .instrument(NoteBlockInstrument.BASS)
                .strength(0.3F)
                .sound(SoundType.WOOD)
                .ignitedByLava());
    }

    public GardenBeeNest(BlockBehaviour.Properties properties) {
        super(properties);
        // 默认没有蜂蜜
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HAS_HONEY, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    // 比较器改为从 BE 读花蜜和蜂蜜数量
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof GardenBeeNestBlockEntity be) {
            return be.nectarLevel + be.honeyCount();
        }
        return 0;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity te, ItemStack stack) {
        super.playerDestroy(level, player, pos, state, te, stack);
        // 替换为 GardenHiveBlockEntity
        if (!level.isClientSide && te instanceof GardenBeeNestBlockEntity beehiveblockentity) {
            if (!EnchantmentHelper.hasTag(stack, EnchantmentTags.PREVENTS_BEE_SPAWNS_WHEN_MINING)) {
                beehiveblockentity.emptyAllLivingFromHive(player, state, GardenBeeNestBlockEntity.BeeReleaseStatus.EMERGENCY);
                level.updateNeighbourForOutputSignal(pos, this);
                this.angerNearbyBees(level, pos);
            }
            CriteriaTriggers.BEE_NEST_DESTROYED.trigger((ServerPlayer)player, state, stack, beehiveblockentity.getOccupantCount());
        }
    }

    private void angerNearbyBees(Level level, BlockPos pos) {
        AABB aabb = new AABB(pos).inflate(8.0, 6.0, 8.0);
        List<GardenBee> list = level.getEntitiesOfClass(GardenBee.class, aabb);
        if (!list.isEmpty()) {
            List<Player> list1 = level.getEntitiesOfClass(Player.class, aabb);
            if (list1.isEmpty()) return;
            for (GardenBee bee : list) {
                // 【新增过滤】如果这只蜜蜂已经被驯服了，就不激怒它
                if (bee.isTame()) continue;

                if (bee.getTarget() == null) {
                    Player player = Util.getRandom(list1, level.random);
                    bee.setTarget(player);
                }
            }
        }
    }

    public static void dropHoneycomb(Level level, BlockPos pos) {
        popResource(level, pos, new ItemStack(Items.HONEYCOMB, 3));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GardenBeeNestBlockEntity be) {
            // 尝试取蜜（消耗玻璃瓶/剪刀）
            if (be.take(player, stack)) {
                // 取蜜成功后：
                // 如果下方没有营火（没有被烟熏镇静）
                if (!CampfireBlock.isSmokeyPos(level, pos)) {
                    // 如果里面有蜜蜂，激怒周围的游荡蜜蜂
                    if (!be.isEmpty()) {
                        this.angerNearbyBees(level, pos);
                    }
                    // 将巢内的蜜蜂全部以“紧急状态”放出，它们会直接攻击玩家
                    be.emptyAllLivingFromHive(player, state, GardenBeeNestBlockEntity.BeeReleaseStatus.EMERGENCY);
                }

                // 更新红石信号
                level.updateNeighbourForOutputSignal(pos, this);

                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        // 如果拿的不是瓶子/剪刀，或者没蜂蜜，走默认交互
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    private boolean hiveContainsBees(Level level, BlockPos pos) {
        // 替换为 GardenHiveBlockEntity
        return level.getBlockEntity(pos) instanceof GardenBeeNestBlockEntity be && !be.isEmpty();
    }

    public void releaseBeesAndResetHoneyLevel(Level level, BlockState state, BlockPos pos, @Nullable Player player, GardenBeeNestBlockEntity.BeeReleaseStatus beeReleaseStatus) {
        this.resetHoneyLevel(level, state, pos);
        // 替换为 GardenHiveBlockEntity
        if (level.getBlockEntity(pos) instanceof GardenBeeNestBlockEntity be) {
            be.emptyAllLivingFromHive(player, state, beeReleaseStatus);
        }
    }

    public void resetHoneyLevel(Level level, BlockState state, BlockPos pos) {
        if (state.hasProperty(HAS_HONEY) && state.getValue(HAS_HONEY)) {
            level.setBlock(pos, state.setValue(HAS_HONEY, false), 3);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // 改为读取方块实体里的蜂蜜数量是否大于 0
        if (level.getBlockEntity(pos) instanceof GardenBeeNestBlockEntity be && be.getHoneyCount() > 0) {
            for (int i = 0; i < random.nextInt(1) + 1; i++) {
                this.trySpawnDripParticles(level, pos, state);
            }
        }
    }

    private void trySpawnDripParticles(Level level, BlockPos pos, BlockState state) {
        if (state.getFluidState().isEmpty() && !(level.random.nextFloat() < 0.3F)) {
            VoxelShape voxelshape = state.getCollisionShape(level, pos);
            double d0 = voxelshape.max(Direction.Axis.Y);
            if (d0 >= 1.0 && !state.is(BlockTags.IMPERMEABLE)) {
                double d1 = voxelshape.min(Direction.Axis.Y);
                if (d1 > 0.0) {
                    this.spawnParticle(level, pos, voxelshape, (double)pos.getY() + d1 - 0.05);
                } else {
                    BlockPos blockpos = pos.below();
                    BlockState blockstate = level.getBlockState(blockpos);
                    VoxelShape voxelshape1 = blockstate.getCollisionShape(level, blockpos);
                    double d2 = voxelshape1.max(Direction.Axis.Y);
                    if ((d2 < 1.0 || !blockstate.isCollisionShapeFullBlock(level, blockpos)) && blockstate.getFluidState().isEmpty()) {
                        this.spawnParticle(level, pos, voxelshape, (double)pos.getY() - 0.05);
                    }
                }
            }
        }
    }

    private void spawnParticle(Level level, BlockPos pos, VoxelShape shape, double y) {
        this.spawnFluidParticle(level,
                (double)pos.getX() + shape.min(Direction.Axis.X),
                (double)pos.getX() + shape.max(Direction.Axis.X),
                (double)pos.getZ() + shape.min(Direction.Axis.Z),
                (double)pos.getZ() + shape.max(Direction.Axis.Z),
                y);
    }

    private void spawnFluidParticle(Level particleData, double x1, double x2, double z1, double z2, double y) {
        particleData.addParticle(ParticleTypes.DRIPPING_HONEY,
                Mth.lerp(particleData.random.nextDouble(), x1, x2), y,
                Mth.lerp(particleData.random.nextDouble(), z1, z2),
                0.0, 0.0, 0.0);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_HONEY, FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GardenBeeNestBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide? null : createTickerHelper(type, BlockEntityRegister.GARDEN_BEE_NEST.get(), GardenBeeNestBlockEntity::serverTick);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)
                && level.getBlockEntity(pos) instanceof GardenBeeNestBlockEntity beehiveblockentity) {

            boolean hasHoney = state.getValue(HAS_HONEY);
            boolean hasBees = !beehiveblockentity.isEmpty();

            if (hasBees || hasHoney) {
                ItemStack itemstack = new ItemStack(this);
                // 打包蜜蜂数据
                itemstack.applyComponents(beehiveblockentity.collectComponents());

                // 打包外观状态 (has_honey 是布尔值，存 1 代表有，0 代表无)
                itemstack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(HAS_HONEY, hasHoney));

                ItemEntity itementity = new ItemEntity(level, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), itemstack);
                itementity.setDefaultPickUpDelay();
                level.addFreshEntity(itementity);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Entity entity = params.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (entity instanceof PrimedTnt || entity instanceof Creeper || entity instanceof WitherSkull
                || entity instanceof WitherBoss || entity instanceof MinecartTNT) {
            BlockEntity blockentity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
            // 替换为 GardenHiveBlockEntity
            if (blockentity instanceof GardenBeeNestBlockEntity beehiveblockentity) {
                beehiveblockentity.emptyAllLivingFromHive(null, state, GardenBeeNestBlockEntity.BeeReleaseStatus.EMERGENCY);
            }
        }
        return super.getDrops(state, params);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        // 替换为 GardenHiveBlockEntity
        if (level.getBlockState(facingPos).getBlock() instanceof FireBlock
                && level.getBlockEntity(currentPos) instanceof GardenBeeNestBlockEntity beehiveblockentity) {
            beehiveblockentity.emptyAllLivingFromHive(null, state, GardenBeeNestBlockEntity.BeeReleaseStatus.EMERGENCY);
        }
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

}