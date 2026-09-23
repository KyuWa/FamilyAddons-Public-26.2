package org.kyowa.familyaddons.util

import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot
import org.kyowa.familyaddons.COLOR_CODE_REGEX

/** The sidebar as plain lines, top to bottom, colour codes stripped. */
object Scoreboard {

    fun lines(): List<String> {
        val mc = Minecraft.getInstance()
        val board = mc.level?.scoreboard ?: return emptyList()
        val objective = board.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return emptyList()
        val out = ArrayList<String>()
        for (entry in board.listPlayerScores(objective)) {
            val owner = entry.owner()
            val team = board.getPlayersTeam(owner)
            val line = if (team != null) {
                team.playerPrefix.string + team.playerSuffix.string
            } else {
                owner
            }
            out.add(line.replace(COLOR_CODE_REGEX, "").trim())
        }
        return out.reversed()
    }
}
