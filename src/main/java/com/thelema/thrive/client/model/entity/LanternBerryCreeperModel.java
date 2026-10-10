package com.thelema.thrive.client.model.entity;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.entity.LanternBerryCreeper;
import com.thelema.thrive.generic.entity.MossyZombie;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class LanternBerryCreeperModel extends GeoModel<LanternBerryCreeper> {

    @Override
    public ResourceLocation getModelResource(LanternBerryCreeper lanternBerryCreeper) {
        return ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, "geo/lantern_berry_creeper.geo.json");

    }
    @Override
    public ResourceLocation getTextureResource(LanternBerryCreeper lanternBerryCreeper) {
        return ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, "textures/entity/lantern_berry_creeper.png");
    }

    @Override
    public ResourceLocation getAnimationResource(LanternBerryCreeper lanternBerryCreeper) {
        return ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, "animations/lantern_berry_creeper.animation.json");
    }
}