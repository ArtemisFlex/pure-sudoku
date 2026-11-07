package com.puresudoku.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puresudoku.core.Difficulty
import com.puresudoku.core.FinishBonus
import com.puresudoku.core.Game
import com.puresudoku.core.GameSnapshot
import com.puresudoku.core.Grid
import com.puresudoku.core.MoveResult
import com.puresudoku.core.SudokuGenerator
import com.puresudoku.data.DifficultyStats
import com.puresudoku.data.SaveRepository
import com.puresudoku.data.SolveRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object PickDifficulty : Screen
    /** Continue list; when [replaceFor] is set, the player is choosing a slot to overwrite. */
    data class Slots(val replaceFor: Difficulty? = null) : Screen
    data class Generating(val difficulty: Difficulty) : Screen
    data object Playing : Screen
}

enum class Phase { PLAYING, PAUSED, WON, LOST }

/** One-shot animation triggers. [id] changes every time so the UI can key effects on it. */
data class PlacedFx(val id: Long, val cell: Int, val points: Int)
data class WaveFx(val id: Long, val origin: Int, val cells: Set<Int>)
data class ErrorFx(val id: Long, val cell: Int, val digit: Int)

data class WinSummary(val timeMs: Long, val score: Int, val bonus: FinishBonus, val strikes: Int, val record: SolveRecord)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SaveRepository(app)
    private val generator = SudokuGenerator()

    var screen by mutableStateOf<Screen>(Screen.Home); private set
    var slots by mutableStateOf(repo.loadSlots()); private set
    var stats by mutableStateOf(loadStats()); private set

    // ---- current game ----
    private var game: Game? = null
    var activeSlot = -1; private set
    var difficulty by mutableStateOf(Difficulty.EASY); private set
    var phase by mutableStateOf(Phase.PLAYING); private set
    /** Bumped on every board change; the UI reads board data through it. */
    var boardVersion by mutableIntStateOf(0); private set
    var selected by mutableIntStateOf(-1); private set
    var notesMode by mutableStateOf(false); private set
    var elapsedMs by mutableLongStateOf(0L); private set
    var score by mutableIntStateOf(0); private set
    var strikes by mutableIntStateOf(0); private set
    var canUndo by mutableStateOf(false); private set
    var placedFx by mutableStateOf<PlacedFx?>(null); private set
    var waveFx by mutableStateOf<WaveFx?>(null); private set
    var errorFx by mutableStateOf<ErrorFx?>(null); private set
    var winSummary by mutableStateOf<WinSummary?>(null); private set

    private var fxId = 0L
    private var foreground = true
    private var timerJob: Job? = null
    private var generateJob: Job? = null

    // Read-only board accessors for the UI (call after reading boardVersion).
    fun value(i: Int) = game?.values?.get(i) ?: 0
    fun notes(i: Int) = game?.notes?.get(i) ?: 0
    fun isGiven(i: Int) = game?.isGiven(i) ?: false
    fun remaining(d: Int) = game?.remaining(d) ?: 9
    fun solutionAt(i: Int) = game?.solution?.get(i) ?: 0

    val hasSaves get() = slots.any { it != null }

    // ---------------- navigation ----------------

    fun goHome() {
        persist()
        stopTimer()
        game = null
        refreshMeta()
        screen = Screen.Home
    }

    fun openDifficultyPicker() { screen = Screen.PickDifficulty }
    fun openSlots() { refreshMeta(); screen = Screen.Slots() }

    fun back() {
        when (val s = screen) {
            Screen.Home -> Unit
            Screen.PickDifficulty -> screen = Screen.Home
            is Screen.Slots -> screen = if (s.replaceFor != null) Screen.PickDifficulty else Screen.Home
            is Screen.Generating -> { generateJob?.cancel(); screen = Screen.PickDifficulty }
            Screen.Playing -> if (phase == Phase.PLAYING) pause() else goHome()
        }
    }

    // ---------------- starting games ----------------

    fun chooseDifficulty(d: Difficulty) {
        val free = slots.indexOfFirst { it == null }
        if (free >= 0) startNew(d, free) else screen = Screen.Slots(replaceFor = d)
    }

    fun replaceSlot(slot: Int, d: Difficulty) {
        repo.clear(slot)
        startNew(d, slot)
    }

    private fun startNew(d: Difficulty, slot: Int) {
        screen = Screen.Generating(d)
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            val puzzle = withContext(Dispatchers.Default) { generator.generate(d) }
            // Let the build animation play for a moment, even when generation is instant.
            val wait = 900 - (SystemClock.elapsedRealtime() - started)
            if (wait > 0) delay(wait)
            attach(Game.new(puzzle), slot)
            persist()
        }
    }

    fun resume(slot: Int) {
        val snap = repo.load(slot) ?: return
        attach(Game.restore(snap), slot)
    }

    fun deleteSlot(slot: Int) {
        repo.clear(slot)
        refreshMeta()
    }

    private fun attach(g: Game, slot: Int) {
        game = g
        activeSlot = slot
        difficulty = g.difficulty
        selected = -1
        notesMode = false
        winSummary = null
        placedFx = null; waveFx = null; errorFx = null
        phase = Phase.PLAYING
        syncFromGame()
        screen = Screen.Playing
        startTimer()
    }

    fun playAgain() {
        // The finished game's slot was already cleared, so the new game reuses it.
        game = null
        refreshMeta()
        startNew(difficulty, activeSlot)
    }

    // ---------------- input ----------------

    fun select(cell: Int) {
        if (phase != Phase.PLAYING) return
        selected = if (selected == cell) -1 else cell
    }

    fun toggleNotesMode() { notesMode = !notesMode }

    fun input(digit: Int) {
        val g = game ?: return
        val cell = selected
        if (phase != Phase.PLAYING || cell < 0) return
        val result = if (notesMode) g.toggleNote(cell, digit) else g.place(cell, digit)
        when (result) {
            MoveResult.Ignored -> return
            MoveResult.NotesChanged -> Unit
            is MoveResult.Placed -> {
                placedFx = PlacedFx(++fxId, cell, result.points)
                if (result.completedUnits.isNotEmpty()) wave(cell, result.completedUnits)
            }
            is MoveResult.Wrong -> {
                errorFx = ErrorFx(++fxId, cell, digit)
                if (result.gameOver) onLost()
            }
            is MoveResult.Solved -> {
                placedFx = PlacedFx(++fxId, cell, result.points)
                onWon(result.bonus)
            }
        }
        syncFromGame()
        if (phase == Phase.PLAYING) persist()
    }

    fun erase() {
        val g = game ?: return
        if (phase != Phase.PLAYING || selected < 0) return
        if (g.erase(selected) != MoveResult.Ignored) { syncFromGame(); persist() }
    }

    fun undo() {
        val g = game ?: return
        if (phase != Phase.PLAYING) return
        if (g.undo()) { syncFromGame(); persist() }
    }

    private fun wave(origin: Int, units: List<Int>) {
        val cells = units.flatMap { Grid.UNITS[it].toList() }.toSet()
        waveFx = WaveFx(++fxId, origin, cells)
    }

    // ---------------- end states ----------------

    private fun onWon(bonus: FinishBonus) {
        val g = game ?: return
        stopTimer()
        phase = Phase.WON
        selected = -1
        repo.clear(activeSlot)
        val record = repo.recordSolve(g.difficulty, g.score, g.elapsedMs)
        winSummary = WinSummary(g.elapsedMs, g.score, bonus, g.strikes, record)
        waveFx = WaveFx(++fxId, 40, (0 until 81).toSet())
        refreshMeta()
    }

    private fun onLost() {
        stopTimer()
        phase = Phase.LOST
        selected = -1
        repo.clear(activeSlot)
        refreshMeta()
    }

    // ---------------- pause & timer ----------------

    fun pause() {
        if (phase != Phase.PLAYING) return
        phase = Phase.PAUSED
        stopTimer()
        persist()
    }

    fun unpause() {
        if (phase != Phase.PAUSED) return
        phase = Phase.PLAYING
        startTimer()
    }

    /** Called from the activity lifecycle. The clock never runs in the background. */
    fun setForeground(fg: Boolean) {
        foreground = fg
        if (!fg) {
            stopTimer()
            persist()
        } else if (screen == Screen.Playing && phase == Phase.PLAYING) {
            startTimer()
        }
    }

    private fun startTimer() {
        if (!foreground || timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            var sinceSave = 0L
            while (isActive) {
                delay(200)
                val now = SystemClock.elapsedRealtime()
                val g = game ?: break
                val dt = now - last
                last = now
                g.elapsedMs += dt
                elapsedMs = g.elapsedMs
                sinceSave += dt
                if (sinceSave > 5000) { sinceSave = 0; persist() }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    // ---------------- persistence ----------------

    private fun persist() {
        val g = game ?: return
        if (activeSlot < 0 || phase == Phase.WON || phase == Phase.LOST) return
        repo.save(activeSlot, g.snapshot())
    }

    private fun syncFromGame() {
        val g = game ?: return
        score = g.score
        strikes = g.strikes
        elapsedMs = g.elapsedMs
        canUndo = g.canUndo
        boardVersion++
    }

    private fun refreshMeta() {
        slots = repo.loadSlots()
        stats = loadStats()
    }

    private fun loadStats(): Map<Difficulty, DifficultyStats> =
        Difficulty.entries.associateWith { repo.stats(it) }

    override fun onCleared() {
        persist()
        super.onCleared()
    }
}

/** Fraction of the empty cells that have been solved. */
fun GameSnapshot.progress(): Float {
    val empty = givens.count { it == 0 }
    if (empty == 0) return 1f
    val solved = (0 until 81).count { givens[it] == 0 && values[it] != 0 }
    return solved / empty.toFloat()
}
