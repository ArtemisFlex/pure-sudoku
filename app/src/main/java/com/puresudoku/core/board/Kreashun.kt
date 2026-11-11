package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.math.roundToInt
import kotlin.random.Random

/** One move in Kreashun. */
sealed class KAct {
    object Roll : KAct()
    object End : KAct()
    /** A village on corner [v] (in setup, or joined to your walls later). */
    data class Village(val v: Int) : KAct()
    data class Tower(val v: Int) : KAct()
    /** A wall on board edge [e]. Closing the last open side of a tile captures it. */
    data class Wall(val e: Int) : KAct()
    data class Castle(val h: Int) : KAct()
    data class Hire(val h: Int) : KAct()
    /** A knight on your tile [from] attacks the touching rival tile [to]. */
    data class Strike(val from: Int, val to: Int) : KAct()
    data class Trade(val give: Int, val get: Int) : KAct()
}

/**
 * Kreashun: Eiseria's dice and corner villages meet Wisteria's walls and knights. No dragon.
 * Wall every side of a tile to capture it: whoever builds the closing wall takes it.
 * Captured tiles pay their corner villages double, can raise a castle (3 hit points), and hire knights
 * that strike touching rival tiles. A hit breaks the wall on that side first; broken walls are rubble and
 * can't be rebuilt. Then a hit takes a tile without a castle, or knocks a point off a castle.
 * Points: one per tile held, plus one per rival castle felled.
 * You lose if your points drop back to 0 after you've scored.
 */
class Kreashun(val map: RealmGrid, val diff: Int, val target: Int) {
    companion object {
        const val TIMBER = 0; const val ORE = 1; const val GRAIN = 2; const val BEASTS = 3; const val MANA = 4; const val WASTE = 5

        const val SETUP = 0; const val ROLL = 1; const val MAIN = 2; const val OVER = 3

        val COST_VILLAGE = intArrayOf(1, 0, 1, 1, 1)
        val COST_TOWER = intArrayOf(0, 3, 2, 0, 0)
        val COST_WALL = intArrayOf(1, 0, 0, 0, 1)
        val COST_CASTLE = intArrayOf(2, 3, 3, 0, 2)
        /** Raising a castle again on a tile taken from a felled castle: half price. */
        val COST_CASTLE_HALF = intArrayOf(1, 2, 2, 0, 1)
        val COST_KNIGHT = intArrayOf(0, 1, 1, 1, 0)

        const val CASTLE_HP = 3
        const val MAX_VILLAGES = 5; const val MAX_TOWERS = 4
        /** Knights a tile can hold: a bare captured tile, and a castle. */
        const val CAP_TILE = 2; const val CAP_CASTLE = 3

        private val WASTES = intArrayOf(1, 1, 2, 2, 3, 3)
        private val SETUP_ORDER = intArrayOf(0, 1, 1, 0)

        fun newGame(d: Difficulty, rng: Random): Kreashun {
            val map = RealmMap.forLevel(d.ordinal)
            val g = Kreashun(map, d.ordinal, maxOf(5, (map.HEXES * 0.34f).roundToInt()))
            g.deal(rng)
            return g
        }

        fun fromState(s: IntArray): Kreashun {
            var i = 0
            fun get(): IntArray { val n = s[i++]; val a = s.copyOfRange(i, i + n); i += n; return a }
            val q = get(); val r = get(); val lv = s[i++]; val tg = s[i]
            return Kreashun(RealmMap.fromCoords(q, r), lv, tg).also { it.load(s) }
        }
    }

    val H = map.HEXES
    val terrain = IntArray(H)
    val number = IntArray(H)

    val vOwner = IntArray(map.VERTS) { -1 }
    val vLevel = IntArray(map.VERTS)
    /** Wall owner per edge (-1: none). */
    val wall = IntArray(map.EDGES) { -1 }
    /** Edges where a wall was broken: rubble, never rebuilt, but still closes the side. */
    val rubble = BooleanArray(map.EDGES)

