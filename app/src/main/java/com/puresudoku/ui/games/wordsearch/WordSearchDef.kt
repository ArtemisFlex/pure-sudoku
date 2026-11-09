package com.puresudoku.ui.games.wordsearch

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.ScoreRules
import com.puresudoku.core.Scoring
import com.puresudoku.core.ints
import com.puresudoku.core.toJson
import com.puresudoku.core.words.WordSearchPlacement
import com.puresudoku.core.words.WordSearchPuzzle
import com.puresudoku.core.words.WordSearchRules
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.components.StatusBar
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.FxIds
import com.puresudoku.ui.session.MiddleStat
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
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

private val BAND_COLORS = listOf(
    Color(0x663F67D6), Color(0x66E8A93A), Color(0x663FA66B), Color(0x66D9619F),
    Color(0x667A4FD0), Color(0x662F8FB5), Color(0x66E05A47), Color(0x668C6A4F),
)

data class SelectFx(val id: Long, val cells: List<Int>)

class WordSearchSession(
    difficulty: Difficulty,
    val puzzle: WordSearchPuzzle,
    /** For each word: (startCell, endCell) once found, or null. */
    private val foundAt: Array<IntArray?> = arrayOfNulls(puzzle.words.size),
) : Session(GameKind.WORD_SEARCH, difficulty) {
    private val cfg = WordSearchRules.config(difficulty)
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    val n = puzzle.size

    var version by mutableIntStateOf(0); private set
    var preview by mutableStateOf<IntArray?>(null); private set
    var errorFx by mutableStateOf<SelectFx?>(null); private set
    var foundFx by mutableStateOf<SelectFx?>(null); private set

    fun found(k: Int) = foundAt[k]
    val foundCount get() = foundAt.count { it != null }

    override val usesStrikes get() = false
    override val middleStat get() = MiddleStat.Counter("Found", "$foundCount/${puzzle.words.size}")
    override val progress: Float get() = foundCount / puzzle.words.size.toFloat()

    /** Cells on the straight line from [a] to [b], or null if not straight. */
    fun line(a: Int, b: Int): List<Int>? {
        val dr = b / n - a / n; val dc = b % n - a % n
        if (dr != 0 && dc != 0 && abs(dr) != abs(dc)) return null
        val len = max(abs(dr), abs(dc))
        val sr = dr.coerceIn(-1, 1); val sc = dc.coerceIn(-1, 1)
        return List(len + 1) { k -> (a / n + sr * k) * n + (a % n + sc * k) }
    }

    fun setPreview(a: Int, b: Int) { preview = line(a, b)?.let { intArrayOf(a, it.last()) } }
    fun clearPreview() { preview = null }

    fun select(a: Int, b: Int) {
        preview = null
        if (outcome != Outcome.PLAYING) return
        val cells = line(a, b)
        if (cells == null) { nudge("Words run in straight lines: across, down or diagonal"); return }
        if (cells.size < 2) return
        val text = cells.map { puzzle.letters[it] }.joinToString("")
        val k = puzzle.words.indices.firstOrNull { foundAt[it] == null && (puzzle.words[it].word == text || puzzle.words[it].word == text.reversed()) }
        if (k == null) {
            errorFx = SelectFx(FxIds.next(), cells)
            val already = puzzle.words.indices.any { foundAt[it] != null && puzzle.words[it].word.let { w -> w == text || w == text.reversed() } }
            nudge(if (already) "You've already found that one" else "\"${text.uppercase()}\" isn't one of the hidden words")
            return
        }
        foundAt[k] = intArrayOf(cells.first(), cells.last())
        score += rules.basePoints * puzzle.words[k].word.length
        foundFx = SelectFx(FxIds.next(), cells)
        version++
        if (foundCount == puzzle.words.size) {
            val b2 = Scoring.finish(rules, elapsedMs, 0)
            finish(true, listOf(BonusLine("Time bonus", b2.timeBonus)))
        } else changed()
    }

    override fun saveState(): JSONObject = JSONObject()
        .put("size", n).put("letters", String(puzzle.letters)).put("theme", puzzle.theme)
        .put("words", JSONArray().also { a -> puzzle.words.forEach { a.put(JSONObject().put("w", it.word).put("r", it.row).put("c", it.col).put("dr", it.dr).put("dc", it.dc)) } })
        .put("found", JSONArray().also { a -> foundAt.forEach { a.put(it?.toJson() ?: JSONArray()) } })
}

