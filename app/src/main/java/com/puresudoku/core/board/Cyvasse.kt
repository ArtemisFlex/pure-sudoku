package com.puresudoku.core.board

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** Cyvasse: gameofcyvasse.com rules with the player's custom setup and fortress rules. */
class Cyvasse {
    companion object {
        const val N = 8
        const val CELLS = N * N
        const val SETUP = 0; const val BATTLE = 1; const val OVER = 2
        /** [winner] when the game ends without a victor. */
        const val DRAWN = 2

        const val PLAIN = 0; const val WATER = 1; const val FOREST = 2
        const val MOUNTAIN = 1; const val KEEP = 2

        const val RABBLE = 1; const val SPEARMAN = 2; const val CROSSBOW = 3; const val LIGHT_HORSE = 4; const val HEAVY_HORSE = 5
        const val ELEPHANT = 6; const val CATAPULT = 7; const val TREBUCHET = 8; const val DRAGON = 9; const val KING = 10
        val TYPES = RABBLE..KING
        /** How many of each piece a side has (index = type). */
        val ARMY = intArrayOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
        const val MOUNTAINS = 6
        const val WATERS = 5; const val FORESTS_PER_SIDE = 6
        /** Moves without a capture or a piece brought out before the game is drawn. */
        const val QUIET_LIMIT = 160

        val NAMES = arrayOf("", "Rabble", "Spearman", "Crossbowman", "Light Horse", "Heavy Horse", "Elephant", "Catapult", "Trebuchet", "Dragon", "King")
        val PLURALS = arrayOf("", "Rabble", "Spearmen", "Crossbowmen", "Light Horses", "Heavy Horses", "Elephants", "Catapults", "Trebuchets", "Dragons", "Kings")
        val MOVES_TEXT = arrayOf("", "Moves one tile.", "Moves one tile; cavalry cannot capture it.",
            "Moves one tile; shoots within one tile and can capture dragons.", "Moves up to three tiles.",
            "Moves up to two tiles.", "Moves one tile.", "Moves one tile; shoots within two tiles and can capture dragons.",
            "Moves one tile; shoots up to three tiles along its last direction of movement and can capture dragons.",
            "Flies in a straight line; only the opposing dragon blocks it. Cannot attack into forests.",
            "Moves one tile. Any piece can capture it; its capture ends the game.")
        /** Rough worth of each piece, for the computer and the coach. */
        val VALUE = intArrayOf(0, 10, 16, 20, 30, 32, 36, 40, 44, 90, 0)

        /** Default symmetric terrain for fixtures; new games start empty for player placement. */
        val TERRAIN: IntArray = IntArray(CELLS).also { t ->
            for (c in intArrayOf(8, 19, 27, 36, 44, 55)) t[c] = WATER
            for (c in intArrayOf(10, 17, 30, 33, 46, 53)) t[c] = FOREST
        }
        val FORESTS: IntArray = (0 until CELLS).filter { TERRAIN[it] == FOREST }.toIntArray()

        fun newSetup(): Cyvasse = Cyvasse().also { it.terrain.fill(PLAIN) }

        fun piece(side: Int, type: Int) = side * 10 + type
        fun ownerOf(v: Int) = if (v == 0) -1 else (v - 1) / 10
        fun typeOf(v: Int) = if (v == 0) 0 else (v - 1) % 10 + 1
        fun row(c: Int) = c / N
        fun col(c: Int) = c % N
        /** Alabaster (0) holds rows 4-7, Onyx (1) rows 0-3. */
        fun inHalf(side: Int, c: Int) = if (side == 0) row(c) >= N / 2 else row(c) < N / 2
        /** The row by the screen, where no keep may stand. */
        fun frontRow(side: Int) = if (side == 0) N / 2 else N / 2 - 1
        fun coord(c: Int) = "${'a' + col(c)}${N - row(c)}"
        fun sideName(side: Int) = if (side == 0) "Alabaster" else "Onyx"

        val ORTHO = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
        val DIAG = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1))
        val ALL8 = ORTHO + DIAG

        fun neighbours(c: Int): List<Int> {
            val r = row(c); val k = col(c)
            val out = ArrayList<Int>(8)
            for (d in ALL8) { val nr = r + d[0]; val nc = k + d[1]; if (nr in 0 until N && nc in 0 until N) out += nr * N + nc }
            return out
        }
        val NEIGHBOURS: Array<IntArray> = Array(CELLS) { neighbours(it).toIntArray() }

        // ---- moves, packed in one Int: kind | a | b | via ----
        const val K_MOVE = 0; const val K_DEPLOY = 1; const val K_SWAP = 2; const val K_PASS = 3; const val K_RANGED = 4
        /** [a] is the start (or the piece type when bringing one out), [b] the end, [via] a catapult's first victim. */
        fun mv(kind: Int, a: Int, b: Int, via: Int = -1) = (kind shl 24) or (a shl 16) or (b shl 8) or (via + 1)
        fun kindOf(m: Int) = m ushr 24
        fun aOf(m: Int) = (m ushr 16) and 0xFF
        fun bOf(m: Int) = (m ushr 8) and 0xFF
        fun viaOf(m: Int) = (m and 0xFF) - 1
        val PASS = mv(K_PASS, 0, 0)

        fun fromJson(o: JSONObject): Cyvasse {
            fun ia(k: String, n: Int) = o.optJSONArray(k)?.let { a -> IntArray(a.length()) { a.getInt(it) } } ?: IntArray(n)
            val g = Cyvasse()
            ia("board", CELLS).copyInto(g.board); ia("block", CELLS).copyInto(g.block)
            o.optJSONArray("terrain")?.let { a -> if (a.length() == CELLS) for (i in 0 until CELLS) g.terrain[i] = a.getInt(i) }
            ia("keepAt", 2).copyInto(g.keepAt)
            g.keepGone[0] = o.optBoolean("gone0"); g.keepGone[1] = o.optBoolean("gone1")
            ia("res0", 11).copyInto(g.reserve[0]); ia("res1", 11).copyInto(g.reserve[1])
            ia("cap0", 11).copyInto(g.captured[0]); ia("cap1", 11).copyInto(g.captured[1])
            g.phase = o.optInt("phase"); g.placer = o.optInt("placer"); g.turn = o.optInt("turn"); g.first = o.optInt("first")
            g.winner = o.optInt("winner", -1); g.bonusRabble = o.optInt("bonus", -1); g.quiet = o.optInt("quiet"); g.plies = o.optInt("plies")
            g.lastMove = o.optInt("last", -1); g.lastBy = o.optInt("lastBy", -1); g.lastText = o.optString("lastText", "")
            g.keepFell = o.optInt("keepFell", -1)
            ia("waterWait", CELLS).copyInto(g.waterWait); ia("facing", CELLS).copyInto(g.facing)
            ia("fortHits", 2).copyInto(g.fortHits)
            return g
        }
    }

    /** The piece on each tile (0 = none; see [piece]). */
    val board = IntArray(CELLS)
    /** What stands on each tile that isn't a piece: a mountain or a keep. */
    val block = IntArray(CELLS)
    /** Terrain selected during setup, persisted with the match. */
    val terrain = TERRAIN.copyOf()
    val keepAt = intArrayOf(-1, -1)
    val keepGone = BooleanArray(2)
    /** Water delay and trebuchet direction travel with the piece at each cell. */
    val waterWait = IntArray(CELLS)
    val facing = IntArray(CELLS)
    val fortHits = IntArray(2)
    /** Pieces waiting off the board, by type. */
    val reserve = Array(2) { ARMY.copyOf() }
    /** The enemy pieces each side has taken, by type. */
    val captured = Array(2) { IntArray(11) }
    var phase = SETUP
    /** Who is behind the screen during [SETUP]: Alabaster first, then Onyx. */
    var placer = 0
    var turn = 0
    /** Who moves first, decided by a coin. */
    var first = 0
    var winner = -1
    /** After a rabble moves, the tile it reached: the same player may move one other rabble, or pass. */
    var bonusRabble = -1
    var quiet = 0
    var plies = 0
    var lastMove = -1
    var lastBy = -1
    var lastText = ""
    /** The side whose keep fell on the last move, or -1. */
    var keepFell = -1

    fun toJson(): JSONObject {
        fun a(x: IntArray) = JSONArray().also { j -> x.forEach { j.put(it) } }
        return JSONObject().put("board", a(board)).put("block", a(block)).put("terrain", a(terrain)).put("keepAt", a(keepAt))
            .put("gone0", keepGone[0]).put("gone1", keepGone[1]).put("waterWait", a(waterWait)).put("facing", a(facing)).put("fortHits", a(fortHits))
            .put("res0", a(reserve[0])).put("res1", a(reserve[1])).put("cap0", a(captured[0])).put("cap1", a(captured[1]))
            .put("phase", phase).put("placer", placer).put("turn", turn).put("first", first).put("winner", winner)
            .put("bonus", bonusRabble).put("quiet", quiet).put("plies", plies)
            .put("last", lastMove).put("lastBy", lastBy).put("lastText", lastText).put("keepFell", keepFell)
    }

    fun copy(): Cyvasse {
        val g = Cyvasse()
        board.copyInto(g.board); block.copyInto(g.block); terrain.copyInto(g.terrain); keepAt.copyInto(g.keepAt)
        waterWait.copyInto(g.waterWait); facing.copyInto(g.facing); fortHits.copyInto(g.fortHits)
        g.keepGone[0] = keepGone[0]; g.keepGone[1] = keepGone[1]
        for (s in 0..1) { reserve[s].copyInto(g.reserve[s]); captured[s].copyInto(g.captured[s]) }
        g.phase = phase; g.placer = placer; g.turn = turn; g.first = first; g.winner = winner
        g.bonusRabble = bonusRabble; g.quiet = quiet; g.plies = plies
        g.lastMove = lastMove; g.lastBy = lastBy; g.lastText = lastText; g.keepFell = keepFell
        return g
    }

    fun keepStands(side: Int) = keepAt[side] >= 0 && !keepGone[side]

    /** The tiles round [side]'s keep where its pieces enter the board (and which the enemy must take). */
    fun homeTiles(side: Int): IntArray {
        if (!keepStands(side)) return IntArray(0)
        return NEIGHBOURS[keepAt[side]].filter { block[it] == 0 }.toIntArray()
    }

    fun isHome(side: Int, c: Int) = keepStands(side) && block[c] == 0 && c in NEIGHBOURS[keepAt[side]]

    fun mountainsPlaced(side: Int) = (0 until CELLS).count { block[it] == MOUNTAIN && inHalf(side, it) }
    fun terrainPlaced(side: Int, type: Int) = (0 until CELLS).count { inHalf(side, it) && terrain[it] == type }

    fun toggleTerrain(side: Int, type: Int, c: Int): Boolean {
        if (phase != SETUP || side != placer || type !in WATER..FOREST || c !in 0 until CELLS || !inHalf(side, c)) return false
        if (block[c] != 0 || board[c] != 0) return false
        if (terrain[c] == type) { terrain[c] = PLAIN; return true }
        if (terrain[c] != PLAIN || terrainPlaced(side, type) >= if (type == WATER) WATERS else FORESTS_PER_SIDE) return false
        terrain[c] = type
        return true
    }
    fun reserveCount(side: Int) = reserve[side].sum()
    fun onBoard(side: Int) = board.count { ownerOf(it) == side }
    fun kingAlive(side: Int) = captured[1 - side][KING] == 0

    /**
     * How many of the eight lines out from [side]'s king are open to attack: they reach an enemy piece, or the
     * enemy's half, before anything of its own (or a mountain or keep) blocks them. Lines across and along wrap
     * round the edges, the way light horses ride. A king in reserve has none.
     */
    fun openLines(side: Int): Int {
        val k = (0 until CELLS).firstOrNull { board[it] == piece(side, KING) } ?: return 0
        var open = 0
        for (d in ALL8) {
            var r = row(k); var c = col(k)
            for (step in 1 until N) {
                r += d[0]; c += d[1]
                if (r !in 0 until N || c !in 0 until N) break
                val t = r * N + c
                if (block[t] != 0 || ownerOf(board[t]) == side) break
                if (ownerOf(board[t]) == 1 - side || !inHalf(side, t)) { open++; break }
            }
        }
        return open
    }

    // =================================================================  setting up, behind the screen

    fun canMountain(side: Int, c: Int) = phase == SETUP && side == placer && c in 0 until CELLS && inHalf(side, c) &&
        terrain[c] == PLAIN && block[c] == 0 && board[c] == 0 && mountainsPlaced(side) < MOUNTAINS

    /** Sets a mountain on [c], or lifts the one there. */
    fun toggleMountain(side: Int, c: Int): Boolean {
        if (phase != SETUP || side != placer || c !in 0 until CELLS || !inHalf(side, c)) return false
        if (block[c] == MOUNTAIN) { block[c] = 0; return true }
        if (!canMountain(side, c)) return false
        block[c] = MOUNTAIN
        return true
    }

    fun canKeep(side: Int, c: Int) = phase == SETUP && side == placer && c in 0 until CELLS && inHalf(side, c) &&
        terrain[c] == PLAIN && block[c] == 0 && board[c] == 0

    /** Sets (or moves) the keep. Pieces left off the new home tiles go back to reserve. */
    fun placeKeep(side: Int, c: Int): Boolean {
        if (!canKeep(side, c)) return false
        if (keepAt[side] >= 0) block[keepAt[side]] = 0
        keepAt[side] = c; block[c] = KEEP
        return true
    }

    fun canStand(side: Int, type: Int, c: Int) = phase == SETUP && side == placer && type in TYPES && c in 0 until CELLS && inHalf(side, c) && reserve[side][type] > 0 && board[c] == 0 && block[c] != KEEP && (block[c] != MOUNTAIN || type == DRAGON)

    fun standPiece(side: Int, type: Int, c: Int): Boolean {
        if (!canStand(side, type, c)) return false
        board[c] = piece(side, type); waterWait[c] = if (terrain[c] == WATER) 1 else 0; reserve[side][type]--
        return true
    }

    fun liftPiece(side: Int, c: Int): Boolean {
        if (phase != SETUP || ownerOf(board[c]) != side) return false
        reserve[side][typeOf(board[c])]++; board[c] = 0; waterWait[c] = 0; facing[c] = 0
        return true
    }

    fun clearSetup(side: Int) {
        if (phase != SETUP) return
        for (c in 0 until CELLS) if (inHalf(side, c)) { if (ownerOf(board[c]) == side) liftPiece(side, c); block[c] = 0; terrain[c] = PLAIN }
        keepAt[side] = -1
    }

    fun setupDone(side: Int) = keepAt[side] >= 0 && mountainsPlaced(side) == MOUNTAINS &&
        terrainPlaced(side, WATER) == WATERS && terrainPlaced(side, FOREST) == FORESTS_PER_SIDE && reserveCount(side) == 0

    /** Random complete setup, including all ten pieces. */
    fun randomSetup(side: Int, rnd: Random, level: Int = 3) {
        val old = placer; placer = side; clearSetup(side)
        val cells = (0 until CELLS).filter { inHalf(side, it) }.shuffled(rnd)
        var i = 0
        repeat(WATERS) { toggleTerrain(side, WATER, cells[i++]) }
        repeat(FORESTS_PER_SIDE) { toggleTerrain(side, FOREST, cells[i++]) }
        repeat(MOUNTAINS) { toggleMountain(side, cells[i++]) }
        placeKeep(side, cells[i++])
        // Avoid crippling a random army even though manual placement permits it.
        val open = cells.filter { block[it] != MOUNTAIN && block[it] != KEEP }.shuffled(rnd)
        for ((t, c) in TYPES.zip(open)) standPiece(side, t, c)
        placer = old
    }

    /** The player behind the screen is finished: the other sets up, or the screen comes down. */
    fun ready(side: Int, coin: Boolean): Boolean {
        if (phase != SETUP || side != placer || !setupDone(side)) return false
        if (side == 0) placer = 1
        else { phase = BATTLE; first = if (coin) 1 else 0; turn = first }
        return true
    }

    // =================================================================  battle
    private fun canTake(from: Int, target: Int): Boolean {
        val attacker = typeOf(board[from]); val defender = typeOf(board[target])
        if (defender == 0) return false
        if (attacker == KING || defender == KING) return true
        if (defender == SPEARMAN && attacker in LIGHT_HORSE..HEAVY_HORSE) return false
        if (defender == DRAGON && attacker in intArrayOf(CROSSBOW, CATAPULT, TREBUCHET)) return true
        return defender <= attacker + 1
    }

    fun legalMoves(): IntArray {
        if (phase != BATTLE) return IntArray(0)
        val out = IntList()
        for (c in 0 until CELLS) if (ownerOf(board[c]) == turn && waterWait[c] == 0) pieceMoves(c, out)
        if (out.size == 0) out.add(PASS)
        return out.toArray()
    }

    fun pieceMoves(from: Int, out: IntList) {
        val type = typeOf(board[from]); if (type == 0 || waterWait[from] > 0) return
        val side = ownerOf(board[from])
        if (type == DRAGON) {
            for (d in ALL8) {
                var r = row(from); var c = col(from); var lastEmpty = from
                while (true) {
                    r += d[0]; c += d[1]
                    if (r !in 0 until N || c !in 0 until N) break
                    val t = r * N + c; val v = board[t]
                    if (v == 0) { out.add(mv(K_MOVE, from, t, lastEmpty)); lastEmpty = t }
                    else if (terrain[t] != FOREST && canTake(from, t)) out.add(mv(K_MOVE, from, t, lastEmpty))
                    if (v == piece(1 - side, DRAGON)) break
                }
            }
        } else {
            val limit = when (type) { LIGHT_HORSE -> 3; HEAVY_HORSE -> 2; else -> 1 }
            data class Step(val cell: Int, val left: Int, val previous: Int)
            val queue = ArrayDeque<Step>(); queue.add(Step(from, limit, from))
            val best = IntArray(CELLS) { -1 }; best[from] = limit
            while (queue.isNotEmpty()) {
                val step = queue.removeFirst()
                if (step.left <= 0) continue
                for (t in NEIGHBOURS[step.cell]) {
                    if (t == from || block[t] == MOUNTAIN) continue
                    val left = step.left - 1 - if (terrain[t] == FOREST) 1 else 0
                    if (board[t] != 0) {
                        if (canTake(from, t)) out.add(mv(K_MOVE, from, t, step.cell))
                    } else {
                        out.add(mv(K_MOVE, from, t, step.cell))
                        if (terrain[t] != WATER && left > 0 && left > best[t]) { best[t] = left; queue.add(Step(t, left, step.cell)) }
                    }
                }
            }
        }
        if (terrain[from] == WATER || type !in intArrayOf(CROSSBOW, CATAPULT, TREBUCHET)) return
        val range = when (type) { CROSSBOW -> 1; CATAPULT -> 2; else -> 3 }
        for (t in 0 until CELLS) {
            if (board[t] == 0 || t == from || !canTake(from, t)) continue
            val dr = row(t) - row(from); val dc = col(t) - col(from)
            val distance = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc))
            if (distance > range) continue
            if (type == TREBUCHET) {
                val dir = facing[from] - 1
                if (dir !in ALL8.indices || dr != ALL8[dir][0] * distance || dc != ALL8[dir][1] * distance) continue
            }
            // Mountains block shots through them; a dragon on a mountain may be targeted.
            var blocked = false
            for (n in 1 until distance) {
                val rr = row(from) + kotlin.math.round(dr.toFloat() * n / distance).toInt()
                val cc = col(from) + kotlin.math.round(dc.toFloat() * n / distance).toInt()
                if (block[rr * N + cc] == MOUNTAIN) { blocked = true; break }
            }
            if (!blocked) out.add(mv(K_RANGED, from, t))
        }
    }

    fun isLegal(m: Int) = legalMoves().contains(m)
    fun victims(m: Int): List<Int> = if (kindOf(m) in intArrayOf(K_MOVE, K_RANGED) && board[bOf(m)] != 0) listOf(bOf(m)) else emptyList()

    fun play(m: Int, check: Boolean = true): Boolean {
        if (phase != BATTLE || (check && !isLegal(m))) return false
        val s = turn; val a = aOf(m); val b = bOf(m); val kind = kindOf(m)
        lastText = describe(m); keepFell = -1
        // A unit that entered water on its previous turn is free after this turn.
        for (c in 0 until CELLS) if (ownerOf(board[c]) == s && waterWait[c] > 0) waterWait[c]--
        if (kind == K_MOVE || kind == K_RANGED) {
            val victim = board[b]
            val fort = (0..1).firstOrNull { keepAt[it] == b && !keepGone[it] }
            var repelled = false
            if (victim != 0 && fort != null) {
                fortHits[fort]++
                val hitsNeeded = if (typeOf(victim) == KING && typeOf(board[a]) != DRAGON) 2 else 1
                if (fortHits[fort] >= hitsNeeded) { keepGone[fort] = true; keepFell = fort }
                repelled = true
                lastText += if (keepGone[fort]) "; the fortress is ruined and the defender survives" else "; the fortress absorbs the attack"
            } else if (victim != 0) {
                val capturedSide = ownerOf(victim); val t = typeOf(victim)
                captured[1 - capturedSide][t]++; board[b] = 0; waterWait[b] = 0; facing[b] = 0
                if (t == KING) winner = 1 - capturedSide
            }
            if (kind == K_MOVE) {
                val destination = if (repelled) viaOf(m).takeIf { it >= 0 } ?: a else b
                if (destination != a) {
                    val moving = board[a]; val oldFacing = facing[a]
                    board[a] = 0; waterWait[a] = 0; facing[a] = 0
                    board[destination] = moving
                    waterWait[destination] = if (terrain[destination] == WATER && typeOf(moving) != DRAGON) 1 else 0
                    val dr = (row(destination) - row(a)).coerceIn(-1, 1); val dc = (col(destination) - col(a)).coerceIn(-1, 1)
                    facing[destination] = if (typeOf(moving) == TREBUCHET) ALL8.indexOfFirst { it[0] == dr && it[1] == dc } + 1 else oldFacing
                }
            }
        }
        lastMove = m; lastBy = s; plies++; bonusRabble = -1
        if (winner >= 0) phase = OVER else turn = 1 - s
        return true
    }

    fun describe(m: Int): String = when (kindOf(m)) {
        K_PASS -> "passes because no unit can move or attack"
        K_RANGED -> "fires a ${NAMES[typeOf(board[aOf(m)])]} at ${coord(bOf(m))}"
        else -> "moves a ${NAMES[typeOf(board[aOf(m)])]} from ${coord(aOf(m))} to ${coord(bOf(m))}"
    }

}

