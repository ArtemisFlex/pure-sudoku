package com.puresudoku.core

import kotlin.random.Random

// =====================================================================
// Grid Recall
// =====================================================================

object RecallRules {
    data class Config(
        val n: Int, val start: Int, val rounds: Int,
        val baseShowMs: Long, val perItemMs: Long,
        /** Items flash one at a time and must be recalled in that order. */
        val ordered: Boolean,
        val basePoints: Int, val parSeconds: Int,
    )

    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(3, 2, 8, 1200, 650, false, 10, 150)
        Difficulty.MEDIUM -> Config(4, 3, 9, 1000, 550, false, 15, 210)
        Difficulty.HARD -> Config(4, 3, 10, 800, 450, false, 25, 260)
        Difficulty.EXPERT -> Config(5, 3, 10, 600, 700, true, 35, 330)
        Difficulty.MASTER -> Config(5, 4, 10, 500, 600, true, 50, 400)
        Difficulty.EXTREME -> Config(6, 4, 12, 400, 520, true, 75, 480)
    }

    /** One more item every second round. */
    fun itemCount(cfg: Config, round: Int) = minOf(cfg.start + round / 2, cfg.n * cfg.n - 1)

    /** A random pattern: distinct cells, each with a digit 1–9. */
    fun pattern(cfg: Config, round: Int, rng: Random): Pair<IntArray, IntArray> {
        val k = itemCount(cfg, round)
        val cells = (0 until cfg.n * cfg.n).shuffled(rng).take(k).toIntArray()
        val digits = IntArray(k) { 1 + rng.nextInt(9) }
        return cells to digits
    }

    fun showMs(cfg: Config, round: Int) = cfg.baseShowMs + cfg.perItemMs * itemCount(cfg, round)
}

// =====================================================================
// Code Breaker
// =====================================================================

object CodeRules {
    data class Config(val length: Int, val symbols: Int, val repeats: Boolean, val guesses: Int, val strict: Boolean, val basePoints: Int, val parSeconds: Int)

    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(3, 6, false, 10, false, 10, 150)
        Difficulty.MEDIUM -> Config(4, 6, false, 10, false, 15, 240)
        Difficulty.HARD -> Config(4, 6, true, 10, false, 25, 330)
        Difficulty.EXPERT -> Config(4, 8, true, 10, false, 35, 420)
        Difficulty.MASTER -> Config(5, 8, true, 12, true, 50, 600)
        Difficulty.EXTREME -> Config(6, 9, true, 12, true, 75, 840)
    }

    fun secret(cfg: Config, rng: Random): IntArray =
        if (cfg.repeats) IntArray(cfg.length) { 1 + rng.nextInt(cfg.symbols) }
        else (1..cfg.symbols).shuffled(rng).take(cfg.length).toIntArray()

    /** (right symbol in the right place, right symbol in the wrong place) */
    fun feedback(secret: IntArray, guess: IntArray): Pair<Int, Int> {
        var exact = 0
        val sc = IntArray(10); val gc = IntArray(10)
        for (i in secret.indices) {
            if (secret[i] == guess[i]) exact++ else { sc[secret[i]]++; gc[guess[i]]++ }
        }
        var near = 0
        for (s in 1..9) near += minOf(sc[s], gc[s])
        return exact to near
    }

    /** Could [candidate] be the secret, given every guess and its feedback so far? */
    fun consistent(candidate: IntArray, history: List<Pair<IntArray, Pair<Int, Int>>>) =
        history.all { (g, fb) -> feedback(candidate, g) == fb }
}

// =====================================================================
// Lumen (lights out)
// =====================================================================

