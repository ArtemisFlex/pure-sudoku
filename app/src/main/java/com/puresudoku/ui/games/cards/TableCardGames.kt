package com.puresudoku.ui.games.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.ScoreRules
import com.puresudoku.core.Scoring
import com.puresudoku.core.cards.BlackjackRules
import com.puresudoku.core.cards.BlackjackTable
import com.puresudoku.core.cards.CMove
import com.puresudoku.core.cards.CWait
import com.puresudoku.core.cards.Cards
import com.puresudoku.core.cards.Cuttle
import com.puresudoku.core.cards.CuttleRules
import com.puresudoku.core.cards.FishEvent
import com.puresudoku.core.cards.GoFish
import com.puresudoku.core.cards.PointCard
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.components.PlayingCard
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.BonusLine
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
import kotlin.random.Random

private fun JSONArray.intList(): MutableList<Int> = MutableList(length()) { getInt(it) }
private fun List<Int>.jarr(): JSONArray = JSONArray().also { a -> forEach { a.put(it) } }

@Composable
private fun Label(text: String) = Text(text.uppercase(), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold,
    color = LocalBoardColors.current.note)

// =====================================================================
// Blackjack
// =====================================================================

class BlackjackSession(difficulty: Difficulty, val t: BlackjackTable, chips0: Int, round0: Int) : Session(GameKind.BLACKJACK, difficulty) {
    val cfg = t.cfg
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    var version by mutableIntStateOf(0); private set
    var chips by mutableIntStateOf(chips0); private set
    var round by mutableIntStateOf(round0); private set
    /** 0 betting, 1 playing a hand, 2 showing the result. */
    var phase by mutableIntStateOf(0); private set
    var bet by mutableIntStateOf(10)
    var message by mutableStateOf("Place a bet and deal."); private set
    val target get() = cfg.start * cfg.targetPct / 100

    init { score = chips * rules.basePoints / 5 }

    override val usesStrikes get() = false
    override val middleStat get() = MiddleStat.Counter("Chips", "$chips")
    override val progress: Float get() = round / cfg.hands.toFloat()
    override val wonTitle get() = "Beat the house!"
    override val lostTitle get() = if (chips < BlackjackRules.BETS.first()) "Out of chips" else "The house wins"
    override val lostMessage get() = "You finished with $chips chips; the goal was $target. This game's save slot has been cleared."

    private val chipsFree get() = chips - t.bets.sum()

    val advice: String?
        get() = if (cfg.hints && phase == 1) BlackjackRules.advice(t.hand, t.dealer[0], t.canDouble(chipsFree), t.canSplit(chipsFree)) else null

    fun deal() {
        if (outcome != Outcome.PLAYING || phase == 1) return
        if (bet > chips) { nudge("You only have $chips chips"); return }
        t.deal(bet); round++; phase = 1
        message = ""
        if (BlackjackRules.isBlackjack(t.hands[0]) || BlackjackRules.isBlackjack(t.dealer)) conclude() else after()
    }

    fun hit() {
        if (phase != 1) return
        t.hit()
        if (t.busted() || BlackjackRules.total(t.hand).first == 21) nextHand() else after()
    }

    fun stand() { if (phase == 1) nextHand() }

    fun double() {
        if (phase != 1) return
        if (!t.canDouble(chipsFree)) { nudge(if (t.hand.size != 2) "You can only double on your first two cards" else "Not enough chips to double"); return }
        t.doubleDown(); nextHand()
    }

    fun split() {
        if (phase != 1) return
        if (!t.canSplit(chipsFree)) { nudge(if (t.hands.size > 1) "You can split only once" else "Split needs a pair and enough chips"); return }
        t.split(); after()
    }

    private fun nextHand() { if (!t.nextHand()) conclude() else after() }

    private fun conclude() {
        t.dealerPlays()
        val net = t.settle()
        chips += net.sum()
        val bj = BlackjackRules.isBlackjack(t.hands[0]) && t.hands.size == 1
        message = net.mapIndexed { k, n ->
            val pre = if (net.size > 1) "Hand ${k + 1}: " else ""
            pre + when {
                n > 0 && bj -> "Blackjack! +$n"
                n > 0 -> "You win +$n"
                n < 0 && t.busted(k) -> "Bust −${-n}"
                n < 0 -> "Dealer wins −${-n}"
                else -> "Push"
            }
        }.joinToString(" · ")
        phase = 2
        score = chips * rules.basePoints / 5
        bet = bet.coerceAtMost(maxOf(BlackjackRules.BETS.first(), chips))
        version++
        when {
            chips < BlackjackRules.BETS.first() -> finish(false)
            round >= cfg.hands -> if (chips >= target) finish(true, listOf(BonusLine("Time bonus", Scoring.finish(rules, elapsedMs, 0).timeBonus))) else finish(false)
            else -> changed()
        }
    }

    private fun after() { version++; changed() }

