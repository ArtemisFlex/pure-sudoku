package com.puresudoku.ui.games.sudoku

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.DigitGame
import com.puresudoku.core.DigitSpec
import com.puresudoku.core.GameKind
import com.puresudoku.core.Grid
import com.puresudoku.core.ScoreRules
import com.puresudoku.core.SudokuGenerator
import com.puresudoku.ui.components.LogoGrid
import com.puresudoku.ui.games.DigitDemoLayout
import com.puresudoku.ui.games.DigitPlay
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.DigitGridSession
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.tutorial.DemoStage
import com.puresudoku.ui.tutorial.TutorialStep
import kotlinx.coroutines.delay
import org.json.JSONObject

class SudokuSession(difficulty: Difficulty, game: DigitGame) : DigitGridSession(GameKind.SUDOKU, difficulty, game) {
    override fun region(i: Int) = Grid.box(i)
}

object SudokuDef : GameDef {
    override val kind = GameKind.SUDOKU
    override fun background(session: com.puresudoku.ui.session.Session) = com.puresudoku.R.drawable.vbg_sudoku
    private val generator = SudokuGenerator()

    fun spec(d: Difficulty) = DigitSpec(
        width = 9, height = 9, maxDigit = 9,
        playable = BooleanArray(81) { true },
        exclusive = Grid.UNITS.toList(),
        bonusUnits = Grid.UNITS.toList(),
        rules = ScoreRules(d.basePoints, d.parSeconds),
    )

    override fun generate(d: Difficulty): Session {
        val p = synchronized(generator) { generator.generate(d) }
        return SudokuSession(d, DigitGame(spec(d), p.puzzle.copyOf(), p.solution.copyOf()))
    }

    override fun restore(o: JSONObject): Session {
        val d = Difficulty.valueOf(o.getString("difficulty"))
        return SudokuSession(d, DigitGridSession.restoreGame(spec(d), o)).restoreCommon(o)
    }

    override fun levelInfo(d: Difficulty) = "${d.minClues}–${d.maxClues} clues"

