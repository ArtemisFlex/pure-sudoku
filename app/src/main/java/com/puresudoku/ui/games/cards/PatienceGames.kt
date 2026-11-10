package com.puresudoku.ui.games.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.ScoreRules
import com.puresudoku.core.Scoring
import com.puresudoku.core.cards.Cards
import com.puresudoku.core.cards.Klondike
import com.puresudoku.core.cards.Pile
import com.puresudoku.core.cards.PyramidDeal
import com.puresudoku.core.cards.PyramidRules
import com.puresudoku.core.ints
import com.puresudoku.core.toJson
import com.puresudoku.ui.components.ActionButton
import com.puresudoku.ui.components.CardSlot
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.components.MovingCard
import com.puresudoku.ui.components.StatusBar
import com.puresudoku.ui.components.cardHeight
import com.puresudoku.ui.games.GameDef
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
import java.security.SecureRandom
import kotlin.random.Random

// =====================================================================
// Solitaire (Klondike)
// =====================================================================

class SolitaireSession(difficulty: Difficulty, var k: Klondike, moves: Int = 0) : Session(GameKind.SOLITAIRE, difficulty) {
    val cfg = Klondike.config(difficulty)
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    private val undoStack = ArrayDeque<Pair<Klondike, Int>>()

    var version by mutableIntStateOf(0); private set
    var moves by mutableIntStateOf(moves); private set
    var selected by mutableStateOf<Pair<Pile, Int>?>(null); private set
    var errorFx by mutableStateOf<CardFx?>(null); private set
    var finishing by mutableStateOf(false); private set
    private var finishClock = 0L

    override val usesStrikes get() = false
    override val middleStat get() = MiddleStat.Counter("Moves", "$moves")
    override val progress: Float get() = k.foundations.sumOf { it.size } / 52f
    override val lostTitle get() = "Out of moves"
    override val lostMessage get() = "No more moves can help. This game's save slot has been cleared."

    val targets: List<Pile> get() = selected?.let { (p, i) -> k.destinations(p, i) } ?: emptyList()
    val canAutoFinish get() = !finishing && k.stock.isEmpty() && k.waste.isEmpty() && (0 until 7).all { k.down[it] == 0 } && !k.won

    private fun push() { undoStack.addLast(k.copy() to score); if (undoStack.size > 80) undoStack.removeFirst() }

    fun tapStock() {
        if (outcome != Outcome.PLAYING || finishing) return
        selected = null
        if (k.stock.isEmpty() && !k.canRedeal()) {
            nudge(if (k.waste.isEmpty()) "The deck is empty" else "No passes left through the deck"); return
        }
        push()
        k.draw(); moves++; version++
        afterMove()
    }

    fun tapCard(pile: Pile, index: Int) {
        if (outcome != Outcome.PLAYING || finishing) return
        val sel = selected
        if (sel != null && sel != (pile to index)) {
            if (pile in targets) { doMove(sel.first, sel.second, pile); return }
            selected = null
        }
        if (pile is Pile.Tableau && !k.faceUp(pile.i, index)) { nudge("That card is face down — uncover it first"); return }
        val dests = k.destinations(pile, index)
        val card = k.run(pile, index)?.firstOrNull() ?: return
        if (dests.isEmpty()) { errorFx = cardFx(card); nudge("${Cards.label(card)} has nowhere to go yet"); return }
        val f = dests.firstOrNull { it is Pile.Foundation }
        if (f != null || dests.size == 1 || sel == (pile to index)) { doMove(pile, index, f ?: dests.first()); return }
        selected = pile to index
        version++
    }

    fun tapEmpty(pile: Pile) {
        if (outcome != Outcome.PLAYING) return
        val sel = selected
        if (sel != null && pile in targets) doMove(sel.first, sel.second, pile)
        else if (pile is Pile.Tableau) nudge("Only a King can go in an empty column")
        else if (pile is Pile.Foundation) nudge("Foundations start with an Ace")
    }

