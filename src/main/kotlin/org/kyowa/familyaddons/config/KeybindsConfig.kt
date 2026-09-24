package org.kyowa.familyaddons.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.*
import org.lwjgl.glfw.GLFW

class KeybindsConfig {

    @Expose @JvmField
    @ConfigOption(name = "GFS", desc = "Get-from-sack keybinds: a key per item that tops you back up from your sacks.")
    @ConfigEditorAccordion(id = 30)
    var pearlAccordion = false

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Ender Pearl", desc = "Enable the GFS Ender Pearl keybind.")
    @ConfigEditorBoolean
    var pearlEnabled = true

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Ender Pearl Key", desc = "Press to /gfs ENDER_PEARL up to the amount below.")
    @ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var pearlKey = GLFW.GLFW_KEY_UNKNOWN

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Ender Pearl Amount", desc = "How many Ender Pearls to keep in your inventory. The keybind tops you up to this from your sacks.")
    @ConfigEditorSlider(minValue = 1f, maxValue = 16f, minStep = 1f)
    var pearlAmount = 16f

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Superboom TNT", desc = "Enable the GFS Superboom TNT keybind.")
    @ConfigEditorBoolean
    var superboomEnabled = true

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Superboom TNT Key", desc = "Press to /gfs SUPERBOOM_TNT up to the amount below.")
    @ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var superboomKey = GLFW.GLFW_KEY_UNKNOWN

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Superboom TNT Amount", desc = "How many Superboom TNT to keep in your inventory. The keybind tops you up to this from your sacks.")
    @ConfigEditorSlider(minValue = 1f, maxValue = 64f, minStep = 1f)
    var superboomAmount = 64f

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Inflatable Jerry", desc = "Enable the GFS Inflatable Jerry keybind.")
    @ConfigEditorBoolean
    var jerryEnabled = true

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Inflatable Jerry Key", desc = "Press to /gfs INFLATABLE_JERRY up to the amount below.")
    @ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var jerryKey = GLFW.GLFW_KEY_UNKNOWN

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Inflatable Jerry Amount", desc = "How many Inflatable Jerries to keep in your inventory. The keybind tops you up to this from your sacks.")
    @ConfigEditorSlider(minValue = 1f, maxValue = 64f, minStep = 1f)
    var jerryAmount = 64f

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Decoy", desc = "Enable the GFS Decoy keybind.")
    @ConfigEditorBoolean
    var decoyEnabled = true

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Decoy Key", desc = "Press to /gfs DECOY up to the amount below.")
    @ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var decoyKey = GLFW.GLFW_KEY_UNKNOWN

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Decoy Amount", desc = "How many Decoys to keep in your inventory. The keybind tops you up to this from your sacks.")
    @ConfigEditorSlider(minValue = 1f, maxValue = 64f, minStep = 1f)
    var decoyAmount = 64f

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Toxic Arrow Poison", desc = "Enable the GFS Toxic Arrow Poison keybind.")
    @ConfigEditorBoolean
    var tapEnabled = true

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Toxic Arrow Poison Key", desc = "Press to /gfs TOXIC_ARROW_POISON up to the amount below.")
    @ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var tapKey = GLFW.GLFW_KEY_UNKNOWN

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Toxic Arrow Poison Amount", desc = "How many Toxic Arrow Poison (192 = 3 stacks) to keep in your inventory. The keybind tops you up to this from your sacks.")
    @ConfigEditorSlider(minValue = 1f, maxValue = 192f, minStep = 1f)
    var tapAmount = 192f

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Twilight Arrow Poison", desc = "Enable the GFS Twilight Arrow Poison keybind.")
    @ConfigEditorBoolean
    var twapEnabled = true

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Twilight Arrow Poison Key", desc = "Press to /gfs TWILIGHT_ARROW_POISON up to the amount below.")
    @ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var twapKey = GLFW.GLFW_KEY_UNKNOWN

    @Expose @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Twilight Arrow Poison Amount", desc = "How many Twilight Arrow Poison to keep in your inventory. The keybind tops you up to this from your sacks.")
    @ConfigEditorSlider(minValue = 1f, maxValue = 64f, minStep = 1f)
    var twapAmount = 64f

    /** JSON list of {item, amount, key}: any sack item by its SkyBlock id, topped up to amount on its key. */
    @Expose @JvmField
    var customGfs = "[]"

    @JvmField
    @ConfigAccordionId(id = 30)
    @ConfigOption(name = "Custom GFS Items", desc = "Add your own GFS keybinds: any sack item by its id (ender_pearl, superboom_tnt, ...), the amount to keep, and the key that tops it up. As many as you like.")
    @ConfigEditorButton(buttonText = "Edit")
    var customGfsEdit: Runnable = Runnable { }
}
