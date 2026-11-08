package com.puresudoku.core

import java.security.SecureRandom
import kotlin.random.Random

/**
 * A Kakuro board. Black cells hold clues: [downSum] for the run below,
 * [acrossSum] for the run to the right (0 = no run). White cells are filled 1–9.
 */
class KakuroPuzzle(
    val width: Int,
    val height: Int,
    val white: BooleanArray,
    val solution: IntArray,
    val givens: IntArray,
    val acrossSum: IntArray,
    val downSum: IntArray,
) {
    /** Every horizontal and vertical run of white cells. */
    val runs: List<IntArray> = KakuroRules.runs(width, height, white)
}

object KakuroRules {
    data class Config(val w: Int, val h: Int, val blackRate: Int, val maxRun: Int, val basePoints: Int, val parSeconds: Int)

    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(6, 6, 30, 4, 10, 240)
        Difficulty.MEDIUM -> Config(7, 7, 28, 5, 15, 420)
        Difficulty.HARD -> Config(8, 8, 26, 6, 25, 720)
        Difficulty.EXPERT -> Config(9, 9, 24, 7, 35, 1080)
        Difficulty.MASTER -> Config(10, 10, 25, 6, 50, 1560)
        Difficulty.EXTREME -> Config(10, 12, 24, 7, 75, 2280)
    }

    fun runs(w: Int, h: Int, white: BooleanArray): List<IntArray> {
        val out = ArrayList<IntArray>()
        for (r in 0 until h) {
            var c = 0
            while (c < w) {
                if (!white[r * w + c]) { c++; continue }
                val start = c
                while (c < w && white[r * w + c]) c++
                out += IntArray(c - start) { r * w + start + it }
            }
        }
        for (c in 0 until w) {
            var r = 0
            while (r < h) {
                if (!white[r * w + c]) { r++; continue }
                val start = r
                while (r < h && white[r * w + c]) r++
                out += IntArray(r - start) { (start + it) * w + c }
            }
        }
        return out
    }

    fun spec(p: KakuroPuzzle, d: Difficulty): DigitSpec {
        val cfg = config(d)
        return DigitSpec(
            p.width, p.height, 9, p.white.copyOf(), p.runs, p.runs,
            ScoreRules(cfg.basePoints, cfg.parSeconds), showRemaining = false,
        )
    }

    /** possible[mask][k][s]: can k distinct digits not in mask sum to s? */
    val possible: Array<Array<BooleanArray>> by lazy {
        val t = Array(1024) { Array(10) { BooleanArray(46) } }
        for (mask in 0 until 1024) t[mask][0][0] = true
        // iterate masks from full to empty so "mask | d" is ready first
        for (mask in 1023 downTo 0) {
            for (k in 1..9) for (s in 1..45) {
                var ok = false
                for (d in 1..9) {
                    if (mask and (1 shl d) != 0 || d > s) continue
                    if (t[mask or (1 shl d)][k - 1][s - d]) { ok = true; break }
                }
                t[mask][k][s] = ok
            }
        }
        t
    }
}

/** Counts Kakuro solutions (up to a limit) with run-sum pruning. */
class KakuroSolver(private val w: Int, private val h: Int, private val white: BooleanArray, private val sums: Map<Int, Int>) {
    private val runs = KakuroRules.runs(w, h, white)
    private val runSum = IntArray(runs.size) { sums[it] ?: 0 }
    private val runsOf = Array(w * h) { i -> runs.indices.filter { i in runs[it] }.toIntArray() }
    private val grid = IntArray(w * h)
    private val used = IntArray(runs.size)
    private val left = IntArray(runs.size)
    private val filled = IntArray(runs.size)
    val solutions = ArrayList<IntArray>()
    private var nodes = 0L
    private class Budget : RuntimeException(null, null, false, false)

