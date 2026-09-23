package org.kyowa.familyaddons.util

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import org.kyowa.familyaddons.features.NameStyle

/**
 * The mod's chat identity: every line FamilyAddons prints starts with `[FA]`
 * drawn in the same purple-to-dark sweep FamilyStorage uses for its label, so
 * players can tell at a glance which mod is talking.
 *
 * Use [prefixed] instead of `Component.literal("§6[FA] …")`; legacy § codes
 * inside the body still render, so existing message strings work unchanged.
 */
object FaChat {

    private const val TAG = "[FA]"

    // Same purple-to-dark band FamilyStorage uses for its label.
    const val GRADIENT_BRIGHT = 0xC86EFF // (200, 110, 255)
    const val GRADIENT_DARK = 0x4B147D   // (75, 20, 125)

    /**
     * Text in the mod's moving purple band: the same `<wave>` the Name Changer
     * draws, so `[FA]` sweeps in step with an owner name styled the same way.
     * Chat lines are built once, so the letters carry NameStyle's marker colours
     * and the font hook swaps in the live colour each frame. With Name Changer's
     * Animate off it holds still as a plain gradient.
     */
    fun gradient(text: String): MutableComponent =
        NameStyle.render("<wave:${hex(GRADIENT_DARK)}:${hex(GRADIENT_BRIGHT)}>$text</wave>", Style.EMPTY).copy()

    private fun hex(rgb: Int) = "#%06x".format(rgb)

    /** `[FA] ` in the gradient, followed by [body]. */
    fun prefixed(body: Component): MutableComponent = gradient(TAG).append(Component.literal(" ")).append(body)
    fun prefixed(text: String): MutableComponent = prefixed(Component.literal(text))

    /** Convenience: send a prefixed line to the local player's chat. Safe to call from any thread. */
    fun send(text: String) {
        val mc = Minecraft.getInstance()
        mc.execute { mc.player?.sendSystemMessage(prefixed(text)) }
    }
}
