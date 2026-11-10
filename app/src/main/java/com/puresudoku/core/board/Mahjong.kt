package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.math.abs
import kotlin.random.Random

/**
 * Mahjong solitaire. Tiles sit at (x, y, z) in half-tile units (a tile covers x..x+1, y..y+1).
 * A tile is free when nothing lies on it and its left or right side is open.
 * Faces: 0-8 dots, 9-17 bamboo, 18-26 characters, 27-30 winds, 31-33 dragons,
 * 34-37 flowers (any flower matches), 38-41 seasons (any season matches).
 */
class Mahjong(val tiles: List<Tile>) {
    class Tile(val id: Int, val x: Int, val y: Int, val z: Int, var face: Int, var removed: Boolean = false)

    data class MjConfig(val name: String, val shuffles: Int, val hints: Int)

    companion object {
        fun config(d: Difficulty) = when (d) {
            Difficulty.EASY -> MjConfig("Pagoda", 99, 99)
            Difficulty.MEDIUM -> MjConfig("Courtyard", 5, 5)
            Difficulty.HARD -> MjConfig("Temple", 3, 3)
            Difficulty.EXPERT -> MjConfig("Fortress", 2, 1)
            Difficulty.MASTER -> MjConfig("Turtle", 1, 0)
            Difficulty.EXTREME -> MjConfig("Great Turtle", 0, 0)
        }

        /** Layers as (x, y, w, h) rectangles in whole tiles. */
        private fun layout(d: Difficulty): List<IntArray> {
            val out = ArrayList<IntArray>()
            fun layer(z: Int, x0: Int, y0: Int, w: Int, h: Int, skip: (Int, Int) -> Boolean = { _, _ -> false }) {
                for (y in 0 until h) for (x in 0 until w) if (!skip(x, y)) out += intArrayOf((x0 + x) * 2, (y0 + y) * 2, z)
            }
            when (d) {
                Difficulty.EASY -> { layer(0, 0, 0, 6, 4); layer(1, 1, 1, 4, 2); layer(2, 2, 1, 2, 2) }
                Difficulty.MEDIUM -> { layer(0, 0, 0, 8, 6); layer(1, 1, 1, 6, 4) }
                Difficulty.HARD -> { layer(0, 0, 0, 10, 6); layer(1, 1, 1, 8, 4); layer(2, 3, 1, 4, 4) }
                Difficulty.EXPERT -> { layer(0, 0, 0, 10, 7); layer(1, 1, 1, 8, 5); layer(2, 3, 2, 4, 2); layer(3, 4, 2, 2, 1) }
                Difficulty.MASTER -> {
                    layer(0, 0, 0, 12, 8) { x, y -> (x == 0 || x == 11) && (y == 0 || y == 7) }
                    layer(1, 3, 1, 6, 6); layer(2, 4, 3, 4, 2)
                }
                Difficulty.EXTREME -> {
                    layer(0, 0, 0, 12, 8) { x, y -> (x == 0 || x == 11) && (y == 0 || y == 7) }
                    layer(1, 3, 1, 6, 6); layer(2, 4, 2, 4, 3); layer(3, 5, 3, 2, 2)
                }
            }
            return out
        }

        fun group(face: Int) = when (face) { in 34..37 -> 34; in 38..41 -> 38; else -> face }
        fun matches(a: Int, b: Int) = group(a) == group(b)

        fun newGame(d: Difficulty, rng: Random): Mahjong {
            val pos = layout(d)
            val tiles = pos.mapIndexed { i, p -> Tile(i, p[0], p[1], p[2], -1) }
            val g = Mahjong(tiles)
            // faces: as many distinct pairs as fit, from a full set
            val pairs = ArrayList<IntArray>()
            val full = ArrayList<Int>().apply { for (f in 0..33) repeat(4) { add(f) }; for (f in 34..41) add(f) }
            val grouped = full.groupBy { group(it) }.values.flatMap { it.chunked(2) }.map { it.toIntArray() }.shuffled(rng)
            for (p in grouped) { if (pairs.size * 2 >= tiles.size) break; pairs += p }
            g.deal(tiles, pairs, rng)
            return g
        }
    }

    /** Assigns face pairs so the layout can be cleared (reverse-removal simulation). */
    fun deal(target: List<Tile>, pairs: List<IntArray>, rng: Random) {
        repeat(200) {
            val left = target.toMutableList()
            val order = ArrayList<Pair<Tile, Tile>>()
            while (left.size >= 2) {
                val free = left.filter { freeAmong(it, left) }
                if (free.size < 2) break
                val a = free[rng.nextInt(free.size)]
                val rest = free - a
                val b = rest[rng.nextInt(rest.size)]
                order += a to b; left -= a; left -= b
            }
            if (left.isEmpty()) {
                val ps = pairs.shuffled(rng)
                order.forEachIndexed { k, (a, b) -> a.face = ps[k][0]; b.face = ps[k][1] }
                return
            }
        }
        // fallback (should not happen): plain random
        val faces = pairs.flatMap { it.toList() }.shuffled(rng)
        target.forEachIndexed { k, t -> t.face = faces[k] }
    }

    private fun overlaps(a: Tile, b: Tile) = abs(a.x - b.x) < 2 && abs(a.y - b.y) < 2

    private fun freeAmong(t: Tile, set: List<Tile>): Boolean {
        var leftBlocked = false; var rightBlocked = false
        for (o in set) {
            if (o === t) continue
            if (o.z == t.z + 1 && overlaps(o, t)) return false
            if (o.z == t.z && abs(o.y - t.y) < 2) {
                if (o.x == t.x - 2) leftBlocked = true
                if (o.x == t.x + 2) rightBlocked = true
            }
        }
        return !(leftBlocked && rightBlocked)
    }

    val remaining get() = tiles.filter { !it.removed }
    fun isFree(t: Tile) = !t.removed && freeAmong(t, remaining)

    fun hintPair(): Pair<Tile, Tile>? {
        val free = remaining.filter { freeAmong(it, remaining) }
        for (i in free.indices) for (j in i + 1 until free.size) if (matches(free[i].face, free[j].face)) return free[i] to free[j]
        return null
    }

    val cleared get() = tiles.all { it.removed }
    val stuck get() = !cleared && hintPair() == null

    fun remove(a: Tile, b: Tile) { a.removed = true; b.removed = true }

    /** Re-deals the remaining faces onto the remaining tiles, solvably. */
    fun shuffle(rng: Random) {
        val left = remaining
        val faces = left.map { it.face }.groupBy { group(it) }.values.flatMap { it.chunked(2) }.map { it.toIntArray() }
        deal(left, faces, rng)
    }

    val width get() = tiles.maxOf { it.x } + 2
    val height get() = tiles.maxOf { it.y } + 2
}
