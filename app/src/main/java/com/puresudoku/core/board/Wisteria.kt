package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.math.roundToInt
import kotlin.random.Random

/** One move in Wisteria. */
sealed class WAct {
    object End : WAct()
    object Skip : WAct()
    object BuySpell : WAct()
    data class Settle(val h: Int) : WAct()
    data class Claim(val h: Int) : WAct()
    data class Upgrade(val h: Int) : WAct()
    data class Hire(val h: Int) : WAct()
    /** A village's knight attacks a touching enemy tile at once; with [black], the Black Dragon Knight strikes instead, from any of your tiles. */
    data class Strike(val from: Int, val to: Int, val black: Boolean = false) : WAct()
    /**
     * A tower's or castle's whole garrison marches along [route] (one tile per turn); its last tile is the target.
     * With [black], the Black Dragon Knight leads it from any of your tiles (a full garrison there joins him) and may fly over lakes.
     */
    data class March(val from: Int, val route: List<Int>, val black: Boolean = false) : WAct()
    /** A wall on the side between your tile [a] and its neighbour [b]. */
    data class Wall(val a: Int, val b: Int) : WAct()
    data class Trade(val give: Int, val get: Int) : WAct()
    data class Spell(val kind: Int, val a: Int = -1, val b: Int = -1) : WAct()
}

/** A marching army: where it stands, the tiles still to walk (last = target), and how many knights ([black]: the Black Dragon Knight leads it, counted in [size]). */
class Army(val owner: Int, var pos: Int, val route: ArrayList<Int>, var size: Int, var black: Boolean = false, var hunt: Boolean = false) {
    val target get() = route.lastOrNull() ?: pos
}

/**
 * Wisteria: territory conquest on the Eiseria islands. No dice: every tile you hold pays out each turn.
 * Claim neighbouring land, raise towers and castles, hire knights to strike or march on your rival,
 * and wall your borders. A single dragon leaves its lair in the middle every few turns (5, 4 or 3 by level), striking the leader.
 */
class Wisteria(val map: RealmGrid, val diff: Int, val target: Int, val ashfall: Boolean = false) {
    companion object {
        const val LAND = 0; const val WATER = 1; const val LAIR = 2; const val BARREN = 3
        /** Ashfall only: open sea around the island, molten lava, and cooled ashland. */
        const val SEA = 4; const val LAVA = 5; const val ASH = 6
        const val SETUP = 0; const val MAIN = 1; const val CHOICE = 2; const val OVER = 3

        const val RALLY = 0; const val RELIC = 1; const val STONE = 2; const val HARVEST = 3; const val SIEGE = 4
        val SPELLS = listOf("Rally", "Relic", "Stonewright", "Harvest Moon", "Siege Fire")
        val SPELL_TEXT = listOf(
            "A free knight joins one of your tiles that has room.",
            "A secret treasure: worth 1 point. Counts by itself.",
            "Raise two walls for free.",
            "Take any two resources from the supply.",
            "Break one of your rival's walls.",
        )

        /** Ashfall's spells: Stonewright's slot is Quench, and Rift is a sixth. */
        const val QUENCH = 2; const val RIFT = 5
        val ASH_SPELLS = listOf("Rally", "Relic", "Quench", "Harvest Moon", "Siege Fire", "Rift")
        val ASH_SPELL_TEXT = listOf(
            "A free knight joins one of your tiles that has room.",
            "A secret treasure: worth 1 point. Counts by itself.",
            "Cool one of your tiles: the next eruption can't bury it.",
            "Take any two resources from the supply.",
            "Break one of your rival's walls.",
            "Shift one forecast lava flow onto a new path from the volcano.",
        )

        /** Ashfall: turns between eruptions, flows per eruption at most, turns lava stays molten, turns ash takes to ripen. */
        val ERUPT_EVERY = intArrayOf(4, 4, 4, 4, 4, 4)
        val MAX_FLOWS = intArrayOf(3, 3, 4, 4, 5, 5)
        const val MOLTEN = 4
        const val RIPEN = 1
        /** Ashfall's goal is half again Wisteria's. */
        const val ASH_GOAL = 1.5f

        private val NB_Q = intArrayOf(1, 1, 0, -1, -1, 0)
        private val NB_R = intArrayOf(0, -1, -1, 0, 1, 1)
        private val gridCache = HashMap<String, RealmGrid>()
        private fun grid(q: IntArray, r: IntArray): RealmGrid = synchronized(gridCache) {
            gridCache.getOrPut(q.joinToString(",") + "/" + r.joinToString(",")) { RealmMap.fromCoords(q, r) }
        }

        /** Wisteria: Ashfall. The level's island inside a ring of open sea that lava can turn into new land. */
        fun newAshfall(d: Difficulty, rng: Random): Wisteria {
            val baseMap = RealmMap.forLevel(d.ordinal)
            val qs = baseMap.hexQ.toMutableList(); val rs = baseMap.hexR.toMutableList()
            val have = HashSet<Long>(); for (i in qs.indices) have += qs[i] * 1000L + rs[i]
            for (i in 0 until baseMap.HEXES) for (k in 0..5) {
                val q = baseMap.hexQ[i] + NB_Q[k]; val r = baseMap.hexR[i] + NB_R[k]
                if (have.add(q * 1000L + r)) { qs += q; rs += r }
            }
            val map = grid(qs.toIntArray(), rs.toIntArray())
            val land = baseMap.HEXES - 1 - WATERS[d.ordinal] - BARRENS[d.ordinal]
            val g = Wisteria(map, d.ordinal, (land * 0.72f * ASH_GOAL).roundToInt(), true)
            for (h in baseMap.HEXES until map.HEXES) g.kind[h] = SEA
            g.deal(rng)
            return g
        }

        val COST_TOWER = intArrayOf(0, 3, 2, 0, 0)
        val COST_CASTLE = intArrayOf(2, 3, 3, 0, 2)
        val COST_KNIGHT = intArrayOf(0, 1, 1, 1, 0)
        val COST_WALL = intArrayOf(1, 0, 0, 0, 1)
        val COST_SPELL = intArrayOf(0, 1, 1, 1, 0)
        private val CLAIM_BASE = intArrayOf(1, 0, 1, 1, 1)

        /** Turns between dragon attacks, by level: Easy and Medium 5, Hard and Expert 4, Master and Extreme 3. */
        val DRAGON_EVERY = intArrayOf(5, 5, 4, 4, 3, 3)
        const val DRAGON_HP = 5
        val CAPACITY = intArrayOf(0, 1, 2, 3)
        val LEVEL_NAME = listOf("", "village", "tower", "castle")

        private val WATERS = intArrayOf(1, 1, 2, 3, 4, 5)
        private val BARRENS = intArrayOf(0, 0, 1, 1, 2, 2)

        fun newGame(d: Difficulty, rng: Random): Wisteria {
            val map = RealmMap.forLevel(d.ordinal)
            // land tiles minus lair, water and barren; the goal is about two thirds of the land as villages
            val land = map.HEXES - 1 - WATERS[d.ordinal] - BARRENS[d.ordinal]
            val g = Wisteria(map, d.ordinal, (land * 0.72f).roundToInt())
            g.deal(rng)
            return g
        }

        fun fromState(s: IntArray): Wisteria {
            var i = 0
            fun get(): IntArray { val n = s[i++]; val a = s.copyOfRange(i, i + n); i += n; return a }
            val q = get(); val r = get(); val lv = s[i++]; val tg = s[i]
            return Wisteria(grid(q, r), lv % 100, tg, lv >= 100).also { it.load(s) }
        }
    }

