package com.buildingassistant.client;

import com.buildingassistant.BuildingAssistant;
import com.buildingassistant.config.ClientConfig;
import com.buildingassistant.project.BuildProject;
import com.buildingassistant.project.ProjectManager;
import com.buildingassistant.validation.BlockMatcher;
import com.buildingassistant.validation.BuildingValidator;
import com.buildingassistant.validation.MaterialGroups;
import com.buildingassistant.validation.ValidationMode;
import com.buildingassistant.validation.ValidationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Keeps the validation state of every project in step with the world the client sees.
 *
 * <p>Work is driven by events, never by the frame rate: a block change re-checks one position, a new or
 * moved build is checked once in full, and each tick re-checks a small slice in the background.
 */
public final class ClientValidation {

    private static MaterialGroups groups = MaterialGroups.defaults();
    private static BuildingValidator validator;

    /** Settings the current validator was built from; a difference means it has to be rebuilt. */
    private static ValidationMode builtMode;
    private static List<? extends String> builtIgnoredProperties;
    private static boolean builtCheckClearance;

    private ClientValidation() {
    }

    /** Reads the material group file, creating it with defaults on first start. May run off-thread. */
    public static void loadMaterialGroups() {
        Path file = materialGroupsFile();
        MaterialGroups.LoadedGroups loaded = MaterialGroups.load(file);
        if (loaded.problem() != null) {
            BuildingAssistant.LOGGER.warn("Material groups file {}: {}", file, loaded.problem());
        }
        if (!file.toFile().isFile()) {
            try {
                loaded.groups().save(file);
            } catch (IOException e) {
                BuildingAssistant.LOGGER.warn("Could not write default material groups to {}", file, e);
            }
        }
        Minecraft.getInstance().execute(() -> {
            groups = loaded.groups();
            invalidate();
        });
    }

    public static Path materialGroupsFile() {
        return FMLPaths.CONFIGDIR.get().resolve(BuildingAssistant.MOD_ID).resolve("material_groups.json");
    }

    /** Tags arrive when joining a world and can change on reload; group membership depends on them. */
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        invalidate();
    }

    /** Drops the validator so that it is rebuilt from current settings and every build is re-checked. */
    public static void invalidate() {
        validator = null;
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        List<BuildProject> projects = ProjectManager.get().projects();
        if (level == null || projects.isEmpty()) {
            return;
        }
        boolean rebuilt = ensureValidator();
        int interval = ClientConfig.RESCAN_INTERVAL_TICKS.get();
        for (BuildProject project : projects) {
            if (project.dimension() != level.dimension()) {
                continue;
            }
            ValidationState state = project.validation();
            if (state == null || rebuilt || state.projection() != project.projection()) {
                state = new ValidationState(project.projection());
                validator.checkAll(state, level);
                project.setValidation(state);
            } else {
                int size = state.projection().size();
                validator.checkSlice(state, level, (size + interval - 1) / interval);
            }
            // Stop pointing at an error once the player has fixed it.
            int selected = project.selectedError();
            if (selected >= 0 && state.statusAt(selected) != ValidationState.WRONG
                    && state.statusAt(selected) != ValidationState.OBSTRUCTED) {
                project.setSelectedError(-1);
            }
        }
    }

    /** Called for every block change the client learns about. */
    public static void onBlockChanged(ClientLevel level, BlockPos pos) {
        if (validator == null) {
            return;
        }
        for (BuildProject project : ProjectManager.get().projects()) {
            ValidationState state = project.validation();
            if (state != null && project.dimension() == level.dimension()
                    && state.projection() == project.projection()) {
                validator.checkPosition(state, level, pos);
            }
        }
    }

    /** The validator in use, or null before the first tick with a project. */
    public static BuildingValidator validator() {
        return validator;
    }

    /** @return true when a new validator was created */
    private static boolean ensureValidator() {
        ValidationMode mode = ClientConfig.VALIDATION_MODE.get();
        List<? extends String> ignored = ClientConfig.IGNORED_PROPERTIES.get();
        boolean checkClearance = ClientConfig.CHECK_CLEARANCE.get();
        if (validator != null && mode == builtMode && checkClearance == builtCheckClearance
                && ignored.equals(builtIgnoredProperties)) {
            return false;
        }
        groups.resolve();
        validator = new BuildingValidator(new BlockMatcher(mode, List.copyOf(ignored), groups), checkClearance);
        builtMode = mode;
        builtIgnoredProperties = List.copyOf(ignored);
        builtCheckClearance = checkClearance;
        return true;
    }
}
