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
    /** The worth (a tenth of each piece's value) of this side's pieces an enemy catapult or trebuchet can hit right now. */
    const val SHOT_AT = 20
    /** How many enemy catapults and trebuchets this side can attack. */
    const val SIEGE_HUNT = 21
    const val COUNT = 22

    val NAMES = listOf("rabble", "spearman", "crossbowman", "light horse", "heavy horse", "elephant", "catapult", "trebuchet", "dragon",
        "mobility", "threats", "king pressure", "king shelter", "rabble advance", "units across", "forest cover", "fortress", "king in fortress", "pieces taken", "open lines to king", "under siege fire", "siege engines threatened")

    /** The hand-set starting point for training. */
    val HAND = intArrayOf(100, 220, 260, 280, 340, 400, 440, 460, 900, 3, 2, 8, 12, 15, 10, 10, 40, 30, 5, -15, -8, 40)

    /**
     * The trained weights: retrained by self-play on all three fields after the siege changes (57% against the previous
     * weights over 150 games), then with the siege-lane weights tripled and doubled. That made no difference between
     * computers (51% over 150 games) but cut how much a sniping trebuchet takes from the Easy computer.
     */
    val TRAINED = intArrayOf(82, 129, 258, 338, 121, 323, 668, 437, 1010, 0, 5, 5, 15, 18, 4, 3, 41, 41, 8, -19, -21, 84)

    /** The weights for an open-field game (until trained separately, the standard ones). */
    val TRAINED_OPEN = TRAINED.copyOf()

    fun weightsFor(g: Cyvasse) = if (g.openField) TRAINED_OPEN else TRAINED

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
        val ma = IntList(96); val mb = IntList(96)
        features(g, s, a, ma); features(g, 1 - s, b, mb)
        siege(g, ma, a, b); siege(g, mb, b, a)
        var v = 0
        for (i in 0 until COUNT) v += w[i] * (a[i] - b[i])
        return v
    }

    /**
     * Siege features from one side's [moves]: the other side's pieces under its siege fire ([them]'s SHOT_AT), and the
     * other side's siege engines it can attack ([us]'s SIEGE_HUNT).
     */
    private fun siege(g: Cyvasse, moves: IntList, us: IntArray, them: IntArray) {
        val shot = BooleanArray(Cyvasse.CELLS); val hunted = BooleanArray(Cyvasse.CELLS)
        for (i in 0 until moves.size) {
            val m = moves[i]; val b = bOf(m); val v = g.board[b]
            if (v == 0 || ownerOf(v) == ownerOf(g.board[aOf(m)])) continue
            val attacker = typeOf(g.board[aOf(m)]); val target = typeOf(v)
            if (kindOf(m) == Cyvasse.K_RANGED && (attacker == Cyvasse.CATAPULT || attacker == Cyvasse.TREBUCHET) && !shot[b]) {
                shot[b] = true; them[SHOT_AT] += if (target == KING) 50 else Cyvasse.VALUE[target] / 10
            }
            if ((target == Cyvasse.CATAPULT || target == Cyvasse.TREBUCHET) && !hunted[b]) { hunted[b] = true; us[SIEGE_HUNT]++ }
        }
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

    /** Sets the computer's terrain and fortress: learned preferences in an open-field game, sharper on higher levels. */
    fun setup(g: Cyvasse, side: Int, level: Int, rnd: Random) {
        if (!g.openField) { g.randomSetup(side, rnd, level); return }
        // Easy and Medium place at random; from Hard up, one of the layouts that held up in self-play
        if (level <= 1) CyvassePlacement.place(g, side, CyvassePlacement.FLAT, 1.0, rnd)
        else CyvassePlacement.placeLayout(g, side, CyvassePlacement.PROVEN.random(rnd), mirror = rnd.nextBoolean())
    }

    /** The move to play for the side to move. */
    fun best(g: Cyvasse, level: Int, rnd: Random, weights: IntArray = CyvasseEval.weightsFor(g)): Int = best(g, level(level), rnd, weights)

    fun best(g: Cyvasse, ai: AiLevel, rnd: Random, weights: IntArray = CyvasseEval.TRAINED): Int {
        val moves = g.legalMoves()
        if (moves.isEmpty()) return Cyvasse.PASS
        if (moves.size == 1) return moves[0]
        return AlphaBeta(CyvasseSearch(g, weights), ai, rnd).best() ?: moves[0]
    }
}

