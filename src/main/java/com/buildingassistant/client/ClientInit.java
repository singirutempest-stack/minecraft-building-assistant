package com.buildingassistant.client;

import com.buildingassistant.BuildingAssistant;
import com.buildingassistant.client.command.BuildingAssistantCommand;
import com.buildingassistant.client.gui.ProgressHud;
import com.buildingassistant.client.input.KeyBindings;
import com.buildingassistant.client.render.GhostRenderer;
import com.buildingassistant.library.SchematicLibrary;
import com.buildingassistant.project.ProjectManager;
import net.minecraft.Util;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Wires up everything that exists only on the client. This class is referenced only when running on a
 * client, so a dedicated server never loads any rendering or input code.
 */
public final class ClientInit {

    private ClientInit() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientInit::onClientSetup);
        modEventBus.addListener(ClientInit::onRegisterReloadListeners);
        modEventBus.addListener(KeyBindings::register);
        modEventBus.addListener(ProgressHud::register);

        MinecraftForge.EVENT_BUS.addListener(KeyBindings::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientValidation::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientValidation::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(GhostRenderer::onRenderLevelStage);
        MinecraftForge.EVENT_BUS.addListener(BuildingAssistantCommand::register);
        MinecraftForge.EVENT_BUS.addListener(ClientPersistence::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(ClientPersistence::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::onLoggingOut);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        // Reading and validating files happens off the game thread.
        SchematicLibrary.loadAsync();
        CompletableFuture.runAsync(ClientValidation::loadMaterialGroups, Util.backgroundExecutor())
                .exceptionally(error -> {
                    BuildingAssistant.LOGGER.error("Loading material groups failed", error);
                    return null;
                });
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        // A resource reload can change block models and textures, so cached ghost geometry is stale.
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> GhostRenderer.invalidateAll());
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Save first: the next lines discard the projects.
        ClientPersistence.saveNowAndClose();
        GhostRenderer.reset();
        ProjectManager.get().clear();
    }
}
