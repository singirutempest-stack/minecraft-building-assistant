package com.buildingassistant.client.input;

import com.buildingassistant.client.ProjectActions;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/** Hotkeys. All of them can be rebound in the game's controls screen. */
public final class KeyBindings {

    private static final String CATEGORY = "key.categories.buildingassistant";
    private static final int UNBOUND = InputConstants.UNKNOWN.getValue();

    private static final KeyMapping MOVE_FORWARD = key("move_forward", GLFW.GLFW_KEY_UP);
    private static final KeyMapping MOVE_BACK = key("move_back", GLFW.GLFW_KEY_DOWN);
    private static final KeyMapping MOVE_LEFT = key("move_left", GLFW.GLFW_KEY_LEFT);
    private static final KeyMapping MOVE_RIGHT = key("move_right", GLFW.GLFW_KEY_RIGHT);
    private static final KeyMapping MOVE_UP = key("move_up", GLFW.GLFW_KEY_PAGE_UP);
    private static final KeyMapping MOVE_DOWN = key("move_down", GLFW.GLFW_KEY_PAGE_DOWN);
    private static final KeyMapping ROTATE = key("rotate", GLFW.GLFW_KEY_R);
    private static final KeyMapping MIRROR = key("mirror", GLFW.GLFW_KEY_V);
    private static final KeyMapping TOGGLE_GHOSTS = key("toggle_ghosts", GLFW.GLFW_KEY_H);
    private static final KeyMapping MOVE_TO_CROSSHAIR = key("move_to_crosshair", GLFW.GLFW_KEY_B);
    private static final KeyMapping NEXT_ERROR = key("next_error", GLFW.GLFW_KEY_N);
    private static final KeyMapping LAYER_UP = key("layer_up", GLFW.GLFW_KEY_RIGHT_BRACKET);
    private static final KeyMapping LAYER_DOWN = key("layer_down", GLFW.GLFW_KEY_LEFT_BRACKET);
    private static final KeyMapping LAYER_MODE = key("layer_mode", GLFW.GLFW_KEY_BACKSLASH);
    private static final KeyMapping STAGE_NEXT = key("stage_next", GLFW.GLFW_KEY_APOSTROPHE);
    private static final KeyMapping STAGE_PREVIOUS = key("stage_previous", GLFW.GLFW_KEY_SEMICOLON);
    // Less frequent actions start without a key so they do not collide with other mods.
    private static final KeyMapping STAGE_MODE = key("stage_mode", UNBOUND);
    private static final KeyMapping PAUSE = key("pause", UNBOUND);
    private static final KeyMapping NEXT_PROJECT = key("next_project", UNBOUND);

    private static final KeyMapping[] ALL = {
            MOVE_FORWARD, MOVE_BACK, MOVE_LEFT, MOVE_RIGHT, MOVE_UP, MOVE_DOWN,
            ROTATE, MIRROR, TOGGLE_GHOSTS, MOVE_TO_CROSSHAIR, NEXT_ERROR,
            LAYER_UP, LAYER_DOWN, LAYER_MODE, STAGE_NEXT, STAGE_PREVIOUS, STAGE_MODE, PAUSE, NEXT_PROJECT
    };

    private KeyBindings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        for (KeyMapping mapping : ALL) {
            event.register(mapping);
        }
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().player == null) {
            return;
        }
        // Holding a movement key repeats it, like holding a letter key while typing.
        while (MOVE_FORWARD.consumeClick()) {
            ProjectActions.moveRelativeToPlayer(1, 0, 0);
        }
        while (MOVE_BACK.consumeClick()) {
            ProjectActions.moveRelativeToPlayer(-1, 0, 0);
        }
        while (MOVE_RIGHT.consumeClick()) {
            ProjectActions.moveRelativeToPlayer(0, 1, 0);
        }
        while (MOVE_LEFT.consumeClick()) {
            ProjectActions.moveRelativeToPlayer(0, -1, 0);
        }
        while (MOVE_UP.consumeClick()) {
            ProjectActions.moveRelativeToPlayer(0, 0, 1);
        }
        while (MOVE_DOWN.consumeClick()) {
            ProjectActions.moveRelativeToPlayer(0, 0, -1);
        }
        while (ROTATE.consumeClick()) {
            ProjectActions.rotate();
        }
        while (MIRROR.consumeClick()) {
            ProjectActions.mirror();
        }
        while (TOGGLE_GHOSTS.consumeClick()) {
            ProjectActions.toggleGhosts();
        }
        while (MOVE_TO_CROSSHAIR.consumeClick()) {
            ProjectActions.moveToCrosshair();
        }
        while (NEXT_ERROR.consumeClick()) {
            ProjectActions.nextError();
        }
        while (LAYER_UP.consumeClick()) {
            ProjectActions.changeLayer(1);
        }
        while (LAYER_DOWN.consumeClick()) {
            ProjectActions.changeLayer(-1);
        }
        while (LAYER_MODE.consumeClick()) {
            ProjectActions.cycleLayerMode();
        }
        while (STAGE_NEXT.consumeClick()) {
            ProjectActions.changeStage(1);
        }
        while (STAGE_PREVIOUS.consumeClick()) {
            ProjectActions.changeStage(-1);
        }
        while (STAGE_MODE.consumeClick()) {
            ProjectActions.cycleStageMode();
        }
        while (PAUSE.consumeClick()) {
            ProjectActions.togglePaused();
        }
        while (NEXT_PROJECT.consumeClick()) {
            ProjectActions.selectNextProject();
        }
    }

    private static KeyMapping key(String name, int keyCode) {
        return new KeyMapping("key.buildingassistant." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, keyCode, CATEGORY);
    }
}
