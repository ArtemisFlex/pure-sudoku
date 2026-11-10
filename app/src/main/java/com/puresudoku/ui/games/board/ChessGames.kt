package com.puresudoku.ui.games.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.board.AlphaBeta
import com.puresudoku.core.board.Chess
import com.puresudoku.core.board.Shogi
import com.puresudoku.core.board.Xiangqi
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.games.board.ChessArt.drawChessPiece
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.tutorial.DemoStage
import com.puresudoku.ui.tutorial.DemoTargets
import com.puresudoku.ui.tutorial.TutorialStep
import com.puresudoku.ui.tutorial.demoTarget
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

/** Invisible boxes over board cells so the how-to-play finger can point at them. */
@Composable
fun CellTargets(targets: DemoTargets?, cells: List<Pair<String, Rect>>) {
    if (targets == null) return
    val d = androidx.compose.ui.platform.LocalDensity.current
    for ((k, r) in cells) with(d) {
        Box(Modifier.offset(r.left.toDp(), r.top.toDp()).size(r.width.toDp(), r.height.toDp()).demoTarget(targets, k))
    }
}

private fun JSONArray.ints() = IntArray(length()) { getInt(it) }
private fun IntArray.jarr() = JSONArray().also { a -> forEach { a.put(it) } }

/** Draws text centred at [c] using the platform canvas (for CJK glyphs on pieces). */
fun DrawScope.glyph(text: String, c: Offset, sizePx: Float, color: Color, bold: Boolean = true) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; textSize = sizePx
        this.color = android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }
    val fm = paint.fontMetrics
    drawContext.canvas.nativeCanvas.drawText(text, c.x, c.y - (fm.ascent + fm.descent) / 2, paint)
}

// =====================================================================  Chess

class ChessSession(difficulty: Difficulty, val g: Chess) : DuelSession(GameKind.CHESS, difficulty) {
    var selected by mutableIntStateOf(-1); private set
    var lastFrom by mutableIntStateOf(-1); private set
    var lastTo by mutableIntStateOf(-1); private set
    var moveId by mutableIntStateOf(0); private set
    var promoChoices by mutableStateOf<List<Int>?>(null); private set
    private var legalCache: List<Int> = emptyList(); private var legalAt = -1

    val legal: List<Int> get() { if (legalAt != version) { legalCache = if (g.turn == 1) g.legalMoves() else emptyList(); legalAt = version }; return legalCache }

    init { message = "Your move: you are White" }

    override val wonTitle get() = "Checkmate!"
    override val lostTitle get() = "Checkmated"

    fun tap(sq: Int) {
        if (outcome != Outcome.PLAYING) return
        if (thinking || g.turn != 1) { nudge("The computer is thinking"); return }
        val p = g.b[sq]
        if (selected >= 0) {
            val ms = legal.filter { Chess.src(it) == selected && Chess.dst(it) == sq }
            if (ms.isNotEmpty()) {
                if (ms.size > 1) { promoChoices = ms; return }
                play(ms[0]); return
            }
        }
        if (p > 0) {
            if (legal.none { Chess.src(it) == sq }) { nudge(if (g.inCheck()) "You're in check: that piece can't help" else "That piece has no legal moves"); selected = -1; bump(); return }
            selected = if (selected == sq) -1 else sq; bump(); return
        }
        if (selected >= 0) nudge(if (g.inCheck()) "You must get out of check" else "That piece can't move there")
        selected = -1; bump()
    }

    fun choosePromotion(m: Int?) { promoChoices = null; if (m != null) play(m) else bump() }

    private fun play(m: Int) {
        if (!guideAllows(Chess.src(m), Chess.dst(m))) { selected = -1; bump(); return }
        message = "You played ${g.san(m)}"
        apply(m)
        val code = guideDone()
        if (outcome != Outcome.PLAYING) return
        if (code != null) scriptedReply(code) { c ->
            g.legalMoves().firstOrNull { Chess.src(it) == c / 100 && Chess.dst(it) == c % 100 }?.let { mv -> message = "Computer played ${g.san(mv)}"; apply(mv) }
        } else reply()
    }

    private fun apply(m: Int) {
        lastFrom = Chess.src(m); lastTo = Chess.dst(m); moveId++
        g.make(m); moves++; selected = -1
        bump()
        when (g.result()) {
            "checkmate" -> if (g.turn == -1) win() else lose()
            "stalemate" -> { message = "Stalemate"; draw() }
            "fifty" -> { message = "Fifty moves without a capture"; draw() }
            "repetition" -> { message = "Threefold repetition"; draw() }
            "material" -> { message = "Not enough pieces to mate"; draw() }
            else -> { if (g.inCheck()) message += if (g.turn == 1) " · You're in check!" else " · Check!"; changed() }
        }
    }

    private fun reply() {
        val copy = g.copyGame(); val lvl = Chess.level(aiDifficulty); val seed = rng.nextLong()
        computer(think = { val m = AlphaBeta(copy.searchable(), lvl, Random(seed)).best(); m?.let { it to copy.san(it) } }) { r ->
            if (r == null) return@computer
            message = "Computer played ${r.second}"
            apply(r.first)
        }
    }

