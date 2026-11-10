package com.puresudoku.ui.games.lumen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.RestartAlt
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.LumenRules
import com.puresudoku.core.ScoreRules
import com.puresudoku.core.Scoring
import com.puresudoku.core.ints
import com.puresudoku.core.toBooleanArray
import com.puresudoku.core.toJson
import com.puresudoku.ui.components.ActionButton
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.components.StatusBar
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.FxIds
import com.puresudoku.ui.session.MiddleStat
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.session.WaveFx
import com.puresudoku.ui.theme.LocalBoardColors
import com.puresudoku.ui.tutorial.DemoStage
import com.puresudoku.ui.tutorial.DemoTargets
import com.puresudoku.ui.tutorial.TutorialStep
import com.puresudoku.ui.tutorial.demoTarget
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

class LumenSession(
    difficulty: Difficulty,
    val cfg: LumenRules.Config,
    val initial: BooleanArray,
    val par: Int,
    lights: BooleanArray = initial.copyOf(),
    presses: IntArray = IntArray(0),
) : Session(GameKind.LUMEN, difficulty) {
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    val n = cfg.n
    private val lights = lights
    private val presses = ArrayDeque(presses.toList())

    var version by mutableIntStateOf(0); private set
    /** The tiles flipped by the last tap, rippling out from [WaveFx.origin]. */
    var flipFx by mutableStateOf<WaveFx?>(null); private set

    val moves get() = presses.size
    fun lit(i: Int) = lights[i]

    override val usesStrikes get() = false
    override val middleStat get() = MiddleStat.Counter("Moves", "$moves · par $par")
    override val progress: Float get() = 1f - lights.count { it } / initial.count { it }.coerceAtLeast(1).toFloat()
    override val wonTitle get() = "Lights out!"

    fun press(i: Int) {
        if (outcome != Outcome.PLAYING) return
        LumenRules.press(n, cfg.pattern, lights, i)
        presses.addLast(i)
        flipFx = WaveFx(FxIds.next(), i, LumenRules.affected(n, cfg.pattern, i).toSet())
        version++
        if (lights.none { it }) {
            flipFx = WaveFx(FxIds.next(), i, (0 until n * n).toSet())
            val movesScore = cfg.basePoints * 20 * par / maxOf(par, moves)
            val time = Scoring.finish(rules, elapsedMs, 0).timeBonus
            finish(true, listOf(BonusLine(if (moves <= par) "Par bonus" else "Moves bonus", movesScore), BonusLine("Time bonus", time)))
        } else changed()
    }

    fun undo() {
        if (outcome != Outcome.PLAYING) return
        val i = presses.removeLastOrNull() ?: return
        LumenRules.press(n, cfg.pattern, lights, i)
        flipFx = WaveFx(FxIds.next(), i, LumenRules.affected(n, cfg.pattern, i).toSet())
        version++; changed()
    }

    fun reset() {
        if (outcome != Outcome.PLAYING || presses.isEmpty()) return
        initial.copyInto(lights); presses.clear()
        flipFx = WaveFx(FxIds.next(), n * n / 2, (0 until n * n).toSet())
        version++; changed()
    }

    override fun saveState(): JSONObject = JSONObject()
        .put("initial", initial.toJson()).put("lights", lights.toJson())
        .put("presses", presses.toIntArray().toJson()).put("par", par)
}

object LumenDef : GameDef {
    override val kind = GameKind.LUMEN
    override fun background(session: com.puresudoku.ui.session.Session) = com.puresudoku.R.drawable.vbg_lumen
    private val rng = Random(SecureRandom().nextLong())

    override fun generate(d: Difficulty): Session {
        val cfg = LumenRules.config(d)
        val (lights, par) = synchronized(rng) { LumenRules.generate(cfg, rng) }
        return LumenSession(d, cfg, lights, par)
    }

    override fun restore(o: JSONObject): Session {
        val d = Difficulty.valueOf(o.getString("difficulty"))
        val st = o.getJSONObject("state")
        return LumenSession(
            d, LumenRules.config(d), st.getJSONArray("initial").toBooleanArray(), st.getInt("par"),
            st.getJSONArray("lights").toBooleanArray(), st.ints("presses"),
        ).restoreCommon(o)
    }

