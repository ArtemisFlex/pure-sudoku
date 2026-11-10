package com.puresudoku.data

import android.content.Context
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import org.json.JSONObject

enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

data class DifficultyStats(
    val wins: Int = 0,
    val bestScore: Int = 0,
    /** Fastest solve time in ms; 0 means no solve yet. */
    val bestTimeMs: Long = 0,
    val totalTimeMs: Long = 0,
) {
    val averageTimeMs: Long get() = if (wins == 0) 0 else totalTimeMs / wins
}

data class SolveRecord(val stats: DifficultyStats, val newBestTime: Boolean, val newBestScore: Boolean)

/** One finished game on a personal leaderboard. */
data class LeaderEntry(val score: Int, val difficulty: Difficulty, val timeMs: Long, val date: Long)

/** Every finished (or abandoned) game, newest first in [SaveRepository.history]. */
data class GameResult(val result: Result, val score: Int, val difficulty: Difficulty, val timeMs: Long, val date: Long) {
    enum class Result(val label: String) { WON("Won"), LOST("Lost"), DRAW("Draw"), ABANDONED("Abandoned") }
}

/** Summary of everything you've played in one game. */
data class GameStats(
    val wins: Int, val losses: Int, val abandoned: Int, val draws: Int,
    val currentStreak: Int, val bestStreak: Int,
    val bestScore: Int, val fastestWinMs: Long, val averageWinMs: Long, val totalTimeMs: Long,
) {
    val played get() = wins + losses + abandoned + draws
    val winRate get() = if (played == 0) 0 else (wins * 100 + played / 2) / played
}

/** Lightweight view of a save, for the slot list (no need to rebuild the game). */
data class SlotSummary(
    val difficulty: Difficulty,
    val elapsedMs: Long,
    val score: Int,
    val strikes: Int,
    val usesStrikes: Boolean,
    val progress: Float,
)

/** Local-only persistence: 3 save slots per game, per-game records, settings. */
class SaveRepository(context: Context) {
    private val prefs = context.getSharedPreferences("pure_sudoku", Context.MODE_PRIVATE)

    init { migrateV1() }

    // ---------------- saves ----------------

    fun loadJson(kind: GameKind, slot: Int): JSONObject? = runCatching {
        prefs.getString(slotKey(kind, slot), null)?.let { JSONObject(it) }
    }.getOrNull()

    fun save(kind: GameKind, slot: Int, json: JSONObject) {
        prefs.edit().putString(slotKey(kind, slot), json.toString()).apply()
    }

    fun clear(kind: GameKind, slot: Int) {
        prefs.edit().remove(slotKey(kind, slot)).apply()
    }

    fun summaries(kind: GameKind): List<SlotSummary?> = List(SLOT_COUNT) { slot ->
        loadJson(kind, slot)?.let { o ->
            runCatching {
                SlotSummary(
                    difficulty = Difficulty.valueOf(o.getString("difficulty")),
                    elapsedMs = o.optLong("elapsedMs"),
                    score = o.optInt("score"),
                    strikes = o.optInt("strikes"),
                    usesStrikes = o.optBoolean("usesStrikes", true),
                    progress = o.optDouble("progress", 0.0).toFloat(),
                )
            }.getOrNull()
        }
    }

    private fun slotKey(kind: GameKind, slot: Int) = "save_${kind.name}_$slot"

    // ---------------- records ----------------

    /** Sudoku keeps its original key names so existing records carry over. */
    private fun statKey(name: String, kind: GameKind, d: Difficulty) =
        if (kind == GameKind.SUDOKU) "${name}_${d.name}" else "${name}_${kind.name}_${d.name}"

    fun stats(kind: GameKind, d: Difficulty) = DifficultyStats(
        wins = prefs.getInt(statKey("wins", kind, d), 0),
        bestScore = prefs.getInt(statKey("best_score", kind, d), 0),
        bestTimeMs = prefs.getLong(statKey("best_time", kind, d), 0),
        totalTimeMs = prefs.getLong(statKey("total_time", kind, d), 0),
    )

