package org.kyowa.familyaddons.features

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.kyowa.familyaddons.COLOR_CODE_REGEX
import org.kyowa.familyaddons.config.FamilyConfigManager

class HudEditorScreen : Screen(Component.literal("FA HUD Editor")) {

    data class HudElement(
        val id: String,
        val label: String,
        var x: Int,
        var y: Int,
        var w: Int,
        var h: Int,
        var scale: Float = 1f,
        var dragging: Boolean = false,
        var dragOffX: Double = 0.0,
        var dragOffY: Double = 0.0,
        val canScale: Boolean = false,
        val onSave: (HudElement) -> Unit,
        val renderContent: (context: GuiGraphicsExtractor, elem: HudElement) -> Unit
    ) {
        val screenW get() = (w * scale).toInt()
        val screenH get() = (h * scale).toInt()
    }

    private val elements = mutableListOf<HudElement>()
    private var activeElement: HudElement? = null

    override fun init() {
        elements.clear()
        val client = Minecraft.getInstance()
        val sw = client.window.guiScaledWidth
        val sh = client.window.guiScaledHeight
        val tr = client.font

        // Mini boss timer (Crimson Isle)
        elements.add(HudElement(
            id = "miniBoss", label = "Mini Boss Timer",
            x = FamilyConfigManager.config.crimsonIsle.miniBossHudX.takeIf { it >= 0 } ?: 10,
            y = FamilyConfigManager.config.crimsonIsle.miniBossHudY.takeIf { it >= 0 } ?: 10,
            w = 120, h = 24,
            scale = FamilyConfigManager.config.crimsonIsle.miniBossHudScale.toFloatOrNull() ?: 1f,
            canScale = true,
            onSave = { elem ->
                FamilyConfigManager.config.crimsonIsle.miniBossHudX = elem.x
                FamilyConfigManager.config.crimsonIsle.miniBossHudY = elem.y
                FamilyConfigManager.config.crimsonIsle.miniBossHudScale = "%.1f".format(elem.scale)
            },
            renderContent = { ctx, _ ->
                ctx.text(tr, "§cMage Outlaw §7- §f1:42", 0, 0, -1, true)
                ctx.text(tr, "§6Bladesoul §7- §aSpawning", 0, 11, -1, true)
            }
        ))

        // Key Tracker
        val ktW = 120
        val ktH = InfernalKeyTracker.getLineCount() * 10 + 4
        elements.add(HudElement(
            id = "keyTracker", label = "Key Tracker",
            x = FamilyConfigManager.config.kuudra.keyTrackerHudX,
            y = FamilyConfigManager.config.kuudra.keyTrackerHudY,
            w = ktW, h = ktH,
            canScale = false,
            onSave = { elem ->
                FamilyConfigManager.config.kuudra.keyTrackerHudX = elem.x
                FamilyConfigManager.config.kuudra.keyTrackerHudY = elem.y
            },
            renderContent = { ctx, _ -> InfernalKeyTracker.renderLines(ctx) }
        ))



        // Kuudra DT Title
        val dtScale = DtTitle.getScale()
        val dtPlain = DtTitle.PREVIEW_TEXT.replace(COLOR_CODE_REGEX, "")
        val dtW = tr.width(dtPlain)
        val dtX = if (FamilyConfigManager.config.kuudra.dtTitleHudX == -1)
            ((sw - dtW * dtScale) / 2f).toInt()
        else FamilyConfigManager.config.kuudra.dtTitleHudX
        val dtY = if (FamilyConfigManager.config.kuudra.dtTitleHudY == -1)
            (sh / 2f - 20f).toInt()
        else FamilyConfigManager.config.kuudra.dtTitleHudY

        elements.add(HudElement(
            id = "dtTitle", label = "Kuudra DT Title",
            x = dtX, y = dtY,
            w = dtW, h = 10,
            scale = dtScale,
            canScale = true,
            onSave = { elem ->
                FamilyConfigManager.config.kuudra.dtTitleHudX = elem.x
                FamilyConfigManager.config.kuudra.dtTitleHudY = elem.y
                FamilyConfigManager.config.kuudra.dtTitleScale = "%.1f".format(elem.scale)
            },
            renderContent = { ctx, _ ->
                ctx.text(tr, DtTitle.PREVIEW_TEXT, 0, 0, 0xFFFFFFFF.toInt(), true)
            }
        ))

        // Dungeon DT Title
        val dunScale = DungeonDtTitle.getScale()
        val dunPlain = DungeonDtTitle.PREVIEW_TEXT.replace(COLOR_CODE_REGEX, "")
        val dunW = tr.width(dunPlain)
        val dunX = if (FamilyConfigManager.config.dungeons.dungeonDtTitleHudX == -1)
            ((sw - dunW * dunScale) / 2f).toInt()
        else FamilyConfigManager.config.dungeons.dungeonDtTitleHudX
        val dunY = if (FamilyConfigManager.config.dungeons.dungeonDtTitleHudY == -1)
            (sh / 2f - 40f).toInt()
        else FamilyConfigManager.config.dungeons.dungeonDtTitleHudY

        elements.add(HudElement(
            id = "kuudraDirection", label = "Kuudra Direction",
            x = FamilyConfigManager.config.kuudra.directionHudX,
            y = FamilyConfigManager.config.kuudra.directionHudY,
            w = 80, h = 10,
            onSave = { elem ->
                FamilyConfigManager.config.kuudra.directionHudX = elem.x
                FamilyConfigManager.config.kuudra.directionHudY = elem.y
                FamilyConfigManager.config.kuudra.directionScale = "%.1f".format(elem.scale)
                FamilyConfigManager.config.kuudra.directionScaleSlider = elem.scale.coerceIn(1f, 6f)
            },
            renderContent = { ctx, _ ->
                ctx.text(tr, Component.literal(KuudraDirection.PREVIEW_TEXT), 0, 0, 0xFF00AA00.toInt(), true)
            }
        ))

    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, 0x88000000.toInt())
        val tr = Minecraft.getInstance().font
        val hint = "§7Drag to move  |  §eScroll §7to scale  |  §eEsc §7to save"
        val hintW = tr.width(hint.replace(COLOR_CODE_REGEX, ""))
        context.text(tr, hint, (width - hintW) / 2, 8, -1, true)

