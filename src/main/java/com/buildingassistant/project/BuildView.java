package com.buildingassistant.project;

import com.buildingassistant.schematic.format.SchematicFormat;

/**
 * Which part of a build the player wants to see: a layer selection combined with a stage selection.
 *
 * <p>This only affects what is displayed. Checking the build against the world always covers every
 * block, so switching layers or stages never loses or resets progress.
 */
public final class BuildView {

    public enum LayerMode {
        /** Every layer. */
        ALL,
        /** Only the current layer. */
        SINGLE,
        /** The current layer and a few above and below it. */
        NEIGHBOURS,
        /** Everything from the bottom up to the current layer. */
        UP_TO
    }

    public enum StageMode {
        /** Every stage. */
        ALL,
        /** Only the current stage. */
        CURRENT,
        /** The current stage and all stages before it. */
        UP_TO
    }

    private final int layerCount;
    private final int stageCount;

    private LayerMode layerMode = LayerMode.ALL;
    private int layer;
    private StageMode stageMode = StageMode.ALL;
    private int stage;
    private int version;

    public BuildView(int layerCount, int stageCount) {
        this.layerCount = Math.max(1, layerCount);
        this.stageCount = Math.max(1, stageCount);
    }

    public int layerCount() {
        return layerCount;
    }

    public int stageCount() {
        return stageCount;
    }

    public LayerMode layerMode() {
        return layerMode;
    }

    public void setLayerMode(LayerMode layerMode) {
        if (this.layerMode != layerMode) {
            this.layerMode = layerMode;
            version++;
        }
    }

    /** Current layer, counted from 0 at the bottom. */
    public int layer() {
        return layer;
    }

    /** Sets the current layer, clamped to the build's height. */
    public void setLayer(int layer) {
        int clamped = Math.max(0, Math.min(layerCount - 1, layer));
        if (this.layer != clamped) {
            this.layer = clamped;
            version++;
        }
    }

    public StageMode stageMode() {
        return stageMode;
    }

    public void setStageMode(StageMode stageMode) {
        if (this.stageMode != stageMode) {
            this.stageMode = stageMode;
            version++;
        }
    }

    /** Index of the current stage. */
    public int stage() {
        return stage;
    }

    /** Sets the current stage, clamped to the stages the build has. */
    public void setStage(int stage) {
        int clamped = Math.max(0, Math.min(stageCount - 1, stage));
        if (this.stage != clamped) {
            this.stage = clamped;
            version++;
        }
    }

    /**
     * Whether a block is shown.
     *
     * @param blockLayer      the block's layer
     * @param blockStage      the block's stage, or {@link SchematicFormat#COMMON_STAGE}
     * @param neighbourRange  how many layers above and below count in {@link LayerMode#NEIGHBOURS}
     */
    public boolean isVisible(int blockLayer, int blockStage, int neighbourRange) {
        boolean layerVisible = switch (layerMode) {
            case ALL -> true;
            case SINGLE -> blockLayer == layer;
            case NEIGHBOURS -> Math.abs(blockLayer - layer) <= neighbourRange;
            case UP_TO -> blockLayer <= layer;
        };
        if (!layerVisible) {
            return false;
        }
        // Blocks of the common stage belong to every stage.
        if (blockStage == SchematicFormat.COMMON_STAGE) {
            return true;
        }
        return switch (stageMode) {
            case ALL -> true;
            case CURRENT -> blockStage == stage;
            case UP_TO -> blockStage <= stage;
        };
    }

    /** True when nothing is filtered out. */
    public boolean showsEverything() {
        return layerMode == LayerMode.ALL && stageMode == StageMode.ALL;
    }

    /** Increases with every change; lets renderers notice that the selection changed. */
    public int version() {
        return version;
    }
}
