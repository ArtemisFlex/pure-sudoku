package com.puresudoku.core.words

import com.puresudoku.core.Difficulty
import kotlin.random.Random

// =====================================================================
// Crossword
// =====================================================================

class CrosswordEntry(val number: Int, val row: Int, val col: Int, val across: Boolean, val answer: String, val clue: String) {
    fun cells(w: Int): IntArray = IntArray(answer.length) { k -> if (across) row * w + col + k else (row + k) * w + col }
}

/** [solution] holds a letter per cell, or ' ' for a black square. */
class CrosswordPuzzle(val width: Int, val height: Int, val solution: CharArray, val entries: List<CrosswordEntry>) {
    val numberAt: IntArray = IntArray(width * height).also { a -> entries.forEach { e -> a[e.row * width + e.col] = e.number } }
    fun isOpen(i: Int) = solution[i] != ' '
}

object CrosswordRules {
    data class Config(val size: Int, val minZipf: Int, val words: Int, val maxLen: Int, val basePoints: Int, val parSeconds: Int)

    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(7, 45, 7, 6, 10, 240)
        Difficulty.MEDIUM -> Config(9, 42, 11, 8, 12, 420)
        Difficulty.HARD -> Config(11, 39, 16, 9, 15, 660)
        Difficulty.EXPERT -> Config(12, 36, 21, 10, 20, 900)
        Difficulty.MASTER -> Config(13, 33, 26, 11, 25, 1200)
        Difficulty.EXTREME -> Config(14, 30, 32, 11, 35, 1500)
    }

    /**
     * Free-form crossword: start with one word across the middle, then keep
     * adding the word that crosses the most letters. New letters may only
     * touch other words where they cross, so no accidental words appear.
     */
    fun generate(d: Difficulty, rng: Random): CrosswordPuzzle {
        val cfg = config(d)
        var best: CrosswordPuzzle? = null
        repeat(12) {
            val p = attempt(cfg, rng)
            if (best == null || p.entries.size > best!!.entries.size) best = p
            if (p.entries.size >= cfg.words) return p
        }
        return best!!
    }

    private fun attempt(cfg: Config, rng: Random): CrosswordPuzzle {
        val n = cfg.size + 4
        val pool = WordData.clues.keys
            .filter { it.length in 3..cfg.maxLen && (WordData.lexicon[it]?.zipf ?: 0) >= cfg.minZipf }
            .shuffled(rng)
        val g = CharArray(n * n) { ' ' }
        val placed = ArrayList<Triple<String, Int, Boolean>>() // word, start cell, across
        val used = HashSet<String>()

        fun fits(word: String, r: Int, c: Int, across: Boolean): Int {
            val dr = if (across) 0 else 1; val dc = if (across) 1 else 0
            val er = r + dr * (word.length - 1); val ec = c + dc * (word.length - 1)
            if (r < 0 || c < 0 || er >= n || ec >= n) return -1
            val br = r - dr; val bc = c - dc
            if (br >= 0 && bc >= 0 && g[br * n + bc] != ' ') return -1
            val ar = er + dr; val ac = ec + dc
            if (ar < n && ac < n && g[ar * n + ac] != ' ') return -1
            var crossings = 0
            for (k in word.indices) {
                val rr = r + dr * k; val cc = c + dc * k
                val cur = g[rr * n + cc]
                if (cur != ' ') {
                    if (cur != word[k]) return -1
                    crossings++
                } else {
                    // new letters must not sit beside other letters (sideways)
                    val s1r = rr + dc; val s1c = cc + dr; val s2r = rr - dc; val s2c = cc - dr
                    if (s1r in 0 until n && s1c in 0 until n && g[s1r * n + s1c] != ' ') return -1
                    if (s2r in 0 until n && s2c in 0 until n && g[s2r * n + s2c] != ' ') return -1
                }
            }
            if (crossings == word.length) return -1
            return crossings
        }

        fun place(word: String, r: Int, c: Int, across: Boolean) {
            for (k in word.indices) {
                val rr = r + if (across) 0 else k; val cc = c + if (across) k else 0
                g[rr * n + cc] = word[k]
            }
            placed += Triple(word, r * n + c, across); used += word
        }

        val first = pool.firstOrNull { it.length >= minOf(cfg.maxLen, cfg.size - 1) - 1 } ?: pool.first()
        place(first, n / 2, (n - first.length) / 2, true)

        var misses = 0
        for (word in pool) {
            if (placed.size >= cfg.words || misses > 3000) break
            if (word in used || used.any { WordData.relatedForms(it, word) }) continue
            var bestScore = 0
            val options = ArrayList<Triple<Int, Int, Boolean>>()
            for (k in word.indices) for (i in g.indices) {
                if (g[i] != word[k]) continue
                val r = i / n; val c = i % n
                for (across in booleanArrayOf(true, false)) {
                    val sr = if (across) r else r - k
                    val sc = if (across) c - k else c
                    val sco = fits(word, sr, sc, across)
                    if (sco <= 0) continue
                    // keep within the target size
                    val minR = minOf(sr, placed.minOf { it.second / n }); val minC = minOf(sc, placed.minOf { it.second % n })
                    val maxR = maxOf(sr + if (across) 0 else word.length - 1, placed.maxOf { endR(it, n) })
                    val maxC = maxOf(sc + if (across) word.length - 1 else 0, placed.maxOf { endC(it, n) })
                    if (maxR - minR >= cfg.size || maxC - minC >= cfg.size) continue
                    if (sco > bestScore) { bestScore = sco; options.clear() }
                    if (sco == bestScore) options += Triple(sr, sc, across)
                }
            }
            if (options.isEmpty()) { misses++; continue }
            val (r, c, a) = options[rng.nextInt(options.size)]
            place(word, r, c, a)
        }

        // crop to the used area
        val minR = placed.minOf { it.second / n }; val minC = placed.minOf { it.second % n }
        val maxR = placed.maxOf { endR(it, n) }; val maxC = placed.maxOf { endC(it, n) }
        val w = maxC - minC + 1; val h = maxR - minR + 1
        val sol = CharArray(w * h) { i -> g[(minR + i / w) * n + (minC + i % w)] }
        val starts = placed.map { (word, s, a) -> Triple(word, (s / n - minR) * w + (s % n - minC), a) }
            .sortedWith(compareBy({ it.second }, { if (it.third) 0 else 1 }))
        val numbers = HashMap<Int, Int>()
        var next = 1
        val entries = starts.map { (word, cell, across) ->
            val num = numbers.getOrPut(cell) { next++ }
            CrosswordEntry(num, cell / w, cell % w, across, word, WordData.clues.getValue(word))
        }
        return CrosswordPuzzle(w, h, sol, entries)
    }

    private fun endR(t: Triple<String, Int, Boolean>, n: Int) = t.second / n + if (t.third) 0 else t.first.length - 1
    private fun endC(t: Triple<String, Int, Boolean>, n: Int) = t.second % n + if (t.third) t.first.length - 1 else 0
}

