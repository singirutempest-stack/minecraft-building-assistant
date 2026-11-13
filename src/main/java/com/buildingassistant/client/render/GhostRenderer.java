package com.buildingassistant.client.render;

import com.buildingassistant.BuildingAssistant;
import com.buildingassistant.config.ClientConfig;
import com.buildingassistant.config.GhostStyle;
import com.buildingassistant.project.BuildProject;
import com.buildingassistant.project.ProjectManager;
import com.buildingassistant.transform.SchematicProjection;
import com.buildingassistant.validation.ValidationState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Draws the ghost blocks of every visible project.
 *
 * <p>Geometry is cached in vertex buffers and drawn with vanilla shaders after the level has been
 * rendered, on top of the finished frame but tested against the world's depth buffer. Nothing here
 * depends on a particular renderer mod, and no block is ever placed in the world.
 */
public final class GhostRenderer {

    /** Builds further away than this are not drawn. */
    private static final double MAX_RENDER_DISTANCE = 192.0;

    private static final Map<UUID, CachedMesh> MESHES = new HashMap<>();
    private static boolean disabledByError;
    /** Set once the post-level hook has fired; from then on the event fallback stays silent. */
    private static boolean afterLevelHookSeen;

    private GhostRenderer() {
    }

    /**
     * Preferred entry point, called by {@code GameRendererMixin} once the level is fully drawn. Works the
     * same with the vanilla renderer, Embeddium and Oculus shader packs.
     */
    public static void renderAfterLevel(PoseStack poseStack, Camera camera) {
        if (!afterLevelHookSeen) {
            afterLevelHookSeen = true;
            BuildingAssistant.LOGGER.info("Ghost blocks are drawn after the level (post-level hook active)");
        }
        guarded(() -> render(poseStack, RenderSystem.getProjectionMatrix(), camera.getPosition(), null, true));
    }

