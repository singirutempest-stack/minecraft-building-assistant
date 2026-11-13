package com.buildingassistant.client.render;

import com.buildingassistant.BuildingAssistant;
import com.buildingassistant.project.BuildView;
import com.buildingassistant.transform.SchematicProjection;
import com.buildingassistant.validation.ValidationState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/**
 * GPU geometry of one build's ghost blocks, one vertex buffer per layer.
 *
 * <p>Geometry is stored relative to the build's anchor, so moving a build does not rebuild anything by
 * itself. Blocks that are already placed correctly are left out. When blocks are placed or removed, only
 * the affected layers are rebuilt. Splitting by layer also lets the layer view draw a subset as is.
 *
 * <p>All methods must be called on the render thread.
 */
final class GhostMesh {

    /** Fluids are drawn slightly lower than a full block, like a still water surface. */
    private static final double FLUID_HEIGHT = 14.0 / 16.0;

    /**
     * Shared scratch buffer. It is only used inside {@link #rebuild}, which runs on the render thread,
     * and sharing it avoids allocating native memory for every build.
     */
    private static BufferBuilder scratch;

    /** Entry is not drawn and does not hide neighbouring faces. */
    static final byte KIND_ABSENT = 0;
    /** Entry is drawn as a ghost block. */
    static final byte KIND_GHOST = 1;
    /** The real block is already in place: not drawn, but it hides the faces of ghosts next to it. */
    static final byte KIND_PLACED = 2;

    private VertexBuffer[] layers = new VertexBuffer[0];
    private byte[] builtKinds = new byte[0];
    private boolean outline;
    private boolean blockEntityGhosts;

    /**
     * Brings the geometry up to date and rebuilds only the layers whose content changed.
     *
     * @param validation what is known about the world, or null to draw every block as a ghost
     * @param view       the player's layer and stage selection; blocks outside it are not drawn
     * @param blockEntityGhosts true when beds, chests and similar blocks are drawn separately by
     *                          {@link GhostBlockEntities} and must be left out here
     * @param rebuildAll true when the build's shape or the style changed and nothing can be reused
     */
    void sync(SchematicProjection projection, @Nullable ValidationState validation, BuildView view,
              int neighbourRange, boolean outline, boolean blockEntityGhosts, ClientLevel level,
              boolean rebuildAll) {
        this.blockEntityGhosts = blockEntityGhosts;
        int layerCount = projection.layerCount();
        byte[] kinds = kindsOf(projection, validation, view, neighbourRange);
        if (rebuildAll || layers.length != layerCount || builtKinds.length != kinds.length || this.outline != outline) {
            close();
            layers = new VertexBuffer[layerCount];
            rebuildAll = true;
        }
        this.outline = outline;

        // A layer has to be rebuilt when one of its own entries changed, or one directly above or below,
        // because hidden faces depend on the neighbours.
        boolean[] changed = new boolean[layerCount];
        if (!rebuildAll) {
            for (int y = 0; y < layerCount; y++) {
                for (int i = projection.layerStart(y); i < projection.layerEnd(y); i++) {
                    if (kinds[i] != builtKinds[i]) {
                        changed[y] = true;
                        break;
                    }
                }
            }
        }
        builtKinds = kinds;

        GhostBlockView view = null;
        for (int y = 0; y < layerCount; y++) {
            boolean rebuild = rebuildAll || changed[y] || (y > 0 && changed[y - 1])
                    || (y + 1 < layerCount && changed[y + 1]);
            if (!rebuild) {
                continue;
            }
            if (view == null) {
                view = new GhostBlockView(projection, kinds, level);
            }
            rebuildLayer(projection, kinds, view, y);
        }
    }

    private static byte[] kindsOf(SchematicProjection projection, @Nullable ValidationState validation,
                                  BuildView view, int neighbourRange) {
        byte[] kinds = new byte[projection.size()];
        for (int i = 0; i < kinds.length; i++) {
            if (projection.stateAt(i).isAir()) {
                // Clearance entries mark free space; there is nothing to draw.
                kinds[i] = KIND_ABSENT;
                continue;
            }
            byte status = validation == null ? ValidationState.MISSING : validation.statusAt(i);
            if (status == ValidationState.CORRECT) {
                // A placed block hides neighbouring ghost faces whether or not its layer is selected.
                kinds[i] = KIND_PLACED;
            } else if (status == ValidationState.WRONG) {
                // A wrong block is marked by a highlight instead; its real shape is unknown here,
                // so it must not hide any ghost faces.
                kinds[i] = KIND_ABSENT;
            } else {
                boolean visible = view.isVisible(projection.layerAt(i), projection.stageAt(i), neighbourRange);
                kinds[i] = visible ? KIND_GHOST : KIND_ABSENT;
            }
        }
        return kinds;
    }

