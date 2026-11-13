package com.buildingassistant;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Mod entry point. Only wires lifecycle listeners; every feature lives in its own package
 * and registers itself from here as it is added.
 */
@Mod(BuildingAssistant.MOD_ID)
public final class BuildingAssistant {

    public static final String MOD_ID = "buildingassistant";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BuildingAssistant() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Building Assistant loaded");
    }
}
