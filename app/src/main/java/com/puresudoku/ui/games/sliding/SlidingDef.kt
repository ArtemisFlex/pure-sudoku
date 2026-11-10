package com.puresudoku.ui.games.sliding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.ScoreRules
import com.puresudoku.core.Scoring
import com.puresudoku.core.SlidingRules
import com.puresudoku.core.ints
import com.puresudoku.core.toJson
import com.puresudoku.ui.components.ActionButton
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.components.StatusBar
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.ErrorFx
import com.puresudoku.ui.session.FxIds
import com.puresudoku.ui.session.MiddleStat
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
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

class SlidingSession(
    difficulty: Difficulty,
    val cfg: SlidingRules.Config,
    val initial: IntArray,
    val par: Int,
    tiles: IntArray = initial.copyOf(),
    moves: Int = 0,
) : Session(GameKind.SLIDING_TILES, difficulty) {
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    val n = cfg.n
    private val tiles = tiles

    var version by mutableIntStateOf(0); private set
    var moves by mutableIntStateOf(moves); private set
    var winFx by mutableStateOf<WaveFx?>(null); private set
    /** Tile that was tapped but can't move. */
    var errorFx by mutableStateOf<ErrorFx?>(null); private set

    /** Position (index) of each tile number. */
    fun positionOf(tile: Int) = tiles.indexOf(tile)
    fun tileAt(i: Int) = tiles[i]
    fun inPlace(tile: Int) = tile != 0 && tiles.indexOf(tile) == tile - 1

    override val usesStrikes get() = false
    override val middleStat get() = MiddleStat.Counter("Moves", if (n == 3) "$moves · par $par" else "$moves")
    override val progress: Float get() = (1 until n * n).count { inPlace(it) } / (n * n - 1).toFloat()
    override val wonTitle get() = "In order!"

    fun slideAt(i: Int) {
        if (outcome != Outcome.PLAYING) return
        val m = SlidingRules.slide(tiles, n, i)
        if (m == 0) {
            if (tiles[i] != 0) {
                errorFx = ErrorFx(FxIds.next(), tiles[i], 0)
                nudge("Only tiles in the same row or column as the gap can slide")
            }
            return
        }
        moves += m
        version++
        if (SlidingRules.isSolved(tiles)) {
            winFx = WaveFx(FxIds.next(), n * n - 1, (0 until n * n).toSet())
            val moveScore = cfg.basePoints * 20 * par / maxOf(par, moves)
            val time = Scoring.finish(rules, elapsedMs, 0).timeBonus
            finish(true, listOf(BonusLine(if (n == 3) "Par bonus" else "Moves bonus", moveScore), BonusLine("Time bonus", time)))
        } else changed()
    }

    fun slideTile(tile: Int) = slideAt(positionOf(tile))

    fun reset() {
        if (outcome != Outcome.PLAYING || moves == 0) return
        initial.copyInto(tiles); moves = 0; version++; changed()
    }

    override fun saveState(): JSONObject = JSONObject()
        .put("initial", initial.toJson()).put("tiles", tiles.toJson()).put("moves", moves).put("par", par)
}

object SlidingDef : GameDef {
    override val kind = GameKind.SLIDING_TILES
    override fun background(session: com.puresudoku.ui.session.Session) = com.puresudoku.R.drawable.vbg_sliding_tiles
    private val rng = Random(SecureRandom().nextLong())

    override fun generate(d: Difficulty): Session {
        val cfg = SlidingRules.config(d)
        val t = synchronized(rng) { SlidingRules.generate(cfg, rng) }
        return SlidingSession(d, cfg, t, SlidingRules.par(t, cfg.n))
    }

    override fun restore(o: JSONObject): Session {
        val d = Difficulty.valueOf(o.getString("difficulty"))
        val st = o.getJSONObject("state")
        return SlidingSession(d, SlidingRules.config(d), st.ints("initial"), st.getInt("par"), st.ints("tiles"), st.getInt("moves")).restoreCommon(o)
    }