    override fun saveState(): JSONObject = JSONObject()
        .put("shoe", t.shoe.jarr()).put("chips", chips).put("round", round).put("phase", phase).put("bet", bet).put("msg", message)
        .put("dealer", t.dealer.jarr()).put("hands", JSONArray().also { a -> t.hands.forEach { a.put(it.jarr()) } })
        .put("bets", t.bets.jarr()).put("doubled", JSONArray(t.doubled)).put("active", t.active).put("revealed", t.dealerRevealed)

    companion object {
        fun restore(o: JSONObject): BlackjackSession {
            val d = Difficulty.valueOf(o.getString("difficulty")); val st = o.getJSONObject("state")
            val t = BlackjackTable(BlackjackRules.config(d), st.getJSONArray("shoe").intList(), Random(SecureRandom().nextLong()))
            t.dealer += st.getJSONArray("dealer").intList()
            val hs = st.getJSONArray("hands"); for (k in 0 until hs.length()) t.hands += hs.getJSONArray(k).intList()
            t.bets += st.getJSONArray("bets").intList()
            val db = st.getJSONArray("doubled"); for (k in 0 until db.length()) t.doubled += db.getBoolean(k)
            t.active = st.getInt("active"); t.dealerRevealed = st.getBoolean("revealed")
            return BlackjackSession(d, t, st.getInt("chips"), st.getInt("round")).also { s ->
                s.phase = st.getInt("phase").let { if (it == 1 && t.hands.isEmpty()) 0 else it }
                s.bet = st.getInt("bet"); s.message = st.optString("msg"); s.restoreCommon(o)
            }
        }
    }
}

object BlackjackDef : GameDef {
    override val kind = GameKind.BLACKJACK
    private val rng = Random(SecureRandom().nextLong())
    override fun generate(d: Difficulty): Session {
        val cfg = BlackjackRules.config(d)
        val r = Random(synchronized(rng) { rng.nextLong() })
        return BlackjackSession(d, BlackjackTable(cfg, BlackjackTable.shoe(cfg.decks, r), r), cfg.start, 0)
    }
    override fun restore(o: JSONObject): Session = BlackjackSession.restore(o)
    override fun hint(d: Difficulty) = "Get closer to 21 than the dealer without going over. Finish ${BlackjackRules.config(d).hands} hands with at least ${BlackjackRules.config(d).let { it.start * it.targetPct / 100 }} chips."
    override fun levelInfo(d: Difficulty) = BlackjackRules.config(d).let { "${it.decks} deck${if (it.decks > 1) "s" else ""} · goal ${it.start * it.targetPct / 100}" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "One deck, strategy tips, modest goal."
        Difficulty.MEDIUM -> "Two decks, strategy tips."
        Difficulty.HARD -> "Four decks, dealer hits soft 17."
        Difficulty.EXPERT -> "Six decks, no tips."
        Difficulty.MASTER -> "Blackjack pays only 6:5."
        Difficulty.EXTREME -> "Eight decks, 6:5, double your chips."
    }
    @Composable override fun Play(session: Session) = BlackjackTableUi(session as BlackjackSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    private fun demo(): BlackjackSession {
        val c = Cards::of
        val cfg = BlackjackRules.config(Difficulty.EASY)
        // drawn from the end: you 10♠, dealer 9♣, you 6♥, dealer 7♦, then 4♣ (your hit), 8♠ (dealer)
        val shoe = (MutableList(20) { c(2 + it % 8, it % 4) } + listOf(c(8, 3), c(4, 0), c(7, 1), c(6, 2), c(9, 0), c(10, 3))).toMutableList()
        return BlackjackSession(Difficulty.EASY, BlackjackTable(cfg, shoe, Random(3)), 100, 0)
    }

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Bet and deal", "Pick a bet and deal. You and the dealer get two cards; one of the dealer's stays hidden. Number cards count their value, J/Q/K count 10 and Aces 1 or 11.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                tap("bet-10") { s.bet = 10 }
                tap("deal") { s.deal() }
                wait(600); highlight(listOf("you"), 1300)
            }) { t -> BlackjackTableUi(s, t) }
        },
        TutorialStep("Hit or stand", "Hit to take another card, Stand to stop. Go over 21 and you bust. The dealer then draws to 17 or more; whoever is closer to 21 wins the bet.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); s.deal(); wait(700)
                tap("hit") { s.hit() }
                wait(600)
                tap("stand") { s.stand() }
                wait(1600)
            }) { t -> BlackjackTableUi(s, t) }
        },
        TutorialStep("Double, split, and the goal", "Double doubles your bet for exactly one more card. Split a pair into two hands. Play the set number of hands and finish with the goal in chips to win.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = { s = demo(); s.deal(); wait(500); highlight(listOf("actions"), 1300); highlight(listOf("goal"), 1300) }) { t -> BlackjackTableUi(s, t) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlackjackTableUi(s: BlackjackSession, targets: DemoTargets?) {
    com.puresudoku.ui.components.ProvideCardTheme(com.puresudoku.ui.components.CardThemes.BLACKJACK) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val t = s.t
    val colors = LocalBoardColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Hand ${minOf(s.round + if (s.phase == 0) 1 else 0, s.cfg.hands)} of ${s.cfg.hands} · goal ${s.target} chips",
            fontSize = 13.sp, color = colors.note, modifier = Modifier.demoTarget(targets, "goal"))
        Spacer(Modifier.height(10.dp))
        Label("Dealer" + if (t.dealer.isNotEmpty()) " · " + (if (t.dealerRevealed) "${BlackjackRules.total(t.dealer).first}" else "${BlackjackRules.total(listOf(t.dealer[0])).first} + ?") else "")
        Spacer(Modifier.height(6.dp))
        Row(Modifier.height(100.dp), horizontalArrangement = Arrangement.spacedBy((-22).dp)) {
            t.dealer.forEachIndexed { i, c -> PopCard(c, i != 1 || t.dealerRevealed, 70.dp) }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.demoTarget(targets, "you"), horizontalAlignment = Alignment.CenterHorizontally) {
            if (t.hands.isEmpty()) {
                Label("You"); Spacer(Modifier.height(6.dp)); Box(Modifier.height(100.dp))
            }
            t.hands.forEachIndexed { k, h ->
                val (tot, soft) = BlackjackRules.total(h)
                val activeHand = s.phase == 1 && k == t.active && t.hands.size > 1
                Label((if (t.hands.size > 1) "Hand ${k + 1}" else "You") + " · ${if (soft && tot < 21) "soft " else ""}$tot · bet ${t.bets[k]}" + if (t.doubled[k]) " (doubled)" else "")
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.height(100.dp).then(if (activeHand) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)).padding(4.dp) else Modifier),
                    horizontalArrangement = Arrangement.spacedBy((-22).dp),
                ) { for (c in h) PopCard(c, true, 70.dp, dim = s.phase == 2 && tot > 21) }
                Spacer(Modifier.height(8.dp))
            }
        }
        TableMessage(s.message)
        s.advice?.let { Spacer(Modifier.height(6.dp)); Text("Tip: $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(12.dp))
        if (s.phase == 1) {
            FlowRow(Modifier.demoTarget(targets, "actions"), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                Button(onClick = s::hit, modifier = Modifier.demoTarget(targets, "hit")) { Text("Hit") }
                Button(onClick = s::stand, modifier = Modifier.demoTarget(targets, "stand")) { Text("Stand") }
                OutlinedButton(onClick = s::double) { Text("Double") }
                OutlinedButton(onClick = s::split) { Text("Split") }
            }
        } else if (s.outcome == Outcome.PLAYING) {
            Label("Bet")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (b in BlackjackRules.BETS) FilterChip(selected = s.bet == b, onClick = { if (b <= s.chips) s.bet = b else s.nudge("You only have ${s.chips} chips") },
                    label = { Text("$b") }, enabled = b <= s.chips, modifier = Modifier.demoTarget(targets, "bet-$b"))
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = s::deal, modifier = Modifier.demoTarget(targets, "deal")) { Text(if (s.phase == 2) "Next hand" else "Deal") }
        }
        Spacer(Modifier.height(12.dp))
    }
    }
}

