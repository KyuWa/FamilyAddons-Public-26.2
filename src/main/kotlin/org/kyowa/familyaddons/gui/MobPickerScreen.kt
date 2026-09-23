package org.kyowa.familyaddons.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import org.joml.Quaternionf
import org.joml.Vector3f
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.SpawnEggItem
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

/**
 * Picks a mob for the disguise: every mob that has a spawn egg, drawn as that
 * egg (a still icon), with its name and id. Search filters by either. Clicking
 * one hands its id ("minecraft:slime") back and returns.
 */
class MobPickerScreen(private val parent: Screen?, private val current: String, private val onPick: (String) -> Unit) : Screen(Component.literal("Pick a mob")) {

    class Mob(val id: String, val name: String, val egg: ItemStack, val type: EntityType<*>) {
        /** A client-side instance for drawing; made once a world exists, never ticked, so it stands still. */
        var entity: LivingEntity? = null
        var entityFailed = false
    }

    companion object {
        private var cached: List<Mob>? = null

        /**
         * Every mob with a spawn egg. Built on first use and kept; before a world has
         * been joined the item components are not bound yet and the egg lookup throws,
         * so until then this is empty and is tried again next time.
         */
        val mobs: List<Mob>
            get() {
                cached?.let { return it }
                val out = ArrayList<Mob>()
                try {
                    for (type in BuiltInRegistries.ENTITY_TYPE) {
                        val egg = SpawnEggItem.byId(type).orElse(null) ?: continue
                        val id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()
                        out.add(Mob(id, type.description.string, ItemStack(egg.value()), type))
                    }
                } catch (e: Exception) {
                    return emptyList()
                }
                return out.sortedBy { it.name }.also { cached = it }
            }
    }

    private val search = TextField("")
    private var winX = 0; private var winY = 0; private var winW = 0; private var winH = 0
    private var cellW = 118; private val cellH = 30
    private val rotation = Quaternionf().rotateZ(Math.PI.toFloat()).mul(Quaternionf().rotateX(20f * (Math.PI.toFloat() / 180f)))
    private val cameraTilt = Quaternionf().rotateX(20f * (Math.PI.toFloat() / 180f))

    /** Draws the mob itself in the cell; falls back to its egg when there is no world or it cannot be built. */
    private fun drawMob(ctx: GuiGraphicsExtractor, m: Mob, x: Int, y: Int) {
        val mc = Minecraft.getInstance()
        val level = mc.level
        if (level != null && m.entity == null && !m.entityFailed) {
            val made = runCatching { m.type.create(level, EntitySpawnReason.LOAD) }
            m.entity = (made.getOrNull() as? LivingEntity)?.also {
                // A freshly built entity has no id yet, and renderers ask for one
                // (item models keyed by entity). Give it a private negative id.
                it.setId(-1_000_000 - mobs.indexOf(m))
                // Face the viewer, the way the inventory paper doll does: the model's
                // front is +Z, and the rotateZ(π) view puts the camera on -Z.
                it.yBodyRot = 180f; it.yRot = 180f; it.yHeadRot = 180f; it.xRot = 0f
                it.yBodyRotO = 180f; it.yRotO = 180f; it.yHeadRotO = 180f; it.xRotO = 0f
            }
            if (m.entity == null) {
                org.slf4j.LoggerFactory.getLogger("FamilyAddons").warn("mob picker: cannot build ${m.id} (${made.exceptionOrNull()?.toString() ?: "not a living entity"}), using its egg")
                m.entityFailed = true
            }
        }
        val e = m.entity
        if (e == null || e.level() !== level) { m.entity = null; ctx.item(m.egg, x + 4, y + 7); return }
        val result = runCatching {
            val state = mc.entityRenderDispatcher.getRenderer(e).createRenderState(e, 1f)
            state.shadowPieces.clear()
            val size = maxOf(e.bbHeight, e.bbWidth, 0.5f)
            val scale = 22f / size
            ctx.entity(state, scale, Vector3f(0f, e.bbHeight / 2f + 0.0625f, 0f), rotation, cameraTilt, x, y, x + 26, y + cellH)
        }
        if (result.isFailure) {
            org.slf4j.LoggerFactory.getLogger("FamilyAddons").warn("mob picker: cannot draw ${m.id}, using its egg", result.exceptionOrNull())
            m.entityFailed = true; m.entity = null; ctx.item(m.egg, x + 4, y + 7)
        }
    }
    private var scroll = 0f; private var scrollTarget = 0f
    private var lastFrameMs = System.currentTimeMillis()
    private var maxScroll = 0
    private var draggingBar = false; private var dragBarOffset = 0
    private val barX get() = winX + winW - 7
    private val gridY get() = winY + 30
    private val gridH get() = winH - 30 - 8

