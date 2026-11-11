package com.puresudoku.ui.games.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.board.Backgammon
import com.puresudoku.core.board.GoGame
import com.puresudoku.core.board.Mahjong
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.BonusLine
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
import kotlin.random.Random

private fun JSONArray.ints() = IntArray(length()) { getInt(it) }
private fun IntArray.jarr() = JSONArray().also { a -> forEach { a.put(it) } }

// =====================================================================  Go

class GoSession(difficulty: Difficulty, val g: GoGame) : DuelSession(GameKind.GO, difficulty) {
    var ghost by mutableIntStateOf(-1); private set
    var final by mutableStateOf<GoGame.Estimate?>(null); private set

    init { message = "You are Black. Tap twice to place a stone" }
    override val middleStat get() = MiddleStat.Counter("Captures", "${g.captures[1]}–${g.captures[2]}")
    override val wonTitle get() = "You win on the board!"
    override val lostTitle get() = "White wins"
    override val lostMessage get() = "${scoreText()}. This game's save slot has been cleared."
    override val progress: Float get() = (g.moveCount / (g.n * g.n * 1.2f)).coerceAtMost(0.95f)

    private fun scoreText(): String {
        val f = final ?: return ""
        val s = f.score
        return if (s > 0) "Black wins by ${"%.1f".format(s)}" else "White wins by ${"%.1f".format(-s)}"
    }

    fun tap(i: Int) {
        if (outcome != Outcome.PLAYING || final != null) return
        if (thinking || g.turn != 1) { nudge("White is thinking"); return }
        if (g.cells[i] != 0) { nudge("There's already a stone there"); return }
        if (!g.legal(i)) { nudge(if (i == g.ko) "Ko: you can't retake right away, play elsewhere first" else "That would be suicide: no liberties"); return }
        if (!guideAllows(-1, i)) return
        if (ghost != i) { ghost = i; bump(); return }
        ghost = -1
        val caps = g.play(i)!!
        moves++
        message = if (caps.isNotEmpty()) "You captured ${caps.size}" else "White's turn"
        bump(); changed()
        val code = guideDone()
        if (code != null) scriptedReply(code) { c -> if (g.play(c) != null) moves++; message = "Your move"; changed() }
        else reply()
    }

    fun pass() {
        if (outcome != Outcome.PLAYING || final != null || thinking || g.turn != 1) return
        ghost = -1
        g.play(-1); moves++
        message = "You passed"
        bump()
        if (g.over) score() else { changed(); reply() }
    }

    private fun reply() {
        val copy = g.copy(); val lvl = GoGame.level(aiDifficulty); val seed = rng.nextLong()
        computer(minMs = 500, think = { copy.aiMove(lvl, Random(seed)) }) { m ->
            val caps = g.play(m) ?: g.play(-1)!!
            moves++
            announce(when { m < 0 -> "Computer passes"; caps.isNotEmpty() -> "Computer captures ${caps.size} stone${if (caps.size > 1) "s" else ""}"; else -> "Computer places a stone" },
                when { m < 0 -> if (g.over) "Both passed: the game is scored." else "Pass too to end the game, or keep playing."; caps.isNotEmpty() -> "Those stones had no liberties left."; else -> "Row ${m / g.n + 1}, column ${m % g.n + 1}." })
            message = when {
                m < 0 -> if (g.over) "White passes too" else "White passes. Pass to end the game, or keep playing"
                caps.isNotEmpty() -> "White captured ${caps.size}"
                else -> "Your move"
            }
            if (g.over) score() else changed()
        }
    }

    private fun score() {
        thinking = true; bump()
        val copy = g.copy(); val seed = rng.nextLong()
        AiWorker.run(600, { copy.estimate(Random(seed), 200) }) { est ->
            thinking = false
            final = est
            message = scoreText() + " (komi ${g.komi})"
            bump()
            AiWorker.later(2200) {
                if (outcome != Outcome.PLAYING) return@later
                if (est.score > 0) win(listOf(BonusLine("Margin", (est.score * rules.basePoints).toInt()))) else lose()
            }
        }
    }

    override fun resume() {
        if (outcome != Outcome.PLAYING) return
        if (g.over) score() else if (g.turn == 2) reply()
    }

    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (g.turn != 1 || final != null) return null
        val copy = g.copy()
        return {
            val m = copy.aiMove(GoGame.level(Difficulty.HARD), Random(1))
            if (m < 0) Triple("Nothing big is left: you can pass.", -1, -1)
            else Triple("Play on the ringed point (tap it twice).", -1, m)
        }
    }

    override fun saveState(): JSONObject = JSONObject().put("n", g.n).put("cells", g.cells.jarr()).put("turn", g.turn).put("ko", g.ko).put("passes", g.passes)
        .put("mc", g.moveCount).put("caps", g.captures.jarr()).put("last", g.last).put("moves", moves)

    companion object {
        fun restore(o: JSONObject): GoSession {
            val st = o.getJSONObject("state"); val g = GoGame(st.getInt("n"))
            st.getJSONArray("cells").ints().copyInto(g.cells); g.turn = st.getInt("turn"); g.ko = st.getInt("ko"); g.passes = st.getInt("passes")
            g.moveCount = st.getInt("mc"); st.getJSONArray("caps").ints().copyInto(g.captures); g.last = st.getInt("last")
            return GoSession(Difficulty.valueOf(o.getString("difficulty")), g).also { it.moves = st.optInt("moves"); it.restoreCommon(o) }
        }
    }
}

