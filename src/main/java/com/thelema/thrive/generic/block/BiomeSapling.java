package com.thelema.thrive.generic.block;

import com.thelema.thrive.register.BlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BiomeSapling extends SaplingBlock {

    /**
     * 保底结构：未注册的群系（如 mod 群系）也至少能长出一棵树。
     */
    public static final ResourceLocation FALLBACK_TREE = ResourceLocation.fromNamespaceAndPath("thrive", "biome_core_tree_plains");

    public static Map<ResourceLocation, ResourceLocation> MAP = new HashMap<>();

    public static void register(String id, String loc) {
        MAP.put(ResourceLocation.parse(id), ResourceLocation.parse(loc));
    }

    static {
        register("minecraft:forest", "thrive:biome_core_tree_forest");
        register("minecraft:plains", "thrive:biome_core_tree_plains");
        register("thrive:garden", "thrive:biome_core_tree_garden");
        register("thrive:moss", "thrive:biome_core_tree_moss");
    }

    public static ResourceLocation treeStructureFor(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> MAP.get(key.location()))
                .orElse(FALLBACK_TREE);
    }

    public BiomeSapling(Properties properties) {
        super(new TreeGrower("biome_tree", Optional.empty(), Optional.empty(), Optional.empty()),
                properties.randomTicks());
    }

    @Override
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.cycle(STAGE), Block.UPDATE_CLIENTS);
            return;
        }

        ResourceLocation structureLocation = treeStructureFor(level.getBiome(pos));
        if (structureLocation == null) return;

        StructureTemplateManager manager = level.getServer().getStructureManager();
        StructureTemplate template = manager.get(structureLocation).orElse(null);
        if (template == null) return;

        BlockPos toCore = findCoreOffset(template);
        if (toCore == null) return;

        BlockPos toOrigin = pos.subtract(toCore);

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false)
                .addProcessor(BlockIgnoreProcessor.AIR);

        level.removeBlock(pos, false);
        template.placeInWorld(level, toOrigin, toOrigin, settings, level.random,
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);

        Vec3i size = template.getSize();
        BlockPos toEnd = toOrigin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        for (BlockPos p : BlockPos.betweenClosed(toOrigin, toEnd)) {
            level.getLightEngine().checkBlock(p);
        }
    }

    private BlockPos findCoreOffset(StructureTemplate template) {
        List<StructureTemplate.StructureBlockInfo> list = template.filterBlocks(
                BlockPos.ZERO,
                new StructurePlaceSettings(),
                BlockRegister.BIOME_CORE.get());
        return list.isEmpty() ? null : list.get(0).pos();
    }
}