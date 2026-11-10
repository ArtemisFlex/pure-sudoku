package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.math.abs
import kotlin.random.Random

/**
 * Full chess rules: castling, en passant, promotion, check, mate, stalemate, fifty moves,
 * threefold repetition and insufficient material. Board index r*8+c with r = 0 on rank 8.
 * Pieces: +white (you) / -black: 1 pawn, 2 knight, 3 bishop, 4 rook, 5 queen, 6 king.
 */
class Chess : Searchable<Int> {
    val b = IntArray(64)
    var turn = 1
    var castle = 15   // 1 white O-O, 2 white O-O-O, 4 black O-O, 8 black O-O-O
    var ep = -1
    var halfmove = 0
    var key = 0L
    val keys = ArrayList<Long>()
    private val undo = ArrayList<LongArray>()

    companion object {
        const val P = 1; const val N = 2; const val B = 3; const val R = 4; const val Q = 5; const val K = 6
        const val EP = 1; const val CASTLE = 2; const val DOUBLE = 4

        fun mv(from: Int, to: Int, promo: Int = 0, flag: Int = 0) = from or (to shl 6) or (promo shl 12) or (flag shl 15)
        fun src(m: Int) = m and 63
        fun dst(m: Int) = (m shr 6) and 63
        fun promo(m: Int) = (m shr 12) and 7
        fun flag(m: Int) = m shr 15

        private val zr = Random(20261110)
        private val ZP = Array(13) { LongArray(64) { zr.nextLong() } }
        private val ZTURN = zr.nextLong()
        private val ZC = LongArray(16) { zr.nextLong() }
        private val ZEP = LongArray(8) { zr.nextLong() }

        private val KN = arrayOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
        private val KG = arrayOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
        private val DIAG = arrayOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        private val ORTH = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        val VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 0)

