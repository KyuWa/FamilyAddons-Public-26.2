package org.kyowa.familyaddons.features

import org.kyowa.familyaddons.util.FaColour
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderType
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.kyowa.familyaddons.COLOR_CODE_REGEX
import org.kyowa.familyaddons.config.FamilyConfigManager

object EntityHighlight {

    val highlighted = mutableSetOf<Entity>()          // Highlight-category matches
    val bestiaryHighlighted = mutableSetOf<Entity>()  // Bestiary zone/mob matches
    private var tick = 0

    /** True when any bestiary highlight source is configured (master toggle
     *  is checked separately — the category's Enable Highlight gates all). */
    private fun bestiaryActive(): Boolean {
        // The zone toggle is owned by the Safari category inside the Safari, so it is
        // checked before this category's master switch.
        if (BestiaryZoneHighlight.zoneOn() && BestiaryZoneHighlight.resolvedZoneIndex() > 0) return true
        val cfg = FamilyConfigManager.config.highlight
        if (!cfg.enabled) return false
        return cfg.mobName.isNotBlank()
    }

    private fun shouldScan(): Boolean {
        if (BestiaryZoneHighlight.zoneOn() && BestiaryZoneHighlight.resolvedZoneIndex() > 0) return true
        val cfg = FamilyConfigManager.config.highlight
        if (!cfg.enabled) return false
        if (cfg.mobNames.isNotBlank()) return true
        return cfg.mobName.isNotBlank()
    }

    /** Highlight name-list match: loose `.contains()` on name or customName. */
    private fun matchesManual(entity: Entity): Boolean {
        if (!FamilyConfigManager.config.highlight.enabled) return false
        val manualNames = FamilyConfigManager.config.highlight.mobNames
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        if (manualNames.isEmpty()) return false
        val name = entity.name.string.replace(COLOR_CODE_REGEX, "").lowercase()
        val customNameRaw = entity.customName?.string?.replace(COLOR_CODE_REGEX, "")?.lowercase()
        return manualNames.any { n -> name.contains(n) || customNameRaw?.contains(n) == true }
    }

    /**
     * Bestiary match: the single tracked mob (loose contains, legacy
     * behaviour), plus the zone-highlight names (stripped customName, exact
     * equality or one allowed modifier prefix — see [matchesWithModifier]).
     */
    private fun matchesBestiary(entity: Entity): Boolean {
        val bestiary = FamilyConfigManager.config.highlight
        val name = entity.name.string.replace(COLOR_CODE_REGEX, "").lowercase()
        val customNameRaw = entity.customName?.string?.replace(COLOR_CODE_REGEX, "")?.lowercase()

        val tracked = bestiary.mobName.trim().lowercase()
        if (tracked.isNotBlank() && (name.contains(tracked) || customNameRaw?.contains(tracked) == true)) {
            return true
        }

        // Hypixel puts a critter's name on an invisible armour stand riding above the
        // mob; matching that stand (and boxing it, as invisible entities fall back to)
        // is exactly how the bestiary zone highlighted every named critter. Do not
        // exclude armour stands here.
        var readableName = ""
        if (BestiaryZoneHighlight.zoneOn() && customNameRaw != null) {
            val zoneNames = BestiaryZoneHighlight.activeMobNames
                .map { it.lowercase() }.filter { it.isNotBlank() }
            val stripped = stripBestiaryNametag(customNameRaw)
            readableName = stripped
            if (zoneNames.isNotEmpty() && stripped.isNotBlank() &&
                zoneNames.any { matchesWithModifier(stripped, it) }) return true
        }

        // Shape rules: entity type, size, variant, worn head and so on, with no
        // nametag involved. Hypixel hangs most names on a separate invisible stand
        // above the mob, so the mob itself is matchable before that stand exists or
        // is paired — which is what makes a shape rule highlight sooner than a name.
        //
        // The nametag stays authoritative: an entity carrying a readable name that is
        // NOT a mob of this zone is something else, whatever its shape resembles, so
        // it never reaches the rules. Only unnamed entities fall through, which is the
        // exact case a shape rule is meant to catch.
        if (BestiaryZoneHighlight.zoneOn() && readableName.isBlank()) {
            if (BestiaryZoneHighlight.matchesNameless(entity)) return true
        }
        return false
    }

    /**
     * Allowed modifier-word prefixes that share a bestiary entry with the base mob.
     * Per Hypixel wiki, "corrupted" and "runic" are universal spawn variants — a
     * Corrupted Wither Skeleton kill counts toward the Wither Skeleton bestiary.
     * This is intentionally a small whitelist to avoid false positives like
     * "Cave Spider" matching when "Spider" is the active target.
     */
    private val ALLOWED_MODIFIERS = setOf("corrupted", "runic", "sparkling")

