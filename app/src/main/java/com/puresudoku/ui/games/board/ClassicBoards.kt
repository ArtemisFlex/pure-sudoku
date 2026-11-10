package com.puresudoku.ui.games.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.board.AlphaBeta
import com.puresudoku.core.board.Checkers
import com.puresudoku.core.board.Gomoku
import com.puresudoku.core.board.Mancala
import com.puresudoku.core.board.Morris
import com.puresudoku.core.board.TicTacToe
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.MiddleStat
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.tutorial.DemoStage
import com.puresudoku.ui.tutorial.DemoTargets
import com.puresudoku.ui.tutorial.TutorialStep
import com.puresudoku.ui.tutorial.demoTarget
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private fun JSONArray.ints() = IntArray(length()) { getInt(it) }
private fun IntArray.jarr() = JSONArray().also { a -> forEach { a.put(it) } }

// =====================================================================  Tic-tac-toe

class TicTacToeSession(difficulty: Difficulty, val g: TicTacToe) : DuelSession(GameKind.TIC_TAC_TOE, difficulty) {
    var round by mutableIntStateOf(1); private set
    var youWins by mutableIntStateOf(0); private set
    var cpuWins by mutableIntStateOf(0); private set
    var draws by mutableIntStateOf(0); private set
    var roundOver by mutableStateOf(false); private set
    /** Order each mark was placed, so new marks animate in. */
    val placedAt = IntArray(9)

    init { message = "Round 1: you are X and go first" }

    override val middleStat get() = MiddleStat.Counter("Rounds", "$youWins–$cpuWins")
    override val progress: Float get() = (round - 1) / 5f
    override val wonTitle get() = "Match won!"
    override val lostTitle get() = "Match lost"
    override val lostMessage get() = "You $youWins, computer $cpuWins. This game's save slot has been cleared."
    override val drawMessage get() = "You $youWins, computer $cpuWins. This game's save slot has been cleared."

    val youStart get() = round % 2 == 1

    fun tap(i: Int) {
        if (outcome != Outcome.PLAYING || roundOver) return
        if (thinking || g.turn != 1) { nudge("Wait for O"); return }
        if (g.cells[i] != 0) { nudge("That square is taken"); return }
        if (!guideAllows(-1, i)) return
        g.make(i); moves++; placedAt[i] = moves; bump()
        val code = guideDone()
        if (!checkRound()) {
            if (code != null) scriptedReply(code) { c -> if (g.cells[c] == 0 && g.turn == 2) { g.make(c); moves++; placedAt[c] = moves }; checkRound(); changed() }
            else reply()
        }
    }

    private fun reply() {
        val copy = TicTacToe(g.cells.copyOf(), g.turn); val lvl = TicTacToe.level(aiDifficulty); val seed = rng.nextLong()
        computer(minMs = 500, think = { AlphaBeta(copy, lvl, Random(seed)).best() }) { m ->
            if (m != null) { g.make(m); moves++; placedAt[m] = moves }
            checkRound(); changed()
        }
    }

    /** Returns true when the round ended. */
    private fun checkRound(): Boolean {
        if (!g.over) { message = "Your turn"; changed(); return false }
        roundOver = true
        when (g.winner) {
            1 -> { youWins++; message = "You take round $round!" }
            2 -> { cpuWins++; message = "O takes round $round" }
            else -> { draws++; message = "Round $round is a draw" }
        }
        bump()
        val decided = youWins >= 3 || cpuWins >= 3 || round >= 5
        AiWorker.later(1500) {
            if (outcome != Outcome.PLAYING) return@later
            if (decided) {
                when { youWins > cpuWins -> win(); youWins < cpuWins -> lose(); else -> draw() }
            } else nextRound()
        }
        changed()
        return true
    }

    private fun nextRound() {
        round++; roundOver = false
        g.cells.fill(0); placedAt.fill(0)
        g.turn = if (youStart) 1 else 2
        message = if (youStart) "Round $round: your move" else "Round $round: O starts"
        bump(); changed()
        if (!youStart) reply()
    }

    override fun resume() {
        if (outcome != Outcome.PLAYING) return
        if (roundOver || g.over) { roundOver = false; nextRound() } else if (g.turn == 2) reply()
    }

    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (g.turn != 1 || roundOver) return null
        val copy = TicTacToe(g.cells.copyOf(), g.turn)
        return {
            AlphaBeta(copy, TicTacToe.level(Difficulty.EXTREME), Random(1)).best()?.let { m ->
                copy.make(m); val wins = copy.winner == 1; copy.unmake()
                val o = TicTacToe(copy.cells.copyOf().also { it[m] = 2 }, 1)
                val blocks = o.winner == 2
                Triple(when { wins -> "Take the ringed square: three in a row!"; blocks -> "Take the ringed square to block O's line."; m == 4 -> "Take the centre: it's on four lines."; m % 2 == 0 -> "Take the ringed corner: corners sit on three lines."; else -> "Take the ringed square." }, -1, m)
            }
        }
    }

    override fun saveState(): JSONObject = JSONObject().put("cells", g.cells.jarr()).put("turn", g.turn).put("round", round)
        .put("you", youWins).put("cpu", cpuWins).put("draws", draws).put("moves", moves)

    companion object {
        fun restore(o: JSONObject): TicTacToeSession {
            val st = o.getJSONObject("state")
            val g = TicTacToe(st.getJSONArray("cells").ints(), st.getInt("turn"))
            return TicTacToeSession(Difficulty.valueOf(o.getString("difficulty")), g).also {
                it.round = st.getInt("round"); it.youWins = st.getInt("you"); it.cpuWins = st.getInt("cpu"); it.draws = st.getInt("draws"); it.moves = st.optInt("moves")
                it.message = "Round ${it.round}"; it.restoreCommon(o)
            }
        }
    }
}

