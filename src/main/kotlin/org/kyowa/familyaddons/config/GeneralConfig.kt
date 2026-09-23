package org.kyowa.familyaddons.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigAccordionId
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorAccordion
import org.kyowa.familyaddons.commands.TestCommand

class GeneralConfig {
    @Expose @JvmField
    @ConfigOption(name = "HUD", desc = "")
    @ConfigEditorAccordion(id = 1)
    var hudAccordion = false

    @JvmField
    @ConfigAccordionId(id = 1)
    @ConfigOption(name = "HUD Editor", desc = "Open the HUD editor to move and resize HUD elements.")
    @ConfigEditorButton(buttonText = "Open")
    var openHudEditor: Runnable = Runnable {
        TestCommand.openGuiNextTick = true
    }

    /** Version that last ran, so the updater can say "updated from X to Y" once. Not in the GUI. */
    @Expose @JvmField
    var lastRunVersion = ""

    @Expose @JvmField
    @ConfigOption(name = "Disconnect", desc = "")
    @ConfigEditorAccordion(id = 3)
    var disconnectAccordion = false

    @Expose @JvmField
    @ConfigAccordionId(id = 3)
    @ConfigOption(name = "Confirm Disconnect", desc = "Clicking Disconnect in the pause menu asks first instead of leaving straight away. The Disconnect button on that prompt is locked for two seconds so the same click cannot fall through it.")
    @ConfigEditorBoolean
    var confirmDisconnect = true

    @Expose @JvmField
    @ConfigAccordionId(id = 3)
    @ConfigOption(name = "Disconnect Lock Timer", desc = "Lock the Disconnect button on that prompt for two seconds, with a countdown. Off: the prompt still asks, but Disconnect can be clicked straight away.")
    @ConfigEditorBoolean
    var confirmDisconnectTimer = true

    @Expose @JvmField
    @ConfigOption(name = "Shared Mob Data", desc = "")
    @ConfigEditorAccordion(id = 4)
    var mobDataAccordion = false

    @Expose @JvmField
    @ConfigAccordionId(id = 4)
    @ConfigOption(name = "Share Mob Data", desc = "Help the mod learn mobs it does not highlight yet. While you play it notes what the mobs around you look like (entity type, size, max health, the head texture they wear, their equipment) and sends each distinct shape once, so a mob that goes unhighlighted can be fixed for everyone. Nothing about you is sent: no username, no UUID, no coordinates, no chat, and other players are skipped entirely. Turn it off and this client sends nothing.")
    @ConfigEditorBoolean
    var entityCatalog = true

    @Expose @JvmField
    @ConfigAccordionId(id = 4)
    @ConfigOption(name = "Use Shared Mob Rules", desc = "Apply the mob rules FamilyAddons publishes, so mobs that went unhighlighted start working without waiting for a mod update. Turn this off to highlight only from what is built into this version.")
    @ConfigEditorBoolean
    var useRemoteRules = true

    @Expose @JvmField
    @ConfigOption(name = "Updates", desc = "")
    @ConfigEditorAccordion(id = 5)
    var updatesAccordion = false

    @Expose @JvmField
    @ConfigAccordionId(id = 5)
    @ConfigOption(name = "Update Check", desc = "Say in chat when a newer version is out, once after you join. Nothing is ever downloaded or installed.")
    @ConfigEditorBoolean
    var updateCheck = true

    @Expose @JvmField
    @ConfigAccordionId(id = 1)
    @ConfigOption(name = "Count Me", desc = "Tell KyoWaa's server that this account is running the mod, so the user list is right. Your name, UUID and the two version numbers, nothing else, on join and once a minute. Off means nothing is sent.")
    @ConfigEditorBoolean
    var usageCount: Boolean = true

    /** What the config screen remembers between sessions (chosen look, panel positions). Not shown in the GUI. */
    @Expose @JvmField
    var uiMemory: MutableMap<String, String> = mutableMapOf()
}
