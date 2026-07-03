package com.thelema.thrive.register;

import com.thelema.thelemalib.item.ItemCustomData;
import com.thelema.thelemalib.tip.TTManager;
import com.thelema.thelemalib.tip.ToolTip;
import com.thelema.thrive.util.Flags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.datafix.fixes.ItemStackComponentRemainderFix;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

@EventBusSubscriber
public class TipRegister {

    @SubscribeEvent
    public static void extra(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        // 读取标记状态（存储为 byte，默认0）
        byte state = ItemCustomData.get(stack).getByte(Flags.CONTAINED_MOSS_FRAME);

        if (state == 1) {
            // 普通框架标记
            event.getToolTip().add(Component.translatable("tooltip.thrive.moss_frame_contained.desc")
                    .withStyle(ChatFormatting.DARK_GREEN));
        } else if (state == 2) {
            event.getToolTip().add(Component.translatable("tooltip.thrive.moss_frame_contained_hidden.desc")
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
    }

}