object TicTacToeDef : GameDef {
    override val kind = GameKind.TIC_TAC_TOE
    override fun generate(d: Difficulty): Session = TicTacToeSession(d, TicTacToe())
    override fun restore(o: JSONObject): Session = TicTacToeSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Get three X's in a row. Win more of the five rounds than the computer."
    override fun levelInfo(d: Difficulty) = "Best of five rounds"
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "O often misses things."
        Difficulty.MEDIUM -> "O sometimes slips."
        Difficulty.HARD -> "O blocks most threats."
        Difficulty.EXPERT -> "O rarely errs."
        Difficulty.MASTER -> "O almost never errs."
        Difficulty.EXTREME -> "O plays perfectly: a draw is a fine result."
    }
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_tictactoe
    @Composable override fun Play(session: Session) = TicTacToeUi(session as TicTacToeSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override val tutorial = listOf(
        TutorialStep("Three in a row", "Tap a square to draw your X. The computer answers with O. Three in a row (across, down or diagonal) wins the round.") { active ->
            var s by remember { mutableStateOf(TicTacToeSession(Difficulty.EASY, TicTacToe())) }
            DemoStage(active, script = {
                s.cancelThinking(); s = TicTacToeSession(Difficulty.EASY, TicTacToe()); wait(500)
                tap("t-4") { s.tap(4) }; wait(1400)
            }) { t -> TicTacToeUi(s, t) }
        },
        TutorialStep("Best of five", "A match is up to five rounds and you take turns going first. Win more rounds than the computer to win the match; equal rounds is a draw.") { active ->
            var s by remember { mutableStateOf(TicTacToeSession(Difficulty.EASY, TicTacToe())) }
            DemoStage(active, script = { s.cancelThinking(); s = TicTacToeSession(Difficulty.EASY, TicTacToe()); wait(400); highlight(listOf("score"), 1800) }) { t -> TicTacToeUi(s, t) }
        },
    )
}

@Composable
private fun TicTacToeUi(s: TicTacToeSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val chalk = Color(0xFFF4F1E6)
    val progress = FloatArray(9) { 1f }
    for (i in 0..8) androidx.compose.runtime.key(i) {
        val k = s.placedAt[i]
        val a = remember(k) { Animatable(if (k == 0) 1f else 0f) }
        LaunchedEffect(k) { if (k != 0) a.animateTo(1f, tween(360)) }
        progress[i] = a.value
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.demoTarget(targets, "score"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreChip("You (X)", "${s.youWins}", Color(0xFF2F6FD6), g.turn == 1 && !s.roundOver)
            ScoreChip("Draws", "${s.draws}", Color(0xFF9E9E9E), false)
            ScoreChip("O", "${s.cpuWins}", Color(0xFFE53935), g.turn == 2 && !s.roundOver)
        }
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight) * 0.96f
            Box(Modifier.size(side)) {
                Canvas(Modifier.size(side).clip(RoundedCornerShape(18.dp)).pointerInput(s) {
                    detectTapGestures { o -> val c = (o.x / (size.width / 3f)).toInt().coerceIn(0, 2); val r = (o.y / (size.height / 3f)).toInt().coerceIn(0, 2); s.tap(r * 3 + c) }
                }) {
                    drawRect(Brush.radialGradient(listOf(Color(0xFF2E5A47), Color(0xFF1B3A2E)), center, size.width * 0.8f))
                    val c = size.width / 3f
                    for (k in 1..2) {
                        drawLine(chalk.copy(alpha = 0.9f), Offset(k * c + 2f, c * 0.15f), Offset(k * c - 2f, size.height - c * 0.15f), c * 0.04f, StrokeCap.Round)
                        drawLine(chalk.copy(alpha = 0.9f), Offset(c * 0.15f, k * c - 2f), Offset(size.width - c * 0.15f, k * c + 2f), c * 0.04f, StrokeCap.Round)
                    }
                    for (i in 0..8) {
                        val m = g.cells[i]; if (m == 0) continue
                        val p = progress[i]
                        val ctr = Offset((i % 3) * c + c / 2, (i / 3) * c + c / 2)
                        val r = c * 0.3f
                        if (m == 1) {
                            val col = Color(0xFF9CC7FF)
                            val a = (p * 2).coerceAtMost(1f); val b = ((p - 0.5f) * 2).coerceIn(0f, 1f)
                            drawLine(col, ctr - Offset(r, r), ctr - Offset(r, r) + Offset(2 * r, 2 * r) * a, c * 0.07f, StrokeCap.Round)
                            if (b > 0) drawLine(col, ctr + Offset(r, -r), ctr + Offset(r, -r) + Offset(-2 * r, 2 * r) * b, c * 0.07f, StrokeCap.Round)
                        } else {
                            drawArc(Color(0xFFFF9C8F), -90f, 360f * p, false, ctr - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(c * 0.07f, cap = StrokeCap.Round))
                        }
                    }
                    g.winLine()?.let { l ->
                        val a = Offset((l[0] % 3) * c + c / 2, (l[0] / 3) * c + c / 2); val b = Offset((l[2] % 3) * c + c / 2, (l[2] / 3) * c + c / 2)
                        drawLine(Color(0xFFFFE066), a - (b - a) * 0.15f, b + (b - a) * 0.15f, c * 0.06f, StrokeCap.Round)
                    }
                    if (s.coachTo >= 0) coachRings(listOf(Offset((s.coachTo % 3) * c + c / 2, (s.coachTo / 3) * c + c / 2)), c * 0.4f)
                }
                val d = LocalDensity.current
                val cpx = with(d) { side.toPx() } / 3f
                CellTargets(targets, (0..8).map { "t-$it" to Rect((it % 3) * cpx, (it / 3) * cpx, (it % 3 + 1) * cpx, (it / 3 + 1) * cpx) })
            }
        }
        Spacer(Modifier.height(10.dp))
        TurnBanner(if (s.thinking) "O is thinking…" else "Round ${s.round} of 5 · ${s.message}", s.thinking)
        Spacer(Modifier.height(if (targets == null) 12.dp else 4.dp))
    }
}

// =====================================================================  Gomoku

class GomokuSession(difficulty: Difficulty, val g: Gomoku) : DuelSession(GameKind.GOMOKU, difficulty) {
    var ghost by mutableIntStateOf(-1); private set

    init { message = "You are Black: tap twice to place a stone" }
    override val wonTitle get() = "Five in a row!"
    override val lostTitle get() = "White made five"
    override val drawMessage get() = "The board filled up. This game's save slot has been cleared."

    fun tap(i: Int) {
        if (outcome != Outcome.PLAYING) return
        if (thinking || g.turn != 1) { nudge("White is thinking"); return }
        if (g.cells[i] != 0) { nudge("There's already a stone there"); return }
        if (!guideAllows(-1, i)) return
        if (ghost != i) { ghost = i; bump(); return }
        ghost = -1
        g.play(i); moves++; bump()
        val code = guideDone()
        if (!end()) {
            if (code != null) scriptedReply(code) { c -> if (g.play(c)) moves++; message = "Your move"; end() }
            else reply()
        }
    }

