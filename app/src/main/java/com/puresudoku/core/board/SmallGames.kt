package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.math.abs
import kotlin.random.Random

// =====================================================================  Tic-tac-toe

/** 3x3 board; 1 = X (you), 2 = O (computer). */
class TicTacToe(val cells: IntArray = IntArray(9), var turn: Int = 1) : Searchable<Int> {
    private val hist = ArrayList<Int>()

    companion object {
        val LINES = arrayOf(intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8), intArrayOf(0, 3, 6), intArrayOf(1, 4, 7),
            intArrayOf(2, 5, 8), intArrayOf(0, 4, 8), intArrayOf(2, 4, 6))
        /** Rounds in a match, and how well O plays. */
        fun level(d: Difficulty) = when (d) {
            Difficulty.EASY -> AiLevel(1, 200, blunder = 0.55)
            Difficulty.MEDIUM -> AiLevel(2, 200, blunder = 0.35)
            Difficulty.HARD -> AiLevel(3, 300, blunder = 0.2)
            Difficulty.EXPERT -> AiLevel(9, 400, blunder = 0.1)
            Difficulty.MASTER -> AiLevel(9, 400, blunder = 0.04)
            Difficulty.EXTREME -> AiLevel(9, 400)
        }
    }

    fun winLine(): IntArray? = LINES.firstOrNull { l -> cells[l[0]] != 0 && cells[l[0]] == cells[l[1]] && cells[l[1]] == cells[l[2]] }
    val winner get() = winLine()?.let { cells[it[0]] } ?: 0
    val full get() = cells.none { it == 0 }
    val over get() = winner != 0 || full

    override fun legalMoves() = if (over) emptyList() else (0..8).filter { cells[it] == 0 }
    override fun make(m: Int) { cells[m] = turn; hist += m; turn = 3 - turn }
    override fun unmake() { val m = hist.removeAt(hist.lastIndex); cells[m] = 0; turn = 3 - turn }
    override fun evaluate() = 0
    override fun terminal(): Int? = when {
        winner != 0 -> if (winner == turn) WIN else -WIN
        full -> 0
        else -> null
    }
    override fun orderKey(m: Int) = if (m == 4) 2 else if (m % 2 == 0) 1 else 0
}

// =====================================================================  Gomoku

/** Freestyle gomoku: five or more in a row wins. 1 = black (you, first), 2 = white. */
class Gomoku(val n: Int = 15) {
    val cells = IntArray(n * n)
    var turn = 1
    val moves = ArrayList<Int>()
    var winLine: List<Int>? = null

    val over get() = winLine != null || moves.size == n * n

    fun play(i: Int): Boolean {
        if (cells[i] != 0 || over) return false
        cells[i] = turn; moves += i
        winLine = fiveThrough(i)
        turn = 3 - turn
        return true
    }

    fun undo() { val i = moves.removeAt(moves.lastIndex); cells[i] = 0; winLine = null; turn = 3 - turn }

    private fun fiveThrough(i: Int): List<Int>? {
        val p = cells[i]; val r = i / n; val c = i % n
        for ((dr, dc) in DIRS) {
            val line = mutableListOf(i)
            var k = 1; while (at(r + dr * k, c + dc * k) == p) { line += (r + dr * k) * n + c + dc * k; k++ }
            k = 1; while (at(r - dr * k, c - dc * k) == p) { line += (r - dr * k) * n + c - dc * k; k++ }
            if (line.size >= 5) return line
        }
        return null
    }

    private fun at(r: Int, c: Int) = if (r in 0 until n && c in 0 until n) cells[r * n + c] else -1

