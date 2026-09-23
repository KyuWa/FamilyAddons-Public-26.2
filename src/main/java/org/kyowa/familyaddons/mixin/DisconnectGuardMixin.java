package org.kyowa.familyaddons.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.kyowa.familyaddons.features.DisconnectGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The pause menu's Disconnect button is the only caller of disconnectFromWorld, so
 * this is where a misclick can be caught and turned into a confirm screen. Kicks
 * and server transfers come through other paths and are untouched. See
 * DisconnectGuard.
 */
@Mixin(Minecraft.class)
public class DisconnectGuardMixin {

    @Inject(method = "disconnectFromWorld", at = @At("HEAD"), cancellable = true)
    private void familyaddons$confirmDisconnect(Component reason, CallbackInfo ci) {
        if (DisconnectGuard.intercept((Minecraft) (Object) this, reason)) ci.cancel();
    }
}