    private fun end(): Boolean {
        if (g.winLine != null) { if (g.turn == 2) win() else lose(); return true }
        if (g.over) { draw(); return true }
        changed(); return false
    }

    private fun reply() {
        val copy = Gomoku(g.n); for (m in g.moves) copy.play(m)
        val lvl = Gomoku.level(aiDifficulty); val seed = rng.nextLong()
        computer(minMs = 500, think = { copy.aiMove(lvl, Random(seed)) }) { m ->
            g.play(m); moves++; message = "Your move"; end()
        }
    }

    override fun resume() { if (g.turn == 2 && outcome == Outcome.PLAYING) reply() }
    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (g.turn != 1) return null
        val copy = Gomoku(g.n); for (m in g.moves) copy.play(m)
        return { val m = copy.aiMove(Gomoku.level(Difficulty.EXTREME), Random(1)); Triple("Place your stone on the ringed point (tap it twice).", -1, m) }
    }
    override fun saveState(): JSONObject = JSONObject().put("moves", g.moves.toIntArray().jarr())

    companion object {
        fun restore(o: JSONObject): GomokuSession {
            val g = Gomoku(); for (m in o.getJSONObject("state").getJSONArray("moves").ints()) g.play(m)
            return GomokuSession(Difficulty.valueOf(o.getString("difficulty")), g).also { it.moves = g.moves.size; it.restoreCommon(o) }
        }
    }
}

object GomokuDef : GameDef {
    override val kind = GameKind.GOMOKU
    override fun generate(d: Difficulty): Session = GomokuSession(d, Gomoku())
    override fun restore(o: JSONObject): Session = GomokuSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Get five black stones in an unbroken line. Block White's open threes and fours."
    override fun levelInfo(d: Difficulty) = "15 × 15 board"
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "White plays loosely."
        Difficulty.MEDIUM -> "White blocks the obvious."
        Difficulty.HARD -> "White builds its own lines."
        Difficulty.EXPERT -> "White looks a move ahead."
        Difficulty.MASTER -> "Sharp attack and defence."
        Difficulty.EXTREME -> "White's strongest play."
    }
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_gomoku
    @Composable override fun Play(session: Session) = GomokuUi(session as GomokuSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override val tutorial = listOf(
        TutorialStep("Place a stone", "Stones go on the line crossings. Tap once to preview, tap the same spot again to place it. White answers each move.") { active ->
            var s by remember { mutableStateOf(GomokuSession(Difficulty.EASY, Gomoku())) }
            DemoStage(active, script = {
                s.cancelThinking(); s = GomokuSession(Difficulty.EASY, Gomoku()); wait(500)
                tap("g-112") { s.tap(112) }; wait(400); tap("g-112") { s.tap(112) }; wait(1500)
            }) { t -> GomokuUi(s, t) }
        },
        TutorialStep("Five in a row", "The first player with five stones in an unbroken line (across, down or diagonal) wins. Watch for White's open threes: block them early.") { active ->
            var s by remember { mutableStateOf(demoGomoku()) }
            DemoStage(active, script = {
                s.cancelThinking(); s = demoGomoku(); wait(500)
                tap("g-${7 * 15 + 9}") { s.tap(7 * 15 + 9) }; wait(300); tap("g-${7 * 15 + 9}") { s.tap(7 * 15 + 9) }; wait(1800)
            }) { t -> GomokuUi(s, t) }
        },
    )

    private fun demoGomoku(): GomokuSession {
        val g = Gomoku()
        for (m in listOf(7 * 15 + 5, 9 * 15 + 9, 7 * 15 + 6, 10 * 15 + 3, 7 * 15 + 7, 11 * 15 + 11, 7 * 15 + 8, 3 * 15 + 3)) g.play(m)
        return GomokuSession(Difficulty.EASY, g)
    }
}

@Composable
private fun GomokuUi(s: GomokuSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val last = g.moves.lastOrNull() ?: -1
    val pop = rememberPop(last)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight)
            val n = g.n
            Box(Modifier.size(side)) {
                Canvas(Modifier.size(side).clip(RoundedCornerShape(8.dp)).pointerInput(s) {
                    detectTapGestures { o -> val c = size.width / n.toFloat(); s.tap((o.y / c).toInt().coerceIn(0, n - 1) * n + (o.x / c).toInt().coerceIn(0, n - 1)) }
                }) {
                    wood(Color(0xFFE9D3A4), Color(0xFFD7B983), Color(0xFF9A7B4A), seed = 11)
                    val c = size.width / n
                    val ink = Color(0xFF5A4326)
                    for (k in 0 until n) {
                        drawLine(ink, Offset(c / 2, c / 2 + k * c), Offset(size.width - c / 2, c / 2 + k * c), 1.4f)
                        drawLine(ink, Offset(c / 2 + k * c, c / 2), Offset(c / 2 + k * c, size.height - c / 2), 1.4f)
                    }
                    for ((r, cc) in listOf(3 to 3, 3 to 11, 7 to 7, 11 to 3, 11 to 11)) drawCircle(ink, c * 0.1f, Offset(c / 2 + cc * c, c / 2 + r * c))
                    val win = g.winLine?.toSet().orEmpty()
                    for (i in 0 until n * n) {
                        val st = g.cells[i]; if (st == 0) continue
                        val o = Offset(c / 2 + (i % n) * c, c / 2 + (i / n) * c)
                        val r = c * 0.46f * (if (i == last) pop else 1f)
                        if (st == 1) stone(o, r, Color(0xFF1D1D22), Color(0xFF6A6A78), Color(0xFF000000))
                        else stone(o, r, Color(0xFFF2F0EA), Color(0xFFFFFFFF), Color(0xFFBDB8AE))
                        if (i in win) drawCircle(Color(0xFFE53935), r * 0.35f, o)
                        else if (i == last) drawCircle(if (st == 1) Color.White else Color.Black, r * 0.22f, o, style = Stroke(c * 0.06f))
                    }
                    if (s.ghost >= 0) stone(Offset(c / 2 + (s.ghost % n) * c, c / 2 + (s.ghost / n) * c), c * 0.46f, Color(0x991D1D22), Color(0x996A6A78), Color(0x99000000), shadow = false)
                    if (s.coachTo >= 0) coachRings(listOf(Offset(c / 2 + (s.coachTo % n) * c, c / 2 + (s.coachTo / n) * c)), c * 0.5f)
                }
                val cpx = with(LocalDensity.current) { side.toPx() } / n
                CellTargets(targets, listOf(112, 7 * 15 + 9).map { "g-$it" to Rect((it % n) * cpx, (it / n) * cpx, (it % n + 1) * cpx, (it / n + 1) * cpx) })
            }
        }
        Spacer(Modifier.height(10.dp))
        TurnBanner(when {
            s.thinking -> "White is thinking…"
            s.ghost >= 0 -> "Tap again to place it"
            else -> s.message
        }, s.thinking)
        Spacer(Modifier.height(if (targets == null) 12.dp else 4.dp))
    }
}

