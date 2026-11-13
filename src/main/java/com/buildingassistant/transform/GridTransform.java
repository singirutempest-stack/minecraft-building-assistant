package com.buildingassistant.transform;

/**
 * Maps horizontal schematic coordinates to the rotated and mirrored footprint, using integers only.
 *
 * <p>The order matches vanilla structures: mirror first (flip along X), then rotate clockwise when seen
 * from above. The result is shifted back so that the transformed footprint again starts at (0, 0); the
 * placement anchor therefore always marks the lowest corner of the build, whatever its orientation.
 *
 * <p>Minecraft's horizontal axes: +X is east, +Z is south. One clockwise quarter turn sends a step
 * (dx, dz) to (-dz, dx), so east becomes south.
 *
 * @param sizeX        schematic extent on X before transforming
 * @param sizeZ        schematic extent on Z before transforming
 * @param quarterTurns clockwise quarter turns, 0-3
 * @param mirrored     whether X is flipped before rotating
 */
public record GridTransform(int sizeX, int sizeZ, int quarterTurns, boolean mirrored) {

    public GridTransform {
        if (sizeX < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("Size must be positive: " + sizeX + "x" + sizeZ);
        }
        quarterTurns &= 3;
    }

    public int outSizeX() {
        return (quarterTurns & 1) == 0 ? sizeX : sizeZ;
    }

    public int outSizeZ() {
        return (quarterTurns & 1) == 0 ? sizeZ : sizeX;
    }

    public int outX(int x, int z) {
        int mirroredX = mirrored ? sizeX - 1 - x : x;
        return switch (quarterTurns) {
            case 0 -> mirroredX;
            case 1 -> sizeZ - 1 - z;
            case 2 -> sizeX - 1 - mirroredX;
            default -> z;
        };
    }

    public int outZ(int x, int z) {
        int mirroredX = mirrored ? sizeX - 1 - x : x;
        return switch (quarterTurns) {
            case 0 -> z;
            case 1 -> mirroredX;
            case 2 -> sizeZ - 1 - z;
            default -> sizeX - 1 - mirroredX;
        };
    }

    /** Inverse of {@link #outX}/{@link #outZ}: the schematic X of a transformed position. */
    public int inX(int outX, int outZ) {
        int mirroredX = switch (quarterTurns) {
            case 0 -> outX;
            case 1 -> outZ;
            case 2 -> sizeX - 1 - outX;
            default -> sizeX - 1 - outZ;
        };
        return mirrored ? sizeX - 1 - mirroredX : mirroredX;
    }

    /** Inverse of {@link #outX}/{@link #outZ}: the schematic Z of a transformed position. */
    public int inZ(int outX, int outZ) {
        return switch (quarterTurns) {
            case 0 -> outZ;
            case 1 -> sizeZ - 1 - outX;
            case 2 -> sizeZ - 1 - outZ;
            default -> outX;
        };
    }

    /** X component of a direction (dx, dz) after the transform. */
    public int outDeltaX(int dx, int dz) {
        int mirroredDx = mirrored ? -dx : dx;
        return switch (quarterTurns) {
            case 0 -> mirroredDx;
            case 1 -> -dz;
            case 2 -> -mirroredDx;
            default -> dz;
        };
    }

    /** Z component of a direction (dx, dz) after the transform. */
    public int outDeltaZ(int dx, int dz) {
        int mirroredDx = mirrored ? -dx : dx;
        return switch (quarterTurns) {
            case 0 -> dz;
            case 1 -> mirroredDx;
            case 2 -> -dz;
            default -> -mirroredDx;
        };
    }
}
