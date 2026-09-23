package org.kyowa.familyaddons.party

import org.kyowa.familyaddons.COLOR_CODE_REGEX
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft

object PartyTracker {

    private val RANK_REGEX = Regex("""\[[^\]]+\]\s*""")
    private val SYMBOL_REGEX = Regex("[+★✦✧☆✪✫✬✭✮✯❖◆◇◈•●▪■▶»():,]")
    private val SPACE_REGEX = Regex("""\s+""")
    private val NAME_REGEX = Regex("[A-Za-z0-9_]{3,16}")
    private val PARTY_CHAT_REGEX = Regex("""^Party\s*[>»]\s*(?:\[[^\]]+\]\s*)?([^:]+):\s*(.+)$""")
    private val JOINED_REGEX = Regex("""^(?:\[[^\]]+\]\s*)?(.+?) joined the party\.$""", RegexOption.IGNORE_CASE)
    private val LEFT_REGEX = Regex("""^(?:\[[^\]]+\]\s*)?(.+?) (?:left the party|has left the party)\.$""", RegexOption.IGNORE_CASE)
    private val KICKED_REGEX = Regex("""^(?:\[[^\]]+\]\s*)?(.+?) has been removed from the party\.$""", RegexOption.IGNORE_CASE)
    private val TRANSFER_REGEX = Regex("""^The party was transferred to (?:\[[^\]]+\]\s*)?(\S+)""", RegexOption.IGNORE_CASE)
    private val DISBANDED_REGEX = Regex("""disbanded the party|You left the party\.|The party has been disbanded""", RegexOption.IGNORE_CASE)
    private val JOINED_THEIR_PARTY_REGEX = Regex("""^You have joined (?:\[[^\]]+\]\s*)?(\S+)'s party!$""", RegexOption.IGNORE_CASE)
    private val PARTYING_WITH_REGEX = Regex("""^You'll be partying with: (.+)$""", RegexOption.IGNORE_CASE)
    /** Header of /p list: "Party Members (4)" — the one line that states the real size. */
    private val COUNT_REGEX = Regex("""^Party Members \((\d+)\)$""")
    private val PF_JOIN_REGEX = Regex("""^Party Finder > (?:\[[^\]]+\]\s*)?([A-Za-z0-9_]{3,16}) joined the (?:dungeon )?group!""", RegexOption.IGNORE_CASE)
    private val OFFLINE_REMOVED_REGEX = Regex("""^(?:Kicked (?:\[[^\]]+\]\s*)?([A-Za-z0-9_]{3,16}) because they were offline\.|(?:\[[^\]]+\]\s*)?([A-Za-z0-9_]{3,16}) was removed from (?:your|the) party because they disconnected\.?)$""", RegexOption.IGNORE_CASE)
    /** "[MVP+] X has promoted [VIP] Y to Party Moderator" / "has demoted [VIP] Y to Party Member" */
    private val PROMOTED_MOD_REGEX = Regex("""has promoted (?:\[[^\]]+\]\s*)?([A-Za-z0-9_]{3,16}) to Party Moderator""", RegexOption.IGNORE_CASE)
    private val DEMOTED_REGEX = Regex("""has demoted (?:\[[^\]]+\]\s*)?([A-Za-z0-9_]{3,16}) to Party Member""", RegexOption.IGNORE_CASE)
    /** "[MVP+] X has promoted [VIP] Y to Party Leader" */
    private val PROMOTED_LEADER_REGEX = Regex("""has promoted (?:\[[^\]]+\]\s*)?([A-Za-z0-9_]{3,16}) to Party Leader""", RegexOption.IGNORE_CASE)
    /** "You invited [MVP+] X to your party!" — only the leader or a moderator can, so with no leader known it is you. */
    private val YOU_INVITED_REGEX = Regex("""^You invited (?:\[[^\]]+\]\s*)?[A-Za-z0-9_]{3,16} to your party!""", RegexOption.IGNORE_CASE)
    private val KICKED_SELF_REGEX = Regex("""^You have been kicked from the party by""", RegexOption.IGNORE_CASE)
    private val NOT_IN_PARTY_REGEX = Regex("""^You are not (?:currently )?in a party""", RegexOption.IGNORE_CASE)

