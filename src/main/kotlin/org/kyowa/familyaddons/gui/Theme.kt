package org.kyowa.familyaddons.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** FamilyAddons' colours: the purple band [FA] is drawn in, on a dark violet-black. */
object Theme {
    const val ACCENT = 0xFFC86EFF.toInt()        // (200, 110, 255) bright end of the band
    const val ACCENT_DARK = 0xFF4B147D.toInt()   // (75, 20, 125) dark end
    const val ACCENT_MID = 0xFF8A40C8.toInt()
    const val ACCENT_DIM = 0x40C86EFF             // borders, focus rings

    const val WINDOW = 0xFA100D17.toInt()
    const val SIDEBAR = 0xFF17121F.toInt()
    const val PANEL = 0xFF13101A.toInt()
    const val CARD = 0xFF1C1726.toInt()
    const val CARD_HOVER = 0xFF221C2E.toInt()
    const val TRACK = 0xFF2B2438.toInt()
    const val TRACK_HOVER = 0xFF362D46.toInt()
    const val BORDER = 0xFF2E2640.toInt()

    const val TEXT = 0xFFF3EEFA.toInt()
    const val TEXT_DIM = 0xFF9E96B3.toInt()
    const val TEXT_FAINT = 0xFF6A6280.toInt()
    const val SHADOW = 0xB0000000.toInt()

    const val GOOD = 0xFF6EE7A0.toInt()
    const val BAD = 0xFFFF6E8A.toInt()

    /** Colour of a character at [t] in 0..1 along the FA band, dark at both ends, bright mid. */
    fun band(t: Double): Int {
        val k = 0.5 - 0.5 * Math.cos(2 * Math.PI * t)
        return lerp(ACCENT_DARK, ACCENT, k)
    }

    fun lerp(from: Int, to: Int, t: Double): Int {
        val k = t.coerceIn(0.0, 1.0)
        fun ch(shift: Int) = (((from shr shift) and 0xFF) + (((to shr shift) and 0xFF) - ((from shr shift) and 0xFF)) * k).roundToInt()
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}

/** Drawing helpers over the vanilla extractor: rounded boxes, band text, clipping. */
class Gfx(val ctx: GuiGraphicsExtractor, val font: Font) {

    fun fill(x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        if (x2 <= x1 || y2 <= y1) return
        ctx.fill(x1, y1, x2, y2, color)
    }

    /** Rectangle with corners rounded by [r] pixels. */
    fun rrect(x1: Int, y1: Int, x2: Int, y2: Int, r: Int, color: Int) {
        val w = x2 - x1; val h = y2 - y1
        if (w <= 0 || h <= 0) return
        val rad = minOf(r, w / 2, h / 2)
        if (rad <= 0) { fill(x1, y1, x2, y2, color); return }
        fill(x1, y1 + rad, x2, y2 - rad, color)
        for (i in 0 until rad) {
            val d = rad - i - 0.5
            val inset = rad - sqrt(rad * rad - d * d).roundToInt()
            fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color)
            fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color)
        }
    }

    /** One-pixel rounded border. */
    fun rborder(x1: Int, y1: Int, x2: Int, y2: Int, r: Int, color: Int) {
        val rad = minOf(r, (x2 - x1) / 2, (y2 - y1) / 2)
        fill(x1 + rad, y1, x2 - rad, y1 + 1, color)
        fill(x1 + rad, y2 - 1, x2 - rad, y2, color)
        fill(x1, y1 + rad, x1 + 1, y2 - rad, color)
        fill(x2 - 1, y1 + rad, x2, y2 - rad, color)
        for (i in 0 until rad) {
            val d = rad - i - 0.5
            val inset = rad - sqrt(rad * rad - d * d).roundToInt()
            fill(x1 + inset, y1 + i, x1 + inset + 1, y1 + i + 1, color)
            fill(x2 - inset - 1, y1 + i, x2 - inset, y1 + i + 1, color)
            fill(x1 + inset, y2 - i - 1, x1 + inset + 1, y2 - i, color)
            fill(x2 - inset - 1, y2 - i - 1, x2 - inset, y2 - i, color)
        }
    }

    fun text(s: String, x: Int, y: Int, color: Int, shadow: Boolean = false) = ctx.text(font, s, x, y, color, shadow)
    fun text(s: FormattedCharSequence, x: Int, y: Int, color: Int, shadow: Boolean = false) = ctx.text(font, s, x, y, color, shadow)
    fun text(c: Component, x: Int, y: Int, color: Int, shadow: Boolean = false) = ctx.text(font, c, x, y, color, shadow)
    fun width(s: String) = font.width(s)
    val lineHeight get() = font.lineHeight

    /** Text drawn in the FA band, sweeping over time like the Name Changer's <wave>. */
    fun bandText(s: String, x: Int, y: Int, shadow: Boolean = true, animate: Boolean = true) {
        var cx = x
        val n = maxOf(1, s.length - 1)
        val phase = if (animate) (System.currentTimeMillis() % 2400L) / 2400.0 else 0.0
        for ((i, ch) in s.withIndex()) {
            val col = Theme.band(i.toDouble() / n - phase)
            val str = ch.toString()
            ctx.text(font, str, cx, y, col, shadow)
            cx += font.width(str)
        }
    }

    /** Text at [scale] of normal size, anchored at its top-left; hit boxes stay in normal pixels. */
    fun textScaled(s: String, x: Int, y: Int, color: Int, scale: Float, shadow: Boolean = false) {
        val pose = ctx.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(scale, scale)
        ctx.text(font, s, 0, 0, color, shadow)
        pose.popMatrix()
    }

    /** [s] cut to fit [width] when drawn at [scale]. */
    fun clipScaled(s: String, width: Int, scale: Float): String = clip(s, (width / scale).toInt())

    fun wrap(s: String, width: Int): List<FormattedCharSequence> =
        if (s.isEmpty()) emptyList() else font.split(Component.literal(s), maxOf(10, width))

    /** [s] cut to fit [width], with an ellipsis when it had to be. */
    fun clip(s: String, width: Int): String {
        if (font.width(s) <= width) return s
        val dots = "…"
        var t = font.plainSubstrByWidth(s, maxOf(0, width - font.width(dots)))
        return t + dots
    }

    /** [s] cut to fit [width], no ellipsis. */
    fun cut(s: String, width: Int): String = font.plainSubstrByWidth(s, maxOf(0, width))

    inline fun clipped(x1: Int, y1: Int, x2: Int, y2: Int, block: () -> Unit) {
        ctx.enableScissor(x1, y1, x2, y2)
        try { block() } finally { ctx.disableScissor() }
    }
}