    /** Returns the solution count (up to [limit]), or -1 if the search ran over [budget] steps. */
    fun count(givens: IntArray, limit: Int = 2, budget: Long = 400_000): Int {
        grid.fill(0); used.fill(0); filled.fill(0); solutions.clear(); nodes = 0
        this.budget = budget
        for (r in runs.indices) left[r] = runSum[r]
        for (i in givens.indices) if (givens[i] != 0) {
            if (!placeOk(i, givens[i])) return 0
            set(i, givens[i])
        }
        return try { search(limit) } catch (e: Budget) { -1 }
    }

    private var budget = 0L

    private fun set(i: Int, d: Int) {
        grid[i] = d
        for (r in runsOf[i]) { used[r] = used[r] or (1 shl d); left[r] -= d; filled[r]++ }
    }

    private fun unset(i: Int, d: Int) {
        grid[i] = 0
        for (r in runsOf[i]) { used[r] = used[r] and (1 shl d).inv(); left[r] += d; filled[r]-- }
    }

    private fun placeOk(i: Int, d: Int): Boolean {
        for (r in runsOf[i]) {
            if (used[r] and (1 shl d) != 0) return false
            val remK = runs[r].size - filled[r] - 1
            val remS = left[r] - d
            if (remS < 0 || remS > 45 || !KakuroRules.possible[used[r] or (1 shl d)][remK][remS]) return false
        }
        return true
    }

    private fun search(limit: Int): Int {
        if (++nodes > budget) throw Budget()
        var best = -1; var bestMask = 0; var bestCount = 99
        for (i in 0 until w * h) {
            if (!white[i] || grid[i] != 0) continue
            var m = 0
            for (d in 1..9) if (placeOk(i, d)) m = m or (1 shl d)
            val c = Integer.bitCount(m)
            if (c == 0) return 0
            if (c < bestCount) { best = i; bestMask = m; bestCount = c; if (c == 1) break }
        }
        if (best < 0) { solutions += grid.copyOf(); return 1 }
        var total = 0
        var m = bestMask
        while (m != 0) {
            val bit = m and -m; m = m xor bit
            val d = Integer.numberOfTrailingZeros(bit)
            set(best, d)
            total += search(limit - total)
            unset(best, d)
            if (total >= limit) return total
        }
        return total
    }
}

/**
 * Kakuro generator:
 *  1. random symmetric layout: row 0 / column 0 are clue cells, every run is
 *     2+ cells and no longer than the level allows, all white cells connected;
 *  2. random fill with no repeats in any run (prefers low/high digits, which
 *     make sums with fewer combinations);
 *  3. solver check; if more than one answer exists, try a new fill, and as a
 *     last resort reveal a cell where the answers differ.
 */
class KakuroGenerator(seed: Long = SecureRandom().nextLong() xor System.nanoTime()) {
    private val rng = Random(seed)

    fun generate(d: Difficulty): KakuroPuzzle {
        val cfg = KakuroRules.config(d)
        var best: KakuroPuzzle? = null
        repeat(8) {
            val layout = layout(cfg) ?: return@repeat
            repeat(4) {
                val p = fillAndCheck(cfg, layout) ?: return@repeat
                val g = p.givens.count { it != 0 }
                if (best == null || g < best!!.givens.count { it != 0 }) best = p
                if (g == 0) return best!!
            }
        }
        return best ?: generate(d)
    }

    private fun layout(cfg: KakuroRules.Config): BooleanArray? {
        val w = cfg.w; val h = cfg.h
        repeat(200) {
            val white = BooleanArray(w * h) { i -> i / w > 0 && i % w > 0 }
            val inner = (1 until h).flatMap { r -> (1 until w).map { c -> r * w + c } }
            val target = inner.size * cfg.blackRate / 100
            var blacks = 0
            for (i in inner.shuffled(rng)) {
                if (blacks >= target) break
                val mirror = (h - (i / w)) * w + (w - (i % w))
                if (!white[i]) continue
                white[i] = false; blacks++
                if (mirror in inner && mirror != i && white[mirror]) { white[mirror] = false; blacks++ }
            }
            if (fixRuns(white, w, h, cfg.maxRun) && connected(white, w, h) && white.count { it } >= inner.size / 2) return white
        }
        return null
    }

