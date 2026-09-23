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
 * FamilyAddons' config screen, drawn by hand: a window with a top bar (title,
 * search, close), a category sidebar on the left, and on the right the options
 * of the chosen category as tight rows with a divider between them, the
 * description in a hover tooltip. Accordions from the config are collapsible
 * groups. Search looks through every category at once. Scrolling is smooth,
 * the scrollbar can be dragged, and every value is written the moment it
 * changes.
 */
class FaConfigScreen(private val parent: Screen?, initialSearch: String) : Screen(Component.literal("FamilyAddons")) {

    /** What sits after the name in the top bar. */
    private val VERSION_LABEL = "v" + org.kyowa.familyaddons.FamilyAddons.VERSION

    // ── layout ────────────────────────────────────────────────────────
    private var winX = 0; private var winY = 0; private var winW = 0; private var winH = 0
    private val topH = 24
    private val pad = 6
    private val cardPad = 6
    private val radius = 4
    private val resetLane = 12          // room kept free for the ↺ on every card
    private val sideW = 112
    private val headerH get() = topH
    private val contentX get() = winX + sideW + pad
    private val contentY get() = winY + headerH + pad
    private val contentW get() = winW - sideW - pad * 2 - 8      // 8 = scrollbar lane
    private val contentH get() = winH - headerH - pad * 2
    private val scrollbarX get() = contentX + contentW + 3

    // ── state ─────────────────────────────────────────────────────────
    private var selected: CategorySpec = ConfigSpec.categories.firstOrNull { it.key == Session.category } ?: ConfigSpec.categories.first()
    private val widgets = HashMap<String, Widget>()
    private val search = TextField(initialSearch, onChange = { scrollTarget = 0f })
    private var scroll = 0f
    private var scrollTarget = 0f
    private var sideScroll = 0f
    private var sideScrollTarget = 0f
    private var draggingBar = false
    private var dragBarOffset = 0
    private var overlay: Widget? = null
    private var focus: Widget? = null
    private val hoverAnim = HashMap<String, Float>()
    private var lastFrameMs = System.currentTimeMillis()
    private var tooltip: Pair<OptionSpec, IntArray>? = null   // option + [x, y] to draw it at
    private var stylePickerOpen = false
    private val openedAt = System.currentTimeMillis()
    private val OPEN_MS = 220

    /** One drawn thing in the content column. */
    private class Entry(val kind: Int, val opt: OptionSpec?, val label: String, val x: Int, val y: Int, val w: Int, val h: Int, val widget: Widget?, val descLines: List<net.minecraft.util.FormattedCharSequence>, val descCut: Boolean) {
        /** Only this band of the entry (content coordinates) is drawn and clickable; set while its group is opening or closing. */
        var clipY1 = Int.MIN_VALUE; var clipY2 = Int.MAX_VALUE
        fun visibleAt(cy: Int) = cy >= clipY1 && cy < clipY2
    }
    /** Per group: 0 folded .. 1 open, eased towards the Session state each frame. */
    private val groupAnim = HashMap<String, Float>()
    private var frameDt = 0f
    /** When the category last changed, for the slide-in. */
    private var switchedAt = 0L
    private val SWITCH_MS = 160
    private val KIND_CARD = 0; private val KIND_ACCORDION = 1; private val KIND_LABEL = 2
    private var entries: List<Entry> = emptyList()
    private var contentHeight = 0

    private fun widget(opt: OptionSpec): Widget? = widgets.getOrPut(opt.key) { widgetFor(opt) ?: return null }
    // Which groups are open and which category is showing last only for this game
    // session: a fresh start opens on the first category with everything folded.
    private object Session { var category: String? = null; val expanded = HashSet<String>() }
    private fun expanded(opt: OptionSpec): Boolean = opt.key in Session.expanded
    private fun toggleExpanded(opt: OptionSpec) { if (!Session.expanded.add(opt.key)) Session.expanded.remove(opt.key) }

