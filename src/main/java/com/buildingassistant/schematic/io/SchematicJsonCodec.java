package com.buildingassistant.schematic.io;

import com.buildingassistant.schematic.format.Category;
import com.buildingassistant.schematic.format.Difficulty;
import com.buildingassistant.schematic.format.PreviewInfo;
import com.buildingassistant.schematic.format.RawSchematic;
import com.buildingassistant.schematic.format.SchematicFormat;
import com.buildingassistant.schematic.format.SchematicMetadata;
import com.buildingassistant.schematic.format.StageDefinition;
import com.buildingassistant.schematic.validation.IssueCollector;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Reads and writes the JSON form of the schematic format. Reading never throws on bad input:
 * every problem is reported through the {@link IssueCollector} and the result is {@code null}.
 */
public final class SchematicJsonCodec {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int MAX_BLOCK_ENTRIES =
            SchematicFormat.MAX_SIZE * SchematicFormat.MAX_SIZE * SchematicFormat.MAX_SIZE;

    private SchematicJsonCodec() {
    }

    @Nullable
    public static RawSchematic parse(Reader reader, IssueCollector issues) {
        JsonElement root;
        try {
            root = JsonParser.parseReader(reader);
        } catch (JsonParseException | IllegalStateException | NumberFormatException e) {
            issues.error("File is not valid JSON: " + e.getMessage());
            return null;
        }
        if (root == null || !root.isJsonObject()) {
            issues.error("File does not contain a schematic object");
            return null;
        }
        JsonObject object = root.getAsJsonObject();

        OptionalInt version = asInt(object.get("format_version"));
        if (version.isEmpty()) {
            issues.error("Missing or invalid field 'format_version'");
            return null;
        }
        if (version.getAsInt() > SchematicFormat.FORMAT_VERSION) {
            issues.error("Schematic was saved by a newer version of the mod (format " + version.getAsInt() + ")");
            return null;
        }
        if (version.getAsInt() != SchematicFormat.FORMAT_VERSION) {
            issues.error("Unsupported format version " + version.getAsInt());
            return null;
        }

        SchematicMetadata metadata = parseMetadata(object.get("metadata"), issues);
        int[] size = parseDimensions(object.get("dimensions"), issues);
        List<String> palette = parsePalette(object.get("palette"), issues);
        List<StageDefinition> stages = parseStages(object.get("stages"), issues);
        int[] blocks = parseBlocks(object.get("blocks"), issues);
        PreviewInfo preview = parsePreview(object.get("preview"), issues);
        int[] floors = parseFloors(object.get("floors"), issues);

        if (metadata == null || size == null || palette == null || stages == null || blocks == null || floors == null) {
            return null;
        }
        return new RawSchematic(version.getAsInt(), metadata, size[0], size[1], size[2], palette, stages, blocks,
                preview, floors);
    }

    /** Optional field; absent means a single floor. */
    @Nullable
    private static int[] parseFloors(@Nullable JsonElement element, IssueCollector issues) {
        if (element == null || element.isJsonNull()) {
            return RawSchematic.SINGLE_FLOOR.clone();
        }
        if (!element.isJsonArray() || element.getAsJsonArray().size() > SchematicFormat.MAX_SIZE) {
            issues.error("Field 'floors' must be a list of layer numbers");
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        int[] floors = new int[array.size()];
        for (int i = 0; i < floors.length; i++) {
            OptionalInt value = asInt(array.get(i));
            if (value.isEmpty()) {
                issues.error("Field 'floors' must be a list of layer numbers");
                return null;
            }
            floors[i] = value.getAsInt();
        }
        return floors;
    }

    @Nullable
    private static SchematicMetadata parseMetadata(@Nullable JsonElement element, IssueCollector issues) {
        JsonObject object = asObject(element, "metadata", issues);
        if (object == null) {
            return null;
        }
        String id = requiredString(object, "metadata.id", "id", issues);
        String name = requiredString(object, "metadata.name", "name", issues);
        String categoryName = requiredString(object, "metadata.category", "category", issues);
        String difficultyName = requiredString(object, "metadata.difficulty", "difficulty", issues);

        Category category = null;
        if (categoryName != null) {
            category = Category.bySerializedName(categoryName).orElse(null);
            if (category == null) {
                issues.error("Unknown category '" + categoryName + "'");
            }
        }
        Difficulty difficulty = null;
        if (difficultyName != null) {
            difficulty = Difficulty.bySerializedName(difficultyName).orElse(null);
            if (difficulty == null) {
                issues.error("Unknown difficulty '" + difficultyName + "'");
            }
        }
        if (id == null || name == null || category == null || difficulty == null) {
            return null;
        }
        return new SchematicMetadata(
                id,
                name,
                optionalString(object, "description", ""),
                category,
                difficulty,
                optionalString(object, "author", "Unknown"),
                optionalString(object, "source", ""),
                optionalString(object, "license", ""));
    }

    @Nullable
    private static int[] parseDimensions(@Nullable JsonElement element, IssueCollector issues) {
        JsonObject object = asObject(element, "dimensions", issues);
        if (object == null) {
            return null;
        }
        OptionalInt x = asInt(object.get("x"));
        OptionalInt y = asInt(object.get("y"));
        OptionalInt z = asInt(object.get("z"));
        if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
            issues.error("Field 'dimensions' must contain integer x, y and z");
            return null;
        }
        return new int[]{x.getAsInt(), y.getAsInt(), z.getAsInt()};
    }

