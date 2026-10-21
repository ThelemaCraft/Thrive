package com.thelema.thrive.generic.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public class Lake extends Feature<Lake.LakeConfig> {

    private static final BlockState AIR = Blocks.CAVE_AIR.defaultBlockState();

    public Lake(Codec<LakeConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<LakeConfig> context) {
        return placeAt(context.level(), context.origin(), context.random(),
                context.config().fluid(), context.config().barrier());
    }

    /**
     * 静态放置方法，供运行时调用。
     */
    public static boolean placeAt(LevelAccessor level, BlockPos origin, RandomSource random,
                                   BlockState fluid, BlockState barrier) {
        if (origin.getY() <= level.getMinBuildHeight() + 4) {
            return false;
        }

        BlockPos start = origin.below(4);
        // 湖泊范围内若包含 BiomeCore，整个放弃，避免破坏
        for (BlockPos p : BlockPos.betweenClosed(start, start.offset(15, 7, 15))) {
            if (level.getBlockState(p).is(BlockRegister.BIOME_CORE.get())) return false;
        }
        boolean[] lake = new boolean[16 * 16 * 8];

        int blobCount = random.nextInt(4) + 4;
        for (int b = 0; b < blobCount; b++) {
            double rx = random.nextDouble() * 6.0 + 3.0;
            double ry = random.nextDouble() * 4.0 + 2.0;
            double rz = random.nextDouble() * 6.0 + 3.0;
            double cx = random.nextDouble() * (16.0 - rx - 2.0) + 1.0 + rx / 2.0;
            double cy = random.nextDouble() * (8.0 - ry - 4.0) + 2.0 + ry / 2.0;
            double cz = random.nextDouble() * (16.0 - rz - 2.0) + 1.0 + rz / 2.0;

            for (int x = 1; x < 15; x++) {
                for (int z = 1; z < 15; z++) {
                    for (int y = 1; y < 7; y++) {
                        double dx = (x - cx) / (rx / 2.0);
                        double dy = (y - cy) / (ry / 2.0);
                        double dz = (z - cz) / (rz / 2.0);
                        if (dx * dx + dy * dy + dz * dz < 1.0) {
                            lake[(x * 16 + z) * 8 + y] = true;
                        }
                    }
                }
            }
        }

        // 边界校验
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 8; y++) {
                    boolean isEdge = !lake[(x * 16 + z) * 8 + y] && (
                            x < 15 && lake[((x + 1) * 16 + z) * 8 + y] ||
                                    x > 0 && lake[((x - 1) * 16 + z) * 8 + y] ||
                                    z < 15 && lake[(x * 16 + z + 1) * 8 + y] ||
                                    z > 0 && lake[(x * 16 + z - 1) * 8 + y] ||
                                    y < 7 && lake[(x * 16 + z) * 8 + y + 1] ||
                                    y > 0 && lake[(x * 16 + z) * 8 + y - 1]);
                    if (!isEdge) continue;

                    BlockPos p = start.offset(x, y, z);
                    BlockState s = level.getBlockState(p);

                    if (y >= 4 && s.liquid()) return false;
                    if (y < 4 && !s.isSolid() && !s.is(fluid.getBlock())) return false;
                }
            }
        }

        // 清掉湖泊顶部上方紧邻的植物，避免它们因失去支撑掉落
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int topY = -1;
                for (int y = 7; y >= 0; y--) {
                    if (lake[(x * 16 + z) * 8 + y]) {
                        topY = y;
                        break;
                    }
                }
                if (topY < 0) continue;

                // 向上最多检查 2 格：单层植物 1 格，高草 2 格
                for (int dy = 1; dy <= 2; dy++) {
                    BlockPos p = start.offset(x, topY + dy, z);
                    if (p.getY() >= level.getMaxBuildHeight()) break;
                    BlockState s = level.getBlockState(p);
                    if (s.isAir()) continue;
                    if (s.getBlock() instanceof BushBlock) {
                        level.setBlock(p, AIR, 2);
                    } else {
                        break;   // 撞到实心方块就停
                    }
                }
            }
        }

        // 填充
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 8; y++) {
                    if (!lake[(x * 16 + z) * 8 + y]) continue;

                    BlockPos p = start.offset(x, y, z);
                    BlockState current = level.getBlockState(p);
                    if (current.is(BlockTags.FEATURES_CANNOT_REPLACE)) continue;

                    boolean aboveWater = y >= 4;
                    level.setBlock(p, aboveWater ? AIR : fluid, 2);
                }
            }
        }

        // 屏障层
        if (!barrier.isAir()) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 8; y++) {
                        boolean isEdge = !lake[(x * 16 + z) * 8 + y] && (
                                x < 15 && lake[((x + 1) * 16 + z) * 8 + y] ||
                                        x > 0 && lake[((x - 1) * 16 + z) * 8 + y] ||
                                        z < 15 && lake[(x * 16 + z + 1) * 8 + y] ||
                                        z > 0 && lake[(x * 16 + z - 1) * 8 + y] ||
                                        y < 7 && lake[(x * 16 + z) * 8 + y + 1] ||
                                        y > 0 && lake[(x * 16 + z) * 8 + y - 1]);
                        if (!isEdge) continue;

                        if (y >= 4 && random.nextInt(2) == 0) continue;

                        BlockPos p = start.offset(x, y, z);
                        BlockState s = level.getBlockState(p);
                        if (s.isSolid() && !s.is(BlockTags.LAVA_POOL_STONE_CANNOT_REPLACE)) {
                            level.setBlock(p, barrier, 2);
                        }
                    }
                }
            }
        }

        return true;
    }

    public record LakeConfig(BlockState fluid, BlockState barrier) implements FeatureConfiguration {

        public static final Codec<LakeConfig> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                BlockState.CODEC.fieldOf("fluid").forGetter(LakeConfig::fluid),
                BlockState.CODEC.fieldOf("barrier").forGetter(LakeConfig::barrier)
        ).apply(inst, LakeConfig::new));
    }
}