// =====================================================================
// Go Fish
// =====================================================================

private val FISH_NAMES = listOf("You", "Ava", "Ben", "Cleo")

class GoFishSession(difficulty: Difficulty, val g: GoFish) : Session(GameKind.GO_FISH, difficulty) {
    val cfg = GoFish.config(difficulty)
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    var version by mutableIntStateOf(0); private set
    var rank by mutableIntStateOf(0); private set
    val log = mutableStateListOf("Pick a card, then tap a player to ask for that rank.")
    private var clock = 0L

    override val usesStrikes get() = false
    override val middleStat get() = MiddleStat.Counter("Books", "${g.books[0].size}")
    override val progress: Float get() = g.books.sumOf { it.size } / 13f
    override val wonTitle get() = "Most books!"
    override val lostTitle get() = "Out-fished"
    override val lostMessage get() = "You made ${g.books[0].size} books; ${FISH_NAMES[best()]} made ${g.books[best()].size}. This game's save slot has been cleared."

    private fun best() = (1 until g.players).maxBy { g.books[it].size }

    private fun say(events: List<FishEvent>) {
        for (e in events) log += when (e) {
            is FishEvent.Asked -> "${FISH_NAMES[e.from]} asked ${if (e.to == 0) "you" else FISH_NAMES[e.to]} for ${Cards.plural(e.rank)}: " +
                if (e.got > 0) "got ${e.got}!" else "Go fish!"
            is FishEvent.Fished -> if (e.lucky) "…and fished one up! Another turn." else if (e.player == 0) "You drew ${Cards.label(e.card)}." else continue
            is FishEvent.Book -> "${FISH_NAMES[e.player]} completed the ${Cards.plural(e.rank)}!"
            is FishEvent.Drew -> continue
        }
        while (log.size > 6) log.removeAt(0)
    }

