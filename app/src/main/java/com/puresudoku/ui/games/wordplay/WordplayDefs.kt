package com.puresudoku.ui.games.wordplay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.puresudoku.core.words.LadderPuzzle
import com.puresudoku.core.words.LadderRules
import com.puresudoku.core.words.WheelPuzzle
import com.puresudoku.core.words.WheelRules
import com.puresudoku.ui.components.ActionButton
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.components.LetterKeyboard
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// =====================================================================
// Word Ladder
// =====================================================================

class LadderSession(
    difficulty: Difficulty,
    val puzzle: LadderPuzzle,
    rungs: List<String> = listOf(puzzle.start),
    private var streak: Int = 0,
) : Session(GameKind.WORD_LADDER, difficulty) {
    private val cfg = LadderRules.config(difficulty)
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    private val dict: Set<String> by lazy { LadderRules.valid(puzzle.start.length) }
    val rungs = rungs.toMutableList()

    var version by mutableIntStateOf(0); private set
    var slot by mutableIntStateOf(-1); private set
    var errorFx by mutableStateOf(0L); private set
    var addFx by mutableStateOf(0L); private set

    val current get() = rungs.last()
    val steps get() = rungs.size - 1
    override val progress: Float get() {
        val t = puzzle.end
        return current.indices.count { current[it] == t[it] } / t.length.toFloat()
    }

    fun pickSlot(i: Int) { if (outcome == Outcome.PLAYING) { slot = i; version++ } }

    fun key(ch: Char) {
        if (outcome != Outcome.PLAYING) return
        if (slot < 0) { nudge("First tap the letter in your word you want to change"); return }
        if (current[slot] == ch) { nudge("That's the same letter — pick a different one"); return }
        val next = current.substring(0, slot) + ch + current.substring(slot + 1)
        if (next in rungs) { errorFx = FxIds.next(); nudge("You've already used \"${next.uppercase()}\""); return }
        if (next !in dict) {
            errorFx = FxIds.next()
            streak = 0
            score -= minOf(score, rules.basePoints)
            nudge("\"${next.uppercase()}\" isn't a word in our dictionary")
            version++
            if (!addStrike()) changed()
            return
        }
        rungs += next
        streak++
        score += Scoring.placement(rules, streak)
        addFx = FxIds.next()
        slot = -1
        version++
        if (next == puzzle.end) {
            val stepScore = cfg.basePoints * 20 * puzzle.par / maxOf(puzzle.par, steps)
            val b = Scoring.finish(rules, elapsedMs, strikes)
            finish(true, listOf(BonusLine(if (steps <= puzzle.par) "Par bonus" else "Steps bonus", stepScore),
                BonusLine("Time bonus", b.timeBonus), BonusLine("Hearts left bonus", b.livesBonus)))
        } else changed()
    }

    fun undo() {
        if (outcome != Outcome.PLAYING) return
        if (rungs.size <= 1) { nudge("Nothing to undo yet"); return }
        rungs.removeAt(rungs.lastIndex); slot = -1; version++; changed()
    }

    override val lostMessage: String get() = "Three strikes. One way was: ${puzzle.example.joinToString(" → ") { it.uppercase() }}."

    override fun saveState(): JSONObject = JSONObject()
        .put("start", puzzle.start).put("end", puzzle.end).put("par", puzzle.par)
        .put("example", JSONArray(puzzle.example)).put("rungs", JSONArray(rungs)).put("streak", streak)
}

object LadderDef : GameDef {
    override val kind = GameKind.WORD_LADDER
    private val rng = Random(SecureRandom().nextLong())

    override fun generate(d: Difficulty): Session = LadderSession(d, synchronized(rng) { LadderRules.generate(d, rng) })

    override fun restore(o: JSONObject): Session {
        val d = Difficulty.valueOf(o.getString("difficulty"))
        val st = o.getJSONObject("state")
        fun list(k: String) = st.getJSONArray(k).let { a -> List(a.length()) { a.getString(it) } }
        val p = LadderPuzzle(st.getString("start"), st.getString("end"), st.getInt("par"), list("example"))
        return LadderSession(d, p, list("rungs"), st.optInt("streak")).restoreCommon(o)
    }

    override fun hint(d: Difficulty) = "Tap a letter in your word, then a new letter, to make a real word. Reach the goal word."
    override fun levelInfo(d: Difficulty) = LadderRules.config(d).let { "${it.length}-letter words" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Short words, short ladders."
        Difficulty.MEDIUM -> "Four-letter words."
        Difficulty.HARD -> "Longer climbs."
        Difficulty.EXPERT -> "Up to eight steps."
        Difficulty.MASTER -> "Five-letter words."
        Difficulty.EXTREME -> "Five letters, ten steps."
    }

