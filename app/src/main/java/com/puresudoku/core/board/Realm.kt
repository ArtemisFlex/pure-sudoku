package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Board geometry for one game of Eiseria: pointy-top hexes in axial coordinates, their corners and edges.
 * Positions are in hex-size units (centre to corner = 1), centred on the middle hex.
 * Harder levels use bigger islands: the radius-2 hexagon plus some of the next ring.
 */
class RealmGrid(val hexQ: IntArray, val hexR: IntArray) {
    val HEXES = hexQ.size
    val hexX: FloatArray
    val hexY: FloatArray
    val ring: IntArray
    val hexVerts: Array<IntArray>
    val hexNbrs: Array<IntArray>
    val vertX: FloatArray
    val vertY: FloatArray
    val vertHexes: Array<IntArray>
    val vertNbrs: Array<IntArray>
    val vertEdges: Array<IntArray>
    val edgeA: IntArray
    val edgeB: IntArray
    /** Edges on the coast (touching one hex), in order around the board. */
    val coast: IntArray
    val VERTS: Int
    val EDGES: Int
    /** Furthest corner from the centre, in hex units. */
    val extent: Float

    init {
        val s3 = sqrt(3.0)
        hexX = FloatArray(HEXES) { (s3 * (hexQ[it] + hexR[it] / 2.0)).toFloat() }
        hexY = FloatArray(HEXES) { (1.5 * hexR[it]).toFloat() }
        ring = IntArray(HEXES) { maxOf(abs(hexQ[it]), abs(hexR[it]), abs(hexQ[it] + hexR[it])) }
        val keys = HashMap<Long, Int>()
        val vx = ArrayList<Float>(); val vy = ArrayList<Float>()
        hexVerts = Array(HEXES) { h ->
            IntArray(6) { k ->
                val a = Math.toRadians(60.0 * k - 90.0)
                val x = hexX[h] + kotlin.math.cos(a).toFloat(); val y = hexY[h] + kotlin.math.sin(a).toFloat()
                val key = (x * 1000).roundToInt().toLong() * 100_000L + (y * 1000).roundToInt()
                keys.getOrPut(key) { vx += x; vy += y; vx.size - 1 }
            }
        }
        VERTS = vx.size
        vertX = vx.toFloatArray(); vertY = vy.toFloatArray()
        vertHexes = Array(VERTS) { v -> (0 until HEXES).filter { v in hexVerts[it] }.toIntArray() }
        val ea = ArrayList<Int>(); val eb = ArrayList<Int>()
        val seen = HashSet<Long>()
        for (h in 0 until HEXES) for (k in 0 until 6) {
            val a = hexVerts[h][k]; val b = hexVerts[h][(k + 1) % 6]
            val key = minOf(a, b) * 1000L + maxOf(a, b)
            if (seen.add(key)) { ea += minOf(a, b); eb += maxOf(a, b) }
        }
        edgeA = ea.toIntArray(); edgeB = eb.toIntArray(); EDGES = edgeA.size
        vertEdges = Array(VERTS) { v -> (0 until EDGES).filter { edgeA[it] == v || edgeB[it] == v }.toIntArray() }
        vertNbrs = Array(VERTS) { v -> vertEdges[v].map { if (edgeA[it] == v) edgeB[it] else edgeA[it] }.toIntArray() }
        hexNbrs = Array(HEXES) { h ->
            (0 until HEXES).filter { o -> o != h && hexVerts[o].count { it in hexVerts[h] } == 2 }.toIntArray()
        }
        coast = (0 until EDGES).filter { e -> vertHexes[edgeA[e]].intersect(vertHexes[edgeB[e]].toSet()).size == 1 }
            .sortedBy { e -> atan2((vertY[edgeA[e]] + vertY[edgeB[e]]).toDouble(), (vertX[edgeA[e]] + vertX[edgeB[e]]).toDouble()) }
            .toIntArray()
        extent = (0 until VERTS).maxOf { sqrt(vertX[it] * vertX[it] + vertY[it] * vertY[it]) }
    }

    /** One portal per painted pier: the outermost coast edge facing that pier. */
    val docks: IntArray by lazy {
        val used = HashSet<Int>()
        IntArray(RealmMap.DOCK_PX.size) { k ->
            val want = atan2((RealmMap.DOCK_PY[k] - RealmMap.ISLAND_CY).toDouble(), (RealmMap.DOCK_PX[k] - RealmMap.ISLAND_CX).toDouble())
            coast.filter { it !in used }.minBy { e ->
                val mx = (vertX[edgeA[e]] + vertX[edgeB[e]]) / 2.0; val my = (vertY[edgeA[e]] + vertY[edgeB[e]]) / 2.0
                var d = abs(atan2(my, mx) - want); if (d > Math.PI) d = 2 * Math.PI - d
                d * 4 - sqrt(mx * mx + my * my) * 0.25
            }.also { used += it }
        }
    }

    fun edgeOf(a: Int, b: Int): Int = vertEdges[a].firstOrNull { edgeA[it] == b || edgeB[it] == b } ?: -1
    fun other(e: Int, v: Int) = if (edgeA[e] == v) edgeB[e] else edgeA[e]
}

/** Board shapes by level, plus where the painted island and its piers sit in rt_island. */
object RealmMap {
    /** Island art: centre and the radius (px, in the 1254-px source) the board's outer corners reach. */
    const val ISLAND_CX = 630f
    const val ISLAND_CY = 590f
    const val ISLAND_FIT = 462f
    /** The seven painted piers, in source px. */
    val DOCK_PX = floatArrayOf(285f, 965f, 130f, 1130f, 185f, 1075f, 640f)
    val DOCK_PY = floatArrayOf(205f, 210f, 455f, 465f, 830f, 830f, 1090f)

    private val cache = HashMap<Int, RealmGrid>()

