package com.buildingassistant.schematic.format;

import java.util.List;

/**
 * A schematic exactly as stored on disk, before block states are resolved against the game registry.
 * Contains no Minecraft types, so it can be parsed and validated without a running game.
 *
 * @param palette block state strings such as {@code minecraft:oak_stairs[facing=north]}
 * @param blocks  flattened entries of {@link SchematicFormat#BLOCK_STRIDE} integers each:
 *                x, y, z, palette index, stage index ({@link SchematicFormat#COMMON_STAGE} for shared blocks)
 * @param floors  layers on which a storey begins, ascending; lets the player jump between storeys
 */
public record RawSchematic(
        int formatVersion,
        SchematicMetadata metadata,
        int sizeX,
        int sizeY,
        int sizeZ,
        List<String> palette,
        List<StageDefinition> stages,
        int[] blocks,
        PreviewInfo preview,
        int[] floors) {

    /** Used when a file does not list floors: the whole build is one floor starting at the bottom. */
    public static final int[] SINGLE_FLOOR = {0};

    public int blockCount() {
        return blocks.length / SchematicFormat.BLOCK_STRIDE;
    }
}
