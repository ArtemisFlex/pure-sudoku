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
    @Volatile private var lastEnd = ""
    @Volatile private var longest = 0
    private fun log(s: String) { println(s); out.appendText(s + "\n") }

    /** One game: [wA] plays [aSide]. Returns the winner (0, 1) or [Cyvasse.DRAWN]. [uses] counts each side's strategies. */
    private fun game(wA: IntArray, wB: IntArray, aSide: Int, ai: AiLevel, rnd: Random, uses: Array<IntArray>? = null, aiB: AiLevel = ai,
                     start: Cyvasse = Cyvasse.standard(), randomPlies: Int = 2): Int {
        val g = start
        var plies = 0
        // no move limit. A game only stops short of checkmate or stalemate when it can never end: two bare kings,
        // or the same position for the third time (as in chess); those count as draws in training.
        val seen = HashMap<Int, Int>()
        while (g.phase == Cyvasse.BATTLE) {
            if ((0 until Cyvasse.CELLS).none { g.board[it] != 0 && Cyvasse.typeOf(g.board[it]) != Cyvasse.KING }) break
            val key = g.board.contentHashCode() * 31 + g.waterWait.contentHashCode() * 7 + g.turn
            if (seen.merge(key, 1, Int::plus)!! >= 3) break
            val legal = g.legalMoves()
            if (legal.isEmpty()) break
            // a couple of random opening moves so games differ
            val m = if (plies < randomPlies) legal[rnd.nextInt(legal.size)] else if (g.turn == aSide) CyvasseAi.best(g, ai, rnd, wA) else CyvasseAi.best(g, aiB, rnd, wB)
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
            "place" -> place()
            "learnplace" -> learnPlace()
            "layouts" -> layouts()
            "colors" -> {
                val ai = AiLevel(2, 100_000, noise = 6, quiescence = true)
                for (i in 0 until 10) {
                    val gr = Random(100 + i)
                    val r = game(CyvasseEval.TRAINED, CyvasseEval.TRAINED, 0, ai, gr, start = openStart({ it.randomSetup(0, gr) }, { it.randomSetup(1, gr) }), randomPlies = 0)
                    log("open game $i: winner $r, $lastEnd")
                }
            }
            "opentune" -> openTune()
            "openstudy" -> study(open = true)
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
            "study" -> study(open = false)
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

    private fun study(open: Boolean) {
        val n = (System.getenv("CYVASSE_GAMES") ?: "40").toInt()
        val ai = AiLevel(2, 100_000, noise = 6, quiescence = true)
        val winner = IntArray(CyvasseStrategy.entries.size); val loser = IntArray(CyvasseStrategy.entries.size)
        var decided = 0; var draws = 0
        val rnd = Random(4242)
        for (i in 0 until n) {
            val uses = Array(2) { IntArray(CyvasseStrategy.entries.size) }
            val gr = Random(rnd.nextInt())
            val r = if (open) game(CyvasseEval.TRAINED_OPEN, CyvasseEval.TRAINED_OPEN, 0, ai, gr, uses, start = trainedOpenStart(gr), randomPlies = 0)
                else game(CyvasseEval.TRAINED, CyvasseEval.TRAINED, 0, ai, gr, uses)
            if (r == Cyvasse.DRAWN) { draws++; continue }
            decided++
            for (s in CyvasseStrategy.entries) { winner[s.ordinal] += uses[r][s.ordinal]; loser[s.ordinal] += uses[1 - r][s.ordinal] }
        }
        log("study${if (open) " (open field)" else ""}: $n games, $decided decided, $draws drawn")
        for (s in CyvasseStrategy.entries) {
            val w = winner[s.ordinal]; val l = loser[s.ordinal]
            val share = if (w + l == 0) 0 else 100 * w / (w + l)
            log("${s.name}: winners ${"%.1f".format(w.toDouble() / maxOf(1, decided))} a game, losers ${"%.1f".format(l.toDouble() / maxOf(1, decided))}; winners' share $share%")
        }
    }

    // ------------------------------------------------------------------ open field

    /** An open-field start: [setA] places Alabaster's terrain, [setB] Onyx's. */
    private fun openStart(setA: (Cyvasse) -> Unit, setB: (Cyvasse) -> Unit): Cyvasse {
        val g = Cyvasse.openField()
        setA(g); check(g.ready(0, false)); setB(g); check(g.ready(1, false))
        return g
    }

    private fun slots(g: Cyvasse, side: Int, chosen: IntArray) {
        val old = g.placer; g.placer = side; g.clearSetup(side)
        if (chosen[0] >= 0) g.placeKeep(side, CyvassePlacement.tile(chosen[0], side))
        for (k in 1..3) g.toggleMountain(side, CyvassePlacement.tile(chosen[k], side))
        for (k in 4..6) g.toggleTerrain(side, Cyvasse.FOREST, CyvassePlacement.tile(chosen[k], side))
        g.placer = old
    }

    private fun trainedOpenStart(rnd: Random) = openStart(
        { CyvassePlacement.place(it, 0, CyvassePlacement.FLAT, 0.5, rnd) }, { CyvassePlacement.place(it, 1, CyvassePlacement.FLAT, 0.5, rnd) })

    /**
     * Learns placement preferences by the cross-entropy method: each generation samples layouts from the current
     * preferences, plays each (as both colours) against random layouts, and shifts the preferences toward the best.
     */
    private fun place() {
        val gens = (System.getenv("CYVASSE_GENS") ?: "10").toInt(); val pop = (System.getenv("CYVASSE_POP") ?: "16").toInt()
        val ai = AiLevel(2, 100_000, noise = 6, quiescence = true)
        val prefs = Array(3) { DoubleArray(CyvassePlacement.TILES.size) }
        val rnd = Random(31)
        log("place: $gens generations of $pop layouts")
        for (gen in 1..gens) {
            val cands = List(pop) { CyvassePlacement.place(Cyvasse.openField(), 0, prefs, 1.0, rnd) }
            val score = DoubleArray(pop)
            for ((i, c) in cands.withIndex()) for (side in 0..1) {
                val gr = Random(rnd.nextInt())
                val start = if (side == 0) openStart({ slots(it, 0, c) }, { it.randomSetup(1, gr) }) else openStart({ it.randomSetup(0, gr) }, { slots(it, 1, c) })
                val r = game(CyvasseEval.TRAINED, CyvasseEval.TRAINED, side, ai, gr, start = start, randomPlies = 0)
                score[i] += if (r == side) 1.0 else if (r == Cyvasse.DRAWN) 0.5 else 0.0
            }
            val elite = cands.indices.sortedByDescending { score[it] + rnd.nextDouble() * 0.01 }.take(pop / 4)
            for (item in 0..2) {
                val range = when (item) { 0 -> 0..0; 1 -> 1..3; else -> 4..6 }
                val all = DoubleArray(prefs[item].size); val best = DoubleArray(prefs[item].size)
                for (c in cands) for (k in range) all[c[k]] += 1.0 / pop
                for (e in elite) for (k in range) best[cands[e][k]] += 1.0 / elite.size
                for (t in prefs[item].indices) prefs[item][t] += 3.0 * (best[t] - all[t])
            }
            log("gen $gen: mean score ${"%.2f".format(score.average())} of 2, best ${score.max()}")
        }
        for ((item, name) in listOf("KEEP", "MOUNTAIN", "FOREST").withIndex())
            log("$name = doubleArrayOf(${prefs[item].joinToString(", ") { "%.2f".format(it) }})")
        // how the learned layouts fare against random ones
        val n = (System.getenv("CYVASSE_MATCH") ?: "20").toInt()
        var w = 0; var d = 0; var l = 0
        for (i in 0 until n) for (side in 0..1) {
            val gr = Random(9000 + i)
            val learned = { g: Cyvasse, sd: Int -> CyvassePlacement.place(g, sd, prefs, 0.5, gr); Unit }
            val start = if (side == 0) openStart({ learned(it, 0) }, { it.randomSetup(1, gr) }) else openStart({ it.randomSetup(0, gr) }, { learned(it, 1) })
            val r = game(CyvasseEval.TRAINED, CyvasseEval.TRAINED, side, ai, gr, start = start, randomPlies = 0)
            if (r == side) w++ else if (r == Cyvasse.DRAWN) d++ else l++
        }
        log("learned layouts vs random layouts: $w won, $d drawn, $l lost")
    }

    /** SPSA on open-field games (both sides placing with the trained preferences), starting from the standard weights. */
    private fun openTune() {
        val iters = (System.getenv("CYVASSE_ITERS") ?: "120").toInt()
        val ai = AiLevel(2, 100_000, noise = 6, quiescence = true)
        val theta = CyvasseEval.TRAINED.map { it.toDouble() }.toDoubleArray()
        val step = DoubleArray(theta.size) { maxOf(1.0, kotlin.math.abs(theta[it]) * 0.15) }
        val rnd = Random(77)
        log("opentune: $iters SPSA iterations")
        for (k in 1..iters) {
            val delta = IntArray(theta.size) { if (rnd.nextBoolean()) 1 else -1 }
            val plus = IntArray(theta.size) { (theta[it] + step[it] * delta[it]).toInt() }
            val minus = IntArray(theta.size) { (theta[it] - step[it] * delta[it]).toInt() }
            // a batch of game pairs in parallel, so each step is judged on more than one pair
            val pairs = (System.getenv("CYVASSE_PAIRS") ?: "12").toInt()
            val seed = rnd.nextInt()
            val r = parallel(pairs * 2) { j ->
                val side = j % 2; val gr = Random(seed + j / 2)
                val w = game(plus, minus, side, ai, gr, start = trainedOpenStart(gr), randomPlies = 0)
                if (w == side) 1.0 else if (w == Cyvasse.DRAWN) 0.0 else -1.0
            }.sum() / pairs
            val gain = 0.6 / Math.pow(k.toDouble(), 0.3)
            for (i in theta.indices) theta[i] += gain * r * delta[i] * step[i] / 2
            for (i in 0..8) theta[i] = theta[i].coerceIn(40.0, 1400.0)
            if (k % 10 == 0) log("iter $k: " + theta.joinToString(", ") { it.toInt().toString() })
        }
        val tuned = IntArray(theta.size) { theta[it].toInt() }
        log("TRAINED_OPEN = intArrayOf(${tuned.joinToString(", ")})")
        val n = (System.getenv("CYVASSE_MATCH") ?: "24").toInt()
        val res = parallel(n * 2) { j ->
            val side = j % 2; val gr = Random(5000 + j / 2)
            val r = game(tuned, CyvasseEval.TRAINED, side, ai, gr, start = trainedOpenStart(gr), randomPlies = 0)
            if (r == side) 1 else if (r == Cyvasse.DRAWN) 0 else -1
        }
        val w = res.count { it == 1 }; val d = res.count { it == 0 }; val l = res.count { it == -1 }
        log("open-field weights vs standard weights on open fields: $w won, $d drawn, $l lost")
    }

    private val threads = maxOf(2, Runtime.getRuntime().availableProcessors() - 4)

    /** Runs [n] independent jobs on all cores. */
    private fun <T> parallel(n: Int, job: (Int) -> T): List<T> {
        val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
        try { return (0 until n).map { i -> pool.submit<T> { job(i) } }.map { it.get() } } finally { pool.shutdown() }
    }

    /**
     * Learns where to place terrain from many games between random layouts: for every tile and item (fortress,
     * mountain, forest), how often the side that put it there won. The preferences are those win rates as log-odds.
     */
    private fun learnPlace() {
        val n = (System.getenv("CYVASSE_GAMES") ?: "600").toInt()
        val ai = AiLevel(2, 100_000, noise = 6, quiescence = true)
        val uniform = Array(3) { DoubleArray(CyvassePlacement.TILES.size) }
        log("learnplace: $n games between random layouts on $threads threads")
        val t0 = System.currentTimeMillis()
        val results = parallel(n) { i ->
            val gr = Random(70_000 + i)
            var a = IntArray(0); var b = IntArray(0)
            val start = openStart({ a = CyvassePlacement.place(it, 0, uniform, 1.0, gr) }, { b = CyvassePlacement.place(it, 1, uniform, 1.0, gr) })
            Triple(a, b, game(CyvasseEval.TRAINED, CyvasseEval.TRAINED, 0, ai, gr, start = start, randomPlies = 0))
        }
        log("played in ${(System.currentTimeMillis() - t0) / 1000} s; Alabaster won ${results.count { it.third == 0 }}, Onyx ${results.count { it.third == 1 }}, drawn ${results.count { it.third == Cyvasse.DRAWN }}")
        val wins = Array(3) { DoubleArray(CyvassePlacement.TILES.size) }; val plays = Array(3) { DoubleArray(CyvassePlacement.TILES.size) }
        for ((a, b, r) in results) for ((side, slots) in listOf(0 to a, 1 to b)) {
            val score = if (r == side) 1.0 else if (r == Cyvasse.DRAWN) 0.5 else 0.0
            for ((k, slot) in slots.withIndex()) {
                val item = if (k == 0) 0 else if (k <= 3) 1 else 2
                plays[item][slot] += 1.0; wins[item][slot] += score
            }
        }
        val prefs = Array(3) { item -> DoubleArray(CyvassePlacement.TILES.size) { t ->
            val p = (wins[item][t] + 2) / (plays[item][t] + 4)
            4.0 * kotlin.math.ln(p / (1 - p))
        } }
        for ((item, name) in listOf("KEEP", "MOUNTAIN", "FOREST").withIndex()) {
            log("$name = doubleArrayOf(${prefs[item].joinToString(", ") { "%.2f".format(it) }})")
            log("  win rates by row (river first): " + (0 until 3).joinToString(" | ") { row -> (0 until Cyvasse.W).joinToString(" ") { c -> val t = row * Cyvasse.W + c; "%.0f".format(100 * wins[item][t] / maxOf(1.0, plays[item][t])) } })
        }
        // check: learned layouts against random ones, each colour equally often
        val m = (System.getenv("CYVASSE_MATCH") ?: "120").toInt()
        val check = parallel(m) { i ->
            val gr = Random(90_000 + i); val side = i % 2
            val learned = { g: Cyvasse, sd: Int -> CyvassePlacement.place(g, sd, prefs, 0.5, gr); Unit }
            val start = if (side == 0) openStart({ learned(it, 0) }, { CyvassePlacement.place(it, 1, uniform, 1.0, gr) })
                else openStart({ CyvassePlacement.place(it, 0, uniform, 1.0, gr) }, { learned(it, 1) })
            val r = game(CyvasseEval.TRAINED, CyvasseEval.TRAINED, side, ai, gr, start = start, randomPlies = 0)
            if (r == side) 1.0 else if (r == Cyvasse.DRAWN) 0.5 else 0.0
        }
        log("learned layouts vs random layouts: ${"%.1f".format(100 * check.average())}% over $m games (${check.count { it == 1.0 }} won, ${check.count { it == 0.5 }} drawn, ${check.count { it == 0.0 }} lost)")
    }

    /**
     * A tournament of whole layouts (fortress, 3 mountains, 3 forests; slots count from the river: 0-9, 10-19, 20-29):
     * designed ones and random ones each play random layouts as both colours; the best few are played again at
     * length so a lucky start can't win it.
     */
    private fun layouts() {
        val ai = AiLevel(2, 100_000, noise = 6, quiescence = true)
        val uniform = Array(3) { DoubleArray(CyvassePlacement.TILES.size) }
        val designed = linkedMapOf(
            "river wall" to intArrayOf(24, 2, 5, 8, 13, 15, 17),
            "forest screen" to intArrayOf(25, 10, 19, 15, 3, 5, 7),
            "flank mountains" to intArrayOf(20, 0, 9, 1, 4, 5, 14),
            "king's citadel" to intArrayOf(25, 24, 26, 15, 14, 16, 5),
            "siege lanes" to intArrayOf(21, 0, 3, 9, 4, 5, 6),
            "forest keep" to intArrayOf(22, 11, 18, 5, 1, 8, 23),
        )
        val rnd = Random(808)
        val cands = LinkedHashMap<String, IntArray>(designed)
        for (i in 0 until 18) cands["random $i"] = CyvassePlacement.place(Cyvasse.openField(), 0, uniform, 1.0, rnd)
        fun play(layout: IntArray, games: Int, seed: Int): Double = parallel(games) { j ->
            val side = j % 2; val gr = Random(seed + j)
            val start = if (side == 0) openStart({ slots(it, 0, layout) }, { CyvassePlacement.place(it, 1, uniform, 1.0, gr) })
                else openStart({ CyvassePlacement.place(it, 0, uniform, 1.0, gr) }, { slots(it, 1, layout) })
            val r = game(CyvasseEval.TRAINED, CyvasseEval.TRAINED, side, ai, gr, start = start, randomPlies = 0)
            if (r == side) 1.0 else if (r == Cyvasse.DRAWN) 0.5 else 0.0
        }.average()
        val first = (System.getenv("CYVASSE_FIRST") ?: "80").toInt(); val second = (System.getenv("CYVASSE_SECOND") ?: "240").toInt()
        log("layouts: ${cands.size} layouts, $first games each vs random layouts, then the best 4 for $second more")
        val score = LinkedHashMap<String, Double>()
        for ((name, l) in cands) { score[name] = play(l, first, 11_000); log("  $name ${l.joinToString(",")}: ${"%.1f".format(100 * score[name]!!)}%") }
        for (name in score.entries.sortedByDescending { it.value }.take(4).map { it.key }) {
            val again = play(cands[name]!!, second, 55_000)
            log("RETEST $name ${cands[name]!!.joinToString(",")}: ${"%.1f".format(100 * again)}% over $second fresh games")
        }
    }
}
