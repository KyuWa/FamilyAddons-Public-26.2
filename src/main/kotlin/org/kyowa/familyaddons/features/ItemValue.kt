package org.kyowa.familyaddons.features

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * What a container is worth, from the prices [ItemPrices] keeps.
 *
 * An item is its base price plus everything that was put into it: the books on
 * it, hot potato and fuming books, a recombobulator, master stars, gemstones
 * and the rest. Without that a Hyperion with five books reads the same as one
 * straight off the auction house, which is what made the old totals feel wrong.
 *
 * Everything is valued at what you would get rather than what it would cost:
 * bazaar goods at insta-sell, auction items at what they sell for. Anything
 * unpriced counts as nothing, so the total is a floor, not a guess.
 */
object ItemValue {

    /** Rows in the hover breakdown before the rest is summed up as "and N more". */
    private const val BREAKDOWN_ROWS = 14

    private val RARITIES = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")
    private val MASTER_STARS = listOf(
        "FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR",
        "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"
    )
    private val petLevelRegex = Regex("""\[Lvl (\d+)]""")

    /** Coins [stack] is worth in total (unit price × count); 0 when nothing in it has a price. */
    fun valueOf(stack: ItemStack?): Double {
        if (stack == null || stack.isEmpty) return 0.0
        ItemPrices.ensureFresh()
        val id = ItemPrices.idOf(stack) ?: return 0.0
        val count = stack.count.coerceAtLeast(1)

        if (id.startsWith("PET:")) return petValue(stack, id) * count

        if (id.startsWith("ENCHBOOK:")) {
            val parts = id.split(":")
            if (parts.size < 3) return 0.0
            val baz = ItemPrices.bazaarOf("ENCHANTMENT_${parts[1].uppercase()}_${parts[2]}")
            return (baz?.instaSell ?: 0.0) * count
        }

        // A bazaar good is exactly its stack: nothing can be applied to it.
        ItemPrices.bazaarOf(id)?.let { return it.instaSell * count }

        val base = ItemPrices.sellOf(id) ?: ItemPrices.binOf(id) ?: 0.0
        return (base + upgradesValue(stack)) * count
    }

    /**
     * A pet is priced from live listings of its kind: the cheapest one of the
     * same rarity at or below this pet's level, which is the closest thing to
     * "what would mine go for". Its held item is added on top.
     */
    private fun petValue(stack: ItemStack, id: String): Double {
        val parts = id.split(":")
        if (parts.size < 3) return 0.0
        val tag = "PET_${parts[1]}"
        val tier = RARITIES.getOrNull(parts[2].toIntOrNull() ?: -1)
        val level = petLevelRegex.find(stack.hoverName.string)?.groupValues?.get(1)?.toIntOrNull() ?: 1

        val listings = ItemPrices.petListingsOf(tag)
        val sameTier = listings?.filter { tier == null || it.tier.equals(tier, true) }
        val base = when {
            !sameTier.isNullOrEmpty() -> {
                val atOrBelow = sameTier.filter { it.level <= level }
                (if (atOrBelow.isNotEmpty()) atOrBelow else sameTier).minOf { it.price }
            }
            else -> ItemPrices.sellOf(tag) ?: 0.0
        }

        val held = ItemPrices.petHeldItem(stack)?.let { unitPrice(it) } ?: 0.0
        return base + held
    }

    /** Books, potato books, stars, gems — what was sunk into an item after buying it. */
    private fun upgradesValue(stack: ItemStack): Double {
        val ea = ItemPrices.extraAttributesOf(stack) ?: return 0.0
        var extra = 0.0

        // Enchantments, each worth what its book sells for.
        val enchantments = ea.getCompoundOrEmpty("enchantments")
        for (key in enchantments.keySet()) {
            val level = enchantments.getInt(key).orElse(0)
            if (level <= 0) continue
            extra += unitPrice("ENCHANTMENT_${key.uppercase()}_$level")
        }

        // Hot potato books, and fuming ones past the tenth.
        val potatoes = ea.getInt("hot_potato_count").orElse(0)
        if (potatoes > 0) {
            extra += unitPrice("HOT_POTATO_BOOK") * minOf(potatoes, 10)
            if (potatoes > 10) extra += unitPrice("FUMING_POTATO_BOOK") * (potatoes - 10)
        }

        if (ea.getInt("rarity_upgrades").orElse(0) > 0) extra += unitPrice("RECOMBOBULATOR_3000")

        // Stars past the fifth are master stars; the first five cost essence, not coins.
        val stars = maxOf(ea.getInt("upgrade_level").orElse(0), ea.getInt("dungeon_item_level").orElse(0))
        for (star in 6..minOf(stars, 10)) extra += unitPrice(MASTER_STARS[star - 6])

        extra += gemsValue(ea)

        val artOfWar = ea.getInt("art_of_war_count").orElse(0)
        if (artOfWar > 0) extra += unitPrice("THE_ART_OF_WAR") * artOfWar
        if (ea.getInt("artOfPeaceApplied").orElse(0) > 0) extra += unitPrice("THE_ART_OF_PEACE")

        val singularities = ea.getInt("wood_singularity_count").orElse(0)
        if (singularities > 0) extra += unitPrice("WOOD_SINGULARITY") * singularities

        val tuners = ea.getInt("tuned_transmission").orElse(0)
        if (tuners > 0) extra += unitPrice("TRANSMISSION_TUNER") * tuners

        if (ea.getInt("ethermerge").orElse(0) > 0) extra += unitPrice("ETHERWARP_MERGER")

        return extra
    }

