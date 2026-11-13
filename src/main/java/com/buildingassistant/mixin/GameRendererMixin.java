package com.buildingassistant.mixin;

import com.buildingassistant.client.render.GhostRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the ghost renderer a hook right after the level has been drawn completely.
 *
 * <p>Forge's render stage events all fire inside the level renderer. With a shader pack (Oculus), that
 * is the middle of the pack's own pipeline: the pack replaces the vanilla shaders and decides lighting
 * and transparency itself, which turns ghost blocks solid. Directly after the level renderer returns,
 * the pack has finished and written its final image, the world's depth buffer is still intact, and
 * vanilla shaders behave normally again. No Forge event exists at that point, hence this mixin.
 *
 * <p>The injection is optional. If another mod prevents it, the ghost renderer falls back to the Forge
 * event and the game still starts.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void buildingassistant$afterLevelRendered(float partialTick, long finishNanoTime, PoseStack poseStack,
                                                      CallbackInfo callback) {
        GhostRenderer.renderAfterLevel(poseStack, ((GameRenderer) (Object) this).getMainCamera());
    }
}
