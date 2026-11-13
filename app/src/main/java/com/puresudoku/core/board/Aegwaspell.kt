package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

/** One thing the player does in Aegwaspell. */
sealed class AAct {
    data class Build(val h: Int, val type: Int) : AAct()
    /** A road from the connected tile [a] to its neighbour [b]. */
    data class Road(val a: Int, val b: Int) : AAct()
    data class Upgrade(val h: Int) : AAct()
    data class Train(val n: Int) : AAct()
    data class Scout(val h: Int) : AAct()
    /** March every soldier against rival [r]. */
    data class Attack(val r: Int) : AAct()
    /** Tear down the building on [h]: half its cost comes back, and the tile is rubble until next season. */
    data class Demolish(val h: Int) : AAct()
    /** Rest the farm on [h] (or work it again): resting soil recovers. */
    data class Fallow(val h: Int) : AAct()
    /** Set decree [kind] (rations, taxes, work hours) to [level]. */
    data class Decree(val kind: Int, val level: Int) : AAct()
    /** Answer the waiting event card: [opt] 0 or 1. */
    data class Choose(val opt: Int) : AAct()
    object End : AAct()
}

/** A rival civilization: its capital and lands, and a strength that grows every season. */
class Rival(val name: String, var capital: Int, var strength: Int, var alive: Boolean = true)

/**
 * Aegwaspell: a single-player civilization game. You land at a port at the foot of a large, fogged world and grow a
 * people. Each season a harvest die is rolled and an event card is drawn. Every tile holds one building, and you can
 * only build on tiles your roads reach. Feed, house and please your people; train soldiers and take the rival
 * capitals. Win with a great population once every rival has fallen; lose if your last person dies.
 */
