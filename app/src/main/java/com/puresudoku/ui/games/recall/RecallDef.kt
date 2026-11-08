package com.puresudoku.ui.games.recall

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.RecallRules
import com.puresudoku.core.ScoreRules
import com.puresudoku.core.Scoring
import com.puresudoku.core.ints
import com.puresudoku.core.toBooleanArray
import com.puresudoku.core.toJson
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.components.NumberPad
import com.puresudoku.ui.components.StatusBar
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.ErrorFx
import com.puresudoku.ui.session.FxIds
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.PlacedFx
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
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

enum class RecallPhase { SHOW, RECALL, PASS }

class RecallSession(
    difficulty: Difficulty,
    val cfg: RecallRules.Config = RecallRules.config(difficulty),
    round: Int = 0,
    cells: IntArray? = null,
    digits: IntArray? = null,
    found: BooleanArray? = null,
    private var streak: Int = 0,
) : Session(GameKind.GRID_RECALL, difficulty) {
    private val rng = Random(SecureRandom().nextLong())
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)

    var round by mutableIntStateOf(round); private set
    var cells: IntArray = cells ?: IntArray(0); private set
    var digits: IntArray = digits ?: IntArray(0); private set
    var found: BooleanArray = found ?: BooleanArray(0); private set
    var phase by mutableStateOf(RecallPhase.SHOW); private set
    var phaseMs by mutableLongStateOf(0L); private set
    var selectedItem by mutableIntStateOf(-1); private set
    var version by mutableIntStateOf(0); private set
    var placedFx by mutableStateOf<PlacedFx?>(null); private set
    var errorFx by mutableStateOf<ErrorFx?>(null); private set
    var waveFx by mutableStateOf<WaveFx?>(null); private set

    init {
        if (cells == null) newRound(round)
        else if (found != null && found.all { it }) phase = RecallPhase.PASS
    }

    val showMs: Long get() = RecallRules.showMs(cfg, round)
    /** For ordered levels: which item is flashing right now (-1 = none). */
    val flashing: Int
        get() {
            if (phase != RecallPhase.SHOW || !cfg.ordered) return -1
            val per = showMs / cells.size.coerceAtLeast(1)
            val k = (phaseMs / per).toInt()
            return if (k < cells.size && phaseMs % per < per * 4 / 5) k else -1
        }
    val nextOrder: Int get() = found.indexOfFirst { !it }

    override val progress: Float get() = round / cfg.rounds.toFloat()

    private fun newRound(r: Int) {
        val (c, d) = RecallRules.pattern(cfg, r, rng)
        setPattern(r, c, d)
    }

    /** Demo helper: a fixed pattern. */
    fun setPattern(r: Int, c: IntArray, d: IntArray) {
        round = r; cells = c; digits = d; found = BooleanArray(c.size)
        phase = RecallPhase.SHOW; phaseMs = 0; selectedItem = -1
        version++
    }

    override fun onTick(dtMs: Long) {
        if (outcome != Outcome.PLAYING) return
        phaseMs += dtMs
        when (phase) {
            RecallPhase.SHOW -> if (phaseMs >= showMs) { phase = RecallPhase.RECALL; phaseMs = 0; version++ }
            RecallPhase.PASS -> if (phaseMs >= 1300) {
                if (round + 1 >= cfg.rounds) {
                    val b = Scoring.finish(rules, elapsedMs, strikes)
                    finish(true, listOf(BonusLine("Time bonus", b.timeBonus), BonusLine("Hearts left bonus", b.livesBonus)))
                } else { newRound(round + 1); changed() }
            }
            RecallPhase.RECALL -> Unit
        }
    }

    fun skipShow() { if (phase == RecallPhase.SHOW) { phase = RecallPhase.RECALL; phaseMs = 0; version++ } }

    fun tapCell(cell: Int) {
        if (outcome != Outcome.PLAYING || phase != RecallPhase.RECALL) return
        val item = cells.indexOf(cell)
        if (item >= 0 && found[item]) return
        if (item < 0 || (cfg.ordered && item != nextOrder)) { mistake(cell); return }
        selectedItem = item
    }

    fun input(digit: Int) {
        if (outcome != Outcome.PLAYING || phase != RecallPhase.RECALL) return
        val item = selectedItem
        if (item < 0) return
        if (digits[item] != digit) { mistake(cells[item]); return }
        found[item] = true
        selectedItem = -1
        streak++
        val pts = Scoring.placement(rules, streak)
        score += pts
        placedFx = PlacedFx(FxIds.next(), cells[item], pts)
        if (found.all { it }) {
            score += Scoring.unitBonus(rules)
            phase = RecallPhase.PASS; phaseMs = 0
            waveFx = WaveFx(FxIds.next(), cells[item], (0 until cfg.n * cfg.n).toSet())
        }
        version++
        changed()
    }

    private fun mistake(cell: Int) {
        errorFx = ErrorFx(FxIds.next(), cell, 0)
        streak = 0
        score -= minOf(score, rules.basePoints)
        version++
        if (!addStrike()) changed()
    }

    override val lostMessage: String get() = "Three strikes on round ${round + 1}. This game's save slot has been cleared."

    override fun saveState(): JSONObject = JSONObject()
        .put("round", round)
        .put("cells", cells.toJson())
        .put("digits", digits.toJson())
        .put("found", found.toJson())
        .put("streak", streak)
}