        for (elem in elements) {
            if (elem.dragging) {
                elem.x = (mouseX - elem.dragOffX).toInt()
                elem.y = (mouseY - elem.dragOffY).toInt()
            }
            val sw = elem.screenW; val sh = elem.screenH
            val isActive = elem == activeElement
            val matrices = context.pose()
            matrices.pushMatrix()
            matrices.translate(elem.x.toFloat(), elem.y.toFloat())
            matrices.scale(elem.scale, elem.scale)
            elem.renderContent(context, elem)
            matrices.popMatrix()

            val border = if (isActive) 0xFFFFFF00.toInt() else 0xFFFFFFFF.toInt()
            context.fill(elem.x - 1, elem.y - 1, elem.x + sw + 1, elem.y, border)
            context.fill(elem.x - 1, elem.y + sh, elem.x + sw + 1, elem.y + sh + 1, border)
            context.fill(elem.x - 1, elem.y, elem.x, elem.y + sh, border)
            context.fill(elem.x + sw, elem.y, elem.x + sw + 1, elem.y + sh, border)
            if (elem.canScale) {
                context.text(tr, "§7${"%.1f".format(elem.scale)}x", elem.x, elem.y + sh + 3, -1, true)
            }
        }
        super.extractRenderState(context, mouseX, mouseY, delta)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val mx = mouseX.toInt(); val my = mouseY.toInt()
        val elem = elements.lastOrNull { e ->
            mx >= e.x && mx <= e.x + e.screenW && my >= e.y && my <= e.y + e.screenH
        } ?: activeElement ?: return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        if (!elem.canScale) return true
        val delta = if (verticalAmount > 0) 0.1f else -0.1f
        elem.scale = "%.1f".format((elem.scale + delta).coerceIn(0.5f, 5f)).toFloat()
        return true
    }

    fun onMousePress(mouseX: Double, mouseY: Double) {
        val mx = mouseX.toInt(); val my = mouseY.toInt()
        for (elem in elements.reversed()) {
            if (mx >= elem.x && mx <= elem.x + elem.screenW &&
                my >= elem.y && my <= elem.y + elem.screenH) {
                elem.dragging = true
                elem.dragOffX = mouseX - elem.x
                elem.dragOffY = mouseY - elem.y
                activeElement = elem
                return
            }
        }
        activeElement = null
    }

    fun onMouseRelease() { elements.forEach { it.dragging = false } }

    override fun onClose() {
        elements.forEach { it.onSave(it) }
        FamilyConfigManager.save()
        super.onClose()
    }

    override fun isPauseScreen() = false
}