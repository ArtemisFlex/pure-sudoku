package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Board geometry for Eiseria: 19 pointy-top hexes (radius 2), 54 corners, 72 edges.
 * Positions are in hex-size units (centre to corner = 1), centred on the middle hex.
 */
object RealmMap {
    const val HEXES = 19
    val hexQ: IntArray
    val hexR: IntArray
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

    init {
        val qs = ArrayList<Int>(); val rs = ArrayList<Int>()
        for (r in -2..2) for (q in -2..2) if (abs(q + r) <= 2) { qs += q; rs += r }
        hexQ = qs.toIntArray(); hexR = rs.toIntArray()
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
    }

    /** The seven piers painted on the board art, in hex units; each portal sits on the coast edge nearest one. */
    val DOCK_X = floatArrayOf(-2.82f, 2.80f, -4.06f, 4.13f, -3.69f, 3.75f, 0.12f)
    val DOCK_Y = floatArrayOf(-3.14f, -3.14f, -1.03f, -0.95f, 2.07f, 2.03f, 4.21f)
    val docks: IntArray by lazy {
        val used = HashSet<Int>()
        IntArray(DOCK_X.size) { k ->
            coast.filter { it !in used }.minBy { e ->
                val mx = (vertX[edgeA[e]] + vertX[edgeB[e]]) / 2 - DOCK_X[k]; val my = (vertY[edgeA[e]] + vertY[edgeB[e]]) / 2 - DOCK_Y[k]
                mx * mx + my * my
            }.also { used += it }
        }
    }

    fun edgeOf(a: Int, b: Int): Int = vertEdges[a].firstOrNull { edgeA[it] == b || edgeB[it] == b } ?: -1
    fun other(e: Int, v: Int) = if (edgeA[e] == v) edgeB[e] else edgeA[e]
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
class Realm(val twist: Int, val target: Int) {
    companion object {
        const val TIMBER = 0; const val ORE = 1; const val GRAIN = 2; const val BEASTS = 3; const val MANA = 4; const val WASTE = 5
        val RES = listOf("timber", "ore", "grain", "beasts", "mana")
        val TERRAIN = listOf("Enchanted forest", "Dwarven mountain", "Elven fields", "Griffin meadow", "Crystal spires", "Dragon's Waste")

        const val FOG = 0; const val WAKE = 1; const val NIGHT = 2

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

        fun newGame(twist: Int, d: Difficulty, rng: Random): Realm {
            val g = Realm(twist, target(d))
            g.diceDeck = d.ordinal <= 1
            g.deal(rng)
            return g
        }
    }

    // ---------------------------------------------------------------- state

    val terrain = IntArray(RealmMap.HEXES)
    val number = IntArray(RealmMap.HEXES)
    val revealed = BooleanArray(RealmMap.HEXES) { true }
    val ruin = BooleanArray(RealmMap.HEXES)
    var ley = IntArray(0)
    var portEdge = IntArray(7)
    /** 0..4 = 2:1 for that resource, 5 = 3:1 any. */
    var portType = IntArray(7)

    val vOwner = IntArray(RealmMap.VERTS) { -1 }
    val vLevel = IntArray(RealmMap.VERTS)
    val eOwner = IntArray(RealmMap.EDGES) { -1 }

    val res = Array(2) { IntArray(5) }
    val spells = Array(2) { IntArray(5) }
    /** Spells bought this turn (can't be played until next turn). */
    val fresh = IntArray(5)
    val deck = ArrayList<Int>()
    val knights = IntArray(2)
    var spellPlayed = false
    var freeRoads = 0

    var dragon = 0
    var dragonHp = 3
    var dragonWounds = 0
    var dragonGone = false
    var slayer = -1

    var roadHolder = -1
    var hostHolder = -1

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

