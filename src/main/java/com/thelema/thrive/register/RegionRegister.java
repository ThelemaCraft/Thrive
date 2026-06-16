package com.thelema.thrive.register;

import com.mojang.datafixers.util.Pair;
import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.resourcekey.TBiomes;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.*;

import java.util.function.Consumer;
import static terrablender.api.ParameterUtils.*;

public class RegionRegister {

    public static final ResourceLocation FLOWER_SEA_REGION = region("flower_sea_region");
    public static final ResourceLocation MOSS_LAND_REGION = region("moss_land_region");

    public static void register(){

        // 苔藓地
        mossLandRegion();

        // 花海
        flowerSeaRegion();
    }

    private static void mossLandRegion() {
        Regions.register(new Region(MOSS_LAND_REGION, RegionType.OVERWORLD, 1) {
            @Override
            public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {

                VanillaParameterOverlayBuilder builder = new VanillaParameterOverlayBuilder();

                // 基础苔藓地
                new ParameterPointListBuilder()
                        .temperature(Temperature.span(Temperature.NEUTRAL, Temperature.NEUTRAL))
                        .humidity(Humidity.span(Humidity.HUMID, Humidity.WET))
                        .continentalness(Climate.Parameter.span(0.00F, 0.55F))
                        .erosion(Erosion.EROSION_0, Erosion.EROSION_1)
                        .depth(Depth.SURFACE, Depth.FLOOR)
                        .weirdness(Weirdness.MID_SLICE_NORMAL_ASCENDING, Weirdness.MID_SLICE_NORMAL_DESCENDING)
                        .build()
                        .forEach(point -> builder.add(point, TBiomes.MOSS_LAND));

                builder.build().forEach(mapper);
            }

        });


    }

    private static void flowerSeaRegion() {
        Regions.register(new Region(FLOWER_SEA_REGION, RegionType.OVERWORLD, 1) {
            @Override
            public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {

                VanillaParameterOverlayBuilder builder = new VanillaParameterOverlayBuilder();

                // 花海：温暖 + 低侵蚀（平坦）
                new ParameterPointListBuilder()
                        .temperature(Temperature.span(Temperature.WARM, Temperature.HOT))      // 温暖到炎热（低纬度）
                        .humidity(Humidity.span(Humidity.DRY, Humidity.NEUTRAL))              // 干燥到中性
                        .continentalness(Continentalness.span(Continentalness.COAST, Continentalness.INLAND)) // 靠近内陆
                        .erosion(Erosion.EROSION_0, Erosion.EROSION_1)                       // 侵蚀度最低（最平坦）
                        .depth(Depth.SURFACE, Depth.FLOOR)
                        .weirdness(Weirdness.MID_SLICE_NORMAL_ASCENDING, Weirdness.MID_SLICE_NORMAL_DESCENDING)
                        .build()
                        .forEach(point -> builder.add(point, TBiomes.FLOWER_SEA));

                builder.build().forEach(mapper);
            }
        });
    }

    public static ResourceLocation region(String name){
        return ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, name);
    }

}