object GoDef : GameDef {
    override val kind = GameKind.GO
    override fun generate(d: Difficulty): Session = GoSession(d, GoGame(GoGame.size(d)))
    override fun restore(o: JSONObject): Session = GoSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Surround empty points and capture stones by taking their last liberty. When neither side can gain, both pass and the board is counted."
    override fun levelInfo(d: Difficulty) = "${GoGame.size(d)} × ${GoGame.size(d)} board · komi 7.5"
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A small board and a gentle opponent."
        Difficulty.MEDIUM -> "Small board, steadier play."
        Difficulty.HARD -> "Small board, a thoughtful opponent."
        Difficulty.EXPERT -> "The 13 × 13 board."
        Difficulty.MASTER -> "13 × 13, stronger reading."
        Difficulty.EXTREME -> "13 × 13, White's strongest play."
    }
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_go
    @Composable override fun Play(session: Session) = GoUi(session as GoSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override val tutorial = listOf(
        TutorialStep("Stones and liberties", "Take turns placing stones on the crossings (tap twice to place). A group's empty neighbouring points are its liberties; fill the last one and the group is captured.") { active ->
            var s by remember { mutableStateOf(demoGo()) }
            DemoStage(active, script = {
                s.cancelThinking(); s = demoGo(); wait(500)
                tap("go-${4 * 9 + 5}") { s.tap(4 * 9 + 5) }; wait(300); tap("go-${4 * 9 + 5}") { s.tap(4 * 9 + 5) }; wait(1800)
            }) { t -> GoUi(s, t) }
        },
        TutorialStep("Territory and passing", "Wall off areas the opponent can't live in. When there's nothing useful left, tap Pass; two passes in a row end the game. Stones plus surrounded points are counted, and White gets 7.5 points of komi.") { active ->
            var s by remember { mutableStateOf(demoGo()) }
            DemoStage(active, script = { s.cancelThinking(); s = demoGo(); wait(400); highlight(listOf("pass"), 1800) }) { t -> GoUi(s, t) }
        },
    )

    private fun demoGo(): GoSession {
        val g = GoGame(9)
        for (m in listOf(3 * 9 + 4, 4 * 9 + 4, 4 * 9 + 3, 6 * 9 + 6, 5 * 9 + 4, 2 * 9 + 6)) g.play(m)
        return GoSession(Difficulty.EASY, g)
    }
}

@Composable
private fun GoUi(s: GoSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val pop = rememberPop(g.last * 10000 + g.moveCount)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreChip("You captured", "${g.captures[1]}", Color(0xFF1D1D22), g.turn == 1)
            ScoreChip("White captured", "${g.captures[2]}", Color(0xFFF2F0EA), g.turn == 2)
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight)
            val n = g.n
            Box(Modifier.size(side)) {
                Canvas(Modifier.size(side).clip(RoundedCornerShape(8.dp)).pointerInput(s) {
                    detectTapGestures { o -> val c = size.width / n.toFloat(); s.tap((o.y / c).toInt().coerceIn(0, n - 1) * n + (o.x / c).toInt().coerceIn(0, n - 1)) }
                }) {
                    wood(Color(0xFFE6B86A), Color(0xFFCF9A4C), Color(0xFF8A5A22), seed = 5)
                    val c = size.width / n
                    val ink = Color(0xFF3A2410)
                    for (k in 0 until n) {
                        drawLine(ink, Offset(c / 2, c / 2 + k * c), Offset(size.width - c / 2, c / 2 + k * c), 1.6f)
                        drawLine(ink, Offset(c / 2 + k * c, c / 2), Offset(c / 2 + k * c, size.height - c / 2), 1.6f)
                    }
                    for (h in GoGame.hoshi(n)) drawCircle(ink, c * 0.09f, Offset(c / 2 + (h % n) * c, c / 2 + (h / n) * c))
                    val fin = s.final
                    for (i in 0 until n * n) {
                        val st = g.cells[i]
                        val o = Offset(c / 2 + (i % n) * c, c / 2 + (i / n) * c)
                        if (st != 0) {
                            val r = c * 0.47f * (if (i == g.last) pop else 1f)
                            val dead = fin != null && i in fin.dead
                            if (st == 1) stone(o, r, Color(0xFF1D1D22).copy(alpha = if (dead) 0.45f else 1f), Color(0xFF6A6A78), Color(0xFF000000))
                            else stone(o, r, Color(0xFFF2F0EA).copy(alpha = if (dead) 0.45f else 1f), Color(0xFFFFFFFF), Color(0xFFBDB8AE))
                            if (i == g.last && fin == null) drawCircle(if (st == 1) Color.White else Color.Black, r * 0.3f, o, style = Stroke(c * 0.06f))
                        }
                        if (fin != null && (st == 0 || i in fin.dead)) {
                            val own = fin.owner[i]
                            if (abs(own) > 120) drawRect(if (own > 0) Color(0xFF1D1D22) else Color(0xFFF7F7F7), o - Offset(c * 0.14f, c * 0.14f), Size(c * 0.28f, c * 0.28f))
                        }
                    }
                    if (s.ghost >= 0) stone(Offset(c / 2 + (s.ghost % n) * c, c / 2 + (s.ghost / n) * c), c * 0.47f, Color(0x991D1D22), Color(0x996A6A78), Color(0x99000000), shadow = false)
                    if (s.coachTo >= 0) coachRings(listOf(Offset(c / 2 + (s.coachTo % n) * c, c / 2 + (s.coachTo / n) * c)), c * 0.5f)
                }
                val cpx = with(LocalDensity.current) { side.toPx() } / n
                CellTargets(targets, (0 until n * n).map { "go-$it" to Rect((it % n) * cpx, (it / n) * cpx, (it % n + 1) * cpx, (it / n + 1) * cpx) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TurnBanner(when {
                s.thinking && s.final == null && g.over -> "Counting…"
                s.thinking -> "White is thinking…"
                s.ghost >= 0 -> "Tap again to place it"
                else -> s.message
            }, s.thinking, Modifier.weight(1f, fill = false))
            OutlinedButton(onClick = s::pass, enabled = !s.thinking && s.final == null, modifier = Modifier.demoTarget(targets, "pass")) { Text("Pass") }
        }
        Spacer(Modifier.height(if (targets == null) 12.dp else 4.dp))
    }
}

// =====================================================================  Backgammon

class BackgammonSession(difficulty: Difficulty, val g: Backgammon) : DuelSession(GameKind.BACKGAMMON, difficulty) {
    var selected by mutableIntStateOf(-1); private set
    private var seqs: List<List<Backgammon.Step>> = emptyList()
    private val played = ArrayList<Backgammon.Step>()
    var lastSteps by mutableStateOf<List<Backgammon.Step>>(emptyList()); private set
    var rollId by mutableIntStateOf(0); private set

