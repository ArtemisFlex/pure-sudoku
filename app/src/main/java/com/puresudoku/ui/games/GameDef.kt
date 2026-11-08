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

    /** The play area under the status bar. */
    @Composable
    fun Play(session: Session)

    @Composable
    fun Icon(size: Dp)

    val tutorial: List<TutorialStep>
}

object Games {
    val all: List<GameDef> by lazy {
        listOf(
            com.puresudoku.ui.games.sudoku.SudokuDef,
            com.puresudoku.ui.games.cagemath.CageMathDef,
            com.puresudoku.ui.games.kakuro.KakuroDef,
            com.puresudoku.ui.games.picture.PictureDef,
            com.puresudoku.ui.games.hashi.HashiDef,
        )
    }

    fun of(kind: GameKind): GameDef = all.first { it.kind == kind }
}
