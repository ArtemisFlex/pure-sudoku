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
    val JESTER get() = R.drawable.art_jester

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
        1 -> R.drawable.art_rg_wolf
        else -> when (Cards.suit(c)) { 2 -> R.drawable.art_rg_heal; 1 -> R.drawable.art_rg_draw; 0 -> R.drawable.art_rg_hammer; else -> R.drawable.art_rg_shield }
    }

    /** Dungeon Solitaire's own tomb monsters (2..10). */
    fun tombMonster(r: Int) = when {
        r <= 4 -> R.drawable.art_ds_bat
        r <= 7 -> R.drawable.art_ds_spider
        else -> R.drawable.art_ds_mummy
    }

    /** Cuttle: each rank shows what it does. */
    fun cuttle(c: Int) = when (Cards.rank(c)) {
        1 -> R.drawable.art_cut_purge
        2 -> R.drawable.art_cut_counter
        3 -> R.drawable.art_cut_salvage
        4 -> R.drawable.art_cut_discard
        5 -> R.drawable.art_cut_draw
        6 -> R.drawable.art_cut_topple
        7 -> R.drawable.art_cut_reveal
        8 -> R.drawable.art_cut_glasses
        9 -> R.drawable.art_cut_return
        10 -> R.drawable.art_cut_gem
        11 -> R.drawable.art_cut_steal
        12 -> R.drawable.art_cut_tower
        else -> R.drawable.art_cut_crown
    }

    /** Dungeon Delver: the face cards and Aces of the deep. */
    fun delver(c: Int) = when (Cards.rank(c)) {
        1 -> R.drawable.art_dd_lantern
        11 -> R.drawable.art_dd_delver
        12 -> R.drawable.art_dd_oracle
        13 -> R.drawable.art_dd_wyvern
        else -> 0
    }

    /** Dungeon Solitaire: what a card means depends on whether it's the turn's encounter. */
    fun dungeon(c: Int, encounter: Boolean): Int {
        if (c == JOKER) return R.drawable.art_scroll
        val r = Cards.rank(c); val s = Cards.suit(c)
        return when {
            r == 1 -> R.drawable.art_torch
            r == 11 -> when (s) { 3 -> R.drawable.art_ds_berserk; 1 -> R.drawable.art_ds_disarm; 0 -> R.drawable.art_key; else -> R.drawable.art_boot }
            r == 12 -> R.drawable.art_blessing
            r == 13 -> R.drawable.art_chest
            encounter -> when (s) { 3 -> tombMonster(r); 1 -> R.drawable.art_trap; else -> R.drawable.art_door }
            s == 1 -> R.drawable.art_coins
            else -> 0
        }
    }
}
