package com.puresudoku.core.cards

import com.puresudoku.core.Difficulty
import kotlin.random.Random

/** A point card on the field; [jacks] stacked on it have changed who controls it. */
class PointCard(val card: Int, val jacks: MutableList<Int> = mutableListOf())

sealed interface CMove {
    val card: Int
    data class Draw(override val card: Int = -1) : CMove
    data class Pass(override val card: Int = -1) : CMove
    data class Points(override val card: Int) : CMove
    data class Scuttle(override val card: Int, val target: Int) : CMove   // target = opponent point card
    data class Royal(override val card: Int) : CMove                      // K, Q or 8 as glasses
    data class Jack(override val card: Int, val target: Int) : CMove      // steal opponent point card
    data class OneOff(override val card: Int, val target: Int = -1) : CMove // target card for 2 / 9
}

/** What the game is waiting for. */
sealed interface CWait {
    data object Move : CWait
    /** [who] may answer the pending one-off with a 2. */
    data class Counter(val who: Int) : CWait
    /** [who] must discard [count] cards (opponent played a 4). */
    data class Discard(val who: Int, val count: Int) : CWait
    /** [who] picks a card from the scrap pile (played a 3). */
    data class ScrapPick(val who: Int) : CWait
    /** [who] must play the revealed card [card] (played a 7). */
    data class Seven(val who: Int, val card: Int) : CWait
}

/**
 * Cuttle, the classic combat card game, for two players (0 = you, 1 = AI).
 * First to the point goal wins (21, lowered by Kings: 14 / 10 / 5 / 0).
 */
class Cuttle(val rng: Random) {
    val deck = mutableListOf<Int>()
    val scrap = mutableListOf<Int>()
    val hands = List(2) { mutableListOf<Int>() }
    val points = List(2) { mutableListOf<PointCard>() }
    /** Kings, Queens and Eights played as glasses. */
    val royals = List(2) { mutableListOf<Int>() }
    var turn = 0
    var wait: CWait = CWait.Move
    var winner = -1
    var stalemate = false
    var passes = 0
    var turns = 0
    val log = mutableListOf<String>()

    // pending one-off and its counters
    var pending: CMove.OneOff? = null
    var pendingBy = -1
    val counters = mutableListOf<Int>()

    companion object {
        const val HAND_LIMIT = 8
        fun newGame(rng: Random): Cuttle {
            val g = Cuttle(rng)
            g.deck += Cards.deck(rng)
            repeat(5) { g.hands[1] += g.deck.removeAt(g.deck.lastIndex) }
            repeat(6) { g.hands[0] += g.deck.removeAt(g.deck.lastIndex) }
            return g
        }
        fun r(c: Int) = Cards.rank(c)
        fun name(c: Int) = Cards.label(c)
        /** Higher card for scuttling: by rank, then suit (♣ < ♦ < ♥ < ♠). */
        fun beats(a: Int, b: Int) = r(a) > r(b) || (r(a) == r(b) && Cards.suit(a) > Cards.suit(b))

        fun oneOffText(rank: Int) = when (rank) {
            1 -> "scrap every point card"
            2 -> "scrap a royal or glasses"
            3 -> "take a card from the scrap pile"
            4 -> "make the opponent discard two"
            5 -> "draw two cards"
            6 -> "scrap every royal and glasses"
            7 -> "play the top card of the deck"
            9 -> "return a royal or glasses to its owner's hand"
            else -> ""
        }
    }

    fun opp(p: Int) = 1 - p
    fun score(p: Int) = points[p].sumOf { r(it.card) }
    fun kings(p: Int) = royals[p].count { r(it) == 13 }
    fun goal(p: Int) = when (kings(p)) { 0 -> 21; 1 -> 14; 2 -> 10; 3 -> 5; else -> 0 }
    fun queens(p: Int) = royals[p].count { r(it) == 12 }
    fun glasses(p: Int) = royals[p].any { r(it) == 8 }

    /** Cards of player [p] that the opponent may target with a 2, 9 or Jack. */
    fun targetable(p: Int, c: Int): Boolean {
        val q = queens(p)
        if (q == 0) return true
        if (q == 1) return r(c) == 12
        return false
    }

