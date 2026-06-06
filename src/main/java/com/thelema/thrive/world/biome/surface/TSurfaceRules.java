package com.thelema.thrive.world.biome.surface;

import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.world.biome.TBiomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import terrablender.api.SurfaceRuleManager;

public class TSurfaceRules {

    public static void init(){

        overworld(mossland());

    }

    private static void overworld(SurfaceRules.RuleSource rule){
        SurfaceRuleManager.addSurfaceRules(
                SurfaceRuleManager.RuleCategory.OVERWORLD,
                ThriveMod.MOD_ID,
                rule
        );
    }

    static SurfaceRules.RuleSource mossland() {

        SurfaceRules.RuleSource MOSS_BLOCK = SurfaceRules.state(Blocks.MOSS_BLOCK.defaultBlockState());

        return SurfaceRules.ifTrue(
                SurfaceRules.isBiome(TBiomes.MOSS_LAND),
                SurfaceRules.sequence(
                        // 地表顶层
                        SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, MOSS_BLOCK),
                        // 地表下第1层（侧面第2格）
                        SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, MOSS_BLOCK),
                        // 地表下 5层
                        SurfaceRules.ifTrue(
                                SurfaceRules.stoneDepthCheck(4, true, 0, CaveSurface.FLOOR),
                                MOSS_BLOCK
                        ),

                        // 天花板
                        SurfaceRules.ifTrue(SurfaceRules.ON_CEILING, MOSS_BLOCK),
                        // 洞穴/坑洞表面
                        SurfaceRules.ifTrue(SurfaceRules.hole(), MOSS_BLOCK),
                        // 陡坡/悬崖（侧面的裸露部分）
                        SurfaceRules.ifTrue(SurfaceRules.steep(), MOSS_BLOCK)
                )
        );
    }

}
