package com.puresudoku.core

import java.security.SecureRandom
import kotlin.random.Random

/** One outlined group of cells and the result its digits must produce. */
class Cage(val cells: IntArray, val op: Char, val target: Int) {
    /** Label drawn in the cage's first cell, e.g. "12×". Single cells show nothing (they're given). */
    val label: String get() = when (op) {
        '=' -> ""
        '+' -> "$target+"
        '-' -> "$target−"
        'x' -> "$target×"
        '/' -> "$target÷"
        else -> "$target"
    }
}

class CagePuzzle(val n: Int, val solution: IntArray, val cages: List<Cage>) {
    val cageOf = IntArray(n * n).also { a -> cages.forEachIndexed { k, c -> c.cells.forEach { a[it] = k } } }
    /** Single-cell cages are shown as given digits. */
    val givens = IntArray(n * n).also { g -> cages.filter { it.op == '=' }.forEach { g[it.cells[0]] = it.target } }
}

object CageMathRules {
    data class Config(val n: Int, val ops: String, val maxCage: Int, val basePoints: Int, val parSeconds: Int)

    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(4, "+", 3, 10, 150)
        Difficulty.MEDIUM -> Config(5, "+-", 3, 15, 330)
        Difficulty.HARD -> Config(6, "+-x/", 3, 25, 600)
        Difficulty.EXPERT -> Config(7, "+-x/", 4, 35, 960)
        Difficulty.MASTER -> Config(8, "+-x/", 4, 50, 1500)
        Difficulty.EXTREME -> Config(9, "+-x/", 4, 75, 2400)
    }

    fun spec(n: Int, d: Difficulty): DigitSpec {
        val rows = (0 until n).map { r -> IntArray(n) { r * n + it } }
        val cols = (0 until n).map { c -> IntArray(n) { it * n + c } }
        val cfg = config(d)
        return DigitSpec(n, n, n, BooleanArray(n * n) { true }, rows + cols, rows + cols, ScoreRules(cfg.basePoints, cfg.parSeconds))
    }
}

/**
 * Exact solver for Cage Math. Each cage's possible digit tuples are listed up
 * front; the search keeps only tuples that still fit the row/column rules.
 */
class CageSolver(private val n: Int, private val cages: List<Cage>) {
    private val cells = n * n
    private val all = (1 shl (n + 1)) - 2
    private val cageOf = IntArray(cells)
    private val posInCage = IntArray(cells)
    private val tuples: Array<List<IntArray>>

    init {
        cages.forEachIndexed { k, c -> c.cells.forEachIndexed { p, cell -> cageOf[cell] = k; posInCage[cell] = p } }
        tuples = Array(cages.size) { enumerate(cages[it]) }
    }

    val tupleCount: Int get() = tuples.sumOf { it.size }

    private fun enumerate(c: Cage): List<IntArray> {
        val out = ArrayList<IntArray>()
        val k = c.cells.size
        val cur = IntArray(k)
        fun ok(): Boolean = when (c.op) {
            '=' -> cur[0] == c.target
            '+' -> cur.sum() == c.target
            'x' -> cur.fold(1) { a, b -> a * b } == c.target
            '-' -> kotlin.math.abs(cur[0] - cur[1]) == c.target
            '/' -> maxOf(cur[0], cur[1]) == c.target * minOf(cur[0], cur[1])
            else -> false
        }
        fun rec(i: Int, acc: Int) {
            if (i == k) { if (ok()) out += cur.copyOf(); return }
            for (d in 1..n) {
                if (c.op == '+' && acc + d > c.target) break
                if (c.op == 'x' && (acc * d > c.target || c.target % (acc * d) != 0)) continue
                var clash = false
                for (j in 0 until i) {
                    val a = c.cells[i]; val b = c.cells[j]
                    if (cur[j] == d && (a / n == b / n || a % n == b % n)) { clash = true; break }
                }
                if (clash) continue
                cur[i] = d
                rec(i + 1, if (c.op == 'x') acc * d else acc + d)
            }
        }
        rec(0, if (c.op == 'x') 1 else 0)
        return out
    }

