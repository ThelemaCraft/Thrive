package com.thelema.thrive.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelema.thrive.register.FluidRegister;
import com.thelema.thrive.register.ParticleRegister;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {

    // 1 & 2: 拦截 tick 里的水判定，让鱼钩在果汁里能成功浮起
    @ModifyExpressionValue(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z")
    )
    private boolean thrive$allowJuiceInTick(boolean original, @Local FluidState fluidstate) {
        return original || fluidstate.getFluidType() == FluidRegister.LANTERN_BERRY_JUICE_TYPE.get();
    }

    // 3: 拦截 catchingFish 里的液体方块判定（允许生成粒子和咬钩）
    @ModifyExpressionValue(
            method = "catchingFish",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z")
    )
    private boolean thrive$allowJuiceParticles(boolean original, @Local BlockState blockstate) {
        return original || blockstate.is(FluidRegister.LANTERN_BERRY_JUICE_BLOCK.get());
    }

    // 4: 拦截开阔水域判定
    @ModifyExpressionValue(
            method = "getOpenWaterTypeForBlock",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z")
    )
    private boolean thrive$allowJuiceOpenWater(boolean original, @Local FluidState fluidstate) {
        return original || fluidstate.getFluidType() == FluidRegister.LANTERN_BERRY_JUICE_TYPE.get();
    }

    // 5: 拦截战利品表获取 (预留，如果你需要果汁专属战利品在这里修改)
    @ModifyExpressionValue(
            method = "retrieve",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ReloadableServerRegistries$Holder;getLootTable(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/level/storage/loot/LootTable;")
    )
    private LootTable thrive$changeJuiceLootTable(LootTable original, @Local ResourceKey<LootTable> key) {
        FishingHook hook = (FishingHook) (Object) this;
        if (hook.level().getFluidState(hook.blockPosition()).getFluidType() == FluidRegister.LANTERN_BERRY_JUICE_TYPE.get()) {
            // 如果需要替换战利品表，在这里 return 你的 lootTable
        }
        return original;
    }

    // 6: 替换果汁里的粒子（水花、气泡、汇聚水流）
    @ModifyArg(
            method = "catchingFish",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"),
            index = 0
    )
    private ParticleOptions thrive$changeJuiceParticles(ParticleOptions original) {
        FishingHook hook = (FishingHook) (Object) this;
        if (hook.level().getFluidState(hook.blockPosition()).getFluidType() == FluidRegister.LANTERN_BERRY_JUICE_TYPE.get()) {
            // 原版水花和汇聚水流 -> 换成我们的彩色水花（带物理碰撞）
            if (original == ParticleTypes.SPLASH || original == ParticleTypes.FISHING) {
                return ParticleRegister.JUICE_SPLASH.get();
            }
            // 原版气泡 -> 换成我们绑定了新贴图的气泡类
            if (original == ParticleTypes.BUBBLE) {
                return ParticleRegister.JUICE_BUBBLE.get();
            }
        }
        return original;
    }
}