package com.buildingassistant.transform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridTransformTest {

    private static final int[][] SIZES = {{1, 1}, {3, 5}, {7, 2}, {15, 15}, {1, 15}};

    @Test
    void identityLeavesCoordinatesUnchanged() {
        GridTransform grid = new GridTransform(3, 5, 0, false);
        assertEquals(3, grid.outSizeX());
        assertEquals(5, grid.outSizeZ());
        assertEquals(2, grid.outX(2, 4));
        assertEquals(4, grid.outZ(2, 4));
    }

    @Test
    void quarterTurnSendsEastToSouth() {
        // 3 wide on X, 5 long on Z. After one clockwise turn the footprint is 5 on X and 3 on Z.
        GridTransform grid = new GridTransform(3, 5, 1, false);
        assertEquals(5, grid.outSizeX());
        assertEquals(3, grid.outSizeZ());
        // The north-west corner ends up in the north-east corner.
        assertEquals(4, grid.outX(0, 0));
        assertEquals(0, grid.outZ(0, 0));
        // A step east (+X) becomes a step south (+Z).
        assertEquals(0, grid.outDeltaX(1, 0));
        assertEquals(1, grid.outDeltaZ(1, 0));
    }

    @Test
    void halfTurnFlipsBothAxes() {
        GridTransform grid = new GridTransform(3, 5, 2, false);
        assertEquals(3, grid.outSizeX());
        assertEquals(5, grid.outSizeZ());
        assertEquals(2, grid.outX(0, 0));
        assertEquals(4, grid.outZ(0, 0));
        assertEquals(-1, grid.outDeltaX(1, 0));
        assertEquals(-1, grid.outDeltaZ(0, 1));
    }

    @Test
    void threeQuarterTurnSendsEastToNorth() {
        GridTransform grid = new GridTransform(3, 5, 3, false);
        assertEquals(5, grid.outSizeX());
        assertEquals(3, grid.outSizeZ());
        assertEquals(0, grid.outX(0, 0));
        assertEquals(2, grid.outZ(0, 0));
        assertEquals(0, grid.outDeltaX(1, 0));
        assertEquals(-1, grid.outDeltaZ(1, 0));
    }

    @Test
    void mirrorFlipsOnlyX() {
        GridTransform grid = new GridTransform(3, 5, 0, true);
        assertEquals(2, grid.outX(0, 1));
        assertEquals(1, grid.outZ(0, 1));
        assertEquals(-1, grid.outDeltaX(1, 0));
        assertEquals(1, grid.outDeltaZ(0, 1));
    }

    @Test
    void quarterTurnsWrapAround() {
        assertEquals(new GridTransform(3, 5, 1, false), new GridTransform(3, 5, 5, false));
        assertEquals(new GridTransform(3, 5, 3, false), new GridTransform(3, 5, -1, false));
    }

    @Test
    void everyOrientationIsABijectionOntoTheFootprint() {
        forEachOrientation((grid) -> {
            boolean[] hit = new boolean[grid.outSizeX() * grid.outSizeZ()];
            for (int x = 0; x < grid.sizeX(); x++) {
                for (int z = 0; z < grid.sizeZ(); z++) {
                    int outX = grid.outX(x, z);
                    int outZ = grid.outZ(x, z);
                    assertTrue(outX >= 0 && outX < grid.outSizeX(), () -> "x out of footprint for " + grid);
                    assertTrue(outZ >= 0 && outZ < grid.outSizeZ(), () -> "z out of footprint for " + grid);
                    int cell = outZ * grid.outSizeX() + outX;
                    assertFalse(hit[cell], () -> "two cells map to the same position for " + grid);
                    hit[cell] = true;
                }
            }
        });
    }

    @Test
    void inverseRestoresTheOriginalCoordinates() {
        forEachOrientation((grid) -> {
            for (int x = 0; x < grid.sizeX(); x++) {
                for (int z = 0; z < grid.sizeZ(); z++) {
                    int outX = grid.outX(x, z);
                    int outZ = grid.outZ(x, z);
                    assertEquals(x, grid.inX(outX, outZ), () -> "inverse x for " + grid);
                    assertEquals(z, grid.inZ(outX, outZ), () -> "inverse z for " + grid);
                }
            }
        });
    }

    @Test
    void neighboursMoveByTheTransformedDirection() {
        // If this holds, a block's facing (transformed as a direction) always points at the same
        // neighbour it pointed at before, which is what keeps positions and orientations in sync.
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        forEachOrientation((grid) -> {
            for (int x = 0; x < grid.sizeX(); x++) {
                for (int z = 0; z < grid.sizeZ(); z++) {
                    for (int[] step : steps) {
                        int nx = x + step[0];
                        int nz = z + step[1];
                        if (nx < 0 || nx >= grid.sizeX() || nz < 0 || nz >= grid.sizeZ()) {
                            continue;
                        }
                        assertEquals(grid.outX(x, z) + grid.outDeltaX(step[0], step[1]), grid.outX(nx, nz),
                                () -> "neighbour x for " + grid);
                        assertEquals(grid.outZ(x, z) + grid.outDeltaZ(step[0], step[1]), grid.outZ(nx, nz),
                                () -> "neighbour z for " + grid);
                    }
                }
            }
        });
    }

    @Test
    void fourQuarterTurnsReturnToTheStart() {
        for (int[] size : SIZES) {
            for (int x = 0; x < size[0]; x++) {
                for (int z = 0; z < size[1]; z++) {
                    int cx = x;
                    int cz = z;
                    int sx = size[0];
                    int sz = size[1];
                    for (int turn = 0; turn < 4; turn++) {
                        GridTransform quarter = new GridTransform(sx, sz, 1, false);
                        int nextX = quarter.outX(cx, cz);
                        int nextZ = quarter.outZ(cx, cz);
                        cx = nextX;
                        cz = nextZ;
                        sx = quarter.outSizeX();
                        sz = quarter.outSizeZ();
                    }
                    assertEquals(x, cx);
                    assertEquals(z, cz);
                }
            }
        }
    }

    @Test
    void composedQuarterTurnsMatchDirectRotation() {
        for (int[] size : SIZES) {
            for (int turns = 0; turns < 4; turns++) {
                GridTransform direct = new GridTransform(size[0], size[1], turns, false);
                for (int x = 0; x < size[0]; x++) {
                    for (int z = 0; z < size[1]; z++) {
                        int cx = x;
                        int cz = z;
                        int sx = size[0];
                        int sz = size[1];
                        for (int turn = 0; turn < turns; turn++) {
                            GridTransform quarter = new GridTransform(sx, sz, 1, false);
                            int nextX = quarter.outX(cx, cz);
                            int nextZ = quarter.outZ(cx, cz);
                            cx = nextX;
                            cz = nextZ;
                            sx = quarter.outSizeX();
                            sz = quarter.outSizeZ();
                        }
                        assertEquals(cx, direct.outX(x, z));
                        assertEquals(cz, direct.outZ(x, z));
                    }
                }
            }
        }
    }

    @Test
    void mirroringTwiceIsIdentity() {
        for (int[] size : SIZES) {
            GridTransform mirror = new GridTransform(size[0], size[1], 0, true);
            for (int x = 0; x < size[0]; x++) {
                for (int z = 0; z < size[1]; z++) {
                    assertEquals(x, mirror.outX(mirror.outX(x, z), mirror.outZ(x, z)));
                    assertEquals(z, mirror.outZ(mirror.outX(x, z), mirror.outZ(x, z)));
                }
            }
        }
    }

    private interface GridCheck {
        void check(GridTransform grid);
    }

    private static void forEachOrientation(GridCheck check) {
        for (int[] size : SIZES) {
            for (int turns = 0; turns < 4; turns++) {
                check.check(new GridTransform(size[0], size[1], turns, false));
                check.check(new GridTransform(size[0], size[1], turns, true));
            }
        }
    }
}