    /** Pattern score of placing [p] at empty [i]. */
    private fun potential(i: Int, p: Int): Int {
        val r = i / n; val c = i % n
        var total = 0
        var fours = 0; var openThrees = 0
        for ((dr, dc) in DIRS) {
            var count = 1; var openEnds = 0
            var k = 1; while (at(r + dr * k, c + dc * k) == p) { count++; k++ }
            if (at(r + dr * k, c + dc * k) == 0) openEnds++
            k = 1; while (at(r - dr * k, c - dc * k) == p) { count++; k++ }
            if (at(r - dr * k, c - dc * k) == 0) openEnds++
            total += when {
                count >= 5 -> 1_000_000
                count == 4 && openEnds == 2 -> 100_000
                count == 4 && openEnds == 1 -> { fours++; 12_000 }
                count == 3 && openEnds == 2 -> { openThrees++; 8_000 }
                count == 3 && openEnds == 1 -> 600
                count == 2 && openEnds == 2 -> 500
                count == 2 && openEnds == 1 -> 60
                count == 1 && openEnds == 2 -> 20
                else -> 1
            }
        }
        if (fours >= 2 || (fours >= 1 && openThrees >= 1)) total += 90_000
        if (openThrees >= 2) total += 40_000
        return total
    }

    private fun candidates(): List<Int> {
        if (moves.isEmpty()) return listOf((n / 2) * n + n / 2)
        val out = LinkedHashSet<Int>()
        for (m in moves) {
            val r = m / n; val c = m % n
            for (dr in -2..2) for (dc in -2..2) {
                val rr = r + dr; val cc = c + dc
                if (rr in 0 until n && cc in 0 until n && cells[rr * n + cc] == 0) out += rr * n + cc
            }
        }
        return out.toList()
    }

    private fun score(i: Int, me: Int) = potential(i, me) * 11 / 10 + potential(i, 3 - me)

    /** Computer move: win, block a win, make/stop open fours, then the best attack + defence. */
    fun aiMove(level: GomokuLevel, rng: Random): Int {
        val me = turn; val op = 3 - me
        val cands = candidates()
        if (rng.nextDouble() < level.blunder) return cands[rng.nextInt(cands.size)]
        val att = cands.associateWith { potential(it, me) }
        val def = cands.associateWith { potential(it, op) }
        att.entries.firstOrNull { it.value >= 1_000_000 }?.let { return it.key }
        def.entries.filter { it.value >= 1_000_000 }.maxByOrNull { att[it.key]!! }?.let { return it.key }
        if (level.noise < 1000) {
            att.entries.filter { it.value >= 100_000 }.maxByOrNull { it.value }?.let { return it.key }
            def.entries.filter { it.value >= 100_000 }.maxByOrNull { att[it.key]!! + it.value }?.let { return it.key }
        }
        val ranked = cands.map { it to maxOf(att[it]!! * 12 / 10, def[it]!!) + (att[it]!! + def[it]!!) / 4 + rng.nextInt(level.noise + 1) }
            .sortedByDescending { it.second }
        if (!level.look) return ranked[0].first
        var best = ranked[0].first; var bestV = Int.MIN_VALUE
        for ((m, s) in ranked.take(10)) {
            play(m)
            val reply = candidates().maxOfOrNull { maxOf(potential(it, op), potential(it, me) * 9 / 10) } ?: 0
            undo()
            val v = s - reply * 6 / 10
            if (v > bestV) { bestV = v; best = m }
        }
        return best
    }

    data class GomokuLevel(val noise: Int, val blunder: Double, val look: Boolean)

    companion object {
        val DIRS = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        fun level(d: Difficulty) = when (d) {
            Difficulty.EASY -> GomokuLevel(9000, 0.25, false)
            Difficulty.MEDIUM -> GomokuLevel(3000, 0.1, false)
            Difficulty.HARD -> GomokuLevel(800, 0.03, false)
            Difficulty.EXPERT -> GomokuLevel(300, 0.0, true)
            Difficulty.MASTER -> GomokuLevel(60, 0.0, true)
            Difficulty.EXTREME -> GomokuLevel(0, 0.0, true)
        }
    }
}

// =====================================================================  Mancala (Kalah)

/**
 * Kalah: pits 0..5 are yours (store 6), 7..12 the computer's (store 13).
 * Sow anticlockwise, skip the opponent's store, last seed in your store = go again,
 * last seed in your empty pit = capture it and the opposite pit.
 */
