package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

/** One thing the player does in AegConquest. */
sealed class CAct {
    data class Build(val h: Int, val type: Int) : CAct()
    /** A road from the connected tile [a] to its neighbour [b]. */
    data class Road(val a: Int, val b: Int) : CAct()
    data class Upgrade(val h: Int) : CAct()
    data class Train(val n: Int) : CAct()
    data class Scout(val h: Int) : CAct()
    /** The soldiers on your tile [from] attack the touching rival tile [to]. */
    data class Attack(val from: Int, val to: Int) : CAct()
    /** Put one of your idle people to work at the building on [h] ([d] = 1), or send one home ([d] = -1). */
    data class Assign(val h: Int, val d: Int) : CAct()
    /** Station a soldier from your reserve on [h] ([d] = 1), or call one back ([d] = -1). */
    data class Guard(val h: Int, val d: Int) : CAct()
    /** At your port: give [TRADE_RATE] of [give] for 1 of [get]. */
    data class Trade(val give: Int, val get: Int) : CAct()
    /** Tear down the building on [h]: half its cost comes back, and the tile is rubble until next season. */
    data class Demolish(val h: Int) : CAct()
    /** Rest the farm on [h] (or work it again): resting soil recovers. */
    data class Fallow(val h: Int) : CAct()
    /** Set decree [kind] (rations, taxes, work hours) to [level]. */
    data class Decree(val kind: Int, val level: Int) : CAct()
    /** Answer the waiting event card: [opt] 0 or 1. */
    data class Choose(val opt: Int) : CAct()
    object End : CAct()
}

/** A rival civilization: its capital and lands, and a strength that grows every season. */
class CRival(val name: String, var capital: Int, var strength: Int, var alive: Boolean = true)

/**
 * AegConquest: a single-player civilization game. You land at a port at the foot of a large, fogged world and grow a
 * people. Each season a harvest die is rolled and an event card is drawn. Every tile holds one building, and you can
 * only build on tiles your roads reach. Feed, house and please your people; train soldiers and take the rival
 * capitals. Win with a great population once every rival has fallen; lose if your last person dies.
 */
class AegConquest(override val map: RealmGrid, override val diff: Int) : AegLand {
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
        /** The port's rate: this many of one good for one of another. */
        const val TRADE_RATE = 4
        const val SOLDIERS_PER_BARRACKS = 8

        val RADIUS = intArrayOf(5, 5, 6, 6, 7, 7)
        val RIVALS = intArrayOf(2, 2, 3, 3, 4, 4)
        val POP_GOAL = intArrayOf(35, 45, 55, 65, 75, 90)
        val RIVAL_START = intArrayOf(5, 6, 7, 8, 9, 10)
        /** Seasons between each point of rival growth. */
        val RIVAL_GROWS = intArrayOf(6, 5, 5, 4, 4, 3)
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

        fun newGame(d: Difficulty, rng: Random): AegConquest = AegConquest(grid(RADIUS[d.ordinal]), d.ordinal).also { it.deal(rng) }

        fun fromState(s: IntArray): AegConquest {
            val g = AegConquest(grid(s[0]), s[1]); g.load(s); return g
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
    /** People at work in each building. A building makes nothing until it has its full crew. */
    val workers = IntArray(H)
    /** Your soldiers stationed on each tile. */
    val guard = IntArray(H)
    /** Rival soldiers on each of their tiles. */
    val rivalGuard = IntArray(H)
    override val res = IntArray(5)
    override var pop = 6
    /** Soldiers in reserve, not yet stationed on a tile. */
    var soldiers = 0
    override var happiness = 60
    override var season = 0
    override var capital = -1
    var lastHarvest = 0
    val lastDice = intArrayOf(0, 0)
    val rivals = ArrayList<CRival>()
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
            rivals += CRival(RIVAL_NAMES[i], spot, RIVAL_START[diff] + i * 2)
            terrain[spot] = PLAINS
            for (h in 0 until H) if (terrain[h] != SEA && hexDist(h, spot) <= 1 && rivalLand[h] < 0) { rivalLand[h] = i; rivalGuard[h] = 1 }
            rivalGuard[spot] = RIVAL_START[diff] + i * 2
        }
        // ruins hold treasure
        (0 until H).filter { terrain[it] != SEA && rivalLand[it] < 0 && hexDist(it, capital) >= 3 }.shuffled(rng).take(H / 16).forEach { ruins[it] = true }
        for (h in 0 until H) if (hexDist(h, capital) <= 2) revealed[h] = true
        res[FOOD] = 20; res[TIMBER] = 10; res[STONE] = 4; res[ORE] = 0; res[GOLD] = 5
        guard[capital] = 1
        newDeck(rng)
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

