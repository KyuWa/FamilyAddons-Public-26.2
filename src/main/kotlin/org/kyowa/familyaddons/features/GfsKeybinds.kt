package org.kyowa.familyaddons.features

import org.kyowa.familyaddons.util.FaChat

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Items
import net.minecraft.network.chat.Component
import org.kyowa.familyaddons.config.FamilyConfigManager
import org.lwjgl.glfw.GLFW

object GfsKeybinds {

    // Track previous key states to detect press edges
    private var pearlWasDown = false
    private var superboomWasDown = false
    private var jerryWasDown = false
    private var decoyWasDown = false
    private var tapWasDown = false
    private var twapWasDown = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            // Don't fire if any screen is open (includes chat, inventory, etc.)
            if (client.gui.screen() != null) {
                pearlWasDown = false
                superboomWasDown = false
                jerryWasDown = false
                decoyWasDown = false
                tapWasDown = false
                twapWasDown = false
                return@register
            }

            val cfg = FamilyConfigManager.config.keybinds
            val handle = client.window.handle()

            fun isDown(key: Int) = key != GLFW.GLFW_KEY_UNKNOWN &&
                GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS

            val pearlDown = isDown(cfg.pearlKey)
            val superboomDown = isDown(cfg.superboomKey)
            val jerryDown = isDown(cfg.jerryKey)
            val decoyDown = isDown(cfg.decoyKey)
            val tapDown = isDown(cfg.tapKey)
            val twapDown = isDown(cfg.twapKey)

            // Amounts come from the sliders; each is clamped to the item's stack limit.
            if (pearlDown && !pearlWasDown && cfg.pearlEnabled)
                gfs(client, "ENDER_PEARL", amount(cfg.pearlAmount, 16), useVanillaId = true)
            if (superboomDown && !superboomWasDown && cfg.superboomEnabled)
                gfs(client, "SUPERBOOM_TNT", amount(cfg.superboomAmount, 64))
            if (jerryDown && !jerryWasDown && cfg.jerryEnabled)
                gfs(client, "INFLATABLE_JERRY", amount(cfg.jerryAmount, 64))
            if (decoyDown && !decoyWasDown && cfg.decoyEnabled)
                gfs(client, "DECOY", amount(cfg.decoyAmount, 64))
            if (tapDown && !tapWasDown && cfg.tapEnabled)
                gfs(client, "TOXIC_ARROW_POISON", amount(cfg.tapAmount, 192))
            if (twapDown && !twapWasDown && cfg.twapEnabled)
                gfs(client, "TWILIGHT_ARROW_POISON", amount(cfg.twapAmount, 64))

            tickCustom(client, cfg.customGfs, ::isDown)

            pearlWasDown = pearlDown
            superboomWasDown = superboomDown
            jerryWasDown = jerryDown
            decoyWasDown = decoyDown
            tapWasDown = tapDown
            twapWasDown = twapDown
        }
    }

    private fun amount(slider: Float, max: Int): Int = slider.toInt().coerceIn(1, max)

    // ── custom entries from the Keybinds > Custom GFS editor ─────────────
    private class Custom(val item: String, val amount: Int, val key: Int) { var wasDown = false }
    private var customJson = ""
    private var custom: List<Custom> = emptyList()

    /** Parses the JSON list once per change; keeps each entry's key state for edge detection. */
    private fun customEntries(json: String): List<Custom> {
        if (json == customJson) return custom
        customJson = json
        custom = runCatching {
            com.google.gson.JsonParser.parseString(json).asJsonArray.mapNotNull { e ->
                val o = e.asJsonObject
                val item = o.get("item")?.asString?.trim()?.uppercase()?.replace(' ', '_') ?: return@mapNotNull null
                if (item.isEmpty()) return@mapNotNull null
                Custom(item, o.get("amount")?.asString?.toIntOrNull()?.coerceIn(1, 64 * 36) ?: 1, o.get("key")?.asString?.toIntOrNull() ?: GLFW.GLFW_KEY_UNKNOWN)
            }
        }.getOrDefault(emptyList())
        return custom
    }

    private fun tickCustom(client: Minecraft, json: String, isDown: (Int) -> Boolean) {
        for (c in customEntries(json)) {
            val down = isDown(c.key)
            if (down && !c.wasDown) gfs(client, c.item, c.amount, matchVanillaToo = true)
            c.wasDown = down
        }
    }

    private fun gfs(client: Minecraft, sbId: String, max: Int, useVanillaId: Boolean = false, matchVanillaToo: Boolean = false) {
        val player = client.player ?: return
        var current = 0

        for (i in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(i)
            if (stack.isEmpty) continue

            val matches = if (useVanillaId) {
                stack.item == Items.ENDER_PEARL
            } else {
                getSkyblockId(stack) == sbId ||
                    // a custom entry may name a vanilla item (ender_pearl) that carries no SkyBlock id
                    (matchVanillaToo && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.item).path.equals(sbId, ignoreCase = true))
            }

            if (matches) current += stack.count
        }

        val needed = max - current
        if (needed <= 0) {
            FaChat.send("§7Already have $current §8(want $max)")
            return
        }

        player.connection.sendChat("/gfs $sbId $needed")
    }

    private fun getSkyblockId(stack: net.minecraft.world.item.ItemStack): String? {
        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        val nbt = customData.copyTag()
        return nbt.getString("id").orElse(null)?.ifBlank { null }
            ?: nbt.getCompoundOrEmpty("ExtraAttributes").getString("id").orElse(null)?.ifBlank { null }
    }

    private fun chat(player: net.minecraft.client.player.LocalPlayer, msg: String) {
        player.sendSystemMessage(Component.literal(msg))
    }
}
