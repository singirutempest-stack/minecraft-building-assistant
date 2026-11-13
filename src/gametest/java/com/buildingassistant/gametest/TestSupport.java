package com.buildingassistant.gametest;

import com.buildingassistant.schematic.Schematic;
import com.buildingassistant.schematic.io.LoadResult;
import com.buildingassistant.schematic.io.SchematicLoader;
import net.minecraft.gametest.framework.GameTestAssertException;

import java.io.StringReader;
import java.util.Objects;

/** Shared helpers for GameTests. */
final class TestSupport {

    /** Name of the empty structure every logic-only test runs in. */
    static final String EMPTY_TEMPLATE = "empty";

    private TestSupport() {
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    static void checkEquals(Object expected, Object actual, String what) {
        if (!Objects.equals(expected, actual)) {
            throw new GameTestAssertException(what + ": expected " + expected + " but was " + actual);
        }
    }

    static Schematic load(String json) {
        LoadResult result = SchematicLoader.read(new StringReader(json));
        if (!result.isSuccess()) {
            throw new GameTestAssertException("Schematic failed to load: " + result.errors());
        }
        return result.schematic();
    }

    /** Builds schematic JSON around the given palette and block entries. */
    static String schematicJson(int sizeX, int sizeY, int sizeZ, String paletteJson, String blocksJson) {
        return """
                {
                  "format_version": 1,
                  "metadata": {"id": "test", "name": "Test", "category": "houses", "difficulty": "easy"},
                  "dimensions": {"x": %d, "y": %d, "z": %d},
                  "palette": [%s],
                  "stages": [{"id": "foundation", "name": "Foundation"}, {"id": "walls", "name": "Walls"}],
                  "blocks": [%s]
                }
                """.formatted(sizeX, sizeY, sizeZ, paletteJson, blocksJson);
    }
}
