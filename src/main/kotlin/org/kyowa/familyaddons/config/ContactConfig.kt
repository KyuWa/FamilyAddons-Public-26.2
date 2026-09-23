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

    // ── Question ─────────────────────────────────────────────────────
    @Expose @JvmField
    @ConfigOption(name = "Ask a Question", desc = "")
    @ConfigEditorAccordion(id = 3)
    var questionAccordion = false

    @JvmField
    @ConfigAccordionId(id = 3)
    @ConfigOption(name = "Your question", desc = "Anything you want to ask about the mod. The answer arrives in chat, in game, whenever it is written.")
    @ConfigEditorText
    var questionText: String = ""

    @JvmField
    @ConfigAccordionId(id = 3)
    @ConfigOption(name = "Send", desc = "Send the question. One message every five minutes, three an hour.")
    @ConfigEditorButton(buttonText = "Send")
    var questionSend: Runnable = Runnable { org.kyowa.familyaddons.features.Contact.sendQuestion() }

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
