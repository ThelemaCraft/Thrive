package com.thelema.thrive.client.model.entity;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.entity.BreezeCore;
import com.thelema.thrive.generic.entity.LanternBerryCreeper;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class BreezeCoreModel extends GeoModel<BreezeCore> {

    @Override
    public ResourceLocation getModelResource(BreezeCore breezeCore) {
        return ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, "geo/breeze_core.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BreezeCore breezeCore) {
        return ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, "textures/entity/breeze_core/breeze_core.png");
    }

    @Override
    public ResourceLocation getAnimationResource(BreezeCore breezeCore) {
        return ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, "animations/breeze_core.animation.json");

    }
}