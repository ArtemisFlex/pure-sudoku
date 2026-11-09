package com.puresudoku.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.puresudoku.core.Difficulty
import com.puresudoku.core.DigitGame
import com.puresudoku.core.DigitSpec
import com.puresudoku.core.GameKind
import com.puresudoku.core.MoveResult
import com.puresudoku.core.ints
import com.puresudoku.core.toJson
import org.json.JSONObject

/** One-shot animation triggers. */
data class PlacedFx(val id: Long, val cell: Int, val points: Int)
data class WaveFx(val id: Long, val origin: Int, val cells: Set<Int>)
data class ErrorFx(val id: Long, val cell: Int, val digit: Int)

/** Session for the "write digits into cells" games: Sudoku, Cage Math, Kakuro. */
abstract class DigitGridSession(kind: GameKind, difficulty: Difficulty, val game: DigitGame) : Session(kind, difficulty) {
    val spec: DigitSpec get() = game.spec

    /** Bumped on every board change; UI reads board data after reading this. */
    var boardVersion by mutableIntStateOf(0); private set
    var selected by mutableIntStateOf(-1); private set
    var notesMode by mutableStateOf(false); private set
    var canUndo by mutableStateOf(false); private set
    var placedFx by mutableStateOf<PlacedFx?>(null); private set
    var waveFx by mutableStateOf<WaveFx?>(null); private set
    var errorFx by mutableStateOf<ErrorFx?>(null); private set

    init {
        score = game.score
        strikes = game.strikes
    }

    fun value(i: Int) = game.values[i]
    fun notes(i: Int) = game.notes[i]
    fun isGiven(i: Int) = game.isGiven(i)
    fun isPlayable(i: Int) = spec.playable[i]
    fun remaining(d: Int) = game.remaining(d)

    /** Cells drawn with a thick border between them are in different regions. */
    open fun region(i: Int): Int = 0

    override val progress: Float get() = game.progress

    fun select(cell: Int) {
        if (outcome != Outcome.PLAYING || !spec.playable[cell]) return
        selected = if (selected == cell) -1 else cell
    }

    /** Demo/tutorial helper: select without toggling. */
    fun selectExact(cell: Int) { selected = cell }

    fun toggleNotesMode() { notesMode = !notesMode }

    fun input(digit: Int) {
        val cell = selected
        if (outcome != Outcome.PLAYING) return
        if (cell < 0) { nudge("Tap a square first, then a number"); return }
        if (game.values[cell] != 0) {
            errorFx = ErrorFx(FxIds.next(), cell, 0)
            nudge(if (game.isGiven(cell)) "That number is part of the puzzle" else "That square is already solved")
            return
        }
        val result = if (notesMode) game.toggleNote(cell, digit) else game.place(cell, digit, elapsedMs)
        when (result) {
            MoveResult.Ignored -> return
            MoveResult.NotesChanged -> Unit
            is MoveResult.Placed -> {
                placedFx = PlacedFx(FxIds.next(), cell, result.points)
                if (result.completedUnits.isNotEmpty()) wave(cell, result.completedUnits)
            }
            is MoveResult.Wrong -> errorFx = ErrorFx(FxIds.next(), cell, digit)
            is MoveResult.Solved -> placedFx = PlacedFx(FxIds.next(), cell, result.points)
        }
        sync()
        when (result) {
            is MoveResult.Wrong -> if (result.gameOver) { selected = -1; finish(false) }
            is MoveResult.Solved -> {
                selected = -1
                waveFx = WaveFx(FxIds.next(), cell, (0 until spec.cells).filter { spec.playable[it] }.toSet())
                finish(
                    true,
                    listOf(BonusLine("Time bonus", result.bonus.timeBonus), BonusLine("Hearts left bonus", result.bonus.livesBonus)),
                    bonusAlreadyScored = true,
                )
            }
            else -> Unit
        }
        if (outcome == Outcome.PLAYING) changed()
    }

    fun erase() {
        if (outcome != Outcome.PLAYING) return
        if (selected < 0) { nudge("Tap a square first"); return }
        if (game.values[selected] != 0) { errorFx = ErrorFx(FxIds.next(), selected, 0); nudge("Erase only clears notes"); return }
        if (game.erase(selected) != MoveResult.Ignored) { sync(); changed() }
    }

    fun undo() {
        if (outcome != Outcome.PLAYING) return
        if (game.undo()) { sync(); changed() }
    }

    private fun wave(origin: Int, units: List<Int>) {
        val cells = units.flatMap { spec.bonusUnits[it].toList() }.toSet()
        waveFx = WaveFx(FxIds.next(), origin, cells)
    }

    private fun sync() {
        score = game.score
        strikes = game.strikes
        canUndo = game.canUndo
        boardVersion++
    }

    override fun saveState(): JSONObject = JSONObject().apply {
        put("givens", game.givens.toJson())
        put("solution", game.solution.toJson())
        put("values", game.values.toJson())
        put("notes", game.notes.toJson())
        put("streak", game.streak)
        saveExtra(this)
    }

    /** Game-specific layout data (cages, clue layout...). */
    protected open fun saveExtra(o: JSONObject) {}

    companion object {
        /** Rebuilds the shared digit-game state saved by [saveState]. */
        fun restoreGame(spec: DigitSpec, common: JSONObject): DigitGame {
            val st = common.getJSONObject("state")
            return DigitGame(
                spec,
                givens = st.ints("givens"),
                solution = st.ints("solution"),
                values = st.ints("values"),
                notes = st.ints("notes"),
                strikes = common.optInt("strikes", 0),
                score = common.optInt("score", 0),
                streak = st.optInt("streak", 0),
            )
        }
    }
}
