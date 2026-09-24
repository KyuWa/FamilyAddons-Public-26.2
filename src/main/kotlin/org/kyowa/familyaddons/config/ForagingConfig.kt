package org.kyowa.familyaddons.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigAccordionId
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorAccordion

/** Foraging: the Big Helix tree route on Torrhus Canyon. */
class ForagingConfig {
    @Expose @JvmField
    @ConfigOption(name = "Helix Tree Waypoints", desc = "Big Helix tree route on Torrhus Canyon: box outlines (trees green, etherwarp spots cyan, Evasive shop white) with a distance label. Only the current stop is drawn.")
    @ConfigEditorBoolean
    var helixWaypoints = false

}
