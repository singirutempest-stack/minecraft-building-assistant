package com.buildingassistant.gametest;

import com.buildingassistant.BuildingAssistant;
import com.buildingassistant.schematic.Schematic;
import com.buildingassistant.transform.GridTransform;
import com.buildingassistant.transform.Placement;
import com.buildingassistant.transform.SchematicProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

import static com.buildingassistant.gametest.TestSupport.check;
import static com.buildingassistant.gametest.TestSupport.checkEquals;
import static com.buildingassistant.gametest.TestSupport.load;
import static com.buildingassistant.gametest.TestSupport.schematicJson;

/**
 * Rotation and mirroring of block states. Every test compares the game's own state transform with the
 * direction computed by {@link GridTransform}; if they agree for all eight orientations, block
 * orientation can never drift away from block position.
 */
@GameTestHolder(BuildingAssistant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TransformGameTests {

    private static final boolean[] MIRROR_OPTIONS = {false, true};

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void facingBlocksFollowTheCoordinateTransform(GameTestHelper helper) {
        forEachOrientation((placement, grid) -> {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                Direction expected = transformed(grid, direction);

                checkEquals(expected, placement.transform(Blocks.OAK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, direction)).getValue(StairBlock.FACING),
                        "stairs " + direction + " with " + placement);
                checkEquals(expected, placement.transform(Blocks.OAK_DOOR.defaultBlockState()
                        .setValue(DoorBlock.FACING, direction)).getValue(DoorBlock.FACING),
                        "door " + direction + " with " + placement);
                checkEquals(expected, placement.transform(Blocks.OAK_TRAPDOOR.defaultBlockState()
                        .setValue(TrapDoorBlock.FACING, direction)).getValue(TrapDoorBlock.FACING),
                        "trapdoor " + direction + " with " + placement);
                checkEquals(expected, placement.transform(Blocks.OAK_WALL_SIGN.defaultBlockState()
                        .setValue(WallSignBlock.FACING, direction)).getValue(WallSignBlock.FACING),
                        "wall sign " + direction + " with " + placement);
                checkEquals(expected, placement.transform(Blocks.LADDER.defaultBlockState()
                        .setValue(LadderBlock.FACING, direction)).getValue(LadderBlock.FACING),
                        "ladder " + direction + " with " + placement);
                checkEquals(expected, placement.transform(Blocks.FURNACE.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, direction))
                        .getValue(BlockStateProperties.HORIZONTAL_FACING),
                        "furnace " + direction + " with " + placement);
                checkEquals(expected, placement.transform(Blocks.OBSERVER.defaultBlockState()
                        .setValue(BlockStateProperties.FACING, direction)).getValue(BlockStateProperties.FACING),
                        "observer " + direction + " with " + placement);
            }
            // Vertical facings are untouched by horizontal transforms.
            checkEquals(Direction.UP, placement.transform(Blocks.OBSERVER.defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.UP)).getValue(BlockStateProperties.FACING),
                    "observer facing up with " + placement);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void doorHingeFlipsOnlyWhenMirrored(GameTestHelper helper) {
        forEachOrientation((placement, grid) -> {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockState door = Blocks.OAK_DOOR.defaultBlockState()
                        .setValue(DoorBlock.FACING, direction)
                        .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
                DoorHingeSide expected = placement.mirrored() ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;
                checkEquals(expected, placement.transform(door).getValue(DoorBlock.HINGE),
                        "hinge of door " + direction + " with " + placement);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void standingSignsRotateInSixteenths(GameTestHelper helper) {
        forEachOrientation((placement, grid) -> {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockState sign = Blocks.OAK_SIGN.defaultBlockState()
                        .setValue(StandingSignBlock.ROTATION, signRotation(direction));
                checkEquals(signRotation(transformed(grid, direction)),
                        placement.transform(sign).getValue(StandingSignBlock.ROTATION),
                        "standing sign " + direction + " with " + placement);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void railsFollowTheCoordinateTransform(GameTestHelper helper) {
        forEachOrientation((placement, grid) -> {
            boolean swapsAxes = (grid.quarterTurns() & 1) == 1;
            checkEquals(swapsAxes ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH,
                    placement.transform(Blocks.RAIL.defaultBlockState()
                            .setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH)).getValue(RailBlock.SHAPE),
                    "straight rail with " + placement);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                checkEquals(ascending(transformed(grid, direction)),
                        placement.transform(Blocks.RAIL.defaultBlockState()
                                .setValue(RailBlock.SHAPE, ascending(direction))).getValue(RailBlock.SHAPE),
                        "ascending rail " + direction + " with " + placement);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void pillarsSwapHorizontalAxesOnQuarterTurns(GameTestHelper helper) {
        forEachOrientation((placement, grid) -> {
            boolean swapsAxes = (grid.quarterTurns() & 1) == 1;
            checkEquals(swapsAxes ? Direction.Axis.Z : Direction.Axis.X,
                    placement.transform(Blocks.OAK_LOG.defaultBlockState()
                            .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X)).getValue(RotatedPillarBlock.AXIS),
                    "log on X with " + placement);
            checkEquals(Direction.Axis.Y,
                    placement.transform(Blocks.OAK_LOG.defaultBlockState()
                            .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y)).getValue(RotatedPillarBlock.AXIS),
                    "upright log with " + placement);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void projectionKeepsBlocksInsideTheAnchoredBox(GameTestHelper helper) {
        // An L-shaped footprint, so that every orientation gives a different layout.
        Schematic schematic = load(schematicJson(3, 2, 2,
                "\"minecraft:cobblestone\", \"minecraft:oak_stairs[facing=east]\"",
                "[0, 0, 0, 0, 0], [1, 0, 0, 0, 0], [2, 0, 0, 0, 0], [0, 0, 1, 0, 0], [2, 1, 0, 1, 1]"));
        BlockPos anchor = new BlockPos(100, 64, -40);

        forEachOrientation((unanchored, ignored) -> {
            Placement placement = unanchored.withAnchor(anchor);
            GridTransform grid = placement.grid(schematic.sizeX(), schematic.sizeZ());
            SchematicProjection projection = SchematicProjection.create(schematic, placement);

            BoundingBox bounds = projection.bounds();
            checkEquals(anchor, new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()), "lowest corner");
            checkEquals(grid.outSizeX(), bounds.getXSpan(), "footprint on X with " + placement);
            checkEquals(grid.outSizeZ(), bounds.getZSpan(), "footprint on Z with " + placement);
            checkEquals(schematic.sizeY(), bounds.getYSpan(), "height");
            checkEquals(schematic.entryCount(), projection.size(), "entry count");
            check(projection.transformWarnings().isEmpty(), "vanilla blocks must transform without warnings");

            Set<Long> seen = new HashSet<>();
            for (int i = 0; i < projection.size(); i++) {
                BlockPos pos = BlockPos.of(projection.positionAt(i));
                check(bounds.isInside(pos), "block outside bounds with " + placement + ": " + pos);
                check(seen.add(projection.positionAt(i)), "two blocks share a position with " + placement);
                checkEquals(i, projection.indexOf(pos), "index lookup");

                // Map the world position back to the schematic and compare with what is stored there.
                int localX = grid.inX(pos.getX() - anchor.getX(), pos.getZ() - anchor.getZ());
                int localZ = grid.inZ(pos.getX() - anchor.getX(), pos.getZ() - anchor.getZ());
                int localY = pos.getY() - anchor.getY();
                BlockState source = schematic.stateAt(localX, localY, localZ);
                check(source != null, "projected block has no source cell with " + placement);
                checkEquals(placement.transform(source), projection.stateAt(i), "projected state");
                checkEquals(schematic.stageAt(localX, localY, localZ), projection.stageAt(i), "projected stage");
                checkEquals(localY, projection.layerAt(i), "projected layer");
            }
            checkEquals(-1, projection.indexOf(anchor.offset(-1, 0, 0)), "position outside the build");

            // The stairs face east in the schematic; in the world they must face the transformed direction.
            int stairsX = anchor.getX() + grid.outX(2, 0);
            int stairsZ = anchor.getZ() + grid.outZ(2, 0);
            int stairsIndex = projection.indexOf(new BlockPos(stairsX, anchor.getY() + 1, stairsZ));
            check(stairsIndex >= 0, "stairs not found with " + placement);
            checkEquals(transformed(grid, Direction.EAST),
                    projection.stateAt(stairsIndex).getValue(StairBlock.FACING), "stairs facing with " + placement);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void rotatingInPlaceKeepsTheFootprintCentred(GameTestHelper helper) {
        // Even and odd differences between the two extents round differently, so both are covered.
        int[][] sizes = {{9, 5}, {8, 5}, {1, 15}, {7, 7}};
        BlockPos start = new BlockPos(10, 64, 10);
        for (int[] size : sizes) {
            int sizeX = size[0];
            int sizeZ = size[1];
            Placement placement = Placement.at(start);
            int startCentreX2 = 2 * start.getX() + sizeX;
            int startCentreZ2 = 2 * start.getZ() + sizeZ;

            for (int turn = 1; turn <= 4; turn++) {
                placement = placement.rotatedClockwise(sizeX, sizeZ);
                GridTransform grid = placement.grid(sizeX, sizeZ);
                // Centres are compared doubled to stay in integers; rounding may shift them by half a block.
                int centreX2 = 2 * placement.anchor().getX() + grid.outSizeX();
                int centreZ2 = 2 * placement.anchor().getZ() + grid.outSizeZ();
                check(Math.abs(centreX2 - startCentreX2) <= 1 && Math.abs(centreZ2 - startCentreZ2) <= 1,
                        "footprint centre of " + sizeX + "x" + sizeZ + " moved after " + turn + " turns");
            }
            checkEquals(Rotation.NONE, placement.rotation(), "rotation after four turns");
            checkEquals(start, placement.anchor(), "anchor of " + sizeX + "x" + sizeZ + " after four turns");
        }
        helper.succeed();
    }

    private interface OrientationCheck {
        void check(Placement placement, GridTransform grid);
    }

    private static void forEachOrientation(OrientationCheck check) {
        for (Rotation rotation : Rotation.values()) {
            for (boolean mirrored : MIRROR_OPTIONS) {
                Placement placement = new Placement(BlockPos.ZERO, rotation, mirrored);
                check.check(placement, placement.grid(5, 7));
            }
        }
    }

    /** The direction a horizontal direction points in after the coordinate transform. */
    private static Direction transformed(GridTransform grid, Direction direction) {
        int dx = grid.outDeltaX(direction.getStepX(), direction.getStepZ());
        int dz = grid.outDeltaZ(direction.getStepX(), direction.getStepZ());
        for (Direction candidate : Direction.Plane.HORIZONTAL) {
            if (candidate.getStepX() == dx && candidate.getStepZ() == dz) {
                return candidate;
            }
        }
        throw new GameTestAssertException("No direction for step " + dx + "," + dz);
    }

    /** Standing sign rotation value whose text faces the given direction. */
    private static int signRotation(Direction direction) {
        return switch (direction) {
            case SOUTH -> 0;
            case WEST -> 4;
            case NORTH -> 8;
            case EAST -> 12;
            default -> throw new GameTestAssertException("Not horizontal: " + direction);
        };
    }

    private static RailShape ascending(Direction direction) {
        return switch (direction) {
            case EAST -> RailShape.ASCENDING_EAST;
            case WEST -> RailShape.ASCENDING_WEST;
            case NORTH -> RailShape.ASCENDING_NORTH;
            case SOUTH -> RailShape.ASCENDING_SOUTH;
            default -> throw new GameTestAssertException("Not horizontal: " + direction);
        };
    }
}
