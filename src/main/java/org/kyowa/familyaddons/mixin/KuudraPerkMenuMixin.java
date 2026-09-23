package org.kyowa.familyaddons.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.kyowa.familyaddons.features.ComposterGuard;
import org.kyowa.familyaddons.features.KuudraPerkMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Kuudra Perk Menu: hidden perks are not drawn, and clicks go through KuudraPerkMenu first. ComposterGuard sees clicks too. */
@Mixin(AbstractContainerScreen.class)
public abstract class KuudraPerkMenuMixin {

    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void familyaddons$hidePerk(GuiGraphicsExtractor ctx, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (KuudraPerkMenu.hideSlot((AbstractContainerScreen<?>) (Object) this, slot)) ci.cancel();
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void familyaddons$perkClick(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (KuudraPerkMenu.onSlotClick(self, slot, slotId, button, input) || ComposterGuard.onSlotClick(self, slot)) ci.cancel();
    }
}