    override fun levelInfo(d: Difficulty) = LumenRules.config(d).let {
        "${it.n}×${it.n}" + when (it.pattern) { LumenRules.Pattern.PLUS -> ""; LumenRules.Pattern.CROSS -> " · diagonals"; LumenRules.Pattern.KING -> " · all around" }
    }

    override fun hint(d: Difficulty): String = when (LumenRules.config(d).pattern) { LumenRules.Pattern.PLUS -> "Tapping a tile flips it and the tiles above, below, left and right. Turn every light off."; LumenRules.Pattern.CROSS -> "Tapping a tile flips it and its four diagonal neighbours. Turn every light off."; LumenRules.Pattern.KING -> "Tapping a tile flips it and all eight tiles around it. Turn every light off." }

    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A tiny board to learn the moves."
        Difficulty.MEDIUM -> "Four by four."
        Difficulty.HARD -> "The classic five by five."
        Difficulty.EXPERT -> "Five by five, more scrambled."
        Difficulty.MASTER -> "Taps flip the diagonals."
        Difficulty.EXTREME -> "Taps flip all eight around."
    }

    @Composable
    override fun Play(session: Session) = LumenLayout(session as LumenSession, null)

    @Composable
    override fun Icon(size: Dp) = GameIcon(kind, size)

    // ---------------------------------------------------------------- tutorial

    private fun demoFrom(n: Int, pattern: LumenRules.Pattern, presses: List<Int>, d: Difficulty = Difficulty.EASY): LumenSession {
        val cfg = LumenRules.config(d).copy(n = n, pattern = pattern)
        val lights = BooleanArray(n * n)
        presses.forEach { LumenRules.press(n, pattern, lights, it) }
        return LumenSession(d, cfg, lights, LumenRules.minimumPresses(n, pattern, lights) ?: presses.size)
    }

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep(
            "Flip the lights",
            "Tapping a tile switches it, plus the tiles directly above, below, left and right of it. Lit tiles go dark and dark tiles light up.",
        ) { active ->
            var s by remember { mutableStateOf(demoFrom(3, LumenRules.Pattern.PLUS, emptyList())) }
            DemoStage(active, script = {
                s = demoFrom(3, LumenRules.Pattern.PLUS, emptyList())
                wait(500)
                tap("cell-4") { s.press(4) }
                wait(900)
                tap("cell-0") { s.press(0) }
                wait(900)
                tap("cell-0") { s.press(0) }
                tap("cell-4") { s.press(4) }
                wait(800)
            }) { t -> LumenLayout(s, t, controls = false) }
        },
        TutorialStep(
            "Lights out",
            "Switch every light off to win.",
        ) { active ->
            var s by remember { mutableStateOf(demoFrom(3, LumenRules.Pattern.PLUS, listOf(0, 8))) }
            DemoStage(active, script = {
                s = demoFrom(3, LumenRules.Pattern.PLUS, listOf(0, 8))
                wait(900)
                tap("cell-0") { s.press(0) }
                wait(500)
                tap("cell-8") { s.press(8) }
                wait(2000)
            }) { t -> LumenLayout(s, t, controls = false) }
        },
        TutorialStep(
            "Moves and par",
            "Par is the fewest taps that can clear the board. Match it for full points. Undo a tap or reset the board whenever you like.",
        ) { active ->
            var s by remember { mutableStateOf(demoFrom(4, LumenRules.Pattern.PLUS, listOf(5, 10, 3))) }
            DemoStage(active, script = {
                s = demoFrom(4, LumenRules.Pattern.PLUS, listOf(5, 10, 3))
                wait(400)
                highlight(listOf("middle"), 1300)
                tap("cell-6") { s.press(6) }
                wait(400)
                tap("undo") { s.undo() }
                wait(400)
                tap("cell-5") { s.press(5) }
                tap("cell-10") { s.press(10) }
                tap("cell-3") { s.press(3) }
                wait(1800)
            }) { t -> LumenLayout(s, t, status = true) }
        },
        TutorialStep(
            "New patterns",
            "On Master, a tap flips the four diagonal neighbours instead. On Extreme, it flips all eight tiles around it.",
        ) { active ->
            var s by remember { mutableStateOf(demoFrom(5, LumenRules.Pattern.CROSS, emptyList())) }
            DemoStage(active, script = {
                s = demoFrom(5, LumenRules.Pattern.CROSS, emptyList())
                wait(500)
                tap("cell-12") { s.press(12) }
                wait(1300)
                s = demoFrom(5, LumenRules.Pattern.KING, emptyList())
                wait(500)
                tap("cell-12") { s.press(12) }
                wait(1500)
            }) { t -> LumenLayout(s, t, controls = false) }
        },
    )
}