object LumenRules {
    enum class Pattern { PLUS, CROSS, KING }
    data class Config(val n: Int, val pattern: Pattern, val minPresses: Int, val maxPresses: Int, val basePoints: Int, val parSeconds: Int)

    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(3, Pattern.PLUS, 2, 3, 10, 60)
        Difficulty.MEDIUM -> Config(4, Pattern.PLUS, 4, 5, 15, 120)
        Difficulty.HARD -> Config(5, Pattern.PLUS, 6, 8, 25, 180)
        Difficulty.EXPERT -> Config(5, Pattern.PLUS, 9, 12, 35, 240)
        Difficulty.MASTER -> Config(6, Pattern.CROSS, 8, 11, 50, 300)
        Difficulty.EXTREME -> Config(6, Pattern.KING, 9, 13, 75, 420)
    }

    /** Cells toggled by pressing [i]. */
    fun affected(n: Int, p: Pattern, i: Int): IntArray {
        val r = i / n; val c = i % n
        val offs = when (p) {
            Pattern.PLUS -> listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1)
            Pattern.CROSS -> listOf(0 to 0, 1 to 1, -1 to -1, 1 to -1, -1 to 1)
            Pattern.KING -> listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, -1 to -1, 1 to -1, -1 to 1)
        }
        return offs.mapNotNull { (dr, dc) ->
            val rr = r + dr; val cc = c + dc
            if (rr in 0 until n && cc in 0 until n) rr * n + cc else null
        }.toIntArray()
    }

    fun press(n: Int, p: Pattern, lights: BooleanArray, i: Int) {
        for (x in affected(n, p, i)) lights[x] = !lights[x]
    }

    /** Fewest presses that switch every light off (null if impossible). Gaussian elimination over GF(2). */
    fun minimumPresses(n: Int, p: Pattern, lights: BooleanArray): Int? {
        val m = n * n
        // rows: equations per cell; cols: press variables + rhs
        val rows = Array(m) { cell -> java.util.BitSet(m + 1).also { bs ->
            for (v in 0 until m) if (cell in affected(n, p, v)) bs.set(v)
            if (lights[cell]) bs.set(m)
        } }
        val pivotOfCol = IntArray(m) { -1 }
        var r = 0
        for (col in 0 until m) {
            val piv = (r until m).firstOrNull { rows[it][col] } ?: continue
            val t = rows[r]; rows[r] = rows[piv]; rows[piv] = t
            for (k in 0 until m) if (k != r && rows[k][col]) rows[k].xor(rows[r])
            pivotOfCol[col] = r
            r++
        }
        for (k in r until m) if (rows[k][m]) return null
        val free = (0 until m).filter { pivotOfCol[it] < 0 }
        if (free.size > 16) return null
        var best = Int.MAX_VALUE
        for (mask in 0 until (1 shl free.size)) {
            val x = BooleanArray(m)
            free.forEachIndexed { k, col -> x[col] = mask and (1 shl k) != 0 }
            for (col in 0 until m) {
                val pr = pivotOfCol[col]
                if (pr < 0) continue
                var v = rows[pr][m]
                for (f in free) if (rows[pr][f] && x[f]) v = !v
                x[col] = v
            }
            best = minOf(best, x.count { it })
        }
        return best
    }

    /** A random solvable board that needs a few presses, and its par. */
    fun generate(cfg: Config, rng: Random): Pair<BooleanArray, Int> {
        while (true) {
            val lights = BooleanArray(cfg.n * cfg.n)
            val k = rng.nextInt(cfg.minPresses, cfg.maxPresses + 1)
            for (i in (0 until cfg.n * cfg.n).shuffled(rng).take(k)) press(cfg.n, cfg.pattern, lights, i)
            if (lights.none { it }) continue
            val par = minimumPresses(cfg.n, cfg.pattern, lights) ?: continue
            if (par >= cfg.minPresses) return lights to par
        }
    }
}

// =====================================================================
// Sliding Tiles
// =====================================================================

object SlidingRules {
    data class Config(val n: Int, val scrambleMoves: Int, val basePoints: Int, val parSeconds: Int)