    val H = map.HEXES
    val kind = IntArray(H)
    /** Which painting a land tile shows: its main resource. */
    val art = IntArray(H)
    /** Per-turn yield: yields[h * 5 + r]. */
    val yields = IntArray(H * 5)
    val owner = IntArray(H) { -1 }
    val level = IntArray(H)
    val knights = IntArray(H)
    /** Dragon damage a tile has taken. Its health is its level (village 1, tower 2, castle 3) minus this; it never heals. */
    val scar = IntArray(H)
    fun hp(h: Int) = level[h] - scar[h]
    val revealed = BooleanArray(H) { true }
    /** Wall owner per board edge (-1: none). A wall guards its owner's tile on that side. */
    val wall = IntArray(map.EDGES) { -1 }
    val armies = ArrayList<Army>()

    val res = Array(2) { IntArray(5) }
    val spells = Array(2) { IntArray(6) }
    val fresh = IntArray(6)
    val deck = ArrayList<Int>()
    var spellPlayed = false
    var freeWalls = 0

    var lair = 0
    var dragon = 0
    val dragonEvery get() = DRAGON_EVERY[diff.coerceIn(0, 5)]
    var dragonTimer = DRAGON_EVERY[diff.coerceIn(0, 5)]
    var dragonHp = DRAGON_HP
    /** Who slew the dragon (-1: it lives). They wear the Dragon Scale (+1 point) and command the Black Dragon Knight. */
    var slayer = -1
    /** The Black Dragon Knight has struck or set out this turn. */
    var blackUsed = false
    val dragonAlive get() = slayer < 0 && !ashfall
    /** Debug/stat: the last blow came from knights rather than a walled tower. Not saved. */
    var slainByKnights = false

    // Ashfall: the volcano
    /** Turns a tile stays molten (LAVA), or an ASH tile takes to ripen (0: ripe, paying double). */
    val heat = IntArray(H)
    /** Tiles cooled by Quench: the next eruption can't bury them. */
    val quench = BooleanArray(H)
    /** Land that is still ruined or ashen. The volcano never buries it again until it's renewed: later flows pass over it. */
    val burned = BooleanArray(H)
    /** The next eruption's lava flows, each a path out from the volcano, shown to both players. */
    val forecast = ArrayList<IntArray>()
    var eruptIn = 0
    var eruptions = 0
    /** How the game ended: 0 points goal, 1 last land lost, 2 the island locked, 3 turn limit. */
    var endReason = -1
    val spellNames get() = if (ashfall) ASH_SPELLS else SPELLS
    val spellText get() = if (ashfall) ASH_SPELL_TEXT else SPELL_TEXT

    var turn = 0
    var turns = 0
    var phase = SETUP
    var setupStep = 0
    var pending = -1
    var winner = -1
    val log = ArrayList<String>()

    val foggy get() = diff >= 1 && !ashfall
    val actor: Int get() = if (phase == SETUP) setupStep % 2 else turn

    // ---------------------------------------------------------------- setup

    private fun deal(rng: Random) {
        lair = (0 until H).minBy { map.hexX[it] * map.hexX[it] + map.hexY[it] * map.hexY[it] }
        kind[lair] = LAIR
        dragon = lair
        val others = (0 until H).filter { it != lair && kind[it] != SEA }.shuffled(rng)
        var k = 0
        repeat(WATERS[diff]) {
            // water never cuts the island in two
            while (k < others.size) {
                val h = others[k++]
                kind[h] = WATER
                if (connected()) break else kind[h] = LAND
            }
        }
        repeat(BARRENS[diff]) { while (k < others.size) { val h = others[k++]; if (kind[h] == LAND) { kind[h] = BARREN; break } } }
        for (h in 0 until H) if (kind[h] == LAND) rollYields(h, rng)
        deck.clear()
        if (ashfall) { repeat(9) { deck += RALLY }; repeat(4) { deck += RELIC }; repeat(4) { deck += QUENCH }; repeat(3) { deck += HARVEST }; repeat(2) { deck += SIEGE }; repeat(3) { deck += RIFT } }
        else { repeat(10) { deck += RALLY }; repeat(5) { deck += RELIC }; repeat(3) { deck += STONE }; repeat(3) { deck += HARVEST }; repeat(4) { deck += SIEGE } }
        deck.shuffle(rng)
        if (ashfall) { dragon = -1; eruptIn = ERUPT_EVERY[diff] + 2; planEruption(rng) }
        if (foggy) for (h in 0 until H) revealed[h] = h == lair || map.hexNbrs[lair].contains(h) || kind[h] == WATER
    }

    private fun connected(): Boolean {
        val land = (0 until H).filter { kind[it] != WATER && kind[it] != SEA }
        if (land.isEmpty()) return true
        val seen = BooleanArray(H); val q = ArrayDeque<Int>(); q += land[0]; seen[land[0]] = true
        while (q.isNotEmpty()) { val h = q.removeFirst(); for (n in map.hexNbrs[h]) if (!seen[n] && kind[n] != WATER && kind[n] != SEA) { seen[n] = true; q += n } }
        return land.all { seen[it] }
    }

    /** A tile's yield: 3 of one resource, 2 of two, or 1 of three. */
    private fun rollYields(h: Int, rng: Random) {
        for (r in 0..4) yields[h * 5 + r] = 0
        val main = rng.nextInt(5); art[h] = main
        val roll = rng.nextInt(100)
        when {
            roll < 15 -> yields[h * 5 + main] = 3
            roll < 35 -> { val o = (main + 1 + rng.nextInt(4)) % 5; yields[h * 5 + main] = 2; yields[h * 5 + o] = 2 }
            else -> {
                yields[h * 5 + main] = 1
                for (r in (0..4).filter { it != main }.shuffled(rng).take(2)) yields[h * 5 + r] = 1
            }
        }
    }

    // ---------------------------------------------------------------- queries

    fun yieldOf(h: Int) = IntArray(5) { yields[h * 5 + it] }
    fun total(p: Int) = res[p].sum()
    fun can(p: Int, cost: IntArray) = (0..4).all { res[p][it] >= cost[it] }
    fun tiles(p: Int) = (0 until H).count { owner[it] == p }
    fun vp(p: Int, withRelics: Boolean = true) = (0 until H).sumOf { if (owner[it] == p) level[it] else 0 } + (if (withRelics) spells[p][RELIC] else 0) + (if (slayer == p) 1 else 0)

