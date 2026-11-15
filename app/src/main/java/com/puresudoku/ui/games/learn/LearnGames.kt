package com.puresudoku.ui.games.learn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.learn.DivisionCards
import com.puresudoku.core.learn.FlashCard
import com.puresudoku.core.learn.Learn
import com.puresudoku.core.learn.LearnGame
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.MiddleStat
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.theme.Poppins
import com.puresudoku.ui.tutorial.TutorialStep
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// =====================================================================  the session

/**
 * One deck of flashcards. No hearts to lose: a wrong answer shows the right one and how to get
 * there, and the round always ends with stars for how many were right first time.
 */
class LearnSession(kind: GameKind, difficulty: Difficulty, val cards: List<FlashCard>, at: Int = 0, right: Int = 0) : Session(kind, difficulty) {
    var at by mutableIntStateOf(at); private set
    var right by mutableIntStateOf(right); private set
    /** The answer given to the current card, or null. */
    var given by mutableStateOf<String?>(null); private set
    /** Number-pad entry for typed answers. */
    var typed by mutableStateOf(""); private set
    var showSteps by mutableStateOf(false)
    private var streak = 0
    private var peeked = false

    val card: FlashCard get() = cards[at]
    val correct get() = given != null && sameAnswer(given!!, card.answer)
    override val usesStrikes = false
    override val progress: Float get() = at / cards.size.toFloat()
    override val middleStat: MiddleStat get() = MiddleStat.Counter("Right", "$right/${cards.size}")
    override val wonTitle: String get() = when { right >= cards.size - 1 -> "Superstar!"; right >= cards.size * 7 / 10 -> "Great job!"; else -> "Good practice!" }

    private fun sameAnswer(a: String, b: String) = a.replace(" ", "").equals(b.replace(" ", ""), ignoreCase = true)

    fun choose(a: String) {
        if (given != null || outcome != Outcome.PLAYING) return
        given = a
        if (correct) {
            if (!peeked) { right++; streak++; score += 10 + minOf(streak, 5) * 2 } else score += 3
        } else { streak = 0; showSteps = true }
        changed()
    }

    fun key(k: String) {
        if (given != null) return
        typed = when (k) {
            "⌫" -> typed.dropLast(1)
            "R" -> if (typed.isNotEmpty() && 'R' !in typed) "$typed R " else typed
            else -> if (typed.length < 10) typed + k else typed
        }
    }

    fun submit() { if (typed.isNotBlank()) choose(typed.trim()) }

    fun peek() { showSteps = !showSteps; if (given == null && showSteps) peeked = true }

    fun next() {
        if (given == null || outcome != Outcome.PLAYING) return
        if (at + 1 >= cards.size) {
            val stars = when { right >= cards.size - 1 -> 3; right >= cards.size * 7 / 10 -> 2; right >= cards.size / 2 -> 1; else -> 0 }
            finish(true, listOf(BonusLine("Stars: " + "★".repeat(stars) + "☆".repeat(3 - stars), stars * 20)))
            return
        }
        at++; given = null; typed = ""; showSteps = false; peeked = false
        changed()
    }

    override fun saveState(): JSONObject = JSONObject().put("at", at).put("right", right).put("cards", JSONArray().also { a ->
        for (c in cards) a.put(JSONObject().put("p", c.prompt).put("a", c.ask).put("pic", c.picture).put("c", JSONArray(c.choices)).put("ans", c.answer)
            .put("s", JSONArray(c.steps)).put("v", c.visual))
    })

    companion object {
        fun restore(kind: GameKind, o: JSONObject): LearnSession {
            val st = o.getJSONObject("state")
            val arr = st.getJSONArray("cards")
            val cards = List(arr.length()) { i ->
                val c = arr.getJSONObject(i)
                fun list(k: String) = c.getJSONArray(k).let { a -> List(a.length()) { a.getString(it) } }
                FlashCard(c.getString("p"), c.getString("a"), c.optString("pic"), list("c"), c.getString("ans"), list("s"), c.optString("v"))
            }
            return LearnSession(kind, Difficulty.valueOf(o.getString("difficulty")), cards, st.getInt("at"), st.getInt("right")).also { it.restoreCommon(o) }
        }
    }
}

// =====================================================================  the games