    val owner = IntArray(H) { -1 }
    /** Castle hit points left on a tile (0: no castle). */
    val castle = IntArray(H)
    /** Tile taken from a felled castle: its next castle costs half. */
    val halfCastle = BooleanArray(H)
    val knights = IntArray(H)

    val res = Array(2) { IntArray(5) }
    val felled = IntArray(2)
    /** Has each player scored at least once? Dropping back to 0 after that loses. */
    val scored = BooleanArray(2)

    var turn = 0
    var turns = 0
    var phase = SETUP
    var setupStep = 0
    var setupVillage = -1
    var lastRoll = 0
    var lastDie = intArrayOf(0, 0)
    var winner = -1
    val log = ArrayList<String>()

    val actor: Int get() = if (phase == SETUP) SETUP_ORDER[setupStep.coerceAtMost(3)] else turn

    // ---------------------------------------------------------------- setup

    private fun deal(rng: Random) {
        val n = H
        val wastes = WASTES[diff.coerceIn(0, 5)]
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
    }

    // ---------------------------------------------------------------- queries

    fun total(p: Int) = res[p].sum()
    fun can(p: Int, cost: IntArray) = (0..4).all { res[p][it] >= cost[it] }
    fun count(p: Int, level: Int) = (0 until map.VERTS).count { vOwner[it] == p && vLevel[it] == level }
    fun tiles(p: Int) = (0 until H).count { owner[it] == p }
    fun vp(p: Int) = tiles(p) + felled[p]

    /** The six sides of tile [h], as board edges. */
    fun sides(h: Int): IntArray { val vs = map.hexVerts[h]; return IntArray(6) { k -> map.edgeOf(vs[k], vs[(k + 1) % 6]) } }
    fun closed(e: Int) = wall[e] >= 0 || rubble[e]
    fun closedSides(h: Int) = sides(h).count { closed(it) }
    fun openSides(h: Int) = sides(h).filter { !closed(it) }

    /** Shared side between two touching tiles (-1 if they don't touch). */
    fun side(a: Int, b: Int): Int {
        val shared = map.hexVerts[a].filter { it in map.hexVerts[b] }
        return if (shared.size == 2) map.edgeOf(shared[0], shared[1]) else -1
    }

    private fun spacingOk(v: Int) = vOwner[v] < 0 && map.vertNbrs[v].all { vOwner[it] < 0 }

    fun villageSpots(p: Int): List<Int> {
        if (count(p, 1) >= MAX_VILLAGES) return emptyList()
        return (0 until map.VERTS).filter { v -> spacingOk(v) && (phase == SETUP || map.vertEdges[v].any { wall[it] == p }) }
    }

    fun towerSpots(p: Int): List<Int> = if (count(p, 2) >= MAX_TOWERS) emptyList() else (0 until map.VERTS).filter { vOwner[it] == p && vLevel[it] == 1 }

    /** Edges where [p] may build a wall: free sides joined to their villages, walls or tiles. */
    fun wallSpots(p: Int): List<Int> {
        if (phase == SETUP) return if (setupVillage >= 0) map.vertEdges[setupVillage].filter { !closed(it) } else emptyList()
        return (0 until map.EDGES).filter { e -> !closed(e) && wallReaches(p, e) }
    }

    private fun wallReaches(p: Int, e: Int): Boolean {
        for (v in intArrayOf(map.edgeA[e], map.edgeB[e])) {
            if (vOwner[v] == p) return true
            if (vOwner[v] < 0 && map.vertEdges[v].any { it != e && wall[it] == p }) return true
        }
        return map.vertHexes[map.edgeA[e]].intersect(map.vertHexes[map.edgeB[e]].toSet()).any { owner[it] == p }
    }

    /** Tiles that a wall on [e] would capture right now. */
    fun wouldCapture(e: Int): List<Int> =
        map.vertHexes[map.edgeA[e]].intersect(map.vertHexes[map.edgeB[e]].toSet()).filter { h -> owner[h] < 0 && openSides(h) == listOf(e) }

