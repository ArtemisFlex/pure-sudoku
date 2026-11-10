package com.puresudoku.ui.games.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.cards.Cards
import com.puresudoku.core.cards.CrazyEights
import com.puresudoku.ui.components.CardThemes
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.components.LocalCardTheme
import com.puresudoku.ui.components.PlayingCard
import com.puresudoku.ui.components.ProvideCardTheme
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.games.board.AiWorker
import com.puresudoku.ui.games.board.DuelSession
import com.puresudoku.ui.games.board.TurnBanner
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.MiddleStat
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.tutorial.DemoStage
import com.puresudoku.ui.tutorial.DemoTargets
import com.puresudoku.ui.tutorial.TutorialStep
import com.puresudoku.ui.tutorial.demoTarget
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private val C8_NAMES = listOf("You", "Ava", "Ben", "Cleo")

class CrazyEightsSession(difficulty: Difficulty, val g: CrazyEights) : DuelSession(GameKind.CRAZY_EIGHTS, difficulty) {
    var eightAsk by mutableIntStateOf(-1); private set
    var errorFx by mutableStateOf<CardFx?>(null); private set
    var lastPlayer by mutableIntStateOf(-1); private set

    init { message = "Match the suit or rank. Eights are wild" }
    override val middleStat get() = MiddleStat.Counter("Cards", "${g.hands[0].size}")
    override val progress: Float get() = (1f - g.hands[0].size / 12f).coerceIn(0f, 0.95f)
    override val wonTitle get() = "Out first!"
    override val lostTitle get() = "${C8_NAMES[g.winner.coerceAtLeast(1)]} went out first"
    override val lostMessage get() = "You still held ${g.hands[0].size} card${if (g.hands[0].size == 1) "" else "s"}. This game's save slot has been cleared."

    private val myTurn get() = outcome == Outcome.PLAYING && g.turn == 0 && !thinking

    fun tapCard(c: Int) {
        if (!myTurn) { nudge("Wait for your turn"); return }
        if (!g.canPlay(c)) {
            errorFx = cardFx(c)
            nudge("That card doesn't match")
            return
        }
        if (Cards.rank(c) == 8) { eightAsk = c; return }
        playMine(c, Cards.suit(c))
    }

    fun chooseSuit(s: Int?) {
        val c = eightAsk; eightAsk = -1
        if (s == null || c < 0) { bump(); return }
        playMine(c, s)
    }

    private fun playMine(c: Int, s: Int) {
        g.play(0, c, s); moves++; lastPlayer = 0
        message = if (Cards.rank(c) == 8) "You played an 8 and called ${Cards.suitName(s)}" else "You played ${Cards.label(c)}"
        bump()
        if (!checkEnd()) { changed(); runOpponents() }
    }

    fun tapDeck() {
        if (!myTurn) { nudge("Wait for your turn"); return }
        val c = g.draw(0)
        if (c == null) {
            g.pass(0); message = "Nothing left to draw: you pass"; bump()
            if (!checkEnd()) { changed(); runOpponents() }
            return
        }
        message = "You drew ${Cards.label(c)}"
        bump(); changed()
    }

    private fun checkEnd(): Boolean {
        if (g.winner == 0) {
            val pts = (1 until g.players).sumOf { p -> g.hands[p].sumOf { CrazyEights.value(it) } }
            score += pts * rules.basePoints / 10
            win(listOf(BonusLine("Cards left in rivals' hands", pts * rules.basePoints / 10)))
            return true
        }
        if (g.winner > 0) { lose(); return true }
        if (g.blocked) {
            val w = g.lightest()
            message = "Nobody can move: the lightest hand wins"
            if (w == 0) win() else { g.winner = w; lose() }
            return true
        }
        return false
    }

    /** Plays the computer seats one by one, with a pause so you can follow. */
    private fun runOpponents() {
        if (outcome != Outcome.PLAYING || g.turn == 0) return
        thinking = true; bump()
        AiWorker.later(900) { opponentStep() }
    }

    private fun opponentStep() {
        if (outcome != Outcome.PLAYING) return
        val p = g.turn
        if (p == 0) { thinking = false; message += " · Your turn"; bump(); changed(); return }
        var drew = 0
        var choice = g.aiChoice(p)
        while (choice == null) {
            g.draw(p) ?: break
            drew++
            choice = g.aiChoice(p)
        }
        val who = C8_NAMES[p]
        val drawText = if (drew > 0) "$who drew $drew" else ""
        if (choice != null) {
            val (c, s) = choice
            g.play(p, c, s); lastPlayer = p
            message = (if (drawText.isNotEmpty()) "$drawText, then " else "$who ") +
                (if (Cards.rank(c) == 8) "played an 8 and called ${Cards.suitName(s)}" else "played ${Cards.label(c)}") +
                (if (g.hands[p].size == 1) " · Last card!" else "")
        } else {
            g.pass(p); message = "$drawText${if (drew > 0) " and " else "$who "}passed"
        }
        moves++
        bump()
        if (checkEnd()) return
        changed()
        if (g.turn == 0) { thinking = false; bump() } else AiWorker.later(900) { opponentStep() }
    }

