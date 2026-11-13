package com.buildingassistant.config;

import com.buildingassistant.validation.ValidationMode;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Player preferences. Stored by Forge in {@code config/buildingassistant-client.toml}, so they survive
 * game restarts and can also be edited by hand.
 */
public final class ClientConfig {

    private static final Pattern HEX_COLOR = Pattern.compile("[0-9a-fA-F]{6}");

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue GHOST_OPACITY;
    public static final ForgeConfigSpec.EnumValue<GhostStyle> GHOST_STYLE;
    public static final ForgeConfigSpec.ConfigValue<String> MISSING_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> WRONG_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> CLEARANCE_COLOR;
    public static final ForgeConfigSpec.BooleanValue HIGHLIGHT_WRONG_BLOCKS;

    public static final ForgeConfigSpec.EnumValue<ValidationMode> VALIDATION_MODE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IGNORED_PROPERTIES;
    public static final ForgeConfigSpec.BooleanValue CHECK_CLEARANCE;
    public static final ForgeConfigSpec.IntValue RESCAN_INTERVAL_TICKS;

    public static final ForgeConfigSpec.IntValue NEIGHBOUR_LAYER_RANGE;

    public static final ForgeConfigSpec.BooleanValue HUD_ENABLED;
    public static final ForgeConfigSpec.EnumValue<HudCorner> HUD_CORNER;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Ghost block display").push("ghost");
        GHOST_OPACITY = builder
                .comment("Opacity of ghost blocks, from 0.15 (faint) to 1.0 (solid).")
                .defineInRange("opacity", 0.6, 0.15, 1.0);
        GHOST_STYLE = builder
                .comment("How missing blocks are drawn.")
                .defineEnum("style", GhostStyle.TEXTURED);
        MISSING_COLOR = builder
                .comment("Colour of blocks that still have to be placed, as RRGGBB. Used by the TINTED and OUTLINE styles.")
                .define("missingColor", "8FD3FF", ClientConfig::isHexColor);
        WRONG_COLOR = builder
                .comment("Highlight colour of wrongly placed blocks, as RRGGBB.")
                .define("wrongColor", "E0352B", ClientConfig::isHexColor);
        CLEARANCE_COLOR = builder
                .comment("Highlight colour of blocks standing where the build needs free space, as RRGGBB.")
                .define("clearanceColor", "E8A317", ClientConfig::isHexColor);
        HIGHLIGHT_WRONG_BLOCKS = builder
                .comment("Mark blocks that do not match the schematic.")
                .define("highlightWrongBlocks", true);
        builder.pop();

        builder.comment("Checking placed blocks against the schematic").push("validation");
        VALIDATION_MODE = builder
                .comment("STRICT: block and state must match. FLEXIBLE: blocks of the same material group are accepted.")
                .defineEnum("mode", ValidationMode.STRICT);
        IGNORED_PROPERTIES = builder
                .comment("Block state properties that are never compared. These change on their own while playing",
                        "(a door being open, a lamp being lit), so comparing them would report false errors.")
                .defineList("ignoredProperties",
                        List.of("waterlogged", "powered", "lit", "open", "distance", "persistent", "age", "stage",
                                "snowy", "power", "triggered", "enabled", "extended", "occupied", "attached",
                                "disarmed", "in_wall", "has_book", "has_record", "honey_level", "moisture", "level",
                                "instrument", "note"),
                        value -> value instanceof String);
        CHECK_CLEARANCE = builder
                .comment("Report blocks standing in positions the schematic marks as free space.")
                .define("checkClearance", true);
        RESCAN_INTERVAL_TICKS = builder
                .comment("Ticks for one full background re-check of a build. The check is spread over this time.")
                .defineInRange("rescanIntervalTicks", 40, 10, 400);
        builder.pop();

        builder.comment("Layer view").push("layers");
        NEIGHBOUR_LAYER_RANGE = builder
                .comment("How many layers above and below the current one are shown in the neighbour-layer mode.")
                .defineInRange("neighbourRange", 1, 1, 7);
        builder.pop();

        builder.comment("Progress panel").push("hud");
        HUD_ENABLED = builder
                .comment("Show the progress panel of the current build.")
                .define("enabled", true);
        HUD_CORNER = builder
                .comment("Screen corner of the progress panel.")
                .defineEnum("corner", HudCorner.TOP_LEFT);
        builder.pop();

        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    /** Parses a configured RRGGBB colour; falls back to white if the stored value is damaged. */
    public static int color(ForgeConfigSpec.ConfigValue<String> value) {
        String text = value.get();
        return isHexColor(text) ? Integer.parseInt(text, 16) : 0xFFFFFF;
    }

    private static boolean isHexColor(Object value) {
        return value instanceof String text && HEX_COLOR.matcher(text).matches();
    }
}
