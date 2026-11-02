package com.thelema.thrive.register.constant;

import com.thelema.thrive.ThriveMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

public interface DamageTypeConstant {

    ResourceKey<DamageType> SUN = dt("sun");
    ResourceKey<DamageType> WIND_GROUND = dt("wind_ground");

    static ResourceKey<DamageType> dt(String name){
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, name));
    }
}
