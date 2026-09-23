package org.kyowa.familyaddons.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigAccordionId
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorAccordion

class PlayerDisguiseConfig {

    @Expose @JvmField
    @ConfigOption(name = "Disguise", desc = "")
    @ConfigEditorAccordion(id = 1)
    var disguiseAccordion = false

    @Expose @JvmField
    @ConfigAccordionId(id = 1)
    @ConfigOption(name = "Enabled", desc = "Replace your player model with the chosen mob.")
    @ConfigEditorBoolean
    var enabled: Boolean = false

    @Expose @JvmField
    @ConfigAccordionId(id = 1)
    @ConfigOption(name = "Mob ID", desc = "Entity ID to disguise as, e.g. minecraft:slime, minecraft:creeper, minecraft:zombie, minecraft:villager")
    @ConfigEditorText
    var mobId: String = "minecraft:slime"

    @Expose @JvmField
    @ConfigAccordionId(id = 1)
    @ConfigOption(name = "Scope", desc = "Self Only = only you see yourself as the mob. Everyone = all players on your screen appear as the mob.")
    @ConfigEditorDropdown(values = ["Self Only", "Everyone"])
    var scope: Int = 0

    @Expose @JvmField
    @ConfigAccordionId(id = 1)
    @ConfigOption(name = "Baby", desc = "Render as a baby mob. Works for zombie, cow, pig, sheep, villager, etc.")
    @ConfigEditorBoolean
    var baby: Boolean = false

    @Expose @JvmField
    @ConfigAccordionId(id = 1)
    @ConfigOption(name = "Sheared", desc = "Render the mob as sheared. Works with sheep (removes wool) and snow golem (removes pumpkin).")
    @ConfigEditorBoolean
    var sheared: Boolean = false

    @Expose @JvmField
    @ConfigOption(name = "Scale", desc = "")
    @ConfigEditorAccordion(id = 2)
    var scaleAccordion = false

    @Expose @JvmField
    @ConfigAccordionId(id = 2)
    @ConfigOption(name = "Custom Scaling", desc = "Manually scale the disguise model. Overrides the Baby toggle (baby is ignored when this is on). Sheared still applies.")
    @ConfigEditorBoolean
    var customScalingEnabled: Boolean = false

    @Expose @JvmField
    @ConfigAccordionId(id = 2)
    @ConfigOption(name = "Custom Scale (x10)", desc = "Scale multiplier x10. 10 = 1.0 (normal), 5 = 0.5 (half), 20 = 2.0 (double), 50 = 5.0 (max). Only used when Custom Scaling is on.")
    @ConfigEditorSlider(minValue = 1f, maxValue = 50f, minStep = 1f)
    var customScaleTenths: Float = 10f

    @Expose @JvmField
    @ConfigOption(name = "Others", desc = "")
    @ConfigEditorAccordion(id = 3)
    var friendsAccordion = false

    @Expose @JvmField
    @ConfigAccordionId(id = 3)
    @ConfigOption(name = "Show Others' Disguises", desc = "Render other players with the disguise they chose, when they run the mod too. Off: everyone looks normal to you.")
    @ConfigEditorBoolean
    var showFriendsDisguises: Boolean = true

    @JvmField
    @ConfigAccordionId(id = 3)
    @ConfigOption(name = "Refresh Now", desc = "Instantly fetch the latest disguises from everyone with FamilyAddons.")
    @ConfigEditorButton(buttonText = "Refresh")
    var refreshDisguises: Runnable = Runnable {
        org.kyowa.familyaddons.features.SharedDisguiseSync.fetchAllNow()
    }
}