/** One flashcard game: its deck builder and words for each level. */
class LearnDef(override val kind: GameKind, private val game: LearnGame, private val blurbs: List<String>, private val howTo: String) : GameDef {
    override fun generate(d: Difficulty): Session = LearnSession(kind, d, Learn.deck(game, d.ordinal, Random(com.puresudoku.platform.nowMillis())))
    override fun restore(o: JSONObject): Session = LearnSession.restore(kind, o)
    override fun levelInfo(d: Difficulty) = Learn.AGES[d.ordinal]
    override fun levelBlurb(d: Difficulty) = blurbs[d.ordinal]
    override fun hint(d: Difficulty) = howTo
    @Composable override fun Play(session: Session) = LearnUi(session as LearnSession)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)
    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Flip, think, answer", howTo + " Tap the answer, or type it on the number pad.") { _ -> SampleCard(game, 1) },
        TutorialStep("The i button shows how", "Stuck? Tap the i on the card to see how to work it out, step by step. Got one wrong? The steps open by themselves.") { _ -> SampleCard(game, 3, steps = true) },
        TutorialStep("Earn your stars", "Ten cards a round. Right first time earns stars. Each level is for an age, from 4 (Easy) to 10 (Extreme).") { _ -> SampleCard(game, 4) },
    )
}

object LearnGames {
    val MATH = LearnDef(GameKind.LEARN_MATH, LearnGame.MATH, listOf(
        "Count pictures and add small groups.", "Adding and taking away up to 10.", "Adding and subtracting up to 20 and two-digit sums.",
        "Times tables, division facts and two-digit subtraction.", "Bigger multiplication and three-digit sums.", "Long multiplication and order of operations."),
        "Work out the answer to each card.")
    val DIVISION = LearnDef(GameKind.LEARN_DIVISION, LearnGame.DIVISION, listOf(
        "Share things out fairly between friends.", "Sharing and making equal groups.", "Long division: two digits by one.",
        "Long division: three digits by one.", "Long division with remainders.", "Four digits by two-digit numbers, with remainders."),
        "Divide each number. The i button walks through long division step by step.")
    val ALGEBRA = LearnDef(GameKind.LEARN_ALGEBRA, LearnGame.ALGEBRA, listOf(
        "Picture puzzles: how many more?", "Missing numbers up to 10.", "Missing numbers in sums and times tables.",
        "Solve for x in one step.", "Two-step equations.", "x on both sides, and brackets."),
        "Find the missing number, or solve for x.")
    val LETTERS = LearnDef(GameKind.LEARN_LETTERS, LearnGame.LETTERS, listOf(
        "First letters of picture words.", "First letters and matching pictures.", "Last letters.",
        "Fill in the missing vowel.", "Spell tricky everyday words.", "Spell hard words correctly."),
        "Look at the picture, say the word, and listen for its letters.")
    val WORDS = LearnDef(GameKind.LEARN_WORDS, LearnGame.WORDS, listOf(
        "Read three-letter words.", "Read short picture words.", "Pick the word that fits the sentence.",
        "Opposites.", "Past tense: what happened yesterday.", "What do big words mean?"),
        "Read the word or sentence and pick the answer.")
    val READING = LearnDef(GameKind.LEARN_READING, LearnGame.READING, listOf(
        "Read a short sentence.", "Read a sentence and answer a question.", "Two sentences: who, what, where.",
        "A short story: when, who, what.", "Read between the lines: why and how.", "Order of events and the main idea."),
        "Read the story on the card, then answer the question.")
    val CLOCK = LearnDef(GameKind.LEARN_CLOCK, LearnGame.CLOCK, listOf(
        "O'clock.", "O'clock and half past.", "Quarter past and quarter to.",
        "Every five minutes.", "Any minute.", "How much later? Time that passes."),
        "Read the clock: the short hand is the hour, the long hand is the minutes.")
    val all = listOf(MATH, DIVISION, ALGEBRA, LETTERS, WORDS, READING, CLOCK)
}

@Composable
private fun SampleCard(game: LearnGame, level: Int, steps: Boolean = false) {
    val c = remember { Learn.card(game, level, Random(level * 7 + game.ordinal)) }
    Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CardFace(c, Modifier.widthIn(max = 320.dp).fillMaxWidth())
            if (steps) { Spacer(Modifier.height(8.dp)); StepsPanel(c, Modifier.widthIn(max = 320.dp).heightIn(max = 110.dp)) }
        }
    }
}