    override fun resume() { if (g.turn == -1 && outcome == Outcome.PLAYING) reply() }

    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (g.turn != 1) return null
        val copy = g.copyGame()
        return {
            AlphaBeta(copy.searchable(), com.puresudoku.core.board.AiLevel(4, 1500, quiescence = true), Random(7)).best()?.let { m ->
                val names = listOf("", "pawn", "knight", "bishop", "rook", "queen", "king")
                fun sq(i: Int) = "${'a' + i % 8}${8 - i / 8}"
                val victim = abs(copy.b[Chess.dst(m)])
                val san = copy.san(m)
                Triple("Try $san: your ${names[abs(copy.b[Chess.src(m)])]} from ${sq(Chess.src(m))} to ${sq(Chess.dst(m))}" +
                    (if (victim != 0) ", capturing the ${names[victim]}" else "") + (if (san.endsWith("+")) ", with check" else if (san.endsWith("#")) ": checkmate!" else "") + ".",
                    Chess.src(m), Chess.dst(m))
            }
        }
    }

    override val drawMessage get() = "$message. This game's save slot has been cleared."

    override fun saveState(): JSONObject = JSONObject().put("b", g.b.jarr()).put("turn", g.turn).put("castle", g.castle).put("ep", g.ep)
        .put("half", g.halfmove).put("keys", JSONArray(g.keys.map { it.toString() })).put("lf", lastFrom).put("lt", lastTo).put("moves", moves).put("msg", message)

    companion object {
        fun restore(o: JSONObject): ChessSession {
            val st = o.getJSONObject("state"); val g = Chess()
            st.getJSONArray("b").ints().copyInto(g.b); g.turn = st.getInt("turn"); g.castle = st.getInt("castle"); g.ep = st.getInt("ep"); g.halfmove = st.getInt("half")
            g.key = g.hash()
            st.optJSONArray("keys")?.let { a -> for (k in 0 until a.length()) g.keys += a.getString(k).toLong() }
            if (g.keys.isEmpty()) g.keys += g.key
            return ChessSession(Difficulty.valueOf(o.getString("difficulty")), g).also {
                it.lastFrom = st.optInt("lf", -1); it.lastTo = st.optInt("lt", -1); it.moves = st.optInt("moves"); it.message = st.optString("msg"); it.restoreCommon(o)
            }
        }
    }
}

object ChessDef : GameDef {
    override val kind = GameKind.CHESS
    override fun generate(d: Difficulty): Session = ChessSession(d, Chess.start())
    override fun restore(o: JSONObject): Session = (ChessSession.restore(o)).also { it.resume() }
    override fun hint(d: Difficulty) = "Tap a piece to see where it can go, then tap a square. Checkmate the black king to win."
    override fun levelInfo(d: Difficulty) = "Computer looks ${Chess.level(d).depth} move${if (Chess.level(d).depth > 1) "s" else ""} ahead"
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A beginner who often blunders."
        Difficulty.MEDIUM -> "Spots simple captures."
        Difficulty.HARD -> "A solid club player."
        Difficulty.EXPERT -> "Calculates tactics four moves deep."
        Difficulty.MASTER -> "Strong and patient."
        Difficulty.EXTREME -> "Its very best, thinking up to 4 seconds."
    }
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_chess
    @Composable override fun Play(session: Session) = ChessBoardUi(session as ChessSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Move your pieces", "You play White. Tap one of your pieces to see the squares it can reach, then tap a square to move. Each piece moves its own way; captures land on an enemy piece.") { active ->
            var s by remember { mutableStateOf(ChessSession(Difficulty.EASY, Chess.start())) }
            DemoStage(active, script = {
                s.cancelThinking(); s = ChessSession(Difficulty.EASY, Chess.start()); wait(500)
                tap("sq-52") { s.tap(52) }; wait(500)
                tap("sq-36") { s.tap(36) }; wait(2200)
            }) { t -> ChessBoardUi(s, t) }
        },
        TutorialStep("Check and checkmate", "Attacking the king is check: the threatened side must get out of it. If it can't, that's checkmate and the game is over. Stalemate and repeated positions are draws.") { active ->
            var s by remember { mutableStateOf(ChessSession(Difficulty.EASY, Chess.fromFen("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1"))) }
            DemoStage(active, script = {
                s.cancelThinking(); s = ChessSession(Difficulty.EASY, Chess.fromFen("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1")); wait(500)
                tap("sq-56") { s.tap(56) }; wait(500)
                tap("sq-0") { s.tap(0) }; wait(2000)
            }) { t -> ChessBoardUi(s, t) }
        },
    )
}

private val CHESS_LIGHT = Color(0xFFEFD9B0)
private val CHESS_DARK = Color(0xFFB07A48)

