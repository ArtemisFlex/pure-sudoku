package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.math.roundToInt
import kotlin.random.Random

/** One secret order in Veilguard. Orders are placed face down and all carried out together. */
sealed class VOrder {
    /** Move every banner on [from] to the touching tile [to]: reinforce, take free land, or attack. */
    data class March(val from: Int, val to: Int) : VOrder()
    /** Raise a knight, or a cheap decoy banner that looks just like one. */
    data class Muster(val h: Int, val decoy: Boolean) : VOrder()
    data class Build(val h: Int) : VOrder()
    /** A wall on the side between your tile [a] and its neighbour [b]. */
    data class Wall(val a: Int, val b: Int) : VOrder()
    data class Fortify(val h: Int) : VOrder()
    data class Claim(val h: Int) : VOrder()
    /** Spy on a rival tile: its true knights and decoys, and whether the Crown hides there. */
    data class Scout(val h: Int) : VOrder()
    /** Move your hidden Crown to another tile you hold. */
    data class Crown(val h: Int) : VOrder()

    fun code(): IntArray = when (this) {
        is March -> intArrayOf(0, from, to); is Muster -> intArrayOf(1, h, if (decoy) 1 else 0); is Build -> intArrayOf(2, h, 0)
        is Wall -> intArrayOf(3, a, b); is Fortify -> intArrayOf(4, h, 0); is Claim -> intArrayOf(5, h, 0)
        is Scout -> intArrayOf(6, h, 0); is Crown -> intArrayOf(7, h, 0)
    }

    companion object {
        fun of(t: Int, a: Int, b: Int): VOrder = when (t) {
            0 -> March(a, b); 1 -> Muster(a, b == 1); 2 -> Build(a); 3 -> Wall(a, b); 4 -> Fortify(a); 5 -> Claim(a); 6 -> Scout(a); else -> Crown(a)
        }
    }
}

/**
 * Veilguard: a game of deception on the Eiseria islands. Both players give up to three secret orders a round and
 * they resolve together. Each player hides a Crown on one of their tiles; lose it and you lose the game. Armies are
 * stacks of banners, and some banners are only decoys. The Veilnight Thief creeps toward the leader every round,
 * stealing as he goes; whoever kills him wins the Thief's Dagger and makes him their own.
 */
class Veilguard(val map: RealmGrid, val diff: Int, val target: Int) {
    companion object {
        const val LAND = 0; const val WATER = 1; const val HIDEOUT = 2; const val BARREN = 3
        const val SETUP = 0; const val PLAN = 1; const val OVER = 2

        const val MAX_ORDERS = 3
        const val THIEF_HP = 4
        const val ROUND_CAP = 60
        /** Cards the thief steals each round he stands on someone's land, by level. */
        val STEAL = intArrayOf(1, 1, 2, 2, 2, 3)
        /** Rounds between the thief's acts of sabotage, by level (like the dragon's timer). */
        val SABOTAGE = intArrayOf(5, 5, 4, 4, 3, 3)
        val LEVEL_NAME = listOf("", "village", "tower", "castle")
        val ORDER_NAME = listOf("March", "Muster", "Build", "Wall", "Fortify", "Claim", "Scout", "Crown")

        const val RALLY = 0; const val RELIC = 1; const val HARVEST = 2; const val SIEGE = 3; const val VEIL = 4
        val SPELLS = listOf("Rally", "Relic", "Harvest Moon", "Siege Fire", "Veil")
        val SPELL_TEXT = listOf(
            "A free knight joins one of your tiles when the orders are revealed.",
            "A secret treasure: worth 1 point. Counts by itself.",
            "Take any two resources when the orders are revealed.",
            "Burn up to 2 banners, knights or decoys, on a rival tile next to yours before anyone marches.",
            "The rival forgets everything it has learned about you: its spy reports and where it saw your Crown.",
        )
        val COST_SPELL = intArrayOf(0, 1, 1, 1, 0)

        val COST_KNIGHT = intArrayOf(0, 1, 1, 1, 0)
        val COST_DECOY = intArrayOf(1, 0, 0, 0, 0)
        val COST_TOWER = intArrayOf(0, 3, 2, 0, 0)
        val COST_CASTLE = intArrayOf(2, 3, 3, 0, 2)
        val COST_WALL = intArrayOf(1, 0, 0, 0, 1)
        val COST_FORTIFY = intArrayOf(0, 0, 1, 0, 0)
        val COST_SCOUT = intArrayOf(0, 0, 0, 0, 1)
        val FREE = IntArray(5)
        private val CLAIM_BASE = intArrayOf(1, 0, 1, 1, 1)
        private val WATERS = intArrayOf(1, 1, 2, 3, 4, 5)
        private val BARRENS = intArrayOf(0, 0, 1, 1, 2, 2)
        /** Who settles at each setup step: you, the rival twice, then you. */
        val SETUP_ORDER = intArrayOf(0, 1, 1, 0)

        /** Points goal as a share of the island's land. */
        var GOAL = 0.9f

        /** Banners a tile can hold. */
        fun cap(level: Int) = 2 + 2 * level

        fun newGame(d: Difficulty, rng: Random): Veilguard {
            val map = RealmMap.forLevel(d.ordinal)
            val land = map.HEXES - 1 - WATERS[d.ordinal] - BARRENS[d.ordinal]
            val g = Veilguard(map, d.ordinal, (land * GOAL).roundToInt())
            g.deal(rng)
            return g
        }

        fun fromState(s: IntArray): Veilguard {
            var i = 0
            fun get(): IntArray { val n = s[i++]; val a = s.copyOfRange(i, i + n); i += n; return a }
            val q = get(); val r = get(); val lv = s[i++]; val tg = s[i]
            return Veilguard(RealmMap.fromCoords(q, r), lv, tg).also { it.load(s) }
        }
    }