    override fun armed() = allSoldiers()
    override fun loseSoldiers(n: Int) {
        var k = n; val t2 = minOf(k, soldiers); soldiers -= t2; k -= t2
        for (h in 0 until H) { if (k == 0) break; val x = minOf(k, guard[h]); guard[h] -= x; k -= x }
    }
    override fun prideRival() { rivals.indices.filter { rivals[it].alive }.maxByOrNull { rivalStrength(it) }?.let { rivalGuard[rivals[it].capital] += 2 } }
    override fun sendHome(h: Int) { workers[h] = 0 }

    /** Tiles the roads reach from the port. */
    override fun connected(): BooleanArray {
        val c = BooleanArray(H); val q = ArrayDeque<Int>(); q += capital; c[capital] = true
        if (rivalLand[capital] >= 0) return c.also { it[capital] = false }
        while (q.isNotEmpty()) { val h = q.removeFirst(); for (n in map.hexNbrs[h]) if (!c[n] && road[side(h, n)] && rivalLand[n] < 0) { c[n] = true; q += n } }
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

    /** Buildings with their full crew, on land your roads still reach: only these produce. */
    override fun staffed(): BooleanArray {
        val conn = connected()
        return BooleanArray(H) { h -> conn[h] && workers[h] >= WORKERS[building[h]] }
    }

    /** People not at work in any building (soldiers are counted apart). */
    fun idle() = pop - workers.sum()
 /** Ships call while your port stands. */
    fun canTrade() = capital >= 0 && building[capital] == PORT && rivalLand[capital] < 0

    fun allSoldiers() = soldiers + guard.sum()
    fun rivalStrength(r: Int) = (0 until H).sumOf { if (rivalLand[it] == r) rivalGuard[it] else 0 }

    /** When people die, the last jobs empty first: workers leave the least vital buildings. */
    private fun trimWorkers() {
        // a burned or lost building sends its crew home
        for (h in 0 until H) if (workers[h] > WORKERS[building[h]] || rivalLand[h] >= 0) workers[h] = if (rivalLand[h] >= 0) 0 else WORKERS[building[h]]
        var over = workers.sum() - pop
        if (over <= 0) return
        val order = (0 until H).filter { workers[it] > 0 }.sortedByDescending { when (building[it]) { FARM, RANCH, FISHERY -> 0; MILL, QUARRY -> 1; else -> 2 } }
        for (h in order) { val k = minOf(over, workers[h]); workers[h] -= k; over -= k; if (over == 0) break }
    }

    /** Fills [h]'s crew from idle people, as far as they go. */
    private fun staff(h: Int) { workers[h] += minOf(idle(), WORKERS[building[h]] - workers[h]).coerceAtLeast(0) }

    /** A tile's defence against an attack: its soldiers, plus a watchtower on it and your port's walls. */
    fun defence(h: Int) = guard[h] + when (building[h]) { TOWER -> 3; PORT -> 2; TOWN -> 2; VILLAGE -> 1; else -> 0 } + (if (citadel) (if (h == capital) 4 else 1) else 0)
    /** Whether the Grand Citadel stands and is manned: refreshed after every order. */
    var citadel = false
    /** The port trades at the agreed four-to-one rate. */
    fun tradeRate() = TRADE_RATE
    fun rivalDefence(h: Int) = rivalGuard[h] + (if (rivals.getOrNull(rivalLand[h])?.capital == h) 2 else 0)

    /** What each staffed building makes this season, before the harvest die. */
    fun production(harvest: Float = 1f, cold: Boolean = winter): IntArray {
        val out = IntArray(5)
        val st = staffed()
        val wa = sw.activeWonders(st)
        for (h in 0 until H) {
            if (WORKERS[building[h]] > 0 && !st[h] || sw.resting(h)) continue
            when (building[h]) {
                PORT -> { out[FOOD] += 3; out[TIMBER] += 1; out[GOLD] += 1 }
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

    fun eats() = sw.mouths() + allSoldiers()
    /** Food the land makes in an average season of the year, winter included. */
    fun yearlyFood() = (3 * production(1f, false)[FOOD] + production(1f, true)[FOOD]) / 4

    // ---------------------------------------------------------------- actions

    fun buildable(h: Int, conn: BooleanArray = connected()): List<Int> {
        if (!conn[h] || building[h] != NONE || rivalLand[h] >= 0 || sw.rubble[h] > 0) return emptyList()
        return (VILLAGE..COLOSSEUM).filter { it != TOWN && terrain[h] in ALLOWED[it] } + sw.wondersFor(h)
    }

    fun roadTargets(a: Int, conn: BooleanArray = connected()): List<Int> =
        if (!conn[a]) emptyList() else map.hexNbrs[a].filter { isLand(it) && revealed[it] && !conn[it] && rivalLand[it] < 0 && !road[side(a, it)] }

    /** Your tile touching rival tile [to] with the most soldiers on it (-1: none touches it). */
    fun attackFrom(to: Int, conn: BooleanArray = connected()): Int =
        if (rivalLand[to] < 0) -1 else map.hexNbrs[to].filter { conn[it] && guard[it] > 0 }.maxByOrNull { guard[it] } ?: -1

    fun scoutable(h: Int): Boolean = revealed[h] && map.hexNbrs[h].any { !revealed[it] }

    fun legal(a: CAct): Boolean {
        if (over) return false
        // a waiting card must be answered first (decrees may still change)
        if (sw.choice >= 0 && a !is CAct.Choose && a !is CAct.Decree) return false
        val conn = connected()
        return when (a) {
            is CAct.Demolish -> sw.canDemolish(a.h, conn)
            is CAct.Fallow -> conn[a.h] && building[a.h] == FARM
            is CAct.Decree -> a.kind in 0..2 && a.level in 0..(if (a.kind == AegStewardship.HOURS) 1 else 2) && sw.decree[a.kind] != a.level
            is CAct.Choose -> sw.choice >= 0 && a.opt in 0..1
            is CAct.Build -> a.type in buildable(a.h, conn) && can(COST[a.type])
            is CAct.Road -> a.b in roadTargets(a.a, conn) && can(roadCost(a.b))
            is CAct.Upgrade -> building[a.h] == VILLAGE && can(COST[TOWN])
            is CAct.Train -> a.n > 0 && allSoldiers() + a.n <= soldierCap() && (0..4).all { res[it] >= SOLDIER_COST[it] * a.n } && idle() >= a.n && pop - a.n >= 1
            is CAct.Scout -> scoutable(a.h) && can(SCOUT_COST)
            is CAct.Attack -> conn[a.from] && guard[a.from] > 0 && rivalLand[a.to] >= 0 && a.to in map.hexNbrs[a.from]
            is CAct.Assign -> conn[a.h] && WORKERS[building[a.h]] > 0 && if (a.d > 0) workers[a.h] < WORKERS[building[a.h]] && idle() > 0 && !sw.fallow[a.h] else workers[a.h] > 0
            is CAct.Guard -> conn[a.h] && if (a.d > 0) soldiers > 0 else guard[a.h] > 0
            is CAct.Trade -> canTrade() && a.give in 0..4 && a.get in 0..4 && a.give != a.get && res[a.give] >= tradeRate()
            CAct.End -> true
        }
    }

    fun apply(a: CAct, rng: Random) {
        trimWorkers()
        if (!legal(a)) return
        when (a) {
            is CAct.Build -> { pay(COST[a.type]); building[a.h] = a.type; sw.started(a.h); staff(a.h); log += "You built a ${NAME[a.type].lowercase()} on the ${TERRAIN[terrain[a.h]]}" }
            is CAct.Road -> {
                pay(roadCost(a.b)); road[side(a.a, a.b)] = true
                reveal(a.b, if (terrain[a.b] == HILLS || terrain[a.b] == MOUNTAIN) 2 else 1)
                log += "Your road reached the ${TERRAIN[terrain[a.b]]}"
                if (ruins[a.b]) treasure(a.b, rng)
            }
            is CAct.Upgrade -> { pay(COST[TOWN]); building[a.h] = TOWN; log += "Your village grew into a town" }
            is CAct.Train -> { for (r in 0..4) res[r] -= SOLDIER_COST[r] * a.n; pop -= a.n; soldiers += a.n; log += "${a.n} of your people took up arms" }
            is CAct.Scout -> { pay(SCOUT_COST); reveal(a.h, 2); log += "Your scouts mapped the land beyond the ${TERRAIN[terrain[a.h]]}" }
            is CAct.Attack -> attack(a.from, a.to, rng)
            is CAct.Assign -> workers[a.h] += a.d
            is CAct.Guard -> { guard[a.h] += a.d; soldiers -= a.d }
            is CAct.Trade -> { val k = tradeRate(); res[a.give] -= k; res[a.get]++; log += "Your port traded $k ${RES[a.give]} for 1 ${RES[a.get]}" }
            is CAct.Demolish -> sw.demolish(a.h, COST[building[a.h]])
            is CAct.Fallow -> sw.setFallow(a.h)
            is CAct.Decree -> sw.setDecree(a.kind, a.level)
            is CAct.Choose -> sw.choose(a.opt, rng)
            CAct.End -> endSeason(rng)
        }
        checkEnd()
        citadel = sw.active(AegStewardship.CITADEL)
    }

    private fun reveal(c: Int, r: Int) { for (h in 0 until H) if (hexDist(h, c) <= r) revealed[h] = true }

    private fun treasure(h: Int, rng: Random) {
        ruins[h] = false
        when (rng.nextInt(4)) {
            0 -> { res[GOLD] += 8; log += "Ruins! Your people found 8 gold" }
            1 -> { pop += 3; log += "Ruins! 3 survivors joined your people (assign them work)" }
            2 -> { res[STONE] += 5; res[TIMBER] += 3; log += "Ruins! You salvaged 5 stone and 3 timber" }
            else -> { reveal(h, 3); log += "Ruins! An old map showed the land around" }
        }
    }

    private fun roll(rng: Random): Int { lastDice[0] = rng.nextInt(1, 7); lastDice[1] = rng.nextInt(1, 7); return lastDice[0] + lastDice[1] }

    /** Your soldiers on [from] attack the rival tile [to]: soldiers plus one die against soldiers plus one die. */
    private fun attack(from: Int, to: Int, rng: Random) {
        val r = rivalLand[to]; val rv = rivals[r]
        val n = guard[from]
        val dA = rng.nextInt(1, 7); val dD = rng.nextInt(1, 7); lastDice[0] = dA; lastDice[1] = dD
        val a = n + dA; val d = rivalDefence(to) + dD
        val name = rv.name
        if (a > d) {
            val lost = minOf(n - 1, (rivalGuard[to] + 1) / 2)
            val left = n - lost
            guard[from] = 0
            val wasCapital = rv.capital == to
            rivalLand[to] = -1; rivalGuard[to] = 0; revealed[to] = true
            road[side(from, to)] = true
            guard[to] = left
            log += "Your $n soldiers (rolled $dA) beat ${name}'s ${d - dD} (rolled $dD) and took the ${TERRAIN[terrain[to]]}" + if (lost > 0) ": $lost fell" else ""
            if (ruins[to]) treasure(to, rng)
            if (wasCapital) fallCapital(r, by = -1)
        } else {
            val lost = (n + 1) / 2; guard[from] -= lost
            val hurt = minOf(rivalGuard[to], n / 3); rivalGuard[to] -= hurt
            log += "Your $n soldiers (rolled $dA) were thrown back by ${name}'s ${d - dD} (rolled $dD): $lost fell"
        }
    }

    /** A rival's capital falls to [by] (-1: you). Its people join the victor and the rest of its land is freed. */
    private fun fallCapital(r: Int, by: Int) {
        val rv = rivals[r]
        rv.alive = false
        val spoils = 10 + rivalStrength(r) / 2
        for (h in 0 until H) if (rivalLand[h] == r) { rivalLand[h] = -1; rivalGuard[h] = 0 }
        if (by < 0) {
            res[GOLD] += spoils
            val joined = 4 + diff
            pop += joined
            log += "The capital of ${rv.name} has fallen! $joined of its people join you (idle, ready for work), with $spoils gold"
        } else log += "${rv.name.replaceFirstChar { it.uppercase() }} has fallen to ${rivals[by].name}!"
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
        trimWorkers()
        rivalsAct(rng)
        trimWorkers()
        sw.endOfSeason(staffed())
        season++
        if (season % 4 == 0) sw.record(year - 1)
    }

    /** Whose a tile is: 0 yours (on your roads), 1 + r a rival's, -1 nobody's. */
    private fun ownerOf(h: Int, conn: BooleanArray) = when { rivalLand[h] >= 0 -> 1 + rivalLand[h]; conn[h] -> 0; else -> -1 }

    /**
     * The rival civilizations' season: they raise soldiers and post them on their borders. From Hard up they also
     * spread into free land and attack whatever land of yours or of another rival their soldiers outnumber.
     */
    private fun rivalsAct(rng: Random) {
        for ((r, rv) in rivals.withIndex()) {
            if (!rv.alive) continue
            val conn = connected()
            val mine = (0 until H).filter { rivalLand[it] == r }
            if (mine.isEmpty()) { rv.alive = false; continue }
            // new soldiers, on the borders that face an enemy, weakest first
            if (season % RIVAL_GROWS[diff] == 0 && rivalStrength(r) < mine.size * 4) repeat(1 + mine.size / 5) {
                val border = mine.filter { h -> map.hexNbrs[h].any { n -> ownerOf(n, conn).let { it >= 0 && it != 1 + r } } }
                val at = border.minByOrNull { rivalGuard[it] } ?: rv.capital
                rivalGuard[at]++
            }
            if (diff < 2) continue
            // spread into free land beside them, every third season
            if (season % 3 == r % 3) {
                val step = mine.filter { rivalGuard[it] >= 2 }.flatMap { h -> map.hexNbrs[h].filter { n -> isLand(n) && ownerOf(n, conn) == -1 && building[n] == NONE }.map { h to it } }
                    .maxByOrNull { (_, n) -> map.hexNbrs[n].count { rivalLand[it] == r } + rng.nextDouble() }
                if (step != null) { rivalGuard[step.first]--; rivalLand[step.second] = r; rivalGuard[step.second] = 1 }
            }
            // attack the weakest enemy tile they outnumber
            var best: Pair<Int, Int>? = null; var bv = 0
            for (h in mine) for (n in map.hexNbrs[h]) {
                val o = ownerOf(n, conn); if (o < 0 || o == 1 + r) continue
                if (o == 0 && season / 4 < (if (diff >= 4) 3 else 4)) continue   // a few years' grace before they turn on you
                if (o == 0 && season % 2 == r % 2) continue                       // and they march on you only every other season
                val def = if (o == 0) defence(n) else rivalDefence(n)
                val margin = rivalGuard[h] - 1 - def
                if (margin >= (if (o == 0) 3 else 2) && margin > bv) { bv = margin; best = h to n }
            }
            best?.let { (h, n) -> rivalAttack(r, h, n, rng) }
        }
    }

    private fun rivalAttack(r: Int, from: Int, to: Int, rng: Random) {
        val rv = rivals[r]; val n = rivalGuard[from] - 1
        val conn = connected()
        val mineToo = conn[to] && rivalLand[to] < 0
        if (!mineToo && rivalLand[to] < 0) return
        val def = (if (mineToo) defence(to) else rivalDefence(to)) + rng.nextInt(1, 7)
        val atk = n + rng.nextInt(1, 7)
        val who = rv.name.replaceFirstChar { it.uppercase() }
        if (atk > def) {
            rivalGuard[from] -= n
            if (mineToo) {
                val lost = guard[to]; guard[to] = 0
                val b = building[to]
                val dead = (workers[to] + 1) / 2; pop -= dead; workers[to] = 0
                if (to == capital) { log += "$who stormed your port! Your capital has fallen"; pop = 0 }
                else log += "$who took your ${TERRAIN[terrain[to]]}" + (if (b != NONE) " and burned its ${NAME[b].lowercase()}" else "") + (if (lost > 0) ": $lost soldiers fell" else "") + if (dead > 0) ", $dead workers killed" else ""
                building[to] = NONE
            } else {
                val o = rivalLand[to]
                log += "$who took land from ${rivals[o].name}"
                if (rivals[o].capital == to) { rivalLand[to] = r; rivalGuard[to] = maxOf(1, n - rivalGuard[to]); fallCapital(o, r); return }
            }
            rivalLand[to] = r; rivalGuard[to] = maxOf(1, n - (def - 3).coerceAtLeast(0) / 2)
        } else {
            rivalGuard[from] -= (n + 1) / 2
            if (mineToo) { guard[to] = maxOf(0, guard[to] - n / 3); log += "$who attacked your ${TERRAIN[terrain[to]]}, and your defenders held" }
            else rivalGuard[to] = maxOf(1, rivalGuard[to] - n / 3)
        }
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
            2 -> { val n = minOf(maxOf(0, housing() - pop), 2 + diff / 2 + 2); pop += n; if (n > 0) "Migrants: $n newcomers settled in your homes, ready for work" else "Migrants passed by: you had no homes for them" }
            3 -> { val n = if (count(TEMPLE) > 0) pop / 40 else maxOf(1, pop / 15); pop -= n; "Plague: $n of your people died" + if (count(TEMPLE) > 0) " (your temples eased it)" else "" }
            4 -> if (allSoldiers() >= 3) "Bandits: your soldiers drove them off" else { val g = res[GOLD] / 2; val f = res[FOOD] / 4; res[GOLD] -= g; res[FOOD] -= f; "Bandits stole $g gold and $f food. Soldiers would stop them" }
            5 -> { val g = 3 + 3 * count(MARKET); res[GOLD] += g; "Merchant caravan: you traded for $g gold" }
            6 -> { happiness += 10; val f = minOf(res[FOOD], 4); res[FOOD] -= f; "Festival: your people feast (+10 happiness, $f food)" }
            7 -> { val h = anyOf(rng, RANCH); if (h >= 0) { val f = minOf(res[FOOD], 4); res[FOOD] -= f; "Wolves raided a ranch: $f food lost" } else "Wolves howl in the hills, but find nothing to take" }
            8 -> { val h = (0 until H).filter { building[it] in setOf(FARM, FISHERY) && map.hexNbrs[it].any { n -> terrain[n] == SEA || terrain[n] == SWAMP } }.let { if (it.isEmpty()) -1 else it[rng.nextInt(it.size)] }
                if (h >= 0) { val what = NAME[building[h]].lowercase(); building[h] = NONE; "Flood: the waters swept away your $what" } else "Flood: the rivers rose, but harmed nothing" }
            9 -> { val h = anyOf(rng, QUARRY, MINE); if (h >= 0 && rng.nextBoolean()) { val n = minOf(pop, 1); pop -= n; building[h] = NONE; "Rockfall: your ${TERRAIN[terrain[h]]} workings collapsed" } else "Rockfall in the mountains, but no one was hurt" }
            10 -> { val h = anyOf(rng, MILL); if (h >= 0 && rng.nextBoolean()) { building[h] = NONE; "Forest fire: a lumber mill burned down" } else { val t2 = minOf(res[TIMBER], 3); res[TIMBER] -= t2; "Forest fire: $t2 timber burned" } }
            11 -> { val g = if (count(MINE) > 0) 8 else 2; res[GOLD] += g; "Gold vein: +$g gold" }
            12 -> raid(rng)
            13 -> { val rv = rivals.filter { it.alive }.maxByOrNull { rivalStrength(rivals.indexOf(it)) }
                if (rv == null) "An envoy came from the far lands, bringing only greetings"
                else if (res[GOLD] >= 4) { res[GOLD] -= 4; "${rv.name.replaceFirstChar { it.uppercase() }} demanded tribute: you paid 4 gold" } else { happiness -= 8; "${rv.name.replaceFirstChar { it.uppercase() }} demanded tribute you couldn't pay: your people despair" } }
            14 -> { if (allSoldiers() < soldierCap()) { soldiers++; "A wandering hero joined your army" } else { happiness += 5; "A wandering hero told tales of your glory (+5 happiness)" } }
            15 -> { val h = (0 until H).filter { revealed[it] && map.hexNbrs[it].any { n -> !revealed[n] } }.let { if (it.isEmpty()) -1 else it[rng.nextInt(it.size)] }; if (h >= 0) reveal(h, 3); "An old map showed you lands beyond the fog" }
            16 -> if (winter) { val f = minOf(res[FOOD], pop / 3 + 2); res[FOOD] -= f; "Harsh frost: $f food spoiled in the cold" } else { happiness -= 3; "A cold snap: your people grumble" }
            17 -> { happiness += 8; "Blessing: good omens lift your people's hearts (+8 happiness)" }
            18 -> { happiness -= 8 - 2 * minOf(count(TEMPLE) + count(COLOSSEUM), 3); "Unrest: your people grow restless" }
            19 -> { val f = 2 + 3 * count(FISHERY) + 2 * count(PORT); res[FOOD] += f; "A shoal of fish: +$f food" }
            20 -> { res[TIMBER] += 3; res[STONE] += 3; "Builders' guild: +3 timber, +3 stone" }
            21 -> { if (count(RANCH) > 0) { val f = minOf(res[FOOD], 2 * count(RANCH)); res[FOOD] -= f; "Herd sickness: $f food lost" } else "Herd sickness among the wild beasts" }
            22 -> { if (count(BARRACKS) > 0 && allSoldiers() + 2 <= soldierCap()) { soldiers += 2; "Volunteers: 2 soldiers joined your barracks" } else { pop += minOf(2, maxOf(0, housing() - pop)); "Volunteers came to help build" } }
            else -> "A quiet season"
        }
        log += text
    }

    private fun raid(rng: Random): String {
        val live = rivals.indices.filter { rivals[it].alive }
        val r = live.maxByOrNull { rivalStrength(it) } ?: return "Raiders were seen, but no rival is left to send them"
        if (year < 3) return "Rival scouts were seen at the edge of your lands"
        val rv = rivals[r]
        val name = rv.name.replaceFirstChar { it.uppercase() }
        val conn = connected()
        val h = (0 until H).filter { conn[it] && building[it] != NONE }.minByOrNull { hexDist(it, rv.capital) } ?: return "Raid: $name found nothing worth taking"
        val def = defence(h) + rng.nextInt(1, 7)
        val atk = rivalStrength(r) / 4 + rng.nextInt(1, 7)
        return if (def >= atk) "Raid: $name struck your ${TERRAIN[terrain[h]]}, and its defenders threw them back"
        else {
            val dead = minOf(workers[h], 1 + rivalStrength(r) / 8); workers[h] -= dead; pop -= dead
            val lost = minOf(guard[h], 1 + guard[h] / 3); guard[h] -= lost
            val g = res[GOLD] / 3; res[GOLD] -= g
            val b = building[h]
            if (h != capital && rng.nextInt(3) > 0) building[h] = NONE
            "Raid! $name struck your ${TERRAIN[terrain[h]]}" + (if (building[h] == NONE && b != NONE) " and burned its ${NAME[b].lowercase()}" else "") +
                (if (dead > 0) ", $dead killed" else "") + (if (lost > 0) ", $lost soldiers fell" else "") + if (g > 0) ", $g gold taken" else ""
        }
    }

    // ---------------------------------------------------------------- the end

    val rivalsLeft get() = rivals.count { it.alive }

    private fun checkEnd() {
        if (over) return
        trimWorkers()
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
        put(workers); put(guard); put(rivalGuard)
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
        rivals.clear(); get().toList().chunked(3).forEachIndexed { k, (c, st, al) -> rivals += CRival(RIVAL_NAMES[k], c, st, al == 1) }
        deck.clear(); deck += get().toList()
        if (i < s.size) { get().copyInto(workers); get().copyInto(guard); get().copyInto(rivalGuard) }
        else {
            // a save from before workers and garrisons: crew the buildings, food first, and post the rivals' strength at home
            var free = pop
            for (h in (0 until H).sortedBy { when (building[it]) { FARM, RANCH, FISHERY -> 0; MILL, QUARRY -> 1; else -> 2 } }) {
                val w = minOf(free, WORKERS[building[h]]); workers[h] = w; free -= w
            }
            for ((r, rv) in rivals.withIndex()) if (rv.alive) for (h in 0 until H) if (rivalLand[h] == r) rivalGuard[h] = if (h == rv.capital) rv.strength else 1
        }
        if (i < s.size) sw.load(get())
    }

    fun copy(): AegConquest = fromState(state())
}

/** A simple steward, for the how-to-play demo, the coach and balance tests: feeds, houses and arms its people, then marches. */
object AegConquestAi {
    private fun value(g: AegConquest, h: Int): Double = when (g.terrain[h]) {
        AegConquest.FIELDS -> 5.0; AegConquest.COAST -> 4.0; AegConquest.MEADOW -> 3.5; AegConquest.FOREST -> 3.0; AegConquest.PLAINS -> 3.0
        AegConquest.HILLS -> 2.5; AegConquest.MOUNTAIN -> 2.0; else -> 0.5
    } + (if (g.ruins[h]) 4.0 else 0.0)

    fun next(g: AegConquest, rng: Random): CAct {
        val A = AegConquest
        val conn = g.connected()
        val free = (0 until g.H).filter { conn[it] && g.building[it] == A.NONE && g.rivalLand[it] < 0 }
        fun build(types: List<Int>): CAct? {
            for (t in types) {
                if (!g.can(A.COST[t])) continue
                val h = free.filter { t in g.buildable(it, conn) }.maxByOrNull { if (t == A.FARM && g.terrain[it] == A.FIELDS) 2 else 0 } ?: continue
                return CAct.Build(h, t)
            }
            return null
        }
        val foodNet = g.yearlyFood() - g.eats()
        val workers = g.jobs()
        g.sw.aiOrder(foodNet, g.foodCap())?.let { o -> return when (o[0]) { 0 -> CAct.Choose(o[1]); 1 -> CAct.Decree(o[1], o[2]); else -> CAct.Fallow(o[1]) } }
        // 0. idle people go to work, food first
        if (g.idle() > 0) (0 until g.H).filter { conn[it] && g.workers[it] < A.WORKERS[g.building[it]] }
            .minByOrNull { when (g.building[it]) { A.FARM, A.RANCH, A.FISHERY -> 0; A.MILL, A.QUARRY -> 1; else -> 2 } }?.let { return CAct.Assign(it, 1) }
        // 0b. the port: turn a large surplus into the good it has least of
        if (g.canTrade()) {
            val rich = (0..4).maxByOrNull { g.res[it] - if (it == A.FOOD) g.eats() * 2 else 0 }!!
            val poor = (1..4).minByOrNull { g.res[it] }!!
            val spare = g.res[rich] - (if (rich == A.FOOD) g.eats() * 2 else 8)
            if (rich != poor && spare >= A.TRADE_RATE && g.res[poor] < 6) return CAct.Trade(rich, poor)
        }
        // 1. food first, and a lumber mill early so timber never runs dry
        if (foodNet < 2 || g.res[A.FOOD] < g.eats()) build(listOf(A.FARM, A.FISHERY, A.RANCH))?.let { return it }
        if (g.count(A.MILL) == 0) build(listOf(A.MILL))?.let { return it }
        // 2. homes, when people are near the roof and there are jobs to fill
        if (g.pop >= g.housing() - 2) {
            build(listOf(A.VILLAGE))?.let { return it }
            (0 until g.H).firstOrNull { g.building[it] == A.VILLAGE && g.can(A.COST[A.TOWN]) }?.let { return CAct.Upgrade(it) }
        }
        // 3. timber and stone
        if (g.count(A.MILL) < 1 + g.pop / 25) build(listOf(A.MILL))?.let { return it }
        if (g.count(A.QUARRY) < 1 + g.pop / 30) build(listOf(A.QUARRY))?.let { return it }
        // 4. keep people happy, and store food
        if (g.happiness < 45 && g.count(A.TEMPLE) < 3) build(listOf(A.TEMPLE, A.COLOSSEUM))?.let { return it }
        if (g.res[A.FOOD] >= g.foodCap() - 8) build(listOf(A.GRANARY))?.let { return it }
        // 5. arms: barracks, mines for ore, soldiers
        val weakest = g.rivals.indices.filter { g.rivals[it].alive }.minOfOrNull { g.rivalStrength(it) } ?: 0
        val strongest = g.rivals.indices.filter { g.rivals[it].alive }.maxOfOrNull { g.rivalStrength(it) } ?: 0
        if (g.pop >= 14 && g.soldierCap() < strongest + 6 && g.count(A.BARRACKS) < 2 + g.pop / 10) build(listOf(A.BARRACKS))?.let { return it }
        if (g.count(A.BARRACKS) > 0 && g.count(A.MINE) < 1 + g.pop / 20) build(listOf(A.MINE))?.let { return it }
        if (g.count(A.BARRACKS) > 0 && g.count(A.MARKET) < 1 + g.pop / 25) build(listOf(A.MARKET))?.let { return it }
        if (g.count(A.TOWER) < g.pop / 30) build(listOf(A.TOWER))?.let { return it }
        val room = g.soldierCap() - g.allSoldiers()
        if (room > 0 && g.pop > 12 && foodNet > 0) {
            val n = (1..room).lastOrNull { k -> (0..4).all { g.res[it] >= A.SOLDIER_COST[it] * k } && g.idle() >= k && g.pop - k >= 10 } ?: 0
            if (n > 0) return CAct.Train(n)
        }
        // 6. soldiers: shore up any border tile a rival could break, gather beside the weakest rival tile, then strike
        val border = (0 until g.H).filter { conn[it] && g.rivalLand[it] < 0 && g.map.hexNbrs[it].any { n -> g.rivalLand[n] >= 0 } }
        fun threat(h: Int) = (g.map.hexNbrs[h].filter { g.rivalLand[it] >= 0 }.maxOfOrNull { g.rivalGuard[it] - 1 } ?: 0) - g.defence(h)
        val danger = g.diff >= 2   // only from Hard up do rivals attack
        val stage = border.minByOrNull { h -> g.map.hexNbrs[h].filter { g.rivalLand[it] >= 0 }.minOf { g.rivalDefence(it) } * 10 - g.guard[h] - h * 0.001 }
            ?: (0 until g.H).filter { conn[it] }.minByOrNull { h -> ((0 until g.H).filter { g.rivalLand[it] >= 0 }.minOfOrNull { g.hexDist(h, it) } ?: 99) * 1000 + h }
        if (g.soldiers > 0) {
            if (danger) border.maxByOrNull { threat(it) }?.takeIf { threat(it) >= 2 }?.let { return CAct.Guard(it, 1) }
            if (stage != null) return CAct.Guard(stage, 1)
        }
        // call back soldiers standing where no rival threatens, so they can mass at the staging tile
        for (h in 0 until g.H) if (conn[h] && h != stage && g.guard[h] > 0 && (!danger || threat(h) < 0 || h !in border)) return CAct.Guard(h, -1)
        var strike: CAct.Attack? = null; var sv = -99
        for (h in border) if (g.guard[h] > 0) for (t2 in g.map.hexNbrs[h]) if (g.rivalLand[t2] >= 0) {
            val m = g.guard[h] - g.rivalDefence(t2) + (if (g.rivals[g.rivalLand[t2]].capital == t2) 2 else 0)
            if (g.guard[h] >= g.rivalDefence(t2) + 2 && m > sv) { sv = m; strike = CAct.Attack(h, t2) }
        }
        strike?.let { return it }
        // 6b. a wonder, once there's a town and stone to spare
        if (g.count(A.TOWN) > 0 && g.res[A.STONE] >= 12 && g.res[A.GOLD] >= 8) build(AegStewardship.WONDERS.toList())?.let { return it }
        // 7. more food and homes ahead of need
        if (foodNet < 6 && workers < g.pop + 2) build(listOf(A.FARM, A.FISHERY, A.RANCH))?.let { return it }
        if (g.housing() - g.pop < 4) build(listOf(A.VILLAGE))?.let { return it }
        // 8. roads: toward rich land, ruins and the nearest rival
        if (g.res[A.TIMBER] >= 2) {
            val target = g.rivals.filter { it.alive }.minByOrNull { r -> (0 until g.H).filter { conn[it] }.minOf { g.hexDist(it, r.capital) } }?.capital
            var best: CAct.Road? = null; var bv = -99.0
            for (a in 0 until g.H) if (conn[a]) for (b in g.roadTargets(a, conn)) {
                if (!g.can(g.roadCost(b))) continue
                val v = value(g, b) - (if (target != null) g.hexDist(b, target) * (if (g.allSoldiers() >= 6) 0.8 else 0.25) else 0.0) + rng.nextDouble()
                if (v > bv) { bv = v; best = CAct.Road(a, b) }
            }
            if (best != null && (free.size < 3 || g.res[A.TIMBER] >= 6)) return best
        }
        if (g.can(A.SCOUT_COST) && g.res[A.GOLD] >= 8 && rng.nextInt(4) == 0) (0 until g.H).filter { conn[it] && g.scoutable(it) }.randomOrNull(rng)?.let { return CAct.Scout(it) }
        return CAct.End
    }
}
