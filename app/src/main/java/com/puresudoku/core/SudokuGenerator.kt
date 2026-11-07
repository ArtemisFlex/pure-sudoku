package com.puresudoku.core

import java.security.SecureRandom
import kotlin.random.Random

data class GeneratedPuzzle(
    val puzzle: IntArray,
    val solution: IntArray,
    val difficulty: Difficulty,
    val clues: Int,
    val level: Int,
)

/**
 * Puzzle randomizer.
 *
 * 1. Build a brand-new random solved grid: randomized backtracking (shuffled
 *    digit order at every cell), then scrambled further with validity-preserving
 *    transforms: digit relabelling, row/column swaps within bands, band/stack
 *    swaps and an optional transpose. Every game starts from a fresh seed.
 * 2. Dig holes in random order (mirrored pairs for the easier grades), keeping a
 *    removal only if the puzzle still has exactly one solution and doesn't
 *    become harder than the target grade.
 * 3. Grade the result with [LogicGrader]. Keep it only if it truly needs the
 *    techniques for the requested difficulty; otherwise try again. If the time
 *    budget runs out, the closest candidate found is used.
 */
class SudokuGenerator(seed: Long = SecureRandom().nextLong() xor System.nanoTime()) {
    private val rng = Random(seed)

    fun generate(difficulty: Difficulty, isCancelled: () -> Boolean = { false }): GeneratedPuzzle {
        val deadline = System.currentTimeMillis() + difficulty.budgetMs
        var best: GeneratedPuzzle? = null
        var bestCost = Int.MAX_VALUE
        while (true) {
            val attempt = attempt(difficulty)
            val cost = cost(attempt, difficulty)
            if (cost < bestCost) { best = attempt; bestCost = cost }
            if (cost == 0 || isCancelled() || System.currentTimeMillis() > deadline) return best!!
        }
    }

    fun randomSolution(): IntArray {
        val grid = SudokuSolver.solve(IntArray(81), rng)!!
        return scramble(grid)
    }

    private fun attempt(d: Difficulty): GeneratedPuzzle {
        val solution = randomSolution()
        val puzzle = solution.copyOf()
        val target = rng.nextInt(d.minClues, d.maxClues + 1)
        val groups: List<IntArray> = if (d.symmetric) {
            (0..40).map { if (it == 40) intArrayOf(40) else intArrayOf(it, 80 - it) }
        } else {
            (0 until 81).map { intArrayOf(it) }
        }.shuffled(rng)

        var clues = 81
        for (g in groups) {
            if (clues <= target) break
            if (clues - g.size < d.minClues) continue
            for (c in g) puzzle[c] = 0
            val ok = SudokuSolver.hasUniqueSolution(puzzle) &&
                (d.maxLevel >= LogicGrader.LEVEL_BEYOND || LogicGrader.grade(puzzle).level <= d.maxLevel)
            if (ok) clues -= g.size else for (c in g) puzzle[c] = solution[c]
        }
        val grade = LogicGrader.grade(puzzle)
        return GeneratedPuzzle(puzzle, solution, d, clues, grade.level)
    }

    private fun cost(p: GeneratedPuzzle, d: Difficulty): Int {
        val levelOff = when {
            p.level < d.minLevel -> d.minLevel - p.level
            p.level > d.maxLevel -> p.level - d.maxLevel
            else -> 0
        }
        val clueOff = when {
            p.clues < d.minClues -> d.minClues - p.clues
            p.clues > d.maxClues -> p.clues - d.maxClues
            else -> 0
        }
        return levelOff * 20 + clueOff
    }

    /** Validity-preserving shuffles so the same backtracking path never repeats a look. */
    private fun scramble(src: IntArray): IntArray {
        val digits = (1..9).shuffled(rng)
        val rows = bandOrder()
        val cols = bandOrder()
        val transpose = rng.nextBoolean()
        return IntArray(81) { i ->
            var r = rows[i / 9]
            var c = cols[i % 9]
            if (transpose) { val t = r; r = c; c = t }
            digits[src[r * 9 + c] - 1]
        }
    }

    private fun bandOrder(): List<Int> =
        (0..2).shuffled(rng).flatMap { band -> (0..2).shuffled(rng).map { band * 3 + it } }
}