    /** The Black Dragon Knight is home and ready for [p] this turn. */
    fun blackReady(p: Int) = slayer == p && !blackUsed && armies.none { it.black }

    /** Enemy tiles the Black Dragon Knight can strike from [from]. */
    fun blackStrikeTargets(p: Int, from: Int): List<Int> =
        if (owner[from] != p || !blackReady(p)) emptyList() else map.hexNbrs[from].filter { owner[it] == 1 - p }

    /** Tiles the Black Dragon Knight can march on from [from], flying over lakes. */
    fun blackMarchTargets(p: Int, from: Int): List<Int> =
        if (owner[from] != p || !blackReady(p)) emptyList()
        else (0 until H).filter { h -> h != from && ownable(h) && revealed[h] && owner[h] != p && path(from, h, fly = true) != null }

    /** Knights that leave [from] with the Black Dragon Knight: a full tower or castle garrison joins him. */
    fun escort(from: Int) = if (level[from] >= 2 && knights[from] >= CAPACITY[level[from]]) knights[from] else 0
    fun ownable(h: Int) = kind[h] == LAND || kind[h] == BARREN || kind[h] == ASH
    /** Knights can walk here: not lakes, and in Ashfall not the sea, lava or the volcano. */
    fun passable(h: Int) = kind[h] != WATER && !(ashfall && (kind[h] == SEA || kind[h] == LAVA || kind[h] == LAIR))
    /** What [h] pays its holder each turn for resource [r]: ash pays nothing while it cools, then 1 extra of its main resource. */
    fun pays(h: Int, r: Int) = when {
        kind[h] != ASH -> yields[h * 5 + r]
        heat[h] > 0 -> 0
        else -> yields[h * 5 + r] + (if (r == art[h]) 1 else 0)
    }
    /** The forecast flow that runs through [h] (-1: none). */
    fun flowAt(h: Int) = forecast.indexOfFirst { h in it }
    /** [h] lies in a forecast flow and the lava will bury it (land it has burned before is passed over). */
    fun inDanger(h: Int) = !burned[h] && flowAt(h) >= 0
    /** Open sea and lakes the lava could still turn into land. */
    fun growthLeft() = (0 until H).count { kind[it] == SEA || kind[it] == WATER }

    /** Shared side between two touching tiles (-1 if they don't touch). */
    fun side(a: Int, b: Int): Int {
        val shared = map.hexVerts[a].filter { it in map.hexVerts[b] }
        return if (shared.size == 2) map.edgeOf(shared[0], shared[1]) else -1
    }

    /** Claim price: a village's cost, plus extra for rich tiles, plus one card per three tiles you already hold. */
    fun claimCost(p: Int, h: Int): IntArray {
        val c = CLAIM_BASE.copyOf()
        val y = yieldOf(h)
        for (r in 0..4) if (y[r] >= 2) c[r] += y[r] - 1
        val extra = tiles(p) / 3
        for (k in 0 until extra) c[intArrayOf(0, 1, 2, 3, 4)[k % 5]]++
        return c
    }

    fun claimable(p: Int): List<Int> = (0 until H).filter { h ->
        owner[h] < 0 && ownable(h) && h != dragon && revealed[h] && map.hexNbrs[h].any { owner[it] == p }
    }

    fun settleSpots(p: Int): List<Int> = (0 until H).filter { h ->
        kind[h] == LAND && owner[h] < 0 && h != dragon && map.hexNbrs[h].none { owner[it] == 1 - p } && !map.hexNbrs[lair].contains(h)
    }

    fun upgradeCost(h: Int) = if (level[h] == 1) COST_TOWER else COST_CASTLE
    fun canUpgrade(p: Int, h: Int) = owner[h] == p && level[h] in 1..2 && can(p, upgradeCost(h))
    fun canHire(p: Int, h: Int) = owner[h] == p && knights[h] < CAPACITY[level[h]] && can(p, COST_KNIGHT)

    /** Enemy tiles a village's knight on [from] can strike now. */
    fun strikeTargets(p: Int, from: Int): List<Int> =
        if (owner[from] != p || level[from] != 1 || knights[from] < 1) emptyList()
        else map.hexNbrs[from].filter { owner[it] == 1 - p || (dragonAlive && it == dragon) }

    /** Tiles a full tower/castle garrison on [from] can march on: enemy tiles, or unclaimed land to take far away. */
    fun marchTargets(p: Int, from: Int): List<Int> =
        if (owner[from] != p || level[from] < 2 || knights[from] < CAPACITY[level[from]]) emptyList()
        else (0 until H).filter { h -> h != from && (ownable(h) || (dragonAlive && h == dragon)) && revealed[h] && owner[h] != p && reachable(from, h) }

    /** Sides where [p] may raise a wall: around their own tiles, on sides without one (lake sides too: the dragon flies). */
    fun wallSpots(p: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (h in 0 until H) if (owner[h] == p) for (n in map.hexNbrs[h]) { val e = side(h, n); if (e >= 0 && wall[e] < 0) out += h to n }
        return out
    }

    fun siegeTargets(p: Int) = (0 until map.EDGES).filter { wall[it] == 1 - p }

    fun canPlay(p: Int, k: Int): Boolean {
        if (phase != MAIN || turn != p || spellPlayed || k == RELIC) return false
        if (spells[p][k] - fresh[k] <= 0) return false
        return when (k) {
            RALLY -> (0 until H).any { owner[it] == p && knights[it] < CAPACITY[level[it]] }
            QUENCH -> if (ashfall) (0 until H).any { owner[it] == p && !quench[it] } else wallSpots(p).isNotEmpty()
            RIFT -> ashfall && forecast.isNotEmpty()
            SIEGE -> siegeTargets(p).isNotEmpty()
            else -> true
        }
    }

    private fun reachable(a: Int, b: Int) = path(a, b) != null

    /** A planned march: each step touches the last, never water (unless the Black Dragon Knight flies it), at most 24 steps, ending on a tile it may attack. */
    fun validRoute(p: Int, from: Int, route: List<Int>, black: Boolean = false): Boolean {
        val goals = if (black) blackMarchTargets(p, from) else marchTargets(p, from)
        if (route.isEmpty() || route.size > 24 || route.last() !in goals) return false
        var at = from
        for (h in route) { if (h !in map.hexNbrs[at] || (!passable(h) && !black)) return false; at = h }
        return true
    }

    /** Shortest path from [a] to [b], excluding [a]. Water blocks unless [fly]. */
    fun path(a: Int, b: Int, fly: Boolean = false): List<Int>? {
        if (a == b) return emptyList()
        val prev = IntArray(H) { -2 }; prev[a] = -1
        val q = ArrayDeque<Int>(); q += a
        while (q.isNotEmpty()) {
            val h = q.removeFirst()
            if (h == b) break
            for (n in map.hexNbrs[h]) if (prev[n] == -2 && (fly || passable(n))) { prev[n] = h; q += n }
        }
        if (prev[b] == -2) return null
        val out = ArrayList<Int>(); var c = b
        while (c != a) { out += c; c = prev[c] }
        return out.reversed()
    }

