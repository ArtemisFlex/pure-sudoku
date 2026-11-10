package com.puresudoku.core.board

import kotlin.random.Random

/** A two-player, perfect-information game that the shared alpha-beta search can play. */
interface Searchable<M> {
    /** Legal moves for the side to move. */
    fun legalMoves(): List<M>
    fun make(m: M)
    fun unmake()
    /** Static evaluation from the side to move's point of view. */
    fun evaluate(): Int
    /** Null while the game goes on, otherwise the result for the side to move (e.g. -WIN when it has lost). */
    fun terminal(): Int?
    /** Bigger = search first (captures, threats). */
    fun orderKey(m: M): Int = 0
    /** Moves worth searching past the horizon (captures); empty = no quiescence. */
    fun noisyMoves(): List<M> = emptyList()
}

const val WIN = 1_000_000

/** How strongly the computer plays at one level. */
data class AiLevel(
    val depth: Int,
    val timeMs: Long,
    /** Random spread added to root move scores (weaker = bigger). */
    val noise: Int = 0,
    /** Chance of simply playing a random legal move. */
    val blunder: Double = 0.0,
    val quiescence: Boolean = false,
)

/** Iterative-deepening negamax with alpha-beta, a node budget and root noise for weaker levels. */
class AlphaBeta<M>(private val g: Searchable<M>, private val level: AiLevel, private val rng: Random) {
    private var deadline = 0L
    private var aborted = false
    private var nodes = 0L

    fun best(): M? {
        val root = g.legalMoves()
        if (root.isEmpty()) return null
        if (root.size == 1) return root[0]
        if (level.blunder > 0 && rng.nextDouble() < level.blunder) return root[rng.nextInt(root.size)]
        deadline = System.currentTimeMillis() + level.timeMs
        var order = root.sortedByDescending { g.orderKey(it) }
        var bestMove = order[0]
        for (d in 1..level.depth) {
            aborted = false
            val scored = ArrayList<Pair<M, Int>>(order.size)
            var alpha = -WIN * 2
            for (m in order) {
                g.make(m)
                val v = -negamax(d - 1, -WIN * 2, -alpha + level.noise + 1, 1)
                g.unmake()
                if (aborted) break
                scored += m to v
                if (v > alpha) alpha = v
            }
            if (aborted && scored.isEmpty()) break
            // weaker levels: jitter the root scores so they don't always find the best move
            val pick = scored.maxBy { it.second + if (level.noise > 0) rng.nextInt(-level.noise, level.noise + 1) else 0 }
            bestMove = pick.first
            if (aborted) break
            if (scored.maxOf { it.second } >= WIN - 1000) break
            order = scored.sortedByDescending { it.second }.map { it.first } + order.filter { o -> scored.none { it.first == o } }
            if (System.currentTimeMillis() > deadline) break
        }
        return bestMove
    }

    private fun negamax(depth: Int, a0: Int, beta: Int, ply: Int): Int {
        if ((++nodes and 1023L) == 0L && System.currentTimeMillis() > deadline) aborted = true
        if (aborted) return 0
        g.terminal()?.let { return if (it <= -WIN + 1000 || it >= WIN - 1000) (if (it < 0) it + ply else it - ply) else it }
        if (depth <= 0) return if (level.quiescence) quiesce(a0, beta, 0) else g.evaluate()
        var alpha = a0
        val moves = g.legalMoves().sortedByDescending { g.orderKey(it) }
        if (moves.isEmpty()) return g.evaluate()
        var best = -WIN * 2
        for (m in moves) {
            g.make(m)
            val v = -negamax(depth - 1, -beta, -alpha, ply + 1)
            g.unmake()
            if (aborted) return 0
            if (v > best) best = v
            if (v > alpha) alpha = v
            if (alpha >= beta) break
        }
        return best
    }

    private fun quiesce(a0: Int, beta: Int, qd: Int): Int {
        if ((++nodes and 1023L) == 0L && System.currentTimeMillis() > deadline) aborted = true
        if (aborted) return 0
        g.terminal()?.let { return it }
        val stand = g.evaluate()
        if (stand >= beta || qd > 6) return stand
        var alpha = maxOf(a0, stand)
        for (m in g.noisyMoves().sortedByDescending { g.orderKey(it) }) {
            g.make(m)
            val v = -quiesce(-beta, -alpha, qd + 1)
            g.unmake()
            if (aborted) return 0
            if (v >= beta) return v
            if (v > alpha) alpha = v
        }
        return alpha
    }
}
