package org.kyowa.familyaddons.util

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import org.kyowa.familyaddons.FamilyAddons

/**
 * Where on SkyBlock the player is, from two sources:
 *  1. The Hypixel Mod API location event (map / mode), when the
 *     hypixel-mod-api mod is installed. Authoritative and instant.
 *  2. The tab list "Area: …" line, always available, used whenever the API
 *     has not spoken since the last world change (mod missing, API down).
 *
 * [map] and [mode] are cleared on every world change so a stale answer from
 * the previous server can never win over the tab list.
 */
object HypixelLocation {

    @Volatile var map: String? = null
        internal set
    @Volatile var mode: String? = null
        internal set
    @Volatile var apiAvailable = false
        private set

    fun register() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> map = null; mode = null }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> map = null; mode = null }
        if (FabricLoader.getInstance().isModLoaded("hypixel-mod-api")) {
            try {
                HypixelApiBridge.init()
                apiAvailable = true
                FamilyAddons.LOGGER.info("HypixelLocation: Hypixel Mod API location events subscribed")
            } catch (t: Throwable) {
                FamilyAddons.LOGGER.warn("HypixelLocation: Mod API present but hook failed, using tab list only: ${t.message}")
            }
        } else {
            FamilyAddons.LOGGER.info("HypixelLocation: hypixel-mod-api not installed, using tab list only")
        }
    }

    /** Current area name: the API map if it has reported for this world, else the tab list's "Area:" line. */
    fun areaName(): String? = map ?: tabArea()

    /** The "Area: …" line Hypixel puts in the tab list. */
    private fun tabArea(): String? {
        val entries = net.minecraft.client.Minecraft.getInstance().connection?.onlinePlayers ?: return null
        for (e in entries) {
            val line = e.tabListDisplayName?.string?.replace(org.kyowa.familyaddons.COLOR_CODE_REGEX, "")?.trim() ?: continue
            if (line.startsWith("Area:", true)) return line.substringAfter(":").trim()
        }
        return null
    }

    /** "api" or "tab", for debug output. */
    fun source(): String = if (map != null) "api" else "tab"
}
