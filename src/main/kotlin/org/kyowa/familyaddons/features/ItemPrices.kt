package org.kyowa.familyaddons.features

import com.google.gson.JsonParser
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import org.kyowa.familyaddons.FamilyAddons
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

/**
 * SkyBlock prices, for totalling up a container ([ItemValue]).
 *
 * Two sources, because they answer different questions:
 *  - the **bazaar** comes in one list from Hypixel, refreshed every five minutes;
 *  - **auction** prices are asked for one item at a time from Coflnet's API and
 *    kept for ten minutes, since there is no usable bulk dump any more.
 *
 * Nothing is requested until something actually asks for a price, so a player
 * who never opens their storage never makes a request, and an item that has no
 * price simply counts as nothing rather than blocking the total.
 */
object ItemPrices {

    private val http: HttpClient = HttpClient.newHttpClient()

    private val bazaar = mutableMapOf<String, BazaarData>()
    private var lastBazaarFetch = 0L
    private const val BAZAAR_CACHE_MS = 5 * 60 * 1000L

    // Auction cache: the lowest BIN, what it actually sells for, and when we asked.
    private val binPrice = mutableMapOf<String, Double>()
    private val sellPrice = mutableMapOf<String, Double>()
    private val askedAt = mutableMapOf<String, Long>()
    private val inFlight: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val running = AtomicInteger(0)
    private const val AUCTION_CACHE_MS = 10 * 60 * 1000L
    private const val MAX_IN_FLIGHT = 6

    private const val AUCTION_API = "https://sky.coflnet.com/api/item/price/%s/current"
    private const val BAZAAR_API = "https://api.hypixel.net/v2/skyblock/bazaar"

    data class BazaarData(val instaBuy: Double, val instaSell: Double)

    /** Refresh the bazaar list if it is stale. Cheap to call every frame. */
    fun ensureFresh() {
        val now = System.currentTimeMillis()
        if (now - lastBazaarFetch > BAZAAR_CACHE_MS) {
            lastBazaarFetch = now
            fetchBazaar()
        }
    }

    /** True once anything at all has been priced, so a caller can tell "loading" from "worthless". */
    fun hasPrices(): Boolean =
        synchronized(bazaar) { bazaar.isNotEmpty() } || synchronized(sellPrice) { sellPrice.isNotEmpty() }

    fun bazaarOf(id: String): BazaarData? = synchronized(bazaar) { bazaar[id] }

    /** Lowest BIN for an auction item: what one costs to buy. Null until it has been fetched. */
    fun binOf(tag: String): Double? {
        requestIfStale(tag)
        return synchronized(binPrice) { binPrice[tag] }
    }

    /** What an auction item actually sells for — the side you are on when valuing storage. */
    fun sellOf(tag: String): Double? {
        requestIfStale(tag)
        return synchronized(sellPrice) { sellPrice[tag] }
    }

    /** The SkyBlock item id for a stack: `PET:type:rarity`, `ENCHBOOK:name:level` or a plain id. */
    fun idOf(stack: ItemStack): String? = getSkyblockId(stack)

    private fun requestIfStale(tag: String) {
        if (tag.isBlank()) return
        val now = System.currentTimeMillis()
        val asked = synchronized(askedAt) { askedAt[tag] }
        if (asked != null && now - asked < AUCTION_CACHE_MS) return
        if (!inFlight.add(tag)) return
        if (running.get() >= MAX_IN_FLIGHT) { inFlight.remove(tag); return }

        synchronized(askedAt) { askedAt[tag] = now }
        running.incrementAndGet()
        CompletableFuture.runAsync {
            try {
                val res = http.send(
                    HttpRequest.newBuilder().uri(URI.create(String.format(AUCTION_API, tag)))
                        .header("User-Agent", "FamilyAddons/1.0").GET().build(),
                    HttpResponse.BodyHandlers.ofString()
                )
                val json = JsonParser.parseString(res.body()).asJsonObject
                // available = -1 means Coflnet has never seen the item; leave it unpriced.
                val available = json.get("available")?.asInt ?: 0
                val sell = json.get("sell")?.asDouble ?: 0.0
                val buy = json.get("buy")?.asDouble ?: 0.0
                if (available >= 0 && (sell > 0 || buy > 0)) {
                    synchronized(sellPrice) { sellPrice[tag] = sell }
                    synchronized(binPrice) { binPrice[tag] = buy }
                }
            } catch (e: Exception) {
                FamilyAddons.LOGGER.warn("ItemPrices: $tag failed: ${e.message}")
                // Let it be asked again sooner than the full cache time.
                synchronized(askedAt) { askedAt.remove(tag) }
            } finally {
                running.decrementAndGet()
                inFlight.remove(tag)
            }
        }
    }

    private fun getSkyblockId(stack: ItemStack): String? {
        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        val nbt = customData.copyTag()

        val rawId: String =
            nbt.getString("id").orElse(null)?.ifBlank { null }
                ?: nbt.getCompoundOrEmpty("ExtraAttributes").getString("id").orElse(null)?.ifBlank { null }
                ?: nbt.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes").getString("id").orElse(null)?.ifBlank { null }
                ?: return null

        if (rawId == "PET") {
            val petInfoStr =
                nbt.getString("petInfo").orElse(null)?.ifBlank { null }
                    ?: nbt.getCompoundOrEmpty("ExtraAttributes").getString("petInfo").orElse(null)?.ifBlank { null }
                    ?: return null
            return try {
                val petJson = JsonParser.parseString(petInfoStr).asJsonObject
                val type = petJson.get("type")?.asString?.ifBlank { null } ?: return null
                val tier = petJson.get("tier")?.asString?.ifBlank { null } ?: return null
                val rarityIndex = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC").indexOf(tier)
                if (rarityIndex < 0) return null
                "PET:$type:$rarityIndex"
            } catch (e: Exception) { null }
        }

        // An enchanted book is priced by the enchantment it carries.
        if (rawId == "ENCHANTED_BOOK") {
            val enchNbt =
                nbt.getCompoundOrEmpty("enchantments").takeIf { !it.isEmpty }
                    ?: nbt.getCompoundOrEmpty("ExtraAttributes").getCompoundOrEmpty("enchantments").takeIf { !it.isEmpty }
                    ?: nbt.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes").getCompoundOrEmpty("enchantments")
            if (!enchNbt.isEmpty) {
                val firstKey = enchNbt.keySet().firstOrNull()
                if (firstKey != null) {
                    val level = enchNbt.getInt(firstKey).orElse(-1)
                    if (level >= 0) return "ENCHBOOK:${firstKey.uppercase()}:$level"
                }
            }
            // A plain book falls through to its own price.
        }

        return rawId
    }

    private fun fetchBazaar() {
        CompletableFuture.runAsync {
            try {
                val res = http.send(
                    HttpRequest.newBuilder().uri(URI.create(BAZAAR_API))
                        .header("User-Agent", "FamilyAddons/1.0").GET().build(),
                    HttpResponse.BodyHandlers.ofString()
                )
                val json = JsonParser.parseString(res.body()).asJsonObject
                if (!json.get("success").asBoolean) return@runAsync
                synchronized(bazaar) {
                    bazaar.clear()
                    for ((id, product) in json.getAsJsonObject("products").entrySet()) {
                        val qs = product.asJsonObject.getAsJsonObject("quick_status")
                        bazaar[id] = BazaarData(qs.get("buyPrice").asDouble, qs.get("sellPrice").asDouble)
                    }
                }
            } catch (e: Exception) { FamilyAddons.LOGGER.warn("ItemPrices: bazaar failed: ${e.message}") }
        }
    }
}
