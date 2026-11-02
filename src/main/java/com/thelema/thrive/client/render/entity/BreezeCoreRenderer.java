package com.thelema.thrive.client.render.entity;

import com.thelema.thrive.client.model.entity.BreezeCoreModel;
import com.thelema.thrive.generic.entity.BreezeCore;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class BreezeCoreRenderer extends GeoEntityRenderer<BreezeCore> {

    public BreezeCoreRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new BreezeCoreModel());
        addRenderLayer(new BreezeCoreWindLayer(this));
    }


}