    override fun hint(d: Difficulty): String = "Tap a square, then a number. Every row, column and 3×3 box needs 1–9 once."

    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Relaxed. Plenty of numbers to start."
        Difficulty.MEDIUM -> "Fewer clues to work from."
        Difficulty.HARD -> "Sparse grid. Needs careful thought."
        Difficulty.EXPERT -> "Demanding. Look before you place."
        Difficulty.MASTER -> "Very few clues, deep logic."
        Difficulty.EXTREME -> "The hardest puzzles we make."
    }

    @Composable
    override fun Play(session: Session) = DigitPlay(session as SudokuSession)

    @Composable
    override fun Icon(size: Dp) = LogoGrid(size)

    // ---------------------------------------------------------------- tutorial

    /** A finished grid used for every demo card. */
    private val DEMO_SOLUTION = (
        "534678912" + "672195348" + "198342567" +
            "859761423" + "426853791" + "713924856" +
            "961537284" + "287419635" + "345286179"
        ).map { it - '0' }.toIntArray()

    private fun demo(holes: List<Int>): SudokuSession {
        val givens = DEMO_SOLUTION.copyOf()
        for (h in holes) givens[h] = 0
        return SudokuSession(Difficulty.EASY, DigitGame(spec(Difficulty.EASY), givens, DEMO_SOLUTION.copyOf()))
    }

    private val SCATTER = listOf(2, 6, 10, 15, 21, 25, 29, 33, 37, 43, 47, 51, 56, 59, 64, 68, 72, 77)

    override fun lesson() = demo(listOf(4, 22, 40, 58, 76)) to listOf(
        com.puresudoku.ui.session.LessonStep("Every row, column and 3×3 box needs 1 to 9 once each. This board is almost full: five cells are empty, all in the middle column."),
        com.puresudoku.ui.session.LessonStep("Look at the top row. Eight digits are already there. The only one missing is 7, so tap the empty cell in that row and enter 7. A row with one gap is called a full house.", { (it as com.puresudoku.ui.session.DigitGridSession).value(4) == 7 }),
        com.puresudoku.ui.session.LessonStep("Third row: which digit is missing? Count 1 to 9 along it. It's 4. Fill it in.", { (it as com.puresudoku.ui.session.DigitGridSession).value(22) == 4 }),
        com.puresudoku.ui.session.LessonStep("Middle row: find the missing digit and enter it.", { (it as com.puresudoku.ui.session.DigitGridSession).value(40) == 5 }),
        com.puresudoku.ui.session.LessonStep("Seventh row: same trick. If you're unsure, check the column too: the digit you pick can't already be in the middle column.", { (it as com.puresudoku.ui.session.DigitGridSession).value(58) == 3 }),
        com.puresudoku.ui.session.LessonStep("Last gap. Rows, columns and boxes all agree on one digit here. Finish the puzzle.", { (it as com.puresudoku.ui.session.DigitGridSession).value(76) == 8 }),
    )

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep(
            "The goal",
            "Fill every empty square so that each row, each column and each 3×3 box contains the numbers 1 to 9 exactly once.",
        ) { active ->
            val holes = listOf(40) + SCATTER.filter { it != 37 && it != 43 }
            var s by remember { mutableStateOf(demo(holes)) }
            DemoStage(active, script = {
                s = demo(holes)
                wait(500)
                highlight((36..44).map { "cell-$it" }, 1300)
                highlight((0..8).map { "cell-${it * 9 + 4}" }, 1300)
                highlight(listOf(30, 31, 32, 39, 40, 41, 48, 49, 50).map { "cell-$it" }, 1300)
                tap("cell-40") { s.selectExact(40) }
                tap("key-5") { s.input(5) }
                wait(1600)
            }) { t -> DigitDemoLayout(s, t, showStatus = false, showActions = false) }
        },
        TutorialStep(
            "Place a number",
            "Tap an empty square to select it, then tap a number below. The row, column and box it belongs to light up to help you check.",
        ) { active ->
            var s by remember { mutableStateOf(demo(SCATTER)) }
            DemoStage(active, script = {
                s = demo(SCATTER)
                wait(400)
                tap("cell-21") { s.selectExact(21) }
                wait(500)
                tap("key-3") { s.input(3) }
                wait(700)
                tap("cell-47") { s.selectExact(47) }
                wait(500)
                tap("key-3") { s.input(3) }
                wait(1200)
            }) { t -> DigitDemoLayout(s, t, showActions = false) }
        },
        TutorialStep(
            "Pencil in notes",
            "Not sure yet? Turn on Notes and tap numbers to jot small reminders in a square. Tap Notes again to go back to placing numbers.",
        ) { active ->
            var s by remember { mutableStateOf(demo(SCATTER)) }
            DemoStage(active, script = {
                s = demo(SCATTER)
                wait(400)
                tap("notes") { s.toggleNotesMode() }
                tap("cell-2") { s.selectExact(2) }
                tap("key-2") { s.input(2) }
                tap("key-4") { s.input(4) }
                tap("cell-6") { s.selectExact(6) }
                tap("key-9") { s.input(9) }
                tap("key-4") { s.input(4) }
                wait(500)
                tap("notes") { s.toggleNotesMode() }
                tap("cell-2") { s.selectExact(2) }
                tap("key-4") { s.input(4) }
                wait(1400)
            }) { t -> DigitDemoLayout(s, t, showStatus = false) }
        },
        TutorialStep(
            "Three strikes",
            "A wrong number is never placed. It flashes red and costs a heart. Lose all three hearts and the game is over.",
        ) { active ->
            var s by remember { mutableStateOf(demo(SCATTER)) }
            DemoStage(active, script = {
                s = demo(SCATTER)
                wait(400)
                tap("cell-29") { s.selectExact(29) }
                tap("key-1") { s.input(1) }
                wait(700)
                highlight(listOf("middle"), 1100)
                tap("key-7") { s.input(7) }
                wait(700)
                highlight(listOf("middle"), 1100)
                tap("key-9") { s.input(9) }
                wait(1200)
            }) { t -> DigitDemoLayout(s, t, showActions = false) }
        },
        TutorialStep(
            "Score and time",
            "Every correct number scores points, with extra for finishing a row, column or box. Finish quickly with hearts left for a bonus. Your times are saved, and up to 3 games of each puzzle save automatically.",
        ) { active ->
            val holes = listOf(60, 61, 70) + SCATTER.filter { it < 54 }
            var s by remember { mutableStateOf(demo(holes)) }
            LaunchedEffect(active, s) { while (active) { delay(1000); s.elapsedMs += 1000 } }
            DemoStage(active, script = {
                s = demo(holes)
                s.elapsedMs = 83_000
                wait(400)
                tap("cell-60") { s.selectExact(60) }
                tap("key-2") { s.input(2) }
                tap("cell-61") { s.selectExact(61) }
                tap("key-8") { s.input(8) }
                tap("cell-70") { s.selectExact(70) }
                tap("key-3") { s.input(3) }
                wait(500)
                highlight(listOf("score"), 1200)
                highlight(listOf("time"), 1200)
                wait(600)
            }) { t -> DigitDemoLayout(s, t, showActions = false) }
        },
    )
}
