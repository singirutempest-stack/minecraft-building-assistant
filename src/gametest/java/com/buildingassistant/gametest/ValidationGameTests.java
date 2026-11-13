package com.buildingassistant.gametest;

import com.buildingassistant.BuildingAssistant;
import com.buildingassistant.schematic.Schematic;
import com.buildingassistant.schematic.format.SchematicFormat;
import com.buildingassistant.transform.Placement;
import com.buildingassistant.transform.SchematicProjection;
import com.buildingassistant.validation.BlockMatcher;
import com.buildingassistant.validation.BuildingValidator;
import com.buildingassistant.validation.MaterialGroups;
import com.buildingassistant.validation.ValidationMode;
import com.buildingassistant.validation.ValidationState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.buildingassistant.gametest.TestSupport.check;
import static com.buildingassistant.gametest.TestSupport.checkEquals;
import static com.buildingassistant.gametest.TestSupport.load;
import static com.buildingassistant.gametest.TestSupport.schematicJson;

/** The validator against real blocks in a real world. */
@GameTestHolder(BuildingAssistant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ValidationGameTests {

    private static final String AREA_TEMPLATE = "area";

    private static final BlockState COBBLESTONE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState OAK_PLANKS = Blocks.OAK_PLANKS.defaultBlockState();
    private static final BlockState STAIRS_EAST = Blocks.OAK_STAIRS.defaultBlockState()
            .setValue(StairBlock.FACING, Direction.EAST);

    /**
     * A 3x2x2 build: three cobblestone and one plank block on the ground (stage 0), stairs facing east
     * on top (stage 1), and one position that has to stay free.
     */
    private static final String PALETTE = """
            "minecraft:cobblestone", "minecraft:oak_planks", "minecraft:oak_stairs[facing=east]", "minecraft:air"
            """;
    private static final String BLOCKS = """
            [0, 0, 0, 0, 0], [1, 0, 0, 0, 0], [2, 0, 0, 0, 0], [0, 0, 1, 1, 0], [2, 1, 0, 2, 1], [1, 1, 0, 3, -1]
            """;
    private static final int REQUIRED = 5;

    @GameTest(template = AREA_TEMPLATE)
    public static void emptyWorldIsAllMissing(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, ValidationMode.STRICT, true, Placement.at(BlockPos.ZERO));
        fixture.checkAll();

        checkEquals(REQUIRED, fixture.state.required(), "required");
        checkEquals(0, fixture.state.correct(), "correct");
        checkEquals(0, fixture.state.wrong(), "wrong");
        checkEquals(REQUIRED, fixture.state.missing(), "missing");
        checkEquals(0, fixture.state.obstructed(), "obstructed");
        checkEquals(0.0F, fixture.state.progress(), "progress");
        check(!fixture.state.isComplete(), "an empty site is not complete");
        checkEquals(ValidationState.CLEAR, fixture.statusAt(1, 1, 0), "free clearance position");
        checkEquals(-1, fixture.state.nextError(-1), "next error");
        helper.succeed();
    }

    @GameTest(template = AREA_TEMPLATE)
    public static void exactBuildIsComplete(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, ValidationMode.STRICT, true, Placement.at(BlockPos.ZERO));
        fixture.buildEverything();
        fixture.checkAll();

        checkEquals(REQUIRED, fixture.state.correct(), "correct");
        checkEquals(0, fixture.state.missing(), "missing");
        checkEquals(0, fixture.state.errors(), "errors");
        checkEquals(1.0F, fixture.state.progress(), "progress");
        check(fixture.state.isComplete(), "a finished build is complete");
        checkEquals(4, fixture.state.correctInStage(0), "correct in stage 0");
        checkEquals(1, fixture.state.correctInStage(1), "correct in stage 1");
        check(fixture.state.isStageComplete(0) && fixture.state.isStageComplete(1), "both stages complete");
        checkEquals(1.0F, fixture.state.stageProgress(0), "stage progress");
        checkEquals(0, fixture.state.requiredInStage(SchematicFormat.COMMON_STAGE), "common stage size");
        checkEquals(4, fixture.state.correctInLayer(0), "correct in layer 0");
        checkEquals(1, fixture.state.correctInLayer(1), "correct in layer 1");
        helper.succeed();
    }

    @GameTest(template = AREA_TEMPLATE)
    public static void strictModeRejectsSimilarMaterials(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, ValidationMode.STRICT, true, Placement.at(BlockPos.ZERO));
        fixture.buildEverything();
        fixture.set(0, 0, 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
        fixture.checkAll();

        checkEquals(ValidationState.WRONG, fixture.statusAt(0, 0, 1), "spruce instead of oak planks");
        checkEquals(1, fixture.state.wrong(), "wrong");
        checkEquals(REQUIRED - 1, fixture.state.correct(), "correct");
        checkEquals(0, fixture.state.missing(), "missing");
        checkEquals(1, fixture.state.wrongInStage(0), "wrong in stage 0");
        check(!fixture.state.isComplete() && !fixture.state.isStageComplete(0), "not complete with a wrong block");
        helper.succeed();
    }

    @GameTest(template = AREA_TEMPLATE)
    public static void flexibleModeAcceptsMaterialGroupOnly(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, ValidationMode.FLEXIBLE, true, Placement.at(BlockPos.ZERO));
        fixture.buildEverything();
        fixture.set(0, 0, 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
        fixture.set(0, 0, 0, Blocks.DIRT.defaultBlockState());
        fixture.checkAll();

        checkEquals(ValidationState.CORRECT, fixture.statusAt(0, 0, 1), "spruce planks for oak planks");
        checkEquals(ValidationState.WRONG, fixture.statusAt(0, 0, 0), "dirt for cobblestone");
        checkEquals(1, fixture.state.wrong(), "wrong");
        helper.succeed();
    }

    @GameTest(template = AREA_TEMPLATE)
    public static void orientationIsCheckedInBothModes(GameTestHelper helper) {
        BlockState oakWest = STAIRS_EAST.setValue(StairBlock.FACING, Direction.WEST);
        BlockState spruceEast = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        BlockState spruceWest = spruceEast.setValue(StairBlock.FACING, Direction.WEST);

        Fixture strict = new Fixture(helper, ValidationMode.STRICT, true, Placement.at(BlockPos.ZERO));
        strict.set(2, 1, 0, oakWest);
        strict.checkAll();
        checkEquals(ValidationState.WRONG, strict.statusAt(2, 1, 0), "strict: oak stairs facing the wrong way");
        checkEquals(BlockMatcher.Result.WRONG_STATE, strict.validator.matcher().compare(STAIRS_EAST, oakWest),
                "reason for a turned block");
        checkEquals(BlockMatcher.Result.WRONG_BLOCK, strict.validator.matcher().compare(STAIRS_EAST, COBBLESTONE),
                "reason for another block");
        strict.set(2, 1, 0, spruceEast);
        strict.checkAll();
        checkEquals(ValidationState.WRONG, strict.statusAt(2, 1, 0), "strict: spruce stairs");

        Fixture flexible = new Fixture(helper, ValidationMode.FLEXIBLE, true, Placement.at(BlockPos.ZERO));
        flexible.set(2, 1, 0, spruceEast);
        flexible.checkAll();
        checkEquals(ValidationState.CORRECT, flexible.statusAt(2, 1, 0), "flexible: spruce stairs facing right");
        flexible.set(2, 1, 0, spruceWest);
        flexible.checkAll();
        checkEquals(ValidationState.WRONG, flexible.statusAt(2, 1, 0), "flexible: spruce stairs facing wrong");
        helper.succeed();
    }

    @GameTest(template = AREA_TEMPLATE)
    public static void ignoredAndNeighbourDerivedPropertiesDoNotCount(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, ValidationMode.STRICT, true, Placement.at(BlockPos.ZERO));
        // "waterlogged" is on the ignore list; the corner shape is computed by the game from neighbours.
        fixture.set(2, 1, 0, STAIRS_EAST
                .setValue(BlockStateProperties.WATERLOGGED, true)
                .setValue(StairBlock.SHAPE, StairsShape.INNER_LEFT));
        fixture.checkAll();
        checkEquals(ValidationState.CORRECT, fixture.statusAt(2, 1, 0), "stairs differing only in ignored properties");

        // Without the ignore list entry the same block is an error.
        BuildingValidator picky = new BuildingValidator(
                new BlockMatcher(ValidationMode.STRICT, List.of(), fixture.groups), true);
        checkEquals(ValidationState.WRONG, picky.classify(STAIRS_EAST,
                STAIRS_EAST.setValue(BlockStateProperties.WATERLOGGED, true)), "waterlogged when not ignored");
        helper.succeed();
    }

    @GameTest(template = AREA_TEMPLATE)
    public static void replaceablePlantsCountAsMissingNotWrong(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, ValidationMode.STRICT, true, Placement.at(BlockPos.ZERO));
        fixture.set(0, 0, 0, Blocks.GRASS.defaultBlockState());
        fixture.set(1, 1, 0, Blocks.GRASS.defaultBlockState());
        fixture.checkAll();
        checkEquals(ValidationState.MISSING, fixture.statusAt(0, 0, 0), "grass where cobblestone goes");
        checkEquals(ValidationState.CLEAR, fixture.statusAt(1, 1, 0), "grass in a free position");
        checkEquals(0, fixture.state.errors(), "errors");
        helper.succeed();
    }

    @GameTest(template = AREA_TEMPLATE)
    public static void blocksInFreeSpaceAreReportedSeparately(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, ValidationMode.STRICT, true, Placement.at(BlockPos.ZERO));
        fixture.buildEverything();
        fixture.set(1, 1, 0, Blocks.STONE.defaultBlockState());
        fixture.checkAll();

        checkEquals(ValidationState.OBSTRUCTED, fixture.statusAt(1, 1, 0), "stone in a free position");
        checkEquals(1, fixture.state.obstructed(), "obstructed");
        checkEquals(0, fixture.state.wrong(), "wrong");
        checkEquals(1, fixture.state.errors(), "errors");
        checkEquals(1.0F, fixture.state.progress(), "progress counts required blocks only");
        check(!fixture.state.isComplete(), "an obstructed build is not complete");

        Fixture lenient = new Fixture(helper, ValidationMode.STRICT, false, Placement.at(BlockPos.ZERO));
        lenient.checkAll();
        checkEquals(ValidationState.CLEAR, lenient.statusAt(1, 1, 0), "clearance check switched off");
        check(lenient.state.isComplete(), "complete when clearance is not checked");
        helper.succeed();
    }

    @GameTest(template = AREA_TEMPLATE)
    public static void singlePositionUpdatesFollowBlockChanges(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, ValidationMode.STRICT, true, Placement.at(BlockPos.ZERO));
        fixture.checkAll();
        int versionBefore = fixture.state.version();

        fixture.set(1, 0, 0, COBBLESTONE);
        check(fixture.validator.checkPosition(fixture.state, fixture.level, fixture.world(1, 0, 0)),
                "placing a block must change the status");
        checkEquals(1, fixture.state.correct(), "correct after placing");
        check(fixture.state.version() != versionBefore, "version must change with the status");
        check(!fixture.validator.checkPosition(fixture.state, fixture.level, fixture.world(1, 0, 0)),
                "re-checking an unchanged position reports no change");

        fixture.set(1, 0, 0, Blocks.DIRT.defaultBlockState());
        fixture.validator.checkPosition(fixture.state, fixture.level, fixture.world(1, 0, 0));
        checkEquals(0, fixture.state.correct(), "correct after replacing with a wrong block");
        checkEquals(1, fixture.state.wrong(), "wrong after replacing with a wrong block");

        fixture.set(1, 0, 0, Blocks.AIR.defaultBlockState());
        fixture.validator.checkPosition(fixture.state, fixture.level, fixture.world(1, 0, 0));
        checkEquals(0, fixture.state.wrong(), "wrong after removing the block");
        checkEquals(REQUIRED, fixture.state.missing(), "missing after removing the block");

        check(!fixture.validator.checkPosition(fixture.state, fixture.level, fixture.world(0, 1, 1)),
                "positions outside the build are ignored");
        helper.succeed();
    }

    @GameTest(template = AREA_TEMPLATE)
    public static void backgroundSlicesEventuallyCoverTheBuild(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, ValidationMode.STRICT, true, Placement.at(BlockPos.ZERO));
        fixture.checkAll();
        // Blocks appear without any notification, as when a chunk is loaded.
        fixture.buildEverything();
        checkEquals(0, fixture.state.correct(), "nothing noticed yet");

        int entries = fixture.projection.size();
        fixture.validator.checkSlice(fixture.state, fixture.level, 2);
        check(fixture.state.correct() < REQUIRED, "one slice must not check everything");
        for (int checked = 2; checked < entries; checked += 2) {
            fixture.validator.checkSlice(fixture.state, fixture.level, 2);
        }
        checkEquals(REQUIRED, fixture.state.correct(), "all blocks found after a full round");
        helper.succeed();
    }

    @GameTest(template = AREA_TEMPLATE)
    public static void nextErrorCyclesThroughErrors(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, ValidationMode.STRICT, true, Placement.at(BlockPos.ZERO));
        fixture.set(0, 0, 0, Blocks.DIRT.defaultBlockState());
        fixture.set(1, 1, 0, Blocks.STONE.defaultBlockState());
        fixture.checkAll();
        checkEquals(2, fixture.state.errors(), "errors");

        int first = fixture.state.nextError(-1);
        int second = fixture.state.nextError(first);
        check(first >= 0 && second >= 0 && first != second, "two different errors expected");
        checkEquals(first, fixture.state.nextError(second), "wraps around to the first error");
        checkEquals(fixture.projection.indexOf(fixture.world(0, 0, 0)), first, "first error is the wrong block");
        checkEquals(fixture.projection.indexOf(fixture.world(1, 1, 0)), second, "second error is the obstruction");
        helper.succeed();
    }

    @GameTest(template = AREA_TEMPLATE)
    public static void rotatedAndMirroredBuildValidatesInTheWorld(GameTestHelper helper) {
        for (Rotation rotation : Rotation.values()) {
            for (boolean mirrored : new boolean[]{false, true}) {
                Fixture fixture = new Fixture(helper, ValidationMode.STRICT, true,
                        new Placement(BlockPos.ZERO, rotation, mirrored));
                fixture.clearSite();
                // Build exactly what the projection asks for, where it asks for it.
                for (int i = 0; i < fixture.projection.size(); i++) {
                    fixture.level.setBlock(BlockPos.of(fixture.projection.positionAt(i)),
                            fixture.projection.stateAt(i), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                fixture.checkAll();
                check(fixture.state.isComplete(), "build must be complete with " + fixture.projection.placement());

                // The untransformed build in the same place is wrong unless the orientation is the identity.
                if (rotation != Rotation.NONE || mirrored) {
                    Fixture unrotated = new Fixture(helper, ValidationMode.STRICT, true, Placement.at(BlockPos.ZERO));
                    unrotated.checkAll();
                    check(!unrotated.state.isComplete(),
                            "a build placed with " + fixture.projection.placement() + " must not pass as unrotated");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void materialGroupsResolveAndSurviveSaving(GameTestHelper helper) throws IOException {
        MaterialGroups groups = MaterialGroups.defaults();
        groups.resolve();
        check(groups.interchangeable(Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS), "planks are one group");
        check(groups.interchangeable(Blocks.WHITE_WOOL, Blocks.RED_WOOL), "wool colours are one group");
        check(groups.interchangeable(Blocks.GLASS, Blocks.RED_STAINED_GLASS), "glass colours are one group");
        check(groups.interchangeable(Blocks.WHITE_CONCRETE, Blocks.BLACK_CONCRETE), "concrete colours are one group");
        check(groups.interchangeable(Blocks.OAK_STAIRS, Blocks.BIRCH_STAIRS), "wooden stairs are one group");
        check(!groups.interchangeable(Blocks.OAK_PLANKS, Blocks.COBBLESTONE), "planks and cobblestone differ");
        check(!groups.interchangeable(Blocks.OAK_PLANKS, Blocks.OAK_STAIRS), "planks and stairs differ");
        check(!groups.interchangeable(Blocks.OAK_STAIRS, Blocks.STONE_STAIRS), "wooden and stone stairs differ");

        Path file = Files.createTempFile("buildingassistant-groups", ".json");
        try {
            groups.save(file);
            MaterialGroups.LoadedGroups loaded = MaterialGroups.load(file);
            check(loaded.problem() == null, "saved groups must load cleanly: " + loaded.problem());
            checkEquals(groups.groups(), loaded.groups().groups(), "groups after saving and loading");

            Files.writeString(file, "{ this is not json");
            MaterialGroups.LoadedGroups broken = MaterialGroups.load(file);
            check(broken.problem() != null, "a damaged file must be reported");
            checkEquals(groups.groups(), broken.groups().groups(), "defaults are used for a damaged file");
        } finally {
            Files.deleteIfExists(file);
        }
        helper.succeed();
    }

    /** One placed copy of the test build inside the test area. */
    private static final class Fixture {
        final ServerLevel level;
        final BlockPos anchor;
        final SchematicProjection projection;
        final MaterialGroups groups;
        final BuildingValidator validator;
        final ValidationState state;

        Fixture(GameTestHelper helper, ValidationMode mode, boolean checkClearance, Placement orientation) {
            Schematic schematic = load(schematicJson(3, 2, 2, PALETTE, BLOCKS));
            this.level = helper.getLevel();
            this.anchor = helper.absolutePos(new BlockPos(2, 2, 2));
            this.projection = SchematicProjection.create(schematic, orientation.withAnchor(anchor));
            this.groups = MaterialGroups.defaults();
            this.groups.resolve();
            this.validator = new BuildingValidator(new BlockMatcher(mode, List.of("waterlogged"), groups), checkClearance);
            this.state = new ValidationState(projection);
        }

        BlockPos world(int x, int y, int z) {
            return anchor.offset(x, y, z);
        }

        /** Sets a block exactly as given, without the game adjusting it to its neighbours. */
        void set(int x, int y, int z, BlockState blockState) {
            level.setBlock(world(x, y, z), blockState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        void buildEverything() {
            set(0, 0, 0, COBBLESTONE);
            set(1, 0, 0, COBBLESTONE);
            set(2, 0, 0, COBBLESTONE);
            set(0, 0, 1, OAK_PLANKS);
            set(2, 1, 0, STAIRS_EAST);
        }

        void clearSite() {
            for (BlockPos pos : BlockPos.betweenClosed(anchor, anchor.offset(3, 3, 3))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }

        void checkAll() {
            validator.checkAll(state, level);
        }

        byte statusAt(int x, int y, int z) {
            int index = projection.indexOf(world(x, y, z));
            check(index >= 0, "no entry at " + x + "," + y + "," + z);
            return state.statusAt(index);
        }
    }
}
