package org.kyowa.familyaddons.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.*

class PartyConfig {
    @Expose @JvmField
    @ConfigOption(name = "Rep Check", desc = "")
    @ConfigEditorAccordion(id = 11)
    var repCheckAccordion = false

    @Expose @JvmField
    @ConfigAccordionId(id = 11)
    @ConfigOption(name = "Rep Check", desc = "Auto check Crimson Isle rep when someone joins party.")
    @ConfigEditorBoolean
    var repCheckEnabled = false

    // Party Commands accordion
    @Expose @JvmField
    @ConfigOption(name = "Party Commands", desc = "")
    @ConfigEditorAccordion(id = 10)
    var partyCommandsAccordion = false

    @Expose @JvmField
    @ConfigAccordionId(id = 10)
    @ConfigOption(name = "Enable Party Commands", desc = "Master toggle for all party commands below.")
    @ConfigEditorBoolean
    var commandsEnabled = true

    @Expose @JvmField
    @ConfigAccordionId(id = 10)
    @ConfigOption(name = "Whitelist", desc = "Comma-separated IGNs that can use party commands. Leave blank to allow anyone.")
    @ConfigEditorText
    var partyWhitelist = ""

    @Expose @JvmField
    @ConfigAccordionId(id = 10)
    @ConfigOption(name = "pt", desc = "\"pt\" or \"pt <name>\" in party chat transfers the party to the sender, or to <name>. Whitelisted players only. Only the leader's client sends it.")
    @ConfigEditorBoolean
    var ptEnabled = true

    @Expose @JvmField
    @ConfigAccordionId(id = 10)
    @ConfigOption(name = "warp", desc = "\"warp\" in party chat warps the party. Whitelisted players only. Only the leader's client sends it.")
    @ConfigEditorBoolean
    var warpEnabled = true

    @Expose @JvmField
    @ConfigAccordionId(id = 10)
    @ConfigOption(name = "k / kick", desc = "\"k <name>\" or \"kick <name>\" in party chat kicks that member. Whitelisted players only. Only the leader's client sends it.")
    @ConfigEditorBoolean
    var kickEnabled = true

    @Expose @JvmField
    @ConfigAccordionId(id = 10)
    @ConfigOption(name = "inv / invite", desc = "\"inv <name>\" or \"invite <name>\" in party chat invites that player. Whitelisted players only. Sent by the leader's or a moderator's client, since Hypixel takes invites from both.")
    @ConfigEditorBoolean
    var invEnabled = true

    @Expose @JvmField
    @ConfigAccordionId(id = 10)
    @ConfigOption(name = "allinv / ai", desc = "\"allinv\" or \"ai\" in party chat toggles the All Invite setting. Whitelisted players only. Only the leader's client sends it.")
    @ConfigEditorBoolean
    var allinvEnabled = true

    @Expose @JvmField
    @ConfigAccordionId(id = 10)
    @ConfigOption(name = "calc / c", desc = "\"calc <expression>\" or \"c <expression>\" in party chat posts the result. Anyone's client answers.")
    @ConfigEditorBoolean
    var calcEnabled = true
}
