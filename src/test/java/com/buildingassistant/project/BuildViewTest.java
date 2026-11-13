package com.buildingassistant.project;

import com.buildingassistant.schematic.format.SchematicFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildViewTest {

    private static final int COMMON = SchematicFormat.COMMON_STAGE;

    @Test
    void everythingIsVisibleByDefault() {
        BuildView view = new BuildView(7, 4);
        assertTrue(view.showsEverything());
        for (int layer = 0; layer < 7; layer++) {
            for (int stage = -1; stage < 4; stage++) {
                assertTrue(view.isVisible(layer, stage, 1));
            }
        }
    }

    @Test
    void singleLayerShowsOnlyThatLayer() {
        BuildView view = new BuildView(7, 4);
        view.setLayerMode(BuildView.LayerMode.SINGLE);
        view.setLayer(3);
        assertFalse(view.showsEverything());
        assertTrue(view.isVisible(3, 0, 1));
        assertFalse(view.isVisible(2, 0, 1));
        assertFalse(view.isVisible(4, 0, 1));
    }

    @Test
    void neighbourModeUsesTheConfiguredRange() {
        BuildView view = new BuildView(9, 1);
        view.setLayerMode(BuildView.LayerMode.NEIGHBOURS);
        view.setLayer(4);
        assertTrue(view.isVisible(3, 0, 1));
        assertTrue(view.isVisible(5, 0, 1));
        assertFalse(view.isVisible(2, 0, 1));
        assertFalse(view.isVisible(6, 0, 1));
        assertTrue(view.isVisible(2, 0, 2));
        assertTrue(view.isVisible(6, 0, 2));
        assertFalse(view.isVisible(1, 0, 2));
    }

    @Test
    void upToShowsTheCurrentLayerAndEverythingBelow() {
        BuildView view = new BuildView(7, 1);
        view.setLayerMode(BuildView.LayerMode.UP_TO);
        view.setLayer(2);
        assertTrue(view.isVisible(0, 0, 1));
        assertTrue(view.isVisible(2, 0, 1));
        assertFalse(view.isVisible(3, 0, 1));
    }

    @Test
    void currentStageShowsThatStageAndCommonBlocks() {
        BuildView view = new BuildView(7, 4);
        view.setStageMode(BuildView.StageMode.CURRENT);
        view.setStage(2);
        assertTrue(view.isVisible(0, 2, 1));
        assertFalse(view.isVisible(0, 1, 1));
        assertFalse(view.isVisible(0, 3, 1));
        assertTrue(view.isVisible(0, COMMON, 1), "common blocks belong to every stage");
    }

    @Test
    void upToStageShowsEarlierStagesToo() {
        BuildView view = new BuildView(7, 4);
        view.setStageMode(BuildView.StageMode.UP_TO);
        view.setStage(2);
        assertTrue(view.isVisible(0, 0, 1));
        assertTrue(view.isVisible(0, 2, 1));
        assertFalse(view.isVisible(0, 3, 1));
    }

    @Test
    void layerAndStageSelectionsCombine() {
        BuildView view = new BuildView(7, 4);
        view.setLayerMode(BuildView.LayerMode.SINGLE);
        view.setLayer(1);
        view.setStageMode(BuildView.StageMode.CURRENT);
        view.setStage(0);
        assertTrue(view.isVisible(1, 0, 1));
        assertFalse(view.isVisible(1, 1, 1), "right layer, wrong stage");
        assertFalse(view.isVisible(2, 0, 1), "right stage, wrong layer");
        assertFalse(view.isVisible(2, COMMON, 1), "common blocks still follow the layer selection");
    }

    @Test
    void layerAndStageAreClampedToTheBuild() {
        BuildView view = new BuildView(7, 4);
        view.setLayer(99);
        assertEquals(6, view.layer());
        view.setLayer(-5);
        assertEquals(0, view.layer());
        view.setStage(99);
        assertEquals(3, view.stage());
        view.setStage(-1);
        assertEquals(0, view.stage());
    }

    @Test
    void versionChangesOnlyWhenSomethingChanges() {
        BuildView view = new BuildView(7, 4);
        int version = view.version();
        view.setLayer(0);
        view.setStage(0);
        view.setLayerMode(BuildView.LayerMode.ALL);
        view.setStageMode(BuildView.StageMode.ALL);
        assertEquals(version, view.version(), "setting the same values must not count as a change");

        view.setLayer(2);
        assertNotEquals(version, view.version());
        version = view.version();
        view.setStageMode(BuildView.StageMode.CURRENT);
        assertNotEquals(version, view.version());
        version = view.version();
        // Clamped to the same value: still no change.
        view.setLayer(2);
        view.setStage(-3);
        assertEquals(version, view.version());
    }
}
