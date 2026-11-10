package com.puresudoku.core.cards

import com.puresudoku.core.Difficulty
import kotlin.random.Random

/**
 * Crazy Eights. Match the top card's suit or rank; eights are wild and name a new suit.
 * Can't play? Draw until you can (if the pile runs out, the discards are reshuffled; if
 * nothing is left at all, pass). First to empty their hand wins. You are player 0.
 */
class CrazyEights(val players: Int, val skill: Double, private val rng: Random) {
    val deck = ArrayList<Int>()
    val discard = ArrayList<Int>()
    val hands = List(players) { ArrayList<Int>() }
    var turn = 0
    var suit = 0
    var winner = -1
    var passesInRow = 0
    /** Suits each player was seen drawing on (they lacked them). */
    val lacks = List(players) { HashSet<Int>() }

    data class Config(val opponents: Int, val skill: Double, val basePoints: Int, val parSeconds: Int)

    companion object {
        fun config(d: Difficulty) = when (d) {
            Difficulty.EASY -> Config(1, 0.0, 10, 240)
            Difficulty.MEDIUM -> Config(2, 0.3, 15, 300)
            Difficulty.HARD -> Config(2, 0.7, 25, 300)
            Difficulty.EXPERT -> Config(3, 0.6, 35, 360)
            Difficulty.MASTER -> Config(3, 0.85, 50, 360)
            Difficulty.EXTREME -> Config(3, 1.0, 75, 420)
        }
        fun value(c: Int) = when (val r = Cards.rank(c)) { 8 -> 50; 11, 12, 13 -> 10; 1 -> 1; else -> r }

        fun newGame(d: Difficulty, rng: Random): CrazyEights {
            val cfg = config(d)
            val g = CrazyEights(cfg.opponents + 1, cfg.skill, rng)
            g.deck += Cards.deck(rng)
            val n = if (g.players == 2) 7 else 5
            repeat(n) { for (p in 0 until g.players) g.hands[p] += g.deck.removeAt(g.deck.lastIndex) }
            // the starter is never an eight
            var top = g.deck.removeAt(g.deck.lastIndex)
            while (Cards.rank(top) == 8) { g.deck.add(0, top); top = g.deck.removeAt(g.deck.lastIndex) }
            g.discard += top; g.suit = Cards.suit(top)
            return g
        }
    }

    val top get() = discard.last()

    fun canPlay(c: Int) = Cards.rank(c) == 8 || Cards.suit(c) == suit || Cards.rank(c) == Cards.rank(top)
    fun playable(p: Int) = hands[p].filter { canPlay(it) }

    /** Draws one card for [p]; reshuffles the discards when needed. Null when nothing is left. */
    fun draw(p: Int): Int? {
        if (deck.isEmpty() && discard.size > 1) {
            val keep = discard.removeAt(discard.lastIndex)
            deck += discard.shuffled(rng); discard.clear(); discard += keep
        }
        if (deck.isEmpty()) return null
        val c = deck.removeAt(deck.lastIndex)
        hands[p] += c
        lacks[p] += suit
        return c
    }

    fun play(p: Int, c: Int, newSuit: Int = Cards.suit(c)) {
        hands[p].remove(c); discard += c
        suit = if (Cards.rank(c) == 8) newSuit else Cards.suit(c)
        passesInRow = 0
        if (hands[p].isEmpty()) winner = p
        turn = (p + 1) % players
    }

    fun pass(p: Int) { passesInRow++; turn = (p + 1) % players }

    /** Everyone stuck with nothing to draw: the lightest hand wins. */
    val blocked get() = passesInRow >= players

    fun lightest(): Int = (0 until players).minBy { p -> hands[p].sumOf { value(it) } * 100 + p }

    fun bestSuit(p: Int): Int {
        val counts = IntArray(4); for (c in hands[p]) if (Cards.rank(c) != 8) counts[Cards.suit(c)]++
        val next = (p + 1) % players
        return (0..3).maxBy { s -> counts[s] * 10 + (if (skill > 0.5 && s in lacks[next]) 6 else 0) + rng.nextInt(2) }
    }

    /** Computer's choice: a card to play (and suit for an eight), or null to draw. */
    fun aiChoice(p: Int): Pair<Int, Int>? {
        val opts = playable(p)
        if (opts.isEmpty()) return null
        if (rng.nextDouble() > skill) {
            val c = opts[rng.nextInt(opts.size)]
            return c to bestSuit(p)
        }
        val nonEights = opts.filter { Cards.rank(it) != 8 }
        val next = (p + 1) % players
        val danger = hands[next].size <= 2
        val pick = if (nonEights.isNotEmpty() && !(danger && opts.any { Cards.rank(it) == 8 } && nonEights.none { Cards.suit(it) in lacks[next] })) {
            val counts = IntArray(4); for (c in hands[p]) counts[Cards.suit(c)]++
            nonEights.maxBy { c ->
                var v = counts[Cards.suit(c)] * 4 + value(c)
                if (Cards.suit(c) in lacks[next]) v += 12
                if (Cards.suit(c) != suit) v += 2 // switching suits via rank keeps options open
                v
            }
        } else opts.first { Cards.rank(it) == 8 }
        return pick to bestSuit(p)
    }

    fun state(): IntArray {
        val out = ArrayList<Int>()
        out += listOf(players, turn, suit, winner, passesInRow, deck.size); out += deck
        out += discard.size; out += discard
        for (h in hands) { out += h.size; out += h }
        for (l in lacks) { var m = 0; for (s in l) m = m or (1 shl s); out += m }
        return out.toIntArray()
    }

    fun load(s: IntArray) {
        var k = 1
        turn = s[k++]; suit = s[k++]; winner = s[k++]; passesInRow = s[k++]
        deck.clear(); repeat(s[k++]) { deck += s[k++] }
        discard.clear(); repeat(s[k++]) { discard += s[k++] }
        for (h in hands) { h.clear(); repeat(s[k++]) { h += s[k++] } }
        for (l in lacks) { l.clear(); val m = s[k++]; for (b in 0..3) if (m and (1 shl b) != 0) l += b }
    }
}