    init { message = "Roll the dice to start" }
    override val middleStat get() = MiddleStat.Counter("Pips", "${g.pips(0)}–${g.pips(1)}")
    override val progress: Float get() = (g.off[0] / 15f).coerceAtMost(0.95f)
    override val wonTitle get() = when (g.winPoints()) { 3 -> "Backgammon!"; 2 -> "Gammon!"; else -> "You bore off first!" }
    override val lostTitle get() = "The computer bore off first"

    val needRoll get() = g.turn == 0 && g.dice.isEmpty() && !thinking && outcome == Outcome.PLAYING
    val canUndo get() = played.isNotEmpty() && g.turn == 0

    fun options(from: Int) = seqs.mapNotNull { it.firstOrNull() }.filter { it.from == from }.distinct()
    fun movableFrom() = seqs.mapNotNull { it.firstOrNull()?.from }.toSet()

    fun roll() {
        if (!needRoll) return
        val fixed = guide?.drop(guideAt)?.firstOrNull { !it.info }?.dice
        if (guideStep?.info == true) guideNext()
        if (fixed != null) {
            g.rolled = intArrayOf(fixed[0], fixed[1]); g.dice.clear()
            if (fixed[0] == fixed[1]) repeat(4) { g.dice += fixed[0] } else { g.dice += fixed[0]; g.dice += fixed[1] }
        } else g.roll(rng)
        rollId++
        seqs = g.sequences(dedupe = false); played.clear(); selected = -1
        message = "You rolled ${g.rolled.joinToString(" and ")}"
        bump()
        if (seqs.all { it.isEmpty() }) { message += " · no legal moves"; bump(); AiWorker.later(1200) { endTurn() } }
    }

    fun tapPoint(p: Int) {
        if (outcome != Outcome.PLAYING) return
        if (g.turn != 0 || thinking) { nudge("The computer is playing"); return }
        if (g.dice.isEmpty()) { nudge("Roll the dice first"); return }
        if (selected != -1) {
            val st = options(selected).firstOrNull { it.to == p }
            if (st != null) { step(st); return }
        }
        if (p in movableFrom()) { selected = if (selected == p) -1 else p; bump(); return }
        nudge(if (g.bar[0] > 0) "Bring your checker in from the bar first" else if (selected != -1) "That checker can't land there" else "No legal move from there")
    }

    private var cpuDice: IntArray? = null

    private fun step(st: Backgammon.Step) {
        if (!guideAllows(st.from, st.to)) { selected = -1; bump(); return }
        guideStep?.cpuDice?.let { cpuDice = it }
        guideDone()
        g.apply(st); g.dice.remove(st.die); played += st
        seqs = seqs.filter { it.firstOrNull() == st }.map { it.drop(1) }
        selected = -1; bump()
        if (g.winner == 0) { finishGame(); return }
        if (seqs.all { it.isEmpty() }) AiWorker.later(500) { if (g.turn == 0 && played.isNotEmpty()) endTurn() }
    }

    fun undo() {
        if (!canUndo) return
        val st = played.removeAt(played.lastIndex)
        g.revert(st); g.dice += st.die
        seqs = g.sequences(left = g.dice.toList(), dedupe = false)
        // keep only sequences consistent with what's still played: recompute from the start of the turn
        selected = -1; bump()
    }

    private fun endTurn() {
        if (outcome != Outcome.PLAYING || g.turn != 0) return
        g.dice.clear(); played.clear(); seqs = emptyList(); moves++
        g.turn = 1; changed()
        computerTurn()
    }

    private fun computerTurn() {
        val fixed = cpuDice; cpuDice = null
        if (fixed != null) { g.rolled = intArrayOf(fixed[0], fixed[1]); g.dice.clear(); g.dice += fixed[0]; g.dice += fixed[1] } else g.roll(rng)
        rollId++
        message = "Computer rolled ${g.rolled.joinToString(" and ")}"
        bump()
        val snap = Backgammon(); snap.load(g.state()); val lvl = Backgammon.level(aiDifficulty); val seed = rng.nextLong()
        computer(minMs = 700, think = { snap.aiSequence(lvl, Random(seed)) }) { seq ->
            fun name(p: Int) = when (p) { Backgammon.BAR -> "the bar"; Backgammon.OFF -> "off"; else -> "${p + 1}" }
            announce("Computer rolls ${g.rolled.joinToString(" and ")}",
                if (seq.isEmpty()) "It has no legal move." else "It moves " + seq.joinToString(", ") { "${name(it.from)} → ${name(it.to)}" + if (it.hit) " (hit!)" else "" } + ".", 2200)
            playOut(seq, 0)
        }
    }

    private fun playOut(seq: List<Backgammon.Step>, k: Int) {
        if (outcome != Outcome.PLAYING) return
        if (k >= seq.size) {
            lastSteps = seq
            if (seq.isEmpty()) message += " · no moves"
            g.dice.clear(); g.turn = 0; moves++
            message = (if (seq.isEmpty()) "The computer couldn't move. " else "") + "Your turn: roll"
            bump(); changed(); return
        }
        g.apply(seq[k], 1); g.dice.remove(seq[k].die); lastSteps = seq.take(k + 1); bump()
        if (g.winner == 1) { finishGame(); return }
        AiWorker.later(450) { playOut(seq, k + 1) }
    }