    private fun select(cat: CategorySpec) {
        if (cat !== selected) { selected = cat; scrollTarget = 0f; scroll = 0f; switchedAt = System.currentTimeMillis() }
        Session.category = cat.key
    }

    private fun groupOpenness(header: OptionSpec): Float {
        val target = if (expanded(header)) 1f else 0f
        val cur = groupAnim[header.key] ?: target
        var next = cur + (target - cur) * minOf(1f, frameDt * 14)
        if (abs(target - next) < 0.005f) next = target
        groupAnim[header.key] = next
        return next
    }

    // ── layout pass ───────────────────────────────────────────────────

    private fun buildEntries(g: Gfx) {
        val out = ArrayList<Entry>()
        val q = search.value.trim().lowercase()
        val cols = 1
        val gap = 4
        val colW = (contentW - gap * (cols - 1)) / cols
        val colY = IntArray(cols)
        fun bottom() = colY.max()

        fun rowHeight(opt: OptionSpec, wdg: Widget?, w: Int, indent: Int): Triple<Int, List<net.minecraft.util.FormattedCharSequence>, Boolean> {
            val textW = w - indent - cardPad * 2 - resetLane - (wdg?.let { it.w + cardPad } ?: 0)
            return Triple(maxOf(g.lineHeight, wdg?.h ?: 0) + cardPad * 2 - 2, emptyList(), opt.desc.isNotBlank())
        }

        fun card(opt: OptionSpec, indent: Int) {
            val c = colY.indices.minByOrNull { colY[it] } ?: 0
            val x = c * (colW + gap)
            val wdg = widget(opt)
            // wide controls (text boxes, sliders) give way when the column is narrow
            val fit = minOf(130, maxOf(70, (colW - indent) * 45 / 100))
            if (wdg is TextOpt) wdg.w = fit
            if (wdg is Slider) wdg.w = fit
            // the mob id box gets more room: ids like minecraft:wither_skeleton are long
            if (wdg is MobPickerOpt) wdg.w = minOf(200, maxOf(120, (colW - indent) * 60 / 100))
            val (h, lines, cut) = rowHeight(opt, wdg, colW, indent)
            out.add(Entry(KIND_CARD, opt, "", x + indent, colY[c], colW - indent, h, wdg, lines, cut))
            colY[c] += h + 1
        }
        fun fullWidth(kind: Int, opt: OptionSpec?, label: String, h: Int) {
            val y = bottom()
            out.add(Entry(kind, opt, label, 0, y, contentW, h, null, emptyList(), false))
            colY.fill(y + h + gap)
        }

        if (q.isEmpty()) {
            val headers = selected.options.filter { it.type == "accordion" }.associateBy { it.id }
            val children = selected.options.filter { it.type != "accordion" && it.accordion != null && headers.containsKey(it.accordion) }.groupBy { it.accordion!! }
            for (opt in selected.options) {
                if (opt.type == "accordion") {
                    fullWidth(KIND_ACCORDION, opt, "", g.lineHeight + cardPad * 2)
                    val a = groupOpenness(opt)
                    val kids = children[opt.id] ?: continue
                    if (a <= 0.001f) continue
                    // lay the children out in full, then show only the top a-th of them
                    // and pull everything below up by what is still folded
                    val startY = bottom()
                    val first = out.size
                    for (child in kids) card(child, 10)
                    val fullH = bottom() - startY
                    val shownH = (fullH * a).roundToInt()
                    if (a < 1f) for (i in first until out.size) { out[i].clipY1 = startY; out[i].clipY2 = startY + shownH }
                    colY.fill(startY + shownH + gap - if (a < 1f) 1 else 0)
                    continue
                }
                if (opt.accordion != null && headers.containsKey(opt.accordion)) continue   // drawn under its header
                card(opt, 0)
            }
        } else {
            for (cat in ConfigSpec.categories) {
                val headers = cat.options.filter { it.type == "accordion" }.associateBy { it.id }
                val hits = cat.options.filter {
                    it.type != "accordion" && (it.name.lowercase().contains(q) || it.desc.lowercase().contains(q) ||
                        (it.accordion?.let { id -> headers[id]?.name?.lowercase()?.contains(q) } ?: false))
                }
                if (hits.isEmpty()) continue
                fullWidth(KIND_LABEL, null, cat.name, g.lineHeight + 4)
                var lastGroup: Int? = -1
                for (opt in hits) {
                    val group = opt.accordion
                    if (group != lastGroup && group != null) fullWidth(KIND_LABEL, null, "  › " + (headers[group]?.name ?: ""), g.lineHeight + 2)
                    lastGroup = group
                    card(opt, if (group != null) 10 else 0)
                }
                colY.fill(bottom() + 4)
            }
            if (out.isEmpty()) fullWidth(KIND_LABEL, null, "Nothing matches \"${search.value.trim()}\"", g.lineHeight)
        }
        entries = out
        contentHeight = bottom()
    }

