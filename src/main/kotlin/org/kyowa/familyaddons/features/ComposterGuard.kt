package org.kyowa.familyaddons.features

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.kyowa.familyaddons.COLOR_CODE_REGEX
import org.kyowa.familyaddons.config.FamilyConfigManager
import org.kyowa.familyaddons.util.FaChat

/**
 * The Composter menu's "Insert Crops from Sacks" cauldron empties your sacks into
 * the composter in one click. With Block Composter Sack Insert on that click is
 * swallowed and the tooltip says so.
 */
object ComposterGuard {

    private fun enabled() = FamilyConfigManager.config.utilities.blockComposterSackInsert

    private fun clean(s: String) = s.replace(COLOR_CODE_REGEX, "").trim()

    private fun inComposter(): Boolean {
        val screen = Minecraft.getInstance().gui.screen() as? AbstractContainerScreen<*> ?: return false
        return clean(screen.title.string) == "Composter"
    }

    private fun isInsertButton(stack: ItemStack): Boolean =
        !stack.isEmpty && stack.item == Items.CAULDRON && clean(stack.hoverName.string) == "Insert Crops from Sacks"

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, tooltip ->
            if (!enabled() || !isInsertButton(stack) || !inComposter()) return@register
            tooltip.add(Component.empty())
            tooltip.add(Component.literal("§cBlocked by ").append(FaChat.gradient("[FA]")))
        }
    }

    /** True when the click is the cauldron's and must not reach the server. */
    @JvmStatic
    fun onSlotClick(screen: AbstractContainerScreen<*>, slot: Slot?): Boolean {
        if (slot == null || !enabled()) return false
        if (clean(screen.title.string) != "Composter") return false
        return isInsertButton(slot.item)
    }
}
