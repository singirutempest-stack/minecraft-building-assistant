package com.buildingassistant.validation;

import com.buildingassistant.schematic.format.SchematicFormat;
import com.buildingassistant.transform.SchematicProjection;

/**
 * What is known about every block of one placed build, plus running totals.
 *
 * <p>This is a cache of facts read from the world, not a record of what the player did: it is rebuilt
 * from the actual blocks whenever a build is placed, moved or loaded, and kept current by block change
 * notifications and a slow background re-check.
 */
public final class ValidationState {

    /** The chunk is not loaded, nothing is known. Counts as missing. */
    public static final byte UNKNOWN = 0;
    /** Nothing is there yet (air or something the player can build over, such as grass). */
    public static final byte MISSING = 1;
    public static final byte CORRECT = 2;
    public static final byte WRONG = 3;
    /** Clearance entry that is free, as it should be. */
    public static final byte CLEAR = 4;
    /** Clearance entry with a block in the way. */
    public static final byte OBSTRUCTED = 5;

    private final SchematicProjection projection;
    private final byte[] statuses;
    private final int stageCount;

    private int correct;
    private int wrong;
    private int obstructed;
    /** Correct blocks per stage; the last element belongs to the common stage. */
    private final int[] correctPerStage;
    private final int[] wrongPerStage;
    private final int[] correctPerLayer;

    private int version;
    private int scanCursor;

    public ValidationState(SchematicProjection projection) {
        this.projection = projection;
        this.statuses = new byte[projection.size()];
        this.stageCount = projection.schematic().stages().size();
        this.correctPerStage = new int[stageCount + 1];
        this.wrongPerStage = new int[stageCount + 1];
        this.correctPerLayer = new int[projection.layerCount()];
    }

    public SchematicProjection projection() {
        return projection;
    }

    public byte statusAt(int index) {
        return statuses[index];
    }

    /** @return true when the status changed */
    boolean set(int index, byte status) {
        byte previous = statuses[index];
        if (previous == status) {
            return false;
        }
        count(index, previous, -1);
        count(index, status, 1);
        statuses[index] = status;
        version++;
        return true;
    }

    private void count(int index, byte status, int delta) {
        int stage = stageSlot(projection.stageAt(index));
        switch (status) {
            case CORRECT -> {
                correct += delta;
                correctPerStage[stage] += delta;
                correctPerLayer[projection.layerAt(index)] += delta;
            }
            case WRONG -> {
                wrong += delta;
                wrongPerStage[stage] += delta;
            }
            case OBSTRUCTED -> obstructed += delta;
            default -> {
            }
        }
    }

    private int stageSlot(int stage) {
        return stage == SchematicFormat.COMMON_STAGE ? stageCount : stage;
    }

    /** Blocks the schematic asks for. */
    public int required() {
        return projection.schematic().requiredBlockCount();
    }

    public int correct() {
        return correct;
    }

    public int wrong() {
        return wrong;
    }

    public int missing() {
        return required() - correct - wrong;
    }

    public int obstructed() {
        return obstructed;
    }

    /** Wrong blocks plus blocks standing in free space. */
    public int errors() {
        return wrong + obstructed;
    }

    public boolean isComplete() {
        return correct == required() && obstructed == 0;
    }

    /** Share of required blocks that are correctly placed, 0 to 1. */
    public float progress() {
        return required() == 0 ? 1.0F : (float) correct / required();
    }

    public int correctInStage(int stage) {
        return correctPerStage[stageSlot(stage)];
    }

    public int wrongInStage(int stage) {
        return wrongPerStage[stageSlot(stage)];
    }

    public int requiredInStage(int stage) {
        return projection.schematic().requiredBlocksInStage(stage);
    }

    public boolean isStageComplete(int stage) {
        return correctInStage(stage) == requiredInStage(stage);
    }

    public float stageProgress(int stage) {
        int required = requiredInStage(stage);
        return required == 0 ? 1.0F : (float) correctInStage(stage) / required;
    }

    public int correctInLayer(int layer) {
        return correctPerLayer[layer];
    }

    /** Increases with every status change; lets renderers notice that something changed. */
    public int version() {
        return version;
    }

    /**
     * Next entry after {@code after} that is wrong or obstructed, wrapping around.
     *
     * @return the entry index, or -1 when there are no errors
     */
    public int nextError(int after) {
        int size = statuses.length;
        for (int step = 1; step <= size; step++) {
            int index = Math.floorMod(after + step, size);
            if (statuses[index] == WRONG || statuses[index] == OBSTRUCTED) {
                return index;
            }
        }
        return -1;
    }

    int scanCursor() {
        return scanCursor;
    }

    void setScanCursor(int scanCursor) {
        this.scanCursor = scanCursor;
    }
}