    val H = map.HEXES
    val kind = IntArray(H)
    val art = IntArray(H)
    val yields = IntArray(H * 5)
    val owner = IntArray(H) { -1 }
    val level = IntArray(H)
    val knights = IntArray(H)
    val decoys = IntArray(H)
    /** Thief damage a building has taken. Its health is its level (village 1, tower 2, castle 3) minus this; it never heals. */
    val scar = IntArray(H)
    fun hp(h: Int) = level[h] - scar[h]
    val wall = IntArray(map.EDGES) { -1 }
    /** Each player's hidden Crown tile. */
    val crown = IntArray(2) { -1 }
    val res = Array(2) { IntArray(5) }
    /** This round's orders, already paid for. */
    val orders = Array(2) { ArrayList<VOrder>() }
    private val paid = Array(2) { ArrayList<IntArray>() }
    val spells = Array(2) { IntArray(5) }
    /** Spells bought this round: they can't be cast until next round. */
    val fresh = Array(2) { IntArray(5) }
    /** The spell each player casts this round, face down like an order: kind, a, b (kind -1: none). */
    val cast = Array(2) { intArrayOf(-1, -1, -1) }
    val deck = ArrayList<Int>()

    // what each player knows about the other
    /** Where [p] last saw the rival's Crown (-1: unknown). It may have moved since. */
    val seenCrown = IntArray(2) { -1 }
    /** Where [p] last actually saw the rival's Crown, and in which round: it can only have crept a tile a round since. */
    val crownTrail = IntArray(2) { -1 }
    val trailRound = IntArray(2) { -1 }
    /** The round [p] last scouted each tile, and the true banners seen then. */
    val seenRound = Array(2) { IntArray(H) { -1 } }
    val seenKnights = Array(2) { IntArray(H) }
    val seenDecoys = Array(2) { IntArray(H) }
    /** The round a rival spy was seen on each of [p]'s tiles. */
    val spiedOn = Array(2) { IntArray(H) { -1 } }

    var hideout = 0
    var thief = 0
    var thiefHp = THIEF_HP
    /** Rounds until the thief can sabotage again; at 0 he wrecks the next building he stands on. */
    var sabotageIn = 0
    /** Who killed the thief (-1: he's wild). His killer wears the Dagger and commands him. */
    var thiefOwner = -1
    val thiefWild get() = thiefOwner < 0

    var round = 0
    var phase = SETUP
    var setupStep = 0
    var winner = -1
    /** How the game ended: 0 points, 1 Crown taken, 2 last land lost, 3 round limit. */
    var endReason = -1
    val log = ArrayList<String>()

    val actor: Int get() = if (phase == SETUP) SETUP_ORDER[setupStep.coerceAtMost(3)] else 0

    // ---------------------------------------------------------------- setup

    private fun deal(rng: Random) {
        hideout = (0 until H).minBy { map.hexX[it] * map.hexX[it] + map.hexY[it] * map.hexY[it] }
        kind[hideout] = HIDEOUT
        thief = hideout
        val others = (0 until H).filter { it != hideout }.shuffled(rng)
        var k = 0
        repeat(WATERS[diff]) {
            while (k < others.size) { val h = others[k++]; kind[h] = WATER; if (connected()) break else kind[h] = LAND }
        }
        repeat(BARRENS[diff]) { while (k < others.size) { val h = others[k++]; if (kind[h] == LAND) { kind[h] = BARREN; break } } }
        for (h in 0 until H) {
            if (kind[h] != LAND) continue
            val main = rng.nextInt(5); art[h] = main
            val roll = rng.nextInt(100)
            when {
                roll < 15 -> yields[h * 5 + main] = 3
                roll < 35 -> { val o = (main + 1 + rng.nextInt(4)) % 5; yields[h * 5 + main] = 2; yields[h * 5 + o] = 2 }
                else -> { yields[h * 5 + main] = 1; for (r in (0..4).filter { it != main }.shuffled(rng).take(2)) yields[h * 5 + r] = 1 }
            }
        }
        for (p in 0..1) res[p].fill(2)
        newDeck(rng)
    }

    private fun newDeck(rng: Random) {
        deck.clear()
        repeat(8) { deck += RALLY }; repeat(2) { deck += RELIC }; repeat(3) { deck += HARVEST }; repeat(4) { deck += SIEGE }; repeat(4) { deck += VEIL }
        deck.shuffle(rng)
    }

    private fun connected(): Boolean {
        val land = (0 until H).filter { kind[it] != WATER }
        val seen = BooleanArray(H); val q = ArrayDeque<Int>(); q += land[0]; seen[land[0]] = true
        while (q.isNotEmpty()) { val h = q.removeFirst(); for (n in map.hexNbrs[h]) if (!seen[n] && kind[n] != WATER) { seen[n] = true; q += n } }
        return land.all { seen[it] }
    }

    fun settleSpots(p: Int): List<Int> = (0 until H).filter { h ->
        kind[h] == LAND && owner[h] < 0 && h != hideout && hideout !in map.hexNbrs[h] && map.hexNbrs[h].none { owner[it] == 1 - p }
    }

    fun settle(h: Int) {
        val p = actor
        if (phase != SETUP || h !in settleSpots(p)) return
        owner[h] = p; level[h] = 1
        if (crown[p] < 0) { crown[p] = h; knights[h] = 2; decoys[h] = 1 } else knights[h] = 1
        log += (if (p == 0) "You settled " else "The rival settled ") + tileName(h)
        setupStep++
        if (setupStep >= 4) { phase = PLAN; round = 1; sabotageIn = SABOTAGE[diff]; income() }
    }

    // ---------------------------------------------------------------- queries

    fun yieldOf(h: Int) = IntArray(5) { yields[h * 5 + it] }
    fun worth(h: Int) = (0..4).sumOf { yields[h * 5 + it] }
    fun total(p: Int) = res[p].sum()
    fun can(p: Int, cost: IntArray) = (0..4).all { res[p][it] >= cost[it] }
    fun tiles(p: Int) = (0 until H).count { owner[it] == p }
    fun banners(h: Int) = knights[h] + decoys[h]
    fun ownable(h: Int) = kind[h] == LAND || kind[h] == BARREN
    fun vp(p: Int, withRelics: Boolean = true) = (0 until H).sumOf { if (owner[it] == p) level[it] else 0 } + (if (thiefOwner == p) 1 else 0) + (if (withRelics) spells[p][RELIC] else 0)
    fun side(a: Int, b: Int): Int {
        val shared = map.hexVerts[a].filter { it in map.hexVerts[b] }
        return if (shared.size == 2) map.edgeOf(shared[0], shared[1]) else -1
    }
    fun leader(): Int {
        val a = vp(0); val b = vp(1)
        return when { a > b -> 0; b > a -> 1; tiles(0) > tiles(1) -> 0; tiles(1) > tiles(0) -> 1; else -> -1 }
    }
    fun upgradeCost(h: Int) = if (level[h] <= 1) COST_TOWER else COST_CASTLE