    /**
     * Fallback entry point through Forge's render stage event, used only when the post-level hook is not
     * available. Ghosts drawn here can turn opaque under a shader pack.
     */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || afterLevelHookSeen) {
            return;
        }
        guarded(() -> render(event.getPoseStack(), event.getProjectionMatrix(), event.getCamera().getPosition(),
                event.getFrustum(), false));
    }

    private static void guarded(Runnable renderCall) {
        if (disabledByError) {
            return;
        }
        try {
            renderCall.run();
        } catch (RuntimeException e) {
            // A rendering failure must never crash the game. Switch ghosts off and tell the player;
            // leaving and re-entering the world tries again.
            disabledByError = true;
            BuildingAssistant.LOGGER.error("Ghost block rendering failed and was switched off", e);
            closeAll();
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("buildingassistant.message.render_failed", String.valueOf(e)), false);
            }
        }
    }

    /**
     * @param frustum    view frustum for culling, or null to skip the check
     * @param afterLevel true when called after the level renderer has finished
     */
    private static void render(PoseStack poseStack, Matrix4f projectionMatrix, Vec3 camera,
                               @Nullable Frustum frustum, boolean afterLevel) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        ProjectManager manager = ProjectManager.get();
        if (level == null) {
            return;
        }
        dropMeshesOfRemovedProjects(manager);
        if (!manager.ghostsVisible() || manager.projects().isEmpty()) {
            return;
        }

        GhostStyle style = ClientConfig.GHOST_STYLE.get();
        boolean outline = style == GhostStyle.OUTLINE;
        float alpha = ClientConfig.GHOST_OPACITY.get().floatValue();
        int rgb = style == GhostStyle.TEXTURED ? 0xFFFFFF : ClientConfig.color(ClientConfig.MISSING_COLOR);
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;
        boolean blockEntityGhosts = !outline && GhostBlockEntities.isAvailable();
        boolean highlightErrors = ClientConfig.HIGHLIGHT_WRONG_BLOCKS.get();
        int wrongColor = ClientConfig.color(ClientConfig.WRONG_COLOR);
        int clearanceColor = ClientConfig.color(ClientConfig.CLEARANCE_COLOR);

        for (BuildProject project : manager.projects()) {
            if (!project.visible() || project.dimension() != level.dimension()) {
                continue;
            }
            SchematicProjection projection = project.projection();
            BoundingBox bounds = projection.bounds();
            AABB box = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0);
            if (box.distanceToSqr(camera) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE
                    || (frustum != null && !frustum.isVisible(box))) {
                continue;
            }

            // Use the validation result only when it describes the build where it stands right now.
            ValidationState validation = project.validation();
            if (validation != null && validation.projection() != projection) {
                validation = null;
            }

            CachedMesh cached = MESHES.computeIfAbsent(project.id(), id -> new CachedMesh());
            boolean rebuildAll = cached.shapeVersion != project.shapeVersion() || cached.outline != outline
                    || cached.blockEntityGhosts != blockEntityGhosts
                    || cached.supportGeneration != GhostBlockEntities.supportGeneration() || !cached.built;
            int validationVersion = validation == null ? -1 : validation.version();
            if (rebuildAll || cached.validation != validation || cached.validationVersion != validationVersion
                    || cached.projection != projection) {
                cached.mesh.sync(projection, validation, outline, blockEntityGhosts, level, rebuildAll);
                if (blockEntityGhosts) {
                    cached.blockEntities.sync(projection, cached.mesh.kinds(), level);
                } else {
                    cached.blockEntities.clear();
                }
                cached.shapeVersion = project.shapeVersion();
                cached.outline = outline;
                cached.blockEntityGhosts = blockEntityGhosts;
                cached.supportGeneration = GhostBlockEntities.supportGeneration();
                cached.built = true;
                cached.validation = validation;
                cached.validationVersion = validationVersion;
                cached.projection = projection;
            }
            if (highlightErrors) {
                cached.highlights.sync(validation, wrongColor, clearanceColor);
            }

            BlockPos anchor = project.placement().anchor();
            poseStack.pushPose();
            poseStack.translate(anchor.getX() - camera.x, anchor.getY() - camera.y, anchor.getZ() - camera.z);
            try {
                Matrix4f modelView = poseStack.last().pose();
                int lastLayer = cached.mesh.layerCount() - 1;
                cached.mesh.draw(modelView, projectionMatrix, 0, lastLayer, red, green, blue, alpha, afterLevel);
                cached.blockEntities.draw(poseStack, minecraft.getFrameTime(), 0, lastLayer,
                        red, green, blue, alpha, afterLevel);
                if (highlightErrors) {
                    cached.highlights.draw(modelView, projectionMatrix, afterLevel);
                }
                int selected = project.selectedError();
                if (validation != null && selected >= 0 && selected < projection.size()) {
                    long packed = projection.positionAt(selected);
                    drawSelectionBox(poseStack, BlockPos.getX(packed) - anchor.getX(),
                            BlockPos.getY(packed) - anchor.getY(), BlockPos.getZ(packed) - anchor.getZ(), afterLevel);
                }
            } finally {
                poseStack.popPose();
            }
        }
    }

    /**
     * Outlines the error the player navigated to. Drawn without depth test so it can be found through
     * walls; a single box per frame, like the game's own block selection outline.
     */
    private static void drawSelectionBox(PoseStack poseStack, int x, int y, int z, boolean afterLevel) {
        RenderType lines = RenderType.lines();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(lines.mode(), lines.format());
        float grow = 0.02F;
        LevelRenderer.renderLineBox(poseStack, builder, x - grow, y - grow, z - grow,
                x + 1 + grow, y + 1 + grow, z + 1 + grow, 1.0F, 1.0F, 0.2F, 1.0F);
        lines.setupRenderState();
        if (afterLevel) {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        }
        RenderSystem.disableDepthTest();
        try {
            BufferUploader.drawWithShader(builder.end());
        } finally {
            RenderSystem.enableDepthTest();
            lines.clearRenderState();
        }
    }

    /** Forces every mesh to be rebuilt, for example after a resource reload changed block textures. */
    public static void invalidateAll() {
        for (CachedMesh cached : MESHES.values()) {
            cached.built = false;
        }
    }

    /** Frees all GPU resources. */
    public static void closeAll() {
        for (CachedMesh cached : MESHES.values()) {
            cached.close();
        }
        MESHES.clear();
    }

    /** Called when the player leaves the world: frees resources and clears a previous failure. */
    public static void reset() {
        closeAll();
        disabledByError = false;
    }

    private static void dropMeshesOfRemovedProjects(ProjectManager manager) {
        if (MESHES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, CachedMesh>> iterator = MESHES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, CachedMesh> entry = iterator.next();
            boolean alive = false;
            for (BuildProject project : manager.projects()) {
                if (project.id().equals(entry.getKey())) {
                    alive = true;
                    break;
                }
            }
            if (!alive) {
                entry.getValue().close();
                iterator.remove();
            }
        }
    }

    private static final class CachedMesh {
        final GhostMesh mesh = new GhostMesh();
        final HighlightMesh highlights = new HighlightMesh();
        final GhostBlockEntities blockEntities = new GhostBlockEntities();
        int shapeVersion;
        boolean outline;
        boolean blockEntityGhosts;
        int supportGeneration;
        boolean built;
        @Nullable
        ValidationState validation;
        int validationVersion;
        @Nullable
        SchematicProjection projection;

        void close() {
            mesh.close();
            highlights.close();
            blockEntities.clear();
        }
    }
}