    /** Extra hexes from the third ring at each level (0 at Easy, the full ring at Extreme). */
    private val EXTRA = intArrayOf(0, 3, 6, 9, 12, 18)

    fun forLevel(level: Int): RealmGrid = cache.getOrPut(level.coerceIn(0, 5)) { build(EXTRA[level.coerceIn(0, 5)]) }

    val standard: RealmGrid get() = forLevel(0)

    private fun build(extra: Int): RealmGrid {
        val qs = ArrayList<Int>(); val rs = ArrayList<Int>()
        for (r in -2..2) for (q in -2..2) if (abs(q + r) <= 2) { qs += q; rs += r }
        val ring3 = ArrayList<Pair<Int, Int>>()
        for (r in -3..3) for (q in -3..3) if (maxOf(abs(q), abs(r), abs(q + r)) == 3) ring3 += q to r
        val s3 = sqrt(3.0)
        ring3.sortBy { (q, r) -> atan2(1.5 * r, s3 * (q + r / 2.0)) }
        if (extra > 0) for (k in 0 until extra) { val (q, r) = ring3[(k * ring3.size) / extra]; qs += q; rs += r }
        return RealmGrid(qs.toIntArray(), rs.toIntArray())
    }

    /** Rebuilds the grid a save was made on (old saves: the standard board). */
    fun fromCoords(q: IntArray, r: IntArray): RealmGrid {
        for (lv in 0..5) { val g = forLevel(lv); if (g.hexQ.contentEquals(q) && g.hexR.contentEquals(r)) return g }
        return RealmGrid(q, r)
    }
}

/** One move in Realmforge. Codes are plain ints so the AI and saves stay simple. */
sealed class RAct {
    object Roll : RAct()
    object End : RAct()
    object BuySpell : RAct()
    data class Road(val e: Int) : RAct()
    data class Village(val v: Int) : RAct()
    data class Tower(val v: Int) : RAct()
    data class Dragon(val tile: Int) : RAct()
    data class Discard(val counts: IntArray) : RAct()
    data class Trade(val give: Int, val get: Int) : RAct()
    /** Play a spell. [a]/[b]: Harvest Moon resources, or Fireball edge in [a]. */
    data class Spell(val kind: Int, val a: Int = -1, val b: Int = -1) : RAct()
    /** Computer proposes a deal: it gives [give] (counts) and wants [want] (counts). */
    data class Offer(val give: IntArray, val want: IntArray) : RAct()
}

/**
 * Eiseria: a two-player fantasy take on the settle-and-trade hex game.
 * Player 0 is you, player 1 the computer. Three twists: the Unmapped Realm (fog of war),
 * Dragon's Wake (a growing, walking dragon you can slay) and Night & Day.
 */
class Realm(val twist: Int, val target: Int, val map: RealmGrid = RealmMap.standard, val level: Int = 0) {
    companion object {
        const val TIMBER = 0; const val ORE = 1; const val GRAIN = 2; const val BEASTS = 3; const val MANA = 4; const val WASTE = 5
        val RES = listOf("timber", "ore", "grain", "beasts", "mana")
        val TERRAIN = listOf("Enchanted forest", "Dwarven mountain", "Elven fields", "Griffin meadow", "Crystal spires", "Dragon's Waste")

        const val FOG = 0; const val WAKE = 1; const val NIGHT = 2
        /** Ashfall: a volcano in the middle erupts on every 7, burying tiles everyone depends on. */
        const val ASH = 3
        /** Ashfall's spells: the Knight steals a card instead of chasing a dragon. */
        val ASH_SPELLS = listOf("Knight", "Relic", "Earthshaper", "Harvest Moon", "Fireball")
        val ASH_SPELL_TEXT = listOf(
            "Steal a card from your rival. Three or more Knights, and the most, earns Mightiest Host (2 points).",
            "A secret treasure: worth 1 point. Counts by itself.",
            "Build two roads for free.",
            "Take any two resources from the supply.",
            "Burn one of your rival's roads that has a loose end.",
        )
        /** Ashfall: turns a buried tile stays ruined, then ashen, and flows per eruption at most, by level. */
        const val RUINED = 4
        const val ASHEN = 1


        const val KNIGHT = 0; const val RELIC = 1; const val EARTH = 2; const val HARVEST = 3; const val FIREBALL = 4
        val SPELLS = listOf("Knight", "Relic", "Earthshaper", "Harvest Moon", "Fireball")
        val SPELL_TEXT = listOf(
            "Drive the dragon to another tile and steal a card. Three or more Knights, and the most, earns Mightiest Host (2 points).",
            "A secret treasure: worth 1 point. Counts by itself.",
            "Build two roads for free.",
            "Take any two resources from the supply.",
            "Burn one of your rival's roads that has a loose end.",
        )

        val COST_ROAD = intArrayOf(1, 0, 0, 0, 1)
        val COST_VILLAGE = intArrayOf(1, 0, 1, 1, 1)
        val COST_TOWER = intArrayOf(0, 3, 2, 0, 0)
        val COST_SPELL = intArrayOf(0, 1, 1, 1, 0)

        const val SETUP = 0; const val ROLL = 1; const val DISCARD = 2; const val DRAGON = 3; const val MAIN = 4; const val OVER = 5

        const val MAX_ROADS = 15; const val MAX_VILLAGES = 5; const val MAX_TOWERS = 4

        fun pips(n: Int) = if (n in 2..12) 6 - abs(7 - n) else 0

        fun target(d: Difficulty) = when (d) {
            Difficulty.EASY, Difficulty.MEDIUM -> 10
            Difficulty.HARD, Difficulty.EXPERT -> 11
            else -> 12
        }

        /** Wastelands on the board at each level. */
        private val WASTES = intArrayOf(1, 1, 2, 2, 3, 3)
        /** Share of tiles visible at the start, by level, when fog is on. */
        private val CLEAR = floatArrayOf(0.37f, 0.32f, 0.27f, 0.22f, 0.17f, 0.12f)
        /** Wyrmrise: how many wild dragons may wake from newly found wastelands. */
        val WILD_CAP = intArrayOf(0, 0, 1, 2, 9, 9)

        /** Rebuilds a game (with its island shape) from [state]. */
        fun fromState(s: IntArray): Realm {
            if (s[2] != -77) return Realm(s[0], s[1]).also { it.load(s) }
            var i = 3
            fun get(): IntArray { val n = s[i++]; val a = s.copyOfRange(i, i + n); i += n; return a }
            val q = get(); val r = get(); val lv = s[i]
            return Realm(s[0], s[1], RealmMap.fromCoords(q, r), lv).also { it.load(s) }
        }

        fun newGame(twist: Int, d: Difficulty, rng: Random): Realm {
            val g = Realm(twist, target(d), RealmMap.forLevel(d.ordinal), d.ordinal)
            g.diceDeck = d.ordinal <= 1
            g.deal(rng)
            return g
        }
    }

