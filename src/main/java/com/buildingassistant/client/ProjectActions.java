package com.buildingassistant.client;

import com.buildingassistant.config.ClientConfig;
import com.buildingassistant.project.BuildProject;
import com.buildingassistant.project.BuildView;
import com.buildingassistant.project.ProjectManager;
import com.buildingassistant.schematic.Schematic;
import com.buildingassistant.schematic.format.StageDefinition;
import com.buildingassistant.transform.GridTransform;
import com.buildingassistant.transform.Placement;
import com.buildingassistant.validation.ValidationMode;
import com.buildingassistant.validation.ValidationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * What the player can do with builds. Hotkeys, commands and screens all go through these methods, so
 * every entry point behaves the same, gives the same feedback and is saved the same way.
 */
public final class ProjectActions {

    /** How far the crosshair may be from the player when picking a spot for a build. */
    private static final double PICK_RANGE = 24.0;

    private ProjectActions() {
    }

    // --- Starting, stopping and choosing builds ---------------------------------------------------

    /** Starts a project where the player is looking and makes it the current one. */
    @Nullable
    public static BuildProject start(Schematic schematic) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        Placement placement = centredOn(schematic, Placement.at(BlockPos.ZERO), targetPosition(player));
        BuildProject project = ProjectManager.get().start(schematic, player.level().dimension(), placement);
        project.setValidationMode(ClientConfig.VALIDATION_MODE.get());
        ProjectManager.get().setGhostsVisible(true);
        notify(Component.translatable("buildingassistant.message.started", displayName(schematic)));
        return project;
    }

    /** Removes the current project. The world is not touched. */
    public static void stop() {
        BuildProject project = requireProject();
        if (project != null) {
            ProjectManager.get().remove(project);
            notify(Component.translatable("buildingassistant.message.stopped", displayName(project.schematic())));
        }
    }

    /** Makes the next project in the list the current one. */
    public static void selectNextProject() {
        List<BuildProject> projects = ProjectManager.get().projects();
        BuildProject current = requireProject();
        if (current == null) {
            return;
        }
        select(projects.get((projects.indexOf(current) + 1) % projects.size()));
    }

    /** Makes the project with the given position in the list (starting at 1) the current one. */
    public static boolean selectProject(int number) {
        List<BuildProject> projects = ProjectManager.get().projects();
        if (number < 1 || number > projects.size()) {
            return false;
        }
        select(projects.get(number - 1));
        return true;
    }

    private static void select(BuildProject project) {
        ProjectManager.get().setCurrent(project);
        BlockPos anchor = project.placement().anchor();
        notify(Component.translatable("buildingassistant.message.selected", displayName(project.schematic()),
                anchor.getX(), anchor.getY(), anchor.getZ()));
    }

    public static void setPaused(boolean paused) {
        BuildProject project = requireProject();
        if (project != null) {
            project.setPaused(paused);
            changed();
            notify(Component.translatable(paused
                    ? "buildingassistant.message.paused" : "buildingassistant.message.resumed",
                    displayName(project.schematic())));
        }
    }

    public static void togglePaused() {
        BuildProject project = ProjectManager.get().current();
        setPaused(project == null || !project.paused());
    }

    // --- Placement --------------------------------------------------------------------------------

    /**
     * Moves the current project relative to where the player faces, so "forward" always pushes the
     * build away from the player.
     */
    public static void moveRelativeToPlayer(int forward, int right, int up) {
        LocalPlayer player = Minecraft.getInstance().player;
        BuildProject project = requireProject();
        if (player == null || project == null) {
            return;
        }
        Direction facing = player.getDirection();
        Direction side = facing.getClockWise();
        move(project,
                facing.getStepX() * forward + side.getStepX() * right,
                up,
                facing.getStepZ() * forward + side.getStepZ() * right);
    }

    public static void moveBy(int dx, int dy, int dz) {
        BuildProject project = requireProject();
        if (project != null) {
            move(project, dx, dy, dz);
        }
    }

    public static void moveToCrosshair() {
        LocalPlayer player = Minecraft.getInstance().player;
        BuildProject project = requireProject();
        if (player == null || project == null) {
            return;
        }
        project.setPlacement(centredOn(project.schematic(), project.placement(), targetPosition(player)));
        changed();
        notifyPosition(project);
    }

    public static void rotate() {
        BuildProject project = requireProject();
        if (project != null) {
            project.rotateClockwise();
            changed();
            notify(Component.translatable("buildingassistant.message.rotated",
                    project.placement().quarterTurns() * 90));
        }
    }

    public static void mirror() {
        BuildProject project = requireProject();
        if (project != null) {
            project.toggleMirror();
            changed();
            notify(Component.translatable(project.placement().mirrored()
                    ? "buildingassistant.message.mirror_on"
                    : "buildingassistant.message.mirror_off"));
        }
    }

    public static void toggleGhosts() {
        setGhostsVisible(!ProjectManager.get().ghostsVisible());
    }

    public static void setGhostsVisible(boolean visible) {
        ProjectManager.get().setGhostsVisible(visible);
        notify(Component.translatable(visible
                ? "buildingassistant.message.ghosts_shown"
                : "buildingassistant.message.ghosts_hidden"));
    }

    // --- Layers -----------------------------------------------------------------------------------

    /** Moves the current layer up or down. Leaves "all layers" mode so the change is visible. */
    public static void changeLayer(int delta) {
        BuildProject project = requireProject();
        if (project != null) {
            setLayer(project, project.view().layer() + delta);
        }
    }

    /** Selects a layer by its number, starting at 1 for the bottom layer. */
    public static void selectLayer(int number) {
        BuildProject project = requireProject();
        if (project != null) {
            setLayer(project, number - 1);
        }
    }

    public static void setLayerMode(BuildView.LayerMode mode) {
        BuildProject project = requireProject();
        if (project != null) {
            project.view().setLayerMode(mode);
            changed();
            notifyLayer(project);
        }
    }

    public static void cycleLayerMode() {
        BuildProject project = requireProject();
        if (project != null) {
            BuildView.LayerMode[] modes = BuildView.LayerMode.values();
            setLayerMode(modes[(project.view().layerMode().ordinal() + 1) % modes.length]);
        }
    }

    /**
     * Jumps to the first layer of a well-known part of the build.
     *
     * @param part "foundation", "walls" or "roof"
     */
    public static void jumpToPart(String part) {
        BuildProject project = requireProject();
        if (project == null) {
            return;
        }
        Schematic schematic = project.schematic();
        int stage = schematic.stageIndex(part);
        int layer = stage >= 0 ? schematic.firstLayerOfStage(stage) : -1;
        if (layer < 0) {
            // The build has no stage of that name; fall back to where such a part usually is.
            layer = switch (part) {
                case "roof" -> schematic.sizeY() - 1;
                case "walls" -> Math.min(1, schematic.sizeY() - 1);
                default -> 0;
            };
        }
        setLayer(project, layer);
    }

    /** Jumps to the next storey above ({@code direction} 1) or below (-1) the current layer. */
    public static void changeFloor(int direction) {
        BuildProject project = requireProject();
        if (project == null) {
            return;
        }
        int[] floors = project.schematic().floors();
        int layer = project.view().layer();
        int target = layer;
        if (direction > 0) {
            for (int floor : floors) {
                if (floor > layer) {
                    target = floor;
                    break;
                }
            }
        } else {
            for (int i = floors.length - 1; i >= 0; i--) {
                if (floors[i] < layer) {
                    target = floors[i];
                    break;
                }
            }
        }
        setLayer(project, target);
    }

    private static void setLayer(BuildProject project, int layer) {
        BuildView view = project.view();
        view.setLayer(layer);
        if (view.layerMode() == BuildView.LayerMode.ALL) {
            view.setLayerMode(BuildView.LayerMode.SINGLE);
        }
        changed();
        notifyLayer(project);
    }

    private static void notifyLayer(BuildProject project) {
        BuildView view = project.view();
        notify(Component.translatable("buildingassistant.message.layer", view.layer() + 1, view.layerCount(),
                Component.translatable("buildingassistant.layer_mode."
                        + view.layerMode().name().toLowerCase(Locale.ROOT))));
    }

    // --- Stages -----------------------------------------------------------------------------------

    /** Moves to the next or previous stage. Leaves "all stages" mode so the change is visible. */
    public static void changeStage(int delta) {
        BuildProject project = requireProject();
        if (project != null) {
            setStage(project, project.view().stage() + delta);
        }
    }

    /** Selects a stage by its number, starting at 1. */
    public static void selectStage(int number) {
        BuildProject project = requireProject();
        if (project != null) {
            setStage(project, number - 1);
        }
    }

    public static void setStageMode(BuildView.StageMode mode) {
        BuildProject project = requireProject();
        if (project != null) {
            project.view().setStageMode(mode);
            changed();
            notifyStage(project);
        }
    }

    public static void cycleStageMode() {
        BuildProject project = requireProject();
        if (project != null) {
            BuildView.StageMode[] modes = BuildView.StageMode.values();
            setStageMode(modes[(project.view().stageMode().ordinal() + 1) % modes.length]);
        }
    }

    private static void setStage(BuildProject project, int stage) {
        BuildView view = project.view();
        view.setStage(stage);
        if (view.stageMode() == BuildView.StageMode.ALL) {
            view.setStageMode(BuildView.StageMode.CURRENT);
        }
        changed();
        notifyStage(project);
    }

    private static void notifyStage(BuildProject project) {
        BuildView view = project.view();
        notify(Component.translatable("buildingassistant.message.stage", view.stage() + 1, view.stageCount(),
                stageName(project.schematic(), view.stage()),
                Component.translatable("buildingassistant.stage_mode."
                        + view.stageMode().name().toLowerCase(Locale.ROOT))));
    }

    /** Called by the validator when the stage the player is working on has just been finished. */
    static void onStageCompleted(BuildProject project, int stage, ValidationState state) {
        Schematic schematic = project.schematic();
        if (state.isComplete()) {
            notify(Component.translatable("buildingassistant.message.build_complete", displayName(schematic)));
            return;
        }
        int next = -1;
        if (ClientConfig.AUTO_ADVANCE_STAGE.get()) {
            for (int candidate = stage + 1; candidate < schematic.stages().size(); candidate++) {
                if (!state.isStageComplete(candidate)) {
                    next = candidate;
                    break;
                }
            }
        }
        if (next < 0) {
            notify(Component.translatable("buildingassistant.message.stage_complete", stageName(schematic, stage)));
            return;
        }
        project.view().setStage(next);
        changed();
        notify(Component.translatable("buildingassistant.message.stage_complete_next",
                stageName(schematic, stage), stageName(schematic, next)));
    }

    /** Stage name in the player's language when a translation exists, otherwise the stored name. */
    public static Component stageName(Schematic schematic, int stage) {
        StageDefinition definition = schematic.stages().get(stage);
        return I18n.exists(definition.translationKey())
                ? Component.translatable(definition.translationKey())
                : Component.literal(definition.name());
    }

    // --- Checking ---------------------------------------------------------------------------------

    /** Points the player at the next wrong block: outlines it through walls and names what belongs there. */
    public static void nextError() {
        BuildProject project = requireProject();
        if (project == null) {
            return;
        }
        ValidationState validation = project.validation();
        if (validation == null) {
            notify(Component.translatable("buildingassistant.message.not_checked_yet"));
            return;
        }
        int index = validation.nextError(project.selectedError());
        project.setSelectedError(index);
        if (index < 0) {
            notify(Component.translatable("buildingassistant.message.no_errors"));
            return;
        }
        // Make sure the error is not hidden by the layer selection.
        BuildView view = project.view();
        if (view.layerMode() != BuildView.LayerMode.ALL) {
            view.setLayer(validation.projection().layerAt(index));
            changed();
        }
        BlockPos pos = BlockPos.of(validation.projection().positionAt(index));
        BlockState expected = validation.projection().stateAt(index);
        if (expected.isAir()) {
            notify(Component.translatable("buildingassistant.message.error_obstructed",
                    pos.getX(), pos.getY(), pos.getZ()));
        } else {
            notify(Component.translatable("buildingassistant.message.error_wrong",
                    pos.getX(), pos.getY(), pos.getZ(), expected.getBlock().getName()));
        }
    }

    /**
     * Sets how strictly the current build is checked and re-checks it. The choice also becomes the
     * default for builds started later.
     */
    public static void setValidationMode(ValidationMode mode) {
        ClientConfig.VALIDATION_MODE.set(mode);
        ClientConfig.VALIDATION_MODE.save();
        BuildProject project = ProjectManager.get().current();
        if (project != null) {
            project.setValidationMode(mode);
            changed();
        }
        notify(Component.translatable("buildingassistant.message.mode",
                Component.translatable("buildingassistant.validation_mode." + mode.name().toLowerCase(Locale.ROOT))));
    }

    /** One-line summary of the current build's state, or null when there is nothing to report. */
    @Nullable
    public static Component statusSummary() {
        BuildProject project = ProjectManager.get().current();
        ValidationState validation = project == null ? null : project.validation();
        if (validation == null) {
            return null;
        }
        return Component.translatable("buildingassistant.message.status",
                displayName(project.schematic()), Math.round(validation.progress() * 100.0F),
                validation.correct(), validation.required(), validation.missing(),
                validation.wrong(), validation.obstructed());
    }

    // --- Helpers ----------------------------------------------------------------------------------

    /** Schematic name in the player's language when a translation exists, otherwise the stored name. */
    public static Component displayName(Schematic schematic) {
        String key = schematic.metadata().nameTranslationKey();
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(schematic.metadata().name());
    }

    private static void move(BuildProject project, int dx, int dy, int dz) {
        project.move(dx, dy, dz);
        changed();
        notifyPosition(project);
    }

    private static void notifyPosition(BuildProject project) {
        BlockPos anchor = project.placement().anchor();
        notify(Component.translatable("buildingassistant.message.moved", anchor.getX(), anchor.getY(), anchor.getZ()));
    }

    /** Marks project data as changed so it gets saved. */
    private static void changed() {
        ProjectManager.get().markDirty();
    }

    /** Returns the current project, telling the player when there is none. */
    @Nullable
    private static BuildProject requireProject() {
        BuildProject project = ProjectManager.get().current();
        if (project == null) {
            notify(Component.translatable("buildingassistant.message.no_project"));
        }
        return project;
    }

    /** Short feedback above the hotbar; it does not clutter the chat. */
    private static void notify(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }

    /** The block space the player points at, or a spot a few blocks ahead when pointing at nothing. */
    private static BlockPos targetPosition(LocalPlayer player) {
        HitResult hit = player.pick(PICK_RANGE, 0.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos().relative(blockHit.getDirection());
        }
        return player.blockPosition().relative(player.getDirection(), 4);
    }

    /** Keeps rotation and mirroring, and puts the centre of the footprint on the given position. */
    private static Placement centredOn(Schematic schematic, Placement placement, BlockPos target) {
        GridTransform grid = placement.grid(schematic.sizeX(), schematic.sizeZ());
        return placement.withAnchor(target.offset(-grid.outSizeX() / 2, 0, -grid.outSizeZ() / 2));
    }
}