@Composable
private fun ChessBoardUi(s: ChessSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val anim = remember(s.moveId) { Animatable(0f) }
    LaunchedEffect(s.moveId) { anim.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        CapturedRow(g, white = true)
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight)
            val cell = side / 8
            val legalTargets = if (s.selected >= 0) s.legal.filter { Chess.src(it) == s.selected }.map { Chess.dst(it) }.toSet() else emptySet()
            val checkSq = if (g.inCheck()) g.kingSq(g.turn) else -1
            Box(Modifier.size(side)) {
                Canvas(Modifier.size(side).clip(RoundedCornerShape(6.dp)).pointerInput(s) {
                    detectTapGestures { o -> val c = (o.x / (size.width / 8f)).toInt().coerceIn(0, 7); val r = (o.y / (size.height / 8f)).toInt().coerceIn(0, 7); s.tap(r * 8 + c) }
                }) {
                    val cs = size.width / 8f
                    for (r in 0..7) for (c in 0..7) {
                        val i = r * 8 + c
                        drawRect(if ((r + c) % 2 == 0) CHESS_LIGHT else CHESS_DARK, Offset(c * cs, r * cs), Size(cs, cs))
                        if (i == s.lastFrom || i == s.lastTo) drawRect(Color(0x66F6E05A), Offset(c * cs, r * cs), Size(cs, cs))
                        if (i == s.selected) drawRect(Color(0x8854A0F0), Offset(c * cs, r * cs), Size(cs, cs))
                        if (i == checkSq) drawCircle(Brush.radialGradient(listOf(Color(0xCCFF2A2A), Color(0x00FF2A2A)), Offset(c * cs + cs / 2, r * cs + cs / 2), cs * 0.7f), cs * 0.7f, Offset(c * cs + cs / 2, r * cs + cs / 2))
                    }
                    // coordinates
                    for (k in 0..7) {
                        glyph("${'a' + k}", Offset(k * cs + cs * 0.88f, 8 * cs - cs * 0.12f), cs * 0.18f, if (k % 2 == 1) CHESS_DARK else CHESS_LIGHT)
                        glyph("${8 - k}", Offset(cs * 0.1f, k * cs + cs * 0.14f), cs * 0.18f, if (k % 2 == 1) CHESS_DARK else CHESS_LIGHT)
                    }
                    for (i in 0..63) {
                        val p = g.b[i]; if (p == 0) continue
                        var x = (i % 8) * cs; var y = (i / 8) * cs
                        if (i == s.lastTo && s.lastFrom >= 0 && anim.value < 1f) {
                            x = ((s.lastFrom % 8) + ((i % 8) - (s.lastFrom % 8)) * anim.value) * cs
                            y = ((s.lastFrom / 8) + ((i / 8) - (s.lastFrom / 8)) * anim.value) * cs
                        }
                        drawChessPiece(abs(p), p > 0, Rect(x + cs * 0.06f, y + cs * 0.04f, x + cs * 0.94f, y + cs * 0.92f))
                    }
                    for (t in legalTargets) {
                        val c = Offset((t % 8) * cs + cs / 2, (t / 8) * cs + cs / 2)
                        if (g.b[t] != 0) drawCircle(Color(0x99000000), cs * 0.46f, c, style = Stroke(cs * 0.08f))
                        else drawCircle(Color(0x55000000), cs * 0.15f, c)
                    }
                    coachRings(listOf(s.coachFrom, s.coachTo).filter { it >= 0 }.map { Offset((it % 8) * cs + cs / 2, (it / 8) * cs + cs / 2) }, cs * 0.48f)
                }
                val d = androidx.compose.ui.platform.LocalDensity.current
                val cpx = with(d) { cell.toPx() }
                CellTargets(targets, (0..63).map { "sq-$it" to Rect((it % 8) * cpx, (it / 8) * cpx, (it % 8 + 1) * cpx, (it / 8 + 1) * cpx) })
            }
        }
        Spacer(Modifier.height(6.dp))
        CapturedRow(g, white = false)
        Spacer(Modifier.height(8.dp))
        TurnBanner(if (s.thinking) "Computer is thinking…" else s.message.ifEmpty { "Your move" }, s.thinking)
        Spacer(Modifier.height(if (targets == null) 12.dp else 4.dp))
    }
    s.promoChoices?.let { choices ->
        AlertDialog(
            onDismissRequest = { s.choosePromotion(null) },
            title = { Text("Promote your pawn") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in choices.sortedByDescending { Chess.promo(it) }) {
                        Canvas(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(CHESS_LIGHT).clickable { s.choosePromotion(m) }) {
                            drawChessPiece(Chess.promo(m), true, Rect(Offset(size.width * 0.08f, size.height * 0.06f), Size(size.width * 0.84f, size.height * 0.84f)))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { s.choosePromotion(null) }) { Text("Cancel") } },
        )
    }
}