    fun castleCost(h: Int) = if (halfCastle[h]) COST_CASTLE_HALF else COST_CASTLE
    fun canCastle(p: Int, h: Int) = owner[h] == p && castle[h] == 0 && can(p, castleCost(h))
    fun capacity(h: Int) = if (castle[h] > 0) CAP_CASTLE else CAP_TILE
    fun canHire(p: Int, h: Int) = owner[h] == p && knights[h] < capacity(h) && can(p, COST_KNIGHT)

    fun strikeTargets(p: Int, from: Int): List<Int> =
        if (owner[from] != p || knights[from] < 1) emptyList() else map.hexNbrs[from].filter { owner[it] == 1 - p }

    // ---------------------------------------------------------------- actions

    fun legal(a: KAct): Boolean {
        val p = actor
        if (phase == OVER) return false
        if (phase == SETUP) return when (a) {
            is KAct.Village -> setupVillage < 0 && a.v in villageSpots(p)
            is KAct.Wall -> setupVillage >= 0 && a.e in wallSpots(p)
            else -> false
        }
        if (phase == ROLL) return a == KAct.Roll
        return when (a) {
            KAct.Roll -> false
            KAct.End -> true
            is KAct.Village -> can(p, COST_VILLAGE) && a.v in villageSpots(p)
            is KAct.Tower -> can(p, COST_TOWER) && a.v in towerSpots(p)
            is KAct.Wall -> can(p, COST_WALL) && a.e in wallSpots(p)
            is KAct.Castle -> canCastle(p, a.h)
            is KAct.Hire -> canHire(p, a.h)
            is KAct.Strike -> a.to in strikeTargets(p, a.from)
            is KAct.Trade -> a.give != a.get && a.give in 0..4 && a.get in 0..4 && res[p][a.give] >= 4
        }
    }

    fun apply(a: KAct, rng: Random) {
        val p = actor
        val who = if (p == 0) "You" else "The rival"
        when (a) {
            is KAct.Village -> {
                vOwner[a.v] = p; vLevel[a.v] = 1
                if (phase == SETUP) {
                    setupVillage = a.v
                    if (setupStep >= 2) for (h in map.vertHexes[a.v]) if (terrain[h] != WASTE) res[p][terrain[h]]++
                } else pay(p, COST_VILLAGE)
                log += "$who built a village"
            }
            is KAct.Wall -> {
                wall[a.e] = p
                if (phase == SETUP) {
                    setupVillage = -1; setupStep++
                    if (setupStep >= 4) { phase = ROLL; turn = 0 }
                } else {
                    pay(p, COST_WALL)
                    val got = map.vertHexes[map.edgeA[a.e]].intersect(map.vertHexes[map.edgeB[a.e]].toSet()).filter { owner[it] < 0 && closedSides(it) == 6 }
                    for (h in got) { owner[h] = p; log += "$who walled in ${tileName(h)} and captured it!" }
                    if (got.isEmpty()) log += "$who raised a wall"
                }
            }
            is KAct.Tower -> { pay(p, COST_TOWER); vLevel[a.v] = 2; log += "$who raised a tower" }
            is KAct.Castle -> { pay(p, castleCost(a.h)); castle[a.h] = CASTLE_HP; halfCastle[a.h] = false; log += "$who built a castle" }
            is KAct.Hire -> { pay(p, COST_KNIGHT); knights[a.h]++; log += "$who hired a knight" }
            is KAct.Strike -> strike(p, a.from, a.to)
            is KAct.Trade -> { res[p][a.give] -= 4; res[p][a.get]++ }
            KAct.Roll -> roll(rng)
            KAct.End -> endTurn()
        }
        checkWin()
    }

    private fun pay(p: Int, c: IntArray) { for (r in 0..4) res[p][r] -= c[r] }