    fun pick(c: Int) {
        if (outcome != Outcome.PLAYING) return
        rank = if (rank == Cards.rank(c)) 0 else Cards.rank(c); version++
    }

    fun ask(to: Int) {
        if (outcome != Outcome.PLAYING) return
        if (g.turn != 0) { nudge("Wait for your turn"); return }
        if (rank == 0) { nudge("First pick a card from your hand"); return }
        if (g.hands[to].isEmpty()) { nudge("${FISH_NAMES[to]} has no cards"); return }
        say(g.ask(0, to, rank))
        if (rank !in g.ranksIn(0)) rank = 0
        clock = 0
        after()
    }

    /** Draw when nobody else holds cards. */
    fun lonelyDraw() {
        val p = g.turn
        val ev = mutableListOf<FishEvent>()
        if (g.deck.isNotEmpty()) {
            val c = g.deck.removeAt(g.deck.lastIndex); g.hands[p] += c
            if (p == 0) log += "Nobody else has cards. You drew ${Cards.label(c)}."
        }
        g.checkBooks(p, ev); say(ev)
        if (!g.over) g.advance(ev)
        after()
    }

    private val othersEmpty get() = (0 until g.players).none { it != g.turn && g.hands[it].isNotEmpty() }

    /** One AI turn. */
    fun step() {
        if (outcome != Outcome.PLAYING || g.turn == 0 || g.over) return
        if (othersEmpty) { lonelyDraw(); return }
        val (to, r) = g.aiChoice(g.turn)
        say(g.ask(g.turn, to, r))
        after()
    }

    override fun onTick(dtMs: Long) {
        if (outcome != Outcome.PLAYING) return
        if (g.turn == 0 && !(othersEmpty && !g.over)) return
        clock += dtMs
        if (clock < 1100) return
        clock = 0
        if (g.turn == 0) lonelyDraw() else step()
    }

    private fun after() {
        version++
        score = g.books[0].size * rules.basePoints * 10
        if (g.over) {
            if (g.books[0].size >= g.books[best()].size) finish(true, listOf(BonusLine("Time bonus", Scoring.finish(rules, elapsedMs, 0).timeBonus)))
            else finish(false)
        } else changed()
    }

    override fun saveState(): JSONObject = JSONObject()
        .put("deck", g.deck.jarr()).put("turn", g.turn)
        .put("hands", JSONArray().also { a -> g.hands.forEach { a.put(it.jarr()) } })
        .put("books", JSONArray().also { a -> g.books.forEach { a.put(it.jarr()) } })
        .put("log", JSONArray(log.toList()))

    companion object {
        fun restore(o: JSONObject): GoFishSession {
            val d = Difficulty.valueOf(o.getString("difficulty")); val st = o.getJSONObject("state"); val cfg = GoFish.config(d)
            val g = GoFish(cfg.opponents + 1, cfg.memory, Random(SecureRandom().nextLong()))
            g.deck += st.getJSONArray("deck").intList(); g.turn = st.getInt("turn")
            val hs = st.getJSONArray("hands"); for (p in 0 until g.players) g.hands[p] += hs.getJSONArray(p).intList()
            val bs = st.getJSONArray("books"); for (p in 0 until g.players) g.books[p] += bs.getJSONArray(p).intList()
            return GoFishSession(d, g).also { s ->
                st.optJSONArray("log")?.let { a -> s.log.clear(); for (k in 0 until a.length()) s.log += a.getString(k) }
                s.restoreCommon(o)
            }
        }
    }
}

