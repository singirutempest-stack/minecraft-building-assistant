package com.buildingassistant.client.gui;

import com.buildingassistant.client.ClientValidation;
import com.buildingassistant.client.ProjectActions;
import com.buildingassistant.config.ClientConfig;
import com.buildingassistant.config.HudCorner;
import com.buildingassistant.project.BuildProject;
import com.buildingassistant.project.BuildView;
import com.buildingassistant.project.ProjectManager;
import com.buildingassistant.transform.SchematicProjection;
import com.buildingassistant.validation.BlockMatcher;
import com.buildingassistant.validation.BuildingValidator;
import com.buildingassistant.validation.ValidationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Small panel showing the progress of the current build: name, progress bar, block counts, errors, and
 * what the schematic expects at the block under the crosshair when that block is wrong.
 *
 * <p>Drawn with plain rectangles and the game font so it sits naturally next to the vanilla HUD. It is
 * hidden together with the HUD (F1) and while the debug screen is open.
 */
public final class ProgressHud {

    private static final int MARGIN = 4;
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int BAR_HEIGHT = 5;
    private static final int MIN_WIDTH = 120;

    private static final int PANEL_COLOR = 0xC0101010;
    private static final int BORDER_COLOR = 0xFF2B2B2B;
    private static final int BAR_BACKGROUND = 0xFF2B2B2B;
    private static final int BAR_FILL = 0xFF5DBB3F;
    private static final int BAR_FILL_DONE = 0xFFE3C13B;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int MUTED_COLOR = 0xB8B8B8;
    private static final int ERROR_COLOR = 0xFF6B5E;
    private static final int DONE_COLOR = 0xFFE36B;

    private ProgressHud() {
    }

    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("progress", ProgressHud::render);
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        if (!ClientConfig.HUD_ENABLED.get() || minecraft.options.hideGui || minecraft.options.renderDebug
                || minecraft.level == null || !ProjectManager.get().ghostsVisible()) {
            return;
        }
        BuildProject project = ProjectManager.get().current();
        if (project == null || project.dimension() != minecraft.level.dimension()) {
            return;
        }
        Font font = minecraft.font;
        Component title = ProjectActions.displayName(project.schematic());
        if (project.paused()) {
            drawPanel(graphics, font, screenWidth, screenHeight, title, -1.0F, false,
                    List.of(new Line(Component.translatable("buildingassistant.hud.paused"), MUTED_COLOR)));
            return;
        }
        ValidationState validation = project.validation();
        if (validation == null || validation.projection() != project.projection()) {
            return;
        }

        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Component.translatable("buildingassistant.hud.progress",
                Math.round(validation.progress() * 100.0F), validation.correct(), validation.required()),
                MUTED_COLOR));

        BuildView view = project.view();
        int stage = view.stage();
        lines.add(new Line(Component.translatable("buildingassistant.hud.stage", stage + 1, view.stageCount(),
                ProjectActions.stageName(project.schematic(), stage),
                validation.correctInStage(stage), validation.requiredInStage(stage)),
                validation.isStageComplete(stage) ? DONE_COLOR
                        : view.stageMode() == BuildView.StageMode.ALL ? MUTED_COLOR : TEXT_COLOR));
        if (view.layerMode() != BuildView.LayerMode.ALL) {
            int layer = view.layer();
            lines.add(new Line(Component.translatable("buildingassistant.hud.layer", layer + 1, view.layerCount(),
                    validation.correctInLayer(layer), project.schematic().requiredBlocksInLayer(layer)), TEXT_COLOR));
        }
        if (validation.isComplete()) {
            lines.add(new Line(Component.translatable("buildingassistant.hud.complete"), DONE_COLOR));
        } else if (validation.errors() > 0) {
            lines.add(new Line(Component.translatable("buildingassistant.hud.errors", validation.errors()), ERROR_COLOR));
        }
        Component hint = crosshairHint(minecraft, project, validation);
        if (hint != null) {
            lines.add(new Line(hint, ERROR_COLOR));
        }

        drawPanel(graphics, font, screenWidth, screenHeight, title, validation.progress(), validation.isComplete(), lines);
    }

    /**
     * @param progress share of the bar to fill, or a negative value to leave the bar out
     */
    private static void drawPanel(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                                  Component title, float progress, boolean complete, List<Line> lines) {
        boolean hasBar = progress >= 0.0F;
        int contentWidth = Math.max(MIN_WIDTH, font.width(title));
        for (Line line : lines) {
            contentWidth = Math.max(contentWidth, font.width(line.text()));
        }
        int width = contentWidth + PADDING * 2;
        int height = PADDING * 2 + LINE_HEIGHT + (hasBar ? BAR_HEIGHT + 3 : 0) + lines.size() * LINE_HEIGHT;

        HudCorner corner = ClientConfig.HUD_CORNER.get();
        int x = corner.isRight() ? screenWidth - width - MARGIN : MARGIN;
        int y = corner.isBottom() ? screenHeight - height - MARGIN : MARGIN;

        graphics.fill(x, y, x + width, y + height, PANEL_COLOR);
        graphics.renderOutline(x, y, width, height, BORDER_COLOR);

        int textX = x + PADDING;
        int cursorY = y + PADDING;
        graphics.drawString(font, title, textX, cursorY, TEXT_COLOR, true);
        cursorY += LINE_HEIGHT;

        if (hasBar) {
            int barWidth = width - PADDING * 2;
            graphics.fill(textX, cursorY, textX + barWidth, cursorY + BAR_HEIGHT, BAR_BACKGROUND);
            int filled = Math.round(barWidth * Math.min(1.0F, progress));
            if (filled > 0) {
                graphics.fill(textX, cursorY, textX + filled, cursorY + BAR_HEIGHT, complete ? BAR_FILL_DONE : BAR_FILL);
            }
            cursorY += BAR_HEIGHT + 3;
        }

        for (Line line : lines) {
            graphics.drawString(font, line.text(), textX, cursorY, line.color(), true);
            cursorY += LINE_HEIGHT;
        }
    }

    /** Explains what is wrong with the block under the crosshair, or null when nothing is wrong there. */
    @Nullable
    private static Component crosshairHint(Minecraft minecraft, BuildProject project, ValidationState validation) {
        HitResult hit = minecraft.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return null;
        }
        SchematicProjection projection = validation.projection();
        int index = projection.indexOf(blockHit.getBlockPos());
        if (index < 0) {
            return null;
        }
        byte status = validation.statusAt(index);
        if (status == ValidationState.OBSTRUCTED) {
            return Component.translatable("buildingassistant.hud.must_be_empty");
        }
        if (status != ValidationState.WRONG) {
            return null;
        }
        BlockState expected = projection.stateAt(index);
        BuildingValidator validator = ClientValidation.validatorFor(project);
        BlockState actual = minecraft.level.getBlockState(blockHit.getBlockPos());
        boolean wrongState = validator != null
                && validator.matcher().compare(expected, actual) == BlockMatcher.Result.WRONG_STATE;
        return Component.translatable(
                wrongState ? "buildingassistant.hud.wrong_orientation" : "buildingassistant.hud.expected",
                expected.getBlock().getName());
    }

    private record Line(Component text, int color) {
    }
}
