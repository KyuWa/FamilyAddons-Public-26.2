package org.kyowa.familyaddons.features

import com.google.gson.JsonParser
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import org.kyowa.familyaddons.FamilyAddons
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

/**
 * SkyBlock prices: lowest BIN from Moulberry's dump and the live bazaar.
 *
 * This build only uses them to total up a container ([ItemValue]); nothing is
 * added to item tooltips. Both lists are fetched at most once every five
 * minutes, off-thread, and only after something actually asks for a price, so
 * a player who never opens their storage never makes a request.
 */
object ItemPrices {

    private val http: HttpClient = HttpClient.newHttpClient()
    private val lowestBin = mutableMapOf<String, Double>()
    private val bazaar = mutableMapOf<String, BazaarData>()
    private var lastBinFetch = 0L
    private var lastBazaarFetch = 0L
    private const val CACHE_MS = 5 * 60 * 1000L

    data class BazaarData(val instaBuy: Double, val instaSell: Double)

    /** Refresh the lists if they are stale. Cheap to call every frame. */
    fun ensureFresh() {
        val now = System.currentTimeMillis()
        if (now - lastBinFetch > CACHE_MS) { lastBinFetch = now; fetchBin() }
        if (now - lastBazaarFetch > CACHE_MS) { lastBazaarFetch = now; fetchBazaar() }
    }

    fun binOf(key: String): Double? = synchronized(lowestBin) { lowestBin[key] }

    fun bazaarOf(id: String): BazaarData? = synchronized(bazaar) { bazaar[id] }

    /** The SkyBlock item id for a stack: `PET:type:rarity`, `ENCHBOOK:name:level` or a plain id. */
    fun idOf(stack: ItemStack): String? = getSkyblockId(stack)

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
            // A plain book falls through to its own BIN.
        }

        return rawId
    }

    private fun fetchBin() {
        CompletableFuture.runAsync {
            try {
                val res = http.send(
                    HttpRequest.newBuilder().uri(URI.create("https://moulberry.codes/lowestbin.json"))
                        .header("User-Agent", "FamilyAddons/1.0").GET().build(),
                    HttpResponse.BodyHandlers.ofString()
                )
                val json = JsonParser.parseString(res.body()).asJsonObject
                synchronized(lowestBin) {
                    lowestBin.clear()
                    for ((k, v) in json.entrySet()) lowestBin[k] = v.asDouble
                }
            } catch (e: Exception) { FamilyAddons.LOGGER.warn("ItemPrices: lowestBin failed: ${e.message}") }
        }
    }

    private fun fetchBazaar() {
        CompletableFuture.runAsync {
            try {
                val res = http.send(
                    HttpRequest.newBuilder().uri(URI.create("https://api.hypixel.net/v2/skyblock/bazaar"))
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
