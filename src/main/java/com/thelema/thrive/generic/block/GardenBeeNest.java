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

/**
 * 花园蜂巢方块类。
 * 负责处理视觉状态（是否有蜂蜜）、交互（取蜜）、掉落物打包（携带蜜蜂数据）。
 * 核心的酿蜜与蜜蜂管理逻辑委托给 {@link GardenBeeNestBlockEntity}。
 */
public class GardenBeeNest extends BaseEntityBlock {
    public static final MapCodec<GardenBeeNest> CODEC = simpleCodec(GardenBeeNest::new);

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** 控制外观模型切换：有蜂蜜时显示满蜜纹理 */
    public static final BooleanProperty HAS_HONEY = BooleanProperty.create("has_honey");

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
        // 默认放置时无蜂蜜，朝向北
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HAS_HONEY, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // 使用标准方块模型渲染，不需要 BER
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true; // 允许比较器读取
    }

    /**
     * 比较器输出信号 = 花蜜等级 + 成品蜂蜜数量。
     */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof GardenBeeNestBlockEntity be) {
            return be.nectarLevel + be.honeyCount();
        }
        return 0;
    }

    /**
     * 方块被破坏时触发（例如玩家挖掘或爆炸）。
     * 负责释放巢内蜜蜂，并触发进度触发器。
     */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity te, ItemStack stack) {
        super.playerDestroy(level, player, pos, state, te, stack);
        if (!level.isClientSide && te instanceof GardenBeeNestBlockEntity be) {
            // 如果工具没有“精准采集”或“阻止蜜蜂生成”的附魔，则紧急释放蜜蜂
            if (!EnchantmentHelper.hasTag(stack, EnchantmentTags.PREVENTS_BEE_SPAWNS_WHEN_MINING)) {
                be.emptyAllLivingFromHive(player, state, GardenBeeNestBlockEntity.BeeReleaseStatus.EMERGENCY);
                level.updateNeighbourForOutputSignal(pos, this);
                this.angerNearbyBees(level, pos);
            }
            CriteriaTriggers.BEE_NEST_DESTROYED.trigger((ServerPlayer)player, state, stack, be.getOccupantCount());
        }
    }

    /**
     * 激怒周围尚未驯服的野生花园蜜蜂。
     */
    private void angerNearbyBees(Level level, BlockPos pos) {
        AABB aabb = new AABB(pos).inflate(8.0, 6.0, 8.0);
        List<GardenBee> list = level.getEntitiesOfClass(GardenBee.class, aabb);
        if (list.isEmpty()) return;
        
        List<Player> players = level.getEntitiesOfClass(Player.class, aabb);
        if (players.isEmpty()) return;

        for (GardenBee bee : list) {
            if (bee.isTame()) continue; // 已驯服的蜜蜂不攻击玩家
            if (bee.getTarget() == null) {
                bee.setTarget(Util.getRandom(players, level.random));
            }
        }
    }

    /**
     * 玩家右键交互：
     * 优先尝试用玻璃瓶或剪刀取蜜。如果取蜜成功且没有营火镇压，会激怒并释放蜜蜂。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        // 卫语句：客户端或非本方块实体直接返回
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof GardenBeeNestBlockEntity be)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
        
        // 如果手里拿着玻璃瓶/剪刀且成功取蜜，继续往下走处理激怒逻辑
        if (!be.take(player, stack)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }

        // 取蜜后若下方没有营火，激怒周围的蜜蜂并释放巢内的蜜蜂。
        // 但蜂巢里的蜜蜂全是玩家自己的，就不放出来（否则出来又进巢，右键会抽搐）。
        if (!CampfireBlock.isSmokeyPos(level, pos)
                && !be.isEmpty()
                && !be.isOwnedBy(player)) {
            this.angerNearbyBees(level, pos);
            be.emptyAllLivingFromHive(player, state, GardenBeeNestBlockEntity.BeeReleaseStatus.EMERGENCY);
        }
        level.updateNeighbourForOutputSignal(pos, this);
        return ItemInteractionResult.sidedSuccess(false);
    }

    /**
     * 客户端粒子效果：当内部有成品蜂蜜时，随机滴落蜂蜜粒子。
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof GardenBeeNestBlockEntity be && be.honeyCount() > 0) {
            this.trySpawnDripParticles(level, pos, state);
        }
    }

    private void trySpawnDripParticles(Level level, BlockPos pos, BlockState state) {
        // 如果方块上方有水，或者随机概率不满足，就不滴落
        if (state.getFluidState().isEmpty() && !(level.random.nextFloat() < 0.3F)) {
            VoxelShape voxelshape = state.getCollisionShape(level, pos);
            double d0 = voxelshape.max(Direction.Axis.Y);
            // 根据碰撞箱判断从何处滴落
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
        return level.isClientSide ? null : createTickerHelper(type, BlockEntityRegister.GARDEN_BEE_NEST.get(), GardenBeeNestBlockEntity::serverTick);
    }

    /**
     * 创造模式破坏方块时：如果里面有蜜蜂或有蜂蜜，将数据打包到掉落物中。
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)
                && level.getBlockEntity(pos) instanceof GardenBeeNestBlockEntity be) {

            boolean hasHoney = state.getValue(HAS_HONEY);
            boolean hasBees = !be.isEmpty();

            if (hasBees || hasHoney) {
                ItemStack itemstack = new ItemStack(this);
                // 将容器内的蜜蜂数据转化为物品的 DataComponent
                itemstack.applyComponents(be.collectComponents());
                // 将外观状态（是否有蜂蜜）打包进物品状态
                itemstack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(HAS_HONEY, hasHoney));

                ItemEntity itementity = new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), itemstack);
                itementity.setDefaultPickUpDelay();
                level.addFreshEntity(itementity);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * 处理战利品表掉落（TNT、苦力怕等炸毁时，需要释放蜜蜂）。
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Entity entity = params.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (entity instanceof PrimedTnt || entity instanceof Creeper || entity instanceof WitherSkull
                || entity instanceof WitherBoss || entity instanceof MinecartTNT) {
            BlockEntity blockentity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
            if (blockentity instanceof GardenBeeNestBlockEntity be) {
                be.emptyAllLivingFromHive(null, state, GardenBeeNestBlockEntity.BeeReleaseStatus.EMERGENCY);
            }
        }
        return super.getDrops(state, params);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState,
                                     LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        // 邻接方块是火时，紧急释放蜜蜂
        if (facingState.getBlock() instanceof FireBlock
                && level.getBlockEntity(currentPos) instanceof GardenBeeNestBlockEntity hive) {
            hive.emptyAllLivingFromHive(null, state, GardenBeeNestBlockEntity.BeeReleaseStatus.EMERGENCY);
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