    private val grid = IntArray(cells)
    private val rowUsed = IntArray(n)
    private val colUsed = IntArray(n)
    var lastSolution: IntArray? = null; private set
    private val found = ArrayList<IntArray>()

    /** Counts solutions up to [limit]; the first ones found are kept in [solutions]. */
    fun count(limit: Int = 2): Int {
        grid.fill(0); rowUsed.fill(0); colUsed.fill(0); found.clear()
        return search(limit)
    }

    val solutions: List<IntArray> get() = found

    private fun search(limit: Int): Int {
        // Candidate masks per cell from the tuples still alive in its cage.
        val cand = IntArray(cells)
        for ((k, c) in cages.withIndex()) {
            val posMask = IntArray(c.cells.size)
            for (t in tuples[k]) {
                var alive = true
                for (p in c.cells.indices) {
                    val cell = c.cells[p]
                    val v = grid[cell]
                    if (v != 0) { if (v != t[p]) { alive = false; break } }
                    else if ((rowUsed[cell / n] or colUsed[cell % n]) and (1 shl t[p]) != 0) { alive = false; break }
                }
                if (alive) for (p in c.cells.indices) posMask[p] = posMask[p] or (1 shl t[p])
            }
            for (p in c.cells.indices) cand[c.cells[p]] = posMask[p]
        }
        var best = -1; var bestCount = 99
        for (i in 0 until cells) {
            if (grid[i] != 0) continue
            val m = cand[i] and all and (rowUsed[i / n] or colUsed[i % n]).inv()
            val cnt = Integer.bitCount(m)
            if (cnt == 0) return 0
            if (cnt < bestCount) { best = i; bestCount = cnt; cand[i] = m; if (cnt == 1) break } else cand[i] = m
        }
        if (best < 0) {
            found += grid.copyOf()
            return 1
        }
        var m = cand[best]
        var total = 0
        while (m != 0) {
            val bit = m and -m
            m = m xor bit
            val d = Integer.numberOfTrailingZeros(bit)
            grid[best] = d; rowUsed[best / n] = rowUsed[best / n] or bit; colUsed[best % n] = colUsed[best % n] or bit
            total += search(limit - total)
            grid[best] = 0; rowUsed[best / n] = rowUsed[best / n] and bit.inv(); colUsed[best % n] = colUsed[best % n] and bit.inv()
            if (total >= limit) return total
        }
        return total
    }
}

/**
 * Cage Math generator:
 *  1. random Latin square (randomized backtracking),
 *  2. random cages grown from random seeds, with random operations,
 *  3. solver check; if more than one answer exists, a cell where two answers
 *     differ is split off into its own given cell, and the check repeats.
 */
class CageMathGenerator(seed: Long = SecureRandom().nextLong() xor System.nanoTime()) {
    private val rng = Random(seed)

    fun generate(d: Difficulty): CagePuzzle {
        val cfg = CageMathRules.config(d)
        var best: CagePuzzle? = null
        var bestGivens = Int.MAX_VALUE
        repeat(6) {
            val p = attempt(cfg)
            val givens = p.cages.count { it.op == '=' }
            if (givens < bestGivens) { best = p; bestGivens = givens }
            if (givens <= cfg.n / 2) return best!!
        }
        return best!!
    }

    fun latinSquare(n: Int): IntArray {
        val g = IntArray(n * n)
        val row = IntArray(n); val col = IntArray(n)
        fun fill(i: Int): Boolean {
            if (i == n * n) return true
            val r = i / n; val c = i % n
            for (d in (1..n).shuffled(rng)) {
                val bit = 1 shl d
                if ((row[r] or col[c]) and bit != 0) continue
                g[i] = d; row[r] = row[r] or bit; col[c] = col[c] or bit
                if (fill(i + 1)) return true
                row[r] = row[r] and bit.inv(); col[c] = col[c] and bit.inv()
            }
            g[i] = 0
            return false
        }
        fill(0)
        return g
    }