// ------------------------------------------------------------------- UI

@Composable
private fun LumenLayout(s: LumenSession, targets: DemoTargets?, status: Boolean = false, controls: Boolean = true) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (status) StatusBar(s.score, s.middleStat, s.elapsedMs, targets = targets)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = min(min(maxWidth, maxHeight), 480.dp)
            LumenGrid(s, side, targets)
        }
        if (controls) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionButton(Icons.AutoMirrored.Outlined.Undo, "Undo", enabled = s.moves > 0, modifier = Modifier.demoTarget(targets, "undo"), onClick = s::undo)
                ActionButton(Icons.Outlined.RestartAlt, "Reset", enabled = s.moves > 0, onClick = s::reset)
            }
            Spacer(Modifier.height(if (targets == null) 16.dp else 4.dp))
        }
    }
}

@Composable
private fun LumenGrid(s: LumenSession, side: Dp, targets: DemoTargets?) {
    val n = s.n
    val gap = if (n <= 4) 10.dp else 7.dp
    Column(Modifier.width(side).aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
        for (r in 0 until n) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            for (c in 0 until n) LumenTile(s, r * n + c, Modifier.weight(1f), targets)
        }
    }
}

private val LIT = Color(0xFFFFC93C)
private val LIT_GLOW = Color(0xFFFFF1B8)

@Composable
private fun LumenTile(s: LumenSession, i: Int, modifier: Modifier, targets: DemoTargets?) {
    val colors = LocalBoardColors.current
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val n = s.n
    val on = s.lit(i)
    // What the tile shows; it switches at the halfway point of the flip.
    var shown by remember { mutableStateOf(on) }
    var flipping by remember { mutableStateOf(false) }
    val flip = remember { Animatable(0f) }
    val fx = s.flipFx
    LaunchedEffect(fx?.id) {
        if (fx != null && i in fx.cells) {
            flipping = true
            try {
                val dist = max(abs(fx.origin / n - i / n), abs(fx.origin % n - i % n))
                delay(dist * 35L)
                flip.snapTo(0f)
                flip.animateTo(0.5f, tween(95, easing = FastOutSlowInEasing))
                shown = s.lit(i)
                flip.animateTo(1f, tween(110, easing = FastOutSlowInEasing))
            } finally {
                flip.snapTo(0f)
                shown = s.lit(i)
                flipping = false
            }
        }
    }
    LaunchedEffect(on) { if (!flipping) shown = on }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val face = if (shown) LIT else colors.related
    Box(
        modifier
            .fillMaxSize()
            .demoTarget(targets, "cell-$i")
            .graphicsLayer {
                rotationY = flip.value * 180f
                cameraDistance = 12f * density
                val k = (if (pressed) 0.92f else 1f) - 0.06f * kotlin.math.sin(flip.value * Math.PI).toFloat()
                scaleX = k; scaleY = k
            }
            .clip(RoundedCornerShape(14.dp))
            .background(face)
            .drawBehind {
                if (shown) drawRect(Brush.radialGradient(listOf(LIT_GLOW.copy(alpha = 0.9f), LIT_GLOW.copy(alpha = 0f)), radius = size.minDimension * 0.6f))
            }
            .clickable(press, null) { s.press(i) },
    )
}