/** A small growable list of Ints, so move generation doesn't box. */
class IntList(cap: Int = 64) {
    private var a = IntArray(cap)
    var size = 0; private set
    fun add(x: Int) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = x }
    operator fun get(i: Int) = a[i]
    fun toArray() = a.copyOf(size)
}

/**
 * The computer's lord: wild on Easy, greedy on Medium, then a search that looks two to four moves deep,
 * weighing pieces, pressure on the keeps and the safety of its king.
 */
object CyvasseAi {
    fun depthFor(level: Int) = when (level) { 0, 1 -> 1; 2, 3 -> 2; 4 -> 3; else -> 4 }
    fun timeFor(level: Int) = when (level) { 4 -> 2000L; 5 -> 2500L; else -> 1200L }

    fun setup(g: Cyvasse, side: Int, level: Int, rnd: Random) = g.randomSetup(side, rnd, level)

    /** The move to play for the side to move. */
    fun best(g: Cyvasse, level: Int, rnd: Random): Int {
        val moves = g.legalMoves()
        if (moves.isEmpty()) return Cyvasse.PASS
        val me = g.turn
        // Easy plays loosely: often any move, though it grabs a king when it sees one
        if (level <= 0) {
            for (m in moves) if (g.victims(m).any { Cyvasse.typeOf(g.board[it]) == Cyvasse.KING && Cyvasse.ownerOf(g.board[it]) != me }) return m
            if (rnd.nextInt(100) < 55) return moves[rnd.nextInt(moves.size)]
        }
        val deadline = now() + timeFor(level)
        val maxDepth = depthFor(level)
        var best = moves[rnd.nextInt(moves.size)]
        var ordered = order(g, moves)
        for (depth in 1..maxDepth) {
            var alpha = -INF
            var bestHere = ordered[0]
            val scores = HashMap<Int, Int>()
            var timedOut = false
            for (m in ordered) {
                val c = g.copy(); c.play(m, check = false)
                val v = search(c, depth - 1, alpha, INF, me, deadline) + noise(level, rnd)
                scores[m] = v
                if (v > alpha) { alpha = v; bestHere = m }
                if (depth > 1 && now() > deadline) { timedOut = true; break }
            }
            if (!timedOut || depth == 1) best = bestHere
            if (timedOut) break
            ordered = ordered.sortedByDescending { scores[it] ?: -INF }.toIntArray()
        }
        return best
    }

