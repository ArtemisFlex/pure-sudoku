package com.puresudoku.core.board

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Sea Battle: the classic hidden-fleet guessing game for two. Each side hides a fleet on a square grid, then they take
 * turns firing at a square of the other's waters: a miss, a hit, or (once every square of a ship is hit) a sinking.
 * Sink the whole enemy fleet to win.
 */
class Cratebarge(val size: Int, val fleet: IntArray) {
    companion object {
        const val PLACE = 0; const val BATTLE = 1; const val OVER = 2
        const val NONE = 0; const val MISS = 1; const val HIT = 2
        /** The result of a shot: a miss, a hit, or a sinking (SUNK + ship index). */
        const val R_MISS = 0; const val R_HIT = 1; const val R_SUNK = 100

        /** Grid size and fleet by level: a small, quick sea on Easy, the full fleet after. */
        fun forLevel(@Suppress("UNUSED_PARAMETER") level: Int): Cratebarge = Cratebarge(10, intArrayOf(5, 4, 3, 3, 2, 2))

        fun shipName(len: Int, index: Int, fleet: IntArray): String {
            val names = listOf("Crownwake Flagbarge", "Ironbelly Cargo Ark", "Thornplate Ferry", "Moonhook Skiff", "Pipknife Cutter", "Barrelbug Dinghy")
            return if (fleet.contentEquals(intArrayOf(5, 4, 3, 3, 2, 2)) && index in names.indices) names[index] else "Ship $len"
        }

        fun fromJson(o: JSONObject): Cratebarge {
            fun ia(k: String) = o.getJSONArray(k).let { a -> IntArray(a.length()) { a.getInt(it) } }
            val g = Cratebarge(o.getInt("size"), ia("fleet"))
            g.start[0] = ia("start0"); g.start[1] = ia("start1"); g.vert[0] = ia("vert0"); g.vert[1] = ia("vert1")
            g.shots[0] = ia("shots0"); g.shots[1] = ia("shots1")
            g.phase = o.getInt("phase"); g.placer = o.getInt("placer"); g.turn = o.getInt("turn"); g.winner = o.optInt("winner", -1)
            g.lastShot = o.optInt("lastShot", -1); g.lastBy = o.optInt("lastBy", -1); g.lastResult = o.optInt("lastResult", -1)
            return g
        }
    }

    val cells = size * size
    /** Where each ship's first square is (-1 while unplaced), and whether it runs down (1) or across (0). */
    var start = Array(2) { IntArray(fleet.size) { -1 } }
    var vert = Array(2) { IntArray(fleet.size) }
    /** shots[p]: what [p] has fired at the other side's waters (NONE, MISS or HIT for each square). */
    var shots = Array(2) { IntArray(size * size) }
    var phase = PLACE
    /** Who is placing their fleet during PLACE. */
    var placer = 0
    var turn = 0
    var winner = -1
    var lastShot = -1
    var lastBy = -1
    var lastResult = -1

    fun toJson(): JSONObject {
        fun a(x: IntArray) = JSONArray().also { j -> x.forEach { j.put(it) } }
        return JSONObject().put("size", size).put("fleet", a(fleet))
            .put("start0", a(start[0])).put("start1", a(start[1])).put("vert0", a(vert[0])).put("vert1", a(vert[1]))
            .put("shots0", a(shots[0])).put("shots1", a(shots[1]))
            .put("phase", phase).put("placer", placer).put("turn", turn).put("winner", winner)
            .put("lastShot", lastShot).put("lastBy", lastBy).put("lastResult", lastResult)
    }

    fun copy() = fromJson(toJson())

    fun name(i: Int) = shipName(fleet[i], i, fleet)
    fun coord(c: Int) = "${'A' + c % size}${c / size + 1}"

