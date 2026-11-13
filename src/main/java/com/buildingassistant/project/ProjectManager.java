package com.buildingassistant.project;

import com.buildingassistant.schematic.Schematic;
import com.buildingassistant.transform.Placement;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Builds the player is currently working on. Lives on the client and is only touched from the client
 * thread.
 */
public final class ProjectManager {

    private static final ProjectManager INSTANCE = new ProjectManager();

    private final List<BuildProject> projects = new ArrayList<>();
    @Nullable
    private BuildProject current;
    private boolean ghostsVisible = true;

    private ProjectManager() {
    }

    public static ProjectManager get() {
        return INSTANCE;
    }

    /** Starts a new project and makes it the current one. */
    public BuildProject start(Schematic schematic, ResourceKey<Level> dimension, Placement placement) {
        BuildProject project = new BuildProject(UUID.randomUUID(), schematic, dimension, placement);
        projects.add(project);
        current = project;
        return project;
    }

    public void remove(BuildProject project) {
        projects.remove(project);
        if (current == project) {
            current = projects.isEmpty() ? null : projects.get(projects.size() - 1);
        }
    }

    /** Forgets every project, for example when the player leaves the world. */
    public void clear() {
        projects.clear();
        current = null;
    }

    @Nullable
    public BuildProject current() {
        return current;
    }

    public void setCurrent(BuildProject project) {
        if (projects.contains(project)) {
            current = project;
        }
    }

    public List<BuildProject> projects() {
        return Collections.unmodifiableList(projects);
    }

    /** Master switch for all ghost blocks, independent of each project's own visibility. */
    public boolean ghostsVisible() {
        return ghostsVisible;
    }

    public void setGhostsVisible(boolean ghostsVisible) {
        this.ghostsVisible = ghostsVisible;
    }
}
