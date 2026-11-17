package com.puresudoku.core.board

import com.puresudoku.core.board.Cyvasse.Companion.CATAPULT
import com.puresudoku.core.board.Cyvasse.Companion.CROSSBOW
import com.puresudoku.core.board.Cyvasse.Companion.DRAGON
import com.puresudoku.core.board.Cyvasse.Companion.HEAVY_HORSE
import com.puresudoku.core.board.Cyvasse.Companion.KING
import com.puresudoku.core.board.Cyvasse.Companion.K_MOVE
import com.puresudoku.core.board.Cyvasse.Companion.K_PROMO
import com.puresudoku.core.board.Cyvasse.Companion.K_RANGED
import com.puresudoku.core.board.Cyvasse.Companion.LIGHT_HORSE
import com.puresudoku.core.board.Cyvasse.Companion.NEIGHBOURS
import com.puresudoku.core.board.Cyvasse.Companion.RABBLE
import com.puresudoku.core.board.Cyvasse.Companion.RIVER
import com.puresudoku.core.board.Cyvasse.Companion.SPEARMAN
import com.puresudoku.core.board.Cyvasse.Companion.TREBUCHET
import com.puresudoku.core.board.Cyvasse.Companion.aOf
import com.puresudoku.core.board.Cyvasse.Companion.bOf
import com.puresudoku.core.board.Cyvasse.Companion.kindOf
import com.puresudoku.core.board.Cyvasse.Companion.ownerOf
import com.puresudoku.core.board.Cyvasse.Companion.row
import com.puresudoku.core.board.Cyvasse.Companion.typeOf

/**
 * The named plans of Cyvasse, recognised in any move. They're checked in order, so a move that fits several is
 * named for the first. [level] is the training level (0 = Easy) that teaches it; [found] is what self-play
 * training showed about it.
 */
enum class CyvasseStrategy(val title: String, val level: Int, val idea: String, val found: String = "") {
    CROWN("Maren's Crown", 0,
        "March a rabble across the river to the enemy's back row and crown it: it becomes any piece but a king. A rabble two rows from the end is a threat the rival must answer.",
        "In 40 self-play games, winners crowned or nearly crowned a rabble 1.8 times a game; losers 0.5. Of all the plans, this one most often marked the winner."),
    FORD("Corwin at the Ford", 0,
        "Fight for the river. Whoever holds the crossing decides where the battle is fought: take enemies as they wade in, since water holds a piece for a turn.",
        "In self-play both sides fought at the river about seven times a game; winners slightly more (54%)."),
    VOLLEY("Lady Ysolde's Bolts", 1,
        "Post crossbowmen where enemies must pass, and shoot the one that steps diagonally beside them. A forest hides your crossbowman from siege engines and dragons.",
        "Rare in self-play (once every five games) and even between winners and losers: a crossbowman's shot is a chance to take, not a plan to build on."),
    SPEAR("Tarn Spearbreaker", 1,
        "Spearmen strike two tiles away in any direction. Lead with them and break the enemy line where it is thinnest.",
        "In self-play winners led with spearmen 2.2 times a game, losers 1.7 (57%)."),
    SIEGE("Old Hollis's Siege", 2,
        "Catapults and trebuchets reach five tiles, only to the first piece in line, and must reload after each shot. Clear a lane, fire, and keep the engine out of the enemy's reach while it reloads.",
        "In self-play winners fired siege engines 4.5 times a game, losers 3.1 (59%): long-range fire wears the rival down."),
    CHARGE("Rowan Ashmane's Charge", 2,
        "Horses ride the diagonals. Send them deep: take by landing, or ride in beside the enemy king to tie down its defenders.",
        "In self-play both sides charged with horses about 1.7 times a game: an even trade unless the charge also takes something."),
    SHIELD("The Ember Knight", 3,
        "When your king is in check, answer with another piece: block the line or take the attacker, and keep the king where it stands.",
        "Even in self-play (0.6 a game for both): it saves the game, but doesn't win it on its own."),
    PEACE("King Osric's Peace", 3,
        "Strengthen before you strike: bring pieces in beside your king, so the rival's attack finds nothing loose.",
        "A warning from self-play: losers sheltered their king 11.2 times a game, winners 8.4. Shelter when you must, then get back to the attack."),
    RAID("Seren the Young Flame", 4,
        "Fly your dragon into the enemy's half and take what it finds. Keep its lines open: pieces stop it, mountains don't.",
        "In self-play winners made dragon raids 1.0 times a game, losers 0.8 (56%)."),
    THORN("Ilsa's Thorn", 5,
        "Strike at the king. Every check forces an answer and gains you a move; checks that also take a piece or close the king's escape lead to mate.",
        "In self-play winners gave check 3.1 times a game, losers 1.2: the second strongest sign of the winner.");

    companion object {
        /** The plan [m] carries out in [g] (before it's played), or null for an ordinary move. */
        fun of(g: Cyvasse, m: Int): CyvasseStrategy? {
            val kind = kindOf(m)
            if (kind != K_MOVE && kind != K_RANGED && kind != K_PROMO) return null
            val a = aOf(m); val b = bOf(m)
            val mover = g.board[a]; if (mover == 0) return null
            val side = ownerOf(mover); val type = typeOf(mover)
            val victim = g.board[b].takeIf { it != 0 && ownerOf(it) != side }
            val after = g.copy(); after.play(m, check = false, settle = false)
            val wasInCheck = g.inCheck(side)
            return when {
                kind == K_PROMO || (type == RABBLE && g.progress(side, b) >= Cyvasse.H - 3) -> CROWN
                after.phase == Cyvasse.OVER && after.winner == side || after.inCheck(1 - side) -> THORN
                wasInCheck && type != KING -> SHIELD
                type == DRAGON && victim != null && Cyvasse.inHalf(1 - side, b) -> RAID
                type == CROSSBOW && kind == K_RANGED -> VOLLEY
                (type == CATAPULT || type == TREBUCHET) && kind == K_RANGED -> SIEGE
                type == SPEARMAN && victim != null -> SPEAR
                (type == LIGHT_HORSE || type == HEAVY_HORSE) && (victim != null || nearKing(g, 1 - side, b)) -> CHARGE
                row(b) == RIVER && (victim != null || type != RABBLE) -> FORD
                victim == null && (type != KING && nearKing(g, side, b) && !nearKing(g, side, a)) -> PEACE
                else -> null
            }
        }

        private fun nearKing(g: Cyvasse, kingSide: Int, c: Int): Boolean {
            val k = (0 until Cyvasse.CELLS).firstOrNull { g.board[it] == Cyvasse.piece(kingSide, KING) } ?: return false
            return c in NEIGHBOURS[k]
        }

        fun forLevel(level: Int) = entries.filter { it.level == level }
    }
}
