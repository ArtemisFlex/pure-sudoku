package com.puresudoku.ui.games.hashi

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
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.HashiGenerator
import com.puresudoku.core.HashiPuzzle
import com.puresudoku.core.HashiRules
import com.puresudoku.core.Island
import com.puresudoku.core.ScoreRules
import com.puresudoku.core.Scoring
import com.puresudoku.core.ints
import com.puresudoku.core.toJson
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.components.StatusBar
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.FxIds
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.theme.LocalBoardColors
import com.puresudoku.ui.theme.Poppins
import com.puresudoku.ui.tutorial.DemoStage
import com.puresudoku.ui.tutorial.DemoTargets
import com.puresudoku.ui.tutorial.TutorialStep
import com.puresudoku.ui.tutorial.demoTarget
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

data class BridgeFx(val id: Long, val edge: Int)
data class IslandFx(val id: Long, val islands: Set<Int>)

class HashiSession(
    difficulty: Difficulty,
    val puzzle: HashiPuzzle,
    private val placed: IntArray = IntArray(puzzle.edges.size),
    private var streak: Int = 0,
) : Session(GameKind.HASHI, difficulty) {
    private val rules = HashiRules.config(difficulty).let { ScoreRules(it.basePoints, it.parSeconds) }

    var version by mutableIntStateOf(0); private set
    var selected by mutableIntStateOf(-1); private set
    var bridgeFx by mutableStateOf<BridgeFx?>(null); private set
    var errorFx by mutableStateOf<IslandFx?>(null); private set
    var doneFx by mutableStateOf<IslandFx?>(null); private set

    fun placed(e: Int) = placed[e]
    fun built(i: Int) = puzzle.edges.indices.filter { puzzle.edges[it].a == i || puzzle.edges[it].b == i }.sumOf { placed[it] }
    fun complete(i: Int) = built(i) == puzzle.islands[i].need

    override val progress: Float get() = placed.sum() / puzzle.totalBridges.coerceAtLeast(1).toFloat()

    fun edgeBetween(a: Int, b: Int): Int = puzzle.edges.indexOfFirst { (it.a == a && it.b == b) || (it.a == b && it.b == a) }

    /** Islands the selected island could still bridge to (for highlighting). */
    fun neighbours(i: Int): List<Int> = puzzle.edges.filter { it.a == i || it.b == i }.map { if (it.a == i) it.b else it.a }

    fun tapIsland(i: Int) {
        if (outcome != Outcome.PLAYING) return
        val sel = selected
        when {
            sel < 0 -> selected = i
            sel == i -> selected = -1
            edgeBetween(sel, i) >= 0 -> { selected = -1; bridge(sel, i) }
            else -> selected = i
        }
    }

    fun clearSelection() { selected = -1 }

    /** Adds one bridge between islands [a] and [b] if they're neighbours. */
    fun bridge(a: Int, b: Int) {
        if (outcome != Outcome.PLAYING) return
        val e = edgeBetween(a, b)
        if (e < 0) {
            errorFx = IslandFx(FxIds.next(), setOf(a, b))
            nudge("Bridges only run straight across or down, to the nearest island")
            return
        }
        if (placed[e] >= puzzle.solution[e]) {
            errorFx = IslandFx(FxIds.next(), setOf(a, b))
            streak = 0
            score -= minOf(score, rules.basePoints)
            version++
            if (!addStrike()) changed()
            return
        }
        placed[e]++
        streak++
        score += Scoring.placement(rules, streak)
        bridgeFx = BridgeFx(FxIds.next(), e)
        val doneNow = listOf(a, b).filter { complete(it) }
        if (doneNow.isNotEmpty()) {
            score += Scoring.unitBonus(rules) / 3 * doneNow.size
            doneFx = IslandFx(FxIds.next(), doneNow.toSet())
        }
        version++
        if (placed.indices.all { placed[it] == puzzle.solution[it] }) {
            doneFx = IslandFx(FxIds.next(), puzzle.islands.indices.toSet())
            val b2 = Scoring.finish(rules, elapsedMs, strikes)
            finish(true, listOf(BonusLine("Time bonus", b2.timeBonus), BonusLine("Hearts left bonus", b2.livesBonus)))
        } else changed()
    }

    override fun saveState(): JSONObject = JSONObject()
        .put("w", puzzle.w).put("h", puzzle.h)
        .put("islands", JSONArray().also { a -> puzzle.islands.forEach { a.put(JSONArray().put(it.r).put(it.c).put(it.need)) } })
        .put("solution", puzzle.solution.toJson())
        .put("placed", placed.toJson())
        .put("streak", streak)
}