    fun recordSolve(kind: GameKind, d: Difficulty, score: Int, timeMs: Long): SolveRecord {
        val old = stats(kind, d)
        val newBestTime = old.bestTimeMs == 0L || timeMs < old.bestTimeMs
        val newBestScore = score > old.bestScore
        val updated = DifficultyStats(
            wins = old.wins + 1,
            bestScore = maxOf(old.bestScore, score),
            bestTimeMs = if (newBestTime) timeMs else old.bestTimeMs,
            totalTimeMs = old.totalTimeMs + timeMs,
        )
        prefs.edit()
            .putInt(statKey("wins", kind, d), updated.wins)
            .putInt(statKey("best_score", kind, d), updated.bestScore)
            .putLong(statKey("best_time", kind, d), updated.bestTimeMs)
            .putLong(statKey("total_time", kind, d), updated.totalTimeMs)
            .apply()
        return SolveRecord(updated, newBestTime && old.wins > 0, newBestScore && old.wins > 0)
    }

    // ---------------- personal leaderboard ----------------

    fun leaderboard(kind: GameKind): List<LeaderEntry> = runCatching {
        val a = org.json.JSONArray(prefs.getString("board_${kind.name}", "[]"))
        List(a.length()) { k ->
            val o = a.getJSONObject(k)
            LeaderEntry(o.getInt("s"), Difficulty.valueOf(o.getString("d")), o.getLong("t"), o.getLong("at"))
        }
    }.getOrDefault(emptyList())

    /** Adds a win; returns its place (1 = best) or 0 if it didn't make the top 10. */
    fun addToLeaderboard(kind: GameKind, e: LeaderEntry): Int {
        val all = (leaderboard(kind) + e).sortedWith(compareByDescending<LeaderEntry> { it.score }.thenBy { it.timeMs }).take(10)
        val a = org.json.JSONArray()
        all.forEach { a.put(JSONObject().put("s", it.score).put("d", it.difficulty.name).put("t", it.timeMs).put("at", it.date)) }
        prefs.edit().putString("board_${kind.name}", a.toString()).apply()
        return all.indexOf(e) + 1
    }

    // ---------------- full history ----------------

    fun history(kind: GameKind): List<GameResult> = runCatching {
        val a = org.json.JSONArray(prefs.getString("hist_${kind.name}", "[]"))
        List(a.length()) { k ->
            val o = a.getJSONObject(k)
            GameResult(GameResult.Result.valueOf(o.getString("r")), o.getInt("s"), Difficulty.valueOf(o.getString("d")), o.getLong("t"), o.getLong("at"))
        }
    }.getOrDefault(emptyList())

    fun addHistory(kind: GameKind, r: GameResult) {
        val all = (listOf(r) + history(kind)).take(HISTORY_LIMIT)
        val a = org.json.JSONArray()
        all.forEach { a.put(JSONObject().put("r", it.result.name).put("s", it.score).put("d", it.difficulty.name).put("t", it.timeMs).put("at", it.date)) }
        val e = prefs.edit().putString("hist_${kind.name}", a.toString())
        when (r.result) {
            GameResult.Result.LOST -> e.putInt("losses_${kind.name}", prefs.getInt("losses_${kind.name}", 0) + 1)
            GameResult.Result.ABANDONED -> e.putInt("abandons_${kind.name}", prefs.getInt("abandons_${kind.name}", 0) + 1)
            GameResult.Result.DRAW -> e.putInt("draws_${kind.name}", prefs.getInt("draws_${kind.name}", 0) + 1)
            GameResult.Result.WON -> {}
        }
        if (r.result != GameResult.Result.WON) e.putLong("other_time_${kind.name}", prefs.getLong("other_time_${kind.name}", 0) + r.timeMs)
        e.putInt("streak_best_${kind.name}", maxOf(prefs.getInt("streak_best_${kind.name}", 0), streakOf(all)))
        e.apply()
    }

    private fun streakOf(h: List<GameResult>) = h.takeWhile { it.result == GameResult.Result.WON }.size

    fun gameStats(kind: GameKind): GameStats {
        val per = Difficulty.entries.map { stats(kind, it) }
        val wins = per.sumOf { it.wins }
        val h = history(kind)
        val winTime = per.sumOf { it.totalTimeMs }
        return GameStats(
            wins = wins,
            losses = prefs.getInt("losses_${kind.name}", 0),
            abandoned = prefs.getInt("abandons_${kind.name}", 0),
            draws = prefs.getInt("draws_${kind.name}", 0),
            currentStreak = streakOf(h),
            bestStreak = maxOf(prefs.getInt("streak_best_${kind.name}", 0), streakOf(h)),
            bestScore = per.maxOf { it.bestScore },
            fastestWinMs = per.filter { it.bestTimeMs > 0 }.minOfOrNull { it.bestTimeMs } ?: 0,
            averageWinMs = if (wins == 0) 0 else winTime / wins,
            totalTimeMs = winTime + prefs.getLong("other_time_${kind.name}", 0),
        )
    }

