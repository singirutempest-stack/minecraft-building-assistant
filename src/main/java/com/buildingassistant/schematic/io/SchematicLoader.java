package com.buildingassistant.schematic.io;

import com.buildingassistant.schematic.Schematic;
import com.buildingassistant.schematic.format.RawSchematic;
import com.buildingassistant.schematic.validation.IssueCollector;
import com.buildingassistant.schematic.validation.RawSchematicValidator;
import com.buildingassistant.schematic.validation.SchematicResolver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads and saves schematics in the mod's own format.
 *
 * <p>Loading runs three steps: parse the JSON, check the structure, resolve block states. Any failure
 * ends up in the {@link LoadResult}; no input can make these methods throw. They do not touch the world
 * and are safe to call from a background thread.
 */
public final class SchematicLoader {

    /** Far above any valid 15x15x15 schematic; guards against reading huge files into memory. */
    private static final long MAX_FILE_BYTES = 4L * 1024 * 1024;

    private SchematicLoader() {
    }

    public static LoadResult read(Path file) {
        IssueCollector issues = new IssueCollector();
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                issues.error("File is larger than " + (MAX_FILE_BYTES / (1024 * 1024)) + " MB");
                return failure(issues);
            }
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                return read(reader, issues);
            }
        } catch (IOException e) {
            issues.error("Could not read file: " + e.getMessage());
            return failure(issues);
        }
    }

    public static LoadResult read(Reader reader) {
        return read(reader, new IssueCollector());
    }

    /** Validates and resolves a schematic that was built in memory, for example by an importer. */
    public static LoadResult fromRaw(RawSchematic raw) {
        return fromRaw(raw, new IssueCollector());
    }

    /** Writes through a temporary file so that a crash cannot leave a half-written schematic behind. */
    public static void write(Schematic schematic, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, SchematicJsonCodec.toJsonString(schematic.toRaw()), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static LoadResult read(Reader reader, IssueCollector issues) {
        try {
            RawSchematic raw = SchematicJsonCodec.parse(reader, issues);
            if (raw == null) {
                return failure(issues);
            }
            return fromRaw(raw, issues);
        } catch (RuntimeException e) {
            issues.error("Unexpected error while reading schematic: " + e);
            return failure(issues);
        }
    }

    private static LoadResult fromRaw(RawSchematic raw, IssueCollector issues) {
        try {
            RawSchematicValidator.validate(raw, issues);
            if (issues.hasErrors()) {
                return failure(issues);
            }
            Schematic schematic = SchematicResolver.resolve(raw, issues);
            if (schematic == null) {
                return failure(issues);
            }
            return new LoadResult(schematic, issues.errors(), issues.warnings());
        } catch (RuntimeException e) {
            issues.error("Unexpected error while validating schematic: " + e);
            return failure(issues);
        }
    }

    private static LoadResult failure(IssueCollector issues) {
        return new LoadResult(null, issues.errors(), issues.warnings());
    }
}