/** Pieces captured from one side, smallest first. */
@Composable
private fun CapturedRow(g: Chess, white: Boolean) {
    val start = intArrayOf(0, 8, 2, 2, 2, 1, 1)
    val sign = if (white) 1 else -1
    val lost = (1..5).flatMap { t -> List((start[t] - g.b.count { it == t * sign }).coerceAtLeast(0)) { t } }
    val diff = (1..5).sumOf { t -> (g.b.count { it == t } - g.b.count { it == -t }) * Chess.VALUE[t] } / 100
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (white) "Computer took" else "You took", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(6.dp))
        Canvas(Modifier.weight(1f).height(22.dp)) {
            lost.forEachIndexed { k, t -> drawChessPiece(t, white, Rect(k * size.height * 0.62f, 0f, k * size.height * 0.62f + size.height, size.height)) }
        }
        val lead = if (white) -diff else diff
        if (lead > 0) Text("+$lead", fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// =====================================================================  Xiangqi

class XiangqiSession(difficulty: Difficulty, val g: Xiangqi) : DuelSession(GameKind.XIANGQI, difficulty) {
    var selected by mutableIntStateOf(-1); private set
    var lastFrom by mutableIntStateOf(-1); private set
    var lastTo by mutableIntStateOf(-1); private set
    var moveId by mutableIntStateOf(0); private set
    private var legalCache: List<Int> = emptyList(); private var legalAt = -1
    val legal: List<Int> get() { if (legalAt != version) { legalCache = if (g.turn == 1) g.legalMoves() else emptyList(); legalAt = version }; return legalCache }

    init { message = "Your move: you are Red" }

    override val wonTitle get() = "The general falls!"
    override val lostTitle get() = "Your general is taken"
    override val drawMessage get() = "$message. This game's save slot has been cleared."

    fun tap(i: Int) {
        if (outcome != Outcome.PLAYING) return
        if (thinking || g.turn != 1) { nudge("The computer is thinking"); return }
        if (selected >= 0) {
            val m = legal.firstOrNull { Xiangqi.src(it) == selected && Xiangqi.dst(it) == i }
            if (m != null) {
                if (!guideAllows(Xiangqi.src(m), Xiangqi.dst(m))) { selected = -1; bump(); return }
                apply(m)
                val code = guideDone()
                if (outcome == Outcome.PLAYING) {
                    if (code != null) scriptedReply(code) { c -> g.legalMoves().firstOrNull { Xiangqi.src(it) == c / 100 && Xiangqi.dst(it) == c % 100 }?.let { apply(it) } }
                    else reply()
                }
                return
            }
        }
        if (g.b[i] > 0) {
            if (legal.none { Xiangqi.src(it) == i }) { nudge(if (g.inCheck()) "Your general is in check" else "That piece can't move"); selected = -1; bump(); return }
            selected = if (selected == i) -1 else i; bump(); return
        }
        if (selected >= 0) nudge("That piece can't move there")
        selected = -1; bump()
    }

    private fun apply(m: Int) {
        val captured = g.b[Xiangqi.dst(m)]
        lastFrom = Xiangqi.src(m); lastTo = Xiangqi.dst(m); moveId++
        val who = if (g.turn == 1) "You" else "Computer"
        val name = (if (g.b[lastFrom] > 0) Xiangqi.RED_NAMES else Xiangqi.BLACK_NAMES)[abs(g.b[lastFrom])]
        g.make(m); moves++; selected = -1
        message = "$who moved $name" + if (captured != 0) " and captured ${(if (captured > 0) Xiangqi.RED_NAMES else Xiangqi.BLACK_NAMES)[abs(captured)]}" else ""
        bump()
        when (g.result()) {
            "checkmate", "stalemate" -> if (g.turn == -1) win() else lose()
            "quiet" -> { message = "120 moves without a capture"; draw() }
            "repetition" -> { message = "Repeated position"; draw() }
            else -> { if (g.inCheck()) message += " · Check!"; changed() }
        }
    }

    private fun reply() {
        val copy = Xiangqi(); copy.load(g.state()); val lvl = Xiangqi.level(aiDifficulty); val seed = rng.nextLong()
        computer(think = { AlphaBeta(copy, lvl, Random(seed)).best() }) { m -> if (m != null) apply(m) }
    }

    override fun resume() { if (g.turn == -1 && outcome == Outcome.PLAYING) reply() }

    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (g.turn != 1) return null
        val copy = Xiangqi(); copy.load(g.state())
        return {
            AlphaBeta(copy, com.puresudoku.core.board.AiLevel(4, 1500, quiescence = true), Random(7)).best()?.let { m ->
                val roles = listOf("", "General", "Advisor", "Elephant", "Horse", "Chariot", "Cannon", "Soldier")
                val f = Xiangqi.src(m); val t = Xiangqi.dst(m); val p = abs(copy.b[f]); val v = abs(copy.b[t])
                Triple("Move your ${roles[p]} (${Xiangqi.RED_NAMES[p]}) ${directionWords(t / 9 - f / 9, t % 9 - f % 9)}" +
                    (if (v != 0) ", capturing the ${roles[v]}" else "") + ". It's ringed in gold.", f, t)
            }
        }
    }

    override fun saveState(): JSONObject = JSONObject().put("s", g.state().jarr()).put("lf", lastFrom).put("lt", lastTo).put("moves", moves).put("msg", message)

    companion object {
        fun restore(o: JSONObject): XiangqiSession {
            val st = o.getJSONObject("state"); val g = Xiangqi(); g.load(st.getJSONArray("s").ints())
            return XiangqiSession(Difficulty.valueOf(o.getString("difficulty")), g).also {
                it.lastFrom = st.optInt("lf", -1); it.lastTo = st.optInt("lt", -1); it.moves = st.optInt("moves"); it.message = st.optString("msg"); it.restoreCommon(o)
            }
        }
    }
}

object XiangqiDef : GameDef {
    override val kind = GameKind.XIANGQI
    override fun generate(d: Difficulty): Session = XiangqiSession(d, Xiangqi())
    override fun restore(o: JSONObject): Session = XiangqiSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Pieces stand on the points. Tap a red piece, then a point. Trap the black general to win."
    override fun levelInfo(d: Difficulty) = "Computer looks ${Xiangqi.level(d).depth} moves ahead"
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A careless opponent."
        Difficulty.MEDIUM -> "Grabs loose pieces."
        Difficulty.HARD -> "Plays sound moves."
        Difficulty.EXPERT -> "Sees combinations."
        Difficulty.MASTER -> "Deep and careful."
        Difficulty.EXTREME -> "Its strongest play."
    }
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_xiangqi
    @Composable override fun Play(session: Session) = XiangqiBoardUi(session as XiangqiSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Across the river", "You play Red at the bottom. Tap a piece, then a highlighted point. Chariots move like rooks; horses step one then diagonally (unless blocked); cannons must jump exactly one piece to capture.") { active ->
            var s by remember { mutableStateOf(XiangqiSession(Difficulty.EASY, Xiangqi())) }
            DemoStage(active, script = {
                s.cancelThinking(); s = XiangqiSession(Difficulty.EASY, Xiangqi()); wait(500)
                tap("pt-${7 * 9 + 1}") { s.tap(7 * 9 + 1) }; wait(500)
                tap("pt-${7 * 9 + 4}") { s.tap(7 * 9 + 4) }; wait(2200)
            }) { t -> XiangqiBoardUi(s, t) }
        },
        TutorialStep("Palace and general", "Generals and advisors never leave the palace (the X-marked square). Elephants can't cross the river; soldiers may step sideways once across. The two generals may never face each other on an open file. Trap the enemy general to win.") { active ->
            var s by remember { mutableStateOf(XiangqiSession(Difficulty.EASY, Xiangqi())) }
            DemoStage(active, script = {
                s.cancelThinking(); s = XiangqiSession(Difficulty.EASY, Xiangqi()); wait(400)
                highlight(listOf("pt-${8 * 9 + 4}"), 1400)
                tap("pt-${9 * 9 + 4}") { s.tap(9 * 9 + 4) }; wait(1500)
            }) { t -> XiangqiBoardUi(s, t) }
        },
    )
}