    // ── render ────────────────────────────────────────────────────────

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val g = Gfx(ctx, font)
        val now = System.currentTimeMillis()
        val dt = ((now - lastFrameMs).coerceIn(0, 100)) / 1000f
        lastFrameMs = now
        tooltip = null

        winW = (width * 0.86).roundToInt().coerceIn(360, 680).coerceAtMost(width - 8)
        winH = (height * 0.88).roundToInt().coerceIn(200, 440).coerceAtMost(height - 8)
        winX = (width - winW) / 2; winY = (height - winH) / 2

        // Opening: the window grows from 92% and fades in over OPEN_MS, eased out.
        val raw = ((now - openedAt).toFloat() / OPEN_MS).coerceIn(0f, 1f)
        val t = 1f - (1f - raw) * (1f - raw) * (1f - raw)
        ctx.fill(0, 0, width, height, ((0x90 * t).roundToInt() shl 24))
        val pose = ctx.pose()
        pose.pushMatrix()
        if (t < 1f) {
            val s = 0.92f + 0.08f * t
            val cx = winX + winW / 2f; val cy = winY + winH / 2f
            pose.translate(cx, cy); pose.scale(s, s); pose.translate(-cx, -cy)
        }
        g.rrect(winX, winY, winX + winW, winY + winH, 6, Theme.WINDOW)
        g.rborder(winX, winY, winX + winW, winY + winH, 6, Theme.BORDER)

        renderTopBar(g, mouseX, mouseY)
        renderSidebar(g, mouseX, mouseY, dt)
        renderContent(g, mouseX, mouseY, dt)
        if (t < 1f) g.rrect(winX, winY, winX + winW, winY + winH, 6, Theme.withAlpha(Theme.WINDOW, ((1f - t) * 255).roundToInt()))
        pose.popMatrix()