    private fun strike(p: Int, from: Int, to: Int) {
        knights[from]--
        val who = if (p == 0) "Your" else "The rival's"
        val e = side(from, to)
        if (e >= 0 && wall[e] >= 0) {
            wall[e] = -1; rubble[e] = true
            log += "$who knight broke the wall into ${tileName(to)}"
            return
        }
        if (castle[to] > 0) {
            castle[to]--
            if (castle[to] > 0) { log += "$who knight hit the castle on ${tileName(to)} (${castle[to]} left)"; return }
            felled[p]++
            log += "$who knights felled a castle! +1 point"
            capture(p, to, fromCastle = true)
        } else capture(p, to, fromCastle = false)
    }

    /** [p] takes tile [h] by force: its walls, corner buildings and knights change sides. */
    private fun capture(p: Int, h: Int, fromCastle: Boolean) {
        val old = owner[h]
        owner[h] = p; castle[h] = 0; halfCastle[h] = fromCastle
        for (e in sides(h)) if (e >= 0 && wall[e] == old) wall[e] = p
        for (v in map.hexVerts[h]) if (vOwner[v] == old) vOwner[v] = p
        log += (if (p == 0) "You" else "The rival") + " captured ${tileName(h)}" + if (fromCastle) " (a new castle costs half)" else ""
    }

    private fun roll(rng: Random) {
        lastDie = intArrayOf(rng.nextInt(1, 7), rng.nextInt(1, 7))
        val n = lastDie[0] + lastDie[1]
        lastRoll = n
        phase = MAIN
        if (n == 7) { log += "A 7: nothing pays out"; return }
        for (p in 0..1) {
            val got = production(p, n)
            for (r in 0..4) res[p][r] += got[r]
            if (got.sum() > 0) log += (if (p == 0) "You got " else "The rival got ") + (0..4).filter { got[it] > 0 }.joinToString(", ") { "${got[it]} ${Realm.RES[it]}" }
        }
    }

    /** What [p] collects on roll [n]: each corner building pays its level, doubled on tiles [p] holds. */
    fun production(p: Int, n: Int): IntArray {
        val got = IntArray(5)
        for (h in 0 until H) {
            if (number[h] != n || terrain[h] == WASTE) continue
            val mult = if (owner[h] == p) 2 else 1
            for (v in map.hexVerts[h]) if (vOwner[v] == p) got[terrain[h]] += vLevel[v] * mult
        }
        return got
    }

    private fun endTurn() {
        turns++
        if (turns >= 400) { winner = when { vp(0) > vp(1) -> 0; vp(1) > vp(0) -> 1; else -> 2 }; phase = OVER; return }
        turn = 1 - turn
        phase = ROLL
    }

    private fun checkWin() {
        if (phase == SETUP || winner >= 0) return
        for (p in 0..1) if (vp(p) > 0) scored[p] = true
        for (p in listOf(turn, 1 - turn)) if (vp(p) >= target) { winner = p; phase = OVER; return }
        for (p in 0..1) if (scored[p] && vp(p) == 0) { winner = 1 - p; phase = OVER; return }
    }

    fun tileName(h: Int): String = if (terrain[h] == WASTE) "a wasteland" else "the " + listOf("forest", "mountain", "fields", "meadow", "crystal spires")[terrain[h]]

    // ---------------------------------------------------------------- save / copy

    fun state(): IntArray {
        val o = ArrayList<Int>()
        fun put(a: IntArray) { o += a.size; a.forEach { o += it } }
        put(map.hexQ); put(map.hexR); o += diff; o += target
        put(terrain); put(number); put(vOwner); put(vLevel); put(wall); put(rubble.map { if (it) 1 else 0 }.toIntArray())
        put(owner); put(castle); put(halfCastle.map { if (it) 1 else 0 }.toIntArray()); put(knights)
        put(res[0]); put(res[1]); put(felled); put(scored.map { if (it) 1 else 0 }.toIntArray())
        put(intArrayOf(turn, turns, phase, setupStep, setupVillage, lastRoll, lastDie[0], lastDie[1], winner))
        return o.toIntArray()
    }