@Composable
private fun XiangqiBoardUi(s: XiangqiSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val anim = remember(s.moveId) { Animatable(0f) }
    LaunchedEffect(s.moveId) { anim.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val cw = min(maxWidth / 9.2f, maxHeight / 10.4f)
            val w = cw * 9.2f; val h = cw * 10.4f
            val targetsSet = if (s.selected >= 0) s.legal.filter { Xiangqi.src(it) == s.selected }.map { Xiangqi.dst(it) }.toSet() else emptySet()
            Box(Modifier.size(w, h)) {
                Canvas(Modifier.size(w, h).clip(RoundedCornerShape(10.dp)).pointerInput(s) {
                    detectTapGestures { o ->
                        val c = size.width / 9.2f
                        val col = ((o.x - c * 0.6f) / c + 0.5f).toInt().coerceIn(0, 8); val row = ((o.y - c * 0.7f) / c + 0.5f).toInt().coerceIn(0, 9)
                        s.tap(row * 9 + col)
                    }
                }) {
                    val c = size.width / 9.2f
                    wood(Color(0xFFF0C97C), Color(0xFFD9A55A), Color(0xFF7A4A1A))
                    fun pt(r: Int, col: Int) = Offset(c * 0.6f + col * c, c * 0.7f + r * c)
                    val ink = Color(0xFF5A2E10)
                    for (r in 0..9) drawLine(ink, pt(r, 0), pt(r, 8), 2f)
                    for (col in 0..8) {
                        if (col == 0 || col == 8) drawLine(ink, pt(0, col), pt(9, col), 2f)
                        else { drawLine(ink, pt(0, col), pt(4, col), 2f); drawLine(ink, pt(5, col), pt(9, col), 2f) }
                    }
                    for ((a, b) in listOf(pt(0, 3) to pt(2, 5), pt(0, 5) to pt(2, 3), pt(7, 3) to pt(9, 5), pt(7, 5) to pt(9, 3))) drawLine(ink, a, b, 2f)
                    glyph("楚 河", Offset(pt(4, 2).x, (pt(4, 0).y + pt(5, 0).y) / 2), c * 0.55f, ink.copy(alpha = 0.75f))
                    glyph("漢 界", Offset(pt(4, 6).x, (pt(4, 0).y + pt(5, 0).y) / 2), c * 0.55f, ink.copy(alpha = 0.75f))
                    if (s.lastFrom >= 0) drawCircle(Color(0x553A8F3A), c * 0.48f, pt(s.lastFrom / 9, s.lastFrom % 9))
                    for (i in 0..89) {
                        val p = g.b[i]; if (p == 0) continue
                        var o = pt(i / 9, i % 9)
                        if (i == s.lastTo && s.lastFrom >= 0 && anim.value < 1f) {
                            val a = pt(s.lastFrom / 9, s.lastFrom % 9); o = a + (o - a) * anim.value
                        }
                        val red = p > 0
                        stone(o, c * 0.44f, Color(0xFFF6E7C8), Color(0xFFFFF8E6), Color(0xFFC9A66B))
                        drawCircle(if (red) Color(0xFFB71C1C) else Color(0xFF1A1A1A), c * 0.36f, o, style = Stroke(c * 0.04f))
                        if (i == s.selected) drawCircle(Color(0xFF2F6FD6), c * 0.47f, o, style = Stroke(c * 0.07f))
                        glyph((if (red) Xiangqi.RED_NAMES else Xiangqi.BLACK_NAMES)[abs(p)], o, c * 0.46f, if (red) Color(0xFFB71C1C) else Color(0xFF1A1A1A))
                    }
                    for (t in targetsSet) drawCircle(if (g.b[t] != 0) Color(0xAAD32F2F) else Color(0x882F6FD6), c * if (g.b[t] != 0) 0.5f else 0.14f, pt(t / 9, t % 9),
                        style = if (g.b[t] != 0) Stroke(c * 0.07f) else androidx.compose.ui.graphics.drawscope.Fill)
                    coachRings(listOf(s.coachFrom, s.coachTo).filter { it >= 0 }.map { pt(it / 9, it % 9) }, c * 0.5f)
                }
                val d = androidx.compose.ui.platform.LocalDensity.current
                val cpx = with(d) { cw.toPx() }
                CellTargets(targets, (0..89).map { i ->
                    val x = cpx * 0.6f + (i % 9) * cpx; val y = cpx * 0.7f + (i / 9) * cpx
                    "pt-$i" to Rect(x - cpx / 2, y - cpx / 2, x + cpx / 2, y + cpx / 2)
                })
            }
        }
        Spacer(Modifier.height(8.dp))
        TurnBanner(if (s.thinking) "Computer is thinking…" else s.message.ifEmpty { "Your move" }, s.thinking)
        Spacer(Modifier.height(if (targets == null) 12.dp else 4.dp))
    }
}

