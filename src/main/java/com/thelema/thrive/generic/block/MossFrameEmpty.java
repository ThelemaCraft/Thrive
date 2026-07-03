// MossFrameEmpty 简化版（核心改动：统一使用 LevelMap.sync() 全量同步）

package com.thelema.thrive.generic.block;

import com.thelema.thelemalib.data.LevelMap;
import com.thelema.thelemalib.item.ItemCustomData;
import com.thelema.thrive.config.TConfig;
import com.thelema.thrive.register.BlockRegister;
import com.thelema.thrive.util.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;

@EventBusSubscriber
public class MossFrameEmpty extends Block {

    public static final String KEY = Flags.CONTAINED_MOSS_FRAME;

    // 状态常量
    public static final short STATE_NONE   = 0;
    public static final short STATE_MARKED = 1;   // 显示框架
    public static final short STATE_HIDDEN = 2;   // 隐藏框架

    public MossFrameEmpty() {
        super(Properties.of()
                .instabreak()
                .sound(SoundType.WOOD)
                .noOcclusion());
    }

    /** 获取当前维度的 LevelMap */
    private static LevelMap<BlockPos, Short> map(ServerLevel level) {
        return LevelMap.common(level, KEY);
    }

    // ───── 状态读写 ─────

    public static short getState(BlockPos pos, ServerLevel level) {
        return map(level).getOrDefault(pos, STATE_NONE);
    }

    public static boolean hasMark(BlockPos pos, ServerLevel level) {
        return getState(pos, level) == STATE_MARKED;
    }

    public static boolean hasAnyMark(BlockPos pos, ServerLevel level) {
        short s = getState(pos, level);
        return s == STATE_MARKED || s == STATE_HIDDEN;
    }

    // ───── 修改 + 全量同步 ─────

    /** 设置标记并增量同步 */
    public static void mark(BlockPos pos, ServerLevel level) {
        map(level).put(pos, STATE_MARKED);
        map(level).syncPut(STATE_MARKED, pos);
    }

    /** 设置隐藏标记，客户端侧移除同步 */
    public static void markHidden(BlockPos pos, ServerLevel level) {
        map(level).put(pos, STATE_HIDDEN);
        map(level).syncPut(STATE_HIDDEN, pos);
    }

    /** 剪刀切换隐藏 / 恢复显示 */
    public static void toggleHidden(BlockPos pos, ServerLevel level) {
        LevelMap<BlockPos, Short> m = map(level);
        short current = m.getOrDefault(pos, STATE_NONE);
        if (current == STATE_MARKED) {
            m.put(pos, STATE_HIDDEN);
            m.syncPut(STATE_HIDDEN, pos);
        } else if (current == STATE_HIDDEN) {
            m.put(pos, STATE_MARKED);
            m.syncPut(STATE_MARKED, pos);
        }
    }

    /** 移除标记，客户端侧同步删除 */
    public static void removeMark(BlockPos pos, ServerLevel level) {
        map(level).remove(pos);
        map(level).syncRemove(pos);
    }

    /** 全量同步（仅用于登录时） */
    public static void syncAll(ServerLevel level) {
        LevelMap<BlockPos, Short> m = map(level);
        m.sync();   // 直接全量发送，客户端替换整个缓存
    }


    // ───── 事件处理（逻辑不变，仅替换同步调用）─────

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;
        BlockPos pos = event.getPos();

        LevelAccessor accessor = event.getLevel();
        if (accessor.getBlockState(pos).is(Blocks.AIR)) {
            if (!accessor.isClientSide()) {
                removeMark(pos, (ServerLevel) accessor);   // 内部已调用 syncAll
            }
        }