        val ov = overlay
        if (ov != null && ov.wantsOverlay) {
            ctx.nextStratum()
            ov.renderOverlay(g, mouseX, mouseY, width, height)
        } else {
            if (ov != null) overlay = null
            tooltip?.let { (opt, at) -> ctx.nextStratum(); renderTooltip(g, opt, at[0], at[1]) }
        }
        ctx.nextStratum()
        GuiStyle.drawPicker(g, search.x - 100, winY + 5, 94, stylePickerOpen, mouseX, mouseY)
    }

    private fun renderTooltip(g: Gfx, opt: OptionSpec, mx: Int, my: Int) {
        val w = 220
        val lines = g.wrap(opt.desc, w - 12)
        val h = 6 + g.lineHeight + 3 + lines.size * g.lineHeight + 6
        var x = mx + 10; var y = my + 10
        if (x + w > width - 4) x = mx - w - 6
        if (y + h > height - 4) y = height - 4 - h
        g.rrect(x, y, x + w, y + h, 4, 0xF8100D17.toInt())
        g.rborder(x, y, x + w, y + h, 4, Theme.ACCENT_DIM)
        g.text(opt.name, x + 6, y + 6, Theme.ACCENT)
        var ly = y + 6 + g.lineHeight + 3
        for (l in lines) { g.text(l, x + 6, ly, Theme.TEXT); ly += g.lineHeight }
    }

    private fun renderTopBar(g: Gfx, mx: Int, my: Int) {
        g.fill(winX + 1, winY + topH, winX + winW - 1, winY + topH + 1, Theme.BORDER)
        g.bandText("FamilyAddons", winX + 10, winY + 8)
        // search
        search.w = 140; search.h = 14; search.x = winX + winW - 14 - search.w - 18; search.y = winY + 5
        search.placeholder = "Search settings…"
        // the version only when it has room before the style button
        val vx = winX + 10 + g.width("FamilyAddons") + 5
        if (vx + g.width(VERSION_LABEL) < search.x - 104) g.text(VERSION_LABEL, vx, winY + 8, Theme.TEXT_FAINT)
        search.render(g, mx, my)
        if (search.value.isNotEmpty()) {
            val overX = mx >= search.x + search.w - 12 && mx < search.x + search.w && my >= search.y && my < search.y + search.h
            g.text("×", search.x + search.w - 9, search.y + 3, if (overX) Theme.BAD else Theme.TEXT_DIM)
        }
        // the style picker is drawn last (see extractRenderState) so its list sits above the content
        // close
        val cx = winX + winW - 18
        val overClose = mx >= cx && mx < cx + 12 && my >= winY + 5 && my < winY + 19
        g.rrect(cx, winY + 5, cx + 12, winY + 19, 3, if (overClose) Theme.withAlpha(Theme.BAD, 0x50) else Theme.TRACK)
        g.text("×", cx + 3, winY + 8, if (overClose) Theme.BAD else Theme.TEXT_DIM)
    }

    // ── categories: sidebar or tabs ───────────────────────────────────

    private val sideRowH = 18

    private fun renderSidebar(g: Gfx, mx: Int, my: Int, dt: Float) {
        val x1 = winX + 1; val y1 = winY + topH + 1; val x2 = winX + sideW; val y2 = winY + winH - 1
        g.fill(x1, y1, x2, y2, Theme.SIDEBAR)
        g.fill(x2, y1, x2 + 1, y2, Theme.BORDER)
        val total = ConfigSpec.categories.size * sideRowH + pad * 2
        val maxScroll = maxOf(0, total - (y2 - y1))
        sideScrollTarget = sideScrollTarget.coerceIn(0f, maxScroll.toFloat())
        sideScroll += (sideScrollTarget - sideScroll) * minOf(1f, dt * 14)
        g.clipped(x1, y1, x2, y2) {
            var y = y1 + pad - sideScroll.roundToInt()
            for (cat in ConfigSpec.categories) {
                val rx = x1 + pad; val rw = sideW - pad * 2 - 1
                val over = mx >= rx && mx < rx + rw && my >= y && my < y + sideRowH && my >= y1 && my < y2 && overlay == null
                val sel = cat === selected && search.value.isBlank()
                val a = anim("side:" + cat.key, over || sel, dt)
                if (a > 0.01f) g.rrect(rx, y, rx + rw, y + sideRowH - 2, 3, Theme.lerp(Theme.SIDEBAR, if (sel) Theme.CARD_HOVER else Theme.CARD, a.toDouble()))
                if (sel) g.rrect(rx, y + 3, rx + 2, y + sideRowH - 5, 1, Theme.ACCENT)
                g.text(g.clip(cat.name, rw - 12), rx + 7, y + 5, if (sel || over) Theme.TEXT else Theme.TEXT_DIM)
                y += sideRowH
            }
        }
    }

    private fun anim(key: String, on: Boolean, dt: Float): Float {
        val cur = hoverAnim[key] ?: 0f
        val target = if (on) 1f else 0f
        var next = cur + (target - cur) * minOf(1f, dt * 16)
        if (abs(target - next) < 0.01f) next = target
        hoverAnim[key] = next
        return next
    }

    // ── content ───────────────────────────────────────────────────────

    private fun renderContent(g: Gfx, mx: Int, my: Int, dt: Float) {
        frameDt = dt
        buildEntries(g)
        // a new category slides up into place and fades in
        val sw = ((System.currentTimeMillis() - switchedAt).toFloat() / SWITCH_MS).coerceIn(0f, 1f)
        val st = 1f - (1f - sw) * (1f - sw)
        val slide = ((1f - st) * 10).roundToInt()
        val maxScroll = maxOf(0, contentHeight - contentH)
        scrollTarget = scrollTarget.coerceIn(0f, maxScroll.toFloat())
        scroll += (scrollTarget - scroll) * minOf(1f, dt * 14)
        if (abs(scrollTarget - scroll) < 0.5f) scroll = scrollTarget
        val off = scroll.roundToInt()
        val inContent = mx >= contentX && mx < contentX + contentW + 8 && my >= contentY && my < contentY + contentH && overlay == null

        g.clipped(contentX, contentY, contentX + contentW + 8, contentY + contentH) {
            if (slide != 0) { g.ctx.pose().pushMatrix(); g.ctx.pose().translate(0f, slide.toFloat()) }
            for ((ei, e) in entries.withIndex()) {
                val ey = contentY + e.y - off
                val ex = contentX + e.x
                if (ey + e.h < contentY || ey > contentY + contentH) { e.widget?.let { it.x = -9999; it.y = -9999 }; continue }
                // a group mid-animation shows only a band of its rows
                val clipped = e.clipY1 != Int.MIN_VALUE
                if (clipped) {
                    val cy1 = contentY + e.clipY1 - off; val cy2 = contentY + e.clipY2 - off
                    if (ey >= cy2 || ey + e.h <= cy1) { e.widget?.let { it.x = -9999; it.y = -9999 }; continue }
                    g.ctx.enableScissor(contentX, maxOf(contentY, cy1), contentX + contentW + 8, minOf(contentY + contentH, cy2))
                }
                val overRow = inContent && mx >= ex && mx < ex + e.w && my >= ey && my < ey + e.h && (!clipped || e.visibleAt(my - contentY + off))
                when (e.kind) {
                    KIND_LABEL -> g.text(e.label, ex + 2, ey + 2, Theme.ACCENT)
                    KIND_ACCORDION -> {
                        val opt = e.opt!!
                        val a = anim("acc:" + opt.key, overRow, dt)
                        g.rrect(ex, ey, ex + e.w, ey + e.h, radius, Theme.lerp(Theme.CARD, Theme.CARD_HOVER, a.toDouble()))
                        g.rrect(ex, ey + 4, ex + 2, ey + e.h - 4, 1, Theme.ACCENT_MID)
                        g.text(if (expanded(opt)) "▾" else "▸", ex + cardPad + 2, ey + cardPad, Theme.ACCENT)
                        g.text(opt.name, ex + cardPad + 12, ey + cardPad, Theme.TEXT)
                        if (opt.desc.isNotBlank()) g.text(g.clip(opt.desc, e.w - g.width(opt.name) - 40), ex + cardPad + 16 + g.width(opt.name), ey + cardPad, Theme.TEXT_FAINT)
                    }
                    KIND_CARD -> {
                        val opt = e.opt!!
                        val a = anim("card:" + opt.key, overRow, dt)
                        if (a > 0.01f) g.rrect(ex, ey, ex + e.w, ey + e.h, 2, Theme.lerp(Theme.WINDOW, Theme.CARD, a.toDouble()))
                        // a divider only between rows, not under the last one of a group
                        val nextIsRow = entries.getOrNull(ei + 1)?.kind == KIND_CARD
                        if (nextIsRow) g.fill(ex + 2, ey + e.h, ex + e.w - 2, ey + e.h + 1, Theme.BORDER)
                        val wdg = e.widget
                        if (wdg != null) { wdg.x = ex + e.w - cardPad - wdg.w; wdg.y = ey + (e.h - wdg.h) / 2 }
                        val textW = e.w - cardPad * 2 - resetLane - (wdg?.let { it.w + cardPad } ?: 0)
                        val nameY = ey + (e.h - g.lineHeight) / 2 + 1
                        g.text(g.clip(opt.name, textW), ex + cardPad, nameY, Theme.TEXT)
                        val overText = overRow && mx < ex + cardPad + textW
                        if (overText && opt.desc.isNotBlank()) tooltip = opt to intArrayOf(mx, my)
                        if (wdg != null && !Values.isDefault(opt) && opt.type != "button") {
                            val rx = wdg.x - resetLane + 2
                            val overR = mx >= rx - 2 && mx < rx + 9 && my >= ey && my < ey + e.h && inContent
                            g.text("↺", rx, ey + (e.h - g.lineHeight) / 2 + 1, if (overR) Theme.ACCENT else Theme.TEXT_FAINT)
                        }
                        wdg?.render(g, mx, my)
                    }
                }
                if (clipped) g.ctx.disableScissor()
            }
            if (slide != 0) g.ctx.pose().popMatrix()
            if (st < 1f) g.fill(contentX, contentY, contentX + contentW + 8, contentY + contentH, Theme.withAlpha(Theme.WINDOW, ((1f - st) * 255).roundToInt()))
        }
        if (maxScroll > 0) {
            val laneY1 = contentY; val laneH = contentH
            g.rrect(scrollbarX, laneY1, scrollbarX + 4, laneY1 + laneH, 2, Theme.TRACK)
            val thumbH = maxOf(16, laneH * contentH / contentHeight)
            val thumbY = laneY1 + ((laneH - thumbH) * scroll / maxScroll).roundToInt()
            val overBar = mx >= scrollbarX - 2 && mx < scrollbarX + 6 && my >= thumbY && my < thumbY + thumbH
            g.rrect(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbH, 2, if (draggingBar || overBar) Theme.ACCENT else Theme.ACCENT_MID)
        }
    }

    private fun scrollbarThumb(): IntArray? {
        val maxScroll = maxOf(0, contentHeight - contentH)
        if (maxScroll <= 0) return null
        val thumbH = maxOf(16, contentH * contentH / contentHeight)
        val thumbY = contentY + ((contentH - thumbH) * scroll / maxScroll).roundToInt()
        return intArrayOf(thumbY, thumbH)
    }

    // ── input ─────────────────────────────────────────────────────────

    private fun setFocus(w: Widget?) {
        if (focus !== w) { focus?.unfocus(); focus = w }
    }

    override fun mouseClicked(e: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = e.x(); val my = e.y(); val b = e.button()

        overlay?.let { ov ->
            val inside = ov.overlayClick(mx, my, b)
            if (!ov.wantsOverlay) overlay = null
            if (inside) { setFocus(ov); return true }
        }

        // top bar
        if (search.click(mx, my, b)) {
            if (search.value.isNotEmpty() && mx >= search.x + search.w - 12) { search.set(""); search.focused = false; scrollTarget = 0f }
            setFocus(null); return true
        }
        val cx = winX + winW - 18
        if (mx >= cx && mx < cx + 12 && my >= winY + 5 && my < winY + 19) { onClose(); return true }
        if (GuiStyle.clickPicker(search.x - 100, winY + 5, 94, stylePickerOpen, mx, my, parent) { stylePickerOpen = it }) { setFocus(null); return true }

        // categories
        if (mx >= winX && mx < winX + sideW && my >= winY + topH && my < winY + winH) {
            var y = winY + topH + 1 + pad - sideScroll.roundToInt()
            for (cat in ConfigSpec.categories) {
                if (my >= y && my < y + sideRowH) { select(cat); search.set(""); search.focused = false; setFocus(null); return true }
                y += sideRowH
            }
            return true
        }

        // scrollbar
        scrollbarThumb()?.let { (thumbY, thumbH) ->
            if (mx >= scrollbarX - 3 && mx < scrollbarX + 7 && my >= contentY && my < contentY + contentH) {
                if (my >= thumbY && my < thumbY + thumbH) { draggingBar = true; dragBarOffset = (my - thumbY).toInt() }
                else {
                    val maxScroll = contentHeight - contentH
                    scrollTarget = ((my - contentY - thumbH / 2) / (contentH - thumbH) * maxScroll).toFloat()
                    scroll = scrollTarget
                    draggingBar = true; dragBarOffset = thumbH / 2
                }
                return true
            }
        }

        // content
        if (mx >= contentX && mx < contentX + contentW && my >= contentY && my < contentY + contentH) {
            val off = scroll.roundToInt()
            for (en in entries) {
                val ey = contentY + en.y - off; val ex = contentX + en.x
                if (my < ey || my >= ey + en.h || mx < ex || mx >= ex + en.w) continue
                if (!en.visibleAt((my - contentY + off).toInt())) continue
                when (en.kind) {
                    KIND_ACCORDION -> { toggleExpanded(en.opt!!); setFocus(null); return true }
                    KIND_CARD -> {
                        val wdg = en.widget
                        if (wdg != null) {
                            if (!Values.isDefault(en.opt!!) && en.opt.type != "button") {
                                val rx = wdg.x - resetLane + 2
                                if (mx >= rx - 2 && mx < rx + 9) { Values.clear(en.opt); widgets.remove(en.opt.key); setFocus(null); return true }
                            }
                            if (wdg.click(mx, my, b)) {
                                setFocus(wdg)
                                if (wdg.wantsOverlay) overlay = wdg
                                return true
                            }
                        }
                        // the whole card flips a toggle
                        if (wdg is Toggle && b == 0) { Values.set(en.opt!!, !Values.bool(en.opt)); setFocus(null); return true }
                        setFocus(null)
                        return true
                    }
                }
            }
            setFocus(null)
            return true
        }
        setFocus(null)
        return mx >= winX && mx < winX + winW && my >= winY && my < winY + winH
    }

    override fun mouseDragged(e: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mx = e.x(); val my = e.y()
        overlay?.let { if (it.overlayDrag(mx, my)) return true }
        if (draggingBar) {
            scrollbarThumb()?.let { (_, thumbH) ->
                val maxScroll = contentHeight - contentH
                scrollTarget = ((my - dragBarOffset - contentY) / (contentH - thumbH) * maxScroll).toFloat().coerceIn(0f, maxScroll.toFloat())
                scroll = scrollTarget
            }
            return true
        }
        if (search.focused) return search.drag(mx)
        focus?.let { if (it.drag(mx, my)) return true }
        return false
    }

    override fun mouseReleased(e: MouseButtonEvent): Boolean {
        draggingBar = false
        overlay?.release()
        focus?.release()
        widgets.values.forEach { it.release() }
        return true
    }

    override fun mouseScrolled(mx: Double, my: Double, hAmount: Double, vAmount: Double): Boolean {
        overlay?.let { if (it.overlayScroll(mx, my, vAmount)) return true }
        if (mx >= winX && mx < winX + sideW && my >= winY + topH) { sideScrollTarget -= (vAmount * sideRowH).toFloat(); return true }
        if (mx >= contentX && my >= contentY && my < contentY + contentH) {
            // the wheel only ever scrolls the list; a slider under it is left alone
            scrollTarget -= (vAmount * 28).toFloat()
            return true
        }
        return false
    }

    override fun keyPressed(e: KeyEvent): Boolean {
        if (search.focused) {
            if (e.key() == GLFW.GLFW_KEY_ESCAPE) { search.focused = false; return true }
            return search.key(e)
        }
        overlay?.let { if (it.key(e)) return true }
        focus?.let { if (it.key(e)) return true }
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (overlay != null) { overlay?.closeOverlay(); overlay = null; return true }
            onClose(); return true
        }
        if (e.key() == GLFW.GLFW_KEY_F && e.hasControlDown()) { search.focused = true; return true }
        return super.keyPressed(e)
    }

    override fun charTyped(e: CharacterEvent): Boolean {
        if (search.focused) return search.char(e)
        overlay?.let { if (it.char(e)) return true }
        focus?.let { if (it.char(e)) return true }
        return false
    }

    override fun onClose() {
        focus?.unfocus(); search.focused = false
        Values.save()
        Minecraft.getInstance().gui.setScreen(parent)
    }

    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = false
}