    private fun finishGame() {
        val pts = g.winPoints()
        if (g.winner == 0) win(if (pts > 1) listOf(BonusLine(if (pts == 3) "Backgammon" else "Gammon", rules.basePoints * 10 * (pts - 1))) else emptyList())
        else lose()
    }

    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (g.turn != 0) return null
        if (g.dice.isEmpty()) return { Triple("Roll the dice first.", -1, -1) }
        val snap = Backgammon(); snap.load(g.state())
        return {
            val seq = snap.aiSequence(Backgammon.level(Difficulty.EXTREME), Random(1))
            fun name(p: Int) = when (p) { Backgammon.BAR -> "the bar"; Backgammon.OFF -> "off"; else -> "point ${p + 1}" }
            if (seq.isEmpty()) Triple("No moves this roll.", -1, -1)
            else Triple("Play " + seq.joinToString(", ") { "${name(it.from)} → ${name(it.to)}" + if (it.hit) " (hit!)" else "" } + ".",
                seq[0].from.takeIf { it in 0..23 } ?: -1, seq[0].to.takeIf { it in 0..23 } ?: -1)
        }
    }

    override fun resume() {
        if (outcome != Outcome.PLAYING) return
        if (g.turn == 1) computerTurn()
        else if (g.dice.isNotEmpty()) { seqs = g.sequences(dedupe = false); if (seqs.all { it.isEmpty() }) AiWorker.later(800) { endTurn() } }
    }

    override fun saveState(): JSONObject = JSONObject().put("s", g.state().jarr()).put("moves", moves)

    companion object {
        fun restore(o: JSONObject): BackgammonSession {
            val g = Backgammon(); g.load(o.getJSONObject("state").getJSONArray("s").ints())
            return BackgammonSession(Difficulty.valueOf(o.getString("difficulty")), g).also { it.moves = o.getJSONObject("state").optInt("moves"); it.restoreCommon(o) }
        }
    }
}

object BackgammonDef : GameDef {
    override val kind = GameKind.BACKGAMMON
    override fun generate(d: Difficulty): Session = BackgammonSession(d, Backgammon())
    override fun restore(o: JSONObject): Session = BackgammonSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Roll, then move your white checkers toward your home (bottom right) by the dice. Hit lone black checkers; bear all 15 off to win."
    override fun levelInfo(d: Difficulty) = "One game · gammons count double"
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A loose, random-ish opponent."
        Difficulty.MEDIUM -> "Knows the basics."
        Difficulty.HARD -> "Makes points and hits blots."
        Difficulty.EXPERT -> "Plays solid positional backgammon."
        Difficulty.MASTER -> "Careful and safe."
        Difficulty.EXTREME -> "Also weighs your return shots."
    }
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_backgammon
    @Composable override fun Play(session: Session) = BackgammonUi(session as BackgammonSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override val tutorial = listOf(
        TutorialStep("Roll and move", "Tap Roll. Then tap one of your white checkers and a highlighted point: each die moves one checker that many points, around the board toward your home at the bottom right. Doubles play four times.") { active ->
            var s by remember { mutableStateOf(BackgammonSession(Difficulty.EASY, Backgammon())) }
            DemoStage(active, script = {
                s.cancelThinking(); s = BackgammonSession(Difficulty.EASY, Backgammon()); wait(500)
                tap("roll") { s.roll() }; wait(600)
                val p = s.movableFrom().maxOrNull() ?: 23
                tap("bg-$p") { s.tapPoint(p) }; wait(500)
                s.options(p).firstOrNull()?.let { st -> tap("bg-${st.to}") { s.tapPoint(st.to) } }; wait(1500)
            }) { t -> BackgammonUi(s, t) }
        },
        TutorialStep("Hit, enter, bear off", "Land on a point holding one enemy checker to send it to the bar; it must re-enter before moving anything else. Points with two or more checkers are blocked. Once all 15 of yours are home, bear them off. First to bear off all wins.") { active ->
            var s by remember { mutableStateOf(BackgammonSession(Difficulty.EASY, Backgammon())) }
            DemoStage(active, script = { s.cancelThinking(); s = BackgammonSession(Difficulty.EASY, Backgammon()); wait(400); highlight(listOf("home"), 1800) }) { t -> BackgammonUi(s, t) }
        },
    )
}

/** Board geometry: left column holds points 12..23 top to bottom, right column 11..0 top to bottom. */
private class BgGeo(val w: Float, val h: Float) {
    val m = w * 0.03f
    val colW = (w - m * 2) * 0.38f
    val barH = h * 0.08f
    val ptH = (h - m * 2 - barH) / 12f
    val offW = w * 0.14f
    fun pointRect(i: Int): Rect {
        val left = i >= 12
        val k = if (left) i - 12 else 11 - i
        val top = m + k * ptH + (if (k >= 6) barH else 0f)
        val x = if (left) m else w - m - offW - colW
        return Rect(x, top, x + colW, top + ptH)
    }
    val barRect get() = Rect(m, m + 6 * ptH, w - m - offW, m + 6 * ptH + barH)
    val offRect get() = Rect(w - m - offW + 4f, h / 2, w - m, h - m)
    val cpuOffRect get() = Rect(w - m - offW + 4f, m, w - m, h / 2)
}

private fun DrawScope.die(c: Offset, s: Float, v: Int, used: Boolean, dark: Boolean) {
    drawRoundRect(if (dark) Color(0xFF2B2B33) else Color(0xFFFFFBF0), c - Offset(s / 2, s / 2), Size(s, s), CornerRadius(s * 0.2f), alpha = if (used) 0.4f else 1f)
    val pip = if (dark) Color.White else Color(0xFF1D1D22)
    val q = s * 0.26f
    val pos = when (v) { 1 -> listOf(0 to 0); 2 -> listOf(-1 to -1, 1 to 1); 3 -> listOf(-1 to -1, 0 to 0, 1 to 1); 4 -> listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)
        5 -> listOf(-1 to -1, 1 to -1, 0 to 0, -1 to 1, 1 to 1); else -> listOf(-1 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 1 to 1) }
    for ((a, b) in pos) drawCircle(pip.copy(alpha = if (used) 0.4f else 1f), s * 0.09f, c + Offset(a * q, b * q))
}

