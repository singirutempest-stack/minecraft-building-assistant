package com.buildingassistant.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the project file of a world.
 *
 * <p>Reading is forgiving: a damaged entry is skipped and reported, the rest still loads. Nothing here
 * throws on bad content.
 */
public final class ProjectStore {

    public static final int FORMAT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ProjectStore() {
    }

    public static String toJson(SavedState state) {
        JsonObject root = new JsonObject();
        root.addProperty("format_version", FORMAT_VERSION);
        root.addProperty("current", state.currentId());
        root.addProperty("ghosts_visible", state.ghostsVisible());
        JsonArray projects = new JsonArray();
        for (SavedProject project : state.projects()) {
            JsonObject object = new JsonObject();
            object.addProperty("id", project.id());
            object.addProperty("schematic", project.schematicId());
            object.addProperty("dimension", project.dimension());
            JsonArray anchor = new JsonArray();
            anchor.add(project.x());
            anchor.add(project.y());
            anchor.add(project.z());
            object.add("anchor", anchor);
            object.addProperty("quarter_turns", project.quarterTurns());
            object.addProperty("mirrored", project.mirrored());
            object.addProperty("visible", project.visible());
            object.addProperty("paused", project.paused());
            object.addProperty("validation_mode", project.validationMode());
            object.addProperty("layer_mode", project.layerMode());
            object.addProperty("layer", project.layer());
            object.addProperty("stage_mode", project.stageMode());
            object.addProperty("stage", project.stage());
            object.addProperty("placed", project.placed());
            object.addProperty("required", project.required());
            projects.add(object);
        }
        root.add("projects", projects);
        return GSON.toJson(root);
    }

    public static Loaded fromJson(String json) {
        return read(new StringReader(json));
    }

    public static Loaded read(Reader reader) {
        List<String> problems = new ArrayList<>();
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                return new Loaded(SavedState.EMPTY, List.of("the file does not contain project data"), true);
            }
            root = element.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException | NumberFormatException e) {
            return new Loaded(SavedState.EMPTY, List.of("the file is not valid JSON: " + e.getMessage()), true);
        }

        int version = intOr(root.get("format_version"), -1);
        if (version > FORMAT_VERSION) {
            // Written by a newer mod version: leave the file alone rather than lose what it contains.
            return new Loaded(SavedState.EMPTY,
                    List.of("the file was saved by a newer version of the mod (format " + version + ")"), true);
        }

        List<SavedProject> projects = new ArrayList<>();
        JsonElement projectsElement = root.get("projects");
        if (projectsElement != null && projectsElement.isJsonArray()) {
            JsonArray array = projectsElement.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                SavedProject project = parseProject(array.get(i));
                if (project == null) {
                    problems.add("project " + (i + 1) + " is damaged and was skipped");
                } else {
                    projects.add(project);
                }
            }
        }
        SavedState state = new SavedState(stringOr(root.get("current"), ""),
                booleanOr(root.get("ghosts_visible"), true), projects);
        return new Loaded(state, problems, false);
    }

    @Nullable
    private static SavedProject parseProject(JsonElement element) {
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String id = stringOr(object.get("id"), null);
        String schematic = stringOr(object.get("schematic"), null);
        String dimension = stringOr(object.get("dimension"), null);
        JsonElement anchorElement = object.get("anchor");
        if (id == null || schematic == null || dimension == null
                || anchorElement == null || !anchorElement.isJsonArray() || anchorElement.getAsJsonArray().size() != 3) {
            return null;
        }
        JsonArray anchor = anchorElement.getAsJsonArray();
        int x = intOr(anchor.get(0), Integer.MIN_VALUE);
        int y = intOr(anchor.get(1), Integer.MIN_VALUE);
        int z = intOr(anchor.get(2), Integer.MIN_VALUE);
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) {
            return null;
        }
        return new SavedProject(id, schematic, dimension, x, y, z,
                intOr(object.get("quarter_turns"), 0) & 3,
                booleanOr(object.get("mirrored"), false),
                booleanOr(object.get("visible"), true),
                booleanOr(object.get("paused"), false),
                stringOr(object.get("validation_mode"), "strict"),
                stringOr(object.get("layer_mode"), "all"),
                intOr(object.get("layer"), 0),
                stringOr(object.get("stage_mode"), "all"),
                intOr(object.get("stage"), 0),
                intOr(object.get("placed"), 0),
                intOr(object.get("required"), 0));
    }

    /** Reads a project file. A missing file is an empty state, not a problem. */
    public static Loaded read(Path file) {
        if (!Files.isRegularFile(file)) {
            return new Loaded(SavedState.EMPTY, List.of(), false);
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return read(reader);
        } catch (IOException e) {
            return new Loaded(SavedState.EMPTY, List.of("the file could not be read: " + e.getMessage()), true);
        }
    }

    /** Writes through a temporary file so that a crash cannot leave a half-written file behind. */
    public static void write(Path file, SavedState state) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, toJson(state), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String stringOr(@Nullable JsonElement element, @Nullable String fallback) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : fallback;
    }

    private static boolean booleanOr(@Nullable JsonElement element, boolean fallback) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                ? element.getAsBoolean() : fallback;
    }

    private static int intOr(@Nullable JsonElement element, int fallback) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        try {
            double value = element.getAsDouble();
            return value == Math.rint(value) && Math.abs(value) <= Integer.MAX_VALUE ? (int) value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * @param state      what could be read
     * @param problems   what could not
     * @param unreadable true when the file as a whole could not be used; it must then not be overwritten
     *                   without keeping a copy
     */
    public record Loaded(SavedState state, List<String> problems, boolean unreadable) {
    }
}