// =====================================================================  Shogi

class ShogiSession(difficulty: Difficulty, val g: Shogi) : DuelSession(GameKind.SHOGI, difficulty) {
    /** Selected board square, or 100 + piece type for a piece in hand. */
    var selected by mutableIntStateOf(-1); private set
    var lastTo by mutableIntStateOf(-1); private set
    var lastFrom by mutableIntStateOf(-1); private set
    var moveId by mutableIntStateOf(0); private set
    var promoAsk by mutableStateOf<Pair<Int, Int>?>(null); private set
    private var legalCache: List<Int> = emptyList(); private var legalAt = -1
    val legal: List<Int> get() { if (legalAt != version) { legalCache = if (g.turn == 1) g.legalMoves() else emptyList(); legalAt = version }; return legalCache }

    init { message = "Your move: you are Sente (bottom)" }

    override val wonTitle get() = "Tsumi! Checkmate"
    override val lostTitle get() = "Your king is caught"
    override val drawMessage get() = "$message. This game's save slot has been cleared."

    fun movesFromSelected(): List<Int> = when {
        selected >= 100 -> legal.filter { Shogi.isDrop(it) && Shogi.src(it) - Shogi.DROP == selected - 100 }
        selected >= 0 -> legal.filter { !Shogi.isDrop(it) && Shogi.src(it) == selected }
        else -> emptyList()
    }

    fun tapHand(t: Int) {
        if (outcome != Outcome.PLAYING || thinking) return
        if (g.hands[0][t] == 0) return
        if (legal.none { Shogi.isDrop(it) && Shogi.src(it) - Shogi.DROP == t }) { nudge("That piece can't be dropped anywhere right now"); return }
        selected = if (selected == 100 + t) -1 else 100 + t; bump()
    }

    fun tap(i: Int) {
        if (outcome != Outcome.PLAYING) return
        if (thinking || g.turn != 1) { nudge("The computer is thinking"); return }
        if (selected >= 0) {
            val ms = movesFromSelected().filter { Shogi.dst(it) == i }
            if (ms.size == 2) { promoAsk = ms.first { Shogi.promotes(it) } to ms.first { !Shogi.promotes(it) }; return }
            if (ms.size == 1) { play(ms[0]); return }
        }
        if (g.b[i] > 0) {
            if (legal.none { !Shogi.isDrop(it) && Shogi.src(it) == i }) { nudge(if (g.inCheck()) "Your king is in check" else "That piece can't move"); selected = -1; bump(); return }
            selected = if (selected == i) -1 else i; bump(); return
        }
        if (selected >= 0) nudge(if (selected >= 100) "You can't drop it there" else "That piece can't move there")
        selected = -1; bump()
    }

    fun answerPromotion(promote: Boolean?) {
        val a = promoAsk ?: return; promoAsk = null
        when (promote) { true -> play(a.first); false -> play(a.second); null -> bump() }
    }

    private fun play(m: Int) {
        if (!guideAllows(Shogi.src(m), Shogi.dst(m))) { selected = -1; bump(); return }
        apply(m)
        val code = guideDone()
        if (outcome != Outcome.PLAYING) return
        if (code != null) scriptedReply(code) { c -> g.legalMoves().firstOrNull { Shogi.src(it) == c / 100 && Shogi.dst(it) == c % 100 && !Shogi.promotes(it) }?.let { apply(it) } }
        else reply()
    }

    private fun apply(m: Int) {
        val who = if (g.turn == 1) "You" else "Computer"
        lastTo = Shogi.dst(m); lastFrom = if (Shogi.isDrop(m)) -1 else Shogi.src(m); moveId++
        val captured = if (Shogi.isDrop(m)) 0 else abs(g.b[lastTo])
        val piece = if (Shogi.isDrop(m)) Shogi.src(m) - Shogi.DROP else abs(g.b[Shogi.src(m)])
        g.make(m); moves++; selected = -1
        message = "$who " + (if (Shogi.isDrop(m)) "dropped ${Shogi.NAMES[piece]}" else "moved ${Shogi.NAMES[piece]}" + if (Shogi.promotes(m)) " and promoted" else "") +
            if (captured != 0) ", taking ${Shogi.NAMES[Shogi.base(captured)]}" else ""
        bump()
        when (g.result()) {
            "checkmate" -> if (g.turn == -1) win() else lose()
            "repetition" -> { message = "Sennichite: the same position four times"; draw() }
            "moves" -> { message = "The game ran too long"; draw() }
            else -> { if (g.inCheck()) message += " · Check!"; changed() }
        }
    }

    private fun reply() {
        val copy = Shogi(); copy.load(g.state()); val lvl = Shogi.level(aiDifficulty); val seed = rng.nextLong()
        computer(think = { AlphaBeta(copy, lvl, Random(seed)).best() }) { m -> if (m != null) apply(m) }
    }

