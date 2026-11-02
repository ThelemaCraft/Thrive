package com.thelema.thrive.generic.blockentity.modifier.cloud;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;
import com.thelema.thrive.generic.blockentity.modifier.BiomeModifier;
import com.thelema.thrive.generic.blockentity.modifier.Ring;
import com.thelema.thrive.generic.blockentity.modifier.Succession;
import com.thelema.thrive.register.BlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

import static com.thelema.thrive.generic.blockentity.modifier.BiomeUtil.*;

public class AnyToCloud implements BiomeModifier {

    static final int PHASE_REWRITE = 0;
    static final int PHASE_CLOUD   = 1;
    static final int PHASE_DONE    = 2;

    static final int MIN_Y = 200;
    static final int MAX_Y = 250;

    /** 每个空气坐标触发云团的概率分之一（越大越稀）。 */
    static final int CLUSTER_CHANCE = 96;
    /** 云团底层的水平半径。 */
    static final int CLUSTER_RADIUS = 2;
    /** 云团的垂直层数（逐层收窄）。 */
    static final int CLUSTER_LAYERS = 3;

    static final ResourceKey<Biome> TARGET = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath("thrive", "cloud"));

    private static final Succession ANY_TO_CLOUD = new Succession(null, TARGET);

    @Override
    public boolean canUse(ServerLevel level, BiomeCoreBlockEntity be) {
        if (be.getBlockPos().getY() < MIN_Y) return false;
        Holder<Biome> biome = level.getBiome(be.getBlockPos());
        return biome.unwrapKey()
                .map(k -> k.location().getNamespace().equals("minecraft"))
                .orElse(false);
    }

    @Override
    public boolean tick(ServerLevel level, BiomeCoreBlockEntity be) {
        int phase = getPhase(be);
        if (phase >= PHASE_DONE) return true;

        boolean done = switch (phase) {
            case PHASE_REWRITE -> forBS(be, Ring.DEFAULT, ANY_TO_CLOUD);
            case PHASE_CLOUD   -> forPos(be, Ring.DEFAULT, (b, p) -> buildCloud(level, p));
            default -> true;
        };

        if (done) nextPhase(be);
        return false;
    }

    static void buildCloud(ServerLevel level, BlockPos p) {
        if (p.getY() < MIN_Y || p.getY() > MAX_Y) return;
        if (!level.getBlockState(p).isAir()) return;

        RandomSource random = level.random;
        if (random.nextInt(CLUSTER_CHANCE) != 0) return;

        BlockState cloud = BlockRegister.CLOUD.get().defaultBlockState();

        for (int dy = 0; dy < CLUSTER_LAYERS; dy++) {
            int r = CLUSTER_RADIUS - dy;       // 逐层收窄
            if (r < 0) break;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (random.nextInt(3) == 0) continue;   // 边缘稀疏化
                    BlockPos q = p.offset(dx, dy, dz);
                    if (!level.getBlockState(q).isAir()) continue;
                    set(level, q, cloud);
                    markDirty(level, q);
                }
            }
        }
    }
}