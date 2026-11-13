package com.buildingassistant.gametest;

import com.buildingassistant.BuildingAssistant;
import com.buildingassistant.schematic.MaterialList;
import com.buildingassistant.schematic.Schematic;
import com.buildingassistant.schematic.format.SchematicFormat;
import com.buildingassistant.schematic.io.LoadResult;
import com.buildingassistant.schematic.io.SchematicLoader;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static com.buildingassistant.gametest.TestSupport.check;
import static com.buildingassistant.gametest.TestSupport.checkEquals;
import static com.buildingassistant.gametest.TestSupport.load;
import static com.buildingassistant.gametest.TestSupport.schematicJson;

/** Schematic loading against the real block registry. */
@GameTestHolder(BuildingAssistant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SchematicGameTests {

    private static final String HUT_PALETTE = """
            "minecraft:cobblestone",
            "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]",
            "minecraft:air"
            """;
    private static final String HUT_BLOCKS = """
            [0, 0, 0, 0, 0], [1, 0, 0, 0, 0], [0, 1, 0, 1, 1], [1, 1, 0, 2, -1]
            """;

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void validSchematicResolvesBlockStates(GameTestHelper helper) {
        Schematic schematic = load(schematicJson(2, 2, 1, HUT_PALETTE, HUT_BLOCKS));

        checkEquals(Blocks.COBBLESTONE.defaultBlockState(), schematic.stateAt(0, 0, 0), "state at 0,0,0");
        BlockState stairs = schematic.stateAt(0, 1, 0);
        check(stairs != null && stairs.is(Blocks.OAK_STAIRS), "stairs expected at 0,1,0");
        checkEquals(Direction.EAST, stairs.getValue(StairBlock.FACING), "stairs facing");
        checkEquals(Half.TOP, stairs.getValue(StairBlock.HALF), "stairs half");
        checkEquals(1, schematic.stageAt(0, 1, 0), "stairs stage");

        // Stored air is a clearance cell: present, but not something to place.
        check(schematic.stateAt(1, 1, 0) != null && schematic.stateAt(1, 1, 0).isAir(), "clearance cell expected");
        checkEquals(4, schematic.entryCount(), "entry count");
        checkEquals(3, schematic.requiredBlockCount(), "required block count");
        checkEquals(2, schematic.requiredBlocksInStage(0), "blocks in foundation");
        checkEquals(1, schematic.requiredBlocksInStage(1), "blocks in walls");
        checkEquals(0, schematic.requiredBlocksInStage(SchematicFormat.COMMON_STAGE), "common blocks");
        checkEquals(2, schematic.requiredBlocksInLayer(0), "blocks in layer 0");
        checkEquals(1, schematic.requiredBlocksInLayer(1), "blocks in layer 1");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void unknownBlockIsRejected(GameTestHelper helper) {
        LoadResult result = SchematicLoader.read(new StringReader(schematicJson(1, 1, 2,
                "\"minecraft:cobblestone\", \"minecraft:definitely_not_a_block\"",
                "[0, 0, 0, 0, 0], [0, 0, 1, 1, 1]")));
        check(!result.isSuccess(), "schematic with an unknown block must not load");
        check(result.errors().stream().anyMatch(error -> error.contains("definitely_not_a_block")),
                "error should name the unknown block: " + result.errors());
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void invalidBlockStatePropertyIsRejected(GameTestHelper helper) {
        LoadResult wrongValue = SchematicLoader.read(new StringReader(schematicJson(1, 1, 2,
                "\"minecraft:cobblestone\", \"minecraft:oak_stairs[facing=up]\"",
                "[0, 0, 0, 0, 0], [0, 0, 1, 1, 1]")));
        check(!wrongValue.isSuccess(), "invalid property value must not load");

        LoadResult wrongProperty = SchematicLoader.read(new StringReader(schematicJson(1, 1, 2,
                "\"minecraft:cobblestone\", \"minecraft:cobblestone[facing=north]\"",
                "[0, 0, 0, 0, 0], [0, 0, 1, 1, 1]")));
        check(!wrongProperty.isSuccess(), "unknown property must not load");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void damagedFilesNeverThrow(GameTestHelper helper) {
        String[] inputs = {"", "{", "null", "[]", "{\"format_version\": 1}", "\0\1\2", "{\"format_version\": 99}"};
        for (String input : inputs) {
            LoadResult result = SchematicLoader.read(new StringReader(input));
            check(!result.isSuccess(), "damaged input must not load: " + input);
            check(!result.errors().isEmpty(), "damaged input must produce an error message: " + input);
        }
        LoadResult missingFile = SchematicLoader.read(Path.of("this-file-does-not-exist.json"));
        check(!missingFile.isSuccess() && !missingFile.errors().isEmpty(), "a missing file must be reported");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void savedSchematicLoadsBackIdentical(GameTestHelper helper) throws IOException {
        Schematic original = load(schematicJson(2, 2, 1, HUT_PALETTE, HUT_BLOCKS));
        Path file = Files.createTempFile("buildingassistant-test", SchematicFormat.FILE_EXTENSION);
        try {
            SchematicLoader.write(original, file);
            LoadResult result = SchematicLoader.read(file);
            check(result.isSuccess(), "saved schematic must load: " + result.errors());
            Schematic copy = result.schematic();

            checkEquals(original.metadata(), copy.metadata(), "metadata");
            checkEquals(original.stages(), copy.stages(), "stages");
            checkEquals(original.entryCount(), copy.entryCount(), "entry count");
            original.forEachBlock((x, y, z, state, stage) -> {
                checkEquals(state, copy.stateAt(x, y, z), "state at " + x + "," + y + "," + z);
                checkEquals(stage, copy.stageAt(x, y, z), "stage at " + x + "," + y + "," + z);
            });
        } finally {
            Files.deleteIfExists(file);
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void stagesAndFloorsAreExposed(GameTestHelper helper) throws IOException {
        // Stage 0 has blocks on layers 0 and 2, stage 1 only on layer 1. Floors start at layers 0 and 2.
        String json = schematicJson(1, 3, 1, "\"minecraft:cobblestone\"",
                "[0, 0, 0, 0, 0], [0, 1, 0, 0, 1], [0, 2, 0, 0, 0]")
                .replace("\"blocks\":", "\"floors\": [0, 2], \"blocks\":");
        Schematic schematic = load(json);

        checkEquals(0, schematic.stageIndex("foundation"), "index of the first stage");
        checkEquals(1, schematic.stageIndex("walls"), "index of the second stage");
        checkEquals(-1, schematic.stageIndex("roof"), "index of a stage the build does not have");
        checkEquals(0, schematic.firstLayerOfStage(0), "first layer of stage 0");
        checkEquals(1, schematic.firstLayerOfStage(1), "first layer of stage 1");
        checkEquals(-1, schematic.firstLayerOfStage(SchematicFormat.COMMON_STAGE), "common stage has no blocks");
        check(Arrays.equals(new int[]{0, 2}, schematic.floors()), "floors");

        Path file = Files.createTempFile("buildingassistant-test", SchematicFormat.FILE_EXTENSION);
        try {
            SchematicLoader.write(schematic, file);
            LoadResult result = SchematicLoader.read(file);
            check(result.isSuccess(), "saved schematic must load: " + result.errors());
            check(Arrays.equals(new int[]{0, 2}, result.schematic().floors()), "floors after saving");
        } finally {
            Files.deleteIfExists(file);
        }
        check(Arrays.equals(new int[]{0}, load(schematicJson(2, 2, 1, HUT_PALETTE, HUT_BLOCKS)).floors()),
                "a schematic without listed floors has a single floor");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY_TEMPLATE)
    public static void materialListCountsItemsNotBlocks(GameTestHelper helper) {
        String palette = """
                "minecraft:oak_door[half=lower,facing=north,hinge=left,open=false,powered=false]",
                "minecraft:oak_door[half=upper,facing=north,hinge=left,open=false,powered=false]",
                "minecraft:stone_slab[type=double,waterlogged=false]",
                "minecraft:stone_slab[type=bottom,waterlogged=false]",
                "minecraft:water[level=0]",
                "minecraft:farmland[moisture=7]",
                "minecraft:wall_torch[facing=north]",
                "minecraft:red_bed[part=foot,facing=north,occupied=false]",
                "minecraft:red_bed[part=head,facing=north,occupied=false]",
                "minecraft:potted_poppy",
                "minecraft:air"
                """;
        String blocks = """
                [0, 0, 0, 0, 0], [0, 1, 0, 1, 0], [1, 0, 0, 2, 0], [2, 0, 0, 3, 0], [3, 0, 0, 4, 0],
                [4, 0, 0, 5, 1], [5, 0, 0, 6, 1], [6, 0, 0, 7, 1], [6, 0, 1, 8, 1], [7, 0, 0, 9, 1],
                [7, 1, 0, 10, -1]
                """;
        MaterialList materials = MaterialList.of(load(schematicJson(8, 2, 2, palette, blocks)));

        checkEquals(1, materials.countOf(Items.OAK_DOOR), "doors");
        checkEquals(3, materials.countOf(Items.STONE_SLAB), "slabs (double slab is two)");
        checkEquals(1, materials.countOf(Items.WATER_BUCKET), "water buckets");
        checkEquals(1, materials.countOf(Items.DIRT), "dirt for farmland");
        checkEquals(1, materials.countOf(Items.TORCH), "torches");
        checkEquals(1, materials.countOf(Items.RED_BED), "beds");
        checkEquals(1, materials.countOf(Items.FLOWER_POT), "flower pots");
        checkEquals(1, materials.countOf(Items.POPPY), "poppies");
        checkEquals(0, materials.countOf(Items.AIR), "air");
        checkEquals(10, materials.totalItems(), "total items");
        checkEquals(0, materials.blocksWithoutItem(), "blocks without item");
        helper.succeed();
    }
}
