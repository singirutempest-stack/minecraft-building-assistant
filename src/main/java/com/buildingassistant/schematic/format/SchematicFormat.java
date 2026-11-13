package com.buildingassistant.schematic.format;

import java.util.regex.Pattern;

/** Constants of the Building Assistant schematic format. */
public final class SchematicFormat {

    /** Version written by this build. Files with another version are rejected with a clear message. */
    public static final int FORMAT_VERSION = 1;

    /** Largest allowed extent on each axis. */
    public static final int MAX_SIZE = 15;

    /** Number of integers per block entry: x, y, z, palette index, stage index. */
    public static final int BLOCK_STRIDE = 5;

    /** Stage index of a block that belongs to every stage. */
    public static final int COMMON_STAGE = -1;

    public static final int MAX_STAGES = 32;
    public static final int MAX_PALETTE_SIZE = 4096;
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_DESCRIPTION_LENGTH = 512;

    public static final String FILE_EXTENSION = ".json";

    /** Ids double as file names and translation key parts, so they stay lowercase ASCII. */
    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]{1,64}");

    private SchematicFormat() {
    }
}
