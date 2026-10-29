package com.thelema.thrive.generic.blockentity.modifier.garden;

import com.thelema.thrive.generic.block.GardenBeeNest;
import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import com.thelema.thrive.generic.blockentity.GardenBeeNestBlockEntity;
import com.thelema.thrive.generic.blockentity.modifier.BiomeModifier;
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
import java.util.function.Predicate;

/** Garden 系转换的共享常量与静态工具。 */
final class GardenHelper {

    private GardenHelper() {}

    static final int TICKS_PER_RING = 20;
    static final int MAX_RING = 64;
    static final int CLEAR_DELAY = 1;
    static final int GARDEN_DELAY = 6;
    static final int BONE_MEAL_DELAY = 8;
    static final int BONE_MEAL_CHANCE = 18;
    static final int TREE_FREE_RADIUS = 7;
    static final int TREE_CHANCE = 50;

    static final ResourceKey<Biome> TARGET = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath("thrive", "garden"));
    static final ResourceLocation TREE_GARDEN =
            ResourceLocation.fromNamespaceAndPath("thrive", "biome_core_tree_garden");
    static final ResourceKey<ConfiguredFeature<?, ?>> FANCY_OAK = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, ResourceLocation.withDefaultNamespace("fancy_oak"));

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

    static void processClear(BiomeModifier self, ServerLevel level, BiomeCoreBlockEntity be,
                             int r, Predicate<Holder<Biome>> inRange) {
        BlockState air = Blocks.AIR.defaultBlockState();
        self.forRing(level, be, r, (p, s) -> {
            if (s.isAir()) return;
            if (!s.getFluidState().isEmpty()) return;
            if (!inRange.test(level.getBiome(p))) return;
            boolean plant = s.is(BlockTags.REPLACEABLE_BY_TREES)
                    || s.is(BlockTags.FLOWERS)
                    || s.is(BlockTags.SAPLINGS);
            boolean tree = s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES);
            boolean hive = s.getBlock() instanceof GardenBeeNest;
            if (!plant && !tree && !hive) return;
            self.set(level, p, air);
            self.markDirty(be, p);
        });
    }

    static void processGarden(BiomeModifier self, ServerLevel level, BiomeCoreBlockEntity be,
                              int r, Predicate<Holder<Biome>> inRange) {
        if (r <= TREE_FREE_RADIUS) return;
        RandomSource random = level.random;
        self.forRing(level, be, r, (p, s) -> {
            if (!s.is(Blocks.GRASS_BLOCK) && !s.is(BlockTags.DIRT)) return;
            if (!level.getBlockState(p.above()).isAir()) return;
            if (!level.canSeeSky(p.above())) return;
            if (!inRange.test(level.getBiome(p))) return;
            if (random.nextInt(TREE_CHANCE) != 0) return;
            if (hasNearbyLogs(level, p, 5)) return;
            BlockPos base = p.above();
            if (!growFancyOak(level, base)) return;
            for (BlockPos d : BlockPos.betweenClosed(
                    base.offset(-3, -1, -3), base.offset(3, 12, 3))) {
                self.markDirty(be, d);
            }
            plantFlowerCluster(self, level, be, base, random, inRange);
            hangGardenHive(self, level, be, base, random);
        });
    }

    static void processBoneMeal(BiomeModifier self, ServerLevel level, BiomeCoreBlockEntity be, int r) {
        if (r <= TREE_FREE_RADIUS) return;
        RandomSource random = level.random;
        self.forRing(level, be, r, (p, s) -> {
            if (!s.is(Blocks.GRASS_BLOCK)) return;
            if (!level.getBlockState(p.above()).isAir()) return;
            if (!level.canSeeSky(p.above())) return;
            if (random.nextInt(BONE_MEAL_CHANCE) != 0) return;
            if (!(s.getBlock() instanceof BonemealableBlock bonemealable)) return;
            if (!bonemealable.isValidBonemealTarget(level, p, s)) return;
            if (!bonemealable.isBonemealSuccess(level, random, p, s)) return;
            bonemealable.performBonemeal(level, random, p, s);
            for (BlockPos d : BlockPos.betweenClosed(
                    p.offset(-2, 0, -2), p.offset(2, 2, 2))) {
                self.markDirty(be, d);
            }
        });
    }

    static boolean growFancyOak(ServerLevel level, BlockPos pos) {
        var features = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
        return features.getOrThrow(FANCY_OAK).value()
                .place(level, level.getChunkSource().getGenerator(), level.random, pos);
    }

    static boolean hasNearbyLogs(ServerLevel level, BlockPos ground, int spacing) {
        for (BlockPos p : BlockPos.betweenClosed(
                ground.offset(-spacing, 0, -spacing),
                ground.offset(spacing, 12, spacing))) {
            if (level.getBlockState(p).is(BlockTags.LOGS)) return true;
        }
        return false;
    }

    static void plantFlowerCluster(BiomeModifier self, ServerLevel level, BiomeCoreBlockEntity be,
                                   BlockPos base, RandomSource random,
                                   Predicate<Holder<Biome>> inRange) {
        Block flower = FLOWERS[random.nextInt(FLOWERS.length)];
        int attempts = 6 + random.nextInt(5);
        for (int i = 0; i < attempts; i++) {
            int ox = random.nextInt(5) - 2;
            int oz = random.nextInt(5) - 2;
            tryPlaceFlower(self, level, be, base.offset(ox, 0, oz), flower, inRange);
        }
    }

    static void hangGardenHive(BiomeModifier self, ServerLevel level, BiomeCoreBlockEntity be,
                               BlockPos base, RandomSource random) {
        if (random.nextFloat() > 0.45F) return;
        for (int dy = 2; dy <= 5; dy++) {
            BlockPos trunk = base.above(dy);
            if (!level.getBlockState(trunk).is(BlockTags.LOGS)) continue;
            List<Direction> open = new ArrayList<>();
            for (Direction d : HORIZONTAL) {
                BlockPos hivePos = trunk.relative(d);
                if (level.getBlockState(hivePos).isAir()
                        && level.getBlockState(hivePos.relative(d)).isAir()) {
                    open.add(d);
                }
            }
            if (open.isEmpty()) continue;
            Direction facing = open.get(random.nextInt(open.size()));
            BlockPos hivePos = trunk.relative(facing);
            self.set(level, hivePos, BlockRegister.GARDEN_BEE_NEST.get().defaultBlockState()
                    .setValue(GardenBeeNest.FACING, facing));
            self.markDirty(be, hivePos);
            if (level.getBlockEntity(hivePos) instanceof GardenBeeNestBlockEntity hive) {
                int numBees = 1 + random.nextInt(2);
                for (int i = 0; i < numBees; i++) {
                    hive.storeBee(GardenBeeNestBlockEntity.Occupant.create(random.nextInt(599)));
                }
                hive.setChanged();
            }
            return;
        }
    }

    static void tryPlaceFlower(BiomeModifier self, ServerLevel level, BiomeCoreBlockEntity be,
                               BlockPos p, Block flower, Predicate<Holder<Biome>> inRange) {
        if (!inRange.test(level.getBiome(p))) return;
        if (!level.getBlockState(p).isAir()) return;
        BlockState below = level.getBlockState(p.below());
        if (!below.is(Blocks.GRASS_BLOCK) && !below.is(BlockTags.DIRT)) return;
        if (flower == Blocks.LILAC || flower == Blocks.PEONY
                || flower == Blocks.ROSE_BUSH || flower == Blocks.SUNFLOWER) {
            if (!level.getBlockState(p.above()).isAir()) return;
            self.set(level, p, flower.defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
            self.set(level, p.above(), flower.defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            self.markDirty(be, p);
            return;
        }
        self.set(level, p, flower.defaultBlockState());
        self.markDirty(be, p);
    }
}