    private fun doMove(from: Pile, index: Int, to: Pile) {
        push()
        val flipped = k.move(from, index, to)
        score += when {
            to is Pile.Foundation -> 10
            from is Pile.Waste -> 5
            from is Pile.Foundation -> -minOf(score, 15)
            else -> 0
        } + if (flipped) 5 else 0
        moves++; selected = null; version++
        afterMove()
    }

    private fun afterMove() {
        if (k.won) {
            val b = Scoring.finish(rules, elapsedMs, 0)
            finish(true, listOf(BonusLine("Time bonus", b.timeBonus)))
            return
        }
        if (!k.hasMoves()) { finish(false); return }
        changed()
    }

    fun undo() {
        if (outcome != Outcome.PLAYING || finishing) return
        if (!cfg.undo) { nudge("No undo on Extreme"); return }
        val (prev, sc) = undoStack.removeLastOrNull() ?: run { nudge("Nothing to undo"); return }
        k = prev; score = sc; moves++; selected = null; version++; changed()
    }

    fun giveUp() { if (outcome == Outcome.PLAYING) finish(false) }
    fun autoFinish() { if (canAutoFinish) { finishing = true; finishClock = 0 } }

    override fun onTick(dtMs: Long) {
        if (!finishing || outcome != Outcome.PLAYING) return
        finishClock += dtMs
        if (finishClock < 110) return
        finishClock = 0
        // move the lowest card that can go up
        val cands = (0 until 7).mapNotNull { t -> k.tableau[t].lastIndex.takeIf { it >= 0 }?.let { Pile.Tableau(t) to it } }
        val best = cands.filter { (p, i) -> k.destinations(p, i).any { it is Pile.Foundation } }
            .minByOrNull { (p, i) -> Cards.rank(k.tableau[(p as Pile.Tableau).i][i]) } ?: run { finishing = false; return }
        val f = k.destinations(best.first, best.second).first { it is Pile.Foundation }
        k.move(best.first, best.second, f); score += 10; moves++; version++
        if (k.won) afterMove()
    }

    override fun saveState(): JSONObject = JSONObject()
        .put("stock", k.stock.toIntArray().toJson()).put("waste", k.waste.toIntArray().toJson())
        .put("found", JSONArray().also { a -> k.foundations.forEach { a.put(it.toIntArray().toJson()) } })
        .put("tab", JSONArray().also { a -> k.tableau.forEach { a.put(it.toIntArray().toJson()) } })
        .put("down", k.down.toJson()).put("redeals", k.redealsUsed).put("moves", moves)

    companion object {
        fun restore(o: JSONObject): SolitaireSession {
            val d = Difficulty.valueOf(o.getString("difficulty"))
            val st = o.getJSONObject("state")
            val cfg = Klondike.config(d)
            fun lists(key: String) = st.getJSONArray(key).let { a -> List(a.length()) { a.getJSONArray(it).let { x -> MutableList(x.length()) { x.getInt(it) } } } }
            val k = Klondike(cfg.draw, cfg.redeals, st.ints("stock").toMutableList(), st.ints("waste").toMutableList(),
                lists("found"), lists("tab"), st.ints("down"), st.getInt("redeals"))
            return SolitaireSession(d, k, st.getInt("moves")).also { it.restoreCommon(o) }
        }
    }
}

object SolitaireDef : GameDef {
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_solitaire
    override val kind = GameKind.SOLITAIRE
    private val rng = Random(SecureRandom().nextLong())
    override fun generate(d: Difficulty): Session = SolitaireSession(d, synchronized(rng) { Klondike.deal(d, rng) })
    override fun restore(o: JSONObject): Session = SolitaireSession.restore(o)
    override fun hint(d: Difficulty) = "Build the four suits up from Ace to King. In the columns, stack down in alternating colours. Tap a card to move it."
    override fun levelInfo(d: Difficulty) = Klondike.config(d).let { "Draw ${it.draw} · " + if (it.redeals < 0) "unlimited passes" else "${it.redeals + 1} pass${if (it.redeals == 0) "" else "es"}" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Turn one card at a time, as often as you like."
        Difficulty.MEDIUM -> "One at a time, three passes."
        Difficulty.HARD -> "Turn three at a time."
        Difficulty.EXPERT -> "Three at a time, three passes."
        Difficulty.MASTER -> "Three at a time, two passes."
        Difficulty.EXTREME -> "One pass, no undo."
    }

