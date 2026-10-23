package com.thelema.thrive.generic.blockentity;

import com.thelema.thrive.generic.blockentity.cap.Rotator;
import com.thelema.thrive.generic.blockentity.cap.Surface;
import com.thelema.thrive.generic.blockentity.convertor.Convertor;
import com.thelema.thrive.generic.blockentity.convertor.ConvertorRegistry;
import com.thelema.thrive.register.BlockEntityRegister;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

public class BiomeCoreBlockEntity extends BlockEntity {

    public final Surface surface = new Surface(this);
    public final Rotator rotator = new Rotator(this);

    /** Surface 放入物品后启动的抽取倒计时（tick）。0 = 未启动。 */
    public int checkTimer;

    /** 当前运行的转换器 id。null = 空闲。 */
    public ResourceLocation activeConvertorId;

    /** 待重发 chunk 集合。本会话有效，不存 NBT。 */
    public final Set<Long> dirty = new HashSet<>();
    /** 通用NBT */
    public final CompoundTag data = new CompoundTag();

    public BiomeCoreBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegister.BIOME_CORE.get(), pos, state);
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
        be.surface.tick();
        be.rotator.tick();
        if (level.isClientSide) return;

        // 每 10 天（240,000 tick）的黎明时分，在 Surface 中生成一个物品
        long dayTime = level.getDayTime();
        if (dayTime > 0 && dayTime % 240000L == 0) {
            // 如果 Surface 里面是空的，才生成
            if (be.surface.getStack().isEmpty()) {
                // 替换为你想要生成的物品，比如群系核心本身或其他材料
                be.surface.set(new ItemStack(BlockRegister.BIOME_CORE.get()), Direction.UP);
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3); // 通知客户端刷新
            }
        }
        // 转换中
        if (!be.rotator.isEmpty()) {
            Convertor c = ConvertorRegistry.get(be.activeConvertorId);
            if (c != null) c.tick((ServerLevel) level, be);
            return;
        }

        // 空闲：倒计时归零则抽取
        if (be.checkTimer > 0 && --be.checkTimer == 0) {
            extractFromSurface(level, be);
        }
    }

    private static void extractFromSurface(Level level, BiomeCoreBlockEntity be) {
        ItemStack stack = be.surface.getStack();
        if (stack.isEmpty()) return;

        ResourceLocation id;
        // 先试通用：不需要群系
        ResourceLocation universal = ConvertorRegistry.route(stack.getItem(), null);
        if (universal != null) {
            id = universal;
        } else {
            // 按源群系
            ResourceKey<Biome> here = level.getBiome(be.getBlockPos()).unwrapKey().orElse(null);
            if (here == null) return;
            id = ConvertorRegistry.route(stack.getItem(), here);
        }
        if (id == null) return;

        ItemStack extracted = stack.copyWithCount(1);
        if (stack.getCount() <= 1) be.surface.clear();
        else be.surface.setCount(stack.getCount() - 1);
        be.rotator.set(extracted);
        be.activeConvertorId = id;
    }

    /** 清空转换状态：Rotator、计时、进度、转换器 id。 */
    public void resetConversion() {
        rotator.clear();
        checkTimer = 0;
        data.getAllKeys().clear();
        activeConvertorId = null;
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
        tag.put("data", data);
        if (activeConvertorId != null) tag.putString("ActiveConvertor", activeConvertorId.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.loadAdditional(tag, regs);
        surface.load(tag, regs);
        rotator.load(tag, regs);
        if (tag.contains("data")) data.merge(tag.getCompound("data"));
        checkTimer = tag.getInt("CheckTimer");
        if (tag.contains("ActiveConvertor")) {
            activeConvertorId = ResourceLocation.parse(tag.getString("ActiveConvertor"));
        }
    }
}