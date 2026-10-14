package com.thelema.thrive.integration.jade;

import com.thelema.thrive.generic.block.GardenHive;
import com.thelema.thrive.generic.blockentity.GardenHiveBlockEntity;
import com.thelema.thrive.generic.entity.GardenBee;
import com.thelema.thrive.integration.jade.component.GardenBeeComponentProvider;
import com.thelema.thrive.integration.jade.component.GardenHiveComponentProvider;
import com.thelema.thrive.integration.jade.data.GardenBeeDataProvider;
import com.thelema.thrive.integration.jade.data.GardenHiveDataProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class ThriveJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(GardenBeeDataProvider.INSTANCE, GardenBee.class);
        registration.registerBlockDataProvider(GardenHiveDataProvider.INSTANCE, GardenHiveBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(GardenBeeComponentProvider.INSTANCE, GardenBee.class);
        registration.registerBlockComponent(GardenHiveComponentProvider.INSTANCE, GardenHive.class);
    }
}