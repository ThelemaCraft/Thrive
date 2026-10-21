package com.thelema.thrive.generic.blockentity;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.block.GardenHive;
import com.thelema.thrive.generic.blockentity.other.GardenHiveRecipes;
import com.thelema.thrive.generic.entity.GardenBee;
import com.thelema.thrive.register.BlockEntityRegister;
import com.thelema.thrive.register.BlockRegister;
import com.thelema.thrive.register.EntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GardenHiveBlockEntity extends BlockEntity {

    public static final int MAX_BEES = 3;
    public static final int MAX_NECTAR = 5;
    public static final int MAX_HONEY = 5;

    /** 蜜蜂在巢内工作所需时间（2400 tick = 2 分钟）。 */
    public static final int NEST_TICKS = 2400;

    /** 花盆/花朵扫描半径（切比雪夫）。3 = 完整 7×7×7 立方体。 */
    public static final int FLOWER_SCAN_RADIUS = 3;

    /** 调试开关：true 时把每次酿蜜的扫描与配方匹配打到日志。 */
    public static final boolean DEBUG_BREW = true;

    public int nectarLevel = 0;
    public final Item[] honey = new Item[5];
    public int occupantCount = 0;

    /** 巢内蜜蜂工作倒计时 */
    public int nestTimer = 0;

    /** 上一 tick 是否白天。用于检测"天亮那一刻"。 */
    private boolean wasDay = false;


    public GardenHiveBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegister.GARDEN_HIVE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GardenHiveBlockEntity be) {
        if (level.isClientSide) return;

        // 1. 巢内全局计时：只要里面有蜜蜂，就开始倒计时
        if (be.occupantCount > 0) {
            be.nestTimer++;

            // 计时到了
            if (be.nestTimer >= NEST_TICKS) {
                be.nestTimer = 0; // 进度条重置
                be.occupantCount--; // 消耗一只蜜蜂

                if (be.nectarLevel < MAX_NECTAR) {
                    be.nectarLevel++;
                }

                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);

                // 直接放出一只（无论白天黑夜，原版如果在夜里憋完了也会被放出来，没粉了它会自己再进去）
                be.releaseOneBee(level, pos);
            }
        }

        // 2. 天亮那一刻结算：白天刚出现时，花粉满就瞬间酿好，不走进度条
        boolean isDay = level.isDay() && !level.isRaining();
        if (isDay && !be.wasDay && be.nectarLevel >= MAX_NECTAR && be.honeyCount() < MAX_HONEY) {
            be.finishBrew(level, pos);
            level.setBlock(pos, state.setValue(GardenHive.HONEY_LEVEL, 5), 3);
        }
        be.wasDay = isDay;
    }

    private void finishBrew(Level level, BlockPos pos) {
        List<Flower> flowers = collectFlowers(level, pos);

        if (DEBUG_BREW) {
            ThriveMod.LOGGER.info("[GardenHive] brew at {} nectar={} honey={}/{} flowers={}",
                    pos, nectarLevel, honeyCount(), MAX_HONEY, flowers.size());
            for (int i = 0; i < Math.min(flowers.size(), 12); i++) {
                Flower flower = flowers.get(i);
                ThriveMod.LOGGER.info("[GardenHive]   #{} {} dist={}",
                        i + 1, BuiltInRegistries.ITEM.getKey(flower.item()),
                        Math.sqrt(flower.dist()));
            }
        }

        Item result = pickHoney(flowers);

        if (DEBUG_BREW) {
            ThriveMod.LOGGER.info("[GardenHive] result = {}", BuiltInRegistries.ITEM.getKey(result));
        }

        // 存入空槽位
        for (int i = 0; i < MAX_HONEY; i++) {
            if (honey[i] == null) {
                honey[i] = result;
                break;
            }
        }
        // 结算后，花粉清零
        nectarLevel = 0;
        setChanged();
    }

    /**
     * 在附近的全部花里，逐条配方找“能凑齐、且三朵花最紧凑”的那一条。
     * 不再只看最近的三朵，避免最近三朵是杂花时永远只能出原版蜂蜜瓶。
     */
    private Item pickHoney(List<Flower> flowers) {
        Item best = null;
        double bestWorstDist = Double.MAX_VALUE;

        for (GardenHiveRecipes.Recipe recipe : GardenHiveRecipes.RECIPE) {
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

            boolean complete = need.values().stream().allMatch(v -> v == 0);
            if (!complete) continue;

            if (DEBUG_BREW) {
                ThriveMod.LOGGER.info("[GardenHive]   candidate {} worstDist={}",
                        BuiltInRegistries.ITEM.getKey(recipe.honey()), Math.sqrt(worstDist));
            }
            if (worstDist < bestWorstDist) {
                bestWorstDist = worstDist;
                best = recipe.honey();
            }
        }

        if (DEBUG_BREW && best == null) {
            ThriveMod.LOGGER.info("[GardenHive]   no recipe fits -> honey bottle");
        }
        return best != null ? best : Items.HONEY_BOTTLE;
    }

    /** 7×7×7 立方体范围内全部“花盆或花朵”，按距离排序。双高花只算下半段。 */
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

                    // 花盆
                    if (state.getBlock() instanceof FlowerPotBlock pot) {
                        Block content = pot.getPotted();
                        if (content != Blocks.AIR) flower = content.asItem();
                    }
                    // 直接种在地里的花（含高花、杜鹃等花盆放不下的）
                    else if (state.is(BlockTags.FLOWERS) && !state.is(BlockTags.LEAVES)) {
                        if (state.hasProperty(DoublePlantBlock.HALF)
                                && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
                            continue; // 双高花只算一次
                        }
                        flower = state.getBlock().asItem();
                    }

                    if (flower == null || flower == Items.AIR) continue;
                    found.add(new Flower(p.distSqr(pos), flower));
                }

        found.sort(java.util.Comparator.comparingDouble(Flower::dist));
        return found;
    }

    private record Flower(double dist, Item item) {}

    public boolean takeHoney(Player player, ItemStack held) {
        if (!held.is(Items.GLASS_BOTTLE)) return false;

        for (int i = 0; i < MAX_HONEY; i++) {
            if (honey[i] != null) {
                ItemStack result = new ItemStack(honey[i]);
                honey[i] = null;

                if (!player.getAbilities().instabuild) held.shrink(1);
                if (!player.getInventory().add(result)) player.drop(result, false);

                player.level().playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                setChanged();
                return true;
            }
        }
        return false;
    }

    public int honeyCount() {
        int count = 0;
        for (Item item : honey) if (item != null) count++;
        return count;
    }

    /**
     * 放出一只蜜蜂。
     */
    void releaseOneBee(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);
        GardenBee bee = EntityRegister.GARDEN_BEE.get().create(level);
        if (bee != null) {
            bee.home = pos;
            bee.stayOutOfHiveCountdown = 400; // 出巢后一段时间不再进巢
            bee.moveTo(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
            level.addFreshEntity(bee);
        }
        setChanged();
    }

    public void releaseBees(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);
        for (int i = 0; i < occupantCount; i++) {
            GardenBee bee = EntityRegister.GARDEN_BEE.get().create(level);
            if (bee != null) {
                if (level.getBlockState(pos).is(BlockRegister.GARDEN_HIVE)){
                    bee.home = pos;
                }
                bee.stayOutOfHiveCountdown = 400;
                bee.moveTo(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
                level.addFreshEntity(bee);
            }
        }
        occupantCount = 0;
        nestTimer = 0; // 重置计时
        setChanged();
    }

    // ==================== NBT ====================
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.saveAdditional(tag, regs);
        tag.putInt("NectarLevel", nectarLevel);
        tag.putInt("OccupantCount", occupantCount);
        tag.putInt("NestTimer", nestTimer);

        CompoundTag h = new CompoundTag();
        for (int i = 0; i < MAX_HONEY; i++) {
            if (honey[i] != null) {
                h.putString("s" + i, BuiltInRegistries.ITEM.getKey(honey[i]).toString());
            }
        }
        tag.put("Honey", h);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.loadAdditional(tag, regs);
        nectarLevel = tag.getInt("NectarLevel");
        occupantCount = tag.getInt("OccupantCount");
        nestTimer = tag.getInt("NestTimer");

        CompoundTag h = tag.getCompound("Honey");
        for (int i = 0; i < MAX_HONEY; i++) {
            if (h.contains("s" + i)) {
                honey[i] = BuiltInRegistries.ITEM.get(ResourceLocation.parse(h.getString("s" + i)));
            }
        }
    }
}