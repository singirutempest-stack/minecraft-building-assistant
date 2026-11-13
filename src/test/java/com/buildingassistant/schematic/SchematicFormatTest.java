package com.buildingassistant.schematic;

import com.buildingassistant.schematic.format.Category;
import com.buildingassistant.schematic.format.Difficulty;
import com.buildingassistant.schematic.format.RawSchematic;
import com.buildingassistant.schematic.format.SchematicFormat;
import com.buildingassistant.schematic.io.SchematicJsonCodec;
import com.buildingassistant.schematic.validation.IssueCollector;
import com.buildingassistant.schematic.validation.RawSchematicValidator;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing and structural validation of the schematic format; needs no running game. */
class SchematicFormatTest {

    private static final String VALID = """
            {
              "format_version": 1,
              "metadata": {
                "id": "test_hut",
                "name": "Test Hut",
                "description": "A tiny hut.",
                "category": "houses",
                "difficulty": "easy",
                "author": "Tester",
                "source": "original",
                "license": "CC0"
              },
              "dimensions": {"x": 2, "y": 2, "z": 1},
              "palette": ["minecraft:cobblestone", "minecraft:oak_planks"],
              "stages": [
                {"id": "foundation", "name": "Foundation"},
                {"id": "walls", "name": "Walls"}
              ],
              "preview": {"yaw": 200, "pitch": 30},
              "blocks": [
                [0, 0, 0, 0, 0],
                [1, 0, 0, 0, 0],
                [0, 1, 0, 1, 1],
                [1, 1, 0, 1, -1]
              ]
            }
            """;

    @Test
    void validSchematicParsesAndPassesValidation() {
        IssueCollector issues = new IssueCollector();
        RawSchematic raw = SchematicJsonCodec.parse(new StringReader(VALID), issues);
        assertNotNull(raw, () -> String.join("; ", issues.errors()));
        RawSchematicValidator.validate(raw, issues);
        assertFalse(issues.hasErrors(), () -> String.join("; ", issues.errors()));

        assertEquals("test_hut", raw.metadata().id());
        assertEquals(Category.HOUSES, raw.metadata().category());
        assertEquals(Difficulty.EASY, raw.metadata().difficulty());
        assertEquals(2, raw.sizeX());
        assertEquals(2, raw.sizeY());
        assertEquals(1, raw.sizeZ());
        assertEquals(4, raw.blockCount());
        assertEquals(2, raw.stages().size());
        assertEquals(200.0F, raw.preview().yaw());
        assertEquals(SchematicFormat.COMMON_STAGE, raw.blocks()[4 * SchematicFormat.BLOCK_STRIDE - 1]);
    }

    @Test
    void writingAndReadingGivesTheSameSchematic() {
        RawSchematic original = SchematicJsonCodec.parse(new StringReader(VALID), new IssueCollector());
        assertNotNull(original);
        String json = SchematicJsonCodec.toJsonString(original);

        IssueCollector issues = new IssueCollector();
        RawSchematic copy = SchematicJsonCodec.parse(new StringReader(json), issues);
        assertNotNull(copy, () -> String.join("; ", issues.errors()));
        assertEquals(original.metadata(), copy.metadata());
        assertEquals(original.palette(), copy.palette());
        assertEquals(original.stages(), copy.stages());
        assertEquals(original.preview(), copy.preview());
        assertEquals(original.sizeX(), copy.sizeX());
        assertEquals(original.sizeY(), copy.sizeY());
        assertEquals(original.sizeZ(), copy.sizeZ());
        assertArrayEquals(original.blocks(), copy.blocks());
    }

    @Test
    void brokenJsonIsRejectedWithoutException() {
        assertParseFails("{ \"format_version\": 1, \"metadata\": ");
        assertParseFails("");
        assertParseFails("[1, 2, 3]");
        assertParseFails("not json at all");
    }

    @Test
    void missingRequiredFieldsAreRejected() {
        assertParseFails(VALID.replace("\"format_version\": 1,", ""));
        assertParseFails(VALID.replace("\"id\": \"test_hut\",", ""));
        assertParseFails(VALID.replace("\"name\": \"Test Hut\",", ""));
        assertParseFails(VALID.replace("\"dimensions\": {\"x\": 2, \"y\": 2, \"z\": 1},", ""));
        assertParseFails(VALID.replace("\"palette\": [\"minecraft:cobblestone\", \"minecraft:oak_planks\"],", ""));
    }

    @Test
    void wrongFieldTypesAreRejected() {
        assertParseFails(VALID.replace("\"x\": 2", "\"x\": \"two\""));
        assertParseFails(VALID.replace("\"x\": 2", "\"x\": 2.5"));
        assertParseFails(VALID.replace("[0, 0, 0, 0, 0]", "[0, 0, 0, 0]"));
        assertParseFails(VALID.replace("[0, 0, 0, 0, 0]", "[0, 0, \"a\", 0, 0]"));
        assertParseFails(VALID.replace("\"minecraft:cobblestone\"", "17"));
    }

    @Test
    void unknownCategoryAndDifficultyAreRejected() {
        assertParseFails(VALID.replace("\"houses\"", "\"spaceships\""));
        assertParseFails(VALID.replace("\"easy\"", "\"impossible\""));
    }

    @Test
    void newerFormatVersionIsRejected() {
        IssueCollector issues = new IssueCollector();
        assertNull(SchematicJsonCodec.parse(
                new StringReader(VALID.replace("\"format_version\": 1", "\"format_version\": 2")), issues));
        assertTrue(issues.errors().get(0).contains("newer"));
    }

    @Test
    void oversizedSchematicIsRejected() {
        assertValidationFails(VALID.replace("\"x\": 2", "\"x\": 16"), "allowed range");
        assertValidationFails(VALID.replace("\"y\": 2", "\"y\": 0"), "allowed range");
    }