    // ---------------------------------------------------------------- state

    val terrain = IntArray(map.HEXES)
    val number = IntArray(map.HEXES)
    val revealed = BooleanArray(map.HEXES) { true }
    val ruin = BooleanArray(map.HEXES)
    var ley = IntArray(0)
    var portEdge = IntArray(7)
    /** 0..4 = 2:1 for that resource, 5 = 3:1 any. */
    var portType = IntArray(7)

    val vOwner = IntArray(map.VERTS) { -1 }
    val vLevel = IntArray(map.VERTS)
    val eOwner = IntArray(map.EDGES) { -1 }

    val res = Array(2) { IntArray(5) }
    val spells = Array(2) { IntArray(5) }
    /** Spells bought this turn (can't be played until next turn). */
    val fresh = IntArray(5)
    val deck = ArrayList<Int>()
    val knights = IntArray(2)
    var spellPlayed = false
    var freeRoads = 0

    var dragon = 0
    /** Wyrmrise: wild dragons woken from newly found wastelands. They block their tiles and walk with the dragon. */
    val wild = ArrayList<Int>()
    /** Who has claimed each tile's wasteland (-1: nobody). */
    var wasteClaim = IntArray(map.HEXES) { -1 }
    var dragonHp = 3
    var dragonWounds = 0
    var dragonGone = false
    var slayer = -1

    var roadHolder = -1
    var hostHolder = -1

    // Ashfall: the volcano
    var volcano = -1
    /** Each tile's state after the lava: 0 untouched, 1 ruined, 2 ashen, 3 renewed (richer). */
    val stage = IntArray(map.HEXES)
    /** Turns left in a ruined or ashen tile's current state. */
    val heat = IntArray(map.HEXES)
    /** The next eruption's lava flows, each a path out from the volcano, shown to both players. */
    val forecast = ArrayList<IntArray>()
    var eruptions = 0
    /** The tiles the last eruption's lava ran over, and the die that sent it. */
    var lastFlow = IntArray(0)
    var lastAshDie = 0
    val spellNames get() = if (twist == ASH) ASH_SPELLS else SPELLS
    val spellText get() = if (twist == ASH) ASH_SPELL_TEXT else SPELL_TEXT

    var turn = 0
    var phase = SETUP
    var setupStep = 0
    var setupVillage = -1
    var round = 0
    var afterDragon = MAIN
    val discardNeed = IntArray(2)
    var diceDeck = false
    val dice = ArrayList<Int>()
    var lastRoll = 0
    var lastDie = intArrayOf(0, 0)
    var winner = -1

    /** Things that just happened, for the screen's message line. Cleared by the session. */
    val log = ArrayList<String>()

    val night get() = twist == NIGHT && round % 2 == 1
    private val setupOrder = intArrayOf(0, 1, 1, 0)

    // ---------------------------------------------------------------- setup

    /** True when this game hides tiles under fog: always in the Unmapped Realm, from Hard up in the others. */
    val foggy get() = twist == FOG || (level >= 2 && twist != ASH)

