package com.buildingassistant.client;

import com.buildingassistant.BuildingAssistant;
import com.buildingassistant.config.ClientConfig;
import com.buildingassistant.project.BuildProject;
import com.buildingassistant.project.BuildView;
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

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the validation state of every project in step with the world the client sees.
 *
 * <p>Work is driven by events, never by the frame rate: a block change re-checks one position, a new or
 * moved build is checked once in full, and each tick re-checks a small slice in the background.
 */
public final class ClientValidation {

    private static MaterialGroups groups = MaterialGroups.defaults();
    /** One validator per checking mode; empty until first needed and after settings change. */
    private static final Map<ValidationMode, BuildingValidator> VALIDATORS = new EnumMap<>(ValidationMode.class);

    /** Settings the current validators were built from; a difference means they have to be rebuilt. */
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
        if (!Files.isRegularFile(file)) {
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

    /** Drops the validators so that they are rebuilt from current settings and every build is re-checked. */
    public static void invalidate() {
        VALIDATORS.clear();
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
        boolean rebuilt = ensureValidators();
        int interval = ClientConfig.RESCAN_INTERVAL_TICKS.get();
        for (BuildProject project : projects) {
            if (project.paused() || project.dimension() != level.dimension()) {
                continue;
            }
            BuildingValidator validator = VALIDATORS.get(project.validationMode());
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
            if (project == ProjectManager.get().current()) {
                announceFinishedStage(project, state);
            }
        }
    }

    /**
     * Tells the player once when the stage they are working on is finished and, if enabled, moves on to
     * the next unfinished stage.
     */
    private static void announceFinishedStage(BuildProject project, ValidationState state) {
        BuildView view = project.view();
        int stage = view.stage();
        if (view.stageMode() == BuildView.StageMode.ALL || !state.isStageComplete(stage)) {
            if (project.announcedStage() == stage) {
                // The stage was finished and then broken again; announce it again when it is redone.
                project.setAnnouncedStage(-1);
            }
            return;
        }
        if (project.announcedStage() == stage) {
            return;
        }
        project.setAnnouncedStage(stage);
        ProjectActions.onStageCompleted(project, stage, state);
    }

    /** Called for every block change the client learns about. */
    public static void onBlockChanged(ClientLevel level, BlockPos pos) {
        if (VALIDATORS.isEmpty()) {
            return;
        }
        for (BuildProject project : ProjectManager.get().projects()) {
            ValidationState state = project.validation();
            if (state != null && !project.paused() && project.dimension() == level.dimension()
                    && state.projection() == project.projection()) {
                VALIDATORS.get(project.validationMode()).checkPosition(state, level, pos);
            }
        }
    }

    /** The validator used for a project, or null before the first tick with a project. */
    @Nullable
    public static BuildingValidator validatorFor(BuildProject project) {
        return VALIDATORS.get(project.validationMode());
    }

    /** @return true when new validators were created */
    private static boolean ensureValidators() {
        List<? extends String> ignored = ClientConfig.IGNORED_PROPERTIES.get();
        boolean checkClearance = ClientConfig.CHECK_CLEARANCE.get();
        if (!VALIDATORS.isEmpty() && checkClearance == builtCheckClearance && ignored.equals(builtIgnoredProperties)) {
            return false;
        }
        groups.resolve();
        VALIDATORS.clear();
        for (ValidationMode mode : ValidationMode.values()) {
            VALIDATORS.put(mode, new BuildingValidator(new BlockMatcher(mode, List.copyOf(ignored), groups), checkClearance));
        }
        builtIgnoredProperties = List.copyOf(ignored);
        builtCheckClearance = checkClearance;
        return true;
    }
}