    @Composable override fun Play(session: Session) = SolitaireTable(session as SolitaireSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    private fun demo(): SolitaireSession {
        // A hand-set table: an Ace ready to go up, a 6♥ that fits on the 7♠, and a face-down card under it.
        val cfg = Klondike.config(Difficulty.EASY)
        val k = Klondike(cfg.draw, cfg.redeals)
        val c = Cards::of
        k.tableau[0] += c(1, 3)
        k.tableau[1] += listOf(c(9, 0), c(7, 3)); k.down[1] = 1
        k.tableau[2] += listOf(c(4, 0), c(6, 2)); k.down[2] = 1
        k.tableau[3] += c(13, 1)
        k.tableau[4] += listOf(c(10, 2), c(12, 0)); k.down[4] = 1
        k.tableau[5] += c(2, 3)
        k.tableau[6] += listOf(c(5, 1), c(8, 2)); k.down[6] = 1
        k.stock += listOf(c(3, 2), c(11, 3), c(5, 3), c(9, 2), c(1, 2))
        return SolitaireSession(Difficulty.EASY, k)
    }

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Build the suits", "Get every card onto the four piles at the top, one suit each, from Ace up to King. Tap a card and it jumps to the best place it can go.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                tap("card-${Cards.of(1, 3)}") { s.tapCard(Pile.Tableau(0), 0) }
                wait(500)
                tap("card-${Cards.of(2, 3)}") { s.tapCard(Pile.Tableau(5), 0) }
                wait(1200)
            }) { t -> SolitaireTable(s, t) }
        },
        TutorialStep("Stack the columns", "In the columns, place cards downward in alternating colours (a red 6 on a black 7). Moving a card turns over the one beneath. Only Kings fill empty columns.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                tap("card-${Cards.of(6, 2)}") { s.tapCard(Pile.Tableau(2), 1) }
                wait(800)
                tap("card-${Cards.of(4, 0)}") { s.tapCard(Pile.Tableau(2), 0) }
                wait(1200)
            }) { t -> SolitaireTable(s, t) }
        },
        TutorialStep("Turn the deck", "Tap the deck to turn cards over. When it runs out, tap again to turn the pile back over (higher levels limit how often). Stuck? Undo, or give up with the flag.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                tap("stock") { s.tapStock() }
                wait(500)
                tap("card-${Cards.of(1, 2)}") { s.tapCard(Pile.Waste, 0) }
                wait(500)
                tap("stock") { s.tapStock() }
                wait(1200)
            }) { t -> SolitaireTable(s, t) }
        },
    )
}