    private fun deal(rng: Random) {
        val n = map.HEXES
        val wastes = WASTES[level.coerceIn(0, 5)]
        // resources in the classic 4:3:4:4:3 mix, scaled to the island
        val base = intArrayOf(4, 3, 4, 4, 3); val m = n - wastes
        val counts = IntArray(5) { base[it] * m / 18 }
        var left = m - counts.sum(); var k = 0
        while (left > 0) { counts[intArrayOf(0, 2, 3, 1, 4)[k % 5]]++; left--; k++ }
        val tiles = ArrayList<Int>()
        for (r in 0..4) repeat(counts[r]) { tiles += r }
        repeat(wastes) { tiles += WASTE }
        val std = listOf(2, 3, 3, 4, 4, 5, 5, 6, 6, 8, 8, 9, 9, 10, 10, 11, 11, 12)
        var tries = 0
        while (true) {
            tiles.shuffle(rng)
            val nums = ArrayList<Int>()
            while (nums.size < m) nums += std.shuffled(rng).take(minOf(18, m - nums.size))
            val ns = nums.shuffled(rng).iterator()
            for (h in 0 until n) { terrain[h] = tiles[h]; number[h] = if (tiles[h] == WASTE) 0 else ns.next() }
            val ok = (0 until n).none { h -> number[h] in setOf(6, 8) && map.hexNbrs[h].any { number[it] in setOf(6, 8) } }
            if (ok || ++tries > 400) break
        }
        dragon = (0 until n).filter { terrain[it] == WASTE }.minBy { map.ring[it] * 10 + rng.nextInt(10) }
        if (twist == ASH) {
            // the volcano takes the middle tile; whatever was there swaps with a wasteland
            volcano = (0 until n).minBy { map.hexX[it] * map.hexX[it] + map.hexY[it] * map.hexY[it] }
            if (terrain[volcano] != WASTE) {
                val w = (0 until n).first { terrain[it] == WASTE }
                terrain[w] = terrain[volcano]; number[w] = number[volcano]
                terrain[volcano] = WASTE; number[volcano] = 0
            }
            dragon = volcano; dragonGone = true
        }
        portEdge = map.docks.copyOf()
        portType = (listOf(5, 5) + (0..4)).shuffled(rng).toIntArray()
        deck.clear()
        repeat(14) { deck += KNIGHT }; repeat(5) { deck += RELIC }; repeat(2) { deck += EARTH }; repeat(2) { deck += HARVEST }; repeat(2) { deck += FIREBALL }
        deck.shuffle(rng)
        if (foggy) {
            val clear = maxOf(4, (n * CLEAR[level.coerceIn(0, 5)]).roundToInt())
            val order = (0 until n).sortedBy { sqrt(map.hexX[it] * map.hexX[it] + map.hexY[it] * map.hexY[it]) + rng.nextFloat() * 0.6f }
            for (h in 0 until n) revealed[h] = false
            order.take(clear).forEach { revealed[it] = true }
            revealed[dragon] = true
        }
        if (twist == FOG) {
            val hidden = (0 until n).filter { !revealed[it] && terrain[it] != WASTE }.shuffled(rng)
            hidden.take(3 + level / 2).forEach { ruin[it] = true }
            val tips = (0 until map.VERTS).filter { map.vertHexes[it].size == 1 }
            ley = intArrayOf(45, 135, 225, 315).map { deg ->
                tips.minBy { v ->
                    val a = Math.toDegrees(atan2(map.vertY[v].toDouble(), map.vertX[v].toDouble())).let { if (it < 0) it + 360 else it }
                    minOf(abs(a - deg), 360 - abs(a - deg)) - sqrt(map.vertX[v] * map.vertX[v] + map.vertY[v] * map.vertY[v]) * 2
                }
            }.toIntArray()
        }
        if (twist == WAKE) { dragonHp = 3; dragonWounds = 0 }
    }

    // ---------------------------------------------------------------- queries

    val setupPlayer get() = setupOrder[setupStep.coerceAtMost(3)]
    val actor: Int get() = when (phase) {
        SETUP -> setupPlayer
        DISCARD -> if (discardNeed[0] > 0) 0 else 1
        else -> turn
    }

    fun total(p: Int) = res[p].sum()
    fun can(p: Int, cost: IntArray) = (0..4).all { res[p][it] >= cost[it] }
    fun count(p: Int, level: Int) = (0 until map.VERTS).count { vOwner[it] == p && vLevel[it] == level }
    fun roads(p: Int) = eOwner.count { it == p }

    fun vp(p: Int, withRelics: Boolean = true): Int =
        count(p, 1) + 2 * count(p, 2) + (if (roadHolder == p) 2 else 0) + (if (hostHolder == p) 2 else 0) +
            (if (slayer == p) 2 else 0) + (if (withRelics) spells[p][RELIC] else 0) + wasteClaim.count { it == p }

    /** Is [h] blocked by a dragon? */
    fun blocked(h: Int) = (h == dragon && !dragonGone) || h in wild

    /** Progress toward claiming wasteland [h]: (roads around it, has a tower on it). */
    fun claimProgress(h: Int, p: Int): Pair<Int, Boolean> {
        val vs = map.hexVerts[h]
        val roads = (0 until 6).count { k -> map.edgeOf(vs[k], vs[(k + 1) % 6]).let { it >= 0 && eOwner[it] == p } }
        return roads to vs.any { vOwner[it] == p && vLevel[it] == 2 }
    }

    /** The first to ring a wasteland with 2+ roads and raise a tower on it claims it: 1 point and a free spell. */
    private fun checkClaims(p: Int) {
        for (h in 0 until map.HEXES) {
            if (terrain[h] != WASTE || wasteClaim[h] >= 0 || h == volcano) continue
            val (r, t) = claimProgress(h, p)
            if (r >= 2 && t) {
                wasteClaim[h] = p
                log += (if (p == 0) "You claim" else "The rival claims") + " the Dragon's Waste! +1 point"
                if (deck.isNotEmpty()) { val sp = deck.removeAt(deck.lastIndex); spells[p][sp]++; if (turn == p) fresh[sp]++ }
            }
        }
    }

    fun portRate(p: Int, r: Int): Int {
        var best = 4
        for (k in portEdge.indices) {
            val e = portEdge[k]
            val mine = vOwner[map.edgeA[e]] == p || vOwner[map.edgeB[e]] == p
            if (!mine) continue
            if (portType[k] == 5) best = minOf(best, 3) else if (portType[k] == r) best = 2
        }
        return best
    }

    fun portAt(v: Int): Int = portEdge.indices.firstOrNull { map.edgeA[portEdge[it]] == v || map.edgeB[portEdge[it]] == v }?.let { portType[it] } ?: -1

    private fun spacingOk(v: Int) = vOwner[v] < 0 && map.vertNbrs[v].all { vOwner[it] < 0 }

    fun villageSpots(p: Int): List<Int> {
        if (count(p, 1) >= MAX_VILLAGES) return emptyList()
        return (0 until map.VERTS).filter { v ->
            spacingOk(v) && (phase == SETUP || map.vertEdges[v].any { eOwner[it] == p })
        }
    }

    fun towerSpots(p: Int): List<Int> = if (count(p, 2) >= MAX_TOWERS) emptyList() else (0 until map.VERTS).filter { vOwner[it] == p && vLevel[it] == 1 }

