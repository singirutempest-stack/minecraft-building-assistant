package com.buildingassistant.persistence;

/**
 * One project as stored on disk. Holds only plain values, so it can be read without a running game.
 *
 * <p>{@code placed} and {@code required} are a snapshot for display in project lists. They are never
 * trusted as progress: after loading, progress is recomputed from the blocks actually in the world.
 *
 * @param quarterTurns clockwise quarter turns, 0-3
 * @param layer        current layer, counted from 0
 * @param stage        current stage index
 */
public record SavedProject(
        String id,
        String schematicId,
        String dimension,
        int x,
        int y,
        int z,
        int quarterTurns,
        boolean mirrored,
        boolean visible,
        boolean paused,
        String validationMode,
        String layerMode,
        int layer,
        String stageMode,
        int stage,
        int placed,
        int required) {
}