object RecallDef : GameDef {
    override val kind = GameKind.GRID_RECALL

    override fun generate(d: Difficulty): Session = RecallSession(d)

    override fun restore(o: JSONObject): Session {
        val d = Difficulty.valueOf(o.getString("difficulty"))
        val st = o.getJSONObject("state")
        return RecallSession(
            d, round = st.getInt("round"), cells = st.ints("cells"), digits = st.ints("digits"),
            found = st.getJSONArray("found").toBooleanArray(), streak = st.optInt("streak"),
        ).restoreCommon(o)
    }

    override fun levelInfo(d: Difficulty) = RecallRules.config(d).let { "${it.n}×${it.n} · ${it.rounds} rounds" }

    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A few numbers, plenty of time."
        Difficulty.MEDIUM -> "Bigger grid, more to hold."
        Difficulty.HARD -> "Less time to look."
        Difficulty.EXPERT -> "Numbers flash in order. Keep it."
        Difficulty.MASTER -> "Longer sequences, faster."
        Difficulty.EXTREME -> "Big grid, fast flashes."
    }

    @Composable
    override fun Play(session: Session) {
        val s = session as RecallSession
        RecallLayout(s, null)
    }

    @Composable
    override fun Icon(size: Dp) = GameIcon(kind, size)

    // ---------------------------------------------------------------- tutorial

    private fun demo(ordered: Boolean = false): RecallSession {
        val s = RecallSession(if (ordered) Difficulty.EXPERT else Difficulty.EASY,
            cfg = RecallRules.config(if (ordered) Difficulty.EXPERT else Difficulty.EASY).copy(n = if (ordered) 4 else 3))
        if (ordered) s.setPattern(0, intArrayOf(5, 14, 2), intArrayOf(4, 9, 1))
        else s.setPattern(0, intArrayOf(1, 6, 8), intArrayOf(7, 3, 5))
        return s
    }

    /** Runs the demo session's game clock while the card is on screen. */
    @Composable
    private fun Clock(s: RecallSession, active: Boolean) {
        LaunchedEffect(active, s) {
            while (active) { delay(32); s.onTick(32) }
        }
    }

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep(
            "Memorize",
            "Numbers appear on the grid for a few seconds. Remember each number and where it was before they hide.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            Clock(s, active)
            DemoStage(active, script = {
                s = demo()
                wait(600)
                highlight(listOf("phase"), 1300)
                wait(2500)
            }) { t -> RecallLayout(s, t) }
        },
        TutorialStep(
            "Put them back",
            "When they hide, tap a square where a number was, then tap that number below.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            Clock(s, active)
            DemoStage(active, script = {
                s = demo()
                s.skipShow()
                wait(500)
                tap("cell-1") { s.tapCell(1) }; tap("key-7") { s.input(7) }
                tap("cell-6") { s.tapCell(6) }; tap("key-3") { s.input(3) }
                tap("cell-8") { s.tapCell(8) }; tap("key-5") { s.input(5) }
                wait(1600)
            }) { t -> RecallLayout(s, t) }
        },
        TutorialStep(
            "In order",
            "From Expert up, numbers flash one at a time. Put them back in the same order they appeared.",
        ) { active ->
            var s by remember { mutableStateOf(demo(ordered = true)) }
            Clock(s, active)
            DemoStage(active, script = {
                s = demo(ordered = true)
                wait(s.showMs + 400)
                tap("cell-5") { s.tapCell(5) }; tap("key-4") { s.input(4) }
                tap("cell-14") { s.tapCell(14) }; tap("key-9") { s.input(9) }
                tap("cell-2") { s.tapCell(2) }; tap("key-1") { s.input(1) }
                wait(1500)
            }) { t -> RecallLayout(s, t) }
        },
        TutorialStep(
            "Rounds and strikes",
            "Every couple of rounds adds another number. A wrong square or number costs a heart; three strikes ends the game. Clear every round to win.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            Clock(s, active)
            DemoStage(active, script = {
                s = demo()
                s.skipShow()
                wait(500)
                tap("cell-0") { s.tapCell(0) }
                wait(500)
                highlight(listOf("middle"), 1100)
                tap("cell-1") { s.tapCell(1) }; tap("key-7") { s.input(7) }
                wait(1500)
            }) { t -> RecallLayout(s, t, status = true) }
        },
    )
}

// ------------------------------------------------------------------- UI