class Mancala(val pits: IntArray = IntArray(14) { if (it == 6 || it == 13) 0 else 4 }, var turn: Int = 0) : Searchable<Int> {
    private val hist = ArrayList<Pair<IntArray, Int>>()

    companion object {
        fun level(d: Difficulty) = when (d) {
            Difficulty.EASY -> AiLevel(1, 200, noise = 6, blunder = 0.3)
            Difficulty.MEDIUM -> AiLevel(2, 300, noise = 3, blunder = 0.12)
            Difficulty.HARD -> AiLevel(4, 400, noise = 1)
            Difficulty.EXPERT -> AiLevel(6, 600)
            Difficulty.MASTER -> AiLevel(9, 900)
            Difficulty.EXTREME -> AiLevel(14, 1500)
        }
        fun store(p: Int) = if (p == 0) 6 else 13
        fun pitsOf(p: Int) = if (p == 0) 0..5 else 7..12
    }

    val over get() = (0..5).all { pits[it] == 0 } || (7..12).all { pits[it] == 0 }

    /** Sows from pit [i]; returns the pits touched in order (for animation). */
    fun sow(i: Int): List<Int> {
        val p = turn
        var seeds = pits[i]; pits[i] = 0
        var k = i
        val path = ArrayList<Int>()
        while (seeds > 0) {
            k = (k + 1) % 14
            if (k == store(1 - p)) continue
            pits[k]++; seeds--; path += k
        }
        if (k in pitsOf(p) && pits[k] == 1 && pits[12 - k] > 0) {
            pits[store(p)] += pits[k] + pits[12 - k]; pits[k] = 0; pits[12 - k] = 0
        }
        if (over) {
            for (j in 0..5) { pits[6] += pits[j]; pits[j] = 0 }
            for (j in 7..12) { pits[13] += pits[j]; pits[j] = 0 }
        } else if (k != store(p)) turn = 1 - p
        return path
    }

    override fun legalMoves() = if (over) emptyList() else pitsOf(turn).filter { pits[it] > 0 }
    override fun make(m: Int) { hist += pits.copyOf() to turn; sow(m) }
    override fun unmake() { val (p, t) = hist.removeAt(hist.lastIndex); p.copyInto(pits); turn = t }
    override fun evaluate(): Int {
        val mine = pits[store(turn)] - pits[store(1 - turn)]
        val side = pitsOf(turn).sumOf { pits[it] } - pitsOf(1 - turn).sumOf { pits[it] }
        return mine * 100 + side * 20
    }
    override fun terminal(): Int? {
        if (!over) return null
        val d = pits[store(turn)] - pits[store(1 - turn)]
        return if (d > 0) WIN - 100 + d else if (d < 0) -WIN + 100 + d else 0
    }
    override fun orderKey(m: Int): Int {
        val land = (m + pits[m]) % 13
        return if (land == store(turn) - (if (turn == 0) 0 else 0)) 10 else 0
    }
}

// =====================================================================  Nine Men's Morris

/**
 * Points 0..23 on three nested squares. You are 1 (white), the computer 2 (black).
 * Place 9 each, then move along lines; with 3 left you may fly. A mill (3 in a row) removes
 * an enemy piece not in a mill (unless all are).
 */
class Morris : Searchable<Morris.Move> {
    data class Move(val from: Int, val to: Int, val remove: Int)

    val board = IntArray(24)
    val inHand = intArrayOf(0, 9, 9)
    var turn = 1
    var quiet = 0  // moves since the last mill, for the draw rule
    private val hist = ArrayList<IntArray>()

