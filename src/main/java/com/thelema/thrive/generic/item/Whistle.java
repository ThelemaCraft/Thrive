package com.thelema.thrive.generic.item;

import com.thelema.thrive.generic.blockentity.GardenBeeNestBlockEntity;
import com.thelema.thrive.generic.entity.GardenBee;
import com.thelema.thrive.register.PoiRegister;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** 蜜蜂哨：右键切换附近自己驯服的蜜蜂在“自由行动 / 跟随”之间切换。 */
public class Whistle extends Item {

    /** 哨声影响的半径（格）。 */
    public static final double RANGE = 64.0;

    public Whistle() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        List<GardenBee> bees = level.getEntitiesOfClass(GardenBee.class,
                player.getBoundingBox().inflate(RANGE),
                bee -> bee.isTame() && bee.isOwnedBy(player));
        List<GardenBeeNestBlockEntity> nests = findNests(level, player);

        boolean anyInNest = nests.stream().anyMatch(nest -> nest.hasOwnedBee(player));
        if (bees.isEmpty() && !anyInNest) {
            player.displayClientMessage(Component.translatable("item.thrive.whistle.none"), true);
            return InteractionResultHolder.fail(stack);
        }

        // 场上的蜂和巢内的蜂都算：全部已在自由行动 → 切回跟随；否则统一切成自由行动。
        boolean allFree = bees.stream().allMatch(GardenBee::isOrderedToSit)
                && nests.stream().filter(nest -> nest.hasOwnedBee(player))
                        .allMatch(nest -> nest.allOwnedSitting(player));
        boolean free = !allFree;

        for (GardenBee bee : bees) {
            bee.setOrderedToSit(free);
            bee.getNavigation().stop();
            bee.setTarget(null);
        }

        for (GardenBeeNestBlockEntity nest : nests) {
            if (!nest.hasOwnedBee(player)) continue;
            if (free) {
                // 自由行动：巢内的也改成自由，等它们自然出巢即可。
                nest.setOwnedSitting(player, true);
            } else {
                // 跟随：直接让巢内自己的蜂出来，并设为跟随。
                nest.releaseOwnedBees(player);
            }
        }

        player.displayClientMessage(Component.translatable(
                free ? "item.thrive.whistle.free" : "item.thrive.whistle.follow"), true);
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(),
                player.getSoundSource(), 1.0F, free ? 1.4F : 1.0F);
        return InteractionResultHolder.success(stack);
    }

    /** 找出范围内的花园蜂巢。 */
    private static List<GardenBeeNestBlockEntity> findNests(Level level, Player player) {
        if (!(level instanceof ServerLevel server)) return List.of();

        List<GardenBeeNestBlockEntity> nests = new ArrayList<>();
        server.getPoiManager()
                .getInRange(poi -> poi.value() == PoiRegister.GARDEN_BEE_NEST.get(),
                        player.blockPosition(), (int) RANGE, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos)
                .map(server::getBlockEntity)
                .filter(be -> be instanceof GardenBeeNestBlockEntity)
                .map(be -> (GardenBeeNestBlockEntity) be)
                .forEach(nests::add);
        return nests;
    }
}
