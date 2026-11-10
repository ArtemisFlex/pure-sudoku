package com.puresudoku.ui.games.crossword

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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.puresudoku.core.ScoreRules
import com.puresudoku.core.Scoring
import com.puresudoku.core.words.CrosswordEntry
import com.puresudoku.core.words.CrosswordPuzzle
import com.puresudoku.core.words.CrosswordRules
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.components.LetterKeyboard
import com.puresudoku.ui.components.StatusBar
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.ErrorFx
import com.puresudoku.ui.session.FxIds
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
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

class CrosswordSession(
    difficulty: Difficulty,
    val puzzle: CrosswordPuzzle,
    private val filled: CharArray = CharArray(puzzle.width * puzzle.height) { ' ' },
    private var streak: Int = 0,
) : Session(GameKind.CROSSWORD, difficulty) {
    private val cfg = CrosswordRules.config(difficulty)
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    val w = puzzle.width

    var version by mutableIntStateOf(0); private set
    var selected by mutableIntStateOf(-1); private set
    var across by mutableStateOf(true); private set
    var errorFx by mutableStateOf<ErrorFx?>(null); private set
    var waveFx by mutableStateOf<WaveFx?>(null); private set
    var popFx by mutableStateOf<ErrorFx?>(null); private set

    fun letter(i: Int) = filled[i]

    private fun entriesAt(i: Int) = puzzle.entries.filter { i in it.cells(w) }
    val entry: CrosswordEntry? get() = if (selected < 0) null else entriesAt(selected).let { list -> list.firstOrNull { it.across == across } ?: list.firstOrNull() }
    fun entryCells(): Set<Int> = entry?.cells(w)?.toSet() ?: emptySet()

    override val progress: Float get() = filled.count { it != ' ' } / puzzle.solution.count { it != ' ' }.coerceAtLeast(1).toFloat()

    fun select(i: Int) {
        if (outcome != Outcome.PLAYING || !puzzle.isOpen(i)) return
        val dirs = entriesAt(i).map { it.across }.toSet()
        if (i == selected && dirs.size == 2) across = !across
        else if (across !in dirs) across = dirs.first()
        selected = i
    }

    fun selectEntry(e: CrosswordEntry) {
        across = e.across
        selected = e.cells(w).firstOrNull { filled[it] == ' ' } ?: e.cells(w).first()
    }

    fun moveEntry(delta: Int) {
        val list = puzzle.entries.sortedWith(compareBy({ if (it.across) 0 else 1 }, { it.number }))
        val cur = entry
        val k = if (cur == null) 0 else (list.indexOf(cur) + delta).mod(list.size)
        selectEntry(list[k])
    }

    fun type(ch: Char) {
        if (outcome != Outcome.PLAYING) return
        val i = selected
        if (i < 0) { nudge("Tap a square in the grid first"); return }
        if (filled[i] != ' ') { advance(); return }
        if (puzzle.solution[i] != ch) {
            errorFx = ErrorFx(FxIds.next(), i, ch.code)
            streak = 0
            score -= minOf(score, rules.basePoints)
            version++
            if (!addStrike()) changed()
            return
        }
        place(i, scored = true)
        advance()
        if (outcome == Outcome.PLAYING) changed()
    }

    /** Hint: fills the selected square, costing points but no heart. */
    fun reveal() {
        if (outcome != Outcome.PLAYING) return
        val i = selected
        if (i < 0) { nudge("Select a square to reveal"); return }
        if (filled[i] != ' ') { nudge("That square is already filled"); return }
        score -= minOf(score, rules.basePoints * 2)
        place(i, scored = false)
        advance()
        if (outcome == Outcome.PLAYING) changed()
    }

    private fun place(i: Int, scored: Boolean) {
        filled[i] = puzzle.solution[i]
        popFx = ErrorFx(FxIds.next(), i, 0)
        if (scored) { streak++; score += Scoring.placement(rules, streak) }
        val done = entriesAt(i).filter { e -> e.cells(w).all { filled[it] != ' ' } }
        if (done.isNotEmpty()) {
            score += Scoring.unitBonus(rules) * done.size
            waveFx = WaveFx(FxIds.next(), i, done.flatMap { it.cells(w).toList() }.toSet())
        }
        version++
        if ((puzzle.solution.indices).all { puzzle.solution[it] == ' ' || filled[it] != ' ' }) {
            selected = -1
            waveFx = WaveFx(FxIds.next(), i, puzzle.solution.indices.filter { puzzle.isOpen(it) }.toSet())
            val b = Scoring.finish(rules, elapsedMs, strikes)
            finish(true, listOf(BonusLine("Time bonus", b.timeBonus), BonusLine("Hearts left bonus", b.livesBonus)))
        }
    }

    /** Moves to the next empty square of this answer, or the next unfinished answer. */
    private fun advance() {
        val e = entry ?: return
        val cells = e.cells(w)
        val after = cells.dropWhile { it != selected }.drop(1).firstOrNull { filled[it] == ' ' }
            ?: cells.firstOrNull { filled[it] == ' ' }
        if (after != null) { selected = after; return }
        val next = puzzle.entries.sortedWith(compareBy({ if (it.across) 0 else 1 }, { it.number }))
            .firstOrNull { x -> x.cells(w).any { filled[it] == ' ' } } ?: return
        selectEntry(next)
    }

    override fun saveState(): JSONObject = JSONObject()
        .put("w", puzzle.width).put("h", puzzle.height)
        .put("solution", String(puzzle.solution))
        .put("filled", String(filled))
        .put("streak", streak)
        .put("entries", JSONArray().also { a ->
            puzzle.entries.forEach { e -> a.put(JSONObject().put("n", e.number).put("r", e.row).put("c", e.col).put("a", e.across).put("w", e.answer).put("q", e.clue)) }
        })
}

