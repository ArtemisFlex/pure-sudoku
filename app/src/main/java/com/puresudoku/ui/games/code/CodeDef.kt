package com.puresudoku.ui.games.code

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.CodeRules
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.ScoreRules
import com.puresudoku.core.Scoring
import com.puresudoku.core.ints
import com.puresudoku.core.toJson
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
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import kotlin.random.Random

/** Fixed, distinct colours for code symbols 1–9 (white numbers on top). */
private val SYMBOL_COLORS = listOf(
    Color(0xFFE05A47), Color(0xFFE8A93A), Color(0xFF3FA66B), Color(0xFF2F8FB5), Color(0xFF3F67D6),
    Color(0xFF7A4FD0), Color(0xFFD9619F), Color(0xFF8C6A4F), Color(0xFF5E6672),
)

data class RejectFx(val id: Long, val text: String)

class CodeSession(
    difficulty: Difficulty,
    val secret: IntArray,
    val history: MutableList<Pair<IntArray, Pair<Int, Int>>> = mutableListOf(),
) : Session(GameKind.CODE_BREAKER, difficulty) {
    val cfg = CodeRules.config(difficulty)
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    val current = IntArray(cfg.length)

    var version by mutableIntStateOf(0); private set
    var rejectFx by mutableStateOf<RejectFx?>(null); private set

    override val usesStrikes get() = false
    override val middleStat get() = MiddleStat.Counter("Guesses", "${history.size}/${cfg.guesses}")
    override val progress get() = history.size / cfg.guesses.toFloat()
    override val wonTitle get() = "Code cracked!"
    override val lostTitle get() = "Out of guesses"
    override val lostMessage get() = "The code was ${secret.joinToString(" ")}. This game's save slot has been cleared."

    val full get() = current.all { it != 0 }

    fun pick(sym: Int) {
        if (outcome != Outcome.PLAYING) return
        val k = current.indexOfFirst { it == 0 }
        if (k < 0) return
        current[k] = sym; version++
    }

    fun clearSlot(k: Int) { if (outcome == Outcome.PLAYING) { current[k] = 0; version++ } }

    fun backspace() {
        val k = current.indexOfLast { it != 0 }
        if (k >= 0) clearSlot(k)
    }

    fun submit() {
        if (outcome != Outcome.PLAYING || !full) return
        val guess = current.copyOf()
        if (cfg.strict && !CodeRules.consistent(guess, history)) {
            rejectFx = RejectFx(FxIds.next(), "That guess doesn't fit the clues so far")
            return
        }
        val fb = CodeRules.feedback(secret, guess)
        history += guess to fb
        current.fill(0)
        version++
        if (fb.first == cfg.length) {
            val left = cfg.guesses - history.size
            val guessBonus = cfg.basePoints * 10 * (left + 1)
            val time = Scoring.finish(rules, elapsedMs, 0).timeBonus
            finish(true, listOf(BonusLine("Guesses left bonus", guessBonus), BonusLine("Time bonus", time)))
        } else if (history.size >= cfg.guesses) {
            finish(false)
        } else changed()
    }

    override fun saveState(): JSONObject = JSONObject()
        .put("secret", secret.toJson())
        .put("guesses", JSONArray().also { a -> history.forEach { (g, fb) -> a.put(JSONObject().put("g", g.toJson()).put("e", fb.first).put("n", fb.second)) } })
}

object CodeDef : GameDef {
    override val kind = GameKind.CODE_BREAKER
    private val rng = Random(SecureRandom().nextLong())

    override fun generate(d: Difficulty): Session = CodeSession(d, synchronized(rng) { CodeRules.secret(CodeRules.config(d), rng) })

    override fun restore(o: JSONObject): Session {
        val d = Difficulty.valueOf(o.getString("difficulty"))
        val st = o.getJSONObject("state")
        val arr = st.getJSONArray("guesses")
        val hist = (0 until arr.length()).map { k -> arr.getJSONObject(k).let { it.ints("g") to (it.getInt("e") to it.getInt("n")) } }.toMutableList()
        return CodeSession(d, st.ints("secret"), hist).restoreCommon(o)
    }

