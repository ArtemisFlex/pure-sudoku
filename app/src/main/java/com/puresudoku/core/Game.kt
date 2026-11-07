package com.puresudoku.core

/** Everything needed to resume a game. Stored in one of the 3 save slots. */
class GameSnapshot(
    val difficulty: Difficulty,
    val givens: IntArray,
    val solution: IntArray,
    val values: IntArray,
    val notes: IntArray,
    val strikes: Int,
    val score: Int,
    val streak: Int,
    val elapsedMs: Long,
    val startedAt: Long,
)

sealed interface MoveResult {
    data object Ignored : MoveResult
    data object NotesChanged : MoveResult
    data class Placed(val points: Int, val completedUnits: List<Int>) : MoveResult
    data class Wrong(val strikes: Int, val penalty: Int, val gameOver: Boolean) : MoveResult
    data class Solved(val points: Int, val completedUnits: List<Int>, val bonus: FinishBonus) : MoveResult
}

data class FinishBonus(val timeBonus: Int, val livesBonus: Int) {
    val total get() = timeBonus + livesBonus
}

/**
 * Game rules, independent of Android.
 *  - Mistakes are checked immediately: a wrong number is rejected and costs a strike.
 *  - Three strikes ends the game.
 *  - Correct numbers lock in, so score can't be farmed by erasing and re-placing.
 *  - Undo and erase act on pencil notes.
 */
class Game(
    val difficulty: Difficulty,
    val givens: IntArray,
    val solution: IntArray,
    val values: IntArray = givens.copyOf(),
    val notes: IntArray = IntArray(81),
    strikes: Int = 0,
    score: Int = 0,
    streak: Int = 0,
    var elapsedMs: Long = 0,
    val startedAt: Long = System.currentTimeMillis(),
) {
    var strikes = strikes; private set
    var score = score; private set
    var streak = streak; private set

    private val undoStack = ArrayDeque<IntArray>()

    val isSolved get() = (0 until 81).all { values[it] == solution[it] }
    val isOver get() = strikes >= MAX_STRIKES
    val canUndo get() = undoStack.isNotEmpty()
    val filledCount get() = values.count { it != 0 }

    fun isGiven(i: Int) = givens[i] != 0
    fun remaining(d: Int) = 9 - values.count { it == d }

    fun place(cell: Int, digit: Int): MoveResult {
        if (isOver || isSolved || values[cell] != 0) return MoveResult.Ignored
        if (solution[cell] != digit) {
            strikes++
            streak = 0
            val penalty = minOf(score, difficulty.basePoints)
            score -= penalty
            return MoveResult.Wrong(strikes, penalty, isOver)
        }
        values[cell] = digit
        notes[cell] = 0
        val clear = (1 shl digit).inv()
        for (p in Grid.PEERS[cell]) notes[p] = notes[p] and clear

        streak++
        val completed = completedUnitsAt(cell)
        val points = Scoring.placement(difficulty, streak) + completed.size * Scoring.unitBonus(difficulty)
        score += points

        if (isSolved) {
            val bonus = Scoring.finish(difficulty, elapsedMs, strikes)
            score += bonus.total
            undoStack.clear()
            return MoveResult.Solved(points, completed, bonus)
        }
        return MoveResult.Placed(points, completed)
    }

    fun toggleNote(cell: Int, digit: Int): MoveResult {
        if (isOver || values[cell] != 0) return MoveResult.Ignored
        pushUndo()
        notes[cell] = notes[cell] xor (1 shl digit)
        return MoveResult.NotesChanged
    }

    fun erase(cell: Int): MoveResult {
        if (isOver || values[cell] != 0 || notes[cell] == 0) return MoveResult.Ignored
        pushUndo()
        notes[cell] = 0
        return MoveResult.NotesChanged
    }

    fun undo(): Boolean {
        val prev = undoStack.removeLastOrNull() ?: return false
        prev.copyInto(notes)
        return true
    }

    private fun pushUndo() {
        undoStack.addLast(notes.copyOf())
        if (undoStack.size > 200) undoStack.removeFirst()
    }

    /** Units (0-26, see [Grid.UNITS]) that this placement just completed. */
    private fun completedUnitsAt(cell: Int): List<Int> =
        listOf(Grid.row(cell), 9 + Grid.col(cell), 18 + Grid.box(cell))
            .filter { u -> Grid.UNITS[u].all { values[it] != 0 } }

    fun snapshot() = GameSnapshot(
        difficulty, givens.copyOf(), solution.copyOf(), values.copyOf(), notes.copyOf(),
        strikes, score, streak, elapsedMs, startedAt,
    )

    companion object {
        const val MAX_STRIKES = 3

        fun new(p: GeneratedPuzzle) = Game(p.difficulty, p.puzzle.copyOf(), p.solution.copyOf())

        fun restore(s: GameSnapshot) = Game(
            s.difficulty, s.givens, s.solution, s.values, s.notes,
            s.strikes, s.score, s.streak, s.elapsedMs, s.startedAt,
        )
    }
}

object Scoring {
    /** Base points, plus a small streak bonus for consecutive correct numbers (caps at +100%). */
    fun placement(d: Difficulty, streak: Int): Int =
        d.basePoints + d.basePoints * minOf(streak - 1, 10) / 10

    /** Bonus for each row, column or box completed. */
    fun unitBonus(d: Difficulty) = d.basePoints * 3

    /** Finishing faster than par and with strikes to spare both pay out. */
    fun finish(d: Difficulty, elapsedMs: Long, strikes: Int): FinishBonus {
        val secs = (elapsedMs / 1000).toInt()
        val time = (d.parSeconds - secs).coerceAtLeast(0) * d.basePoints / 10
        val lives = (Game.MAX_STRIKES - strikes) * d.basePoints * 10
        return FinishBonus(time, lives)
    }
}

fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
