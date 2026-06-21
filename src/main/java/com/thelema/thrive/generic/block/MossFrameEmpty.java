package com.thelema.thrive.generic.block;

import com.thelema.thelemalib.data.BlockMap;
import com.thelema.thelemalib.item.ItemCustomData;
import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import static com.thelema.thrive.util.Flags.CONTAINED_MOSS_FRAME;

@EventBusSubscriber
public class MossFrameEmpty extends Block {

    public MossFrameEmpty() {
        super(Properties.of()
                .instabreak()
                .sound(SoundType.WOOD)
                .requiresCorrectToolForDrops()
                .noOcclusion());
    }

    /** 标记位置 */
    public static void markContained(ServerLevel level, BlockPos pos) {
        BlockMap<Boolean> bm = BlockMap.common(level, CONTAINED_MOSS_FRAME);
        bm.put(pos, true);
        bm.setDirty();
        bm.sync();
    }

    /** 检查该位置是否被记录 */
    public static boolean isContained(ServerLevel level, BlockPos pos) {
        return BlockMap.<Boolean>common(level, CONTAINED_MOSS_FRAME).containsKey(pos);
    }

    /** 移除记录 */
    public static void removeContained(ServerLevel level, BlockPos pos) {
        BlockMap<Boolean> bm = BlockMap.common(level, CONTAINED_MOSS_FRAME);
        bm.remove(pos);
        bm.setDirty();
        bm.sync();
    }

    // 放置时标记
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        // 检查是否有 thrive_contained_moss_frame 标记
        if (!ItemCustomData.get(stack).getBoolean(CONTAINED_MOSS_FRAME)) return;

        BlockPos pos = event.getPos();
        ServerLevel level = (ServerLevel) event.getLevel();
        MossFrameEmpty.markContained(level, pos);
    }

    // 左键立刻破坏，掉落带有标记的方块物品
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        // 只在服务端执行
        if (event.getLevel().isClientSide()) return;

        ServerLevel level = (ServerLevel)event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        // 检查是否被记录（即玩家放置时带 NBT的）
        if (!isContained(level, pos)) {
            BlockMap<Boolean> common = BlockMap.<Boolean>common(level, CONTAINED_MOSS_FRAME);
            System.out.println(common.map().getTag());
            return;
        }

        // 1. 移除方块（不触发掉落）
        level.destroyBlock(pos, false);

        // 2. 生成带标记的物品
        ItemStack stack = new ItemStack(state.getBlock());
        ItemCustomData.get(stack).putBoolean(CONTAINED_MOSS_FRAME, true);

        // 3. 掉落物品
        Block.popResource(level, pos, stack);

        // 4. 移除记录
        removeContained(level, pos);

        event.setCanceled(true);
    }

    //客户端同步
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide()) return; // 仅在服务端执行

        // 如果服务器有多个维度，需要为每个维度同步，或者 sync() 自动处理
        ServerLevel serverLevel = (ServerLevel) level;
        BlockMap<Boolean> bm = BlockMap.common(serverLevel, CONTAINED_MOSS_FRAME);
        bm.sync(); // 假设 sync() 会向所有已连接玩家发送更新
    }

    // 左键立刻破坏
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        Player player = event.getEntity();
        ItemStack hand = event.getItemStack();
        BlockPos pos = event.getPos();

        // 斧头
        if (!hand.is(ItemTags.AXES)) return;
        // 标记
        if (!isContained(level, pos)) return;
        // 掉落框架
        Block.popResource(level, pos, new ItemStack(BlockRegister.MOSS_FRAME_EMPTY.get()));
        // 移除标记
        removeContained(level, pos);
        // 声音
        level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0f, 1.0f);
        // 耐久损耗
        hand.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
    }

}