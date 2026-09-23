package org.kyowa.familyaddons.features

import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import org.kyowa.familyaddons.FamilyAddons
import org.kyowa.familyaddons.KeyFetcher
import org.kyowa.familyaddons.config.FamilyConfigManager
import org.kyowa.familyaddons.util.FaChat
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * Bug reports and suggestions, sent from the Contact category straight to the
 * mod's Discord. What goes with the text: your name, your uuid and the two
 * version numbers — nothing about your machine, your world or your session.
 *
 * One message every five minutes and three an hour; the worker keeps that
 * count, so it holds however the client is restarted.
 */
object Contact {

    private const val WORKER_URL = "https://fa-contact.220395610.workers.dev/contact"
    private const val MIN_LENGTH = 10
    private const val MAX_LENGTH = 1000

    private val http: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
    }
    @Volatile private var sending = false

    private fun cfg() = FamilyConfigManager.config.contact

    fun sendBug() = send("bug", cfg().bugText) { cfg().bugText = "" }
    fun sendIdea() = send("idea", cfg().ideaText) { cfg().ideaText = "" }

    private fun send(kind: String, raw: String, clear: () -> Unit) {
        val text = raw.trim()
        val what = if (kind == "bug") "report" else "suggestion"
        if (text.length < MIN_LENGTH) {
            FaChat.send("§cWrite a little more first — at least $MIN_LENGTH characters.")
            return
        }
        if (sending) return
        val mc = Minecraft.getInstance()
        val user = mc.user
        val uuid = user?.profileId?.toString()
        val name = user?.name
        if (uuid == null || name == null) {
            FaChat.send("§cCould not tell who you are, so the $what was not sent.")
            return
        }
        sending = true
        CompletableFuture.runAsync {
            try {
                val body = com.google.gson.Gson().toJson(
                    mapOf(
                        "uuid" to uuid,
                        "name" to name,
                        "kind" to kind,
                        "text" to text.take(MAX_LENGTH),
                        "version" to FamilyAddons.VERSION,
                        "mc" to FamilyAddons.MC_VERSION,
                    )
                )
                val req = HttpRequest.newBuilder(URI.create(WORKER_URL))
                    .header("Content-Type", "application/json")
                    .header(KeyFetcher.SECRET_HEADER, KeyFetcher.SECRET_TOKEN)
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()
                val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
                mc.execute { reply(resp.statusCode(), resp.body(), what, clear) }
            } catch (e: Exception) {
                FamilyAddons.LOGGER.warn("Contact: send failed: ${e.message}")
                mc.execute { FaChat.send("§cCould not reach the server — try again in a moment.") }
            } finally {
                sending = false
            }
        }
    }

    /** Turns the worker's answer into one line a player can act on. */
    private fun reply(status: Int, body: String, what: String, clear: () -> Unit) {
        when (status) {
            200 -> {
                clear()
                FamilyConfigManager.save()
                FaChat.send("§aThanks — your $what is through.")
            }
            429 -> {
                val json = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
                when (json?.get("error")?.asString) {
                    "hourly" -> FaChat.send("§eThat is three this hour — the next one can go in a while.")
                    else -> {
                        val mins = json?.get("minutes")?.asInt ?: 5
                        FaChat.send("§eOne at a time: try again in §f$mins §eminute${if (mins == 1) "" else "s"}.")
                    }
                }
            }
            400 -> FaChat.send("§cThat did not go through — write a little more and try again.")
            else -> FaChat.send("§cThe server said no ($status). Try again later.")
        }
    }
}