@Composable
private fun BackgammonUi(s: BackgammonSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val shake = remember(s.rollId) { Animatable(0f) }
    LaunchedEffect(s.rollId) { shake.animateTo(0f, keyframes { durationMillis = 420; 18f at 60; -14f at 140; 9f at 220; -4f at 320 }) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val w = min(maxWidth, maxHeight * 0.62f); val h = w / 0.62f
            val sel = s.selected
            val dests = if (sel != -1) s.options(sel).map { it.to }.toSet() else emptySet()
            val movable = if (g.turn == 0 && g.dice.isNotEmpty()) s.movableFrom() else emptySet()
            Box(Modifier.size(w, h)) {
                Canvas(Modifier.size(w, h).clip(RoundedCornerShape(10.dp)).pointerInput(s) {
                    detectTapGestures { o ->
                        val geo = BgGeo(size.width.toFloat(), size.height.toFloat())
                        when {
                            geo.offRect.contains(o) -> s.tapPoint(Backgammon.OFF)
                            geo.barRect.contains(o) -> s.tapPoint(Backgammon.BAR)
                            else -> (0..23).firstOrNull { geo.pointRect(it).contains(o) }?.let { s.tapPoint(it) }
                        }
                    }
                }) {
                    val geo = BgGeo(size.width, size.height)
                    drawRect(Brush.verticalGradient(listOf(Color(0xFF5D3A1E), Color(0xFF3E2512))))
                    drawRect(Color(0xFF1F5B3A), Offset(geo.m, geo.m), Size(size.width - geo.m * 2 - geo.offW, size.height - geo.m * 2))
                    for (i in 0..23) {
                        val r = geo.pointRect(i); val left = i >= 12
                        val tri = Path().apply {
                            if (left) { moveTo(r.left, r.top + 2f); lineTo(r.right, r.center.y); lineTo(r.left, r.bottom - 2f) }
                            else { moveTo(r.right, r.top + 2f); lineTo(r.left, r.center.y); lineTo(r.right, r.bottom - 2f) }
                            close()
                        }
                        drawPath(tri, if (i % 2 == 0) Color(0xFFE8D2A6) else Color(0xFFB0412E))
                        if (i in dests) drawPath(tri, Color(0x8864B5F6))
                        if (i == sel) drawPath(tri, Color(0xFF64B5F6), style = Stroke(4f))
                        val n = abs(g.pts[i]); if (n == 0) continue
                        val white = g.pts[i] > 0
                        val cr = minOf(geo.ptH * 0.46f, r.width / 12f)
                        for (k in 0 until n) {
                            val slot = minOf(k, 5)
                            val x = if (left) r.left + cr + slot * cr * 1.9f else r.right - cr - slot * cr * 1.9f
                            val o = Offset(x, r.center.y)
                            if (white) stone(o, cr, Color(0xFFF4EBD6), Color.White, Color(0xFFC9B892)) else stone(o, cr, Color(0xFF2B2B33), Color(0xFF6A6A78), Color(0xFF0E0E12))
                            if (k == n - 1 && i in movable) drawCircle(Color(0xFF64B5F6), cr * 1.08f, o, style = Stroke(3f))
                        }
                        if (n > 6) glyph("$n", Offset(if (left) r.left + cr + 5 * cr * 1.9f else r.right - cr - 5 * cr * 1.9f, r.center.y), cr * 1.1f, if (white) Color.Black else Color.White)
                    }
                    // bar
                    val bar = geo.barRect
                    drawRect(Color(0xFF4A2C17), bar.topLeft, bar.size)
                    val cr = geo.ptH * 0.4f
                    for (k in 0 until g.bar[0]) stone(Offset(bar.left + cr * 1.2f + k * cr * 1.6f, bar.center.y), cr, Color(0xFFF4EBD6), Color.White, Color(0xFFC9B892))
                    for (k in 0 until g.bar[1]) stone(Offset(bar.right - cr * 1.2f - k * cr * 1.6f, bar.center.y), cr, Color(0xFF2B2B33), Color(0xFF6A6A78), Color(0xFF0E0E12))
                    if (Backgammon.BAR in movable) drawRect(Color(0xFF64B5F6), bar.topLeft, bar.size, style = Stroke(4f))
                    // dice in the middle of the bar
                    if (g.rolled.isNotEmpty()) {
                        val ds = geo.barH * 0.8f
                        val used = g.dice.size
                        val dark = g.turn == 1
                        val c0 = Offset(bar.center.x - ds * 0.65f + shake.value, bar.center.y)
                        val doubles = g.rolled[0] == g.rolled[1]
                        die(c0, ds, g.rolled[0], if (doubles) used < 3 else used == 0 || (used == 1 && g.dice[0] != g.rolled[0]), dark)
                        die(Offset(bar.center.x + ds * 0.65f - shake.value, bar.center.y), ds, g.rolled[1], if (doubles) used < 1 else used == 0 || (used == 1 && g.dice[0] != g.rolled[1]), dark)
                    }
                    // trays
                    for ((rect, count, white) in listOf(Triple(geo.offRect, g.off[0], true), Triple(geo.cpuOffRect, g.off[1], false))) {
                        drawRect(Color(0xFF2E1A0C), rect.topLeft, rect.size)
                        for (k in 0 until count) {
                            val y = if (white) rect.bottom - 4f - k * (rect.height - 8f) / 15f else rect.top + 4f + k * (rect.height - 8f) / 15f
                            drawRect(if (white) Color(0xFFF4EBD6) else Color(0xFF2B2B33), Offset(rect.left + 3f, y - (rect.height / 15f) * 0.42f), Size(rect.width - 6f, (rect.height / 15f) * 0.8f))
                        }
                    }
                    if (Backgammon.OFF in dests) drawRect(Color(0xFF64B5F6), geo.offRect.topLeft, geo.offRect.size, style = Stroke(5f))
                    coachRings(listOf(s.coachFrom, s.coachTo).filter { it in 0..23 }.map { geo.pointRect(it).center }, geo.ptH * 0.5f)
                }
                val d = LocalDensity.current
                val geo = with(d) { BgGeo(w.toPx(), h.toPx()) }
                CellTargets(targets, (0..23).map { "bg-$it" to geo.pointRect(it) } + listOf("home" to Rect(geo.pointRect(5).left, geo.pointRect(5).top, geo.pointRect(0).right, geo.pointRect(0).bottom)))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TurnBanner(if (s.thinking) "Computer is thinking…" else s.message, s.thinking, Modifier.weight(1f, fill = false))
            if (s.needRoll) Button(onClick = s::roll, modifier = Modifier.demoTarget(targets, "roll")) { Text("Roll") }
            else if (s.canUndo) OutlinedButton(onClick = s::undo) { Text("Undo") }
        }
        Spacer(Modifier.height(if (targets == null) 12.dp else 4.dp))
    }
}