    @Composable
    override fun Play(session: Session) = LadderLayout(session as LadderSession, null)

    @Composable
    override fun Icon(size: Dp) = GameIcon(kind, size)

    private fun demo() = LadderSession(Difficulty.EASY, LadderPuzzle("cat", "dog", 3, listOf("cat", "cot", "dot", "dog")))

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Change one letter", "Turn the top word into the goal word. Tap a letter in your word, then pick a new letter on the keyboard. Every step must be a real word.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                highlight(listOf("goal"), 1100)
                tap("slot-1") { s.pickSlot(1) }
                tap("kb-o") { s.key('o') }
                wait(600)
                tap("slot-0") { s.pickSlot(0) }
                tap("kb-d") { s.key('d') }
                wait(1200)
            }) { t -> LadderLayout(s, t) }
        },
        TutorialStep("Reach the goal", "Letters that already match the goal turn blue. Reach the goal in as few steps as you can: par is the shortest possible ladder.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); s.pickSlot(1); s.key('o'); s.pickSlot(0); s.key('d')
                wait(600)
                tap("slot-2") { s.pickSlot(2) }
                tap("kb-g") { s.key('g') }
                wait(1600)
            }) { t -> LadderLayout(s, t) }
        },
        TutorialStep("Strikes and undo", "A made-up word is rejected and costs a heart; three strikes ends the game. Undo steps back a rung for free.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                tap("slot-0") { s.pickSlot(0) }
                tap("kb-x") { s.key('x') }
                wait(500)
                highlight(listOf("middle"), 1000)
                tap("kb-b") { s.key('b') }
                wait(600)
                tap("undo") { s.undo() }
                wait(1200)
            }) { t -> LadderLayout(s, t, status = true) }
        },
    )
}

@Composable
private fun LadderLayout(s: LadderSession, targets: DemoTargets?, status: Boolean = false) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val colors = LocalBoardColors.current
    val goal = s.puzzle.end
    val scroll = rememberScrollState()
    LaunchedEffect(s.rungs.size) { scroll.animateScrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (status) StatusBar(s.score, s.middleStat, s.elapsedMs, targets = targets)
        Row(Modifier.demoTarget(targets, "goal"), verticalAlignment = Alignment.CenterVertically) {
            Text("GOAL  ", fontSize = 12.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            WordTiles(goal, 34.dp, highlight = goal.indices.toSet(), strong = true)
            Text("   ${s.steps} step${if (s.steps == 1) "" else "s"} · par ${s.puzzle.par}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (k in s.rungs.indices) {
                val w = s.rungs[k]
                val isCurrent = k == s.rungs.lastIndex
                val match = w.indices.filter { w[it] == goal[it] }.toSet()
                if (!isCurrent) WordTiles(w, 38.dp, highlight = match, dim = true)
                else CurrentRung(s, w, match, targets)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            ActionButton(Icons.AutoMirrored.Outlined.Undo, "Undo", enabled = s.rungs.size > 1, modifier = Modifier.demoTarget(targets, "undo"), onClick = s::undo)
        }
        LetterKeyboard({ s.key(it) }, targets = targets, keyHeight = if (targets != null) 34 else 46)
        Spacer(Modifier.height(if (targets == null) 12.dp else 2.dp))
    }
}

@Composable
private fun CurrentRung(s: LadderSession, w: String, match: Set<Int>, targets: DemoTargets?) {
    val colors = LocalBoardColors.current
    val shake = remember { Animatable(0f) }
    LaunchedEffect(s.errorFx) { if (s.errorFx != 0L) shake.animateTo(0f, keyframes { durationMillis = 380; -10f at 50; 9f at 110; -6f at 170; 3f at 240; 0f at 380 }) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(s.addFx) { if (s.addFx != 0L) { pop.snapTo(0.7f); pop.animateTo(1f, spring(0.45f, Spring.StiffnessMedium)) } }
    Row(
        Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) }.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        w.forEachIndexed { i, ch ->
            val sel = i == s.slot
            val bg by animateColorAsState(when { sel -> colors.selected; i in match -> colors.sameDigit; else -> colors.boardBg }, label = "rung")
            Box(
                Modifier.size(52.dp).demoTarget(targets, "slot-$i").clip(RoundedCornerShape(12.dp)).background(bg)
                    .border(if (sel) 2.5.dp else 1.5.dp, if (sel) colors.entered else colors.thickLine, RoundedCornerShape(12.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { s.pickSlot(i) },
                contentAlignment = Alignment.Center,
            ) { Text(ch.uppercase(), fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = colors.given) }
        }
    }
}

@Composable
private fun WordTiles(w: String, size: Dp, highlight: Set<Int>, dim: Boolean = false, strong: Boolean = false) {
    val colors = LocalBoardColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.graphicsLayer { alpha = if (dim) 0.75f else 1f }) {
        w.forEachIndexed { i, ch ->
            Box(
                Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(if (strong) colors.entered else if (i in highlight) colors.sameDigit else colors.related),
                contentAlignment = Alignment.Center,
            ) { Text(ch.uppercase(), fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.48f).sp, color = if (strong) colors.boardBg else colors.given) }
        }
    }
}