    fun leader(): Int {
        val a = vp(0, false); val b = vp(1, false)
        return when { a > b -> 0; b > a -> 1; tiles(0) > tiles(1) -> 0; tiles(1) > tiles(0) -> 1; else -> -1 }
    }

    // ---------------------------------------------------------------- actions

    fun legal(a: WAct): Boolean {
        val p = actor
        if (phase == OVER) return false
        if (phase == CHOICE) return a == WAct.Skip || (a is WAct.Claim && a.h == pending && can(p, claimCost(p, a.h)))
        if (phase == SETUP) return a is WAct.Settle && a.h in settleSpots(p)
        return when (a) {
            WAct.End -> freeWalls == 0
            WAct.Skip -> false
            WAct.BuySpell -> can(p, COST_SPELL) && deck.isNotEmpty() && freeWalls == 0
            is WAct.Settle -> false
            is WAct.Claim -> a.h in claimable(p) && can(p, claimCost(p, a.h))
            is WAct.Upgrade -> canUpgrade(p, a.h)
            is WAct.Hire -> canHire(p, a.h)
            is WAct.Strike -> a.to in (if (a.black) blackStrikeTargets(p, a.from) else strikeTargets(p, a.from))
            is WAct.March -> validRoute(p, a.from, a.route, a.black)
            is WAct.Wall -> owner[a.a] == p && side(a.a, a.b).let { it >= 0 && wall[it] < 0 } && (freeWalls > 0 || can(p, COST_WALL))
            is WAct.Trade -> a.give != a.get && res[p][a.give] >= 4
            is WAct.Spell -> canPlay(p, a.kind) && when (a.kind) {
                RALLY -> owner.getOrNull(a.a) == p && knights[a.a] < CAPACITY[level[a.a]]
                HARVEST -> a.a in 0..4 && a.b in 0..4
                QUENCH -> !ashfall || (owner.getOrNull(a.a) == p && !quench[a.a])
                RIFT -> a.a in forecast.indices
                SIEGE -> a.a in siegeTargets(p)
                else -> true
            }
        }
    }

    fun apply(a: WAct, rng: Random) {
        val p = actor
        val who = if (p == 0) "You" else "The rival"
        when (a) {
            is WAct.Settle -> {
                take(a.h, p); log += "$who settled ${tileName(a.h)}"
                setupStep++
                if (setupStep >= 2) { phase = MAIN; turn = 0; startTurn(rng) }
            }
            WAct.Skip -> { log += "$who left the ruins unclaimed"; pending = -1; phase = MAIN; checkWin() }
            is WAct.Claim -> {
                pay(p, claimCost(p, a.h)); take(a.h, p)
                log += "$who claimed ${tileName(a.h)}"
                if (phase == CHOICE) { pending = -1; phase = MAIN }
            }
            is WAct.Upgrade -> { pay(p, upgradeCost(a.h)); level[a.h]++; log += "$who raised a ${LEVEL_NAME[level[a.h]]}" }
            is WAct.Hire -> { pay(p, COST_KNIGHT); knights[a.h]++; log += "$who hired a knight" }
            is WAct.Strike -> {
                if (a.black) { blackUsed = true; log += "The Black Dragon Knight strikes for ${if (p == 0) "you" else "the rival"}!" }
                else { knights[a.from]--; log += "$who struck from a village" }
                attack(p, a.from, a.to, 1)
            }
            is WAct.March -> {
                if (a.black) {
                    val n = escort(a.from); knights[a.from] -= n; blackUsed = true
                    armies += Army(p, a.from, ArrayList(a.route), n + 1, black = true)
                    log += "The Black Dragon Knight rides out" + if (n > 0) " with $n knight${if (n > 1) "s" else ""}" else ""
                } else {
                    val n = knights[a.from]; knights[a.from] = 0
                    val hunt = dragonAlive && a.route.last() == dragon
                    armies += Army(p, a.from, ArrayList(a.route), n, hunt = hunt)
                    log += "$who sent $n knight${if (n > 1) "s" else ""} " + if (hunt) "to hunt the dragon" else "marching"
                }
            }
            is WAct.Wall -> {
                if (freeWalls > 0) freeWalls-- else pay(p, COST_WALL)
                wall[side(a.a, a.b)] = p
                log += "$who raised a wall"
                if (freeWalls > 0 && wallSpots(p).isEmpty()) freeWalls = 0
            }
            is WAct.Trade -> { res[p][a.give] -= 4; res[p][a.get]++ }
            WAct.BuySpell -> { pay(p, COST_SPELL); val s = deck.removeAt(deck.lastIndex); spells[p][s]++; fresh[s]++; log += if (p == 0) "You learned ${spellNames[s]}" else "The rival learned a spell" }
            is WAct.Spell -> {
                spells[p][a.kind]--; spellPlayed = true
                when (a.kind) {
                    RALLY -> { knights[a.a]++; log += "$who rallied a knight" }
                    STONE -> if (ashfall) { quench[a.a] = true; log += "$who quenched ${tileName(a.a)}: the next eruption can't bury it" }
                        else { freeWalls = 2; log += "$who cast Stonewright" }
                    RIFT -> { rift(a.a, rng); log += "$who cast Rift: a lava flow shifted" }
                    HARVEST -> { res[p][a.a]++; res[p][a.b]++; log += "$who cast Harvest Moon" }
                    SIEGE -> { wall[a.a] = -1; log += "$who broke a wall with Siege Fire" }
                }
            }
            WAct.End -> endTurn(rng)
        }
        checkWin()
    }

    private fun pay(p: Int, c: IntArray) { for (r in 0..4) res[p][r] -= c[r] }

    private fun take(h: Int, p: Int) {
        scar[h] = 0
        owner[h] = p; level[h] = 1; knights[h] = 0
        if (foggy) { revealed[h] = true; for (n in map.hexNbrs[h]) revealed[n] = true }
    }

    private fun lose(h: Int) { owner[h] = -1; level[h] = 0; knights[h] = 0; scar[h] = 0 }

    fun tileName(h: Int): String = when (kind[h]) {
        LAIR -> if (ashfall) "the volcano" else "the dragon's lair"; WATER -> "the lake"; BARREN -> "a barren waste"
        SEA -> "the sea"; LAVA -> "molten lava"; ASH -> if (heat[h] > 0) "the ashen waste" else "the renewed " + listOf("forest", "mountain", "fields", "meadow", "crystal spires")[art[h]]
        else -> "the " + listOf("forest", "mountain", "fields", "meadow", "crystal spires")[art[h]]
    }

