package org.kyowa.familyaddons.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigAccordionId
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorAccordion

class ChatFiltersConfig {
    @Expose @JvmField
    @ConfigOption(name = "Chat Filters", desc = "")
    @ConfigEditorAccordion(id = 1)
    var filtersAccordion = false

    @Expose @JvmField
    @ConfigAccordionId(id = 1)
    @ConfigOption(name = "Enable Chat Filters", desc = "Hide chat messages containing filtered phrases.")
    @ConfigEditorBoolean
    var enabled = false

    @Expose @JvmField
    @ConfigAccordionId(id = 1)
    @ConfigOption(name = "Filtered Phrases", desc = "Comma-separated list of phrases to hide from chat.")
    @ConfigEditorText
    var chatFilterList = "Your Implosion hit"
}
