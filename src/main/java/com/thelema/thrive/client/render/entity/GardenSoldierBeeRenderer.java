package com.thelema.thrive.client.render.entity;

import com.thelema.thrive.client.model.entity.GardenSoldierBeeModel;
import com.thelema.thrive.generic.entity.GardenSoldierBee;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GardenSoldierBeeRenderer extends MobRenderer<GardenSoldierBee, GardenSoldierBeeModel<GardenSoldierBee>> {

    /** 正常状态：复用原版蜜蜂材质占位 */
    private static final ResourceLocation BEE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/bee/bee.png");
    /** 战斗/暴怒状态：复用原版暴怒材质占位 */
    private static final ResourceLocation ANGRY_BEE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/bee/bee_angry.png");

    public GardenSoldierBeeRenderer(EntityRendererProvider.Context context) {
        super(context, new GardenSoldierBeeModel<>(context.bakeLayer(GardenSoldierBeeModel.LAYER_LOCATION)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(GardenSoldierBee entity) {
        // 处于中立记仇或战斗状态时，替换为暴怒材质
        if (entity.isAngry()) {
            return ANGRY_BEE_TEXTURE;
        }
        return BEE_TEXTURE;
    }
}