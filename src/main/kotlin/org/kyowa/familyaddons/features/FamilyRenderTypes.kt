package org.kyowa.familyaddons.features

import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import org.kyowa.familyaddons.FamilyAddons

/**
 * How this mod draws in the world. Boxes test depth, so nothing drawn around
 * a mob or a player can be seen through a block: they go into the same target
 * the world does, and its depth buffer hides them.
 * [LINES_NO_DEPTH] is kept only as a name the features already use; it is the
 * same depth-tested lines as [LINES]. [BEAM] is the exception — see there.
 */
object FamilyRenderTypes {

    val LINES: RenderType by lazy {
        RenderType.create(
            "familyaddons_lines",
            RenderSetup.builder(RenderPipelines.LINES)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .createRenderSetup()
        )
    }

    /** The same lines as [LINES]: this build has no see-through variant. */
    val LINES_NO_DEPTH: RenderType get() = LINES

    /**
     * Beacon columns, the way they have always been: a marker you can see from
     * anywhere, so this one draws over the world on purpose.
     */
    val BEAM: RenderType by lazy {
        RenderType.create(
            "familyaddons_beam",
            RenderSetup.builder(RenderPipelines.DEBUG_QUADS)
                .setOutputTarget(OutputTarget.MAIN_TARGET)
                .createRenderSetup()
        )
    }
}
