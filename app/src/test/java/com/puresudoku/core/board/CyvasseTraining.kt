package com.puresudoku.core.board

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Self-play training for the Cyvasse computer. Not part of the normal test run: set CYVASSE_TRAIN to run it.
 *
 *  - CYVASSE_TRAIN=bench   times a few self-play games
 *  - CYVASSE_TRAIN=tune    tunes [CyvasseEval] weights by SPSA self-play, then matches them against the hand-set ones
 *  - CYVASSE_TRAIN=study   plays trained-vs-trained games and reports how often winners and losers used each strategy
 *
 * Results go to build/cyvasse-training.txt.
 */
class CyvasseTraining {
    private val mode = System.getenv("CYVASSE_TRAIN")
    private val out = File("build/cyvasse-training.txt")
    private var lastEnd = ""
    private var longest = 0
    private fun log(s: String) { println(s); out.appendText(s + "\n") }

    /** One game: [wA] plays [aSide]. Returns the winner (0, 1) or [Cyvasse.DRAWN]. [uses] counts each side's strategies. */
    private fun game(wA: IntArray, wB: IntArray, aSide: Int, ai: AiLevel, rnd: Random, uses: Array<IntArray>? = null, aiB: AiLevel = ai): Int {
        val g = Cyvasse.standard()
        var plies = 0
        // no move limit: every game is played out to checkmate or stalemate
        while (g.phase == Cyvasse.BATTLE) {
            val legal = g.legalMoves()
            if (legal.isEmpty()) break
            // a couple of random opening moves so games differ
            val m = if (plies < 2) legal[rnd.nextInt(legal.size)] else if (g.turn == aSide) CyvasseAi.best(g, ai, rnd, wA) else CyvasseAi.best(g, aiB, rnd, wB)
            if (uses != null) CyvasseStrategy.of(g, m)?.let { uses[g.turn][it.ordinal]++ }
            g.play(m)
            plies++
        }
        lastEnd = "after $plies plies: ${g.lastText}"
        longest = maxOf(longest, plies)
        return if (g.phase == Cyvasse.OVER) g.winner else Cyvasse.DRAWN
    }

    /** [a]'s score over [n] pairs of games (each side once): win 1, draw 0.5. */
    private fun match(a: IntArray, b: IntArray, n: Int, ai: AiLevel, seed: Int, aiB: AiLevel = ai): Double {
        var score = 0.0; var w = 0; var d = 0; var l = 0
        for (i in 0 until n) for (side in 0..1) {
            val r = game(a, b, side, ai, Random(seed * 1000 + i), aiB = aiB)
            if (r == side) w++ else if (r == Cyvasse.DRAWN) d++ else l++
            score += if (r == side) 1.0 else if (r == Cyvasse.DRAWN) 0.5 else 0.0
        }
        log("  match: $w won, $d drawn, $l lost")
        return score / (2 * n)
    }

    @Test fun train() {
        assumeTrue(mode != null)
        out.parentFile.mkdirs()
        when (mode) {
            "bench" -> {
                val ai = AiLevel(2, 100_000, noise = 6, quiescence = true)
                for (i in 0 until 2) {
                    val t = System.currentTimeMillis()
                    val r = game(CyvasseEval.HAND, CyvasseEval.HAND, 0, ai, Random(i))
                    log("bench game $i: winner $r in ${System.currentTimeMillis() - t} ms, $lastEnd")
                }
            }
            "tune" -> tune()
            "speed" -> {
                val g = Cyvasse.standard()
                val r = Random(1)
                repeat(8) { g.play(g.legalMoves().let { it[r.nextInt(it.size)] }) }
                for (d in 2..5) {
                    val t = System.currentTimeMillis()
                    val m = CyvasseAi.best(g, AiLevel(d, 600_000, quiescence = true), Random(1))
                    log("depth $d: ${System.currentTimeMillis() - t} ms (${g.describe(m)})")
                }
            }
            "check" -> {
                val d2 = AiLevel(2, 100_000, noise = 6, quiescence = true)
                val d3 = AiLevel(3, 100_000, noise = 6, quiescence = true)
                val n = (System.getenv("CYVASSE_MATCH") ?: "6").toInt()
                log("depth 3 vs depth 2: ${"%.1f".format(100 * match(CyvasseEval.HAND, CyvasseEval.HAND, n, d3, 5, aiB = d2))}%")
                System.getenv("CYVASSE_W")?.let { wt ->
                    val w = wt.split(",").map { it.trim().toInt() }.toIntArray()
                    log("candidate vs hand-set: ${"%.1f".format(100 * match(w, CyvasseEval.HAND, n * 3, d2, 9))}%")
                }
            }
            "study" -> study()
        }
    }

