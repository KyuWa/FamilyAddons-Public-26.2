package org.kyowa.familyaddons.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/**
 * Which config screen opens. Remembered across sessions; every screen has a
 * switch in its top bar, and /fagui style <n> sets it too.
 */
enum class GuiStyle(val label: String) {
    COMPACT("Compact"),
    PANELS("Panels");

    companion object {
        fun current(): GuiStyle = entries.getOrElse(Values.uiInt("style", 0)) { COMPACT }
        fun set(s: GuiStyle) = Values.setUi("style", s.ordinal)

        fun screen(parent: Screen?, search: String = ""): Screen = when (current()) {
            COMPACT -> FaConfigScreen(parent, search)
            PANELS -> PanelsScreen(parent, search)
        }

        /** Switch to [s] and reopen on it, keeping the parent. */
        fun switchTo(s: GuiStyle, parent: Screen?) {
            if (s == current()) return
            set(s)
            Minecraft.getInstance().gui.setScreen(screen(parent))
        }

        /**
         * A small dropdown for choosing the style, shared by the screens. [open] is
         * kept by the caller; draw it last so its list sits above everything.
         */
        fun drawPicker(g: Gfx, x: Int, y: Int, w: Int, open: Boolean, mx: Int, my: Int) {
            val over = mx >= x && mx < x + w && my >= y && my < y + 14
            g.rrect(x, y, x + w, y + 14, 3, if (over || open) Theme.TRACK_HOVER else Theme.TRACK)
            g.rborder(x, y, x + w, y + 14, 3, if (open) Theme.ACCENT else Theme.BORDER)
            g.text(g.clip("Style: " + current().label, w - 16), x + 4, y + 3, if (over || open) Theme.TEXT else Theme.TEXT_DIM)
            g.text(if (open) "\u25B4" else "\u25BE", x + w - 10, y + 3, Theme.TEXT_DIM)
            if (!open) return
            val ly = y + 16
            g.rrect(x, ly, x + w, ly + entries.size * 12 + 4, 3, Theme.CARD)
            g.rborder(x, ly, x + w, ly + entries.size * 12 + 4, 3, Theme.ACCENT_DIM)
            for ((i, s) in entries.withIndex()) {
                val ry = ly + 2 + i * 12
                val rowOver = mx >= x && mx < x + w && my >= ry && my < ry + 12
                if (rowOver) g.fill(x + 2, ry, x + w - 2, ry + 12, Theme.CARD_HOVER)
                if (s == current()) g.fill(x + 2, ry + 2, x + 4, ry + 10, Theme.ACCENT)
                g.text(s.label, x + 7, ry + 2, if (s == current()) Theme.TEXT else Theme.TEXT_DIM)
            }
        }

        /** Handles a click for the picker; true when it landed on the button or its list. */
        fun clickPicker(x: Int, y: Int, w: Int, open: Boolean, mx: Double, my: Double, parent: Screen?, setOpen: (Boolean) -> Unit): Boolean {
            if (mx >= x && mx < x + w && my >= y && my < y + 14) { setOpen(!open); return true }
            if (!open) return false
            val ly = y + 16
            if (mx >= x && mx < x + w && my >= ly && my < ly + entries.size * 12 + 4) {
                val i = ((my - ly - 2) / 12).toInt()
                setOpen(false)
                entries.getOrNull(i)?.let { switchTo(it, parent) }
                return true
            }
            setOpen(false)
            return false
        }
    }
}
