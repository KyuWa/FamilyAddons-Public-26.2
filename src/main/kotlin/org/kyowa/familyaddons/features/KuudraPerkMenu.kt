package org.kyowa.familyaddons.features

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import org.kyowa.familyaddons.COLOR_CODE_REGEX
import org.kyowa.familyaddons.config.FamilyConfigManager

/**
 *
 * them, so the menu is only the ones that matter. Human Cannonball is kept for
 * the stun role when Show Stun Perks is on.
 *
 * Middle Click GUI sends a left click on a slot as a middle click (CLONE) instead
 * of the normal pickup. Hypixel treats a middle click in its menus as a plain
 * click, but the client never tries to pick the item up, so nothing ghosts and
 * the click registers on the first try. Only in the Perk Menu; every other
 * menu, in a run or not, keeps its normal clicks.
 */
object KuudraPerkMenu {

    private val HIDDEN_CONTAINS = listOf("Steady Hands", "Bomberman", "Mining Frenzy")
    private val HIDDEN_EQUALS = listOf("Elle's Lava Rod", "Elle's Pickaxe", "Auto Revive")

    private fun cfg() = FamilyConfigManager.config.kuudra

    private fun title(screen: AbstractContainerScreen<*>): String = screen.title.string.replace(COLOR_CODE_REGEX, "").trim()
    private fun isPerkMenu(screen: AbstractContainerScreen<*>) = title(screen) == "Perk Menu"

    private fun hidden(name: String): Boolean {
        if (HIDDEN_CONTAINS.any { name.contains(it) } || HIDDEN_EQUALS.any { name == it }) return true
        return !cfg().removePerksShowStun && name.contains("Human Cannonball")
    }

    private fun slotName(slot: Slot): String? {
        val stack = slot.item
        if (stack.isEmpty) return null
        return stack.hoverName.string.replace(COLOR_CODE_REGEX, "").trim()
    }

    /** True when this slot should not be drawn. */
    @JvmStatic
    fun hideSlot(screen: AbstractContainerScreen<*>, slot: Slot): Boolean {
        if (!cfg().removePerks || !isPerkMenu(screen)) return false
        val name = slotName(slot) ?: return false
        return hidden(name)
    }

    /**
     * Called before the screen handles a slot click. True when the click was dealt
     * with here (swallowed, or sent as a middle click) and the screen must not.
     */
    @JvmStatic
    fun onSlotClick(screen: AbstractContainerScreen<*>, slot: Slot?, slotId: Int, button: Int, input: ContainerInput): Boolean {
        if (slot == null) return false
        val perkMenu = isPerkMenu(screen)
        if (cfg().removePerks && perkMenu) {
            val name = slotName(slot)
            if (name != null && hidden(name)) return true
        }
        if (cfg().middleClickGui && perkMenu && input == ContainerInput.PICKUP && button == 0) {
            // only the menu's own slots: the player's inventory stays a normal click
            if (slot.container === Minecraft.getInstance().player?.inventory) return false
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return false
            mc.gameMode?.handleContainerInput(screen.menu.containerId, slotId, 2, ContainerInput.CLONE, player)
            return true
        }
        return false
    }
}
