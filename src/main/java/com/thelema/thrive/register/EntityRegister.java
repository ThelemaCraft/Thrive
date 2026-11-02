package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.entity.*;
import com.thelema.thrive.generic.entity.projectile.WindBlade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

@EventBusSubscriber
public class EntityRegister {

    static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ThriveMod.MOD_ID);

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }

    public static final DeferredHolder<EntityType<?>, EntityType<MossyZombie>> MOSSY_ZOMBIE;
    public static final DeferredHolder<EntityType<?>, EntityType<PrimedLanternBerry>> PRIMED_LANTERN_BERRY;
    public static final DeferredHolder<EntityType<?>, EntityType<LanternBerryCreeper>> LANTERN_BERRY_CREEPER;
    public static final DeferredHolder<EntityType<?>, EntityType<GrassLandSheep>> GRASS_LAND_SHEEP;
    public static final DeferredHolder<EntityType<?>, EntityType<GardenBee>> GARDEN_BEE;
    public static final DeferredHolder<EntityType<?>, EntityType<GardenPillager>> GARDEN_PILLAGER;
    public static final DeferredHolder<EntityType<?>, EntityType<PrimedSunBerry>> PRIMED_SUN_BERRY;
    public static final DeferredHolder<EntityType<?>, EntityType<Sun>> SUN;
    public static final DeferredHolder<EntityType<?>, EntityType<GardenWitch>> GARDEN_WITCH;
    public static final DeferredHolder<EntityType<?>, EntityType<BreezeCore>> BREEZE_CORE;
    public static final DeferredHolder<EntityType<?>, EntityType<WindBlade>> WIND_BLADE;

    static {
        MOSSY_ZOMBIE = ENTITIES.register("mossy_zombie", () -> EntityType.Builder.of(MossyZombie::new, MobCategory.MONSTER)
                .sized(0.6f, 1.8f)
                .build("mossy_zombie"));

        PRIMED_LANTERN_BERRY = ENTITIES.register("primed_lantern_berry", () -> EntityType.Builder.<PrimedLanternBerry>of(PrimedLanternBerry::new, MobCategory.MISC)
                .fireImmune()
                .sized(0.98F, 0.98F)
                .eyeHeight(0.15F)
                .clientTrackingRange(10)
                .build("primed_lantern_berry"));

        LANTERN_BERRY_CREEPER = ENTITIES.register("lantern_berry_creeper", () -> EntityType.Builder.of(LanternBerryCreeper::new, MobCategory.MONSTER)
                .sized(1.50f, 0.98f)
                .build("lantern_berry_creeper"));

        GRASS_LAND_SHEEP = ENTITIES.register("grass_land_sheep", () -> EntityType.Builder.of(GrassLandSheep::new, MobCategory.AMBIENT)
                .sized(1.0f, 1.0f)
                .build("grass_land_sheep"));

        GARDEN_BEE = ENTITIES.register("garden_bee", () -> EntityType.Builder.of(GardenBee::new, MobCategory.CREATURE)
                .sized(0.7f, 0.6f)
                .build("garden_bee"));


        GARDEN_PILLAGER = ENTITIES.register("garden_pillager", () -> EntityType.Builder
                .of(GardenPillager::new, MobCategory.MONSTER)
                .sized(0.6F, 1.95F)
                .clientTrackingRange(8)
                .build("garden_pillager"));

        PRIMED_SUN_BERRY = ENTITIES.register("primed_sun_berry", () -> EntityType.Builder
                .<PrimedSunBerry>of(PrimedSunBerry::new, MobCategory.MISC)
                .sized(3.0f, 3.0f)
                .fireImmune()
                .clientTrackingRange(16)
                .updateInterval(1)
                .build("primed_sun_berry"));

        SUN = ENTITIES.register("sun", () -> EntityType.Builder
                .of(Sun::new, MobCategory.MISC)
                .sized(5.0f, 5.0f)
                .fireImmune()
                .clientTrackingRange(32)
                .updateInterval(1)
                .build("sun"));

        GARDEN_WITCH = ENTITIES.register("garden_witch", () -> EntityType.Builder
                .of(GardenWitch::new, MobCategory.MONSTER)
                .sized(0.6F, 1.95F)
                .clientTrackingRange(8)
                .build("garden_witch"));

        BREEZE_CORE = ENTITIES.register("breeze_core", () -> EntityType.Builder
                .of(BreezeCore::new, MobCategory.CREATURE)
                .sized(1.0f, 1.0f)
                .fireImmune()
                .build("breeze_core"));

        WIND_BLADE = ENTITIES.register("wind_blade", () -> EntityType.Builder
                .<WindBlade>of(WindBlade::new, MobCategory.MISC)
                .sized(0.4F, 0.4F)
                .clientTrackingRange(4)
                .updateInterval(10)
                .build("wind_blade"));
    }

    // ========== 事件注册 ==========
    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(MOSSY_ZOMBIE.get(), MossyZombie.createAttributes().build());
        event.put(LANTERN_BERRY_CREEPER.get(), LanternBerryCreeper.createAttributes().build());
        event.put(GRASS_LAND_SHEEP.get(), GrassLandSheep.createAttributes().build());
        event.put(GARDEN_BEE.get(), GardenBee.createAttributes().build());
        event.put(GARDEN_PILLAGER.get(), GardenPillager.createAttributes().build());
        event.put(GARDEN_WITCH.get(), GardenWitch.createAttributes().build());
        event.put(BREEZE_CORE.get(), BreezeCore.createAttributes().build());
    }

    @SubscribeEvent
    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(MOSSY_ZOMBIE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(LANTERN_BERRY_CREEPER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(GARDEN_PILLAGER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(GARDEN_WITCH.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(BREEZE_CORE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    // 允许一些生物生成在草丛，地毯这些地方
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void allowSpawnOn(MobSpawnEvent.PositionCheck event) {
        Mob m = event.getEntity();
        if (!isOurMob(m.getType())) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL) return;
        if (event.getResult() != MobSpawnEvent.PositionCheck.Result.DEFAULT) return;
        AABB box = m.getBoundingBox();
        boolean pass = false;
        for (BlockPos p : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState s = event.getLevel().getBlockState(p);
            if (s.isAir()) continue;
            if (!PASSABLE.contains(s.getBlock())) {
                pass = true;
                break;
            }
        }
        if (!pass) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.SUCCEED);
        }
    }

    /**
     * 注册完成前不能调用 DeferredHolder#get()，所以延迟到事件里再比较。
     */
    private static boolean isOurMob(EntityType<?> type) {
        return type == MOSSY_ZOMBIE.get()
                || type == LANTERN_BERRY_CREEPER.get()
                || type == GARDEN_PILLAGER.get()
                || type == GARDEN_WITCH.get()
                || type == GRASS_LAND_SHEEP.get()
                || type == GARDEN_BEE.get();
    }

    private static final Set<Block> PASSABLE = Set.of(
            Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN,
            Blocks.MOSS_CARPET,
            Blocks.AZALEA, Blocks.FLOWERING_AZALEA
    );
}
