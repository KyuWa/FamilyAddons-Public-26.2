package org.kyowa.familyaddons.commands

import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.kyowa.familyaddons.features.AutoUpdater
import org.kyowa.familyaddons.features.NameSync
import org.kyowa.familyaddons.features.PartyRepCheck
import org.kyowa.familyaddons.party.PartyTracker
import org.kyowa.familyaddons.util.FaChat

/**
 * `/fa` and everything under it. `/familyaddons` is the same tree, and the
 * client lowercases either one before it is parsed, so any capitalisation works.
 */
object TestCommand {

    var openGuiNextTick = false
    var openConfigNextTick = false

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            val fa = dispatcher.register(
                literal("fa")
                    // /fa — the settings
                    .executes { openConfigNextTick = true; 1 }

                    // /fa gui — move the HUD around
                    .then(literal("gui").executes { openGuiNextTick = true; 1 })

                    // /fa update — ask whether there is a newer version
                    .then(literal("update").executes { AutoUpdater.checkNow(); 1 })

                    // /fa party — who the mod thinks is in your party
                    .then(literal("party").executes {
                        val p = Minecraft.getInstance().player ?: return@executes 1
                        val members = PartyTracker.members
                        if (members.isEmpty()) {
                            p.sendSystemMessage(FaChat.prefixed("§7No party."))
                        } else {
                            p.sendSystemMessage(FaChat.prefixed("§eParty (${members.size}):"))
                            for (name in members.keys) {
                                val mark = if (name.equals(PartyTracker.leader, true)) " §6★" else ""
                                p.sendSystemMessage(Component.literal("  §f$name$mark"))
                            }
                        }
                        1
                    })

                    // /fa checkrep — the party's Kuudra runs, from the public API
                    .then(literal("checkrep")
                        .then(argument("player", StringArgumentType.word())
                            .executes { ctx -> PartyRepCheck.fetchRep(StringArgumentType.getString(ctx, "player")); 1 })
                        .executes {
                            val me = Minecraft.getInstance().user?.name
                            if (me != null) PartyRepCheck.fetchRep(me)
                            1
                        })

                    // /fa name … — your own shared display name
                    .then(literal("name")
                        .then(literal("preview").executes { NameSync.preview(); 1 })
                        .then(literal("submit").executes { NameSync.submit(); 1 })
                        .then(literal("remove").executes { NameSync.remove(); 1 })
                        .then(literal("status").executes { NameSync.checkStatus(); 1 })
                        .executes { NameSync.help(); 1 })

                    .then(literal("help").executes { help(); 1 })
            )
            // the long spelling opens the same tree
            dispatcher.register(literal("familyaddons").redirect(fa).executes { openConfigNextTick = true; 1 })
        }
    }

    private fun help() {
        val p = Minecraft.getInstance().player ?: return
        p.sendSystemMessage(FaChat.prefixed("§ecommands"))
        for (line in listOf(
            "§6/fa §7— open the settings",
            "§6/fa gui §7— move the HUD elements",
            "§6/fa party §7— who is in your party",
            "§6/fa checkrep [player] §7— how many Kuudra runs they have",
            "§6/fa name §7— your shared display name",
            "§6/fa update §7— check for a newer version",
        )) p.sendSystemMessage(Component.literal("  $line"))
    }
}
