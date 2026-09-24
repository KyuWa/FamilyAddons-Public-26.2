package org.kyowa.familyaddons.mixin.storage;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.kyowa.familyaddons.storage.StorageOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Screen-change callbacks: "closed" for the previous screen at the start of the
 * screen swap, and "opened" for the new screen (including null).
 *
 * 26.2: the current screen and {@code setScreen} moved from {@code Minecraft} to {@code Gui}
 * ({@code Minecraft.getInstance().gui.screen()} / {@code gui.setScreen(...)}).
 */
@Mixin(Gui.class)
public class MinecraftScreenMixin {

    @Shadow
    private Screen screen;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void familystorage$guiClosed(Screen newScreen, CallbackInfo ci) {
        if (this.screen != null) {
            StorageOverlay.onGuiClosed(this.screen);
        }
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void familystorage$guiOpened(Screen newScreen, CallbackInfo ci) {
        StorageOverlay.onGuiOpened(this.screen);
    }
}
