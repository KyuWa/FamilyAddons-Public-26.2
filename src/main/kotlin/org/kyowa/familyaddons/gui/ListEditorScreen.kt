package org.kyowa.familyaddons.gui

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

/**
 * A screen that edits a list of rows, each row a few columns (text, number,
 * key). Rows can be added and removed; the list is saved as it changes, as a
 * JSON array under [storageKey]. Two lists use it: command shortcuts and custom
 * GFS items. Esc or Back returns to the config screen.
 */
class ListEditorScreen(
    private val parent: Screen?,
    private val subtitle: String,
    private val storageKey: String,
    private val columns: List<Column>,
    private val emptyHint: String,
    private val footer: String,
    /** Column whose value is an item id: its icon is drawn when the id resolves. */
    private val itemColumn: String? = null,
) : Screen(Component.literal(subtitle)) {

    enum class Kind { TEXT, NUMBER, KEY }
    class Column(val key: String, val label: String, val kind: Kind, val weight: Int, val placeholder: String = "")

    class Row(val values: MutableMap<String, String>) {
        val fields = HashMap<String, TextField>()
        var listening = false
    }

    companion object {
        private val cache = HashMap<String, MutableList<Row>>()

        fun load(storageKey: String): MutableList<Row> = cache.getOrPut(storageKey) {
            val list = ArrayList<Row>()
            runCatching {
                for (e in JsonParser.parseString(Values.rawString(storageKey) ?: "[]").asJsonArray) {
                    val o = e.asJsonObject
                    list.add(Row(o.entrySet().associate { it.key to it.value.asString }.toMutableMap()))
                }
            }
            list
        }

        fun save(storageKey: String) {
            val arr = JsonArray()
            for (r in load(storageKey)) arr.add(JsonObject().apply { r.values.forEach { (k, v) -> addProperty(k, v) } })
            Values.setRaw(storageKey, Gson().toJson(arr))
        }

        fun count(storageKey: String) = load(storageKey).size

        fun shortcuts(parent: Screen?) = ListEditorScreen(parent, "command shortcuts", "utilities.commandShortcutList",
            listOf(Column("alias", "Alias", Kind.TEXT, 26, "/pw"), Column("command", "Runs", Kind.TEXT, 46, "/warp home"), Column("key", "Key", Kind.KEY, 18)),
            "No shortcuts yet. Add one below: the alias on the left, the command it runs on the right.",
            "Type /alias in chat to run its command; a key runs it without chat.")

        fun customGfs(parent: Screen?) = ListEditorScreen(parent, "custom GFS", "keybinds.customGfs",
            listOf(Column("item", "Item id", Kind.TEXT, 44, "ender_pearl"), Column("amount", "Amount", Kind.NUMBER, 16, "16"), Column("key", "Key", Kind.KEY, 24)),
            "No custom items yet. Add one below: the item's id, how many to keep, and the key that tops it up.",
            "Ids as in /gfs: ender_pearl, superboom_tnt, toxic_arrow_poison…", itemColumn = "item")
    }

    private val rows get() = load(storageKey)
    private fun save() = save(storageKey)
    private fun field(r: Row, c: Column): TextField = r.fields.getOrPut(c.key) {
        TextField(r.values[c.key] ?: "", numeric = c.kind == Kind.NUMBER, onChange = { r.values[c.key] = it; save() }).also { it.placeholder = c.placeholder }
    }

    private var winX = 0; private var winY = 0; private var winW = 0; private var winH = 0
    private val rowH = 20
    private var scroll = 0f; private var scrollTarget = 0f
    private var lastFrameMs = System.currentTimeMillis()
    private var maxScroll = 0
    private var draggingBar = false; private var dragBarOffset = 0
    private val barX get() = winX + winW - 7
    private val listY get() = winY + 46
    private val listH get() = winH - 46 - 30

    /** x and width of each column across the row. */
    private fun columnRects(): List<IntArray> {
        val x0 = winX + 10; val total = winW - 20 - 16   // 16 for the delete button
        val iconW = if (itemColumn != null) 18 else 0
        val avail = total - iconW - 6 * (columns.size - 1)
        val sumW = columns.sumOf { it.weight }
        var x = x0 + iconW
        return columns.map { c -> val w = avail * c.weight / sumW; val r = intArrayOf(x, w); x += w + 6; r }
    }

    private fun itemIcon(id: String): ItemStack? {
        val clean = id.trim().lowercase()
        if (clean.isEmpty()) return null
        val rl = Identifier.tryParse(if (":" in clean) clean else "minecraft:$clean") ?: return null
        val item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null) ?: return null
        return ItemStack(item)
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        val g = Gfx(ctx, font)
        val now = System.currentTimeMillis(); val dt = ((now - lastFrameMs).coerceIn(0, 100)) / 1000f; lastFrameMs = now
        winW = (width * 0.7).roundToInt().coerceIn(340, 520).coerceAtMost(width - 8)
        winH = (height * 0.8).roundToInt().coerceIn(180, 360).coerceAtMost(height - 8)
        winX = (width - winW) / 2; winY = (height - winH) / 2

        ctx.fill(0, 0, width, height, 0x90000000.toInt())
        g.rrect(winX, winY, winX + winW, winY + winH, 6, Theme.WINDOW)
        g.rborder(winX, winY, winX + winW, winY + winH, 6, Theme.BORDER)
        g.fill(winX + 1, winY + 24, winX + winW - 1, winY + 25, Theme.BORDER)
        g.bandText("FamilyAddons", winX + 10, winY + 8)
        g.text(subtitle, winX + 10 + g.width("FamilyAddons") + 5, winY + 8, Theme.TEXT_FAINT)

        val rects = columnRects()
        for ((i, c) in columns.withIndex()) g.text(c.label, rects[i][0], winY + 32, Theme.TEXT_DIM)

        val total = rows.size * rowH
        maxScroll = maxOf(0, total - listH)
        scrollTarget = scrollTarget.coerceIn(0f, maxScroll.toFloat())
        scroll += (scrollTarget - scroll) * minOf(1f, dt * 14)
        val off = scroll.roundToInt()

        g.clipped(winX + 1, listY, winX + winW - 1, listY + listH) {
            if (rows.isEmpty()) for ((i, l) in g.wrap(emptyHint, winW - 20).withIndex()) g.text(l, winX + 10, listY + 6 + i * g.lineHeight, Theme.TEXT_FAINT)
            for ((ri, r) in rows.withIndex()) {
                val y = listY + ri * rowH - off
                if (y + rowH < listY || y > listY + listH) continue
                if (itemColumn != null) itemIcon(r.values[itemColumn] ?: "")?.let { ctx.item(it, winX + 10, y + 1) }
                for ((i, c) in columns.withIndex()) {
                    val (x, w) = rects[i].let { it[0] to it[1] }
                    when (c.kind) {
                        Kind.TEXT, Kind.NUMBER -> { val f = field(r, c); f.x = x; f.y = y + 2; f.w = w; f.h = 14; f.render(g, mx, my) }
                        Kind.KEY -> {
                            val k = r.values[c.key]?.toIntOrNull() ?: GLFW.GLFW_KEY_UNKNOWN
                            val over = mx >= x && mx < x + w && my >= y + 2 && my < y + 16
                            g.rrect(x, y + 2, x + w, y + 16, 3, if (r.listening) Theme.ACCENT_DARK else if (over) Theme.TRACK_HOVER else Theme.TRACK)
                            g.rborder(x, y + 2, x + w, y + 16, 3, if (r.listening) Theme.ACCENT else Theme.BORDER)
                            val label = if (r.listening) "Press…" else if (k == GLFW.GLFW_KEY_UNKNOWN) "None" else InputConstants.Type.KEYSYM.getOrCreate(k).displayName.string
                            g.text(g.clip(label, w - 6), x + 4, y + 5, if (k == GLFW.GLFW_KEY_UNKNOWN && !r.listening) Theme.TEXT_FAINT else Theme.TEXT)
                        }
                    }
                }
                val dx = winX + winW - 10 - 12
                val overD = mx >= dx && mx < dx + 12 && my >= y + 2 && my < y + 16
                g.text("×", dx + 3, y + 5, if (overD) Theme.BAD else Theme.TEXT_FAINT)
            }
        }
        if (maxScroll > 0) {
            val (thumbY, thumbH) = thumb()
            g.rrect(barX, listY, barX + 4, listY + listH, 2, Theme.TRACK)
            val over = mx >= barX - 2 && mx < barX + 6 && my >= thumbY && my < thumbY + thumbH
            g.rrect(barX, thumbY, barX + 4, thumbY + thumbH, 2, if (draggingBar || over) Theme.ACCENT else Theme.ACCENT_MID)
        }

        val by = winY + winH - 22
        g.fill(winX + 1, by - 4, winX + winW - 1, by - 3, Theme.BORDER)
        button(g, mx, my, winX + 10, by, 60, "+ Add", Theme.ACCENT_DARK, Theme.ACCENT_MID)
        button(g, mx, my, winX + winW - 10 - 60, by, 60, "Back", Theme.TRACK, Theme.TRACK_HOVER)
        g.text(g.clip(footer, winW - 150), winX + 78, by + 4, Theme.TEXT_FAINT)
    }

    private fun button(g: Gfx, mx: Int, my: Int, x: Int, y: Int, w: Int, label: String, bg: Int, hover: Int) {
        val over = mx >= x && mx < x + w && my >= y && my < y + 16
        g.rrect(x, y, x + w, y + 16, 3, if (over) hover else bg)
        g.text(label, x + (w - g.width(label)) / 2, y + 4, Theme.TEXT, true)
    }

    private fun unfocusAll(except: TextField? = null) {
        for (r in rows) { r.fields.values.forEach { if (it !== except) it.focused = false }; r.listening = false }
    }

    private fun focusedField(): TextField? = rows.firstNotNullOfOrNull { r -> r.fields.values.firstOrNull { it.focused } }

    private fun thumb(): Pair<Int, Int> {
        val total = maxScroll + listH
        val thumbH = maxOf(16, listH * listH / maxOf(1, total))
        val thumbY = listY + if (maxScroll > 0) ((listH - thumbH) * scroll / maxScroll).roundToInt() else 0
        return thumbY to thumbH
    }

    private fun barDrag(my: Double) {
        val (_, thumbH) = thumb()
        scrollTarget = ((my - dragBarOffset - listY) / (listH - thumbH) * maxScroll).toFloat().coerceIn(0f, maxScroll.toFloat())
        scroll = scrollTarget
    }

    override fun mouseClicked(e: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = e.x(); val my = e.y()
        if (maxScroll > 0 && mx >= barX - 3 && mx < barX + 7 && my >= listY && my < listY + listH) {
            val (thumbY, thumbH) = thumb()
            unfocusAll()
            if (my >= thumbY && my < thumbY + thumbH) { draggingBar = true; dragBarOffset = (my - thumbY).toInt() }
            else { draggingBar = true; dragBarOffset = thumbH / 2; barDrag(my) }
            return true
        }
        val by = winY + winH - 22
        if (mx >= winX + 10 && mx < winX + 70 && my >= by && my < by + 16) {
            unfocusAll(); rows.add(Row(columns.associate { it.key to "" }.toMutableMap())); save(); scrollTarget = Float.MAX_VALUE; return true
        }
        if (mx >= winX + winW - 70 && mx < winX + winW - 10 && my >= by && my < by + 16) { onClose(); return true }
        if (my >= listY && my < listY + listH) {
            val rects = columnRects(); val off = scroll.roundToInt()
            for ((ri, r) in rows.withIndex()) {
                val y = listY + ri * rowH - off
                if (my < y + 2 || my >= y + 16) continue
                for ((i, c) in columns.withIndex()) {
                    val (x, w) = rects[i].let { it[0] to it[1] }
                    if (c.kind == Kind.KEY) { if (mx >= x && mx < x + w) { unfocusAll(); r.listening = true; return true } }
                    else { val f = field(r, c); if (f.click(mx, my, e.button())) { unfocusAll(f); return true } }
                }
                val dx = winX + winW - 10 - 12
                if (mx >= dx && mx < dx + 12) { unfocusAll(); rows.removeAt(ri); save(); return true }
            }
        }
        unfocusAll()
        return true
    }

    override fun mouseDragged(e: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (draggingBar) { barDrag(e.y()); return true }
        return focusedField()?.drag(e.x()) ?: false
    }
    override fun mouseReleased(e: MouseButtonEvent): Boolean { draggingBar = false; return true }
    override fun mouseScrolled(mx: Double, my: Double, h: Double, v: Double): Boolean {
        val amount = if (v != 0.0) v else h
        scrollTarget -= (amount * rowH).toFloat()
        return true
    }

    /** Tab moves to the next slot in the row, then on to the next row; shift+Tab goes back. */
    private fun tabTo(forward: Boolean): Boolean {
        var ri = rows.indexOfFirst { r -> r.listening || r.fields.values.any { it.focused } }
        if (ri < 0) { if (rows.isEmpty()) return false; ri = 0; focusSlot(rows[0], if (forward) 0 else columns.size - 1); return true }
        val r = rows[ri]
        var ci = columns.indexOfFirst { c -> if (c.kind == Kind.KEY) r.listening else r.fields[c.key]?.focused == true }
        ci += if (forward) 1 else -1
        if (ci >= columns.size) { ri++; ci = 0 } else if (ci < 0) { ri--; ci = columns.size - 1 }
        if (ri !in rows.indices) return true
        unfocusAll()
        focusSlot(rows[ri], ci)
        scrollTarget = (ri * rowH - listH / 2).toFloat()
        return true
    }

    private fun focusSlot(r: Row, ci: Int) {
        val c = columns[ci]
        if (c.kind == Kind.KEY) r.listening = true else field(r, c).focused = true
    }

    override fun keyPressed(e: KeyEvent): Boolean {
        if (e.key() == GLFW.GLFW_KEY_TAB) return tabTo(!e.hasShiftDown())
        rows.firstOrNull { it.listening }?.let { r ->
            val col = columns.first { it.kind == Kind.KEY }
            r.values[col.key] = (if (e.key() == GLFW.GLFW_KEY_ESCAPE) GLFW.GLFW_KEY_UNKNOWN else e.key()).toString()
            r.listening = false; save(); return true
        }
        focusedField()?.let { return it.key(e) }
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
        return super.keyPressed(e)
    }

    override fun charTyped(e: CharacterEvent) = focusedField()?.char(e) ?: false
    override fun onClose() { unfocusAll(); save(); Minecraft.getInstance().gui.setScreen(parent) }
    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = false
}