    /**
     * [n] knights from [from] hit [to]. A wall on that side stops the whole attack (and breaks).
     * Each knight knocks a castle to a tower, a tower to a village (dying each time), or burns a village.
     */
    private fun attack(p: Int, from: Int, to: Int, n: Int) {
        val who = if (p == 0) "Your" else "The rival's"
        if (dragonAlive && to == dragon) { fightDragon(p, n); return }
        val e = side(from, to)
        if (e >= 0 && wall[e] == owner[to] && owner[to] >= 0) {
            wall[e] = -1
            log += "$who attack broke on a wall"
            return
        }
        if (owner[to] < 0) { offerClaim(p, to); return }
        var hits = n
        while (hits > 0 && owner[to] >= 0) {
            if (level[to] >= 2 && hp(to) >= 2) { level[to]--; hits--; knights[to] = minOf(knights[to], CAPACITY[level[to]]); log += "$who knights brought a ${LEVEL_NAME[level[to] + 1]} down to a ${LEVEL_NAME[level[to]]}" }
            else { val was = LEVEL_NAME[level[to]]; lose(to); hits--; log += "$who knights burned a $was" }
        }
        if (owner[to] < 0) offerClaim(p, to)
    }

    private fun offerClaim(p: Int, h: Int) {
        if (h == dragon || !ownable(h)) return
        if (can(p, claimCost(p, h))) { pending = h; phase = CHOICE; turn = p }
    }

    private fun startTurn(rng: Random) {
        val p = turn
        // income
        val got = IntArray(5)
        for (h in 0 until H) if (owner[h] == p) for (r in 0..4) got[r] += pays(h, r)
        for (r in 0..4) res[p][r] += got[r]
        if (got.sum() > 0) log += (if (p == 0) "Your lands paid " else "The rival's lands paid ") + (0..4).filter { got[it] > 0 }.joinToString(", ") { "${got[it]} ${Realm.RES[it]}" }
        // armies march one step along their planned routes; armies that meet fight
        for (army in armies.filter { it.owner == p }.toList()) {
            if (army !in armies) continue
            if (army.hunt) {
                // a hunting party follows the dragon wherever it flies, as far as land allows
                val way = if (dragonAlive) path(army.pos, dragon) else null
                if (way == null) { armies.remove(army); log += (if (p == 0) "Your" else "The rival's") + " hunters lost the dragon's trail and went home"; continue }
                if (way.isEmpty()) { armies.remove(army); fightDragon(p, army.size); continue }
                army.route.clear(); army.route += way
            }
            val next = army.route.firstOrNull() ?: run { armies.remove(army); null } ?: continue
            if (army.route.size == 1) {
                battle(army, next)
                if (army.size <= 0) continue
                armies.remove(army)
                log += if (p == 0) "Your army reached its target" else "The rival's army arrived!"
                attack(p, army.pos, next, army.size)
                if (phase == CHOICE) return
            } else {
                army.route.removeAt(0); army.pos = next
                battle(army, next)
            }
        }
    }

    /** Every enemy army on [h] fights [army]: knights cancel one for one, the bigger side keeps the difference. */
    private fun battle(army: Army, h: Int) {
        for (foe in armies.filter { it.owner != army.owner && it.pos == h }.toList()) {
            if (army.size <= 0) break
            val a = army.size; val b = foe.size
            army.size = a - minOf(a, b); foe.size = b - minOf(a, b)
            // the Black Dragon Knight never falls
            if (army.black && army.size < 1) army.size = 1
            if (foe.black && foe.size < 1) foe.size = 1
            val mine = if (army.owner == 0) a else b; val theirs = if (army.owner == 0) b else a
            log += "Armies clash: your $mine against the rival's $theirs. " + when {
                army.size > 0 -> (if (army.owner == 0) "You" else "The rival") + " march on with ${army.size}"
                foe.size > 0 -> (if (foe.owner == 0) "You" else "The rival") + " hold with ${foe.size}"
                else -> "None survive"
            }
            if (foe.size <= 0) armies.remove(foe)
        }
        if (army.size <= 0) armies.remove(army)
    }

    private fun endTurn(rng: Random) {
        fresh.fill(0); spellPlayed = false; freeWalls = 0; blackUsed = false
        turns++
        if (turns >= (if (ashfall) 300 else 200)) { winner = leader().let { if (it < 0) 2 else it }; endReason = 3; phase = OVER; return }
        if (ashfall) { volcanoTick(rng); if (phase == OVER) return }
        if (dragonAlive) {
            dragonTimer--
            if (dragonTimer <= 0) dragonMove()
        }
        turn = 1 - turn
        phase = MAIN
        checkWin()
        if (phase == MAIN) startTurn(rng)
    }

    /**
     * The leader's tile the dragon will strike next, and the tile it flies in from (it approaches along its shortest
     * flight from where it is now). Null if no one leads or the dragon is dead.
     */
    fun dragonPlan(): Pair<Int, Int>? {
        if (!dragonAlive) return null
        val lead = leader(); if (lead < 0) return null
        // its target: the leader's grandest tile, the nearest of those
        val routes = (0 until H).filter { owner[it] == lead }.mapNotNull { h -> path(dragon, h, fly = true)?.takeIf { it.isNotEmpty() }?.let { h to it } }
        val (target, route) = routes.maxByOrNull { (h, r) -> level[h] * 100 - r.size } ?: return null
        val from = if (route.size >= 2) route[route.size - 2] else dragon
        return from to target
    }

    private fun dragonMove() {
        dragonTimer = dragonEvery
        val plan = dragonPlan()
        if (plan == null) { log += "The dragon stirs, but no one leads"; return }
        val (from, next) = plan
        val o = owner[next]
        val whose = if (o == 0) "your" else "the rival's"
        log += "The dragon flies straight for $whose ${LEVEL_NAME[level[next]]}!"
        // walls don't stop it: it smashes the one in its way
        val e = side(from, next)
        if (e >= 0 && wall[e] >= 0) { wall[e] = -1; log += "It smashed the wall in its way" }
        scar[next]++
        if (hp(next) <= 0) {
            val was = LEVEL_NAME[level[next]]
            lose(next); log += "The dragon razed $whose $was! The land is lost"
        } else log += "The dragon wounded $whose ${LEVEL_NAME[level[next]]} (${hp(next)} of ${level[next]} health left)"
        for (a in armies) if (a.pos == next) a.size = if (a.black) 1 else 0
        armies.removeAll { it.size <= 0 }
        dragon = lair
        log += "The dragon returns to its lair"
    }

    // ---------------------------------------------------------------- Ashfall: the volcano

    private fun dist(a: Int, b: Int): Int {
        val dq = map.hexQ[a] - map.hexQ[b]; val dr = map.hexR[a] - map.hexR[b]
        return (kotlin.math.abs(dq) + kotlin.math.abs(dr) + kotlin.math.abs(dq + dr)) / 2
    }

    /** Neighbours of [h] further from the volcano: lava only runs downhill, out to sea. */
    private fun outward(h: Int) = map.hexNbrs[h].filter { dist(it, lair) > dist(h, lair) }

    /** A lava path starting on [start] that covers [len] fresh tiles at most, ending early where it meets the sea or a lake. */
    private fun walk(start: Int, len: Int, rng: Random): IntArray {
        val out = arrayListOf(start); var cur = start
        // only fresh ground counts toward a flow's length: it runs across old burns to reach new land
        fun freshCount() = out.count { !burned[it] }
        while (freshCount() < len && out.size < 24 && kind[cur] != SEA && kind[cur] != WATER) {
            val next = outward(cur); if (next.isEmpty()) break
            // the lava seeks fresh ground: it only runs over land it has burned when there's no other way
            val fresh = next.filter { !burned[it] }
            val pick = if (fresh.isNotEmpty()) fresh else next
            cur = pick[rng.nextInt(pick.size)]; out += cur
        }
        return out.toIntArray()
    }