    fun roadSpots(p: Int): List<Int> {
        if (roads(p) >= MAX_ROADS) return emptyList()
        if (phase == SETUP) return if (setupVillage >= 0) map.vertEdges[setupVillage].filter { eOwner[it] < 0 } else emptyList()
        return (0 until map.EDGES).filter { e -> eOwner[e] < 0 && roadReaches(p, e) }
    }

    private fun roadReaches(p: Int, e: Int): Boolean {
        for (v in intArrayOf(map.edgeA[e], map.edgeB[e])) {
            if (vOwner[v] == p) return true
            if (vOwner[v] < 0 && map.vertEdges[v].any { it != e && eOwner[it] == p }) return true
        }
        return false
    }

    /** Rival roads Fireball may burn: an end touching none of their own pieces. */
    fun fireballTargets(p: Int): List<Int> {
        val o = 1 - p
        return (0 until map.EDGES).filter { e ->
            eOwner[e] == o && intArrayOf(map.edgeA[e], map.edgeB[e]).any { v ->
                vOwner[v] != o && map.vertEdges[v].none { it != e && eOwner[it] == o }
            }
        }
    }

    fun dragonTiles(): List<Int> = (0 until map.HEXES).filter { it != dragon }

    /** Players with a building on [tile]. */
    fun ownersAt(tile: Int) = map.hexVerts[tile].filter { vOwner[it] >= 0 }.map { vOwner[it] }.toSet()

    fun canPlay(p: Int, kind: Int): Boolean {
        if (spellPlayed || turn != p || kind == RELIC) return false
        if (spells[p][kind] - (if (p == turn) fresh[kind] else 0) <= 0) return false
        return when (kind) {
            KNIGHT -> phase == ROLL || phase == MAIN
            EARTH -> phase == MAIN && roadSpots(p).isNotEmpty()
            HARVEST -> phase == MAIN
            FIREBALL -> phase == MAIN && fireballTargets(p).isNotEmpty()
            else -> false
        }
    }

    fun leyPower(p: Int): Int = if (twist != FOG) 0 else ley.count { v -> map.vertEdges[v].any { eOwner[it] == p } || vOwner[v] == p } / 2

    fun leyVisible(v: Int) = map.vertHexes[v].any { revealed[it] }

    // ---------------------------------------------------------------- actions

    fun legal(a: RAct): Boolean {
        val p = actor
        if (phase == OVER) return false
        return when (a) {
            RAct.Roll -> phase == ROLL
            RAct.End -> phase == MAIN && freeRoads == 0
            RAct.BuySpell -> phase == MAIN && can(p, COST_SPELL) && deck.isNotEmpty()
            is RAct.Road -> (phase == SETUP && setupVillage >= 0 || phase == MAIN && (freeRoads > 0 || can(p, COST_ROAD))) && a.e in roadSpots(p)
            is RAct.Village -> (phase == SETUP && setupVillage < 0 || phase == MAIN && freeRoads == 0 && can(p, COST_VILLAGE)) && a.v in villageSpots(p)
            is RAct.Tower -> phase == MAIN && freeRoads == 0 && can(p, COST_TOWER) && a.v in towerSpots(p)
            is RAct.Dragon -> phase == DRAGON && a.tile != dragon && a.tile in 0 until map.HEXES
            is RAct.Discard -> phase == DISCARD && a.counts.sum() == discardNeed[p] && (0..4).all { a.counts[it] in 0..res[p][it] }
            is RAct.Trade -> phase == MAIN && freeRoads == 0 && a.give != a.get && res[p][a.give] >= portRate(p, a.give)
            is RAct.Spell -> canPlay(p, a.kind) && when (a.kind) {
                HARVEST -> a.a in 0..4 && a.b in 0..4
                FIREBALL -> a.a in fireballTargets(p)
                else -> true
            }
            is RAct.Offer -> phase == MAIN && p == 1 && (0..4).all { res[1][it] >= a.give[it] && res[0][it] >= a.want[it] }
        }
    }

    /** Applies [a] for the acting player. Randomness (dice, steals, spell draws) comes from [rng]. */
    fun apply(a: RAct, rng: Random) {
        val p = actor
        when (a) {
            RAct.Roll -> roll(rng)
            RAct.End -> endTurn(rng)
            RAct.BuySpell -> {
                pay(p, COST_SPELL)
                val s = deck.removeAt(deck.lastIndex); spells[p][s]++; fresh[s]++
                log += if (p == 0) "You learned ${spellNames[s]}" else "Rival bought a spell"
            }
            is RAct.Road -> {
                if (phase == SETUP) {
                    eOwner[a.e] = p; reveal(intArrayOf(map.edgeA[a.e], map.edgeB[a.e]), p, rng)
                    setupVillage = -1; setupStep++
                    if (setupStep >= 4) { phase = ROLL; turn = 0; round = 0 }
                } else {
                    if (freeRoads > 0) freeRoads-- else pay(p, COST_ROAD)
                    eOwner[a.e] = p
                    reveal(intArrayOf(map.edgeA[a.e], map.edgeB[a.e]), p, rng)
                    updateRoad()
                    checkClaims(p)
                    if (freeRoads > 0 && roadSpots(p).isEmpty()) freeRoads = 0
                }
            }
            is RAct.Village -> {
                vOwner[a.v] = p; vLevel[a.v] = 1
                if (phase == SETUP) {
                    setupVillage = a.v
                    if (setupStep >= 2) for (h in map.vertHexes[a.v]) if (terrain[h] != WASTE) res[p][terrain[h]]++
                } else pay(p, COST_VILLAGE)
                reveal(intArrayOf(a.v), p, rng)
                updateRoad()
            }
            is RAct.Tower -> { pay(p, COST_TOWER); vLevel[a.v] = 2; checkClaims(p) }
            is RAct.Dragon -> moveDragon(a.tile, p, rng, steal = 1 + (if (twist == WAKE && dragonHp >= 5) 1 else 0))
            is RAct.Discard -> {
                for (r in 0..4) res[p][r] -= a.counts[r]
                discardNeed[p] = 0
                log += (if (p == 0) "You" else "Rival") + " discarded ${a.counts.sum()}"
                if (discardNeed.all { it == 0 }) phase = if (dragonGone) MAIN else DRAGON
            }
            is RAct.Trade -> { res[p][a.give] -= portRate(p, a.give); res[p][a.get]++ }
            is RAct.Spell -> castSpell(p, a, rng)
            is RAct.Offer -> {
                for (r in 0..4) { res[1][r] += a.want[r] - a.give[r]; res[0][r] += a.give[r] - a.want[r] }
                log += "Deal done"
            }
        }
        checkWin()
    }