    fun load(s: IntArray) {
        var i = 0
        fun get(): IntArray { val n = s[i++]; val a = s.copyOfRange(i, i + n); i += n; return a }
        get(); get(); i += 2
        get().copyInto(terrain); get().copyInto(number); get().copyInto(vOwner); get().copyInto(vLevel); get().copyInto(wall)
        get().forEachIndexed { k, b -> rubble[k] = b == 1 }
        get().copyInto(owner); get().copyInto(castle); get().forEachIndexed { k, b -> halfCastle[k] = b == 1 }; get().copyInto(knights)
        get().copyInto(res[0]); get().copyInto(res[1]); get().copyInto(felled); get().forEachIndexed { k, b -> scored[k] = b == 1 }
        val m = get()
        turn = m[0]; turns = m[1]; phase = m[2]; setupStep = m[3]; setupVillage = m[4]; lastRoll = m[5]; lastDie = intArrayOf(m[6], m[7]); winner = m[8]
    }

    fun copy(): Kreashun = fromState(state())
}

/** The Kreashun computer player: one action at a time. */
object KreashunAi {
    class Level(val noise: Double, val aggression: Double, val careful: Boolean, val trade: Double)

    fun level(d: Difficulty) = when (d) {
        Difficulty.EASY -> Level(3.0, 0.3, false, 0.3)
        Difficulty.MEDIUM -> Level(2.0, 0.5, false, 0.5)
        Difficulty.HARD -> Level(1.2, 0.7, true, 0.8)
        Difficulty.EXPERT -> Level(0.7, 0.8, true, 0.9)
        Difficulty.MASTER -> Level(0.3, 0.9, true, 1.0)
        Difficulty.EXTREME -> Level(0.0, 1.0, true, 1.0)
    }

    private fun pips(g: Kreashun, h: Int) = if (g.terrain[h] == Kreashun.WASTE) 0 else Realm.pips(g.number[h])

    private fun spotValue(g: Kreashun, v: Int): Double {
        val hs = g.map.vertHexes[v]
        val kinds = hs.filter { g.terrain[it] != Kreashun.WASTE }.map { g.terrain[it] }.toSet().size
        return hs.sumOf { pips(g, it).toDouble() } + kinds * 0.8
    }

    /** How much holding tile [h] is worth to [p]: a point, plus doubled income for their corners there. */
    private fun tileWorth(g: Kreashun, p: Int, h: Int) =
        3.0 + pips(g, h) * g.map.hexVerts[h].sumOf { v -> if (g.vOwner[v] == p) g.vLevel[v] else 0 } * 0.3

    private fun missing(g: Kreashun, p: Int, cost: IntArray) = IntArray(5) { maxOf(0, cost[it] - g.res[p][it]) }

    private fun wallScore(g: Kreashun, p: Int, e: Int, careful: Boolean): Double {
        val o = 1 - p
        var v = 0.0
        val hs = g.map.vertHexes[g.map.edgeA[e]].intersect(g.map.vertHexes[g.map.edgeB[e]].toSet())
        val rivalCan = if (careful) g.wallSpots(o).toSet() else emptySet()
        for (h in hs) {
            if (g.owner[h] >= 0) continue
            val after = g.closedSides(h) + 1
            v += after * after * 0.25 * (tileWorth(g, p, h) / 3.0)
            // don't leave a tile one wall from closed where the rival can close it
            if (after == 5 && careful) {
                val last = g.openSides(h).firstOrNull { it != e }
                if (last != null && last in rivalCan) v -= 8.0
            }
        }
        return v
    }

