package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.random.Random

/**
 * Go with area scoring (Chinese rules). 1 = black (you, first), 2 = white (computer), komi to white.
 * Suicide is illegal, simple ko applies. Two passes in a row end the game; dead stones are
 * estimated with random playouts before counting.
 */
class GoGame(val n: Int, val komi: Double = 7.5) {
    val cells = IntArray(n * n)
    var turn = 1
    var ko = -1
    var passes = 0
    var moveCount = 0
    val captures = IntArray(3)
    /** Last move point, or -1 for a pass / none. */
    var last = -1

    private val nb: Array<IntArray> = Array(n * n) { i ->
        val r = i / n; val c = i % n
        listOfNotNull(if (r > 0) i - n else null, if (r < n - 1) i + n else null, if (c > 0) i - 1 else null, if (c < n - 1) i + 1 else null).toIntArray()
    }

    data class GoLevel(val playouts: Int, val timeMs: Long, val noise: Double)

    companion object {
        fun size(d: Difficulty) = if (d.ordinal <= 2) 9 else 13
        fun level(d: Difficulty) = when (d) {
            Difficulty.EASY -> GoLevel(250, 500, 0.12)
            Difficulty.MEDIUM -> GoLevel(700, 900, 0.05)
            Difficulty.HARD -> GoLevel(2500, 1800, 0.0)
            Difficulty.EXPERT -> GoLevel(1500, 1800, 0.03)
            Difficulty.MASTER -> GoLevel(3500, 3000, 0.0)
            Difficulty.EXTREME -> GoLevel(8000, 5000, 0.0)
        }
        /** Star points for drawing. */
        fun hoshi(n: Int): List<Int> = when (n) {
            9 -> listOf(2 * 9 + 2, 2 * 9 + 6, 4 * 9 + 4, 6 * 9 + 2, 6 * 9 + 6)
            13 -> listOf(3 * 13 + 3, 3 * 13 + 9, 6 * 13 + 6, 9 * 13 + 3, 9 * 13 + 9)
            else -> emptyList()
        }
    }

    fun copy(): GoGame {
        val g = GoGame(n, komi); cells.copyInto(g.cells); g.turn = turn; g.ko = ko; g.passes = passes; g.moveCount = moveCount
        captures.copyInto(g.captures); g.last = last; return g
    }

    /** Stones of the group at [i] and whether it has a liberty. */
    private fun groupInfo(i: Int, mark: BooleanArray, stones: MutableList<Int>): Int {
        val color = cells[i]
        var libs = 0
        val stack = ArrayDeque<Int>(); stack.add(i); mark[i] = true
        val seenLib = HashSet<Int>()
        while (stack.isNotEmpty()) {
            val s = stack.removeLast(); stones += s
            for (x in nb[s]) {
                if (cells[x] == 0) { if (seenLib.add(x)) libs++ }
                else if (cells[x] == color && !mark[x]) { mark[x] = true; stack.add(x) }
            }
        }
        return libs
    }

    fun liberties(i: Int): Int = groupInfo(i, BooleanArray(n * n), ArrayList())

    fun legal(i: Int, color: Int = turn): Boolean {
        if (i < 0) return true
        if (cells[i] != 0 || i == ko) return false
        if (nb[i].any { cells[it] == 0 }) return true
        cells[i] = color
        var ok = false
        for (x in nb[i]) if (cells[x] == 3 - color && liberties(x) == 0) { ok = true; break }
        if (!ok) ok = liberties(i) > 0
        cells[i] = 0
        return ok
    }

    /** Plays at [i] (or passes with -1). Returns captured stones, or null if illegal. */
    fun play(i: Int): List<Int>? {
        if (i < 0) { passes++; ko = -1; turn = 3 - turn; last = -1; moveCount++; return emptyList() }
        if (!legal(i)) return null
        val color = turn
        cells[i] = color
        val dead = ArrayList<Int>()
        for (x in nb[i]) if (cells[x] == 3 - color) {
            val st = ArrayList<Int>()
            if (groupInfo(x, BooleanArray(n * n), st) == 0) for (s in st) if (cells[s] != 0) { cells[s] = 0; dead += s }
        }
        captures[color] += dead.size
        ko = if (dead.size == 1 && nb[i].none { cells[it] == color } && liberties(i) == 1) dead[0] else -1
        passes = 0; turn = 3 - color; last = i; moveCount++
        return dead
    }

    val over get() = passes >= 2

    /** True eye-ish point: all neighbours are [color] (used to keep playouts from filling eyes). */
    private fun eyeLike(i: Int, color: Int) = nb[i].all { cells[it] == color }

