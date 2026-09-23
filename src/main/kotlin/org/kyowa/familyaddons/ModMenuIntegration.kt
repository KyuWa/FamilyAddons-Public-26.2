package org.kyowa.familyaddons

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import org.kyowa.familyaddons.gui.ConfigSpec
import org.kyowa.familyaddons.gui.GuiStyle

class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory { parent -> ConfigSpec.load(); GuiStyle.screen(parent) }
}
