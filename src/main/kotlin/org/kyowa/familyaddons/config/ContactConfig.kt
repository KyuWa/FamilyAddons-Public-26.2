package org.kyowa.familyaddons.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigAccordionId
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorAccordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class ContactConfig {

    // ── Bug report ───────────────────────────────────────────────────
    @Expose @JvmField
    @ConfigOption(name = "Report a Bug", desc = "")
    @ConfigEditorAccordion(id = 1)
    var bugAccordion = false

    @JvmField
    @ConfigAccordionId(id = 1)
    @ConfigOption(name = "What happened", desc = "What went wrong, and what you were doing when it did. Your name and the two version numbers are sent with it, nothing else.")
    @ConfigEditorText
    var bugText: String = ""

    @JvmField
    @ConfigAccordionId(id = 1)
    @ConfigOption(name = "Send", desc = "Send the report. One message every five minutes, three an hour.")
    @ConfigEditorButton(buttonText = "Send")
    var bugSend: Runnable = Runnable { org.kyowa.familyaddons.features.Contact.sendBug() }

    // ── Suggestion ───────────────────────────────────────────────────
    @Expose @JvmField
    @ConfigOption(name = "Suggest Something", desc = "")
    @ConfigEditorAccordion(id = 2)
    var ideaAccordion = false

    @JvmField
    @ConfigAccordionId(id = 2)
    @ConfigOption(name = "Your idea", desc = "A feature you would like, or something that could work better. Your name and the two version numbers are sent with it, nothing else.")
    @ConfigEditorText
    var ideaText: String = ""

    @JvmField
    @ConfigAccordionId(id = 2)
    @ConfigOption(name = "Send", desc = "Send the suggestion. One message every five minutes, three an hour.")
    @ConfigEditorButton(buttonText = "Send")
    var ideaSend: Runnable = Runnable { org.kyowa.familyaddons.features.Contact.sendIdea() }
}
