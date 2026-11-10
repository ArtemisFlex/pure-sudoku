package com.puresudoku.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.GameCategory
import com.puresudoku.data.DifficultyStats
import com.puresudoku.data.LeaderEntry
import com.puresudoku.data.SaveRepository
import com.puresudoku.data.SlotSummary
import com.puresudoku.data.SolveRecord
import com.puresudoku.data.ThemeMode
import com.puresudoku.ui.games.Games
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Hub : Screen
    data class Category(val category: GameCategory) : Screen
    data class Menu(val kind: GameKind) : Screen
    data class PickLevel(val kind: GameKind) : Screen
    /** Continue list; when [replaceFor] is set, the player is choosing a slot to overwrite. */
    data class Slots(val kind: GameKind, val replaceFor: Difficulty? = null) : Screen
    data class Generating(val kind: GameKind, val difficulty: Difficulty) : Screen
    data object Playing : Screen
}

data class WinSummary(val timeMs: Long, val score: Int, val bonus: List<BonusLine>, val record: SolveRecord, val rank: Int = 0)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SaveRepository(app)
    private val backupStore = com.puresudoku.data.BackupStore(app)

    var screen by mutableStateOf<Screen>(Screen.Hub); private set
    var themeMode by mutableStateOf(repo.themeMode()); private set
    var slots by mutableStateOf(loadSlots()); private set
    var stats by mutableStateOf(loadStats()); private set

    var session by mutableStateOf<Session?>(null); private set
    var activeKind by mutableStateOf(GameKind.SUDOKU); private set
    private var activeSlot = -1
    var paused by mutableStateOf(false); private set
    var winSummary by mutableStateOf<WinSummary?>(null); private set
    /** Game whose how-to-play cards are open, or null. */
    var tutorialFor by mutableStateOf<GameKind?>(null); private set

    private var foreground = true
    private var timerJob: Job? = null
    private var generateJob: Job? = null

    fun keepsakes(kind: GameKind): List<String> = repo.keepsakes(kind)
    fun history(kind: GameKind) = repo.history(kind)
    fun gameStats(kind: GameKind) = repo.gameStats(kind)

    // ---------------- backup ----------------

    /** Set by the activity: open the system folder / file pickers. */
    var pickBackupFolder: (() -> Unit)? = null
    var pickBackupFile: (() -> Unit)? = null

    var backupFolderName by mutableStateOf(backupStore.folderName()); private set
    var lastBackupAt by mutableStateOf(backupStore.lastBackupAt); private set
    /** A backup waiting for the player to confirm restoring it: (json, savedAt). */
    var pendingRestore by mutableStateOf<Pair<String, Long>?>(null); private set
    var backupMessage by mutableStateOf<String?>(null)

    private fun autoBackup() {
        if (backupStore.folder == null) return
        val json = repo.exportAll()
        viewModelScope.launch(Dispatchers.IO) {
            val ok = backupStore.write(json)
            withContext(Dispatchers.Main) {
                lastBackupAt = backupStore.lastBackupAt
                if (!ok) backupMessage = "Couldn't write to your backup folder. Choose it again in Settings."
            }
        }
    }

    fun backupNow() {
        if (backupStore.folder == null) { pickBackupFolder?.invoke(); return }
        autoBackup()
        backupMessage = "Backed up to ${backupFolderName}"
    }

    fun onBackupFolderPicked(uri: android.net.Uri) {
        backupStore.folder = uri
        backupFolderName = backupStore.folderName()
        val found = backupStore.readFromFolder(uri)
        val savedAt = found?.let { runCatching { org.json.JSONObject(it).getLong("savedAt") }.getOrNull() }
        if (found != null && savedAt != null) pendingRestore = found to savedAt
        else { autoBackup(); backupMessage = "Backups will be saved to $backupFolderName" }
    }

    fun onBackupFilePicked(uri: android.net.Uri) {
        val text = backupStore.read(uri)
        val savedAt = text?.let { runCatching { org.json.JSONObject(it).takeIf { o -> o.optString("app") in com.puresudoku.data.SaveRepository.BACKUP_IDS }?.getLong("savedAt") }.getOrNull() }
        if (text == null || savedAt == null) backupMessage = "That file isn't a PureGames backup"
        else pendingRestore = text to savedAt
    }

    /** [restore] true replaces this phone's data with the backup; false keeps it (and overwrites the backup). */
    fun resolvePendingRestore(restore: Boolean) {
        val p = pendingRestore ?: return
        pendingRestore = null
        if (restore) {
            if (repo.importAll(p.first)) {
                themeMode = repo.themeMode()
                refreshMeta()
                backupMessage = "Restored your saves, records and history"
            } else backupMessage = "That backup couldn't be read"
        }
        autoBackup()
    }

    // ---------------- settings ----------------

    fun updateThemeMode(m: ThemeMode) { themeMode = m; repo.setThemeMode(m) }

    // ---------------- navigation ----------------

    fun openCategory(c: GameCategory) { screen = Screen.Category(c) }
    fun leaderboard(kind: GameKind) = repo.leaderboard(kind)

    fun openGame(kind: GameKind) { activeKind = kind; refreshMeta(); screen = Screen.Menu(kind) }
    fun openLevelPicker(kind: GameKind) { screen = Screen.PickLevel(kind) }
    fun openSlots(kind: GameKind) { refreshMeta(); screen = Screen.Slots(kind) }

    fun back() {
        if (tutorialFor != null) { closeTutorial(); return }
        when (val s = screen) {
            Screen.Hub -> Unit
            is Screen.Category -> screen = Screen.Hub
            is Screen.Menu -> screen = Screen.Category(s.kind.category)
            is Screen.PickLevel -> screen = Screen.Menu(s.kind)
            is Screen.Slots -> screen = if (s.replaceFor != null) Screen.PickLevel(s.kind) else Screen.Menu(s.kind)
            is Screen.Generating -> { generateJob?.cancel(); screen = Screen.PickLevel(s.kind) }
            Screen.Playing -> if (session?.outcome == Outcome.PLAYING && !paused) pause() else leaveGame()
        }
    }

    /** Back to this game's menu, saving progress. */
    fun leaveGame() {
        persist()
        stopTimer()
        detach(session)
        session = null
        paused = false
        refreshMeta()
        screen = Screen.Menu(activeKind)
    }

    // ---------------- starting games ----------------

    fun chooseLevel(kind: GameKind, d: Difficulty) {
        val free = slots.getValue(kind).indexOfFirst { it == null }
        if (free >= 0) startNew(kind, d, free) else screen = Screen.Slots(kind, replaceFor = d)
    }

    fun replaceSlot(kind: GameKind, slot: Int, d: Difficulty) {
        recordAbandon(kind, slot)
        repo.clear(kind, slot)
        startNew(kind, d, slot)
    }

    /** Starts the training-wheels game for one level: always the same start, never saved or scored. */
    fun startTraining(kind: GameKind, d: Difficulty) {
        activeKind = kind
        screen = Screen.Generating(kind, d)
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            val s = withContext(Dispatchers.Default) {
                val def = Games.of(kind)
                val start = repo.trainingStart(kind, d) ?: def.generate(d).toJson().also { repo.saveTrainingStart(kind, d, it) }
                runCatching { def.restore(start) }.getOrElse { def.generate(d) }
            }
            (s as? com.puresudoku.ui.games.board.DuelSession)?.cancelThinking()
            s.training = true
            val wait = 700 - (SystemClock.elapsedRealtime() - started)
            if (wait > 0) delay(wait)
            attach(s, -1)
            (s as? com.puresudoku.ui.games.board.DuelSession)?.resume()
        }
    }

    private fun startNew(kind: GameKind, d: Difficulty, slot: Int) {
        activeKind = kind
        screen = Screen.Generating(kind, d)
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            val s = withContext(Dispatchers.Default) { Games.of(kind).generate(d) }
            // Let the build animation play for a moment, even when generation is instant.
            val wait = 900 - (SystemClock.elapsedRealtime() - started)
            if (wait > 0) delay(wait)
            attach(s, slot)
            persist()
        }
    }

    fun resume(kind: GameKind, slot: Int) {
        val json = repo.loadJson(kind, slot) ?: return
        val s = runCatching { Games.of(kind).restore(json) }.getOrNull()
        if (s == null) { repo.clear(kind, slot); refreshMeta(); return }
        attach(s, slot)
    }

    fun deleteSlot(kind: GameKind, slot: Int) {
        recordAbandon(kind, slot)
        repo.clear(kind, slot)
        refreshMeta()
        autoBackup()
    }

    /** A saved game thrown away unfinished counts in your history. */
    private fun recordAbandon(kind: GameKind, slot: Int) {
        val sum = repo.summaries(kind).getOrNull(slot) ?: return
        repo.addHistory(kind, com.puresudoku.data.GameResult(com.puresudoku.data.GameResult.Result.ABANDONED, sum.score, sum.difficulty, sum.elapsedMs, System.currentTimeMillis()))
    }

    /** A session we leave must not report back (e.g. a computer move landing later). */
    private fun detach(s: Session?) {
        s ?: return
        s.onChanged = {}; s.onFinished = {}
        (s as? com.puresudoku.ui.games.board.DuelSession)?.cancelThinking()
    }

    private fun attach(s: Session, slot: Int) {
        s.onChanged = ::persist
        s.onFinished = ::onFinished
        session = s
        activeKind = s.kind
        activeSlot = slot
        paused = false
        winSummary = null
        screen = Screen.Playing
        if (!repo.tutorialSeen(s.kind)) {
            repo.markTutorialSeen(s.kind)
            tutorialFor = s.kind
        }
        updateTimer()
    }

    fun playAgain() {
        val s = session ?: return
        detach(s)
        session = null
        if (s.training) { startTraining(s.kind, s.difficulty); return }
        refreshMeta()
        // The finished game's slot was already cleared, so the new game reuses it.
        startNew(s.kind, s.difficulty, activeSlot)
    }

    // ---------------- how to play ----------------

    fun openTutorial(kind: GameKind) { tutorialFor = kind; updateTimer() }
    fun closeTutorial() { tutorialFor = null; updateTimer() }

    // ---------------- end states ----------------

    private fun onFinished(outcome: Outcome) {
        val s = session ?: return
        stopTimer()
        if (s.training) return
        repo.clear(s.kind, activeSlot)
        if (outcome == Outcome.WON) {
            repo.addKeepsakes(s.kind, s.keepsakes)
            val record = repo.recordSolve(s.kind, s.difficulty, s.score, s.elapsedMs)
            val rank = repo.addToLeaderboard(s.kind, LeaderEntry(s.score, s.difficulty, s.elapsedMs, System.currentTimeMillis()))
            winSummary = WinSummary(s.elapsedMs, s.score, s.bonusLines, record, rank)
        }
        repo.addHistory(s.kind, com.puresudoku.data.GameResult(
            when (outcome) {
                Outcome.WON -> com.puresudoku.data.GameResult.Result.WON
                Outcome.DRAW -> com.puresudoku.data.GameResult.Result.DRAW
                else -> com.puresudoku.data.GameResult.Result.LOST
            },
            s.score, s.difficulty, s.elapsedMs, System.currentTimeMillis(),
        ))
        refreshMeta()
        autoBackup()
    }

    // ---------------- pause & timer ----------------

    fun pause() {
        if (session?.outcome != Outcome.PLAYING) return
        paused = true
        updateTimer()
        persist()
    }

    fun unpause() { paused = false; updateTimer() }

    /** Called from the activity lifecycle. The clock never runs in the background. */
    fun setForeground(fg: Boolean) {
        foreground = fg
        if (!fg) { persist(); autoBackup() }
        updateTimer()
    }

    private fun updateTimer() {
        val s = session
        val run = foreground && screen == Screen.Playing && !paused && tutorialFor == null &&
            s != null && s.outcome == Outcome.PLAYING
        if (run) startTimer() else stopTimer()
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            var sinceSave = 0L
            while (isActive) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                val s = session ?: break
                if (s.outcome != Outcome.PLAYING) break
                val dt = now - last
                last = now
                s.elapsedMs += dt
                s.onTick(dt)
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
        val s = session ?: return
        if (activeSlot < 0 || s.outcome != Outcome.PLAYING) return
        repo.save(s.kind, activeSlot, s.toJson())
    }

    private fun refreshMeta() {
        slots = loadSlots()
        stats = loadStats()
    }

    private fun loadSlots(): Map<GameKind, List<SlotSummary?>> = GameKind.entries.associateWith { repo.summaries(it) }

    private fun loadStats(): Map<GameKind, Map<Difficulty, DifficultyStats>> =
        GameKind.entries.associateWith { k -> Difficulty.entries.associateWith { repo.stats(k, it) } }

    override fun onCleared() {
        persist()
        super.onCleared()
    }
}