    /** Random playout to the end; returns the area score from black's view (minus komi). */
    private fun playout(rng: Random): Double {
        var guard = n * n * 3
        var consecutivePasses = 0
        val empties = ArrayList<Int>(n * n)
        while (consecutivePasses < 2 && guard-- > 0) {
            empties.clear(); for (i in cells.indices) if (cells[i] == 0) empties += i
            var played = false
            while (empties.isNotEmpty()) {
                val k = rng.nextInt(empties.size)
                val i = empties[k]; empties[k] = empties[empties.lastIndex]; empties.removeAt(empties.lastIndex)
                if (eyeLike(i, turn)) continue
                if (legal(i)) { play(i); played = true; break }
            }
            if (played) consecutivePasses = 0 else { play(-1); consecutivePasses++ }
        }
        return areaScore(cells)
    }

    /** Area score (stones + surrounded empty) for black minus white, minus komi. */
    fun areaScore(board: IntArray = cells): Double {
        var black = 0; var white = 0
        val seen = BooleanArray(n * n)
        for (i in board.indices) {
            when (board[i]) {
                1 -> black++
                2 -> white++
                0 -> if (!seen[i]) {
                    val region = ArrayList<Int>(); val border = HashSet<Int>()
                    val st = ArrayDeque<Int>(); st.add(i); seen[i] = true
                    while (st.isNotEmpty()) {
                        val s = st.removeLast(); region += s
                        for (x in nb[s]) { if (board[x] == 0) { if (!seen[x]) { seen[x] = true; st.add(x) } } else border += board[x] }
                    }
                    if (border.size == 1) { if (1 in border) black += region.size else white += region.size }
                }
            }
        }
        return black - white - komi
    }

    /** Flat Monte-Carlo with UCB over candidate moves; returns -1 to pass. */
    fun aiMove(level: GoLevel, rng: Random): Int {
        val me = turn
        val cands = (0 until n * n).filter { cells[it] == 0 && !eyeLike(it, me) && legal(it) }
        if (cands.isEmpty()) return -1
        if (rng.nextDouble() < level.noise) return cands[rng.nextInt(cands.size)]
        val wins = DoubleArray(cands.size); val visits = IntArray(cands.size)
        val deadline = System.currentTimeMillis() + level.timeMs
        var total = 0
        while (total < level.playouts && System.currentTimeMillis() < deadline) {
            val k = if (total < cands.size) total else {
                var best = 0; var bv = -1.0
                for (j in cands.indices) {
                    val v = wins[j] / visits[j] + 1.2 * Math.sqrt(Math.log(total.toDouble()) / visits[j])
                    if (v > bv) { bv = v; best = j }
                }
                best
            }
            val g = copy(); g.play(cands[k])
            val s = g.playout(rng)
            val won = if (me == 1) s > 0 else s < 0
            wins[k] += if (won) 1.0 else 0.0; visits[k]++; total++
        }
        var best = 0
        for (j in cands.indices) if (visits[j] > visits[best]) best = j
        val rate = wins[best] / visits[best].coerceAtLeast(1)
        // resign-like pass when everything loses, or pass back when ahead and the opponent passed
        if (passes == 1 && finalScoreFor(me, rng, 60) > 0) return -1
        if (rate < 0.03 && moveCount > n * n / 2) return -1
        return cands[best]
    }

    /** Estimated final score from [me]'s point of view after removing likely-dead stones. */
    fun finalScoreFor(me: Int, rng: Random, samples: Int = 120): Double {
        val s = estimate(rng, samples).score
        return if (me == 1) s else -s
    }

    class Estimate(val score: Double, val dead: Set<Int>, val owner: IntArray)

    /** Ownership by playouts: stones mostly owned by the other colour are dead. */
    fun estimate(rng: Random, samples: Int = 160): Estimate {
        val own = IntArray(n * n)
        repeat(samples) {
            val g = copy(); g.passes = 0
            g.playout(rng)
            // ownership of each point after the playout
            for (i in 0 until n * n) {
                val c = g.cells[i]
                val o = if (c != 0) c else {
                    val ns = g.nb[i].map { g.cells[it] }.filter { it != 0 }.toSet()
                    if (ns.size == 1) ns.first() else 0
                }
                if (o == 1) own[i]++ else if (o == 2) own[i]--
            }
        }
        val dead = HashSet<Int>()
        for (i in 0 until n * n) {
            if (cells[i] == 1 && own[i] < -samples * 0.6) dead += i
            if (cells[i] == 2 && own[i] > samples * 0.6) dead += i
        }
        val b = cells.copyOf()
        for (d in dead) b[d] = 0
        return Estimate(areaScore(b), dead, own)
    }
}
