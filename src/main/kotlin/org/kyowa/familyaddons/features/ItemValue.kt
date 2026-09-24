package org.kyowa.familyaddons.features

import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * What a container is worth, from the prices [ItemPrices] already keeps.
 *
 * Everything is valued at what you would get for it rather than what it would
 * cost to buy: bazaar goods at insta-sell, auction items at what they sell
 * for. An item with no price counts as nothing, so the total is a floor.
 */
object ItemValue {

    /** Rows in the hover breakdown before the rest is summed up as "and N more". */
    private const val BREAKDOWN_ROWS = 14

    /** Coins [stack] is worth in total (unit price × count); 0 when it has no price. */
    fun valueOf(stack: ItemStack?): Double {
        if (stack == null || stack.isEmpty) return 0.0
        ItemPrices.ensureFresh()
        val id = ItemPrices.idOf(stack) ?: return 0.0
        val count = stack.count.coerceAtLeast(1)

        // Pets are priced by their kind: "PET:SPIRIT:4" is the SPIRIT auction tag.
        if (id.startsWith("PET:")) {
            val parts = id.split(":")
            if (parts.size < 3) return 0.0
            return (ItemPrices.sellOf("PET_${parts[1]}") ?: 0.0) * count
        }

        if (id.startsWith("ENCHBOOK:")) {
            val parts = id.split(":")
            if (parts.size < 3) return 0.0
            val baz = ItemPrices.bazaarOf("ENCHANTMENT_${parts[1].uppercase()}_${parts[2]}")
            return (baz?.instaSell ?: 0.0) * count
        }

        ItemPrices.bazaarOf(id)?.let { return it.instaSell * count }
        return (ItemPrices.sellOf(id) ?: ItemPrices.binOf(id) ?: 0.0) * count
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
