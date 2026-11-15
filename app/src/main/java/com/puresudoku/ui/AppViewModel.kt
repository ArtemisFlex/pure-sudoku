package com.puresudoku.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    /** Every game two people can play together, on one device or online. */
    data object TwoPlayer : Screen
    data class Menu(val kind: GameKind) : Screen
    data class PickLevel(val kind: GameKind) : Screen
    /** Continue list; when [replaceFor] is set, the player is choosing a slot to overwrite. */
    data class Slots(val kind: GameKind, val replaceFor: Difficulty? = null) : Screen
    data class Generating(val kind: GameKind, val difficulty: Difficulty) : Screen
    data object Playing : Screen
    /** Host or join an online game of [kind] with a room code. */
    data class Online(val kind: GameKind) : Screen
}

data class WinSummary(val timeMs: Long, val score: Int, val bonus: List<BonusLine>, val record: SolveRecord, val rank: Int = 0)

/** The app's state and navigation, shared by Android and the browser; each supplies its storage and backups. */
class AppViewModel(store: com.puresudoku.data.KeyValueStore, private val backups: com.puresudoku.data.BackupHost) {
    private val repo = SaveRepository(store)
    private val viewModelScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)

    var screen by mutableStateOf<Screen>(Screen.Hub); private set
    var themeMode by mutableStateOf(repo.themeMode()); private set
    var tableMode by mutableStateOf(repo.tableMode()); private set
    /** Pinned favourites shown at the top of the home screen. */
    var favorites by mutableStateOf(repo.favorites()); private set
    fun isFavorite(kind: GameKind) = kind in favorites
    /** Pins or unpins a game; returns false if all 16 favourite spots are taken. */
    fun toggleFavorite(kind: GameKind): Boolean {
        favorites = when {
            kind in favorites -> favorites - kind
            favorites.size >= MAX_FAVORITES -> return false
            else -> favorites + kind
        }
        repo.setFavorites(favorites)
        return true
    }
    fun toggleTableMode() { tableMode = !tableMode; repo.setTableMode(tableMode) }
    var slots by mutableStateOf(loadSlots()); private set
    var stats by mutableStateOf(loadStats()); private set

    var session by mutableStateOf<Session?>(null); private set
    var activeKind by mutableStateOf(GameKind.SUDOKU); private set
    private var activeSlot = -1
    private var pendingLocalTwoPlayer = false
    val choosingLocalTwoPlayer: Boolean get() = pendingLocalTwoPlayer
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

    var backupFolderName by mutableStateOf(backups.folderName()); private set
    var lastBackupAt by mutableStateOf(backups.lastBackupAt); private set
    /** A backup waiting for the player to confirm restoring it: (json, savedAt). */
    var pendingRestore by mutableStateOf<Pair<String, Long>?>(null); private set
    var backupMessage by mutableStateOf<String?>(null)

    private fun autoBackup() {
        if (!backups.hasFolder) return
        val json = repo.exportAll()
        viewModelScope.launch(com.puresudoku.platform.ioDispatcher) {
            val ok = backups.write(json)
            withContext(Dispatchers.Main) {
                lastBackupAt = backups.lastBackupAt
                if (!ok) backupMessage = "Couldn't write to your backup folder. Choose it again in Settings."
            }
        }
    }

    fun backupNow() {
        if (!backups.hasFolder) { pickBackupFolder?.invoke(); return }
        autoBackup()
        backupMessage = "Backed up to ${backupFolderName}"
    }

    /** True where backups go to a folder automatically (Android); false where the player downloads a file (web). */
    val backupsUseFolder: Boolean get() = backups.usesFolder

    /** Everything, as one backup document (for the browser's download). */
    fun exportBackup(): String = repo.exportAll()

    fun onBackupFolderPicked(id: String) {
        val found = backups.useFolder(id)
        backupFolderName = backups.folderName()
        val savedAt = found?.let { runCatching { org.json.JSONObject(it).getLong("savedAt") }.getOrNull() }
        if (found != null && savedAt != null) pendingRestore = found to savedAt
        else { autoBackup(); backupMessage = "Backups will be saved to $backupFolderName" }
    }

    fun onBackupFilePicked(id: String) = onBackupText(backups.readFile(id))

    /** A backup file's text, read by the platform; null if it couldn't be read. */
    fun onBackupText(text: String?) {
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

    /** A game's page remembers whether it was opened from the Two Players list, so Back returns there. */
    private var menuFromTwoPlayer = false
    fun openTwoPlayer() { screen = Screen.TwoPlayer }
    fun openGame(kind: GameKind) { menuFromTwoPlayer = screen == Screen.TwoPlayer; activeKind = kind; refreshMeta(); screen = Screen.Menu(kind) }
    fun openLevelPicker(kind: GameKind) { pendingLocalTwoPlayer = false; screen = Screen.PickLevel(kind) }

    fun startLocalTwoPlayer(kind: GameKind) {
        if (kind !in com.puresudoku.core.localTwoPlayerGames) return
        pendingLocalTwoPlayer = true
        chooseLevel(kind, Difficulty.EASY)
    }
    fun openSlots(kind: GameKind) { refreshMeta(); screen = Screen.Slots(kind) }

    // ---------------- online play ----------------

    fun openOnline(kind: GameKind) {
        com.puresudoku.online.OnlineMatch.leave()
        com.puresudoku.online.OnlineMatch.onStart = ::startOnline
        screen = Screen.Online(kind)
    }

    fun closeOnline(kind: GameKind) { com.puresudoku.online.OnlineMatch.leave(); screen = Screen.Menu(kind) }

    // Online games run as pass-and-play on both devices. After each of your turns the whole game is
    // sent across and the other device carries on from it, so dice, decks and every rule stay in step.
    // Online games aren't saved and don't count in stats.
    private var sentRev = 0
    private var seenRev = 0
    /** Whose turn it was at the last sync: only that device sends the next update. */
    private var syncTurn: Int? = null
    private var applyingRemote = false
    private var endedRemotely = false

    /** Both devices are connected: the host deals the game and sends it; the guest waits for it. */
    private fun startOnline(kind: GameKind, seat: Int) {
        sentRev = 0; seenRev = 0; syncTurn = null; endedRemotely = false
        com.puresudoku.online.OnlineMatch.onMessage = ::onlineMessage
        if (seat != 1) return
        val s = Games.of(kind).generate(Difficulty.EASY) as? com.puresudoku.ui.games.board.DuelSession ?: return
        s.localTwoPlayer = true
        s.message = twoPlayerOpening(kind)
        attachOnline(s, seat)
        sendOnline(s)
    }

    private fun attachOnline(s: com.puresudoku.ui.games.board.DuelSession, seat: Int) {
        s.localTwoPlayer = true
        s.onlineSeat = seat
        attach(s, -1)
        s.onChanged = { onlineChanged(s) }
        syncTurn = s.tableSeat
        if (s.tableSeat == seat) s.skipHandoff()
    }

    private fun mineToSend(s: com.puresudoku.ui.games.board.DuelSession) = syncTurn == null || syncTurn == s.onlineSeat

    private fun onlineChanged(s: com.puresudoku.ui.games.board.DuelSession) {
        if (applyingRemote || session !== s) return
        if (mineToSend(s)) sendOnline(s)
        syncTurn = s.tableSeat
        if (s.tableSeat == s.onlineSeat) s.skipHandoff()
    }

    private fun sendOnline(s: Session) = com.puresudoku.online.OnlineMatch.send("s:${++sentRev}|${s.toJson()}")

    private fun onlineMessage(msg: String) {
        val cur = session as? com.puresudoku.ui.games.board.DuelSession
        when {
            msg == "bye" -> { endedRemotely = true; cur?.opponentLeft() }
            msg.startsWith("e:") -> {
                val o = runCatching { Outcome.valueOf(msg.drop(2)) }.getOrNull() ?: return
                endedRemotely = true; cur?.remoteEnd(o)
            }
            msg.startsWith("s:") -> {
                val bar = msg.indexOf('|')
                val rev = msg.substring(2, maxOf(2, bar)).toIntOrNull() ?: return
                if (rev <= seenRev) return
                seenRev = rev
                val json = runCatching { org.json.JSONObject(msg.substring(bar + 1)) }.getOrNull() ?: return
                val kind = GameKind.entries.firstOrNull { it.name == json.optString("kind") } ?: return
                val s = runCatching { Games.of(kind).restore(json) }.getOrNull() as? com.puresudoku.ui.games.board.DuelSession ?: return
                detach(cur)
                applyingRemote = true
                attachOnline(s, com.puresudoku.online.OnlineMatch.seat)
                applyingRemote = false
            }
        }
    }

    /** The first message of a pass-and-play game, naming who starts. */
    private fun twoPlayerOpening(kind: GameKind) = when (kind) {
        GameKind.TIC_TAC_TOE -> "Player 1: X, your turn"; GameKind.CHESS -> "Player 1: White, your turn"; GameKind.SHOGI -> "Player 1: Sente, your turn"
        GameKind.MANCALA -> "Player 1: bottom row, your turn"; GameKind.MORRIS -> "Player 1: White, your turn"; GameKind.BACKGAMMON -> "Player 1: roll"
        GameKind.CHECKERS, GameKind.XIANGQI -> "Player 1: Red, your turn"; GameKind.GO, GameKind.GOMOKU -> "Player 1: Black, your turn"
        else -> "Player 1, your turn"
    }

    private val Session.isOnline get() = (this as? com.puresudoku.ui.games.board.DuelSession)?.online == true

    fun back() {
        if (tutorialFor != null) { closeTutorial(); return }
        when (val s = screen) {
            Screen.Hub -> Unit
            is Screen.Category, Screen.TwoPlayer -> screen = Screen.Hub
            is Screen.Menu -> screen = if (menuFromTwoPlayer) Screen.TwoPlayer else Screen.Category(s.kind.category)
            is Screen.PickLevel -> screen = Screen.Menu(s.kind)
            is Screen.Online -> closeOnline(s.kind)
            is Screen.Slots -> {
                val local = pendingLocalTwoPlayer
                pendingLocalTwoPlayer = false
                screen = if (s.replaceFor != null && !local) Screen.PickLevel(s.kind) else Screen.Menu(s.kind)
            }
            is Screen.Generating -> { generateJob?.cancel(); screen = Screen.PickLevel(s.kind) }
            Screen.Playing -> if (session?.outcome == Outcome.PLAYING && !paused) pause() else leaveGame()
        }
    }

    /** Back to this game's menu, saving progress. */
    fun leaveGame() {
        if (session?.isOnline == true) {
            com.puresudoku.online.OnlineMatch.leave()
            stopTimer(); detach(session); session = null; paused = false
            screen = Screen.Menu(activeKind); return
        }
        persist()
        stopTimer()
        detach(session)
        session = null
        paused = false
        refreshMeta()
        screen = Screen.Menu(activeKind)
    }

    // ---------------- starting games ----------------

    /** "New game": the level picker, or straight to a slot for games with one endless mode. */
    fun newGame(kind: GameKind) {
        if (Games.of(kind).singleLevel) { pendingLocalTwoPlayer = false; chooseLevel(kind, Difficulty.EASY) } else openLevelPicker(kind)
    }

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
            val started = com.puresudoku.platform.monotonicMillis()
            val scripted = if (d == Difficulty.EASY) withContext(Dispatchers.Default) { runCatching { Games.of(kind).lesson() }.getOrNull() } else null
            val s = scripted?.first ?: withContext(Dispatchers.Default) {
                val def = Games.of(kind)
                val start = repo.trainingStart(kind, d) ?: def.generate(d).toJson().also { repo.saveTrainingStart(kind, d, it) }
                runCatching { def.restore(start) }.getOrElse { def.generate(d) }
            }
            scripted?.let { s.lesson = it.second; s.lessonAt = 0 }
            (s as? com.puresudoku.ui.games.board.DuelSession)?.cancelThinking()
            s.training = true
            if (d == Difficulty.EASY) com.puresudoku.core.board.BoardScripts.of(kind)?.let { steps -> (s as? com.puresudoku.ui.games.board.DuelSession)?.startGuide(steps) }
            val wait = 700 - (com.puresudoku.platform.monotonicMillis() - started)
            if (wait > 0) delay(wait)
            attach(s, -1)
            (s as? com.puresudoku.ui.games.board.DuelSession)?.resume()
        }
    }

    private fun startNew(kind: GameKind, d: Difficulty, slot: Int) {
        val local = pendingLocalTwoPlayer
        pendingLocalTwoPlayer = false
        activeKind = kind
        screen = Screen.Generating(kind, d)
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            val started = com.puresudoku.platform.monotonicMillis()
            val s = withContext(Dispatchers.Default) { Games.of(kind).generate(d) }
            if (local) (s as? com.puresudoku.ui.games.board.DuelSession)?.let { duel ->
                duel.localTwoPlayer = true
                duel.message = when (kind) { GameKind.TIC_TAC_TOE -> "Player 1: X, your turn"; GameKind.CHESS -> "Player 1: White, your turn"; GameKind.SHOGI -> "Player 1: Sente, your turn"; GameKind.MANCALA -> "Player 1: bottom row, your turn"; GameKind.MORRIS -> "Player 1: White, your turn"; GameKind.BACKGAMMON -> "Player 1: roll"; GameKind.CHECKERS, GameKind.XIANGQI -> "Player 1: Red, your turn"; GameKind.GO, GameKind.GOMOKU -> "Player 1: Black, your turn"; else -> "Player 1, your turn" }
            }
            // Let the build animation play for a moment, even when generation is instant.
            val wait = 900 - (com.puresudoku.platform.monotonicMillis() - started)
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
        if (repo.loadJson(kind, slot)?.optJSONObject("state")?.optBoolean("localTwoPlayer", false) == true) return
        val sum = repo.summaries(kind).getOrNull(slot) ?: return
        repo.addHistory(kind, com.puresudoku.data.GameResult(com.puresudoku.data.GameResult.Result.ABANDONED, sum.score, sum.difficulty, sum.elapsedMs, com.puresudoku.platform.nowMillis()))
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
        if ((s as? com.puresudoku.ui.games.board.DuelSession)?.localTwoPlayer != true && !s.isOnline && !repo.tutorialSeen(s.kind)) {
            repo.markTutorialSeen(s.kind)
            tutorialFor = s.kind
        }
        updateTimer()
    }

    fun playAgain() {
        val s = session ?: return
        if (s.isOnline) { detach(s); session = null; openOnline(s.kind); return }
        detach(s)
        session = null
        if (s.training) { startTraining(s.kind, s.difficulty); return }
        refreshMeta()
        // The finished game's slot was already cleared, so the new game reuses it.
        pendingLocalTwoPlayer = (s as? com.puresudoku.ui.games.board.DuelSession)?.localTwoPlayer == true
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
        if (s.isOnline) {
            // The device that made the last move tells the other how it ended.
            val d = s as com.puresudoku.ui.games.board.DuelSession
            if (!endedRemotely && !applyingRemote && mineToSend(d)) { sendOnline(d); com.puresudoku.online.OnlineMatch.send("e:${outcome.name}") }
            endedRemotely = false
            return
        }
        repo.clear(s.kind, activeSlot)
        if ((s as? com.puresudoku.ui.games.board.DuelSession)?.localTwoPlayer == true) { refreshMeta(); autoBackup(); return }
        if (outcome == Outcome.WON) {
            repo.addKeepsakes(s.kind, s.keepsakes)
            val record = repo.recordSolve(s.kind, s.difficulty, s.score, s.elapsedMs)
            val rank = repo.addToLeaderboard(s.kind, LeaderEntry(s.score, s.difficulty, s.elapsedMs, com.puresudoku.platform.nowMillis()))
            winSummary = WinSummary(s.elapsedMs, s.score, s.bonusLines, record, rank)
        }
        repo.addHistory(s.kind, com.puresudoku.data.GameResult(
            when (outcome) {
                Outcome.WON -> com.puresudoku.data.GameResult.Result.WON
                Outcome.DRAW -> com.puresudoku.data.GameResult.Result.DRAW
                else -> com.puresudoku.data.GameResult.Result.LOST
            },
            s.score, s.difficulty, s.elapsedMs, com.puresudoku.platform.nowMillis(),
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
            var last = com.puresudoku.platform.monotonicMillis()
            var sinceSave = 0L
            while (isActive) {
                delay(100)
                val now = com.puresudoku.platform.monotonicMillis()
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

    /** The app is closing: save, and stop background work. */
    fun close() {
        persist()
        viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}

const val MAX_FAVORITES = 16