class Aegwaspell(override val map: RealmGrid, override val diff: Int) : AegLand {
    companion object {
        // terrain
        const val SEA = 0; const val PLAINS = 1; const val FIELDS = 2; const val MEADOW = 3; const val FOREST = 4
        const val HILLS = 5; const val MOUNTAIN = 6; const val SWAMP = 7; const val DESERT = 8; const val COAST = 9
        val TERRAIN = listOf("sea", "plains", "fields", "meadow", "forest", "hills", "mountains", "swamp", "desert", "coast")

        // resources
        const val FOOD = 0; const val TIMBER = 1; const val STONE = 2; const val ORE = 3; const val GOLD = 4
        val RES = listOf("food", "timber", "stone", "ore", "gold")

        // buildings
        const val NONE = 0; const val PORT = 1; const val VILLAGE = 2; const val TOWN = 3; const val FARM = 4; const val RANCH = 5
        const val FISHERY = 6; const val MILL = 7; const val QUARRY = 8; const val MINE = 9; const val GRANARY = 10; const val MARKET = 11
        const val BARRACKS = 12; const val TOWER = 13; const val TEMPLE = 14; const val COLOSSEUM = 15
        val NAME = listOf("", "Port", "Village", "Town", "Farm", "Ranch", "Fishery", "Lumber mill", "Quarry", "Mine", "Granary",
            "Market", "Barracks", "Watchtower", "Temple", "Colosseum",
            "Lighthouse", "Great Library", "Sky Temple", "Hanging Gardens", "Grand Citadel", "Royal Palace")
        val COST = arrayOf(
            IntArray(5), IntArray(5), intArrayOf(0, 3, 1, 0, 0), intArrayOf(0, 4, 4, 0, 2), intArrayOf(0, 2, 0, 0, 0), intArrayOf(0, 3, 0, 0, 0),
            intArrayOf(0, 2, 0, 0, 0), intArrayOf(0, 1, 1, 0, 0), intArrayOf(0, 2, 0, 0, 0), intArrayOf(0, 3, 2, 0, 0), intArrayOf(0, 3, 2, 0, 0),
            intArrayOf(0, 3, 3, 0, 0), intArrayOf(0, 4, 3, 0, 1), intArrayOf(0, 2, 2, 0, 0), intArrayOf(0, 0, 3, 0, 2), intArrayOf(0, 2, 5, 0, 3),
            // the wonders
            intArrayOf(0, 8, 12, 0, 8), intArrayOf(0, 10, 10, 0, 12), intArrayOf(0, 4, 16, 0, 10),
            intArrayOf(0, 12, 8, 0, 8), intArrayOf(0, 8, 16, 4, 6), intArrayOf(0, 12, 12, 0, 15),
        )
        /** People each building needs at work before it produces. */
        val WORKERS = intArrayOf(0, 0, 0, 0, 2, 2, 1, 2, 2, 3, 0, 2, 2, 1, 1, 2, 2, 3, 2, 3, 2, 2)
        /** People each building houses. */
        val HOUSING = intArrayOf(0, 8, 6, 15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val ALLOWED: Array<Set<Int>> = arrayOf(
            emptySet(), emptySet(),
            setOf(PLAINS, FIELDS, MEADOW, COAST, HILLS, DESERT), emptySet(),
            setOf(FIELDS, PLAINS), setOf(MEADOW, PLAINS), setOf(COAST), setOf(FOREST, SWAMP), setOf(HILLS, DESERT), setOf(MOUNTAIN, HILLS),
            setOf(PLAINS, FIELDS, MEADOW, COAST, HILLS, DESERT, FOREST), setOf(PLAINS, DESERT, COAST), setOf(PLAINS, MEADOW, HILLS, DESERT, FIELDS),
            setOf(PLAINS, FIELDS, MEADOW, COAST, HILLS, DESERT, FOREST, MOUNTAIN, SWAMP), setOf(PLAINS, HILLS, MOUNTAIN, MEADOW, DESERT), setOf(PLAINS, DESERT, MEADOW),
            emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(),   // wonders: see AegStewardship.WONDER_LAND
        )
        val SOLDIER_COST = intArrayOf(2, 0, 0, 1, 1)
        val SCOUT_COST = intArrayOf(1, 0, 0, 0, 1)
        const val SOLDIERS_PER_BARRACKS = 8

        val RADIUS = intArrayOf(5, 5, 6, 6, 7, 7)
        val RIVALS = intArrayOf(2, 2, 3, 3, 4, 4)
        val POP_GOAL = intArrayOf(35, 45, 55, 65, 75, 90)
        val RIVAL_START = intArrayOf(5, 6, 7, 8, 9, 10)
        /** Seasons between each point of rival growth. */
        val RIVAL_GROWS = intArrayOf(6, 5, 4, 4, 3, 3)
        val RIVAL_NAMES = listOf("the Ironhold", "the Thornmarch", "the Saltreach", "the Duskfen")
        val SEASON = listOf("Spring", "Summer", "Autumn", "Winter")

        private val NB_Q = intArrayOf(1, 1, 0, -1, -1, 0)
        private val NB_R = intArrayOf(0, -1, -1, 0, 1, 1)
        private val gridCache = HashMap<Int, RealmGrid>()
        fun grid(radius: Int): RealmGrid = synchronized(gridCache) {
            gridCache.getOrPut(radius) {
                val qs = ArrayList<Int>(); val rs = ArrayList<Int>()
                for (q in -radius..radius) for (r in -radius..radius) if (abs(q + r) <= radius) { qs += q; rs += r }
                RealmGrid(qs.toIntArray(), rs.toIntArray())
            }
        }

        fun newGame(d: Difficulty, rng: Random): Aegwaspell = Aegwaspell(grid(RADIUS[d.ordinal]), d.ordinal).also { it.deal(rng) }

        fun fromState(s: IntArray): Aegwaspell {
            val g = Aegwaspell(grid(s[0]), s[1]); g.load(s); return g
        }
    }

    override val H = map.HEXES
    override val terrain = IntArray(H)
    override val building = IntArray(H)
    val revealed = BooleanArray(H)
    val ruins = BooleanArray(H)
    /** Which rival owns each tile (-1: none). */
    val rivalLand = IntArray(H) { -1 }
    /** Roads, on the edge between two touching tiles. */
    val road = BooleanArray(map.EDGES)
    override val res = IntArray(5)
    override var pop = 6
    var soldiers = 0
    override var happiness = 60
    override var season = 0
    override var capital = -1
    var lastHarvest = 0
    val lastDice = intArrayOf(0, 0)
    val rivals = ArrayList<Rival>()
    val deck = ArrayList<Int>()
    /** The last event card drawn (-1: none yet). */
    var lastCard = -1
    var over = false
    var won = false
    override val log = ArrayList<String>()
    /** Decrees, choices, soil, children, wonders and the chronicle. */
    val sw = AegStewardship(this)

    val year get() = season / 4 + 1
    val seasonName get() = SEASON[season % 4]
    val winter get() = season % 4 == 3
    val goal get() = POP_GOAL[diff]

    // ---------------------------------------------------------------- the world

    private fun deal(rng: Random) {
        val radius = RADIUS[diff]
        // regions: scattered seeds, each tile takes the terrain of its nearest seed
        val land = listOf(PLAINS, PLAINS, FIELDS, FIELDS, MEADOW, MEADOW, FOREST, FOREST, FOREST, HILLS, HILLS, MOUNTAIN, MOUNTAIN, SWAMP, DESERT)
        val seeds = (0 until H).shuffled(rng).take(H / 5).map { it to land[rng.nextInt(land.size)] }
        for (h in 0 until H) {
            val ring = map.ring[h]
            terrain[h] = if (ring >= radius) SEA else seeds.minBy { (s, _) -> hexDist(s, h) * 10 + rng.nextInt(9) }.second
        }
        // a few inland lakes of sea, and the coast
        repeat(radius / 2) { val h = (0 until H).filter { map.ring[it] in 2 until radius - 1 }.random(rng); terrain[h] = SEA }
        for (h in 0 until H) if (terrain[h] != SEA && map.hexNbrs[h].any { terrain[it] == SEA } && rng.nextInt(100) < 55) terrain[h] = COAST
        // the port: the southernmost coast tile
        capital = (0 until H).filter { terrain[it] != SEA && map.hexNbrs[it].any { n -> terrain[n] == SEA } }.maxBy { map.hexY[it] * 10 - abs(map.hexX[it]) }
        terrain[capital] = COAST
        building[capital] = PORT
        for (n in map.hexNbrs[capital]) if (terrain[n] == SEA || terrain[n] == MOUNTAIN) terrain[n] = if (terrain[n] == SEA) SEA else HILLS
        // a little good land beside the port, so the first seasons can be survived
        map.hexNbrs[capital].filter { terrain[it] != SEA }.shuffled(rng).let { ns ->
            ns.getOrNull(0)?.let { terrain[it] = FIELDS }; ns.getOrNull(1)?.let { terrain[it] = FOREST }; ns.getOrNull(2)?.let { terrain[it] = PLAINS }
        }
        // rivals: far from the port, spread around the north
        val far = (0 until H).filter { map.ring[it] <= radius - 1 && hexDist(it, capital) >= radius }.shuffled(rng)
        for (i in 0 until RIVALS[diff]) {
            val spot = (far.filter { s -> rivals.all { hexDist(s, it.capital) >= 4 } }.ifEmpty { far.filter { s -> rivals.none { it.capital == s } } })
                .maxByOrNull { s -> hexDist(s, capital) * 3 + rivals.sumOf { hexDist(s, it.capital) } + rng.nextInt(4) } ?: break
            rivals += Rival(RIVAL_NAMES[i], spot, RIVAL_START[diff] + i * 2)
            terrain[spot] = PLAINS
            for (h in 0 until H) if (terrain[h] != SEA && hexDist(h, spot) <= 1) rivalLand[h] = i
        }
        // ruins hold treasure
        (0 until H).filter { terrain[it] != SEA && rivalLand[it] < 0 && hexDist(it, capital) >= 3 }.shuffled(rng).take(H / 16).forEach { ruins[it] = true }
        for (h in 0 until H) if (hexDist(h, capital) <= 2) revealed[h] = true
        res[FOOD] = 20; res[TIMBER] = 10; res[STONE] = 4; res[ORE] = 0; res[GOLD] = 5
        newDeck(rng)
        sw.seedForecast(rng)
    }

    fun hexDist(a: Int, b: Int): Int {
        val dq = map.hexQ[a] - map.hexQ[b]; val dr = map.hexR[a] - map.hexR[b]
        return (abs(dq) + abs(dr) + abs(dq + dr)) / 2
    }

    fun side(a: Int, b: Int): Int {
        val shared = map.hexVerts[a].filter { it in map.hexVerts[b] }
        return if (shared.size == 2) map.edgeOf(shared[0], shared[1]) else -1
    }

    fun isLand(h: Int) = terrain[h] != SEA

    override fun armed() = soldiers
    override fun loseSoldiers(n: Int) { soldiers = maxOf(0, soldiers - n) }
    override fun prideRival() { rivals.filter { it.alive }.maxByOrNull { it.strength }?.let { it.strength += 2 } }
    override fun sendHome(h: Int) {}

    /** Tiles the roads reach from the port. */
    override fun connected(): BooleanArray {
        val c = BooleanArray(H); val q = ArrayDeque<Int>(); q += capital; c[capital] = true
        while (q.isNotEmpty()) { val h = q.removeFirst(); for (n in map.hexNbrs[h]) if (!c[n] && road[side(h, n)]) { c[n] = true; q += n } }
        return c
    }

    // ---------------------------------------------------------------- people and goods

    fun housing() = sw.housing()
    fun jobs() = (0 until H).sumOf { WORKERS[building[it]] }
    fun count(type: Int) = (0 until H).count { building[it] == type }
    fun soldierCap() = count(BARRACKS) * SOLDIERS_PER_BARRACKS + if (sw.active(AegStewardship.CITADEL)) 8 else 0
    fun foodCap() = 30 + 40 * count(GRANARY) + 10 * count(TOWN)
    fun can(cost: IntArray) = (0..4).all { res[it] >= cost[it] }
    private fun pay(cost: IntArray) { for (r in 0..4) res[r] -= cost[r] }
    fun roadCost(b: Int) = when (terrain[b]) { MOUNTAIN, SWAMP -> intArrayOf(0, 2, 1, 0, 0); HILLS -> intArrayOf(0, 1, 1, 0, 0); else -> intArrayOf(0, 1, 0, 0, 0) }

    /** Buildings that get workers, food first: the rest stand idle. */
    override fun staffed(): BooleanArray {
        val out = BooleanArray(H)
        var free = pop
        val order = (0 until H).filter { WORKERS[building[it]] > 0 && !sw.fallow[it] }.sortedBy { when (building[it]) { FARM, RANCH, FISHERY -> 0; MILL, QUARRY -> 1; else -> 2 } }
        for (h in order) { val w = WORKERS[building[h]]; if (free >= w) { out[h] = true; free -= w } }
        return out
    }

    /** What each staffed building makes this season, before the harvest die. */
    fun production(harvest: Float = 1f, cold: Boolean = winter): IntArray {
        val out = IntArray(5)
        val st = staffed()
        val wa = sw.activeWonders(st)
        for (h in 0 until H) {
            if (WORKERS[building[h]] > 0 && !st[h] || sw.resting(h)) continue
            when (building[h]) {
                PORT -> { out[FOOD] += 3; out[GOLD] += 1 }
                FARM -> if (!cold) out[FOOD] += ((if (terrain[h] == FIELDS) 7 else 5) * harvest).roundToInt()
                RANCH -> out[FOOD] += if (cold) 3 else 4
                FISHERY -> out[FOOD] += if (cold) 3 else 4
                MILL -> out[TIMBER] += if (terrain[h] == FOREST) 3 else 2
                QUARRY -> out[STONE] += 3
                MINE -> { out[ORE] += 2; out[GOLD] += 1 }
                MARKET -> out[GOLD] += 3
            }
            sw.adjust(h, out, cold, wa)
        }
        return out
    }

    fun eats() = sw.mouths() + soldiers
    /** Food the land makes in an average season of the year, winter included. */
    fun yearlyFood() = (3 * production(1f, false)[FOOD] + production(1f, true)[FOOD]) / 4

    // ---------------------------------------------------------------- actions

    fun buildable(h: Int, conn: BooleanArray = connected()): List<Int> {
        if (!conn[h] || building[h] != NONE || rivalLand[h] >= 0 || sw.rubble[h] > 0) return emptyList()
        return (VILLAGE..COLOSSEUM).filter { it != TOWN && terrain[h] in ALLOWED[it] } + sw.wondersFor(h)
    }

    fun roadTargets(a: Int, conn: BooleanArray = connected()): List<Int> =
        if (!conn[a]) emptyList() else map.hexNbrs[a].filter { isLand(it) && revealed[it] && !conn[it] && rivalLand[it] < 0 && !road[side(a, it)] }

    /** Rivals whose land touches a tile your roads reach: your army can march on them. */
    fun attackable(conn: BooleanArray = connected()): List<Int> =
        rivals.indices.filter { r -> rivals[r].alive && (0 until H).any { h -> rivalLand[h] == r && map.hexNbrs[h].any { conn[it] } } }

    fun scoutable(h: Int): Boolean = revealed[h] && map.hexNbrs[h].any { !revealed[it] }

    fun legal(a: AAct): Boolean {
        if (over) return false
        // a waiting card must be answered first (decrees may still change)
        if (sw.choice >= 0 && a !is AAct.Choose && a !is AAct.Decree) return false
        val conn = connected()
        return when (a) {
            is AAct.Demolish -> sw.canDemolish(a.h, conn)
            is AAct.Fallow -> conn[a.h] && building[a.h] == FARM
            is AAct.Decree -> a.kind in 0..2 && a.level in 0..(if (a.kind == AegStewardship.HOURS) 1 else 2) && sw.decree[a.kind] != a.level
            is AAct.Choose -> sw.canChoose(a.opt)
            is AAct.Build -> a.type in buildable(a.h, conn) && can(COST[a.type])
            is AAct.Road -> a.b in roadTargets(a.a, conn) && can(roadCost(a.b))
            is AAct.Upgrade -> building[a.h] == VILLAGE && can(COST[TOWN])
            is AAct.Train -> a.n > 0 && soldiers + a.n <= soldierCap() && (0..4).all { res[it] >= SOLDIER_COST[it] * a.n } && pop - a.n >= 1
            is AAct.Scout -> scoutable(a.h) && can(SCOUT_COST)
            is AAct.Attack -> soldiers > 0 && a.r in attackable(conn)
            AAct.End -> true
        }
    }

    fun apply(a: AAct, rng: Random) {
        if (!legal(a)) return
        when (a) {
            is AAct.Build -> { pay(COST[a.type]); building[a.h] = a.type; sw.started(a.h); log += "You built a ${NAME[a.type].lowercase()} on the ${TERRAIN[terrain[a.h]]}" }
            is AAct.Road -> {
                pay(roadCost(a.b)); road[side(a.a, a.b)] = true
                reveal(a.b, if (terrain[a.b] == HILLS || terrain[a.b] == MOUNTAIN) 2 else 1)
                log += "Your road reached the ${TERRAIN[terrain[a.b]]}"
                if (ruins[a.b]) treasure(a.b, rng)
            }
            is AAct.Upgrade -> { pay(COST[TOWN]); building[a.h] = TOWN; log += "Your village grew into a town" }
            is AAct.Train -> { for (r in 0..4) res[r] -= SOLDIER_COST[r] * a.n; pop -= a.n; soldiers += a.n; log += "${a.n} of your people took up arms" }
            is AAct.Scout -> { pay(SCOUT_COST); reveal(a.h, 2); log += "Your scouts mapped the land beyond the ${TERRAIN[terrain[a.h]]}" }
            is AAct.Attack -> attack(a.r, rng)
            is AAct.Demolish -> sw.demolish(a.h, COST[building[a.h]])
            is AAct.Fallow -> sw.setFallow(a.h)
            is AAct.Decree -> sw.setDecree(a.kind, a.level)
            is AAct.Choose -> sw.choose(a.opt, rng)
            AAct.End -> endSeason(rng)
        }
        checkEnd()
    }

    private fun reveal(c: Int, r: Int) { for (h in 0 until H) if (hexDist(h, c) <= r) revealed[h] = true }

    private fun treasure(h: Int, rng: Random) {
        ruins[h] = false
        when (rng.nextInt(4)) {
            0 -> { res[GOLD] += 8; log += "Ruins! Your people found 8 gold" }
            1 -> { pop += 3; log += "Ruins! 3 survivors joined your people" }
            2 -> { res[STONE] += 5; res[TIMBER] += 3; log += "Ruins! You salvaged 5 stone and 3 timber" }
            else -> { reveal(h, 3); log += "Ruins! An old map showed the land around" }
        }
    }

    private fun roll(rng: Random): Int { lastDice[0] = rng.nextInt(1, 7); lastDice[1] = rng.nextInt(1, 7); return lastDice[0] + lastDice[1] }

    private fun attack(r: Int, rng: Random) {
        val rv = rivals[r]; val n = soldiers
        val a = n + roll(rng); val d = rv.strength + roll(rng)
        if (a > d) {
            rv.alive = false
            val lost = minOf(n - 1, (rv.strength + 1) / 2); soldiers -= lost
            for (h in 0 until H) if (rivalLand[h] == r) { rivalLand[h] = -1; revealed[h] = true }
            res[GOLD] += 10 + rv.strength / 2
            val joined = 4 + rv.strength / 4; pop += joined
            log += "Your army took the capital of ${rv.name}! ${if (lost > 0) "$lost soldiers fell. " else ""}$joined of their people joined you, with ${10 + rv.strength / 2} gold"
        } else {
            val lost = (n + 1) / 2; soldiers -= lost; rv.strength = maxOf(1, rv.strength - n / 3)
            log += "Your army was thrown back from ${rv.name}: $lost soldiers fell (they rolled ${d - rv.strength} on ${rv.strength} strength)"
        }
    }

    // ---------------------------------------------------------------- the season

    private fun endSeason(rng: Random) {
        // the harvest die
        sw.growUp()
        val wa = sw.activeWonders()
        val dice = sw.harvestDice(rng, lastDice); lastHarvest = dice
        val harvest = sw.harvestShare(dice, wa)
        val got = production(harvest)
        for (r in 0..4) res[r] += got[r]
        log += "$seasonName harvest: rolled $dice" + when { winter -> " (winter: the fields lie fallow)"; dice <= 4 -> ", a poor year"; dice >= 10 -> ", a bountiful year!"; else -> "" }
        // temples and the colosseum
        // happiness settles toward a middling mood; temples lift it
        happiness += (if (happiness > 55) -2 else if (happiness < 45) 2 else 0) + 4 * count(TEMPLE).coerceAtMost(3)
        sw.seasonMoodAndGold(wa)
        val st = staffed()
        for (h in 0 until H) if (building[h] == COLOSSEUM && st[h]) { val d = rng.nextInt(1, 7); if (d >= 4) { happiness += 12; log += "Games at the colosseum (rolled $d): your people cheer" } }
        // the event card
        drawCard(rng)
        // eating
        val need = eats()
        if (res[FOOD] >= need) res[FOOD] -= need
        else {
            val short = need - res[FOOD]; res[FOOD] = 0
            val died = maxOf(1, short / 2); pop = maxOf(0, pop - died)
            happiness -= 10
            log += "Famine! $died of your people starved"
        }
        if (res[FOOD] > foodCap()) { log += "${res[FOOD] - foodCap()} food spoiled: build granaries to store more"; res[FOOD] = foodCap() }
        // homes
        val room = housing() - sw.people()
        if (room < 0) { val left = maxOf(1, -room / 3); pop -= left; happiness -= 4; log += "$left homeless people left your lands" }
        // growth
        happiness = happiness.coerceIn(0, 100)
        val surplus = res[FOOD] >= eats() / 2
        when {
            happiness < 10 && pop > 0 -> { val gone = maxOf(1, pop / 10); pop -= gone; revolt(rng); log += "Revolt! $gone people rose against you" }
            happiness < 30 && pop > 0 -> { val gone = maxOf(1, pop / 20); pop -= gone; log += "Unhappy, $gone people left" }
            // children are born only while the land can feed one more mouth through the year
            surplus && room > 0 && yearlyFood() > eats() -> {
                val bf = sw.birthFactor(wa)
                val born = if (bf <= 0.0) 0 else minOf(room, maxOf(1, (pop * (if (happiness >= 60) 0.15 else 0.08) * bf).roundToInt()))
                sw.kids[0] += born; if (born > 0) log += "$born children were born"
            }
        }
        // rivals grow
        for (rv in rivals) if (rv.alive && season % RIVAL_GROWS[diff] == 0) rv.strength++
        sw.endOfSeason(staffed())
        season++
        if (season % 4 == 0) sw.record(year - 1)
    }

    private fun revolt(rng: Random) {
        val hs = (0 until H).filter { building[it] != NONE && building[it] != PORT }
        if (hs.isNotEmpty()) { val h = hs[rng.nextInt(hs.size)]; log += "The rebels burned your ${NAME[building[h]].lowercase()}"; building[h] = NONE }
    }

    // ---------------------------------------------------------------- events

    /** The event cards: each is drawn once per shuffle of the deck. */
    val CARDS = listOf(
        "Bountiful season", "Drought", "Migrants", "Plague", "Bandits", "Merchant caravan", "Festival", "Wolves",
        "Flood", "Rockfall", "Forest fire", "Gold vein", "Raid", "Envoy's demand", "Wandering hero", "Old map",
        "Harsh frost", "Blessing", "Unrest", "Shoal of fish", "Builders' guild", "Herd sickness", "Volunteers", "Quiet season",
    )

    private fun newDeck(rng: Random) { deck.clear(); deck += CARDS.indices; deck += listOf(23, 23); deck.shuffle(rng) }

    private fun anyOf(rng: Random, vararg types: Int) = (0 until H).filter { building[it] in types }.let { if (it.isEmpty()) -1 else it[rng.nextInt(it.size)] }

    private fun drawCard(rng: Random) {
        if (deck.isEmpty()) newDeck(rng)
        val c = deck.removeAt(deck.lastIndex); lastCard = c
        // some cards ask you to choose: you answer at the start of your next turn
        if (c in AegStewardship.CHOICES) { sw.choice = c; log += "${CARDS[c]}: a choice awaits you"; return }
        val text: String = when (c) {
            0 -> { res[FOOD] += 4 + count(FARM) * 2; "Bountiful season: +${4 + count(FARM) * 2} food" }
            1 -> { val lost = minOf(res[FOOD], 3 + count(FARM) * 2); res[FOOD] -= lost; "Drought: the fields withered, $lost food lost" }
            2 -> { val n = minOf(maxOf(0, housing() - pop), 2 + diff / 2 + 2); pop += n; if (n > 0) "Migrants: $n newcomers settled in your homes" else "Migrants passed by: you had no homes for them" }
            3 -> { val n = if (count(TEMPLE) > 0) pop / 40 else maxOf(1, pop / 15); pop -= n; "Plague: $n of your people died" + if (count(TEMPLE) > 0) " (your temples eased it)" else "" }
            4 -> if (soldiers >= 3) "Bandits: your soldiers drove them off" else { val g = res[GOLD] / 2; val f = res[FOOD] / 4; res[GOLD] -= g; res[FOOD] -= f; "Bandits stole $g gold and $f food. Soldiers would stop them" }
            5 -> { val g = 3 + 3 * count(MARKET); res[GOLD] += g; "Merchant caravan: you traded for $g gold" }
            6 -> { happiness += 10; val f = minOf(res[FOOD], 4); res[FOOD] -= f; "Festival: your people feast (+10 happiness, $f food)" }
            7 -> { val h = anyOf(rng, RANCH); if (h >= 0) { val f = minOf(res[FOOD], 4); res[FOOD] -= f; "Wolves raided a ranch: $f food lost" } else "Wolves howl in the hills, but find nothing to take" }
            8 -> { val h = (0 until H).filter { building[it] in setOf(FARM, FISHERY) && map.hexNbrs[it].any { n -> terrain[n] == SEA || terrain[n] == SWAMP } }.let { if (it.isEmpty()) -1 else it[rng.nextInt(it.size)] }
                if (h >= 0) { val what = NAME[building[h]].lowercase(); building[h] = NONE; "Flood: the waters swept away your $what" } else "Flood: the rivers rose, but harmed nothing" }
            9 -> { val h = anyOf(rng, QUARRY, MINE); if (h >= 0 && rng.nextBoolean()) { val n = minOf(pop, 1); pop -= n; building[h] = NONE; "Rockfall: your ${TERRAIN[terrain[h]]} workings collapsed" } else "Rockfall in the mountains, but no one was hurt" }
            10 -> { val h = anyOf(rng, MILL); if (h >= 0 && rng.nextBoolean()) { building[h] = NONE; "Forest fire: a lumber mill burned down" } else { val t2 = minOf(res[TIMBER], 3); res[TIMBER] -= t2; "Forest fire: $t2 timber burned" } }
            11 -> { val g = if (count(MINE) > 0) 8 else 2; res[GOLD] += g; "Gold vein: +$g gold" }
            12 -> raid(rng)
            13 -> { val rv = rivals.filter { it.alive }.maxByOrNull { it.strength }
                if (rv == null) "An envoy came from the far lands, bringing only greetings"
                else if (res[GOLD] >= 4) { res[GOLD] -= 4; "${rv.name.replaceFirstChar { it.uppercase() }} demanded tribute: you paid 4 gold" } else { happiness -= 8; "${rv.name.replaceFirstChar { it.uppercase() }} demanded tribute you couldn't pay: your people despair" } }
            14 -> { if (soldiers < soldierCap()) { soldiers++; "A wandering hero joined your army" } else { happiness += 5; "A wandering hero told tales of your glory (+5 happiness)" } }
            15 -> { val h = (0 until H).filter { revealed[it] && map.hexNbrs[it].any { n -> !revealed[n] } }.let { if (it.isEmpty()) -1 else it[rng.nextInt(it.size)] }; if (h >= 0) reveal(h, 3); "An old map showed you lands beyond the fog" }
            16 -> if (winter) { val f = minOf(res[FOOD], pop / 3 + 2); res[FOOD] -= f; "Harsh frost: $f food spoiled in the cold" } else { happiness -= 3; "A cold snap: your people grumble" }
            17 -> { happiness += 8; "Blessing: good omens lift your people's hearts (+8 happiness)" }
            18 -> { happiness -= 8 - 2 * minOf(count(TEMPLE) + count(COLOSSEUM), 3); "Unrest: your people grow restless" }
            19 -> { val f = 2 + 3 * count(FISHERY) + 2 * count(PORT); res[FOOD] += f; "A shoal of fish: +$f food" }
            20 -> { res[TIMBER] += 3; res[STONE] += 3; "Builders' guild: +3 timber, +3 stone" }
            21 -> { if (count(RANCH) > 0) { val f = minOf(res[FOOD], 2 * count(RANCH)); res[FOOD] -= f; "Herd sickness: $f food lost" } else "Herd sickness among the wild beasts" }
            22 -> { if (count(BARRACKS) > 0 && soldiers + 2 <= soldierCap()) { soldiers += 2; "Volunteers: 2 soldiers joined your barracks" } else { pop += minOf(2, maxOf(0, housing() - pop)); "Volunteers came to help build" } }
            else -> "A quiet season"
        }
        log += text
    }

    private fun raid(rng: Random): String {
        val rv = rivals.filter { it.alive }.maxByOrNull { it.strength } ?: return "Raiders were seen, but no rival is left to send them"
        if (year < 3) return "Rival scouts were seen at the edge of your lands"
        val name = rv.name.replaceFirstChar { it.uppercase() }
        val def = soldiers + 2 * count(TOWER) + roll(rng) + if (sw.active(AegStewardship.CITADEL)) 6 else 0
        val atk = rv.strength / 3 + roll(rng)
        return if (def >= atk) { rv.strength = maxOf(1, rv.strength - 1); "Raid: $name attacked, and your defenders threw them back" }
        else {
            val killed = minOf(pop, 1 + rv.strength / 6); pop -= killed
            val lostS = minOf(soldiers, 1 + soldiers / 3); soldiers -= lostS
            val h = (0 until H).filter { building[it] != NONE && building[it] != PORT }.maxByOrNull { -hexDist(it, rv.capital) }
            val g = res[GOLD] / 3; res[GOLD] -= g
            if (h != null && rng.nextInt(3) > 0) { building[h] = NONE }
            "Raid! $name struck: $killed people killed" + (if (lostS > 0) ", $lostS soldiers fell" else "") + (if (g > 0) ", $g gold taken" else "") + (if (h != null) ", buildings burned" else "")
        }
    }

    // ---------------------------------------------------------------- the end

    val rivalsLeft get() = rivals.count { it.alive }

    private fun checkEnd() {
        if (over) return
        if (pop <= 0) { pop = 0; over = true; won = false; log += "Your last people are gone. Your civilization has fallen"; sw.record(year); return }
        if (rivalsLeft == 0 && sw.people() >= goal) { over = true; won = true; log += "Every rival has fallen, and ${sw.people()} people call your land home. Your civilization endures!"; sw.record(year) }
    }

    // ---------------------------------------------------------------- save / copy

    fun state(): IntArray {
        val o = ArrayList<Int>()
        fun put(a: IntArray) { o += a.size; a.forEach { o += it } }
        o += RADIUS[diff]; o += diff
        put(terrain); put(building); put(revealed.map { if (it) 1 else 0 }.toIntArray()); put(ruins.map { if (it) 1 else 0 }.toIntArray())
        put(rivalLand); put(road.map { if (it) 1 else 0 }.toIntArray()); put(res)
        put(intArrayOf(pop, soldiers, happiness, season, capital, lastHarvest, lastDice[0], lastDice[1], lastCard, if (over) 1 else 0, if (won) 1 else 0))
        put(rivals.flatMap { listOf(it.capital, it.strength, if (it.alive) 1 else 0) }.toIntArray())
        put(deck.toIntArray())
        put(sw.state())
        return o.toIntArray()
    }

    fun load(s: IntArray) {
        var i = 2
        fun get(): IntArray { val n = s[i++]; val a = s.copyOfRange(i, i + n); i += n; return a }
        get().copyInto(terrain); get().copyInto(building)
        get().forEachIndexed { k, b -> revealed[k] = b == 1 }; get().forEachIndexed { k, b -> ruins[k] = b == 1 }
        get().copyInto(rivalLand); get().forEachIndexed { k, b -> road[k] = b == 1 }; get().copyInto(res)
        val m = get()
        pop = m[0]; soldiers = m[1]; happiness = m[2]; season = m[3]; capital = m[4]; lastHarvest = m[5]; lastDice[0] = m[6]; lastDice[1] = m[7]
        lastCard = m[8]; over = m[9] == 1; won = m[10] == 1
        rivals.clear(); get().toList().chunked(3).forEachIndexed { k, (c, st, al) -> rivals += Rival(RIVAL_NAMES[k], c, st, al == 1) }
        deck.clear(); deck += get().toList()
        if (i < s.size) sw.load(get())
    }

    fun copy(): Aegwaspell = fromState(state())
}

/** A simple steward, for the how-to-play demo, the coach and balance tests: feeds, houses and arms its people, then marches. */
object AegwaspellAi {
    private fun value(g: Aegwaspell, h: Int): Double = when (g.terrain[h]) {
        Aegwaspell.FIELDS -> 5.0; Aegwaspell.COAST -> 4.0; Aegwaspell.MEADOW -> 3.5; Aegwaspell.FOREST -> 3.0; Aegwaspell.PLAINS -> 3.0
        Aegwaspell.HILLS -> 2.5; Aegwaspell.MOUNTAIN -> 2.0; else -> 0.5
    } + (if (g.ruins[h]) 4.0 else 0.0)

