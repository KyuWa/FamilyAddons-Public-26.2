package org.kyowa.familyaddons.features

import it.unimi.dsi.fastutil.booleans.BooleanConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.network.chat.Component
import org.kyowa.familyaddons.config.FamilyConfigManager
import org.kyowa.familyaddons.util.FaChat

/**
 * Stops a misclick on the pause menu's Disconnect button from ending a run. The
 * disconnect is swallowed and a confirm screen shown instead, with its Disconnect
 * button locked for two seconds so the same click cannot go straight through it.
 * Stay, or Escape, returns to the pause menu.
 */
object DisconnectGuard {
    private const val DELAY_TICKS = 40

    /** Set by the confirm screen right before it disconnects for real. */
    private var confirmed = false

    /**
     * Called from the head of Minecraft.disconnectFromWorld. True when the disconnect
     * was swallowed and the confirm screen shown instead.
     */
    @JvmStatic
    fun intercept(mc: Minecraft, reason: Component): Boolean {
        if (confirmed) { confirmed = false; return false }
        if (!FamilyConfigManager.config.general.confirmDisconnect) return false
        // Save and Quit on a local world is not the misclick this guards against.
        if (mc.isLocalServer) return false
        if (mc.gui.screen() !is PauseScreen) return false
        mc.gui.setScreen(ConfirmDisconnectScreen(reason))
        return true
    }

    private fun disconnectNow(reason: Component) {
        confirmed = true
        Minecraft.getInstance().disconnectFromWorld(reason)
    }

    private fun stay() {
        val mc = Minecraft.getInstance()
        mc.gui.setScreen(PauseScreen(true))
    }

    class ConfirmDisconnectScreen(reason: Component) : ConfirmScreen(
        BooleanConsumer { yes -> if (yes) disconnectNow(reason) else stay() },
        FaChat.prefixed("Are you sure you want to Disconnect ?"),
        Component.empty(),
        Component.literal("Disconnect"),
        Component.literal("Stay"),
    ) {
        private var ticksLeft = if (FamilyConfigManager.config.general.confirmDisconnectTimer) DELAY_TICKS else 0

        override fun init() {
            super.init()
            applyCountdown()
        }

        override fun tick() {
            super.tick()
            if (ticksLeft > 0) {
                ticksLeft--
                applyCountdown()
            }
        }

        private fun applyCountdown() {
            val button = yesButton ?: return
            if (ticksLeft > 0) {
                button.active = false
                button.message = Component.literal("Disconnect (" + (ticksLeft + 19) / 20 + ")")
            } else {
                button.active = true
                button.message = Component.literal("Disconnect")
            }
        }
    }
}
