package com.thelema.thrive.generic.fluid;

import com.thelema.thelemalib.damage.DamageQueue;
import com.thelema.thelemalib.data.T2;
import com.thelema.thrive.register.EffectRegister;
import com.thelema.thrive.register.FluidRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 浆果汁爆燃效果 - 模块化状态机
 * 对外只提供：
 * - startLit：进入点燃动画（之后轮询会自动爆炸）
 * - forceExplode：直接爆炸（跳过动画），传入是否消耗流体
 * - 查询状态 getState
 * - 独立的爆炸效果方法供外部自定义调用
 */
@EventBusSubscriber
public class FireExplodeOfBerryJuice {

    // ========== 配置 ==========
    private static final int LIT_TICKS = 40;          // 点燃动画持续时间（2秒）
    private static final int LIT_HEIGHT = 3;          // 火焰上升高度
    private static final int EXPLOSION_RADIUS = 5;     // 爆炸半径
    private static final int EXPLOSION_DAMAGE = 10;    // 伤害值
    private static final int FIRE_TICKS = 100;         // 点燃延长时间（5秒）
    private static final int COOLDOWN_TICKS = 80;      // 爆炸后冷却时间（4秒）

    // 状态常量
    public static final int STATE_IDLE = 0;
    public static final int STATE_LIT = 1;
    public static final int STATE_COOLDOWN = 3;

    // 标志位（存储在 T2.right 的高位）
    private static final long FLAG_CONSUME_FLUID = 1L << 48;
    private static final long TIME_MASK = (1L << 48) - 1;

    // 状态存储
    private static final Map<BlockPos, T2<Integer, Long>> STATE_MAP = new HashMap<>();

    // ========== 对外状态查询 ==========
    public static int getState(BlockPos pos) {
        T2<Integer, Long> entry = STATE_MAP.get(pos);
        return entry == null ? STATE_IDLE : entry.left;
    }

    // ========== 1. 点燃动画（进入 STATE_LIT） ==========
    /**
     * 开始点燃动画（默认动画结束后会爆炸并消耗流体）
     */
    public static void startLit(ServerLevel level, BlockPos pos) {
        startLit(level, pos, true);
    }

    /**
     * 开始点燃动画，可指定动画结束后是否消耗流体
     */
    public static void startLit(ServerLevel level, BlockPos pos, boolean consumeFluid) {
        if (STATE_MAP.containsKey(pos)) return;
        long timeData = (level.getGameTime() & TIME_MASK) | (consumeFluid ? FLAG_CONSUME_FLUID : 0);
        STATE_MAP.put(pos, new T2<>(STATE_LIT, timeData));
        level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    // ========== 2. 立即爆炸（跳过动画，直接进入爆炸流程） ==========
    /**
     * 立即爆炸（不经过点燃动画），可选是否消耗流体
     */
    public static void forceExplode(ServerLevel level, BlockPos pos, boolean consumeFluid) {
        // 清除已有状态，避免冲突
        STATE_MAP.remove(pos);
        // 执行爆炸核心
        doExplosion(level, pos, consumeFluid);
        // 进入冷却状态
        long timeData = (level.getGameTime() & TIME_MASK) | (consumeFluid ? FLAG_CONSUME_FLUID : 0);
        STATE_MAP.put(pos, new T2<>(STATE_COOLDOWN, timeData));
    }

    public static void forceExplode(ServerLevel level, BlockPos pos) {
        forceExplode(level, pos, true);
    }

    // ========== 3. 爆炸核心步骤（可拆分，供外部自定义组合） ==========
    /**
     * 播放爆炸视觉和音效
     */
    public static void playExplosionEffect(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0F, 0.8F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                1, 0.0, 0.0, 0.0, 0.0);
        int particleCount = 300;
        double speed = 12.0;
        double goldenRatio = (1 + Math.sqrt(5)) / 2;
        for (int i = 0; i < particleCount; i++) {
            double theta = 2 * Math.PI * i / goldenRatio;
            double phi = Math.acos(1 - 2.0 * (i + 0.5) / particleCount);
            double dx = Math.sin(phi) * Math.cos(theta);
            double dy = Math.sin(phi) * Math.sin(theta);
            double dz = Math.cos(phi);
            level.sendParticles(ParticleTypes.FLAME,
                    pos.getX() + 0.5 + dx * 0.2, pos.getY() + 0.5 + dy * 0.2, pos.getZ() + 0.5 + dz * 0.2,
                    1, dx * speed, dy * speed, dz * speed, 0);
        }
    }

    /**
     * 应用爆炸伤害和点燃效果
     */
    public static void applyExplosionDamage(ServerLevel level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(EXPLOSION_RADIUS);
        level.getEntitiesOfClass(LivingEntity.class, area).forEach(entity -> {
            entity.setRemainingFireTicks(entity.getRemainingFireTicks() + FIRE_TICKS);
            DamageQueue.hurt(entity, EXPLOSION_DAMAGE, DamageTypes.IN_FIRE.location());
        });
    }