    fun claimCost(p: Int, h: Int): IntArray {
        val c = CLAIM_BASE.copyOf()
        val y = yieldOf(h)
        for (r in 0..4) if (y[r] >= 2) c[r] += y[r] - 1
        repeat(tiles(p) / 3) { c[it % 5]++ }
        return c
    }

    fun tileName(h: Int): String = when (kind[h]) {
        HIDEOUT -> "the thief's hideout"; WATER -> "the lake"; BARREN -> "a barren waste"
        else -> "the " + listOf("forest", "mountain", "fields", "meadow", "crystal spires")[art[h]]
    }

    /** Shortest land path from [a] to [b], excluding [a]. */
    fun path(a: Int, b: Int): List<Int>? {
        if (a == b) return emptyList()
        val prev = IntArray(H) { -2 }; prev[a] = -1
        val q = ArrayDeque<Int>(); q += a
        while (q.isNotEmpty()) {
            val h = q.removeFirst(); if (h == b) break
            for (n in map.hexNbrs[h]) if (prev[n] == -2 && kind[n] != WATER) { prev[n] = h; q += n }
        }
        if (prev[b] == -2) return null
        val out = ArrayList<Int>(); var c = b
        while (c != a) { out += c; c = prev[c] }
        return out.reversed()
    }

    /** Where the thief heads next: the victim's richest tile (nearest first), and his next step there. */
    fun thiefPlan(): Pair<Int, Int>? {
        if (phase != PLAN) return null
        val victim = if (thiefWild) leader() else 1 - thiefOwner
        if (victim < 0) return null
        val goals = (0 until H).filter { owner[it] == victim && it != thief }.mapNotNull { h -> path(thief, h)?.let { h to it } }
        val (goal, route) = goals.maxByOrNull { (h, r) -> worth(h) * 10 + level[h] * 3 - r.size * 4 } ?: return null
        return goal to (route.firstOrNull() ?: thief)
    }

    // ---------------------------------------------------------------- orders

    fun costOf(p: Int, o: VOrder): IntArray = when (o) {
        is VOrder.March, is VOrder.Crown -> FREE
        is VOrder.Muster -> if (o.decoy) COST_DECOY else COST_KNIGHT
        is VOrder.Build -> upgradeCost(o.h)
        is VOrder.Wall -> COST_WALL
        is VOrder.Fortify -> COST_FORTIFY
        is VOrder.Claim -> claimCost(p, o.h)
        is VOrder.Scout -> COST_SCOUT
    }

    fun canOrder(p: Int, o: VOrder): Boolean {
        if (phase != PLAN || winner >= 0 || orders[p].size >= MAX_ORDERS || !can(p, costOf(p, o))) return false
        val mine = orders[p]
        return when (o) {
            is VOrder.March -> owner[o.from] == p && banners(o.from) > 0 && o.to in map.hexNbrs[o.from] && kind[o.to] != WATER &&
                mine.none { it is VOrder.March && it.from == o.from }
            is VOrder.Muster -> owner[o.h] == p && banners(o.h) + mine.count { it is VOrder.Muster && it.h == o.h } < cap(level[o.h])
            is VOrder.Build -> owner[o.h] == p && level[o.h] < 3 && mine.none { it is VOrder.Build && it.h == o.h }
            is VOrder.Wall -> owner[o.a] == p && side(o.a, o.b).let { e -> e >= 0 && wall[e] < 0 && mine.none { it is VOrder.Wall && side(it.a, it.b) == e } }
            is VOrder.Fortify -> owner[o.h] == p && mine.none { it is VOrder.Fortify && it.h == o.h }
            is VOrder.Claim -> owner[o.h] < 0 && ownable(o.h) && map.hexNbrs[o.h].any { owner[it] == p } && mine.none { it is VOrder.Claim && it.h == o.h }
            is VOrder.Scout -> owner[o.h] == 1 - p && mine.none { it is VOrder.Scout && it.h == o.h }
            is VOrder.Crown -> owner[o.h] == p && o.h != crown[p] && crown[p] >= 0 && o.h in map.hexNbrs[crown[p]] && mine.none { it is VOrder.Crown }
        }
    }

    fun place(p: Int, o: VOrder): Boolean {
        if (!canOrder(p, o)) return false
        val c = costOf(p, o).copyOf()
        for (r in 0..4) res[p][r] -= c[r]
        orders[p] += o; paid[p] += c
        return true
    }

    fun cancel(p: Int, i: Int) {
        if (phase != PLAN || i !in orders[p].indices) return
        val c = paid[p].removeAt(i); orders[p].removeAt(i)
        for (r in 0..4) res[p][r] += c[r]
    }

    private fun refund(p: Int, o: VOrder) {
        val i = orders[p].indexOf(o); if (i < 0) return
        val c = paid[p][i]; for (r in 0..4) res[p][r] += c[r]
        paid[p][i] = FREE
    }

    fun trade(p: Int, give: Int, get: Int): Boolean {
        if (phase != PLAN || give == get || res[p][give] < 4) return false
        res[p][give] -= 4; res[p][get]++
        return true
    }

    // ---------------------------------------------------------------- spells

    fun siegeTargets(p: Int) = (0 until H).filter { owner[it] == 1 - p && banners(it) > 0 && map.hexNbrs[it].any { n -> owner[n] == p } }
    fun rallySpots(p: Int) = (0 until H).filter { owner[it] == p && banners(it) < cap(level[it]) }

    fun buySpell(p: Int): Boolean {
        if (phase != PLAN || winner >= 0 || !can(p, COST_SPELL) || deck.isEmpty()) return false
        for (r in 0..4) res[p][r] -= COST_SPELL[r]
        val s = deck.removeAt(deck.lastIndex); spells[p][s]++; fresh[p][s]++
        if (p == 0) log += "You learned ${SPELLS[s]}"
        return true
    }

