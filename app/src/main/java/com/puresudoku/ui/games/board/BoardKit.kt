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
    protected val rng = Random(System.nanoTime())
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

    protected fun lose() { thinking = false; finish(false) }
    protected fun draw() { thinking = false; score += rules.basePoints * 3; finishDraw() }

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
