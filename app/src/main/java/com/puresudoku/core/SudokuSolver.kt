package com.puresudoku.core

import kotlin.random.Random

/** Grid geometry shared by the solver, grader and UI. Cells are indexed 0..80, row-major. */
object Grid {
    const val ALL = 0x3FE // bits 1..9

    fun row(i: Int) = i / 9
    fun col(i: Int) = i % 9
    fun box(i: Int) = (i / 27) * 3 + (i % 9) / 3

    /** 27 units: rows 0-8, columns 9-17, boxes 18-26. */
    val UNITS: Array<IntArray> = Array(27) { u ->
        when {
            u < 9 -> IntArray(9) { u * 9 + it }
            u < 18 -> IntArray(9) { it * 9 + (u - 9) }
            else -> {
                val b = u - 18
                val r0 = (b / 3) * 3
                val c0 = (b % 3) * 3
                IntArray(9) { (r0 + it / 3) * 9 + c0 + it % 3 }
            }
        }
    }

    /** The 20 cells sharing a row, column or box with each cell. */
    val PEERS: Array<IntArray> = Array(81) { i ->
        (0 until 81).filter { it != i && sees(i, it) }.toIntArray()
    }

    fun sees(a: Int, b: Int): Boolean =
        a != b && (row(a) == row(b) || col(a) == col(b) || box(a) == box(b))
}

/** Fast exact solver: bitmasks + most-constrained-cell-first backtracking. */
object SudokuSolver {

    /** Counts solutions, stopping once [limit] is reached. 0 means invalid/unsolvable. */
    fun countSolutions(grid: IntArray, limit: Int = 2): Int {
        val s = State.from(grid) ?: return 0
        return s.count(limit)
    }

    fun hasUniqueSolution(grid: IntArray): Boolean = countSolutions(grid, 2) == 1

    /** Returns one solution; with [rng] the candidate order is shuffled, giving a random grid. */
    fun solve(grid: IntArray, rng: Random? = null): IntArray? {
        val s = State.from(grid) ?: return null
        return if (s.fill(rng)) s.g else null
    }

    private class State(val g: IntArray, val rows: IntArray, val cols: IntArray, val boxes: IntArray) {

        private fun mask(i: Int) =
            Grid.ALL and (rows[Grid.row(i)] or cols[Grid.col(i)] or boxes[Grid.box(i)]).inv()

        private fun set(i: Int, v: Int) {
            val bit = 1 shl v
            g[i] = v
            rows[Grid.row(i)] = rows[Grid.row(i)] or bit
            cols[Grid.col(i)] = cols[Grid.col(i)] or bit
            boxes[Grid.box(i)] = boxes[Grid.box(i)] or bit
        }

        private fun clear(i: Int, v: Int) {
            val bit = (1 shl v).inv()
            g[i] = 0
            rows[Grid.row(i)] = rows[Grid.row(i)] and bit
            cols[Grid.col(i)] = cols[Grid.col(i)] and bit
            boxes[Grid.box(i)] = boxes[Grid.box(i)] and bit
        }

        /** Most constrained empty cell: returns -1 if full, -2 if a dead end exists. */
        private fun pick(): Int {
            var best = -1
            var bestCount = 10
            for (i in 0 until 81) {
                if (g[i] != 0) continue
                val c = Integer.bitCount(mask(i))
                if (c == 0) return -2
                if (c < bestCount) {
                    best = i; bestCount = c
                    if (c == 1) break
                }
            }
            return best
        }

        fun count(limit: Int): Int {
            val i = pick()
            if (i == -1) return 1
            if (i == -2) return 0
            var m = mask(i)
            var total = 0
            while (m != 0) {
                val bit = m and -m
                m = m xor bit
                val v = Integer.numberOfTrailingZeros(bit)
                set(i, v)
                total += count(limit - total)
                clear(i, v)
                if (total >= limit) return total
            }
            return total
        }

        fun fill(rng: Random?): Boolean {
            val i = pick()
            if (i == -1) return true
            if (i == -2) return false
            val m = mask(i)
            val digits = (1..9).filter { m and (1 shl it) != 0 }.toMutableList()
            if (rng != null) digits.shuffle(rng)
            for (v in digits) {
                set(i, v)
                if (fill(rng)) return true
                clear(i, v)
            }
            return false
        }

        companion object {
            fun from(grid: IntArray): State? {
                val s = State(grid.copyOf(), IntArray(9), IntArray(9), IntArray(9))
                for (i in 0 until 81) {
                    val v = grid[i]
                    if (v == 0) continue
                    if (v !in 1..9) return null
                    val bit = 1 shl v
                    if ((s.rows[Grid.row(i)] or s.cols[Grid.col(i)] or s.boxes[Grid.box(i)]) and bit != 0) return null
                    s.set(i, v)
                }
                return s
            }
        }
    }
}