object CrosswordDef : GameDef {
    override val kind = GameKind.CROSSWORD
    override fun background(session: com.puresudoku.ui.session.Session) = com.puresudoku.R.drawable.vbg_crossword
    private val rng = Random(SecureRandom().nextLong())

    override fun generate(d: Difficulty): Session = CrosswordSession(d, synchronized(rng) { CrosswordRules.generate(d, rng) })

    override fun restore(o: JSONObject): Session {
        val d = Difficulty.valueOf(o.getString("difficulty"))
        val st = o.getJSONObject("state")
        val arr = st.getJSONArray("entries")
        val entries = List(arr.length()) { k -> arr.getJSONObject(k).let { CrosswordEntry(it.getInt("n"), it.getInt("r"), it.getInt("c"), it.getBoolean("a"), it.getString("w"), it.getString("q")) } }
        val p = CrosswordPuzzle(st.getInt("w"), st.getInt("h"), st.getString("solution").toCharArray(), entries)
        return CrosswordSession(d, p, st.getString("filled").toCharArray(), st.optInt("streak")).restoreCommon(o)
    }

    override fun hint(d: Difficulty) = "Tap a square to see its clue; tap it again to switch across/down. Type the answer."
    override fun levelInfo(d: Difficulty) = CrosswordRules.config(d).let { "about ${it.words} words" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A mini crossword of everyday words."
        Difficulty.MEDIUM -> "A small grid, familiar words."
        Difficulty.HARD -> "More words, trickier clues."
        Difficulty.EXPERT -> "A big grid with a broad vocabulary."
        Difficulty.MASTER -> "Large grid, uncommon words."
        Difficulty.EXTREME -> "The biggest grid, the rarest words."
    }

    @Composable
    override fun Play(session: Session) = CrosswordLayout(session as CrosswordSession, null)

    @Composable
    override fun Icon(size: Dp) = GameIcon(kind, size)

    // ---------------------------------------------------------------- tutorial

    /*  Demo grid 5×5:
          C A T # #      1A CAT  "A small furry pet that purrs"
          A # O W L      1D CAR  "A vehicle with four wheels"
          R # E # #      2D TOE  "One of the digits on your foot"
                         3A OWL  "A bird that hoots at night"  (shares the O)   */
    private fun demo(): CrosswordSession {
        val w = 5; val h = 3
        val sol = ("CAT##" + "A#OWL" + "R#E##").map { if (it == '#') ' ' else it.lowercaseChar() }.toCharArray()
        val entries = listOf(
            CrosswordEntry(1, 0, 0, true, "cat", "A small furry pet that purrs"),
            CrosswordEntry(1, 0, 0, false, "car", "A vehicle with four wheels"),
            CrosswordEntry(2, 0, 2, false, "toe", "One of the digits on your foot"),
            CrosswordEntry(3, 1, 2, true, "owl", "A bird that hoots at night"),
        )
        return CrosswordSession(Difficulty.EASY, CrosswordPuzzle(w, h, sol, entries))
    }

