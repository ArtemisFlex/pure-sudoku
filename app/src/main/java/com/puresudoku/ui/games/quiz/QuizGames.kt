package com.puresudoku.ui.games.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.ScoreRules
import com.puresudoku.core.Scoring
import com.puresudoku.core.words.QuizQuestion
import com.puresudoku.core.words.QuizRules
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
import java.security.SecureRandom
import kotlin.random.Random

private val GOOD = Color(0xFF3FA66B)

/** A multiple-choice word game: a fixed list of rounds, 3 strikes. */
class QuizSession(
    kind: GameKind,
    difficulty: Difficulty,
    val questions: List<QuizQuestion>,
    round: Int = 0,
    private var streak: Int = 0,
) : Session(kind, difficulty) {
    private val cfg = QuizRules.config(difficulty)
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)

    var round by mutableIntStateOf(round); private set
    var chosen by mutableIntStateOf(-1); private set
    private var answeredMs by mutableLongStateOf(0L)
    var answerFx by mutableStateOf(0L); private set

    val current: QuizQuestion get() = questions[round]
    val answeredRight get() = chosen >= 0 && chosen == current.correct
    override val progress: Float get() = round / questions.size.toFloat()

    fun choose(i: Int) {
        if (outcome != Outcome.PLAYING) return
        if (chosen >= 0) { nudge("Tap Next for the next question"); return }
        chosen = i
        answeredMs = 0
        answerFx = FxIds.next()
        if (i == current.correct) {
            streak++
            score += Scoring.placement(rules, streak)
            changed()
        } else {
            streak = 0
            score -= minOf(score, rules.basePoints)
            if (!addStrike()) changed()
        }
    }

    override fun onTick(dtMs: Long) {
        if (chosen < 0 || outcome != Outcome.PLAYING) return
        answeredMs += dtMs
        // right answers move on quickly; wrong ones leave time to read why
        if (answeredMs >= if (answeredRight) 1100 else 3500) next()
    }

    fun next() {
        if (chosen < 0 || outcome != Outcome.PLAYING) return
        if (round + 1 >= questions.size) {
            val b = Scoring.finish(rules, elapsedMs, strikes)
            finish(true, listOf(BonusLine("Time bonus", b.timeBonus), BonusLine("Hearts left bonus", b.livesBonus)))
            return
        }
        round++
        chosen = -1
        changed()
    }

    override fun saveState(): JSONObject = JSONObject()
        .put("round", round).put("streak", streak)
        .put("questions", JSONArray().also { a ->
            questions.forEach { q ->
                a.put(JSONObject().put("p", q.prompt).put("s", q.subject).put("o", JSONArray(q.options)).put("c", q.correct).put("e", q.explain))
            }
        })

    companion object {
        fun restore(kind: GameKind, o: JSONObject): QuizSession {
            val d = Difficulty.valueOf(o.getString("difficulty"))
            val st = o.getJSONObject("state")
            val arr = st.getJSONArray("questions")
            val qs = List(arr.length()) { k ->
                val q = arr.getJSONObject(k)
                val opts = q.getJSONArray("o")
                QuizQuestion(q.getString("p"), q.getString("s"), List(opts.length()) { opts.getString(it) }, q.getInt("c"), q.getString("e"))
            }
            return QuizSession(kind, d, qs, st.getInt("round"), st.optInt("streak")).also { it.restoreCommon(o) }
        }
    }
}

/** Shared definition for the three quiz games. */
abstract class QuizDef : GameDef {
    protected val rng = Random(SecureRandom().nextLong())
    abstract fun round(d: Difficulty, i: Int, used: MutableSet<String>): QuizQuestion

    override fun generate(d: Difficulty): Session {
        val used = HashSet<String>()
        val qs = synchronized(rng) { List(QuizRules.config(d).rounds) { round(d, it, used) } }
        return QuizSession(kind, d, qs)
    }

    override fun restore(o: JSONObject): Session = QuizSession.restore(kind, o)

    override fun levelInfo(d: Difficulty) = QuizRules.config(d).let { "${it.rounds} rounds · ${it.options} choices" }

    @Composable
    override fun Play(session: Session) = QuizLayout(session as QuizSession, null)

    @Composable
    override fun Icon(size: Dp) = GameIcon(kind, size)

    protected fun demo(vararg qs: QuizQuestion) = QuizSession(kind, Difficulty.EASY, qs.toList())

