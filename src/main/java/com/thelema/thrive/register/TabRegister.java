package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class TabRegister {

    static final DeferredRegister<CreativeModeTab> CREATIVE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ThriveMod.MOD_ID);

    public static void register(IEventBus bus) {
        CREATIVE_TAB.register(bus);
    }

    public static final Supplier<CreativeModeTab> THRIVE_TAB = CREATIVE_TAB.register("thrive_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.thrive"))
                    .icon(() -> new ItemStack(BlockRegister.LANTERN_BERRY.get()))
                    .displayItems((parameters, output) -> {
                        // 添加你的所有物品/方块
                        ItemRegister.ITEM.getEntries().forEach(entry -> output.accept(entry.get()));
                        // 桶
                        output.accept(FluidRegister.LANTERN_BERRY_JUICE_BUCKET.get());


                    })
                    .build());
}