    override fun levelInfo(d: Difficulty) = CodeRules.config(d).let { "${it.length} of ${it.symbols} · ${it.guesses} tries" }

    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Short code, no repeats."
        Difficulty.MEDIUM -> "Four symbols, no repeats."
        Difficulty.HARD -> "Symbols can repeat."
        Difficulty.EXPERT -> "Eight symbols to choose from."
        Difficulty.MASTER -> "Every guess must fit the clues."
        Difficulty.EXTREME -> "Six-symbol code, strict clues."
    }

    @Composable
    override fun Play(session: Session) = CodeLayout(session as CodeSession, null)

    @Composable
    override fun Icon(size: Dp) = GameIcon(kind, size)

    // ---------------------------------------------------------------- tutorial

    private fun demo(d: Difficulty = Difficulty.MEDIUM) = CodeSession(d, intArrayOf(2, 5, 1, 4))

    private suspend fun com.puresudoku.ui.tutorial.DemoScope.enter(s: CodeSession, guess: IntArray) {
        for (g in guess) tap("sym-$g") { s.pick(g) }
        tap("submit") { s.submit() }
        wait(900)
    }

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep(
            "Crack the code",
            "A secret code of numbered colours is hidden. Tap colours to fill your guess, tap a slot to clear it, then tap Guess.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                tap("sym-1") { s.pick(1) }; tap("sym-2") { s.pick(2) }; tap("sym-6") { s.pick(6) }
                tap("slot-2") { s.clearSlot(2) }
                tap("sym-3") { s.pick(3) }; tap("sym-4") { s.pick(4) }
                tap("submit") { s.submit() }
                wait(1500)
            }) { t -> CodeLayout(s, t) }
        },
        TutorialStep(
            "Read the clues",
            "Each solid dot means a colour is right and in the right place. Each hollow dot means a colour is in the code but somewhere else. The dots don't say which ones.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                s.pick(1); s.pick(2); s.pick(3); s.pick(4); s.submit()
                wait(900)
                highlight(listOf("fb-0"), 1600)
                enter(s, intArrayOf(2, 1, 5, 4))
                highlight(listOf("fb-1"), 1600)
            }) { t -> CodeLayout(s, t) }
        },
        TutorialStep(
            "Narrow it down",
            "Use every clue to make a better next guess. Crack the code before you run out of guesses — fewer guesses score more.",
        ) { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                enter(s, intArrayOf(1, 2, 3, 4))
                enter(s, intArrayOf(2, 1, 5, 4))
                enter(s, intArrayOf(2, 5, 1, 4))
                wait(1600)
            }) { t -> CodeLayout(s, t, status = true) }
        },
        TutorialStep(
            "Strict levels",
            "On Master and Extreme, every guess must fit all the clues you've already seen. A guess that couldn't be the code is turned away.",
        ) { active ->
            var s by remember { mutableStateOf(CodeSession(Difficulty.MASTER, intArrayOf(2, 5, 1, 4, 3))) }
            DemoStage(active, script = {
                s = CodeSession(Difficulty.MASTER, intArrayOf(2, 5, 1, 4, 3))
                enter(s, intArrayOf(1, 2, 3, 4, 5))
                for (g in intArrayOf(1, 2, 3, 4, 6)) tap("sym-$g") { s.pick(g) }
                tap("submit") { s.submit() }
                wait(1800)
            }) { t -> CodeLayout(s, t) }
        },
    )
}

// ------------------------------------------------------------------- UI

