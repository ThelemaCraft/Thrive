package com.thelema.thrive.generic.fluid;

import com.thelema.thelemalib.data.LevelMap;
import com.thelema.thelemalib.damage.DamageQueue;
import com.thelema.thrive.register.BlockRegister;
import com.thelema.thrive.register.EffectRegister;
import com.thelema.thrive.ThriveMod;
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

import java.util.*;

public class FireExplode {

    // ========== 状态常量（数字） ==========
    public static final int STATE_IDLE = 0;
    public static final int STATE_LIT = 1;
    public static final int STATE_COOLDOWN = 2;

    // ========== LevelMap Key ==========
    private static final String STATE_KEY = "thrive_fire_explode_state";

    // ========== 常量配置 ==========
    public static final int LIT_DURATION_TICKS = 60;        // 3秒
    public static final int COOLDOWN_DURATION_TICKS = 80;   // 4秒
    public static final int EXPLOSION_RADIUS = 5;
    public static final int DAMAGE_AMOUNT = 10;
    public static final int FIRE_TICKS = 100;
    public static final int ANIMATION_PARTICLE_TICKS = 2;

    // ========== 状态存储（使用 LevelMap 支持序列化） ==========
    private static LevelMap<BlockPos, Integer> getMap(ServerLevel level) {
        return LevelMap.common(level, STATE_KEY);
    }

    // ========== 状态查询 ==========
    public static int getState(ServerLevel level, BlockPos pos) {
        return getMap(level).getOrDefault(pos, STATE_IDLE);
    }

    public static boolean isLit(ServerLevel level, BlockPos pos) {
        return getState(level, pos) == STATE_LIT;
    }

    public static boolean isCooldown(ServerLevel level, BlockPos pos) {
        return getState(level, pos) == STATE_COOLDOWN;
    }

    // ========== 核心入口 ==========
    public static void lit(ServerLevel level, BlockPos pos) {
        LevelMap<BlockPos, Integer> map = getMap(level);
        int current = map.getOrDefault(pos, STATE_IDLE);

        // 点燃中或冷却中，拒绝重复点燃
        if (current == STATE_LIT || current == STATE_COOLDOWN) return;

        // 1. 进入点燃状态
        map.put(pos, STATE_LIT);

        // 2. 播放点燃音效
        level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.5F, 1.0F);

        // 3. 启动动画
        playLitAnimation(level, pos, 0);

        // 4. 调度：1.5秒后扩散点燃
        ThriveMod.schedule(LIT_DURATION_TICKS / 2, () -> {
            if (map.containsKey(pos)) {
                juiceSpreadLit(level, pos);
            }
        });

