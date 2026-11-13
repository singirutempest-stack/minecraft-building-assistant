package com.buildingassistant.config;

/** Screen corner the progress panel is anchored to. */
public enum HudCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    public boolean isRight() {
        return this == TOP_RIGHT || this == BOTTOM_RIGHT;
    }

    public boolean isBottom() {
        return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
    }
}