// =====================================================================
// Word Search
// =====================================================================

class WordSearchPlacement(val word: String, val row: Int, val col: Int, val dr: Int, val dc: Int)
class WordSearchPuzzle(val size: Int, val letters: CharArray, val theme: String, val words: List<WordSearchPlacement>)

object WordSearchRules {
    data class Config(val size: Int, val words: Int, val dirs: List<Pair<Int, Int>>, val minZipf: Int, val basePoints: Int, val parSeconds: Int)

    private val RIGHT_DOWN = listOf(0 to 1, 1 to 0)
    private val PLUS_DIAG = RIGHT_DOWN + listOf(1 to 1)
    private val FORWARD = PLUS_DIAG + listOf(-1 to 1)
    private val ALL = FORWARD + listOf(0 to -1, -1 to 0, -1 to -1, 1 to -1)

    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(7, 6, RIGHT_DOWN, 40, 10, 180)
        Difficulty.MEDIUM -> Config(9, 8, PLUS_DIAG, 38, 12, 300)
        Difficulty.HARD -> Config(10, 10, FORWARD, 36, 15, 420)
        Difficulty.EXPERT -> Config(11, 12, ALL, 35, 20, 540)
        Difficulty.MASTER -> Config(12, 14, ALL, 33, 25, 660)
        Difficulty.EXTREME -> Config(13, 16, ALL, 30, 35, 780)
    }

    private const val LETTER_WEIGHTS = "eeeeeeeeeeeettttttttaaaaaaaaooooooooiiiiiiinnnnnnnsssssshhhhhhrrrrrrddddllllcccuuummwwffggyyppbbvkjxqz"

    fun generate(d: Difficulty, rng: Random): WordSearchPuzzle {
        val cfg = config(d)
        while (true) {
            val (theme, pool) = WordData.themes.entries.shuffled(rng).first().let { e ->
                e.key to e.value.filter { it.length in 3..cfg.size && (WordData.lexicon[it]?.zipf ?: 0) >= cfg.minZipf }.shuffled(rng)
            }
            if (pool.size < cfg.words) continue
            val n = cfg.size
            val g = CharArray(n * n) { ' ' }
            val placed = ArrayList<WordSearchPlacement>()
            for (word in pool) {
                if (placed.size >= cfg.words) break
                if (placed.any { WordData.relatedForms(it.word, word) }) continue
                repeat(80) {
                    if (placed.lastOrNull()?.word == word) return@repeat
                    val (dr, dc) = cfg.dirs[rng.nextInt(cfg.dirs.size)]
                    val r = rng.nextInt(n); val c = rng.nextInt(n)
                    val er = r + dr * (word.length - 1); val ec = c + dc * (word.length - 1)
                    if (er !in 0 until n || ec !in 0 until n) return@repeat
                    var ok = true
                    for (k in word.indices) {
                        val ch = g[(r + dr * k) * n + (c + dc * k)]
                        if (ch != ' ' && ch != word[k]) { ok = false; break }
                    }
                    if (!ok) return@repeat
                    for (k in word.indices) g[(r + dr * k) * n + (c + dc * k)] = word[k]
                    placed += WordSearchPlacement(word, r, c, dr, dc)
                }
            }
            if (placed.size < cfg.words) continue
            for (i in g.indices) if (g[i] == ' ') g[i] = LETTER_WEIGHTS[rng.nextInt(LETTER_WEIGHTS.length)]
            return WordSearchPuzzle(n, g, theme, placed)
        }
    }
}

