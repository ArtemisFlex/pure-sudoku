package com.puresudoku.ui.games.board

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.ScoreRules
import com.puresudoku.core.Scoring
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.MiddleStat
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import java.util.concurrent.Executors
import kotlin.random.Random

/** One background thread for every computer opponent; results come back on the main thread. */
object AiWorker {
    private val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "board-ai").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
    private val main = Handler(Looper.getMainLooper())

    /** Runs [work] off the main thread, then hands its result to [done] no sooner than [minMs] after starting. */
    fun <T> run(minMs: Long = 450, work: () -> T, done: (T) -> Unit) {
        val start = System.currentTimeMillis()
        pool.execute {
            val r = runCatching(work)
            val wait = (minMs - (System.currentTimeMillis() - start)).coerceAtLeast(0)
            main.postDelayed({ r.onSuccess(done) }, wait)
        }
    }

    fun later(ms: Long, block: () -> Unit) { main.postDelayed(block, ms) }
}

/**
 * Shared bits of every two-player board game: the computer's turn, scoring, draws and results.
 * Subclasses own their engine and call [afterMove] when a move lands.
 */
abstract class DuelSession(kind: GameKind, difficulty: Difficulty) : Session(kind, difficulty) {
    var version by mutableIntStateOf(0); private set
    var thinking by mutableStateOf(false); protected set
    var message by mutableStateOf("")
    protected var rng = Random(System.nanoTime())
    protected val rules = ScoreRules(difficulty.basePoints * 20, difficulty.parSeconds)
    /** Bumped whenever the session is replaced, so stale AI replies are dropped. */
    private var generation = 0
    var moves by mutableIntStateOf(0)

    override val usesStrikes get() = false
    override val middleStat: MiddleStat get() = MiddleStat.Counter("Moves", "$moves")
    override val progress: Float get() = (moves / 80f).coerceAtMost(0.95f)
    override val wonTitle get() = "You win!"
    override val lostTitle get() = "Computer wins"
    override val lostMessage get() = "Better luck next game. This game's save slot has been cleared."

    protected fun bump() { version++ }

    /** In training the computer plays a level gentler and the same way every time. */
    override var training: Boolean = false
        set(v) { field = v; if (v) rng = Random(20261110) }
    val aiDifficulty: Difficulty get() = if (training) Difficulty.entries[maxOf(0, difficulty.ordinal - 1)] else difficulty

    /** Coach's suggested move, as words plus squares to ring on the board (-1 = none). */
    var coachTip by mutableStateOf<String?>(null)
    var coachFrom by mutableIntStateOf(-1)
    var coachTo by mutableIntStateOf(-1)

    /** Background work that returns (text, from, to) for the coach, or null if this game can't suggest. */
    open fun coachWork(): (() -> Triple<String, Int, Int>?)? = null

    fun askCoach() {
        if (thinking || outcome != Outcome.PLAYING) { coachTip = "Wait for your turn"; return }
        val w = coachWork() ?: run { coachTip = null; return }
        coachTip = "Coach is looking…"; coachFrom = -1; coachTo = -1
        AiWorker.run(250, w) { r ->
            coachTip = r?.first ?: "No good suggestion right now"
            coachFrom = r?.second ?: -1; coachTo = r?.third ?: -1
            bump()
        }
    }

    protected fun clearCoach() { coachFrom = -1; coachTo = -1 }

    // ---------------- scripted lessons ----------------

    var guide: List<com.puresudoku.core.board.Step>? = null
        private set
    var guideAt by mutableIntStateOf(0)
        private set
    val guideStep: com.puresudoku.core.board.Step? get() = guide?.getOrNull(guideAt)

    /** Starts a scripted lesson; the board rings show each step's move. */
    fun startGuide(steps: List<com.puresudoku.core.board.Step>) { guide = steps; guideAt = 0; syncGuide() }

    private fun syncGuide() {
        val st = guideStep
        coachFrom = if (st != null && !st.info) st.from else -1
        coachTo = if (st != null && !st.info) st.to else -1
        coachTip = null
        bump()
    }

    /** Next on an info step. */
    fun guideNext() { if (guideStep?.info == true) { guideAt++; onGuideInfoDone(); syncGuide() } }

    /** Hook for games that need to act when an info step is passed (e.g. backgammon rolls). */
    protected open fun onGuideInfoDone() {}

    /** True if this move is the one the lesson asks for (or no lesson is running). */
    protected fun guideAllows(from: Int, to: Int): Boolean {
        val st = guideStep ?: return true
        if (st.info) { nudge("Read the coach, then tap Next"); return false }
        if ((st.from < 0 || st.from == from) && (st.to < 0 || st.to == to)) return true
        nudge("Follow the lesson: make the ringed move")
        return false
    }

    /** Call once the player's lesson move has landed. Returns the opponent's scripted reply code, if any. */
    protected fun guideDone(): Int? {
        val st = guideStep ?: return null
        if (st.info) return null
        guideAt++
        syncGuide()
        return st.reply
    }

