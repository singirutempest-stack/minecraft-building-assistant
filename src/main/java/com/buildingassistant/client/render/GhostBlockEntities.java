package com.buildingassistant.client.render;

import com.buildingassistant.BuildingAssistant;
import com.buildingassistant.transform.SchematicProjection;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ghosts of blocks that the game draws with block entity renderers instead of block models: beds,
 * chests, signs, banners, heads, shulker boxes and the like.
 *
 * <p>For each such ghost a detached block entity is created. It is never added to the world; it only
 * carries the block state so the game's own renderer can draw the real model, correctly oriented. The
 * renderers are asked for translucent versions of their render types so the result blends like the
 * other ghost blocks.
 *
 * <p>These models are drawn each frame, like the game does for real block entities. There are only a
 * handful per build.
 */
final class GhostBlockEntities {

    /** Per block: whether a working block entity renderer exists. Blocks whose renderer failed are false. */
    private static final Reference2BooleanOpenHashMap<Block> SUPPORTED = new Reference2BooleanOpenHashMap<>();
    /** Increases when a block is found to be unsupported, so cached geometry can fall back to boxes. */
    private static int supportGeneration;
    private static Map<RenderType, RenderType> translucentTypes;

    private final List<Ghost> ghosts = new ArrayList<>();

    /** Translucent render targets used here do not reach the screen after the level in Fabulous mode. */
    static boolean isAvailable() {
        return !Minecraft.useShaderTransparency();
    }

    static int supportGeneration() {
        return supportGeneration;
    }

    /** Whether the state is drawn here rather than as a fallback box in the ghost mesh. */
    static boolean handles(BlockState state) {
        Block block = state.getBlock();
        if (state.getRenderShape() != RenderShape.ENTITYBLOCK_ANIMATED || !(block instanceof EntityBlock entityBlock)) {
            return false;
        }
        if (SUPPORTED.containsKey(block)) {
            return SUPPORTED.getBoolean(block);
        }
        boolean supported = false;
        try {
            BlockEntity probe = entityBlock.newBlockEntity(BlockPos.ZERO, state);
            supported = probe != null
                    && Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(probe) != null;
        } catch (RuntimeException e) {
            BuildingAssistant.LOGGER.warn("Cannot preview {} with its own renderer, using a box instead", block, e);
        }
        SUPPORTED.put(block, supported);
        return supported;
    }

    /** Recreates the detached block entities for the entries that are currently shown as ghosts. */
    void sync(SchematicProjection projection, byte[] kinds, ClientLevel level) {
        ghosts.clear();
        BlockPos anchor = projection.placement().anchor();
        for (int i = 0; i < projection.size(); i++) {
            BlockState state = projection.stateAt(i);
            if (kinds[i] != GhostMesh.KIND_GHOST || !handles(state)) {
                continue;
            }
            BlockPos pos = BlockPos.of(projection.positionAt(i));
            try {
                BlockEntity blockEntity = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
                if (blockEntity == null) {
                    continue;
                }
                // Renderers read orientation from the state only when a level is present.
                blockEntity.setLevel(level);
                ghosts.add(new Ghost(blockEntity, pos.getX() - anchor.getX(), pos.getY() - anchor.getY(),
                        pos.getZ() - anchor.getZ()));
            } catch (RuntimeException e) {
                markUnsupported(state.getBlock(), e);
            }
        }
    }

    /**
     * @param poseStack pose already translated to the build's anchor
     */
    void draw(PoseStack poseStack, float partialTick, int firstLayer, int lastLayer,
              float red, float green, float blue, float alpha, boolean afterLevel) {
        if (ghosts.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BlockEntityRenderDispatcher dispatcher = minecraft.getBlockEntityRenderDispatcher();
        MultiBufferSource.BufferSource source = minecraft.renderBuffers().bufferSource();
        // Anything still queued belongs to someone else and must not pick up the ghost tint.
        source.endBatch();
        MultiBufferSource ghostSource = renderType -> source.getBuffer(translucent(renderType));

        float previousFogStart = RenderSystem.getShaderFogStart();
        if (afterLevel) {
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        }
        RenderSystem.setShaderColor(red, green, blue, alpha);
        try {
            for (Ghost ghost : ghosts) {
                if (ghost.y < firstLayer || ghost.y > lastLayer) {
                    continue;
                }
                BlockEntityRenderer<BlockEntity> renderer = dispatcher.getRenderer(ghost.blockEntity);
                if (renderer == null) {
                    continue;
                }
                poseStack.pushPose();
                poseStack.translate(ghost.x, ghost.y, ghost.z);
                try {
                    renderer.render(ghost.blockEntity, partialTick, poseStack, ghostSource,
                            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                } catch (RuntimeException e) {
                    markUnsupported(ghost.blockEntity.getBlockState().getBlock(), e);
                } finally {
                    poseStack.popPose();
                }
            }
            source.endBatch();
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShaderFogStart(previousFogStart);
        }
    }

    void clear() {
        ghosts.clear();
    }

    private static void markUnsupported(Block block, RuntimeException cause) {
        if (!SUPPORTED.containsKey(block) || SUPPORTED.getBoolean(block)) {
            BuildingAssistant.LOGGER.warn("Previewing {} with its own renderer failed, using a box instead", block, cause);
            SUPPORTED.put(block, false);
            supportGeneration++;
        }
    }

    /**
     * The translucent counterpart of a render type used by vanilla block entity renderers. Types that are
     * not known are returned unchanged and draw opaque.
     */
    private static RenderType translucent(RenderType renderType) {
        if (translucentTypes == null) {
            translucentTypes = buildTranslucentTypes();
        }
        return translucentTypes.getOrDefault(renderType, renderType);
    }

    private static Map<RenderType, RenderType> buildTranslucentTypes() {
        List<ResourceLocation> textures = List.of(
                Sheets.BED_SHEET, Sheets.CHEST_SHEET, Sheets.SHULKER_SHEET, Sheets.SIGN_SHEET, Sheets.BANNER_SHEET,
                Sheets.DECORATED_POT_SHEET, InventoryMenu.BLOCK_ATLAS,
                DefaultPlayerSkin.getDefaultSkin(),
                new ResourceLocation("textures/entity/skeleton/skeleton.png"),
                new ResourceLocation("textures/entity/skeleton/wither_skeleton.png"),
                new ResourceLocation("textures/entity/zombie/zombie.png"),
                new ResourceLocation("textures/entity/creeper/creeper.png"),
                new ResourceLocation("textures/entity/piglin/piglin.png"),
                new ResourceLocation("textures/entity/enderdragon/dragon.png"));
        // Render types are cached singletons per texture, so identity lookup finds them.
        Map<RenderType, RenderType> map = new IdentityHashMap<>();
        for (ResourceLocation texture : textures) {
            RenderType translucent = RenderType.entityTranslucentCull(texture);
            map.put(RenderType.entitySolid(texture), translucent);
            map.put(RenderType.entityCutout(texture), translucent);
            map.put(RenderType.entityCutoutNoCull(texture), translucent);
            map.put(RenderType.entityCutoutNoCullZOffset(texture), translucent);
            map.put(RenderType.entityNoOutline(texture), translucent);
            map.put(RenderType.entityTranslucent(texture), translucent);
        }
        return map;
    }

    private record Ghost(BlockEntity blockEntity, int x, int y, int z) {
    }
}
