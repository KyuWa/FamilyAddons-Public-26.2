package org.kyowa.familyaddons.util

import java.awt.Color

/**
 * Reads the config's colour strings, "chroma:alpha:r:g:b", the way the colour
 * picker writes them. With a chroma speed above 0 the hue cycles over time (a
 * full turn every 8/speed seconds, the same pace the picker previews at).
 * Every reader throws on a malformed string so callers keep their own fallback.
 */
object FaColour {
    /** r, g, b, a as 0..255. */
    fun parts(s: String): IntArray {
        val p = s.split(":")
        val chroma = p[0].toInt()
        var r = p[2].toInt(); var g = p[3].toInt(); var b = p[4].toInt()
        if (chroma > 0) {
            val hue = (System.currentTimeMillis() % 800_000L) / 1000f * chroma / 8f
            val c = Color.HSBtoRGB(hue % 1f, 1f, 1f)
            r = (c shr 16) and 0xFF; g = (c shr 8) and 0xFF; b = c and 0xFF
        }
        return intArrayOf(r, g, b, p[1].toInt())
    }

    /** Opaque ARGB. */
    fun argb(s: String): Int {
        val c = parts(s)
        return (0xFF shl 24) or (c[0] shl 16) or (c[1] shl 8) or c[2]
    }

    /** r, g, b, a in 0..1. */
    fun floats(s: String): FloatArray {
        val c = parts(s)
        return floatArrayOf(c[0] / 255f, c[1] / 255f, c[2] / 255f, c[3] / 255f)
    }
}