    /** The squares ship [i] of side [p] would cover from [s], running down if [v]; null if it would leave the grid. */
    fun span(i: Int, s: Int, v: Boolean): List<Int>? {
        if (s !in 0 until cells) return null
        val r = s / size; val c = s % size; val len = fleet[i]
        if (v && r + len > size) return null
        if (!v && c + len > size) return null
        return List(len) { k -> if (v) s + k * size else s + k }
    }

    fun shipCells(p: Int, i: Int): List<Int> = if (start[p][i] < 0) emptyList() else span(i, start[p][i], vert[p][i] == 1) ?: emptyList()

    /** The ship of side [p] covering square [c], or -1. */
    fun shipAt(p: Int, c: Int): Int = fleet.indices.firstOrNull { c in shipCells(p, it) } ?: -1

    fun canPlace(p: Int, i: Int, s: Int, v: Boolean, apart: Boolean = false): Boolean {
        val sq = span(i, s, v) ?: return false
        for (j in fleet.indices) {
            if (j == i) continue
            val other = shipCells(p, j)
            for (c in sq) {
                if (c in other) return false
                if (apart && other.any { o -> kotlin.math.abs(o / size - c / size) <= 1 && kotlin.math.abs(o % size - c % size) <= 1 }) return false
            }
        }
        return true
    }

    fun place(p: Int, i: Int, s: Int, v: Boolean): Boolean {
        if (phase != PLACE || !canPlace(p, i, s, v)) return false
        start[p][i] = s; vert[p][i] = if (v) 1 else 0
        return true
    }

    fun remove(p: Int, i: Int) { if (phase == PLACE) start[p][i] = -1 }
    fun clear(p: Int) { if (phase == PLACE) start[p].fill(-1) }
    fun allPlaced(p: Int) = start[p].all { it >= 0 }

    /** Scatter side [p]'s fleet at random; [apart] keeps ships from touching. */
    fun randomFleet(p: Int, rnd: Random, apart: Boolean = false) {
        repeat(200) {
            start[p].fill(-1)
            var ok = true
            for (i in fleet.indices.sortedByDescending { fleet[it] }) {
                var placed = false
                repeat(300) {
                    if (placed) return@repeat
                    val v = rnd.nextBoolean(); val s = rnd.nextInt(cells)
                    if (canPlace(p, i, s, v, apart)) { start[p][i] = s; vert[p][i] = if (v) 1 else 0; placed = true }
                }
                if (!placed) { ok = false; break }
            }
            if (ok) return
        }
    }

    /** Side [p] has finished placing: the other side places, or battle begins. */
    fun ready(p: Int): Boolean {
        if (phase != PLACE || p != placer || !allPlaced(p)) return false
        if (p == 0) placer = 1 else { phase = BATTLE; turn = 0 }
        return true
    }

    /** Ship [i] of side [p] is sunk once the other side has hit every square of it. */
    fun sunk(p: Int, i: Int): Boolean { val sq = shipCells(p, i); return sq.isNotEmpty() && sq.all { shots[1 - p][it] == HIT } }
    fun shipsLeft(p: Int) = fleet.indices.count { !sunk(p, it) }

    fun canFire(p: Int, c: Int) = phase == BATTLE && p == turn && c in 0 until cells && shots[p][c] == NONE

    /** Side [p] fires at square [c] of the other side's waters. Returns R_MISS, R_HIT or R_SUNK + ship, or -1 if not allowed. */
    fun fire(p: Int, c: Int): Int {
        if (!canFire(p, c)) return -1
        val q = 1 - p
        val ship = shipAt(q, c)
        val result: Int
        if (ship < 0) { shots[p][c] = MISS; result = R_MISS }
        else { shots[p][c] = HIT; result = if (sunk(q, ship)) R_SUNK + ship else R_HIT }
        lastShot = c; lastBy = p; lastResult = result
        if (shipsLeft(q) == 0) { winner = p; phase = OVER } else turn = q
        return result
    }