    /** Tutorial card that answers the given demo rounds. */
    protected fun card(title: String, body: String, status: Boolean = false, make: () -> QuizSession, picks: List<Int>): TutorialStep =
        TutorialStep(title, body) { active ->
            var s by remember { mutableStateOf(make()) }
            DemoStage(active, script = {
                s = make()
                for (p in picks) {
                    wait(900)
                    tap("opt-$p") { s.choose(p) }
                    wait(if (s.answeredRight) 1300 else 2600)
                    if (status && !s.answeredRight) highlight(listOf("middle"), 1000)
                    tap("next") { s.next() }
                }
                wait(600)
            }) { t -> QuizLayout(s, t, status = status) }
        }
}

object SynonymsDef : QuizDef() {
    override val kind = GameKind.SYNONYMS
    override fun background(session: com.puresudoku.ui.session.Session) = com.puresudoku.R.drawable.vbg_synonyms
    override fun round(d: Difficulty, i: Int, used: MutableSet<String>) = QuizRules.synonymRound(d, i, rng, used)
    override fun hint(d: Difficulty) = "Read whether you need the same meaning or the opposite, then tap that word."
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Everyday words."
        Difficulty.MEDIUM -> "Everyday words, more choices."
        Difficulty.HARD -> "Less common words."
        Difficulty.EXPERT -> "A richer vocabulary."
        Difficulty.MASTER -> "Uncommon words."
        Difficulty.EXTREME -> "Rare words, six choices."
    }

    override val tutorial = listOf(
        card("Same meaning", "Each round shows a word. When it says \"Same meaning as\", tap the word that means about the same thing.",
            make = { demo(QuizQuestion("Same meaning as", "enormous", listOf("tiny", "huge", "quiet"), 1, "\"huge\" means much the same as \"enormous\".")) },
            picks = listOf(1)),
        card("Or the opposite", "When it says \"Opposite of\", tap the word that means the reverse.",
            make = { demo(QuizQuestion("Opposite of", "ancient", listOf("modern", "dusty", "famous"), 0, "\"modern\" is the opposite of \"ancient\".")) },
            picks = listOf(0)),
        card("Three strikes", "A wrong answer costs a heart, and the right one is shown so you learn it. Three strikes ends the game; get through every round to win.",
            status = true,
            make = { demo(QuizQuestion("Same meaning as", "weary", listOf("angry", "tired", "eager"), 1, "\"tired\" means much the same as \"weary\".")) },
            picks = listOf(0)),
    )
}

object RhymeDef : QuizDef() {
    override val kind = GameKind.RHYME_TIME
    override fun background(session: com.puresudoku.ui.session.Session) = com.puresudoku.R.drawable.vbg_rhyme_time
    override fun round(d: Difficulty, i: Int, used: MutableSet<String>) = QuizRules.rhymeRound(d, i, rng, used)
    override fun hint(d: Difficulty) = "Say the words in your head. Pick the one whose ending sounds the same."
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Simple rhymes."
        Difficulty.MEDIUM -> "More choices."
        Difficulty.HARD -> "Watch for look-alike endings."
        Difficulty.EXPERT -> "Two look-alikes per round."
        Difficulty.MASTER -> "Uncommon words."
        Difficulty.EXTREME -> "Rare words and tricky spellings."
    }

    override val tutorial = listOf(
        card("Find the rhyme", "Words rhyme when their endings sound the same, like \"light\" and \"kite\". Tap the word that rhymes with the one shown.",
            make = { demo(QuizQuestion("Which word rhymes with", "light", listOf("lint", "kite", "lamp"), 1, "\"kite\" rhymes with \"light\".")) },
            picks = listOf(1)),
        card("Sound, not spelling", "Some words look like they rhyme but don't. \"Bear\" rhymes with \"hair\", not with \"near\". Trust your ear.",
            make = { demo(QuizQuestion("Which word rhymes with", "bear", listOf("near", "hair", "beam"), 1, "\"hair\" rhymes with \"bear\". \"near\" looks similar but sounds different.")) },
            picks = listOf(0)),
        card("Three strikes", "A wrong answer costs a heart and shows the right one. Three strikes ends the game; finish every round to win.",
            status = true,
            make = { demo(QuizQuestion("Which word rhymes with", "phone", listOf("stone", "done", "gone"), 0, "\"stone\" rhymes with \"phone\". \"done\" and \"gone\" look similar but sound different.")) },
            picks = listOf(2)),
    )
}

object AlliterationDef : QuizDef() {
    override val kind = GameKind.ALLITERATION
    override fun background(session: com.puresudoku.ui.session.Session) = com.puresudoku.R.drawable.vbg_alliteration
    override fun round(d: Difficulty, i: Int, used: MutableSet<String>) = QuizRules.alliterationRound(d, i, rng, used)
    override fun hint(d: Difficulty) = "All but one word start with the same sound. Tap the one that doesn't."
    override fun levelInfo(d: Difficulty) = QuizRules.config(d).let { "${it.rounds} rounds · ${it.options + 1} words" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Same letter, same sound."
        Difficulty.MEDIUM -> "Longer phrases."
        Difficulty.HARD -> "Spellings start to trick you."
        Difficulty.EXPERT -> "Same letter, different sound."
        Difficulty.MASTER -> "Long phrases, rarer words."
        Difficulty.EXTREME -> "Seven words, every trick."
    }