        // piece-square tables from white's view (row 0 = rank 8)
        private val PST_P = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 50, 50, 50, 50, 50, 50, 50, 50, 10, 10, 20, 30, 30, 20, 10, 10, 5, 5, 10, 25, 25, 10, 5, 5,
            0, 0, 0, 20, 20, 0, 0, 0, 5, -5, -10, 0, 0, -10, -5, 5, 5, 10, 10, -20, -20, 10, 10, 5, 0, 0, 0, 0, 0, 0, 0, 0)
        private val PST_N = intArrayOf(-50, -40, -30, -30, -30, -30, -40, -50, -40, -20, 0, 0, 0, 0, -20, -40, -30, 0, 10, 15, 15, 10, 0, -30, -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30, -30, 5, 10, 15, 15, 10, 5, -30, -40, -20, 0, 5, 5, 0, -20, -40, -50, -40, -30, -30, -30, -30, -40, -50)
        private val PST_B = intArrayOf(-20, -10, -10, -10, -10, -10, -10, -20, -10, 0, 0, 0, 0, 0, 0, -10, -10, 0, 5, 10, 10, 5, 0, -10, -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 10, 10, 10, 10, 0, -10, -10, 10, 10, 10, 10, 10, 10, -10, -10, 5, 0, 0, 0, 0, 5, -10, -20, -10, -10, -10, -10, -10, -10, -20)
        private val PST_R = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 5, 10, 10, 10, 10, 10, 10, 5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, 0, 0, 0, 5, 5, 0, 0, 0)
        private val PST_Q = intArrayOf(-20, -10, -10, -5, -5, -10, -10, -20, -10, 0, 0, 0, 0, 0, 0, -10, -10, 0, 5, 5, 5, 5, 0, -10, -5, 0, 5, 5, 5, 5, 0, -5,
            0, 0, 5, 5, 5, 5, 0, -5, -10, 5, 5, 5, 5, 5, 0, -10, -10, 0, 5, 0, 0, 0, 0, -10, -20, -10, -10, -5, -5, -10, -10, -20)
        private val PST_K = intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30, -20, -30, -30, -40, -40, -30, -30, -20, -10, -20, -20, -20, -20, -20, -20, -10,
            20, 20, 0, 0, 0, 0, 20, 20, 20, 30, 10, 0, 0, 10, 30, 20)
        private val PST_KE = intArrayOf(-50, -40, -30, -20, -20, -30, -40, -50, -30, -20, -10, 0, 0, -10, -20, -30, -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30, -30, -10, 30, 40, 40, 30, -10, -30, -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -30, 0, 0, 0, 0, -30, -30, -50, -30, -30, -30, -30, -30, -30, -50)

        fun level(d: Difficulty) = when (d) {
            Difficulty.EASY -> AiLevel(1, 400, noise = 220, blunder = 0.15, quiescence = false)
            Difficulty.MEDIUM -> AiLevel(2, 600, noise = 90, blunder = 0.05, quiescence = true)
            Difficulty.HARD -> AiLevel(3, 900, noise = 30, quiescence = true)
            Difficulty.EXPERT -> AiLevel(4, 1500, noise = 8, quiescence = true)
            Difficulty.MASTER -> AiLevel(5, 2500, quiescence = true)
            Difficulty.EXTREME -> AiLevel(7, 4000, quiescence = true)
        }

        fun start(): Chess = fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

        fun fromFen(fen: String): Chess {
            val g = Chess()
            val parts = fen.split(" ")
            var r = 0; var c = 0
            for (ch in parts[0]) when {
                ch == '/' -> { r++; c = 0 }
                ch.isDigit() -> c += ch - '0'
                else -> {
                    val t = "pnbrqk".indexOf(ch.lowercaseChar()) + 1
                    g.b[r * 8 + c] = if (ch.isUpperCase()) t else -t; c++
                }
            }
            g.turn = if (parts.getOrElse(1) { "w" } == "w") 1 else -1
            val cs = parts.getOrElse(2) { "-" }
            g.castle = (if ('K' in cs) 1 else 0) or (if ('Q' in cs) 2 else 0) or (if ('k' in cs) 4 else 0) or (if ('q' in cs) 8 else 0)
            val e = parts.getOrElse(3) { "-" }
            g.ep = if (e == "-") -1 else (8 - (e[1] - '0')) * 8 + (e[0] - 'a')
            g.halfmove = parts.getOrElse(4) { "0" }.toIntOrNull() ?: 0
            g.key = g.hash(); g.keys += g.key
            return g
        }
    }

    fun hash(): Long {
        var h = 0L
        for (i in 0..63) if (b[i] != 0) h = h xor ZP[b[i] + 6][i]
        if (turn < 0) h = h xor ZTURN
        h = h xor ZC[castle]
        if (ep >= 0) h = h xor ZEP[ep % 8]
        return h
    }

    fun kingSq(color: Int): Int { for (i in 0..63) if (b[i] == 6 * color) return i; return -1 }

    fun attacked(sq: Int, by: Int): Boolean {
        val r = sq / 8; val c = sq % 8
        // pawns: a white pawn attacks upwards (r-1)
        val pr = r + by
        if (pr in 0..7) {
            if (c > 0 && b[pr * 8 + c - 1] == by * P) return true
            if (c < 7 && b[pr * 8 + c + 1] == by * P) return true
        }
        for ((dr, dc) in KN) { val rr = r + dr; val cc = c + dc; if (rr in 0..7 && cc in 0..7 && b[rr * 8 + cc] == by * N) return true }
        for ((dr, dc) in KG) { val rr = r + dr; val cc = c + dc; if (rr in 0..7 && cc in 0..7 && b[rr * 8 + cc] == by * K) return true }
        for ((dr, dc) in DIAG) {
            var rr = r + dr; var cc = c + dc
            while (rr in 0..7 && cc in 0..7) { val p = b[rr * 8 + cc]; if (p != 0) { if (p == by * B || p == by * Q) return true; break }; rr += dr; cc += dc }
        }
        for ((dr, dc) in ORTH) {
            var rr = r + dr; var cc = c + dc
            while (rr in 0..7 && cc in 0..7) { val p = b[rr * 8 + cc]; if (p != 0) { if (p == by * R || p == by * Q) return true; break }; rr += dr; cc += dc }
        }
        return false
    }

    fun inCheck(color: Int = turn) = kingSq(color).let { it >= 0 && attacked(it, -color) }

    private fun pseudo(capturesOnly: Boolean = false): MutableList<Int> {
        val out = ArrayList<Int>(48)
        val me = turn
        for (i in 0..63) {
            val p = b[i]; if (p == 0 || p * me < 0) continue
            val r = i / 8; val c = i % 8
            when (abs(p)) {
                P -> {
                    val dr = -me
                    val nr = r + dr
                    val promoRow = if (me > 0) 0 else 7
                    fun add(to: Int, flag: Int = 0) {
                        if (to / 8 == promoRow) for (pr in intArrayOf(Q, N, R, B)) out += mv(i, to, pr, flag) else out += mv(i, to, 0, flag)
                    }
                    if (nr in 0..7) {
                        if (!capturesOnly && b[nr * 8 + c] == 0) {
                            add(nr * 8 + c)
                            val startRow = if (me > 0) 6 else 1
                            if (r == startRow && b[(r + 2 * dr) * 8 + c] == 0) out += mv(i, (r + 2 * dr) * 8 + c, 0, DOUBLE)
                        } else if (capturesOnly && b[nr * 8 + c] == 0 && nr == promoRow) add(nr * 8 + c)
                        for (dc in intArrayOf(-1, 1)) {
                            val cc = c + dc; if (cc !in 0..7) continue
                            val t = nr * 8 + cc
                            if (b[t] * me < 0) add(t)
                            else if (t == ep) out += mv(i, t, 0, EP)
                        }
                    }
                }
                N -> for ((dr, dc) in KN) { val rr = r + dr; val cc = c + dc; if (rr in 0..7 && cc in 0..7) { val t = rr * 8 + cc; if (b[t] * me < 0 || (!capturesOnly && b[t] == 0)) out += mv(i, t) } }
                K -> {
                    for ((dr, dc) in KG) { val rr = r + dr; val cc = c + dc; if (rr in 0..7 && cc in 0..7) { val t = rr * 8 + cc; if (b[t] * me < 0 || (!capturesOnly && b[t] == 0)) out += mv(i, t) } }
                    if (!capturesOnly) {
                        val home = if (me > 0) 60 else 4
                        if (i == home && !attacked(home, -me)) {
                            val ks = if (me > 0) 1 else 4; val qs = if (me > 0) 2 else 8
                            if (castle and ks != 0 && b[home + 1] == 0 && b[home + 2] == 0 && b[home + 3] == me * R &&
                                !attacked(home + 1, -me) && !attacked(home + 2, -me)) out += mv(i, home + 2, 0, CASTLE)
                            if (castle and qs != 0 && b[home - 1] == 0 && b[home - 2] == 0 && b[home - 3] == 0 && b[home - 4] == me * R &&
                                !attacked(home - 1, -me) && !attacked(home - 2, -me)) out += mv(i, home - 2, 0, CASTLE)
                        }
                    }
                }
                else -> {
                    val dirs = when (abs(p)) { B -> DIAG; R -> ORTH; else -> DIAG + ORTH }
                    for ((dr, dc) in dirs) {
                        var rr = r + dr; var cc = c + dc
                        while (rr in 0..7 && cc in 0..7) {
                            val t = rr * 8 + cc
                            if (b[t] == 0) { if (!capturesOnly) out += mv(i, t) }
                            else { if (b[t] * me < 0) out += mv(i, t); break }
                            rr += dr; cc += dc
                        }
                    }
                }
            }
        }
        return out
    }

    private fun legalFrom(list: List<Int>): List<Int> {
        val me = turn
        return list.filter { m -> make(m); val ok = !inCheck(me); unmake(); ok }
    }

    override fun legalMoves(): List<Int> = legalFrom(pseudo())
    override fun noisyMoves(): List<Int> = legalFrom(pseudo(capturesOnly = true))

    override fun make(m: Int) {
        val f = src(m); val t = dst(m); val fl = flag(m)
        val p = b[f]
        var captured = b[t]; var capSq = t
        if (fl == EP) { capSq = t + 8 * turn; captured = b[capSq] }
        undo += longArrayOf(m.toLong(), captured.toLong(), capSq.toLong(), castle.toLong(), ep.toLong(), halfmove.toLong(), key)
        b[capSq] = 0
        b[t] = if (promo(m) != 0) promo(m) * turn else p
        b[f] = 0
        if (fl == CASTLE) {
            if (t > f) { b[t - 1] = b[t + 1]; b[t + 1] = 0 } else { b[t + 1] = b[t - 2]; b[t - 2] = 0 }
        }
        // castling rights
        if (abs(p) == K) castle = castle and (if (turn > 0) 3.inv() else 12.inv())
        for (sq in intArrayOf(f, t)) when (sq) {
            63 -> castle = castle and 1.inv(); 56 -> castle = castle and 2.inv()
            7 -> castle = castle and 4.inv(); 0 -> castle = castle and 8.inv()
        }
        ep = if (fl == DOUBLE) (f + t) / 2 else -1
        halfmove = if (abs(p) == P || captured != 0) 0 else halfmove + 1
        turn = -turn
        key = hash()
        keys += key
    }

    override fun unmake() {
        val u = undo.removeAt(undo.lastIndex)
        keys.removeAt(keys.lastIndex)
        turn = -turn
        val m = u[0].toInt(); val f = src(m); val t = dst(m)
        val moved = if (promo(m) != 0) P * turn else b[t]
        b[f] = moved
        b[t] = 0
        b[u[2].toInt()] = u[1].toInt()
        if (flag(m) == CASTLE) {
            if (t > f) { b[t + 1] = b[t - 1]; b[t - 1] = 0 } else { b[t - 2] = b[t + 1]; b[t + 1] = 0 }
        }
        castle = u[3].toInt(); ep = u[4].toInt(); halfmove = u[5].toInt(); key = u[6]
    }

    fun repetitions() = keys.count { it == key }

    fun insufficient(): Boolean {
        val rest = b.filter { it != 0 && abs(it) != K }
        if (rest.isEmpty()) return true
        if (rest.size == 1 && abs(rest[0]) in intArrayOf(N, B)) return true
        return false
    }

    /** "checkmate", "stalemate", "fifty", "repetition", "material", or null. */
    fun result(): String? {
        if (legalMoves().isEmpty()) return if (inCheck()) "checkmate" else "stalemate"
        if (halfmove >= 100) return "fifty"
        if (repetitions() >= 3) return "repetition"
        if (insufficient()) return "material"
        return null
    }

    override fun terminal(): Int? {
        if (halfmove >= 100 || repetitions() >= 2 && undo.isNotEmpty() || insufficient()) return 0
        return null
    }

    override fun evaluate(): Int {
        var mg = 0
        var nonPawn = 0
        for (i in 0..63) { val p = b[i]; if (p != 0 && abs(p) in 2..5) nonPawn += VALUE[abs(p)] }
        val endgame = nonPawn <= 1300
        for (i in 0..63) {
            val p = b[i]; if (p == 0) continue
            val t = abs(p)
            val idx = if (p > 0) i else (7 - i / 8) * 8 + i % 8
            val pst = when (t) { P -> PST_P; N -> PST_N; B -> PST_B; R -> PST_R; Q -> PST_Q; else -> if (endgame) PST_KE else PST_K }[idx]
            val v = VALUE[t] + pst
            mg += if (p > 0) v else -v
        }
        return mg * turn
    }

    override fun orderKey(m: Int): Int {
        val victim = if (flag(m) == EP) P else abs(b[dst(m)])
        val attacker = abs(b[src(m)])
        return (if (victim != 0) 10 * VALUE[victim] - attacker else 0) + promo(m) * 100
    }

    /** Wraps [legalMoves] in a mate check for the search. */
    fun searchable(): Searchable<Int> = object : Searchable<Int> by this {
        override fun terminal(): Int? {
            this@Chess.terminal()?.let { return it }
            if (legalMoves().isEmpty()) return if (inCheck()) -WIN else 0
            return null
        }
    }

    fun perft(d: Int): Long {
        if (d == 0) return 1
        var n = 0L
        for (m in legalMoves()) { make(m); n += perft(d - 1); unmake() }
        return n
    }

    fun san(m: Int): String {
        val f = src(m); val t = dst(m); val p = abs(b[f])
        fun sq(i: Int) = "${'a' + i % 8}${8 - i / 8}"
        if (flag(m) == CASTLE) return if (t > f) "O-O" else "O-O-O"
        val cap = b[t] != 0 || flag(m) == EP
        val pre = if (p == P) (if (cap) "${'a' + f % 8}" else "") else "NBRQK"[p - 2].toString()
        val promoS = if (promo(m) != 0) "=" + "NBRQ"[promo(m) - 2] else ""
        make(m); val chk = if (inCheck()) (if (legalMoves().isEmpty()) "#" else "+") else ""; unmake()
        return pre + (if (cap) "x" else "") + sq(t) + promoS + chk
    }

    fun copyGame(): Chess {
        val g = Chess(); b.copyInto(g.b); g.turn = turn; g.castle = castle; g.ep = ep; g.halfmove = halfmove; g.key = key; g.keys += keys
        return g
    }
}