    private val clock = kotlin.time.TimeSource.Monotonic.markNow()
    private fun now() = clock.elapsedNow().inWholeMilliseconds

    private const val INF = 1_000_000
    private const val WIN = 100_000

    private fun noise(level: Int, rnd: Random) = when (level) { 0 -> rnd.nextInt(40); 1 -> rnd.nextInt(14); 2 -> rnd.nextInt(6); 3 -> rnd.nextInt(3); else -> 0 }

    /** Minimax from [me]'s point of view (the side to move can stay the same for a rabble's second step). */
    private fun search(g: Cyvasse, depth: Int, alphaIn: Int, betaIn: Int, me: Int, deadline: Long): Int {
        if (g.phase == Cyvasse.OVER) return when (g.winner) { me -> WIN + depth; Cyvasse.DRAWN -> 0; else -> -WIN - depth }
        if (depth <= 0) return eval(g, me)
        val raw = g.legalMoves()
        if (raw.isEmpty()) return if (g.turn == me) -WIN - depth else WIN + depth
        val moves = order(g, raw)
        var alpha = alphaIn; var beta = betaIn
        val maxing = g.turn == me
        var bestV = if (maxing) -INF else INF
        for (m in moves) {
            val c = g.copy(); c.play(m, check = false)
            val v = search(c, depth - 1, alpha, beta, me, deadline)
            if (maxing) { if (v > bestV) bestV = v; if (v > alpha) alpha = v } else { if (v < bestV) bestV = v; if (v < beta) beta = v }
            if (alpha >= beta) break
            if (now() > deadline) break
        }
        return bestV
    }

