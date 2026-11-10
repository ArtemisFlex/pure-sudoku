package com.puresudoku.ui.games.picture

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.NonogramPuzzle
import com.puresudoku.core.NonogramGenerator
import com.puresudoku.core.NonogramRules
import com.puresudoku.core.ScoreRules
import com.puresudoku.core.Scoring
import com.puresudoku.core.ints
import com.puresudoku.core.toBooleanArray
import com.puresudoku.core.toJson
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.components.StatusBar
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.ErrorFx
import com.puresudoku.ui.session.FxIds
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.session.WaveFx
import com.puresudoku.ui.theme.LocalBoardColors
import com.puresudoku.ui.theme.Poppins
import com.puresudoku.ui.tutorial.DemoStage
import com.puresudoku.ui.tutorial.DemoTargets
import com.puresudoku.ui.tutorial.TutorialStep
import com.puresudoku.ui.tutorial.demoTarget
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class PictureSession(
    difficulty: Difficulty,
    val puzzle: NonogramPuzzle,
    private val cells: IntArray = IntArray(puzzle.width * puzzle.height),
    private var streak: Int = 0,
) : Session(GameKind.PICTURE_LOGIC, difficulty) {
    val w = puzzle.width
    val h = puzzle.height
    private val rules = NonogramRules.config(difficulty).let { ScoreRules(it.basePoints, it.parSeconds) }

    var version by mutableIntStateOf(0); private set
    var markMode by mutableStateOf(false); private set
    var errorFx by mutableStateOf<ErrorFx?>(null); private set
    var waveFx by mutableStateOf<WaveFx?>(null); private set

    fun state(i: Int) = cells[i]
    fun rowDone(r: Int) = (0 until w).all { c -> !puzzle.solution[r * w + c] || cells[r * w + c] == NonogramRules.FILLED }
    fun colDone(c: Int) = (0 until h).all { r -> !puzzle.solution[r * w + c] || cells[r * w + c] == NonogramRules.FILLED }

    override val progress: Float
        get() = (0 until w * h).count { puzzle.solution[it] && cells[it] == NonogramRules.FILLED } / puzzle.filledCount.coerceAtLeast(1).toFloat()

    fun toggleMode() { markMode = !markMode }
    fun setMode(mark: Boolean) { markMode = mark }

    /** Fills (or marks) a cell. Returns false on a mistake, so a drag stops there. */
    fun act(i: Int, fill: Boolean = !markMode): Boolean {
        if (outcome != Outcome.PLAYING || cells[i] != NonogramRules.UNKNOWN) return true
        val should = puzzle.solution[i]
        if (fill != should) {
            cells[i] = if (should) NonogramRules.FILLED else NonogramRules.EMPTY
            errorFx = ErrorFx(FxIds.next(), i, 0)
            streak = 0
            score -= minOf(score, rules.basePoints)
            version++
            if (!addStrike()) { completeLines(i); checkWin(i); changed() }
            return false
        }
        cells[i] = if (fill) NonogramRules.FILLED else NonogramRules.EMPTY
        if (fill) {
            streak++
            score += Scoring.placement(rules, streak) / 2 + 1
        }
        completeLines(i)
        version++
        checkWin(i)
        if (outcome == Outcome.PLAYING) changed()
        return true
    }

    private fun completeLines(i: Int) {
        val r = i / w; val c = i % w
        val done = mutableSetOf<Int>()
        if (rowDone(r) && (0 until w).any { cells[r * w + it] == NonogramRules.UNKNOWN }) {
            for (x in 0 until w) { if (cells[r * w + x] == NonogramRules.UNKNOWN) cells[r * w + x] = NonogramRules.EMPTY; done += r * w + x }
        }
        if (colDone(c) && (0 until h).any { cells[it * w + c] == NonogramRules.UNKNOWN }) {
            for (y in 0 until h) { if (cells[y * w + c] == NonogramRules.UNKNOWN) cells[y * w + c] = NonogramRules.EMPTY; done += y * w + c }
        }
        if (done.isNotEmpty()) {
            score += Scoring.unitBonus(rules) / 2
            waveFx = WaveFx(FxIds.next(), i, done)
        }
    }

    private fun checkWin(i: Int) {
        if ((0 until w * h).any { puzzle.solution[it] && cells[it] != NonogramRules.FILLED }) return
        for (k in 0 until w * h) if (cells[k] == NonogramRules.UNKNOWN) cells[k] = NonogramRules.EMPTY
        version++
        waveFx = WaveFx(FxIds.next(), i, (0 until w * h).toSet())
        val b = Scoring.finish(rules, elapsedMs, strikes)
        finish(true, listOf(BonusLine("Time bonus", b.timeBonus), BonusLine("Hearts left bonus", b.livesBonus)))
    }

    override fun saveState(): JSONObject = JSONObject()
        .put("w", w).put("h", h)
        .put("solution", puzzle.solution.toJson())
        .put("cells", cells.toJson())
        .put("streak", streak)
}

