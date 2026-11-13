package com.buildingassistant.project;

import com.buildingassistant.schematic.Schematic;
import com.buildingassistant.transform.Placement;
import com.buildingassistant.transform.SchematicProjection;
import com.buildingassistant.validation.ValidationMode;
import com.buildingassistant.validation.ValidationState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * One build in progress: a schematic placed somewhere in a dimension.
 *
 * <p>The projection (world positions and oriented block states) is derived from the placement and
 * recomputed only after the placement changes.
 */
public final class BuildProject {

    private final UUID id;
    private final Schematic schematic;
    private final ResourceKey<Level> dimension;
    private final BuildView view;
    private Placement placement;
    private boolean visible = true;
    private boolean paused;
    private ValidationMode validationMode = ValidationMode.STRICT;
    /** Stage for which the "stage complete" message was already shown, or -1. */
    private int announcedStage = -1;
    private int lastKnownPlaced;

    private SchematicProjection projection;
    private int shapeVersion;
    @Nullable
    private ValidationState validation;
    /** Entry the player navigated to with "next error", or -1. */
    private int selectedError = -1;

    public BuildProject(UUID id, Schematic schematic, ResourceKey<Level> dimension, Placement placement) {
        this.id = id;
        this.schematic = schematic;
        this.dimension = dimension;
        this.placement = placement;
        this.view = new BuildView(schematic.sizeY(), schematic.stages().size());
    }

    /** Layer and stage selection of this build. */
    public BuildView view() {
        return view;
    }

    /**
     * A paused build stays in the project list with all its settings, but is neither drawn nor checked
     * until the player resumes it.
     */
    public boolean paused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) {
            validation = null;
            selectedError = -1;
        }
    }

    /** True when the build should currently be drawn and checked. */
    public boolean isActive() {
        return visible && !paused;
    }

    public ValidationMode validationMode() {
        return validationMode;
    }

    public void setValidationMode(ValidationMode validationMode) {
        if (this.validationMode != validationMode) {
            this.validationMode = validationMode;
            validation = null;
            selectedError = -1;
        }
    }

    /**
     * Number of correctly placed blocks the last time the build was checked. Shown in lists while the
     * build is not being checked; never used as actual progress.
     */
    public int lastKnownPlaced() {
        return lastKnownPlaced;
    }

    public void setLastKnownPlaced(int lastKnownPlaced) {
        this.lastKnownPlaced = lastKnownPlaced;
    }

    public int announcedStage() {
        return announcedStage;
    }

    public void setAnnouncedStage(int announcedStage) {
        this.announcedStage = announcedStage;
    }

    public UUID id() {
        return id;
    }

    public Schematic schematic() {
        return schematic;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public Placement placement() {
        return placement;
    }

    public void setPlacement(Placement next) {
        if (next.rotation() != placement.rotation() || next.mirrored() != placement.mirrored()) {
            shapeVersion++;
        }
        placement = next;
        projection = null;
        validation = null;
        selectedError = -1;
    }

    public void move(int dx, int dy, int dz) {
        setPlacement(placement.moved(dx, dy, dz));
    }

    public void rotateClockwise() {
        setPlacement(placement.rotatedClockwise(schematic.sizeX(), schematic.sizeZ()));
    }

    public void toggleMirror() {
        setPlacement(placement.withMirrored(!placement.mirrored()));
    }

    public boolean visible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public SchematicProjection projection() {
        if (projection == null) {
            projection = SchematicProjection.create(schematic, placement);
        }
        return projection;
    }

    /**
     * What is currently known about the build's blocks in the world, or null when the build has not been
     * checked since it was placed or moved.
     */
    @Nullable
    public ValidationState validation() {
        return validation;
    }

    public void setValidation(@Nullable ValidationState validation) {
        this.validation = validation;
        this.selectedError = -1;
    }

    public int selectedError() {
        return selectedError;
    }

    public void setSelectedError(int selectedError) {
        this.selectedError = selectedError;
    }

    /**
     * Changes whenever the build's shape in the world changes (rotation or mirroring). Moving the build
     * does not change it, which lets renderers keep their geometry and only shift it.
     */
    public int shapeVersion() {
        return shapeVersion;
    }
}
