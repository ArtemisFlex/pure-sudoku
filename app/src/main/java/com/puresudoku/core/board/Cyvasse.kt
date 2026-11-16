package com.puresudoku.core.board

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Cyvasse, PureGames' standard version: both sides start from the same mirrored layout of mountains, forests, a fortress
 * and ten pieces. Kings are in check like chess (no move may leave your own king attackable; no legal move while in
 * check is checkmate). The line between the halves is a river: siege engines never cross it, and other ground units
 * that have crossed it cannot step back until promoted. A rabble reaching the enemy's back row is promoted.
 */
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
        val MOVES_TEXT = arrayOf("", "Moves one tile. Promoted on the enemy's back row.",
            "Moves up to two tiles but attacks only diagonally; cavalry cannot capture it.",
            "Moves one tile; shoots one tile and can capture dragons.", "Moves up to three tiles.",
            "Moves up to two tiles.", "Moves one tile.",
            "Moves one tile; shoots up to four tiles in a line, over your own pieces, and can capture dragons. Never crosses the river or enters a fortress.",
            "Moves one tile; shoots up to three tiles in a line, over your own pieces, and can capture dragons. Never crosses the river or enters a fortress.",
            "Flies any distance in a line over water and forest; mountains, pieces and fortresses stop it. Cannot attack into forests.",
            "Moves one tile. It can never be left in check; checkmate ends the game.")
        /** Rough worth of each piece, for the computer and the coach. */
        val VALUE = intArrayOf(0, 10, 16, 20, 30, 32, 36, 40, 44, 90, 0)

        /** Default symmetric terrain for fixtures; new games start empty for player placement. */
        val TERRAIN: IntArray = IntArray(CELLS).also { t ->
            for (c in intArrayOf(8, 19, 27, 36, 44, 55)) t[c] = WATER
            for (c in intArrayOf(10, 17, 30, 33, 46, 53)) t[c] = FOREST
        }
        val FORESTS: IntArray = (0 until CELLS).filter { TERRAIN[it] == FOREST }.toIntArray()

        fun newSetup(): Cyvasse = Cyvasse().also { it.terrain.fill(PLAIN) }

        /**
         * The standard opening. Alabaster's half, from the river back: mountains on a, d, e and h; forests on a, b, d, e, g
         * and h; the fortress on a with a crossbowman beside it and the elephant on h; and the back row catapult, spearman,
         * light horse, dragon, king, heavy horse, rabble, trebuchet. Onyx's half mirrors it across the river.
         */
        fun standard(): Cyvasse {
            val g = Cyvasse()
            g.terrain.fill(PLAIN)
            for (side in 0..1) {
                // Alabaster's rows are 4 (by the river) to 7 (the back row); Onyx's mirror them
                fun at(r: Int, c: Int) = (if (side == 0) r else N - 1 - r) * N + c
                for (c in intArrayOf(0, 3, 4, 7)) g.block[at(4, c)] = MOUNTAIN
                for (c in intArrayOf(0, 1, 3, 4, 6, 7)) g.terrain[at(5, c)] = FOREST
                g.keepAt[side] = at(6, 0); g.block[at(6, 0)] = KEEP
                g.board[at(6, 1)] = piece(side, CROSSBOW); g.board[at(6, 7)] = piece(side, ELEPHANT)
                val back = intArrayOf(CATAPULT, SPEARMAN, LIGHT_HORSE, DRAGON, KING, HEAVY_HORSE, RABBLE, TREBUCHET)
                for (c in 0 until N) g.board[at(7, c)] = piece(side, back[c])
                g.reserve[side].fill(0)
            }
            g.phase = BATTLE; g.turn = 0; g.first = 0
            return g
        }

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
        /** A rabble's move onto the enemy's back row; [via] holds the piece it becomes. */
        const val K_PROMO = 5
        /** What a rabble may become: anything but a king or another rabble. */
        val PROMOTIONS = SPEARMAN..DRAGON
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
            ia("crossed", CELLS).copyInto(g.crossed); ia("promoted", CELLS).copyInto(g.promoted)
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
    /** 1 where the ground unit standing there has crossed the river (it may no longer step back), or was promoted. */
    val crossed = IntArray(CELLS)
    val promoted = IntArray(CELLS)
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
            .put("gone0", keepGone[0]).put("gone1", keepGone[1]).put("waterWait", a(waterWait)).put("facing", a(facing)).put("fortHits", a(fortHits)).put("crossed", a(crossed)).put("promoted", a(promoted))
            .put("res0", a(reserve[0])).put("res1", a(reserve[1])).put("cap0", a(captured[0])).put("cap1", a(captured[1]))
            .put("phase", phase).put("placer", placer).put("turn", turn).put("first", first).put("winner", winner)
            .put("bonus", bonusRabble).put("quiet", quiet).put("plies", plies)
            .put("last", lastMove).put("lastBy", lastBy).put("lastText", lastText).put("keepFell", keepFell)
    }

    fun copy(): Cyvasse {
        val g = Cyvasse()
        board.copyInto(g.board); block.copyInto(g.block); terrain.copyInto(g.terrain); keepAt.copyInto(g.keepAt)
        waterWait.copyInto(g.waterWait); facing.copyInto(g.facing); fortHits.copyInto(g.fortHits)
        crossed.copyInto(g.crossed); promoted.copyInto(g.promoted)
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

    /** A standing (not ruined) fortress on [c], or -1. */
    fun fortAt(c: Int): Int = (0..1).firstOrNull { keepAt[it] == c && !keepGone[it] } ?: -1

    /** How far [side] has advanced on [c]'s row: 0 on its own back row, 7 on the enemy's. */
    fun progress(side: Int, c: Int) = if (side == 0) N - 1 - row(c) else row(c)

    /** The enemy's back row, where a rabble is promoted. */
    fun promotionRow(side: Int) = if (side == 0) 0 else N - 1

    private fun isSiege(type: Int) = type == CATAPULT || type == TREBUCHET

    /** Whether the piece on [from] may capture the piece on [target]. Never your own. */
    fun canTake(from: Int, target: Int): Boolean {
        val attacker = typeOf(board[from]); val defender = typeOf(board[target])
        if (defender == 0 || attacker == 0 || ownerOf(board[from]) == ownerOf(board[target])) return false
        if (attacker == KING || defender == KING) return true
        if (defender == SPEARMAN && attacker in LIGHT_HORSE..HEAVY_HORSE) return false
        if (defender == DRAGON && attacker in intArrayOf(CROSSBOW, CATAPULT, TREBUCHET)) return true
        return defender <= attacker + 1
    }

    /** Every move and attack the side to move could make, ignoring whether it leaves its own king in check. */
    fun pseudoMoves(side: Int = turn): IntArray {
        val out = IntList()
        for (c in 0 until CELLS) if (ownerOf(board[c]) == side && waterWait[c] == 0) pieceMoves(c, out)
        return out.toArray()
    }

    /** The legal moves: none may leave the mover's own king in check. Empty means checkmate or stalemate. */
    fun legalMoves(): IntArray {
        if (phase != BATTLE) return IntArray(0)
        val side = turn
        val out = IntList()
        for (m in pseudoMoves(side)) {
            val c = copy(); c.play(m, check = false, settle = false)
            if (!c.inCheck(side)) out.add(m)
        }
        return out.toArray()
    }

    /** [side]'s king could be taken by the other side's next move. A king inside a standing fortress is shielded by it. */
    fun inCheck(side: Int): Boolean {
        val k = (0 until CELLS).firstOrNull { board[it] == piece(side, KING) } ?: return false
        if (fortAt(k) >= 0) return false
        val enemy = 1 - side
        val out = IntList()
        for (c in 0 until CELLS) if (ownerOf(board[c]) == enemy && waterWait[c] == 0) pieceMoves(c, out)
        for (i in 0 until out.size) { val m = out[i]; if (bOf(m) == k && kindOf(m) != K_PASS) return true }
        return false
    }

    fun pieceMoves(from: Int, out: IntList) {
        val type = typeOf(board[from]); if (type == 0 || waterWait[from] > 0) return
        val side = ownerOf(board[from])
        if (type == DRAGON) {
            // straight lines, over water and forest; mountains, pieces and fortresses stop it
            for (d in ALL8) {
                var r = row(from); var c = col(from); var lastEmpty = from
                while (true) {
                    r += d[0]; c += d[1]
                    if (r !in 0 until N || c !in 0 until N) break
                    val t = r * N + c
                    if (block[t] == MOUNTAIN) break
                    val v = board[t]
                    if (fortAt(t) >= 0) {
                        // it may only come down on a fortress to attack the piece inside
                        if (v != 0 && canTake(from, t)) out.add(mv(K_MOVE, from, t, lastEmpty))
                        break
                    }
                    if (v != 0) {
                        if (terrain[t] != FOREST && canTake(from, t)) out.add(mv(K_MOVE, from, t, lastEmpty))
                        break
                    }
                    out.add(mv(K_MOVE, from, t, lastEmpty)); lastEmpty = t
                }
            }
            return
        }
        val crossedLock = crossed[from] == 1 && promoted[from] == 0
        val limit = when (type) { LIGHT_HORSE -> 3; HEAVY_HORSE, SPEARMAN -> 2; else -> 1 }
        data class Step(val cell: Int, val left: Int)
        val queue = ArrayDeque<Step>(); queue.add(Step(from, limit))
        val best = IntArray(CELLS) { -1 }; best[from] = limit
        val seen = HashSet<Int>()
        fun emit(m: Int) { if (seen.add(m)) out.add(m) }
        while (queue.isNotEmpty()) {
            val step = queue.removeFirst()
            if (step.left <= 0) continue
            for (t in NEIGHBOURS[step.cell]) {
                if (t == from || block[t] == MOUNTAIN) continue
                // once across the river, a ground unit only goes forward or sideways (until promoted)
                if (crossedLock && progress(side, t) < progress(side, step.cell)) continue
                // siege engines never cross water, the river between the halves included
                if (isSiege(type) && (terrain[t] == WATER || !inHalf(side, t))) continue
                val fort = fortAt(t) >= 0
                if (fort && isSiege(type)) continue
                val left = step.left - 1 - if (terrain[t] == FOREST) 1 else 0
                if (board[t] != 0) {
                    // a spearman attacks only diagonally, next to where it stands
                    if (type == SPEARMAN) continue
                    if (canTake(from, t)) emitArrival(from, t, step.cell, type, side, ::emit)
                } else {
                    emitArrival(from, t, step.cell, type, side, ::emit)
                    // a fortress or water ends the move
                    if (!fort && terrain[t] != WATER && left > 0 && left > best[t]) { best[t] = left; queue.add(Step(t, left)) }
                }
            }
        }
        if (type == SPEARMAN) for (d in DIAG) {
            val r = row(from) + d[0]; val c = col(from) + d[1]
            if (r !in 0 until N || c !in 0 until N) continue
            val t = r * N + c
            if (block[t] == MOUNTAIN || board[t] == 0 || !canTake(from, t)) continue
            if (crossedLock && progress(side, t) < progress(side, from)) continue
            emitArrival(from, t, from, type, side, ::emit)
        }
        // shooting: not from water or forest; straight lines; catapults and trebuchets fire over their own pieces
        if (type !in intArrayOf(CROSSBOW, CATAPULT, TREBUCHET) || terrain[from] == WATER || terrain[from] == FOREST) return
        val range = when (type) { CROSSBOW -> 1; CATAPULT -> 4; else -> 3 }
        for (d in ALL8) {
            var r = row(from); var c = col(from)
            for (dist in 1..range) {
                r += d[0]; c += d[1]
                if (r !in 0 until N || c !in 0 until N) break
                val t = r * N + c
                if (block[t] == MOUNTAIN) break
                val v = board[t]
                if (v == 0) { if (fortAt(t) >= 0) break else continue }
                if (ownerOf(v) == side) { if (isSiege(type)) continue else break }
                if (canTake(from, t)) out.add(mv(K_RANGED, from, t))
                break
            }
        }
    }

    /** Adds the move from [from] to [t] (reached from [via]); a rabble reaching the enemy's back row becomes one move per promotion. */
    private fun emitArrival(from: Int, t: Int, via: Int, type: Int, side: Int, emit: (Int) -> Unit) {
        if (type == RABBLE && promoted[from] == 0 && row(t) == promotionRow(side) && fortAt(t) < 0) {
            for (p in PROMOTIONS) emit(mv(K_PROMO, from, t, p))
        } else emit(mv(K_MOVE, from, t, via))
    }

    fun isLegal(m: Int) = legalMoves().contains(m)
    fun victims(m: Int): List<Int> = if (kindOf(m) in intArrayOf(K_MOVE, K_RANGED, K_PROMO) && board[bOf(m)] != 0 && ownerOf(board[bOf(m)]) != ownerOf(board[aOf(m)])) listOf(bOf(m)) else emptyList()

    /**
     * Plays [m]. With [settle], checks afterwards whether the side now to move is checkmated (the mover wins) or
     * stalemated (a draw); the computer's search skips that and judges positions itself.
     */
    fun play(m: Int, check: Boolean = true, settle: Boolean = true): Boolean {
        if (phase != BATTLE || (check && !isLegal(m))) return false
        val s = turn; val a = aOf(m); val b = bOf(m); val kind = kindOf(m)
        lastText = describe(m); keepFell = -1
        // A unit that entered water on its previous turn is free after this turn.
        for (c in 0 until CELLS) if (ownerOf(board[c]) == s && waterWait[c] > 0) waterWait[c]--
        if (kind == K_MOVE || kind == K_RANGED || kind == K_PROMO) {
            val victim = board[b]
            val fort = fortAt(b)
            var repelled = false
            if (victim != 0 && fort >= 0) {
                fortHits[fort]++
                val hitsNeeded = if (typeOf(victim) == KING && typeOf(board[a]) != DRAGON) 2 else 1
                if (fortHits[fort] >= hitsNeeded) {
                    keepGone[fort] = true; keepFell = fort; block[b] = 0
                    // a ruined fortress counts as a piece taken
                    captured[s][0]++
                }
                repelled = true
                lastText += if (keepGone[fort]) "; the fortress is ruined and the defender survives" else "; the fortress absorbs the attack"
            } else if (victim != 0) {
                val capturedSide = ownerOf(victim); val t = typeOf(victim)
                captured[1 - capturedSide][t]++; clearCell(b)
                if (t == KING) winner = 1 - capturedSide
            }
            if (kind != K_RANGED) {
                val destination = if (repelled) viaOf(m).takeIf { it >= 0 && kind == K_MOVE } ?: a else b
                if (destination != a) {
                    val moving = board[a]; val wasCrossed = crossed[a]; val wasPromoted = promoted[a]
                    clearCell(a)
                    board[destination] = if (kind == K_PROMO) piece(s, viaOf(m)) else moving
                    promoted[destination] = if (kind == K_PROMO) 1 else wasPromoted
                    crossed[destination] = if (wasCrossed == 1 || !inHalf(s, destination)) 1 else 0
                    waterWait[destination] = if (terrain[destination] == WATER && typeOf(moving) != DRAGON) 1 else 0
                }
            }
        }
        lastMove = m; lastBy = s; plies++; bonusRabble = -1
        if (winner >= 0) { phase = OVER; return true }
        turn = 1 - s
        if (settle && legalMoves().isEmpty()) {
            if (inCheck(turn)) { winner = s; lastText += ": checkmate" } else { winner = DRAWN; lastText += ": stalemate" }
            phase = OVER
        } else if (settle && inCheck(turn)) lastText += ": check"
        return true
    }

    private fun clearCell(c: Int) { board[c] = 0; waterWait[c] = 0; facing[c] = 0; crossed[c] = 0; promoted[c] = 0 }

    fun describe(m: Int): String = when (kindOf(m)) {
        K_PASS -> "passes because no unit can move or attack"
        K_RANGED -> "fires a ${NAMES[typeOf(board[aOf(m)])]} at ${coord(bOf(m))}"
        K_PROMO -> "moves a Rabble from ${coord(aOf(m))} to ${coord(bOf(m))} and promotes it to a ${NAMES[viaOf(m)]}"
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
                val c = g.copy(); c.play(m, check = false, settle = false)
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
        if (raw.isEmpty()) return if (!g.inCheck(g.turn)) 0 else if (g.turn == me) -WIN - depth else WIN + depth
        val moves = order(g, raw)
        var alpha = alphaIn; var beta = betaIn
        val maxing = g.turn == me
        var bestV = if (maxing) -INF else INF
        for (m in moves) {
            val c = g.copy(); c.play(m, check = false, settle = false)
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
