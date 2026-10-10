package com.thelema.thrive.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(JigsawPlacement.class)
public class JigsawPlacementMixin {

    @Unique
    private static final int THRIVE_VOID_THRESHOLD = 8;

    // 防止结构在虚空附近生成
    @Inject(method = "addPieces", at = @At("HEAD"), cancellable = true)
    private static void thrive$skipVoidPlacement(
            Structure.GenerationContext context,
            Holder<StructureTemplatePool> startPool,
            Optional<ResourceLocation> startJigsawName,
            int maxDepth,
            BlockPos pos,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            int maxDistanceFromCenter,
            PoolAliasLookup aliasLookup,
            DimensionPadding dimensionPadding,
            LiquidSettings liquidSettings,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir
    ) {
        if (projectStartToHeightmap.isEmpty()) {
            return;
        }

        ResourceLocation poolId = startPool.unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);
        if (poolId == null || !"thrive".equals(poolId.getNamespace())) {
            return;
        }

        LevelHeightAccessor heightAccessor = context.heightAccessor();
        int minY = heightAccessor.getMinBuildHeight();

        // 提前查询 pos 位置的地面高度（绝对 Y）
        int surfaceY = context.chunkGenerator().getFirstFreeHeight(
                pos.getX(), pos.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG,
                heightAccessor,
                context.randomState()
        );

        // 高度图落到世界底部附近，说明这一列是虚空
        if (surfaceY <= minY + THRIVE_VOID_THRESHOLD) {
            cir.setReturnValue(Optional.empty());
        }
    }
}