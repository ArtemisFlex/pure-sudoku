package com.puresudoku.core.board

import com.puresudoku.core.board.Cyvasse.Companion.KING
import com.puresudoku.core.board.Cyvasse.Companion.K_PROMO
import com.puresudoku.core.board.Cyvasse.Companion.NEIGHBOURS
import com.puresudoku.core.board.Cyvasse.Companion.OVER
import com.puresudoku.core.board.Cyvasse.Companion.aOf
import com.puresudoku.core.board.Cyvasse.Companion.bOf
import com.puresudoku.core.board.Cyvasse.Companion.kindOf
import com.puresudoku.core.board.Cyvasse.Companion.ownerOf
import com.puresudoku.core.board.Cyvasse.Companion.typeOf
import kotlin.random.Random

/**
 * How the computer judges a Cyvasse position: a weighted sum of features, each counted for both sides and
 * subtracted. The weights were tuned by self-play (see CyvasseTraining in the tests).
 */
object CyvasseEval {
    // feature indices: 0..8 hold the piece counts, rabble to dragon
    const val MOBILITY = 9
    const val THREAT = 10
    const val PRESSURE = 11
    const val SHELTER = 12
    const val ADVANCE = 13
    const val CROSSED = 14
    const val FOREST = 15
    const val FORTRESS = 16
    const val KING_FORT = 17
    const val TAKEN = 18
    const val EXPOSED = 19
    const val COUNT = 20

    val NAMES = listOf("rabble", "spearman", "crossbowman", "light horse", "heavy horse", "elephant", "catapult", "trebuchet", "dragon",
        "mobility", "threats", "king pressure", "king shelter", "rabble advance", "units across", "forest cover", "fortress", "king in fortress", "pieces taken", "open lines to king")

    /** The hand-set starting point for training. */
    val HAND = intArrayOf(100, 220, 260, 280, 340, 400, 440, 460, 900, 3, 2, 8, 12, 15, 10, 10, 40, 30, 5, -15)

    /** The trained weights. */
    val TRAINED = intArrayOf(81, 111, 280, 372, 118, 319, 670, 418, 996, 0, 5, 6, 15, 20, 5, 4, 41, 41, 9, -20)

    /** Fills [f] with [s]'s features; [moves] receives [s]'s pseudo-legal moves. */
    fun features(g: Cyvasse, s: Int, f: IntArray, moves: IntList) {
        f.fill(0)
        val e = 1 - s
        var myKing = -1; var theirKing = -1
        for (c in 0 until Cyvasse.CELLS) {
            val p = g.board[c]; if (p == 0) continue
            val t = typeOf(p)
            if (t == KING) { if (ownerOf(p) == s) myKing = c else theirKing = c; continue }
            if (ownerOf(p) != s) continue
            f[t - 1]++
            if (t == Cyvasse.RABBLE) f[ADVANCE] += maxOf(0, g.progress(s, c) - Cyvasse.RIVER)
            else if (Cyvasse.inHalf(e, c)) f[CROSSED]++
            if (g.terrain[c] == Cyvasse.FOREST && t != Cyvasse.CATAPULT && t != Cyvasse.TREBUCHET) f[FOREST]++
        }
        for (c in 0 until Cyvasse.CELLS) if (ownerOf(g.board[c]) == s && g.waterWait[c] == 0) g.pieceMoves(c, moves)
        f[MOBILITY] = moves.size
        val seen = BooleanArray(Cyvasse.CELLS)
        for (i in 0 until moves.size) {
            val m = moves[i]; val b = bOf(m)
            val v = g.board[b]
            if (v != 0 && ownerOf(v) == e && !seen[b]) { seen[b] = true; f[THREAT] += if (typeOf(v) == KING) 30 else Cyvasse.VALUE[typeOf(v)] / 10 }
            if (theirKing >= 0 && (b == theirKing || b in NEIGHBOURS[theirKing])) f[PRESSURE]++
        }
        if (myKing >= 0) {
            f[SHELTER] = NEIGHBOURS[myKing].count { ownerOf(g.board[it]) == s }
            if (g.fortAt(myKing) >= 0) f[KING_FORT] = 1
            f[EXPOSED] = g.openLines(s)
        }
        if (g.keepStands(s)) f[FORTRESS] = 1
        f[TAKEN] = g.takenBy(s)
    }

