package com.puresudoku.core

/**
 * Shape and rules of a "write digits into cells" puzzle. Sudoku, Cage Math and
 * Kakuro all run on this.
 */
class DigitSpec(
    val width: Int,
    val height: Int,
    val maxDigit: Int,
    /** false for cells that aren't filled in (Kakuro clue/black cells). */
    val playable: BooleanArray,
    /** Groups whose digits must all differ (rows/columns/boxes/runs). */
    val exclusive: List<IntArray>,
    /** Groups that pay a bonus (and ripple) when completed. */
    val bonusUnits: List<IntArray>,
    val rules: ScoreRules,
    /** Show "how many of this digit are left" under the number keys. */
    val showRemaining: Boolean = true,
) {
    val cells get() = width * height

    /** Cells sharing an exclusive group with each cell. */
    val peers: Array<IntArray> = Array(width * height) { i ->
        val set = LinkedHashSet<Int>()
        for (g in exclusive) if (i in g) for (c in g) if (c != i) set += c
        set.toIntArray()
    }

    val unitsOf: Array<IntArray> = Array(width * height) { i ->
        bonusUnits.indices.filter { i in bonusUnits[it] }.toIntArray()
    }
}

data class ScoreRules(val basePoints: Int, val parSeconds: Int)

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

const val MAX_STRIKES = 3

/**
 * Rules shared by the digit games:
 *  - a wrong digit is rejected and costs a strike; three strikes ends the game
 *  - correct digits lock in, so score can't be farmed
 *  - undo and erase act on pencil notes
 */
class DigitGame(
    val spec: DigitSpec,
    val givens: IntArray,
    val solution: IntArray,
    val values: IntArray = givens.copyOf(),
    val notes: IntArray = IntArray(spec.cells),
    strikes: Int = 0,
    score: Int = 0,
    streak: Int = 0,
) {
    var strikes = strikes; private set
    var score = score; private set
    var streak = streak; private set

    private val undoStack = ArrayDeque<IntArray>()

    val isSolved get() = (0 until spec.cells).all { !spec.playable[it] || values[it] == solution[it] }
    val isOver get() = strikes >= MAX_STRIKES
    val canUndo get() = undoStack.isNotEmpty()

    fun isGiven(i: Int) = givens[i] != 0
    fun remaining(d: Int): Int {
        var need = 0
        for (i in 0 until spec.cells) if (spec.playable[i]) {
            if (solution[i] == d) need++
            if (values[i] == d) need--
        }
        return need
    }

    val progress: Float
        get() {
            var open = 0; var done = 0
            for (i in 0 until spec.cells) if (spec.playable[i] && givens[i] == 0) {
                open++; if (values[i] != 0) done++
            }
            return if (open == 0) 1f else done / open.toFloat()
        }

    fun place(cell: Int, digit: Int, elapsedMs: Long): MoveResult {
        if (isOver || isSolved || !spec.playable[cell] || values[cell] != 0) return MoveResult.Ignored
        if (solution[cell] != digit) {
            strikes++
            streak = 0
            val penalty = minOf(score, spec.rules.basePoints)
            score -= penalty
            return MoveResult.Wrong(strikes, penalty, isOver)
        }
        values[cell] = digit
        notes[cell] = 0
        val clear = (1 shl digit).inv()
        for (p in spec.peers[cell]) notes[p] = notes[p] and clear

        streak++
        val completed = spec.unitsOf[cell].filter { u -> spec.bonusUnits[u].all { values[it] != 0 } }
        val points = Scoring.placement(spec.rules, streak) + completed.size * Scoring.unitBonus(spec.rules)
        score += points

        if (isSolved) {
            val bonus = Scoring.finish(spec.rules, elapsedMs, strikes)
            score += bonus.total
            undoStack.clear()
            return MoveResult.Solved(points, completed, bonus)
        }
        return MoveResult.Placed(points, completed)
    }

    fun toggleNote(cell: Int, digit: Int): MoveResult {
        if (isOver || !spec.playable[cell] || values[cell] != 0) return MoveResult.Ignored
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
}

object Scoring {
    /** Base points, plus a streak bonus for consecutive correct moves (caps at +100%). */
    fun placement(r: ScoreRules, streak: Int): Int =
        r.basePoints + r.basePoints * minOf(streak - 1, 10) / 10

    fun unitBonus(r: ScoreRules) = r.basePoints * 3

    /** Finishing faster than par and with strikes to spare both pay out. */
    fun finish(r: ScoreRules, elapsedMs: Long, strikes: Int): FinishBonus {
        val secs = (elapsedMs / 1000).toInt()
        val time = (r.parSeconds - secs).coerceAtLeast(0) * r.basePoints / 10
        val lives = (MAX_STRIKES - strikes).coerceAtLeast(0) * r.basePoints * 10
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