// =====================================================================  Nine Men's Morris

class MorrisSession(difficulty: Difficulty, val g: Morris) : DuelSession(GameKind.MORRIS, difficulty) {
    var selected by mutableIntStateOf(-1); private set
    /** Moves waiting for you to choose which enemy piece to take. */
    var removing by mutableStateOf<List<Morris.Move>?>(null); private set
    var lastTo by mutableIntStateOf(-1); private set

    init { message = "Place your white men" }
    override val middleStat get() = MiddleStat.Counter("Men", "${g.count(1)}–${g.count(2)}")
    override val wonTitle get() = "Victory!"
    override val lostTitle get() = "Out of men"
    override val drawMessage get() = "50 moves without a mill. This game's save slot has been cleared."

    val prompt: String get() = when {
        thinking -> "Black is thinking…"
        removing != null -> "Mill! Take one of Black's men"
        g.placing(1) -> "Place a man (${g.inHand[1]} left)"
        g.flying(1) -> "Only three left: you may fly anywhere"
        selected >= 0 -> "Move to a neighbouring point"
        else -> message
    }

    private fun legal() = g.legalMoves()

    fun tap(i: Int) {
        if (outcome != Outcome.PLAYING) return
        if (thinking || g.turn != 1) { nudge("Black is thinking"); return }
        removing?.let { opts ->
            val m = opts.firstOrNull { it.remove == i }
            if (m == null) { nudge(if (g.board[i] == 2 && g.inMill(i)) "Pieces in a mill are protected while others are free" else "Tap one of Black's men"); return }
            removing = null; play(m); return
        }
        val from = if (g.placing(1)) -1 else selected
        if (g.placing(1) || selected >= 0) {
            val ms = legal().filter { it.from == from && it.to == i }
            if (ms.isNotEmpty()) {
                if (!guideAllows(from, i)) { selected = -1; bump(); return }
                if (ms[0].remove >= 0) { removing = ms; lastTo = i; selected = -1; bump(); return }
                play(ms[0]); return
            }
        }
        if (!g.placing(1) && g.board[i] == 1) {
            if (legal().none { it.from == i }) { nudge("That man is boxed in"); return }
            selected = if (selected == i) -1 else i; bump(); return
        }
        nudge(if (g.board[i] != 0) "That point is taken" else if (g.placing(1)) "Pick an empty point" else "Tap one of your men first")
    }

    private fun play(m: Morris.Move) {
        apply(m)
        val code = guideDone()
        if (outcome != Outcome.PLAYING) return
        if (code != null) scriptedReply(code) { c ->
            val f = c / 10000 - 1; val t = (c / 100) % 100; val rm = c % 100 - 1
            g.legalMoves().firstOrNull { it.from == f && it.to == t && (rm < 0 || it.remove == rm) }?.let { apply(it) }
        } else reply()
    }

    private fun apply(m: Morris.Move) {
        g.make(m); moves++; selected = -1; lastTo = m.to
        message = if (m.remove >= 0) (if (g.turn == 2) "You took one of Black's men" else "Black formed a mill and took your man") else ""
        bump()
        when {
            g.winner == 1 -> win()
            g.winner == 2 -> lose()
            g.drawn -> draw()
            else -> changed()
        }
    }

    private fun reply() {
        val copy = Morris(); copy.loadState(g.copyState()); val lvl = Morris.level(aiDifficulty); val seed = rng.nextLong()
        computer(think = { AlphaBeta(copy, lvl, Random(seed)).best() }) { m -> if (m != null) apply(m) }
    }

    override fun resume() { if (g.turn == 2 && outcome == Outcome.PLAYING) reply() }
    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (g.turn != 1 || removing != null) return null
        val copy = Morris(); copy.loadState(g.copyState())
        return {
            AlphaBeta(copy, Morris.level(Difficulty.EXPERT), Random(1)).best()?.let { m ->
                val act = if (m.from < 0) "Place a man on the ringed point" else "Move the ringed man to the ringed point"
                Triple(act + if (m.remove >= 0) ": it forms a mill, then take a black man." else ".", m.from, m.to)
            }
        }
    }
    override fun saveState(): JSONObject = JSONObject().put("s", g.copyState().jarr()).put("moves", moves)

    companion object {
        fun restore(o: JSONObject): MorrisSession {
            val g = Morris(); g.loadState(o.getJSONObject("state").getJSONArray("s").ints())
            return MorrisSession(Difficulty.valueOf(o.getString("difficulty")), g).also { it.moves = o.getJSONObject("state").optInt("moves"); it.restoreCommon(o) }
        }
    }
}

object MorrisDef : GameDef {
    override val kind = GameKind.MORRIS
    override fun generate(d: Difficulty): Session = MorrisSession(d, Morris())
    override fun restore(o: JSONObject): Session = MorrisSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Line up three men on a line to form a mill and take one of Black's. Leave Black with two men, or no moves, to win."
    override fun levelInfo(d: Difficulty) = "Computer looks ${Morris.level(d).depth} moves ahead"
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Black plays loosely."
        Difficulty.MEDIUM -> "Black spots mills."
        Difficulty.HARD -> "Black blocks your mills."
        Difficulty.EXPERT -> "Black plans ahead."
        Difficulty.MASTER -> "Deep and careful."
        Difficulty.EXTREME -> "Black's strongest play."
    }
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_morris
    @Composable override fun Play(session: Session) = MorrisUi(session as MorrisSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override val tutorial = listOf(
        TutorialStep("Place, then move", "Each side places nine men on the points, one at a time. Then you move a man along a line to a free neighbouring point. With only three men left you may fly anywhere.") { active ->
            var s by remember { mutableStateOf(MorrisSession(Difficulty.EASY, Morris())) }
            DemoStage(active, script = {
                s.cancelThinking(); s = MorrisSession(Difficulty.EASY, Morris()); wait(500)
                tap("m-4") { s.tap(4) }; wait(1600)
            }) { t -> MorrisUi(s, t) }
        },
        TutorialStep("Mills", "Three of your men in a row along a line is a mill: take one of Black's men (men inside a mill are safe unless all are). Reduce Black to two men, or leave it no moves, to win.") { active ->
            var s by remember { mutableStateOf(demoMorris()) }
            DemoStage(active, script = {
                s.cancelThinking(); s = demoMorris(); wait(500)
                tap("m-2") { s.tap(2) }; wait(600)
                tap("m-9") { s.tap(9) }; wait(1600)
            }) { t -> MorrisUi(s, t) }
        },
    )

    private fun demoMorris(): MorrisSession {
        val g = Morris()
        g.board[0] = 1; g.board[1] = 1; g.board[9] = 2; g.board[21] = 2; g.inHand[1] = 7; g.inHand[2] = 7
        return MorrisSession(Difficulty.EASY, g)
    }
}

