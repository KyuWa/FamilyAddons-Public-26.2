package org.kyowa.familyaddons.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Panels-style look: one draggable panel per category laid out across the
 * screen, each a column of modules (the config's sub categories). Left-click a
 * module to flip its Enable, right-click (or the arrow) to drop its settings
 * down under it; settings are stacked full-width inside the panel. Panels
 * collapse from their header, remember where they were dragged, and scroll on
 * their own when taller than the screen. Search at the bottom filters modules.
 */
class PanelsScreen(private val parent: Screen?, initialSearch: String) : Screen(Component.literal("FamilyAddons")) {

    // ── model ─────────────────────────────────────────────────────────
    /** A module: a sub category, or the loose options of a category under its own name. */
    private class Module(val key: String, val name: String, val options: List<OptionSpec>, val group: String? = null, val category: String = "", val categoryKey: String = "") {
        /** The module's on/off, when it has one (an Enable-like boolean first). */
        val toggle: OptionSpec? = options.firstOrNull { it.type == "boolean" && (it.name.startsWith("Enable", true) || it.name == options.first().name && it.type == "boolean") }
        var open = false
        var anim = 0f
    }
    private class Panel(val key: String, val name: String, val modules: List<Module>) {
        var x = 0; var y = 0
        var collapsed = false
        var scroll = 0f
        var bodyH = 0
        var dragging = false; var dragDx = 0; var dragDy = 0
        var anim = 1f
    }

    /** What sits after the name in the top bar. */
    private val VERSION_LABEL = "v" + org.kyowa.familyaddons.FamilyAddons.VERSION
    private val PW = 122
    private val HEADER_H = 18
    /** Everything inside a panel is drawn at this scale; the body is laid out in local units. */
    private val FS = 0.8f
    /** Panel body width in local units. */
    private val LW = (PW / FS).roundToInt()
    private val ROW_H = 16
    private val panels: List<Panel>
    private val widgets = HashMap<String, Widget>()
    private val search = TextField(initialSearch)
    private var overlay: Widget? = null
    private var focus: Widget? = null
    private var lastFrameMs = System.currentTimeMillis()
    private val hoverAnim = HashMap<String, Float>()
    private var tooltip: Triple<String, String, IntArray>? = null
    private var hoverKey: String? = null
    private var hoverSince = 0L
    private val HOVER_MS = 500
    private val openedAt = System.currentTimeMillis()
    private var stylePickerOpen = false
    // the search box can be dragged by its grip; where it sits is remembered
    private var searchX = Int.MIN_VALUE; private var searchY = Int.MIN_VALUE
    private var searchDragging = false; private var searchDx = 0; private var searchDy = 0
    private val GRIP_W = 10

    /** Panels and the categories folded into each; a category listed nowhere gets its own. */
    private val layout = listOf(
        "General" to listOf("general"),
        "Utilities" to listOf("utilities", "chatFilters", "translator", "party", "keybinds", "highlight"),
        "SkyBlock" to listOf("mining", "crimsonIsle", "dungeons", "foraging", "safari", "waypoints"),
        "Kuudra" to listOf("kuudra"),
        "Disguise & Names" to listOf("playerDisguise", "nameChanger"),
        "Family Storage" to listOf("storage"),
        "Contact" to listOf("contact"),

    )

    private fun modulesOf(cat: CategorySpec, grouped: Boolean): List<Module> {
        val headers = cat.options.filter { it.type == "accordion" }
        val byId = headers.associateBy { it.id }
        val mods = ArrayList<Module>()
        val group = if (grouped) cat.name else null
        val loose = cat.options.filter { it.type != "accordion" && (it.accordion == null || !byId.containsKey(it.accordion)) }
        if (loose.isNotEmpty()) mods.add(Module(cat.key + ".*", cat.name, loose, group, cat.name, cat.key))
        for (h in headers) mods.add(Module(h.key, h.name, cat.options.filter { it.type != "accordion" && it.accordion == h.id }, group, cat.name, cat.key))
        return mods
    }

    init {
        val byKey = ConfigSpec.categories.associateBy { it.key }
        val placed = HashSet<String>()
        val out = ArrayList<Panel>()
        for ((name, keys) in layout) {
            val cats = keys.mapNotNull { byKey[it] }
            if (cats.isEmpty()) continue
            placed.addAll(cats.map { it.key })
            out.add(Panel(keys.first(), name, cats.flatMap { modulesOf(it, cats.size > 1) }))
        }
        for (cat in ConfigSpec.categories) if (cat.key !in placed) out.add(Panel(cat.key, cat.name, modulesOf(cat, false)))
        panels = out
        // default layout: rows of panels across the screen, saved positions win
        val mc = Minecraft.getInstance()
        val sw = mc.window.guiScaledWidth
        val perRow = maxOf(1, (sw - 16) / (PW + 6))
        var rowY = 28; var rowTallest = 0
        for ((i, p) in panels.withIndex()) {
            if (i > 0 && i % perRow == 0) { rowY += rowTallest + 10; rowTallest = 0 }
            val sx = Values.uiInt("panels.${p.key}.x", Int.MIN_VALUE)
            val sy = Values.uiInt("panels.${p.key}.y", Int.MIN_VALUE)
            if (sx != Int.MIN_VALUE && sy != Int.MIN_VALUE) { p.x = sx; p.y = sy }
            else { p.x = 8 + (i % perRow) * (PW + 6); p.y = rowY }
            rowTallest = maxOf(rowTallest, minOf(170, HEADER_H + 1 + ((p.modules.size * ROW_H + p.modules.mapNotNull { it.group }.distinct().size * 12) * FS).roundToInt() + 8))
            p.y = p.y.coerceAtMost(maxOf(28, mc.window.guiScaledHeight - 60))
            p.collapsed = Values.uiInt("panels.${p.key}.c", 0) == 1
        }
    }

    private fun widget(opt: OptionSpec): Widget? = widgets.getOrPut(opt.key) { widgetFor(opt) ?: return null }

    private fun anim(key: String, on: Boolean, dt: Float, speed: Float = 16f): Float {
        val cur = hoverAnim[key] ?: 0f
        val target = if (on) 1f else 0f
        var next = cur + (target - cur) * minOf(1f, dt * speed)
        if (abs(target - next) < 0.01f) next = target
        hoverAnim[key] = next
        return next
    }

    /** Extra words a category answers to in search, beyond its name. */
    private val searchAliases = mapOf(
        "highlight" to "bestiary be esp",
        "safari" to "critters critter",
        "storage" to "ender chest backpack ec",
        "nameChanger" to "nick nickname name",
        "playerDisguise" to "disguise mob",
    )

    private fun categoryMatches(m: Module, q: String): Boolean =
        m.category.lowercase().contains(q) || (searchAliases[m.categoryKey]?.split(' ')?.any { it.startsWith(q) || q.startsWith(it) && it.length >= 2 } ?: false)

    private fun visibleModules(p: Panel): List<Module> {
        val q = search.value.trim().lowercase()
        if (q.isEmpty()) return p.modules
        // a module matches by its own name, by an option inside it, or by the sub header
        // (category) it sits under, so "mining" or "crimson isle" pull up that whole block
        return p.modules.filter { m -> m.name.lowercase().contains(q) || categoryMatches(m, q) || m.options.any { it.name.lowercase().contains(q) } }
    }

    /** Height of one option row inside an open module. */
    private fun optionH(o: OptionSpec, w: Widget?): Int = when (o.type) {
        "boolean", "colour", "button", "shortcuts", "gfslist" -> ROW_H
        else -> 11 + (w?.h ?: 14) + 4      // name line, then the control full-width
    }

    /**
     * The options a module shows: all of them, except while a search matched only
     * something inside it, when just the matching options are listed.
     */
    private fun visibleOptions(m: Module): List<OptionSpec> {
        val q = search.value.trim().lowercase()
        if (q.isEmpty() || m.name.lowercase().contains(q) || categoryMatches(m, q)) return m.options
        return m.options.filter { it.name.lowercase().contains(q) }
    }

    private fun moduleBodyH(m: Module): Int = visibleOptions(m).sumOf { optionH(it, widget(it)) } + 3

    // ── render ────────────────────────────────────────────────────────

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        val g = Gfx(ctx, font)
        val now = System.currentTimeMillis()
        val dt = ((now - lastFrameMs).coerceIn(0, 100)) / 1000f
        lastFrameMs = now
        tooltip = null
        val raw = ((now - openedAt).toFloat() / 200).coerceIn(0f, 1f)
        val t = 1f - (1f - raw) * (1f - raw)
        ctx.fill(0, 0, width, height, ((0x80 * t).roundToInt() shl 24))

        // title and style switch, top left
        g.bandText("FamilyAddons", 8, 8)
        g.text(VERSION_LABEL, 8 + g.width("FamilyAddons") + 5, 8, Theme.TEXT_FAINT)
        val sx = 8 + g.width("FamilyAddons") + 5 + g.width(VERSION_LABEL) + 8
        val rx = sx + 100
        val overR = mx >= rx && mx < rx + 70 && my >= 5 && my < 19
        g.rrect(rx, 5, rx + 70, 19, 3, if (overR) Theme.TRACK_HOVER else Theme.TRACK)
        g.text("Reset layout", rx + 4, 8, if (overR) Theme.TEXT else Theme.TEXT_DIM)

        for (p in panels) renderPanel(g, p, mx, my, dt, t)

        // search: top right unless it was dragged somewhere else; a grip on its left moves it
        if (searchX == Int.MIN_VALUE) { searchX = Values.uiInt("panels.search.x", width - 8 - 150); searchY = Values.uiInt("panels.search.y", 5) }
        searchX = searchX.coerceIn(GRIP_W, width - 150); searchY = searchY.coerceIn(0, height - 14)
        search.w = 150; search.h = 14; search.x = searchX; search.y = searchY; search.placeholder = "Search modules…"
        val gx = searchX - GRIP_W
        val overGrip = mx >= gx && mx < searchX && my >= searchY && my < searchY + 14
        g.rrect(gx, searchY, searchX + 3, searchY + 14, 3, if (overGrip || searchDragging) Theme.TRACK_HOVER else Theme.TRACK)
        for (i in 0 until 3) g.fill(gx + 3, searchY + 4 + i * 3, gx + 7, searchY + 5 + i * 3, if (overGrip || searchDragging) Theme.ACCENT else Theme.TEXT_FAINT)
        search.render(g, mx, my)

        // the description waits a second on the same row before it shows
        val key = tooltip?.first
        if (key != hoverKey) { hoverKey = key; hoverSince = now }
        val ov = overlay
        if (ov != null && ov.wantsOverlay) { ctx.nextStratum(); ov.renderOverlay(g, mx, my, width, height) }
        else {
            if (ov != null) overlay = null
            if (now - hoverSince >= HOVER_MS) tooltip?.let { (name, desc, at) -> ctx.nextStratum(); renderTooltip(g, name, desc, at[0], at[1]) }
        }
        ctx.nextStratum()
        GuiStyle.drawPicker(g, 8 + g.width("FamilyAddons") + 5 + g.width(VERSION_LABEL) + 8, 5, 94, stylePickerOpen, mx, my)
    }

    private fun renderTooltip(g: Gfx, name: String, desc: String, mx: Int, my: Int) {
        val w = 200
        val lines = g.wrap(desc, w - 12)
        val h = 6 + g.lineHeight + 3 + lines.size * g.lineHeight + 6
        var x = mx + 10; var y = my + 10
        if (x + w > width - 4) x = mx - w - 6
        if (y + h > height - 4) y = height - 4 - h
        g.rrect(x, y, x + w, y + h, 4, 0xF8100D17.toInt())
        g.rborder(x, y, x + w, y + h, 4, Theme.ACCENT_DIM)
        g.text(name, x + 6, y + 6, Theme.ACCENT)
        var ly = y + 6 + g.lineHeight + 3
        for (l in lines) { g.text(l, x + 6, ly, Theme.TEXT); ly += g.lineHeight }
    }

    private fun renderPanel(g: Gfx, p: Panel, mx: Int, my: Int, dt: Float, openT: Float) {
        val x = p.x; val y = p.y
        // header
        val overHead = mx >= x && mx < x + PW && my >= y && my < y + HEADER_H
        g.rrect(x, y, x + PW, y + HEADER_H + 4, 5, Theme.CARD)
        g.fill(x, y + HEADER_H, x + PW, y + HEADER_H + 1, Theme.ACCENT_MID)
        if (p.key == "Contact") g.bandText(p.name, x + (PW - g.width(p.name)) / 2, y + 5, shadow = false)
        else g.text(p.name, x + (PW - g.width(p.name)) / 2, y + 5, if (overHead) Theme.TEXT else Theme.TEXT_DIM)
        val overArrow = overHead && mx >= x + PW - 14
        g.text(if (p.collapsed) "▸" else "▾", x + PW - 11, y + 5, if (overArrow) Theme.ACCENT else Theme.TEXT_FAINT)
        // while searching, a collapsed panel opens to show its hits (its saved state is untouched)
        // and one with nothing matching folds away
        val mods = visibleModules(p)
        val searching = search.value.isNotBlank()
        val showBody = if (searching) mods.isNotEmpty() else !p.collapsed
        p.anim = anim("panel:" + p.key, showBody, dt, 14f)
        if (p.anim <= 0.01f) { p.bodyH = 0; return }

        // body: modules and their open settings
        var fullH = 0   // local units
        var lg: String? = null
        val q = search.value.trim().lowercase()
        for (m in mods) {
            if (m.group != null && m.group != lg) { fullH += 12; lg = m.group }
            // a module whose option matched the search drops open to show it; its own
            // open/closed choice is kept for when the search is cleared
            val hitInside = searching && !m.name.lowercase().contains(q) && !categoryMatches(m, q) && m.options.any { it.name.lowercase().contains(q) }
            m.anim = anim("mod:" + m.key, m.open || hitInside, dt, 14f)
            fullH += ROW_H + (moduleBodyH(m) * m.anim).roundToInt()
        }
        val maxH = ((height - (y + HEADER_H + 1) - 8) / FS).roundToInt()      // local
        val shownL = (minOf(fullH, maxOf(50, maxH)) * p.anim).roundToInt()    // local
        val shownH = (shownL * FS).roundToInt()                                 // screen
        p.bodyH = shownH
        val maxScroll = maxOf(0, fullH - shownL)
        p.scroll = p.scroll.coerceIn(0f, maxScroll.toFloat())
        val by = y + HEADER_H + 1
        g.rrect(x, by - 4, x + PW, by + shownH + 4, 5, Theme.PANEL)
        g.fill(x, by - 4, x + PW, by, Theme.PANEL)
        // mouse in local units
        val lmx = ((mx - x) / FS).roundToInt(); val lmy = ((my - by) / FS).roundToInt()
        val inBody = mx >= x && mx < x + PW && my >= by && my < by + shownH && overlay == null

        g.clipped(x, by, x + PW, by + shownH) {
            val pose = g.ctx.pose()
            pose.pushMatrix(); pose.translate(x.toFloat(), by.toFloat()); pose.scale(FS, FS)
            var cy = -p.scroll.roundToInt()
            var lastGroup: String? = null
            for (m in mods) {
                if (m.group != null && m.group != lastGroup) {
                    g.text("§l" + m.group, 7, cy + 3, Theme.ACCENT_MID)
                    g.fill(10 + g.width("§l" + m.group), cy + 7, LW - 7, cy + 8, Theme.BORDER)
                    cy += 12; lastGroup = m.group
                }
                val rowOver = inBody && lmy >= cy && lmy < cy + ROW_H
                val a = anim("row:" + m.key, rowOver, dt)
                if (a > 0.01f) g.fill(0, cy, LW, cy + ROW_H, Theme.lerp(Theme.PANEL, Theme.CARD_HOVER, a.toDouble()))
                val on = m.toggle?.let { Values.bool(it) } ?: false
                val onA = anim("on:" + m.key, on, dt, 10f)
                val nameCol = Theme.lerp(if (rowOver) Theme.TEXT else Theme.TEXT_DIM, Theme.ACCENT, onA.toDouble())
                g.text(g.clip(m.name, LW - 24), 7, cy + 4, nameCol)
                g.text(if (m.open) "▾" else "▸", LW - 13, cy + 4, Theme.TEXT_FAINT)
                if (rowOver && m.toggle != null) tooltip = Triple(m.name, m.toggle.desc, intArrayOf(mx, my))
                cy += ROW_H
                if (m.anim > 0.01f) {
                    val bodyH = moduleBodyH(m)
                    val show = (bodyH * m.anim).roundToInt()
                    g.fill(0, cy, LW, cy + show, Theme.withAlpha(Theme.WINDOW, 0x60))
                    g.ctx.enableScissor(0, cy, LW, cy + show)
                    var oy = cy + 2
                    for (o in visibleOptions(m)) {
                        val w = widget(o)
                        val h = optionH(o, w)
                        val inline = o.type == "boolean" || o.type == "colour" || o.type == "button" || o.type == "shortcuts" || o.type == "gfslist"
                        val optOver = inBody && lmy >= oy && lmy < oy + h
                        if (w != null) { w.ox = x; w.oy = by; w.scale = FS }
                        if (inline) {
                            g.text(g.clip(o.name, LW - 14 - (w?.w ?: 0) - 4), 9, oy + 4, Theme.TEXT)
                            if (w != null) { w.x = LW - 8 - w.w; w.y = oy + (h - w.h) / 2; w.render(g, lmx, lmy) }
                        } else {
                            g.text(g.clip(o.name, LW - 16), 9, oy + 1, Theme.TEXT)
                            if (w != null) {
                                when (w) { is Slider -> w.w = LW - 18; is TextOpt -> w.w = LW - 18; is Dropdown -> w.w = LW - 18; is Keybind -> w.w = LW - 18; is MobPickerOpt -> w.w = LW - 18; else -> {} }
                                w.x = 9; w.y = oy + 11; w.render(g, lmx, lmy)
                            }
                        }
                        if (optOver && o.desc.isNotBlank() && lmx < 9 + g.width(o.name)) tooltip = Triple(o.name, o.desc, intArrayOf(mx, my))
                        oy += h
                    }
                    g.ctx.disableScissor()
                    cy += show
                }
            }
            pose.popMatrix()
        }
        if (maxScroll > 0) {
            val thumbH = maxOf(10, shownH * shownL / fullH)
            val thumbY = by + ((shownH - thumbH) * p.scroll / maxScroll).roundToInt()
            g.rrect(x + PW - 3, thumbY, x + PW - 1, thumbY + thumbH, 1, Theme.ACCENT_MID)
        }
    }

    // ── input ─────────────────────────────────────────────────────────

    private fun setFocus(w: Widget?) { if (focus !== w) { focus?.unfocus(); focus = w } }

    /** Back to the grid, search box back to the top right; what is open or collapsed stays as it was. */
    private fun resetLayout() {
        searchX = width - 8 - 150; searchY = 5
        Values.setUi("panels.search.x", searchX); Values.setUi("panels.search.y", searchY)
        val perRow = maxOf(1, (width - 16) / (PW + 6))
        var rowY = 28; var rowTallest = 0
        for ((i, p) in panels.withIndex()) {
            if (i > 0 && i % perRow == 0) { rowY += rowTallest + 10; rowTallest = 0 }
            p.x = 8 + (i % perRow) * (PW + 6); p.y = minOf(rowY, maxOf(28, height - 60))
            rowTallest = maxOf(rowTallest, minOf(170, HEADER_H + 1 + ((p.modules.size * ROW_H + p.modules.mapNotNull { it.group }.distinct().size * 12) * FS).roundToInt() + 8))
            savePanel(p)
        }
    }

    private fun savePanel(p: Panel) {
        Values.setUi("panels.${p.key}.x", p.x); Values.setUi("panels.${p.key}.y", p.y); Values.setUi("panels.${p.key}.c", if (p.collapsed) 1 else 0)
    }

    override fun mouseClicked(e: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = e.x(); val my = e.y(); val b = e.button()
        overlay?.let { ov ->
            val inside = ov.overlayClick(mx, my, b)
            if (!ov.wantsOverlay) overlay = null
            if (inside) { setFocus(ov); return true }
        }
        if (mx >= searchX - GRIP_W && mx < searchX && my >= searchY && my < searchY + 14 && b == 0) {
            searchDragging = true; searchDx = (mx - searchX).toInt(); searchDy = (my - searchY).toInt(); setFocus(null); return true
        }
        if (search.click(mx, my, b)) { setFocus(null); return true }
        val sx = 8 + font.width("FamilyAddons") + 5 + font.width(VERSION_LABEL) + 8
        if (GuiStyle.clickPicker(sx, 5, 94, stylePickerOpen, mx, my, parent) { stylePickerOpen = it }) { setFocus(null); return true }
        if (mx >= sx + 100 && mx < sx + 170 && my >= 5 && my < 19) { resetLayout(); return true }

        // panels, topmost first (last drawn is on top)
        for (p in panels.asReversed()) {
            val x = p.x; val y = p.y
            if (mx >= x && mx < x + PW && my >= y && my < y + HEADER_H) {
                if (b != 0 || mx >= x + PW - 14) { p.collapsed = !p.collapsed; savePanel(p); setFocus(null); return true }
                else { p.dragging = true; p.dragDx = (mx - x).toInt(); p.dragDy = (my - y).toInt() }
                setFocus(null); return true
            }
            val by = y + HEADER_H + 1
            if (p.bodyH > 0 && mx >= x && mx < x + PW && my >= by && my < by + p.bodyH) {
                val lmx = (mx - x) / FS; val lmy = (my - by) / FS
                var cy = -p.scroll.roundToInt()
                var lg: String? = null
                for (m in visibleModules(p)) {
                    if (m.group != null && m.group != lg) { cy += 12; lg = m.group }
                    if (lmy >= cy && lmy < cy + ROW_H) {
                        if (b == 1 || lmx >= LW - 18 || m.toggle == null) m.open = !m.open
                        else Values.set(m.toggle, !Values.bool(m.toggle))
                        setFocus(null); return true
                    }
                    cy += ROW_H
                    if (m.anim > 0.01f) {
                        val show = (moduleBodyH(m) * m.anim).roundToInt()
                        if (lmy >= cy && lmy < cy + show && m.anim > 0.99f) {
                            var oy = cy + 2
                            for (o in visibleOptions(m)) {
                                val w = widget(o); val h = optionH(o, w)
                                if (lmy >= oy && lmy < oy + h) {
                                    if (w != null && w.click(lmx, lmy, b)) { setFocus(w); if (w.wantsOverlay) overlay = w; return true }
                                    if (w is Toggle && b == 0) { Values.set(o, !Values.bool(o)); setFocus(null); return true }
                                    setFocus(null); return true
                                }
                                oy += h
                            }
                        }
                        cy += show
                    }
                }
                setFocus(null); return true
            }
        }
        setFocus(null)
        return true
    }

    override fun mouseDragged(e: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mx = e.x(); val my = e.y()
        overlay?.let { if (it.overlayDrag(mx, my)) return true }
        if (searchDragging) { searchX = (mx - searchDx).toInt(); searchY = (my - searchDy).toInt(); return true }
        panels.firstOrNull { it.dragging }?.let { p ->
            p.x = (mx - p.dragDx).toInt().coerceIn(0, width - PW); p.y = (my - p.dragDy).toInt().coerceIn(0, height - HEADER_H); return true
        }
        if (search.focused) return search.drag(mx)
        focus?.let { if (it.drag((mx - it.ox) / it.scale, (my - it.oy) / it.scale)) return true }
        return false
    }

    override fun mouseReleased(e: MouseButtonEvent): Boolean {
        panels.filter { it.dragging }.forEach { it.dragging = false; savePanel(it) }
        if (searchDragging) { searchDragging = false; Values.setUi("panels.search.x", searchX); Values.setUi("panels.search.y", searchY) }
        overlay?.release(); focus?.release(); widgets.values.forEach { it.release() }
        return true
    }

    override fun mouseScrolled(mx: Double, my: Double, h: Double, v: Double): Boolean {
        val amount = if (v != 0.0) v else h
        overlay?.let { if (it.overlayScroll(mx, my, amount)) return true }
        for (p in panels.asReversed()) {
            val by = p.y + HEADER_H + 1
            if (mx >= p.x && mx < p.x + PW && my >= by && my < by + p.bodyH) {
                p.scroll -= (amount * 20).toFloat(); return true
            }
        }
        return false
    }

    override fun keyPressed(e: KeyEvent): Boolean {
        if (search.focused) { if (e.key() == GLFW.GLFW_KEY_ESCAPE) { search.focused = false; return true }; return search.key(e) }
        overlay?.let { if (it.key(e)) return true }
        focus?.let { if (it.key(e)) return true }
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) { if (overlay != null) { overlay?.closeOverlay(); overlay = null; return true }; onClose(); return true }
        if (e.key() == GLFW.GLFW_KEY_F && e.hasControlDown()) { search.focused = true; return true }
        return super.keyPressed(e)
    }

    override fun charTyped(e: CharacterEvent): Boolean {
        if (search.focused) return search.char(e)
        overlay?.let { if (it.char(e)) return true }
        focus?.let { if (it.char(e)) return true }
        return false
    }

    override fun onClose() { focus?.unfocus(); search.focused = false; Values.save(); Minecraft.getInstance().gui.setScreen(parent) }
    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = false
}
