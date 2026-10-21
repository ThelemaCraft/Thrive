package com.thelema.thrive.integration.jade;

import com.thelema.thrive.generic.block.GardenBeeBarracks;
import com.thelema.thrive.generic.block.GardenHive;
import com.thelema.thrive.generic.blockentity.GardenBeeBarracksBlockEntity;
import com.thelema.thrive.generic.blockentity.GardenHiveBlockEntity;
import com.thelema.thrive.integration.jade.component.GardenBeeBarracksComponentProvider;
import com.thelema.thrive.integration.jade.component.GardenHiveComponentProvider;
import com.thelema.thrive.integration.jade.data.GardenBeeBarracksDataProvider;
import com.thelema.thrive.integration.jade.data.GardenHiveDataProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class ThriveJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(GardenHiveDataProvider.INSTANCE, GardenHiveBlockEntity.class);
        registration.registerBlockDataProvider(GardenBeeBarracksDataProvider.INSTANCE, GardenBeeBarracksBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(GardenHiveComponentProvider.INSTANCE, GardenHive.class);
        registration.registerBlockComponent(GardenBeeBarracksComponentProvider.INSTANCE, GardenBeeBarracks.class);
    }
}
