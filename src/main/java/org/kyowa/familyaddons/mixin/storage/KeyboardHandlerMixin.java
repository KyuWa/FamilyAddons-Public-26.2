package org.kyowa.familyaddons.mixin.storage;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.kyowa.familyaddons.storage.StorageOverlay;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keyboard input for the search box. Both hooks sit at the very start of the keyboard handler so
 * that, while the search box is active, nothing else (vanilla key mappings, other mods' screen
 * key events or keybinds) ever sees the keystroke.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void familystorage$keyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (!StorageOverlay.input.isActive) return;
        if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?>)) return;
        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
            StorageOverlay.input.onKeyPressed(event.key());
        }
        ci.cancel();
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void familystorage$charTyped(long window, CharacterEvent event, CallbackInfo ci) {
        if (StorageOverlay.input.onCharTyped(event.codepointAsString())) ci.cancel();
    }
}