@Composable
private fun CodeLayout(s: CodeSession, targets: DemoTargets?, status: Boolean = false) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val cfg = s.cfg
    val list = rememberLazyListState()
    LaunchedEffect(s.history.size) { list.animateScrollToItem(s.history.size.coerceAtMost(cfg.guesses - 1)) }
    val slot = if (cfg.length >= 6) 34.dp else if (cfg.length == 5) 38.dp else 42.dp

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (status) StatusBar(s.score, s.middleStat, s.elapsedMs, targets = targets)
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).widthIn(max = 460.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(cfg.guesses) { row ->
                val done = row < s.history.size
                val isCurrent = row == s.history.size && s.outcome == Outcome.PLAYING
                val guess = when { done -> s.history[row].first; isCurrent -> s.current; else -> null }
                GuessRow(s, row, guess, if (done) s.history[row].second else null, isCurrent, slot, targets)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) {
            val fx = s.rejectFx
            var show by remember { mutableStateOf(false) }
            LaunchedEffect(fx?.id) { if (fx != null) { show = true; delay(2200); show = false } }
            androidx.compose.animation.AnimatedVisibility(show, enter = fadeIn(), exit = fadeOut()) {
                Text(fx?.text ?: "", color = LocalBoardColors.current.error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(6.dp))
        // palette
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (sym in 1..cfg.symbols) {
                SymbolDot(sym, if (cfg.symbols > 7) 34.dp else 40.dp, Modifier.demoTarget(targets, "sym-$sym")
                    .clickable(remember { MutableInteractionSource() }, null) { s.pick(sym) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = s::backspace) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Delete", modifier = Modifier.size(18.dp))
            }
            Button(onClick = s::submit, enabled = s.full, modifier = Modifier.demoTarget(targets, "submit").width(140.dp)) { Text("Guess") }
        }
        Spacer(Modifier.height(if (targets == null) 16.dp else 4.dp))
    }
}

@Composable
private fun GuessRow(
    s: CodeSession, row: Int, guess: IntArray?, fb: Pair<Int, Int>?, isCurrent: Boolean, slot: Dp, targets: DemoTargets?,
) {
    val colors = LocalBoardColors.current
    val shake = remember { Animatable(0f) }
    val rfx = s.rejectFx
    LaunchedEffect(rfx?.id) {
        if (rfx != null && isCurrent) shake.animateTo(0f, keyframes { durationMillis = 380; -10f at 50; 9f at 110; -7f at 170; 4f at 240; 0f at 380 })
    }
    Row(
        Modifier.fillMaxWidth()
            .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) MaterialTheme.colorScheme.surface else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${row + 1}", Modifier.width(22.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (k in 0 until s.cfg.length) {
                val sym = guess?.get(k) ?: 0
                val m = if (isCurrent) Modifier.demoTarget(targets, "slot-$k")
                    .clickable(remember { MutableInteractionSource() }, null) { s.clearSlot(k) } else Modifier
                if (sym == 0) {
                    Box(m.size(slot).clip(CircleShape).border(1.6.dp, if (isCurrent) colors.thickLine.copy(alpha = 0.5f) else colors.thinLine, CircleShape))
                } else {
                    val pop = remember(sym) { Animatable(if (isCurrent) 0.5f else 1f) }
                    LaunchedEffect(sym) { pop.animateTo(1f, spring(0.45f, Spring.StiffnessMedium)) }
                    SymbolDot(sym, slot, m.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
                }
            }
        }
        Feedback(fb, s.cfg.length, Modifier.demoTarget(targets, "fb-$row"))
    }
}

@Composable
private fun Feedback(fb: Pair<Int, Int>?, length: Int, modifier: Modifier) {
    val colors = LocalBoardColors.current
    val perRow = (length + 1) / 2
    Column(modifier.width(14.dp * perRow + 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (r in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (c in 0 until perRow) {
                val k = r * perRow + c
                if (k >= length) continue
                val kind = when {
                    fb == null -> 0
                    k < fb.first -> 2
                    k < fb.first + fb.second -> 1
                    else -> 0
                }
                val appear = remember(fb) { Animatable(if (fb == null) 1f else 0f) }
                LaunchedEffect(fb) { if (fb != null) { delay(120L * k); appear.animateTo(1f, spring(0.5f, Spring.StiffnessMedium)) } }
                Box(
                    Modifier.size(11.dp).graphicsLayer { scaleX = appear.value; scaleY = appear.value }
                        .clip(CircleShape)
                        .background(if (kind == 2) colors.given else Color.Transparent)
                        .border(1.6.dp, if (kind == 0) colors.thinLine else colors.given, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun SymbolDot(sym: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(SYMBOL_COLORS[sym - 1]), contentAlignment = Alignment.Center) {
        Text("$sym", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}
