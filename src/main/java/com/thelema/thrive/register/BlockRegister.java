package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.block.GiantGlowingBerry;
import com.thelema.thrive.generic.block.MossFluidTank;
import com.thelema.thrive.generic.block.MossFrameEmpty;
import com.thelema.thrive.generic.block.MossPlantingBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class BlockRegister {

    static final DeferredRegister<Block> BLOCK = DeferredRegister.createBlocks(ThriveMod.MOD_ID);

    public static void register(IEventBus bus) {
        BLOCK.register(bus);
    }

    public static <B extends Block> DeferredHolder<Block, B> registerWithItem(String name, Supplier<B> supplier) {
        DeferredHolder<Block, B> block = BLOCK.register(name, supplier);
        // 注册对应的方块物品
        ItemRegister.ITEM.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static final DeferredHolder<Block, Block> MOSS_FRAME;
    public static final DeferredHolder<Block, Block> MOSS_FRAME_EMPTY;
    public static final DeferredHolder<Block, Block> GIANT_GLOWING_BERRY;

    public static final DeferredHolder<Block, Block> MOSS_PLANTING_BLOCK;
    public static final DeferredHolder<Block, Block> MOSS_FLUID_TANK;

    static {

        MOSS_FRAME = registerWithItem("moss_frame",
                () -> new Block(Block.Properties.of()
                .strength(4.0F)              // 硬度 4（原木为 2）
                .sound(SoundType.WOOD)       // 木质音效
                .requiresCorrectToolForDrops() // 可选：需要合适工具
        ));

        MOSS_FRAME_EMPTY = registerWithItem("moss_frame_empty", MossFrameEmpty::new);

        MOSS_PLANTING_BLOCK = registerWithItem("moss_planting_block", MossPlantingBlock::new);

        GIANT_GLOWING_BERRY = registerWithItem("giant_glowing_berry", GiantGlowingBerry::new);

        MOSS_FLUID_TANK = registerWithItem("moss_fluid_tank", MossFluidTank::new);

    }



}
