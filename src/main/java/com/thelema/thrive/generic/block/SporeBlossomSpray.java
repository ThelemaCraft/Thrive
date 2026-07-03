package com.thelema.thrive.generic.block;

import com.thelema.thelemalib.data.LevelMap;
import com.thelema.thrive.generic.blockentity.MossFluidTankBlockEntity;
import com.thelema.thrive.register.BlockRegister;
import com.thelema.thrive.register.EffectRegister;
import com.thelema.thrive.register.FluidRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.*;

@EventBusSubscriber
public class SporeBlossomSpray {

    private enum SporeType {
        GLOWING_BERRY(200),
        WATER_FILLED(20),
        MILK_FILLED(20),
        POTION(200);

        final int interval;
        SporeType(int interval) { this.interval = interval; }
    }

    private static final String KEY = "thrive_spore_blossom";

    private static LevelMap<BlockPos, String> getMap(ServerLevel level) {
        return LevelMap.common(level, KEY);
    }

    @SubscribeEvent
    public static void onSporePlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!event.getPlacedBlock().is(Blocks.SPORE_BLOSSOM)) return;

        BlockPos pos = event.getPos();
        SporeType type = getTypeFromAbove(level, pos.above());
        if (type != null) {
            LevelMap<BlockPos, String> map = getMap(level);
            map.put(pos, type.name()); // 自动存盘
            executeEffect(level, pos, type);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LevelMap<BlockPos, String> map = getMap(level);
            if (map.isEmpty()) continue;

            List<BlockPos> toRemove = new ArrayList<>();

            map.forEach((pos, s) -> {
                if (level.isLoaded(pos)) {
                    if (!level.getBlockState(pos).is(Blocks.SPORE_BLOSSOM)) {
                        toRemove.add(pos);
                        return;
                    }
                    SporeType type = getTypeFromAbove(level, pos.above());
                    if (type == null) {
                        toRemove.add(pos);
                        return;
                    }
                    executeEffect(level, pos, type);
                }
            });

            if (!toRemove.isEmpty()) {
                for (BlockPos pos : toRemove) {
                    map.remove(pos); // 自动存盘
                }
            }
        }
    }

    private static void executeEffect(ServerLevel level, BlockPos sporePos, SporeType type) {
        double radius = 20.0;
        AABB area = new AABB(sporePos).inflate(radius);
        BlockPos above = sporePos.above();

        switch (type) {
            case GLOWING_BERRY -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e2 -> true))
                    e.addEffect(new MobEffectInstance(EffectRegister.BEERY_JUICE, 400, 0, false, true));
            }
            case WATER_FILLED -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e2 -> true)) {
                    e.clearFire();
                    level.playSound(null, sporePos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.0F);
                }
                BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
                for (int x = -20; x <= 20; x++) for (int y = -20; y <= 20; y++) for (int z = -20; z <= 20; z++) {
                    mpos.set(sporePos.getX() + x, sporePos.getY() + y, sporePos.getZ() + z);
                    if (mpos.distSqr(sporePos) > radius * radius) continue;
                    BlockState bs = level.getBlockState(mpos);
                    if (bs.is(Blocks.FIRE)) level.setBlock(mpos, Blocks.AIR.defaultBlockState(), 3);
                    else if (bs.is(Blocks.LAVA)) {
                        FluidState fs = level.getFluidState(mpos);
                        level.setBlock(mpos, fs.isSource() ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState(), 3);
                    }
                }
            }
            case MILK_FILLED -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e2 -> true))
                    e.removeAllEffects();
            }
            case POTION -> {
                if (!(level.getBlockEntity(above) instanceof MossFluidTankBlockEntity tank)) return;
                ItemStack stack = tank.getStackInSlot(0);
                if (stack.isEmpty()) return;
                PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
                if (contents == null) return;
                List<MobEffectInstance> effects = new ArrayList<>();
                contents.forEachEffect(effects::add);
                if (effects.isEmpty()) return;
                int maxDuration = effects.stream().mapToInt(MobEffectInstance::getDuration).max().orElse(1);
                float consumeProb = Math.min(400f / maxDuration, 1.0f);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e2 -> true))
                    for (MobEffectInstance eff : effects)
                        e.addEffect(new MobEffectInstance(eff.getEffect(), 400, eff.getAmplifier(), eff.isAmbient(), eff.isVisible()));
                if (level.random.nextFloat() < consumeProb) {
                    tank.extractItem(0, 1, false);
                    tank.insertItem(0, new ItemStack(Items.GLASS_BOTTLE), false);
                    tank.setChanged();
                }
            }
        }
    }

    private static SporeType getTypeFromAbove(Level level, BlockPos above) {
        BlockState state = level.getBlockState(above);
        if (state.is(BlockRegister.GIANT_GLOWING_BERRY.get())) return SporeType.GLOWING_BERRY;
        if (state.is(BlockRegister.MOSS_FLUID_TANK.get())) {
            if (level.getBlockEntity(above) instanceof MossFluidTankBlockEntity tank) {
                FluidStack fluid = tank.getFluidInTank(0);
                if (!fluid.isEmpty()) {
                    if (fluid.getFluid() == FluidRegister.GLOWING_BERRY_JUICE.stillFluid().get())
                        return SporeType.GLOWING_BERRY;
                    return null;
                }
                ItemStack stack = tank.getStackInSlot(0);
                if (!stack.isEmpty()) {
                    Item item = stack.getItem();
                    if (item == Items.WATER_BUCKET) return SporeType.WATER_FILLED;
                    if (item == Items.MILK_BUCKET) return SporeType.MILK_FILLED;
                    if (item instanceof PotionItem) return SporeType.POTION;
                }
            }
        }
        return null;
    }
}