object GoFishDef : GameDef {
    override val kind = GameKind.GO_FISH
    private val rng = Random(SecureRandom().nextLong())
    override fun generate(d: Difficulty): Session = GoFishSession(d, synchronized(rng) { GoFish.newGame(d, Random(rng.nextLong())) })
    override fun restore(o: JSONObject): Session = GoFishSession.restore(o)
    override fun hint(d: Difficulty) = "Ask for ranks you hold. Collect all four of a rank to make a book. Most books wins."
    override fun levelInfo(d: Difficulty) = GoFish.config(d).let { "${it.opponents} opponent${if (it.opponents > 1) "s" else ""} · memory ${it.memory}%" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "One forgetful opponent."
        Difficulty.MEDIUM -> "Two casual opponents."
        Difficulty.HARD -> "Two who pay attention."
        Difficulty.EXPERT -> "Three sharp opponents."
        Difficulty.MASTER -> "Three who rarely forget."
        Difficulty.EXTREME -> "Three with perfect memory."
    }
    @Composable override fun Play(session: Session) = GoFishTable(session as GoFishSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    private fun demo(): GoFishSession {
        val c = Cards::of
        val g = GoFish(2, 0, Random(4))
        g.hands[0] += listOf(c(7, 0), c(7, 2), c(7, 1), c(3, 3), c(12, 2), c(9, 1), c(5, 0))
        g.hands[1] += listOf(c(7, 3), c(4, 0), c(10, 1), c(2, 2), c(13, 0), c(9, 3), c(6, 1))
        g.deck += listOf(c(11, 0), c(8, 2), c(3, 1), c(1, 2))
        return GoFishSession(Difficulty.EASY, g)
    }

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Ask for a rank", "Tap a card in your hand to choose its rank, then tap a player to ask for it. They must hand over every card of that rank, and you go again.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                tap("card-7") { s.pick(Cards.of(7, 0)) }
                tap("opp-1") { s.ask(1) }
                wait(1600)
            }) { t -> GoFishTable(s, t) }
        },
        TutorialStep("Go fish", "If they have none, you 'go fish' from the pond. Draw the rank you asked for and you go again; otherwise the turn passes.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                tap("card-12") { s.pick(Cards.of(12, 2)) }
                tap("opp-1") { s.ask(1) }
                wait(1600)
            }) { t -> GoFishTable(s, t) }
        },
        TutorialStep("Make books", "All four cards of a rank form a book. When every book is made, the player with the most wins. Opponents remember what you've asked for.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(400)
                s.pick(Cards.of(7, 0)); s.ask(1); wait(600)
                highlight(listOf("books"), 1500)
            }) { t -> GoFishTable(s, t) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoFishTable(s: GoFishSession, targets: DemoTargets?) {
    com.puresudoku.ui.components.ProvideCardTheme(com.puresudoku.ui.components.CardThemes.GO_FISH) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val colors = LocalBoardColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            for (p in 1 until g.players) {
                val turn = g.turn == p
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (turn) colors.sameDigit else MaterialTheme.colorScheme.surface)
                        .border(if (s.rank != 0 && g.turn == 0 && g.hands[p].isNotEmpty()) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .clickable { s.ask(p) }.demoTarget(targets, "opp-$p").padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(FISH_NAMES[p], fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy((-26).dp)) { repeat(minOf(6, g.hands[p].size)) { PlayingCard(0, false, 34.dp) } }
                    Spacer(Modifier.height(4.dp))
                    Text("${g.hands[p].size} cards · ${g.books[p].size} books", fontSize = 11.sp, color = colors.note)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Pond: ${g.deck.size} cards", fontSize = 13.sp, color = colors.note)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(10.dp)) {
            for ((i, line) in s.log.takeLast(4).withIndex()) Text(line, fontSize = 13.sp,
                color = if (i == minOf(3, s.log.size - 1)) MaterialTheme.colorScheme.onSurface else colors.note)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.demoTarget(targets, "books").horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("Your books")
            for (r in g.books[0]) Text(Cards.rankLabel(r), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.sameDigit).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(when {
            g.turn != 0 -> "${FISH_NAMES[g.turn]} is thinking…"
            s.rank == 0 -> "Your turn: pick a card"
            else -> "Ask a player for ${Cards.plural(s.rank)}"
        }, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((-14).dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val keyed = HashSet<Int>()
            for (c in g.hands[0].sortedBy { Cards.rank(it) * 4 + Cards.suit(it) }) {
                val r = Cards.rank(c)
                val m = if (keyed.add(r)) Modifier.demoTarget(targets, "card-$r") else Modifier
                PopCard(c, true, 58.dp, m, highlight = r == s.rank, lift = r == s.rank) { s.pick(c) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    }
}

// =====================================================================
// Cuttle
// =====================================================================

class CuttleSession(difficulty: Difficulty, val g: Cuttle) : Session(GameKind.CUTTLE, difficulty) {
    val cfg = CuttleRules.config(difficulty)
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    var version by mutableIntStateOf(0); private set
    var selected by mutableIntStateOf(-1); private set
    val discards = mutableStateListOf<Int>()
    private var clock = 0L

    override val usesStrikes get() = false
    override val middleStat get() = MiddleStat.Counter("Points", "${g.score(0)}/${g.goal(0)}")
    override val progress: Float get() = (g.score(0) / g.goal(0).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    override val wonTitle get() = "You win!"
    override val lostTitle get() = if (g.stalemate) "Stalemate" else "Opponent wins"
    override val lostMessage get() = if (g.stalemate) "Nobody could reach their goal. This game's save slot has been cleared." else "They reached ${g.score(1)} points. This game's save slot has been cleared."

    fun actor(): Int = when (val w = g.wait) {
        CWait.Move -> g.turn
        is CWait.Counter -> w.who
        is CWait.Discard -> w.who
        is CWait.ScrapPick -> w.who
        is CWait.Seven -> w.who
    }

    val myTurn get() = outcome == Outcome.PLAYING && !g.over && actor() == 0

    /** The moves offered for the selected card (or the 7's revealed card). */
    val options: List<CMove>
        get() {
            if (!myTurn) return emptyList()
            return when (val w = g.wait) {
                CWait.Move -> if (selected in g.hands[0]) g.movesFor(0, selected) else emptyList()
                is CWait.Seven -> g.movesFor(0, w.card)
                else -> emptyList()
            }
        }

    fun select(c: Int) {
        if (!myTurn) { nudge("Wait for your opponent"); return }
        when (g.wait) {
            CWait.Move -> {
                selected = if (selected == c) -1 else c
                if (selected >= 0 && g.movesFor(0, c).isEmpty()) { nudge("${Cards.label(c)} has no legal play right now"); selected = -1 }
            }
            is CWait.Discard -> if (c in discards) discards.remove(c) else discards += c
            else -> {}
        }
        version++
    }

    fun play(m: CMove) { if (myTurn) { g.play(m); selected = -1; after() } }

    fun draw() {
        if (!myTurn || g.wait != CWait.Move) return
        when {
            g.deck.isEmpty() -> { g.play(CMove.Pass()); after() }
            g.hands[0].size >= Cuttle.HAND_LIMIT -> nudge("Your hand is full (${Cuttle.HAND_LIMIT}); play a card")
            else -> { g.play(CMove.Draw()); selected = -1; after() }
        }
    }

    fun counter(two: Int?) { if (myTurn && g.wait is CWait.Counter) { g.counter(0, two); after() } }

    fun confirmDiscard() {
        val w = g.wait as? CWait.Discard ?: return
        if (discards.size != w.count) { nudge("Choose ${w.count} card${if (w.count > 1) "s" else ""} to discard"); return }
        g.discard(0, discards.toList()); discards.clear(); after()
    }

    fun pickScrap(c: Int) { if (myTurn && g.wait is CWait.ScrapPick) { g.pickScrap(0, c); after() } }

    /** One opponent action. */
    fun step() {
        if (outcome != Outcome.PLAYING || g.over || actor() != 1) return
        when (val w = g.wait) {
            CWait.Move -> g.play(if (g.allMoves(1).isEmpty()) CMove.Pass() else g.aiMove(1, cfg.skill))
            is CWait.Counter -> g.counter(1, g.aiCounter(1, cfg.skill))
            is CWait.Discard -> g.discard(1, g.aiDiscard(1, w.count))
            is CWait.ScrapPick -> g.pickScrap(1, g.aiScrapPick())
            is CWait.Seven -> g.play(g.aiSeven(1, w.card, cfg.skill))
        }
        after()
    }

    override fun onTick(dtMs: Long) {
        if (outcome != Outcome.PLAYING || g.over) return
        val autoPass = actor() == 0 && g.wait is CWait.Counter && g.hands[0].none { Cuttle.r(it) == 2 }
        if (actor() != 1 && !autoPass) { clock = 0; return }
        clock += dtMs
        if (clock < (if (autoPass) 1600 else 1000)) return
        clock = 0
        if (autoPass) counter(null) else step()
    }

    private fun after() {
        version++
        if (selected !in g.hands[0]) selected = -1
        score = g.score(0) * rules.basePoints
        when {
            g.winner == 0 -> finish(true, listOf(BonusLine("Victory", rules.basePoints * 50), BonusLine("Time bonus", Scoring.finish(rules, elapsedMs, 0).timeBonus)))
            g.over -> finish(false)
            else -> changed()
        }
    }

    override fun saveState(): JSONObject {
        fun pts(l: List<PointCard>) = JSONArray().also { a -> l.forEach { pc -> a.put((listOf(pc.card) + pc.jacks).jarr()) } }
        val w = when (val x = g.wait) {
            CWait.Move -> JSONObject().put("t", "M")
            is CWait.Counter -> JSONObject().put("t", "C").put("who", x.who)
            is CWait.Discard -> JSONObject().put("t", "D").put("who", x.who).put("n", x.count)
            is CWait.ScrapPick -> JSONObject().put("t", "S").put("who", x.who)
            is CWait.Seven -> JSONObject().put("t", "7").put("who", x.who).put("card", x.card)
        }
        return JSONObject().put("deck", g.deck.jarr()).put("scrap", g.scrap.jarr())
            .put("hands", JSONArray().put(g.hands[0].jarr()).put(g.hands[1].jarr()))
            .put("points", JSONArray().put(pts(g.points[0])).put(pts(g.points[1])))
            .put("royals", JSONArray().put(g.royals[0].jarr()).put(g.royals[1].jarr()))
            .put("turn", g.turn).put("wait", w).put("passes", g.passes).put("turns", g.turns)
            .put("pending", g.pending?.let { JSONObject().put("card", it.card).put("target", it.target) } ?: JSONObject.NULL)
            .put("by", g.pendingBy).put("counters", g.counters.jarr()).put("log", JSONArray(g.log.takeLast(5)))
    }

    companion object {
        fun restore(o: JSONObject): CuttleSession {
            val d = Difficulty.valueOf(o.getString("difficulty")); val st = o.getJSONObject("state")
            val g = Cuttle(Random(SecureRandom().nextLong()))
            g.deck += st.getJSONArray("deck").intList(); g.scrap += st.getJSONArray("scrap").intList()
            for (p in 0..1) {
                g.hands[p] += st.getJSONArray("hands").getJSONArray(p).intList()
                g.royals[p] += st.getJSONArray("royals").getJSONArray(p).intList()
                val pa = st.getJSONArray("points").getJSONArray(p)
                for (k in 0 until pa.length()) { val l = pa.getJSONArray(k).intList(); g.points[p] += PointCard(l[0], l.drop(1).toMutableList()) }
            }
            g.turn = st.getInt("turn"); g.passes = st.getInt("passes"); g.turns = st.getInt("turns")
            val w = st.getJSONObject("wait")
            g.wait = when (w.getString("t")) {
                "C" -> CWait.Counter(w.getInt("who"))
                "D" -> CWait.Discard(w.getInt("who"), w.getInt("n"))
                "S" -> CWait.ScrapPick(w.getInt("who"))
                "7" -> CWait.Seven(w.getInt("who"), w.getInt("card"))
                else -> CWait.Move
            }
            st.optJSONObject("pending")?.let { g.pending = CMove.OneOff(it.getInt("card"), it.getInt("target")) }
            g.pendingBy = st.getInt("by"); g.counters += st.getJSONArray("counters").intList()
            st.optJSONArray("log")?.let { a -> for (k in 0 until a.length()) g.log += a.getString(k) }
            return CuttleSession(d, g).also { it.restoreCommon(o) }
        }

        fun label(m: CMove): String = when (m) {
            is CMove.Draw -> "Draw"
            is CMove.Pass -> "Pass"
            is CMove.Points -> "Points: +${Cuttle.r(m.card)}"
            is CMove.Scuttle -> "Scuttle ${Cuttle.name(m.target)}"
            is CMove.Royal -> when (Cuttle.r(m.card)) { 13 -> "King: lowers your goal"; 12 -> "Queen: protects your cards"; else -> "Glasses: see their hand" }
            is CMove.Jack -> "Steal ${Cuttle.name(m.target)}"
            is CMove.OneOff -> Cuttle.oneOffText(Cuttle.r(m.card)).replaceFirstChar { it.uppercase() } + if (m.target >= 0) " (${Cuttle.name(m.target)})" else ""
        }
    }
}

object CuttleDef : GameDef {
    override val kind = GameKind.CUTTLE
    private val rng = Random(SecureRandom().nextLong())
    override fun generate(d: Difficulty): Session = CuttleSession(d, synchronized(rng) { Cuttle.newGame(Random(rng.nextLong())) })
    override fun restore(o: JSONObject): Session = CuttleSession.restore(o)
    override fun hint(d: Difficulty) = "Reach 21 points before your opponent. Numbers are points or one-off effects; royals stay on the table. Kings lower your goal."
    override fun levelInfo(d: Difficulty) = "Opponent skill ${(CuttleRules.config(d).skill * 100).toInt()}%"
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A careless opponent."
        Difficulty.MEDIUM -> "Knows the basics."
        Difficulty.HARD -> "Plays solid cards."
        Difficulty.EXPERT -> "Counters and scuttles well."
        Difficulty.MASTER -> "Rarely makes a mistake."
        Difficulty.EXTREME -> "Always plays its best move."
    }
    @Composable override fun Play(session: Session) = CuttleTable(session as CuttleSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    private fun demo(): CuttleSession {
        val c = Cards::of
        val g = Cuttle(Random(5))
        g.hands[0] += listOf(c(7, 2), c(9, 0), c(1, 3), c(13, 1), c(4, 2), c(11, 0))
        g.hands[1] += listOf(c(3, 0), c(10, 2), c(5, 1), c(12, 3), c(2, 1))
        g.points[1] += PointCard(c(6, 1))
        g.deck += Cards.deck(Random(6)).filter { it !in g.hands[0] && it !in g.hands[1] && it != c(6, 1) }
        return CuttleSession(Difficulty.EASY, g)
    }

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Race to 21", "On your turn, draw a card or play one. Tap a card to see what it can do. Number cards (A–10) can be played for their value in points. First to 21 wins.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                tap("h-${Cards.of(7, 2)}") { s.select(Cards.of(7, 2)) }
                wait(400)
                tap("opt-0") { s.play(s.options[0]) }
                wait(1400)
            }) { t -> CuttleTable(s, t) }
        },
        TutorialStep("Scuttle and steal", "A higher number can scuttle (destroy) an opponent's point card. Jacks steal one. Kings lower your goal, Queens protect your other cards, and an 8 lets you see their hand.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                tap("h-${Cards.of(9, 0)}") { s.select(Cards.of(9, 0)) }
                wait(500)
                tap("opt-1") { s.play(s.options[1]) }
                wait(1400)
            }) { t -> CuttleTable(s, t) }
        },
        TutorialStep("One-offs and twos", "Number cards can also be scrapped for a one-off effect (an Ace scraps every point card, a 5 draws two…). The other player may cancel a one-off with a 2.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                tap("h-${Cards.of(1, 3)}") { s.select(Cards.of(1, 3)) }
                wait(300); highlight(listOf("options"), 1800)
            }) { t -> CuttleTable(s, t) }
        },
    )
}

