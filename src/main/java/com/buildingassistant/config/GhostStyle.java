package com.buildingassistant.config;

/** How blocks that still have to be placed are drawn. */
public enum GhostStyle {
    /** Translucent block models with their real textures. */
    TEXTURED,
    /** Translucent block models tinted with the configured colour. */
    TINTED,
    /** Wireframe boxes only. */
    OUTLINE
}
