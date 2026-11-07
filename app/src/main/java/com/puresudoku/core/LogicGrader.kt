package com.puresudoku.core

/**
 * Solves a puzzle the way a person would, using only logical techniques, and
 * reports the hardest technique it needed. This is what makes difficulty
 * levels honest rather than just "fewer clues".
 */
object LogicGrader {
    const val LEVEL_SINGLES = 1   // naked & hidden singles
    const val LEVEL_LOCKED = 2    // pointing / claiming (locked candidates)
    const val LEVEL_PAIRS = 3     // naked & hidden pairs
    const val LEVEL_TRIPLES = 4   // naked & hidden triples, X-Wing
    const val LEVEL_ADVANCED = 5  // Swordfish, XY-Wing, XYZ-Wing
    const val LEVEL_BEYOND = 6    // needs chains / trial and error beyond the above

    data class Result(val solved: Boolean, val level: Int, val score: Int)

    fun grade(puzzle: IntArray): Result = Session(puzzle).run()

    private class Session(puzzle: IntArray) {
        val v = puzzle.copyOf()
        val cand = IntArray(81)

        init {
            for (i in 0 until 81) {
                if (v[i] != 0) continue
                var m = Grid.ALL
                for (p in Grid.PEERS[i]) if (v[p] != 0) m = m and (1 shl v[p]).inv()
                cand[i] = m
            }
        }

        fun run(): Result {
            var level = 0
            var score = 0
            while (true) {
                if (v.none { it == 0 }) return Result(true, maxOf(level, LEVEL_SINGLES), score)
                if ((0 until 81).any { v[it] == 0 && cand[it] == 0 }) return Result(false, LEVEL_BEYOND, score + 500)
                val step = when {
                    nakedSingle() -> LEVEL_SINGLES to 1
                    hiddenSingle() -> LEVEL_SINGLES to 2
                    lockedCandidates() -> LEVEL_LOCKED to 8
                    nakedSubset(2) || hiddenSubset(2) -> LEVEL_PAIRS to 15
                    nakedSubset(3) || hiddenSubset(3) || fish(2) -> LEVEL_TRIPLES to 25
                    fish(3) || xyWing() || xyzWing() -> LEVEL_ADVANCED to 40
                    else -> return Result(false, LEVEL_BEYOND, score + 200)
                }
                level = maxOf(level, step.first)
                score += step.second
            }
        }

        private fun place(i: Int, d: Int) {
            v[i] = d
            cand[i] = 0
            val clear = (1 shl d).inv()
            for (p in Grid.PEERS[i]) cand[p] = cand[p] and clear
        }

        private fun eliminate(i: Int, mask: Int): Boolean {
            if (v[i] != 0 || cand[i] and mask == 0) return false
            cand[i] = cand[i] and mask.inv()
            return true
        }

        private fun nakedSingle(): Boolean {
            for (i in 0 until 81) {
                if (v[i] == 0 && Integer.bitCount(cand[i]) == 1) {
                    place(i, Integer.numberOfTrailingZeros(cand[i])); return true
                }
            }
            return false
        }

        private fun hiddenSingle(): Boolean {
            for (unit in Grid.UNITS) for (d in 1..9) {
                val bit = 1 shl d
                var found = -1
                var n = 0
                for (c in unit) if (cand[c] and bit != 0) { found = c; n++ }
                if (n == 1) { place(found, d); return true }
            }
            return false
        }

        /** Pointing (box -> line) and claiming (line -> box). */
        private fun lockedCandidates(): Boolean {
            for (u in 0 until 27) for (d in 1..9) {
                val bit = 1 shl d
                val cells = Grid.UNITS[u].filter { cand[it] and bit != 0 }
                if (cells.size < 2) continue
                val targets = mutableListOf<IntArray>()
                if (u >= 18) {
                    if (cells.all { Grid.row(it) == Grid.row(cells[0]) }) targets += Grid.UNITS[Grid.row(cells[0])]
                    if (cells.all { Grid.col(it) == Grid.col(cells[0]) }) targets += Grid.UNITS[9 + Grid.col(cells[0])]
                } else if (cells.all { Grid.box(it) == Grid.box(cells[0]) }) {
                    targets += Grid.UNITS[18 + Grid.box(cells[0])]
                }
                var changed = false
                for (t in targets) for (c in t) if (c !in cells && eliminate(c, bit)) changed = true
                if (changed) return true
            }
            return false
        }

        private fun nakedSubset(n: Int): Boolean {
            for (unit in Grid.UNITS) {
                val pool = unit.filter { v[it] == 0 && Integer.bitCount(cand[it]) in 2..n }
                if (pool.size < n) continue
                val hit = combinations(pool.size, n) { idx ->
                    var union = 0
                    for (k in idx) union = union or cand[pool[k]]
                    if (Integer.bitCount(union) != n) return@combinations false
                    val members = idx.map { pool[it] }
                    var changed = false
                    for (c in unit) if (c !in members && eliminate(c, union)) changed = true
                    changed
                }
                if (hit) return true
            }
            return false
        }

