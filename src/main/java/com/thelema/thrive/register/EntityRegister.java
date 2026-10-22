package com.thelema.thrive.register;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.entity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber
public class EntityRegister {

    static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ThriveMod.MOD_ID);

    public static void register(IEventBus bus){
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
                .<Sun>of(Sun::new, MobCategory.MISC)
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

    }
}
