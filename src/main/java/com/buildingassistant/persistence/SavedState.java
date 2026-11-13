package com.buildingassistant.persistence;

import java.util.List;

/**
 * Everything saved for one world or server.
 *
 * @param currentId     id of the project that was selected, or an empty string
 * @param ghostsVisible state of the master switch for ghost blocks
 */
public record SavedState(String currentId, boolean ghostsVisible, List<SavedProject> projects) {

    public static final SavedState EMPTY = new SavedState("", true, List.of());

    public SavedState {
        projects = List.copyOf(projects);
    }
}