// =====================================================================  Mahjong solitaire

class MahjongSession(difficulty: Difficulty, val g: Mahjong) : Session(GameKind.MAHJONG, difficulty) {
    val cfg = Mahjong.config(difficulty)
    private val rules = com.puresudoku.core.ScoreRules(difficulty.basePoints * 2, difficulty.parSeconds)
    private val rng = Random(System.nanoTime())
    var version by mutableIntStateOf(0); private set
    var selected by mutableIntStateOf(-1); private set
    var shuffles by mutableIntStateOf(cfg.shuffles)
    var hints by mutableIntStateOf(cfg.hints)
    var hinted by mutableStateOf<Pair<Int, Int>?>(null); private set
    var shakeId by mutableStateOf<Pair<Int, Long>?>(null); private set
    var message by mutableStateOf(""); private set

    override val usesStrikes get() = false
    override val middleStat get() = MiddleStat.Counter("Tiles", "${g.remaining.size}")
    override val progress: Float get() = 1f - g.remaining.size / g.tiles.size.toFloat()
    override val wonTitle get() = "Cleared!"
    override val lostTitle get() = "No more moves"
    override val lostMessage get() = "${g.remaining.size} tiles left and no shuffles. This game's save slot has been cleared."

    fun tap(id: Int) {
        if (outcome != Outcome.PLAYING) return
        val t = g.tiles[id]; if (t.removed) return
        if (!g.isFree(t)) { shakeId = id to System.nanoTime(); nudge("That tile is blocked: it needs a free left or right side and nothing on top"); return }
        hinted = null
        when {
            selected == id -> selected = -1
            selected >= 0 && Mahjong.matches(g.tiles[selected].face, t.face) -> {
                g.remove(g.tiles[selected], t); selected = -1
                score += rules.basePoints
                message = ""
                after()
            }
            selected >= 0 -> { shakeId = id to System.nanoTime(); selected = id; message = "Not a match" }
            else -> selected = id
        }
        version++
    }

    private fun after() {
        version++
        if (g.cleared) { finish(true, listOf(BonusLine("Unused shuffles", shuffles.coerceAtMost(5) * rules.basePoints * 5), BonusLine("Time bonus", com.puresudoku.core.Scoring.finish(rules, elapsedMs, 0).timeBonus))); return }
        if (g.stuck) {
            if (shuffles > 0) message = "No pairs left: tap Shuffle"
            else { finish(false); return }
        }
        changed()
    }

    fun hint() {
        if (outcome != Outcome.PLAYING) return
        if (hints <= 0) { nudge("No hints left on this level"); return }
        val p = g.hintPair() ?: run { nudge("No pairs: shuffle instead"); return }
        hints--; hinted = p.first.id to p.second.id; selected = -1; version++; changed()
    }

    fun shuffle() {
        if (outcome != Outcome.PLAYING) return
        if (shuffles <= 0) { nudge("No shuffles left on this level"); return }
        shuffles--; g.shuffle(rng); selected = -1; hinted = null; message = "Shuffled"; version++
        after()
    }

    override fun saveState(): JSONObject = JSONObject().put("faces", g.tiles.map { it.face }.toIntArray().jarr())
        .put("removed", g.tiles.map { if (it.removed) 1 else 0 }.toIntArray().jarr()).put("shuffles", shuffles).put("hints", hints)

    companion object {
        fun restore(o: JSONObject): MahjongSession {
            val d = Difficulty.valueOf(o.getString("difficulty")); val st = o.getJSONObject("state")
            val g = Mahjong.newGame(d, Random(1))
            val faces = st.getJSONArray("faces").ints(); val rem = st.getJSONArray("removed").ints()
            g.tiles.forEachIndexed { k, t -> t.face = faces[k]; t.removed = rem[k] == 1 }
            return MahjongSession(d, g).also { it.shuffles = st.getInt("shuffles"); it.hints = st.getInt("hints"); it.restoreCommon(o) }
        }
    }
}

