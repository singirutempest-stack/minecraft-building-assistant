package com.buildingassistant.schematic.format;

import java.util.Locale;
import java.util.Optional;

/** Library section a schematic is listed under. */
public enum Category {
    MEDIEVAL,
    FANTASY,
    MODERN,
    HOUSES,
    FARMS,
    DECORATIONS,
    INDUSTRIAL,
    INTERIOR,
    STORAGE,
    SURVIVAL,
    /** Imported or player-made schematics that were not assigned a section. */
    CUSTOM;

    private final String serializedName = name().toLowerCase(Locale.ROOT);

    public String serializedName() {
        return serializedName;
    }

    public String translationKey() {
        return "buildingassistant.category." + serializedName;
    }

    public static Optional<Category> bySerializedName(String name) {
        for (Category category : values()) {
            if (category.serializedName.equals(name)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
