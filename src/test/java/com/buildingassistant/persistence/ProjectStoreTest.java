package com.buildingassistant.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectStoreTest {

    private static final SavedProject CABIN = new SavedProject(
            "3f1c2a9e-5b7d-4c11-9a2e-0d6f8b4c1e22", "oak_cabin", "minecraft:overworld",
            -120, 64, 305, 3, true, true, false, "flexible", "single", 4, "current", 2, 39, 184);
    private static final SavedProject TOWER = new SavedProject(
            "9d2b7c10-1a3f-4e58-8c6d-2f5e7a9b0c33", "watch_tower", "minecraft:the_nether",
            8, 70, -16, 0, false, false, true, "strict", "all", 0, "all", 0, 0, 96);

    @Test
    void stateSurvivesWritingAndReading() {
        SavedState state = new SavedState(CABIN.id(), false, List.of(CABIN, TOWER));
        ProjectStore.Loaded loaded = ProjectStore.fromJson(ProjectStore.toJson(state));

        assertFalse(loaded.unreadable());
        assertTrue(loaded.problems().isEmpty(), loaded.problems().toString());
        assertEquals(state, loaded.state());
    }

    @Test
    void fileRoundTripAndMissingFile(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("nested").resolve("world.json");
        ProjectStore.Loaded missing = ProjectStore.read(file);
        assertFalse(missing.unreadable(), "a world without saved projects is not an error");
        assertEquals(SavedState.EMPTY, missing.state());

        SavedState state = new SavedState("", true, List.of(TOWER));
        ProjectStore.write(file, state);
        assertEquals(state, ProjectStore.read(file).state());
        assertFalse(Files.exists(file.resolveSibling("world.json.tmp")), "temporary file must not be left behind");

        // Overwriting replaces the previous content completely.
        ProjectStore.write(file, SavedState.EMPTY);
        assertEquals(SavedState.EMPTY, ProjectStore.read(file).state());
    }

    @Test
    void brokenJsonIsReportedAsUnreadable() {
        for (String json : new String[]{"", "{ \"projects\": [", "[1, 2]", "certainly not json"}) {
            ProjectStore.Loaded loaded = ProjectStore.fromJson(json);
            assertTrue(loaded.unreadable(), "should be unreadable: " + json);
            assertFalse(loaded.problems().isEmpty());
            assertEquals(SavedState.EMPTY, loaded.state());
        }
    }

    @Test
    void newerFormatIsNotTouched() {
        ProjectStore.Loaded loaded = ProjectStore.fromJson("{\"format_version\": 99, \"projects\": []}");
        assertTrue(loaded.unreadable());
        assertTrue(loaded.problems().get(0).contains("newer"));
    }

    @Test
    void damagedEntriesAreSkippedAndTheRestLoads() {
        String json = """
                {
                  "format_version": 1,
                  "current": "x",
                  "ghosts_visible": true,
                  "projects": [
                    {"id": "a", "schematic": "oak_cabin", "dimension": "minecraft:overworld", "anchor": [1, 2, 3]},
                    {"id": "b", "schematic": "oak_cabin", "dimension": "minecraft:overworld", "anchor": [1, 2]},
                    {"id": "c", "dimension": "minecraft:overworld", "anchor": [1, 2, 3]},
                    "not an object",
                    {"id": "d", "schematic": "oak_cabin", "dimension": "minecraft:overworld", "anchor": [1, "two", 3]}
                  ]
                }
                """;
        ProjectStore.Loaded loaded = ProjectStore.fromJson(json);

        assertFalse(loaded.unreadable());
        assertEquals(4, loaded.problems().size());
        assertEquals(1, loaded.state().projects().size());
        SavedProject project = loaded.state().projects().get(0);
        assertEquals("a", project.id());
        assertEquals(3, project.z());
        // Fields that are absent get safe defaults.
        assertEquals(0, project.quarterTurns());
        assertFalse(project.mirrored());
        assertTrue(project.visible());
        assertFalse(project.paused());
        assertEquals("strict", project.validationMode());
        assertEquals("all", project.layerMode());
        assertEquals("all", project.stageMode());
    }

    @Test
    void quarterTurnsAreKeptInRange() {
        String json = """
                {"format_version": 1, "projects": [
                  {"id": "a", "schematic": "s", "dimension": "minecraft:overworld", "anchor": [0, 0, 0], "quarter_turns": 7}
                ]}
                """;
        assertEquals(3, ProjectStore.fromJson(json).state().projects().get(0).quarterTurns());
    }
}
