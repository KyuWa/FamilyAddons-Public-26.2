package org.kyowa.familyaddons.mixin.storage;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.kyowa.familyaddons.storage.StorageOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Equivalent of cancelling GuiScreenEvent.MouseInputEvent.Pre (all mouse input is handled by the
 * overlay's own polling), the "scrolled" trigger, and the TextInput "guiKey" cancel.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenInputMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void familystorage$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (StorageOverlay.isActive()) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void familystorage$mouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (StorageOverlay.isActive()) cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void familystorage$mouseDragged(MouseButtonEvent event, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
        if (StorageOverlay.isActive()) cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void familystorage$mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical, CallbackInfoReturnable<Boolean> cir) {
        if (!StorageOverlay.isActive()) return;
        StorageOverlay.onScrolled(vertical);
        cir.setReturnValue(true);
    }

    // Key presses for the search box are consumed earlier, in KeyboardHandlerMixin, so other
    // mods' keybinds never see them; this fallback only matters if that path is bypassed.
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void familystorage$keyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (StorageOverlay.input.isActive) cir.setReturnValue(true);
    }
}