@Composable
private fun RecallLayout(s: RecallSession, targets: DemoTargets?, status: Boolean = false) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (status) StatusBar(s.score, s.middleStat, s.elapsedMs, targets = targets)
        PhaseBanner(s, targets)
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val side = min(min(maxWidth, maxHeight), 460.dp)
            RecallGrid(s, side, targets)
        }
        Spacer(Modifier.height(12.dp))
        val canType = s.phase == RecallPhase.RECALL && s.selectedItem >= 0
        Box(Modifier.graphicsLayer { alpha = if (canType) 1f else 0.45f }) {
            NumberPad(9, null, false, s::input, targets)
        }
        Spacer(Modifier.height(if (targets == null) 14.dp else 4.dp))
    }
}

@Composable
private fun PhaseBanner(s: RecallSession, targets: DemoTargets?) {
    Column(Modifier.fillMaxWidth().demoTarget(targets, "phase"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "ROUND ${s.round + 1} OF ${s.cfg.rounds}", fontSize = 11.sp, letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedContent(
            s.phase, label = "phase",
            transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
        ) { ph ->
            Text(
                when (ph) {
                    RecallPhase.SHOW -> "Memorize…"
                    RecallPhase.RECALL -> if (s.cfg.ordered) "Your turn — in order" else "Your turn"
                    RecallPhase.PASS -> "Round clear!"
                },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        val frac = if (s.phase == RecallPhase.SHOW) 1f - (s.phaseMs / s.showMs.toFloat()).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier.padding(top = 6.dp).width(160.dp).height(5.dp).clip(RoundedCornerShape(50)),
        )
    }
}

@Composable
private fun RecallGrid(s: RecallSession, side: Dp, targets: DemoTargets?) {
    val n = s.cfg.n
    val gap = 6.dp
    val cell = (side - gap * (n - 1)) / n
    Column(Modifier.width(side).aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
        for (r in 0 until n) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            for (c in 0 until n) RecallTile(s, r * n + c, cell, Modifier.weight(1f), targets)
        }
    }
}

@Composable
private fun RecallTile(s: RecallSession, i: Int, size: Dp, modifier: Modifier, targets: DemoTargets?) {
    val colors = LocalBoardColors.current
    val item = s.cells.indexOf(i)
    val n = s.cfg.n
    val showing = item >= 0 && when (s.phase) {
        RecallPhase.SHOW -> if (s.cfg.ordered) s.flashing == item else true
        RecallPhase.RECALL -> s.found[item]
        RecallPhase.PASS -> true
    }
    val lit = showing && s.phase == RecallPhase.SHOW
    val selected = item >= 0 && item == s.selectedItem
    val bg by animateColorAsState(
        when {
            lit -> colors.entered
            selected -> colors.selected
            showing -> colors.sameDigit
            else -> colors.boardBg
        }, tween(220), label = "tile",
    )
    val pop = remember { Animatable(1f) }
    LaunchedEffect(showing, s.round) { if (showing) { pop.snapTo(0.4f); pop.animateTo(1f, spring(0.45f, Spring.StiffnessMedium)) } }
    val wave = remember { Animatable(0f) }
    val wfx = s.waveFx
    LaunchedEffect(wfx?.id) {
        if (wfx != null && i in wfx.cells) {
            val dist = max(abs(wfx.origin / n - i / n), abs(wfx.origin % n - i % n))
            delay(dist * 60L)
            wave.animateTo(1f, tween(170, easing = FastOutSlowInEasing)); wave.animateTo(0f, tween(420, easing = LinearOutSlowInEasing))
        }
    }
    val shake = remember { Animatable(0f) }
    val err = remember { Animatable(0f) }
    val efx = s.errorFx
    LaunchedEffect(efx?.id) {
        if (efx != null && efx.cell == i) {
            err.snapTo(1f)
            shake.animateTo(0f, keyframes { durationMillis = 380; -9f at 50; 8f at 110; -6f at 170; 3f at 240; 0f at 380 })
            err.animateTo(0f, tween(450))
        }
    }
    val fs = with(LocalDensity.current) { (size * 0.5f).toSp() }
    Box(
        modifier
            .fillMaxSize()
            .demoTarget(targets, "cell-$i")
            .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            .graphicsLayer { val k = 1f + 0.08f * wave.value; scaleX = k; scaleY = k }
            .clip(RoundedCornerShape(size * 0.18f))
            .background(if (err.value > 0f) colors.errorBg else bg)
            .background(colors.wave.copy(alpha = 0.7f * wave.value))
            .clickable(remember { MutableInteractionSource() }, null) { s.tapCell(i) },
        contentAlignment = Alignment.Center,
    ) {
        if (showing) {
            Text(
                "${s.digits[item]}", fontSize = fs, fontFamily = Poppins, fontWeight = FontWeight.Bold,
                color = if (lit) colors.boardBg else colors.given,
                modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
            )
        }
    }
}