    fun next(g: Aegwaspell, rng: Random): AAct {
        val A = Aegwaspell
        val conn = g.connected()
        val free = (0 until g.H).filter { conn[it] && g.building[it] == A.NONE && g.rivalLand[it] < 0 }
        fun build(types: List<Int>): AAct? {
            for (t in types) {
                if (!g.can(A.COST[t])) continue
                val h = free.filter { t in g.buildable(it, conn) }.maxByOrNull { if (t == A.FARM && g.terrain[it] == A.FIELDS) 2 else 0 } ?: continue
                return AAct.Build(h, t)
            }
            return null
        }
        val foodNet = g.yearlyFood() - g.eats()
        val workers = g.jobs()
        g.sw.aiOrder(foodNet, g.foodCap())?.let { o -> return when (o[0]) { 0 -> AAct.Choose(o[1]); 1 -> AAct.Decree(o[1], o[2]); else -> AAct.Fallow(o[1]) } }
        // 1. food first
        if (foodNet < 2 || g.res[A.FOOD] < g.eats()) build(listOf(A.FARM, A.FISHERY, A.RANCH))?.let { return it }
        // 2. homes, when people are near the roof and there are jobs to fill
        if (g.pop >= g.housing() - 2) {
            build(listOf(A.VILLAGE))?.let { return it }
            (0 until g.H).firstOrNull { g.building[it] == A.VILLAGE && g.can(A.COST[A.TOWN]) }?.let { return AAct.Upgrade(it) }
        }
        // 3. timber and stone
        if (g.count(A.MILL) < 1 + g.pop / 25) build(listOf(A.MILL))?.let { return it }
        if (g.count(A.QUARRY) < 1 + g.pop / 30) build(listOf(A.QUARRY))?.let { return it }
        // 4. keep people happy, and store food
        if (g.happiness < 45 && g.count(A.TEMPLE) < 3) build(listOf(A.TEMPLE, A.COLOSSEUM))?.let { return it }
        if (g.res[A.FOOD] >= g.foodCap() - 8) build(listOf(A.GRANARY))?.let { return it }
        // 5. arms: barracks, mines for ore, soldiers
        val weakest = g.rivals.filter { it.alive }.minOfOrNull { it.strength } ?: 0
        if (g.pop >= 14 && g.soldierCap() < weakest + 4 && g.count(A.BARRACKS) < 2 + g.pop / 15) build(listOf(A.BARRACKS))?.let { return it }
        if (g.count(A.BARRACKS) > 0 && g.count(A.MINE) < 1 + g.pop / 20) build(listOf(A.MINE))?.let { return it }
        if (g.count(A.BARRACKS) > 0 && g.count(A.MARKET) < 1 + g.pop / 25) build(listOf(A.MARKET))?.let { return it }
        if (g.count(A.TOWER) < g.pop / 30) build(listOf(A.TOWER))?.let { return it }
        val room = g.soldierCap() - g.soldiers
        if (room > 0 && g.pop > 12 && foodNet > 0) {
            val n = (1..room).lastOrNull { k -> (0..4).all { g.res[it] >= A.SOLDIER_COST[it] * k } && g.pop - k >= 10 } ?: 0
            if (n > 0) return AAct.Train(n)
        }
        // 6. march when strong enough
        for (r in g.attackable(conn)) if (g.soldiers >= g.rivals[r].strength + 2) return AAct.Attack(r)
        // 6b. a wonder, once there's a town and stone to spare
        if (g.count(A.TOWN) > 0 && g.res[A.STONE] >= 12 && g.res[A.GOLD] >= 8) build(AegStewardship.WONDERS.toList())?.let { return it }
        // 7. more food and homes ahead of need
        if (foodNet < 6 && workers < g.pop + 2) build(listOf(A.FARM, A.FISHERY, A.RANCH))?.let { return it }
        if (g.housing() - g.pop < 4) build(listOf(A.VILLAGE))?.let { return it }
        // 8. roads: toward rich land, ruins and the nearest rival
        if (g.res[A.TIMBER] >= 2) {
            val target = g.rivals.filter { it.alive }.minByOrNull { r -> (0 until g.H).filter { conn[it] }.minOf { g.hexDist(it, r.capital) } }?.capital
            var best: AAct.Road? = null; var bv = -99.0
            for (a in 0 until g.H) if (conn[a]) for (b in g.roadTargets(a, conn)) {
                if (!g.can(g.roadCost(b))) continue
                val v = value(g, b) - (if (target != null) g.hexDist(b, target) * (if (g.soldiers >= 6) 0.8 else 0.25) else 0.0) + rng.nextDouble()
                if (v > bv) { bv = v; best = AAct.Road(a, b) }
            }
            if (best != null && (free.size < 3 || g.res[A.TIMBER] >= 6)) return best
        }
        if (g.can(A.SCOUT_COST) && g.res[A.GOLD] >= 8 && rng.nextInt(4) == 0) (0 until g.H).filter { conn[it] && g.scoutable(it) }.randomOrNull(rng)?.let { return AAct.Scout(it) }
        return AAct.End
    }
}