    fun canCast(p: Int, k: Int): Boolean = phase == PLAN && winner < 0 && cast[p][0] < 0 && k != RELIC && spells[p][k] - fresh[p][k] > 0 &&
        when (k) { RALLY -> rallySpots(p).isNotEmpty(); SIEGE -> siegeTargets(p).isNotEmpty(); else -> true }

    fun castSpell(p: Int, k: Int, a: Int = -1, b: Int = -1): Boolean {
        if (!canCast(p, k)) return false
        val ok = when (k) { RALLY -> a in rallySpots(p); SIEGE -> a in siegeTargets(p); HARVEST -> a in 0..4 && b in 0..4; else -> true }
        if (!ok) return false
        spells[p][k]--; cast[p][0] = k; cast[p][1] = a; cast[p][2] = b
        return true
    }

    /** Take back this round's spell before the reveal. */
    fun uncast(p: Int) {
        if (phase != PLAN || cast[p][0] < 0) return
        spells[p][cast[p][0]]++; cast[p][0] = -1; cast[p][1] = -1; cast[p][2] = -1
    }

    // ---------------------------------------------------------------- resolution

    private class Mv(val p: Int, val from: Int, val to: Int, var k: Int, var d: Int)

    /** Turn both players' orders face up and carry them out together. */
    fun resolve(rng: Random) {
        if (phase != PLAN) return
        val fort = BooleanArray(H)
        fun who(p: Int) = if (p == 0) "You" else "The rival"
        fun whose(p: Int) = if (p == 0) "your" else "the rival's"

        // 1. Crowns slip away in secret
        for (p in 0..1) for (o in orders[p]) if (o is VOrder.Crown) {
            crown[p] = o.h
            if (p == 0) log += "Your Crown was moved in secret to ${tileName(o.h)}"
        }
        // 2. walls and fortifications
        for (p in 0..1) for (o in orders[p]) when (o) {
            is VOrder.Wall -> { val e = side(o.a, o.b); if (e >= 0 && wall[e] < 0 && owner[o.a] == p) wall[e] = p }
            is VOrder.Fortify -> if (owner[o.h] == p) fort[o.h] = true
            else -> {}
        }
        // 3. Siege Fire burns banners before anyone marches
        for (p in 0..1) if (cast[p][0] == SIEGE) {
            val t = cast[p][1]
            if (owner[t] != 1 - p) continue
            var burnt = 0
            repeat(2) {
                val n = banners(t)
                if (n > 0) { if (rng.nextInt(n) < knights[t]) knights[t]-- else decoys[t]--; burnt++ }
            }
            log += "${who(p)} hurled Siege Fire at ${whose(1 - p)} ${tileName(t)}" + if (burnt == 0) ", but no banners were there" else " and burnt $burnt banner${if (burnt > 1) "s" else ""}"
        }
        // scouts report what is there right now
        for (p in 0..1) for (o in orders[p]) if (o is VOrder.Scout) {
            val h = o.h
            seenRound[p][h] = round; seenKnights[p][h] = knights[h]; seenDecoys[p][h] = decoys[h]
            spiedOn[1 - p][h] = round
            val found = crown[1 - p] == h
            if (found) { seenCrown[p] = h; crownTrail[p] = h; trailRound[p] = round } else if (seenCrown[p] == h) seenCrown[p] = -1
            log += if (p == 0) "Your spy at ${tileName(h)}: ${knights[h]} knight${if (knights[h] == 1) "" else "s"}, ${decoys[h]} decoy${if (decoys[h] == 1) "" else "s"}" +
                if (found) ". The rival's Crown is there!" else ". No Crown."
            else "A rival spy was seen at your ${LEVEL_NAME[level[h]]} on ${tileName(h)}"
        }
        // 4. marches: every marching stack sets out at once
        val mvs = ArrayList<Mv>()
        for (p in 0..1) for (o in orders[p]) if (o is VOrder.March && owner[o.from] == p && banners(o.from) > 0) {
            mvs += Mv(p, o.from, o.to, knights[o.from], decoys[o.from]); knights[o.from] = 0; decoys[o.from] = 0
        }
        fun home(m: Mv) { if (owner[m.from] == m.p) { knights[m.from] += m.k; decoys[m.from] += m.d } }
        // armies crossing the same border meet in the field
        for (a in mvs) for (b in mvs) if (a.p == 0 && b.p == 1 && a.from == b.to && a.to == b.from) {
            val m = minOf(a.k, b.k)
            log += "Armies met on the road: ${a.k} of yours against ${b.k} of the rival's" + (if (a.d + b.d > 0) ", and the decoys fled" else "")
            a.k -= m; b.k -= m; a.d = 0; b.d = 0
        }
        // the wild thief fights anyone who marches on him; every knight that fights him dies
        if (thiefWild) for (m in mvs.sortedByDescending { it.k }) if (m.to == thief && m.k > 0 && thiefWild) {
            val dmg = minOf(m.k, thiefHp); m.k -= dmg; thiefHp -= dmg
            if (thiefHp <= 0) {
                thiefOwner = m.p
                log += if (m.p == 0) "Your knights died, but slew the Veilnight Thief! You win the Thief's Dagger (+1 point), and he serves you now"
                    else "The rival's knights slew the Veilnight Thief! It wins the Thief's Dagger, and he serves it now"
            } else log += "${who(m.p)} lost $dmg knight${if (dmg > 1) "s" else ""} wounding the Veilnight Thief ($thiefHp of $THIEF_HP left)"
        }
        // arrivals, tile by tile
        for (t in mvs.map { it.to }.distinct().sorted()) {
            val arr = mvs.filter { it.to == t && it.k + it.d > 0 }
            if (arr.isEmpty()) continue
            val o = owner[t]
            if (o >= 0) {
                for (m in arr.filter { it.p == o }) { knights[t] += m.k; decoys[t] += m.d }
                val atk = arr.filter { it.p != o }
                if (atk.isEmpty()) continue
                val a = 1 - o
                val ak = atk.sumOf { it.k }
                if (ak == 0) { log += "${who(a)} sent only decoys against ${whose(o)} ${tileName(t)}. The bluff was exposed"; continue }
                var def = knights[t] + (if (fort[t]) 2 else 0) + (level[t] - 1)
                val walls = atk.map { side(it.from, t) }.filter { it >= 0 && wall[it] == o }.distinct()
                if (walls.isNotEmpty()) { def += 2; walls.forEach { wall[it] = -1 } }
                val dk = knights[t]
                if (ak > def) {
                    val wasCrown = crown[o] == t
                    val lvl = level[t]
                    owner[t] = a; level[t] = maxOf(1, lvl - 1); scar[t] = minOf(scar[t], level[t] - 1)
                    knights[t] = minOf(ak - dk, cap(level[t])); decoys[t] = 0
                    log += "${who(a)} stormed ${whose(o)} ${LEVEL_NAME[lvl]} with $ak knight${if (ak > 1) "s" else ""} and took it" + if (dk > 0) " ($dk defender${if (dk > 1) "s" else ""} fell)" else ""
                    if (wasCrown) {
                        log += if (a == 0) "The rival's Crown was hidden there! It's yours" else "Your Crown was hidden there! The rival has seized it"
                        winner = a; endReason = 1
                    }
                    if (crown[a] < 0) crown[a] = t
                } else {
                    knights[t] = dk - minOf(ak, dk); decoys[t] = 0
                    log += "${who(a)} attacked ${whose(o)} ${tileName(t)} with $ak knight${if (ak > 1) "s" else ""} and was thrown back" +
                        (if (walls.isNotEmpty()) ". A wall held" else "") + if (fort[t]) ". It was fortified" else ""
                }
            } else {
                val byP = arr.groupBy { it.p }
                var k0 = byP[0]?.sumOf { it.k } ?: 0; var k1 = byP[1]?.sumOf { it.k } ?: 0
                if (k0 > 0 && k1 > 0) {
                    val m = minOf(k0, k1); k0 -= m; k1 -= m
                    log += "Both armies reached ${tileName(t)} and fought for it"
                    arr.forEach { it.d = 0 }
                }
                val taker = when { !ownable(t) -> -1; k0 > 0 -> 0; k1 > 0 -> 1; else -> -1 }
                if (taker >= 0) {
                    owner[t] = taker; level[t] = 1; scar[t] = 0
                    knights[t] = minOf(if (taker == 0) k0 else k1, cap(1))
                    decoys[t] = byP[taker]?.sumOf { it.d } ?: 0
                    log += "${who(taker)} marched in and took ${tileName(t)}"
                    byP[1 - taker]?.forEach { it.k = 0; home(it) }
                } else {
                    // nothing to hold here: survivors go home
                    if (k0 == 0) byP[0]?.forEach { it.k = 0 }
                    if (k1 == 0) byP[1]?.forEach { it.k = 0 }
                    for (m in arr) home(m)
                }
            }
        }
        // 5. claims: two claims on one tile both fail
        val claims = (0..1).flatMap { p -> orders[p].filterIsInstance<VOrder.Claim>().map { p to it } }
        for ((p, c) in claims) {
            if (owner[c.h] >= 0 || claims.any { it.first != p && it.second.h == c.h }) {
                refund(p, c)
                if (p == 0) log += "Your claim on ${tileName(c.h)} failed" + if (claims.any { it.first != p && it.second.h == c.h }) ": the rival claimed it too" else ""
            } else { owner[c.h] = p; level[c.h] = 1; scar[c.h] = 0; knights[c.h] = 0; decoys[c.h] = 0; log += "${who(p)} claimed ${tileName(c.h)}" }
        }
        // 6. spells that pay out, then musters and buildings, on tiles still held
        for (p in 0..1) when (cast[p][0]) {
            RALLY -> { val h = cast[p][1]; if (owner[h] == p && banners(h) < cap(level[h])) { knights[h]++; log += "${who(p)} rallied a knight" + if (p == 0) " to ${tileName(h)}" else "" } }
            HARVEST -> { res[p][cast[p][1]]++; res[p][cast[p][2]]++; log += "${who(p)} cast Harvest Moon" }
        }
        for (p in 0..1) for (o in orders[p].toList()) when (o) {
            is VOrder.Muster -> if (owner[o.h] == p && banners(o.h) < cap(level[o.h])) { if (o.decoy) decoys[o.h]++ else knights[o.h]++ } else refund(p, o)
            is VOrder.Build -> if (owner[o.h] == p && level[o.h] < 3) { level[o.h]++; log += "${who(p)} raised a ${LEVEL_NAME[level[o.h]]}" } else refund(p, o)
            else -> {}
        }
        if (crown[0] >= 0 && owner[crown[0]] != 0) crown[0] = (0 until H).firstOrNull { owner[it] == 0 } ?: -1
        if (crown[1] >= 0 && owner[crown[1]] != 1) crown[1] = (0 until H).firstOrNull { owner[it] == 1 } ?: -1
        // 7. the thief creeps one tile and steals
        thiefMove(rng)
        // Veil: the rival forgets what it learned
        for (p in 0..1) if (cast[p][0] == VEIL) {
            val o = 1 - p
            seenCrown[o] = -1; seenRound[o].fill(-1); crownTrail[o] = -1; trailRound[o] = -1
            log += if (p == 0) "You cast Veil: the rival forgets all it has learned about you" else "The rival cast Veil: all you learned about it is forgotten"
        }
        // 8. a new round
        for (p in 0..1) { orders[p].clear(); paid[p].clear(); fresh[p].fill(0); cast[p][0] = -1; cast[p][1] = -1; cast[p][2] = -1 }
        checkEnd()
        if (winner >= 0) { phase = OVER; return }
        round++
        income()
    }

