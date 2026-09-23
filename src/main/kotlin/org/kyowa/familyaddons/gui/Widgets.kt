package org.kyowa.familyaddons.gui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A control on the right of an option row. The row hands it a position each
 * frame; it draws itself and answers to input. A widget that pops something open
 * (dropdown list, colour picker) becomes the screen's overlay and gets the
 * overlay* calls, drawn above everything and receiving input first.
 */
abstract class Widget(val opt: OptionSpec) {
    var x = 0; var y = 0
    /** The screen origin and scale of the space x/y/w/h are in (the panels draw their insides smaller). */
    var ox = 0; var oy = 0; var scale = 1f
    fun sx(v: Int) = ox + (v * scale).roundToInt()
    fun sy(v: Int) = oy + (v * scale).roundToInt()
    fun sw() = (w * scale).roundToInt()
    fun sh() = (h * scale).roundToInt()
    /** Like [contains] but for screen coordinates. */
    fun containsScreen(mx: Double, my: Double) = mx >= sx(x) && mx < sx(x) + sw() && my >= sy(y) && my < sy(y) + sh()
    open val w: Int = 40
    open val h: Int = 14
    fun contains(mx: Double, my: Double) = mx >= x && mx < x + w && my >= y && my < y + h

    abstract fun render(g: Gfx, mx: Int, my: Int)
    open fun click(mx: Double, my: Double, button: Int): Boolean = false
    open fun drag(mx: Double, my: Double): Boolean = false
    open fun release() {}
    open fun scroll(mx: Double, my: Double, amount: Double): Boolean = false
    open fun key(e: KeyEvent): Boolean = false
    open fun char(e: CharacterEvent): Boolean = false
    open fun unfocus() {}
    open val wantsOverlay: Boolean get() = false
    open fun renderOverlay(g: Gfx, mx: Int, my: Int, screenW: Int, screenH: Int) {}
    /** True when the click was inside the overlay (else the overlay closes). */
    open fun overlayClick(mx: Double, my: Double, button: Int): Boolean = false
    open fun overlayDrag(mx: Double, my: Double): Boolean = false
    open fun overlayScroll(mx: Double, my: Double, amount: Double): Boolean = false
    open fun closeOverlay() {}

    protected fun hovered(mx: Int, my: Int) = contains(mx.toDouble(), my.toDouble())
}

// ── Toggle ─────────────────────────────────────────────────────────────────

class Toggle(opt: OptionSpec) : Widget(opt) {
    override val w = 26
    override val h = 13
    private var anim = if (Values.bool(opt)) 1f else 0f

    override fun render(g: Gfx, mx: Int, my: Int) {
        val on = Values.bool(opt)
        val target = if (on) 1f else 0f
        anim += (target - anim) * 0.35f
        if (Math.abs(target - anim) < 0.01f) anim = target
        val track = Theme.lerp(if (hovered(mx, my)) Theme.TRACK_HOVER else Theme.TRACK, Theme.ACCENT_MID, anim.toDouble())
        g.rrect(x, y, x + w, y + h, 6, track)
        val kx = x + 2 + ((w - h) * anim).roundToInt()
        g.rrect(kx, y + 2, kx + h - 4, y + h - 2, 5, Theme.lerp(Theme.TEXT_DIM, Theme.TEXT, anim.toDouble()))
    }

    override fun click(mx: Double, my: Double, button: Int): Boolean {
        if (!contains(mx, my)) return false
        Values.set(opt, !Values.bool(opt))
        return true
    }
}

// ── Slider ─────────────────────────────────────────────────────────────────

class Slider(opt: OptionSpec) : Widget(opt) {
    override var w = 130
    override val h = 14
    private val boxW = 34
    private val trackW get() = w - boxW - 6
    private var dragging = false
    private val decimals: Int = run {
        val s = opt.step.toString().trimEnd('0')
        if (opt.step == opt.step.roundToInt().toFloat()) 0 else (s.substringAfter('.', "").length).coerceIn(1, 3)
    }
    private val field = TextField(fmt(Values.float(opt)), numeric = true, onCommit = { commitTyped(it) })