        // 5. 调度：3秒后爆炸
        ThriveMod.schedule(LIT_DURATION_TICKS, () -> {
            if (map.containsKey(pos)) {
                BlockState state = level.getBlockState(pos);
                // 如果位置不是果汁，或者巨型浆果，说明被提前破坏，不进行爆炸
                if (state.is(FluidRegister.GLOWING_BERRY_JUICE.block().get()) || state.is(BlockRegister.GIANT_GLOWING_BERRY.get())) return;
                // 爆炸
                doExplode(level, pos);

                // 进入冷却状态
                map.put(pos, STATE_COOLDOWN);

                // 调度：4秒后清除冷却
                ThriveMod.schedule(COOLDOWN_DURATION_TICKS, () -> {
                    if (map.getOrDefault(pos, STATE_IDLE) == STATE_COOLDOWN) {
                        map.remove(pos);
                    }
                });
            }
        });
    }

    public static void explode(ServerLevel level, BlockPos pos){
        LevelMap<BlockPos, Integer> map = getMap(level);
        int current = map.getOrDefault(pos, STATE_IDLE);
        // 只当为等待状态
        if (current != STATE_IDLE) return;

        // 立刻爆炸
        doExplode(level, pos);

        // 扩散点燃到浆果
        berrySpreadLit(level, pos);

        // 进入冷却状态, 4秒后清除冷却
        map.put(pos, STATE_COOLDOWN);
        ThriveMod.schedule(COOLDOWN_DURATION_TICKS, () -> {
            if (map.getOrDefault(pos, STATE_IDLE) == STATE_COOLDOWN) {
                map.remove(pos);
            }
        });
    }
    // ========== 函数 ==========

    private static void playLitAnimation(ServerLevel level, BlockPos pos, int elapsed) {
        LevelMap<BlockPos, Integer> map = getMap(level);
        if (!map.containsKey(pos)) return;

        double progress = (double) elapsed / LIT_DURATION_TICKS;
        double y = pos.getY() + 0.2 + progress * 3.0;
        level.sendParticles(ParticleTypes.FLAME,
                pos.getX() + 0.5, y, pos.getZ() + 0.5,
                3, 0.1, 0.05, 0.1, 0.0);

        if (elapsed + ANIMATION_PARTICLE_TICKS < LIT_DURATION_TICKS) {
            ThriveMod.schedule(ANIMATION_PARTICLE_TICKS, () -> {
                playLitAnimation(level, pos, elapsed + ANIMATION_PARTICLE_TICKS);
            });
        }
    }

    private static void playExplosionAnimation(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0F, 0.8F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                1, 0.0, 0.0, 0.0, 0.0);

        int count = 300;
        double speed = 12.0;
        double goldenRatio = (1 + Math.sqrt(5)) / 2;
        for (int i = 0; i < count; i++) {
            double theta = 2 * Math.PI * i / goldenRatio;
            double phi = Math.acos(1 - 2.0 * (i + 0.5) / count);
            double dx = Math.sin(phi) * Math.cos(theta);
            double dy = Math.sin(phi) * Math.sin(theta);
            double dz = Math.cos(phi);
            level.sendParticles(ParticleTypes.FLAME,
                    pos.getX() + 0.5 + dx * 0.2,
                    pos.getY() + 0.5 + dy * 0.2,
                    pos.getZ() + 0.5 + dz * 0.2,
                    1, dx * speed, dy * speed, dz * speed, 0);
        }
    }

    private static void applyDamage(ServerLevel level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(EXPLOSION_RADIUS);
        level.getEntitiesOfClass(LivingEntity.class, area).forEach(entity -> {
            entity.setRemainingFireTicks(entity.getRemainingFireTicks() + FIRE_TICKS);
            DamageQueue.hurt(entity, DAMAGE_AMOUNT, DamageTypes.IN_FIRE.location());
        });
    }

    private static void consumeFluid(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    mPos.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    FluidState fs = level.getFluidState(mPos);
                    if (!fs.isEmpty() && (fs.is(FluidRegister.GLOWING_BERRY_JUICE.stillFluid().get())
                            || fs.is(FluidRegister.GLOWING_BERRY_JUICE.flowingFluid().get()))) {
                        level.setBlock(mPos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static void juiceSpreadLit(ServerLevel level, BlockPos pos) {
        LevelMap<BlockPos, Integer> map = getMap(level);
        List<Direction> faces = new ArrayList<>(List.of(Direction.values()));
        Collections.shuffle(faces, ThriveMod.RANDOM);

        for (int i = 0; i < Math.min(3, faces.size()); i++) {
            Direction face = faces.get(i);
            Direction u = getFirstPerpendicular(face);
            Direction v = face.getClockWise(u.getAxis());

            List<BlockPos> candidates = new ArrayList<>();
            for (int du = -1; du <= 1; du++) {
                for (int dv = -1; dv <= 1; dv++) {
                    BlockPos target = pos.relative(face, 1)
                            .relative(u, du)
                            .relative(v, dv);
                    if (isBerryJuiceBlock(level, target) && !map.containsKey(target)) {
                        candidates.add(target);
                    }
                }
            }

            if (!candidates.isEmpty()) {
                BlockPos target = candidates.get(level.getRandom().nextInt(candidates.size()));
                lit(level, target);
            }
        }
    }

    private static void berrySpreadLit(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        for (int dx = -EXPLOSION_RADIUS; dx <= EXPLOSION_RADIUS; dx++) {
            for (int dy = -EXPLOSION_RADIUS; dy <= EXPLOSION_RADIUS; dy++) {
                for (int dz = -EXPLOSION_RADIUS; dz <= EXPLOSION_RADIUS; dz++) {
                    mPos.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (mPos.distSqr(pos) > EXPLOSION_RADIUS * EXPLOSION_RADIUS) continue;
                    if (isBerryJuiceBlock(level, mPos)) {
                        lit(level, mPos);
                    }
                }
            }
        }
    }

    // ========== 外部可调用工具方法 ==========

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

    public static void juiceInsideEffect(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (entity.tickCount % 5 != 0) return;
        if (entity instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(EffectRegister.BEERY_JUICE, 2400));
        }
        if (isCanLitEntity(entity) && checkNoLitGround(serverLevel, pos)) {
            lit(serverLevel, pos);
        }
    }

    // ========== 辅助方法 ==========

    private static Direction getFirstPerpendicular(Direction face) {
        if (face.getAxis() == Direction.Axis.Y) return Direction.NORTH;
        return Direction.UP;
    }

    private static boolean isBerryJuiceBlock(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(FluidRegister.GLOWING_BERRY_JUICE.block().get());
    }


    private static boolean checkNoLitGround(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    mPos.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    int state = getState(level, mPos);
                    if (state == STATE_LIT || state == STATE_COOLDOWN) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void doExplode(ServerLevel level, BlockPos pos) {
        playExplosionAnimation(level, pos);
        applyDamage(level, pos);
        consumeFluid(level, pos);
    }
}