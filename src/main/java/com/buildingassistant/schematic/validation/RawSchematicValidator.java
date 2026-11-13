package com.buildingassistant.schematic.validation;

import com.buildingassistant.schematic.format.RawSchematic;
import com.buildingassistant.schematic.format.SchematicFormat;
import com.buildingassistant.schematic.format.SchematicMetadata;
import com.buildingassistant.schematic.format.StageDefinition;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Structural checks that need no game registry: sizes, required fields, coordinates, duplicates and stages.
 * Block state strings are checked later by {@link SchematicResolver}.
 */
public final class RawSchematicValidator {

    private RawSchematicValidator() {
    }

    public static void validate(RawSchematic raw, IssueCollector issues) {
        if (raw.formatVersion() != SchematicFormat.FORMAT_VERSION) {
            issues.error("Unsupported format version " + raw.formatVersion()
                    + " (this build reads version " + SchematicFormat.FORMAT_VERSION + ")");
        }
        validateMetadata(raw.metadata(), issues);
        boolean sizeValid = validateSize(raw, issues);
        validatePalette(raw.palette(), issues);
        boolean stagesValid = validateStages(raw.stages(), issues);
        validateBlocks(raw, sizeValid, stagesValid, issues);
    }

    private static void validateMetadata(SchematicMetadata metadata, IssueCollector issues) {
        if (!SchematicFormat.ID_PATTERN.matcher(metadata.id()).matches()) {
            issues.error("Invalid id '" + metadata.id() + "': use 1-64 characters from a-z, 0-9 and _");
        }
        if (metadata.name().isBlank()) {
            issues.error("Name must not be empty");
        } else if (metadata.name().length() > SchematicFormat.MAX_NAME_LENGTH) {
            issues.error("Name is longer than " + SchematicFormat.MAX_NAME_LENGTH + " characters");
        }
        if (metadata.description().length() > SchematicFormat.MAX_DESCRIPTION_LENGTH) {
            issues.error("Description is longer than " + SchematicFormat.MAX_DESCRIPTION_LENGTH + " characters");
        }
    }

    private static boolean validateSize(RawSchematic raw, IssueCollector issues) {
        boolean valid = true;
        valid &= validateAxis("x", raw.sizeX(), issues);
        valid &= validateAxis("y", raw.sizeY(), issues);
        valid &= validateAxis("z", raw.sizeZ(), issues);
        return valid;
    }

    private static boolean validateAxis(String axis, int size, IssueCollector issues) {
        if (size < 1 || size > SchematicFormat.MAX_SIZE) {
            issues.error("Size on " + axis + " is " + size + ", allowed range is 1-" + SchematicFormat.MAX_SIZE);
            return false;
        }
        return true;
    }

    private static void validatePalette(List<String> palette, IssueCollector issues) {
        if (palette.isEmpty()) {
            issues.error("Palette is empty");
        } else if (palette.size() > SchematicFormat.MAX_PALETTE_SIZE) {
            issues.error("Palette has " + palette.size() + " entries, limit is " + SchematicFormat.MAX_PALETTE_SIZE);
        }
        for (int i = 0; i < palette.size(); i++) {
            if (palette.get(i).isBlank()) {
                issues.error("Palette entry " + i + " is empty");
            }
        }
    }

    private static boolean validateStages(List<StageDefinition> stages, IssueCollector issues) {
        if (stages.isEmpty()) {
            issues.error("At least one build stage is required");
            return false;
        }
        if (stages.size() > SchematicFormat.MAX_STAGES) {
            issues.error("Too many stages: " + stages.size() + ", limit is " + SchematicFormat.MAX_STAGES);
            return false;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < stages.size(); i++) {
            StageDefinition stage = stages.get(i);
            if (!SchematicFormat.ID_PATTERN.matcher(stage.id()).matches()) {
                issues.error("Stage " + i + " has invalid id '" + stage.id() + "'");
            } else if (!seen.add(stage.id())) {
                issues.error("Duplicate stage id '" + stage.id() + "'");
            }
            if (stage.name().isBlank()) {
                issues.error("Stage " + i + " has no name");
            }
        }
        return true;
    }

    private static void validateBlocks(RawSchematic raw, boolean sizeValid, boolean stagesValid, IssueCollector issues) {
        int[] blocks = raw.blocks();
        if (blocks.length % SchematicFormat.BLOCK_STRIDE != 0) {
            issues.error("Block data is truncated");
            return;
        }
        if (blocks.length == 0) {
            issues.error("Schematic contains no blocks");
            return;
        }
        if (!sizeValid) {
            // Coordinates cannot be judged against an invalid size.
            return;
        }

        int sizeX = raw.sizeX();
        int sizeY = raw.sizeY();
        int sizeZ = raw.sizeZ();
        boolean[] occupied = new boolean[sizeX * sizeY * sizeZ];
        int stageCount = raw.stages().size();
        int[] blocksPerStage = new int[stageCount];

        for (int offset = 0, entry = 0; offset < blocks.length; offset += SchematicFormat.BLOCK_STRIDE, entry++) {
            int x = blocks[offset];
            int y = blocks[offset + 1];
            int z = blocks[offset + 2];
            int paletteIndex = blocks[offset + 3];
            int stageIndex = blocks[offset + 4];

            if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
                issues.error("Block " + entry + " at (" + x + ", " + y + ", " + z + ") is outside the schematic bounds");
                continue;
            }
            int cell = (y * sizeZ + z) * sizeX + x;
            if (occupied[cell]) {
                issues.error("Duplicate block at (" + x + ", " + y + ", " + z + ")");
            }
            occupied[cell] = true;

            if (paletteIndex < 0 || paletteIndex >= raw.palette().size()) {
                issues.error("Block " + entry + " uses palette index " + paletteIndex + " which does not exist");
            }
            if (stageIndex == SchematicFormat.COMMON_STAGE) {
                continue;
            }
            if (stageIndex < 0 || stageIndex >= stageCount) {
                if (stagesValid) {
                    issues.error("Block " + entry + " uses stage index " + stageIndex + " which does not exist");
                }
            } else {
                blocksPerStage[stageIndex]++;
            }
        }

        if (stagesValid) {
            for (int i = 0; i < stageCount; i++) {
                if (blocksPerStage[i] == 0) {
                    issues.error("Stage '" + raw.stages().get(i).id() + "' has no blocks");
                }
            }
        }
    }
}
