package com.buildingassistant.validation;

/** How strictly placed blocks are compared with the schematic. */
public enum ValidationMode {
    /** Block and all significant state properties must match. */
    STRICT,
    /** A block from the same material group is accepted in place of the expected one. */
    FLEXIBLE
}
