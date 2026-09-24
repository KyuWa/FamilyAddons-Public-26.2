package org.kyowa.familyaddons.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigAccordionId
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorAccordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import org.lwjgl.glfw.GLFW

class UtilitiesConfig {
    @Expose @JvmField
    @ConfigOption(name = "Utilities", desc = "")
    @ConfigEditorAccordion(id = 81)
    var utilitiesAccordion = false

    @Expose @JvmField
    @ConfigAccordionId(id = 81)
    @ConfigOption(name = "Command Shortcuts", desc = "Enable short command aliases: /museum, /pw, /koff")
    @ConfigEditorBoolean
    var commandShortcuts = true

    @Expose @JvmField
    @ConfigAccordionId(id = 81)
    @ConfigOption(name = "Sign Math", desc = "Evaluate math expressions typed on signs before sending.")
    @ConfigEditorBoolean
    var signMath = true


    @Expose @JvmField
    @ConfigAccordionId(id = 81)
    @ConfigOption(name = "Lock Hotbar Scroll", desc = "Prevent hotbar scroll from wrapping around (slot 1 won't go to slot 9 and vice versa).")
    @ConfigEditorBoolean
    var lockHotbarScroll = false

    @Expose @JvmField
    @ConfigAccordionId(id = 81)
    @ConfigOption(name = "Block Composter Sack Insert", desc = "In the Composter menu, the Insert Crops from Sacks cauldron cannot be clicked, so a misclick never empties your sacks into it.")
    @ConfigEditorBoolean
    var blockComposterSackInsert = false

    @Expose @JvmField
    @ConfigAccordionId(id = 81)
    @ConfigOption(name = "Arachne Timer", desc = "Show a countdown timer hologram when an Arachne Crystal is placed.")
    @ConfigEditorBoolean
    var arachneTimer = false




    // ── Camera ────────────────────────────────────────────────────────
    @Expose @JvmField
    @ConfigOption(name = "Camera", desc = "")
    @ConfigEditorAccordion(id = 80)
    var cameraAccordion = false


    @Expose @JvmField
    @ConfigAccordionId(id = 80)
    @ConfigOption(name = "No Front Camera", desc = "Skip 3rd-person front view when cycling perspectives — F5 toggles only between 1st and 3rd person.")
    @ConfigEditorBoolean
    var noFrontCamera = false

    @Expose @JvmField
    @ConfigAccordionId(id = 80)
    @ConfigOption(name = "Custom Distance", desc = "Use a custom third-person camera distance instead of vanilla 4.0.")
    @ConfigEditorBoolean
    var cameraDistEnabled = false

    @Expose @JvmField
    @ConfigAccordionId(id = 80)
    @ConfigOption(name = "Distance", desc = "Distance of the third-person camera from the player. Vanilla is 4.0. Has no effect unless Custom Distance is enabled.")
    @ConfigEditorSlider(minValue = 3f, maxValue = 12f, minStep = 0.1f)
    var cameraDist = 4f




    @Expose @JvmField
    @ConfigOption(name = "Chat Timers", desc = "")
    @ConfigEditorAccordion(id = 82)
    var chatTimersAccordion = false

    @Expose @JvmField
    @ConfigAccordionId(id = 82)
    @ConfigOption(name = "Enable", desc = "Start a countdown on screen whenever a line of chat contains text you chose. Move the timers with /fa gui.")
    @ConfigEditorBoolean
    var chatTimers = false

    @JvmField
    @ConfigAccordionId(id = 82)
    @ConfigOption(name = "Timers", desc = "Your timers: a bit of the chat line to watch for, and how many seconds to count down (1-300).")
    @ConfigEditorButton(buttonText = "Edit")
    var chatTimerEdit: Runnable = Runnable { }

    /** JSON list of {match, seconds}, edited from the Timers option. Not shown in the GUI. */
    @Expose @JvmField
    var chatTimerList = "[]"

    @Expose @JvmField var chatTimerHudX = -1
    @Expose @JvmField var chatTimerHudY = -1
    @Expose @JvmField var chatTimerHudScale = "1.0"

    /** JSON list of {alias, command, key}, edited from the Command Shortcuts option. Not shown in the GUI. */
    @Expose @JvmField
    var commandShortcutList = "[]"
}
