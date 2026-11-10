package com.puresudoku.core.board

import com.puresudoku.core.GameKind

/**
 * One step of a scripted Easy lesson.
 * [from]/[to] are the move the player must make (game-specific squares; -1 = any),
 * [reply] is the opponent's fixed answer (game-specific code; null = let the gentle computer answer),
 * [info] steps just need a tap on Next. Backgammon steps can fix the dice.
 */
class Step(
    val text: String, val from: Int = -1, val to: Int = -1, val reply: Int? = null, val info: Boolean = false,
    val dice: IntArray? = null, val cpuDice: IntArray? = null,
)

/** Fully scripted first lessons for the board games (Easy level). */
object BoardScripts {
    private fun sq(f: Char, r: Int) = (8 - r) * 8 + (f - 'a')
    private fun cm(a: Int, b: Int) = a * 100 + b

    val chess = listOf(
        Step("You play White, at the bottom. The front row is your pawns. Behind them, from the corners in: rooks, knights, bishops, then the queen (on d1) and the king (on e1).", info = true),
        Step("Pawns move straight forward one square, or two on their first move. Tap the ringed pawn on e2, then e4. It frees your bishop and queen.", sq('e', 2), sq('e', 4), cm(sq('e', 7), sq('e', 5))),
        Step("Black copied you. Bishops slide diagonally as far as they like. Move your bishop from f1 to c4: it now aims at f7, the weak square next to Black's king.", sq('f', 1), sq('c', 4), cm(sq('b', 8), sq('c', 6))),
        Step("Black brought out a knight (knights jump in an L). The queen moves like a rook and a bishop combined. Bring her from d1 to h5: queen and bishop now both attack f7.", sq('d', 1), sq('h', 5), cm(sq('g', 8), sq('f', 6))),
        Step("Black attacked your queen with a knight but forgot about f7! Capture on f7 with the queen. The king is attacked, can't take back (your bishop protects f7) and has nowhere to go: checkmate.", sq('h', 5), sq('f', 7)),
    )

    val xiangqi = listOf(
        Step("You play Red, at the bottom. Your General 帥 and two Advisors 仕 stay inside the palace (the box with the X). Elephants 相 guard your side of the river; they can never cross it.", info = true),
        Step("Cannons 炮 move like chariots, straight lines. Move your left cannon to the centre file: tap it, then the middle point of its row. The 'central cannon' is the classic opening.", 7 * 9 + 1, 7 * 9 + 4, cm(1, 2 * 9 + 2)),
        Step("Black developed a horse. Horses 傌 step one point straight, then one diagonally outward, but are blocked if that first point is occupied. Move your right horse up and in.", 9 * 9 + 7, 7 * 9 + 6, cm(0, 9)),
        Step("Black's chariot stepped out. Chariots 俥 are your strongest piece: any distance in a straight line. Lift your right chariot one point.", 9 * 9 + 8, 8 * 9 + 8, cm(2 * 9 + 7, 2 * 9 + 4)),
        Step("Black lined up a central cannon too. Cannons capture by jumping over exactly one piece (the screen). Your soldier is a screen: jump it and capture the black soldier on the centre file.", 7 * 9 + 4, 3 * 9 + 4, cm(2 * 9 + 2, 3 * 9 + 4)),
        Step("Black's horse took your cannon back: a cannon for a soldier is a bad trade for you, so always check what can recapture! The game is now yours to finish against a gentle opponent.", info = true),
    )

    val shogi = listOf(
        Step("You are Sente, at the bottom, and your pieces point up. Back row from the edges in: Lance 香, Knight 桂, Silver 銀, Gold 金, and the King 王 in the middle. The Bishop 角 is on the left, the Rook 飛 on the right, pawns 歩 in front.", info = true),
        Step("Open your bishop's diagonal: move the ringed pawn one step forward (pawns move one step straight).", 6 * 9 + 2, 5 * 9 + 2, cm(2 * 9 + 6, 3 * 9 + 6)),
        Step("Black opened its bishop too: the two bishops now face each other along the diagonal. Capture Black's bishop with yours and promote it to a Horse 馬 when asked.", 7 * 9 + 1, 1 * 9 + 7, cm(0 * 9 + 6, 1 * 9 + 7)),
        Step("Black's silver captured your horse. Captured pieces change sides: you both now have a bishop in your hand (shown below the board). Tap the bishop in your hand, then drop it on the ringed square in the centre, where it eyes both sides of the board.", Shogi.DROP + Shogi.BISHOP, 4 * 9 + 4),
        Step("Drops are shogi's big idea: a captured piece can return anywhere empty. From here, play on against a gentle opponent. Keep your king guarded by golds and silvers.", info = true),
    )

