package com.puresudoku.ui.games.cagemath

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Cage
import com.puresudoku.core.CageMathGenerator
import com.puresudoku.core.CageMathRules
import com.puresudoku.core.CagePuzzle
import com.puresudoku.core.Difficulty
import com.puresudoku.core.DigitGame
import com.puresudoku.core.GameKind
import com.puresudoku.core.ints
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
import org.json.JSONArray
import org.json.JSONObject

class CageMathSession(difficulty: Difficulty, val puzzle: CagePuzzle, game: DigitGame) :
    DigitGridSession(GameKind.CAGE_MATH, difficulty, game) {

    /** The cell that carries each cage's label (its top-left cell). */
    val labelCell: Map<Int, Cage> = puzzle.cages.associateBy { it.cells.min() }

    override fun region(i: Int) = puzzle.cageOf[i]

    override fun saveExtra(o: JSONObject) {
        o.put("n", puzzle.n)
        o.put("cages", JSONArray().also { a ->
            puzzle.cages.forEach { c -> a.put(JSONObject().put("cells", c.cells.toJson()).put("op", c.op.toString()).put("target", c.target)) }
        })
    }
}

object CageMathDef : GameDef {
    override val kind = GameKind.CAGE_MATH
    private val generator = CageMathGenerator()

    private fun session(d: Difficulty, p: CagePuzzle, game: DigitGame? = null) =
        CageMathSession(d, p, game ?: DigitGame(CageMathRules.spec(p.n, d), p.givens, p.solution))

    override fun generate(d: Difficulty): Session = session(d, synchronized(generator) { generator.generate(d) })

    override fun restore(o: JSONObject): Session {
        val d = Difficulty.valueOf(o.getString("difficulty"))
        val st = o.getJSONObject("state")
        val n = st.getInt("n")
        val arr = st.getJSONArray("cages")
        val cages = (0 until arr.length()).map { k ->
            val c = arr.getJSONObject(k)
            Cage(c.ints("cells"), c.getString("op")[0], c.getInt("target"))
        }
        val p = CagePuzzle(n, st.ints("solution"), cages)
        return session(d, p, DigitGridSession.restoreGame(CageMathRules.spec(n, d), o)).restoreCommon(o)
    }

    override fun levelInfo(d: Difficulty): String {
        val c = CageMathRules.config(d)
        return "${c.n}×${c.n} · " + c.ops.map { op -> when (op) { '+' -> "+"; '-' -> "−"; 'x' -> "×"; else -> "÷" } }.joinToString(" ")
    }

