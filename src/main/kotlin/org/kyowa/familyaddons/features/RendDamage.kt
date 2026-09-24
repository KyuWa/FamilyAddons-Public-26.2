package org.kyowa.familyaddons.features

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.world.entity.monster.cubemob.MagmaCube
import org.kyowa.familyaddons.COLOR_CODE_REGEX
import org.kyowa.familyaddons.config.FamilyConfigManager
import org.kyowa.familyaddons.util.FaChat
import java.util.Locale

/**
 * Rend damage: how hard each Rend pull hit Kuudra in the DPS phase, and when.
 *
 * Untested so far. Kuudra is the giant
 * magma cube; in the DPS phase his synced health starts at 25,000 and each unit
 * stands for 9,600 real HP (240M at full). A drop of more than 2,083 units in one
 * sample (20M) can only be a pull, so that is what gets announced: "Pull at 2.4s
 * for 27.6M", the time coloured by how quick it was and the damage by how big.
 *
 * Over the original: the clock starts when Kuudra surfaces (not when the run
 * started), samples are taken every tick straight from the entity rather than
 * from a packet that happens to arrive often, a pull is never counted twice, and
 * KUUDRA DOWN prints a summary (pulls, biggest, first).
 */
object RendDamage {

    private const val KUUDRA_MIN_WIDTH = 14.5f
    private const val DPS_HP_MAX = 25000f
    /** Real HP per synced unit: 240M / 25,000. */
    private const val HP_PER_UNIT = 9600.0
    /** A single-sample drop above this is a pull (20M). */
    private const val PULL_UNITS = 20_000_000.0 / HP_PER_UNIT
    private const val RUN_START_MSG = "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!"

    private class Pull(val atMs: Long, val units: Float)

    private var lastHp = -1f
    private var dpsStartMs = 0L
    private var done = false
    private val pulls = ArrayList<Pull>()

    private fun cfg() = FamilyConfigManager.config.kuudra

    private fun reset() { lastHp = -1f; dpsStartMs = 0L; done = false; pulls.clear() }

    fun register() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }

        ClientReceiveMessageEvents.ALLOW_GAME.register { message, _ ->
            val plain = message.string.replace(COLOR_CODE_REGEX, "").trim()
            when (plain) {
                RUN_START_MSG -> reset()
                "KUUDRA DOWN!" -> if (!done) { done = true; summary() }
            }
            true
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!cfg().rendDamage || done) return@register
            val level = client.level ?: return@register
            var kuudra: MagmaCube? = null
            for (e in level.entitiesForRendering()) if (e is MagmaCube && e.bbWidth >= KUUDRA_MIN_WIDTH) { kuudra = e; break }
            if (kuudra == null) return@register
            val hp = kuudra.health
            // Before the DPS phase Hypixel walks the health down from 100k; only
            // the 25k-and-under stretch is the fight.
            if (hp > DPS_HP_MAX) { lastHp = -1f; return@register }
            val now = System.currentTimeMillis()
            if (dpsStartMs == 0L) dpsStartMs = now
            if (lastHp >= 0f) {
                val diff = lastHp - hp
                if (diff > PULL_UNITS) {
                    val t = (now - dpsStartMs) / 1000.0
                    pulls.add(Pull(now, diff))
                    FaChat.send("§7Pull at ${timeColor(t)}${fmtTime(t)} §7for ${damageColor(diff)}${fmtDamage(diff)}")
                }
            }
            lastHp = hp
        }
    }

    private fun summary() {
        if (!cfg().rendDamage || !cfg().rendSummary || pulls.isEmpty()) return
        val best = pulls.maxBy { it.units }
        val first = pulls.first()
        val total = pulls.sumOf { it.units.toDouble() }.toFloat()
        FaChat.send("§6Rend§7: §f${pulls.size} §7pull${if (pulls.size == 1) "" else "s"}, " +
            "§f${fmtDamage(total)} §7total, biggest ${damageColor(best.units)}${fmtDamage(best.units)}§7, " +
            "first at ${timeColor((first.atMs - dpsStartMs) / 1000.0)}${fmtTime((first.atMs - dpsStartMs) / 1000.0)}")
    }

    private fun fmtTime(t: Double) = String.format(Locale.ROOT, "%.2fs", t)
    private fun fmtDamage(units: Float): String {
        val hp = units * HP_PER_UNIT
        return if (hp >= 1_000_000_000) String.format(Locale.ROOT, "%.2fB", hp / 1e9) else String.format(Locale.ROOT, "%.1fM", hp / 1e6)
    }
    private fun timeColor(t: Double) = when { t < 2.7 -> "§a"; t <= 3.5 -> "§e"; else -> "§c" }
    private fun damageColor(units: Float): String {
        val m = units * HP_PER_UNIT / 1e6
        return when { m > 70 -> "§a"; m > 40 -> "§e"; else -> "§c" }
    }
}