object WordSearchDef : GameDef {
    override val kind = GameKind.WORD_SEARCH
    private val rng = Random(SecureRandom().nextLong())

    override fun generate(d: Difficulty): Session = WordSearchSession(d, synchronized(rng) { WordSearchRules.generate(d, rng) })

    override fun restore(o: JSONObject): Session {
        val d = Difficulty.valueOf(o.getString("difficulty"))
        val st = o.getJSONObject("state")
        val wa = st.getJSONArray("words")
        val words = List(wa.length()) { k -> wa.getJSONObject(k).let { WordSearchPlacement(it.getString("w"), it.getInt("r"), it.getInt("c"), it.getInt("dr"), it.getInt("dc")) } }
        val p = WordSearchPuzzle(st.getInt("size"), st.getString("letters").toCharArray(), st.getString("theme"), words)
        val fa = st.getJSONArray("found")
        val found = Array(words.size) { k -> fa.getJSONArray(k).let { if (it.length() == 2) intArrayOf(it.getInt(0), it.getInt(1)) else null } }
        return WordSearchSession(d, p, found).restoreCommon(o)
    }

    override fun hint(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Drag across the letters to circle a word. Words run left to right or top to bottom."
        Difficulty.MEDIUM -> "Drag to circle words. They run across, down or diagonally down-right."
        Difficulty.HARD -> "Drag to circle words. They run across, down or diagonally."
        else -> "Drag to circle words. They can run in any direction, even backwards."
    }

    override fun levelInfo(d: Difficulty) = WordSearchRules.config(d).let { "${it.size}×${it.size} · ${it.words} words" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Across and down only."
        Difficulty.MEDIUM -> "Adds one diagonal."
        Difficulty.HARD -> "All forward diagonals."
        Difficulty.EXPERT -> "Backwards too."
        Difficulty.MASTER -> "Bigger grid, every direction."
        Difficulty.EXTREME -> "The biggest grid, sixteen words."
    }

    @Composable
    override fun Play(session: Session) = SearchLayout(session as WordSearchSession, null)

    @Composable
    override fun Icon(size: Dp) = GameIcon(kind, size)

    // ---------------------------------------------------------------- tutorial

    private fun demo(): WordSearchSession {
        val letters = ("catxpm" + "dqorsn" + "ouhwzk" + "gyijla" + "feebvt" + "hnrmqs").toCharArray()
        val words = listOf(
            WordSearchPlacement("cat", 0, 0, 0, 1), WordSearchPlacement("dog", 1, 0, 1, 0),
            WordSearchPlacement("owl", 1, 2, 1, 1), WordSearchPlacement("bee", 4, 3, 0, -1),
        )
        return WordSearchSession(Difficulty.EXPERT, WordSearchPuzzle(6, letters, "Animals", words))
    }

    private suspend fun com.puresudoku.ui.tutorial.DemoScope.circle(s: WordSearchSession, a: Int, b: Int) {
        val cells = s.line(a, b) ?: return
        drag("cell-$a", "cell-$b", durationMs = 260 * cells.size,
            onMove = { f -> s.setPreview(a, cells[((cells.size - 1) * f).roundToInt()]) },
            action = { s.select(a, b) })
    }

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Find the words", "Every word in the list is hidden in the grid. Drag from the first letter to the last to circle it.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                highlight(listOf("list"), 1200)
                circle(s, 0, 2)
                wait(400)
                circle(s, 6, 18)
                wait(1200)
            }) { t -> SearchLayout(s, t) }
        },
        TutorialStep("Any direction", "Words can run across, down, diagonally and, on harder levels, backwards. Easy only uses across and down.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                circle(s, 8, 22)
                wait(400)
                circle(s, 27, 25)
                wait(1200)
            }) { t -> SearchLayout(s, t) }
        },
        TutorialStep("Misses", "Circling letters that aren't a hidden word just shakes; there's no penalty. Find every word to win, faster for a bigger bonus.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                circle(s, 12, 14)
                wait(1400)
            }) { t -> SearchLayout(s, t) }
        },
    )
}