    companion object {
        val ADJ: Array<IntArray> = arrayOf(
            intArrayOf(1, 9), intArrayOf(0, 2, 4), intArrayOf(1, 14), intArrayOf(4, 10), intArrayOf(1, 3, 5, 7), intArrayOf(4, 13),
            intArrayOf(7, 11), intArrayOf(4, 6, 8), intArrayOf(7, 12), intArrayOf(0, 10, 21), intArrayOf(3, 9, 11, 18), intArrayOf(6, 10, 15),
            intArrayOf(8, 13, 17), intArrayOf(5, 12, 14, 20), intArrayOf(2, 13, 23), intArrayOf(11, 16), intArrayOf(15, 17, 19), intArrayOf(12, 16),
            intArrayOf(10, 19), intArrayOf(16, 18, 20, 22), intArrayOf(13, 19), intArrayOf(9, 22), intArrayOf(19, 21, 23), intArrayOf(14, 22),
        )
        val MILLS: Array<IntArray> = arrayOf(
            intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8), intArrayOf(9, 10, 11), intArrayOf(12, 13, 14), intArrayOf(15, 16, 17),
            intArrayOf(18, 19, 20), intArrayOf(21, 22, 23), intArrayOf(0, 9, 21), intArrayOf(3, 10, 18), intArrayOf(6, 11, 15), intArrayOf(1, 4, 7),
            intArrayOf(16, 19, 22), intArrayOf(8, 12, 17), intArrayOf(5, 13, 20), intArrayOf(2, 14, 23),
        )
        /** Grid coordinates (0..6) of each point, for drawing. */
        val XY: Array<IntArray> = arrayOf(
            intArrayOf(0, 0), intArrayOf(3, 0), intArrayOf(6, 0), intArrayOf(1, 1), intArrayOf(3, 1), intArrayOf(5, 1), intArrayOf(2, 2), intArrayOf(3, 2), intArrayOf(4, 2),
            intArrayOf(0, 3), intArrayOf(1, 3), intArrayOf(2, 3), intArrayOf(4, 3), intArrayOf(5, 3), intArrayOf(6, 3),
            intArrayOf(2, 4), intArrayOf(3, 4), intArrayOf(4, 4), intArrayOf(1, 5), intArrayOf(3, 5), intArrayOf(5, 5), intArrayOf(0, 6), intArrayOf(3, 6), intArrayOf(6, 6),
        )
        fun level(d: Difficulty) = when (d) {
            Difficulty.EASY -> AiLevel(1, 300, noise = 80, blunder = 0.25)
            Difficulty.MEDIUM -> AiLevel(2, 400, noise = 40, blunder = 0.1)
            Difficulty.HARD -> AiLevel(3, 600, noise = 10)
            Difficulty.EXPERT -> AiLevel(4, 900)
            Difficulty.MASTER -> AiLevel(5, 1300)
            Difficulty.EXTREME -> AiLevel(7, 2000)
        }
    }

    fun count(p: Int) = board.count { it == p } + inHand[p]
    fun placing(p: Int) = inHand[p] > 0
    fun flying(p: Int) = !placing(p) && board.count { it == p } == 3

    fun inMill(i: Int, b: IntArray = board): Boolean {
        val p = b[i]; if (p == 0) return false
        return MILLS.any { m -> i in m && m.all { b[it] == p } }
    }

    private fun formsMill(to: Int, from: Int, p: Int): Boolean {
        val b = board.copyOf(); if (from >= 0) b[from] = 0; b[to] = p
        return inMill(to, b)
    }

    fun removable(p: Int): List<Int> {
        val enemy = (0..23).filter { board[it] == 3 - p }
        val free = enemy.filter { !inMill(it) }
        return free.ifEmpty { enemy }
    }

    /** Where the piece at [from] (or a new piece, from = -1) may go, ignoring removal. */
    fun targets(from: Int, p: Int = turn): List<Int> = when {
        placing(p) -> (0..23).filter { board[it] == 0 }
        from < 0 -> emptyList()
        flying(p) -> (0..23).filter { board[it] == 0 }
        else -> ADJ[from].filter { board[it] == 0 }
    }

    fun makesMill(from: Int, to: Int, p: Int = turn) = formsMill(to, from, p)

    val winner: Int
        get() {
            for (p in 1..2) {
                if (count(p) < 3) return 3 - p
                if (p == turn && legalMoves().isEmpty()) return 3 - p
            }
            return 0
        }
    val drawn get() = quiet >= 100

    override fun legalMoves(): List<Move> {
        val p = turn
        if (count(1) < 3 || count(2) < 3 || drawn) return emptyList()
        val out = ArrayList<Move>()
        val froms = if (placing(p)) listOf(-1) else (0..23).filter { board[it] == p }
        for (f in froms) for (t in targets(f, p)) {
            if (formsMill(t, f, p)) {
                val b = board.copyOf(); if (f >= 0) b[f] = 0; b[t] = p
                val enemy = (0..23).filter { b[it] == 3 - p }
                val free = enemy.filter { !inMill(it, b) }
                for (r in free.ifEmpty { enemy }) out += Move(f, t, r)
            } else out += Move(f, t, -1)
        }
        return out
    }

    override fun make(m: Move) {
        hist += intArrayOf(*board, inHand[1], inHand[2], turn, quiet)
        val p = turn
        if (m.from >= 0) board[m.from] = 0 else inHand[p]--
        board[m.to] = p
        if (m.remove >= 0) { board[m.remove] = 0; quiet = 0 } else quiet++
        turn = 3 - p
    }

    override fun unmake() {
        val s = hist.removeAt(hist.lastIndex)
        for (i in 0..23) board[i] = s[i]
        inHand[1] = s[24]; inHand[2] = s[25]; turn = s[26]; quiet = s[27]
    }

    override fun evaluate(): Int {
        val me = turn; val op = 3 - me
        var v = (count(me) - count(op)) * 1000
        v += (MILLS.count { m -> m.all { board[it] == me } } - MILLS.count { m -> m.all { board[it] == op } }) * 120
        if (!placing(me) && !flying(me)) v += (0..23).filter { board[it] == me }.sumOf { i -> ADJ[i].count { board[it] == 0 } } * 8
        if (!placing(op) && !flying(op)) v -= (0..23).filter { board[it] == op }.sumOf { i -> ADJ[i].count { board[it] == 0 } } * 8
        // two of a mill with the third empty
        for (m in MILLS) {
            val mine = m.count { board[it] == me }; val theirs = m.count { board[it] == op }; val empty = m.count { board[it] == 0 }
            if (mine == 2 && empty == 1) v += 60
            if (theirs == 2 && empty == 1) v -= 70
        }
        return v
    }

    override fun terminal(): Int? {
        if (drawn) return 0
        if (count(turn) < 3) return -WIN
        if (count(3 - turn) < 3) return WIN
        if (legalMoves().isEmpty()) return -WIN
        return null
    }

    override fun orderKey(m: Move) = if (m.remove >= 0) 10 else 0
    override fun noisyMoves() = emptyList<Move>()

    fun copyState(): IntArray = intArrayOf(*board, inHand[1], inHand[2], turn, quiet)
    fun loadState(s: IntArray) { for (i in 0..23) board[i] = s[i]; inHand[1] = s[24]; inHand[2] = s[25]; turn = s[26]; quiet = s[27] }
}

