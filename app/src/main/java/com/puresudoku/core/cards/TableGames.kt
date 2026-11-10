package com.puresudoku.core.cards

import com.puresudoku.core.Difficulty
import kotlin.random.Random

// =====================================================================
// Blackjack
// =====================================================================

object BlackjackRules {
    data class Config(
        val decks: Int, val hitSoft17: Boolean, val bjPays: Pair<Int, Int>, val hands: Int,
        val start: Int, val targetPct: Int, val hints: Boolean, val basePoints: Int, val parSeconds: Int,
    )

    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(1, false, 3 to 2, 20, 100, 120, true, 10, 600)
        Difficulty.MEDIUM -> Config(2, false, 3 to 2, 20, 100, 130, true, 15, 600)
        Difficulty.HARD -> Config(4, true, 3 to 2, 20, 100, 140, false, 25, 600)
        Difficulty.EXPERT -> Config(6, true, 3 to 2, 20, 100, 150, false, 35, 600)
        Difficulty.MASTER -> Config(6, true, 6 to 5, 20, 100, 170, false, 50, 600)
        Difficulty.EXTREME -> Config(8, true, 6 to 5, 20, 100, 200, false, 75, 600)
    }

    val BETS = listOf(5, 10, 25, 50)

    /** Card value for blackjack: A=1 (soft 11 handled in total), faces 10. */
    fun v(c: Int) = minOf(10, Cards.rank(c))

    /** (total, soft?) */
    fun total(hand: List<Int>): Pair<Int, Boolean> {
        var t = hand.sumOf { v(it) }
        val ace = hand.any { Cards.rank(it) == 1 }
        if (ace && t + 10 <= 21) return (t + 10) to true
        return t to false
    }

    fun isBlackjack(hand: List<Int>) = hand.size == 2 && total(hand).first == 21

    /** Basic strategy for multi-deck, dealer stands on soft 17 (close enough as a hint). */
    fun advice(hand: List<Int>, dealerUp: Int, canDouble: Boolean, canSplit: Boolean): String {
        val up = v(dealerUp).let { if (it == 1) 11 else it }
        val (t, soft) = total(hand)
        if (canSplit) {
            val r = v(hand[0])
            val split = when (r) {
                1, 8 -> true
                10, 5 -> false
                9 -> up !in listOf(7, 10, 11)
                7 -> up <= 7
                6 -> up <= 6
                4 -> up in 5..6
                2, 3 -> up <= 7
                else -> false
            }
            if (split) return "Split"
        }
        if (soft) {
            return when {
                t >= 20 -> "Stand"
                t == 19 -> if (up == 6 && canDouble) "Double" else "Stand"
                t == 18 -> when { up in 2..6 && canDouble -> "Double"; up <= 8 -> "Stand"; else -> "Hit" }
                t == 17 -> if (up in 3..6 && canDouble) "Double" else "Hit"
                t in 15..16 -> if (up in 4..6 && canDouble) "Double" else "Hit"
                else -> if (up in 5..6 && canDouble) "Double" else "Hit"
            }
        }
        return when {
            t >= 17 -> "Stand"
            t in 13..16 -> if (up <= 6) "Stand" else "Hit"
            t == 12 -> if (up in 4..6) "Stand" else "Hit"
            t == 11 -> if (canDouble) "Double" else "Hit"
            t == 10 -> if (up <= 9 && canDouble) "Double" else "Hit"
            t == 9 -> if (up in 3..6 && canDouble) "Double" else "Hit"
            else -> "Hit"
        }
    }
}

/** One blackjack table: a shoe, the dealer and up to two player hands (after a split). */
class BlackjackTable(val cfg: BlackjackRules.Config, val shoe: MutableList<Int>, val rng: Random) {
    val dealer = mutableListOf<Int>()
    val hands = mutableListOf<MutableList<Int>>()
    val bets = mutableListOf<Int>()
    val doubled = mutableListOf<Boolean>()
    var active = 0
    var dealerRevealed = false

