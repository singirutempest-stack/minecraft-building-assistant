package com.buildingassistant.client.render;

import com.buildingassistant.project.BuildView;
import com.buildingassistant.transform.SchematicProjection;
import com.buildingassistant.validation.ValidationState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;

/**
 * Coloured boxes over the blocks of one build that are in the wrong place: wrong blocks and blocks
 * standing where the build needs free space.
 *
 * <p>Like the ghost geometry it is cached on the GPU, stored relative to the build's anchor, and
 * rebuilt only when the set of errors changes. Must be used on the render thread.
 */
final class HighlightMesh {

    /** Boxes are slightly larger than a block so their faces never coincide with the block's own. */
    private static final float INFLATE = 0.004F;
    private static final int ALPHA = 110;

    private static BufferBuilder scratch;

    @Nullable
    private VertexBuffer buffer;
    @Nullable
    private ValidationState builtFrom;
    private int builtVersion;
    private int builtViewVersion;
    private int builtNeighbourRange;
    private int builtWrongColor;
    private int builtClearanceColor;

    /**
     * @param view errors outside the player's layer and stage selection are not marked
     */
    void sync(@Nullable ValidationState validation, BuildView view, int neighbourRange,
              int wrongColor, int clearanceColor) {
        if (validation == null) {
            close();
            return;
        }
        if (validation == builtFrom && validation.version() == builtVersion
                && view.version() == builtViewVersion && neighbourRange == builtNeighbourRange
                && wrongColor == builtWrongColor && clearanceColor == builtClearanceColor) {
            return;
        }
        close();
        builtFrom = validation;
        builtVersion = validation.version();
        builtViewVersion = view.version();
        builtNeighbourRange = neighbourRange;
        builtWrongColor = wrongColor;
        builtClearanceColor = clearanceColor;
        if (validation.errors() == 0) {
            return;
        }
        if (scratch == null) {
            scratch = new BufferBuilder(1 << 16);
        }

        SchematicProjection projection = validation.projection();
        BlockPos anchor = projection.placement().anchor();
        try {
            scratch.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < projection.size(); i++) {
                byte status = validation.statusAt(i);
                if (status != ValidationState.WRONG && status != ValidationState.OBSTRUCTED) {
                    continue;
                }
                if (!view.isVisible(projection.layerAt(i), projection.stageAt(i), neighbourRange)) {
                    continue;
                }
                long packed = projection.positionAt(i);
                addBox(BlockPos.getX(packed) - anchor.getX(), BlockPos.getY(packed) - anchor.getY(),
                        BlockPos.getZ(packed) - anchor.getZ(),
                        status == ValidationState.WRONG ? wrongColor : clearanceColor);
            }
            BufferBuilder.RenderedBuffer rendered = scratch.endOrDiscardIfEmpty();
            if (rendered != null) {
                buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                buffer.bind();
                buffer.upload(rendered);
            }
        } finally {
            if (scratch.building()) {
                scratch.endOrDiscardIfEmpty();
            }
            VertexBuffer.unbind();
        }
    }

    private static void addBox(int x, int y, int z, int rgb) {
        float x1 = x - INFLATE;
        float y1 = y - INFLATE;
        float z1 = z - INFLATE;
        float x2 = x + 1 + INFLATE;
        float y2 = y + 1 + INFLATE;
        float z2 = z + 1 + INFLATE;
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        // Counter-clockwise seen from outside, same winding as GhostMesh.
        quad(red, green, blue, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
        quad(red, green, blue, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        quad(red, green, blue, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        quad(red, green, blue, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
        quad(red, green, blue, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
        quad(red, green, blue, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
    }

    private static void quad(int red, int green, int blue,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        scratch.vertex(ax, ay, az).color(red, green, blue, ALPHA).endVertex();
        scratch.vertex(bx, by, bz).color(red, green, blue, ALPHA).endVertex();
        scratch.vertex(cx, cy, cz).color(red, green, blue, ALPHA).endVertex();
        scratch.vertex(dx, dy, dz).color(red, green, blue, ALPHA).endVertex();
    }

    /**
     * @param modelView  model-view matrix already translated to the build's anchor
     * @param afterLevel true when drawing on top of the finished level
     */
    void draw(Matrix4f modelView, Matrix4f projection, boolean afterLevel) {
        if (buffer == null) {
            return;
        }
        if (afterLevel) {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        // The tint lies on top of real blocks; it must not hide anything drawn later.
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        RenderSystem.polygonOffset(-1.0F, -10.0F);
        RenderSystem.enablePolygonOffset();
        try {
            if (RenderSystem.getShader() != null) {
                buffer.bind();
                buffer.drawWithShader(modelView, projection, RenderSystem.getShader());
            }
        } finally {
            VertexBuffer.unbind();
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    void close() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
        builtFrom = null;
    }
}