    private fun fmt(v: Float): String =
        if (decimals == 0) v.roundToInt().toString()
        else String.format(Locale.ROOT, "%.${decimals}f", v).trimEnd('0').trimEnd('.')

    private fun snap(v: Float): Float {
        val steps = ((v - opt.min) / opt.step).roundToInt()
        val snapped = opt.min + steps * opt.step
        return snapped.coerceIn(opt.min, opt.max)
    }

    private fun setValue(v: Float) {
        val s = snap(v)
        Values.set(opt, s)
        if (!field.focused) field.set(fmt(s))
    }

    private fun commitTyped(text: String) {
        val v = text.toFloatOrNull()
        if (v == null) { field.set(fmt(Values.float(opt))); return }
        setValue(v); field.set(fmt(Values.float(opt)))
    }

    override fun render(g: Gfx, mx: Int, my: Int) {
        val v = Values.float(opt)
        val t = if (opt.max > opt.min) ((v - opt.min) / (opt.max - opt.min)).coerceIn(0f, 1f) else 0f
        val ty = y + h / 2
        val overTrack = mx >= x && mx < x + trackW && my >= y && my < y + h
        g.rrect(x, ty - 2, x + trackW, ty + 2, 2, if (overTrack || dragging) Theme.TRACK_HOVER else Theme.TRACK)
        val fillX = x + (trackW * t).roundToInt()
        g.rrect(x, ty - 2, fillX, ty + 2, 2, Theme.ACCENT_MID)
        val kx = (x + (trackW - 8) * t).roundToInt()
        g.rrect(kx, ty - 4, kx + 8, ty + 4, 4, if (dragging) Theme.ACCENT else Theme.TEXT)
        if (!field.focused && field.value != fmt(v)) field.set(fmt(v))
        field.x = x + trackW + 6; field.y = y; field.w = boxW; field.h = h
        field.render(g, mx, my)
    }

    private fun valueAt(mx: Double): Float {
        val t = ((mx - x - 4) / (trackW - 8)).coerceIn(0.0, 1.0)
        return opt.min + (opt.max - opt.min) * t.toFloat()
    }

    override fun click(mx: Double, my: Double, button: Int): Boolean {
        if (field.click(mx, my, button)) return true
        if (!contains(mx, my) || mx >= x + trackW) return false
        dragging = true
        setValue(valueAt(mx))
        return true
    }

    override fun drag(mx: Double, my: Double): Boolean {
        if (field.focused) return field.drag(mx)
        if (!dragging) return false
        setValue(valueAt(mx))
        return true
    }

    override fun release() { dragging = false }

    override fun scroll(mx: Double, my: Double, amount: Double): Boolean {
        if (!contains(mx, my) || mx >= x + trackW) return false
        setValue(Values.float(opt) + (if (amount > 0) opt.step else -opt.step))
        return true
    }

    override fun key(e: KeyEvent) = field.key(e)
    override fun char(e: CharacterEvent) = field.char(e)
    override fun unfocus() { field.focused = false }
}

// ── Text ───────────────────────────────────────────────────────────────────

class TextOpt(opt: OptionSpec) : Widget(opt) {
    override var w = 130
    override val h = 14
    private val field = TextField(Values.string(opt), onChange = { Values.set(opt, it) })

    override fun render(g: Gfx, mx: Int, my: Int) {
        if (!field.focused && field.value != Values.string(opt)) field.set(Values.string(opt))
        field.x = x; field.y = y; field.w = w; field.h = h
        field.render(g, mx, my)
    }
    override fun click(mx: Double, my: Double, button: Int) = field.click(mx, my, button)
    override fun drag(mx: Double, my: Double) = field.drag(mx)
    override fun key(e: KeyEvent) = field.key(e)
    override fun char(e: CharacterEvent) = field.char(e)
    override fun unfocus() { field.focused = false }
}

// ── Dropdown ───────────────────────────────────────────────────────────────

class Dropdown(opt: OptionSpec) : Widget(opt) {
    override var w = 130
    override val h = 14
    private var open = false
    private var listScroll = 0
    private val rowH = 12
    private var listX = 0; private var listY = 0; private var listH = 0
    override val wantsOverlay get() = open