object HashiDef : GameDef {
    override val kind = GameKind.HASHI
    override fun background(session: com.puresudoku.ui.session.Session) = com.puresudoku.R.drawable.vbg_hashi
    private val generator = HashiGenerator()

    override fun generate(d: Difficulty): Session = HashiSession(d, synchronized(generator) { generator.generate(d) })

    override fun restore(o: JSONObject): Session {
        val d = Difficulty.valueOf(o.getString("difficulty"))
        val st = o.getJSONObject("state")
        val arr = st.getJSONArray("islands")
        val islands = (0 until arr.length()).map { k -> arr.getJSONArray(k).let { Island(it.getInt(0), it.getInt(1), it.getInt(2)) } }
        val p = HashiPuzzle(st.getInt("w"), st.getInt("h"), islands, st.ints("solution"))
        return HashiSession(d, p, st.ints("placed"), st.optInt("streak")).restoreCommon(o)
    }

    override fun levelInfo(d: Difficulty) = HashiRules.config(d).let { "${it.islands} islands" }

    override fun hint(d: Difficulty): String = "Drag from island to island to build bridges. Each number is how many bridges it needs."

    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A few islands to connect."
        Difficulty.MEDIUM -> "More islands, a few doubles."
        Difficulty.HARD -> "Crowded seas. Watch crossings."
        Difficulty.EXPERT -> "A full archipelago."
        Difficulty.MASTER -> "Dense map, long chains."
        Difficulty.EXTREME -> "The biggest map we make."
    }

    @Composable
    override fun Play(session: Session) {
        val s = session as HashiSession
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            HashiBoard(s, maxWidth, maxHeight)
        }
    }

    @Composable
    override fun Icon(size: Dp) = GameIcon(kind, size)

    // ---------------------------------------------------------------- tutorial

    /*  Demo map (5×5):
          3 ═══════ 4        island 0 (0,0) need 3     island 1 (0,4) need 4
          │         ║        island 2 (2,0) need 3     island 3 (2,2) need 3
          3 ═══ 3   ║        island 4 (4,2) need 2     island 5 (4,4) need 3
                │   ║
                2 ─ 3       (one solution only)                           */
    private fun demoPuzzle(): HashiPuzzle {
        val islands = listOf(Island(0, 0, 3), Island(0, 4, 4), Island(2, 0, 3), Island(2, 2, 3), Island(4, 2, 2), Island(4, 4, 3))
        val edges = HashiRules.edges(islands)
        val want = mapOf((0 to 1) to 2, (0 to 2) to 1, (2 to 3) to 2, (3 to 4) to 1, (4 to 5) to 1, (1 to 5) to 2)
        return HashiPuzzle(5, 5, islands, IntArray(edges.size) { want[edges[it].a to edges[it].b] ?: 0 })
    }

    private fun demo() = HashiSession(Difficulty.EASY, demoPuzzle())

    override fun lesson() = demo() to listOf(
        com.puresudoku.ui.session.LessonStep("Each island's number is how many bridges touch it. Bridges run straight, never cross, and at most two join any pair. Tap an island, then a neighbour, to add a bridge."),
        com.puresudoku.ui.session.LessonStep("The 4 in the top-right corner only has two neighbours, so it needs two bridges to each. Put a double bridge from it to the 3 on its left.", { val h = it as HashiSession; h.placed(h.edgeBetween(0, 1)) == 2 }),
        com.puresudoku.ui.session.LessonStep("And a double bridge down to the 3 below it.", { val h = it as HashiSession; h.placed(h.edgeBetween(1, 5)) == 2 }),
        com.puresudoku.ui.session.LessonStep("The top-left 3 has two bridges already. It needs one more, and the only way left is down. Add a single bridge.", { val h = it as HashiSession; h.placed(h.edgeBetween(0, 2)) == 1 }),
        com.puresudoku.ui.session.LessonStep("Now finish: every island must reach its number and all islands must link into one group.", { it.outcome == com.puresudoku.ui.session.Outcome.WON }),
    )

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep(
            "Islands",
            "Each circle is an island. Its number is how many bridges must connect to it, no more and no less.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                highlight(listOf("island-0"), 1200)
                highlight(listOf("island-4"), 1200)
                drag("island-4", "island-5", action = { s.bridge(4, 5) })
                drag("island-3", "island-4", action = { s.bridge(3, 4) })
                wait(1200)
            }) { t -> DemoHashi(s, t) }
        },
        TutorialStep(
            "Build a bridge",
            "Drag from one island to another, or tap one and then the other. Bridges only run straight across or straight down.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                drag("island-0", "island-2", action = { s.bridge(0, 2) })
                wait(300)
                tap("island-1") { s.tapIsland(1) }
                tap("island-5") { s.tapIsland(5) }
                wait(1200)
            }) { t -> DemoHashi(s, t) }
        },
        TutorialStep(
            "Double bridges",
            "Connect the same two islands again to make a double bridge. Two is the most allowed between any pair.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                drag("island-0", "island-1", action = { s.bridge(0, 1) })
                drag("island-0", "island-1", action = { s.bridge(0, 1) })
                wait(300)
                drag("island-2", "island-3", action = { s.bridge(2, 3) })
                drag("island-2", "island-3", action = { s.bridge(2, 3) })
                wait(1200)
            }) { t -> DemoHashi(s, t) }
        },
        TutorialStep(
            "Connect them all",
            "Bridges can't cross each other. When every island has its number of bridges and they're all joined into one network, you've won.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                s.bridge(0, 1); s.bridge(0, 1); s.bridge(2, 3); s.bridge(2, 3); s.bridge(0, 2)
                wait(500)
                drag("island-3", "island-4", action = { s.bridge(3, 4) })
                drag("island-4", "island-5", action = { s.bridge(4, 5) })
                drag("island-1", "island-5", action = { s.bridge(1, 5) })
                drag("island-1", "island-5", action = { s.bridge(1, 5) })
                wait(1800)
            }) { t -> DemoHashi(s, t) }
        },
        TutorialStep(
            "Three strikes",
            "A bridge that doesn't belong in the solution is rejected and costs a heart. Three strikes ends the game.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                drag("island-4", "island-5", action = { s.bridge(4, 5) })
                drag("island-4", "island-5", action = { s.bridge(4, 5) })
                wait(500)
                highlight(listOf("middle"), 1200)
                wait(600)
            }) { t -> DemoHashi(s, t, status = true) }
        },
    )

    @Composable
    private fun DemoHashi(s: HashiSession, t: DemoTargets, status: Boolean = false) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            if (status) StatusBar(s.score, s.middleStat, s.elapsedMs, targets = t)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                HashiBoard(s, maxWidth, maxHeight, t)
            }
        }
    }
}