// =====================================================================  the screen

private val CARD_COLORS = listOf(Color(0xFFFFF4D6), Color(0xFFE3F2FD), Color(0xFFE8F5E9), Color(0xFFFCE4EC), Color(0xFFF3E5F5), Color(0xFFFFF3E0))
private val INK = Color(0xFF1B1B1F)

@Composable
fun LearnUi(s: LearnSession) {
    val c = s.card
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        // progress dots
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            for (i in s.cards.indices) Box(Modifier.size(if (i == s.at) 12.dp else 9.dp).clip(CircleShape).background(
                when { i < s.at -> MaterialTheme.colorScheme.primary; i == s.at -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.outlineVariant }))
        }
        val pop by animateFloatAsState(if (s.given == null) 1f else 1.03f, tween(180), label = "pop")
        Box(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            CardFace(c, Modifier.fillMaxWidth().scale(pop), tint = CARD_COLORS[s.at % CARD_COLORS.size],
                ring = when { s.given == null -> null; s.correct -> Color(0xFF2E7D32); else -> Color(0xFFC62828) })
            // the i button: how to work it out
            Surface(Modifier.align(Alignment.TopEnd).padding(8.dp).size(40.dp).clickable { s.peek() }, shape = CircleShape,
                color = if (s.showSteps) MaterialTheme.colorScheme.primary else Color.White, shadowElevation = 3.dp) {
                Box(contentAlignment = Alignment.Center) { Text("i", fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Poppins, color = if (s.showSteps) Color.White else INK) }
            }
        }
        AnimatedVisibility(s.showSteps) {
            StepsPanel(c, Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 8.dp), all = s.given != null && !s.correct)
        }
        Spacer(Modifier.height(10.dp))
        if (s.given != null) {
            Surface(shape = RoundedCornerShape(14.dp), color = if (s.correct) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)) {
                Text(if (s.correct) "Yes! ${c.answer} is right." else "The answer is ${c.answer}.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (s.correct) Color(0xFF1B5E20) else Color(0xFFB71C1C))
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = s::next, modifier = Modifier.widthIn(min = 180.dp).height(54.dp)) { Text(if (s.at + 1 >= s.cards.size) "Finish" else "Next card", fontSize = 18.sp) }
        } else if (c.choices.isNotEmpty()) {
            Choices(s, c)
        } else NumberPad(s, c)
        Spacer(Modifier.height(12.dp))
    }
}

/** The flashcard itself: prompt, pictures, and any drawing (a clock, a long division bracket). */
@Composable
private fun CardFace(c: FlashCard, modifier: Modifier, tint: Color = CARD_COLORS[0], ring: Color? = null) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = tint, shadowElevation = 6.dp,
        border = ring?.let { androidx.compose.foundation.BorderStroke(4.dp, it) }) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(c.ask, fontSize = 15.sp, color = Color(0xFF3A3A40), textAlign = TextAlign.Center, modifier = Modifier.padding(end = 30.dp, start = 30.dp))
            Spacer(Modifier.height(8.dp))
            when {
                c.visual.startsWith("clock:") -> {
                    val (h, m) = c.visual.removePrefix("clock:").split(':').map { it.toInt() }
                    ClockFace(h, m, Modifier.size(180.dp))
                    Spacer(Modifier.height(6.dp))
                }
                c.visual.startsWith("div:") -> {
                    val (a, d) = c.visual.removePrefix("div:").split(':').map { it.toInt() }
                    LongDivision(a, d, 0, Modifier.padding(vertical = 4.dp))
                }
            }
            val long = c.prompt.length > 40
            // a prompt that is only a picture (emoji) is drawn big
            val pictureOnly = c.prompt.length <= 3 && c.prompt.any { it.code > 0x2000 }
            Text(c.prompt, fontSize = if (pictureOnly) 84.sp else if (long) 19.sp else if (c.prompt.length > 14) 30.sp else 44.sp, lineHeight = if (pictureOnly) 96.sp else if (long) 27.sp else 50.sp,
                fontWeight = if (long) FontWeight.Medium else FontWeight.Bold, fontFamily = Poppins, color = INK, textAlign = if (long) TextAlign.Start else TextAlign.Center)
            if (c.picture.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(c.picture, fontSize = if (c.picture.length > 24) 26.sp else 40.sp, lineHeight = if (c.picture.length > 24) 34.sp else 48.sp, textAlign = TextAlign.Center, color = INK)
            }
        }
    }
}

