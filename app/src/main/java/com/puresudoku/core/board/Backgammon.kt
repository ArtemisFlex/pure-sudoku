package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.math.abs
import kotlin.random.Random

/**
 * Backgammon. Points 0..23; you (side 0, positive counts) move from 23 down to 0 and bear off below 0;
 * the computer (side 1, negative counts) moves from 0 up to 23 and bears off above 23.
 * BAR / OFF are pseudo-points in [Step].
 */
class Backgammon {
    val pts = IntArray(24)
    val bar = IntArray(2)
    val off = IntArray(2)
    var turn = 0
    val dice = ArrayList<Int>()   // dice still to play this turn
    var rolled = IntArray(0)

    data class Step(val from: Int, val to: Int, val die: Int, val hit: Boolean)
    data class BgLevel(val noise: Double, val blunder: Double, val lookahead: Boolean)

    companion object {
        const val BAR = 100
        const val OFF = 200
        fun level(d: Difficulty) = when (d) {
            Difficulty.EASY -> BgLevel(30.0, 0.35, false)
            Difficulty.MEDIUM -> BgLevel(14.0, 0.15, false)
            Difficulty.HARD -> BgLevel(5.0, 0.05, false)
            Difficulty.EXPERT -> BgLevel(1.5, 0.0, false)
            Difficulty.MASTER -> BgLevel(0.0, 0.0, false)
            Difficulty.EXTREME -> BgLevel(0.0, 0.0, true)
        }
    }

    init { setup() }

    fun setup() {
        pts.fill(0); bar.fill(0); off.fill(0)
        pts[23] = 2; pts[12] = 5; pts[7] = 3; pts[5] = 5
        pts[0] = -2; pts[11] = -5; pts[16] = -3; pts[18] = -5
    }

    fun sign(side: Int) = if (side == 0) 1 else -1
    private fun own(i: Int, side: Int) = pts[i] * sign(side) > 0
    private fun count(i: Int, side: Int) = maxOf(0, pts[i] * sign(side))
    private fun enemyCount(i: Int, side: Int) = maxOf(0, -pts[i] * sign(side))

    fun homeRange(side: Int) = if (side == 0) 0..5 else 18..23
    fun allHome(side: Int) = bar[side] == 0 && (0..23).filter { own(it, side) }.all { it in homeRange(side) }

    /** Single legal steps for [side] with die [d]. */
    fun stepsFor(side: Int, d: Int): List<Step> {
        val out = ArrayList<Step>()
        if (bar[side] > 0) {
            val t = if (side == 0) 24 - d else d - 1
            if (enemyCount(t, side) <= 1) out += Step(BAR, t, d, enemyCount(t, side) == 1)
            return out
        }
        val home = allHome(side)
        for (i in 0..23) {
            if (!own(i, side)) continue
            val t = if (side == 0) i - d else i + d
            if (t in 0..23) { if (enemyCount(t, side) <= 1) out += Step(i, t, d, enemyCount(t, side) == 1) }
            else if (home) {
                val exact = if (side == 0) i == d - 1 else i == 24 - d
                val furthest = if (side == 0) (i + 1..5).none { own(it, side) } else (18 until i).none { own(it, side) }
                if (exact || furthest) out += Step(i, OFF, d, false)
            }
        }
        return out
    }

    fun apply(s: Step, side: Int = turn) {
        val sg = sign(side)
        if (s.from == BAR) bar[side]-- else pts[s.from] -= sg
        if (s.to == OFF) { off[side]++; return }
        if (s.hit) { pts[s.to] = 0; bar[1 - side]++ }
        pts[s.to] += sg
    }

    fun revert(s: Step, side: Int = turn) {
        val sg = sign(side)
        if (s.to == OFF) off[side]-- else {
            pts[s.to] -= sg
            if (s.hit) { pts[s.to] = -sg; bar[1 - side]-- }
        }
        if (s.from == BAR) bar[side]++ else pts[s.from] += sg
    }

    /** Every maximal legal sequence for the dice left. */
    fun sequences(side: Int = turn, left: List<Int> = dice, dedupe: Boolean = true): List<List<Step>> {
        val all = ArrayList<List<Step>>()
        fun rec(rem: List<Int>, acc: List<Step>) {
            var any = false
            for (d in rem.distinct()) {
                for (s in stepsFor(side, d)) {
                    any = true
                    apply(s, side)
                    rec(rem - d, acc + s)
                    revert(s, side)
                }
            }
            if (!any) all += acc
        }
        rec(left, emptyList())
        val maxLen = all.maxOfOrNull { it.size } ?: 0
        var best = all.filter { it.size == maxLen }
        if (maxLen == 1 && left.size == 2 && left[0] != left[1]) {
            val hi = maxOf(left[0], left[1])
            if (best.any { it[0].die == hi }) best = best.filter { it[0].die == hi }
        }
        if (!dedupe) return best.ifEmpty { listOf(emptyList()) }
        return best.distinctBy { seq -> seq.map { it.from to it.to }.sortedBy { it.first * 1000 + it.second } }
            .ifEmpty { listOf(emptyList()) }
    }