object PictureDef : GameDef {
    override val kind = GameKind.PICTURE_LOGIC
    override fun background(session: com.puresudoku.ui.session.Session) = com.puresudoku.R.drawable.vbg_picture_logic
    private val generator = NonogramGenerator()

    override fun generate(d: Difficulty): Session = PictureSession(d, synchronized(generator) { generator.generate(d) })

    override fun restore(o: JSONObject): Session {
        val d = Difficulty.valueOf(o.getString("difficulty"))
        val st = o.getJSONObject("state")
        val p = NonogramPuzzle(st.getInt("w"), st.getInt("h"), st.getJSONArray("solution").toBooleanArray())
        return PictureSession(d, p, st.ints("cells"), st.optInt("streak")).restoreCommon(o)
    }

    override fun levelInfo(d: Difficulty) = NonogramRules.config(d).let { "${it.w}×${it.h} picture" }

    override fun hint(d: Difficulty): String = "Fill squares to match the clues. Switch to ✕ to mark squares you know are empty."

    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A tiny picture to warm up."
        Difficulty.MEDIUM -> "A small picture, simple clues."
        Difficulty.HARD -> "Clues that need cross-checking."
        Difficulty.EXPERT -> "Bigger picture, deeper logic."
        Difficulty.MASTER -> "A large picture with long clues."
        Difficulty.EXTREME -> "Large and sparse. Patience pays."
    }

    @Composable
    override fun Play(session: Session) {
        val s = session as PictureSession
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                PictureBoard(s, maxWidth, maxHeight)
            }
            Spacer(Modifier.height(12.dp))
            ModeSwitch(s)
            Spacer(Modifier.height(18.dp))
        }
    }

    @Composable
    override fun Icon(size: Dp) = GameIcon(kind, size)

    // ---------------------------------------------------------------- tutorial

    private val HEART = listOf(".#.#.", "#####", "#####", ".###.", "..#..")
    private fun demo(): PictureSession =
        PictureSession(Difficulty.EASY, NonogramPuzzle(5, 5, BooleanArray(25) { HEART[it / 5][it % 5] == '#' }))

    /** Drags the finger across [cells] (in order), acting on each as it's reached. */
    private suspend fun com.puresudoku.ui.tutorial.DemoScope.dragFill(s: PictureSession, cells: List<Int>, fill: Boolean = true) {
        var done = 0
        drag("cell-${cells.first()}", "cell-${cells.last()}", durationMs = 220 * cells.size, onMove = { f ->
            val upto = (f * (cells.size - 1)).roundToInt()
            while (done <= upto) { s.act(cells[done], fill); done++ }
        })
    }

    override fun lesson() = demo() to listOf(
        com.puresudoku.ui.session.LessonStep("The numbers beside each row and above each column are the lengths of the filled runs in that line, in order. This one draws a small picture."),
        com.puresudoku.ui.session.LessonStep("Row 2 says 5 and the row is 5 wide, so every cell is filled. Fill the whole second row.", { (it as PictureSession).rowDone(1) }),
        com.puresudoku.ui.session.LessonStep("Row 3 also says 5. Fill it all.", { (it as PictureSession).rowDone(2) }),
        com.puresudoku.ui.session.LessonStep("Now use the columns: a run that's long compared to the line forces its middle cells. Work line by line to finish the picture. Mark cells you know are empty to keep track.", { it.outcome == com.puresudoku.ui.session.Outcome.WON }),
    )

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep(
            "Number clues",
            "The numbers beside each row and above each column list the runs of filled squares in that line, in order. \"1 1\" means one square, a gap, then one more.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                highlight(listOf("rowclue-1") + (5..9).map { "cell-$it" }, 1500)
                highlight(listOf("rowclue-0") + (0..4).map { "cell-$it" }, 1500)
                highlight(listOf("colclue-0", "cell-0", "cell-5", "cell-10", "cell-15", "cell-20"), 1500)
            }) { t -> DemoPicture(s, t) }
        },
        TutorialStep(
            "Fill squares",
            "Tap a square to fill it, or drag along a row or column to fill several at once.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                dragFill(s, (5..9).toList())
                wait(300)
                dragFill(s, (10..14).toList())
                tap("cell-1") { s.act(1) }
                wait(1200)
            }) { t -> DemoPicture(s, t) }
        },
        TutorialStep(
            "Mark empty squares",
            "Switch to ✕ to mark squares you know are empty. When a line is complete, its leftover squares are marked for you.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                tap("mode-mark") { s.setMode(true) }
                tap("cell-0") { s.act(0) }
                tap("cell-2") { s.act(2) }
                tap("mode-fill") { s.setMode(false) }
                tap("cell-1") { s.act(1) }
                tap("cell-3") { s.act(3) }
                wait(1200)
            }) { t -> DemoPicture(s, t) }
        },
        TutorialStep(
            "Three strikes",
            "Filling a square that should be empty, or marking one that should be filled, costs a heart and shows the right answer. Three strikes ends the game.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                tap("cell-20") { s.act(20) }
                wait(600)
                highlight(listOf("middle"), 1100)
                wait(800)
            }) { t -> DemoPicture(s, t, status = true) }
        },
        TutorialStep(
            "Reveal the picture",
            "Fill every square in the picture to win. Finish quickly with hearts left for bonus points.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                for (i in listOf(1, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 16, 17)) s.act(i)
                wait(500)
                tap("cell-18") { s.act(18) }
                tap("cell-22") { s.act(22) }
                wait(1800)
            }) { t -> DemoPicture(s, t, status = true) }
        },
    )

    @Composable
    private fun DemoPicture(s: PictureSession, t: DemoTargets, status: Boolean = false) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            if (status) StatusBar(s.score, s.middleStat, s.elapsedMs, targets = t)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PictureBoard(s, maxWidth, maxHeight, t)
            }
            Spacer(Modifier.height(8.dp))
            ModeSwitch(s, t)
        }
    }
}

