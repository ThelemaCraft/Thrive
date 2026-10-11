package com.thelema.thrive.generic.blockentity.convertor;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.List;

public class ForestToGarden implements Convertor {

    static final int TICKS_PER_RING = 20;
    static final int MAX_RING = 64;

    static final ResourceKey<Biome> SOURCE = ResourceKey.create(
            Registries.BIOME, ResourceLocation.withDefaultNamespace("forest"));
    static final ResourceKey<Biome> TARGET = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath("thrive", "garden"));

    static final ResourceLocation TREE_FOREST =
            ResourceLocation.fromNamespaceAndPath("thrive", "biome_core_tree_forest");
    static final ResourceLocation TREE_GARDEN =
            ResourceLocation.fromNamespaceAndPath("thrive", "biome_core_tree_garden");

    /** 草地撒花池。 */
    static final Block[] FLOWERS = {
            Blocks.DANDELION, Blocks.POPPY, Blocks.OXEYE_DAISY,
            Blocks.CORNFLOWER, Blocks.AZURE_BLUET, Blocks.ALLIUM,
            Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP,
            Blocks.LILY_OF_THE_VALLEY, Blocks.LILAC, Blocks.PEONY,
            Blocks.ROSE_BUSH, Blocks.SUNFLOWER
    };

    static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    @Override
    public void tick(ServerLevel level, BiomeCoreBlockEntity be) {
        long tick = tick(be);
        int ring = (int) (tick / TICKS_PER_RING);

        if (tick % TICKS_PER_RING == 0) {
            rewriteBiome(level, be, Math.min(ring, MAX_RING), SOURCE, TARGET);
            if (ring >= 1) hangBeehives(level, be, Math.min(ring - 1, MAX_RING));
            if (ring >= 2) plantFlowers(level, be, Math.min(ring - 2, MAX_RING));
        }

        if (tick % 10 == 0) flushDirty(level, be);

        if (ring >= MAX_RING + 2) {
            flushDirty(level, be);
            be.resetConversion();
            replaceStructure(level, be, TREE_GARDEN);
            return;
        }

        advanceTick(be);
    }

    // ─────────────────── 阶段 1：挂蜂巢 ───────────────────

    /** 在树叶正下方挂蜂巢，朝向随机水平。约 1/100 密度。 */
    void hangBeehives(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        RandomSource random = level.random;

        forRing(level, be, r, (p, s) -> {
            if (!level.getBiome(p).is(TARGET)) return;
            if (random.nextInt(50) != 0) return;
            if (!level.getBlockState(p).isAir()) return;

            // 顶上必须是树叶或木头
            BlockState above = level.getBlockState(p.above());
            if (!above.is(BlockTags.LEAVES) && !above.is(BlockTags.LOGS)) return;

            // 收集可用朝向：邻接不是木头
            List<Direction> open = new ArrayList<>();
            for (Direction d : HORIZONTAL) {
                if (!level.getBlockState(p.relative(d)).is(BlockTags.LOGS)) {
                    open.add(d);
                }
            }
            if (open.isEmpty()) return;   // 四面全是木头，跳过

            Direction dir = open.get(random.nextInt(open.size()));
            set(level, p, Blocks.BEE_NEST.defaultBlockState()
                    .setValue(BeehiveBlock.FACING, dir));
            markDirty(be, p);

            // 塞 0~3 只蜜蜂
            if (level.getBlockEntity(p) instanceof BeehiveBlockEntity hive) {
                int count = random.nextInt(4);
                for (int i = 0; i < count; i++) {
                    Bee bee = EntityType.BEE.create(level);
                    if (bee != null) hive.addOccupant(bee);
                }
            }
        });
    }

    // ─────────────────── 阶段 2：撒花 ───────────────────

    /** 在草地表面撒花。约 1/20 密度。 */
    void plantFlowers(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        RandomSource random = level.random;

        forRing(level, be, r, (p, s) -> {
            if (!level.getBiome(p).is(TARGET)) return;
            if (!s.isAir()) return;
            if (random.nextInt(50) != 0) return;   // patch 中心

            // 选定花种，整个 patch 同种
            Block flower = FLOWERS[random.nextInt(FLOWERS.length)];

            // 半径 0 或 1：单朵 或 3×3
            int radius = random.nextInt(2);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (random.nextFloat() > 0.6f) continue;
                    tryPlaceFlower(level, be, p.offset(dx, 0, dz), flower);
                }
            }
        });
    }

    /** 尝试在单点放花。双高花需上方空气。 */
    void tryPlaceFlower(ServerLevel level, BiomeCoreBlockEntity be, BlockPos p, Block flower) {
        if (!level.getBiome(p).is(TARGET)) return;
        if (!level.getBlockState(p).isAir()) return;

        BlockState below = level.getBlockState(p.below());
        if (!below.is(Blocks.GRASS_BLOCK) && !below.is(BlockTags.DIRT)) return;

        if (flower == Blocks.LILAC || flower == Blocks.PEONY
                || flower == Blocks.ROSE_BUSH || flower == Blocks.SUNFLOWER) {
            if (!level.getBlockState(p.above()).isAir()) return;
            set(level, p, flower.defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
            set(level, p.above(), flower.defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            markDirty(be, p);
            return;
        }
        set(level, p, flower.defaultBlockState());
        markDirty(be, p);
    }
}