@Composable
private fun SolitaireTable(s: SolitaireSession, targets: DemoTargets?) {
    com.puresudoku.ui.components.ProvideCardTheme(com.puresudoku.ui.components.CardThemes.SOLITAIRE) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val k = s.k
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val gap = 5.dp
            val cw = min((maxWidth - gap * 6) / 7, 84.dp)
            val ch = cardHeight(cw)
            val left = (maxWidth - (cw * 7 + gap * 6)) / 2
            fun x(col: Int) = left + (cw + gap) * col
            val tabTop = ch + 14.dp
            // squeeze columns that would run off the bottom
            val avail = maxHeight - tabTop - ch
            val dests = s.targets.toSet()
            val sel = s.selected

            // empty slots (tap targets)
            Box(Modifier.offset(x(0), 0.dp).demoTarget(targets, "stock")
                .clickable(remember { MutableInteractionSource() }, null) { s.tapStock() }) {
                CardSlot(cw, label = if (k.canRedeal()) "↻" else "")
            }
            for (f in 0 until 4) Box(Modifier.offset(x(3 + f), 0.dp).clickable(remember { MutableInteractionSource() }, null) { s.tapEmpty(Pile.Foundation(f)) }) {
                CardSlot(cw, label = "A", highlight = Pile.Foundation(f) in dests && k.foundations[f].isEmpty())
            }
            for (t in 0 until 7) Box(Modifier.offset(x(t), tabTop).clickable(remember { MutableInteractionSource() }, null) { s.tapEmpty(Pile.Tableau(t)) }) {
                CardSlot(cw, label = "K", highlight = Pile.Tableau(t) in dests && k.tableau[t].isEmpty())
            }

            val cards = ArrayList<Placed>(52)
            k.stock.forEachIndexed { i, c -> cards += Placed(c, x(0), 0.dp, false, i.toFloat(), onTap = { s.tapStock() }) }
            val fan = if (s.cfg.draw == 3) 3 else 1
            k.waste.forEachIndexed { i, c ->
                val fromEnd = k.waste.size - 1 - i
                val slot = if (fromEnd < fan) (fan - 1 - fromEnd).coerceIn(0, minOf(fan, k.waste.size) - 1) else 0
                val isTop = i == k.waste.lastIndex
                cards += Placed(c, x(1) + cw * 0.28f * slot, 0.dp, true, 100f + i, highlight = isTop && sel?.first == Pile.Waste,
                    tag = "card-$c", onTap = { if (isTop) s.tapCard(Pile.Waste, 0) })
            }
            for (f in 0 until 4) k.foundations[f].forEachIndexed { i, c ->
                val isTop = i == k.foundations[f].lastIndex
                cards += Placed(c, x(3 + f), 0.dp, true, 200f + i, highlight = isTop && Pile.Foundation(f) in dests, tag = "card-$c", onTap = {
                    if (s.selected != null && Pile.Foundation(f) in s.targets) s.tapEmpty(Pile.Foundation(f)) else if (isTop) s.tapCard(Pile.Foundation(f), i)
                })
            }
            for (t in 0 until 7) {
                val pile = k.tableau[t]
                val downStep = ch * 0.13f
                var upStep = ch * 0.27f
                val needed = downStep * k.down[t] + upStep * (pile.size - k.down[t] - 1).coerceAtLeast(0)
                if (needed > avail && pile.size - k.down[t] > 1) upStep = ((avail - downStep * k.down[t]) / (pile.size - k.down[t] - 1)).coerceAtLeast(ch * 0.12f)
                var y = tabTop
                pile.forEachIndexed { i, c ->
                    val up = k.faceUp(t, i)
                    val inSel = sel?.first == Pile.Tableau(t) && i >= sel.second
                    val isTop = i == pile.lastIndex
                    cards += Placed(c, x(t), y, up, 300f + i, highlight = inSel || (isTop && Pile.Tableau(t) in dests), tag = "card-$c", onTap = {
                        if (s.selected != null && Pile.Tableau(t) in s.targets && isTop && !inSel) s.tapEmpty(Pile.Tableau(t)) else s.tapCard(Pile.Tableau(t), i)
                    })
                    y += if (up) upStep else downStep
                }
            }
            CardLayer(cards, cw, s.errorFx, targets, from = DpOffset(x(0), 0.dp))
        }
        if (targets == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionButton(Icons.AutoMirrored.Outlined.Undo, "Undo", enabled = s.cfg.undo, onClick = s::undo)
                if (s.canAutoFinish) ActionButton(Icons.Outlined.AutoAwesome, "Finish", onClick = s::autoFinish)
                ActionButton(Icons.Outlined.Flag, "Give up", onClick = s::giveUp)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    }
}

// =====================================================================
// Pyramid: Dungeon Delver
// =====================================================================