// ------------------------------------------------------------------- board

@Composable
private fun ModeSwitch(s: PictureSession, targets: DemoTargets? = null) {
    val c = LocalBoardColors.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (mark in listOf(false, true)) {
            val on = s.markMode == mark
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, label = "mode")
            val fg by animateColorAsState(if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, label = "modefg")
            Row(
                Modifier.demoTarget(targets, if (mark) "mode-mark" else "mode-fill")
                    .clip(RoundedCornerShape(50)).background(bg)
                    .pointerInput(mark) { awaitEachGesture { awaitFirstDown(); s.setMode(mark) } }
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (mark) Text("✕", color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                else Box(Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)).background(if (on) fg else c.entered))
                Spacer(Modifier.width(8.dp))
                Text(if (mark) "Mark" else "Fill", color = fg, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PictureBoard(s: PictureSession, maxW: Dp, maxH: Dp, targets: DemoTargets? = null) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val colors = LocalBoardColors.current
    val w = s.w; val h = s.h
    val rowClueLen = s.puzzle.rowClues.maxOf { maxOf(1, it.size) }
    val colClueLen = s.puzzle.colClues.maxOf { maxOf(1, it.size) }
    // Clue slots are a bit narrower than cells.
    val unitsW = w + rowClueLen * 0.62f
    val unitsH = h + colClueLen * 0.62f
    val cell = min(min(maxW / unitsW, maxH / unitsH), 56.dp)
    val clueSlot = cell * 0.62f
    val density = LocalDensity.current
    val clueFont = with(density) { min(clueSlot * 0.8f, cell * 0.52f).toSp() }
    val won = s.outcome == Outcome.WON

    Column {
        // column clues
        Row {
            Spacer(Modifier.width(clueSlot * rowClueLen))
            for (c in 0 until w) {
                val done = s.colDone(c)
                val a by animateFloatAsState(if (done) 0.3f else 1f, label = "cc")
                Column(
                    Modifier.width(cell).height(clueSlot * colClueLen).demoTarget(targets, "colclue-$c").graphicsLayer { alpha = a },
                    verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val clue = s.puzzle.colClues[c]
                    for (n in if (clue.isEmpty()) intArrayOf(0) else clue) {
                        Text("$n", fontSize = clueFont, lineHeight = clueFont, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, color = colors.given,
                            modifier = Modifier.height(clueSlot))
                    }
                }
            }
        }
        Row {
            // row clues
            Column {
                for (r in 0 until h) {
                    val done = s.rowDone(r)
                    val a by animateFloatAsState(if (done) 0.3f else 1f, label = "rc")
                    Row(
                        Modifier.width(clueSlot * rowClueLen).height(cell).demoTarget(targets, "rowclue-$r").graphicsLayer { alpha = a }.padding(end = 4.dp),
                        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val clue = s.puzzle.rowClues[r]
                        for (n in if (clue.isEmpty()) intArrayOf(0) else clue) {
                            Text("$n", fontSize = clueFont, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, color = colors.given,
                                modifier = Modifier.width(clueSlot), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
            // grid
            val cellPx = with(density) { cell.toPx() }
            Box(
                Modifier
                    .size(cell * w, cell * h)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.boardBg)
                    .pointerInput(s) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            fun cellAt(o: Offset): Int? {
                                val c = (o.x / cellPx).toInt(); val r = (o.y / cellPx).toInt()
                                return if (c in 0 until w && r in 0 until h && o.x >= 0 && o.y >= 0) r * w + c else null
                            }
                            val start = cellAt(down.position) ?: return@awaitEachGesture
                            val fill = !s.markMode
                            var alive = s.act(start, fill)
                            var axis = -1 // 0 = row, 1 = column
                            var last = start
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull() ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                if (!alive) continue
                                val at = cellAt(ch.position) ?: continue
                                if (at == last) continue
                                if (axis < 0) axis = if (at / w == start / w) 0 else if (at % w == start % w) 1 else -1
                                if (axis < 0) continue
                                // stay on the start line, fill every cell between
                                val target = if (axis == 0) (start / w) * w + at % w else (at / w) * w + start % w
                                val step = if (axis == 0) (if (target > last) 1 else -1) else (if (target > last) w else -w)
                                var x = last
                                while (x != target && alive) { x += step; alive = s.act(x, fill) }
                                last = target
                            }
                        }
                    },
            ) {
                Column {
                    for (r in 0 until h) Row {
                        for (c in 0 until w) PictureCell(s, r * w + c, cell, won, targets)
                    }
                }
                Canvas(Modifier.matchParentSize()) {
                    val cp = size.width / w
                    for (k in 1 until w) drawLine(if (k % 5 == 0) colors.thickLine else colors.thinLine, Offset(k * cp, 0f), Offset(k * cp, size.height), if (k % 5 == 0) 1.6.dp.toPx() else 1.dp.toPx())
                    for (k in 1 until h) drawLine(if (k % 5 == 0) colors.thickLine else colors.thinLine, Offset(0f, k * cp), Offset(size.width, k * cp), if (k % 5 == 0) 1.6.dp.toPx() else 1.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun PictureCell(s: PictureSession, i: Int, size: Dp, won: Boolean, targets: DemoTargets?) {
    val colors = LocalBoardColors.current
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val st = s.state(i)
    val fill by animateFloatAsState(if (st == NonogramRules.FILLED) 1f else 0f, spring(0.55f, Spring.StiffnessMedium), label = "fill")
    val xAlpha by animateFloatAsState(if (st == NonogramRules.EMPTY && !won) 1f else 0f, tween(200), label = "x")
    val wave = remember { Animatable(0f) }
    val fx = s.waveFx
    LaunchedEffect(fx?.id) {
        if (fx != null && i in fx.cells) {
            val dist = max(abs(fx.origin / s.w - i / s.w), abs(fx.origin % s.w - i % s.w))
            delay(dist * 40L)
            wave.animateTo(1f, tween(160, easing = FastOutSlowInEasing)); wave.animateTo(0f, tween(420, easing = LinearOutSlowInEasing))
        }
    }
    val shake = remember { Animatable(0f) }
    val err = remember { Animatable(0f) }
    val efx = s.errorFx
    LaunchedEffect(efx?.id) {
        if (efx != null && efx.cell == i) {
            err.snapTo(1f)
            shake.animateTo(0f, keyframes { durationMillis = 380; -8f at 50; 7f at 110; -5f at 170; 3f at 240; 0f at 380 })
            err.animateTo(0f, tween(500))
        }
    }
    Box(
        Modifier.size(size).demoTarget(targets, "cell-$i").offset { IntOffset(shake.value.dp.roundToPx(), 0) },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (err.value > 0f) drawRect(colors.errorBg.copy(alpha = err.value))
            if (wave.value > 0f) drawRect(colors.wave.copy(alpha = 0.8f * wave.value))
            if (fill > 0f) {
                val inset = this.size.width * 0.06f * (1f - fill) + 1.dp.toPx()
                drawRoundRect(
                    if (won) colors.given else colors.entered,
                    Offset(inset, inset), androidx.compose.ui.geometry.Size(this.size.width - inset * 2, this.size.height - inset * 2),
                    androidx.compose.ui.geometry.CornerRadius(this.size.width * 0.12f * (1.3f - fill)),
                    alpha = fill.coerceIn(0f, 1f),
                )
            }
            if (xAlpha > 0f) {
                val p = this.size.width * 0.3f
                val col = (if (err.value > 0f) colors.error else colors.note).copy(alpha = xAlpha)
                drawLine(col, Offset(p, p), Offset(this.size.width - p, this.size.height - p), 1.6.dp.toPx(), StrokeCap.Round)
                drawLine(col, Offset(this.size.width - p, p), Offset(p, this.size.height - p), 1.6.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}
