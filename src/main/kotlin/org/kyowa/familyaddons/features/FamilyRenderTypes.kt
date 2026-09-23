package org.kyowa.familyaddons.features

import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import org.kyowa.familyaddons.FamilyAddons

/**
 * How this mod draws in the world — and the whole point of this file in the
 * public build: every one of these tests depth, so nothing the mod draws can be
 * seen through a block. [LINES_NO_DEPTH] is kept only as a name the features
 * already use; it is the same depth-tested lines as [LINES].
 */
object FamilyRenderTypes {

    val LINES: RenderType by lazy {
        RenderType.create(
            "familyaddons_lines",
            RenderSetup.builder(RenderPipelines.LINES)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .setOutputTarget(OutputTarget.MAIN_TARGET)
                .createRenderSetup()
        )
    }

    /** The same lines as [LINES]: this build has no see-through variant. */
    val LINES_NO_DEPTH: RenderType get() = LINES

    /**
     * Solid columns for beacon beams, depth tested so terrain hides them. The
     * debug quad pipeline draws without a depth test, so the depth state is set
     * here rather than taken from it.
     */
    private val BEAM_PIPELINE: RenderPipeline by lazy {
        try {
            val base = RenderPipelines.DEBUG_QUADS
            val builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("familyaddons", "pipeline/beam_depth"))
                .withVertexShader(base.vertexShader)
                .withFragmentShader(base.fragmentShader)
                .withPrimitiveTopology(base.primitiveTopology)
                .withCull(base.isCull)
                .withPolygonMode(base.polygonMode)
                .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            base.vertexFormatBindings.forEachIndexed { i, fmt -> fmt?.let { builder.withVertexBinding(i, it) } }
            base.colorTargetStates.forEachIndexed { i, s -> s?.let { builder.withColorTargetState(i, it) } }
            base.bindGroupLayouts.forEach { builder.withBindGroupLayout(it) }
            base.shaderDefines.flags().forEach { builder.withShaderDefine(it) }
            base.shaderDefines.values().forEach { (k, v) ->
                v.toIntOrNull()?.let { builder.withShaderDefine(k, it) }
                    ?: v.toFloatOrNull()?.let { builder.withShaderDefine(k, it) }
            }
            builder.build()
        } catch (e: Exception) {
            FamilyAddons.LOGGER.warn("Couldn't build the depth-tested beam pipeline", e)
            RenderPipelines.DEBUG_QUADS
        }
    }

    val BEAM: RenderType by lazy {
        RenderType.create(
            "familyaddons_beam",
            RenderSetup.builder(BEAM_PIPELINE)
                .setOutputTarget(OutputTarget.MAIN_TARGET)
                .createRenderSetup()
        )
    }
}
