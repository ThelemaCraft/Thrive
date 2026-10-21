package com.thelema.thrive.client.render.entity;

import com.thelema.thrive.generic.entity.GardenWitch;
import net.minecraft.client.model.WitchModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GardenWitchRenderer extends MobRenderer<GardenWitch, WitchModel<GardenWitch>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/witch.png");

    public GardenWitchRenderer(EntityRendererProvider.Context context) {
        super(context, new WitchModel<>(context.bakeLayer(ModelLayers.WITCH)), 0.5F);
        // 帽子层（原版 WitchRenderer 有）
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(),
                context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(GardenWitch witch) {
        return TEXTURE;
    }
}