    companion object {
        fun shoe(decks: Int, rng: Random) = MutableList(52 * decks) { it % 52 }.also { it.shuffle(rng) }
    }

    fun draw(): Int {
        if (shoe.isEmpty()) shoe += shoe(cfg.decks, rng)
        return shoe.removeAt(shoe.lastIndex)
    }

    fun needsShuffle() = shoe.size < 52 * cfg.decks / 4

    fun deal(bet: Int) {
        if (needsShuffle()) { shoe.clear(); shoe += shoe(cfg.decks, rng) }
        dealer.clear(); hands.clear(); bets.clear(); doubled.clear()
        hands += mutableListOf(draw()); dealer += draw(); hands[0] += draw(); dealer += draw()
        bets += bet; doubled += false
        active = 0; dealerRevealed = false
    }

    val hand get() = hands[active]
    fun canDouble(chipsFree: Int) = hand.size == 2 && chipsFree >= bets[active]
    fun canSplit(chipsFree: Int) = hands.size == 1 && hand.size == 2 && BlackjackRules.v(hand[0]) == BlackjackRules.v(hand[1]) && chipsFree >= bets[0]

    fun hit() { hand += draw() }
    fun busted(k: Int = active) = BlackjackRules.total(hands[k]).first > 21

    fun split() {
        val second = mutableListOf(hand.removeAt(1))
        hands += second; bets += bets[0]; doubled += false
        hands[0] += draw(); hands[1] += draw()
    }

    fun doubleDown() { bets[active] *= 2; doubled[active] = true; hit() }

    /** Moves to the next hand; returns false when all hands are done. */
    fun nextHand(): Boolean { if (active + 1 < hands.size) { active++; return true }; return false }

    fun dealerPlays() {
        dealerRevealed = true
        if (hands.all { BlackjackRules.total(it).first > 21 }) return
        while (true) {
            val (t, soft) = BlackjackRules.total(dealer)
            if (t < 17 || (t == 17 && soft && cfg.hitSoft17)) dealer += draw() else break
        }
    }

    /** Net chips won/lost for each hand. */
    fun settle(): List<Int> {
        val (d, _) = BlackjackRules.total(dealer)
        val dealerBj = BlackjackRules.isBlackjack(dealer)
        return hands.indices.map { k ->
            val h = hands[k]; val bet = bets[k]
            val (t, _) = BlackjackRules.total(h)
            val bj = BlackjackRules.isBlackjack(h) && hands.size == 1
            when {
                t > 21 -> -bet
                bj && dealerBj -> 0
                bj -> bet * cfg.bjPays.first / cfg.bjPays.second
                dealerBj -> -bet
                d > 21 -> bet
                t > d -> bet
                t < d -> -bet
                else -> 0
            }
        }
    }
}

// =====================================================================
// Go Fish
// =====================================================================

sealed interface FishEvent {
    data class Asked(val from: Int, val to: Int, val rank: Int, val got: Int) : FishEvent
    data class Fished(val player: Int, val rank: Int, val lucky: Boolean, val card: Int) : FishEvent
    data class Book(val player: Int, val rank: Int) : FishEvent
    data class Drew(val player: Int) : FishEvent
}

