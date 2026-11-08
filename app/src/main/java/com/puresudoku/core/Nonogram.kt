package com.puresudoku.core

import java.security.SecureRandom
import kotlin.random.Random

class NonogramPuzzle(val width: Int, val height: Int, val solution: BooleanArray) {
    val rowClues: List<IntArray> = (0 until height).map { r -> NonogramRules.clue(BooleanArray(width) { solution[r * width + it] }) }
    val colClues: List<IntArray> = (0 until width).map { c -> NonogramRules.clue(BooleanArray(height) { solution[it * width + c] }) }
    val filledCount = solution.count { it }
}

object NonogramRules {
    data class Config(val w: Int, val h: Int, val density: Int, val smooth: Int, val basePoints: Int, val parSeconds: Int, val minPasses: Int)

    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(5, 5, 58, 1, 10, 120, 0)
        Difficulty.MEDIUM -> Config(8, 8, 55, 2, 12, 300, 0)
        Difficulty.HARD -> Config(10, 10, 52, 2, 15, 540, 3)
        Difficulty.EXPERT -> Config(12, 12, 50, 2, 20, 840, 4)
        Difficulty.MASTER -> Config(15, 15, 50, 2, 25, 1320, 5)
        Difficulty.EXTREME -> Config(15, 15, 46, 1, 35, 1800, 7)
    }

    fun clue(line: BooleanArray): IntArray {
        val out = ArrayList<Int>()
        var run = 0
        for (b in line) { if (b) run++ else if (run > 0) { out += run; run = 0 } }
        if (run > 0) out += run
        return out.toIntArray()
    }

    const val UNKNOWN = 0
    const val FILLED = 1
    const val EMPTY = 2

    /**
     * Line logic: given a line's clue and known cells, returns the cells that are
     * forced in every valid arrangement (FILLED/EMPTY), or null if none fit.
     */
    fun solveLine(clue: IntArray, known: IntArray): IntArray? {
        val n = known.size
        val k = clue.size
        // fwd[i][j]: first i cells can hold the first j blocks
        val fwd = Array(n + 1) { BooleanArray(k + 1) }
        fwd[0][0] = true
        for (i in 1..n) {
            if (known[i - 1] != FILLED) for (j in 0..k) if (fwd[i - 1][j]) fwd[i][j] = true
            for (j in 1..k) {
                val len = clue[j - 1]
                val start = i - len
                if (start < 0) continue
                if ((start until i).any { known[it] == EMPTY }) continue
                if (j > 1) {
                    if (start == 0 || known[start - 1] == FILLED) continue
                    if (fwd[start - 1][j - 1]) fwd[i][j] = true
                } else if (fwd[start][0]) fwd[i][j] = true
            }
        }
        if (!fwd[n][k]) return null
        // bwd[i][j]: cells i..n-1 can hold blocks j..k-1
        val bwd = Array(n + 2) { BooleanArray(k + 1) }
        bwd[n][k] = true
        for (i in n - 1 downTo 0) {
            if (known[i] != FILLED && bwd[i + 1][k]) bwd[i][k] = true
            for (j in k - 1 downTo 0) {
                if (known[i] != FILLED && bwd[i + 1][j]) bwd[i][j] = true
                val len = clue[j]
                val end = i + len
                if (end > n) continue
                if ((i until end).any { known[it] == EMPTY }) continue
                if (j < k - 1) {
                    if (end == n || known[end] == FILLED) continue
                    if (bwd[end + 1][j + 1]) bwd[i][j] = true
                } else if (bwd[end][k]) bwd[i][j] = true
            }
        }
        val canFill = BooleanArray(n)
        val canEmpty = BooleanArray(n)
        // empty cell i possible if some split j with fwd[i][j] && bwd[i+1][j]
        for (i in 0 until n) if (known[i] != FILLED) for (j in 0..k) if (fwd[i][j] && bwd[i + 1][j]) { canEmpty[i] = true; break }
        // block j placed at [s, s+len)
        for (j in 0 until k) {
            val len = clue[j]
            for (s in 0..n - len) {
                if ((s until s + len).any { known[it] == EMPTY }) continue
                val leftOk = if (j == 0) fwd[s][0] else s >= 1 && known[s - 1] != FILLED && fwd[s - 1][j]
                val e = s + len
                val rightOk = if (j == k - 1) bwd[e][k] else e < n && known[e] != FILLED && bwd[e + 1][j + 1]
                if (leftOk && rightOk) for (x in s until e) canFill[x] = true
            }
        }
        return IntArray(n) { i ->
            when {
                canFill[i] && !canEmpty[i] -> FILLED
                canEmpty[i] && !canFill[i] -> EMPTY
                canFill[i] || canEmpty[i] -> UNKNOWN
                else -> -1
            }
        }.takeIf { r -> r.none { it == -1 } }
    }

    /** Solves using line logic only. Returns (solved?, passes used). */
    fun lineSolve(p: NonogramPuzzle): Pair<Boolean, Int> {
        val w = p.width; val h = p.height
        val g = IntArray(w * h)
        var passes = 0
        while (true) {
            passes++
            var changed = false
            for (r in 0 until h) {
                val known = IntArray(w) { g[r * w + it] }
                val res = solveLine(p.rowClues[r], known) ?: return false to passes
                for (c in 0 until w) if (res[c] != UNKNOWN && g[r * w + c] == UNKNOWN) { g[r * w + c] = res[c]; changed = true }
            }
            for (c in 0 until w) {
                val known = IntArray(h) { g[it * w + c] }
                val res = solveLine(p.colClues[c], known) ?: return false to passes
                for (r in 0 until h) if (res[r] != UNKNOWN && g[r * w + c] == UNKNOWN) { g[r * w + c] = res[r]; changed = true }
            }
            if (g.none { it == UNKNOWN }) return true to passes
            if (!changed) return false to passes
        }
    }

    fun undetermined(p: NonogramPuzzle): List<Int> {
        val w = p.width; val h = p.height
        val g = IntArray(w * h)
        while (true) {
            var changed = false
            for (r in 0 until h) {
                val res = solveLine(p.rowClues[r], IntArray(w) { g[r * w + it] }) ?: return emptyList()
                for (c in 0 until w) if (res[c] != UNKNOWN && g[r * w + c] == UNKNOWN) { g[r * w + c] = res[c]; changed = true }
            }
            for (c in 0 until w) {
                val res = solveLine(p.colClues[c], IntArray(h) { g[it * w + c] }) ?: return emptyList()
                for (r in 0 until h) if (res[r] != UNKNOWN && g[r * w + c] == UNKNOWN) { g[r * w + c] = res[r]; changed = true }
            }
            if (!changed) return (0 until w * h).filter { g[it] == UNKNOWN }
        }
    }
}