    override fun levelInfo(d: Difficulty) = SlidingRules.config(d).let { "${it.n}×${it.n} tiles" }

    override fun hint(d: Difficulty): String = "Tap a tile in the same row or column as the gap to slide it. Order them 1, 2, 3… with the gap last."

    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Lightly shuffled 3×3."
        Difficulty.MEDIUM -> "Fully shuffled 3×3."
        Difficulty.HARD -> "Lightly shuffled 4×4."
        Difficulty.EXPERT -> "The classic fifteen puzzle."
        Difficulty.MASTER -> "Twenty-four tiles."
        Difficulty.EXTREME -> "Thirty-five tiles. Plan ahead."
    }

    @Composable
    override fun Play(session: Session) = SlidingLayout(session as SlidingSession, null)

    @Composable
    override fun Icon(size: Dp) = GameIcon(kind, size)

    // ---------------------------------------------------------------- tutorial

    private fun demo(tiles: IntArray): SlidingSession {
        val cfg = SlidingRules.config(Difficulty.EASY)
        return SlidingSession(Difficulty.EASY, cfg, tiles, SlidingRules.par(tiles, 3))
    }

    // Two moves from solved: 1 2 3 / 4 5 6 / _ 7 8
    private val NEAR = intArrayOf(1, 2, 3, 4, 5, 6, 0, 7, 8)
    // A few moves out: 1 2 3 / 4 _ 6 / 7 5 8
    private val MID = intArrayOf(1, 2, 3, 4, 0, 6, 7, 5, 8)
    // Gap at top-left for a whole-line slide
    private val LINE = intArrayOf(0, 1, 2, 4, 5, 3, 7, 8, 6)

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep(
            "Put them in order",
            "Arrange the tiles 1, 2, 3… left to right, top to bottom, with the empty space in the bottom-right corner.",
        ) { active ->
            var s by remember { mutableStateOf(demo(NEAR)) }
            DemoStage(active, script = {
                s = demo(NEAR)
                wait(600)
                highlight((0..8).map { "cell-$it" }.take(3), 1000)
                highlight((3..5).map { "cell-$it" }, 1000)
                tap("tile-7") { s.slideTile(7) }
                tap("tile-8") { s.slideTile(8) }
                wait(1800)
            }) { t -> SlidingLayout(s, t, controls = false) }
        },
        TutorialStep(
            "Slide a tile",
            "Tap or swipe a tile in the same row or column as the empty space and it slides in, pushing any tiles between. Tiles elsewhere can't move — they shake red.",
        ) { active ->
            var s by remember { mutableStateOf(demo(LINE)) }
            DemoStage(active, script = {
                s = demo(LINE)
                wait(500)
                tap("tile-5") { s.slideTile(5) }
                wait(700)
                tap("tile-2") { s.slideTile(2) }
                wait(500)
                tap("tile-3") { s.slideTile(3) }
                wait(400)
                tap("tile-6") { s.slideTile(6) }
                wait(1600)
            }) { t -> SlidingLayout(s, t, controls = false) }
        },
        TutorialStep(
            "Moves and par",
            "Every tile you slide counts as a move. On 3×3 boards, par is the fewest moves possible. Tiles already in their spot turn pale blue.",
        ) { active ->
            var s by remember { mutableStateOf(demo(MID)) }
            DemoStage(active, script = {
                s = demo(MID)
                wait(400)
                highlight(listOf("middle"), 1300)
                tap("tile-5") { s.slideTile(5) }
                tap("tile-8") { s.slideTile(8) }
                wait(1800)
            }) { t -> SlidingLayout(s, t, status = true, controls = false) }
        },
    )
}

// ------------------------------------------------------------------- UI