@Composable
private fun MorrisUi(s: MorrisSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val pop = rememberPop(s.lastTo * 1000 + s.moves)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreChip("You", "${g.count(1)}", Color(0xFFF4EBD6), g.turn == 1)
            ScoreChip("Black", "${g.count(2)}", Color(0xFF2B2B33), g.turn == 2)
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight)
            val sel = s.selected
            val tg = when {
                s.removing != null -> s.removing!!.map { it.remove }.toSet()
                g.placing(1) && g.turn == 1 -> (0..23).filter { g.board[it] == 0 }.toSet()
                sel >= 0 -> g.targets(sel, 1).toSet()
                else -> emptySet()
            }
            Box(Modifier.size(side)) {
                Canvas(Modifier.size(side).clip(RoundedCornerShape(16.dp)).pointerInput(s) {
                    detectTapGestures { o ->
                        val u = size.width / 7f
                        val hit = (0..23).minByOrNull { val p = Morris.XY[it]; val dx = o.x - (p[0] + 0.5f) * u; val dy = o.y - (p[1] + 0.5f) * u; dx * dx + dy * dy }!!
                        val p = Morris.XY[hit]
                        if (Math.hypot((o.x - (p[0] + 0.5f) * u).toDouble(), (o.y - (p[1] + 0.5f) * u).toDouble()) < u * 0.7) s.tap(hit)
                    }
                }) {
                    drawRect(Brush.linearGradient(listOf(Color(0xFFE8D9B5), Color(0xFFCDB689))))
                    val u = size.width / 7f
                    fun pt(i: Int) = Offset((Morris.XY[i][0] + 0.5f) * u, (Morris.XY[i][1] + 0.5f) * u)
                    val ink = Color(0xFF5B3A1E)
                    for (m in Morris.MILLS) drawLine(ink, pt(m[0]), pt(m[2]), u * 0.06f, StrokeCap.Round)
                    for (i in 0..23) drawCircle(ink, u * 0.1f, pt(i))
                    for (i in 0..23) {
                        val st = g.board[i]; if (st == 0) continue
                        val r = u * 0.34f * (if (i == s.lastTo) pop else 1f)
                        if (st == 1) stone(pt(i), r, Color(0xFFF4EBD6), Color(0xFFFFFFFF), Color(0xFFC9B892))
                        else stone(pt(i), r, Color(0xFF2B2B33), Color(0xFF6A6A78), Color(0xFF0E0E12))
                        if (g.inMill(i)) drawCircle(Color(0xFFE8B931), r * 1.12f, pt(i), style = Stroke(u * 0.05f))
                        if (i == s.selected) drawCircle(Color(0xFF2F6FD6), r * 1.18f, pt(i), style = Stroke(u * 0.07f))
                    }
                    for (t in tg) drawCircle(if (s.removing != null) Color(0xCCD32F2F) else Color(0x882F6FD6), u * if (s.removing != null) 0.42f else 0.14f, pt(t),
                        style = if (s.removing != null) Stroke(u * 0.07f) else androidx.compose.ui.graphics.drawscope.Fill)
                    coachRings(listOf(s.coachFrom, s.coachTo).filter { it >= 0 }.map { pt(it) }, u * 0.42f)
                }
                val u = with(LocalDensity.current) { side.toPx() } / 7f
                CellTargets(targets, (0..23).map { i -> val p = Morris.XY[i]; "m-$i" to Rect(p[0] * u, p[1] * u, (p[0] + 1) * u, (p[1] + 1) * u) })
            }
        }
        Spacer(Modifier.height(10.dp))
        TurnBanner(s.prompt.ifEmpty { "Your move" }, s.thinking)
        Spacer(Modifier.height(if (targets == null) 12.dp else 4.dp))
    }
}

// =====================================================================  Mancala

class MancalaSession(difficulty: Difficulty, val g: Mancala) : DuelSession(GameKind.MANCALA, difficulty) {
    /** Pits touched by the last sowing, in order, for the trail animation. */
    var trail by mutableStateOf<List<Int>>(emptyList()); private set
    var trailId by mutableIntStateOf(0); private set

    init { message = "Pick one of your pits (bottom row)" }
    override val middleStat get() = MiddleStat.Counter("Store", "${g.pits[6]}–${g.pits[13]}")
    override val wonTitle get() = "Most seeds!"
    override val lostTitle get() = "Out-sown"
    override val lostMessage get() = "${g.pits[6]} to ${g.pits[13]}. This game's save slot has been cleared."
    override val drawMessage get() = "${g.pits[6]} each. This game's save slot has been cleared."

    fun tap(i: Int) {
        if (outcome != Outcome.PLAYING) return
        if (thinking || g.turn != 0) { nudge("The computer is sowing"); return }
        if (i !in 0..5) { nudge("Your pits are the bottom row"); return }
        if (g.pits[i] == 0) { nudge("That pit is empty"); return }
        if (!guideAllows(-1, i)) return
        sow(i)
        guideDone()
    }

    private fun sow(i: Int) {
        val mover = g.turn
        val before = g.pits[Mancala.store(mover)]
        trail = g.sow(i); trailId++; moves++
        val gained = g.pits[Mancala.store(mover)] - before
        message = when {
            g.over -> "No seeds left on one side: the rest go home"
            g.turn == mover -> if (mover == 0) "Last seed in your store: go again!" else "The computer goes again"
            gained > 1 -> if (mover == 0) "Capture! +$gained" else "The computer captured $gained"
            else -> if (mover == 0) "Computer's turn" else "Your turn"
        }
        bump()
        if (g.over) {
            val d = g.pits[6] - g.pits[13]
            AiWorker.later(700) { if (outcome == Outcome.PLAYING) { if (d > 0) win() else if (d < 0) lose() else draw() } }
            return
        }
        changed()
        if (g.turn == 1) reply()
    }

