package com.thelema.thrive.generic.blockentity;

import com.thelema.thrive.generic.blockentity.cap.Rotator;
import com.thelema.thrive.generic.blockentity.cap.Surface;
import com.thelema.thrive.generic.blockentity.convertor.Convertor;
import com.thelema.thrive.generic.blockentity.convertor.ConvertorRegistry;
import com.thelema.thrive.register.BlockEntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BiomeCoreBlockEntity extends BlockEntity {

    public final Surface surface = new Surface(this);
    public final Rotator rotator = new Rotator(this);

    /** Surface 放入物品后启动的抽取倒计时（tick）。0 = 未启动。 */
    public int checkTimer;

    /** 转换已运行的 tick 数。读档续跑。 */
    public long convertTick;

    /** 当前运行的转换器。运行时缓存，不进 NBT。 */
    public Convertor activeConvertor;

    /** 抽取时确定的源群系。用于读档后恢复 activeConvertor。 */
    public ResourceKey<Biome> activeSource;

    /** 待重发 chunk 集合。本会话有效，不存 NBT。 */
    public final Set<Long> dirty = new HashSet<>();

    /** 抽取间隔：5 秒。 */
    public static final int CHECK_INTERVAL = 100;

    public BiomeCoreBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegister.THRIVE_CORE.get(), pos, state);
    }

    public int minY() {
        int sectionMin = (worldPosition.getY() >> 4) << 4;
        return Math.max(sectionMin - 32, level.getMinBuildHeight());
    }

    public int maxY() {
        int sectionMin = (worldPosition.getY() >> 4) << 4;
        return Math.min(sectionMin + 47, level.getMaxBuildHeight() - 1);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BiomeCoreBlockEntity be) {
        be.surface.tickAnimation();
        be.rotator.tick();
        if (level.isClientSide) return;

        // 转换中：确保 activeConvertor 就绪，然后跑
        if (!be.rotator.isEmpty()) {
            if (be.activeConvertor == null) resumeConvertor(be);
            if (be.activeConvertor != null) {
                be.activeConvertor.tick((ServerLevel) level, be);
            }
            return;
        }

        // 空闲：倒计时归零则抽取
        if (be.checkTimer > 0 && --be.checkTimer == 0) {
            extractFromSurface(level, be);
        }
    }

    /** 从 Rotator 物品 + activeSource 恢复转换器实例。读档后首次 tick 或刚抽取时调用。 */
    private static void resumeConvertor(BiomeCoreBlockEntity be) {
        if (be.rotator.isEmpty() || be.activeSource == null) return;
        Map<ResourceKey<Biome>, Convertor> byBiome = ConvertorRegistry.MAP.get(be.rotator.item.getItem());
        if (byBiome == null) return;
        be.activeConvertor = byBiome.get(be.activeSource);
    }

    private static void extractFromSurface(Level level, BiomeCoreBlockEntity be) {
        ItemStack stack = be.surface.getStack();
        if (stack.isEmpty()) return;

        Map<ResourceKey<Biome>, Convertor> byBiome = ConvertorRegistry.MAP.get(stack.getItem());
        if (byBiome == null) return;

        ResourceKey<Biome> here = level.getBiome(be.getBlockPos()).unwrapKey().orElse(null);
        if (here == null) return;

        Convertor c = byBiome.get(here);
        if (c == null) return;

        ItemStack extracted = stack.copyWithCount(1);
        if (stack.getCount() <= 1) be.surface.clear();
        else be.surface.setCount(stack.getCount() - 1);
        be.rotator.set(extracted);
        be.activeSource = here;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        surface.save(tag, registries);
        rotator.save(tag, registries);
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.saveAdditional(tag, regs);
        surface.save(tag, regs);
        rotator.save(tag, regs);
        tag.putInt("CheckTimer", checkTimer);
        tag.putLong("ConvertTick", convertTick);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.loadAdditional(tag, regs);
        surface.load(tag, regs);
        rotator.load(tag, regs);
        checkTimer = tag.getInt("CheckTimer");
        convertTick = tag.getLong("ConvertTick");
    }
}