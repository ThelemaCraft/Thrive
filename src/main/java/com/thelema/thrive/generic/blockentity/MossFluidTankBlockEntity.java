package com.thelema.thrive.generic.blockentity;

import com.thelema.thrive.register.BlockEntityRegister;
import com.thelema.thrive.register.FluidRegister;
import com.thelema.thrive.register.ItemRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

public class MossFluidTankBlockEntity extends BlockEntity implements IFluidHandler, IItemHandler {

    public static final int CAPACITY = 4000; // 4000 mB

    // ========== 流体存储 ==========
    private final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            // 每次流体变化都同步客户端
            syncToClient();
        }

        @Override
        public boolean isFluidValid(FluidStack stack) {
            FluidStack existing = getFluid();
            return existing.isEmpty() || FluidStack.isSameFluidSameComponents(existing, stack);
        }
    };

    // ========== 物品存储（1个槽位） ==========
    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            syncToClient();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    public MossFluidTankBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegister.MOSS_FLUID_TANK.get(), pos, state);
    }

    public void triggerItemEffect() {
        if (level == null || level.isClientSide) return;
        // 流体
        FluidStack fluid = tank.getFluid();
        if (fluid.isEmpty()) return;
        if (fluid.getFluid() != FluidRegister.GLOWING_BERRY_JUICE.stillFluid().get()) return;
        int fluidAmount = fluid.getAmount();

        // 物品
        ItemStack stack = inventory.getStackInSlot(0);
        if (stack.isEmpty()) return;
        Item item = stack.getItem();

        if (item == Items.GOLDEN_APPLE) {
            if (fluidAmount >= 1000) {
                tank.drain(1000, FluidAction.EXECUTE);
                inventory.extractItem(0, 1, false);
                inventory.insertItem(0, new ItemStack(ItemRegister.MOSS_GOLDEN_APPLE.get()), false);
                syncToClient();
            }
        } else if (item == Items.ENCHANTED_GOLDEN_APPLE) {
            if (fluidAmount >= 4000) {
                FluidStack drained = tank.drain(4000, FluidAction.EXECUTE);
                if (drained.getAmount() == 4000) {
                    inventory.extractItem(0, 1, false);
                    inventory.insertItem(0, new ItemStack(ItemRegister.ENCHANTED_MOSS_GOLDEN_APPLE.get()), false);
                    syncToClient();
                }
            }
        }else if (item instanceof ArmorItem armorItem){
            if (fluidAmount >= 1000){
                FluidStack drained = tank.drain(1000, FluidAction.EXECUTE);
                if (drained.getAmount() == 1000) {
                    ItemStack armorStack = inventory.getStackInSlot(0);
                    int maxDamage = armorStack.getMaxDamage();
                    int currentDamage = armorStack.getDamageValue();
                    if (currentDamage > 0) {
                        int repairAmount = Math.max(1, maxDamage / 4); // 至少修复1点，否则取1/4
                        int newDamage = Math.max(0, currentDamage - repairAmount);
                        armorStack.setDamageValue(newDamage);
                        syncToClient();
                    }
                }
            }
        }
    }

    // 用于适配的接口方法
    // ---------- 客户端同步核心方法 ----------
    public void syncToClient() {
        if (level != null && !level.isClientSide) {
            setChanged();
            // 标志 3 (邻居+渲染) + 8 (强制同步方块实体NBT)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3 | 8);
        }
    }

    // ---------- IFluidHandler 实现（委托给 tank） ----------
    @Override
    public int getTanks() { return tank.getTanks(); }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        return this.tank.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return this.tank.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return this.tank.isFluidValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return tank.fill(resource, action);
    }

    @Override
    public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
        return tank.drain(resource, action);
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        return tank.drain(maxDrain, action);
    }

    // ---------- IItemHandler 实现（委托给 inventory） ----------
    @Override
    public int getSlots() { return inventory.getSlots(); }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return inventory.getStackInSlot(slot);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        return inventory.insertItem(slot, stack, simulate);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        return inventory.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return inventory.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return inventory.isItemValid(slot, stack);
    }

    // ---------- NBT 持久化 ----------
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // 流体：非空才保存
        if (!tank.getFluid().isEmpty()) {
            tag.put("fluid", tank.getFluid().save(registries));
        }
        // 物品：始终保存（可处理空）
        tag.put("inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // 流体
        if (tag.contains("fluid")) {
            tank.setFluid(FluidStack.parse(registries, tag.getCompound("fluid")).orElse(FluidStack.EMPTY));
        } else {
            tank.setFluid(FluidStack.EMPTY);
        }
        // 物品
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
    }

    // ---------- 网络同步（客户端实时更新） ----------
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // 服务端构建同步包时调用
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        // 客户端收到数据包后调用（替代 onDataPacket 的一种方式）
        loadAdditional(tag, registries);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        // 保证数据包到达时立即处理
        CompoundTag tag = pkt.getTag();
        loadAdditional(tag, registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        // 自动构建更新包，可选但推荐
        return ClientboundBlockEntityDataPacket.create(this);
    }
}