// =====================================================================  Checkers (American)

/**
 * 8x8 American checkers on the dark squares. Board index r*8+c. Pieces: 1 = your man, 2 = your king,
 * -1 = computer man, -2 = computer king. You move up (towards row 0). Captures are compulsory.
 */
class Checkers : Searchable<Checkers.Move> {
    /** A move as the list of squares visited, plus what it jumped. */
    data class Move(val path: List<Int>, val captured: List<Int>) {
        val from get() = path.first()
        val to get() = path.last()
    }

    val b = IntArray(64)
    var turn = 1   // 1 = you, -1 = computer
    var quiet = 0  // moves without a capture or man move
    private val hist = ArrayList<IntArray>()

    init {
        for (r in 0..7) for (c in 0..7) if ((r + c) % 2 == 1) {
            if (r <= 2) b[r * 8 + c] = -1
            if (r >= 5) b[r * 8 + c] = 1
        }
    }

    companion object {
        fun level(d: Difficulty) = when (d) {
            Difficulty.EASY -> AiLevel(1, 300, noise = 120, blunder = 0.2)
            Difficulty.MEDIUM -> AiLevel(2, 400, noise = 60, blunder = 0.08)
            Difficulty.HARD -> AiLevel(4, 700, noise = 15)
            Difficulty.EXPERT -> AiLevel(6, 1000)
            Difficulty.MASTER -> AiLevel(8, 1500)
            Difficulty.EXTREME -> AiLevel(12, 2500)
        }
    }

