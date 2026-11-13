package com.buildingassistant.library;

import com.buildingassistant.BuildingAssistant;
import com.buildingassistant.schematic.Schematic;
import com.buildingassistant.schematic.format.SchematicFormat;
import com.buildingassistant.schematic.io.LoadResult;
import com.buildingassistant.schematic.io.SchematicLoader;
import net.minecraft.Util;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * The schematics shipped inside the mod JAR.
 *
 * <p>Files are read and validated on a background thread; the finished map is then published in one
 * step, so readers always see either the previous or the complete new state.
 */
public final class SchematicLibrary {

    private static final String[] BUILTIN_PATH = {"assets", BuildingAssistant.MOD_ID, "schematics"};

    private static volatile Map<String, Schematic> builtin = Map.of();
    private static volatile boolean loaded;

    private SchematicLibrary() {
    }

    /** Starts loading in the background. Safe to call more than once. */
    public static CompletableFuture<Void> loadAsync() {
        return CompletableFuture.runAsync(SchematicLibrary::loadNow, Util.backgroundExecutor())
                .exceptionally(error -> {
                    BuildingAssistant.LOGGER.error("Loading the schematic library failed", error);
                    loaded = true;
                    return null;
                });
    }

    /** Loads on the calling thread. Returns every problem found, one line per message. */
    public static List<String> loadNow() {
        List<String> problems = new ArrayList<>();
        Map<String, Schematic> result = new LinkedHashMap<>();
        Path directory = ModList.get().getModFileById(BuildingAssistant.MOD_ID).getFile().findResource(BUILTIN_PATH);

        if (Files.isDirectory(directory)) {
            List<Path> files;
            try (Stream<Path> stream = Files.list(directory)) {
                files = stream
                        .filter(path -> path.getFileName().toString().endsWith(SchematicFormat.FILE_EXTENSION))
                        .sorted()
                        .toList();
            } catch (IOException e) {
                files = List.of();
                problems.add("Could not list built-in schematics: " + e.getMessage());
            }
            for (Path file : files) {
                String fileName = file.getFileName().toString();
                LoadResult loadResult = SchematicLoader.read(file);
                if (!loadResult.isSuccess()) {
                    problems.add(fileName + ": " + String.join("; ", loadResult.errors()));
                    continue;
                }
                Schematic schematic = loadResult.schematic();
                String expectedName = schematic.id() + SchematicFormat.FILE_EXTENSION;
                if (!expectedName.equals(fileName)) {
                    problems.add(fileName + ": file name does not match schematic id '" + schematic.id() + "'");
                    continue;
                }
                result.put(schematic.id(), schematic);
            }
        }

        for (String problem : problems) {
            BuildingAssistant.LOGGER.error("Built-in schematic rejected: {}", problem);
        }
        BuildingAssistant.LOGGER.info("Loaded {} built-in schematics", result.size());
        builtin = Map.copyOf(result);
        loaded = true;
        return problems;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static Optional<Schematic> get(String id) {
        return Optional.ofNullable(builtin.get(id));
    }

    public static Collection<Schematic> all() {
        return builtin.values();
    }
}
