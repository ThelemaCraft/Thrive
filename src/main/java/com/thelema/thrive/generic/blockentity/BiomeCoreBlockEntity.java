package com.thelema.thrive.generic.blockentity;

import com.thelema.thrive.generic.blockentity.convertor.Convertor;
import com.thelema.thrive.generic.blockentity.convertor.ConvertorRegistry;
import com.thelema.thrive.generic.blockentity.tool.Surface;
import com.thelema.thrive.register.BlockEntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

public class BiomeCoreBlockEntity extends BlockEntity {

    /** 表面物品能力。 */
    public final Surface surface = new Surface(this);

    /** 旋转物品能力。 */
    //public final Rotator rotator = new Rotator(this, this.worldPosition);

    /** 持久化数据。 */
    public final CompoundTag data = new CompoundTag();
    public final Set<Long> dirty = new HashSet<>();

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
        if (level.isClientSide) return;
        // 启动条件：表面摆着物品，且该物品有对应转换器。拿走或消耗掉，转换自然停
        ItemStack stack = be.surface.getStack();
        Convertor convertor = ConvertorRegistry.MAP.get(stack.getItem());
        if (convertor == null) return;

        // 钥匙只留 1 个，多余的扔出来
        if (stack.getCount() > 1) {
            Block.popResource(level, pos.above(), stack.copyWithCount(stack.getCount() - 1));
            be.surface.setCount(1);
        }

        convertor.tick((ServerLevel) level, be);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        surface.save(tag, registries);
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.saveAdditional(tag, regs);
        surface.save(tag, regs);
        tag.put("data", data);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.loadAdditional(tag, regs);
        surface.load(tag, regs);
        if (tag.contains("data")) data.merge(tag.getCompound("data"));
    }
}
