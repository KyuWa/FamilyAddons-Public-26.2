package org.kyowa.familyaddons

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.kyowa.familyaddons.commands.TestCommand
import org.kyowa.familyaddons.commands.TranslateCommand
import org.kyowa.familyaddons.config.FamilyConfigManager
import org.kyowa.familyaddons.features.*
import org.kyowa.familyaddons.features.translator.ChatTranslator
import org.kyowa.familyaddons.party.PartyTracker
import org.slf4j.LoggerFactory

val COLOR_CODE_REGEX = Regex("§.")

object FamilyAddons : ClientModInitializer {

    val LOGGER = LoggerFactory.getLogger("FamilyAddons")
    const val VERSION = "1.0.0"
    const val MC_VERSION = "26.2"

    private var hudEditorMouseWasDown = false
    private var previousScreen: Screen? = null

    override fun onInitializeClient() {
        LOGGER.info("FamilyAddons $VERSION loading (${org.kyowa.familyaddons.util.BuildFlavor.name} build)...")

        org.kyowa.familyaddons.util.HypixelLocation.register()
        AutoUpdater.register()

        FamilyConfigManager.load()
        KeyFetcher.fetchIfNeeded()

        TestCommand.register()
        KuudraState.register()
        WorldRenderDispatcher.register()
        CorpseESP.register()
        Waypoints.register()
        EntityHighlight.register()
        ShulkerBoxHighlight.register()
        SparklingCritterHighlight.register()
        FloorDropHighlight.register()
        DungeonHighlight.register()
        KuudraOccupancy.register()
        PileWaypoints.register()
        SupplyWaypoints.register()
        KuudraFuelPhase.register()
        KuudraBuildOverlay.register()
        KuudraGiants.register()
        KuudraCrateWaypoints.register()
        PearlWaypoints.register()
        KuudraStunWaypoint.register()
        BestiaryTracker.register()
        BestiaryZoneHighlight.register()
        HelixWaypoints.register()
        org.kyowa.familyaddons.features.safari.SafariTracker.register()
        org.kyowa.familyaddons.features.safari.BeeSpotHighlight.register()

        // Chat
        TranslateCommand.register()

        // Utilities
        CmdShortcut.register()
        org.kyowa.familyaddons.storage.FamilyStorage.init()
        SignMath.register()
        org.kyowa.familyaddons.features.ComposterGuard.register()
        GfsKeybinds.register()
        ArachneTimer.register()

        // Crimson Isle
        MiniBossTimer.register()

        // Solo Kuudra
        PearlTimer.register()

        // Party
        PartyTracker.register()
        PartyRepCheck.register()

        // Rendering & World
        PickaxeAbility.register()

        // Kuudra + Dungeons
        // KuudraPhase must register first — other Kuudra features read its state.
        KuudraPhase.register()
        DtTitle.register()
        InfernalKeyTracker.register()
        DungeonDtTitle.register()
        KuudraDirection.register()
        RendDamage.register()

        // Bestiary

        // Player Disguise sync (multi-player Cloudflare sync)
        SharedDisguiseSync.register()

        // Dev
        NameStyle.register()
        NameSync.register()

        // Chat filter + translator LAST: Fabric's ALLOW_GAME stops calling
        // listeners once one hides a line, so anything registered after a
        // filter would miss e.g. Elle's phase lines when the player filters
        // NPC chat. Every tracker above must see every line first.
        HideMessages.register()
        ChatTranslator.register()

        // Join event
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            SharedDisguiseSync.fetchAllNow() // Re-fetch disguises on every server join so they load immediately
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (TestCommand.openGuiNextTick) {
                TestCommand.openGuiNextTick = false
                client.gui.setScreen(HudEditorScreen())
            }
            if (TestCommand.openConfigNextTick) {
                TestCommand.openConfigNextTick = false
                FamilyConfigManager.openGui()
            }
            val currentScreen = client.gui.screen()
            if (previousScreen != null && currentScreen == null) FamilyConfigManager.save()
            previousScreen = currentScreen
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val screen = client.gui.screen() as? HudEditorScreen ?: run {
                hudEditorMouseWasDown = false
                return@register
            }
            val mouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                client.window.handle(),
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
            ) == org.lwjgl.glfw.GLFW.GLFW_PRESS
            val mx = client.mouseHandler.getScaledXPos(client.window)
            val my = client.mouseHandler.getScaledYPos(client.window)
            if (mouseDown && !hudEditorMouseWasDown) screen.onMousePress(mx, my)
            else if (!mouseDown && hudEditorMouseWasDown) screen.onMouseRelease()
            hudEditorMouseWasDown = mouseDown
        }

        // Dev runs (-Dfamilyaddons.autoopen=true): open the config screen on the title
        // screen, so it can be looked at without joining a world.
        if (System.getProperty("familyaddons.autoopen") == "true") {
            var opened = false
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register { mc, screen, _, _ ->
                if (screen is net.minecraft.client.gui.screens.TitleScreen && !opened) {
                    opened = true
                    mc.execute { FamilyConfigManager.openGui() }
                }
            }
        }


        LOGGER.info("FamilyAddons $VERSION loaded!")
    }
}