package com.puresudoku.core.cards

import kotlin.random.Random

/**
 * Cards are plain Ints 0..51: suit * 13 + (rank - 1).
 * Suits in Cuttle order: 0 clubs, 1 diamonds, 2 hearts, 3 spades.
 */
object Cards {
    fun of(rank: Int, suit: Int) = suit * 13 + (rank - 1)
    fun rank(c: Int) = c % 13 + 1
    fun suit(c: Int) = c / 13
    fun red(c: Int) = suit(c) == 1 || suit(c) == 2

    fun rankLabel(r: Int) = when (r) { 1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> r.toString() }
    fun suitSymbol(s: Int) = when (s) { 0 -> "♣"; 1 -> "♦"; 2 -> "♥"; else -> "♠" }
    fun suitName(s: Int) = when (s) { 0 -> "clubs"; 1 -> "diamonds"; 2 -> "hearts"; else -> "spades" }
    fun label(c: Int) = rankLabel(rank(c)) + suitSymbol(suit(c))
    fun rankName(r: Int) = when (r) { 1 -> "Ace"; 11 -> "Jack"; 12 -> "Queen"; 13 -> "King"; else -> r.toString() }
    fun plural(r: Int) = when (r) { 1 -> "Aces"; 11 -> "Jacks"; 12 -> "Queens"; 13 -> "Kings"; 6 -> "Sixes"; else -> "${r}s" }

    fun deck(rng: Random): MutableList<Int> = (0 until 52).shuffled(rng).toMutableList()
}
