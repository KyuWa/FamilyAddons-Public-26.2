package org.kyowa.familyaddons.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigAccordionId
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorAccordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

/** The former FamilyStorage mod: every ender chest page and backpack shown side by side. */
class StorageConfig {
    @Expose @JvmField
    @ConfigOption(name = "Family Storage", desc = "")
    @ConfigEditorAccordion(id = 1)
    var overlayAccordion = false

    @Expose @JvmField
    @ConfigAccordionId(id = 1)
    @ConfigOption(name = "Enable", desc = "When an ender chest page or backpack is open, show every page and backpack side by side, with a search box. /fs toggles this too. Reopen the storage to apply.")
    @ConfigEditorBoolean
    var enabled = true

    @Expose @JvmField
    @ConfigAccordionId(id = 1)
    @ConfigOption(name = "Container Value", desc = "Show what the page you have open is worth beside its name, and list what is in it when you hover the figure. Bazaar goods are priced at insta-sell and auction items at what they sell for, so the total is a floor.")
    @ConfigEditorBoolean
    var containerValue = true

    @Expose @JvmField
    @ConfigAccordionId(id = 1)
    @ConfigOption(name = "Panel Opacity", desc = "How solid the panels are, 0 to 100. /fs alpha <n> sets it too.")
    @ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 1f)
    var alpha = 100f

    /** Set once the old mod's on/off and opacity were carried over. Not shown in the GUI. */
    @Expose @JvmField
    var migrated = false
}