    private fun filtered(): List<Mob> {
        val q = search.value.trim().lowercase()
        if (q.isEmpty()) return mobs
        return mobs.filter { it.name.lowercase().contains(q) || it.id.lowercase().contains(q) }
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        val g = Gfx(ctx, font)
        val now = System.currentTimeMillis(); val dt = ((now - lastFrameMs).coerceIn(0, 100)) / 1000f; lastFrameMs = now
        winW = (width * 0.8).roundToInt().coerceIn(360, 620).coerceAtMost(width - 8)
        winH = (height * 0.85).roundToInt().coerceIn(200, 420).coerceAtMost(height - 8)
        winX = (width - winW) / 2; winY = (height - winH) / 2

        ctx.fill(0, 0, width, height, 0x90000000.toInt())
        g.rrect(winX, winY, winX + winW, winY + winH, 6, Theme.WINDOW)
        g.rborder(winX, winY, winX + winW, winY + winH, 6, Theme.BORDER)
        g.fill(winX + 1, winY + 24, winX + winW - 1, winY + 25, Theme.BORDER)
        g.bandText("FamilyAddons", winX + 10, winY + 8)
        g.text("pick a mob", winX + 10 + g.width("FamilyAddons") + 5, winY + 8, Theme.TEXT_FAINT)
        search.w = 150; search.h = 14; search.x = winX + winW - 10 - search.w - 18; search.y = winY + 5; search.placeholder = "Search mobs…"
        search.render(g, mx, my)
        val cx = winX + winW - 18
        val overClose = mx >= cx && mx < cx + 12 && my >= winY + 5 && my < winY + 19
        g.rrect(cx, winY + 5, cx + 12, winY + 19, 3, if (overClose) Theme.withAlpha(Theme.BAD, 0x50) else Theme.TRACK)
        g.text("×", cx + 3, winY + 8, if (overClose) Theme.BAD else Theme.TEXT_DIM)

        val list = filtered()
        val cols = maxOf(1, (winW - 20 - 6) / (118 + 4))
        cellW = (winW - 20 - 6 - (cols - 1) * 4) / cols
        val rows = (list.size + cols - 1) / cols
        val total = rows * (cellH + 4)
        maxScroll = maxOf(0, total - gridH)
        scrollTarget = scrollTarget.coerceIn(0f, maxScroll.toFloat())
        scroll += (scrollTarget - scroll) * minOf(1f, dt * 14)
        val off = scroll.roundToInt()
        val gx = winX + 10
        var hoverMob: Mob? = null

        g.clipped(winX + 1, gridY, winX + winW - 1, gridY + gridH) {
            for ((i, m) in list.withIndex()) {
                val x = gx + (i % cols) * (cellW + 4)
                val y = gridY + (i / cols) * (cellH + 4) - off
                if (y + cellH < gridY || y > gridY + gridH) continue
                val over = mx >= x && mx < x + cellW && my >= y && my < y + cellH && my >= gridY && my < gridY + gridH
                val sel = m.id == current
                if (over) hoverMob = m
                g.rrect(x, y, x + cellW, y + cellH, 3, if (over) Theme.CARD_HOVER else Theme.CARD)
                if (sel) g.rborder(x, y, x + cellW, y + cellH, 3, Theme.ACCENT)
                drawMob(ctx, m, x, y)
                g.text(g.clip(m.name, cellW - 32), x + 29, y + 6, if (sel) Theme.TEXT else Theme.TEXT_DIM)
                g.text(g.clip(m.id.removePrefix("minecraft:"), cellW - 32), x + 29, y + 16, Theme.TEXT_FAINT)
            }
        }
        if (maxScroll > 0) {
            val (thumbY, thumbH) = thumb()
            g.rrect(barX, gridY, barX + 4, gridY + gridH, 2, Theme.TRACK)
            val over = mx >= barX - 2 && mx < barX + 6 && my >= thumbY && my < thumbY + thumbH
            g.rrect(barX, thumbY, barX + 4, thumbY + thumbH, 2, if (draggingBar || over) Theme.ACCENT else Theme.ACCENT_MID)
        }
        if (list.isEmpty()) g.text("No mob matches \"${search.value.trim()}\"", gx, gridY + 6, Theme.TEXT_FAINT)
        hoverMob?.let { ctx.nextStratum(); g.rrect(mx + 8, my + 8, mx + 8 + g.width(it.id) + 8, my + 8 + 13, 3, 0xF8100D17.toInt()); g.text(it.id, mx + 12, my + 11, Theme.TEXT) }
    }

