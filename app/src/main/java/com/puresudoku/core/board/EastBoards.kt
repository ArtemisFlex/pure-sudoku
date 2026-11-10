package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.math.abs

// =====================================================================  Xiangqi

/**
 * Chinese chess on 10 rows x 9 files (index r*9+c, r = 0 at the top). Red (you, positive) moves first
 * from the bottom; black (computer) is negative.
 * 1 general, 2 advisor, 3 elephant, 4 horse, 5 chariot, 6 cannon, 7 soldier.
 */
class Xiangqi : Searchable<Int> {
    val b = IntArray(90)
    var turn = 1
    var quiet = 0
    val keys = ArrayList<Long>()
    private val undo = ArrayList<IntArray>()

    init {
        val back = intArrayOf(5, 4, 3, 2, 1, 2, 3, 4, 5)
        for (c in 0..8) { b[c] = -back[c]; b[81 + c] = back[c] }
        b[2 * 9 + 1] = -6; b[2 * 9 + 7] = -6; b[7 * 9 + 1] = 6; b[7 * 9 + 7] = 6
        for (c in 0..8 step 2) { b[3 * 9 + c] = -7; b[6 * 9 + c] = 7 }
        keys += hash()
    }

    companion object {
        const val GEN = 1; const val ADV = 2; const val ELE = 3; const val HOR = 4; const val CHA = 5; const val CAN = 6; const val SOL = 7
        fun mv(f: Int, t: Int) = f or (t shl 7)
        fun src(m: Int) = m and 127
        fun dst(m: Int) = m shr 7
        val VALUE = intArrayOf(0, 0, 200, 200, 400, 900, 450, 100)
        val RED_NAMES = arrayOf("", "帥", "仕", "相", "傌", "俥", "炮", "兵")
        val BLACK_NAMES = arrayOf("", "將", "士", "象", "馬", "車", "砲", "卒")
        private val Z = Array(15) { k -> LongArray(90) { i -> (k * 1_000_003L + i * 7919L) * 0x9E3779B97F4A7C15uL.toLong() xor (i.toLong() shl 17) } }

        fun level(d: Difficulty) = when (d) {
            Difficulty.EASY -> AiLevel(1, 500, noise = 250, blunder = 0.15)
            Difficulty.MEDIUM -> AiLevel(2, 800, noise = 100, blunder = 0.05, quiescence = true)
            Difficulty.HARD -> AiLevel(3, 1200, noise = 30, quiescence = true)
            Difficulty.EXPERT -> AiLevel(4, 2000, noise = 8, quiescence = true)
            Difficulty.MASTER -> AiLevel(5, 3000, quiescence = true)
            Difficulty.EXTREME -> AiLevel(6, 4500, quiescence = true)
        }
    }

    fun hash(): Long { var h = if (turn > 0) 0L else 0x5DEECE66DL; for (i in 0..89) if (b[i] != 0) h = h xor Z[b[i] + 7][i]; return h }

    private fun inPalace(r: Int, c: Int, side: Int) = c in 3..5 && (if (side > 0) r in 7..9 else r in 0..2)
    private fun ownHalf(r: Int, side: Int) = if (side > 0) r >= 5 else r <= 4