    /** Plays a scripted reply after a short pause, like the computer thinking. */
    protected fun scriptedReply(code: Int, apply: (Int) -> Unit) {
        thinking = true; bump()
        AiWorker.later(800) {
            if (outcome != Outcome.PLAYING) return@later
            thinking = false
            apply(code)
            if (!heraldBusy) announce("The computer replies", "Its move is part of the lesson: look at what changed on the board.")
            bump()
        }
    }

    /** The computer thinks in the background, then [apply] runs on the main thread. */
    protected fun <T> computer(minMs: Long = 450, think: () -> T, apply: (T) -> Unit) {
        if (outcome != Outcome.PLAYING) return
        thinking = true; bump()
        val gen = generation
        AiWorker.run(minMs, think) { r ->
            if (gen != generation || outcome != Outcome.PLAYING) return@run
            thinking = false
            apply(r)
            bump()
        }
    }

    fun cancelThinking() { generation++; thinking = false }

    protected fun win(extra: List<BonusLine> = emptyList()) {
        thinking = false
        score += rules.basePoints * 10
        finish(true, extra + BonusLine("Time bonus", Scoring.finish(rules, elapsedMs, 0).timeBonus))
    }

    /** Losing and drawing wait until the computer's last move has been shown. */
    protected fun lose() { thinking = false; whenQuiet { if (outcome == Outcome.PLAYING) finish(false) } }
    protected fun draw() { thinking = false; whenQuiet { if (outcome == Outcome.PLAYING) { score += rules.basePoints * 3; finishDraw() } } }

    /** Called once after restoring, to restart the computer's turn if it was thinking. */
    abstract fun resume()
}

// ---------------------------------------------------------------- shared visuals

/** Status pill: whose turn it is, or what just happened. */
@Composable
fun TurnBanner(text: String, thinking: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thinking) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

/** A glossy stone or disc: radial gradient with a highlight. */
fun DrawScope.stone(center: Offset, r: Float, base: Color, light: Color, dark: Color, shadow: Boolean = true) {
    if (shadow) drawCircle(Color.Black.copy(alpha = 0.28f), r, center + Offset(r * 0.08f, r * 0.14f))
    drawCircle(Brush.radialGradient(listOf(light, base, dark), center = center - Offset(r * 0.35f, r * 0.4f), radius = r * 1.6f), r, center)
}

/** Wood grain fill for boards. */
fun DrawScope.wood(top: Color, bottom: Color, grain: Color, seed: Int = 3) {
    drawRect(Brush.verticalGradient(listOf(top, bottom)))
    val rng = Random(seed)
    var y = 0f
    while (y < size.height) {
        val h = rng.nextFloat() * 6f + 2f
        drawRect(grain.copy(alpha = rng.nextFloat() * 0.10f + 0.03f), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, h))
        y += h + rng.nextFloat() * 10f
    }
}

/** Little pop-in scale for a piece that just appeared. */
@Composable
fun rememberPop(key: Any?): Float {
    val a = remember(key) { Animatable(0.4f) }
    LaunchedEffect(key) { a.animateTo(1f, spring(0.5f, Spring.StiffnessMediumLow)) }
    return a.value
}

@Composable
fun ScoreChip(label: String, value: String, color: Color, active: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (active) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 13.sp)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

fun Dp.half() = this / 2

/** A move's direction from where you sit (the computer plays from the far side). */
fun moveWords(dr: Int, dc: Int): String {
    val v = when { dr > 0 -> "toward you"; dr < 0 -> "back"; else -> "" }
    val h = when { dc < 0 -> "left"; dc > 0 -> "right"; else -> "" }
    val dist = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc))
    val dir = listOf(v, h).filter { it.isNotEmpty() }.joinToString(" and ")
    return if (dist > 1 && (dr == 0 || dc == 0 || kotlin.math.abs(dr) == kotlin.math.abs(dc))) "$dist squares $dir" else "one step $dir"
}

/** Gold rings marking the coach's suggested move. */
fun DrawScope.coachRings(points: List<Offset>, r: Float) {
    for ((k, p) in points.withIndex()) {
        drawCircle(Color(0xFFFFC107).copy(alpha = if (k == 0 && points.size > 1) 0.6f else 0.95f), r, p, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.16f))
    }
}

/** Direction of a move from the mover's view (forward = up the screen). */
fun directionWords(dr: Int, dc: Int): String {
    val v = when { dr < 0 -> "forward"; dr > 0 -> "back"; else -> "" }
    val h = when { dc < 0 -> "left"; dc > 0 -> "right"; else -> "" }
    val dist = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc))
    val dir = listOf(v, h).filter { it.isNotEmpty() }.joinToString("-")
    return if (dist > 1 && (dr == 0 || dc == 0 || kotlin.math.abs(dr) == kotlin.math.abs(dc))) "$dist squares $dir" else dir
}
