package com.puresudoku.ui.games.cards

import com.puresudoku.R
import com.puresudoku.core.cards.Cards
import com.puresudoku.core.cards.JOKER
import com.puresudoku.core.cards.Scoundrel

/**
 * Pixel-art card illustrations for the fantasy card games (vector drawables in res/drawable,
 * drawn in Sprite Studio; sources in tools/card_art). 0 means "plain card face".
 */
object CardArt {
    const val JESTER = R.drawable.art_jester

    /** A monster scaled to its strength (2..14). */
    fun monster(value: Int) = when {
        value <= 4 -> R.drawable.art_slime
        value <= 7 -> R.drawable.art_goblin
        value <= 10 -> R.drawable.art_skeleton
        value <= 12 -> R.drawable.art_troll
        else -> R.drawable.art_dragon
    }

    fun weapon(value: Int) = when {
        value <= 4 -> R.drawable.art_dagger
        value <= 7 -> R.drawable.art_sword
        else -> R.drawable.art_battleaxe
    }

    fun scoundrel(c: Int) = when {
        Scoundrel.isPotion(c) -> R.drawable.art_potion
        Scoundrel.isWeapon(c) -> weapon(Scoundrel.value(c))
        else -> monster(Scoundrel.value(c))
    }

    /** Regicide: the corrupted royals get portraits; everything else stays a plain card. */
    fun regicide(c: Int) = when (Cards.rank(c)) {
        11 -> R.drawable.art_dark_knight
        12 -> R.drawable.art_dark_queen
        13 -> R.drawable.art_lich_king
        else -> 0
    }

    /** Dungeon Solitaire: what a card means depends on whether it's the turn's encounter. */
    fun dungeon(c: Int, encounter: Boolean): Int {
        if (c == JOKER) return R.drawable.art_scroll
        val r = Cards.rank(c); val s = Cards.suit(c)
        return when {
            r == 1 -> R.drawable.art_torch
            r == 11 -> when (s) { 3 -> R.drawable.art_battleaxe; 1 -> R.drawable.art_dagger; 0 -> R.drawable.art_key; else -> R.drawable.art_boot }
            r == 12 -> R.drawable.art_blessing
            r == 13 -> R.drawable.art_chest
            encounter -> when (s) { 3 -> monster(r); 1 -> R.drawable.art_trap; else -> R.drawable.art_door }
            s == 1 -> R.drawable.art_coins
            else -> 0
        }
    }
}
