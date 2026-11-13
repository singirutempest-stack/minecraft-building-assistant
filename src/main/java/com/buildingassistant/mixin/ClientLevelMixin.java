package com.buildingassistant.mixin;

import com.buildingassistant.client.ClientValidation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the validator about block changes on the client. Forge has no client-side block change event,
 * and this method is where the game itself notifies the renderer of a changed block.
 *
 * <p>Optional: without it, changes are still picked up by the periodic background check, only later.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), require = 0)
    private void buildingassistant$onBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags,
                                                  CallbackInfo callback) {
        ClientValidation.onBlockChanged((ClientLevel) (Object) this, pos);
    }
}
