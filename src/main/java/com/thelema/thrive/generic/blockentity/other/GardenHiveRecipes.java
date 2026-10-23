package com.thelema.thrive.generic.blockentity.other;

import com.thelema.thrive.register.ItemRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 无序三元组配方：三种花 → 一种蜂蜜。允许同种花重复。 */
public class GardenHiveRecipes {

    /** 花盆/花朵扫描半径（切比雪夫）。3 = 完整 7×7×7 立方体。 */
    public static final int FLOWER_SCAN_RADIUS = 3;

    public record Recipe(Item a, Item b, Item c, Item honey) {}

    /** 三朵同种花 → 对应的纯蜜。 */
    private static Recipe pure(Item flower, Item honey) {
        return new Recipe(flower, flower, flower, honey);
    }

    public static final List<Recipe> RECIPE = List.of(
            // 纯蜜配方
            pure(Items.DANDELION,            ItemRegister.DANDELION_HONEY.get()),
            pure(Items.POPPY,                ItemRegister.POPPY_HONEY.get()),
            pure(Items.BLUE_ORCHID,          ItemRegister.BLUE_ORCHID_HONEY.get()),
            pure(Items.ALLIUM,               ItemRegister.ALLIUM_HONEY.get()),
            pure(Items.AZURE_BLUET,          ItemRegister.AZURE_BLUET_HONEY.get()),
            pure(Items.RED_TULIP,            ItemRegister.RED_TULIP_HONEY.get()),
            pure(Items.ORANGE_TULIP,         ItemRegister.ORANGE_TULIP_HONEY.get()),
            pure(Items.WHITE_TULIP,          ItemRegister.WHITE_TULIP_HONEY.get()),
            pure(Items.PINK_TULIP,           ItemRegister.PINK_TULIP_HONEY.get()),
            pure(Items.OXEYE_DAISY,          ItemRegister.OXEYE_DAISY_HONEY.get()),
            pure(Items.CORNFLOWER,           ItemRegister.CORNFLOWER_HONEY.get()),
            pure(Items.LILY_OF_THE_VALLEY,   ItemRegister.LILY_OF_THE_VALLEY_HONEY.get()),
            pure(Items.WITHER_ROSE,          ItemRegister.WITHER_ROSE_HONEY.get()),
            pure(Items.TORCHFLOWER,          ItemRegister.TORCHFLOWER_HONEY.get()),
            pure(Items.SUNFLOWER,            ItemRegister.SUNFLOWER_HONEY.get()),
            pure(Items.LILAC,                ItemRegister.LILAC_HONEY.get()),
            pure(Items.PEONY,                ItemRegister.PEONY_HONEY.get()),
            pure(Items.ROSE_BUSH,            ItemRegister.ROSE_BUSH_HONEY.get()),
            pure(Items.PITCHER_PLANT,        ItemRegister.PITCHER_PLANT_HONEY.get()),
            pure(Items.FLOWERING_AZALEA_LEAVES, ItemRegister.FLOWERING_AZALEA_LEAVES_HONEY.get()),
            pure(Items.FLOWERING_AZALEA,     ItemRegister.FLOWERING_AZALEA_HONEY.get()),
            pure(Items.MANGROVE_PROPAGULE,   ItemRegister.MANGROVE_PROPAGULE_HONEY.get()),
            pure(Items.CHERRY_LEAVES,        ItemRegister.CHERRY_LEAVES_HONEY.get()),
            pure(Items.PINK_PETALS,          ItemRegister.PINK_PETALS_HONEY.get()),
            pure(Items.CHORUS_FLOWER,        ItemRegister.CHORUS_FLOWER_HONEY.get()),
            pure(Items.SPORE_BLOSSOM,        ItemRegister.SPORE_BLOSSOM_HONEY.get())
    );

    /** 在蜂巢周围找“能凑齐、且三朵花最紧凑”的配方；凑不出就用原版蜂蜜瓶。 */
    public static Item pickHoney(Level level, BlockPos pos) {
        List<Flower> flowers = collectFlowers(level, pos);

        Item best = null;
        double bestWorstDist = Double.MAX_VALUE;

        for (Recipe recipe : RECIPE) {
            Map<Item, Integer> need = new HashMap<>();
            need.merge(recipe.a(), 1, Integer::sum);
            need.merge(recipe.b(), 1, Integer::sum);
            need.merge(recipe.c(), 1, Integer::sum);

            double worstDist = 0.0;
            for (Flower flower : flowers) {
                int left = need.getOrDefault(flower.item(), 0);
                if (left <= 0) continue;

                need.put(flower.item(), left - 1);
                worstDist = Math.max(worstDist, flower.dist());
                if (need.values().stream().allMatch(v -> v == 0)) break;
            }

            if (need.values().stream().allMatch(v -> v == 0) && worstDist < bestWorstDist) {
                bestWorstDist = worstDist;
                best = recipe.honey();
            }
        }
        return best != null ? best : Items.HONEY_BOTTLE;
    }

    /** 7×7×7 内的全部花盆/花朵，按距离排序；双高花只算下半段。 */
    private static List<Flower> collectFlowers(Level level, BlockPos pos) {
        List<Flower> found = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (int dx = -FLOWER_SCAN_RADIUS; dx <= FLOWER_SCAN_RADIUS; dx++)
            for (int dy = -FLOWER_SCAN_RADIUS; dy <= FLOWER_SCAN_RADIUS; dy++)
                for (int dz = -FLOWER_SCAN_RADIUS; dz <= FLOWER_SCAN_RADIUS; dz++) {
                    p.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!level.isLoaded(p)) continue;

                    BlockState state = level.getBlockState(p);
                    Item flower = null;
                    if (state.getBlock() instanceof FlowerPotBlock pot) {
                        if (pot.getPotted() != Blocks.AIR) flower = pot.getPotted().asItem();
                    } else if (state.is(BlockTags.FLOWERS) && !state.is(BlockTags.LEAVES)) {
                        if (state.hasProperty(DoublePlantBlock.HALF)
                                && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
                            continue;
                        }
                        flower = state.getBlock().asItem();
                    }

                    if (flower == null || flower == Items.AIR) continue;
                    found.add(new Flower(p.distSqr(pos), flower));
                }

        found.sort(Comparator.comparingDouble(Flower::dist));
        return found;
    }

    private record Flower(double dist, Item item) {}
}