    private void rebuildLayer(SchematicProjection projection, byte[] kinds, GhostBlockView view, int y) {
        if (layers[y] != null) {
            layers[y].close();
            layers[y] = null;
        }
        if (scratch == null) {
            scratch = new BufferBuilder(1 << 20);
        }
        Minecraft minecraft = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        BlockPos anchor = projection.placement().anchor();
        RandomSource random = RandomSource.create();
        PoseStack poseStack = new PoseStack();
        BlockPos.MutableBlockPos relative = new BlockPos.MutableBlockPos();

        try {
            if (outline) {
                scratch.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
            } else {
                scratch.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            }
            for (int i = projection.layerStart(y); i < projection.layerEnd(y); i++) {
                if (kinds[i] != KIND_GHOST) {
                    continue;
                }
                BlockState state = projection.stateAt(i);
                long packed = projection.positionAt(i);
                relative.set(BlockPos.getX(packed) - anchor.getX(), BlockPos.getY(packed) - anchor.getY(),
                        BlockPos.getZ(packed) - anchor.getZ());
                poseStack.pushPose();
                poseStack.translate(relative.getX(), relative.getY(), relative.getZ());
                try {
                    if (outline) {
                        addOutline(state, view, relative, poseStack);
                    } else {
                        addBlock(state, view, relative, poseStack, dispatcher, random, minecraft, blockEntityGhosts);
                    }
                } catch (RuntimeException e) {
                    // A broken model from another mod must not take the whole build down with it.
                    BuildingAssistant.LOGGER.warn("Could not build ghost geometry for {}", state, e);
                }
                poseStack.popPose();
            }
            BufferBuilder.RenderedBuffer rendered = scratch.endOrDiscardIfEmpty();
            if (rendered != null) {
                VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                buffer.bind();
                buffer.upload(rendered);
                layers[y] = buffer;
            }
        } finally {
            if (scratch.building()) {
                scratch.endOrDiscardIfEmpty();
            }
            VertexBuffer.unbind();
        }
    }