    private fun pay(p: Int, cost: IntArray) { for (r in 0..4) res[p][r] -= cost[r] }

    private fun reveal(verts: IntArray, p: Int, rng: Random) {
        if (!foggy) return
        for (v in verts) for (h in map.vertHexes[v]) if (!revealed[h]) {
            revealed[h] = true
            if (terrain[h] == WASTE && twist == WAKE && !dragonGone && wild.size < WILD_CAP[level.coerceIn(0, 5)]) {
                wild += h
                log += "A wild dragon wakes in the wasteland!"
            }
            if (twist != FOG) { log += (if (p == 0) "You" else "Rival") + " uncovered a ${TERRAIN[terrain[h]].lowercase()}"; continue }
            if (terrain[h] != WASTE) {
                res[p][terrain[h]]++
                log += (if (p == 0) "You" else "Rival") + " explored a ${TERRAIN[terrain[h]].lowercase()} (+1 ${RES[terrain[h]]})"
            } else log += (if (p == 0) "You" else "Rival") + " found the Dragon's Waste"
            if (ruin[h]) {
                ruin[h] = false
                if (deck.isNotEmpty()) {
                    val s = deck.removeAt(deck.lastIndex); spells[p][s]++
                    if (turn == p && phase != SETUP) fresh[s]++
                    log += if (p == 0) "Ruins! You found a ${spellNames[s]} spell" else "Rival found ruins and a spell"
                }
            }
        }
    }

    private fun roll(rng: Random) {
        val n: Int
        if (diceDeck) {
            if (dice.size <= 5) { dice.clear(); for (a in 1..6) for (b in 1..6) dice += a * 10 + b; dice.shuffle(rng) }
            val d = dice.removeAt(dice.lastIndex); lastDie = intArrayOf(d / 10, d % 10)
        } else lastDie = intArrayOf(rng.nextInt(1, 7), rng.nextInt(1, 7))
        n = lastDie[0] + lastDie[1]
        lastRoll = n
        if (n == 7) {
            if (twist == ASH) erupt(rng)
            if (twist == WAKE && !dragonGone && dragonHp < 6) { dragonHp++; log += "The dragon's wrath grows (strength $dragonHp)" }
            for (q in 0..1) discardNeed[q] = if (total(q) > 7) total(q) / 2 else 0
            afterDragon = MAIN
            phase = when { discardNeed.any { it > 0 } -> DISCARD; dragonGone -> MAIN; else -> DRAGON }
        } else {
            produce(n)
            phase = MAIN
        }
        val lp = leyPower(turn)
        if (lp > 0) { res[turn][MANA] += lp; log += (if (turn == 0) "Your" else "Rival's") + " ley line hums: +$lp mana" }
    }

    fun production(p: Int, n: Int): IntArray {
        val got = IntArray(5)
        for (h in 0 until map.HEXES) {
            if (number[h] != n || blocked(h) || terrain[h] == WASTE || stage[h] == 1 || stage[h] == 2) continue
            var mult = 1
            if (night && terrain[h] == MANA) mult = 2
            // renewed land is richer: each building on it collects one more
            val bonus = if (stage[h] == 3) 1 else 0
            for (v in map.hexVerts[h]) if (vOwner[v] == p) got[terrain[h]] += vLevel[v] * mult + bonus
        }
        return got
    }

    private fun produce(n: Int) {
        for (p in 0..1) {
            val got = production(p, n)
            for (r in 0..4) res[p][r] += got[r]
            if (got.sum() > 0) log += (if (p == 0) "You got " else "Rival got ") + (0..4).filter { got[it] > 0 }.joinToString(", ") { "${got[it]} ${RES[it]}" }
        }
    }

    private fun moveDragon(tile: Int, by: Int, rng: Random, steal: Int) {
        dragon = tile
        phase = afterDragon
        val victim = 1 - by
        if (victim in ownersAt(tile)) stealFrom(victim, by, steal, rng)
        else log += "The dragon lands on the ${TERRAIN[terrain[tile]].lowercase()}"
    }

    private fun stealFrom(victim: Int, by: Int, n: Int, rng: Random) {
        repeat(n) {
            val pool = (0..4).flatMap { r -> List(res[victim][r]) { r } }
            if (pool.isEmpty()) return
            val r = pool[rng.nextInt(pool.size)]
            res[victim][r]--; if (by >= 0) res[by][r]++
            log += if (by == 0) "You stole 1 ${RES[r]}" else if (by == 1) "The rival stole your ${RES[r]}" else "The dragon burned 1 ${RES[r]}"
        }
    }