    /** Permanents (royals, glasses, jacks) of [p] that a 2 or 9 can hit. */
    fun permanents(p: Int): List<Int> = (royals[p] + points[p].flatMap { it.jacks }).filter { targetable(p, it) }

    // ---------------------------------------------------------------- legal moves

    fun movesFor(p: Int, card: Int): List<CMove> {
        val o = opp(p)
        val out = ArrayList<CMove>()
        val rk = r(card)
        if (rk <= 10) {
            out += CMove.Points(card)
            for (pc in points[o]) if (beats(card, pc.card)) out += CMove.Scuttle(card, pc.card)
        }
        when (rk) {
            1, 3, 5, 6 -> out += CMove.OneOff(card)
            4 -> if (hands[o].isNotEmpty()) out += CMove.OneOff(card)
            7 -> if (deck.isNotEmpty()) out += CMove.OneOff(card)
            2, 9 -> for (t in permanents(o)) out += CMove.OneOff(card, t)
            8 -> out += CMove.Royal(card)
            11 -> for (pc in points[o]) if (targetable(o, pc.card)) out += CMove.Jack(card, pc.card)
            12, 13 -> out += CMove.Royal(card)
        }
        if (rk == 3 && scrap.isEmpty()) out.removeAll { it is CMove.OneOff }
        return out
    }

    fun allMoves(p: Int): List<CMove> {
        val out = ArrayList<CMove>()
        if (deck.isNotEmpty() && hands[p].size < HAND_LIMIT) out += CMove.Draw()
        for (c in hands[p]) out += movesFor(p, c)
        if (deck.isEmpty()) out += CMove.Pass()
        return out
    }

    // ---------------------------------------------------------------- playing

    private fun removeFromField(c: Int): Int? {
        // returns the owner it was taken from
        for (p in 0..1) {
            if (royals[p].remove(c)) return p
            val pc = points[p].firstOrNull { it.card == c }
            if (pc != null) { points[p].remove(pc); return p }
            for (x in points[p]) if (x.jacks.remove(c)) {
                // the point card goes back to the other player
                points[p].remove(x); points[opp(p)] += x
                return p
            }
        }
        return null
    }

    private fun scrapPointCard(pc: PointCard) { scrap += pc.card; scrap += pc.jacks; pc.jacks.clear() }

    /** Plays a main move for the player whose turn it is (or the 7's revealed card). */
    fun play(m: CMove) {
        val p = turn
        val o = opp(p)
        val fromSeven = (wait as? CWait.Seven)?.card == m.card && m.card >= 0
        if (m !is CMove.Draw && m !is CMove.Pass) { if (!fromSeven) hands[p].remove(m.card) }
        wait = CWait.Move
        when (m) {
            is CMove.Draw -> { hands[p] += deck.removeAt(deck.lastIndex); log += "${who(p)} drew a card"; passes = 0 }
            is CMove.Pass -> { passes++; log += "${who(p)} passed" }
            is CMove.Points -> { points[p] += PointCard(m.card); log += "${who(p)} played ${name(m.card)} for ${r(m.card)} points"; passes = 0 }
            is CMove.Scuttle -> {
                val pc = points[o].first { it.card == m.target }
                points[o].remove(pc); scrapPointCard(pc); scrap += m.card
                log += "${who(p)} scuttled ${name(m.target)} with ${name(m.card)}"; passes = 0
            }
            is CMove.Royal -> { royals[p] += m.card; log += "${who(p)} played ${name(m.card)}" + if (r(m.card) == 8) " as glasses" else ""; passes = 0 }
            is CMove.Jack -> {
                val pc = points[o].first { it.card == m.target }
                points[o].remove(pc); pc.jacks += m.card; points[p] += pc
                log += "${who(p)} stole ${name(m.target)} with ${name(m.card)}"; passes = 0
            }
            is CMove.OneOff -> {
                pending = m; pendingBy = p; counters.clear(); passes = 0
                log += "${who(p)} played ${name(m.card)}: ${oneOffText(r(m.card))}"
                wait = CWait.Counter(o)
                return
            }
        }
        endTurn()
    }