    private static void addBlock(BlockState state, GhostBlockView view, BlockPos relative, PoseStack poseStack,
                                 BlockRenderDispatcher dispatcher, RandomSource random, Minecraft minecraft,
                                 boolean blockEntityGhosts) {
        if (state.getBlock() instanceof LiquidBlock) {
            FluidState fluid = state.getFluidState();
            IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
            TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(extensions.getStillTexture());
            addBox(poseStack, view, sprite, extensions.getTintColor(), 0, 0, 0, 1, FLUID_HEIGHT, 1);
            return;
        }

        BakedModel model = dispatcher.getBlockModel(state);
        if (state.getRenderShape() == RenderShape.MODEL) {
            long seed = state.getSeed(relative);
            for (RenderType renderType : model.getRenderTypes(state, RandomSource.create(seed), ModelData.EMPTY)) {
                dispatcher.getModelRenderer().tesselateBlock(view, model, state, relative, poseStack, scratch,
                        true, random, seed, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, renderType);
            }
            return;
        }

        // Chests, beds, signs and similar blocks are drawn by block entity renderers and have no block
        // model. Normally GhostBlockEntities draws their real model.
        if (blockEntityGhosts && GhostBlockEntities.handles(state)) {
            return;
        }
        // Fallback when that is not possible: the outline shape, textured with the block's particle
        // texture, so the player still sees where the block goes and how large it is.
        TextureAtlasSprite sprite = model.getParticleIcon(ModelData.EMPTY);
        VoxelShape shape = state.getShape(view, relative);
        if (shape.isEmpty()) {
            shape = Shapes.block();
        }
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                addBox(poseStack, view, sprite, 0xFFFFFF, minX, minY, minZ, maxX, maxY, maxZ));
    }

    private static void addOutline(BlockState state, GhostBlockView view, BlockPos relative, PoseStack poseStack) {
        VoxelShape shape = state.getShape(view, relative);
        if (shape.isEmpty()) {
            shape = Shapes.block();
        }
        // White lines; the configured colour and opacity are applied when drawing.
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                LevelRenderer.renderLineBox(poseStack, scratch, minX, minY, minZ, maxX, maxY, maxZ, 1, 1, 1, 1));
    }

    /** Adds a textured box in block vertex format. Faces are wound counter-clockwise seen from outside. */
    private static void addBox(PoseStack poseStack, GhostBlockView view, TextureAtlasSprite sprite, int rgb,
                               double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        float x1 = (float) minX;
        float y1 = (float) minY;
        float z1 = (float) minZ;
        float x2 = (float) maxX;
        float y2 = (float) maxY;
        float z2 = (float) maxZ;
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        quad(pose, normal, view, sprite, rgb, Direction.DOWN, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
        quad(pose, normal, view, sprite, rgb, Direction.UP, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        quad(pose, normal, view, sprite, rgb, Direction.NORTH, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        quad(pose, normal, view, sprite, rgb, Direction.SOUTH, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
        quad(pose, normal, view, sprite, rgb, Direction.WEST, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
        quad(pose, normal, view, sprite, rgb, Direction.EAST, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
    }

    private static void quad(Matrix4f pose, Matrix3f normal, GhostBlockView view, TextureAtlasSprite sprite, int rgb,
                             Direction face,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        // Same directional shading the game applies to block faces.
        float shade = view.getShade(face, true);
        float red = ((rgb >> 16) & 0xFF) / 255.0F * shade;
        float green = ((rgb >> 8) & 0xFF) / 255.0F * shade;
        float blue = (rgb & 0xFF) / 255.0F * shade;
        vertex(pose, normal, face, ax, ay, az, red, green, blue, sprite.getU0(), sprite.getV1());
        vertex(pose, normal, face, bx, by, bz, red, green, blue, sprite.getU1(), sprite.getV1());
        vertex(pose, normal, face, cx, cy, cz, red, green, blue, sprite.getU1(), sprite.getV0());
        vertex(pose, normal, face, dx, dy, dz, red, green, blue, sprite.getU0(), sprite.getV0());
    }

    private static void vertex(Matrix4f pose, Matrix3f normal, Direction face, float x, float y, float z,
                               float red, float green, float blue, float u, float v) {
        // Element order of DefaultVertexFormat.BLOCK: position, colour, texture, light, normal.
        scratch.vertex(pose, x, y, z)
                .color(red, green, blue, 1.0F)
                .uv(u, v)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, face.getStepX(), face.getStepY(), face.getStepZ())
                .endVertex();
    }

    /**
     * Draws layers {@code firstLayer} to {@code lastLayer} (inclusive).
     *
     * @param modelView  model-view matrix already translated to the build's anchor
     * @param afterLevel true when drawing on top of the finished level: output goes straight to the main
     *                   render target and distance fog is switched off for the draw
     */
    void draw(Matrix4f modelView, Matrix4f projection, int firstLayer, int lastLayer,
              float red, float green, float blue, float alpha, boolean afterLevel) {
        int first = Math.max(0, firstLayer);
        int last = Math.min(layers.length - 1, lastLayer);
        if (first > last) {
            return;
        }

        RenderType renderType = outline ? RenderType.lines() : RenderType.translucent();
        renderType.setupRenderState();
        float previousFogStart = RenderSystem.getShaderFogStart();
        if (afterLevel) {
            // The render type may have selected an intermediate target (Fabulous graphics); at this
            // point those have already been merged, so only the main target reaches the screen.
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        }
        RenderSystem.setShaderColor(red, green, blue, alpha);
        ShaderInstance shader = RenderSystem.getShader();
        try {
            if (shader == null) {
                return;
            }
            if (!outline) {
                // Depth-only pass first. Afterwards only the nearest ghost surface passes the depth test,
                // so overlapping translucent faces do not pile up into a muddy, order-dependent picture.
                RenderSystem.colorMask(false, false, false, false);
                drawLayers(modelView, projection, shader, first, last);
                RenderSystem.colorMask(true, true, true, true);
            }
            drawLayers(modelView, projection, shader, first, last);
        } finally {
            VertexBuffer.unbind();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShaderFogStart(previousFogStart);
            renderType.clearRenderState();
        }
    }

    private void drawLayers(Matrix4f modelView, Matrix4f projection, ShaderInstance shader, int first, int last) {
        for (int y = first; y <= last; y++) {
            VertexBuffer buffer = layers[y];
            if (buffer != null) {
                buffer.bind();
                buffer.drawWithShader(modelView, projection, shader);
            }
        }
    }

    int layerCount() {
        return layers.length;
    }

    /** Per entry, how it was treated in the last sync: one of the {@code KIND_*} values. */
    byte[] kinds() {
        return builtKinds;
    }

    /** Frees the GPU buffers. The mesh can be rebuilt afterwards. */
    void close() {
        for (VertexBuffer buffer : layers) {
            if (buffer != null) {
                buffer.close();
            }
        }
        layers = new VertexBuffer[0];
        builtKinds = new byte[0];
    }
}