    private fun reply() {
        val copy = Mancala(g.pits.copyOf(), g.turn); val lvl = Mancala.level(aiDifficulty); val seed = rng.nextLong()
        computer(minMs = 700, think = { AlphaBeta(copy, lvl, Random(seed)).best() }) { m -> if (m != null) sow(m) }
    }

    override fun resume() { if (g.turn == 1 && outcome == Outcome.PLAYING) reply() }
    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (g.turn != 0) return null
        val copy = Mancala(g.pits.copyOf(), 0)
        return {
            AlphaBeta(copy, Mancala.level(Difficulty.MASTER), Random(1)).best()?.let { m ->
                val n = copy.pits[m]; val land = (m + n) % 13
                Triple("Sow the ringed pit (${n} seed${if (n == 1) "" else "s"})" + if (land == 6) ": the last seed lands in your store, so you go again." else ".", -1, m)
            }
        }
    }
    override fun saveState(): JSONObject = JSONObject().put("pits", g.pits.jarr()).put("turn", g.turn).put("moves", moves)

    companion object {
        fun restore(o: JSONObject): MancalaSession {
            val st = o.getJSONObject("state")
            return MancalaSession(Difficulty.valueOf(o.getString("difficulty")), Mancala(st.getJSONArray("pits").ints(), st.getInt("turn"))).also { it.moves = st.optInt("moves"); it.restoreCommon(o) }
        }
    }
}

object MancalaDef : GameDef {
    override val kind = GameKind.MANCALA
    override fun generate(d: Difficulty): Session = MancalaSession(d, Mancala())
    override fun restore(o: JSONObject): Session = MancalaSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Tap a pit to sow its seeds anticlockwise. End in your store to go again; end in an empty pit of yours to capture."
    override fun levelInfo(d: Difficulty) = "Kalah, 4 seeds per pit"
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A relaxed opponent."
        Difficulty.MEDIUM -> "Takes easy captures."
        Difficulty.HARD -> "Plans a few sowings ahead."
        Difficulty.EXPERT -> "Strong planner."
        Difficulty.MASTER -> "Hard to beat."
        Difficulty.EXTREME -> "Searches very deep."
    }
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_mancala
    @Composable override fun Play(session: Session) = MancalaUi(session as MancalaSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override val tutorial = listOf(
        TutorialStep("Sow the seeds", "Your pits are the bottom row; your store is on the right. Tap a pit: its seeds are dropped one by one into the next pits anticlockwise, including your store but never the computer's.") { active ->
            var s by remember { mutableStateOf(MancalaSession(Difficulty.EASY, Mancala())) }
            DemoStage(active, script = {
                s.cancelThinking(); s = MancalaSession(Difficulty.EASY, Mancala()); wait(500)
                tap("p-2") { s.tap(2) }; wait(1800)
            }) { t -> MancalaUi(s, t) }
        },
        TutorialStep("Extra turns and captures", "If your last seed lands in your store, go again. If it lands in an empty pit on your side, you capture it plus the seeds opposite. When a side runs out, the game ends; most seeds wins.") { active ->
            var s by remember { mutableStateOf(MancalaSession(Difficulty.EASY, Mancala(intArrayOf(1, 0, 3, 4, 2, 1, 5, 4, 4, 6, 4, 2, 3, 9), 0))) }
            DemoStage(active, script = {
                s.cancelThinking(); s = MancalaSession(Difficulty.EASY, Mancala(intArrayOf(1, 0, 3, 4, 2, 1, 5, 4, 4, 6, 4, 2, 3, 9), 0)); wait(500)
                tap("p-0") { s.tap(0) }; wait(1800)
            }) { t -> MancalaUi(s, t) }
        },
    )
}

private val BEADS = listOf(Color(0xFF4FC3F7), Color(0xFFE57373), Color(0xFF81C784), Color(0xFFFFD54F), Color(0xFFBA68C8), Color(0xFFFF8A65))

@Composable
private fun MancalaUi(s: MancalaSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val step = remember(s.trailId) { Animatable(0f) }
    LaunchedEffect(s.trailId) { step.animateTo(s.trail.size.toFloat(), tween(110 * s.trail.size.coerceAtLeast(1))) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreChip("You", "${g.pits[6]}", Color(0xFF8D6E63), g.turn == 0)
            ScoreChip("Computer", "${g.pits[13]}", Color(0xFF455A64), g.turn == 1)
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val w = maxWidth; val h = min(maxHeight, w * 0.55f)
            Box(Modifier.size(w, h)) {
                Canvas(Modifier.size(w, h).pointerInput(s) {
                    detectTapGestures { o ->
                        val u = size.width / 8f
                        val col = (o.x / u).toInt()
                        if (col in 1..6 && o.y > size.height / 2) s.tap(col - 1) else if (col in 1..6) s.nudge("Those are the computer's pits")
                    }
                }) {
                    val u = size.width / 8f
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF9C6B3F), Color(0xFF6E4524))), cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * 0.6f))
                    for (k in 0 until 18) drawLine(Color(0x22000000), Offset(0f, size.height * k / 18f), Offset(size.width, size.height * k / 18f + 6f), 3f)
                    fun pitCenter(i: Int): Offset = when (i) {
                        6 -> Offset(u * 7.5f, size.height / 2); 13 -> Offset(u * 0.5f, size.height / 2)
                        in 0..5 -> Offset(u * (i + 1.5f), size.height * 0.73f)
                        else -> Offset(u * (13 - i + 0.5f), size.height * 0.27f)
                    }
                    val shown = s.trail.take(step.value.toInt()).toSet()
                    for (i in 0..13) {
                        val c = pitCenter(i)
                        val store = i == 6 || i == 13
                        val rx = u * 0.42f; val ry = if (store) size.height * 0.4f else size.height * 0.2f
                        drawOval(Color(0x55000000), c - Offset(rx, ry) + Offset(0f, 3f), Size(rx * 2, ry * 2))
                        drawOval(Brush.radialGradient(listOf(Color(0xFF4A2C17), Color(0xFF6E4524)), c, maxOf(rx, ry)), c - Offset(rx, ry), Size(rx * 2, ry * 2))
                        if (i in shown) drawOval(Color(0x55FFE066), c - Offset(rx, ry), Size(rx * 2, ry * 2))
                        val n = g.pits[i]
                        val rr = Random(i * 31 + 7)
                        val beadR = u * 0.09f
                        for (k in 0 until minOf(n, 30)) {
                            val a = rr.nextFloat() * 6.283f; val d = rr.nextFloat()
                            val o = c + Offset(cos(a) * rx * 0.62f * d, sin(a) * ry * 0.7f * d)
                            stone(o, beadR, BEADS[(k + i) % BEADS.size], Color.White, BEADS[(k + i) % BEADS.size].copy(red = 0.2f), shadow = false)
                        }
                        glyph("$n", c + Offset(0f, if (store) 0f else if (i < 6) ry + u * 0.22f else -ry - u * 0.22f), u * 0.28f, Color(0xFFFFF3E0))
                    }
                    if (g.turn == 0 && !s.thinking) for (i in 0..5) if (g.pits[i] > 0) {
                        val c = pitCenter(i); drawOval(Color(0x662F6FD6), c - Offset(u * 0.44f, size.height * 0.21f), Size(u * 0.88f, size.height * 0.42f), style = Stroke(3f))
                    }
                    if (s.coachTo >= 0) coachRings(listOf(pitCenter(s.coachTo)), u * 0.46f)
                }
                val d = LocalDensity.current
                val (upx, hpx) = with(d) { (w / 8f).toPx() to h.toPx() }
                CellTargets(targets, (0..5).map { "p-$it" to Rect(upx * (it + 1), hpx * 0.5f, upx * (it + 2), hpx) })
            }
        }
        Spacer(Modifier.height(10.dp))
        TurnBanner(if (s.thinking) "Computer is sowing…" else s.message, s.thinking)
        Spacer(Modifier.height(if (targets == null) 12.dp else 4.dp))
    }
}