    private fun pseudo(side: Int, out: MutableList<Int>, capturesOnly: Boolean = false) {
        for (i in 0..89) {
            val p = b[i]; if (p == 0 || p * side < 0) continue
            val r = i / 9; val c = i % 9
            fun add(rr: Int, cc: Int) {
                if (rr !in 0..9 || cc !in 0..8) return
                val t = rr * 9 + cc
                if (b[t] * side > 0) return
                if (capturesOnly && b[t] == 0) return
                out += mv(i, t)
            }
            when (abs(p)) {
                GEN -> for ((dr, dc) in arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) if (inPalace(r + dr, c + dc, side)) add(r + dr, c + dc)
                ADV -> for ((dr, dc) in arrayOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)) if (inPalace(r + dr, c + dc, side)) add(r + dr, c + dc)
                ELE -> for ((dr, dc) in arrayOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)) {
                    val rr = r + 2 * dr; val cc = c + 2 * dc
                    if (rr in 0..9 && cc in 0..8 && ownHalf(rr, side) && b[(r + dr) * 9 + c + dc] == 0) add(rr, cc)
                }
                HOR -> for ((dr, dc) in arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                    val lr = r + dr; val lc = c + dc
                    if (lr !in 0..9 || lc !in 0..8 || b[lr * 9 + lc] != 0) continue
                    if (dr != 0) { add(r + 2 * dr, c - 1); add(r + 2 * dr, c + 1) } else { add(r - 1, c + 2 * dc); add(r + 1, c + 2 * dc) }
                }
                CHA, CAN -> for ((dr, dc) in arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                    var rr = r + dr; var cc = c + dc; var jumped = false
                    while (rr in 0..9 && cc in 0..8) {
                        val t = rr * 9 + cc
                        if (abs(p) == CHA) {
                            if (b[t] == 0) { if (!capturesOnly) out += mv(i, t) } else { if (b[t] * side < 0) out += mv(i, t); break }
                        } else {
                            if (!jumped) { if (b[t] == 0) { if (!capturesOnly) out += mv(i, t) } else jumped = true }
                            else if (b[t] != 0) { if (b[t] * side < 0) out += mv(i, t); break }
                        }
                        rr += dr; cc += dc
                    }
                }
                SOL -> {
                    add(r - side, c)
                    if (!ownHalf(r, side)) { add(r, c - 1); add(r, c + 1) }
                }
            }
        }
    }

    private fun general(side: Int): Int { for (i in 0..89) if (b[i] == GEN * side) return i; return -1 }

    fun inCheck(side: Int = turn): Boolean {
        val g = general(side); if (g < 0) return true
        val opp = general(-side)
        if (opp >= 0 && opp % 9 == g % 9) {
            var clear = true
            for (r in minOf(g, opp) / 9 + 1 until maxOf(g, opp) / 9) if (b[r * 9 + g % 9] != 0) { clear = false; break }
            if (clear) return true
        }
        val ms = ArrayList<Int>(); pseudo(-side, ms, capturesOnly = true)
        return ms.any { dst(it) == g }
    }

    private fun legal(list: List<Int>): List<Int> { val me = turn; return list.filter { make(it); val ok = !inCheck(me); unmake(); ok } }
    override fun legalMoves(): List<Int> { val o = ArrayList<Int>(); pseudo(turn, o); return legal(o) }
    override fun noisyMoves(): List<Int> { val o = ArrayList<Int>(); pseudo(turn, o, true); return legal(o) }

    override fun make(m: Int) {
        val f = src(m); val t = dst(m)
        undo += intArrayOf(m, b[t], quiet)
        quiet = if (b[t] != 0) 0 else quiet + 1
        b[t] = b[f]; b[f] = 0; turn = -turn
        keys += hash()
    }

    override fun unmake() {
        val u = undo.removeAt(undo.lastIndex); keys.removeAt(keys.lastIndex)
        val f = src(u[0]); val t = dst(u[0])
        b[f] = b[t]; b[t] = u[1]; quiet = u[2]; turn = -turn
    }

    fun repetitions() = keys.count { it == keys.last() }

    fun result(): String? {
        if (legalMoves().isEmpty()) return if (inCheck()) "checkmate" else "stalemate"
        if (quiet >= 120) return "quiet"
        if (repetitions() >= 3) return "repetition"
        return null
    }

    override fun terminal(): Int? {
        if (quiet >= 120 || (undo.isNotEmpty() && repetitions() >= 2)) return 0
        if (legalMoves().isEmpty()) return -WIN
        return null
    }

    override fun evaluate(): Int {
        var v = 0
        for (i in 0..89) {
            val p = b[i]; if (p == 0) continue
            val side = if (p > 0) 1 else -1; val t = abs(p); val r = i / 9; val c = i % 9
            var s = VALUE[t]
            if (t == SOL) {
                val adv = if (side > 0) 9 - r else r
                if (!ownHalf(r, side)) s += 100 + (if (c in 3..5) 30 else 0) + adv * 5
            }
            if (t == HOR || t == CAN) s += (4 - abs(c - 4)) * 4
            if (t == CHA) s += (if (side > 0) 9 - r else r) * 3
            v += s * side
        }
        return v * turn
    }

    override fun orderKey(m: Int): Int { val v = abs(b[dst(m)]); return if (v != 0) VALUE[v] * 10 + (if (v == GEN) 50000 else 0) - VALUE[abs(b[src(m)])] else 0 }

    fun state(): IntArray = intArrayOf(*b, turn, quiet)
    fun load(s: IntArray) { s.copyInto(b, 0, 0, 90); turn = s[90]; quiet = s[91]; keys.clear(); keys += hash() }
}