    private fun thumb(): Pair<Int, Int> {
        val total = maxScroll + gridH
        val thumbH = maxOf(16, gridH * gridH / maxOf(1, total))
        val thumbY = gridY + if (maxScroll > 0) ((gridH - thumbH) * scroll / maxScroll).roundToInt() else 0
        return thumbY to thumbH
    }

    private fun barClick(mx: Double, my: Double): Boolean {
        if (maxScroll <= 0 || mx < barX - 3 || mx >= barX + 7 || my < gridY || my >= gridY + gridH) return false
        val (thumbY, thumbH) = thumb()
        if (my >= thumbY && my < thumbY + thumbH) { draggingBar = true; dragBarOffset = (my - thumbY).toInt() }
        else { draggingBar = true; dragBarOffset = thumbH / 2; barDrag(my) }
        return true
    }

    private fun barDrag(my: Double) {
        val (_, thumbH) = thumb()
        scrollTarget = ((my - dragBarOffset - gridY) / (gridH - thumbH) * maxScroll).toFloat().coerceIn(0f, maxScroll.toFloat())
        scroll = scrollTarget
    }

    override fun mouseClicked(e: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = e.x(); val my = e.y()
        if (barClick(mx, my)) return true
        if (search.click(mx, my, e.button())) return true
        val cx = winX + winW - 18
        if (mx >= cx && mx < cx + 12 && my >= winY + 5 && my < winY + 19) { onClose(); return true }
        if (my >= gridY && my < gridY + gridH && mx >= winX + 10) {
            val list = filtered()
            // same column count the layout used (cellW is the stretched width, not the base)
            val cols = maxOf(1, (winW - 20 - 6) / (118 + 4))
            val col = ((mx - (winX + 10)) / (cellW + 4)).toInt()
            val row = ((my - gridY + scroll.roundToInt()) / (cellH + 4)).toInt()
            if (col in 0 until cols && mx - (winX + 10) - col * (cellW + 4) < cellW) {
                val i = row * cols + col
                if (i in list.indices) { onPick(list[i].id); onClose(); return true }
            }
        }
        search.focused = false
        return true
    }

    override fun mouseDragged(e: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (draggingBar) { barDrag(e.y()); return true }
        return search.focused && search.drag(e.x())
    }
    override fun mouseReleased(e: MouseButtonEvent): Boolean { draggingBar = false; return true }
    override fun mouseScrolled(mx: Double, my: Double, h: Double, v: Double): Boolean {
        // some setups deliver the wheel on the horizontal axis; take whichever moved
        val amount = if (v != 0.0) v else h
        scrollTarget -= (amount * 30).toFloat()
        return true
    }

    override fun keyPressed(e: KeyEvent): Boolean {
        if (search.focused) { if (e.key() == GLFW.GLFW_KEY_ESCAPE) { search.focused = false; return true }; return search.key(e) }
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
        return super.keyPressed(e)
    }
    override fun charTyped(e: CharacterEvent) = search.focused && search.char(e)
    override fun onClose() { Minecraft.getInstance().gui.setScreen(parent) }
    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = false
}