// =====================================================================  Checkers

class CheckersSession(difficulty: Difficulty, val g: Checkers) : DuelSession(GameKind.CHECKERS, difficulty) {
    var selected by mutableIntStateOf(-1); private set
    var lastPath by mutableStateOf<List<Int>>(emptyList()); private set
    var moveId by mutableIntStateOf(0); private set

    init { message = "You are Red, moving up. Captures are compulsory" }
    override val middleStat get() = MiddleStat.Counter("Pieces", "${g.b.count { it > 0 }}–${g.b.count { it < 0 }}")
    override val wonTitle get() = "Board cleared!"
    override val lostTitle get() = "Out of moves"
    override val drawMessage get() = "40 moves each without progress. This game's save slot has been cleared."

    private fun legal() = if (g.turn == 1) g.legalMoves() else emptyList()

    fun tap(i: Int) {
        if (outcome != Outcome.PLAYING) return
        if (thinking || g.turn != 1) { nudge("Black is thinking"); return }
        val ms = legal()
        if (selected >= 0) {
            val m = ms.filter { it.from == selected && it.to == i }.maxByOrNull { it.captured.size }
            if (m != null) {
                if (!guideAllows(m.from, m.to)) { selected = -1; bump(); return }
                apply(m)
                val code = guideDone()
                if (outcome == Outcome.PLAYING) {
                    if (code != null) scriptedReply(code) { c -> g.legalMoves().firstOrNull { it.from == c / 100 && it.to == c % 100 }?.let { apply(it) } }
                    else reply()
                }
                return
            }
        }
        if (g.b[i] > 0) {
            if (ms.none { it.from == i }) {
                nudge(if (ms.any { it.captured.isNotEmpty() }) "You must capture: another piece can jump" else "That piece is blocked")
                return
            }
            selected = if (selected == i) -1 else i; bump(); return
        }
        if (selected >= 0) nudge("It can't go there")
    }

    private fun apply(m: Checkers.Move) {
        val wasMan = abs(g.b[m.from]) == 1
        g.make(m); moves++; selected = -1; lastPath = m.path; moveId++
        val crowned = wasMan && abs(g.b[m.to]) == 2
        val who = if (g.turn == -1) "You" else "Black"
        message = when {
            m.captured.size > 1 -> "$who jumped ${m.captured.size} pieces!"
            m.captured.size == 1 -> "$who captured a piece"
            else -> ""
        } + if (crowned) (if (g.turn == -1) " Crowned!" else " Black crowned a king") else ""
        bump()
        when (g.terminal()) {
            null -> changed()
            0 -> draw()
            else -> if (g.turn == -1) win() else lose()
        }
    }

    private fun reply() {
        val copy = Checkers(); copy.load(g.state()); val lvl = Checkers.level(aiDifficulty); val seed = rng.nextLong()
        computer(think = { AlphaBeta(copy, lvl, Random(seed)).best() }) { m -> if (m != null) apply(m) }
    }

    override fun resume() { if (g.turn == -1 && outcome == Outcome.PLAYING) reply() }
    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (g.turn != 1) return null
        val copy = Checkers(); copy.load(g.state())
        return {
            AlphaBeta(copy, Checkers.level(Difficulty.EXPERT), Random(1)).best()?.let { m ->
                Triple("Move the ringed piece to the ringed square" + when (m.captured.size) { 0 -> "."; 1 -> ", jumping a piece."; else -> ", jumping ${m.captured.size} pieces!" }, m.from, m.to)
            }
        }
    }
    override fun saveState(): JSONObject = JSONObject().put("s", g.state().jarr()).put("moves", moves)

    companion object {
        fun restore(o: JSONObject): CheckersSession {
            val g = Checkers(); g.load(o.getJSONObject("state").getJSONArray("s").ints())
            return CheckersSession(Difficulty.valueOf(o.getString("difficulty")), g).also { it.moves = o.getJSONObject("state").optInt("moves"); it.restoreCommon(o) }
        }
    }
}