// =====================================================================
// Word Wheel
// =====================================================================

class WheelSession(
    difficulty: Difficulty,
    val puzzle: WheelPuzzle,
    found: List<String> = emptyList(),
) : Session(GameKind.WORD_WHEEL, difficulty) {
    private val cfg = WheelRules.config(difficulty)
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    val found = found.toMutableList()

    var version by mutableIntStateOf(0); private set
    var entry by mutableStateOf(""); private set
    var outer by mutableStateOf(puzzle.letters); private set
    var errorFx by mutableStateOf(0L); private set
    var goodFx by mutableStateOf(0L); private set
    var lastPangram by mutableStateOf(false); private set

    override val usesStrikes get() = false
    override val middleStat get() = MiddleStat.Counter("Found", "${found.size}/${puzzle.goal}")
    override val progress: Float get() = (found.size / puzzle.goal.toFloat()).coerceAtMost(1f)

    fun tap(c: Char) { if (outcome == Outcome.PLAYING && entry.length < 15) entry += c }
    fun delete() { if (entry.isNotEmpty()) entry = entry.dropLast(1) }
    fun shuffle() { outer = outer.toList().shuffled().joinToString("") }

    fun submit() {
        if (outcome != Outcome.PLAYING) return
        val w = entry
        fun reject(msg: String) { errorFx = FxIds.next(); nudge(msg) }
        when {
            w.isEmpty() -> { nudge("Tap letters to spell a word first"); return }
            w.length < 4 -> { reject("Words need at least 4 letters"); return }
            puzzle.center !in w -> { reject("Every word must use the centre letter, ${puzzle.center.uppercase()}"); return }
            w in found -> { reject("You already found \"${w.uppercase()}\""); entry = ""; return }
            w !in puzzle.answers -> { reject("\"${w.uppercase()}\" isn't in our word list"); return }
        }
        val pangram = w in puzzle.pangrams
        found += w
        lastPangram = pangram
        score += WheelRules.points(w, pangram) * cfg.basePoints / 2
        entry = ""
        goodFx = FxIds.next()
        version++
        if (pangram) nudge("Pangram! Every letter used")
        if (found.size >= puzzle.goal) {
            val b = Scoring.finish(rules, elapsedMs, 0)
            finish(true, listOf(BonusLine("Time bonus", b.timeBonus)))
        } else changed()
    }

    override val wonTitle get() = "Goal reached!"

    override fun saveState(): JSONObject = JSONObject()
        .put("letters", puzzle.letters).put("center", puzzle.center.toString())
        .put("answers", JSONArray(puzzle.answers)).put("pangrams", JSONArray(puzzle.pangrams.toList()))
        .put("goal", puzzle.goal).put("found", JSONArray(found))
}

object WheelDef : GameDef {
    override val kind = GameKind.WORD_WHEEL
    private val rng = Random(SecureRandom().nextLong())

    override fun generate(d: Difficulty): Session = WheelSession(d, synchronized(rng) { WheelRules.generate(d, rng) })

    override fun restore(o: JSONObject): Session {
        val d = Difficulty.valueOf(o.getString("difficulty"))
        val st = o.getJSONObject("state")
        fun list(k: String) = st.getJSONArray(k).let { a -> List(a.length()) { a.getString(it) } }
        val p = WheelPuzzle(st.getString("letters"), st.getString("center")[0], list("answers"), list("pangrams").toSet(), st.getInt("goal"))
        return WheelSession(d, p, list("found")).restoreCommon(o)
    }