    private fun matchesWithModifier(stripped: String, target: String): Boolean {
        if (stripped == target) return true
        // "corrupted wither skeleton" matches target "wither skeleton" iff the
        // text before the target is exactly one allowed modifier word.
        if (stripped.endsWith(" $target")) {
            val prefix = stripped.removeSuffix(" $target")
            if (prefix in ALLOWED_MODIFIERS) return true
        }
        return false
    }

    /**
     * Reduce a Hypixel mob nametag down to just its display name.
     *
     * Strategy: rather than enumerate every prefix/suffix Hypixel uses (level
     * brackets, stars, hearts, runic glyphs, festival markers, mayor perks, etc.),
     * we keep only "name tokens" — whitespace-separated chunks made entirely of
     * letters, apostrophes, or hyphens. Anything containing a digit, bracket,
     * heart, star, or unknown symbol is decoration and gets discarded.
     *
     * Future-proof: when Hypixel adds a new symbol, it gets auto-stripped instead
     * of silently breaking matches.
     *
     * Examples (input → output):
     *   "✯ wither spectre 500❤"           → "wither spectre"
     *   "[lv50] zombie 1,234/5,000❤"      → "zombie"
     *   "wither skeleton 50❤"              → "wither skeleton"
     *   "᠅ runic ghoul ⓢ 2.5m❤"            → "runic ghoul"
     *   "[lv1] flaming spider 100❤"       → "flaming spider"
     *   "✯ bal 7m❤"                        → "bal"
     *
     * NOTE: input is already lowercased and color-code-stripped by the caller.
     */
    private fun stripBestiaryNametag(s: String): String {
        val nameTokenRegex = Regex("""^[a-z'\-]+$""")
        return s.split(Regex("""\s+"""))
            .filter { it.isNotEmpty() && nameTokenRegex.matches(it) }
            .joinToString(" ")
            .trim()
    }

    /**
     * True if [entity] represents a real connected player and must NEVER be highlighted.
     *
     * On Hypixel SkyBlock, mob NPCs are spawned as Player instances (full player skins,
     * custom AI). A real player can be told apart from an NPC because real players have an
     * entry in the tab list (PlayerListEntry); NPC mobs do not. This is the usual check
     * to avoid hitting NPCs with anti-cheat-style filters.
     *
     * Returns false for non-player entities (mobs, animals, armor stands etc.).
     */
    private fun isRealPlayer(entity: Entity): Boolean {
        if (entity !is Player) return false
        val handler = Minecraft.getInstance().connection ?: return false
        return handler.getPlayerInfo(entity.uuid) != null
    }

    private fun resolveEntity(entity: Entity): Entity? {
        if (entity is ArmorStand && entity.isInvisible) {
            val world = Minecraft.getInstance().level ?: return null
            val byId = world.getEntity(entity.id - 1)
            // Reject the id-1 candidate if it's any real connected player (not just self).
            if (byId != null && byId !is ArmorStand && !isRealPlayer(byId) && byId.isAlive) return byId
            val candidates = world.getEntitiesOfClass(
                LivingEntity::class.java, entity.boundingBox.inflate(0.5, 1.5, 0.5)
            ) { it !is ArmorStand && !isRealPlayer(it) && it.isAlive }
            return candidates.minByOrNull { val dx = it.x - entity.x; val dz = it.z - entity.z; dx*dx + dz*dz }
        }
        return entity
    }

    fun getOutlineColor(entity: Entity): Int {
        val cfg = FamilyConfigManager.config.highlight
        // Critter Safari outlines belong to their own category and ignore this
        // category's master toggle. Sparkling wins over the plain critter colour.
        val safari = FamilyConfigManager.config.safari
        if (entity in SparklingCritterHighlight.trackedEntities()) {
            return parseOutlineColor(safari.sparklingColor)
        }
        // Zone highlight in outline style; inside the Safari that is always on and
        // owned by the Safari category, so it is checked before the master switch.
        // Invisible entities are skipped: the glow shader would draw their silhouette
        // (the silverfish hiding inside a Duplico, the name / capture armour stands),
        // and those already get the block box from the invisible fallback below.
        // Display entities (a Gimmiegold is an item display over an invisible fish) render
        // through the outline pass like mobs do, so they are outlined rather than boxed.
        if (bestiaryActive() && BestiaryZoneHighlight.zoneOutline() && entity in bestiaryHighlighted && !entity.isInvisible && (entity is LivingEntity || entity is Display)) {
            return parseOutlineColor(BestiaryZoneHighlight.zoneColor())
        }
        if (!cfg.enabled) return 0
        // Sparkling overrides the normal colors for both styles.
        val sparklingSet = SparklingCritterHighlight.trackedEntities()
        if (cfg.drawingStyle == 1 && entity in highlighted) {
            return parseOutlineColor(if (entity in sparklingSet) safari.sparklingColor else cfg.color)
        }
        return 0
    }