// ------------------------------------------------------------------- board

@Composable
private fun HashiBoard(s: HashiSession, maxW: Dp, maxH: Dp, targets: DemoTargets? = null) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val colors = LocalBoardColors.current
    val p = s.puzzle
    val cell = min(min(maxW / p.w, maxH / p.h), 64.dp)
    val density = LocalDensity.current
    val cellPx = with(density) { cell.toPx() }
    val radius = cell * 0.4f

    // the newest bridge grows from one island to the other
    val fx = s.bridgeFx
    val growAnim = remember(fx?.id) { Animatable(if (fx == null) 1f else 0f) }
    LaunchedEffect(fx?.id) { if (fx != null) growAnim.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }

    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragAt by remember { mutableStateOf(Offset.Zero) }

    fun center(i: Int) = Offset((p.islands[i].c + 0.5f) * cellPx, (p.islands[i].r + 0.5f) * cellPx)
    fun islandAt(o: Offset): Int {
        val k = p.islands.indices.minByOrNull { hypot(center(it).x - o.x, center(it).y - o.y) } ?: return -1
        return if (hypot(center(k).x - o.x, center(k).y - o.y) < cellPx * 0.6f) k else -1
    }

    Box(
        Modifier
            .size(cell * p.w, cell * p.h)
            .pointerInput(s, cellPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = islandAt(down.position)
                    var moved = false
                    var last = down.position
                    if (start >= 0) { dragFrom = start; dragAt = down.position }
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) { last = ch.position; break }
                        if ((ch.position - down.position).getDistance() > cellPx * 0.35f) moved = true
                        dragAt = ch.position
                        last = ch.position
                        ch.consume()
                    }
                    dragFrom = -1
                    val end = islandAt(last)
                    if (moved && start >= 0 && end >= 0 && end != start) { s.clearSelection(); s.bridge(start, end) }
                    else if (moved && start >= 0 && end < 0) s.nudge("Drag all the way onto another island")
                    else if (!moved && start < 0 && s.selected >= 0) s.nudge("Tap an island in line with the selected one")
                    else if (!moved && start >= 0) s.tapIsland(start)
                    else if (!moved) s.clearSelection()
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            @Suppress("UNUSED_VARIABLE") val ver = s.version // redraw bridges on every change
            // sea grid dots
            for (r in 0 until p.h) for (c in 0 until p.w) {
                drawCircle(colors.thinLine, 1.6.dp.toPx(), Offset((c + 0.5f) * cellPx, (r + 0.5f) * cellPx))
            }
            val rPx = radius.toPx()
            for ((e, edge) in p.edges.withIndex()) {
                val n = s.placed(e)
                if (n == 0) continue
                val a = center(edge.a); val b = center(edge.b)
                val dir = (b - a) / (b - a).getDistance()
                val start = a + dir * rPx
                val fullEnd = b - dir * rPx
                val g = if (fx != null && fx.edge == e) growAnim.value else 1f
                val perp = Offset(-dir.y, dir.x) * (cellPx * 0.09f)
                val stroke = 2.6.dp.toPx()
                if (n == 1) {
                    drawLine(colors.given, start, start + (fullEnd - start) * g, stroke, StrokeCap.Round)
                } else {
                    drawLine(colors.given, start - perp, fullEnd - perp, stroke, StrokeCap.Round)
                    drawLine(colors.given, start + perp, start + perp + (fullEnd - start) * g, stroke, StrokeCap.Round)
                }
            }
            if (dragFrom >= 0) {
                drawLine(colors.entered.copy(alpha = 0.6f), center(dragFrom), dragAt, 3.dp.toPx(), StrokeCap.Round)
            }
        }
        val sel = s.selected
        val nbrs = if (sel >= 0) s.neighbours(sel).toSet() else emptySet()
        for (i in p.islands.indices) {
            IslandView(s, i, cell, radius, isSelected = sel == i || dragFrom == i, isCandidate = i in nbrs, targets = targets)
        }
    }
}