    private fun thiefMove(rng: Random) {
        if (thiefWild && sabotageIn > 0) sabotageIn--
        val plan = thiefPlan() ?: return
        thief = plan.second
        val o = owner[thief]
        if (thiefWild) {
            if (o < 0) { log += "The Veilnight Thief creeps toward ${if (leader() == 0) "your" else "the rival's"} land"; return }
            val got = steal(o, STEAL[diff], rng)
            val whose = if (o == 0) "your" else "the rival's"
            log += "The Veilnight Thief slipped into $whose ${tileName(thief)}" + if (got.isEmpty()) ", but found nothing to steal" else " and stole ${got.joinToString(", ")}"
            // and, when his sabotage is ready, wrecks the building there: 1 damage that never heals
            if (sabotageIn > 0) return
            sabotageIn = SABOTAGE[diff]
            scar[thief]++
            if (hp(thief) <= 0) {
                val was = LEVEL_NAME[level[thief]]
                owner[thief] = -1; level[thief] = 0; knights[thief] = 0; decoys[thief] = 0; scar[thief] = 0
                log += "He razed $whose $was! The land is lost"
                if (crown[o] == thief) {
                    crown[o] = (0 until H).filter { owner[it] == o }.maxByOrNull { level[it] * 10 + knights[it] } ?: -1
                    if (o == 0 && crown[0] >= 0) log += "Your Crown fled to ${tileName(crown[0])}"
                }
            } else log += "He sabotaged $whose ${LEVEL_NAME[level[thief]]} (${hp(thief)} of ${level[thief]} health left)"
        } else {
            val ally = thiefOwner
            if (o != 1 - ally) return
            val got = steal(o, STEAL[diff], rng, to = ally)
            // he spies on the tile he robs
            seenRound[ally][thief] = round; seenKnights[ally][thief] = knights[thief]; seenDecoys[ally][thief] = decoys[thief]
            val found = crown[o] == thief
            if (found) { seenCrown[ally] = thief; crownTrail[ally] = thief; trailRound[ally] = round } else if (seenCrown[ally] == thief) seenCrown[ally] = -1
            log += (if (ally == 0) "Your thief robbed the rival's ${tileName(thief)}" else "The rival's thief robbed your ${tileName(thief)}") +
                (if (got.isEmpty()) "" else ": ${got.joinToString(", ")}") + if (found && ally == 0) ". He found the rival's Crown!" else ""
        }
    }

    private fun steal(from: Int, n: Int, rng: Random, to: Int = -1): List<String> {
        val out = ArrayList<String>()
        repeat(n) {
            val have = (0..4).filter { res[from][it] > 0 }
            if (have.isEmpty()) return out
            val r = have[rng.nextInt(have.size)]
            res[from][r]--; if (to >= 0) res[to][r]++
            out += "1 ${Realm.RES[r]}"
        }
        return out
    }

    private fun income() {
        for (p in 0..1) for (h in 0 until H) if (owner[h] == p) for (r in 0..4) res[p][r] += yields[h * 5 + r]
    }

    private fun checkEnd() {
        if (winner >= 0) return
        for (p in 0..1) if (tiles(p) == 0) { winner = 1 - p; endReason = 2; return }
        // no points goal: the Crown decides. Only a stalemate that runs to the round limit goes to the points leader.
        if (round >= ROUND_CAP) { winner = leader().let { if (it < 0) 2 else it }; endReason = 3 }
    }

    // ---------------------------------------------------------------- save / copy

    fun state(): IntArray {
        val o = ArrayList<Int>()
        fun put(a: IntArray) { o += a.size; a.forEach { o += it } }
        put(map.hexQ); put(map.hexR); o += diff; o += target
        put(kind); put(art); put(yields); put(owner); put(level); put(knights); put(decoys); put(wall); put(crown)
        put(res[0]); put(res[1])
        for (p in 0..1) { put(orders[p].flatMap { it.code().toList() }.toIntArray()); put(paid[p].flatMap { it.toList() }.toIntArray()) }
        put(seenCrown); for (p in 0..1) { put(seenRound[p]); put(seenKnights[p]); put(seenDecoys[p]) }
        put(intArrayOf(hideout, thief, thiefHp, thiefOwner, round, phase, setupStep, winner, endReason))
        put(spiedOn[0]); put(spiedOn[1]); put(scar); put(intArrayOf(sabotageIn))
        put(spells[0]); put(spells[1]); put(fresh[0]); put(fresh[1]); put(cast[0]); put(cast[1]); put(deck.toIntArray())
        put(crownTrail); put(trailRound)
        return o.toIntArray()
    }

    fun load(s: IntArray) {
        var i = 0
        fun get(): IntArray { val n = s[i++]; val a = s.copyOfRange(i, i + n); i += n; return a }
        get(); get(); i += 2
        get().copyInto(kind); get().copyInto(art); get().copyInto(yields); get().copyInto(owner); get().copyInto(level)
        get().copyInto(knights); get().copyInto(decoys); get().copyInto(wall); get().copyInto(crown)
        get().copyInto(res[0]); get().copyInto(res[1])
        for (p in 0..1) {
            orders[p].clear(); paid[p].clear()
            get().toList().chunked(3).forEach { (t, a, b) -> orders[p] += VOrder.of(t, a, b) }
            get().toList().chunked(5).forEach { paid[p] += it.toIntArray() }
        }
        get().copyInto(seenCrown); for (p in 0..1) { get().copyInto(seenRound[p]); get().copyInto(seenKnights[p]); get().copyInto(seenDecoys[p]) }
        val m = get()
        hideout = m[0]; thief = m[1]; thiefHp = m[2]; thiefOwner = m[3]; round = m[4]; phase = m[5]; setupStep = m[6]; winner = m[7]; endReason = m[8]
        if (i < s.size) { get().copyInto(spiedOn[0]); get().copyInto(spiedOn[1]) }
        if (i < s.size) get().copyInto(scar)
        if (i < s.size) sabotageIn = get()[0]
        if (i < s.size) {
            for (p in 0..1) get().copyInto(spells[p])
            for (p in 0..1) get().copyInto(fresh[p])
            for (p in 0..1) get().copyInto(cast[p])
            deck.clear(); get().forEach { deck += it }
        } else newDeck(Random(round.toLong() * 31 + 7))   // a save from before spells
        if (i < s.size) { get().copyInto(crownTrail); get().copyInto(trailRound) }
    }

    fun copy(): Veilguard = fromState(state())
}

/**
 * The Veilguard computer player. It sees only what you would see: the rival's banner counts, its own scouting,
 * and where it last spotted the rival's Crown. It never peeks at hidden knights, decoys or the true Crown.
 */
object VeilguardAi {
    class Level(val noise: Double, val aggression: Double, val bluff: Double, val scout: Double, val crownMove: Double, val trade: Double)

