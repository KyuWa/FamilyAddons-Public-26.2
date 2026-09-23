package org.kyowa.familyaddons.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.common.text.StructuredText

class FamilyConfig : Config() {

    override fun getTitle(): StructuredText = StructuredText.of("§6FamilyAddons")

    @Expose @JvmField
    @Category(name = "General", desc = "General settings")
    var general = GeneralConfig()

    @Expose @JvmField
    @Category(name = "Chat Filters", desc = "Filter unwanted chat messages")
    var chatFilters = ChatFiltersConfig()

    @Expose @JvmField
    @Category(name = "Translator", desc = "Translate chat — click a line or auto-translate, any language")
    var translator = TranslatorConfig()

    @Expose @JvmField
    @Category(name = "Utilities", desc = "General utility features")
    var utilities = UtilitiesConfig()

    @Expose @JvmField
    @Category(name = "Party", desc = "Party management features")
    var party = PartyConfig()

    @Expose @JvmField
    @Category(name = "Mining", desc = "Mining features — Mineshaft & Pickaxe Ability")
    var mining = MiningConfig()

    @Expose @JvmField
    @Category(name = "Kuudra", desc = "All Kuudra features")
    var kuudra = KuudraConfig()

    @Expose @JvmField
    @Category(name = "Crimson Isle", desc = "Crimson Isle features")
    var crimsonIsle = CrimsonIsleConfig()

    @Expose @JvmField
    @Category(name = "Dungeons", desc = "Dungeon features")
    var dungeons = DungeonsConfig()




    @Expose @JvmField
    @Category(name = "Keybinds", desc = "GFS keybinds for quick item restocking")
    var keybinds = KeybindsConfig()

    @Expose @JvmField
    @Category(name = "Player Disguise", desc = "Replace player renders with a mob model")
    var playerDisguise = PlayerDisguiseConfig()

    @Expose @JvmField
    @Category(name = "Name Changer", desc = "Your custom display name, seen by everyone with the mod once approved")
    var nameChanger = NameChangerConfig()



    @Expose @JvmField
    @Category(name = "Family Storage", desc = "Ender chest pages and backpacks side by side, with search")
    var storage = StorageConfig()

}