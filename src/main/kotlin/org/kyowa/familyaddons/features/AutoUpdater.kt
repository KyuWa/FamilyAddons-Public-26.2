package org.kyowa.familyaddons.features

import com.google.gson.JsonParser
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import org.kyowa.familyaddons.FamilyAddons
import org.kyowa.familyaddons.config.FamilyConfigManager
import org.kyowa.familyaddons.util.FaChat
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * Tells you in chat when a newer version is out, once per session, shortly
 * after you join a server. It never downloads or installs anything: the line
 * carries links to Modrinth and to the release page, and you update the way you
 * installed the mod in the first place.
 *
 * Modrinth is asked first, since that is where releases land; GitHub is the
 * fallback for anyone who took the jar straight from the releases page.
 */
object AutoUpdater {

    private const val MODRINTH_PROJECT = "familyaddons"
    private const val MODRINTH_API = "https://api.modrinth.com/v2/project/$MODRINTH_PROJECT/version"
    private const val MODRINTH_PAGE = "https://modrinth.com/mod/$MODRINTH_PROJECT"
    private const val GITHUB_API = "https://api.github.com/repos/KyuWa/FamilyAddons/releases/latest"
    private const val GITHUB_PAGE = "https://github.com/KyuWa/FamilyAddons/releases"

    private val http: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
    }

    @Volatile private var newest: String? = null
    @Volatile private var told = false
    private var joinTicks = -1

    fun register() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            // A few seconds after joining, so the line is not lost in the join spam.
            if (!told) joinTicks = 20 * 5
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (joinTicks < 0) return@register
            if (--joinTicks > 0) return@register
            joinTicks = -1
            if (FamilyConfigManager.config.general.updateCheck) check(false)
        }
    }

    /** `/fa update`: says something either way, even when nothing is new. */
    fun checkNow() = check(true)

    private fun check(loud: Boolean) {
        CompletableFuture.runAsync {
            val latest = fromModrinth() ?: fromGitHub()
            val mc = Minecraft.getInstance()
            mc.execute {
                if (latest == null) {
                    if (loud) FaChat.send("§cCould not reach Modrinth or GitHub.")
                    return@execute
                }
                newest = latest
                if (isNewer(latest, FamilyAddons.VERSION)) {
                    told = true
                    announce(latest)
                } else if (loud) {
                    FaChat.send("§aYou are on the newest version (§f${FamilyAddons.VERSION}§a).")
                }
            }
        }
    }

    private fun announce(latest: String) {
        val player = Minecraft.getInstance().player ?: return
        val line = Component.literal("§eFamilyAddons §f$latest §eis out — you have §f${FamilyAddons.VERSION}§e. ")
            .append(link("[Modrinth]", MODRINTH_PAGE, ChatFormatting.GREEN))
            .append(Component.literal(" "))
            .append(link("[GitHub]", GITHUB_PAGE, ChatFormatting.AQUA))
        player.sendSystemMessage(FaChat.prefixed(line))
    }

    private fun link(text: String, url: String, colour: ChatFormatting): Component =
        Component.literal(text).withStyle(
            Style.EMPTY.withColor(colour)
                .withClickEvent(ClickEvent.OpenUrl(URI.create(url)))
                .withHoverEvent(HoverEvent.ShowText(Component.literal("§7$url")))
        )

    /** The newest version on Modrinth for this Minecraft version, or null. */
    private fun fromModrinth(): String? = try {
        val url = "$MODRINTH_API?loaders=[%22fabric%22]&game_versions=[%22${FamilyAddons.MC_VERSION}%22]"
        val body = get(url)
        val versions = JsonParser.parseString(body).asJsonArray
        versions.firstOrNull()?.asJsonObject?.get("version_number")?.asString?.trim()?.removePrefix("v")
    } catch (e: Exception) {
        FamilyAddons.LOGGER.warn("AutoUpdater: Modrinth check failed: ${e.message}")
        null
    }

    /** The newest release on GitHub, for jars taken from there. */
    private fun fromGitHub(): String? = try {
        val json = JsonParser.parseString(get(GITHUB_API)).asJsonObject
        json.get("tag_name")?.asString?.trim()?.removePrefix("v")
    } catch (e: Exception) {
        FamilyAddons.LOGGER.warn("AutoUpdater: GitHub check failed: ${e.message}")
        null
    }

    private fun get(url: String): String {
        val req = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "FamilyAddons/${FamilyAddons.VERSION}")
            .timeout(Duration.ofSeconds(10)).GET().build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) throw IllegalStateException("HTTP ${resp.statusCode()}")
        return resp.body()
    }

    /** True when [a] is a later version than [b]; both are "1.2.3" shaped. */
    private fun isNewer(a: String, b: String): Boolean {
        val x = a.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val l = x.getOrElse(i) { 0 }
            val r = y.getOrElse(i) { 0 }
            if (l != r) return l > r
        }
        return false
    }
}
