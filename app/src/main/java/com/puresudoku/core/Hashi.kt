package com.puresudoku.core

import java.security.SecureRandom
import kotlin.random.Random

/** An island at (r, c) that needs [need] bridges in total. */
data class Island(val r: Int, val c: Int, val need: Int)

/** A possible bridge between islands [a] and [b] (indices, a < b) — they are aligned with nothing between. */
data class Edge(val a: Int, val b: Int, val horizontal: Boolean)

class HashiPuzzle(val w: Int, val h: Int, val islands: List<Island>, val solution: IntArray) {
    val edges: List<Edge> = HashiRules.edges(islands)
    /** Pairs of edge indices that would cross each other. */
    val crossings: List<IntArray> = HashiRules.crossings(islands, edges)
    val crossesOf: Array<IntArray> = Array(edges.size) { e -> crossings.filter { e in it }.map { if (it[0] == e) it[1] else it[0] }.toIntArray() }
    val totalBridges get() = solution.sum()
}

object HashiRules {
    data class Config(val w: Int, val h: Int, val islands: Int, val basePoints: Int, val parSeconds: Int)

    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(7, 7, 8, 10, 120)
        Difficulty.MEDIUM -> Config(8, 8, 12, 15, 240)
        Difficulty.HARD -> Config(9, 9, 16, 25, 420)
        Difficulty.EXPERT -> Config(10, 10, 21, 35, 660)
        Difficulty.MASTER -> Config(11, 11, 27, 50, 960)
        Difficulty.EXTREME -> Config(12, 12, 34, 75, 1320)
    }

    fun edges(islands: List<Island>): List<Edge> {
        val out = ArrayList<Edge>()
        for (i in islands.indices) {
            val a = islands[i]
            // nearest island to the right and below
            val right = islands.withIndex().filter { it.value.r == a.r && it.value.c > a.c }.minByOrNull { it.value.c }
            val down = islands.withIndex().filter { it.value.c == a.c && it.value.r > a.r }.minByOrNull { it.value.r }
            if (right != null) out += Edge(minOf(i, right.index), maxOf(i, right.index), true)
            if (down != null) out += Edge(minOf(i, down.index), maxOf(i, down.index), false)
        }
        return out
    }

    fun crossings(islands: List<Island>, edges: List<Edge>): List<IntArray> {
        val out = ArrayList<IntArray>()
        for (x in edges.indices) for (y in x + 1 until edges.size) {
            val e = edges[x]; val f = edges[y]
            if (e.horizontal == f.horizontal) continue
            val hz = if (e.horizontal) e else f
            val vt = if (e.horizontal) f else e
            val hr = islands[hz.a].r
            val hc1 = minOf(islands[hz.a].c, islands[hz.b].c); val hc2 = maxOf(islands[hz.a].c, islands[hz.b].c)
            val vc = islands[vt.a].c
            val vr1 = minOf(islands[vt.a].r, islands[vt.b].r); val vr2 = maxOf(islands[vt.a].r, islands[vt.b].r)
            if (vc in (hc1 + 1) until hc2 && hr in (vr1 + 1) until vr2) out += intArrayOf(x, y)
        }
        return out
    }

    fun connected(n: Int, edges: List<Edge>, counts: IntArray): Boolean {
        if (n == 0) return true
        val adj = Array(n) { ArrayList<Int>() }
        edges.forEachIndexed { k, e -> if (counts[k] > 0) { adj[e.a] += e.b; adj[e.b] += e.a } }
        val seen = BooleanArray(n); seen[0] = true
        val q = ArrayDeque(listOf(0)); var cnt = 1
        while (q.isNotEmpty()) for (y in adj[q.removeFirst()]) if (!seen[y]) { seen[y] = true; cnt++; q += y }
        return cnt == n
    }
}

/** Counts Hashi solutions (up to a limit) with constraint propagation. */
class HashiSolver(private val p: HashiPuzzle) {
    private val n = p.islands.size
    private val edgesOf = Array(n) { i -> p.edges.indices.filter { p.edges[it].a == i || p.edges[it].b == i }.toIntArray() }
    val solutions = ArrayList<IntArray>()
    private var nodes = 0L

    fun count(limit: Int = 2, budget: Long = 300_000): Int {
        solutions.clear(); nodes = 0
        return try { search(IntArray(p.edges.size) { -1 }, limit, budget) } catch (e: IllegalStateException) { -1 }
    }

    private fun maxFor(x: IntArray, e: Int, fixed: IntArray): Int {
        for (o in p.crossesOf[e]) if (x[o] > 0) return 0
        val ed = p.edges[e]
        return minOf(2, p.islands[ed.a].need - fixed[ed.a], p.islands[ed.b].need - fixed[ed.b]).coerceAtLeast(0)
    }

    /** Applies forced moves; returns false on contradiction. */
    private fun propagate(x: IntArray): Boolean {
        while (true) {
            var changed = false
            val fixed = IntArray(n)
            for (e in x.indices) if (x[e] > 0) { fixed[p.edges[e].a] += x[e]; fixed[p.edges[e].b] += x[e] }
            for (i in 0 until n) {
                val need = p.islands[i].need
                if (fixed[i] > need) return false
                var cap = 0
                for (e in edgesOf[i]) if (x[e] < 0) cap += maxFor(x, e, fixed)
                if (fixed[i] + cap < need) return false
                if (fixed[i] == need) {
                    for (e in edgesOf[i]) if (x[e] < 0) { x[e] = 0; changed = true }
                } else if (fixed[i] + cap == need) {
                    for (e in edgesOf[i]) if (x[e] < 0) { x[e] = maxFor(x, e, fixed); changed = true }
                }
                if (changed) break
            }
            if (!changed) return true
        }
    }