    private fun deal(rng: Random) {
        val tiles = ArrayList<Int>()
        repeat(4) { tiles += TIMBER }; repeat(3) { tiles += ORE }; repeat(4) { tiles += GRAIN }; repeat(4) { tiles += BEASTS }; repeat(3) { tiles += MANA }; tiles += WASTE
        val nums = listOf(2, 3, 3, 4, 4, 5, 5, 6, 6, 8, 8, 9, 9, 10, 10, 11, 11, 12)
        while (true) {
            tiles.shuffle(rng)
            val ns = nums.shuffled(rng).iterator()
            for (h in 0 until RealmMap.HEXES) { terrain[h] = tiles[h]; number[h] = if (tiles[h] == WASTE) 0 else ns.next() }
            val ok = (0 until RealmMap.HEXES).none { h -> number[h] in setOf(6, 8) && RealmMap.hexNbrs[h].any { number[it] in setOf(6, 8) } }
            if (ok) break
        }
        dragon = terrain.indexOf(WASTE)
        val c = RealmMap.coast
        portEdge = RealmMap.docks.copyOf()
        portType = (listOf(5, 5) + (0..4)).shuffled(rng).toIntArray()
        deck.clear()
        repeat(14) { deck += KNIGHT }; repeat(5) { deck += RELIC }; repeat(2) { deck += EARTH }; repeat(2) { deck += HARVEST }; repeat(2) { deck += FIREBALL }
        deck.shuffle(rng)
        if (twist == FOG) {
            for (h in 0 until RealmMap.HEXES) revealed[h] = RealmMap.ring[h] <= 1 || h == dragon
            val outer = (0 until RealmMap.HEXES).filter { RealmMap.ring[it] == 2 && terrain[it] != WASTE }.shuffled(rng)
            outer.take(3).forEach { ruin[it] = true }
            val tips = (0 until RealmMap.VERTS).filter { RealmMap.vertHexes[it].size == 1 }
            ley = intArrayOf(45, 135, 225, 315).map { deg ->
                tips.minBy { v ->
                    val a = Math.toDegrees(atan2(RealmMap.vertY[v].toDouble(), RealmMap.vertX[v].toDouble())).let { if (it < 0) it + 360 else it }
                    minOf(abs(a - deg), 360 - abs(a - deg))
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
    fun count(p: Int, level: Int) = (0 until RealmMap.VERTS).count { vOwner[it] == p && vLevel[it] == level }
    fun roads(p: Int) = eOwner.count { it == p }

    fun vp(p: Int, withRelics: Boolean = true): Int =
        count(p, 1) + 2 * count(p, 2) + (if (roadHolder == p) 2 else 0) + (if (hostHolder == p) 2 else 0) +
            (if (slayer == p) 2 else 0) + (if (withRelics) spells[p][RELIC] else 0)

    fun portRate(p: Int, r: Int): Int {
        var best = 4
        for (k in portEdge.indices) {
            val e = portEdge[k]
            val mine = vOwner[RealmMap.edgeA[e]] == p || vOwner[RealmMap.edgeB[e]] == p
            if (!mine) continue
            if (portType[k] == 5) best = minOf(best, 3) else if (portType[k] == r) best = 2
        }
        return best
    }

    fun portAt(v: Int): Int = portEdge.indices.firstOrNull { RealmMap.edgeA[portEdge[it]] == v || RealmMap.edgeB[portEdge[it]] == v }?.let { portType[it] } ?: -1

    private fun spacingOk(v: Int) = vOwner[v] < 0 && RealmMap.vertNbrs[v].all { vOwner[it] < 0 }

    fun villageSpots(p: Int): List<Int> {
        if (count(p, 1) >= MAX_VILLAGES) return emptyList()
        return (0 until RealmMap.VERTS).filter { v ->
            spacingOk(v) && (phase == SETUP || RealmMap.vertEdges[v].any { eOwner[it] == p })
        }
    }

    fun towerSpots(p: Int): List<Int> = if (count(p, 2) >= MAX_TOWERS) emptyList() else (0 until RealmMap.VERTS).filter { vOwner[it] == p && vLevel[it] == 1 }

    fun roadSpots(p: Int): List<Int> {
        if (roads(p) >= MAX_ROADS) return emptyList()
        if (phase == SETUP) return if (setupVillage >= 0) RealmMap.vertEdges[setupVillage].filter { eOwner[it] < 0 } else emptyList()
        return (0 until RealmMap.EDGES).filter { e -> eOwner[e] < 0 && roadReaches(p, e) }
    }

    private fun roadReaches(p: Int, e: Int): Boolean {
        for (v in intArrayOf(RealmMap.edgeA[e], RealmMap.edgeB[e])) {
            if (vOwner[v] == p) return true
            if (vOwner[v] < 0 && RealmMap.vertEdges[v].any { it != e && eOwner[it] == p }) return true
        }
        return false
    }

    /** Rival roads Fireball may burn: an end touching none of their own pieces. */
    fun fireballTargets(p: Int): List<Int> {
        val o = 1 - p
        return (0 until RealmMap.EDGES).filter { e ->
            eOwner[e] == o && intArrayOf(RealmMap.edgeA[e], RealmMap.edgeB[e]).any { v ->
                vOwner[v] != o && RealmMap.vertEdges[v].none { it != e && eOwner[it] == o }
            }
        }
    }

    fun dragonTiles(): List<Int> = (0 until RealmMap.HEXES).filter { it != dragon }

    /** Players with a building on [tile]. */
    fun ownersAt(tile: Int) = RealmMap.hexVerts[tile].filter { vOwner[it] >= 0 }.map { vOwner[it] }.toSet()

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

    fun leyPower(p: Int): Int = if (twist != FOG) 0 else ley.count { v -> RealmMap.vertEdges[v].any { eOwner[it] == p } || vOwner[v] == p } / 2

    fun leyVisible(v: Int) = RealmMap.vertHexes[v].any { revealed[it] }

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
            is RAct.Dragon -> phase == DRAGON && a.tile != dragon && a.tile in 0 until RealmMap.HEXES
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
                log += if (p == 0) "You learned ${SPELLS[s]}" else "Rival bought a spell"
            }
            is RAct.Road -> {
                if (phase == SETUP) {
                    eOwner[a.e] = p; reveal(intArrayOf(RealmMap.edgeA[a.e], RealmMap.edgeB[a.e]), p, rng)
                    setupVillage = -1; setupStep++
                    if (setupStep >= 4) { phase = ROLL; turn = 0; round = 0 }
                } else {
                    if (freeRoads > 0) freeRoads-- else pay(p, COST_ROAD)
                    eOwner[a.e] = p
                    reveal(intArrayOf(RealmMap.edgeA[a.e], RealmMap.edgeB[a.e]), p, rng)
                    updateRoad()
                    if (freeRoads > 0 && roadSpots(p).isEmpty()) freeRoads = 0
                }
            }
            is RAct.Village -> {
                vOwner[a.v] = p; vLevel[a.v] = 1
                if (phase == SETUP) {
                    setupVillage = a.v
                    if (setupStep >= 2) for (h in RealmMap.vertHexes[a.v]) if (terrain[h] != WASTE) res[p][terrain[h]]++
                } else pay(p, COST_VILLAGE)
                reveal(intArrayOf(a.v), p, rng)
                updateRoad()
            }
            is RAct.Tower -> { pay(p, COST_TOWER); vLevel[a.v] = 2 }
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
        if (twist != FOG) return
        for (v in verts) for (h in RealmMap.vertHexes[v]) if (!revealed[h]) {
            revealed[h] = true
            if (terrain[h] != WASTE) {
                res[p][terrain[h]]++
                log += (if (p == 0) "You" else "Rival") + " explored a ${TERRAIN[terrain[h]].lowercase()} (+1 ${RES[terrain[h]]})"
            } else log += (if (p == 0) "You" else "Rival") + " found the Dragon's Waste"
            if (ruin[h]) {
                ruin[h] = false
                if (deck.isNotEmpty()) {
                    val s = deck.removeAt(deck.lastIndex); spells[p][s]++
                    if (turn == p && phase != SETUP) fresh[s]++
                    log += if (p == 0) "Ruins! You found a ${SPELLS[s]} spell" else "Rival found ruins and a spell"
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
        for (h in 0 until RealmMap.HEXES) {
            if (number[h] != n || (h == dragon && !dragonGone) || terrain[h] == WASTE) continue
            var mult = 1
            if (night && terrain[h] == MANA) mult = 2
            for (v in RealmMap.hexVerts[h]) if (vOwner[v] == p) got[terrain[h]] += vLevel[v] * mult
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
                if (twist == WAKE && !dragonGone) {
                    dragonWounds++
                    if (dragonWounds >= dragonHp) {
                        dragonGone = true; slayer = p
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
    fun harm(tile: Int, p: Int): Int = if (terrain[tile] == WASTE) 0 else RealmMap.hexVerts[tile].sumOf { v -> if (vOwner[v] == p) vLevel[v] * pips(number[tile]) else 0 }

    private fun dragonWalk() {
        val lead = leader()
        val next = RealmMap.hexNbrs[dragon].maxBy { harm(it, lead) * 10 - harm(it, 1 - lead) * 3 + it % 3 }
        dragon = next
        log += "The dragon walks to the ${TERRAIN[terrain[next]].lowercase()}"
    }

    private fun nightRaid(rng: Random) {
        val lead = leader()
        val best = (0 until RealmMap.HEXES).filter { it != dragon }.maxBy { harm(it, lead) * 10 - harm(it, 1 - lead) * 3 + it % 5 }
        dragon = best
        log += "Night falls. The dragon raids the ${TERRAIN[terrain[best]].lowercase()}"
        if (lead in ownersAt(best)) stealFrom(lead, -1, 1, rng)
    }

    // ---------------------------------------------------------------- titles

    fun longestRoad(p: Int): Int {
        var best = 0
        val mine = (0 until RealmMap.EDGES).filter { eOwner[it] == p }
        val used = BooleanArray(RealmMap.EDGES)
        fun dfs(v: Int, len: Int) {
            best = maxOf(best, len)
            if (vOwner[v] >= 0 && vOwner[v] != p && len > 0) return
            for (e in RealmMap.vertEdges[v]) if (eOwner[e] == p && !used[e]) {
                used[e] = true; dfs(RealmMap.other(e, v), len + 1); used[e] = false
            }
        }
        for (e in mine) { dfs(RealmMap.edgeA[e], 0); dfs(RealmMap.edgeB[e], 0) }
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
        put(terrain); put(number); put(revealed.map { if (it) 1 else 0 }.toIntArray()); put(ruin.map { if (it) 1 else 0 }.toIntArray())
        put(ley); put(portEdge); put(portType); put(vOwner); put(vLevel); put(eOwner)
        put(res[0]); put(res[1]); put(spells[0]); put(spells[1]); put(fresh); put(deck.toIntArray()); put(knights)
        put(intArrayOf(if (spellPlayed) 1 else 0, freeRoads, dragon, dragonHp, dragonWounds, if (dragonGone) 1 else 0, slayer,
            roadHolder, hostHolder, turn, phase, setupStep, setupVillage, round, afterDragon, if (diceDeck) 1 else 0, lastRoll, lastDie[0], lastDie[1], winner))
        put(discardNeed); put(dice.toIntArray())
        return o.toIntArray()
    }

    fun load(s: IntArray) {
        var i = 2
        fun get(): IntArray { val n = s[i++]; val a = s.copyOfRange(i, i + n); i += n; return a }
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
    }

    fun copy(): Realm { val s = state(); return Realm(s[0], s[1]).also { it.load(s) } }
}