    private fun current() = opt.values.getOrNull(Values.int(opt)) ?: "?"

    override fun render(g: Gfx, mx: Int, my: Int) {
        val hover = hovered(mx, my)
        g.rrect(x, y, x + w, y + h, 3, if (hover || open) Theme.TRACK_HOVER else Theme.TRACK)
        g.rborder(x, y, x + w, y + h, 3, if (open) Theme.ACCENT else Theme.BORDER)
        g.text(g.clip(current(), w - 18), x + 4, y + 3, Theme.TEXT)
        g.text(if (open) "▴" else "▾", x + w - 10, y + 3, Theme.TEXT_DIM)
    }

    override fun click(mx: Double, my: Double, button: Int): Boolean {
        if (!contains(mx, my)) return false
        open = !open
        return true
    }

    override fun renderOverlay(g: Gfx, mx: Int, my: Int, screenW: Int, screenH: Int) {
        val n = opt.values.size
        val fx = sx(x); val fy = sy(y); val fw = sw(); val fh = sh()
        val wantH = n * rowH + 4
        val below = screenH - (fy + fh + 2)
        listH = minOf(wantH, maxOf(below, fy - 2), 160)
        listX = fx
        listY = if (below >= listH) fy + fh + 2 else fy - 2 - listH
        val maxScroll = maxOf(0, wantH - listH)
        listScroll = listScroll.coerceIn(0, maxScroll)
        g.rrect(listX, listY, listX + fw, listY + listH, 3, Theme.CARD)
        g.rborder(listX, listY, listX + fw, listY + listH, 3, Theme.ACCENT_DIM)
        g.clipped(listX, listY + 2, listX + fw, listY + listH - 2) {
            for ((i, v) in opt.values.withIndex()) {
                val ry = listY + 2 + i * rowH - listScroll
                if (ry + rowH < listY || ry > listY + listH) continue
                val over = mx >= listX && mx < listX + fw && my >= ry && my < ry + rowH && my >= listY && my < listY + listH
                val sel = i == Values.int(opt)
                if (over) g.fill(listX + 2, ry, listX + fw - 2, ry + rowH, Theme.CARD_HOVER)
                if (sel) g.fill(listX + 2, ry + 2, listX + 4, ry + rowH - 2, Theme.ACCENT)
                g.text(g.clip(v, fw - 12), listX + 7, ry + 2, if (sel) Theme.TEXT else Theme.TEXT_DIM)
            }
        }
        if (maxScroll > 0) {
            val thumbH = maxOf(8, listH * listH / wantH)
            val thumbY = listY + 2 + ((listH - 4 - thumbH) * listScroll / maxScroll)
            g.rrect(listX + fw - 4, thumbY, listX + fw - 2, thumbY + thumbH, 1, Theme.ACCENT_DIM)
        }
    }

    override fun overlayClick(mx: Double, my: Double, button: Int): Boolean {
        if (containsScreen(mx, my)) { open = false; return true }
        if (mx < listX || mx >= listX + sw() || my < listY || my >= listY + listH) { open = false; return false }
        val i = ((my - listY - 2 + listScroll) / rowH).toInt()
        if (i in opt.values.indices) { Values.set(opt, i); open = false }
        return true
    }

    override fun overlayScroll(mx: Double, my: Double, amount: Double): Boolean {
        listScroll -= (amount * rowH).roundToInt()
        return true
    }

    override fun closeOverlay() { open = false }
}

// ── Keybind ────────────────────────────────────────────────────────────────

class Keybind(opt: OptionSpec) : Widget(opt) {
    override var w = 90
    override val h = 14
    private var listening = false