// =====================================================================
// Word Ladder
// =====================================================================

class LadderPuzzle(val start: String, val end: String, val par: Int, val example: List<String>)

object LadderRules {
    data class Config(val length: Int, val minSteps: Int, val maxSteps: Int, val nodeZipf: Int, val basePoints: Int, val parSeconds: Int)

    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(3, 3, 4, 46, 10, 120)
        Difficulty.MEDIUM -> Config(4, 3, 4, 40, 15, 180)
        Difficulty.HARD -> Config(4, 5, 6, 38, 25, 300)
        Difficulty.EXPERT -> Config(4, 6, 8, 35, 35, 420)
        Difficulty.MASTER -> Config(5, 5, 7, 36, 50, 540)
        Difficulty.EXTREME -> Config(5, 7, 10, 33, 75, 720)
    }

    /** Words accepted as rungs: anything in the dictionary of that length. */
    fun valid(length: Int): Set<String> = WordData.byLength[length].orEmpty().map { it.word }.toHashSet()

    fun neighbours(w: String, dict: Set<String>): List<String> {
        val out = ArrayList<String>()
        val c = w.toCharArray()
        for (i in c.indices) {
            val orig = c[i]
            for (ch in 'a'..'z') {
                if (ch == orig) continue
                c[i] = ch
                val s = String(c)
                if (s in dict) out += s
            }
            c[i] = orig
        }
        return out
    }

    /** Shortest ladder from [a] to [b] through [dict], or null. */
    fun shortest(a: String, b: String, dict: Set<String>): List<String>? {
        val prev = HashMap<String, String>()
        val q = ArrayDeque(listOf(a)); prev[a] = a
        while (q.isNotEmpty()) {
            val x = q.removeFirst()
            if (x == b) {
                val path = ArrayList<String>(); var cur = b
                while (cur != a) { path += cur; cur = prev.getValue(cur) }
                path += a
                return path.reversed()
            }
            for (y in neighbours(x, dict)) if (y !in prev) { prev[y] = x; q += y }
        }
        return null
    }

    fun generate(d: Difficulty, rng: Random): LadderPuzzle {
        val cfg = config(d)
        val all = valid(cfg.length)
        val common = WordData.byLength[cfg.length].orEmpty().filter { it.zipf >= cfg.nodeZipf }.map { it.word }.toHashSet()
        while (true) {
            val start = common.random(rng)
            // walk outward through common words, pick an end at the right distance
            val dist = HashMap<String, Int>(); dist[start] = 0
            val q = ArrayDeque(listOf(start))
            while (q.isNotEmpty()) {
                val x = q.removeFirst()
                val dx = dist.getValue(x)
                if (dx >= cfg.maxSteps) continue
                for (y in neighbours(x, common)) if (y !in dist) { dist[y] = dx + 1; q += y }
            }
            val ends = dist.filter { it.value in cfg.minSteps..cfg.maxSteps }.keys.toList()
            if (ends.isEmpty()) continue
            val end = ends.random(rng)
            if (WordData.relatedForms(start, end)) continue
            val best = shortest(start, end, all) ?: continue
            if (best.size - 1 < cfg.minSteps) continue
            val example = shortest(start, end, common) ?: best
            return LadderPuzzle(start, end, best.size - 1, example)
        }
    }
}