    override fun lesson() = demo() to listOf(
        com.puresudoku.ui.session.LessonStep("Each numbered square starts a word. Tap a square to pick its word and read the clue. Tap it again to switch between across and down."),
        com.puresudoku.ui.session.LessonStep("Tap the top-left square. Clue 1 across: ‘A small furry pet that purrs’. Type CAT.", { (it as CrosswordSession).letter(0).lowercaseChar() == 'c' && (it as CrosswordSession).letter(1).lowercaseChar() == 'a' && (it as CrosswordSession).letter(2).lowercaseChar() == 't' }),
        com.puresudoku.ui.session.LessonStep("Now 1 down from the same square: ‘A vehicle with four wheels’. The C is already in place from CAT, which is how crosswords help you. Type CAR.", { (it as CrosswordSession).letter(5).lowercaseChar() == 'a' && (it as CrosswordSession).letter(10).lowercaseChar() == 'r' }),
        com.puresudoku.ui.session.LessonStep("2 down starts on the T: ‘One of the digits on your foot’. You know it begins with T. Fill it in.", { (it as CrosswordSession).letter(7).lowercaseChar() == 'o' && (it as CrosswordSession).letter(12).lowercaseChar() == 'e' }),
        com.puresudoku.ui.session.LessonStep("Last one, 3 across: ‘A bird that hoots at night’. Its first letter is shared with TOE. Solve the easy clues first, then let the crossing letters crack the hard ones.", { it.outcome == com.puresudoku.ui.session.Outcome.WON }),
    )

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Pick a clue", "Tap a square to select its word. The clue appears above the keyboard. Tap the same square again to switch between across and down.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                tap("cell-0") { s.select(0) }
                highlight(listOf("clue"), 1300)
                tap("cell-0") { s.select(0) }
                highlight(listOf("clue"), 1300)
            }) { t -> CrosswordLayout(s, t) }
        },
        TutorialStep("Type the answer", "Type letters on the keyboard. The cursor moves along the word for you, and finished words light up.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                tap("cell-0") { s.select(0) }
                for (c in "cat") tap("kb-$c") { s.type(c) }
                wait(700)
                // the cursor has jumped to the next unfinished word (3 Across)
                highlight(listOf("clue"), 1000)
                for (c in "owl") tap("kb-$c") { s.type(c) }
                wait(1200)
            }) { t -> CrosswordLayout(s, t) }
        },
        TutorialStep("Strikes and hints", "A wrong letter is rejected and costs a heart; three strikes ends the game. Stuck? Reveal fills one square for some points, but no heart.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                tap("cell-2") { s.select(2); s.select(2) }
                tap("kb-t") { s.type('t') }
                tap("kb-a") { s.type('a') }
                wait(500)
                highlight(listOf("middle"), 1000)
                tap("reveal") { s.reveal() }
                wait(1200)
            }) { t -> CrosswordLayout(s, t, status = true) }
        },
    )
}

// ------------------------------------------------------------------- UI

@Composable
private fun CrosswordLayout(s: CrosswordSession, targets: DemoTargets?, status: Boolean = false) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (status) StatusBar(s.score, s.middleStat, s.elapsedMs, targets = targets)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val p = s.puzzle
            val cell = min(min(maxWidth / p.width, maxHeight / p.height), 52.dp)
            CrosswordGrid(s, cell, targets)
        }
        Spacer(Modifier.height(8.dp))
        ClueBar(s, targets)
        Spacer(Modifier.height(8.dp))
        LetterKeyboard({ s.type(it) }, targets = targets, keyHeight = if (targets != null) 34 else 46)
        Spacer(Modifier.height(if (targets == null) 12.dp else 2.dp))
    }
}

