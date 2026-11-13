package com.buildingassistant.client;

import com.buildingassistant.BuildingAssistant;
import com.buildingassistant.library.SchematicLibrary;
import com.buildingassistant.persistence.ProjectStore;
import com.buildingassistant.persistence.SavedProject;
import com.buildingassistant.persistence.SavedState;
import com.buildingassistant.project.BuildProject;
import com.buildingassistant.project.BuildView;
import com.buildingassistant.project.ProjectManager;
import com.buildingassistant.schematic.Schematic;
import com.buildingassistant.transform.Placement;
import com.buildingassistant.validation.ValidationMode;
import com.buildingassistant.validation.ValidationState;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Saves the player's projects per world (or per server) and restores them on the next visit.
 *
 * <p>Only the setup of each project is restored: which schematic, where, how it is turned, what the
 * player was looking at. Progress is not restored from the file; the validator recomputes it from the
 * blocks that are really in the world.
 *
 * <p>File access happens off the game thread, except for the final save when leaving a world, which has
 * to finish before the game moves on.
 */
public final class ClientPersistence {

    /** Ticks to wait after a change before saving, so a burst of changes becomes one write. */
    private static final int SAVE_DELAY_TICKS = 40;

    @Nullable
    private static Path file;
    private static boolean loadRequested;
    private static boolean loaded;
    private static int saveCountdown = -1;
    /** Saved projects whose schematic is not available right now. They are written back unchanged. */
    private static List<SavedProject> unresolved = List.of();
    private static CompletableFuture<Void> pendingWrite = CompletableFuture.completedFuture(null);