    /** Captures first (biggest prize first), then everything else. */
    private fun order(g: Cyvasse, moves: IntArray): IntArray {
        val key = IntArray(moves.size) { i ->
            val m = moves[i]
            var k = 0
            for (x in g.victims(m)) { val v = if (Cyvasse.typeOf(g.board[x]) == Cyvasse.KING) 10_000 else Cyvasse.VALUE[Cyvasse.typeOf(g.board[x])] * 10; k += if (Cyvasse.ownerOf(g.board[x]) == g.turn) -v else v }
            k
        }
        return moves.indices.sortedByDescending { key[it] }.map { moves[it] }.toIntArray()
    }

    /** Material on the board and in reserve, pressure on the keeps, and a king kept out of reach. */
    fun eval(g: Cyvasse, me: Int): Int {
        var score = 0
        for (side in 0..1) {
            val sign = if (side == me) 1 else -1
            var v = 0
            for (c in 0 until Cyvasse.CELLS) {
                val p = g.board[c]
                if (Cyvasse.ownerOf(p) != side) continue
                val t = Cyvasse.typeOf(p)
                v += Cyvasse.VALUE[t]
                if (!Cyvasse.inHalf(side, c)) v += 2
            }
            var res = 0
            for (t in Cyvasse.TYPES) res += Cyvasse.VALUE[t] * g.reserve[side][t]
            v += res * 9 / 10
            val e = 1 - side
            // a king alone in the open is a target; one guarded by its own pieces less so
            val k = (0 until Cyvasse.CELLS).firstOrNull { g.board[it] == Cyvasse.piece(side, Cyvasse.KING) }
            if (k != null) v += Cyvasse.NEIGHBOURS[k].count { Cyvasse.ownerOf(g.board[it]) == side } * 3 -
                Cyvasse.NEIGHBOURS[k].count { Cyvasse.ownerOf(g.board[it]) == e } * 12 - g.openLines(side) * 7
            score += sign * v
        }
        return score
    }
}
