package com.buildingassistant.client;

import com.buildingassistant.config.ClientConfig;
import com.buildingassistant.project.BuildProject;
import com.buildingassistant.project.ProjectManager;
import com.buildingassistant.schematic.Schematic;
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
import java.util.Locale;

/**
 * What the player can do with the current project. Hotkeys, commands and screens all go through these
 * methods, so every entry point behaves the same and gives the same feedback.
 */
public final class ProjectActions {

    /** How far the crosshair may be from the player when picking a spot for a build. */
    private static final double PICK_RANGE = 24.0;

    private ProjectActions() {
    }

    /** Starts a project where the player is looking and makes it the current one. */
    @Nullable
    public static BuildProject start(Schematic schematic) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        Placement placement = centredOn(schematic, Placement.at(BlockPos.ZERO), targetPosition(player));
        BuildProject project = ProjectManager.get().start(schematic, player.level().dimension(), placement);
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
        notifyPosition(project);
    }

    public static void rotate() {
        BuildProject project = requireProject();
        if (project != null) {
            project.rotateClockwise();
            notify(Component.translatable("buildingassistant.message.rotated",
                    project.placement().quarterTurns() * 90));
        }
    }

    public static void mirror() {
        BuildProject project = requireProject();
        if (project != null) {
            project.toggleMirror();
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

    /** Switches between strict and flexible checking and re-checks every build. */
    public static void setValidationMode(ValidationMode mode) {
        ClientConfig.VALIDATION_MODE.set(mode);
        ClientConfig.VALIDATION_MODE.save();
        ClientValidation.invalidate();
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

    /** Schematic name in the player's language when a translation exists, otherwise the stored name. */
    public static Component displayName(Schematic schematic) {
        String key = schematic.metadata().nameTranslationKey();
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(schematic.metadata().name());
    }

    private static void move(BuildProject project, int dx, int dy, int dz) {
        project.move(dx, dy, dz);
        notifyPosition(project);
    }

    private static void notifyPosition(BuildProject project) {
        BlockPos anchor = project.placement().anchor();
        notify(Component.translatable("buildingassistant.message.moved", anchor.getX(), anchor.getY(), anchor.getZ()));
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