    fun level(d: Difficulty) = when (d) {
        Difficulty.EASY -> Level(2.0, 0.35, 0.0, 0.15, 0.2, 0.3)
        Difficulty.MEDIUM -> Level(1.5, 0.5, 0.15, 0.3, 0.4, 0.5)
        Difficulty.HARD -> Level(1.0, 0.65, 0.3, 0.5, 0.6, 0.8)
        Difficulty.EXPERT -> Level(0.6, 0.75, 0.45, 0.6, 0.75, 0.9)
        Difficulty.MASTER -> Level(0.3, 0.85, 0.55, 0.7, 0.85, 1.0)
        Difficulty.EXTREME -> Level(0.0, 0.95, 0.65, 0.8, 0.95, 1.0)
    }

    fun settle(g: Veilguard, p: Int, lvl: Level, rng: Random): Int =
        g.settleSpots(p).maxBy { h -> g.worth(h) * 2.0 + g.map.hexNbrs[h].count { g.owner[it] < 0 && g.ownable(it) } * 0.6 + g.map.hexNbrs[h].sumOf { g.worth(it) } * 0.15 + (rng.nextDouble() - 0.5) * lvl.noise }

    /** Knights [p] believes stand on rival tile [h]: what a recent scout saw, or a guess from the banner count. */
    private fun estKnights(g: Veilguard, p: Int, h: Int): Double =
        if (g.seenRound[p][h] >= g.round - 1 && g.seenRound[p][h] >= 0) g.seenKnights[p][h].toDouble() + maxOf(0, g.banners(h) - g.seenKnights[p][h] - g.seenDecoys[p][h])
        else g.banners(h) * 0.75

