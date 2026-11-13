package com.buildingassistant.transform;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where and how a schematic sits in the world.
 *
 * <p>Positions and block states are transformed by the same mirror-then-rotate rule, so the orientation
 * of stairs, doors and similar blocks always agrees with where they end up.
 *
 * @param anchor   world position of the lowest corner of the transformed build
 * @param rotation clockwise rotation seen from above
 * @param mirrored whether the build is flipped along its X axis before rotating
 */
public record Placement(BlockPos anchor, Rotation rotation, boolean mirrored) {

    public Placement {
        anchor = anchor.immutable();
    }

    public static Placement at(BlockPos anchor) {
        return new Placement(anchor, Rotation.NONE, false);
    }

    public Mirror mirror() {
        return mirrored ? Mirror.FRONT_BACK : Mirror.NONE;
    }

    public int quarterTurns() {
        return switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
    }

    public GridTransform grid(int sizeX, int sizeZ) {
        return new GridTransform(sizeX, sizeZ, quarterTurns(), mirrored);
    }

    /** Applies mirror and rotation to a block state with the game's own state logic. */
    @SuppressWarnings("deprecation") // The position-aware Forge overload needs a world; ghosts have none.
    public BlockState transform(BlockState state) {
        return state.mirror(mirror()).rotate(rotation);
    }

    public Placement moved(int dx, int dy, int dz) {
        return new Placement(anchor.offset(dx, dy, dz), rotation, mirrored);
    }

    public Placement withAnchor(BlockPos newAnchor) {
        return new Placement(newAnchor, rotation, mirrored);
    }

    public Placement withMirrored(boolean newMirrored) {
        return new Placement(anchor, rotation, newMirrored);
    }

    /**
     * Turns the build by one clockwise quarter turn around the centre of its footprint, so a non-square
     * build stays where the player is looking instead of swinging around a corner.
     *
     * @param sizeX schematic extent on X (untransformed)
     * @param sizeZ schematic extent on Z (untransformed)
     */
    public Placement rotatedClockwise(int sizeX, int sizeZ) {
        GridTransform before = grid(sizeX, sizeZ);
        Rotation next = rotation.getRotated(Rotation.CLOCKWISE_90);
        // Footprint extents swap; shift the anchor by half the difference to keep the centre fixed.
        // Division truncates towards zero on purpose: the two shifts of consecutive turns then cancel
        // exactly, so four turns always bring the anchor back to where it started.
        int shiftX = (before.outSizeX() - before.outSizeZ()) / 2;
        int shiftZ = (before.outSizeZ() - before.outSizeX()) / 2;
        return new Placement(anchor.offset(shiftX, 0, shiftZ), next, mirrored);
    }
}