/**
 * Picture Logic generator: random noise smoothed into blob shapes (often
 * mirrored, so it reads like a picture), then adjusted until it can be solved
 * with line logic alone — no guessing ever needed, and so only one answer.
 */
class NonogramGenerator(seed: Long = SecureRandom().nextLong() xor System.nanoTime()) {
    private val rng = Random(seed)

    fun generate(d: Difficulty): NonogramPuzzle {
        val cfg = NonogramRules.config(d)
        var fallback: NonogramPuzzle? = null
        repeat(40) {
            val p = attempt(cfg) ?: return@repeat
            val (_, passes) = NonogramRules.lineSolve(p)
            if (fallback == null) fallback = p
            if (passes >= cfg.minPasses) return p
        }
        return fallback ?: generate(d)
    }

    private fun attempt(cfg: NonogramRules.Config): NonogramPuzzle? {
        val w = cfg.w; val h = cfg.h
        var img = BooleanArray(w * h) { rng.nextInt(100) < cfg.density }
        val mirror = rng.nextInt(100) < 55
        if (mirror) for (r in 0 until h) for (c in w / 2 until w) img[r * w + c] = img[r * w + (w - 1 - c)]
        repeat(cfg.smooth) { img = smooth(img, w, h) }
        if (img.count { it } < w * h / 4) return null
        // Flip cells the line logic can't decide until it can solve everything.
        repeat(w * h) {
            val p = NonogramPuzzle(w, h, img)
            val open = NonogramRules.undetermined(p)
            if (open.isEmpty()) return p
            val cell = open[rng.nextInt(open.size)]
            img[cell] = !img[cell]
        }
        return null
    }

    private fun smooth(src: BooleanArray, w: Int, h: Int): BooleanArray = BooleanArray(w * h) { i ->
        val r = i / w; val c = i % w
        var n = 0
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val rr = r + dr; val cc = c + dc
            if (rr in 0 until h && cc in 0 until w && src[rr * w + cc]) n++
        }
        if (src[i]) n >= 3 else n >= 5
    }
}
