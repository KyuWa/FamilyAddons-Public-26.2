package org.kyowa.familyaddons.features

import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.minecraft.client.Minecraft
import org.kyowa.familyaddons.config.FamilyConfigManager
import org.lwjgl.glfw.GLFW

/**
 * Command shortcuts: the three built-in aliases, plus whatever the user added in
 * Utilities > Command Shortcuts > Edit (alias, the command it runs, and an
 * optional key that runs it without opening chat). Typing /alias sends the
 * command instead; the alias never reaches the server.
 */
object CmdShortcut {

    private data class Shortcut(val name: String, val target: String)

    private val builtIn = listOf(
        Shortcut("museum", "warp museum"),
        Shortcut("pw",     "p warp"),
        Shortcut("koff",   "p kickoffline"),
    )

    private class Custom(val alias: String, val command: String, val key: Int) { var wasDown = false }
    private var customJson = ""
    private var custom: List<Custom> = emptyList()

    private fun enabled() = FamilyConfigManager.config.utilities.commandShortcuts

    private fun customEntries(): List<Custom> {
        val json = FamilyConfigManager.config.utilities.commandShortcutList
        if (json == customJson) return custom
        customJson = json
        custom = runCatching {
            com.google.gson.JsonParser.parseString(json).asJsonArray.mapNotNull { e ->
                val o = e.asJsonObject
                val alias = o.get("alias")?.asString?.trim()?.removePrefix("/")?.lowercase() ?: return@mapNotNull null
                val command = o.get("command")?.asString?.trim()?.removePrefix("/") ?: return@mapNotNull null
                if (alias.isEmpty() || command.isEmpty()) return@mapNotNull null
                Custom(alias, command, o.get("key")?.asString?.toIntOrNull() ?: GLFW.GLFW_KEY_UNKNOWN)
            }
        }.getOrDefault(emptyList())
        return custom
    }

    private fun send(command: String) {
        Minecraft.getInstance().player?.connection?.sendCommand(command.removePrefix("/"))
    }

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            for (sc in builtIn) {
                dispatcher.register(
                    literal(sc.name).executes { ctx ->
                        if (!enabled()) return@executes 0
                        ctx.source.player.connection.sendCommand(sc.target)
                        1
                    }
                )
            }
        }

        // Custom aliases are looked up as the command leaves the client, so they
        // can be added and changed without re-registering anything.
        ClientSendMessageEvents.ALLOW_COMMAND.register { command ->
            val typed = command.trim()
            val name = typed.substringBefore(' ').lowercase()
            val rest = typed.substringAfter(' ', "")
            // /FA, /FamilyAddons, any mix of case: Brigadier only knows the lowercase
            // literal, so re-send it lowercased. The re-send comes back through here
            // already lowercase and passes straight on, no loop.
            if ((name == "fa" || name == "familyaddons") && typed.substringBefore(' ') != name) {
                send(if (rest.isEmpty()) name else "$name $rest")
                return@register false
            }
            if (!enabled()) return@register true
            val hit = customEntries().firstOrNull { it.alias == name } ?: return@register true
            send(if (rest.isEmpty()) hit.command else hit.command + " " + rest)
            false
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!enabled()) return@register
            val entries = customEntries()
            if (entries.isEmpty()) return@register
            val screenOpen = client.gui.screen() != null
            val handle = client.window.handle()
            for (c in entries) {
                val down = !screenOpen && c.key != GLFW.GLFW_KEY_UNKNOWN && GLFW.glfwGetKey(handle, c.key) == GLFW.GLFW_PRESS
                if (down && !c.wasDown) send(c.command)
                c.wasDown = down
            }
        }
    }
}