class PyramidSession(
    difficulty: Difficulty,
    val floors: List<PyramidDeal>,
    floor: Int = 0,
    var removed: Long = 0L,
    var stockGone: Int = 0,
    var pos: Int = 0,
    var pass: Int = 0,
) : Session(GameKind.PYRAMID, difficulty) {
    val cfg = PyramidRules.config(difficulty)
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    var floor by mutableIntStateOf(floor); private set
    var version by mutableIntStateOf(0); private set
    /** Selected card: pyramid position 0..27, or 100 + stock index for the waste top. */
    var selected by mutableIntStateOf(-1); private set
    var errorFx by mutableStateOf<CardFx?>(null); private set
    var message by mutableStateOf(""); private set
    var floorFx by mutableLongStateOf(0L); private set

    val deal get() = floors[floor]
    override val usesStrikes get() = false
    override val middleStat get() = MiddleStat.Counter("Floor", "${floor + 1}/${cfg.floors}")
    override val progress: Float get() = (floor + java.lang.Long.bitCount(removed) / 28f) / cfg.floors
    override val wonTitle get() = "Treasure claimed!"
    override val lostTitle get() = "Torches out"
    override val lostMessage get() = "Your last torch burned out on floor ${floor + 1}. This game's save slot has been cleared."

    fun exposed(i: Int) = PyramidRules.exposed(i, removed)
    fun isRemoved(i: Int) = removed and (1L shl i) != 0L
    fun stockLeft(i: Int) = stockGone and (1 shl i) == 0
    val wasteTop: Int get() { for (sIdx in pos - 1 downTo 0) if (stockLeft(sIdx)) return sIdx; return -1 }
    val torchesLeft get() = cfg.passes - 1 - pass
    private fun cardOf(sel: Int) = if (sel >= 100) deal.stock[sel - 100] else deal.pyramid[sel]

    fun tapPyramid(i: Int) {
        if (outcome != Outcome.PLAYING || isRemoved(i)) return
        if (!exposed(i)) { errorFx = cardFx(deal.pyramid[i]); nudge("That card is still covered"); return }
        pick(i)
    }

    /** What to do next, in plain words. */
    val prompt: String
        get() {
            if (selected >= 0) {
                val c = cardOf(selected)
                val need = 13 - Cards.rank(c)
                return "${Cards.label(c)} picked: now tap ${if (need == 1 || need == 8) "an" else "a"} ${Cards.rankName(need)} to make 13 (or tap it again to cancel)"
            }
            return "Tap two uncovered cards that add up to 13 · Kings clear alone · tap the deck for more cards"
        }

    fun tapWaste() { val w = wasteTop; if (w >= 0 && outcome == Outcome.PLAYING) pick(100 + w) }

    private fun pick(sel: Int) {
        val c = cardOf(sel)
        if (Cards.rank(c) == 13) { remove(listOf(sel)); message = "A King falls alone: the wyvern is slain!"; return }
        val prev = selected
        if (prev < 0 || prev == sel) { selected = if (prev == sel) -1 else sel; version++; return }
        val a = cardOf(prev)
        if (Cards.rank(a) + Cards.rank(c) == 13) { remove(listOf(prev, sel)); message = "${Cards.label(a)} + ${Cards.label(c)} = 13"; return }
        errorFx = cardFx(c)
        nudge("${Cards.label(a)} and ${Cards.label(c)} make ${Cards.rank(a) + Cards.rank(c)}, not 13")
        selected = -1; version++
    }

    private fun remove(sels: List<Int>) {
        for (x in sels) if (x >= 100) stockGone = stockGone or (1 shl (x - 100)) else removed = removed or (1L shl x)
        selected = -1
        score += rules.basePoints * sels.size
        version++
        if (removed == (1L shl 28) - 1) floorCleared() else checkStuck()
        if (outcome == Outcome.PLAYING) changed()
    }

    fun draw() {
        if (outcome != Outcome.PLAYING) return
        selected = -1
        if (pos < 24) { pos++; while (pos < 24 && !stockLeft(pos - 1)) pos++ }
        else if (pass + 1 < cfg.passes) { pass++; pos = 0; message = "You light a new torch and search the floor again" }
        else { nudge("No torches left to search again"); return }
        version++
        checkStuck()
        if (outcome == Outcome.PLAYING) changed()
    }

    private fun floorCleared() {
        score += rules.basePoints * 20
        if (floor + 1 >= cfg.floors) {
            val b = Scoring.finish(rules, elapsedMs, 0)
            finish(true, listOf(BonusLine("Torches left bonus", torchesLeft.coerceAtLeast(0) * rules.basePoints * 10), BonusLine("Time bonus", b.timeBonus)))
            return
        }
        floor++; removed = 0; stockGone = 0; pos = 0; pass = 0
        message = "Floor cleared! You descend to floor ${floor + 1}"
        floorFx = System.nanoTime()
        version++
    }

    private fun hasMove(): Boolean {
        val ex = (0 until 28).filter { exposed(it) }.map { deal.pyramid[it] }
        val w = wasteTop.takeIf { it >= 0 }?.let { deal.stock[it] }
        val all = ex + listOfNotNull(w)
        if (all.any { Cards.rank(it) == 13 }) return true
        for (a in all.indices) for (b in a + 1 until all.size) if (Cards.rank(all[a]) + Cards.rank(all[b]) == 13) return true
        return pos < 24 || pass + 1 < cfg.passes
    }

    private fun checkStuck() { if (!hasMove()) finish(false) }

    override fun saveState(): JSONObject = JSONObject()
        .put("floors", JSONArray().also { a -> floors.forEach { a.put(JSONObject().put("p", it.pyramid.toJson()).put("s", it.stock.toJson())) } })
        .put("floor", floor).put("removed", removed).put("stockGone", stockGone).put("pos", pos).put("pass", pass)
}