    override fun hint(d: Difficulty): String = "Each row and column uses every number once. A cage's numbers must make its target."

    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Small grid, adding only."
        Difficulty.MEDIUM -> "Adding and subtracting."
        Difficulty.HARD -> "All four operations."
        Difficulty.EXPERT -> "Bigger grid, bigger cages."
        Difficulty.MASTER -> "8×8 with every operation."
        Difficulty.EXTREME -> "The full 9×9 challenge."
    }

    /** Draws the cage label in the cage's top-left cell. */
    private fun labels(s: CageMathSession): @Composable BoxScope.(Int) -> Unit = { i ->
        val cage = s.labelCell[i]
        if (cage != null && cage.label.isNotEmpty()) {
            val n = s.puzzle.n
            Text(
                cage.label,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 1.dp),
                fontSize = when { n <= 5 -> 13.sp; n <= 7 -> 11.sp; else -> 9.5.sp },
                lineHeight = 12.sp,
                fontFamily = Poppins, fontWeight = FontWeight.SemiBold,
                color = LocalBoardColors.current.given,
            )
        }
    }

    @Composable
    override fun Play(session: Session) {
        val s = session as CageMathSession
        DigitPlay(s, decoration = labels(s))
    }

    @Composable
    override fun Icon(size: Dp) = GameIcon(kind, size)

    // ---------------------------------------------------------------- tutorial

    /*  Demo puzzle (4×4):
        1 2 | 3 4      cages: A{0,1} 3+   B{2,3} 12×
        3 4 1 2               C{4,8} 1−   D{5,6,9} 8+
        4 3 2 1               E{7,11} 2÷  F{10,14} 8×
        2 1 4 3               G{12,13} 1− H{15} given 3          */
    private val DEMO_SOL = intArrayOf(1, 2, 3, 4, 3, 4, 1, 2, 4, 3, 2, 1, 2, 1, 4, 3)
    private val DEMO_CAGES = listOf(
        Cage(intArrayOf(0, 1), '+', 3), Cage(intArrayOf(2, 3), 'x', 12),
        Cage(intArrayOf(4, 8), '-', 1), Cage(intArrayOf(5, 6, 9), '+', 8),
        Cage(intArrayOf(7, 11), '/', 2), Cage(intArrayOf(10, 14), 'x', 8),
        Cage(intArrayOf(12, 13), '-', 1), Cage(intArrayOf(15), '=', 3),
    )

    private fun demo(prefilled: Map<Int, Int> = emptyMap()): CageMathSession {
        val p = CagePuzzle(4, DEMO_SOL, DEMO_CAGES)
        val givens = p.givens.copyOf()
        prefilled.forEach { (k, v) -> givens[k] = v }
        return CageMathSession(Difficulty.EASY, p, DigitGame(CageMathRules.spec(4, Difficulty.EASY), givens, DEMO_SOL))
    }

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep(
            "The grid",
            "Fill the grid so every row and every column contains each number once. A 4×4 grid uses 1–4, a 6×6 grid uses 1–6, and so on.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                highlight((0..3).map { "cell-$it" }, 1300)
                highlight((0..3).map { "cell-${it * 4}" }, 1300)
                tap("cell-0") { s.selectExact(0) }
                tap("key-1") { s.input(1) }
                tap("cell-1") { s.selectExact(1) }
                tap("key-2") { s.input(2) }
                wait(1200)
            }) { t -> DigitDemoLayout(s, t, showStatus = false, showActions = false, decoration = labels(s)) }
        },
        TutorialStep(
            "Cages",
            "Thick outlines group squares into cages. The small label is the target: the cage's numbers must add (+) or multiply (×) to make it. One-square cages are filled in for you.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                highlight(listOf("cell-5", "cell-6", "cell-9"), 1300)
                tap("cell-5") { s.selectExact(5) }; tap("key-4") { s.input(4) }
                tap("cell-6") { s.selectExact(6) }; tap("key-1") { s.input(1) }
                tap("cell-9") { s.selectExact(9) }; tap("key-3") { s.input(3) }
                wait(500)
                highlight(listOf("cell-2", "cell-3"), 1300)
                tap("cell-2") { s.selectExact(2) }; tap("key-3") { s.input(3) }
                tap("cell-3") { s.selectExact(3) }; tap("key-4") { s.input(4) }
                wait(1200)
            }) { t -> DigitDemoLayout(s, t, showStatus = false, showActions = false, decoration = labels(s)) }
        },
        TutorialStep(
            "Subtract and divide",
            "A − or ÷ cage has two squares. Take the smaller number from the larger, or divide the larger by the smaller. Either number can go in either square.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                highlight(listOf("cell-4", "cell-8"), 1200)
                tap("cell-4") { s.selectExact(4) }; tap("key-3") { s.input(3) }
                tap("cell-8") { s.selectExact(8) }; tap("key-4") { s.input(4) }
                wait(400)
                highlight(listOf("cell-7", "cell-11"), 1200)
                tap("cell-7") { s.selectExact(7) }; tap("key-2") { s.input(2) }
                tap("cell-11") { s.selectExact(11) }; tap("key-1") { s.input(1) }
                wait(1200)
            }) { t -> DigitDemoLayout(s, t, showStatus = false, showActions = false, decoration = labels(s)) }
        },
        TutorialStep(
            "Notes and strikes",
            "Use Notes to pencil in options. A wrong number is rejected and costs a heart; three strikes ends the game. Finish fast with hearts left for bonus points.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                tap("notes") { s.toggleNotesMode() }
                tap("cell-12") { s.selectExact(12) }
                tap("key-1") { s.input(1) }; tap("key-2") { s.input(2) }
                tap("notes") { s.toggleNotesMode() }
                tap("key-1") { s.input(1) }
                wait(600)
                highlight(listOf("middle"), 1000)
                tap("key-2") { s.input(2) }
                wait(1200)
            }) { t -> DigitDemoLayout(s, t, decoration = labels(s)) }
        },
    )
}
