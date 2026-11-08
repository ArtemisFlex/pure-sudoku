package com.puresudoku.ui.games.kakuro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.DigitGame
import com.puresudoku.core.GameKind
import com.puresudoku.core.KakuroGenerator
import com.puresudoku.core.KakuroPuzzle
import com.puresudoku.core.KakuroRules
import com.puresudoku.core.ints
import com.puresudoku.core.toBooleanArray
import com.puresudoku.core.toJson
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.games.DigitDemoLayout
import com.puresudoku.ui.games.DigitPlay
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.DigitGridSession
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.theme.LocalBoardColors
import com.puresudoku.ui.theme.Poppins
import com.puresudoku.ui.tutorial.DemoStage
import com.puresudoku.ui.tutorial.TutorialStep
import org.json.JSONObject

class KakuroSession(difficulty: Difficulty, val puzzle: KakuroPuzzle, game: DigitGame) :
    DigitGridSession(GameKind.KAKURO, difficulty, game) {
    override fun saveExtra(o: JSONObject) {
        o.put("w", puzzle.width).put("h", puzzle.height)
        o.put("white", puzzle.white.toJson())
        o.put("across", puzzle.acrossSum.toJson())
        o.put("down", puzzle.downSum.toJson())
    }
}

object KakuroDef : GameDef {
    override val kind = GameKind.KAKURO
    private val generator = KakuroGenerator()

    private fun session(d: Difficulty, p: KakuroPuzzle, game: DigitGame? = null) =
        KakuroSession(d, p, game ?: DigitGame(KakuroRules.spec(p, d), p.givens, p.solution))

    override fun generate(d: Difficulty): Session = session(d, synchronized(generator) { generator.generate(d) })

    override fun restore(o: JSONObject): Session {
        val d = Difficulty.valueOf(o.getString("difficulty"))
        val st = o.getJSONObject("state")
        val p = KakuroPuzzle(
            st.getInt("w"), st.getInt("h"), st.getJSONArray("white").toBooleanArray(),
            st.ints("solution"), st.ints("givens"), st.ints("across"), st.ints("down"),
        )
        return session(d, p, DigitGridSession.restoreGame(KakuroRules.spec(p, d), o)).restoreCommon(o)
    }

