package com.buildingassistant.schematic.format;

/**
 * One construction stage of a schematic, for example "foundation" or "roof".
 *
 * @param id   id unique within the schematic
 * @param name display name; well-known ids are translated, see {@link #translationKey()}
 */
public record StageDefinition(String id, String name) {

    public String translationKey() {
        return "buildingassistant.stage." + id;
    }
}