@Composable
private fun Choices(s: LearnSession, c: FlashCard) {
    val emoji = c.choices.all { it.length <= 4 && it.any { ch -> ch.code > 0x2000 } }
    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in c.choices.chunked(if (c.choices.size == 3 || c.choices.any { it.length > 14 }) 1 else 2)) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (a in row) Surface(
                Modifier.weight(1f).height(if (emoji) 84.dp else 64.dp).clip(RoundedCornerShape(18.dp)).clickable { s.choose(a) },
                shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFCAD3E0)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(a, fontSize = when { emoji -> 44.sp; a.length > 14 -> 17.sp; a.length > 7 -> 21.sp; else -> 28.sp }, fontWeight = FontWeight.Bold, fontFamily = Poppins, color = INK, textAlign = TextAlign.Center, modifier = Modifier.padding(6.dp))
                }
            }
        }
    }
}

@Composable
private fun NumberPad(s: LearnSession, c: FlashCard) {
    val needsR = c.answer.contains("R")
    Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(14.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF8AA4C8))) {
            Box(contentAlignment = Alignment.Center) { Text(s.typed.ifEmpty { "?" }, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = Poppins, color = if (s.typed.isEmpty()) Color(0xFF9AA0A6) else INK) }
        }
        val rows = listOf(listOf("7", "8", "9"), listOf("4", "5", "6"), listOf("1", "2", "3"), listOf(if (needsR) "R" else "", "0", "⌫"))
        for (row in rows) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in row) if (k.isEmpty()) Spacer(Modifier.weight(1f)) else Surface(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp)).clickable { s.key(k) }, shape = RoundedCornerShape(14.dp), color = Color(0xFFEEF2F8)) {
                Box(contentAlignment = Alignment.Center) { Text(k, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = INK) }
            }
        }
        Button(onClick = s::submit, enabled = s.typed.isNotBlank(), modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Check", fontSize = 18.sp) }
    }
}

/** How to work it out: each step on its own line; long division also draws the written method. */
@Composable
private fun StepsPanel(c: FlashCard, modifier: Modifier, all: Boolean = false) {
    // after a wrong answer every step shows at once; otherwise they come one at a time
    var shown by remember(c, all) { mutableIntStateOf(if (all) c.steps.size else 1) }
    val divisionParts = if (c.visual.startsWith("div:")) c.visual.removePrefix("div:").split(':').map { it.toInt() } else null
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 3.dp, border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF8AB4F8))) {
        Column(Modifier.padding(14.dp)) {
            Text("How to work it out", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0B57D0))
            Spacer(Modifier.height(6.dp))
            if (divisionParts != null) {
                // the written method grows one step at a time
                val total = DivisionCards.steps(divisionParts[0], divisionParts[1]).size
                LongDivision(divisionParts[0], divisionParts[1], ((shown - 1) / 3).coerceIn(0, total), Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(6.dp))
            }
            c.steps.take(shown).forEachIndexed { i, line ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(if (i == shown - 1) Color(0xFF0B57D0) else Color(0xFFD2E3FC)), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (i == shown - 1) Color.White else Color(0xFF0B57D0))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(line, fontSize = 16.sp, lineHeight = 22.sp, color = INK)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (shown > 1) OutlinedButton(onClick = { shown-- }) { Text("Back") }
                Spacer(Modifier.weight(1f))
                if (shown < c.steps.size) Button(onClick = { shown++ }) { Text("Next step") }
                else OutlinedButton(onClick = { shown = 1 }) { Text("From the start") }
            }
        }
    }
}

// =====================================================================  drawings