        private fun hiddenSubset(n: Int): Boolean {
            for (unit in Grid.UNITS) {
                val digits = IntArray(10)
                for (d in 1..9) {
                    var pos = 0
                    for (k in 0 until 9) if (cand[unit[k]] and (1 shl d) != 0) pos = pos or (1 shl k)
                    digits[d] = pos
                }
                val pool = (1..9).filter { Integer.bitCount(digits[it]) in 2..n }
                if (pool.size < n) continue
                val hit = combinations(pool.size, n) { idx ->
                    var positions = 0
                    var keep = 0
                    for (k in idx) { positions = positions or digits[pool[k]]; keep = keep or (1 shl pool[k]) }
                    if (Integer.bitCount(positions) != n) return@combinations false
                    var changed = false
                    for (k in 0 until 9) if (positions and (1 shl k) != 0 && eliminate(unit[k], keep.inv() and Grid.ALL)) changed = true
                    changed
                }
                if (hit) return true
            }
            return false
        }

        /** X-Wing (n=2) and Swordfish (n=3), in both row and column orientation. */
        private fun fish(n: Int): Boolean {
            for (d in 1..9) {
                val bit = 1 shl d
                for (byRow in booleanArrayOf(true, false)) {
                    fun cell(line: Int, k: Int) = if (byRow) line * 9 + k else k * 9 + line
                    val lineMask = IntArray(9) { line ->
                        var m = 0
                        for (k in 0 until 9) if (cand[cell(line, k)] and bit != 0) m = m or (1 shl k)
                        m
                    }
                    val pool = (0 until 9).filter { Integer.bitCount(lineMask[it]) in 2..n }
                    if (pool.size < n) continue
                    val hit = combinations(pool.size, n) { idx ->
                        var cover = 0
                        for (k in idx) cover = cover or lineMask[pool[k]]
                        if (Integer.bitCount(cover) != n) return@combinations false
                        val lines = idx.map { pool[it] }
                        var changed = false
                        for (line in 0 until 9) {
                            if (line in lines) continue
                            for (k in 0 until 9) if (cover and (1 shl k) != 0 && eliminate(cell(line, k), bit)) changed = true
                        }
                        changed
                    }
                    if (hit) return true
                }
            }
            return false
        }

        private fun xyWing(): Boolean {
            for (pivot in 0 until 81) {
                if (v[pivot] != 0 || Integer.bitCount(cand[pivot]) != 2) continue
                val pm = cand[pivot]
                val wings = Grid.PEERS[pivot].filter {
                    v[it] == 0 && Integer.bitCount(cand[it]) == 2 && Integer.bitCount(cand[it] and pm) == 1
                }
                for (a in wings.indices) for (b in a + 1 until wings.size) {
                    val w1 = wings[a]; val w2 = wings[b]
                    val shared1 = cand[w1] and pm
                    val shared2 = cand[w2] and pm
                    if (shared1 == shared2) continue
                    val z = cand[w1] and pm.inv()
                    if (z != cand[w2] and pm.inv()) continue
                    var changed = false
                    for (c in Grid.PEERS[w1]) if (c != pivot && Grid.sees(c, w2) && eliminate(c, z)) changed = true
                    if (changed) return true
                }
            }
            return false
        }

        private fun xyzWing(): Boolean {
            for (pivot in 0 until 81) {
                if (v[pivot] != 0 || Integer.bitCount(cand[pivot]) != 3) continue
                val pm = cand[pivot]
                val wings = Grid.PEERS[pivot].filter {
                    v[it] == 0 && Integer.bitCount(cand[it]) == 2 && cand[it] and pm == cand[it]
                }
                for (a in wings.indices) for (b in a + 1 until wings.size) {
                    val w1 = wings[a]; val w2 = wings[b]
                    if (cand[w1] or cand[w2] != pm) continue
                    val z = cand[w1] and cand[w2]
                    if (Integer.bitCount(z) != 1) continue
                    var changed = false
                    for (c in Grid.PEERS[pivot]) if (c != w1 && c != w2 && Grid.sees(c, w1) && Grid.sees(c, w2) && eliminate(c, z)) changed = true
                    if (changed) return true
                }
            }
            return false
        }

        /** Calls [action] with each size-[k] index combination of 0 until [n]; stops at the first true. */
        private inline fun combinations(n: Int, k: Int, action: (IntArray) -> Boolean): Boolean {
            val idx = IntArray(k) { it }
            while (true) {
                if (action(idx)) return true
                var i = k - 1
                while (i >= 0 && idx[i] == n - k + i) i--
                if (i < 0) return false
                idx[i]++
                for (j in i + 1 until k) idx[j] = idx[j - 1] + 1
            }
        }
    }
}
