package com.thelema.thrive.register;

import com.thelema.thelemalib.tip.TTManager;
import com.thelema.thelemalib.tip.ToolTip;
import net.minecraft.ChatFormatting;
import net.minecraft.util.datafix.fixes.ItemStackComponentRemainderFix;
import net.minecraft.world.item.Items;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public class TipRegister {

    public static void register(){
        TTManager.item(ItemRegister.MOSS_CALLUS, TipRegister::mossCallus);
        TTManager.item(Items.SPORE_BLOSSOM, TipRegister::sporeBlossom);
        TTManager.item(BlockRegister.GIANT_GLOWING_BERRY.value().asItem(), TipRegister::giantGlowingBerry);
        TTManager.item(FluidRegister.GLOWING_BERRY_JUICE.bucket().get(), TipRegister::glowingBerryJuiceBucket);
        TTManager.item(ItemRegister.GLOWING_BERRY_JUICE_BOTTLE, TipRegister::glowingBerryJuiceBottle);
    }

    private static void glowingBerryJuiceBucket(ItemTooltipEvent event){
        new ToolTip(event.getToolTip())
                .trans("tooltip.thrive.glowing_berry_juice_bucket.desc").color(ChatFormatting.YELLOW)
                .build();
    }

    private static void glowingBerryJuiceBottle(ItemTooltipEvent event){
        new ToolTip(event.getToolTip())
                .trans("tooltip.thrive.glowing_berry_juice_bottle.desc").color(ChatFormatting.YELLOW)
                .build();
    }

    private static void giantGlowingBerry(ItemTooltipEvent event) {
        new ToolTip(event.getToolTip())
                .trans("tooltip.thrive.giant_glowing_berry.desc").color(ChatFormatting.YELLOW)
                .build();
    }

    private static void sporeBlossom(ItemTooltipEvent event) {
        new ToolTip(event.getToolTip())
                .trans("tooltip.thrive.spore_blossom.desc1").color(ChatFormatting.DARK_GREEN).enter()
                .trans("tooltip.thrive.spore_blossom.desc2").color(ChatFormatting.DARK_GREEN).enter()
                .build();
    }

    private static void mossCallus(ItemTooltipEvent event){
        new ToolTip(event.getToolTip())
                .trans("tooltip.thrive.moss_callus.desc").color(ChatFormatting.GREEN).enter()
                .build();
    }

}
