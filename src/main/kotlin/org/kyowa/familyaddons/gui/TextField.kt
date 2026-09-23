package org.kyowa.familyaddons.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW

/**
 * A text box that behaves like one: the whole value is visible when it fits and
 * scrolls to keep the caret in view when it does not, click places the caret,
 * shift+arrows / double-click select, ctrl+A/C/V/X work, Home/End work. Numeric
 * mode only lets a number be typed. [onCommit] runs on Enter or focus loss.
 */
class TextField(
    var value: String,
    private val numeric: Boolean = false,
    private val onChange: (String) -> Unit = {},
    private val onCommit: (String) -> Unit = {},
) {
    var x = 0; var y = 0; var w = 60; var h = 14
    var focused = false
        set(v) { if (field && !v) onCommit(value); field = v; if (!v) sel = caret }
    private var caret = value.length
    private var sel = caret            // selection anchor; == caret when nothing selected
    private var scroll = 0             // pixels of text hidden on the left
    private var lastClickMs = 0L
    var placeholder = ""

    private val font get() = Minecraft.getInstance().font
    private val padX = 4

    fun set(v: String) { value = v; caret = v.length; sel = caret; ensureVisible() }

    fun contains(mx: Double, my: Double) = mx >= x && mx < x + w && my >= y && my < y + h

    fun render(g: Gfx, mx: Int, my: Int) {
        val hover = contains(mx.toDouble(), my.toDouble())
        g.rrect(x, y, x + w, y + h, 3, if (focused) Theme.TRACK_HOVER else if (hover) Theme.TRACK_HOVER else Theme.TRACK)
        g.rborder(x, y, x + w, y + h, 3, if (focused) Theme.ACCENT else Theme.BORDER)
        val innerW = w - padX * 2
        val ty = y + (h - g.lineHeight) / 2 + 1
        ensureVisible()
        g.clipped(x + padX, y, x + w - padX, y + h) {
            val tx = x + padX - scroll
            if (value.isEmpty() && !focused && placeholder.isNotEmpty()) {
                g.text(placeholder, tx, ty, Theme.TEXT_FAINT)
            } else {
                // selection first, text over it
                if (focused && sel != caret) {
                    val a = minOf(sel, caret); val b = maxOf(sel, caret)
                    val sx1 = tx + font.width(value.substring(0, a))
                    val sx2 = tx + font.width(value.substring(0, b))
                    g.fill(sx1, ty - 1, sx2, ty + g.lineHeight, Theme.withAlpha(Theme.ACCENT, 0x70))
                }
                g.text(value, tx, ty, Theme.TEXT)
                if (focused && (System.currentTimeMillis() / 500) % 2 == 0L) {
                    val cx = tx + font.width(value.substring(0, caret))
                    g.fill(cx, ty - 1, cx + 1, ty + g.lineHeight, Theme.ACCENT)
                }
            }
        }
        if (innerW < font.width(value) && scroll > 0) g.fill(x + padX, y + 2, x + padX + 1, y + h - 2, Theme.TEXT_FAINT)
    }

    private fun ensureVisible() {
        val innerW = w - padX * 2
        val cx = font.width(value.substring(0, caret.coerceIn(0, value.length)))
        if (cx - scroll > innerW) scroll = cx - innerW
        if (cx - scroll < 0) scroll = cx
        val total = font.width(value)
        if (total - scroll < innerW) scroll = maxOf(0, total - innerW)
    }

    fun click(mx: Double, my: Double, button: Int): Boolean {
        if (!contains(mx, my)) { if (focused) focused = false; return false }
        if (button != 0) return true
        focused = true
        val now = System.currentTimeMillis()
        val rel = (mx - (x + padX) + scroll).toInt()
        caret = indexAt(rel)
        if (now - lastClickMs < 300) { sel = 0; caret = value.length } else sel = caret
        lastClickMs = now
        return true
    }

    fun drag(mx: Double): Boolean {
        if (!focused) return false
        caret = indexAt((mx - (x + padX) + scroll).toInt())
        return true
    }

    private fun indexAt(px: Int): Int {
        var acc = 0
        for (i in value.indices) {
            val cw = font.width(value[i].toString())
            if (px < acc + cw / 2) return i
            acc += cw
        }
        return value.length
    }

    private fun deleteSelection(): Boolean {
        if (sel == caret) return false
        val a = minOf(sel, caret); val b = maxOf(sel, caret)
        value = value.removeRange(a, b); caret = a; sel = a
        return true
    }

    private fun insert(s: String) {
        val clean = if (numeric) s.filter { it.isDigit() || it == '.' || it == '-' } else s.filter { it >= ' ' }
        if (clean.isEmpty()) return
        deleteSelection()
        value = value.substring(0, caret) + clean + value.substring(caret)
        caret += clean.length; sel = caret
        onChange(value)
    }

    fun key(e: KeyEvent): Boolean {
        if (!focused) return false
        val mc = Minecraft.getInstance()
        when {
            e.isSelectAll -> { sel = 0; caret = value.length }
            e.isCopy -> if (sel != caret) mc.keyboardHandler.clipboard = value.substring(minOf(sel, caret), maxOf(sel, caret))
            e.isCut -> if (sel != caret) { mc.keyboardHandler.clipboard = value.substring(minOf(sel, caret), maxOf(sel, caret)); deleteSelection(); onChange(value) }
            e.isPaste -> insert(mc.keyboardHandler.clipboard)
            e.key() == GLFW.GLFW_KEY_BACKSPACE -> {
                if (!deleteSelection() && caret > 0) { value = value.removeRange(caret - 1, caret); caret--; sel = caret }
                onChange(value)
            }
            e.key() == GLFW.GLFW_KEY_DELETE -> {
                if (!deleteSelection() && caret < value.length) { value = value.removeRange(caret, caret + 1) }
                onChange(value)
            }
            e.key() == GLFW.GLFW_KEY_LEFT -> { if (caret > 0) caret--; if (!e.hasShiftDown()) sel = caret }
            e.key() == GLFW.GLFW_KEY_RIGHT -> { if (caret < value.length) caret++; if (!e.hasShiftDown()) sel = caret }
            e.key() == GLFW.GLFW_KEY_HOME -> { caret = 0; if (!e.hasShiftDown()) sel = caret }
            e.key() == GLFW.GLFW_KEY_END -> { caret = value.length; if (!e.hasShiftDown()) sel = caret }
            e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER -> focused = false
            e.key() == GLFW.GLFW_KEY_ESCAPE -> focused = false
            else -> return false
        }
        ensureVisible()
        return true
    }

    fun char(e: CharacterEvent): Boolean {
        if (!focused) return false
        insert(e.codepointAsString())
        ensureVisible()
        return true
    }
}
