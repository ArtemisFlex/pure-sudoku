package com.puresudoku.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.tutorial.TutorialStep
import org.json.JSONObject

/** Everything the app needs to know to offer one puzzle type. */
interface GameDef {
    val kind: GameKind

    /** Builds a brand-new game. Runs on a background thread. */
    fun generate(d: Difficulty): Session

    /** Rebuilds a saved game from [Session.toJson]. */
    fun restore(o: JSONObject): Session

    /** Short spec shown on the level picker, e.g. "6×6 grid". */
    fun levelInfo(d: Difficulty): String

    /** One-line description of what the level is like. */
    fun levelBlurb(d: Difficulty): String

    /** One-line reminder of how to play, shown under the status bar. */
    fun hint(d: Difficulty): String

    /** The play area under the status bar. */
    @Composable
    fun Play(session: Session)

    @Composable
    fun Icon(size: Dp)

    /** Optional full-screen pixel-art backdrop (drawable id) for the current moment of play; 0 = none. */
    fun background(session: Session): Int = 0

    /** A fixed Easy training game with a scripted lesson, or null to use the coached game. */
    /** The game draws edge to edge under the top bar, and hides the status row and hint. */
    val fullBleed: Boolean get() = false

    /** One endless mode, no difficulty levels: "New game" goes straight to a free save slot. */
    val singleLevel: Boolean get() = false

    /** A game can draw its own themed top bar in place of the standard one. */
    val hasHeader: Boolean get() = false
    @Composable fun Header(session: Session, actions: HeaderActions) {}

    fun lesson(): Pair<Session, List<com.puresudoku.ui.session.LessonStep>>? = null

    val tutorial: List<TutorialStep>
}

/** What a custom top bar can do. */
class HeaderActions(val back: () -> Unit, val info: () -> Unit, val pause: () -> Unit, val paused: Boolean, val canPause: Boolean)
