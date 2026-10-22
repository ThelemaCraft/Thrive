package com.thelema.thrive.integration.jade;

import com.thelema.thrive.generic.block.GardenBeeNest;
import com.thelema.thrive.generic.blockentity.GardenBeeNestBlockEntity;
import com.thelema.thrive.integration.jade.component.GardenBeeNestComponentProvider;
import com.thelema.thrive.integration.jade.data.GardenBeeNestDataProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class ThriveJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(GardenBeeNestDataProvider.INSTANCE, GardenBeeNestBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(GardenBeeNestComponentProvider.INSTANCE, GardenBeeNest.class);
    }
}
