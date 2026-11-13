package com.buildingassistant;

import com.buildingassistant.client.ClientInit;
import com.buildingassistant.config.ClientConfig;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
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

        // Client preferences only; a dedicated server never loads this file.
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.register(modEventBus);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Building Assistant loaded");
    }
}
