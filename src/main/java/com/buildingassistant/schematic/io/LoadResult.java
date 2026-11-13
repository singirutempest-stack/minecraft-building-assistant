package com.buildingassistant.schematic.io;

import com.buildingassistant.schematic.Schematic;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Outcome of loading a schematic.
 *
 * @param schematic the loaded schematic, {@code null} when loading failed
 * @param errors    reasons for the failure; empty on success
 * @param warnings  non-fatal remarks
 */
public record LoadResult(@Nullable Schematic schematic, List<String> errors, List<String> warnings) {

    public boolean isSuccess() {
        return schematic != null;
    }
}