// =====================================================================  Shogi

/**
 * Japanese chess on 9x9 (index r*9+c, r = 0 at the top). You are sente (positive, bottom, first);
 * the computer is gote (negative). Captured pieces go to your hand and can be dropped back.
 * 1 pawn, 2 lance, 3 knight, 4 silver, 5 gold, 6 bishop, 7 rook, 8 king;
 * promoted: 9 tokin, 10 lance, 11 knight, 12 silver, 13 horse (bishop), 14 dragon (rook).
 */
class Shogi : Searchable<Int> {
    val b = IntArray(81)
    /** hands[0] = yours, hands[1] = computer's; index by piece type 1..7. */
    val hands = arrayOf(IntArray(8), IntArray(8))
    var turn = 1
    var plies = 0
    val keys = ArrayList<Long>()
    private val undo = ArrayList<IntArray>()

    init {
        val back = intArrayOf(2, 3, 4, 5, 8, 5, 4, 3, 2)
        for (c in 0..8) { b[c] = -back[c]; b[72 + c] = back[c]; b[18 + c] = -1; b[54 + c] = 1 }
        b[9 + 1] = -7; b[9 + 7] = -6; b[63 + 1] = 6; b[63 + 7] = 7
        keys += hash()
    }

    companion object {
        const val PAWN = 1; const val LANCE = 2; const val KNIGHT = 3; const val SILVER = 4; const val GOLD = 5; const val BISHOP = 6; const val ROOK = 7; const val KING = 8
        const val DROP = 81
        /** Move: from (0..80, or 81 + piece for a drop) | to << 7 | promote << 14. */
        fun mv(f: Int, t: Int, promote: Boolean = false) = f or (t shl 7) or (if (promote) 1 shl 14 else 0)
        fun src(m: Int) = m and 127
        fun dst(m: Int) = (m shr 7) and 127
        fun promotes(m: Int) = (m shr 14) and 1 == 1
        fun isDrop(m: Int) = src(m) >= DROP

        fun base(t: Int) = if (t in 9..12) t - 8 else if (t == 13) BISHOP else if (t == 14) ROOK else t
        fun promoted(t: Int) = t >= 9
        fun canPromote(t: Int) = t in 1..4 || t == BISHOP || t == ROOK
        fun promote(t: Int) = when (t) { in 1..4 -> t + 8; BISHOP -> 13; ROOK -> 14; else -> t }

        val VALUE = intArrayOf(0, 100, 300, 350, 500, 600, 800, 1000, 0, 550, 550, 550, 550, 1050, 1250)
        val NAMES = arrayOf("", "歩", "香", "桂", "銀", "金", "角", "飛", "王", "と", "杏", "圭", "全", "馬", "龍")
        private val Z = Array(29) { k -> LongArray(81) { i -> ((k + 3) * 2_654_435_761L + i * 40_503L) * -7046029254386353131L } }

        fun level(d: Difficulty) = when (d) {
            Difficulty.EASY -> AiLevel(1, 500, noise = 250, blunder = 0.15)
            Difficulty.MEDIUM -> AiLevel(2, 900, noise = 100, blunder = 0.05)
            Difficulty.HARD -> AiLevel(2, 1200, noise = 25, quiescence = true)
            Difficulty.EXPERT -> AiLevel(3, 2200, noise = 6, quiescence = true)
            Difficulty.MASTER -> AiLevel(3, 3500, quiescence = true)
            Difficulty.EXTREME -> AiLevel(4, 5000, quiescence = true)
        }
    }

