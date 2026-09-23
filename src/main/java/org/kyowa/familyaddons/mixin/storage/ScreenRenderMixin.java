package org.kyowa.familyaddons.mixin.storage;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.kyowa.familyaddons.storage.StorageOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Equivalent of cancelling GuiScreenEvent.DrawScreenEvent.Pre: while the overlay is active the
 * vanilla container screen is not drawn at all and the storage overlay is drawn instead.
 */
@Mixin(Screen.class)
public class ScreenRenderMixin {

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), cancellable = true)
    private void familystorage$drawStorage(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!((Object) this instanceof AbstractContainerScreen<?>)) return;
        if (!StorageOverlay.isActive()) return;
        ctx.nextStratum();
        StorageOverlay.render(ctx, mouseX, mouseY);
        ctx.extractDeferredElements(mouseX, mouseY, delta);
        ci.cancel();
    }
}