    private fun search(x: IntArray, limit: Int, budget: Long): Int {
        if (++nodes > budget) throw IllegalStateException("budget")
        if (!propagate(x)) return 0
        val open = x.indices.filter { x[it] < 0 }
        if (open.isEmpty()) {
            if (!HashiRules.connected(n, p.edges, x)) return 0
            for (i in 0 until n) if (edgesOf[i].sumOf { x[it] } != p.islands[i].need) return 0
            solutions += x.copyOf()
            return 1
        }
        val fixed = IntArray(n)
        for (e in x.indices) if (x[e] > 0) { fixed[p.edges[e].a] += x[e]; fixed[p.edges[e].b] += x[e] }
        val e = open.minBy { maxFor(x, it, fixed) }
        var total = 0
        for (v in maxFor(x, e, fixed) downTo 0) {
            val y = x.copyOf(); y[e] = v
            total += search(y, limit - total, budget)
            if (total >= limit) return total
        }
        return total
    }
}

/**
 * Hashi generator: grows a connected network by repeatedly sending a bridge
 * from an existing island to a new one, adds a few extra bridges to make
 * loops, then keeps only puzzles the solver proves have one answer.
 */
class HashiGenerator(seed: Long = SecureRandom().nextLong() xor System.nanoTime()) {
    private val rng = Random(seed)

    fun generate(d: Difficulty): HashiPuzzle {
        val cfg = HashiRules.config(d)
        var fallback: HashiPuzzle? = null
        repeat(300) {
            val p = attempt(cfg) ?: return@repeat
            if (p.islands.size < cfg.islands * 3 / 4) return@repeat
            val c = HashiSolver(p).count(2)
            if (c == 1) {
                if (p.islands.size >= cfg.islands - 1) return p
                if (fallback == null || p.islands.size > fallback!!.islands.size) fallback = p
            }
        }
        return fallback ?: generate(d)
    }

    private fun attempt(cfg: HashiRules.Config): HashiPuzzle? {
        val w = cfg.w; val h = cfg.h
        val islandAt = IntArray(w * h) { -1 }
        val occupied = IntArray(w * h) // 0 free, 1 horizontal bridge, 2 vertical bridge
        val isl = ArrayList<IntArray>() // r, c
        val bridges = HashMap<Pair<Int, Int>, Int>()

        fun free(r: Int, c: Int) = r in 0 until h && c in 0 until w && islandAt[r * w + c] < 0
        fun nearIsland(r: Int, c: Int, except: Int): Boolean {
            for ((dr, dc) in listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)) {
                val rr = r + dr; val cc = c + dc
                if (rr in 0 until h && cc in 0 until w && islandAt[rr * w + cc] >= 0 && islandAt[rr * w + cc] != except) return true
            }
            return false
        }

        val r0 = rng.nextInt(h); val c0 = rng.nextInt(w)
        islandAt[r0 * w + c0] = 0; isl += intArrayOf(r0, c0)
        var tries = 0
        while (isl.size < cfg.islands && tries++ < 2000) {
            val from = rng.nextInt(isl.size)
            val (fr, fc) = isl[from]
            val (dr, dc) = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)[rng.nextInt(4)]
            val dist = 2 + rng.nextInt(maxOf(1, minOf(w, h) / 2))
            val tr = fr + dr * dist; val tc = fc + dc * dist
            if (!free(tr, tc) || occupied[tr * w + tc] != 0 || nearIsland(tr, tc, from)) continue
            var clear = true
            for (k in 1 until dist) {
                val cell = (fr + dr * k) * w + (fc + dc * k)
                if (islandAt[cell] >= 0 || occupied[cell] != 0) { clear = false; break }
            }
            if (!clear) continue
            val idx = isl.size
            islandAt[tr * w + tc] = idx; isl += intArrayOf(tr, tc)
            for (k in 1 until dist) occupied[(fr + dr * k) * w + (fc + dc * k)] = if (dr == 0) 1 else 2
            bridges[minOf(from, idx) to maxOf(from, idx)] = if (rng.nextInt(100) < 45) 2 else 1
        }

        val islands0 = isl.map { Island(it[0], it[1], 0) }
        val edges = HashiRules.edges(islands0)
        val counts = IntArray(edges.size) { e -> bridges[edges[e].a to edges[e].b] ?: 0 }
        // Every grown bridge must be an edge between nearest neighbours.
        if (counts.sum() != bridges.values.sum()) return null
        // Extra bridges for loops, where they don't cross anything.
        val crossings = HashiRules.crossings(islands0, edges)
        for (e in edges.indices.shuffled(rng)) {
            if (counts[e] > 0 || rng.nextInt(100) >= 35) continue
            if (crossings.any { (it[0] == e && counts[it[1]] > 0) || (it[1] == e && counts[it[0]] > 0) }) continue
            counts[e] = 1 + rng.nextInt(2)
        }
        val need = IntArray(isl.size)
        edges.forEachIndexed { e, ed -> need[ed.a] += counts[e]; need[ed.b] += counts[e] }
        if (need.any { it == 0 || it > 8 }) return null
        val islands = isl.mapIndexed { i, rc -> Island(rc[0], rc[1], need[i]) }
        return HashiPuzzle(w, h, islands, counts)
    }
}