    fun hash(): Long {
        var h = if (turn > 0) 0L else 0x2545F4914F6CDD1DL
        for (i in 0..80) if (b[i] != 0) h = h xor Z[b[i] + 14][i]
        for (s in 0..1) for (t in 1..7) h += hands[s][t].toLong() * (t * 31 + s * 7 + 1) * 0x9E3779B97F4A7C15uL.toLong()
        return h
    }

    private fun steps(t: Int): Array<Pair<Int, Int>> = when (t) {
        PAWN -> arrayOf(-1 to 0)
        KNIGHT -> arrayOf(-2 to -1, -2 to 1)
        SILVER -> arrayOf(-1 to -1, -1 to 0, -1 to 1, 1 to -1, 1 to 1)
        GOLD, 9, 10, 11, 12 -> arrayOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to 0)
        KING -> arrayOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
        13 -> arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        14 -> arrayOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        else -> emptyArray()
    }

    private fun slides(t: Int): Array<Pair<Int, Int>> = when (t) {
        LANCE -> arrayOf(-1 to 0)
        BISHOP, 13 -> arrayOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        ROOK, 14 -> arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        else -> emptyArray()
    }

    private fun inZone(r: Int, side: Int) = if (side > 0) r <= 2 else r >= 6
    /** Rows from the far edge (0 = last row) for a side. */
    private fun depthFromEnd(r: Int, side: Int) = if (side > 0) r else 8 - r

    private fun mustPromote(t: Int, r: Int, side: Int) = when (t) {
        PAWN, LANCE -> depthFromEnd(r, side) == 0
        KNIGHT -> depthFromEnd(r, side) <= 1
        else -> false
    }

    private fun pseudo(side: Int, out: MutableList<Int>, capturesOnly: Boolean = false, withDrops: Boolean = true) {
        for (i in 0..80) {
            val p = b[i]; if (p == 0 || p * side < 0) continue
            val t = abs(p); val r = i / 9; val c = i % 9
            fun add(rr: Int, cc: Int): Boolean {
                if (rr !in 0..8 || cc !in 0..8) return false
                val to = rr * 9 + cc
                if (b[to] * side > 0) return false
                if (!(capturesOnly && b[to] == 0)) {
                    if (canPromote(t) && (inZone(r, side) || inZone(rr, side))) {
                        out += mv(i, to, true)
                        if (!mustPromote(t, rr, side)) out += mv(i, to, false)
                    } else out += mv(i, to)
                }
                return b[to] == 0
            }
            for ((dr, dc) in steps(t)) add(r + dr * side, c + dc * side)
            for ((dr, dc) in slides(t)) {
                var k = 1
                while (add(r + dr * side * k, c + dc * side * k)) k++
            }
        }
        if (!withDrops || capturesOnly) return
        val hand = hands[if (side > 0) 0 else 1]
        for (t in 1..7) {
            if (hand[t] == 0) continue
            for (i in 0..80) {
                if (b[i] != 0) continue
                val r = i / 9
                if ((t == PAWN || t == LANCE) && depthFromEnd(r, side) == 0) continue
                if (t == KNIGHT && depthFromEnd(r, side) <= 1) continue
                if (t == PAWN) { val c = i % 9; if ((0..8).any { b[it * 9 + c] == PAWN * side }) continue }
                out += mv(DROP + t, i)
            }
        }
    }

    private fun king(side: Int): Int { for (i in 0..80) if (b[i] == KING * side) return i; return -1 }

    fun inCheck(side: Int = turn): Boolean {
        val k = king(side); if (k < 0) return true
        val ms = ArrayList<Int>(); pseudo(-side, ms, capturesOnly = true, withDrops = false)
        return ms.any { dst(it) == k }
    }

    private fun legalAll(list: List<Int>, checkPawnMate: Boolean): List<Int> {
        val me = turn
        return list.filter { m ->
            make(m)
            var ok = !inCheck(me)
            if (ok && checkPawnMate && isDrop(m) && src(m) - DROP == PAWN && inCheck(-me)) {
                // a pawn drop may not deliver checkmate
                val replies = ArrayList<Int>(); pseudo(turn, replies)
                if (replies.none { r -> make(r); val esc = !inCheck(-me); unmake(); esc }) ok = false
            }
            unmake(); ok
        }
    }

    override fun legalMoves(): List<Int> { val o = ArrayList<Int>(160); pseudo(turn, o); return legalAll(o, true) }
    override fun noisyMoves(): List<Int> { val o = ArrayList<Int>(); pseudo(turn, o, capturesOnly = true); return legalAll(o, false) }

    override fun make(m: Int) {
        val t = dst(m); val side = turn; val hand = hands[if (side > 0) 0 else 1]
        undo += intArrayOf(m, b[t], plies)
        if (isDrop(m)) {
            val pt = src(m) - DROP; hand[pt]--; b[t] = pt * side
        } else {
            val f = src(m)
            if (b[t] != 0) hand[base(abs(b[t]))]++
            val pt = abs(b[f])
            b[t] = (if (promotes(m)) promote(pt) else pt) * side
            b[f] = 0
        }
        plies++; turn = -turn
        keys += hash()
    }

    override fun unmake() {
        val u = undo.removeAt(undo.lastIndex); keys.removeAt(keys.lastIndex)
        turn = -turn
        val m = u[0]; val t = dst(m); val side = turn; val hand = hands[if (side > 0) 0 else 1]
        if (isDrop(m)) { hand[src(m) - DROP]++; b[t] = 0 }
        else {
            val f = src(m)
            val pt = abs(b[t])
            b[f] = (if (promotes(m)) base(pt) else pt) * side
            b[t] = u[1]
            if (u[1] != 0) hand[base(abs(u[1]))]--
        }
        plies = u[2]
    }

    fun repetitions() = keys.count { it == keys.last() }

    fun result(): String? {
        if (legalMoves().isEmpty()) return "checkmate"
        if (repetitions() >= 4) return "repetition"
        if (plies >= 400) return "moves"
        return null
    }

    override fun terminal(): Int? {
        if (undo.isNotEmpty() && repetitions() >= 2) return 0
        if (legalMoves().isEmpty()) return -WIN
        return null
    }

    override fun evaluate(): Int {
        var v = 0
        for (i in 0..80) {
            val p = b[i]; if (p == 0) continue
            val side = if (p > 0) 1 else -1; val t = abs(p)
            var s = VALUE[t]
            if (t == PAWN || t == SILVER || t == GOLD) s += (8 - depthFromEnd(i / 9, side)).let { 0 } + (if (inZone(i / 9, side)) 15 else 0)
            v += s * side
        }
        for (t in 1..7) v += (hands[0][t] - hands[1][t]) * VALUE[t] * 11 / 10
        // keep the king guarded
        for (side in intArrayOf(1, -1)) {
            val k = king(side); if (k < 0) continue
            var guards = 0
            for (dr in -1..1) for (dc in -1..1) { val r = k / 9 + dr; val c = k % 9 + dc; if (r in 0..8 && c in 0..8 && b[r * 9 + c] * side > 0) guards++ }
            v += guards * 12 * side
        }
        return v * turn
    }

    override fun orderKey(m: Int): Int {
        if (isDrop(m)) return 0
        val v = abs(b[dst(m)])
        return (if (v != 0) VALUE[v] * 10 - VALUE[abs(b[src(m)])] else 0) + (if (promotes(m)) 300 else 0)
    }

    fun state(): IntArray = intArrayOf(*b, *hands[0], *hands[1], turn, plies)
    fun load(s: IntArray) {
        s.copyInto(b, 0, 0, 81); s.copyInto(hands[0], 0, 81, 89); s.copyInto(hands[1], 0, 89, 97); turn = s[97]; plies = s[98]
        keys.clear(); keys += hash()
    }
}