@Composable
private fun Field(points: List<PointCard>, royals: List<Int>, w: Dp) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (points.isEmpty() && royals.isEmpty()) Text("—", color = LocalBoardColors.current.note, modifier = Modifier.padding(vertical = 18.dp))
        for (pc in points) Box(Modifier.width(w + 12.dp * pc.jacks.size)) {
            PopCard(pc.card, true, w, art = CardArt.cuttle(pc.card))
            pc.jacks.forEachIndexed { i, j -> PopCard(j, true, w, Modifier.offset(x = 12.dp * (i + 1), y = 10.dp), art = CardArt.cuttle(j)) }
        }
        if (royals.isNotEmpty() && points.isNotEmpty()) Spacer(Modifier.width(8.dp))
        for (r in royals) PopCard(r, true, w, art = CardArt.cuttle(r))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CuttleTable(s: CuttleSession, targets: DemoTargets?) {
    com.puresudoku.ui.components.ProvideCardTheme(com.puresudoku.ui.components.CardThemes.CUTTLE) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val colors = LocalBoardColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Opponent ${g.score(1)}/${g.goal(1)}", fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("${g.hands[1].size} in hand", fontSize = 12.sp, color = colors.note)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy((-20).dp)) {
            val see = g.glasses(0)
            for (c in g.hands[1]) PopCard(if (see) c else 0, see, 40.dp, art = if (see) CardArt.cuttle(c) else 0)
        }
        Spacer(Modifier.height(6.dp))
        Field(g.points[1], g.royals[1], 50.dp)
        Spacer(Modifier.height(10.dp))
        Text("Deck ${g.deck.size} · Scrap ${g.scrap.size}", fontSize = 12.sp, color = colors.note)
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(8.dp)) {
            val lines = g.log.takeLast(3)
            lines.forEachIndexed { i, line -> Text(line, fontSize = 13.sp, color = if (i == lines.lastIndex) MaterialTheme.colorScheme.onSurface else colors.note) }
            if (lines.isEmpty()) Text("Your move: draw or play a card.", fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("You ${g.score(0)}/${g.goal(0)}", fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (g.queens(0) > 0) Text("Protected by ${g.queens(0)} Queen${if (g.queens(0) > 1) "s" else ""}", fontSize = 12.sp, color = colors.note)
        }
        Spacer(Modifier.height(4.dp))
        Field(g.points[0], g.royals[0], 50.dp)
        Spacer(Modifier.height(10.dp))

        // prompt for whatever the game is waiting on
        val w = g.wait
        val prompt = when {
            g.over -> ""
            s.actor() == 1 -> "Opponent is thinking…"
            w is CWait.Counter -> "Opponent played a one-off. Counter it with a 2?"
            w is CWait.Discard -> "Choose ${w.count} card${if (w.count > 1) "s" else ""} to discard"
            w is CWait.ScrapPick -> "Take a card from the scrap pile"
            w is CWait.Seven -> "Play the revealed ${Cuttle.name(w.card)}"
            s.selected >= 0 -> "What should ${Cuttle.name(s.selected)} do?"
            else -> "Your turn: draw, or tap a card"
        }
        if (prompt.isNotEmpty()) Text(prompt, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))

        if (w is CWait.Seven && s.actor() == 0) { PopCard(w.card, true, 64.dp, highlight = true, art = CardArt.cuttle(w.card)); Spacer(Modifier.height(8.dp)) }
        if (w is CWait.ScrapPick && s.actor() == 0) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in g.scrap.distinct()) PopCard(c, true, 48.dp, art = CardArt.cuttle(c)) { s.pickScrap(c) }
            }
            Spacer(Modifier.height(8.dp))
        }
        val opts = s.options
        if (opts.isNotEmpty()) FlowRow(Modifier.fillMaxWidth().demoTarget(targets, "options"), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            opts.forEachIndexed { i, m -> OutlinedButton(onClick = { s.play(m) }, modifier = Modifier.demoTarget(targets, "opt-$i")) { Text(CuttleSession.label(m), fontSize = 13.sp) } }
        }
        if (s.myTurn && w is CWait.Counter) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (two in g.hands[0].filter { Cuttle.r(it) == 2 }) Button(onClick = { s.counter(two) }) { Text("Counter with ${Cuttle.name(two)}") }
            OutlinedButton(onClick = { s.counter(null) }) { Text("Let it happen") }
        }
        if (s.myTurn && w is CWait.Discard) Button(onClick = s::confirmDiscard) { Text("Discard ${s.discards.size}/${w.count}") }
        if (s.myTurn && w == CWait.Move && s.selected < 0) Button(onClick = s::draw, modifier = Modifier.demoTarget(targets, "draw")) {
            Text(if (g.deck.isEmpty()) "Pass" else "Draw a card")
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in g.hands[0].sortedBy { Cuttle.r(it) * 4 + Cards.suit(it) }) {
                val sel = c == s.selected || c in s.discards
                PopCard(c, true, 56.dp, Modifier.demoTarget(targets, "h-$c"), highlight = sel, lift = sel, art = CardArt.cuttle(c)) { s.select(c) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    }
}