    override fun resume() { if (outcome == Outcome.PLAYING && g.turn != 0) runOpponents() }

    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (g.turn != 0) return null
        val opts = g.playable(0)
        val txt = if (opts.isEmpty()) "Nothing matches: draw from the deck."
        else {
            val counts = IntArray(4); for (c in g.hands[0]) counts[Cards.suit(c)]++
            val normal = opts.filter { Cards.rank(it) != 8 }
            if (normal.isEmpty()) "Only an 8 fits: play it and call ${Cards.suitName(g.bestSuit(0))}, your strongest suit."
            else { val c = normal.maxBy { counts[Cards.suit(it)] * 4 + CrazyEights.value(it) }; "Play ${Cards.label(c)}: it keeps you in ${Cards.suitName(Cards.suit(c))}, where you have the most cards. Save your 8s." }
        }
        return { Triple(txt, -1, -1) }
    }

    override fun saveState(): JSONObject = JSONObject().put("s", JSONArray().also { a -> g.state().forEach { a.put(it) } }).put("moves", moves)

    companion object {
        fun restore(o: JSONObject): CrazyEightsSession {
            val d = Difficulty.valueOf(o.getString("difficulty")); val st = o.getJSONObject("state")
            val arr = st.getJSONArray("s"); val s = IntArray(arr.length()) { arr.getInt(it) }
            val g = CrazyEights(s[0], CrazyEights.config(d).skill, Random(System.nanoTime())); g.load(s)
            return CrazyEightsSession(d, g).also { it.moves = st.optInt("moves"); it.restoreCommon(o) }
        }
    }
}

object CrazyEightsDef : GameDef {
    override val kind = GameKind.CRAZY_EIGHTS
    override fun generate(d: Difficulty): Session = CrazyEightsSession(d, CrazyEights.newGame(d, Random(System.nanoTime())))
    override fun restore(o: JSONObject): Session = CrazyEightsSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Play a card matching the suit or rank on top. Eights are wild. Tap the deck to draw."
    override fun levelInfo(d: Difficulty) = CrazyEights.config(d).let { "${it.opponents} opponent${if (it.opponents > 1) "s" else ""}" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "One opponent who plays at random."
        Difficulty.MEDIUM -> "Two casual opponents."
        Difficulty.HARD -> "Two who save their eights."
        Difficulty.EXPERT -> "Three opponents."
        Difficulty.MASTER -> "Three who watch what you lack."
        Difficulty.EXTREME -> "Three sharp opponents."
    }
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_crazy_eights
    @Composable override fun Play(session: Session) = CrazyEightsTable(session as CrazyEightsSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    private fun demo(): CrazyEightsSession {
        val g = CrazyEights(2, 0.0, Random(3))
        val c = Cards::of
        g.discard += c(9, 2); g.suit = 2
        g.hands[0] += listOf(c(4, 2), c(9, 0), c(8, 3), c(12, 1), c(2, 0))
        g.hands[1] += listOf(c(5, 1), c(3, 3), c(13, 0), c(6, 2), c(10, 3))
        g.deck += Cards.deck(Random(5)).filter { it !in g.hands[0] && it !in g.hands[1] && it != c(9, 2) }
        return CrazyEightsSession(Difficulty.EASY, g)
    }

    override val tutorial = listOf(
        TutorialStep("Match suit or rank", "On your turn, play one card that matches the top card's suit or its rank. The first player to empty their hand wins.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s.cancelThinking(); s = demo(); wait(500)
                tap("h-${Cards.of(4, 2)}") { s.tapCard(Cards.of(4, 2)) }; wait(2400)
            }) { t -> CrazyEightsTable(s, t) }
        },
        TutorialStep("Wild eights and drawing", "An 8 can be played on anything: you then call the suit everyone must follow. Can't (or would rather not) play? Tap the deck to draw a card.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s.cancelThinking(); s = demo(); wait(500)
                tap("h-${Cards.of(8, 3)}") { s.tapCard(Cards.of(8, 3)) }; wait(700)
                s.chooseSuit(0); wait(2200)
            }) { t -> CrazyEightsTable(s, t) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrazyEightsTable(s: CrazyEightsSession, targets: DemoTargets?) = ProvideCardTheme(CardThemes.CRAZY_EIGHTS) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val th = LocalCardTheme.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            for (p in 1 until g.players) {
                val active = g.turn == p && s.outcome == Outcome.PLAYING
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (active) th.back.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(C8_NAMES[p], fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy((-24).dp)) { repeat(minOf(7, g.hands[p].size)) { PlayingCard(0, false, 32.dp) } }
                    Spacer(Modifier.height(2.dp))
                    Text(if (g.hands[p].size == 1) "Last card!" else "${g.hands[p].size} cards", fontSize = 11.sp,
                        color = if (g.hands[p].size == 1) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Box(Modifier.demoTarget(targets, "deck").clickable { s.tapDeck() }, contentAlignment = Alignment.Center) {
                PlayingCard(0, false, 76.dp)
                Text("${g.deck.size}", Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            PopCard(g.top, true, 86.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SUIT", fontSize = 10.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Cards.suitSymbol(g.suit), fontSize = 34.sp, color = th.ink(g.suit), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))
        TurnBanner(if (s.thinking) s.message.ifEmpty { "Opponents are playing…" } else s.message, s.thinking)
        Spacer(Modifier.height(14.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((-10).dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in g.hands[0].sortedBy { Cards.suit(it) * 13 + Cards.rank(it) }) {
                PopCard(c, true, 62.dp, Modifier.demoTarget(targets, "h-$c"), fx = s.errorFx) { s.tapCard(c) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Spacer(Modifier.height(12.dp))
    }
    if (s.eightAsk >= 0) {
        AlertDialog(
            onDismissRequest = { s.chooseSuit(null) },
            title = { Text("Call a suit") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (su in 0..3) Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(th.face).clickable { s.chooseSuit(su) }, contentAlignment = Alignment.Center) {
                        Text(Cards.suitSymbol(su), fontSize = 30.sp, color = th.ink(su))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { s.chooseSuit(null) }) { Text("Cancel") } },
        )
    }
    Spacer(Modifier.width(0.dp))
}