/** The written long division method: divisor, bracket, dividend, and [upTo] steps worked below. */
@Composable
private fun LongDivision(dividend: Int, divisor: Int, upTo: Int, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val digits = dividend.toString()
    val steps = DivisionCards.steps(dividend, divisor)
    val dv = divisor.toString()
    val rows = 2 + upTo * 2
    val cols = dv.length + 1 + digits.length + 3
    Canvas(modifier.size((cols * 22).dp, (rows * 30 + 10).dp)) {
        val cw = 22.dp.toPx(); val rh = 30.dp.toPx()
        val left = (dv.length + 1) * cw
        fun cell(text: String, col: Int, row: Int, color: Color = INK) = drawCentered(tm, text, Offset(left + col * cw + cw / 2, row * rh + rh / 2), color)
        // quotient on top, over the digit each step finished on
        for ((k, s) in steps.withIndex()) if (k < upTo) cell("${s.q}", s.digitIndex, 0, Color(0xFF0B57D0))
        if (upTo >= steps.size && dividend % divisor != 0) cell("R${dividend % divisor}", digits.length + 1, 0, Color(0xFF0B57D0))
        // bracket
        drawLine(INK, Offset(left - cw * 0.15f, rh * 1.05f), Offset(left + digits.length * cw + cw * 0.3f, rh * 1.05f), 2.5f, StrokeCap.Round)
        drawArc(INK, 90f, -180f, false, Offset(left - cw * 0.45f, rh * 1.05f), androidx.compose.ui.geometry.Size(cw * 0.6f, rh * 1f), style = Stroke(2.5f))
        drawCentered(tm, dv, Offset(left - cw * 0.6f - dv.length * cw / 2, rh * 1.55f), INK)
        for ((i, ch) in digits.withIndex()) cell("$ch", i, 1)
        // each step: subtract the product, write what's left (and the next digit brought down)
        var row = 2
        for ((k, s) in steps.withIndex()) {
            if (k >= upTo) break
            val p = s.product.toString()
            for ((j, ch) in p.withIndex()) cell("$ch", s.digitIndex - p.length + 1 + j, row, Color(0xFFC62828))
            cell("−", s.digitIndex - p.length, row, Color(0xFFC62828))
            drawLine(INK, Offset(left + (s.digitIndex - p.length + 1) * cw, (row + 1) * rh - 2f), Offset(left + (s.digitIndex + 1) * cw, (row + 1) * rh - 2f), 2f)
            val next = steps.getOrNull(k + 1)
            val rest = if (next != null && k + 1 < upTo + 1) next.current.toString() else s.rest.toString()
            val end = if (next != null) next.digitIndex else s.digitIndex
            for ((j, ch) in rest.withIndex()) cell("$ch", end - rest.length + 1 + j, row + 1)
            row += 2
        }
    }
}

private fun DrawScope.drawCentered(tm: TextMeasurer, text: String, at: Offset, color: Color, size: Int = 22) {
    val l = tm.measure(text, TextStyle(fontSize = size.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = Poppins))
    drawText(l, topLeft = Offset(at.x - l.size.width / 2f, at.y - l.size.height / 2f))
}

/** A friendly analog clock. */
@Composable
private fun ClockFace(h: Int, m: Int, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    Canvas(modifier) {
        val r = size.minDimension / 2; val c = center
        drawCircle(Color.White, r, c)
        drawCircle(INK, r, c, style = Stroke(r * 0.06f))
        for (i in 1..12) {
            val a = Math2.rad(i * 30.0 - 90)
            drawCentered(tm, "$i", Offset(c.x + (r * 0.78f) * cos(a).toFloat(), c.y + (r * 0.78f) * sin(a).toFloat()), INK, (r / 9).toInt().coerceAtLeast(10))
        }
        for (i in 0 until 60) {
            val a = Math2.rad(i * 6.0 - 90); val long = i % 5 == 0
            val r1 = r * (if (long) 0.9f else 0.94f)
            drawLine(INK.copy(alpha = if (long) 0.8f else 0.35f), Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()), Offset(c.x + r * 0.97f * cos(a).toFloat(), c.y + r * 0.97f * sin(a).toFloat()), if (long) 3f else 1.5f)
        }
        val ha = Math2.rad((h % 12 + m / 60.0) * 30 - 90)
        val ma = Math2.rad(m * 6.0 - 90)
        drawLine(Color(0xFF1A73E8), c, Offset(c.x + r * 0.48f * cos(ha).toFloat(), c.y + r * 0.48f * sin(ha).toFloat()), r * 0.08f, StrokeCap.Round)
        drawLine(Color(0xFFD93025), c, Offset(c.x + r * 0.72f * cos(ma).toFloat(), c.y + r * 0.72f * sin(ma).toFloat()), r * 0.045f, StrokeCap.Round)
        drawCircle(INK, r * 0.06f, c)
    }
}

private object Math2 { fun rad(deg: Double) = deg * kotlin.math.PI / 180 }