    /** Where [p] thinks the rival's Crown hides. */
    fun crownGuess(g: Veilguard, p: Int): Int {
        val o = 1 - p
        val seen = g.seenCrown[p]
        if (seen >= 0 && g.owner[seen] == o) return seen
        // the Crown creeps one tile a round at most: search near where it was last seen
        val trail = g.crownTrail[p]
        fun near(h: Int): Double {
            if (trail < 0) return 0.0
            val d = g.path(trail, h)?.size ?: return 0.0
            val since = g.round - g.trailRound[p]
            return if (d <= since) 12.0 - d * 2.0 else 0.0
        }
        return (0 until g.H).filter { g.owner[it] == o && !(g.seenRound[p][it] >= 0 && g.seenCrown[p] != it && g.seenRound[p][it] >= g.round - 3) }
            .maxByOrNull { g.banners(it) * 2.0 + g.level[it] * 1.5 + g.worth(it) * 0.2 + near(it) } ?: -1
    }

    /** The computer's round: trades first, then orders, spells bought, and the spell cast (kind, a, b). */
    data class VPlan(val trades: List<Pair<Int, Int>>, val orders: List<VOrder>, val buys: Int, val cast: IntArray?)

    fun plan(g0: Veilguard, p: Int, lvl: Level, rng: Random): VPlan {
        val g = g0.copy()
        val o = 1 - p
        val trades = ArrayList<Pair<Int, Int>>()
        val out = ArrayList<VOrder>()
        fun noise() = (rng.nextDouble() - 0.5) * lvl.noise
        fun add(x: VOrder): Boolean { if (g.place(p, x)) { out += x; return true }; return false }
        fun threat(h: Int) = g.map.hexNbrs[h].filter { g.owner[it] == o }.sumOf { estKnights(g, p, it) }
        val mine = (0 until g.H).filter { g.owner[it] == p }
        val crown = g.crown[p]

        // trade spare cards toward knights
        if (rng.nextDouble() < lvl.trade) repeat(3) {
            val need = (0..4).filter { Veilguard.COST_KNIGHT[it] > 0 && g.res[p][it] == 0 }
            val give = (0..4).filter { g.res[p][it] >= 5 }.maxByOrNull { g.res[p][it] }
            if (need.isNotEmpty() && give != null && g.trade(p, give, need[0])) trades += give to need[0]
        }

        // 1. keep the Crown safe: slip it away from danger, or fortify it
        if (crown >= 0) {
            val t = threat(crown); val def = g.knights[crown] + g.level[crown] - 1
            val spied = g.spiedOn[p][crown] >= g.round - 1 && g.spiedOn[p][crown] >= 0
            val near = mine.filter { it in g.map.hexNbrs[crown] }
            if (spied && rng.nextDouble() < lvl.crownMove) {
                near.minByOrNull { threat(it) * 3 - g.level[it] - g.knights[it] + noise() }?.let { add(VOrder.Crown(it)) }
            } else if (t >= def && t > 0) {
                val safe = near.minByOrNull { threat(it) * 3 - g.level[it] - g.knights[it] + noise() }
                if (safe != null && threat(safe) < t && rng.nextDouble() < lvl.crownMove) add(VOrder.Crown(safe))
                else add(VOrder.Fortify(crown))
            }
        }
        // 2. attack where it can win: the guessed Crown above all
        val guess = crownGuess(g, p)
        if (g.seenCrown[p] < 0 && guess >= 0 && rng.nextDouble() < lvl.scout) add(VOrder.Scout(guess))
        // a Crown it has found: mass knights next to it, then storm it
        val known = g.seenCrown[p].takeIf { it >= 0 && g.owner[it] == o }
        if (known != null && rng.nextDouble() < 0.5 + lvl.aggression / 2) {
            val stage = mine.filter { known in g.map.hexNbrs[it] }.maxByOrNull { g.knights[it] }
            if (stage != null) {
                val wall = if (g.wall[g.side(stage, known)] == o) 2 else 0
                val def = estKnights(g, p, known) + (g.level[known] - 1) + wall + 2
                val ring = mine.filter { known in g.map.hexNbrs[it] && g.knights[it] > 0 && !(it == crown && threat(it) > g.knights[it]) }
                    .sortedByDescending { g.knights[it] }.take(Veilguard.MAX_ORDERS - out.size)
                if (g.knights[stage] > def) add(VOrder.March(stage, known))
                else if (ring.sumOf { g.knights[it] } > def + 1) ring.forEach { add(VOrder.March(it, known)) }
                else {
                    mine.filter { it != stage && it != crown && stage in g.map.hexNbrs[it] && g.knights[it] > 0 }.maxByOrNull { g.knights[it] }
                        ?.let { add(VOrder.March(it, stage)) }
                    repeat(2) { add(VOrder.Muster(stage, false)) }
                }
            }
        }
        if (rng.nextDouble() < lvl.aggression) {
            val moves = mine.filter { g.knights[it] > 0 }.flatMap { f -> g.map.hexNbrs[f].filter { g.owner[it] == o }.map { f to it } }
            val best = moves.mapNotNull { (f, t) ->
                val wall = if (g.wall[g.side(f, t)].let { it == o }) 2 else 0
                val def = estKnights(g, p, t) + (g.level[t] - 1) + wall + (if (t == guess) 1.0 else 0.4)
                // never strip the Crown bare next to the enemy
                if (f == g.crown[p] && threat(f) > 0) return@mapNotNull null
                if (g.knights[f] <= def) null else Triple(f, t, (if (t == guess) 25.0 else 0.0) + g.level[t] * 3 + g.worth(t) + (g.knights[f] - def) + noise())
            }.maxByOrNull { it.third }
            if (best != null) add(VOrder.March(best.first, best.second))
        }
        // 2b. close in on the guessed Crown: march a stack one step nearer
        if (guess >= 0 && g.round >= 6 && rng.nextDouble() < lvl.aggression) {
            mine.filter { it != crown && g.knights[it] >= 2 }.mapNotNull { f -> g.path(f, guess)?.firstOrNull()?.let { f to it } }
                .filter { (_, t) -> g.owner[t] == p }.maxByOrNull { (f, _) -> g.knights[f] + noise() }?.let { (f, t) -> add(VOrder.March(f, t)) }
        }
        // 3. grow: claim the best free land
        g.map.let { m ->
            val free = (0 until g.H).filter { h -> g.owner[h] < 0 && g.ownable(h) && m.hexNbrs[h].any { g.owner[it] == p } && g.can(p, g.claimCost(p, h)) }
            free.maxByOrNull { g.worth(it) + noise() }?.let { add(VOrder.Claim(it)) }
        }
        // 4. knights where the rival presses, and on the Crown
        val needy = mine.maxByOrNull { threat(it) - g.knights[it] + (if (it == g.crown[p]) 1.5 else 0.0) + noise() }
        if (needy != null && (threat(needy) > 0 || needy == g.crown[p])) add(VOrder.Muster(needy, false))
        // 5. bluff: decoys on a tile that isn't the Crown, so it looks like one
        if (rng.nextDouble() < lvl.bluff) mine.filter { it != g.crown[p] }.maxByOrNull { g.level[it] + noise() }?.let { add(VOrder.Muster(it, true)) }
        // 6. find the rival's Crown
        if (g.seenCrown[p] < 0 && rng.nextDouble() < lvl.scout)
            (0 until g.H).filter { g.owner[it] == o && it != guess && g.seenRound[p][it] < g.round - 2 }.maxByOrNull { g.banners(it) + g.level[it] + noise() }?.let { add(VOrder.Scout(it)) }
        // 7. build: a tower or castle (not always on the Crown, to hide it)
        mine.filter { g.level[it] < 3 }.maxByOrNull { g.worth(it) + g.level[it] + noise() }?.let { add(VOrder.Build(it)) }
        // 8. fill up with knights at the border, or march a big stack into free land
        repeat(2) {
            mine.filter { g.map.hexNbrs[it].any { n -> g.owner[n] == o } }.maxByOrNull { -g.knights[it] + noise() }?.let { add(VOrder.Muster(it, false)) }
        }
        if (out.size < Veilguard.MAX_ORDERS && rng.nextDouble() < lvl.aggression) mine.filter { g.knights[it] >= 3 && it != g.crown[p] }.firstNotNullOfOrNull { f ->
            g.map.hexNbrs[f].firstOrNull { g.owner[it] < 0 && g.ownable(it) && it != g.thief }?.let { f to it }
        }?.let { add(VOrder.March(it.first, it.second)) }
        // use every order: knights on the Crown, then any build or claim it can afford
        if (out.size < Veilguard.MAX_ORDERS && crown >= 0) add(VOrder.Muster(crown, false))
        if (out.size < Veilguard.MAX_ORDERS) mine.filter { g.level[it] < 3 }.sortedByDescending { g.worth(it) }.firstOrNull { add(VOrder.Build(it)) }
        if (out.size < Veilguard.MAX_ORDERS) mine.shuffled(rng).firstOrNull { add(VOrder.Muster(it, rng.nextDouble() < lvl.bluff)) }
        // a spell: hide a spied Crown, burn the tile it attacks, a knight where it's needed, or resources
        var spell: IntArray? = null
        val attacked = out.filterIsInstance<VOrder.March>().map { it.to }.firstOrNull { it in g.siegeTargets(p) }
        when {
            g.canCast(p, Veilguard.VEIL) && crown >= 0 && g.spiedOn[p][crown] >= 0 && g.spiedOn[p][crown] >= g.round - 2 -> spell = intArrayOf(Veilguard.VEIL, -1, -1)
            g.canCast(p, Veilguard.SIEGE) -> {
                val t = attacked ?: g.siegeTargets(p).maxByOrNull { (if (it == guess) 3.0 else 0.0) + g.banners(it) + noise() }
                if (t != null) spell = intArrayOf(Veilguard.SIEGE, t, -1)
            }
            g.canCast(p, Veilguard.RALLY) -> {
                val h = g.rallySpots(p).maxByOrNull { threat(it) - g.knights[it] + (if (it == g.crown[p]) 1.5 else 0.0) + noise() }
                if (h != null) spell = intArrayOf(Veilguard.RALLY, h, -1)
            }
            g.canCast(p, Veilguard.HARVEST) -> { val low = (0..4).sortedBy { g.res[p][it] }; spell = intArrayOf(Veilguard.HARVEST, low[0], low[1]) }
        }
        spell?.let { g.castSpell(p, it[0], it[1], it[2]) }
        // spare cards buy spells
        var buys = 0
        if (g.total(p) >= 12 && g.spells[p].sum() < 3 && rng.nextDouble() < lvl.aggression && g.buySpell(p)) buys++
        return VPlan(trades, out, buys, spell)
    }
}