    private fun keyName(): String {
        val k = Values.int(opt)
        if (k == GLFW.GLFW_KEY_UNKNOWN) return "None"
        // GLFW names keypad keys like the main row ("2"), so say where they are
        if (k in GLFW.GLFW_KEY_KP_0..GLFW.GLFW_KEY_KP_EQUAL) return "Numpad " + when (k) {
            GLFW.GLFW_KEY_KP_DECIMAL -> "."
            GLFW.GLFW_KEY_KP_DIVIDE -> "/"
            GLFW.GLFW_KEY_KP_MULTIPLY -> "*"
            GLFW.GLFW_KEY_KP_SUBTRACT -> "-"
            GLFW.GLFW_KEY_KP_ADD -> "+"
            GLFW.GLFW_KEY_KP_ENTER -> "Enter"
            GLFW.GLFW_KEY_KP_EQUAL -> "="
            else -> (k - GLFW.GLFW_KEY_KP_0).toString()
        }
        return InputConstants.Type.KEYSYM.getOrCreate(k).displayName.string
    }

    override fun render(g: Gfx, mx: Int, my: Int) {
        val hover = hovered(mx, my)
        g.rrect(x, y, x + w, y + h, 3, if (listening) Theme.withAlpha(Theme.ACCENT_DARK, 0xFF) else if (hover) Theme.TRACK_HOVER else Theme.TRACK)
        g.rborder(x, y, x + w, y + h, 3, if (listening) Theme.ACCENT else Theme.BORDER)
        val label = if (listening) "Press a key…" else keyName()
        val bound = Values.int(opt) != GLFW.GLFW_KEY_UNKNOWN
        val textW = w - if (bound && !listening) 14 else 0
        g.text(g.clip(label, textW - 8), x + (textW - g.width(g.clip(label, textW - 8))) / 2, y + 3, if (listening) Theme.TEXT else if (bound) Theme.TEXT else Theme.TEXT_DIM)
        if (bound && !listening) {
            val overX = mx >= x + w - 13 && mx < x + w && my >= y && my < y + h
            g.text("×", x + w - 9, y + 3, if (overX) Theme.BAD else Theme.TEXT_DIM)
        }
    }

    override fun click(mx: Double, my: Double, button: Int): Boolean {
        if (!contains(mx, my)) { listening = false; return false }
        val bound = Values.int(opt) != GLFW.GLFW_KEY_UNKNOWN
        if (bound && !listening && mx >= x + w - 13) { Values.set(opt, GLFW.GLFW_KEY_UNKNOWN); return true }
        listening = !listening
        return true
    }

    override fun key(e: KeyEvent): Boolean {
        if (!listening) return false
        Values.set(opt, if (e.key() == GLFW.GLFW_KEY_ESCAPE) GLFW.GLFW_KEY_UNKNOWN else e.key())
        listening = false
        return true
    }

    override fun unfocus() { listening = false }
}

// ── Button ─────────────────────────────────────────────────────────────────

class ButtonOpt(opt: OptionSpec) : Widget(opt) {
    override val w get() = maxOf(60, Minecraft.getInstance().font.width(opt.text) + 20)
    override val h = 14
    private var pressedAt = 0L

    override fun render(g: Gfx, mx: Int, my: Int) {
        val hover = hovered(mx, my)
        val flash = System.currentTimeMillis() - pressedAt < 150
        g.rrect(x, y, x + w, y + h, 3, if (flash) Theme.ACCENT else if (hover) Theme.ACCENT_MID else Theme.ACCENT_DARK)
        g.text(opt.text, x + (w - g.width(opt.text)) / 2, y + 3, Theme.TEXT, true)
    }

    override fun click(mx: Double, my: Double, button: Int): Boolean {
        if (!contains(mx, my)) return false
        pressedAt = System.currentTimeMillis()
        Values.runButton(opt)
        return true
    }
}

// ── List editors: a button that opens its own screen ────────────────────

/** Toggle plus "Edit (n)" for the command shortcuts; the list lives in its own screen. */
class ShortcutsOpt(opt: OptionSpec) : Widget(opt) {
    override val w = 26 + 6 + 50
    override val h = 14
    private val toggle = Toggle(opt)