    override fun resume() { if (g.turn == -1 && outcome == Outcome.PLAYING) reply() }

    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (g.turn != 1) return null
        val copy = Shogi(); copy.load(g.state())
        return {
            AlphaBeta(copy, com.puresudoku.core.board.AiLevel(3, 2000, quiescence = true), Random(7)).best()?.let { m ->
                val names = listOf("", "Pawn", "Lance", "Knight", "Silver", "Gold", "Bishop", "Rook", "King", "Tokin", "Promoted lance", "Promoted knight", "Promoted silver", "Horse", "Dragon")
                val t = Shogi.dst(m)
                if (Shogi.isDrop(m)) {
                    val p = Shogi.src(m) - Shogi.DROP
                    Triple("Drop a ${names[p]} (${Shogi.NAMES[p]}) from your hand on the ringed square.", -1, t)
                } else {
                    val f = Shogi.src(m); val p = abs(copy.b[f]); val v = abs(copy.b[t])
                    Triple("Move your ${names[p]} (${Shogi.NAMES[p]}) ${directionWords(t / 9 - f / 9, t % 9 - f % 9)}" +
                        (if (v != 0) ", capturing the ${names[v]}" else "") + (if (Shogi.promotes(m)) " and promote it" else "") + ".", f, t)
                }
            }
        }
    }

    override fun saveState(): JSONObject = JSONObject().put("s", g.state().jarr()).put("lt", lastTo).put("lf", lastFrom).put("moves", moves).put("msg", message)

    companion object {
        fun restore(o: JSONObject): ShogiSession {
            val st = o.getJSONObject("state"); val g = Shogi(); g.load(st.getJSONArray("s").ints())
            return ShogiSession(Difficulty.valueOf(o.getString("difficulty")), g).also {
                it.lastTo = st.optInt("lt", -1); it.lastFrom = st.optInt("lf", -1); it.moves = st.optInt("moves"); it.message = st.optString("msg"); it.restoreCommon(o)
            }
        }
    }
}

object ShogiDef : GameDef {
    override val kind = GameKind.SHOGI
    override fun generate(d: Difficulty): Session = ShogiSession(d, Shogi())
    override fun restore(o: JSONObject): Session = ShogiSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Tap a piece, then a square. Pieces you capture join your hand: tap one below the board to drop it back in."
    override fun levelInfo(d: Difficulty) = "Computer looks ${Shogi.level(d).depth} moves ahead"
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A careless opponent."
        Difficulty.MEDIUM -> "Takes what you leave hanging."
        Difficulty.HARD -> "Plays sound moves."
        Difficulty.EXPERT -> "Sees three moves ahead."
        Difficulty.MASTER -> "Deep and patient."
        Difficulty.EXTREME -> "Its strongest play."
    }
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_shogi
    @Composable override fun Play(session: Session) = ShogiBoardUi(session as ShogiSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Move and promote", "You are Sente, at the bottom; your pieces point up. Tap a piece, then a highlighted square. Reaching the far three rows lets most pieces promote: you'll be asked.") { active ->
            var s by remember { mutableStateOf(ShogiSession(Difficulty.EASY, Shogi())) }
            DemoStage(active, script = {
                s.cancelThinking(); s = ShogiSession(Difficulty.EASY, Shogi()); wait(500)
                tap("sq-${6 * 9 + 2}") { s.tap(6 * 9 + 2) }; wait(500)
                tap("sq-${5 * 9 + 2}") { s.tap(5 * 9 + 2) }; wait(2200)
            }) { t -> ShogiBoardUi(s, t) }
        },
        TutorialStep("Drops", "Captured pieces go to your hand below the board. On your turn you may drop one onto any empty square instead of moving (no two unpromoted pawns in a file). Checkmate the computer's king to win.") { active ->
            var s by remember { mutableStateOf(ShogiSession(Difficulty.EASY, Shogi().also { it.hands[0][Shogi.SILVER] = 1 })) }
            DemoStage(active, script = {
                s.cancelThinking(); s = ShogiSession(Difficulty.EASY, Shogi().also { it.hands[0][Shogi.SILVER] = 1 }); wait(500)
                tap("hand-${Shogi.SILVER}") { s.tapHand(Shogi.SILVER) }; wait(500)
                tap("sq-${5 * 9 + 4}") { s.tap(5 * 9 + 4) }; wait(2200)
            }) { t -> ShogiBoardUi(s, t) }
        },
    )
}

private fun DrawScope.shogiPiece(t: Int, mine: Boolean, center: Offset, w: Float, h: Float, selected: Boolean = false) {
    val path = Path().apply {
        moveTo(0f, -h / 2); lineTo(w * 0.36f, -h * 0.3f); lineTo(w / 2, h / 2); lineTo(-w / 2, h / 2); lineTo(-w * 0.36f, -h * 0.3f); close()
    }
    rotate(if (mine) 0f else 180f, center) {
        val p = Path().apply { addPath(path, center) }
        drawPath(p, Color.Black.copy(alpha = 0.25f), style = Stroke(w * 0.1f))
        drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFBE7B8), Color(0xFFE2BE7A)), startY = center.y - h / 2, endY = center.y + h / 2))
        drawPath(p, if (selected) Color(0xFF2F6FD6) else Color(0xFF7A5226), style = Stroke(if (selected) w * 0.09f else w * 0.035f))
        val promotedPiece = Shogi.promoted(t)
        glyph(Shogi.NAMES[if (t == Shogi.KING && !mine) 0 else t].ifEmpty { "玉" }, center + Offset(0f, h * 0.08f), h * 0.5f,
            if (promotedPiece) Color(0xFFB71C1C) else Color(0xFF2A1A0E))
    }
}