@Composable
private fun SlidingLayout(s: SlidingSession, targets: DemoTargets?, status: Boolean = false, controls: Boolean = true) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (status) StatusBar(s.score, s.middleStat, s.elapsedMs, targets = targets)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = min(min(maxWidth, maxHeight), 480.dp)
            SlidingBoard(s, side, targets)
        }
        if (controls) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionButton(Icons.Outlined.RestartAlt, "Reset", enabled = s.moves > 0, onClick = s::reset)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SlidingBoard(s: SlidingSession, side: Dp, targets: DemoTargets?) {
    val colors = LocalBoardColors.current
    val n = s.n
    val pad = 6.dp
    val cell = (side - pad * 2) / n
    val density = LocalDensity.current
    val cellPx = with(density) { cell.toPx() }
    val padPx = with(density) { pad.toPx() }
    Box(
        Modifier
            .size(side)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.boardBg)
            .pointerInput(s, cellPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var last = down.position
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        last = ch.position
                        if (!ch.pressed) break
                    }
                    fun at(o: Offset): Int {
                        val c = ((o.x - padPx) / cellPx).toInt(); val r = ((o.y - padPx) / cellPx).toInt()
                        return if (c in 0 until n && r in 0 until n) r * n + c else -1
                    }
                    val i = at(down.position)
                    if (i < 0) return@awaitEachGesture
                    // A tap or a swipe both slide the touched tile (if it's in line with the gap).
                    s.slideAt(i)
                }
            },
    ) {
        // invisible cell targets (for highlights in the tutorial)
        if (targets != null) for (i in 0 until n * n) {
            Box(Modifier.offset(pad + cell * (i % n), pad + cell * (i / n)).size(cell).demoTarget(targets, "cell-$i"))
        }
        for (tile in 1 until n * n) SlidingTile(s, tile, cell, pad, targets)
    }
}

@Composable
private fun SlidingTile(s: SlidingSession, tile: Int, cell: Dp, pad: Dp, targets: DemoTargets?) {
    val colors = LocalBoardColors.current
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val n = s.n
    val pos = s.positionOf(tile)
    val density = LocalDensity.current
    val target = with(density) { IntOffset((pad + cell * (pos % n)).roundToPx(), (pad + cell * (pos / n)).roundToPx()) }
    val offset by animateIntOffsetAsState(target, tween(130, easing = FastOutSlowInEasing), label = "tile")
    val shake = remember { Animatable(0f) }
    val err = remember { Animatable(0f) }
    val efx = s.errorFx
    LaunchedEffect(efx?.id) {
        if (efx != null && efx.cell == tile) {
            err.snapTo(1f)
            shake.animateTo(0f, keyframes { durationMillis = 360; -9f at 45; 8f at 100; -6f at 160; 3f at 230; 0f at 360 })
            err.animateTo(0f, tween(350))
        }
    }
    val inPlace = s.inPlace(tile)
    val won = s.outcome == Outcome.WON
    val bg by animateColorAsState(
        when { won -> colors.entered; inPlace -> colors.sameDigit; else -> colors.related }, tween(260), label = "tilebg",
    )
    val wave = remember { Animatable(0f) }
    val fx = s.winFx
    LaunchedEffect(fx?.id) {
        if (fx != null) {
            val dist = max(abs(fx.origin / n - pos / n), abs(fx.origin % n - pos % n))
            delay(dist * 70L)
            wave.animateTo(1f, tween(180, easing = FastOutSlowInEasing)); wave.animateTo(0f, tween(450, easing = LinearOutSlowInEasing))
        }
    }
    val fs = with(density) { (cell * if (n >= 5) 0.38f else 0.44f).toSp() }
    Box(
        Modifier
            .offset { offset + IntOffset(shake.value.dp.roundToPx(), 0) }
            .size(cell)
            .padding(3.dp)
            .demoTarget(targets, "tile-$tile")
            .graphicsLayer { val k = 1f + 0.1f * wave.value; scaleX = k; scaleY = k }
            .clip(RoundedCornerShape(cell * 0.16f))
            .background(if (err.value > 0f) colors.errorBg else bg),
        contentAlignment = Alignment.Center,
    ) {
        Text("$tile", fontSize = fs, fontFamily = Poppins, fontWeight = FontWeight.Bold,
            color = if (err.value > 0f) colors.error else if (won) colors.boardBg else colors.given)
    }
}