    /** Flows per eruption and their length both grow as the volcano wakes. */
    fun flowCount() = minOf(1 + eruptions / 2, MAX_FLOWS[diff])
    fun flowLength() = minOf(1 + eruptions, 12)

    private fun planEruption(rng: Random) {
        forecast.clear()
        val starts = map.hexNbrs[lair].toList().shuffled(rng).sortedBy { if (burned[it]) 1 else 0 }
        for (k in 0 until flowCount()) forecast += walk(starts[k % starts.size], flowLength(), rng)
    }

    /** Rift: flow [i] leaves the volcano by the next free side and finds a new path. */
    private fun rift(i: Int, rng: Random) {
        val old = forecast[i]
        val sides = map.hexNbrs[lair]
        val used = forecast.map { it[0] }.toSet()
        val k = sides.indexOf(old[0])
        val start = (1..sides.size).map { sides[(k + it) % sides.size] }.firstOrNull { it !in used } ?: sides[(k + 1) % sides.size]
        forecast[i] = walk(start, old.size.coerceAtLeast(2), rng)
    }

    private fun volcanoTick(rng: Random) {
        for (h in 0 until H) when {
            kind[h] == LAVA -> if (--heat[h] <= 0) { kind[h] = ASH; heat[h] = RIPEN }
            // once renewed, the land is fresh ground again: the lava may bury it anew
            kind[h] == ASH && heat[h] > 0 -> if (--heat[h] == 0) burned[h] = false
        }
        if (--eruptIn > 0) return
        erupt(rng)
        eruptions++; eruptIn = ERUPT_EVERY[diff]
        quench.fill(false)
        planEruption(rng)
        // nowhere left for the island to grow: the game is locked and the leader wins
        if (growthLeft() == 0 && winner < 0) {
            winner = leader().let { if (it < 0) 2 else it }; endReason = 2; phase = OVER
            log += "The sea is filled and the island can grow no more. " + when (winner) { 0 -> "You lead, and win!"; 1 -> "The rival leads, and wins"; else -> "It's a draw" }
        }
    }

    private fun erupt(rng: Random) {
        log += "The volcano erupts!"
        for (path in forecast) {
            var route = path.toMutableList()
            var prev = lair; var i = 0
            while (i < route.size) {
                var h = route[i]
                val e = side(prev, h)
                if (e >= 0 && wall[e] >= 0) {
                    // a wall turns the lava aside, and breaks
                    val wo = wall[e]; wall[e] = -1
                    val alts = outward(prev).filter { it != h && it != lair }.sortedBy { (if (owner[it] == wo) 1 else 0) * 10 + rng.nextInt(10) }
                    log += "A wall turned the lava aside at ${tileName(h)}"
                    if (alts.isEmpty()) break
                    h = alts[0]
                    route = (route.subList(0, i) + walk(h, route.size - i, rng).toList()).toMutableList()
                }
                if (quench[h]) { log += "The lava hissed against the quenched ${tileName(h).removePrefix("the ")} and stopped"; break }
                when (kind[h]) {
                    SEA, WATER -> {
                        log += if (kind[h] == SEA) "Lava poured into the sea: new land rises" else "Lava boiled the lake away: new land rises"
                        kind[h] = LAVA; heat[h] = MOLTEN; burned[h] = true; rollYields(h, rng)
                        break
                    }
                    LAIR -> {}
                    else -> if (!burned[h]) {
                        // the lava never takes anyone's last land
                        val o = owner[h]
                        if (o >= 0 && tiles(o) == 1) { log += "The lava cooled at the edge of ${if (o == 0) "your" else "the rival's"} last land"; break }
                        bury(h, rng)
                    }
                }
                prev = h; i++
            }
        }
    }

    private fun bury(h: Int, rng: Random) {
        val o = owner[h]
        if (o >= 0) {
            // knights flee to a touching tile with room; buildings are lost
            var k = knights[h]
            for (n in map.hexNbrs[h]) if (k > 0 && owner[n] == o && kind[n] != LAVA) { val room = CAPACITY[level[n]] - knights[n]; val m = minOf(room, k); knights[n] += m; k -= m }
            log += (if (o == 0) "Lava buried your " else "Lava buried the rival's ") + LEVEL_NAME[level[h]] + " on ${tileName(h)}"
            lose(h)
        }
        for (a in armies) if (a.pos == h) a.size = 0
        armies.removeAll { it.size <= 0 }
        for (e in 0 until map.EDGES) if (wall[e] >= 0 && (map.edgeA[e] in map.hexVerts[h] && map.edgeB[e] in map.hexVerts[h])) wall[e] = -1
        if ((0..4).sumOf { yields[h * 5 + it] } == 0) rollYields(h, rng)
        kind[h] = LAVA; heat[h] = MOLTEN; burned[h] = true
    }

    private fun slain(p: Int, by: String) {
        dragonHp = 0; slayer = p; dragon = -1
        slainByKnights = by == "knights"
        log += if (p == 0) "Your $by slew the dragon! You win the Dragon Scale (+1 point) and the Black Dragon Knight"
            else "The rival's $by slew the dragon! It wins the Dragon Scale and the Black Dragon Knight"
    }

    /** [n] knights fall upon the dragon: each wounds it once, and none survive, even the one that lands the killing blow. */
    private fun fightDragon(p: Int, n: Int) {
        val who = if (p == 0) "Your" else "The rival's"
        dragonHp -= n
        if (dragonHp <= 0) { log += "$who knights died, but struck the killing blow"; slain(p, "knights"); return }
        log += "$who ${if (n == 1) "knight" else "$n knights"} fell to the dragon, wounding it ($dragonHp of $DRAGON_HP left)"
    }

    private fun checkWin() {
        if (phase == SETUP || winner >= 0) return
        for (p in listOf(turn, 1 - turn)) if (vp(p) >= target) { winner = p; endReason = 0; phase = OVER; return }
        // a player with no land left (after setup) loses
        for (p in 0..1) if (tiles(p) == 0 && armies.none { it.owner == p } && phase != CHOICE) { winner = 1 - p; endReason = 1; phase = OVER; return }
    }

    // ---------------------------------------------------------------- save / copy

    fun state(): IntArray {
        val o = ArrayList<Int>()
        fun put(a: IntArray) { o += a.size; a.forEach { o += it } }
        put(map.hexQ); put(map.hexR); o += diff + (if (ashfall) 100 else 0); o += target
        put(kind); put(art); put(yields); put(owner); put(level); put(knights)
        put(revealed.map { if (it) 1 else 0 }.toIntArray()); put(wall)
        put(armies.flatMap { listOf(it.owner, it.pos, it.size, it.route.size) + it.route }.toIntArray())
        put(res[0]); put(res[1]); put(spells[0]); put(spells[1]); put(fresh); put(deck.toIntArray())
        put(intArrayOf(if (spellPlayed) 1 else 0, freeWalls, lair, dragon, dragonTimer, turn, turns, phase, setupStep, pending, winner))
        put(intArrayOf(dragonHp, slayer, if (blackUsed) 1 else 0, armies.indexOfFirst { it.black }))
        put(armies.indices.filter { armies[it].hunt }.toIntArray())
        put(scar)
        put(heat); put(quench.map { if (it) 1 else 0 }.toIntArray())
        put(forecast.flatMap { listOf(it.size) + it.toList() }.toIntArray())
        put(intArrayOf(eruptIn, eruptions, endReason))
        put(burned.map { if (it) 1 else 0 }.toIntArray())
        return o.toIntArray()
    }

    fun load(s: IntArray) {
        var i = 0
        fun get(): IntArray { val n = s[i++]; val a = s.copyOfRange(i, i + n); i += n; return a }
        get(); get(); i += 2
        get().copyInto(kind); get().copyInto(art); get().copyInto(yields); get().copyInto(owner); get().copyInto(this.level); get().copyInto(knights)
        get().forEachIndexed { k, b -> revealed[k] = b == 1 }; get().copyInto(wall)
        armies.clear()
        get().let { a -> var k = 0; while (k < a.size) { val n = a[k + 3]; armies += Army(a[k], a[k + 1], ArrayList(a.copyOfRange(k + 4, k + 4 + n).toList()), a[k + 2]); k += 4 + n } }
        get().copyInto(res[0]); get().copyInto(res[1]); get().copyInto(spells[0]); get().copyInto(spells[1]); get().copyInto(fresh)
        deck.clear(); deck += get().toList()
        val m = get()
        spellPlayed = m[0] == 1; freeWalls = m[1]; lair = m[2]; dragon = m[3]; dragonTimer = m[4]; turn = m[5]; turns = m[6]
        phase = m[7]; setupStep = m[8]; pending = m[9]; winner = m[10]
        if (i < s.size) {
            val d = get()
            dragonHp = d[0]; slayer = d[1]; blackUsed = d[2] == 1
            armies.getOrNull(d[3])?.black = true
            if (i < s.size) get().forEach { armies.getOrNull(it)?.hunt = true }
            if (i < s.size) get().copyInto(scar)
        }
        if (i < s.size) {
            get().copyInto(heat); get().forEachIndexed { k, b -> quench[k] = b == 1 }
            forecast.clear(); get().let { a -> var k = 0; while (k < a.size) { val n = a[k]; forecast += a.copyOfRange(k + 1, k + 1 + n); k += 1 + n } }
            val v = get(); eruptIn = v[0]; eruptions = v[1]; endReason = v[2]
        }
        if (i < s.size) get().forEachIndexed { k, b -> burned[k] = b == 1 }
        else for (h in 0 until H) if (kind[h] == LAVA || kind[h] == ASH) burned[h] = true
    }

    fun copy(): Wisteria = fromState(state())
}

/** The Wisteria computer player: one action at a time from the position it can see. */
object WisteriaAi {
    class Level(val noise: Double, val lazy: Double, val aggression: Double, val walls: Boolean, val trade: Double)