@Composable
private fun ShogiBoardUi(s: ShogiSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val anim = remember(s.moveId) { Animatable(0f) }
    LaunchedEffect(s.moveId) { anim.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        HandRow(g.hands[1], mine = false, selected = -1, targets = null) {}
        Spacer(Modifier.height(4.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val cw = min(maxWidth / 9.3f, maxHeight / 10f)
            val w = cw * 9.3f; val h = cw * 9.3f * 1.08f
            val tg = s.movesFromSelected().map { Shogi.dst(it) }.toSet()
            Box(Modifier.size(w, h)) {
                Canvas(Modifier.size(w, h).clip(RoundedCornerShape(6.dp)).pointerInput(s) {
                    detectTapGestures { o ->
                        val cx = size.width / 9.3f; val cy = size.height / 9.3f
                        val col = ((o.x - cx * 0.15f) / cx).toInt().coerceIn(0, 8); val row = ((o.y - cy * 0.15f) / cy).toInt().coerceIn(0, 8)
                        s.tap(row * 9 + col)
                    }
                }) {
                    val cx = size.width / 9.3f; val cy = size.height / 9.3f
                    wood(Color(0xFFF2CD86), Color(0xFFDDAE62), Color(0xFF8A5A22), seed = 7)
                    val ink = Color(0xFF3A2410)
                    for (k in 0..9) {
                        drawLine(ink, Offset(cx * 0.15f + k * cx, cy * 0.15f), Offset(cx * 0.15f + k * cx, cy * 0.15f + 9 * cy), 1.6f)
                        drawLine(ink, Offset(cx * 0.15f, cy * 0.15f + k * cy), Offset(cx * 0.15f + 9 * cx, cy * 0.15f + k * cy), 1.6f)
                    }
                    for ((r, c) in listOf(3 to 3, 3 to 6, 6 to 3, 6 to 6)) drawCircle(ink, 3.5f, Offset(cx * 0.15f + c * cx, cy * 0.15f + r * cy))
                    fun cell(i: Int) = Offset(cx * 0.15f + (i % 9) * cx + cx / 2, cy * 0.15f + (i / 9) * cy + cy / 2)
                    for (i in listOf(s.lastFrom, s.lastTo)) if (i >= 0) drawRect(Color(0x553A8F3A), cell(i) - Offset(cx / 2, cy / 2), Size(cx, cy))
                    for (i in 0..80) {
                        val p = g.b[i]; if (p == 0) continue
                        var o = cell(i)
                        if (i == s.lastTo && s.lastFrom >= 0 && anim.value < 1f) { val a = cell(s.lastFrom); o = a + (o - a) * anim.value }
                        shogiPiece(abs(p), p > 0, o, cx * 0.8f, cy * 0.88f, i == s.selected)
                    }
                    for (t in tg) drawCircle(if (g.b[t] != 0) Color(0xAAD32F2F) else Color(0x882F6FD6), cx * if (g.b[t] != 0) 0.46f else 0.13f, cell(t),
                        style = if (g.b[t] != 0) Stroke(cx * 0.07f) else androidx.compose.ui.graphics.drawscope.Fill)
                    coachRings(listOf(s.coachFrom, s.coachTo).filter { it >= 0 }.map { cell(it) }, cx * 0.5f)
                }
                val d = androidx.compose.ui.platform.LocalDensity.current
                val (cxp, cyp) = with(d) { (w / 9.3f).toPx() to (h / 9.3f).toPx() }
                CellTargets(targets, (0..80).map { i ->
                    val x = cxp * 0.15f + (i % 9) * cxp; val y = cyp * 0.15f + (i / 9) * cyp
                    "sq-$i" to Rect(x, y, x + cxp, y + cyp)
                })
            }
        }
        Spacer(Modifier.height(4.dp))
        HandRow(g.hands[0], mine = true, selected = if (s.selected >= 100) s.selected - 100 else -1, targets = targets) { s.tapHand(it) }
        Spacer(Modifier.height(6.dp))
        TurnBanner(if (s.thinking) "Computer is thinking…" else s.message.ifEmpty { "Your move" }, s.thinking)
        Spacer(Modifier.height(if (targets == null) 12.dp else 4.dp))
    }
    s.promoAsk?.let {
        AlertDialog(
            onDismissRequest = { s.answerPromotion(null) },
            title = { Text("Promote?") },
            text = { Text("This piece has reached the promotion zone. Promoted pieces move like a gold general (rook and bishop gain king steps).") },
            confirmButton = { TextButton(onClick = { s.answerPromotion(true) }) { Text("Promote") } },
            dismissButton = { TextButton(onClick = { s.answerPromotion(false) }) { Text("Keep as is") } },
        )
    }
}

@Composable
private fun HandRow(hand: IntArray, mine: Boolean, selected: Int, targets: DemoTargets?, onTap: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x33A0743A)).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (mine) "Your hand" else "Computer's hand", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
        for (t in 7 downTo 1) {
            if (hand[t] == 0) continue
            Box(Modifier.size(34.dp, 40.dp).demoTarget(targets, "hand-$t").clickable(enabled = mine) { onTap(t) }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(30.dp, 36.dp)) { shogiPiece(t, mine, center, size.width * 0.9f, size.height * 0.9f, selected == t) }
                if (hand[t] > 1) Text("×${hand[t]}", Modifier.align(Alignment.BottomEnd), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