    /**
     * Gemstones in their slots. A slot either names its own kind ("JASPER_0")
     * or keeps it beside itself ("COMBAT_0" with "COMBAT_0_gem" = "JASPER").
     */
    private fun gemsValue(ea: CompoundTag): Double {
        val gems = ea.getCompoundOrEmpty("gems")
        if (gems.isEmpty) return 0.0
        var total = 0.0
        for (key in gems.keySet()) {
            if (key == "unlocked_slots" || key.endsWith("_gem")) continue
            val quality = gems.getString(key).orElse(null)?.ifBlank { null }
                ?: gems.getCompoundOrEmpty(key).getString("quality").orElse(null)?.ifBlank { null }
                ?: continue
            val kind = gems.getString("${key}_gem").orElse(null)?.ifBlank { null }
                ?: key.substringBeforeLast('_')
            total += unitPrice("${quality}_${kind}_GEM")
        }
        return total
    }

    /** One of something, from whichever market it trades on. */
    private fun unitPrice(id: String): Double {
        ItemPrices.bazaarOf(id)?.let { return it.instaSell }
        return ItemPrices.sellOf(id) ?: ItemPrices.binOf(id) ?: 0.0
    }

    /** False until the first price list has landed, so a page can say it is still loading. */
    fun pricesReady(): Boolean {
        ItemPrices.ensureFresh()
        return ItemPrices.hasPrices()
    }

    /** Everything in a container added up. */
    fun totalOf(items: List<ItemStack?>): Double {
        var total = 0.0
        for (stack in items) total += valueOf(stack)
        return total
    }

    /** "1.24B", "12.4M", "345k" — short enough to sit beside a page name. */
    fun formatShort(value: Double): String = when {
        value >= 1_000_000_000 -> String.format("%.2fB", value / 1_000_000_000)
        value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000)
        value >= 10_000 -> String.format("%.0fk", value / 1_000)
        value >= 1_000 -> String.format("%.1fk", value / 1_000)
        else -> value.toLong().toString()
    }

    /**
     * Hover lines for a container: its total, then what each item in it is
     * worth, dearest first. Identical stacks are counted together, so eight
     * separate stacks of the same block read as one row.
     */
    fun breakdown(items: List<ItemStack?>, title: String): List<Component> {
        val rows = LinkedHashMap<String, Row>()
        var total = 0.0

        for (stack in items) {
            val worth = valueOf(stack)
            if (stack == null || worth <= 0.0) continue
            total += worth
            val name = stack.hoverName.string
            val row = rows.getOrPut(name) { Row(stack.hoverName.copy(), 0, 0.0) }
            row.count += stack.count.coerceAtLeast(1)
            row.value += worth
        }

        val lines = ArrayList<Component>()
        lines.add(Component.literal("§6$title§7: §a${formatShort(total)}§7 coins"))

        if (rows.isEmpty()) {
            lines.add(Component.literal("§7Nothing in here has a price."))
            return lines
        }

        lines.add(Component.empty())
        val sorted = rows.values.sortedByDescending { it.value }
        for (row in sorted.take(BREAKDOWN_ROWS)) {
            val line = Component.literal(if (row.count > 1) "§8${row.count}× " else "§8  ")
                .append(row.name)
                .append(Component.literal(" §7— §a${formatShort(row.value)}"))
            lines.add(line)
        }
        if (sorted.size > BREAKDOWN_ROWS) {
            val rest = sorted.drop(BREAKDOWN_ROWS)
            val restValue = rest.sumOf { it.value }
            lines.add(Component.literal("§8and ${rest.size} more §7— §a${formatShort(restValue)}"))
        }
        return lines
    }

    private class Row(val name: Component, var count: Int, var value: Double)
}