    /** Shots fired and hits scored by [p], for accuracy. */
    fun shotsBy(p: Int) = shots[p].count { it != NONE }
    fun hitsBy(p: Int) = shots[p].count { it == HIT }
}

/** The rival admiral: random shots on Easy, then hunting and targeting, then a probability map of where ships can still hide. */
object CratebargeAi {
    fun place(g: Cratebarge, p: Int, level: Int, rnd: Random) = g.randomFleet(p, rnd, apart = level >= 3)

    fun shot(g: Cratebarge, p: Int, level: Int, rnd: Random): Int {
        val q = 1 - p
        val n = g.size
        val shots = g.shots[p]
        val open = (0 until g.cells).filter { shots[it] == Cratebarge.NONE }
        // the hits on ships not yet sunk (a sunk ship is announced, so its squares are known)
        val sunkSquares = g.fleet.indices.filter { g.sunk(q, it) }.flatMap { g.shipCells(q, it) }.toSet()
        val live = (0 until g.cells).filter { shots[it] == Cratebarge.HIT && it !in sunkSquares }
        val left = g.fleet.indices.filter { !g.sunk(q, it) }.map { g.fleet[it] }
        fun nbrs(c: Int) = listOfNotNull(if (c / n > 0) c - n else null, if (c / n < n - 1) c + n else null, if (c % n > 0) c - 1 else null, if (c % n < n - 1) c + 1 else null)

        if (level <= 0 && (live.isEmpty() || rnd.nextInt(100) < 45)) return open.random(rnd)
        if (level <= 2) {
            if (live.isNotEmpty()) {
                // follow a line of hits if there is one
                if (level >= 2) {
                    for (h in live.shuffled(rnd)) for (d in listOf(1, n)) {
                        if (live.contains(h + d) && (d == n || (h + d) / n == h / n)) {
                            var a = h; while (live.contains(a - d) && (d == n || (a - d) / n == a / n) && a - d >= 0) a -= d
                            var b = h + d; while (live.contains(b + d) && (d == n || (b + d) / n == b / n) && b + d < g.cells) b += d
                            val ends = listOfNotNull(
                                (a - d).takeIf { it >= 0 && (d == n || it / n == a / n) && shots[it] == Cratebarge.NONE },
                                (b + d).takeIf { it < g.cells && (d == n || it / n == b / n) && shots[it] == Cratebarge.NONE })
                            if (ends.isNotEmpty()) return ends.random(rnd)
                        }
                    }
                }
                val around = live.flatMap { nbrs(it) }.filter { shots[it] == Cratebarge.NONE }.distinct()
                if (around.isNotEmpty()) return around.random(rnd)
            }
            // hunting: on Hard, only every other square (the smallest ship can't hide between them)
            val step = if (level >= 2) (left.minOrNull() ?: 2) else 1
            val pool = open.filter { step <= 1 || (it / n + it % n) % step == 0 }.ifEmpty { open }
            return pool.random(rnd)
        }
        // the probability map: count every way each remaining ship could still lie
        val heat = IntArray(g.cells)
        for (len in left) for (v in listOf(false, true)) for (s in 0 until g.cells) {
            val r = s / n; val c = s % n
            if (v && r + len > n) continue
            if (!v && c + len > n) continue
            val sq = List(len) { k -> if (v) s + k * n else s + k }
            if (sq.any { shots[it] == Cratebarge.MISS || it in sunkSquares }) continue
            val covers = sq.count { it in live }
            if (live.isNotEmpty() && covers == 0) continue
            val w = if (covers > 0) 20 * covers * covers else 1
            for (x in sq) if (shots[x] == Cratebarge.NONE) heat[x] += w
        }
        val best = open.maxOfOrNull { heat[it] } ?: return open.random(rnd)
        if (best == 0) return open.random(rnd)
        // a little doubt on Expert; Master and Extreme always take the hottest square
        val slack = if (level == 3) best * 15 / 100 else 0
        return open.filter { heat[it] >= best - slack }.random(rnd)
    }
}