    fun next(g: Kreashun, lvl: Level, rng: Random): KAct {
        val p = g.actor
        val o = 1 - p
        fun noise() = (rng.nextDouble() - 0.5) * lvl.noise
        when (g.phase) {
            Kreashun.SETUP -> return if (g.setupVillage < 0) KAct.Village(g.villageSpots(p).maxBy { spotValue(g, it) + noise() })
                else KAct.Wall(g.wallSpots(p).maxBy { e -> wallScore(g, p, e, false) + noise() })
            Kreashun.ROLL -> return KAct.Roll
            Kreashun.OVER -> return KAct.End
        }
        val walls = if (g.can(p, Kreashun.COST_WALL)) g.wallSpots(p) else emptyList()
        // 1. close a tile
        walls.filter { g.wouldCapture(it).isNotEmpty() }.maxByOrNull { e -> g.wouldCapture(e).sumOf { tileWorth(g, p, it) } }?.let { return KAct.Wall(it) }
        // 2. knights strike: finish castles, take bare tiles, break walls in the way
        if (rng.nextDouble() < lvl.aggression) {
            var best: KAct.Strike? = null; var bestV = 0.5
            for (h in 0 until g.H) for (t in g.strikeTargets(p, h)) {
                val e = g.side(h, t)
                val v = when {
                    g.wall[e] >= 0 -> 1.0 + (if (g.knights[h] >= 2) 1.5 else 0.0)
                    g.castle[t] == 1 -> 9.0
                    g.castle[t] > 0 -> 3.0
                    else -> 6.0 + tileWorth(g, p, t)
                } + noise()
                if (v > bestV) { bestV = v; best = KAct.Strike(h, t) }
            }
            best?.let { return it }
        }
        // 3. castles on tiles that touch the rival
        (0 until g.H).filter { g.canCastle(p, it) }.maxByOrNull { h -> g.map.hexNbrs[h].count { g.owner[it] == o } * 2.0 + tileWorth(g, p, h) + noise() }
            ?.takeIf { h -> g.map.hexNbrs[h].any { g.owner[it] == o } || g.halfCastle[h] }?.let { return KAct.Castle(it) }
        // 4. knights where they can reach the rival
        if (rng.nextDouble() < lvl.aggression) (0 until g.H).filter { g.canHire(p, it) && g.map.hexNbrs[it].any { n -> g.owner[n] == o } }
            .maxByOrNull { h -> g.map.hexNbrs[h].count { g.owner[it] == o } + noise() }?.let { return KAct.Hire(it) }
        // 5. towers, then villages
        if (g.can(p, Kreashun.COST_TOWER)) g.towerSpots(p).maxByOrNull { spotValue(g, it) + noise() }?.let { return KAct.Tower(it) }
        if (g.can(p, Kreashun.COST_VILLAGE)) g.villageSpots(p).maxByOrNull { spotValue(g, it) + noise() }?.let { return KAct.Village(it) }
        // 6. walls toward a capture
        walls.maxByOrNull { wallScore(g, p, it, lvl.careful) + noise() }?.takeIf { wallScore(g, p, it, lvl.careful) > 0.4 }?.let { return KAct.Wall(it) }
        // 7. trade toward walls, villages or towers
        val goal = when {
            g.villageSpots(p).isNotEmpty() && g.count(p, 1) < 3 -> Kreashun.COST_VILLAGE
            g.wallSpots(p).isNotEmpty() -> Kreashun.COST_WALL
            g.towerSpots(p).isNotEmpty() -> Kreashun.COST_TOWER
            else -> null
        }
        if (goal != null && rng.nextDouble() < lvl.trade) {
            val miss = missing(g, p, goal)
            val spare = IntArray(5) { r -> maxOf(0, g.res[p][r] - goal[r]) / 4 }
            if (miss.sum() > 0 && spare.sum() >= 1) {
                val give = (0..4).filter { spare[it] > 0 }.maxBy { g.res[p][it] - goal[it] }
                return KAct.Trade(give, (0..4).first { miss[it] > 0 })
            }
        }
        return KAct.End
    }
}