    val checkers = listOf(
        Step("You play Red, moving up. Pieces move one square diagonally forward onto dark squares. Move the ringed piece forward.", 5 * 8 + 2, 4 * 8 + 3, cm(2 * 8 + 5, 3 * 8 + 4)),
        Step("Black stepped next to you with an empty square behind it. Jump it! Capturing is compulsory: tap your piece, then the square beyond the black piece.", 4 * 8 + 3, 2 * 8 + 5, cm(1 * 8 + 6, 3 * 8 + 4)),
        Step("Black had to jump back: a one-for-one trade. Keep your pieces side by side so they protect each other. Move the ringed piece up to support the centre.", 5 * 8 + 4, 4 * 8 + 5),
        Step("Reach the far row to crown a king, which can move backwards too. Play on from here.", info = true),
    )

    private fun mm(from: Int, to: Int, remove: Int = -1) = ((from + 1) * 100 + to) * 100 + (remove + 1)
    val morris = listOf(
        Step("First everyone places nine men, one at a time, on the dots. Points in the middle of each side have four neighbours: place a man on the ringed point.", -1, 4, mm(-1, 10)),
        Step("Three in a row along a line is a mill. Place your second man on the ringed point: two in a row on the line going down.", -1, 1, mm(-1, 21)),
        Step("Black didn't block! Place your third man on the ringed point to complete the mill, then tap one of Black's men to take it.", -1, 7, mm(-1, 13)),
        Step("Mills take men: leave Black with two men, or no moves, and you win. Watch for Black's two-in-a-rows and block them. Play on.", info = true),
    )

    val ticTacToe = listOf(
        Step("You are X and go first. The centre is on four lines, more than any other square: take it.", -1, 4, 1),
        Step("O took an edge, a weak square. Take a corner: now you threaten the diagonal.", -1, 0, 8),
        Step("O blocked. Take the bottom-left corner: you now threaten two lines at once (a fork). O can only block one.", -1, 6, 3),
        Step("O blocked one line. Finish the other diagonal and win the round!", -1, 2),
    )

    val gomoku = listOf(
        Step("You play Black. Stones go on the line crossings: tap once to preview, again to place. Start in the centre.", -1, 112, 0),
        Step("Build a line: place next to your stone.", -1, 113, 14),
        Step("Three in a row with both ends open is an 'open three': a real threat.", -1, 114, 210),
        Step("White ignored it! Make an open four: four in a row with both ends empty. White can only block one end.", -1, 111, 115),
        Step("White blocked one end. Play the other end for five in a row!", -1, 110),
    )

    val go = listOf(
        Step("You play Black. Stones go on the crossings and never move. Start in the centre: tap the ringed point twice.", -1, 40, 22),
        Step("White played above. A stone's empty neighbouring points are its liberties; the white stone has four. Take one away: play to its left.", -1, 21, 60),
        Step("Take away another liberty: play to its right.", -1, 23, 70),
        Step("Now play above it. The white stone has one liberty left: that's 'atari'.", -1, 13, 50),
        Step("White didn't save it. Fill the last liberty, below the stone, to capture it!", -1, 31),
        Step("Captured stones come off the board. Surround territory and capture stones; when neither side can gain, both pass and the board is counted. Play on.", info = true),
    )

    val mancala = listOf(
        Step("Your pits are the bottom row and your store is the big pit on the right. Count the seeds: the ringed pit's 4 seeds reach exactly your store. Tap it.", -1, 2),
        Step("Your last seed landed in your store, so you go again! Now sow the ringed pit.", -1, 5),
        Step("If your last seed lands in an empty pit on your side, you capture it and the seeds opposite. Watch for that, and for chances to land in your store. Play on.", info = true),
    )

    /** For backgammon, from/to are point indices (0 = your 1-point) and dice fix your roll. */
    val backgammon = listOf(
        Step("Your white checkers race around to your home (bottom right) and bear off. Tap Roll: you'll get a 3 and a 1.", info = true),
        Step("A 3-1 makes your 5-point: two checkers on a point are safe. Move 8 → 5 with the 3.", 7, 4, dice = intArrayOf(3, 1)),
        Step("Now 6 → 5 with the 1. The point is made!", 5, 4, cpuDice = intArrayOf(2, 1)),
        Step("The computer moved. Roll again: double sixes! Doubles move four times.", info = true),
        Step("Run a back checker: 24 → 18.", 23, 17, dice = intArrayOf(6, 6)),
        Step("And the other: 24 → 18. They now form a safe point.", 23, 17),
        Step("Bring one down from the midpoint: 13 → 7.", 12, 6),
        Step("And another 13 → 7: your bar point is made.", 12, 6),
        Step("Avoid leaving single checkers (blots) where they can be hit. Play on.", info = true),
    )

    fun of(kind: GameKind): List<Step>? = when (kind) {
        GameKind.CHESS -> chess
        GameKind.XIANGQI -> xiangqi
        GameKind.SHOGI -> shogi
        GameKind.CHECKERS -> checkers
        GameKind.MORRIS -> morris
        GameKind.TIC_TAC_TOE -> ticTacToe
        GameKind.GOMOKU -> gomoku
        GameKind.GO -> go
        GameKind.MANCALA -> mancala
        GameKind.BACKGAMMON -> backgammon
        else -> null
    }
}
