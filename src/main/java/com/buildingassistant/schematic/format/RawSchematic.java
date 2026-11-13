package com.buildingassistant.schematic.format;

import java.util.List;

/**
 * A schematic exactly as stored on disk, before block states are resolved against the game registry.
 * Contains no Minecraft types, so it can be parsed and validated without a running game.
 *
 * @param palette block state strings such as {@code minecraft:oak_stairs[facing=north]}
 * @param blocks  flattened entries of {@link SchematicFormat#BLOCK_STRIDE} integers each:
 *                x, y, z, palette index, stage index ({@link SchematicFormat#COMMON_STAGE} for shared blocks)
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
        PreviewInfo preview) {

    public int blockCount() {
        return blocks.length / SchematicFormat.BLOCK_STRIDE;
    }
}
