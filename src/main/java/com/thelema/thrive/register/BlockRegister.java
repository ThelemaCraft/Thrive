package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.block.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import static com.thelema.thrive.register.ItemRegister.ITEM;

public class BlockRegister {

    public static final DeferredRegister<Block> BLOCK = DeferredRegister.createBlocks(ThriveMod.MOD_ID);
    public static void register(IEventBus bus) {
        BLOCK.register(bus);
    }

    public static <B extends Block> DeferredHolder<Block, B> registerWithItem(String name, Supplier<B> supplier) {
        DeferredHolder<Block, B> block = BLOCK.register(name, supplier);
        // 注册对应的方块物品
        ITEM.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static final DeferredHolder<Block, Block> MOSS_FRAME;
    public static final DeferredHolder<Block, Block> LANTERN_BERRY;

    public static final DeferredHolder<Block, Block> MOSS_PLANTING_BLOCK;

    public static final DeferredHolder<Block, Block> SUN_BERRY;
    public static final DeferredHolder<Block, Block> GRASS_BLOCK;
    public static final DeferredHolder<Block, Block> BIOME_CORE;
    public static final DeferredHolder<Block, Block> RAMPANT_MOSS;
    public static final DeferredHolder<Block, Block> GARDEN_HIVE;
    public static final DeferredHolder<Block, Block> GARDEN_BEE_BARRACKS;

    static {

        MOSS_FRAME = registerWithItem("moss_frame",
                () -> new Block(Block.Properties.of()
                        .strength(4.0F)              // 硬度 4（原木为 2）
                        .sound(SoundType.WOOD)       // 木质音效
                        .requiresCorrectToolForDrops() // 可选：需要合适工具
                ));

        MOSS_PLANTING_BLOCK = registerWithItem("moss_planting_block", MossPlantingBlock::new);

        LANTERN_BERRY = registerWithItem("lantern_berry", LanternBerry::new);

        SUN_BERRY = registerWithItem("sun_berry", SunBerry::new);

        GRASS_BLOCK = registerWithItem("grass_block", () -> new GrassBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).randomTicks().strength(0.6F).sound(SoundType.GRASS)));

        // 群系核心
        BIOME_CORE = registerWithItem("biome_core", BiomeCore::new);

        // 疯长的苔藓块：摆到群系核心上启动转换。堆叠上限为 1，物品在 ItemRegister 里单独注册
        RAMPANT_MOSS = BLOCK.register("rampant_moss", RampantMoss::new);

        GARDEN_HIVE = registerWithItem("garden_hive", GardenHive::new);

        GARDEN_BEE_BARRACKS = registerWithItem("garden_bee_barracks", GardenBeeBarracks::new);

    }




}