    /**
     * 消耗周围的浆果汁流体（一个源 + 所有流动）
     */
    public static void consumeFluidAround(ServerLevel level, BlockPos pos) {
        boolean sourceRemoved = false;
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        for (int dx = -EXPLOSION_RADIUS; dx <= EXPLOSION_RADIUS; dx++) {
            for (int dy = -EXPLOSION_RADIUS; dy <= EXPLOSION_RADIUS; dy++) {
                for (int dz = -EXPLOSION_RADIUS; dz <= EXPLOSION_RADIUS; dz++) {
                    mPos.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (mPos.distSqr(pos) > EXPLOSION_RADIUS * EXPLOSION_RADIUS) continue;
                    FluidState fs = level.getFluidState(mPos);
                    if (fs.isEmpty()) continue;
                    if (!fs.isSource() && fs.is(FluidRegister.GLOWING_BERRY_JUICE.flowingFluid().get())) {
                        level.setBlock(mPos, Blocks.AIR.defaultBlockState(), 3);
                    } else if (!sourceRemoved && fs.isSource() && fs.is(FluidRegister.GLOWING_BERRY_JUICE.stillFluid().get())) {
                        level.setBlock(mPos, Blocks.AIR.defaultBlockState(), 3);
                        sourceRemoved = true;
                    }
                }
            }
        }
    }

    /**
     * 向周围6个方向传播点燃（不消耗流体，避免重复消耗）
     */
    public static void spreadToNeighbors(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (isBerryJuiceBlock(level, neighbor)) {
                startLit(level, neighbor, false);
            }
        }
    }

    // ========== 内部私有方法（爆炸组合、粒子等） ==========
    private static void doExplosion(ServerLevel level, BlockPos pos, boolean consumeFluid) {
        if (consumeFluid) consumeFluidAround(level, pos);
        applyExplosionDamage(level, pos);
        playExplosionEffect(level, pos);
        spreadToNeighbors(level, pos);
    }

    private static void playLitParticle(ServerLevel level, BlockPos pos, int elapsed) {
        double progress = (double) elapsed / FireExplodeOfBerryJuice.LIT_TICKS;
        double y = pos.getY() + 0.2 + progress * LIT_HEIGHT;
        level.sendParticles(ParticleTypes.FLAME,
                pos.getX() + 0.5, y, pos.getZ() + 0.5,
                3, 0.1, 0.05, 0.1, 0.0);
    }

    private static boolean isBerryJuiceBlock(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(FluidRegister.GLOWING_BERRY_JUICE.block().get());
    }

    // ========== 轮询驱动状态机 ==========
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long now = level.getGameTime();
            for (Map.Entry<BlockPos, T2<Integer, Long>> entry : new ArrayList<>(STATE_MAP.entrySet())) {
                BlockPos pos = entry.getKey();
                int state = entry.getValue().left;
                long encoded = entry.getValue().right;
                long startTick = encoded & TIME_MASK;
                boolean consumeFluid = (encoded & FLAG_CONSUME_FLUID) != 0;
                int elapsed = (int)(now - startTick);

                switch (state) {
                    case STATE_LIT -> {
                        playLitParticle(level, pos, elapsed);
                        if (elapsed >= LIT_TICKS) {
                            // 动画结束，执行爆炸（使用存储的 consumeFluid 标志）
                            doExplosion(level, pos, consumeFluid);
                            // 进入冷却状态（保留原标志）
                            long newEncoded = (now & TIME_MASK) | (consumeFluid ? FLAG_CONSUME_FLUID : 0);
                            entry.getValue().left = STATE_COOLDOWN;
                            entry.getValue().right = newEncoded;
                        }
                    }
                    case STATE_COOLDOWN -> {
                        if (elapsed >= COOLDOWN_TICKS) {
                            STATE_MAP.remove(pos);
                        }
                    }
                }
            }
        }
    }

    // ========== 实体进入流体效果 ==========
    public static void juiceInsideEffect(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (entity.tickCount % 5 != 0) return;
        if (entity instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(EffectRegister.BEERY_JUICE, 2400));
        }
        if (isCanLitEntity(entity)) {
            startLit(serverLevel, pos);
        }
    }

    // ========== 辅助判断方法 ==========
    public static boolean isCanLitEntity(Entity entity) {
        if (entity.isOnFire()) return true;
        if (entity instanceof ItemEntity item) {
            return isCanLitItem(item.getItem().getItem());
        }
        return entity instanceof Blaze ||
                (entity instanceof Creeper creeper && creeper.isPowered()) ||
                entity instanceof MagmaCube ||
                entity instanceof Fireball;
    }

    public static boolean isCanLitItem(Item item) {
        return item == Items.TORCH ||
                item == Items.LAVA_BUCKET ||
                item == Items.BLAZE_ROD ||
                item == Items.BLAZE_POWDER ||
                item == Items.MAGMA_BLOCK ||
                item == Items.MAGMA_CREAM ||
                item == Items.CAMPFIRE;
    }

    public static boolean isCanLitBlock(Block block) {
        return block == Blocks.TORCH ||
                block == Blocks.LAVA ||
                block == Blocks.FIRE ||
                block == Blocks.CAMPFIRE ||
                block == Blocks.MAGMA_BLOCK;
    }

    public static boolean checkFireEnv(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    mutable.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (isCanLitBlock(level.getBlockState(mutable).getBlock())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}