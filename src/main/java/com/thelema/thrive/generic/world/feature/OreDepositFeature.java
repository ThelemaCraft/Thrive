package com.thelema.thrive.generic.world.feature;

import com.mojang.serialization.Codec;
import com.thelema.thrive.generic.world.feature.config.OreDepositConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OreDepositFeature extends Feature<OreDepositConfig> {

    public OreDepositFeature(Codec<OreDepositConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<OreDepositConfig> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        OreDepositConfig config = context.config();

        // 1. 调整中心位置到苔藓表面（或任意固体表面上方）
        BlockPos center = findMossSurface(level, origin, random);
        if (center == null) {
            return false; // 找不到合适位置，放弃生成
        }

        // 2. 生成实心石球（半径 2~3）
        int radius = 2 + random.nextInt(2);
        List<BlockPos> stonePositions = generateStoneSphere(center, radius, random);

        // 3. 放置石头
        for (BlockPos p : stonePositions) {
            if (canPlaceAt(level, p)) {
                level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
            }
        }

        // 4. 将内部石头替换为矿物
        List<BlockPos> innerPositions = new ArrayList<>();
        for (BlockPos p : stonePositions) {
            if (isInterior(p, stonePositions)) {
                innerPositions.add(p);
            }
        }

        Collections.shuffle(innerPositions);
        int maxOre = 4 + random.nextInt(4);      // 4~7 个矿物
        int targetOre = Math.min(maxOre, innerPositions.size());

        for (int i = 0; i < targetOre; i++) {
            BlockPos p = innerPositions.get(i);
            Block ore = selectOreBlock(random, config);
            if (ore != null) {
                level.setBlock(p, ore.defaultBlockState(), 3);
            }
        }

        // ========== 新增：顶部装饰 ==========
        // 5. 找出所有暴露在空气中的顶部石头表面（上方为空气/水）
        List<BlockPos> topPositions = new ArrayList<>();
        for (BlockPos p : stonePositions) {
            BlockPos above = p.above();
            // 上方不是球体的一部分，且可替换（空气或水）
            if (!stonePositions.contains(above)) {
                BlockState aboveState = level.getBlockState(above);
                if (aboveState.isAir() || aboveState.getBlock() == Blocks.WATER) {
                    topPositions.add(p);
                }
            }
        }

        // 6. 装饰每个顶部位置
        for (BlockPos p : topPositions) {
            // 只有当前确实是石头才处理（避免被之前步骤更改）
            if (level.getBlockState(p).getBlock() != Blocks.STONE) {
                continue;
            }

            // 6a. 是否覆盖苔藓块（概率 60%）
            if (random.nextFloat() < 0.6f) {
                level.setBlock(p, Blocks.MOSS_BLOCK.defaultBlockState(), 3);
                BlockPos abovePos = p.above();

                // 检查上方是否仍为空（未被其他方块占用）
                if (level.getBlockState(abovePos).isAir()) {
                    // 6b. 是否放苔藓地毯（概率 50%）
                    if (random.nextFloat() < 0.5f) {
                        level.setBlock(abovePos, Blocks.MOSS_CARPET.defaultBlockState(), 3);
                    } else {
                        // 6c. 没放地毯 → 尝试放树苗（概率 30%）
                        if (random.nextFloat() < 0.3f) {
                            Block sapling = random.nextBoolean() ?
                                    Blocks.AZALEA : Blocks.FLOWERING_AZALEA;
                            // 确保上方仍是空气（地毯没放，上方应该是空气）
                            // 树苗需要下方为苔藓块（已满足）且上方为空气
                            level.setBlock(abovePos, sapling.defaultBlockState(), 3);
                        }
                    }
                }
                // 若上方不是空气（例如水），则什么都不做
            }
            // 若不覆盖苔藓块，则保留石头原样
        }

        return !stonePositions.isEmpty();
    }

    // ==================== 辅助方法 ====================

    /**
     * 寻找合适的苔藓表面。
     * 从 origin 开始向下搜索，直到遇到苔藓块（或任意固体非流体方块），
     * 返回该方块上方 1~2 格的位置（让球体半嵌入）。
     */
    private BlockPos findMossSurface(WorldGenLevel level, BlockPos origin, RandomSource random) {
        BlockPos.MutableBlockPos pos = origin.mutable();
        for (int i = 0; i < 20; i++) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() == Blocks.MOSS_BLOCK) {
                int offsetY = 1 + random.nextInt(2); // 1 或 2
                return pos.above(offsetY);
            }
            // 如果是空气或水，继续向下
            if (state.isAir() || state.getBlock() == Blocks.WATER) {
                pos.move(0, -1, 0);
                continue;
            }
            // 遇到其他固体方块（石头、泥土等）也允许生成（兼容非苔藓地表）
            if (!state.isAir() && state.getBlock() != Blocks.WATER) {
                return pos.above(1);
            }
        }
        return null;
    }

    /**
     * 生成实心石球，表面有 5% 随机缺失制造自然感，但内部完全密实。
     */
    private List<BlockPos> generateStoneSphere(BlockPos center, int radius, RandomSource random) {
        List<BlockPos> positions = new ArrayList<>();
        int radiusSq = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq <= radiusSq) {
                        // 只有表面层（距离边界≤1）才随机跳过，保证内部实心
                        boolean isSurface = distSq > (radiusSq - 1);
                        if (isSurface && random.nextFloat() < 0.05) {
                            continue;
                        }
                        positions.add(center.offset(dx, dy, dz));
                    }
                }
            }
        }
        return positions;
    }

    /**
     * 检查位置是否可替换（允许替换空气、水、苔藓或石头）。
     */
    private boolean canPlaceAt(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.getBlock() == Blocks.WATER ||
                state.getBlock() == Blocks.MOSS_BLOCK || state.getBlock() == Blocks.STONE;
    }

    /**
     * 判断是否为内部位置（至少有 4 个相邻块在列表中）。
     */
    private boolean isInterior(BlockPos pos, List<BlockPos> allPositions) {
        int neighbors = 0;
        for (BlockPos neighbor : List.of(
                pos.above(), pos.below(),
                pos.north(), pos.south(),
                pos.east(), pos.west()
        )) {
            if (allPositions.contains(neighbor)) {
                neighbors++;
            }
        }
        return neighbors >= 4;
    }

    /**
     * 根据配置权重随机选择矿石方块。
     */
    private Block selectOreBlock(RandomSource random, OreDepositConfig config) {
        int totalWeight = config.oreWeight() + config.rawWeight();
        if (totalWeight <= 0) return null;

        List<Block> allBlocks = new ArrayList<>();
        for (int i = 0; i < config.oreWeight(); i++) {
            allBlocks.addAll(config.oreBlocks());
        }
        for (int i = 0; i < config.rawWeight(); i++) {
            allBlocks.addAll(config.rawBlocks());
        }

        return allBlocks.get(random.nextInt(allBlocks.size()));
    }
}