    override fun levelInfo(d: Difficulty): String {
        val c = KakuroRules.config(d)
        return "${c.w}×${c.h} board"
    }

    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A small board with short runs."
        Difficulty.MEDIUM -> "More runs to cross-check."
        Difficulty.HARD -> "Longer sums, fewer easy starts."
        Difficulty.EXPERT -> "A full-size board."
        Difficulty.MASTER -> "Large board, long runs."
        Difficulty.EXTREME -> "The biggest board we make."
    }

    /** Clue cells: dark square, diagonal, across sum top-right, down sum bottom-left. */
    private fun clues(p: KakuroPuzzle): @Composable BoxScope.(Int) -> Unit = { i ->
        val c = LocalBoardColors.current
        val across = p.acrossSum[i]
        val down = p.downSum[i]
        BoxWithConstraints(Modifier.fillMaxSize().background(c.thickLine)) {
            val fs = with(LocalDensity.current) { (maxWidth * 0.3f).toSp() }
            if (across > 0 || down > 0) {
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(c.boardBg.copy(alpha = 0.35f), Offset.Zero, Offset(size.width, size.height), 1.dp.toPx())
                }
            }
            if (across > 0) Text(
                "$across", Modifier.align(Alignment.TopEnd).padding(end = 2.dp),
                fontSize = fs, lineHeight = fs, color = c.boardBg, fontFamily = Poppins, fontWeight = FontWeight.SemiBold,
            )
            if (down > 0) Text(
                "$down", Modifier.align(Alignment.BottomStart).padding(start = 2.dp),
                fontSize = fs, lineHeight = fs, color = c.boardBg, fontFamily = Poppins, fontWeight = FontWeight.SemiBold,
            )
        }
    }

    @Composable
    override fun Play(session: Session) {
        val s = session as KakuroSession
        DigitPlay(s, blocked = clues(s.puzzle))
    }

    @Composable
    override fun Icon(size: Dp) = GameIcon(kind, size)

    // ---------------------------------------------------------------- tutorial

    /*  Demo board (4×4, first row/column are clues):
              ↓6  ↓19 ↓11
         →6    1   3   2
         →17   3   9   5
         →13   2   7   4        */
    private val DEMO_WHITE = BooleanArray(16) { it / 4 > 0 && it % 4 > 0 }
    private val DEMO_SOL = intArrayOf(0, 0, 0, 0, 0, 1, 3, 2, 0, 3, 9, 5, 0, 2, 7, 4)
    private val DEMO_ACROSS = IntArray(16).also { it[4] = 6; it[8] = 17; it[12] = 13 }
    private val DEMO_DOWN = IntArray(16).also { it[1] = 6; it[2] = 19; it[3] = 11 }

    private fun demo(): KakuroSession {
        val p = KakuroPuzzle(4, 4, DEMO_WHITE, DEMO_SOL, IntArray(16), DEMO_ACROSS, DEMO_DOWN)
        return KakuroSession(Difficulty.EASY, p, DigitGame(KakuroRules.spec(p, Difficulty.EASY), IntArray(16), DEMO_SOL))
    }

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep(
            "Sums across",
            "Fill the white squares with 1–9. The number in the top-right of a dark square is the total of the white squares to its right.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                highlight(listOf("cell-4", "cell-5", "cell-6", "cell-7"), 1400)
                tap("cell-5") { s.selectExact(5) }; tap("key-1") { s.input(1) }
                tap("cell-6") { s.selectExact(6) }; tap("key-3") { s.input(3) }
                tap("cell-7") { s.selectExact(7) }; tap("key-2") { s.input(2) }
                wait(1200)
            }) { t -> DigitDemoLayout(s, t, showStatus = false, showActions = false, blocked = clues(s.puzzle)) }
        },
        TutorialStep(
            "Sums down",
            "The number in the bottom-left of a dark square is the total of the white squares below it. Every white square belongs to one sum across and one sum down.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                highlight(listOf("cell-2", "cell-6", "cell-10", "cell-14"), 1400)
                tap("cell-6") { s.selectExact(6) }; tap("key-3") { s.input(3) }
                tap("cell-10") { s.selectExact(10) }; tap("key-9") { s.input(9) }
                tap("cell-14") { s.selectExact(14) }; tap("key-7") { s.input(7) }
                wait(1200)
            }) { t -> DigitDemoLayout(s, t, showStatus = false, showActions = false, blocked = clues(s.puzzle)) }
        },
        TutorialStep(
            "No repeats",
            "A number can't appear twice in the same sum. A wrong number is rejected and costs a heart; three strikes ends the game.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                tap("cell-5") { s.selectExact(5) }; tap("key-1") { s.input(1) }
                tap("cell-9") { s.selectExact(9) }
                highlight(listOf("cell-5", "cell-9"), 900)
                tap("key-1") { s.input(1) }
                wait(500)
                highlight(listOf("middle"), 1000)
                tap("key-3") { s.input(3) }
                wait(1200)
            }) { t -> DigitDemoLayout(s, t, showActions = false, blocked = clues(s.puzzle)) }
        },
        TutorialStep(
            "Notes",
            "Turn on Notes to pencil in the numbers a square could be. Placing a number clears it from the notes in the same sums.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                tap("notes") { s.toggleNotesMode() }
                tap("cell-11") { s.selectExact(11) }
                tap("key-5") { s.input(5) }; tap("key-6") { s.input(6) }
                tap("cell-15") { s.selectExact(15) }
                tap("key-4") { s.input(4) }; tap("key-5") { s.input(5) }
                tap("notes") { s.toggleNotesMode() }
                tap("cell-11") { s.selectExact(11) }
                tap("key-5") { s.input(5) }
                wait(1400)
            }) { t -> DigitDemoLayout(s, t, showStatus = false, blocked = clues(s.puzzle)) }
        },
    )
}
