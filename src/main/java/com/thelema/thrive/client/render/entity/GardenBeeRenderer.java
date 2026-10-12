package com.thelema.thrive.client.render.entity;

import com.thelema.thrive.client.model.entity.GardenBeeModel;
import com.thelema.thrive.generic.entity.GardenBee;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GardenBeeRenderer extends MobRenderer<GardenBee, GardenBeeModel<GardenBee>> {

    private static final ResourceLocation BEE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/bee/bee.png");
    private static final ResourceLocation NECTAR_BEE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/bee/bee_nectar.png");
    private static final ResourceLocation ANGRY_BEE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/bee/bee_angry.png");
    private static final ResourceLocation ANGRY_NECTAR_BEE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/bee/bee_angry_nectar.png");

    public GardenBeeRenderer(EntityRendererProvider.Context context) {
        super(context, new GardenBeeModel<>(context.bakeLayer(GardenBeeModel.LAYER_LOCATION)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(GardenBee bee) {
        if (bee.isAngry()) {
            return bee.carriedPollen != null ? ANGRY_NECTAR_BEE_TEXTURE : ANGRY_BEE_TEXTURE;
        }
        return bee.carriedPollen != null ? NECTAR_BEE_TEXTURE : BEE_TEXTURE;
    }
}