    private fun attempt(cfg: CageMathRules.Config): CagePuzzle {
        val n = cfg.n
        val sol = latinSquare(n)
        val cageId = IntArray(n * n) { -1 }
        var next = 0
        for (start in (0 until n * n).shuffled(rng)) {
            if (cageId[start] >= 0) continue
            val size = pickSize(cfg.maxCage)
            val members = mutableListOf(start)
            cageId[start] = next
            while (members.size < size) {
                val frontier = members.flatMap { neighbours(it, n) }.filter { cageId[it] < 0 }.distinct()
                if (frontier.isEmpty()) break
                val pick = frontier[rng.nextInt(frontier.size)]
                cageId[pick] = next
                members += pick
            }
            next++
        }
        var cages = buildCages(cageId, next, sol, n, cfg.ops)

        // Repair until exactly one solution.
        var guard = 0
        while (guard++ < n * n) {
            val solver = CageSolver(n, cages)
            if (solver.count(2) == 1) break
            val (a, b) = solver.solutions
            val diff = (0 until n * n).filter { a[it] != b[it] }
            val cell = diff[rng.nextInt(diff.size)]
            val id = cageId[cell]
            cageId[cell] = next++
            // The rest of the old cage may now be in pieces: relabel each piece.
            val rest = (0 until n * n).filter { cageId[it] == id }
            if (rest.isNotEmpty()) {
                val seen = HashSet<Int>()
                for (s in rest) {
                    if (s in seen) continue
                    val comp = ArrayDeque(listOf(s)); seen += s
                    val label = next++
                    while (comp.isNotEmpty()) {
                        val x = comp.removeFirst(); cageId[x] = label
                        for (y in neighbours(x, n)) if (cageId[y] == id && y !in seen) { seen += y; comp += y }
                    }
                }
            }
            cages = buildCages(cageId, next, sol, n, cfg.ops)
        }
        return CagePuzzle(n, sol, cages)
    }

    private fun pickSize(max: Int): Int {
        val weights = intArrayOf(0, 3, 42, 37, 14, 4).take(max + 1)
        var r = rng.nextInt(weights.sum())
        for (s in weights.indices) { r -= weights[s]; if (r < 0) return s }
        return 2
    }

    private fun buildCages(cageId: IntArray, count: Int, sol: IntArray, n: Int, ops: String): List<Cage> {
        val groups = (0 until count).map { id -> (0 until n * n).filter { cageId[it] == id }.toIntArray() }.filter { it.isNotEmpty() }
        return groups.map { cells ->
            val v = cells.map { sol[it] }
            when {
                cells.size == 1 -> Cage(cells, '=', v[0])
                cells.size == 2 && '/' in ops && maxOf(v[0], v[1]) % minOf(v[0], v[1]) == 0 && rng.nextInt(100) < 55 ->
                    Cage(cells, '/', maxOf(v[0], v[1]) / minOf(v[0], v[1]))
                cells.size == 2 && '-' in ops && rng.nextInt(100) < 55 -> Cage(cells, '-', kotlin.math.abs(v[0] - v[1]))
                'x' in ops && rng.nextInt(100) < 40 && v.fold(1) { a, b -> a * b } <= 3000 -> Cage(cells, 'x', v.fold(1) { a, b -> a * b })
                else -> Cage(cells, '+', v.sum())
            }
        }
    }

    private fun neighbours(i: Int, n: Int): List<Int> {
        val r = i / n; val c = i % n
        val out = ArrayList<Int>(4)
        if (r > 0) out += i - n
        if (r < n - 1) out += i + n
        if (c > 0) out += i - 1
        if (c < n - 1) out += i + 1
        return out
    }
}
