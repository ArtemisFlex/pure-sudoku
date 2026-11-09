package com.puresudoku.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.MAX_STRIKES
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

enum class Outcome { PLAYING, WON, LOST }

/** What sits in the middle of the status bar. */
sealed interface MiddleStat {
    data class Strikes(val used: Int) : MiddleStat
    data class Counter(val label: String, val value: String) : MiddleStat
}

data class BonusLine(val label: String, val value: Int)

/** A short message explaining why a move did nothing. */
data class Nudge(val id: Long, val text: String)

/** Unique ids for one-shot animation triggers, shared by every game. */
object FxIds {
    private val next = AtomicLong(1)
    fun next() = next.getAndIncrement()
}

/**
 * One game in progress, for any puzzle type. Holds the things every game has
 * (timer, score, strikes, outcome) and is saved to / restored from JSON.
 * State is Compose snapshot state, so the UI updates automatically.
 */
abstract class Session(val kind: GameKind, val difficulty: Difficulty) {
    var elapsedMs by mutableLongStateOf(0L)
    var score by mutableIntStateOf(0); protected set
    var strikes by mutableIntStateOf(0); protected set
    var outcome by mutableStateOf(Outcome.PLAYING); private set
    var bonusLines by mutableStateOf<List<BonusLine>>(emptyList()); private set
    var startedAt: Long = System.currentTimeMillis(); private set

    /** Latest "that didn't work" message; the play screen shows it with a buzz. */
    var nudgeFx by mutableStateOf<Nudge?>(null); private set

    fun nudge(text: String) { nudgeFx = Nudge(FxIds.next(), text) }

    /** Set by the host: called after any change worth saving. */
    var onChanged: () -> Unit = {}
    /** Set by the host: called once when the game is won or lost. */
    var onFinished: (Outcome) -> Unit = {}

    open val usesStrikes: Boolean get() = true
    open val middleStat: MiddleStat get() = MiddleStat.Strikes(strikes)
    abstract val progress: Float

    open val wonTitle: String get() = "Solved!"
    open val lostTitle: String get() = "Game over"
    open val lostMessage: String get() = "Three strikes. This game's save slot has been cleared."

    /** Game-clock tick (only while actually playing). */
    open fun onTick(dtMs: Long) {}

    protected fun changed() = onChanged()

    /** Adds a strike; returns true if that ended the game. */
    protected fun addStrike(): Boolean {
        strikes++
        if (strikes >= MAX_STRIKES) { finish(false); return true }
        return false
    }

    protected fun finish(won: Boolean, bonus: List<BonusLine> = emptyList(), bonusAlreadyScored: Boolean = false) {
        if (outcome != Outcome.PLAYING) return
        bonusLines = bonus
        if (!bonusAlreadyScored) score += bonus.sumOf { it.value }
        outcome = if (won) Outcome.WON else Outcome.LOST
        onFinished(outcome)
    }

    protected abstract fun saveState(): JSONObject

    fun toJson(): JSONObject = JSONObject().apply {
        put("kind", kind.name)
        put("difficulty", difficulty.name)
        put("elapsedMs", elapsedMs)
        put("score", score)
        put("strikes", strikes)
        put("startedAt", startedAt)
        put("progress", progress.toDouble())
        put("usesStrikes", usesStrikes)
        put("state", saveState())
    }

    fun restoreCommon(o: JSONObject): Session {
        elapsedMs = o.optLong("elapsedMs", 0)
        score = o.optInt("score", 0)
        strikes = o.optInt("strikes", 0)
        startedAt = o.optLong("startedAt", System.currentTimeMillis())
        return this
    }
}