@Composable
private fun IslandView(s: HashiSession, i: Int, cell: Dp, radius: Dp, isSelected: Boolean, isCandidate: Boolean, targets: DemoTargets?) {
    val colors = LocalBoardColors.current
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val isl = s.puzzle.islands[i]
    val done = s.complete(i)
    val bg by animateColorAsState(
        when { isSelected -> colors.entered; done -> colors.sameDigit; else -> colors.boardBg }, tween(200), label = "isl",
    )
    val pop = remember { Animatable(1f) }
    val dfx = s.doneFx
    LaunchedEffect(dfx?.id) {
        if (dfx != null && i in dfx.islands) { pop.snapTo(0.7f); pop.animateTo(1f, spring(0.4f, Spring.StiffnessMedium)) }
    }
    val shake = remember { Animatable(0f) }
    val efx = s.errorFx
    val err = remember { Animatable(0f) }
    LaunchedEffect(efx?.id) {
        if (efx != null && i in efx.islands) {
            err.snapTo(1f)
            shake.animateTo(0f, keyframes { durationMillis = 380; -8f at 50; 7f at 110; -5f at 170; 3f at 240; 0f at 380 })
            err.animateTo(0f, tween(400))
        }
    }
    val ring by animateFloatAsState(if (isCandidate) 1f else 0f, label = "ring")
    val density = LocalDensity.current
    val fs = with(density) { (radius * 0.95f).toSp() }
    Box(
        Modifier
            .offset(x = cell * isl.c + (cell / 2 - radius), y = cell * isl.r + (cell / 2 - radius))
            .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            .size(radius * 2)
            .demoTarget(targets, "island-$i")
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(CircleShape)
            .background(if (err.value > 0f) colors.errorBg else bg)
            .border(
                if (ring > 0f) 3.dp else 2.2.dp,
                if (err.value > 0f) colors.error else if (ring > 0f) colors.entered else colors.thickLine,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${isl.need}", fontSize = fs, fontFamily = Poppins, fontWeight = FontWeight.Bold,
            color = if (isSelected) colors.boardBg else colors.given,
        )
    }
}