    override fun hint(d: Difficulty) = "Tap letters to spell words of 4+ letters that use the centre letter. Letters can repeat."
    override fun levelInfo(d: Difficulty) = "find ${WheelRules.config(d).goalPct}% of the words"
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Find a quarter of the words."
        Difficulty.MEDIUM -> "Find over a third."
        Difficulty.HARD -> "Find almost half."
        Difficulty.EXPERT -> "Find more than half."
        Difficulty.MASTER -> "Find two thirds."
        Difficulty.EXTREME -> "Find four in five."
    }

    @Composable
    override fun Play(session: Session) = WheelLayout(session as WheelSession, null)

    @Composable
    override fun Icon(size: Dp) = GameIcon(kind, size)

    private fun demo() = WheelSession(
        Difficulty.EASY,
        WheelPuzzle("plnets", 'a', listOf("plan", "plane", "planet", "planets", "plate", "late", "lean", "neat", "slate", "petal", "pant", "seat", "tale"), setOf("planets"), 5),
    )

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Spell a word", "Tap letters to spell a word of four or more letters, then tap Enter. Every word must use the blue centre letter. Letters can be used more than once.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                for (c in "plan") tap("wl-$c") { s.tap(c) }
                tap("enter") { s.submit() }
                wait(500)
                for (c in "late") tap("wl-$c") { s.tap(c) }
                tap("enter") { s.submit() }
                wait(1200)
            }) { t -> WheelLayout(s, t) }
        },
        TutorialStep("Pangrams", "A word that uses all seven letters is a pangram and scores a big bonus. Reach the goal number of words to win.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                for (c in "planets") tap("wl-$c") { s.tap(c) }
                tap("enter") { s.submit() }
                wait(500)
                highlight(listOf("progress"), 1300)
            }) { t -> WheelLayout(s, t) }
        },
        TutorialStep("No penalties", "Words without the centre letter, too short or not in the word list just shake. There are no strikes here, so experiment freely.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                for (c in "pest") tap("wl-$c") { s.tap(c) }
                tap("enter") { s.submit() }
                wait(900)
                tap("del") { s.delete() }; tap("del") { s.delete() }; tap("del") { s.delete() }; tap("del") { s.delete() }
                tap("shuffle") { s.shuffle() }
                wait(1000)
            }) { t -> WheelLayout(s, t) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WheelLayout(s: WheelSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val colors = LocalBoardColors.current
    val shake = remember { Animatable(0f) }
    LaunchedEffect(s.errorFx) { if (s.errorFx != 0L) shake.animateTo(0f, keyframes { durationMillis = 380; -12f at 50; 10f at 110; -7f at 170; 4f at 240; 0f at 380 }) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(s.goodFx) { if (s.goodFx != 0L) { pop.snapTo(1.25f); pop.animateTo(1f, spring(0.4f, Spring.StiffnessMediumLow)) } }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().demoTarget(targets, "progress"), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${s.found.size} of ${s.puzzle.goal} words to win · ${s.puzzle.answers.size} possible", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.padding(top = 6.dp).fillMaxWidth(0.7f).height(6.dp).clip(RoundedCornerShape(50)))
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            Modifier.fillMaxWidth().weight(0.6f).verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (w in s.found.reversed()) {
                Text(w.uppercase(), fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = if (w in s.puzzle.pangrams) colors.boardBg else colors.given,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (w in s.puzzle.pangrams) colors.entered else colors.sameDigit).padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        Text(
            s.entry.uppercase().ifEmpty { " " }, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 3.sp,
            color = colors.given,
            modifier = Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) }.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = min(min(maxWidth, maxHeight), 300.dp)
            Wheel(s, side, targets)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = s::delete, modifier = Modifier.demoTarget(targets, "del")) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Delete", modifier = Modifier.size(18.dp))
            }
            OutlinedButton(onClick = s::shuffle, modifier = Modifier.demoTarget(targets, "shuffle")) {
                Icon(Icons.Outlined.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(18.dp))
            }
            Button(onClick = s::submit, modifier = Modifier.demoTarget(targets, "enter")) { Text("Enter") }
        }
        Spacer(Modifier.height(if (targets == null) 16.dp else 4.dp))
    }
}

@Composable
private fun Wheel(s: WheelSession, side: Dp, targets: DemoTargets?) {
    val colors = LocalBoardColors.current
    val r = side * 0.33f
    val tile = side * 0.27f
    Box(Modifier.size(side)) {
        LetterBall(s.puzzle.center, tile, strong = true, modifier = Modifier.offset(side / 2 - tile / 2, side / 2 - tile / 2).demoTarget(targets, "wl-${s.puzzle.center}")) { s.tap(s.puzzle.center) }
        s.outer.forEachIndexed { k, c ->
            val a = Math.toRadians(60.0 * k - 90)
            val x = side / 2 + r * cos(a).toFloat() - tile / 2
            val y = side / 2 + r * sin(a).toFloat() - tile / 2
            LetterBall(c, tile, strong = false, modifier = Modifier.offset(x, y).demoTarget(targets, "wl-$c")) { s.tap(c) }
        }
    }
}

@Composable
private fun LetterBall(c: Char, size: Dp, strong: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val colors = LocalBoardColors.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val k by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.86f else 1f, spring(0.5f, Spring.StiffnessHigh), label = "ball")
    Box(
        modifier.size(size).graphicsLayer { scaleX = k; scaleY = k }.clip(CircleShape)
            .background(if (strong) colors.entered else colors.related)
            .clickable(src, null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) { Text(c.uppercase(), fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp, color = if (strong) colors.boardBg else colors.given) }
}