    /** Answer to a pending one-off: play a 2 to counter, or null to let it happen. */
    fun counter(p: Int, two: Int?) {
        val w = wait as CWait.Counter
        require(w.who == p)
        if (two != null) {
            hands[p].remove(two); counters += two
            log += "${who(p)} countered with ${name(two)}"
            wait = CWait.Counter(opp(p))
            return
        }
        // chain ends: odd number of counters cancels the one-off
        val m = pending!!
        val by = pendingBy
        scrap += m.card; scrap += counters
        pending = null
        val cancelled = counters.size % 2 == 1
        counters.clear()
        turn = by
        if (cancelled) { log += "The ${name(m.card)} was countered"; wait = CWait.Move; endTurn(); return }
        resolve(by, m)
    }

    private fun resolve(p: Int, m: CMove.OneOff) {
        val o = opp(p)
        when (r(m.card)) {
            1 -> { for (q in 0..1) { points[q].forEach { scrapPointCard(it) }; points[q].clear() } }
            2, 9 -> {
                val t = m.target
                val stillThere = (royals[o] + points[o].flatMap { it.jacks }).contains(t)
                if (stillThere) {
                    val owner = removeFromField(t)
                    if (r(m.card) == 2) scrap += t else hands[owner ?: o] += t
                }
            }
            3 -> { if (scrap.any { it != m.card }) { wait = CWait.ScrapPick(p); return } }
            4 -> {
                val n = minOf(2, hands[o].size)
                if (n > 0) { wait = CWait.Discard(o, n); return }
            }
            5 -> repeat(2) { if (deck.isNotEmpty() && hands[p].size < HAND_LIMIT) hands[p] += deck.removeAt(deck.lastIndex) }
            6 -> for (q in 0..1) {
                scrap += royals[q]; royals[q].clear()
                // jacks fall off; stolen cards return to their owners
                for (pc in points[q].toList()) if (pc.jacks.isNotEmpty()) {
                    val n = pc.jacks.size
                    scrap += pc.jacks; pc.jacks.clear()
                    if (n % 2 == 1) { points[q].remove(pc); points[opp(q)] += pc }
                }
            }
            7 -> if (deck.isNotEmpty()) {
                val c = deck.removeAt(deck.lastIndex)
                log += "${who(p)} revealed ${name(c)}"
                if (movesFor(p, c).isEmpty()) { scrap += c; log += "It couldn't be played and was scrapped" }
                else { wait = CWait.Seven(p, c); return }
            }
        }
        wait = CWait.Move
        endTurn()
    }

    fun discard(p: Int, cards: List<Int>) {
        val w = wait as CWait.Discard
        require(w.who == p && cards.size == w.count)
        for (c in cards) { hands[p].remove(c); scrap += c }
        log += "${who(p)} discarded ${cards.joinToString { name(it) }}"
        wait = CWait.Move
        turn = opp(p)
        endTurn()
    }

    fun pickScrap(p: Int, c: Int) {
        scrap.remove(c); hands[p] += c
        log += "${who(p)} took ${name(c)} from the scrap"
        wait = CWait.Move
        endTurn()
    }

    private fun endTurn() {
        checkWin()
        if (winner >= 0) return
        if (passes >= 3) { stalemate = true; log += "Three passes in a row: stalemate"; return }
        if (++turns > 400) { stalemate = true; log += "The game ran too long: stalemate"; return }
        turn = opp(turn)
        wait = CWait.Move
    }

    fun checkWin() {
        for (p in listOf(turn, opp(turn))) if (score(p) >= goal(p)) { winner = p; return }
    }

    fun who(p: Int) = if (p == 0) "You" else "Opponent"
    val over get() = winner >= 0 || stalemate

    // ---------------------------------------------------------------- AI