// =====================================================================
// Word Wheel
// =====================================================================

class WheelPuzzle(val letters: String, val center: Char, val answers: List<String>, val pangrams: Set<String>, val goal: Int)

object WheelRules {
    data class Config(val goalPct: Int, val answerZipf: Int, val basePoints: Int, val parSeconds: Int)

    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(25, 33, 10, 300)
        Difficulty.MEDIUM -> Config(35, 32, 12, 420)
        Difficulty.HARD -> Config(45, 31, 15, 540)
        Difficulty.EXPERT -> Config(55, 30, 20, 660)
        Difficulty.MASTER -> Config(65, 29, 25, 780)
        Difficulty.EXTREME -> Config(80, 28, 35, 900)
    }

    fun answersFor(letters: String, center: Char, zipf: Int): List<String> {
        val set = letters.toSet()
        return WordData.lexicon.values.asSequence()
            .filter { it.word.length >= 4 && it.zipf >= zipf && center in it.word && it.word.all { c -> c in set } }
            .map { it.word }.sorted().toList()
    }

    fun generate(d: Difficulty, rng: Random): WheelPuzzle {
        val cfg = config(d)
        val seeds = WordData.lexicon.values.filter { it.zipf >= 32 && it.word.toSet().size == 7 && 's' !in it.word }
        while (true) {
            val seed = seeds.random(rng).word
            val letters = seed.toSet().sorted().joinToString("")
            val center = letters.filter { it !in "jqxz" }.random(rng)
            val answers = answersFor(letters, center, cfg.answerZipf)
            if (answers.size !in 18..70) continue
            val pangrams = answers.filter { it.toSet().size == 7 }.toSet()
            val goal = maxOf(5, answers.size * cfg.goalPct / 100)
            // outer letters in a random ring order
            val outer = letters.filter { it != center }.toList().shuffled(rng).joinToString("")
            return WheelPuzzle(outer, center, answers, pangrams, goal)
        }
    }

    /** Points for a found word: longer words score more, pangrams most. */
    fun points(w: String, pangram: Boolean) = (if (w.length == 4) 1 else w.length) + if (pangram) 7 else 0
}
