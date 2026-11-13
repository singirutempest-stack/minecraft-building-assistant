package com.buildingassistant.schematic.format;

import java.util.Locale;
import java.util.Optional;

/** How demanding a build is for the player. */
public enum Difficulty {
    EASY,
    MEDIUM,
    HARD;

    private final String serializedName = name().toLowerCase(Locale.ROOT);

    public String serializedName() {
        return serializedName;
    }

    public String translationKey() {
        return "buildingassistant.difficulty." + serializedName;
    }

    public static Optional<Difficulty> bySerializedName(String name) {
        for (Difficulty difficulty : values()) {
            if (difficulty.serializedName.equals(name)) {
                return Optional.of(difficulty);
            }
        }
        return Optional.empty();
    }
}