    private fun tune() {
        val iters = (System.getenv("CYVASSE_ITERS") ?: "120").toInt()
        val ai = AiLevel(2, 100_000, noise = 6, quiescence = true)
        val theta = CyvasseEval.HAND.map { it.toDouble() }.toDoubleArray()
        // how far each weight is nudged: a share of its size (at least 1)
        val step = DoubleArray(theta.size) { maxOf(1.0, kotlin.math.abs(theta[it]) * 0.15) }
        val rnd = Random(2026)
        log("tune: $iters SPSA iterations at depth ${ai.depth}")
        for (k in 1..iters) {
            val delta = IntArray(theta.size) { if (rnd.nextBoolean()) 1 else -1 }
            val plus = IntArray(theta.size) { (theta[it] + step[it] * delta[it]).toInt() }
            val minus = IntArray(theta.size) { (theta[it] - step[it] * delta[it]).toInt() }
            // a pair of games, each set of weights playing each side once
            var r = 0.0
            for (side in 0..1) {
                val w = game(plus, minus, side, ai, Random(rnd.nextInt()))
                r += if (w == side) 1.0 else if (w == Cyvasse.DRAWN) 0.0 else -1.0
            }
            val gain = 0.6 / Math.pow(k.toDouble(), 0.3)
            for (i in theta.indices) theta[i] += gain * r * delta[i] * step[i] / 2
            // piece values keep their order of magnitude
            for (i in 0..8) theta[i] = theta[i].coerceIn(40.0, 1400.0)
            if (k % 10 == 0) log("iter $k (longest game so far $longest plies): " + theta.joinToString(", ") { it.toInt().toString() })
        }
        val tuned = IntArray(theta.size) { theta[it].toInt() }
        log("TUNED = intArrayOf(${tuned.joinToString(", ")}); longest game $longest plies")
        val n = (System.getenv("CYVASSE_MATCH") ?: "12").toInt()
        val s = match(tuned, CyvasseEval.HAND, n, ai, 77)
        log("tuned vs hand-set: ${"%.1f".format(s * 100)}% over ${2 * n} games")
    }

    private fun study() {
        val n = (System.getenv("CYVASSE_GAMES") ?: "40").toInt()
        val ai = AiLevel(2, 100_000, noise = 6, quiescence = true)
        val winner = IntArray(CyvasseStrategy.entries.size); val loser = IntArray(CyvasseStrategy.entries.size)
        var decided = 0; var draws = 0
        val rnd = Random(4242)
        for (i in 0 until n) {
            val uses = Array(2) { IntArray(CyvasseStrategy.entries.size) }
            val r = game(CyvasseEval.TRAINED, CyvasseEval.TRAINED, 0, ai, Random(rnd.nextInt()), uses)
            if (r == Cyvasse.DRAWN) { draws++; continue }
            decided++
            for (s in CyvasseStrategy.entries) { winner[s.ordinal] += uses[r][s.ordinal]; loser[s.ordinal] += uses[1 - r][s.ordinal] }
        }
        log("study: $n games, $decided decided, $draws drawn")
        for (s in CyvasseStrategy.entries) {
            val w = winner[s.ordinal]; val l = loser[s.ordinal]
            val share = if (w + l == 0) 0 else 100 * w / (w + l)
            log("${s.name}: winners ${"%.1f".format(w.toDouble() / maxOf(1, decided))} a game, losers ${"%.1f".format(l.toDouble() / maxOf(1, decided))}; winners' share $share%")
        }
    }
}
