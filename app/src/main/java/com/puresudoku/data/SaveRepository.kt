package com.puresudoku.data

import android.content.Context
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameSnapshot
import org.json.JSONArray
import org.json.JSONObject

data class DifficultyStats(
    val wins: Int = 0,
    val bestScore: Int = 0,
    /** Fastest solve time in ms; 0 means no solve yet. */
    val bestTimeMs: Long = 0,
    val totalTimeMs: Long = 0,
) {
    val averageTimeMs: Long get() = if (wins == 0) 0 else totalTimeMs / wins
}

data class SolveRecord(
    val stats: DifficultyStats,
    val newBestTime: Boolean,
    val newBestScore: Boolean,
)

/** Local-only persistence: 3 save slots + per-difficulty records, in SharedPreferences. */
class SaveRepository(context: Context) {
    private val prefs = context.getSharedPreferences("pure_sudoku", Context.MODE_PRIVATE)

    fun loadSlots(): List<GameSnapshot?> = List(SLOT_COUNT) { load(it) }

    fun load(slot: Int): GameSnapshot? = runCatching {
        prefs.getString("slot_$slot", null)?.let { decode(JSONObject(it)) }
    }.getOrNull()

    fun save(slot: Int, s: GameSnapshot) {
        prefs.edit().putString("slot_$slot", encode(s).toString()).apply()
    }

    fun clear(slot: Int) {
        prefs.edit().remove("slot_$slot").apply()
    }

    fun stats(d: Difficulty) = DifficultyStats(
        wins = prefs.getInt("wins_${d.name}", 0),
        bestScore = prefs.getInt("best_score_${d.name}", 0),
        bestTimeMs = prefs.getLong("best_time_${d.name}", 0),
        totalTimeMs = prefs.getLong("total_time_${d.name}", 0),
    )

    fun recordSolve(d: Difficulty, score: Int, timeMs: Long): SolveRecord {
        val old = stats(d)
        val newBestTime = old.bestTimeMs == 0L || timeMs < old.bestTimeMs
        val newBestScore = score > old.bestScore
        val updated = DifficultyStats(
            wins = old.wins + 1,
            bestScore = maxOf(old.bestScore, score),
            bestTimeMs = if (newBestTime) timeMs else old.bestTimeMs,
            totalTimeMs = old.totalTimeMs + timeMs,
        )
        prefs.edit()
            .putInt("wins_${d.name}", updated.wins)
            .putInt("best_score_${d.name}", updated.bestScore)
            .putLong("best_time_${d.name}", updated.bestTimeMs)
            .putLong("total_time_${d.name}", updated.totalTimeMs)
            .apply()
        return SolveRecord(updated, newBestTime && old.wins > 0, newBestScore && old.wins > 0)
    }

    private fun encode(s: GameSnapshot) = JSONObject().apply {
        put("difficulty", s.difficulty.name)
        put("givens", s.givens.toJson())
        put("solution", s.solution.toJson())
        put("values", s.values.toJson())
        put("notes", s.notes.toJson())
        put("strikes", s.strikes)
        put("score", s.score)
        put("streak", s.streak)
        put("elapsedMs", s.elapsedMs)
        put("startedAt", s.startedAt)
    }

    private fun decode(o: JSONObject) = GameSnapshot(
        difficulty = Difficulty.valueOf(o.getString("difficulty")),
        givens = o.getJSONArray("givens").toIntArray(),
        solution = o.getJSONArray("solution").toIntArray(),
        values = o.getJSONArray("values").toIntArray(),
        notes = o.getJSONArray("notes").toIntArray(),
        strikes = o.getInt("strikes"),
        score = o.getInt("score"),
        streak = o.optInt("streak", 0),
        elapsedMs = o.getLong("elapsedMs"),
        startedAt = o.getLong("startedAt"),
    )

    private fun IntArray.toJson() = JSONArray().also { a -> forEach { a.put(it) } }
    private fun JSONArray.toIntArray() = IntArray(length()) { getInt(it) }

    companion object {
        const val SLOT_COUNT = 3
    }
}