    fun level(d: Difficulty) = when (d) {
        Difficulty.EASY -> Level(3.0, 0.3, 0.25, false, 0.2)
        Difficulty.MEDIUM -> Level(2.0, 0.15, 0.45, false, 0.5)
        Difficulty.HARD -> Level(1.2, 0.06, 0.65, true, 0.8)
        Difficulty.EXPERT -> Level(0.7, 0.02, 0.8, true, 0.9)
        Difficulty.MASTER -> Level(0.3, 0.0, 0.9, true, 1.0)
        Difficulty.EXTREME -> Level(0.0, 0.0, 1.0, true, 1.0)
    }

    private val W = doubleArrayOf(1.0, 1.15, 1.1, 1.0, 1.0)

    fun tileValue(g: Wisteria, h: Int): Double {
        if (!g.revealed[h]) return 2.2
        if (g.ashfall && g.kind[h] == Wisteria.ASH) {
            var v = 0.0; for (r in 0..4) v += g.yields[h * 5 + r] * W[r]
            return v * (if (g.heat[h] > 0) 1.3 else 2.0) * danger(g, h)
        }
        if (g.kind[h] != Wisteria.LAND) return 0.4
        var v = 0.0; for (r in 0..4) v += g.yields[h * 5 + r] * W[r]
        return v * danger(g, h)
    }

    /** Ashfall: land in a forecast lava flow is worth little. */
    private fun danger(g: Wisteria, h: Int) = if (g.ashfall && g.inDanger(h)) 0.25 else 1.0

    /** How much flow [i] would hurt [p] more than their rival. */
    private fun harm(g: Wisteria, p: Int, i: Int) = g.forecast[i].sumOf { h -> when (g.owner[h]) { p -> g.level[h] + 1; 1 - p -> -(g.level[h] + 1); else -> 0 } }

    private fun spotValue(g: Wisteria, p: Int, h: Int): Double {
        var v = tileValue(g, h) * 2
        v += g.map.hexNbrs[h].count { g.owner[it] < 0 && g.ownable(it) } * 0.4
        v -= if (h == g.dragon || g.map.hexNbrs[g.lair].contains(h)) 1.5 else 0.0
        v -= g.map.hexNbrs[h].count { g.owner[it] == 1 - p } * 0.5
        return v
    }

    private fun missing(g: Wisteria, p: Int, cost: IntArray) = IntArray(5) { maxOf(0, cost[it] - g.res[p][it]) }

