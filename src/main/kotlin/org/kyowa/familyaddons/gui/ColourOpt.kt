package org.kyowa.familyaddons.gui

import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import java.awt.Color
import kotlin.math.roundToInt

/**
 * Colour option, stored the way FamilyAddons stores it: "chroma:alpha:r:g:b".
 *
 * The swatch opens a picker: a saturation/value square with vertical hue and
 * alpha bars beside it, a before/after preview, hex and R G B A boxes that can
 * be typed into, a chroma toggle with its speed, and a row of presets. Every
 * part updates the others live.
 */
class ColourOpt(opt: OptionSpec) : Widget(opt) {
    override val w = 40
    override val h = 14
    private var open = false
    override val wantsOverlay get() = open

    // picker geometry
    private val pw = 196; private val ph = 176
    private var px = 0; private var py = 0
    private val sqW = 120; private val sqH = 84
    private val barW = 10
    private var dragTarget = 0 // 1 square, 2 hue, 3 alpha, 4 chroma speed

    /** Chroma speed 0..10; kept as a float so the slider moves smoothly, stored rounded. */
    private var chroma = 0f; private var alpha = 255; private var hue = 0f; private var sat = 0f; private var value = 1f
    private var before = ""   // value when the picker opened, shown beside the new one

    private val hex = TextField("", onCommit = { commitHex(it) })
    private val fr = TextField("", numeric = true, onCommit = { commitRgb() })
    private val fg = TextField("", numeric = true, onCommit = { commitRgb() })
    private val fb = TextField("", numeric = true, onCommit = { commitRgb() })
    private val fa = TextField("", numeric = true, onCommit = { commitRgb() })
    private val fields = listOf(hex, fr, fg, fb, fa)

    private val presets = listOf(
        Theme.ACCENT and 0xFFFFFF, Theme.ACCENT_DARK and 0xFFFFFF, 0xFFFFFF, 0x000000,
        0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF, 0xAAAAAA,
    )

    init { load() }

    private fun load() {
        val n = Values.string(opt).split(":").mapNotNull { it.trim().toIntOrNull() }
        if (n.size >= 5) {
            chroma = n[0].coerceIn(0, 10).toFloat(); alpha = n[1].coerceIn(0, 255)
            val hsb = Color.RGBtoHSB(n[2].coerceIn(0, 255), n[3].coerceIn(0, 255), n[4].coerceIn(0, 255), null)
            hue = hsb[0]; sat = hsb[1]; value = hsb[2]
        }
        syncFields()
    }

    private fun rgb(): Int = Color.HSBtoRGB(hue, sat, value) and 0xFFFFFF
    private fun argb(): Int = (alpha shl 24) or rgb()
    private fun hexOf(): String = String.format("#%06X", rgb())

    private fun syncFields() {
        val c = rgb()
        if (!hex.focused) hex.set(hexOf())
        if (!fr.focused) fr.set(((c shr 16) and 0xFF).toString())
        if (!fg.focused) fg.set(((c shr 8) and 0xFF).toString())
        if (!fb.focused) fb.set((c and 0xFF).toString())
        if (!fa.focused) fa.set(alpha.toString())
    }

    private fun store() {
        val c = rgb()
        Values.set(opt, "${chroma.roundToInt()}:$alpha:${(c shr 16) and 0xFF}:${(c shr 8) and 0xFF}:${c and 0xFF}")
        syncFields()
    }