    fun roll(rng: Random) {
        val a = rng.nextInt(1, 7); val b = rng.nextInt(1, 7)
        rolled = intArrayOf(a, b)
        dice.clear(); if (a == b) repeat(4) { dice += a } else { dice += a; dice += b }
    }

    val winner get() = when { off[0] == 15 -> 0; off[1] == 15 -> 1; else -> -1 }

    /** 1 single, 2 gammon, 3 backgammon for the winner. */
    fun winPoints(): Int {
        val w = winner; if (w < 0) return 0
        val l = 1 - w
        if (off[l] > 0) return 1
        val inWinnerHome = (0..23).any { own(it, l) && it in homeRange(w) }
        return if (bar[l] > 0 || inWinnerHome) 3 else 2
    }

    fun pips(side: Int): Int {
        var p = bar[side] * 25
        for (i in 0..23) { val c = count(i, side); if (c > 0) p += c * (if (side == 0) i + 1 else 24 - i) }
        return p
    }

    /** Position value for [side] (bigger is better). */
    fun evaluate(side: Int): Double {
        val opp = 1 - side
        var v = (pips(opp) - pips(side)) * 1.0
        v += off[side] * 4.0 - off[opp] * 4.0
        v += bar[opp] * 12.0 - bar[side] * 12.0
        val contact = run {
            val myBack = if (side == 0) (0..23).filter { own(it, side) }.maxOrNull() ?: -1 else (0..23).filter { own(it, side) }.minOrNull() ?: 24
            val theirBack = if (side == 0) (0..23).filter { own(it, opp) }.minOrNull() ?: 24 else (0..23).filter { own(it, opp) }.maxOrNull() ?: -1
            bar[0] + bar[1] > 0 || (if (side == 0) myBack > theirBack else myBack < theirBack)
        }
        if (contact) {
            for (i in 0..23) {
                val c = count(i, side)
                if (c == 1) {
                    // shots: opposing checkers that can reach this blot
                    var shooters = 0
                    for (j in 0..23) if (own(j, opp)) { val dist = if (side == 0) i - j else j - i; if (dist in 1..12) shooters += enemyCount(j, side) }
                    if (bar[opp] > 0) shooters += 2
                    val danger = if (i in homeRange(opp)) 1.0 else 1.6
                    v -= minOf(shooters, 6) * 2.2 * danger
                }
                if (c >= 2) {
                    v += 3.0
                    if (i in homeRange(side)) v += 4.5
                    if ((if (side == 0) i in 4..7 else i in 16..19)) v += 2.0
                }
            }
            // primes
            var run = 0
            for (k in 0..23) { val i = if (side == 0) k else 23 - k; if (count(i, side) >= 2) { run++; v += run * 1.2 } else run = 0 }
        }
        return v
    }

    /** Computer's whole turn as a sequence of steps. */
    fun aiSequence(level: BgLevel, rng: Random): List<Step> {
        val side = turn
        val seqs = sequences()
        if (seqs.size <= 1) return seqs.firstOrNull().orEmpty()
        if (rng.nextDouble() < level.blunder) return seqs[rng.nextInt(seqs.size)]
        var best = seqs[0]; var bv = Double.NEGATIVE_INFINITY
        for (s in seqs) {
            for (st in s) apply(st, side)
            var v = evaluate(side) + if (level.noise > 0) rng.nextDouble(-level.noise, level.noise) else 0.0
            if (winner == side) v += 10_000.0
            if (level.lookahead && winner < 0) {
                // average the opponent's best single-die reply threat over a few rolls
                var worst = 0.0
                for (d in 1..6) {
                    val replies = stepsFor(1 - side, d)
                    worst += replies.count { it.hit } * 2.0
                }
                v -= worst
            }
            for (st in s.asReversed()) revert(st, side)
            if (v > bv) { bv = v; best = s }
        }
        return best
    }

    fun state(): IntArray = intArrayOf(*pts, bar[0], bar[1], off[0], off[1], turn, rolled.getOrElse(0) { 0 }, rolled.getOrElse(1) { 0 }, dice.size, *dice.toIntArray())
    fun load(s: IntArray) {
        s.copyInto(pts, 0, 0, 24); bar[0] = s[24]; bar[1] = s[25]; off[0] = s[26]; off[1] = s[27]; turn = s[28]
        rolled = if (s[29] == 0) IntArray(0) else intArrayOf(s[29], s[30])
        dice.clear(); for (k in 0 until s[31]) dice += s[32 + k]
    }

    fun checkerTotal(side: Int) = bar[side] + off[side] + (0..23).sumOf { count(it, side) }
    @Suppress("unused") private fun dbg() = abs(0)
}