    private ClientPersistence() {
    }

    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        file = FMLPaths.CONFIGDIR.get().resolve(BuildingAssistant.MOD_ID).resolve("projects")
                .resolve(worldKey(Minecraft.getInstance()) + ".json");
        loadRequested = true;
        loaded = false;
        saveCountdown = -1;
        unresolved = List.of();
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || file == null) {
            return;
        }
        // Restoring needs the schematics, which load in the background at startup.
        if (loadRequested && SchematicLibrary.isLoaded()) {
            loadRequested = false;
            Path source = file;
            CompletableFuture.supplyAsync(() -> ProjectStore.read(source), Util.ioPool())
                    .thenAcceptAsync(result -> apply(source, result), Minecraft.getInstance());
        }
        if (!loaded) {
            return;
        }
        if (ProjectManager.get().consumeDirty()) {
            saveCountdown = SAVE_DELAY_TICKS;
        }
        if (saveCountdown >= 0 && saveCountdown-- == 0) {
            Path target = file;
            SavedState snapshot = snapshot();
            pendingWrite = pendingWrite.thenRunAsync(() -> write(target, snapshot), Util.ioPool());
        }
    }

    /** Writes the current state before the projects are discarded. Call when leaving a world. */
    public static void saveNowAndClose() {
        if (file != null && loaded) {
            SavedState snapshot = snapshot();
            // Let a write that is already running finish first so the files cannot interleave.
            pendingWrite.join();
            write(file, snapshot);
        }
        file = null;
        loaded = false;
        loadRequested = false;
        saveCountdown = -1;
        unresolved = List.of();
    }

    private static void apply(Path source, ProjectStore.Loaded result) {
        // The player may have left this world while the file was being read.
        if (!source.equals(file) || Minecraft.getInstance().level == null) {
            return;
        }
        for (String problem : result.problems()) {
            BuildingAssistant.LOGGER.warn("Project file {}: {}", source, problem);
        }
        if (result.unreadable()) {
            keepCopyOfUnreadableFile(source);
            say(Component.translatable("buildingassistant.message.projects_unreadable"));
        }

        ProjectManager manager = ProjectManager.get();
        // Projects the player started before the file was read still have to be saved afterwards.
        boolean startedBeforeLoad = !manager.projects().isEmpty();
        List<SavedProject> missing = new ArrayList<>();
        int restored = 0;
        for (SavedProject saved : result.state().projects()) {
            Optional<Schematic> schematic = SchematicLibrary.get(saved.schematicId());
            BuildProject project = schematic.map(value -> restore(saved, value)).orElse(null);
            if (project == null) {
                missing.add(saved);
                continue;
            }
            manager.add(project);
            if (saved.id().equals(result.state().currentId())) {
                manager.setCurrent(project);
            }
            restored++;
        }
        manager.setGhostsVisible(result.state().ghostsVisible());
        unresolved = List.copyOf(missing);
        // Restoring is not a change that needs saving.
        manager.consumeDirty();
        if (startedBeforeLoad) {
            manager.markDirty();
        }
        loaded = true;

        if (restored > 0) {
            say(Component.translatable("buildingassistant.message.projects_restored", restored));
        }
        if (!missing.isEmpty()) {
            say(Component.translatable("buildingassistant.message.projects_missing", missing.size()));
        }
    }

    @Nullable
    private static BuildProject restore(SavedProject saved, Schematic schematic) {
        UUID id;
        ResourceLocation dimension = ResourceLocation.tryParse(saved.dimension());
        try {
            id = UUID.fromString(saved.id());
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (dimension == null || ProjectManager.get().byId(id) != null) {
            return null;
        }
        Placement placement = new Placement(new BlockPos(saved.x(), saved.y(), saved.z()),
                Rotation.values()[saved.quarterTurns() & 3], saved.mirrored());
        BuildProject project = new BuildProject(id, schematic,
                ResourceKey.create(Registries.DIMENSION, dimension), placement);
        project.setVisible(saved.visible());
        project.setPaused(saved.paused());
        project.setValidationMode(enumOr(ValidationMode.class, saved.validationMode(), ValidationMode.STRICT));
        BuildView view = project.view();
        view.setLayerMode(enumOr(BuildView.LayerMode.class, saved.layerMode(), BuildView.LayerMode.ALL));
        view.setLayer(saved.layer());
        view.setStageMode(enumOr(BuildView.StageMode.class, saved.stageMode(), BuildView.StageMode.ALL));
        view.setStage(saved.stage());
        project.setLastKnownPlaced(saved.placed());
        return project;
    }

    private static SavedState snapshot() {
        ProjectManager manager = ProjectManager.get();
        List<SavedProject> projects = new ArrayList<>();
        for (BuildProject project : manager.projects()) {
            ValidationState validation = project.validation();
            if (validation != null) {
                project.setLastKnownPlaced(validation.correct());
            }
            BlockPos anchor = project.placement().anchor();
            BuildView view = project.view();
            projects.add(new SavedProject(
                    project.id().toString(),
                    project.schematic().id(),
                    project.dimension().location().toString(),
                    anchor.getX(), anchor.getY(), anchor.getZ(),
                    project.placement().quarterTurns(),
                    project.placement().mirrored(),
                    project.visible(),
                    project.paused(),
                    name(project.validationMode()),
                    name(view.layerMode()),
                    view.layer(),
                    name(view.stageMode()),
                    view.stage(),
                    project.lastKnownPlaced(),
                    project.schematic().requiredBlockCount()));
        }
        projects.addAll(unresolved);
        BuildProject current = manager.current();
        return new SavedState(current == null ? "" : current.id().toString(), manager.ghostsVisible(), projects);
    }

    private static void write(Path target, SavedState state) {
        try {
            ProjectStore.write(target, state);
        } catch (IOException | RuntimeException e) {
            BuildingAssistant.LOGGER.error("Could not save projects to {}", target, e);
        }
    }

    private static void keepCopyOfUnreadableFile(Path source) {
        try {
            Files.copy(source, source.resolveSibling(source.getFileName() + ".unreadable"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            BuildingAssistant.LOGGER.warn("Could not keep a copy of the unreadable project file {}", source, e);
        }
    }

    /** A file name that is stable for a world and different for different worlds. */
    private static String worldKey(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        String source;
        String prefix;
        if (server != null) {
            Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            source = root.getFileName() == null ? root.toString() : root.getFileName().toString();
            prefix = "world_";
        } else {
            ServerData data = minecraft.getCurrentServer();
            source = data == null ? "unknown" : data.ip;
            prefix = "server_";
        }
        String readable = source.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (readable.length() > 48) {
            readable = readable.substring(0, 48);
        }
        // The hash keeps names apart that only differ in characters replaced above.
        return prefix + readable + "_" + Integer.toHexString(source.hashCode());
    }

    private static String name(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        for (E value : type.getEnumConstants()) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return fallback;
    }

    private static void say(Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, false);
        }
    }
}