    /** Higher is better for player [p]. [skill] 0..1 turns on smarter play. */
    fun value(p: Int, m: CMove, skill: Double): Double {
        val o = opp(p)
        val myNeed = goal(p) - score(p)
        val oppNeed = goal(o) - score(o)
        val danger = if (oppNeed <= 7) (8 - oppNeed) * 1.5 else 0.0
        return when (m) {
            is CMove.Draw -> 2.0 + (if (hands[p].size <= 3) 2.0 else 0.0)
            is CMove.Pass -> -5.0
            is CMove.Points -> if (r(m.card) >= myNeed) 1000.0 else r(m.card) * 1.0 + (if (r(m.card) <= 2) -1.5 else 0.0)
            is CMove.Scuttle -> {
                val t = points[o].first { it.card == m.target }
                r(t.card) * (1.0 + skill) + danger - r(m.card) * 0.6 + t.jacks.size * 2
            }
            is CMove.Royal -> when (r(m.card)) {
                13 -> { val newGoal = when (kings(p) + 1) { 1 -> 14; 2 -> 10; 3 -> 5; else -> 0 }; if (score(p) >= newGoal) 1000.0 else 7.0 }
                12 -> 4.0 + skill * 3
                else -> 2.5
            }
            is CMove.Jack -> { val t = points[o].first { it.card == m.target }; if (score(p) + r(t.card) >= goal(p)) 1000.0 else r(t.card) * 2.0 + danger }
            is CMove.OneOff -> when (r(m.card)) {
                1 -> (score(o) - score(p)) * 1.3 + danger - 2
                2, 9 -> {
                    val t = m.target
                    val base = when (r(t)) { 13 -> 9.0; 12 -> 6.0; 11 -> 2.0 * (points[p].firstOrNull { pc -> t in pc.jacks }?.let { r(it.card) } ?: points[o].firstOrNull { pc -> t in pc.jacks }?.let { r(it.card) } ?: 3); 8 -> 3.0; else -> 1.0 }
                    (if (r(m.card) == 9) base * 0.6 else base) + danger * 0.5 - (if (r(m.card) == 2) skill * 2.5 else 0.0)
                }
                3 -> (scrap.maxOfOrNull { cardWorth(it) } ?: 0.0) * 0.7
                4 -> 3.0 + minOf(2, hands[o].size) * 1.2
                5 -> if (hands[p].size <= 6) 4.5 else 1.0
                6 -> (royals[o].sumOf { royalWorth(it) } - royals[p].sumOf { royalWorth(it) }) * 1.0
                7 -> 2.5
                else -> 0.0
            }
        }
    }

    private fun royalWorth(c: Int) = when (r(c)) { 13 -> 8.0; 12 -> 5.0; 8 -> 2.0; else -> 1.0 }
    fun cardWorth(c: Int) = when (r(c)) { 13 -> 8.0; 2 -> 6.0; 12 -> 5.0; 11 -> 5.0; else -> r(c) * 0.6 }

    fun aiMove(p: Int, skill: Double): CMove {
        val noise = (1.0 - skill) * 6
        return allMoves(p).maxBy { value(p, it, skill) + rng.nextDouble() * noise }
    }

    fun aiCounter(p: Int, skill: Double): Int? {
        val two = hands[p].firstOrNull { r(it) == 2 } ?: return null
        if (skill < 0.25) return null
        val m = pending ?: return null
        // only bother countering things that hurt us
        val harmful = if (pendingBy == p) false else value(pendingBy, m, skill) > 4 - skill * 2
        return if (harmful) two else null
    }

    fun aiDiscard(p: Int, n: Int) = hands[p].sortedBy { cardWorth(it) }.take(n)
    fun aiScrapPick() = scrap.maxBy { cardWorth(it) }
    fun aiSeven(p: Int, card: Int, skill: Double) = movesFor(p, card).maxBy { value(p, it, skill) }
}

object CuttleRules {
    data class Config(val skill: Double, val basePoints: Int, val parSeconds: Int)
    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(0.0, 10, 600)
        Difficulty.MEDIUM -> Config(0.3, 15, 600)
        Difficulty.HARD -> Config(0.55, 25, 600)
        Difficulty.EXPERT -> Config(0.75, 35, 600)
        Difficulty.MASTER -> Config(0.9, 50, 600)
        Difficulty.EXTREME -> Config(1.0, 75, 600)
    }
}