object MahjongDef : GameDef {
    override fun lesson(): Pair<Session, List<com.puresudoku.ui.session.LessonStep>> {
        val g = Mahjong.newGame(Difficulty.EASY, Random(20261110))
        val (a, b) = g.hintPair()!!
        fun face(f: Int) = when (f) {
            in 0..8 -> "${f + 1} of dots"; in 9..17 -> "${f - 8} of bamboo"; in 18..26 -> "${f - 17} of characters"
            in 27..30 -> listOf("East", "South", "West", "North")[f - 27] + " wind"; in 31..33 -> listOf("red", "green", "white")[f - 31] + " dragon"
            in 34..37 -> "flower"; else -> "season"
        }
        val total = g.tiles.size
        return MahjongSession(Difficulty.EASY, g) to listOf(
            com.puresudoku.ui.session.LessonStep("Clear every tile by removing matching pairs. You can only take a free tile: nothing on top of it, and its left or right side open."),
            com.puresudoku.ui.session.LessonStep("Look for the two ${face(a.face)} tiles. Both are free right now. Tap one, then the other.") { (it as MahjongSession).g.tiles[a.id].removed && it.g.tiles[b.id].removed },
            com.puresudoku.ui.session.LessonStep("Tiles in the middle of a row are blocked on both sides, and tiles under others are hidden. Clear from the top and the ends inward. Stuck? Tap Hint to see a pair.") { (it as MahjongSession).g.remaining.size <= total - 4 || it.outcome != com.puresudoku.ui.session.Outcome.PLAYING },
            com.puresudoku.ui.session.LessonStep("Any flower matches any flower, and any season matches any season. When three of a face are free, choose the pair that frees the most tiles. Shuffle if no pairs are left. Clear the board to win!"),
        )
    }
    override val kind = GameKind.MAHJONG
    override fun generate(d: Difficulty): Session = MahjongSession(d, Mahjong.newGame(d, Random(System.nanoTime())))
    override fun restore(o: JSONObject): Session = MahjongSession.restore(o)
    override fun hint(d: Difficulty) = "Tap two matching free tiles to clear them. A tile is free with nothing on top and an open left or right side."
    override fun levelInfo(d: Difficulty) = Mahjong.config(d).let { "${it.name} layout · ${if (it.shuffles >= 99) "unlimited" else it.shuffles.toString()} shuffles" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A small pagoda with endless help."
        Difficulty.MEDIUM -> "Two layers, a few hints."
        Difficulty.HARD -> "Three layers, 108 tiles."
        Difficulty.EXPERT -> "Four layers, one hint."
        Difficulty.MASTER -> "136 tiles, one shuffle."
        Difficulty.EXTREME -> "The full 144 with no help at all."
    }
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_mahjong
    @Composable override fun Play(session: Session) = MahjongUi(session as MahjongSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override val tutorial = listOf(
        TutorialStep("Match free pairs", "Tap two identical tiles to remove them. Only free tiles can be picked: nothing lying on top, and an open left or right side. Any flower matches any flower; any season matches any season.") { active ->
            var s by remember { mutableStateOf(MahjongSession(Difficulty.EASY, Mahjong.newGame(Difficulty.EASY, Random(4)))) }
            DemoStage(active, script = {
                s = MahjongSession(Difficulty.EASY, Mahjong.newGame(Difficulty.EASY, Random(4))); wait(500)
                val p = s.g.hintPair()
                if (p != null) { tap("mj-${p.first.id}") { s.tap(p.first.id) }; wait(300); tap("mj-${p.second.id}") { s.tap(p.second.id) } }
                wait(1500)
            }) { t -> MahjongUi(s, t) }
        },
        TutorialStep("Hints and shuffles", "Stuck? Hint lights up a pair; Shuffle re-deals the tiles that are left. Each level allows a different number. Clear every tile to win.") { active ->
            var s by remember { mutableStateOf(MahjongSession(Difficulty.EASY, Mahjong.newGame(Difficulty.EASY, Random(4)))) }
            DemoStage(active, script = {
                s = MahjongSession(Difficulty.EASY, Mahjong.newGame(Difficulty.EASY, Random(4))); wait(400)
                tap("hint") { s.hint() }; wait(1600)
            }) { t -> MahjongUi(s, t) }
        },
    )
}

private val NUMS = arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九")
private val WINDS = arrayOf("東", "南", "西", "北")
private val FLOWERS = arrayOf("梅", "蘭", "菊", "竹")
private val SEASONS = arrayOf("春", "夏", "秋", "冬")

/** Draws one tile face inside [r]. */
fun DrawScope.mahjongFace(face: Int, r: Rect) {
    val w = r.width; val h = r.height; val c = r.center
    when {
        face <= 8 -> { // dots
            val n = face + 1
            val cols = listOf(Color(0xFF1565C0), Color(0xFFC62828), Color(0xFF2E7D32))
            val pos = dotLayout(n)
            val rad = w * (if (n == 1) 0.3f else if (n <= 4) 0.17f else 0.12f)
            pos.forEachIndexed { k, (x, y) ->
                val o = Offset(r.left + x * w, r.top + y * h)
                drawCircle(cols[k % 3], rad, o); drawCircle(Color.White, rad * 0.55f, o); drawCircle(cols[(k + 1) % 3], rad * 0.35f, o)
            }
        }
        face <= 17 -> { // bamboo
            val n = face - 8
            if (n == 1) {
                drawCircle(Color(0xFF2E7D32), w * 0.18f, c - Offset(0f, h * 0.05f)); drawCircle(Color(0xFFC62828), w * 0.08f, c - Offset(w * 0.04f, h * 0.1f))
                drawLine(Color(0xFF2E7D32), c + Offset(0f, h * 0.1f), c + Offset(0f, h * 0.32f), w * 0.07f)
            } else {
                val pos = dotLayout(n)
                for ((k, p) in pos.withIndex()) {
                    val o = Offset(r.left + p.first * w, r.top + p.second * h)
                    val col = if (n >= 5 && k == pos.size / 2) Color(0xFFC62828) else Color(0xFF2E7D32)
                    drawRoundRect(col, o - Offset(w * 0.05f, h * 0.11f), Size(w * 0.1f, h * 0.22f), CornerRadius(w * 0.04f))
                    drawLine(Color.White.copy(alpha = 0.7f), o - Offset(w * 0.05f, 0f), o + Offset(w * 0.05f, 0f), 1.5f)
                }
            }
        }
        face <= 26 -> { // characters
            glyph(NUMS[face - 18], c - Offset(0f, h * 0.17f), h * 0.36f, Color(0xFF1D1D22))
            glyph("萬", c + Offset(0f, h * 0.2f), h * 0.34f, Color(0xFFC62828))
        }
        face <= 30 -> glyph(WINDS[face - 27], c, h * 0.52f, Color(0xFF1A237E))
        face == 31 -> glyph("中", c, h * 0.55f, Color(0xFFC62828))
        face == 32 -> glyph("發", c, h * 0.5f, Color(0xFF2E7D32))
        face == 33 -> drawRoundRect(Color(0xFF1565C0), Offset(r.left + w * 0.2f, r.top + h * 0.2f), Size(w * 0.6f, h * 0.6f), CornerRadius(w * 0.08f), style = Stroke(w * 0.07f))
        face <= 37 -> { drawCircle(Color(0x33E91E63), w * 0.36f, c); glyph(FLOWERS[face - 34], c, h * 0.48f, Color(0xFFAD1457)) }
        else -> { drawCircle(Color(0x3343A047), w * 0.36f, c); glyph(SEASONS[face - 38], c, h * 0.48f, Color(0xFF2E7D32)) }
    }
}

private fun dotLayout(n: Int): List<Pair<Float, Float>> = when (n) {
    1 -> listOf(0.5f to 0.5f)
    2 -> listOf(0.5f to 0.3f, 0.5f to 0.7f)
    3 -> listOf(0.28f to 0.25f, 0.5f to 0.5f, 0.72f to 0.75f)
    4 -> listOf(0.3f to 0.3f, 0.7f to 0.3f, 0.3f to 0.7f, 0.7f to 0.7f)
    5 -> listOf(0.28f to 0.25f, 0.72f to 0.25f, 0.5f to 0.5f, 0.28f to 0.75f, 0.72f to 0.75f)
    6 -> listOf(0.3f to 0.22f, 0.7f to 0.22f, 0.3f to 0.5f, 0.7f to 0.5f, 0.3f to 0.78f, 0.7f to 0.78f)
    7 -> listOf(0.25f to 0.18f, 0.5f to 0.25f, 0.75f to 0.32f, 0.3f to 0.6f, 0.7f to 0.6f, 0.3f to 0.82f, 0.7f to 0.82f)
    8 -> listOf(0.3f to 0.18f, 0.7f to 0.18f, 0.3f to 0.39f, 0.7f to 0.39f, 0.3f to 0.61f, 0.7f to 0.61f, 0.3f to 0.82f, 0.7f to 0.82f)
    else -> listOf(0.22f to 0.2f, 0.5f to 0.2f, 0.78f to 0.2f, 0.22f to 0.5f, 0.5f to 0.5f, 0.78f to 0.5f, 0.22f to 0.8f, 0.5f to 0.8f, 0.78f to 0.8f)
}

@Composable
private fun MahjongUi(s: MahjongSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val shake = remember(s.shakeId) { Animatable(0f) }
    LaunchedEffect(s.shakeId) { if (s.shakeId != null) shake.animateTo(0f, keyframes { durationMillis = 360; -8f at 50; 7f at 110; -5f at 170; 2f at 240 }) }
    val order = remember(g) { g.tiles.sortedWith(compareBy({ it.z }, { it.x - it.y }, { it.y })) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val maxZ = g.tiles.maxOf { it.z }
            val unitW = min(maxWidth / (g.width + 1.2f), maxHeight / ((g.height) * 1.3f + 1.6f))
            val unitH = unitW * 1.3f
            val depth = unitW * 0.32f
            val boxW = unitW * g.width + depth * (maxZ + 1); val boxH = unitH * g.height + depth * (maxZ + 1)
            Box(Modifier.size(boxW, boxH)) {
                Canvas(Modifier.size(boxW, boxH).pointerInput(s) {
                    detectTapGestures { o ->
                        val uw = size.width / (g.width + (maxZ + 1) * 0.32f)
                        val uh = uw * 1.3f; val dp = uw * 0.32f
                        val hit = order.asReversed().firstOrNull { t ->
                            if (t.removed) return@firstOrNull false
                            val x = t.x * uw + (maxZ + 1 - t.z) * dp; val y = t.y * uh + (maxZ + 1 - t.z) * dp - dp
                            o.x in x..(x + 2 * uw) && o.y in y..(y + 2 * uh)
                        }
                        if (hit != null) s.tap(hit.id)
                    }
                }) {
                    val uw = size.width / (g.width + (maxZ + 1) * 0.32f)
                    val uh = uw * 1.3f; val dp = uw * 0.32f
                    for (t in order) {
                        if (t.removed) continue
                        var x = t.x * uw + (maxZ + 1 - t.z) * dp; val y = t.y * uh + (maxZ + 1 - t.z) * dp - dp
                        if (s.shakeId?.first == t.id) x += shake.value
                        val tw = 2 * uw; val th = 2 * uh
                        val free = g.isFree(t)
                        // body and edge
                        drawRoundRect(Color(0xFF1E6B4A), Offset(x - dp * 0.15f, y + dp * 0.55f), Size(tw, th), CornerRadius(uw * 0.25f))
                        drawRoundRect(Color(0xFFE9DFC6), Offset(x - dp * 0.08f, y + dp * 0.28f), Size(tw, th), CornerRadius(uw * 0.25f))
                        val face = Rect(x, y, x + tw - uw * 0.06f, y + th - uw * 0.06f)
                        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFFDF6), Color(0xFFF1E9D6)), face.top, face.bottom), face.topLeft, face.size, CornerRadius(uw * 0.22f))
                        mahjongFace(t.face, Rect(face.left + uw * 0.12f, face.top + uh * 0.1f, face.right - uw * 0.12f, face.bottom - uh * 0.1f))
                        val hl = s.selected == t.id || s.hinted?.let { it.first == t.id || it.second == t.id } == true
                        if (hl) drawRoundRect(if (s.selected == t.id) Color(0x6664B5F6) else Color(0x66FFD54F), face.topLeft, face.size, CornerRadius(uw * 0.22f))
                        if (!free) drawRoundRect(Color(0x26000000), face.topLeft, face.size, CornerRadius(uw * 0.22f))
                        drawRoundRect(Color(0x55000000), face.topLeft, face.size, CornerRadius(uw * 0.22f), style = Stroke(1.2f))
                    }
                }
                if (targets != null) {
                    val d = LocalDensity.current
                    val (uw, uh, dp) = with(d) { Triple(unitW.toPx(), unitH.toPx(), depth.toPx()) }
                    CellTargets(targets, g.tiles.filter { !it.removed }.map { t ->
                        val x = t.x * uw + (maxZ + 1 - t.z) * dp; val y = t.y * uh + (maxZ + 1 - t.z) * dp - dp
                        "mj-${t.id}" to Rect(x, y, x + 2 * uw, y + 2 * uh)
                    })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = s::hint, enabled = s.hints > 0, modifier = Modifier.demoTarget(targets, "hint")) { Text("Hint" + if (s.hints in 1..98) " (${s.hints})" else "") }
            OutlinedButton(onClick = s::shuffle, enabled = s.shuffles > 0) { Text("Shuffle" + if (s.shuffles in 1..98) " (${s.shuffles})" else "") }
        }
        if (s.message.isNotEmpty()) { Spacer(Modifier.height(6.dp)); TurnBanner(s.message, false) }
        Spacer(Modifier.height(if (targets == null) 12.dp else 4.dp))
    }
}