    override val tutorial = listOf(
        card("Same first sound", "Alliteration is when words start with the same sound, like \"brave bright bubbles\". One word in each group doesn't fit. Tap it.",
            make = { demo(QuizQuestion("Tap the word that breaks the alliteration", "", listOf("brave", "bright", "kite", "bubbles"), 2, "The others start with a \"b\" sound; \"kite\" starts with \"k\".")) },
            picks = listOf(2)),
        card("Listen, don't look", "It's the sound that counts, not the letter. \"Photo\" fits with \"fern\", but \"city\" doesn't fit with \"cat\".",
            make = { demo(QuizQuestion("Tap the word that breaks the alliteration", "", listOf("fern", "photo", "city", "funny"), 2, "The others start with an \"f\" sound; \"city\" starts with \"s\".")) },
            picks = listOf(2)),
        card("Three strikes", "A wrong pick costs a heart and shows the answer. Three strikes ends the game; clear every round to win.",
            status = true,
            make = { demo(QuizQuestion("Tap the word that breaks the alliteration", "", listOf("knot", "nice", "kettle", "noble"), 2, "The others start with an \"n\" sound (the k in \"knot\" is silent); \"kettle\" starts with \"k\".")) },
            picks = listOf(0)),
    )
}

// ------------------------------------------------------------------- UI

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuizLayout(s: QuizSession, targets: DemoTargets?, status: Boolean = false) {
    val q = s.current
    val answered = s.chosen >= 0
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (status) StatusBar(s.score, s.middleStat, s.elapsedMs, targets = targets)
        Text(
            "ROUND ${s.round + 1} OF ${s.questions.size}", fontSize = 11.sp, letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(q.prompt, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (q.subject.isNotEmpty()) {
            val pop = remember(s.round, s) { Animatable(0.6f) }
            LaunchedEffect(s.round, s) { pop.animateTo(1f, spring(0.5f, Spring.StiffnessMediumLow)) }
            Text(
                q.subject, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 38.sp,
                color = LocalBoardColors.current.entered,
                modifier = Modifier.padding(top = 6.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
            )
        }
        Spacer(Modifier.height(18.dp))
        val chips = q.subject.isEmpty()
        if (chips) {
            FlowRow(
                Modifier.widthIn(max = 460.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                q.options.forEachIndexed { i, o -> OptionButton(s, i, o, chip = true, targets = targets) }
            }
        } else {
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                q.options.forEachIndexed { i, o -> OptionButton(s, i, o, chip = false, targets = targets) }
            }
        }
        Spacer(Modifier.height(16.dp))
        AnimatedVisibility(answered, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                    (if (s.answeredRight) "Correct! " else "Not quite. ") + q.explain,
                    textAlign = TextAlign.Center, fontSize = 15.sp, lineHeight = 21.sp,
                    color = if (s.answeredRight) GOOD else LocalBoardColors.current.error,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = s::next, enabled = s.outcome == Outcome.PLAYING, modifier = Modifier.demoTarget(targets, "next")) { Text("Next") }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OptionButton(s: QuizSession, i: Int, text: String, chip: Boolean, targets: DemoTargets?) {
    val colors = LocalBoardColors.current
    val answered = s.chosen >= 0
    val isRight = i == s.current.correct
    val isChosen = i == s.chosen
    val bg by animateColorAsState(
        when {
            answered && isRight -> GOOD
            answered && isChosen -> colors.error
            else -> MaterialTheme.colorScheme.surface
        }, tween(220), label = "opt",
    )
    val fg = if (answered && (isRight || isChosen)) Color.White else MaterialTheme.colorScheme.onSurface
    val dim = answered && !isRight && !isChosen
    val pop = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(s.answerFx) {
        if (answered && isRight) { pop.snapTo(0.85f); pop.animateTo(1f, spring(0.4f, Spring.StiffnessMedium)) }
        if (answered && isChosen && !isRight) shake.animateTo(0f, keyframes { durationMillis = 400; -12f at 50; 10f at 120; -7f at 190; 4f at 260; 0f at 400 })
    }
    Box(
        Modifier
            .then(if (chip) Modifier else Modifier.fillMaxWidth())
            .demoTarget(targets, "opt-$i")
            .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = if (dim) 0.45f else 1f }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(remember { MutableInteractionSource() }, null) { s.choose(i) }
            .padding(horizontal = if (chip) 18.dp else 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
    }
}
