package com.puresudoku.core

/**
 * Each difficulty is defined by two things:
 *  - how many givens (clues) the puzzle starts with, and
 *  - which human solving techniques are *required* to finish it
 *    (graded by [LogicGrader], see its LEVEL_* constants).
 *
 * A puzzle only counts as e.g. "Hard" if it actually needs a Hard technique,
 * not just because it has few clues.
 */
enum class Difficulty(
    val label: String,
    val minClues: Int,
    val maxClues: Int,
    val minLevel: Int,
    val maxLevel: Int,
    /** Remove clues in mirrored pairs for a classic symmetric look. */
    val symmetric: Boolean,
    /** Points for each correct number placed. */
    val basePoints: Int,
    /** Target solve time; finishing faster earns a time bonus. */
    val parSeconds: Int,
) {
    EASY("Easy", 38, 42, LogicGrader.LEVEL_SINGLES, LogicGrader.LEVEL_SINGLES, true, 10, 420),
    MEDIUM("Medium", 32, 36, LogicGrader.LEVEL_SINGLES, LogicGrader.LEVEL_LOCKED, true, 15, 720),
    HARD("Hard", 28, 32, LogicGrader.LEVEL_LOCKED, LogicGrader.LEVEL_PAIRS, true, 25, 1080),
    EXPERT("Expert", 25, 29, LogicGrader.LEVEL_PAIRS, LogicGrader.LEVEL_TRIPLES, true, 35, 1500),
    MASTER("Master", 23, 27, LogicGrader.LEVEL_TRIPLES, LogicGrader.LEVEL_ADVANCED, false, 50, 2100),
    EXTREME("Extreme", 20, 25, LogicGrader.LEVEL_BEYOND, LogicGrader.LEVEL_BEYOND, false, 75, 3000);

    /** Generation time budget; harder grades need more attempts to hit. */
    val budgetMs: Long
        get() = when (this) {
            EASY, MEDIUM, HARD -> 2500L
            EXPERT -> 3500L
            MASTER -> 5000L
            EXTREME -> 7000L
        }
}