    /** Removes 1-cell runs and splits too-long runs. Returns false if the layout can't be fixed. */
    private fun fixRuns(white: BooleanArray, w: Int, h: Int, maxRun: Int): Boolean {
        repeat(40) {
            var changed = false
            for (run in KakuroRules.runs(w, h, white)) {
                if (run.size == 1) { white[run[0]] = false; changed = true }
                else if (run.size > maxRun) { white[run[1 + rng.nextInt(run.size - 2)]] = false; changed = true }
            }
            // a white cell must be in both an across run and a down run of 2+
            for (i in 0 until w * h) if (white[i]) {
                val r = i / w; val c = i % w
                val across = (c > 0 && white[i - 1]) || (c < w - 1 && white[i + 1])
                val down = (r > 0 && white[i - w]) || (r < h - 1 && white[i + w])
                if (!across || !down) { white[i] = false; changed = true }
            }
            if (!changed) return true
        }
        return false
    }

    private fun connected(white: BooleanArray, w: Int, h: Int): Boolean {
        val start = white.indexOfFirst { it }
        if (start < 0) return false
        val seen = BooleanArray(w * h); seen[start] = true
        val q = ArrayDeque(listOf(start)); var n = 1
        while (q.isNotEmpty()) {
            val x = q.removeFirst()
            for (y in intArrayOf(x - w, x + w, if (x % w > 0) x - 1 else -1, if (x % w < w - 1) x + 1 else -1)) {
                if (y in 0 until w * h && white[y] && !seen[y]) { seen[y] = true; n++; q += y }
            }
        }
        return n == white.count { it }
    }

    private fun fillAndCheck(cfg: KakuroRules.Config, white: BooleanArray): KakuroPuzzle? {
        val w = cfg.w; val h = cfg.h
        val runs = KakuroRules.runs(w, h, white)
        val runsOf = Array(w * h) { i -> runs.indices.filter { i in runs[it] } }
        val sol = IntArray(w * h)
        val used = IntArray(runs.size)
        val order = (0 until w * h).filter { white[it] }
        // Weighted digit order: extremes first makes sums more unique.
        fun digitOrder(): List<Int> = (1..9).sortedBy { d -> rng.nextDouble() * (if (d <= 2 || d >= 8) 0.6 else 1.0) }
        fun fill(k: Int): Boolean {
            if (k == order.size) return true
            val i = order[k]
            for (d in digitOrder()) {
                val bit = 1 shl d
                if (runsOf[i].any { used[it] and bit != 0 }) continue
                sol[i] = d; for (r in runsOf[i]) used[r] = used[r] or bit
                if (fill(k + 1)) return true
                for (r in runsOf[i]) used[r] = used[r] and bit.inv()
            }
            sol[i] = 0
            return false
        }
        if (!fill(0)) return null

        val sums = runs.indices.associateWith { r -> runs[r].sumOf { sol[it] } }
        val solver = KakuroSolver(w, h, white, sums)
        val givens = IntArray(w * h)
        var guard = 0
        while (guard++ < 12) {
            val c = solver.count(givens, 2)
            if (c == 1) break
            if (c <= 0) return null
            val (a, b) = solver.solutions
            val diff = order.filter { a[it] != b[it] }
            val cell = diff[rng.nextInt(diff.size)]
            givens[cell] = sol[cell]
        }
        if (solver.count(givens, 2) != 1) return null

        val across = IntArray(w * h); val down = IntArray(w * h)
        for ((r, run) in runs.withIndex()) {
            val first = run[0]
            val horizontal = run.size == 1 || run[1] == first + 1
            val clueCell = if (horizontal) first - 1 else first - w
            if (horizontal) across[clueCell] = sums.getValue(r) else down[clueCell] = sums.getValue(r)
        }
        return KakuroPuzzle(w, h, white, sol, givens, across, down)
    }
}