    fun next(g: Wisteria, lvl: Level, rng: Random): WAct {
        val p = g.actor
        fun noise() = (rng.nextDouble() - 0.5) * lvl.noise
        when (g.phase) {
            Wisteria.SETUP -> return WAct.Settle(g.settleSpots(p).maxBy { h -> spotValue(g, p, h) + g.map.hexNbrs[h].sumOf { tileValue(g, it) } * 0.3 + noise() })
            Wisteria.CHOICE -> return if (g.can(p, g.claimCost(p, g.pending))) WAct.Claim(g.pending) else WAct.Skip
            Wisteria.OVER -> return WAct.End
        }
        if (g.freeWalls > 0) {
            val spot = g.wallSpots(p).maxByOrNull { (a, b) -> (if (g.owner[b] == 1 - p) 3.0 else 0.0) + g.level[a] }
            if (spot != null) return WAct.Wall(spot.first, spot.second)
        }
        val o = 1 - p
        val near = g.vp(p) >= g.target - 1
        // 1. finish the game with an upgrade
        if (near) (0 until g.H).firstOrNull { g.canUpgrade(p, it) }?.let { return WAct.Upgrade(it) }
        // 2. village strikes on touching enemy villages without a wall
        if (rng.nextDouble() < lvl.aggression) for (h in 0 until g.H) for (t in g.strikeTargets(p, h)) {
            val e = g.side(h, t)
            if (g.wall[e] == o) continue
            if (g.level[t] == 1) return WAct.Strike(h, t)
        }
        // 2b. the Black Dragon Knight: strike an unwalled neighbour, or fly out to a far target
        if (g.blackReady(p)) {
            for (h in 0 until g.H) for (t in g.blackStrikeTargets(p, h)) if (g.wall[g.side(h, t)] != o) return WAct.Strike(h, t, black = true)
            val from = (0 until g.H).filter { g.owner[it] == p }.maxByOrNull { g.escort(it) }
            if (from != null) {
                val n = g.escort(from) + 1
                val best = g.blackMarchTargets(p, from).maxByOrNull { t ->
                    val d = g.path(from, t, fly = true)?.size ?: 99
                    (if (g.owner[t] == o) (if (g.level[t] <= n) 6.0 + g.level[t] * 2 else 2.0) else spotValue(g, p, t)) - d * 0.6 + noise()
                }
                if (best != null) g.path(from, best, fly = true)?.let { return WAct.March(from, it, black = true) }
            }
        }
        // 3. full garrisons march on the weakest valuable target
        for (h in 0 until g.H) {
            val ts = g.marchTargets(p, h).filter { it != g.dragon }; if (ts.isEmpty()) continue
            val n = g.knights[h]
            val best = ts.maxByOrNull { t ->
                val d = g.path(h, t)?.size ?: 99
                val gain = if (g.owner[t] == o) (if (g.level[t] <= n) 6.0 + g.level[t] * 2 else 1.0) else spotValue(g, p, t)
                gain - d * 0.8 + noise()
            }
            if (best != null && rng.nextDouble() < lvl.aggression) g.path(h, best)?.let { return WAct.March(h, it) }
        }
        // 3b. Ashfall: save land from the coming eruption
        if (g.ashfall && g.eruptIn <= 4) {
            val threatened = (0 until g.H).filter { g.owner[it] == p && g.inDanger(it) && !g.quench[it] }.sortedByDescending { g.level[it] * 3 + tileValue(g, it) * 0 }
            if (threatened.isNotEmpty()) {
                val worst = threatened[0]
                if (g.level[worst] >= 2 && g.canPlay(p, Wisteria.QUENCH)) return WAct.Spell(Wisteria.QUENCH, worst)
                if (g.canPlay(p, Wisteria.RIFT)) g.forecast.indices.maxByOrNull { harm(g, p, it) }?.takeIf { harm(g, p, it) > 0 }?.let { return WAct.Spell(Wisteria.RIFT, it) }
                if (lvl.walls && g.can(p, Wisteria.COST_WALL)) for (h in threatened) {
                    val path = g.forecast[g.flowAt(h)]; val i = path.indexOf(h)
                    val prev = if (i <= 0) g.lair else path[i - 1]
                    val e = g.side(h, prev)
                    if (e >= 0 && g.wall[e] < 0 && (g.level[h] >= 2 || g.total(p) >= 6)) return WAct.Wall(h, prev)
                }
                if (g.canPlay(p, Wisteria.QUENCH)) return WAct.Spell(Wisteria.QUENCH, worst)
            }
        }
        // 4. claim land
        val claims = g.claimable(p)
        val affordable = claims.filter { g.can(p, g.claimCost(p, it)) }
        if (affordable.isNotEmpty()) return WAct.Claim(affordable.maxBy { spotValue(g, p, it) + noise() })
        if (rng.nextDouble() < lvl.lazy) return WAct.End
        // 5. towers on the richest or most exposed tiles, castles once rich
        val up = (0 until g.H).filter { g.canUpgrade(p, it) && !(g.ashfall && g.inDanger(it)) }.maxByOrNull { h ->
            tileValue(g, h) + g.map.hexNbrs[h].count { g.owner[it] == o } * 1.5 + (if (g.leader() == p) 1.0 else 0.0) + noise()
        }
        if (up != null && (claims.isEmpty() || g.res[p][1] >= 4 || near)) return WAct.Upgrade(up)
        // 6. walls where the rival can strike
        if (lvl.walls && g.can(p, Wisteria.COST_WALL) && g.total(p) >= 6) {
            val spot = g.wallSpots(p).firstOrNull { (a, b) -> g.owner[b] == o && g.knights[b] > 0 && g.level[b] == 1 }
            if (spot != null) return WAct.Wall(spot.first, spot.second)
        }
        // 7. knights: villages touching enemy villages, and tower garrisons
        if (g.can(p, Wisteria.COST_KNIGHT) && rng.nextDouble() < lvl.aggression) {
            val h = (0 until g.H).filter { g.canHire(p, it) }.maxByOrNull { h ->
                (if (g.level[h] == 1 && g.map.hexNbrs[h].any { g.owner[it] == o && g.level[it] == 1 }) 5.0 else 0.0) +
                    (if (g.level[h] >= 2) 3.0 + g.knights[h] else 0.0) + noise()
            }
            if (h != null && (g.level[h] >= 2 || g.map.hexNbrs[h].any { g.owner[it] == o })) return WAct.Hire(h)
        }
        // 8. spells
        if (g.canPlay(p, Wisteria.RALLY)) (0 until g.H).firstOrNull { g.owner[it] == p && g.knights[it] < Wisteria.CAPACITY[g.level[it]] && g.level[it] >= 2 }?.let { return WAct.Spell(Wisteria.RALLY, it) }
        if (g.canPlay(p, Wisteria.SIEGE)) g.siegeTargets(p).firstOrNull()?.let { return WAct.Spell(Wisteria.SIEGE, it) }
        if (!g.ashfall && g.canPlay(p, Wisteria.STONE) && lvl.walls) return WAct.Spell(Wisteria.STONE)
        // 9. trade toward the best claim
        val goal = claims.minByOrNull { missing(g, p, g.claimCost(p, it)).sum() }?.let { g.claimCost(p, it) }
            ?: (0 until g.H).firstOrNull { g.owner[it] == p && g.level[it] in 1..2 }?.let { g.upgradeCost(it) }
        if (goal != null) {
            val miss = missing(g, p, goal)
            if (g.canPlay(p, Wisteria.HARVEST) && miss.sum() in 1..2) {
                val l = (0..4).flatMap { r -> List(miss[r]) { r } } + listOf(1, 2)
                return WAct.Spell(Wisteria.HARVEST, l[0], l[1])
            }
            val spare = IntArray(5) { r -> maxOf(0, g.res[p][r] - goal[r]) / 4 }
            if (miss.sum() > 0 && spare.sum() >= miss.sum() && rng.nextDouble() < lvl.trade) {
                val give = (0..4).filter { spare[it] > 0 }.maxBy { g.res[p][it] - goal[it] }
                return WAct.Trade(give, (0..4).first { miss[it] > 0 })
            }
        }
        if (g.can(p, Wisteria.COST_SPELL) && g.deck.isNotEmpty() && g.total(p) >= 9) return WAct.BuySpell
        return WAct.End
    }
}
