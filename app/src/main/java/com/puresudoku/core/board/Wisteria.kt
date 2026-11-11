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
    /** A village's knight attacks a touching enemy tile at once. */
    data class Strike(val from: Int, val to: Int) : WAct()
    /** A tower's or castle's whole garrison marches along [route] (one tile per turn); its last tile is the target. */
    data class March(val from: Int, val route: List<Int>) : WAct()
    /** A wall on the side between your tile [a] and its neighbour [b]. */
    data class Wall(val a: Int, val b: Int) : WAct()
    data class Trade(val give: Int, val get: Int) : WAct()
    data class Spell(val kind: Int, val a: Int = -1, val b: Int = -1) : WAct()
}

/** A marching army: where it stands, the tiles still to walk (last = target), and how many knights. */
class Army(val owner: Int, var pos: Int, val route: ArrayList<Int>, var size: Int) {
    val target get() = route.lastOrNull() ?: pos
}

/**
 * Wisteria: territory conquest on the Eiseria islands. No dice: every tile you hold pays out each turn.
 * Claim neighbouring land, raise towers and castles, hire knights to strike or march on your rival,
 * and wall your borders. A single dragon leaves its lair in the middle every five turns, heading for the leader.
 */
class Wisteria(val map: RealmGrid, val diff: Int, val target: Int) {
    companion object {
        const val LAND = 0; const val WATER = 1; const val LAIR = 2; const val BARREN = 3
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

        val COST_TOWER = intArrayOf(0, 3, 2, 0, 0)
        val COST_CASTLE = intArrayOf(2, 3, 3, 0, 2)
        val COST_KNIGHT = intArrayOf(0, 1, 1, 1, 0)
        val COST_WALL = intArrayOf(1, 0, 0, 0, 1)
        val COST_SPELL = intArrayOf(0, 1, 1, 1, 0)
        private val CLAIM_BASE = intArrayOf(1, 0, 1, 1, 1)

        const val DRAGON_EVERY = 5
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
            return Wisteria(RealmMap.fromCoords(q, r), lv, tg).also { it.load(s) }
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
    val revealed = BooleanArray(H) { true }
    /** Wall owner per board edge (-1: none). A wall guards its owner's tile on that side. */
    val wall = IntArray(map.EDGES) { -1 }
    val armies = ArrayList<Army>()

    val res = Array(2) { IntArray(5) }
    val spells = Array(2) { IntArray(5) }
    val fresh = IntArray(5)
    val deck = ArrayList<Int>()
    var spellPlayed = false
    var freeWalls = 0

    var lair = 0
    var dragon = 0
    var dragonTimer = DRAGON_EVERY

    var turn = 0
    var turns = 0
    var phase = SETUP
    var setupStep = 0
    var pending = -1
    var winner = -1
    val log = ArrayList<String>()

    val foggy get() = diff >= 1
    val actor: Int get() = if (phase == SETUP) setupStep % 2 else turn

    // ---------------------------------------------------------------- setup

    private fun deal(rng: Random) {
        lair = (0 until H).minBy { map.hexX[it] * map.hexX[it] + map.hexY[it] * map.hexY[it] }
        kind[lair] = LAIR
        dragon = lair
        val others = (0 until H).filter { it != lair }.shuffled(rng)
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
        for (h in 0 until H) {
            if (kind[h] != LAND) continue
            val main = rng.nextInt(5); art[h] = main
            val roll = rng.nextInt(100)
            when {
                roll < 15 -> yields[h * 5 + main] = 3
                roll < 35 -> { val o = (main + 1 + rng.nextInt(4)) % 5; yields[h * 5 + main] = 2; yields[h * 5 + o] = 2 }
                else -> {
                    yields[h * 5 + main] = 1
                    val rest = (0..4).filter { it != main }.shuffled(rng).take(2)
                    for (r in rest) yields[h * 5 + r] = 1
                }
            }
        }
        deck.clear()
        repeat(10) { deck += RALLY }; repeat(5) { deck += RELIC }; repeat(3) { deck += STONE }; repeat(3) { deck += HARVEST }; repeat(4) { deck += SIEGE }
        deck.shuffle(rng)
        if (foggy) for (h in 0 until H) revealed[h] = h == lair || map.hexNbrs[lair].contains(h) || kind[h] == WATER
    }

    private fun connected(): Boolean {
        val land = (0 until H).filter { kind[it] != WATER }
        if (land.isEmpty()) return true
        val seen = BooleanArray(H); val q = ArrayDeque<Int>(); q += land[0]; seen[land[0]] = true
        while (q.isNotEmpty()) { val h = q.removeFirst(); for (n in map.hexNbrs[h]) if (!seen[n] && kind[n] != WATER) { seen[n] = true; q += n } }
        return land.all { seen[it] }
    }

    // ---------------------------------------------------------------- queries

    fun yieldOf(h: Int) = IntArray(5) { yields[h * 5 + it] }
    fun total(p: Int) = res[p].sum()
    fun can(p: Int, cost: IntArray) = (0..4).all { res[p][it] >= cost[it] }
    fun tiles(p: Int) = (0 until H).count { owner[it] == p }
    fun vp(p: Int, withRelics: Boolean = true) = (0 until H).sumOf { if (owner[it] == p) level[it] else 0 } + (if (withRelics) spells[p][RELIC] else 0)
    fun ownable(h: Int) = kind[h] == LAND || kind[h] == BARREN

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
        else map.hexNbrs[from].filter { owner[it] == 1 - p }

    /** Tiles a full tower/castle garrison on [from] can march on: enemy tiles, or unclaimed land to take far away. */
    fun marchTargets(p: Int, from: Int): List<Int> =
        if (owner[from] != p || level[from] < 2 || knights[from] < CAPACITY[level[from]]) emptyList()
        else (0 until H).filter { h -> h != from && ownable(h) && revealed[h] && owner[h] != p && reachable(from, h) }

    /** Sides where [p] may raise a wall: around their own tiles, on sides without one. */
    fun wallSpots(p: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (h in 0 until H) if (owner[h] == p) for (n in map.hexNbrs[h]) if (kind[n] != WATER) { val e = side(h, n); if (e >= 0 && wall[e] < 0) out += h to n }
        return out
    }

    fun siegeTargets(p: Int) = (0 until map.EDGES).filter { wall[it] == 1 - p }

    fun canPlay(p: Int, k: Int): Boolean {
        if (phase != MAIN || turn != p || spellPlayed || k == RELIC) return false
        if (spells[p][k] - fresh[k] <= 0) return false
        return when (k) {
            RALLY -> (0 until H).any { owner[it] == p && knights[it] < CAPACITY[level[it]] }
            STONE -> wallSpots(p).isNotEmpty()
            SIEGE -> siegeTargets(p).isNotEmpty()
            else -> true
        }
    }

    private fun reachable(a: Int, b: Int) = path(a, b) != null

    /** A planned march: each step touches the last, never water, at most 24 steps, ending on a tile the garrison may attack. */
    fun validRoute(p: Int, from: Int, route: List<Int>): Boolean {
        if (route.isEmpty() || route.size > 24 || route.last() !in marchTargets(p, from)) return false
        var at = from
        for (h in route) { if (h !in map.hexNbrs[at] || kind[h] == WATER) return false; at = h }
        return true
    }

    /** Shortest land path from [a] to [b] (water blocks), excluding [a]. */
    fun path(a: Int, b: Int): List<Int>? {
        if (a == b) return emptyList()
        val prev = IntArray(H) { -2 }; prev[a] = -1
        val q = ArrayDeque<Int>(); q += a
        while (q.isNotEmpty()) {
            val h = q.removeFirst()
            if (h == b) break
            for (n in map.hexNbrs[h]) if (prev[n] == -2 && kind[n] != WATER) { prev[n] = h; q += n }
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
            is WAct.Strike -> a.to in strikeTargets(p, a.from)
            is WAct.March -> validRoute(p, a.from, a.route)
            is WAct.Wall -> owner[a.a] == p && side(a.a, a.b).let { it >= 0 && wall[it] < 0 } && kind[a.b] != WATER && (freeWalls > 0 || can(p, COST_WALL))
            is WAct.Trade -> a.give != a.get && res[p][a.give] >= 4
            is WAct.Spell -> canPlay(p, a.kind) && when (a.kind) {
                RALLY -> owner.getOrNull(a.a) == p && knights[a.a] < CAPACITY[level[a.a]]
                HARVEST -> a.a in 0..4 && a.b in 0..4
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
                knights[a.from]--
                log += "$who struck from a village"
                attack(p, a.from, a.to, 1)
            }
            is WAct.March -> {
                val n = knights[a.from]; knights[a.from] = 0
                armies += Army(p, a.from, ArrayList(a.route), n)
                log += "$who sent $n knight${if (n > 1) "s" else ""} marching"
            }
            is WAct.Wall -> {
                if (freeWalls > 0) freeWalls-- else pay(p, COST_WALL)
                wall[side(a.a, a.b)] = p
                log += "$who raised a wall"
                if (freeWalls > 0 && wallSpots(p).isEmpty()) freeWalls = 0
            }
            is WAct.Trade -> { res[p][a.give] -= 4; res[p][a.get]++ }
            WAct.BuySpell -> { pay(p, COST_SPELL); val s = deck.removeAt(deck.lastIndex); spells[p][s]++; fresh[s]++; log += if (p == 0) "You learned ${SPELLS[s]}" else "The rival learned a spell" }
            is WAct.Spell -> {
                spells[p][a.kind]--; spellPlayed = true
                when (a.kind) {
                    RALLY -> { knights[a.a]++; log += "$who rallied a knight" }
                    STONE -> { freeWalls = 2; log += "$who cast Stonewright" }
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
        owner[h] = p; level[h] = 1; knights[h] = 0
        if (foggy) { revealed[h] = true; for (n in map.hexNbrs[h]) revealed[n] = true }
    }

    private fun lose(h: Int) { owner[h] = -1; level[h] = 0; knights[h] = 0 }

    fun tileName(h: Int): String = when (kind[h]) {
        LAIR -> "the dragon's lair"; WATER -> "the lake"; BARREN -> "a barren waste"
        else -> "the " + listOf("forest", "mountain", "fields", "meadow", "crystal spires")[art[h]]
    }

    /**
     * [n] knights from [from] hit [to]. A wall on that side stops the whole attack (and breaks).
     * Each knight knocks a castle to a tower, a tower to a village (dying each time), or burns a village.
     */
    private fun attack(p: Int, from: Int, to: Int, n: Int) {
        val e = side(from, to)
        val who = if (p == 0) "Your" else "The rival's"
        if (e >= 0 && wall[e] == owner[to] && owner[to] >= 0) {
            wall[e] = -1
            log += "$who attack broke on a wall"
            return
        }
        if (owner[to] < 0) { offerClaim(p, to); return }
        var hits = n
        while (hits > 0 && owner[to] >= 0) {
            if (level[to] >= 2) { level[to]--; hits--; knights[to] = minOf(knights[to], CAPACITY[level[to]]); log += "$who knights brought a ${LEVEL_NAME[level[to] + 1]} down to a ${LEVEL_NAME[level[to]]}" }
            else { lose(to); hits--; log += "$who knights burned a village" }
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
        for (h in 0 until H) if (owner[h] == p) for (r in 0..4) got[r] += yields[h * 5 + r]
        for (r in 0..4) res[p][r] += got[r]
        if (got.sum() > 0) log += (if (p == 0) "Your lands paid " else "The rival's lands paid ") + (0..4).filter { got[it] > 0 }.joinToString(", ") { "${got[it]} ${Realm.RES[it]}" }
        // armies march one step along their planned routes; armies that meet fight
        for (army in armies.filter { it.owner == p }.toList()) {
            if (army !in armies) continue
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
        fresh.fill(0); spellPlayed = false; freeWalls = 0
        turns++
        if (turns >= 200) { winner = leader().let { if (it < 0) 2 else it }; phase = OVER; return }
        dragonTimer--
        if (dragonTimer <= 0) dragonMove()
        turn = 1 - turn
        phase = MAIN
        checkWin()
        if (phase == MAIN) startTurn(rng)
    }

    private fun dragonMove() {
        val lead = leader()
        if (lead < 0) { dragonTimer = DRAGON_EVERY; log += "The dragon stirs, but no one leads"; return }
        val goals = (0 until H).filter { owner[it] == lead }
        val route = goals.mapNotNull { path(dragon, it) }.filter { it.isNotEmpty() }.minByOrNull { it.size }
        if (route == null) { dragonTimer = DRAGON_EVERY; return }
        val next = route.first()
        val e = side(dragon, next)
        if (e >= 0 && wall[e] >= 0 && wall[e] == owner[next]) {
            wall[e] = -1; dragonTimer = 1
            log += "The dragon smashed a wall and is held back a turn"
            return
        }
        dragon = next; dragonTimer = DRAGON_EVERY
        val o = owner[next]
        if (o < 0) { log += "The dragon flies over ${tileName(next)}"; return }
        val whose = if (o == 0) "your" else "the rival's"
        if (level[next] >= 2) {
            level[next]--; knights[next] = minOf(knights[next], CAPACITY[level[next]])
            log += "The dragon battered $whose ${LEVEL_NAME[level[next] + 1]} down to a ${LEVEL_NAME[level[next]]}"
        } else { lose(next); log += "The dragon burned $whose village!" }
        armies.removeAll { it.pos == next }
        dragon = lair
        log += "The dragon returns to its lair"
    }

    private fun checkWin() {
        if (phase == SETUP || winner >= 0) return
        for (p in listOf(turn, 1 - turn)) if (vp(p) >= target) { winner = p; phase = OVER; return }
        // a player with no land left (after setup) loses
        for (p in 0..1) if (tiles(p) == 0 && armies.none { it.owner == p } && phase != CHOICE) { winner = 1 - p; phase = OVER; return }
    }

    // ---------------------------------------------------------------- save / copy

    fun state(): IntArray {
        val o = ArrayList<Int>()
        fun put(a: IntArray) { o += a.size; a.forEach { o += it } }
        put(map.hexQ); put(map.hexR); o += diff; o += target
        put(kind); put(art); put(yields); put(owner); put(level); put(knights)
        put(revealed.map { if (it) 1 else 0 }.toIntArray()); put(wall)
        put(armies.flatMap { listOf(it.owner, it.pos, it.size, it.route.size) + it.route }.toIntArray())
        put(res[0]); put(res[1]); put(spells[0]); put(spells[1]); put(fresh); put(deck.toIntArray())
        put(intArrayOf(if (spellPlayed) 1 else 0, freeWalls, lair, dragon, dragonTimer, turn, turns, phase, setupStep, pending, winner))
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
        if (g.kind[h] != Wisteria.LAND) return 0.4
        var v = 0.0; for (r in 0..4) v += g.yields[h * 5 + r] * W[r]
        return v
    }

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
        // 3. full garrisons march on the weakest valuable target
        for (h in 0 until g.H) {
            val ts = g.marchTargets(p, h); if (ts.isEmpty()) continue
            val n = g.knights[h]
            val best = ts.maxByOrNull { t ->
                val d = g.path(h, t)?.size ?: 99
                val gain = if (g.owner[t] == o) (if (g.level[t] <= n) 6.0 + g.level[t] * 2 else 1.0) else spotValue(g, p, t)
                gain - d * 0.8 + noise()
            }
            if (best != null && rng.nextDouble() < lvl.aggression) g.path(h, best)?.let { return WAct.March(h, it) }
        }
        // 4. claim land
        val claims = g.claimable(p)
        val affordable = claims.filter { g.can(p, g.claimCost(p, it)) }
        if (affordable.isNotEmpty()) return WAct.Claim(affordable.maxBy { spotValue(g, p, it) + noise() })
        if (rng.nextDouble() < lvl.lazy) return WAct.End
        // 5. towers on the richest or most exposed tiles, castles once rich
        val up = (0 until g.H).filter { g.canUpgrade(p, it) }.maxByOrNull { h ->
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
        if (g.canPlay(p, Wisteria.STONE) && lvl.walls) return WAct.Spell(Wisteria.STONE)
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