        byte markState = ItemCustomData.get(stack).getByte(Flags.CONTAINED_MOSS_FRAME);
        if (markState != 0 && !accessor.isClientSide()) {
            ServerLevel level = (ServerLevel) accessor;
            if (markState == 1) {
                mark(pos, level);
            } else if (markState == 2) {
                markHidden(pos, level);
            }
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        if (!hasAnyMark(pos, level)) return;

        short currentState = getState(pos, level);
        if (!event.getEntity().isCreative()){
            level.destroyBlock(pos, false);
        }

        ItemStack stack = new ItemStack(state.getBlock());
        ItemCustomData.update(stack, tag -> tag.putByte(Flags.CONTAINED_MOSS_FRAME, (byte) currentState));
        Block.popResource(level, pos, stack);

        removeMark(pos, level);   // 内部全量同步
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Level l = event.getEntity().level();
        if (l instanceof ServerLevel sl) syncAll(sl);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack hand = event.getItemStack();
        BlockPos pos = event.getPos();

        if (!(event.getLevel() instanceof ServerLevel level)) {
            if (hand.is(BlockRegister.MOSS_FRAME_EMPTY.value().asItem()) && player.isCrouching()) {
                event.setCanceled(true);
            }
            return;
        }

        // 潜行 + 框架 → 标记
        if (hand.is(BlockRegister.MOSS_FRAME_EMPTY.value().asItem()) && player.isCrouching()) {
            List<? extends String> blacklist = TConfig.CONFIG.mossFrameEmptyBlackList.get();
            ResourceLocation location = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());

            if (blacklist.contains(location.toString())) {
                player.displayClientMessage(Component.literal("§cCannot mark " + location + "!"), true);
                event.setCanceled(true);
                return;
            }

            if (!hasMark(pos, level)) {
                event.setCanceled(true);
                hand.shrink(1);
                level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS);
                mark(pos, level);   // 内部 syncAll
            }
            return;
        }

        // 斧头移除标记
        if (hand.is(ItemTags.AXES) && hasAnyMark(pos, level)) {
            Block.popResource(level, pos, new ItemStack(BlockRegister.MOSS_FRAME_EMPTY.get()));
            removeMark(pos, level);   // syncAll
            level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0f, 1.0f);
            hand.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            event.setCanceled(true);
            return;
        }

        // 剪刀切换隐藏 / 恢复
        if (hand.is(Items.SHEARS) && hasAnyMark(pos, level)) {
            toggleHidden(pos, level);   // syncAll
            level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
            hand.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            event.setCanceled(true);
            short state = getState(pos, level);
            if (state == STATE_HIDDEN) {
                player.displayClientMessage(Component.literal("§7框架已隐藏"), true);
            } else if (state == STATE_MARKED) {
                player.displayClientMessage(Component.literal("§a框架已恢复显示"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onItemTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ItemEntity item)) return;
        Level level = item.level();
        if (level.isClientSide()) return;

        ItemStack stack = item.getItem();
        byte markState = ItemCustomData.get(stack).getByte(Flags.CONTAINED_MOSS_FRAME);
        if (markState == 0) return;

        if (item.getDeltaMovement().length() < 0.5) return;

        Block block = Block.byItem(stack.getItem());
        if (block == Blocks.AIR) return;

        BlockPos pos = item.blockPosition();
        BlockState here = level.getBlockState(pos);
        BlockState below = level.getBlockState(pos.below());

        if (below.isAir()) return;

        BlockPos placePos = null;
        if (here.isAir() || here.canBeReplaced()) {
            placePos = pos;
        } else {
            BlockPos upPos = pos.above();
            BlockState upState = level.getBlockState(upPos);
            if (upState.isAir() || upState.canBeReplaced()) {
                placePos = upPos;
            }
        }
        if (placePos == null) return;

        if (level instanceof ServerLevel sl) {
            level.setBlock(placePos, block.defaultBlockState(), 3);
            level.playSound(null, placePos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (markState == 1) {
                mark(placePos, sl);         // 内部 syncAll
            } else if (markState == 2) {
                markHidden(placePos, sl);   // syncAll
            }
            item.discard();
        }
    }
}