    /** The position's worth to [s] under weights [w]. */
    fun score(g: Cyvasse, s: Int, w: IntArray): Int {
        val a = IntArray(COUNT); val b = IntArray(COUNT)
        features(g, s, a, IntList(96)); features(g, 1 - s, b, IntList(96))
        var v = 0
        for (i in 0 until COUNT) v += w[i] * (a[i] - b[i])
        return v
    }

    fun pieceValue(type: Int, w: IntArray) = if (type == KING) 5000 else if (type in 1..9) w[type - 1] else 0
}

/** The shared alpha-beta search playing Cyvasse: legal moves at the root, pseudo-legal below (taking a king ends it). */
class CyvasseSearch(root: Cyvasse, private val w: IntArray) : Searchable<Int> {
    private var g = root.copy()
    private val stack = ArrayList<Cyvasse>()
    private var side = root.turn

    override fun legalMoves(): List<Int> = (if (stack.isEmpty()) g.legalMoves() else g.pseudoMoves(g.turn)).toList()
    override fun make(m: Int) { stack.add(g); g = g.copy().also { it.play(m, check = false, settle = false) }; side = 1 - side }
    override fun unmake() { g = stack.removeAt(stack.size - 1); side = 1 - side }
    override fun evaluate(): Int = CyvasseEval.score(g, side, w)
    override fun terminal(): Int? = if (g.phase != OVER) null else when (g.winner) { Cyvasse.DRAWN -> 0; side -> WIN; else -> -WIN }
    override fun orderKey(m: Int): Int {
        val v = g.board[bOf(m)]
        val promo = if (kindOf(m) == K_PROMO) 800 else 0
        if (v == 0 || ownerOf(v) == ownerOf(g.board[aOf(m)])) return promo
        return 1000 + 10 * CyvasseEval.pieceValue(typeOf(v), w) / 10 - CyvasseEval.pieceValue(typeOf(g.board[aOf(m)]), w) / 10 + promo
    }
    override fun noisyMoves(): List<Int> = g.pseudoMoves(g.turn).filter { val v = g.board[bOf(it)]; (v != 0 && ownerOf(v) != side) || kindOf(it) == K_PROMO }
    override fun noMoveScore(): Int? {
        if (g.inCheck(side) || g.legalMoves().isNotEmpty()) return null
        return when (g.stalemateWinner()) { side -> WIN / 2; Cyvasse.DRAWN -> 0; else -> -WIN / 2 }
    }
}

/** The computer's lord: the shared search at each level's depth and time, with looser play on the easy levels. */
object CyvasseAi {
    fun level(level: Int) = when (level) {
        // like the chess levels: deeper and steadier each step up
        0 -> AiLevel(1, 400, noise = 250, blunder = 0.15, quiescence = false)
        1 -> AiLevel(2, 700, noise = 100, blunder = 0.04, quiescence = true)
        2 -> AiLevel(3, 1200, noise = 25, quiescence = true)
        3 -> AiLevel(4, 2000, noise = 6, quiescence = true)
        4 -> AiLevel(5, 3500, quiescence = true)
        else -> AiLevel(6, 5000, quiescence = true)
    }
    fun depthFor(level: Int) = level(level).depth

    fun setup(g: Cyvasse, side: Int, level: Int, rnd: Random) = g.randomSetup(side, rnd, level)

    /** The move to play for the side to move. */
    fun best(g: Cyvasse, level: Int, rnd: Random, weights: IntArray = CyvasseEval.TRAINED): Int = best(g, level(level), rnd, weights)

    fun best(g: Cyvasse, ai: AiLevel, rnd: Random, weights: IntArray = CyvasseEval.TRAINED): Int {
        val moves = g.legalMoves()
        if (moves.isEmpty()) return Cyvasse.PASS
        if (moves.size == 1) return moves[0]
        return AlphaBeta(CyvasseSearch(g, weights), ai, rnd).best() ?: moves[0]
    }
}