    @Test
    void largestAllowedSizeIsAccepted() {
        String json = VALID.replace("{\"x\": 2, \"y\": 2, \"z\": 1}", "{\"x\": 15, \"y\": 15, \"z\": 15}");
        IssueCollector issues = new IssueCollector();
        RawSchematic raw = SchematicJsonCodec.parse(new StringReader(json), issues);
        assertNotNull(raw);
        RawSchematicValidator.validate(raw, issues);
        assertFalse(issues.hasErrors(), () -> String.join("; ", issues.errors()));
    }

    @Test
    void coordinatesOutsideBoundsAreRejected() {
        assertValidationFails(VALID.replace("[1, 0, 0, 0, 0]", "[2, 0, 0, 0, 0]"), "outside");
        assertValidationFails(VALID.replace("[1, 0, 0, 0, 0]", "[1, -1, 0, 0, 0]"), "outside");
        assertValidationFails(VALID.replace("[1, 0, 0, 0, 0]", "[1, 0, 1, 0, 0]"), "outside");
    }

    @Test
    void duplicateCoordinatesAreRejected() {
        assertValidationFails(VALID.replace("[1, 0, 0, 0, 0]", "[0, 0, 0, 0, 0]"), "Duplicate block");
    }

    @Test
    void invalidPaletteAndStageReferencesAreRejected() {
        assertValidationFails(VALID.replace("[1, 0, 0, 0, 0]", "[1, 0, 0, 2, 0]"), "palette index");
        assertValidationFails(VALID.replace("[1, 0, 0, 0, 0]", "[1, 0, 0, 0, 5]"), "stage index");
        assertValidationFails(VALID.replace("[1, 0, 0, 0, 0]", "[1, 0, 0, 0, -2]"), "stage index");
    }

    @Test
    void stageProblemsAreRejected() {
        assertValidationFails(VALID.replace("\"id\": \"walls\"", "\"id\": \"foundation\""), "Duplicate stage");
        assertValidationFails(VALID.replace("[0, 1, 0, 1, 1]", "[0, 1, 0, 1, 0]"), "has no blocks");
        assertValidationFails(VALID.replace("\"id\": \"walls\"", "\"id\": \"Walls!\""), "invalid id");
    }

    @Test
    void invalidIdAndEmptyNameAreRejected() {
        assertValidationFails(VALID.replace("\"test_hut\"", "\"Test Hut\""), "Invalid id");
        assertValidationFails(VALID.replace("\"Test Hut\"", "\"  \""), "Name must not be empty");
    }

    @Test
    void floorsDefaultToASingleFloorAndCanBeListed() {
        RawSchematic withoutFloors = SchematicJsonCodec.parse(new StringReader(VALID), new IssueCollector());
        assertNotNull(withoutFloors);
        assertArrayEquals(new int[]{0}, withoutFloors.floors());

        String json = VALID.replace("\"preview\":", "\"floors\": [0, 1], \"preview\":");
        IssueCollector issues = new IssueCollector();
        RawSchematic withFloors = SchematicJsonCodec.parse(new StringReader(json), issues);
        assertNotNull(withFloors, () -> String.join("; ", issues.errors()));
        RawSchematicValidator.validate(withFloors, issues);
        assertFalse(issues.hasErrors(), () -> String.join("; ", issues.errors()));
        assertArrayEquals(new int[]{0, 1}, withFloors.floors());

        // Floors are kept when the schematic is written again.
        RawSchematic copy = SchematicJsonCodec.parse(
                new StringReader(SchematicJsonCodec.toJsonString(withFloors)), new IssueCollector());
        assertNotNull(copy);
        assertArrayEquals(new int[]{0, 1}, copy.floors());
    }

    @Test
    void invalidFloorsAreRejected() {
        assertValidationFails(VALID.replace("\"preview\":", "\"floors\": [0, 2], \"preview\":"), "outside the schematic height");
        assertValidationFails(VALID.replace("\"preview\":", "\"floors\": [1, 0], \"preview\":"), "bottom to top");
        assertValidationFails(VALID.replace("\"preview\":", "\"floors\": [0, 0], \"preview\":"), "bottom to top");
        assertValidationFails(VALID.replace("\"preview\":", "\"floors\": [], \"preview\":"), "At least one floor");
        assertParseFails(VALID.replace("\"preview\":", "\"floors\": \"ground\", \"preview\":"));
        assertParseFails(VALID.replace("\"preview\":", "\"floors\": [0.5], \"preview\":"));
    }

    @Test
    void emptyBlockListIsRejected() {
        String json = VALID.substring(0, VALID.indexOf("\"blocks\"")) + "\"blocks\": []\n}";
        assertValidationFails(json, "no blocks");
    }

    private static void assertParseFails(String json) {
        IssueCollector issues = new IssueCollector();
        RawSchematic raw = SchematicJsonCodec.parse(new StringReader(json), issues);
        assertNull(raw, "expected the parser to reject the input");
        assertTrue(issues.hasErrors(), "a rejected file must explain why");
    }

    private static void assertValidationFails(String json, String expectedMessagePart) {
        IssueCollector issues = new IssueCollector();
        RawSchematic raw = SchematicJsonCodec.parse(new StringReader(json), issues);
        assertNotNull(raw, () -> "parse failed unexpectedly: " + String.join("; ", issues.errors()));
        RawSchematicValidator.validate(raw, issues);
        assertTrue(issues.hasErrors(), "expected validation to fail");
        assertTrue(issues.errors().stream().anyMatch(message -> message.contains(expectedMessagePart)),
                () -> "expected an error containing '" + expectedMessagePart + "' but got: " + issues.errors());
    }
}