class GoFish(
    val players: Int,
    /** Chance an AI remembers who asked for what (0..100). */
    val memory: Int,
    val rng: Random,
    val deck: MutableList<Int> = mutableListOf(),
    val hands: List<MutableList<Int>> = List(players) { mutableListOf() },
    val books: List<MutableList<Int>> = List(players) { mutableListOf() },
    var turn: Int = 0,
) {
    companion object {
        data class Config(val opponents: Int, val memory: Int, val basePoints: Int, val parSeconds: Int)

        fun config(d: Difficulty) = when (d) {
            Difficulty.EASY -> Config(1, 10, 10, 300)
            Difficulty.MEDIUM -> Config(2, 30, 15, 360)
            Difficulty.HARD -> Config(2, 65, 25, 360)
            Difficulty.EXPERT -> Config(3, 75, 35, 420)
            Difficulty.MASTER -> Config(3, 90, 50, 420)
            Difficulty.EXTREME -> Config(3, 100, 75, 420)
        }

        fun newGame(d: Difficulty, rng: Random): GoFish {
            val cfg = config(d)
            val g = GoFish(cfg.opponents + 1, cfg.memory, rng)
            g.deck += Cards.deck(rng)
            val n = if (g.players <= 3) 7 else 5
            repeat(n) { for (p in 0 until g.players) g.hands[p] += g.deck.removeAt(g.deck.lastIndex) }
            for (p in 0 until g.players) g.checkBooks(p, mutableListOf())
            return g
        }
    }

    /** What each AI believes: known[ai][player] = ranks that player is known to hold. */
    val known: List<List<MutableSet<Int>>> = List(players) { List(players) { mutableSetOf() } }

    val over get() = books.sumOf { it.size } == 13
    fun ranksIn(p: Int) = hands[p].map { Cards.rank(it) }.toSet()

    fun checkBooks(p: Int, events: MutableList<FishEvent>) {
        for (r in ranksIn(p)) {
            if (hands[p].count { Cards.rank(it) == r } == 4) {
                hands[p].removeAll { Cards.rank(it) == r }
                books[p] += r
                events += FishEvent.Book(p, r)
                for (k in known) for (s in k) s.remove(r)
            }
        }
    }

    /** An empty-handed player draws a card if they can. */
    fun refill(p: Int, events: MutableList<FishEvent>) {
        if (hands[p].isEmpty() && deck.isNotEmpty()) { hands[p] += deck.removeAt(deck.lastIndex); events += FishEvent.Drew(p) }
    }

    /** Passes the turn to the next player who can still play. */
    fun advance(events: MutableList<FishEvent>) {
        repeat(players) {
            turn = (turn + 1) % players
            refill(turn, events)
            if (hands[turn].isNotEmpty()) return
        }
    }

    /** Player [from] asks [to] for [rank]. Returns events; the turn moves on unless they got cards. */
    fun ask(from: Int, to: Int, rank: Int): List<FishEvent> {
        val events = mutableListOf<FishEvent>()
        val got = hands[to].filter { Cards.rank(it) == rank }
        // everyone now knows [from] holds this rank
        for (ai in 0 until players) if (ai != from && rng.nextInt(100) < memory) known[ai][from] += rank
        var again: Boolean
        if (got.isNotEmpty()) {
            hands[to].removeAll(got); hands[from] += got
            for (k in known) k[to].remove(rank)
            events += FishEvent.Asked(from, to, rank, got.size)
            again = true
        } else {
            events += FishEvent.Asked(from, to, rank, 0)
            if (deck.isNotEmpty()) {
                val c = deck.removeAt(deck.lastIndex)
                hands[from] += c
                val lucky = Cards.rank(c) == rank
                events += FishEvent.Fished(from, rank, lucky, c)
                again = lucky
            } else again = false
        }
        checkBooks(from, events)
        refill(from, events)
        refill(to, events)
        if (over) return events
        if (!again || hands[from].isEmpty()) advance(events)
        return events
    }

    /** The AI's choice of (target, rank). */
    fun aiChoice(p: Int): Pair<Int, Int> {
        val mine = hands[p].groupingBy { Cards.rank(it) }.eachCount()
        val others = (0 until players).filter { it != p && hands[it].isNotEmpty() }
        // a rank we hold that we know someone else has
        for ((r, _) in mine.entries.sortedByDescending { it.value }) {
            val who = others.firstOrNull { r in known[p][it] }
            if (who != null) return who to r
        }
        val r = mine.entries.sortedByDescending { it.value + rng.nextDouble() }.first().key
        return others[rng.nextInt(others.size)] to r
    }
}
