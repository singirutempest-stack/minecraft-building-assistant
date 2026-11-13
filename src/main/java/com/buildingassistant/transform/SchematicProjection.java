package com.buildingassistant.transform;

import com.buildingassistant.schematic.Schematic;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * A schematic resolved to world positions for one {@link Placement}.
 *
 * <p>Built once per placement change and then shared by the ghost renderer and the validator, so neither
 * of them repeats coordinate or block state transforms. Entries are stored in parallel arrays ordered
 * from the bottom layer up.
 */
public final class SchematicProjection {

    private static final int ABSENT = -1;

    private final Schematic schematic;
    private final Placement placement;
    private final long[] positions;
    private final BlockState[] states;
    private final byte[] stages;
    private final byte[] layers;
    private final Long2IntOpenHashMap indexByPosition;
    private final BoundingBox bounds;
    private final List<String> warnings;

    private SchematicProjection(Schematic schematic, Placement placement, long[] positions, BlockState[] states,
                                byte[] stages, byte[] layers, BoundingBox bounds, List<String> warnings) {
        this.schematic = schematic;
        this.placement = placement;
        this.positions = positions;
        this.states = states;
        this.stages = stages;
        this.layers = layers;
        this.bounds = bounds;
        this.warnings = List.copyOf(warnings);
        this.indexByPosition = new Long2IntOpenHashMap(positions.length);
        this.indexByPosition.defaultReturnValue(ABSENT);
        for (int i = 0; i < positions.length; i++) {
            indexByPosition.put(positions[i], i);
        }
    }

    public static SchematicProjection create(Schematic schematic, Placement placement) {
        GridTransform grid = placement.grid(schematic.sizeX(), schematic.sizeZ());
        BlockPos anchor = placement.anchor();
        List<String> warnings = new ArrayList<>();

        // Each distinct state is transformed once, not once per block.
        List<BlockState> palette = schematic.palette();
        IdentityHashMap<BlockState, BlockState> transformed = new IdentityHashMap<>();
        for (BlockState state : palette) {
            BlockState result;
            try {
                result = placement.transform(state);
            } catch (RuntimeException e) {
                // A block from another mod may fail to rotate. Keep it unrotated and tell the player.
                result = state;
                warnings.add(state.getBlock().getDescriptionId());
            }
            transformed.put(state, result);
        }

        int count = schematic.entryCount();
        long[] positions = new long[count];
        BlockState[] states = new BlockState[count];
        byte[] stages = new byte[count];
        byte[] layers = new byte[count];
        int[] cursor = new int[1];
        schematic.forEachBlock((x, y, z, state, stage) -> {
            int i = cursor[0]++;
            positions[i] = BlockPos.asLong(
                    anchor.getX() + grid.outX(x, z), anchor.getY() + y, anchor.getZ() + grid.outZ(x, z));
            states[i] = transformed.get(state);
            stages[i] = (byte) stage;
            layers[i] = (byte) y;
        });

        BoundingBox bounds = new BoundingBox(
                anchor.getX(), anchor.getY(), anchor.getZ(),
                anchor.getX() + grid.outSizeX() - 1,
                anchor.getY() + schematic.sizeY() - 1,
                anchor.getZ() + grid.outSizeZ() - 1);
        return new SchematicProjection(schematic, placement, positions, states, stages, layers, bounds, warnings);
    }

    public Schematic schematic() {
        return schematic;
    }

    public Placement placement() {
        return placement;
    }

    /** Number of entries, including clearance entries. */
    public int size() {
        return positions.length;
    }

    /** World position of an entry, packed with {@link BlockPos#asLong()}. */
    public long positionAt(int index) {
        return positions[index];
    }

    /** Expected block state of an entry, already mirrored and rotated. Air marks a clearance entry. */
    public BlockState stateAt(int index) {
        return states[index];
    }

    public int stageAt(int index) {
        return stages[index];
    }

    /** Height of an entry above the bottom of the schematic. */
    public int layerAt(int index) {
        return layers[index];
    }

    /** Entry index at a world position, or -1 when the position is not part of the build. */
    public int indexOf(BlockPos pos) {
        return indexByPosition.get(pos.asLong());
    }

    public int indexOf(long packedPos) {
        return indexByPosition.get(packedPos);
    }

    public BoundingBox bounds() {
        return bounds;
    }

    /** Description ids of blocks whose state could not be rotated or mirrored and was left unchanged. */
    public List<String> transformWarnings() {
        return warnings;
    }
}
