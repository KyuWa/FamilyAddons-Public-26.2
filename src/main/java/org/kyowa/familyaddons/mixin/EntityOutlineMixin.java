package org.kyowa.familyaddons.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.kyowa.familyaddons.features.DungeonHighlight;
import org.kyowa.familyaddons.features.EntityHighlight;
import org.kyowa.familyaddons.features.KuudraHighlight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityOutlineMixin<T extends Entity, S extends EntityRenderState> {

    /**
     * The glow outline draws over everything, block or not, so in this build a
     * mob only glows while you can actually see it. Nothing the mod adds is
     * ever visible through terrain.
     */
    private static boolean familyaddons$visible(Entity entity) {
        var player = Minecraft.getInstance().player;
        return player != null && player.hasLineOfSight(entity);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onUpdateRenderState(T entity, S state, float tickProgress, CallbackInfo ci) {
        if (!familyaddons$visible(entity)) return;
        // EntityHighlight takes priority
        int highlightColor = EntityHighlight.INSTANCE.getOutlineColor(entity);
        if (highlightColor != 0) {
            state.outlineColor = highlightColor;
            return;
        }

        // Dungeon mob highlight
        int dungeonColor = DungeonHighlight.INSTANCE.getOutlineColor(entity);
        if (dungeonColor != 0) {
            state.outlineColor = dungeonColor;
            return;
        }

        // Kuudra boss outline
        int kuudraColor = KuudraHighlight.INSTANCE.getOutlineColor(entity);
        if (kuudraColor != 0) {
            state.outlineColor = kuudraColor;
            return;
        }

    }
}