    private fun castSpell(p: Int, a: RAct.Spell, rng: Random) {
        spells[p][a.kind]--; spellPlayed = true
        val who = if (p == 0) "You" else "Rival"
        when (a.kind) {
            KNIGHT -> {
                knights[p]++
                log += "$who sent a Knight"
                updateHost()
                if (twist == ASH) { stealFrom(1 - p, p, 1, rng); return }
                if (twist == WAKE && !dragonGone) {
                    dragonWounds++
                    if (dragonWounds >= dragonHp) {
                        dragonGone = true; slayer = p
                        if (wild.isNotEmpty()) { wild.clear(); log += "The wild dragons flee with it" }
                        log += if (p == 0) "You drove the dragon off for good! +2 points" else "The rival slew the dragon! +2 points"
                        return
                    } else log += "The dragon is wounded ($dragonWounds of $dragonHp)"
                }
                if (!dragonGone) { afterDragon = phase; phase = DRAGON }
            }
            EARTH -> { freeRoads = 2; log += "$who cast Earthshaper" }
            HARVEST -> { res[p][a.a]++; res[p][a.b]++; log += "$who cast Harvest Moon" }
            FIREBALL -> { eOwner[a.a] = -1; updateRoad(); log += "$who burned a road with Fireball" }
        }
    }

    private fun endTurn(rng: Random) {
        fresh.fill(0); spellPlayed = false; freeRoads = 0
        if (twist == ASH) for (h in 0 until map.HEXES) when (stage[h]) {
            1 -> if (--heat[h] <= 0) { stage[h] = 2; heat[h] = ASHEN }
            2 -> if (--heat[h] <= 0) { stage[h] = 3; heat[h] = 0; log += "The ${TERRAIN[terrain[h]].lowercase()} is renewed, richer than before" }
        }
        turn = 1 - turn
        if (turn == 0) {
            round++
            if (twist == WAKE && !dragonGone) dragonWalk()
            if (twist == NIGHT && night) nightRaid(rng)
        }
        phase = ROLL
        checkWin()
    }

    /** Whoever leads (ties: you), for the dragon to hunt. */
    fun leader(): Int = if (vp(1, false) > vp(0, false)) 1 else 0

    /** How much a dragon on [tile] hurts [p]: pips times building levels. */
    fun harm(tile: Int, p: Int): Int = if (terrain[tile] == WASTE) 0 else map.hexVerts[tile].sumOf { v -> if (vOwner[v] == p) vLevel[v] * pips(number[tile]) else 0 }

    private fun dragonWalk() {
        val lead = leader()
        val next = map.hexNbrs[dragon].maxBy { harm(it, lead) * 10 - harm(it, 1 - lead) * 3 + it % 3 }
        dragon = next
        log += "The dragon walks to the ${TERRAIN[terrain[next]].lowercase()}"
        for (i in wild.indices) {
            val w = wild[i]
            val to = map.hexNbrs[w].filter { it != dragon && it !in wild }.maxByOrNull { harm(it, lead) * 10 - harm(it, 1 - lead) * 3 + it % 3 } ?: continue
            wild[i] = to
        }
        if (wild.isNotEmpty()) log += if (wild.size == 1) "A wild dragon stalks closer" else "${wild.size} wild dragons stalk closer"
    }

    private fun nightRaid(rng: Random) {
        val lead = leader()
        val best = (0 until map.HEXES).filter { it != dragon }.maxBy { harm(it, lead) * 10 - harm(it, 1 - lead) * 3 + it % 5 }
        dragon = best
        log += "Night falls. The dragon raids the ${TERRAIN[terrain[best]].lowercase()}"
        if (lead in ownersAt(best)) stealFrom(lead, -1, 1, rng)
    }

    // ---------------------------------------------------------------- Ashfall: the volcano

    /** Ruined or ashen: the lava passes over it and never buries it again until it's renewed. */
    fun scorched(h: Int) = stage[h] == 1 || stage[h] == 2
    /** Lava can bury [h]: a producing tile, not the volcano, a wasteland or land still recovering. */
    fun fresh(h: Int) = h != volcano && terrain[h] != WASTE && !scorched(h)
    /** The flow through [h] in a forecast (no longer used: a die decides the way). */
    fun flowAt(h: Int) = forecast.indexOfFirst { h in it }
    /** How many tiles in a row the lava runs: it reaches further as the game goes on. */
    fun rowLength() = minOf(1 + eruptions / 2, 6)

    private val byCoord: Map<Long, Int> by lazy { (0 until map.HEXES).associateBy { map.hexQ[it] * 1000L + map.hexR[it] } }
    private val dirQ = intArrayOf(1, 1, 0, -1, -1, 0)
    private val dirR = intArrayOf(0, -1, -1, 0, 1, 1)

    /** The straight row of tiles running out from the volcano in direction [k] (0..5, one per die face). */
    fun row(k: Int, len: Int = 99): List<Int> {
        val out = ArrayList<Int>()
        for (i in 1..len) out += byCoord[(map.hexQ[volcano] + dirQ[k] * i) * 1000L + (map.hexR[volcano] + dirR[k] * i)] ?: break
        return out
    }

    /** [h] could be buried by the next eruption, whichever way the die falls. */
    fun inDanger(h: Int) = twist == ASH && fresh(h) && (0..5).any { h in row(it, rowLength()) }

    /** The volcano erupts on a 7: a second die picks one of its six sides, and lava runs out that way in a straight row. */
    private fun erupt(rng: Random) {
        val die = rng.nextInt(1, 7); lastAshDie = die
        val r = row(die - 1, rowLength())
        log += "The volcano erupts! Its die shows $die" + if (r.isEmpty()) "" else ": lava pours toward the ${TERRAIN[terrain[r[0]]].lowercase()}"
        var prev = volcano
        for (h in r) {
            val shared = map.hexVerts[prev].filter { it in map.hexVerts[h] }
            if (shared.size == 2) {
                val e = map.edgeOf(shared[0], shared[1])
                if (e >= 0 && eOwner[e] >= 0) { log += (if (eOwner[e] == 0) "Lava burned your road" else "Lava burned the rival's road"); eOwner[e] = -1 }
            }
            if (fresh(h)) {
                stage[h] = 1; heat[h] = RUINED
                val who = ownersAt(h)
                log += "Lava buried the ${TERRAIN[terrain[h]].lowercase()} (${number[h]})" + when {
                    who.size == 2 -> ": it pays neither of you for now"
                    0 in who -> ": your buildings there collect nothing for now"
                    1 in who -> ": the rival's buildings there collect nothing for now"
                    else -> ""
                }
            }
            prev = h
        }
        lastFlow = r.toIntArray()
        updateRoad()
        eruptions++
    }