@Composable
private fun ClueBar(s: CrosswordSession, targets: DemoTargets?) {
    val e = s.entry
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface).demoTarget(targets, "clue"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { s.moveEntry(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous clue") }
        AnimatedContent(e, Modifier.weight(1f), label = "clue", transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) }) { entry ->
            if (entry == null) Text("Tap a square to see its clue", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            else Column {
                Text("${entry.number} ${if (entry.across) "ACROSS" else "DOWN"} · ${entry.answer.length} letters", fontSize = 11.sp, letterSpacing = 1.sp,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text(entry.clue, fontSize = 15.sp, lineHeight = 19.sp)
            }
        }
        TextButton(onClick = s::reveal, modifier = Modifier.demoTarget(targets, "reveal")) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Reveal", fontSize = 13.sp)
        }
        IconButton(onClick = { s.moveEntry(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next clue") }
    }
}

@Composable
private fun CrosswordGrid(s: CrosswordSession, cell: Dp, targets: DemoTargets?) {
    val p = s.puzzle
    val sel = s.selected
    val word = s.entryCells()
    Column(Modifier.clip(RoundedCornerShape(8.dp)).background(LocalBoardColors.current.thickLine)) {
        for (r in 0 until p.height) Row {
            for (c in 0 until p.width) {
                val i = r * p.width + c
                if (!p.isOpen(i)) Box(Modifier.size(cell).background(LocalBoardColors.current.thickLine))
                else CrossCell(s, i, cell, i == sel, i in word, targets)
            }
        }
    }
}

@Composable
private fun CrossCell(s: CrosswordSession, i: Int, size: Dp, selected: Boolean, inWord: Boolean, targets: DemoTargets?) {
    val colors = LocalBoardColors.current
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val bg by animateColorAsState(when { selected -> colors.selected; inWord -> colors.sameDigit; else -> colors.boardBg }, tween(160), label = "cc")
    val pop = remember { Animatable(1f) }
    val pfx = s.popFx
    LaunchedEffect(pfx?.id) { if (pfx != null && pfx.cell == i) { pop.snapTo(0.4f); pop.animateTo(1f, spring(0.45f, Spring.StiffnessMedium)) } }
    val wave = remember { Animatable(0f) }
    val wfx = s.waveFx
    LaunchedEffect(wfx?.id) {
        if (wfx != null && i in wfx.cells) {
            val w = s.w
            delay(max(abs(wfx.origin / w - i / w), abs(wfx.origin % w - i % w)) * 45L)
            wave.animateTo(1f, tween(160, easing = FastOutSlowInEasing)); wave.animateTo(0f, tween(420, easing = LinearOutSlowInEasing))
        }
    }
    val shake = remember { Animatable(0f) }
    val err = remember { Animatable(0f) }
    var errCh by remember { mutableStateOf(' ') }
    val efx = s.errorFx
    LaunchedEffect(efx?.id) {
        if (efx != null && efx.cell == i) {
            errCh = efx.digit.toChar(); err.snapTo(1f)
            shake.animateTo(0f, keyframes { durationMillis = 380; -8f at 50; 7f at 110; -5f at 170; 3f at 240; 0f at 380 })
            err.animateTo(0f, tween(450)); errCh = ' '
        }
    }
    val density = LocalDensity.current
    val fs = with(density) { (size * 0.56f).toSp() }
    val ns = with(density) { (size * 0.26f).toSp() }
    Box(
        Modifier.size(size).padding(0.5.dp)
            .demoTarget(targets, "cell-$i")
            .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            .background(if (err.value > 0f) colors.errorBg else bg)
            .background(colors.wave.copy(alpha = 0.8f * wave.value))
            .clickable(remember { MutableInteractionSource() }, null) { s.select(i) },
        contentAlignment = Alignment.Center,
    ) {
        val num = s.puzzle.numberAt[i]
        if (num > 0) Text("$num", Modifier.align(Alignment.TopStart).padding(start = 2.dp), fontSize = ns, lineHeight = ns, color = colors.note)
        val ch = s.letter(i)
        if (errCh != ' ') Text(errCh.uppercase(), fontSize = fs, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, color = colors.error.copy(alpha = err.value))
        else if (ch != ' ') Text(
            ch.uppercase(), fontSize = fs, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, color = colors.given,
            modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
    }
}
