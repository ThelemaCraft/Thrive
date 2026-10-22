package com.thelema.thrive.generic.blockentity.convertor;

import com.thelema.thrive.generic.block.GardenBeeNest;
import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import com.thelema.thrive.generic.blockentity.GardenBeeNestBlockEntity;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * 森林 → 花园。按圈推进，每一圈做四件事：
 * <ul>
 *   <li>改写当前圈的群系；</li>
 *   <li>清理 {@link #CLEAR_DELAY} 圈前的花草、树叶、树木；</li>
 *   <li>在 {@link #GARDEN_DELAY} 圈前（已清干净的地面）种高大橡树、撒花、挂蜂巢；</li>
 *   <li>在 {@link #BONE_MEAL_DELAY} 圈前随机撒骨粉，补出原版的草与花。</li>
 * </ul>
 * 清理和造景错开多圈，避免后一圈的清理把刚种好的树冠削掉。
 */
public class ForestToGarden implements Convertor {

    static final int TICKS_PER_RING = 20;   // 1 秒 1 环
    static final int MAX_RING = 64;         // 4 chunk 半径

    /** 清理滞后当前圈的圈数。 */
    static final int CLEAR_DELAY = 1;
    /** 造景滞后当前圈的圈数。高大橡树树冠半径约 4，留 5 圈缓冲。 */
    static final int GARDEN_DELAY = 6;
    /** 骨粉阶段滞后当前圈的圈数，排在造景之后。 */
    static final int BONE_MEAL_DELAY = 8;
    /** 每格草地施加骨粉的概率（1/N）。 */
    static final int BONE_MEAL_CHANCE = 18;

    /** 中心这个半径内不种树，留给群系之树。 */
    static final int TREE_FREE_RADIUS = 7;

    /** 每列种树的概率（1/N）。 */
    static final int TREE_CHANCE = 50;

    static final ResourceKey<Biome> SOURCE = ResourceKey.create(
            Registries.BIOME, ResourceLocation.withDefaultNamespace("forest"));
    static final ResourceKey<Biome> TARGET = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath("thrive", "garden"));

    static final ResourceLocation TREE_GARDEN =
            ResourceLocation.fromNamespaceAndPath("thrive", "biome_core_tree_garden");

    static final ResourceKey<ConfiguredFeature<?, ?>> FANCY_OAK = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, ResourceLocation.withDefaultNamespace("fancy_oak"));

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
            if (ring <= MAX_RING) {
                rewriteBiome(level, be, ring, SOURCE, TARGET);
            }
            if (ring >= CLEAR_DELAY && ring - CLEAR_DELAY <= MAX_RING) {
                processClear(level, be, ring - CLEAR_DELAY);
            }
            if (ring >= GARDEN_DELAY && ring - GARDEN_DELAY <= MAX_RING) {
                processGarden(level, be, ring - GARDEN_DELAY);
            }
            if (ring >= BONE_MEAL_DELAY && ring - BONE_MEAL_DELAY <= MAX_RING) {
                processBoneMeal(level, be, ring - BONE_MEAL_DELAY);
            }
        }

        if (tick % 10 == 0) flushDirty(level, be);

        if (ring >= MAX_RING + BONE_MEAL_DELAY + 1) {
            flushDirty(level, be);
            be.resetConversion();
            replaceStructure(level, be, TREE_GARDEN);
            return;
        }

        advanceTick(be);
    }

    // ─────────────────── 阶段 1：清理 ───────────────────

    /** 清掉这一圈的全部花草、树叶、树木、原版蜂巢。 */
    void processClear(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        BlockState air = Blocks.AIR.defaultBlockState();

        forRing(level, be, r, (p, s) -> {
            if (s.isAir()) return;
            if (!s.getFluidState().isEmpty()) return; // 别把水/岩浆当花草清掉
            if (!isForestOrGarden(level, p)) return;

            boolean plant = s.is(BlockTags.REPLACEABLE_BY_TREES)
                    || s.is(BlockTags.FLOWERS)
                    || s.is(BlockTags.SAPLINGS);
            boolean tree = s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES);
            boolean hive = s.getBlock() instanceof GardenBeeNest;
            if (!plant && !tree && !hive) return;

            set(level, p, air);
            markDirty(be, p);
        });
    }

    // ─────────────────── 阶段 2：造景 ───────────────────

    /** 在已经清干净的一圈里种高大橡树，树下撒花，树干上挂蜂巢。 */
    void processGarden(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        if (r <= TREE_FREE_RADIUS) return; // 中心留给群系之树

        RandomSource random = level.random;

        forRing(level, be, r, (p, s) -> {
            if (!s.is(Blocks.GRASS_BLOCK) && !s.is(BlockTags.DIRT)) return;
            if (!level.getBlockState(p.above()).isAir()) return;
            if (!level.canSeeSky(p.above())) return; // 只种在地表，别种进洞穴
            if (!isForestOrGarden(level, p)) return;
            if (random.nextInt(TREE_CHANCE) != 0) return;
            if (hasNearbyLogs(level, p, 5)) return;

            BlockPos base = p.above();
            if (!growFancyOak(level, base)) return;

            // 树冠可能跨 chunk，整片标脏
            for (BlockPos d : BlockPos.betweenClosed(
                    base.offset(-3, -1, -3), base.offset(3, 12, 3))) {
                markDirty(be, d);
            }
            plantFlowerCluster(level, be, base, random);
            hangGardenHive(level, be, base, random);
        });
    }

    // ─────────────────── 阶段 3：骨粉 ───────────────────

    /** 在草地上随机撒骨粉，长出原版的草与花。 */
    void processBoneMeal(ServerLevel level, BiomeCoreBlockEntity be, int r) {
        if (r <= TREE_FREE_RADIUS) return; // 中心留给群系之树
        RandomSource random = level.random;

        forRing(level, be, r, (p, s) -> {
            if (!s.is(Blocks.GRASS_BLOCK)) return;
            if (!level.getBlockState(p.above()).isAir()) return;
            if (!level.canSeeSky(p.above())) return;
            if (random.nextInt(BONE_MEAL_CHANCE) != 0) return;
            if (!(s.getBlock() instanceof BonemealableBlock bonemealable)) return;
            if (!bonemealable.isValidBonemealTarget(level, p, s)) return;
            if (!bonemealable.isBonemealSuccess(level, random, p, s)) return;

            bonemealable.performBonemeal(level, random, p, s);

            // 骨粉会往四周长，整片标脏
            for (BlockPos d : BlockPos.betweenClosed(
                    p.offset(-2, 0, -2), p.offset(2, 2, 2))) {
                markDirty(be, d);
            }
        });
    }

    boolean growFancyOak(ServerLevel level, BlockPos pos) {
        var features = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
        return features.getOrThrow(FANCY_OAK).value()
                .place(level, level.getChunkSource().getGenerator(), level.random, pos);
    }

    /** 周围已有树干就跳过，保证树距。 */
    boolean hasNearbyLogs(ServerLevel level, BlockPos ground, int spacing) {
        for (BlockPos p : BlockPos.betweenClosed(
                ground.offset(-spacing, 0, -spacing),
                ground.offset(spacing, 12, spacing))) {
            if (level.getBlockState(p).is(BlockTags.LOGS)) return true;
        }
        return false;
    }

    /** 树干脚下撒同种花簇。 */
    void plantFlowerCluster(ServerLevel level, BiomeCoreBlockEntity be, BlockPos base, RandomSource random) {
        Block flower = FLOWERS[random.nextInt(FLOWERS.length)];
        int attempts = 6 + random.nextInt(5);
        for (int i = 0; i < attempts; i++) {
            int ox = random.nextInt(5) - 2;
            int oz = random.nextInt(5) - 2;
            tryPlaceFlower(level, be, base.offset(ox, 0, oz), flower);
        }
    }

    /** 在树干侧面挂花园蜂巢，门朝外，内部塞 1~2 只蜜蜂。 */
    void hangGardenHive(ServerLevel level, BiomeCoreBlockEntity be, BlockPos base, RandomSource random) {
        if (random.nextFloat() > 0.45F) return;

        for (int dy = 2; dy <= 5; dy++) {
            BlockPos trunk = base.above(dy);
            if (!level.getBlockState(trunk).is(BlockTags.LOGS)) continue;

            List<Direction> open = new ArrayList<>();
            for (Direction d : HORIZONTAL) {
                BlockPos hivePos = trunk.relative(d);
                // 紧贴树干、位置是空气、门外也是空气
                if (level.getBlockState(hivePos).isAir()
                        && level.getBlockState(hivePos.relative(d)).isAir()) {
                    open.add(d);
                }
            }
            if (open.isEmpty()) continue;

            Direction facing = open.get(random.nextInt(open.size()));
            BlockPos hivePos = trunk.relative(facing);
            set(level, hivePos, BlockRegister.GARDEN_BEE_NEST.get().defaultBlockState()
                    .setValue(GardenBeeNest.FACING, facing));
            markDirty(be, hivePos);

            if (level.getBlockEntity(hivePos) instanceof GardenBeeNestBlockEntity hive) {
                // 生成 1 到 2 只蜜蜂
                int numBees = 1 + random.nextInt(2);
                for (int i = 0; i < numBees; i++) {
                    // 随机生成它们已经在里面待了多少 tick（原版范围通常是 0~599）
                    int ticksInHive = random.nextInt(599);
                    // 使用你自定义实体里的 create 方法生成占位数据并塞进去
                    hive.storeBee(GardenBeeNestBlockEntity.Occupant.create(ticksInHive));
                }
                hive.setChanged(); // 标记方块实体已更新，确保保存到存档
            }
            return;
        }
    }

    /** 尝试在单点放花。双高花需上方空气。 */
    void tryPlaceFlower(ServerLevel level, BiomeCoreBlockEntity be, BlockPos p, Block flower) {
        if (!isForestOrGarden(level, p)) return;
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

    /** 群系改写是 4×4×4 量化的，会向下一圈渗 3 格，所以 source/target 都算数。 */
    static boolean isForestOrGarden(ServerLevel level, BlockPos p) {
        Holder<Biome> biome = level.getBiome(p);
        return biome.is(SOURCE) || biome.is(TARGET);
    }
}
