package com.buildingassistant.schematic.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gathers problems found while reading a schematic so that a broken file produces a readable report
 * instead of an exception.
 */
public final class IssueCollector {

    /** A badly damaged file can produce thousands of messages; only the first ones are useful. */
    private static final int MAX_RECORDED = 40;

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private int errorCount;

    public void error(String message) {
        errorCount++;
        if (errors.size() < MAX_RECORDED) {
            errors.add(message);
        }
    }

    public void warn(String message) {
        if (warnings.size() < MAX_RECORDED) {
            warnings.add(message);
        }
    }

    public boolean hasErrors() {
        return errorCount > 0;
    }

    /** Total number of errors, including ones that were not recorded. */
    public int errorCount() {
        return errorCount;
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }
}