    private fun setRgb(r: Int, g: Int, b: Int) {
        val hsb = Color.RGBtoHSB(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255), null)
        hue = hsb[0]; sat = hsb[1]; value = hsb[2]
    }

    private fun commitHex(s: String) {
        val t = s.trim().removePrefix("#")
        val v = t.toIntOrNull(16)
        if (v == null || t.length != 6) { hex.set(hexOf()); return }
        setRgb((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
        store()
    }

    private fun commitRgb() {
        val r = fr.value.toIntOrNull(); val g = fg.value.toIntOrNull(); val b = fb.value.toIntOrNull(); val a = fa.value.toIntOrNull()
        if (r == null || g == null || b == null || a == null) { syncFields(); return }
        setRgb(r, g, b); alpha = a.coerceIn(0, 255)
        store()
    }

    // The rainbow phase advances by speed each frame instead of being derived
    // from the clock, so dragging the speed slider never snaps the colour.
    private var chromaPhase = 0f
    private var chromaLastMs = System.currentTimeMillis()

    /** What the colour looks like right now, chroma included. */
    private fun liveColour(): Int {
        val now = System.currentTimeMillis()
        val dt = (now - chromaLastMs).coerceIn(0, 100) / 1000f
        chromaLastMs = now
        if (chroma < 0.05f) return argb()
        chromaPhase = (chromaPhase + dt * chroma / 8f) % 1f
        return (alpha shl 24) or (Color.HSBtoRGB(chromaPhase, 1f, 1f) and 0xFFFFFF)
    }

    private fun checker(g: Gfx, x1: Int, y1: Int, x2: Int, y2: Int) {
        var cy = y1; var row = 0
        while (cy < y2) {
            var cx = x1; var col = row
            while (cx < x2) {
                g.fill(cx, cy, minOf(cx + 3, x2), minOf(cy + 3, y2), if (col % 2 == 0) 0xFF3A3A3A.toInt() else 0xFF5A5A5A.toInt())
                cx += 3; col++
            }
            cy += 3; row++
        }
    }

    override fun render(g: Gfx, mx: Int, my: Int) {
        if (!open) load()
        val hover = hovered(mx, my)
        g.rrect(x, y, x + w, y + h, 3, if (hover || open) Theme.TRACK_HOVER else Theme.TRACK)
        g.rborder(x, y, x + w, y + h, 3, if (open) Theme.ACCENT else Theme.BORDER)
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF14111C.toInt())
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, liveColour())
    }

    override fun click(mx: Double, my: Double, button: Int): Boolean {
        if (!contains(mx, my)) return false
        open = !open
        if (open) { load(); before = Values.string(opt) }
        return true
    }

    // ── picker ─────────────────────────────────────────────────────────

    private val sqX get() = px + 8
    private val sqY get() = py + 8
    private val hueX get() = sqX + sqW + 6
    private val alphaX get() = hueX + barW + 6
    private val prevX get() = alphaX + barW + 8           // preview column
    private val rowHexY get() = sqY + sqH + 8
    private val rowRgbY get() = rowHexY + 18
    private val rowChromaY get() = rowRgbY + 18
    private val rowPresetY get() = rowChromaY + 16

    override fun renderOverlay(g: Gfx, mx: Int, my: Int, screenW: Int, screenH: Int) {
        px = (sx(x) + sw() - pw).coerceIn(2, screenW - pw - 2)
        py = if (sy(y) + sh() + 2 + ph <= screenH) sy(y) + sh() + 2 else maxOf(2, sy(y) - 2 - ph)
        g.rrect(px, py, px + pw, py + ph, 5, Theme.CARD)
        g.rborder(px, py, px + pw, py + ph, 5, Theme.ACCENT_DIM)

        // saturation across, value down
        for (i in 0 until sqW) {
            val s = i / (sqW - 1f)
            val top = 0xFF000000.toInt() or (Color.HSBtoRGB(hue, s, 1f) and 0xFFFFFF)
            g.ctx.fillGradient(sqX + i, sqY, sqX + i + 1, sqY + sqH, top, 0xFF000000.toInt())
        }
        g.rborder(sqX - 1, sqY - 1, sqX + sqW + 1, sqY + sqH + 1, 1, Theme.BORDER)
        val cx = sqX + (sat * (sqW - 1)).roundToInt(); val cy = sqY + ((1 - value) * (sqH - 1)).roundToInt()
        g.rborder(cx - 4, cy - 4, cx + 5, cy + 5, 4, 0xFF000000.toInt())
        g.rborder(cx - 3, cy - 3, cx + 4, cy + 4, 3, 0xFFFFFFFF.toInt())

        // hue bar, vertical
        for (i in 0 until sqH) g.fill(hueX, sqY + i, hueX + barW, sqY + i + 1, 0xFF000000.toInt() or (Color.HSBtoRGB(i / (sqH - 1f), 1f, 1f) and 0xFFFFFF))
        vmarker(g, hueX, sqY + (hue * (sqH - 1)).roundToInt())

        // alpha bar, vertical: opaque at the top
        g.fill(alphaX, sqY, alphaX + barW, sqY + sqH, 0xFF14111C.toInt())
        for (i in 0 until sqH) g.fill(alphaX, sqY + i, alphaX + barW, sqY + i + 1, ((255 - i * 255 / (sqH - 1)) shl 24) or rgb())
        vmarker(g, alphaX, sqY + ((255 - alpha) * (sqH - 1) / 255f).roundToInt())

        // before / after preview
        val pvW = px + pw - 8 - prevX
        g.text("new", prevX, sqY, Theme.TEXT_FAINT)
        g.fill(prevX, sqY + 10, prevX + pvW, sqY + 40, 0xFF14111C.toInt())
        g.fill(prevX, sqY + 10, prevX + pvW, sqY + 40, liveColour())
        g.text("old", prevX, sqY + 44, Theme.TEXT_FAINT)
        g.fill(prevX, sqY + 54, prevX + pvW, sqY + sqH, 0xFF14111C.toInt())
        g.fill(prevX, sqY + 54, prevX + pvW, sqY + sqH, parseArgb(before))
        g.rborder(prevX - 1, sqY + 9, prevX + pvW + 1, sqY + sqH + 1, 2, Theme.BORDER)

        // hex
        g.text("Hex", sqX, rowHexY + 3, Theme.TEXT_DIM)
        hex.x = sqX + 24; hex.y = rowHexY; hex.w = 66; hex.h = 14
        hex.render(g, mx, my)
        val copyX = hex.x + hex.w + 6
        val overCopy = mx >= copyX && mx < copyX + 30 && my >= rowHexY && my < rowHexY + 14
        g.rrect(copyX, rowHexY, copyX + 30, rowHexY + 14, 3, if (overCopy) Theme.TRACK_HOVER else Theme.TRACK)
        g.text("copy", copyX + 5, rowHexY + 3, Theme.TEXT_DIM)

        // r g b a
        var fx = sqX
        for ((label, f) in listOf("R" to fr, "G" to fg, "B" to fb, "A" to fa)) {
            g.text(label, fx, rowRgbY + 3, Theme.TEXT_DIM)
            f.x = fx + 9; f.y = rowRgbY; f.w = 30; f.h = 14
            f.render(g, mx, my)
            fx += 46
        }

        // chroma: on/off plus speed
        g.text("Chroma", sqX, rowChromaY + 2, Theme.TEXT_DIM)
        val tx = sqX + 42
        val on = chroma > 0f
        g.rrect(tx, rowChromaY, tx + 22, rowChromaY + 11, 5, if (on) Theme.ACCENT_MID else Theme.TRACK)
        g.rrect(tx + 2 + (if (on) 11 else 0), rowChromaY + 2, tx + 9 + (if (on) 11 else 0), rowChromaY + 9, 4, Theme.TEXT)
        val sx = tx + 30; val sw = px + pw - 8 - sx
        g.rrect(sx, rowChromaY + 4, sx + sw, rowChromaY + 7, 1, Theme.TRACK)
        if (on) {
            g.rrect(sx, rowChromaY + 4, sx + (sw * chroma / 10f).roundToInt(), rowChromaY + 7, 1, Theme.ACCENT_MID)
            val kx = sx + ((sw - 6) * chroma / 10f).roundToInt()
            g.rrect(kx, rowChromaY + 2, kx + 6, rowChromaY + 9, 3, Theme.TEXT)
        } else g.text("speed", sx + 2, rowChromaY + 2, Theme.TEXT_FAINT)

        // presets
        var pxx = sqX
        for (c in presets) {
            val over = mx >= pxx && mx < pxx + 12 && my >= rowPresetY && my < rowPresetY + 12
            g.rrect(pxx, rowPresetY, pxx + 12, rowPresetY + 12, 2, 0xFF000000.toInt() or c)
            if (over) g.rborder(pxx, rowPresetY, pxx + 12, rowPresetY + 12, 2, Theme.TEXT)
            pxx += 15
        }
    }

    private fun parseArgb(s: String): Int {
        val n = s.split(":").mapNotNull { it.trim().toIntOrNull() }
        if (n.size < 5) return 0
        return (n[1].coerceIn(0, 255) shl 24) or (n[2].coerceIn(0, 255) shl 16) or (n[3].coerceIn(0, 255) shl 8) or n[4].coerceIn(0, 255)
    }

    private fun vmarker(g: Gfx, bx: Int, my: Int) {
        g.fill(bx - 2, my - 1, bx + barW + 2, my + 2, 0xFF000000.toInt())
        g.fill(bx - 2, my, bx + barW + 2, my + 1, 0xFFFFFFFF.toInt())
    }

    private fun inPicker(mx: Double, my: Double) = mx >= px && mx < px + pw && my >= py && my < py + ph

    private fun applyDrag(mx: Double, my: Double) {
        when (dragTarget) {
            1 -> { sat = ((mx - sqX) / (sqW - 1)).coerceIn(0.0, 1.0).toFloat(); value = (1 - (my - sqY) / (sqH - 1)).coerceIn(0.0, 1.0).toFloat() }
            2 -> hue = ((my - sqY) / (sqH - 1)).coerceIn(0.0, 1.0).toFloat()
            3 -> alpha = (255 - ((my - sqY) / (sqH - 1)).coerceIn(0.0, 1.0) * 255).roundToInt()
            4 -> { val sx = sqX + 72; val sw = px + pw - 8 - sx; chroma = ((mx - sx) / sw * 10).toFloat().coerceIn(0.5f, 10f) }
        }
        store()
    }

    override fun overlayClick(mx: Double, my: Double, button: Int): Boolean {
        if (containsScreen(mx, my)) { closeOverlay(); return true }
        if (!inPicker(mx, my)) { closeOverlay(); return false }
        for (f in fields) if (f.click(mx, my, button)) { fields.filter { it !== f }.forEach { it.focused = false }; return true }
        fields.forEach { it.focused = false }

        val copyX = hex.x + hex.w + 6
        if (mx >= copyX && mx < copyX + 30 && my >= rowHexY && my < rowHexY + 14) {
            net.minecraft.client.Minecraft.getInstance().keyboardHandler.clipboard = hexOf(); return true
        }
        val tx = sqX + 42
        if (my >= rowChromaY - 2 && my < rowChromaY + 13) {
            if (mx >= tx && mx < tx + 22) { chroma = if (chroma > 0f) 0f else 3f; store(); return true }
            if (mx >= tx + 30 && chroma > 0f) { dragTarget = 4; applyDrag(mx, my); return true }
            return true
        }
        if (my >= rowPresetY && my < rowPresetY + 12) {
            val i = ((mx - sqX) / 15).toInt()
            if (i in presets.indices && mx - sqX - i * 15 < 12) { val c = presets[i]; setRgb((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF); store() }
            return true
        }
        dragTarget = when {
            mx >= sqX - 2 && mx < sqX + sqW + 2 && my >= sqY - 2 && my < sqY + sqH + 2 -> 1
            mx >= hueX - 3 && mx < hueX + barW + 3 && my >= sqY - 2 && my < sqY + sqH + 2 -> 2
            mx >= alphaX - 3 && mx < alphaX + barW + 3 && my >= sqY - 2 && my < sqY + sqH + 2 -> 3
            else -> 0
        }
        if (dragTarget != 0) applyDrag(mx, my)
        return true
    }

    override fun overlayDrag(mx: Double, my: Double): Boolean {
        fields.firstOrNull { it.focused }?.let { return it.drag(mx) }
        if (dragTarget == 0) return false
        applyDrag(mx, my)
        return true
    }

    override fun release() { dragTarget = 0 }

    override fun key(e: KeyEvent): Boolean {
        val f = fields.firstOrNull { it.focused }
        if (f != null) return f.key(e)
        if (open && e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            // Esc keeps the colour, like clicking away from the picker
            closeOverlay(); return true
        }
        return false
    }

    override fun char(e: CharacterEvent): Boolean = fields.firstOrNull { it.focused }?.char(e) ?: false
    override fun unfocus() { fields.forEach { it.focused = false } }
    override fun closeOverlay() { open = false; unfocus(); dragTarget = 0 }
}