/**
 * Where to set terrain in an open-field game: random (weighted by a preference per tile of a side's three free rows,
 * seen from Alabaster's side; Onyx's mirror across the river) or one of the [PROVEN] layouts.
 */
object CyvassePlacement {
    const val KEEP = 0; const val MOUNTAIN = 1; const val FOREST = 2
    /** Alabaster's free rows, nearest the river first. */
    val ROWS = (Cyvasse.RIVER + 1)..(Cyvasse.RIVER + 3)
    val TILES: IntArray = ROWS.flatMap { r -> (0 until Cyvasse.W).map { r * Cyvasse.W + it } }.toIntArray()

    /** Every tile equally likely. */
    val FLAT: Array<DoubleArray> = Array(3) { DoubleArray(TILES.size) }

    /**
     * Whole layouts (fortress, 3 mountains, 3 forests; slots from the river: 0-9, 10-19, 20-29) that did best in a
     * self-play tournament: 24 layouts played 80 games each against random layouts, and the best were replayed over 240
     * fresh games. Only the first held up clearly (57% then 56% over 320 games): a forward fortress by the river with
     * a knot of mountains and forests round it on the left. The others came out at 52-53%: in open-field play,
     * placement matters much less than how the pieces are used.
     */
    val PROVEN = listOf(
        intArrayOf(3, 13, 1, 23, 14, 2, 4),
        intArrayOf(25, 10, 19, 15, 3, 5, 7),
        intArrayOf(21, 0, 2, 5, 10, 24, 13),
    )

    /** Sets [side]'s terrain to [layout], optionally mirrored left to right. */
    fun placeLayout(g: Cyvasse, side: Int, layout: IntArray, mirror: Boolean) {
        fun m(i: Int) = if (!mirror) i else (i / Cyvasse.W) * Cyvasse.W + (Cyvasse.W - 1 - i % Cyvasse.W)
        val old = g.placer; g.placer = side; g.clearSetup(side)
        for (k in 1..3) g.toggleMountain(side, tile(m(layout[k]), side))
        for (k in 4..6) g.toggleTerrain(side, Cyvasse.FOREST, tile(m(layout[k]), side))
        g.placer = old
        // should a layout not fit (it always does on a fresh field), fall back to a random one
        if (!g.setupDone(side)) place(g, side, FLAT, 1.0, kotlin.random.Random(layout.sum()))
    }

    /** [side]'s tile for preference slot [i]. */
    fun tile(i: Int, side: Int): Int { val c = TILES[i]; return if (side == 0) c else (Cyvasse.H - 1 - Cyvasse.row(c)) * Cyvasse.W + Cyvasse.col(c) }

    /** Picks a free slot for [item], weighted by preference (softmax at [temperature]). */
    private fun pick(g: Cyvasse, side: Int, prefs: DoubleArray, temperature: Double, rnd: Random): Int? {
        val free = TILES.indices.filter { val c = tile(it, side); g.board[c] == 0 && g.block[c] == 0 && g.terrain[c] == Cyvasse.PLAIN }
        if (free.isEmpty()) return null
        val top = free.maxOf { prefs[it] }
        val w = free.map { kotlin.math.exp((prefs[it] - top) / temperature) }
        var x = rnd.nextDouble() * w.sum()
        for ((k, i) in free.withIndex()) { x -= w[k]; if (x <= 0) return i }
        return free.last()
    }

    /** Sets [side]'s fortress, mountains and forests; returns the chosen slots (fortress first). */
    fun place(g: Cyvasse, side: Int, prefs: Array<DoubleArray>, temperature: Double, rnd: Random): IntArray {
        val old = g.placer; g.placer = side; g.clearSetup(side)
        // slot 0 once held a fortress; there are none now, so it stays empty
        val chosen = arrayListOf(-1)
        repeat(Cyvasse.MOUNTAINS) { pick(g, side, prefs[MOUNTAIN], temperature, rnd)?.let { g.toggleMountain(side, tile(it, side)); chosen += it } }
        repeat(Cyvasse.FORESTS_PER_SIDE) { pick(g, side, prefs[FOREST], temperature, rnd)?.let { g.toggleTerrain(side, Cyvasse.FOREST, tile(it, side)); chosen += it } }
        g.placer = old
        return chosen.toIntArray()
    }
}
