package com.thelema.thrive.mixin;

import com.thelema.thrive.generic.blockentity.MossFluidTankBlockEntity;
import com.thelema.thrive.register.BlockRegister;
import com.thelema.thrive.register.FluidRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(SporeBlossomBlock.class)
public class SporeBlossomBlockMixin {

    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void onAnimateTick(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        BlockPos above = pos.above();
        int color = getSporeColor(level, above);
        if (color == -1) return; // 非特殊方块，保留原版粒子

        ci.cancel(); // 取消原版粒子，替换为彩色粒子

        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(r, g, b), 1.0F);

        // 模拟原版下落孢子粒子
        double x0 = pos.getX() + random.nextDouble();
        double y0 = pos.getY() + 0.7;
        double z0 = pos.getZ() + random.nextDouble();
        level.addParticle(dust, x0, y0, z0, 0.0, 0.0, 0.0);

        // 模拟原版环境孢子粒子
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 14; ++i) {
            mutable.set(
                pos.getX() + Mth.nextInt(random, -10, 10),
                pos.getY() - random.nextInt(10),
                pos.getZ() + Mth.nextInt(random, -10, 10)
            );
            BlockState blockState = level.getBlockState(mutable);
            if (!blockState.isCollisionShapeFullBlock(level, mutable)) {
                level.addParticle(dust,
                    mutable.getX() + random.nextDouble(),
                    mutable.getY() + random.nextDouble(),
                    mutable.getZ() + random.nextDouble(),
                    0.0, 0.0, 0.0
                );
            }
        }
    }

    private int getSporeColor(Level level, BlockPos pos) {
        if (!level.isClientSide) return -1;
        BlockState state = level.getBlockState(pos);  // 直接检测传入的 pos

        // 巨型浆果球方块
        if (state.is(BlockRegister.GIANT_GLOWING_BERRY.get())) {
            return 0xFFFF00; // 黄色
        }

        // 储罐
        if (state.is(BlockRegister.MOSS_FLUID_TANK.get())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MossFluidTankBlockEntity tank) {
                FluidStack fluid = tank.getFluidInTank(0);
                if (!fluid.isEmpty()) {
                    if (fluid.getFluid() == FluidRegister.GLOWING_BERRY_JUICE.stillFluid().get()) {
                        return 0xFFFF00;
                    }
                    return IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid) & 0xFFFFFF;
                }
                ItemStack stack = tank.getStackInSlot(0);
                if (!stack.isEmpty()) {
                    Item item = stack.getItem();
                    if (item == Items.WATER_BUCKET) return 0x0000FF;
                    if (item == Items.MILK_BUCKET) return 0xFFFFFF;
                    if (item instanceof PotionItem) {
                        PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
                        if (potionContents != null && potionContents.hasEffects()) {
                            return potionContents.getColor() & 0xFFFFFF;
                        }
                    }
                }
            }
        }
        return -1;
    }
}