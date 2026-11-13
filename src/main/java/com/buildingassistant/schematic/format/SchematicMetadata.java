package com.buildingassistant.schematic.format;

/**
 * Descriptive data of a schematic.
 *
 * @param id          unique id, see {@link SchematicFormat#ID_PATTERN}
 * @param name        display name; built-in schematics may be overridden by a translation
 * @param description short description
 * @param author      who made the build
 * @param source      where it came from ("original" for builds made for this mod)
 * @param license     license of the build, empty when unknown
 */
public record SchematicMetadata(
        String id,
        String name,
        String description,
        Category category,
        Difficulty difficulty,
        String author,
        String source,
        String license) {

    public String nameTranslationKey() {
        return "buildingassistant.schematic." + id + ".name";
    }

    public String descriptionTranslationKey() {
        return "buildingassistant.schematic." + id + ".description";
    }
}