    /** scrambleMoves = 0 means a uniformly random solvable arrangement. */
    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(3, 24, 10, 90)
        Difficulty.MEDIUM -> Config(3, 0, 15, 180)
        Difficulty.HARD -> Config(4, 80, 25, 360)
        Difficulty.EXPERT -> Config(4, 0, 35, 600)
        Difficulty.MASTER -> Config(5, 0, 50, 1200)
        Difficulty.EXTREME -> Config(6, 0, 75, 2100)
    }

    /** tiles[i] = tile number at position i, 0 = gap. Solved = 1..n²-1 then 0. */
    fun solved(n: Int) = IntArray(n * n) { if (it == n * n - 1) 0 else it + 1 }

    fun isSolved(t: IntArray) = t.indices.all { t[it] == if (it == t.size - 1) 0 else it + 1 }

    fun solvable(t: IntArray, n: Int): Boolean {
        var inv = 0
        val seq = t.filter { it != 0 }
        for (i in seq.indices) for (j in i + 1 until seq.size) if (seq[i] > seq[j]) inv++
        if (n % 2 == 1) return inv % 2 == 0
        val gapRowFromBottom = n - t.indexOf(0) / n
        return (inv + gapRowFromBottom) % 2 == 1
    }

    /** Slides the tile at [i] (and any between it and the gap) toward the gap. Returns tiles moved. */
    fun slide(t: IntArray, n: Int, i: Int): Int {
        val g = t.indexOf(0)
        if (i == g) return 0
        val sameRow = i / n == g / n; val sameCol = i % n == g % n
        if (!sameRow && !sameCol) return 0
        val step = if (sameRow) (if (i > g) 1 else -1) else (if (i > g) n else -n)
        var gap = g; var moved = 0
        while (gap != i) { t[gap] = t[gap + step]; gap += step; moved++ }
        t[i] = 0
        return moved
    }

    fun manhattan(t: IntArray, n: Int): Int {
        var d = 0
        for (i in t.indices) if (t[i] != 0) {
            val goal = t[i] - 1
            d += kotlin.math.abs(goal / n - i / n) + kotlin.math.abs(goal % n - i % n)
        }
        return d
    }

    fun generate(cfg: Config, rng: Random): IntArray {
        val n = cfg.n
        while (true) {
            val t: IntArray
            if (cfg.scrambleMoves > 0) {
                t = solved(n)
                var lastGap = -1
                repeat(cfg.scrambleMoves) {
                    val g = t.indexOf(0)
                    val opts = listOf(g - n, g + n, if (g % n > 0) g - 1 else -1, if (g % n < n - 1) g + 1 else -1)
                        .filter { it in t.indices && it != lastGap }
                    val pick = opts[rng.nextInt(opts.size)]
                    lastGap = g
                    slide(t, n, pick)
                }
            } else {
                t = (0 until n * n).shuffled(rng).toIntArray()
                if (!solvable(t, n)) {
                    // swap two non-gap tiles to flip parity
                    val a = t.indexOfFirst { it != 0 }; val b = t.indexOfLast { it != 0 }
                    val x = t[a]; t[a] = t[b]; t[b] = x
                }
            }
            if (!isSolved(t) && manhattan(t, n) >= n) return t
        }
    }

    /** Fewest single-tile moves for a 3×3 board (breadth-first search from the goal). */
    fun optimal3(t: IntArray): Int {
        val goal = solved(3).joinToString("")
        val start = t.joinToString("")
        if (start == goal) return 0
        val dist = HashMap<String, Int>(200_000)
        dist[goal] = 0
        val q = ArrayDeque(listOf(goal))
        while (q.isNotEmpty()) {
            val s = q.removeFirst()
            val d = dist.getValue(s)
            val g = s.indexOf('0')
            for (nb in intArrayOf(g - 3, g + 3, if (g % 3 > 0) g - 1 else -1, if (g % 3 < 2) g + 1 else -1)) {
                if (nb !in 0..8) continue
                val c = s.toCharArray(); c[g] = c[nb]; c[nb] = '0'
                val ns = String(c)
                if (ns in dist) continue
                dist[ns] = d + 1
                if (ns == start) return d + 1
                q += ns
            }
        }
        return -1
    }

    /** Par: exact for 3×3, otherwise a generous estimate. */
    fun par(t: IntArray, n: Int): Int = if (n == 3) optimal3(t) else manhattan(t, n) * 2
}