object CheckersDef : GameDef {
    override val kind = GameKind.CHECKERS
    override fun generate(d: Difficulty): Session = CheckersSession(d, Checkers())
    override fun restore(o: JSONObject): Session = CheckersSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Move diagonally forward. Jumps are compulsory and can chain. Reach the far row to crown a king."
    override fun levelInfo(d: Difficulty) = "Computer looks ${Checkers.level(d).depth} moves ahead"
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Black often misses jumps ahead."
        Difficulty.MEDIUM -> "Black sees simple trades."
        Difficulty.HARD -> "Solid play."
        Difficulty.EXPERT -> "Sets up multi-jumps."
        Difficulty.MASTER -> "Deep and careful."
        Difficulty.EXTREME -> "Black's strongest play."
    }
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_checkers
    @Composable override fun Play(session: Session) = CheckersUi(session as CheckersSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override val tutorial = listOf(
        TutorialStep("Move diagonally", "You are Red at the bottom. Tap a piece, then a highlighted dark square diagonally forward.") { active ->
            var s by remember { mutableStateOf(CheckersSession(Difficulty.EASY, Checkers())) }
            DemoStage(active, script = {
                s.cancelThinking(); s = CheckersSession(Difficulty.EASY, Checkers()); wait(500)
                tap("k-${5 * 8 + 2}") { s.tap(5 * 8 + 2) }; wait(500)
                tap("k-${4 * 8 + 3}") { s.tap(4 * 8 + 3) }; wait(1800)
            }) { t -> CheckersUi(s, t) }
        },
        TutorialStep("Jump and crown", "Jump over an enemy piece to capture it; if you can jump, you must, and a jump continues while more jumps are open. A piece reaching the far row becomes a king and may move backwards too.") { active ->
            var s by remember { mutableStateOf(demoCheckers()) }
            DemoStage(active, script = {
                s.cancelThinking(); s = demoCheckers(); wait(500)
                tap("k-${5 * 8 + 0}") { s.tap(5 * 8 + 0) }; wait(500)
                tap("k-${1 * 8 + 4}") { s.tap(1 * 8 + 4) }; wait(1800)
            }) { t -> CheckersUi(s, t) }
        },
    )

    private fun demoCheckers(): CheckersSession {
        val g = Checkers(); g.b.fill(0)
        g.b[5 * 8 + 0] = 1; g.b[4 * 8 + 1] = -1; g.b[2 * 8 + 3] = -1; g.b[0 * 8 + 7] = -1; g.b[7 * 8 + 6] = 1
        return CheckersSession(Difficulty.EASY, g)
    }
}

@Composable
private fun CheckersUi(s: CheckersSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val anim = remember(s.moveId) { Animatable(0f) }
    LaunchedEffect(s.moveId) { anim.animateTo(1f, tween(220 * (s.lastPath.size - 1).coerceAtLeast(1), easing = FastOutSlowInEasing)) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight)
            val tg = if (s.selected >= 0) g.legalMoves().filter { it.from == s.selected } else emptyList()
            Box(Modifier.size(side)) {
                Canvas(Modifier.size(side).clip(RoundedCornerShape(8.dp)).pointerInput(s) {
                    detectTapGestures { o -> val c = size.width / 8f; s.tap((o.y / c).toInt().coerceIn(0, 7) * 8 + (o.x / c).toInt().coerceIn(0, 7)) }
                }) {
                    val c = size.width / 8f
                    for (r in 0..7) for (col in 0..7) drawRect(if ((r + col) % 2 == 0) Color(0xFFF3E3C3) else Color(0xFF3B5D46), Offset(col * c, r * c), Size(c, c))
                    for (i in s.lastPath) drawRect(Color(0x44FFE066), Offset((i % 8) * c, (i / 8) * c), Size(c, c))
                    for (i in 0..63) {
                        val p = g.b[i]; if (p == 0) continue
                        var o = Offset((i % 8) * c + c / 2, (i / 8) * c + c / 2)
                        if (i == s.lastPath.lastOrNull() && s.lastPath.size > 1 && anim.value < 1f) {
                            val seg = anim.value * (s.lastPath.size - 1); val k = seg.toInt().coerceAtMost(s.lastPath.size - 2); val f = seg - k
                            val a = s.lastPath[k]; val b = s.lastPath[k + 1]
                            o = Offset(((a % 8) + ((b % 8) - (a % 8)) * f) * c + c / 2, ((a / 8) + ((b / 8) - (a / 8)) * f) * c + c / 2)
                        }
                        val red = p > 0
                        val base = if (red) Color(0xFFD03A3A) else Color(0xFF26262C)
                        stone(o, c * 0.4f, base, if (red) Color(0xFFFF8A80) else Color(0xFF5A5A66), if (red) Color(0xFF7A1414) else Color(0xFF000000))
                        drawCircle(Color.White.copy(alpha = 0.18f), c * 0.28f, o, style = Stroke(c * 0.03f))
                        if (abs(p) == 2) {
                            val cr = Path().apply {
                                moveTo(o.x - c * 0.18f, o.y + c * 0.1f); lineTo(o.x - c * 0.2f, o.y - c * 0.1f); lineTo(o.x - c * 0.08f, o.y); lineTo(o.x, o.y - c * 0.14f)
                                lineTo(o.x + c * 0.08f, o.y); lineTo(o.x + c * 0.2f, o.y - c * 0.1f); lineTo(o.x + c * 0.18f, o.y + c * 0.1f); close()
                            }
                            drawPath(cr, Color(0xFFF2C24B))
                        }
                        if (i == s.selected) drawCircle(Color(0xFF64B5F6), c * 0.45f, o, style = Stroke(c * 0.07f))
                    }
                    for (m in tg) {
                        for (k in 1 until m.path.size - 1) drawCircle(Color(0x6664B5F6), c * 0.1f, Offset((m.path[k] % 8) * c + c / 2, (m.path[k] / 8) * c + c / 2))
                        drawCircle(Color(0xAA64B5F6), c * 0.16f, Offset((m.to % 8) * c + c / 2, (m.to / 8) * c + c / 2))
                    }
                    coachRings(listOf(s.coachFrom, s.coachTo).filter { it >= 0 }.map { Offset((it % 8) * c + c / 2, (it / 8) * c + c / 2) }, c * 0.46f)
                }
                val cpx = with(LocalDensity.current) { side.toPx() } / 8f
                CellTargets(targets, (0..63).map { "k-$it" to Rect((it % 8) * cpx, (it / 8) * cpx, (it % 8 + 1) * cpx, (it / 8 + 1) * cpx) })
            }
        }
        Spacer(Modifier.height(10.dp))
        TurnBanner(if (s.thinking) "Black is thinking…" else s.message.ifEmpty { "Your move" }, s.thinking)
        Spacer(Modifier.height(if (targets == null) 12.dp else 4.dp))
    }
}