    // ---------------------------------------------------------------- titles

    fun longestRoad(p: Int): Int {
        var best = 0
        val mine = (0 until map.EDGES).filter { eOwner[it] == p }
        val used = BooleanArray(map.EDGES)
        fun dfs(v: Int, len: Int) {
            best = maxOf(best, len)
            if (vOwner[v] >= 0 && vOwner[v] != p && len > 0) return
            for (e in map.vertEdges[v]) if (eOwner[e] == p && !used[e]) {
                used[e] = true; dfs(map.other(e, v), len + 1); used[e] = false
            }
        }
        for (e in mine) { dfs(map.edgeA[e], 0); dfs(map.edgeB[e], 0) }
        return best
    }

    private fun updateRoad() {
        val l = IntArray(2) { longestRoad(it) }
        val h = roadHolder
        roadHolder = when {
            h >= 0 && l[h] >= 5 && l[h] >= l[1 - h] -> h
            l[0] >= 5 && l[0] > l[1] -> 0
            l[1] >= 5 && l[1] > l[0] -> 1
            else -> -1
        }
        if (roadHolder != h && roadHolder >= 0) log += (if (roadHolder == 0) "You hold" else "The rival holds") + " the Great Road (+2)"
    }

    private fun updateHost() {
        val h = hostHolder
        for (p in 0..1) if (knights[p] >= 3 && (hostHolder < 0 || knights[p] > knights[hostHolder])) hostHolder = p
        if (hostHolder != h) log += (if (hostHolder == 0) "You lead" else "The rival leads") + " the Mightiest Host (+2)"
    }

    private fun checkWin() {
        if (phase == SETUP || winner >= 0) return
        val p = turn
        if (vp(p) >= target) { winner = p; phase = OVER }
    }

    // ---------------------------------------------------------------- save / copy

    fun state(): IntArray {
        val o = ArrayList<Int>()
        fun put(a: IntArray) { o += a.size; a.forEach { o += it } }
        o += twist; o += target
        o += -77; put(map.hexQ); put(map.hexR); o += level
        put(terrain); put(number); put(revealed.map { if (it) 1 else 0 }.toIntArray()); put(ruin.map { if (it) 1 else 0 }.toIntArray())
        put(ley); put(portEdge); put(portType); put(vOwner); put(vLevel); put(eOwner)
        put(res[0]); put(res[1]); put(spells[0]); put(spells[1]); put(fresh); put(deck.toIntArray()); put(knights)
        put(intArrayOf(if (spellPlayed) 1 else 0, freeRoads, dragon, dragonHp, dragonWounds, if (dragonGone) 1 else 0, slayer,
            roadHolder, hostHolder, turn, phase, setupStep, setupVillage, round, afterDragon, if (diceDeck) 1 else 0, lastRoll, lastDie[0], lastDie[1], winner))
        put(discardNeed); put(dice.toIntArray())
        put(wild.toIntArray()); put(wasteClaim)
        put(stage); put(heat); put(forecast.flatMap { listOf(it.size) + it.toList() }.toIntArray()); put(intArrayOf(volcano, eruptions))
        put(lastFlow); put(intArrayOf(lastAshDie))
        return o.toIntArray()
    }

    fun load(s: IntArray) {
        var i = 2
        fun get(): IntArray { val n = s[i++]; val a = s.copyOfRange(i, i + n); i += n; return a }
        if (s[2] == -77) { i = 3; get(); get(); i++ }
        get().copyInto(terrain); get().copyInto(number)
        get().forEachIndexed { k, b -> revealed[k] = b == 1 }; get().forEachIndexed { k, b -> ruin[k] = b == 1 }
        ley = get(); portEdge = get(); portType = get(); get().copyInto(vOwner); get().copyInto(vLevel); get().copyInto(eOwner)
        get().copyInto(res[0]); get().copyInto(res[1]); get().copyInto(spells[0]); get().copyInto(spells[1]); get().copyInto(fresh)
        deck.clear(); deck += get().toList(); get().copyInto(knights)
        val m = get()
        spellPlayed = m[0] == 1; freeRoads = m[1]; dragon = m[2]; dragonHp = m[3]; dragonWounds = m[4]; dragonGone = m[5] == 1; slayer = m[6]
        roadHolder = m[7]; hostHolder = m[8]; turn = m[9]; phase = m[10]; setupStep = m[11]; setupVillage = m[12]; round = m[13]
        afterDragon = m[14]; diceDeck = m[15] == 1; lastRoll = m[16]; lastDie = intArrayOf(m[17], m[18]); winner = m[19]
        get().copyInto(discardNeed); dice.clear(); dice += get().toList()
        if (i < s.size) { wild.clear(); wild += get().toList(); wasteClaim = get() }
        if (i < s.size) {
            get().copyInto(stage); get().copyInto(heat)
            forecast.clear(); get().let { a -> var k = 0; while (k < a.size) { val n = a[k]; forecast += a.copyOfRange(k + 1, k + 1 + n); k += 1 + n } }
            val v = get(); volcano = v[0]; eruptions = v[1]
        }
        if (i < s.size) { lastFlow = get(); lastAshDie = get()[0] }
        forecast.clear()
    }

    fun copy(): Realm = fromState(state())
}