    private fun parseOutlineColor(s: String): Int = try {
        FaColour.argb(s)
    } catch (e: Exception) { 0xFFFF0000.toInt() }

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            if (!shouldScan()) {
                if (highlighted.isNotEmpty()) highlighted.clear()
                if (bestiaryHighlighted.isNotEmpty()) bestiaryHighlighted.clear()
                return@register
            }
            val interval = FamilyConfigManager.config.highlight.highlightRescanInterval.toInt().coerceIn(1, 20)
            if (tick++ % interval != 0) return@register
            rescan()
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            highlighted.clear()
            bestiaryHighlighted.clear()
        }
    }

    fun rescan() {
        highlighted.clear()
        bestiaryHighlighted.clear()
        val world = Minecraft.getInstance().level ?: return
        if (!shouldScan()) return
        world.entitiesForRendering().forEach { entity ->
            if (!entity.isAlive) return@forEach
            // Skip any real connected player up-front. This prevents matches like the search
            // term "dragon" highlighting a player named "dragonslayer213". NPC mobs that use
            // player skins (Hypixel's fake-player NPCs) pass this check because they are not
            // in the tab list — they will still be highlighted normally.
            if (isRealPlayer(entity)) return@forEach
            val manual = matchesManual(entity)
            // A shape-rule hit IS the thing to mark (the Gazer's head-wearing stand, a
            // mound's interaction box): it must not be resolved to a neighbour or dropped
            // as a nametag stand, which is what the two guards below do for name matches.
            val ruleHit = BestiaryZoneHighlight.zoneOn() && BestiaryZoneHighlight.matchesNameless(entity)
            val bestiary = ruleHit || matchesBestiary(entity)
            if (manual || bestiary) {
                // FIX: if resolveEntity returns null (nametag stand can't find its real mob
                // because the mob died this tick), skip entirely. The old `?: entity` fallback
                // would add the armor stand itself to `highlighted`, causing the highlight to
                // briefly snap to the stand's position before it despawns — visible flicker.
                val target = if (ruleHit) entity else (resolveEntity(entity) ?: return@forEach)
                // Defensive: never highlight an invisible nametag stand directly.
                if (!ruleHit && target is ArmorStand && target.isInvisible) return@forEach
                // Defensive: resolveEntity already filters real players, but double-check.
                if (isRealPlayer(target)) return@forEach
                if (!target.isAlive) return@forEach
                if (manual) highlighted.add(target)
                if (bestiary) bestiaryHighlighted.add(target)
            }
        }
    }

    fun onWorldRender(matrices: PoseStack, collector: SubmitNodeCollector, cam: Vec3) {
        val config = FamilyConfigManager.config.highlight
        // Safari highlights draw even with this category off; they are their own feature.
        if (!config.enabled && SparklingCritterHighlight.trackedEntities().isEmpty() && !BestiaryZoneHighlight.zoneOn()) return
        val shulkerTargets = ShulkerBoxHighlight.trackedEntities() + SparklingCritterHighlight.trackedEntities()
        val sparklingSet = SparklingCritterHighlight.trackedEntities().toSet()
        if (highlighted.isEmpty() && bestiaryHighlighted.isEmpty() && shulkerTargets.isEmpty()) return

        fun parseRgb(s: String, fallback: Triple<Float, Float, Float>): Triple<Float, Float, Float> = try {
            FaColour.floats(s).let { Triple(it[0], it[1], it[2]) }
        } catch (e: Exception) { fallback }

        val (r, g, b) = parseRgb(config.color, Triple(1f, 0f, 0f))

        highlighted.removeIf { !it.isAlive }
        bestiaryHighlighted.removeIf { !it.isAlive }

        // ── ESP boxes — each source drawn with its own color/style ────
        fun drawBoxes(targets: Set<Entity>, rgb: Triple<Float, Float, Float>) {
            val (br, bg, bb2) = rgb
            fun drawAll(alpha: Float, renderType: RenderType) {
                collector.submitCustomGeometry(matrices, renderType) { entry, buf ->
                    for (entity in targets) {
                        if (!entity.isAlive) continue
                        val bb = highlightBox(entity)
                        drawBoxEdges(buf, entry,
                            (bb.minX - cam.x).toFloat(), (bb.minY - cam.y).toFloat(), (bb.minZ - cam.z).toFloat(),
                            (bb.maxX - cam.x).toFloat(), (bb.maxY - cam.y).toFloat(), (bb.maxZ - cam.z).toFloat(),
                            br, bg, bb2, alpha)
                    }
                }
            }
            drawAll(1.0f, FamilyRenderTypes.LINES)
            drawAll(1.0f, FamilyRenderTypes.LINES_NO_DEPTH)
        }

        if (config.drawingStyle == 0 && highlighted.isNotEmpty()) {
            drawBoxes(highlighted - sparklingSet, Triple(r, g, b))
        }
        if (bestiaryActive() && !BestiaryZoneHighlight.zoneOutline() && bestiaryHighlighted.isNotEmpty()) {
            drawBoxes(bestiaryHighlighted - sparklingSet, parseRgb(BestiaryZoneHighlight.zoneColor(), Triple(1f, 0.67f, 0f)))
        }
        // Outline style cannot show an invisible mob (it is never rendered, so the
        // outline pass never sees it): fall back to a box for those.
        if (config.drawingStyle == 1 && highlighted.isNotEmpty()) {
            val hidden = highlighted.filterTo(HashSet()) { it.isInvisible }
            if (hidden.isNotEmpty()) drawBoxes(hidden - sparklingSet, Triple(r, g, b))
        }
        if (bestiaryActive() && BestiaryZoneHighlight.zoneOutline() && bestiaryHighlighted.isNotEmpty()) {
            // Invisible mobs and non-living targets (interaction boxes, displays) have
            // nothing for the outline pass to draw: they get the box instead.
            val hidden = bestiaryHighlighted.filterTo(HashSet()) { it.isInvisible || (it !is LivingEntity && it !is Display) }
            if (hidden.isNotEmpty()) drawBoxes(hidden - sparklingSet, parseRgb(BestiaryZoneHighlight.zoneColor(), Triple(1f, 0.67f, 0f)))
        }

    }

    fun hasHighlighted() = (highlighted.isNotEmpty() || bestiaryHighlighted.isNotEmpty()) && shouldScan()

    /**
     * Box to draw for [entity]. An invisible mob smaller than a block is a
     * disguised critter (Duplico = silverfish under a block display): draw the
     * block it is pretending to be, not its tiny hitbox.
     */
    private fun highlightBox(entity: Entity): AABB {
        val bb = entity.boundingBox
        val tiny = bb.xsize < 1.0 && bb.ysize < 1.0
        val drawnAsIs = !entity.isInvisible && entity is LivingEntity
        if (!tiny || drawnAsIs) return bb
        val cx = (bb.minX + bb.maxX) / 2.0
        val cz = (bb.minZ + bb.maxZ) / 2.0
        return AABB(cx - 0.5, bb.minY, cz - 0.5, cx + 0.5, bb.minY + 1.0, cz + 0.5)
    }

    internal fun drawBoxEdges(
        buf: VertexConsumer,
        entry: PoseStack.Pose,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val edges = arrayOf(
            floatArrayOf(x1,y1,z1,x2,y1,z1), floatArrayOf(x2,y1,z1,x2,y1,z2),
            floatArrayOf(x2,y1,z2,x1,y1,z2), floatArrayOf(x1,y1,z2,x1,y1,z1),
            floatArrayOf(x1,y2,z1,x2,y2,z1), floatArrayOf(x2,y2,z1,x2,y2,z2),
            floatArrayOf(x2,y2,z2,x1,y2,z2), floatArrayOf(x1,y2,z2,x1,y2,z1),
            floatArrayOf(x1,y1,z1,x1,y2,z1), floatArrayOf(x2,y1,z1,x2,y2,z1),
            floatArrayOf(x2,y1,z2,x2,y2,z2), floatArrayOf(x1,y1,z2,x1,y2,z2)
        )
        for (e in edges) {
            val dx = e[3]-e[0]; val dy = e[4]-e[1]; val dz = e[5]-e[2]
            buf.addVertex(entry, e[0], e[1], e[2]).setColor(r, g, b, a).setNormal(entry, dx, dy, dz).setLineWidth(2.0f)
            buf.addVertex(entry, e[3], e[4], e[5]).setColor(r, g, b, a).setNormal(entry, dx, dy, dz).setLineWidth(2.0f)
        }
    }
}