    // name -> rank string (e.g. "MVP+")
    val members = mutableMapOf<String, String>()
    var leader: String? = null

    /**
     * Party moderators, lowercase. Learned from /p list, from promote/demote lines, and
     * from transfers: on Hypixel the leader who hands leadership over becomes a
     * moderator, and whoever becomes leader stops being one.
     */
    val moderators = LinkedHashSet<String>()

    fun isModerator(name: String): Boolean = name.lowercase() in moderators

    /** Sets the leader, applying the transfer rule: the old leader steps down to moderator. */
    private fun setLeader(name: String, transfer: Boolean) {
        val old = leader
        if (transfer && old != null && !old.equals(name, ignoreCase = true)) moderators.add(old.lowercase())
        moderators.remove(name.lowercase())
        leader = name
    }

    /** True once Hypixel has said you are in no party; cleared by any party line. */
    @Volatile var noParty = false

    private val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "fa-party").apply { isDaemon = true } }

    private fun selfName(): String? = Minecraft.getInstance().player?.name?.string

    /**
     * Whether you lead the party: true/false when known, null when the leader has not
     * been seen yet. Alone (no party) counts as leading, since nobody else's mod can
     * be acting for the group.
     */
    fun selfIsLeader(): Boolean? {
        if (noParty) return true
        val l = leader ?: return null
        val me = selfName() ?: return null
        return l.equals(me, ignoreCase = true)
    }

    /**
     * Runs [action] only when you lead the party. If that is not known yet it asks
     * /p list, whose "Party Leader:" line settles it, and decides 600 ms later. Used
     * for everything Hypixel only accepts from the leader (transfer, kick, warp,
     * requeue), so four people running the mod do not all fire the same command and
     * three of them collect "You are not the party leader".
     */
    fun ifLeader(action: () -> Unit) = ifRole(allowModerator = false, action)

    /** Like [ifLeader] but moderators qualify too: what /p invite accepts. */
    fun ifLeaderOrModerator(action: () -> Unit) = ifRole(allowModerator = true, action)

    private fun qualifies(allowModerator: Boolean): Boolean? {
        if (allowModerator) selfName()?.let { if (isModerator(it)) return true }
        return selfIsLeader()
    }

    private fun ifRole(allowModerator: Boolean, action: () -> Unit) {
        when (qualifies(allowModerator)) {
            true -> action()
            false -> return
            null -> {
                Minecraft.getInstance().player?.connection?.sendCommand("p list")
                scheduler.schedule({
                    Minecraft.getInstance().execute { if (qualifies(allowModerator) == true) action() }
                }, 600, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
        }
    }

    /** Size from the last /p list header; -1 once membership changed since. */
    @Volatile var listedCount: Int = -1

    /**
     * Best current party size, never below 1 (you). Uses the /p list header
     * when fresh, else the cached names plus yourself (the cache never learns
     * you from chat), and as a floor the real players standing in this world
     * that also have a tab-list entry (Hypixel NPCs have no tab entry, tab
     * info lines have no entity), which covers a cache that missed someone.
     */
    fun partySize(): Int {
        val mc = Minecraft.getInstance()
        val self = mc.player?.name?.string
        val names = members.keys.mapTo(HashSet()) { it.lowercase() }
        if (self != null) names.add(self.lowercase())
        val cached = if (listedCount > 0) listedCount else names.size
        return maxOf(cached, playersInWorld(), 1)
    }

    /**
     * Real players in this world that also have a tab-list entry (Hypixel NPCs have
     * no tab entry, tab info lines have no entity). Inside a private instance such as
     * a Kuudra run this IS the party: nobody else can be there, and someone who
     * disconnected is gone from it, so it is the number to trust over the cache.
     */
    fun playersInWorld(): Int = realPlayersInWorld().size

    /**
     * Whether a UUID belongs to a real account. Hypixel's NPCs (Elle in Kuudra, the
     * lobby NPCs) DO get tab-list entries, but their profiles carry generated
     * version-2 UUIDs; Mojang accounts are always version 4.
     */
    fun isRealPlayer(uuid: java.util.UUID): Boolean {
        val mc = Minecraft.getInstance()
        if (uuid.version() != 4) return false
        return mc.connection?.getPlayerInfo(uuid) != null
    }

    /**
     * Everyone seen in the current Kuudra instance since it began, lowercase IGNs.
     * The client only has entities for players within tracking range, so a single
     * world count misses whoever is across the arena, which is how a full party
     * read as 3/4 at the requeue check. Everyone is together at the start, and
     * sampling once a second afterwards catches late loaders. A leave, kick or
     * offline line in chat takes that player out again, so someone who quits
     * mid-run is not counted at the end.
     */
    val runPlayers = HashSet<String>()

    /** Called by KuudraState when the client lands in a Kuudra instance. */
    fun startRun() { runPlayers.clear(); sampleRun() }

    fun sampleRun() { runPlayers.addAll(realPlayersInWorld()) }

    /** Party size inside the instance: everyone seen this run, never below what is loaded now. */
    fun playersInRun(): Int = maxOf(runPlayers.size, playersInWorld())

    /** Lowercase IGNs of the real players in this world, yourself included. */
    fun realPlayersInWorld(): Set<String> {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return emptySet()
        return level.players().filter { isRealPlayer(it.uuid) }.mapTo(HashSet()) { it.name.string.lowercase() }
    }

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, _ ->
            val plain = message.string.replace(COLOR_CODE_REGEX, "").trim()
            handleLine(plain, message.string)
            true
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            members.clear()
            moderators.clear()
            leader = null
            listedCount = -1
            noParty = false
        }
    }

    private fun extractRank(raw: String): String {
        val match = Regex("""\[([^\]]+)\]""").find(raw)
        return match?.groupValues?.get(1) ?: ""
    }

    private fun handleLine(plain: String, original: String) {
        // Party chat — sender is a member
        val partyChat = PARTY_CHAT_REGEX.find(plain)
        if (partyChat != null) {
            noParty = false
            val name = cleanName(partyChat.groupValues[1])
            if (name.isNotEmpty() && !members.containsKey(name)) {
                members[name] = ""
            }
            return
        }

        // Who leads, from the lines that say so outright.
        PROMOTED_LEADER_REGEX.find(plain)?.let { setLeader(cleanName(it.groupValues[1]), transfer = true); noParty = false }
        PROMOTED_MOD_REGEX.find(plain)?.let { moderators.add(cleanName(it.groupValues[1]).lowercase()); noParty = false }
        DEMOTED_REGEX.find(plain)?.let { moderators.remove(cleanName(it.groupValues[1]).lowercase()) }
        if (leader == null && YOU_INVITED_REGEX.containsMatchIn(plain)) { leader = selfName(); noParty = false }

        // /p list responses
        COUNT_REGEX.find(plain)?.let { listedCount = it.groupValues[1].toIntOrNull() ?: -1 }
        when {
            plain.startsWith("Party Leader:") -> {
                val after = plain.removePrefix("Party Leader:").trim()
                val rank = extractRank(after)
                val name = cleanName(after)
                if (name.isNotEmpty()) {
                    leader = name
                    members[name] = rank
                    noParty = false
                    // A fresh list follows; its Moderators line (if any) refills this.
                    moderators.clear()
                }
            }
            plain.startsWith("Party Moderators:") -> {
                extractNamesWithRanks(plain).forEach { (name, rank) -> members[name] = rank; moderators.add(name.lowercase()) }
            }
            plain.startsWith("Party Members:") -> {
                extractNamesWithRanks(plain).forEach { (name, rank) -> members[name] = rank; moderators.remove(name.lowercase()) }
            }
        }

        // Join
        JOINED_REGEX.find(plain)?.let {
            val raw = it.groupValues[1]
            val rank = extractRank(raw)
            val name = cleanName(raw)
            if (name.isNotEmpty()) { members[name] = rank; listedCount = -1; noParty = false }
        }

        // Party Finder join ("Party Finder > [MVP+] X joined the group!")
        PF_JOIN_REGEX.find(plain)?.let {
            val name = cleanName(it.groupValues[1])
            if (name.isNotEmpty()) { members[name] = ""; listedCount = -1 }
        }

        // Leave
        LEFT_REGEX.find(plain)?.let {
            val name = cleanName(it.groupValues[1])
            members.remove(name)
            runPlayers.remove(name.lowercase())
            moderators.remove(name.lowercase())
            listedCount = -1
            if (leader?.equals(name, ignoreCase = true) == true) leader = null
        }

        // Kicked
        KICKED_REGEX.find(plain)?.let {
            val name = cleanName(it.groupValues[1])
            members.remove(name)
            runPlayers.remove(name.lowercase())
            moderators.remove(name.lowercase())
            listedCount = -1
        }

        // Removed for being offline / disconnected
        OFFLINE_REMOVED_REGEX.find(plain)?.let {
            val name = cleanName(it.groupValues[1].ifEmpty { it.groupValues[2] })
            if (name.isNotEmpty()) { members.remove(name); runPlayers.remove(name.lowercase()); moderators.remove(name.lowercase()); listedCount = -1 }
        }

        // You got kicked / you are not in a party
        if (KICKED_SELF_REGEX.containsMatchIn(plain) || NOT_IN_PARTY_REGEX.containsMatchIn(plain)) {
            members.clear()
            moderators.clear()
            leader = null
            listedCount = -1
            noParty = true
        }

        // Transfer
        TRANSFER_REGEX.find(plain)?.let {
            val name = cleanName(it.groupValues[1])
            if (name.isNotEmpty()) {
                setLeader(name, transfer = true)
                if (!members.containsKey(name)) members[name] = ""
                noParty = false
            }
        }

        // Disband / you left
        if (DISBANDED_REGEX.containsMatchIn(plain)) {
            members.clear()
            moderators.clear()
            leader = null
            listedCount = -1
            noParty = true
        }

        // "You have joined X's party!"
        JOINED_THEIR_PARTY_REGEX.find(plain)?.let {
            val name = cleanName(it.groupValues[1])
            if (name.isNotEmpty()) {
                leader = name
                members[name] = ""
                noParty = false
            }
        }

        // "You'll be partying with: [RANK] A, [RANK] B, ..."
        PARTYING_WITH_REGEX.find(plain)?.let {
            val rest = it.groupValues[1]
            // Split by comma then parse each segment
            rest.split(",").forEach { seg ->
                val rank = extractRank(seg.trim())
                val name = cleanName(seg.trim())
                if (name.isNotEmpty()) members[name] = rank
            }
        }
    }

    fun cleanName(s: String): String {
        var x = s.replace(RANK_REGEX, " ").trim()
        x = x.replace(SYMBOL_REGEX, " ")
        x = x.replace(SPACE_REGEX, " ").trim()
        val tokens = x.split(" ").filter { it.matches(NAME_REGEX) }
        return tokens.lastOrNull() ?: ""
    }

    private fun extractNamesWithRanks(line: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        // Split by ● or • separators that Hypixel uses between names
        val segments = line.split(Regex("[●•]"))
        for (seg in segments) {
            val rank = extractRank(seg)
            val name = cleanName(seg)
            if (name.isNotEmpty()) results.add(name to rank)
        }
        return results
    }

    fun isLeader(name: String): Boolean =
        leader?.equals(name, ignoreCase = true) == true

    fun resolveMember(query: String, allowSelf: Boolean, selfName: String): String? {
        val pool = if (allowSelf) members.keys.toList()
        else members.keys.filter { it.lowercase() != selfName.lowercase() }
        if (pool.isEmpty()) return null
        val q = query.lowercase()
        pool.find { it.lowercase() == q }?.let { return it }
        val tie = { arr: List<String> -> arr.sortedWith(compareBy({ it.length }, { it })).firstOrNull() }
        val starts = pool.filter { it.lowercase().startsWith(q) }
        if (starts.size == 1) return starts[0]
        if (starts.size > 1) return tie(starts)
        val inc = pool.filter { it.lowercase().contains(q) }
        if (inc.isEmpty()) return null
        return tie(inc)
    }
}