object PyramidDef : GameDef {
    override fun background(session: Session): Int {
        val s = session as PyramidSession
        val f = s.floor; val n = s.floors.size
        return when {
            n > 1 && f == n - 1 -> com.puresudoku.R.drawable.bg_delver_3
            f > 0 || n == 1 && s.difficulty.ordinal >= 3 -> com.puresudoku.R.drawable.bg_delver_2
            else -> com.puresudoku.R.drawable.bg_delver_1
        }
    }
    override val kind = GameKind.PYRAMID
    private val rng = Random(SecureRandom().nextLong())

    override fun generate(d: Difficulty): Session {
        val cfg = PyramidRules.config(d)
        val floors = synchronized(rng) { List(cfg.floors) { PyramidRules.solvableDeal(cfg.passes, rng) } }
        return PyramidSession(d, floors)
    }

    override fun restore(o: JSONObject): Session {
        val d = Difficulty.valueOf(o.getString("difficulty"))
        val st = o.getJSONObject("state")
        val fa = st.getJSONArray("floors")
        val floors = List(fa.length()) { k -> fa.getJSONObject(k).let { PyramidDeal(it.ints("p"), it.ints("s")) } }
        return PyramidSession(d, floors, st.getInt("floor"), st.getLong("removed"), st.getInt("stockGone"), st.getInt("pos"), st.getInt("pass")).restoreCommon(o)
    }

    override fun hint(d: Difficulty) = "Tap two uncovered cards that add up to 13 to clear them (J 11, Q 12; Kings go alone). Clear the pyramid to descend."
    override fun levelInfo(d: Difficulty) = PyramidRules.config(d).let { "${it.floors} floor${if (it.floors > 1) "s" else ""} · ${it.passes} torch${if (it.passes > 1) "es" else ""} each" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "One floor, three torches."
        Difficulty.MEDIUM -> "Two floors, three torches each."
        Difficulty.HARD -> "Two floors, two torches each."
        Difficulty.EXPERT -> "Three floors deep."
        Difficulty.MASTER -> "Four floors deep."
        Difficulty.EXTREME -> "Five floors to the bottom."
    }

    @Composable override fun Play(session: Session) = PyramidTable(session as PyramidSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    private fun demo(): PyramidSession {
        val c = Cards::of
        // bottom row holds an 8 and a 5 side by side, plus a King; the stock starts with a Queen for the Ace
        val pyr = IntArray(28) { (it * 7 + 3) % 52 }
        val bottom = listOf(c(8, 0), c(5, 2), c(13, 3), c(1, 1), c(9, 0), c(4, 1), c(3, 3))
        for (k in 0..6) pyr[21 + k] = bottom[k]
        val used = pyr.toSet()
        val rest = (0 until 52).filter { it !in used }.toMutableList()
        rest.remove(c(12, 0)); rest.add(0, c(12, 0))
        // make the top rows distinct cards
        val top = (0 until 52).filter { it !in bottom && it != c(12, 0) }.shuffled(Random(5)).take(21)
        for (k in 0 until 21) pyr[k] = top[k]
        val stock = (listOf(c(12, 0)) + (0 until 52).filter { it !in pyr && it != c(12, 0) }).take(24).toIntArray()
        return PyramidSession(Difficulty.EASY, listOf(PyramidDeal(pyr, stock)))
    }

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Pairs of 13", "Clear uncovered cards in pairs that add up to 13: Ace 1, Jack 11, Queen 12. Tap one, then the other. Kings are worth 13 and go alone.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                tap("pyr-21") { s.tapPyramid(21) }; tap("pyr-22") { s.tapPyramid(22) }
                wait(600)
                tap("pyr-23") { s.tapPyramid(23) }
                wait(1200)
            }) { t -> PyramidTable(s, t) }
        },
        TutorialStep("Torches and the deck", "Tap the deck to turn a card; its top card can pair with the pyramid. When the deck runs out, a torch lets you search it again. Clear the whole pyramid to descend a floor.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                highlight(listOf("torches"), 1100)
                tap("stock") { s.draw() }
                wait(400)
                tap("waste") { s.tapWaste() }; tap("pyr-24") { s.tapPyramid(24) }
                wait(1400)
            }) { t -> PyramidTable(s, t) }
        },
    )
}