    // ---------------- backup ----------------

    /** Everything (saves, records, history, settings) as one JSON document. */
    fun exportAll(): String {
        val items = JSONObject()
        for ((k, v) in prefs.all) {
            val o = when (v) {
                is String -> JSONObject().put("t", "s").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                else -> null
            } ?: continue
            items.put(k, o)
        }
        return JSONObject().put("app", "PureGames").put("version", 1).put("savedAt", System.currentTimeMillis()).put("items", items).toString()
    }

    /** Replaces all local data with a backup. Returns false if the file isn't a PureGames backup. */
    fun importAll(text: String): Boolean = runCatching {
        val root = JSONObject(text)
        if (root.optString("app") !in BACKUP_IDS) return false
        val items = root.getJSONObject("items")
        val e = prefs.edit().clear()
        for (k in items.keys()) {
            val o = items.getJSONObject(k)
            when (o.getString("t")) {
                "s" -> e.putString(k, o.getString("v"))
                "i" -> e.putInt(k, o.getInt("v"))
                "l" -> e.putLong(k, o.getLong("v"))
                "b" -> e.putBoolean(k, o.getBoolean("v"))
                "f" -> e.putFloat(k, o.getDouble("v").toFloat())
            }
        }
        e.commit()
    }.getOrDefault(false)

    // ---------------- keepsakes ----------------

    fun keepsakes(kind: GameKind): List<String> = runCatching {
        val a = org.json.JSONArray(prefs.getString("keep_${kind.name}", "[]"))
        List(a.length()) { a.getString(it) }
    }.getOrDefault(emptyList())

    fun addKeepsakes(kind: GameKind, items: List<String>) {
        if (items.isEmpty()) return
        val all = (items.reversed() + keepsakes(kind)).take(30)
        prefs.edit().putString("keep_${kind.name}", org.json.JSONArray(all).toString()).apply()
    }

    // ---------------- settings ----------------

    fun themeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)

    fun setThemeMode(m: ThemeMode) { prefs.edit().putString("theme_mode", m.name).apply() }

    fun tutorialSeen(kind: GameKind) = prefs.getBoolean("tutorial_seen_${kind.name}", false)
    fun markTutorialSeen(kind: GameKind) { prefs.edit().putBoolean("tutorial_seen_${kind.name}", true).apply() }

    /** Version 1 (Pure Sudoku) stored 3 sudoku saves as "slot_N" in a flat format. */
    private fun migrateV1() {
        val edit = prefs.edit()
        var changed = false
        for (slot in 0 until SLOT_COUNT) {
            val old = prefs.getString("slot_$slot", null) ?: continue
            changed = true
            edit.remove("slot_$slot")
            runCatching {
                val o = JSONObject(old)
                val givens = o.getJSONArray("givens")
                val values = o.getJSONArray("values")
                var open = 0; var done = 0
                for (i in 0 until givens.length()) if (givens.getInt(i) == 0) {
                    open++; if (values.getInt(i) != 0) done++
                }
                val state = JSONObject()
                    .put("givens", givens)
                    .put("solution", o.getJSONArray("solution"))
                    .put("values", values)
                    .put("notes", o.getJSONArray("notes"))
                    .put("streak", o.optInt("streak", 0))
                val converted = JSONObject()
                    .put("kind", GameKind.SUDOKU.name)
                    .put("difficulty", o.getString("difficulty"))
                    .put("elapsedMs", o.optLong("elapsedMs"))
                    .put("score", o.optInt("score"))
                    .put("strikes", o.optInt("strikes"))
                    .put("startedAt", o.optLong("startedAt", System.currentTimeMillis()))
                    .put("progress", if (open == 0) 1.0 else done.toDouble() / open)
                    .put("usesStrikes", true)
                    .put("state", state)
                edit.putString(slotKey(GameKind.SUDOKU, slot), converted.toString())
            }
        }
        if (changed) edit.apply()
    }

    companion object {
        const val SLOT_COUNT = 3
        const val HISTORY_LIMIT = 500
        /** Backups made before the rename say "Pure Puzzles". */
        val BACKUP_IDS = setOf("PureGames", "Pure Puzzles")
    }
}
