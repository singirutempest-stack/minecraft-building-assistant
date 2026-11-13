package com.buildingassistant.client.render;

import com.buildingassistant.config.ClientConfig;
import com.buildingassistant.config.GhostStyle;
import com.buildingassistant.project.BuildProject;
import com.buildingassistant.project.ProjectManager;
import com.buildingassistant.transform.SchematicProjection;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Draws the ghost blocks of every visible project.
 *
 * <p>Uses only standard Forge hooks and vanilla shaders: geometry is cached in vertex buffers and drawn
 * after the translucent terrain pass, with the same render state as translucent blocks. Nothing here
 * depends on a particular renderer mod, and no block is ever placed in the world.
 */
public final class GhostRenderer {

    /** Builds further away than this are not drawn. */
    private static final double MAX_RENDER_DISTANCE = 192.0;

    private static final Map<UUID, CachedMesh> MESHES = new HashMap<>();

    private GhostRenderer() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
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

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        for (BuildProject project : manager.projects()) {
            if (!project.visible() || project.dimension() != level.dimension()) {
                continue;
            }
            SchematicProjection projection = project.projection();
            BoundingBox bounds = projection.bounds();
            AABB box = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0);
            if (box.distanceToSqr(camera) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE
                    || !event.getFrustum().isVisible(box)) {
                continue;
            }

            CachedMesh cached = MESHES.computeIfAbsent(project.id(), id -> new CachedMesh());
            if (cached.shapeVersion != project.shapeVersion() || cached.outline != outline || !cached.built) {
                cached.mesh.rebuild(projection, outline, level);
                cached.shapeVersion = project.shapeVersion();
                cached.outline = outline;
                cached.built = true;
            }

            BlockPos anchor = project.placement().anchor();
            poseStack.pushPose();
            poseStack.translate(anchor.getX() - camera.x, anchor.getY() - camera.y, anchor.getZ() - camera.z);
            cached.mesh.draw(poseStack.last().pose(), event.getProjectionMatrix(),
                    0, cached.mesh.layerCount() - 1, red, green, blue, alpha);
            poseStack.popPose();
        }
    }

    /** Forces every mesh to be rebuilt, for example after a resource reload changed block textures. */
    public static void invalidateAll() {
        for (CachedMesh cached : MESHES.values()) {
            cached.built = false;
        }
    }

    /** Frees all GPU resources. Called when the player leaves the world. */
    public static void closeAll() {
        for (CachedMesh cached : MESHES.values()) {
            cached.mesh.close();
        }
        MESHES.clear();
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
                entry.getValue().mesh.close();
                iterator.remove();
            }
        }
    }

    private static final class CachedMesh {
        final GhostMesh mesh = new GhostMesh();
        int shapeVersion;
        boolean outline;
        boolean built;
    }
}