@Composable
private fun PyramidTable(s: PyramidSession, targets: DemoTargets?) {
    com.puresudoku.ui.components.ProvideCardTheme(com.puresudoku.ui.components.CardThemes.DELVER) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val deal = s.deal
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().demoTarget(targets, "torches"), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("FLOOR ${s.floor + 1} OF ${s.cfg.floors}", fontSize = 12.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text("   Torches: " + "🔥".repeat(maxOf(0, s.torchesLeft)) + "·".repeat(maxOf(0, s.cfg.passes - 1 - s.torchesLeft)), fontSize = 14.sp)
        }
        Spacer(Modifier.height(6.dp))
        TableMessage(s.message)
        Spacer(Modifier.height(4.dp))
        Text(s.prompt, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cw = min(maxWidth / 7.4f, (maxHeight - 10.dp) / (cardHeight(1.dp).value * (6 * 0.5f + 1) + 1.6f) )
            val ch = cardHeight(cw)
            val rowStep = ch * 0.5f
            val stockY = rowStep * 6 + ch + 16.dp
            val left = (maxWidth - cw * 7.2f) / 2
            val stockX = maxWidth / 2 - cw - 8.dp
            val wasteX = maxWidth / 2 + 8.dp
            Box(Modifier.offset(stockX, stockY).demoTarget(targets, "stock").clickable(remember { MutableInteractionSource() }, null) { s.draw() }) {
                CardSlot(cw, label = if (s.torchesLeft > 0) "🔥" else "")
            }
            Text(if (s.pos < 24) "Deck · tap" else if (s.torchesLeft > 0) "Relight" else "Empty",
                Modifier.offset(stockX, stockY + ch + 2.dp).width(cw), fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier) {
            }
            val cards = ArrayList<Placed>(52)
            for (i in 0 until 28) {
                if (s.isRemoved(i)) continue
                val r = PyramidRules.row(i); val kk = i - PyramidRules.index(r, 0)
                val x = left + cw * 1.03f * (kk + (6 - r) / 2f)
                cards += Placed(deal.pyramid[i], x, rowStep * r, true, r.toFloat(), highlight = s.selected == i, dim = !s.exposed(i),
                    tag = "pyr-$i", onTap = { s.tapPyramid(i) }, art = CardArt.delver(deal.pyramid[i]))
            }
            for (i in 0 until 24) if (i >= s.pos && s.stockLeft(i)) cards += Placed(deal.stock[i], stockX, stockY, false, 50f - i, onTap = { s.draw() })
            val w = s.wasteTop
            for (i in 0 until s.pos) if (s.stockLeft(i)) cards += Placed(deal.stock[i], wasteX, stockY, true, 60f + i,
                highlight = s.selected == 100 + i && i == w, tag = if (i == w) "waste" else null, onTap = { if (i == w) s.tapWaste() }, art = CardArt.delver(deal.stock[i]))
            androidx.compose.runtime.key(s.floor) { CardLayer(cards, cw, s.errorFx, targets, from = DpOffset(maxWidth / 2, stockY)) }
        }
    }
    }
}