    override fun render(g: Gfx, mx: Int, my: Int) {
        toggle.x = x; toggle.y = y
        toggle.render(g, mx, my)
        val bx = x + 32
        val over = mx >= bx && mx < x + w && my >= y && my < y + h
        g.rrect(bx, y, x + w, y + h, 3, if (over) Theme.ACCENT_MID else Theme.ACCENT_DARK)
        val label = "Edit (${ListEditorScreen.count("utilities.commandShortcutList")})"
        g.text(label, bx + (x + w - bx - g.width(label)) / 2, y + 3, Theme.TEXT, true)
    }

    override fun click(mx: Double, my: Double, button: Int): Boolean {
        if (!contains(mx, my)) return false
        if (mx < x + 30) return toggle.click(mx, my, button)
        val mc = Minecraft.getInstance()
        mc.gui.setScreen(ListEditorScreen.shortcuts(mc.gui.screen()))
        return true
    }
}

/** "Edit (n)" for the custom GFS list. */
class GfsListOpt(opt: OptionSpec) : Widget(opt) {
    override val w = 60
    override val h = 14
    override fun render(g: Gfx, mx: Int, my: Int) {
        val over = hovered(mx, my)
        g.rrect(x, y, x + w, y + h, 3, if (over) Theme.ACCENT_MID else Theme.ACCENT_DARK)
        val label = "Edit (${ListEditorScreen.count("keybinds.customGfs")})"
        g.text(label, x + (w - g.width(label)) / 2, y + 3, Theme.TEXT, true)
    }
    override fun click(mx: Double, my: Double, button: Int): Boolean {
        if (!contains(mx, my)) return false
        val mc = Minecraft.getInstance()
        mc.gui.setScreen(ListEditorScreen.customGfs(mc.gui.screen()))
        return true
    }
}

// ── Mob picker: the id box, the mob's egg, and a button to the picker ───────

class MobPickerOpt(opt: OptionSpec) : Widget(opt) {
    override var w = 200
    override val h = 14
    private val btnW = 34
    private val field = TextField(Values.string(opt), onChange = { Values.set(opt, it) })

    private fun egg(): net.minecraft.world.item.ItemStack? {
        val id = Values.string(opt).trim().lowercase().let { if (":" in it) it else "minecraft:$it" }
        return MobPickerScreen.mobs.firstOrNull { it.id == id }?.egg
    }

    override fun render(g: Gfx, mx: Int, my: Int) {
        if (!field.focused && field.value != Values.string(opt)) field.set(Values.string(opt))
        val e = egg()
        val iconW = if (e != null) 18 else 0
        if (e != null) g.ctx.item(e, x, y - 1)
        field.x = x + iconW; field.y = y; field.w = w - btnW - 4 - iconW; field.h = h
        field.render(g, mx, my)
        val bx = x + w - btnW
        val over = mx >= bx && mx < x + w && my >= y && my < y + h
        g.rrect(bx, y, x + w, y + h, 3, if (over) Theme.ACCENT_MID else Theme.ACCENT_DARK)
        g.text("Pick", bx + (btnW - g.width("Pick")) / 2, y + 3, Theme.TEXT, true)
    }

    override fun click(mx: Double, my: Double, button: Int): Boolean {
        if (mx >= x + w - btnW && contains(mx, my)) {
            val mc = Minecraft.getInstance()
            mc.gui.setScreen(MobPickerScreen(mc.gui.screen(), Values.string(opt)) { Values.set(opt, it); field.set(it) })
            return true
        }
        return field.click(mx, my, button)
    }
    override fun drag(mx: Double, my: Double) = field.drag(mx)
    override fun key(e: KeyEvent) = field.key(e)
    override fun char(e: CharacterEvent) = field.char(e)
    override fun unfocus() { field.focused = false }
}

fun widgetFor(opt: OptionSpec): Widget? = when (opt.type) {
    "shortcuts" -> ShortcutsOpt(opt)
    "gfslist" -> GfsListOpt(opt)
    "mobpicker" -> MobPickerOpt(opt)
    "boolean" -> Toggle(opt)
    "slider" -> Slider(opt)
    "text" -> TextOpt(opt)
    "dropdown" -> Dropdown(opt)
    "keybind" -> Keybind(opt)
    "colour" -> ColourOpt(opt)
    "button" -> ButtonOpt(opt)
    else -> null
}