    private fun dirs(piece: Int): List<Int> = when (piece) {
        1 -> listOf(-9, -7)
        -1 -> listOf(7, 9)
        else -> listOf(-9, -7, 7, 9)
    }

    private fun step(i: Int, d: Int): Int {
        val r = i / 8; val c = i % 8
        val nr = r + (if (d < 0) -1 else 1); val nc = c + (if (d == -9 || d == 7) -1 else 1)
        return if (nr in 0..7 && nc in 0..7) nr * 8 + nc else -1
    }

    private fun jumps(i: Int, piece: Int, path: List<Int>, caps: List<Int>, out: MutableList<Move>) {
        var any = false
        for (d in dirs(piece)) {
            val mid = step(i, d); if (mid < 0) continue
            val land = step(mid, d); if (land < 0) continue
            if (b[mid] * piece < 0 && mid !in caps && (b[land] == 0 || land == path.first())) {
                any = true
                val crowned = (piece == 1 && land / 8 == 0) || (piece == -1 && land / 8 == 7)
                if (crowned) out += Move(path + land, caps + mid)
                else jumps(land, piece, path + land, caps + mid, out)
            }
        }
        if (!any && caps.isNotEmpty()) out += Move(path, caps)
    }

    override fun legalMoves(): List<Move> {
        if (quiet >= 80) return emptyList()
        val caps = ArrayList<Move>(); val plain = ArrayList<Move>()
        for (i in 0..63) {
            val p = b[i]; if (p == 0 || p * turn < 0) continue
            jumps(i, p, listOf(i), emptyList(), caps)
            if (caps.isEmpty()) for (d in dirs(p)) { val t = step(i, d); if (t >= 0 && b[t] == 0) plain += Move(listOf(i, t), emptyList()) }
        }
        return if (caps.isNotEmpty()) caps else plain
    }

    override fun make(m: Move) {
        hist += intArrayOf(*b, turn, quiet)
        val p = b[m.from]; b[m.from] = 0
        for (c in m.captured) b[c] = 0
        var np = p
        if (p == 1 && m.to / 8 == 0) np = 2
        if (p == -1 && m.to / 8 == 7) np = -2
        b[m.to] = np
        quiet = if (m.captured.isNotEmpty() || abs(p) == 1) 0 else quiet + 1
        turn = -turn
    }

    override fun unmake() {
        val s = hist.removeAt(hist.lastIndex); s.copyInto(b, 0, 0, 64); turn = s[64]; quiet = s[65]
    }

    override fun evaluate(): Int {
        var v = 0
        for (i in 0..63) {
            val p = b[i]; if (p == 0) continue
            val r = i / 8; val c = i % 8
            var s = if (abs(p) == 1) 100 else 175
            if (abs(p) == 1) s += if (p > 0) (7 - r) * 4 else r * 4
            if (c in 2..5 && r in 2..5) s += 6
            if (abs(p) == 1 && ((p > 0 && r == 7) || (p < 0 && r == 0))) s += 10 // back row guard
            v += if (p > 0) s else -s
        }
        return v * turn
    }

    override fun terminal(): Int? {
        if (quiet >= 80) return 0
        if (legalMoves().isEmpty()) return -WIN
        return null
    }

    override fun orderKey(m: Move) = m.captured.size * 10
    override fun noisyMoves(): List<Move> = legalMoves().filter { it.captured.isNotEmpty() }

    fun state(): IntArray = intArrayOf(*b, turn, quiet)
    fun load(s: IntArray) { s.copyInto(b, 0, 0, 64); turn = s[64]; quiet = s[65] }
}
