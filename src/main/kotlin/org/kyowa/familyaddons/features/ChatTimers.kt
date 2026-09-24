package org.kyowa.familyaddons.features

import com.google.gson.JsonParser
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.kyowa.familyaddons.COLOR_CODE_REGEX
import org.kyowa.familyaddons.config.FamilyConfigManager
import org.kyowa.familyaddons.util.FaChat

/**
 * Timers you write yourself: when a line of chat contains text you chose, a
 * countdown of the seconds you gave it appears on screen.
 *
 * Matching is "does the line contain this", ignoring capitals, so a fragment
 * after the part that changes is enough — "has obtained Scylla" catches the
 * line whoever got it. The same text arriving again restarts that timer rather
 * than stacking a second one beside it.
 */
object ChatTimers {

    private const val MIN_SECONDS = 1
    private const val MAX_SECONDS = 300

    private class Rule(val match: String, val seconds: Int)
    private class Running(val label: String, val endsAtMs: Long)

    private var parsedFrom = ""
    private var rules: List<Rule> = emptyList()
    private val running = ArrayList<Running>()

    private fun cfg() = FamilyConfigManager.config.utilities

    /** Parsed only when the list changes: the chat hook sees every line. */
    private fun rules(): List<Rule> {
        val json = cfg().chatTimerList
        if (json != parsedFrom) {
            parsedFrom = json
            rules = try {
                JsonParser.parseString(json).asJsonArray.mapNotNull { entry ->
                    val row = entry.asJsonObject
                    val match = row.get("match")?.asString?.trim().orEmpty()
                    val seconds = row.get("seconds")?.asString?.trim()?.toIntOrNull()
                    if (match.isEmpty() || seconds == null) null
                    else Rule(match, seconds.coerceIn(MIN_SECONDS, MAX_SECONDS))
                }
            } catch (e: Exception) { emptyList() }
        }
        return rules
    }

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, _ ->
            if (cfg().chatTimers) {
                val plain = message.string.replace(COLOR_CODE_REGEX, "").trim()
                for (rule in rules()) {
                    if (plain.contains(rule.match, ignoreCase = true)) start(rule)
                }
            }
            true
        }

        // Expiry runs on the tick, not the draw, so a timer still finishes
        // while the HUD is hidden.
        ClientTickEvents.END_CLIENT_TICK.register {
            if (running.isEmpty()) return@register
            val now = System.currentTimeMillis()
            val done = running.filter { it.endsAtMs <= now }
            if (done.isEmpty()) return@register
            running.removeAll(done)
            for (timer in done) FaChat.send("§eTime's up §7— ${timer.label}")
        }

        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("familyaddons", "chat_timers"),
            HudElement { ctx, _ -> renderHud(ctx) }
        )
    }

    private fun start(rule: Rule) {
        val label = label(rule.match)
        running.removeIf { it.label == label }
        running.add(Running(label, System.currentTimeMillis() + rule.seconds * 1000L))
    }

    /** The line you watched for is the timer's name, cut to something that fits. */
    private fun label(match: String): String =
        if (match.length <= 22) match else match.take(21) + "…"

    private fun renderHud(ctx: GuiGraphicsExtractor) {
        if (!cfg().chatTimers || running.isEmpty()) return
        val client = Minecraft.getInstance()
        val tr = client.font
        val now = System.currentTimeMillis()

        val scale = cfg().chatTimerHudScale.toFloatOrNull() ?: 1f
        val hudX = cfg().chatTimerHudX.takeIf { it >= 0 } ?: 10
        val hudY = cfg().chatTimerHudY.takeIf { it >= 0 } ?: 60
        val lineHeight = (tr.lineHeight + 2) * scale
        val matrices = ctx.pose()

        running.sortedBy { it.endsAtMs }.forEachIndexed { i, timer ->
            // Seconds and hundredths, counting down live: 12.34, 1.07, 0.00.
            val left = (timer.endsAtMs - now).coerceAtLeast(0L)
            val time = "%.2f".format(left / 1000.0)
            matrices.pushMatrix()
            matrices.translate(hudX.toFloat(), hudY + i * lineHeight)
            matrices.scale(scale, scale)
            ctx.text(tr, Component.literal("§eTime: §f$time"), 0, 0, -1, true)
            matrices.popMatrix()
        }
    }
}