    @Nullable
    private static List<String> parsePalette(@Nullable JsonElement element, IssueCollector issues) {
        JsonArray array = asArray(element, "palette", issues);
        if (array == null) {
            return null;
        }
        if (array.size() > SchematicFormat.MAX_PALETTE_SIZE) {
            issues.error("Palette has " + array.size() + " entries, limit is " + SchematicFormat.MAX_PALETTE_SIZE);
            return null;
        }
        List<String> palette = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement entry = array.get(i);
            if (!isString(entry)) {
                issues.error("Palette entry " + i + " is not a block state string");
                return null;
            }
            palette.add(entry.getAsString());
        }
        return palette;
    }

    @Nullable
    private static List<StageDefinition> parseStages(@Nullable JsonElement element, IssueCollector issues) {
        JsonArray array = asArray(element, "stages", issues);
        if (array == null) {
            return null;
        }
        if (array.size() > SchematicFormat.MAX_STAGES) {
            issues.error("Too many stages: " + array.size() + ", limit is " + SchematicFormat.MAX_STAGES);
            return null;
        }
        List<StageDefinition> stages = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonObject object = asObject(array.get(i), "stages[" + i + "]", issues);
            if (object == null) {
                return null;
            }
            String id = requiredString(object, "stages[" + i + "].id", "id", issues);
            String name = requiredString(object, "stages[" + i + "].name", "name", issues);
            if (id == null || name == null) {
                return null;
            }
            stages.add(new StageDefinition(id, name));
        }
        return stages;
    }

    @Nullable
    private static int[] parseBlocks(@Nullable JsonElement element, IssueCollector issues) {
        JsonArray array = asArray(element, "blocks", issues);
        if (array == null) {
            return null;
        }
        if (array.size() > MAX_BLOCK_ENTRIES) {
            issues.error("Schematic has " + array.size() + " blocks, more than fit into "
                    + SchematicFormat.MAX_SIZE + "x" + SchematicFormat.MAX_SIZE + "x" + SchematicFormat.MAX_SIZE);
            return null;
        }
        int[] blocks = new int[array.size() * SchematicFormat.BLOCK_STRIDE];
        boolean valid = true;
        for (int i = 0; i < array.size(); i++) {
            JsonElement entry = array.get(i);
            if (!entry.isJsonArray() || entry.getAsJsonArray().size() != SchematicFormat.BLOCK_STRIDE) {
                issues.error("Block " + i + " must be [x, y, z, palette, stage]");
                valid = false;
                continue;
            }
            JsonArray tuple = entry.getAsJsonArray();
            for (int j = 0; j < SchematicFormat.BLOCK_STRIDE; j++) {
                OptionalInt value = asInt(tuple.get(j));
                if (value.isEmpty()) {
                    issues.error("Block " + i + " contains a value that is not an integer");
                    valid = false;
                    break;
                }
                blocks[i * SchematicFormat.BLOCK_STRIDE + j] = value.getAsInt();
            }
        }
        return valid ? blocks : null;
    }

    private static PreviewInfo parsePreview(@Nullable JsonElement element, IssueCollector issues) {
        if (element == null || element.isJsonNull()) {
            return PreviewInfo.DEFAULT;
        }
        if (!element.isJsonObject()) {
            issues.warn("Field 'preview' is not an object, using the default camera");
            return PreviewInfo.DEFAULT;
        }
        JsonObject object = element.getAsJsonObject();
        String image = isString(object.get("image")) ? object.get("image").getAsString() : null;
        float yaw = asFloat(object.get("yaw"), PreviewInfo.DEFAULT.yaw());
        float pitch = asFloat(object.get("pitch"), PreviewInfo.DEFAULT.pitch());
        return new PreviewInfo(image, yaw, pitch);
    }

    /** Serializes with one block entry per line so that files stay readable and diff-friendly. */
    public static String toJsonString(RawSchematic raw) {
        StringBuilder out = new StringBuilder(512 + raw.blocks().length * 4);
        out.append("{\n");
        out.append("  \"format_version\": ").append(raw.formatVersion()).append(",\n");

        SchematicMetadata metadata = raw.metadata();
        JsonObject metadataJson = new JsonObject();
        metadataJson.addProperty("id", metadata.id());
        metadataJson.addProperty("name", metadata.name());
        metadataJson.addProperty("description", metadata.description());
        metadataJson.addProperty("category", metadata.category().serializedName());
        metadataJson.addProperty("difficulty", metadata.difficulty().serializedName());
        metadataJson.addProperty("author", metadata.author());
        metadataJson.addProperty("source", metadata.source());
        metadataJson.addProperty("license", metadata.license());
        out.append("  \"metadata\": ").append(GSON.toJson(metadataJson)).append(",\n");

        out.append("  \"dimensions\": {\"x\": ").append(raw.sizeX())
                .append(", \"y\": ").append(raw.sizeY())
                .append(", \"z\": ").append(raw.sizeZ()).append("},\n");

        out.append("  \"palette\": [\n");
        for (int i = 0; i < raw.palette().size(); i++) {
            out.append("    ").append(GSON.toJson(raw.palette().get(i)));
            out.append(i + 1 < raw.palette().size() ? ",\n" : "\n");
        }
        out.append("  ],\n");

        out.append("  \"stages\": [\n");
        for (int i = 0; i < raw.stages().size(); i++) {
            StageDefinition stage = raw.stages().get(i);
            JsonObject stageJson = new JsonObject();
            stageJson.addProperty("id", stage.id());
            stageJson.addProperty("name", stage.name());
            out.append("    ").append(GSON.toJson(stageJson));
            out.append(i + 1 < raw.stages().size() ? ",\n" : "\n");
        }
        out.append("  ],\n");

        JsonObject previewJson = new JsonObject();
        if (raw.preview().image() != null) {
            previewJson.addProperty("image", raw.preview().image());
        }
        previewJson.addProperty("yaw", raw.preview().yaw());
        previewJson.addProperty("pitch", raw.preview().pitch());
        out.append("  \"preview\": ").append(GSON.toJson(previewJson)).append(",\n");

        out.append("  \"floors\": [");
        for (int i = 0; i < raw.floors().length; i++) {
            out.append(i > 0 ? ", " : "").append(raw.floors()[i]);
        }
        out.append("],\n");

        out.append("  \"blocks\": [\n");
        int[] blocks = raw.blocks();
        for (int offset = 0; offset < blocks.length; offset += SchematicFormat.BLOCK_STRIDE) {
            out.append("    [");
            for (int j = 0; j < SchematicFormat.BLOCK_STRIDE; j++) {
                if (j > 0) {
                    out.append(", ");
                }
                out.append(blocks[offset + j]);
            }
            out.append(offset + SchematicFormat.BLOCK_STRIDE < blocks.length ? "],\n" : "]\n");
        }
        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }

    @Nullable
    private static JsonObject asObject(@Nullable JsonElement element, String path, IssueCollector issues) {
        if (element == null || !element.isJsonObject()) {
            issues.error("Missing or invalid field '" + path + "'");
            return null;
        }
        return element.getAsJsonObject();
    }

    @Nullable
    private static JsonArray asArray(@Nullable JsonElement element, String path, IssueCollector issues) {
        if (element == null || !element.isJsonArray()) {
            issues.error("Missing or invalid field '" + path + "'");
            return null;
        }
        return element.getAsJsonArray();
    }

    @Nullable
    private static String requiredString(JsonObject object, String path, String key, IssueCollector issues) {
        JsonElement element = object.get(key);
        if (!isString(element)) {
            issues.error("Missing or invalid field '" + path + "'");
            return null;
        }
        return element.getAsString();
    }

    private static String optionalString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return isString(element) ? element.getAsString() : fallback;
    }

    private static boolean isString(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }

    private static OptionalInt asInt(@Nullable JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return OptionalInt.empty();
        }
        double value;
        try {
            value = element.getAsDouble();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) value);
    }

    private static float asFloat(@Nullable JsonElement element, float fallback) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        try {
            float value = element.getAsFloat();
            return Float.isFinite(value) ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