// ------------------------------------------------------------------- UI

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchLayout(s: WordSearchSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val colors = LocalBoardColors.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("THEME: ${s.puzzle.theme.uppercase()}", fontSize = 12.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = min(min(maxWidth, maxHeight), 520.dp)
            SearchGrid(s, side, targets)
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            Modifier.fillMaxWidth().demoTarget(targets, "list"),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            s.puzzle.words.forEachIndexed { k, w ->
                val got = s.found(k) != null
                val bg by animateColorAsState(if (got) BAND_COLORS[k % BAND_COLORS.size] else MaterialTheme.colorScheme.surface, label = "chip")
                Text(
                    w.word.uppercase(), fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    textDecoration = if (got) TextDecoration.LineThrough else null,
                    color = if (got) colors.note else colors.given,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
        Spacer(Modifier.height(if (targets == null) 16.dp else 4.dp))
    }
}

@Composable
private fun SearchGrid(s: WordSearchSession, side: Dp, targets: DemoTargets?) {
    val colors = LocalBoardColors.current
    val n = s.n
    val cell = side / n
    val density = LocalDensity.current
    val cellPx = with(density) { cell.toPx() }
    val fs = with(density) { (cell * 0.5f).toSp() }
    val shake = remember { Animatable(0f) }
    val efx = s.errorFx
    LaunchedEffect(efx?.id) { if (efx != null) shake.animateTo(0f, keyframes { durationMillis = 380; -8f at 50; 7f at 110; -5f at 170; 3f at 240; 0f at 380 }) }
    val glow = remember { Animatable(0f) }
    val ffx = s.foundFx
    LaunchedEffect(ffx?.id) { if (ffx != null) { glow.snapTo(1f); glow.animateTo(0f, tween(700)) } }

    fun center(i: Int) = Offset((i % n + 0.5f) * cellPx, (i / n + 0.5f) * cellPx)
    Box(
        Modifier.size(side).clip(RoundedCornerShape(14.dp)).background(colors.boardBg)
            .pointerInput(s, cellPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun at(o: Offset): Int {
                        val c = (o.x / cellPx).toInt().coerceIn(0, n - 1); val r = (o.y / cellPx).toInt().coerceIn(0, n - 1)
                        return r * n + c
                    }
                    val start = at(down.position)
                    var end = start
                    s.setPreview(start, start)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        // snap the finger to the nearest of the 8 straight directions
                        val d = ch.position - center(start)
                        val len = (max(abs(d.x), abs(d.y)) / cellPx).roundToInt()
                        val ang = Math.round(atan2(d.y.toDouble(), d.x.toDouble()) / (Math.PI / 4)).toInt()
                        val dirs = listOf(0 to 1, 1 to 1, 1 to 0, 1 to -1, 0 to -1, -1 to -1, -1 to 0, -1 to 1)
                        val (dr, dc) = dirs[((ang % 8) + 8) % 8]
                        var k = len
                        while (k > 0) {
                            val r = start / n + dr * k; val c = start % n + dc * k
                            if (r in 0 until n && c in 0 until n) break
                            k--
                        }
                        end = (start / n + dr * k) * n + (start % n + dc * k)
                        s.setPreview(start, end)
                    }
                    s.select(start, end)
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = cellPx * 0.78f
            for (k in s.puzzle.words.indices) {
                val f = s.found(k) ?: continue
                drawLine(BAND_COLORS[k % BAND_COLORS.size], center(f[0]), center(f[1]), stroke, StrokeCap.Round)
            }
            s.preview?.let { pv ->
                drawLine(colors.entered.copy(alpha = 0.35f), center(pv[0]), center(pv[1]), stroke, StrokeCap.Round)
            }
            efx?.let { e ->
                if (shake.value != 0f) drawLine(colors.error.copy(alpha = 0.35f), center(e.cells.first()) + Offset(shake.value * density, 0f),
                    center(e.cells.last()) + Offset(shake.value * density, 0f), stroke, StrokeCap.Round)
            }
        }
        Column {
            for (r in 0 until n) Row {
                for (c in 0 until n) {
                    val i = r * n + c
                    val inFound = ffx != null && i in ffx.cells
                    Box(
                        Modifier.size(cell).demoTarget(targets, "cell-$i")
                            .graphicsLayer { if (inFound) { val k = 1f + 0.25f * glow.value; scaleX = k; scaleY = k } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(s.puzzle.letters[i].uppercase(), fontSize = fs, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, color = colors.given)
                    }
                }
            }
        }
    }
}
