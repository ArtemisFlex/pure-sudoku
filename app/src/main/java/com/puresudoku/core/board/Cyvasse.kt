package com.puresudoku.core.board

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Cyvasse, the battle game played across Westeros and Essos in A Song of Ice and Fire.
 * George R. R. Martin never wrote its rules down; these follow the most played fan rules, "Cyvasse,
 * rules adapted by A. Marston Croft" (Ill Gotten Games, the version on Tabletopia and BoardGameGeek).
 *
 * - Each side (Alabaster and Onyx) has 3 rabble, 3 spearmen, 3 crossbowmen, 3 light horses, 2 heavy horses,
 *   2 elephants, a catapult, a trebuchet, a dragon and a king, plus a keep and 3 mountains.
 * - Behind a screen, each player sets 3 mountains and a keep on their half (the keep not in the row by the screen),
 *   then stands pieces on any of the "home tiles" around the keep. The rest wait unseen in reserve.
 * - A turn is one move, or bringing a piece out of reserve onto an empty home tile (only while your keep stands).
 *   Pieces capture by moving onto an enemy and can't pass through pieces, mountains or a keep.
 *   A piece entering water stops there (the dragon excepted). Crossbowmen may leap into any empty forest.
 * - Hold two of the enemy's home tiles and their keep falls: it and all their reserves are captured.
 * - Capture the enemy king, on the board or in a fallen keep's reserves, to win.
 *
 * The board is 10 by 10 with fixed water and forest tiles, the same from both sides. Rows 0-4 are Onyx's half
 * (side 1, top), rows 5-9 Alabaster's (side 0, bottom).
 */
class Cyvasse {
    companion object {
        const val N = 10
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
        val ARMY = intArrayOf(0, 3, 3, 3, 3, 2, 2, 1, 1, 1, 1)
        const val MOUNTAINS = 3
        /** Moves without a capture or a piece brought out before the game is drawn. */
        const val QUIET_LIMIT = 160

        val NAMES = arrayOf("", "Rabble", "Spearman", "Crossbowman", "Light Horse", "Heavy Horse", "Elephant", "Catapult", "Trebuchet", "Dragon", "King")
        val PLURALS = arrayOf("", "Rabble", "Spearmen", "Crossbowmen", "Light Horses", "Heavy Horses", "Elephants", "Catapults", "Trebuchets", "Dragons", "Kings")
        val MOVES_TEXT = arrayOf(
            "",
            "One tile across or along. After moving a rabble you may move a second rabble.",
            "One tile across or along, or swap places with a friendly piece beside it.",
            "One tile diagonally, or leap straight into any empty forest.",
            "Any distance across or along. It may ride off one edge and on from the opposite edge.",
            "Any distance across or along, riding past its own pieces.",
            "Any distance across or along. It can only be taken while another enemy piece stands beside it.",
            "Any distance diagonally. It may take two enemy pieces in one move, one behind the other.",
            "Any distance diagonally. When it captures it shoots over any pieces in the way.",
            "Any distance in any of the eight directions, flying over mountains and water.",
            "One tile in any direction. Lose your king and you lose the game.",
        )
        /** Rough worth of each piece, for the computer and the coach. */
        val VALUE = intArrayOf(0, 10, 16, 20, 30, 32, 36, 40, 44, 90, 0)

        /** The fixed lakes and woods, matching under a half turn so both halves are alike. */
        val TERRAIN: IntArray = IntArray(CELLS).also { t ->
            for (c in intArrayOf(43, 44, 55, 56, 38, 61)) t[c] = WATER
            for (c in intArrayOf(12, 87, 26, 73, 48, 51)) t[c] = FOREST
        }
        val FORESTS: IntArray = (0 until CELLS).filter { TERRAIN[it] == FOREST }.toIntArray()

        fun piece(side: Int, type: Int) = side * 10 + type
        fun ownerOf(v: Int) = if (v == 0) -1 else (v - 1) / 10
        fun typeOf(v: Int) = if (v == 0) 0 else (v - 1) % 10 + 1
        fun row(c: Int) = c / N
        fun col(c: Int) = c % N
        /** Alabaster (0) holds rows 5-9, Onyx (1) rows 0-4. */
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
        const val K_MOVE = 0; const val K_DEPLOY = 1; const val K_SWAP = 2; const val K_PASS = 3
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
            ia("keepAt", 2).copyInto(g.keepAt)
            g.keepGone[0] = o.optBoolean("gone0"); g.keepGone[1] = o.optBoolean("gone1")
            ia("res0", 11).copyInto(g.reserve[0]); ia("res1", 11).copyInto(g.reserve[1])
            ia("cap0", 11).copyInto(g.captured[0]); ia("cap1", 11).copyInto(g.captured[1])
            g.phase = o.optInt("phase"); g.placer = o.optInt("placer"); g.turn = o.optInt("turn"); g.first = o.optInt("first")
            g.winner = o.optInt("winner", -1); g.bonusRabble = o.optInt("bonus", -1); g.quiet = o.optInt("quiet"); g.plies = o.optInt("plies")
            g.lastMove = o.optInt("last", -1); g.lastBy = o.optInt("lastBy", -1); g.lastText = o.optString("lastText", "")
            g.keepFell = o.optInt("keepFell", -1)
            return g
        }
    }

    /** The piece on each tile (0 = none; see [piece]). */
    val board = IntArray(CELLS)
    /** What stands on each tile that isn't a piece: a mountain or a keep. */
    val block = IntArray(CELLS)
    val keepAt = intArrayOf(-1, -1)
    val keepGone = BooleanArray(2)
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
        return JSONObject().put("board", a(board)).put("block", a(block)).put("keepAt", a(keepAt))
            .put("gone0", keepGone[0]).put("gone1", keepGone[1])
            .put("res0", a(reserve[0])).put("res1", a(reserve[1])).put("cap0", a(captured[0])).put("cap1", a(captured[1]))
            .put("phase", phase).put("placer", placer).put("turn", turn).put("first", first).put("winner", winner)
            .put("bonus", bonusRabble).put("quiet", quiet).put("plies", plies)
            .put("last", lastMove).put("lastBy", lastBy).put("lastText", lastText).put("keepFell", keepFell)
    }

    fun copy(): Cyvasse {
        val g = Cyvasse()
        board.copyInto(g.board); block.copyInto(g.block); keepAt.copyInto(g.keepAt)
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
            val wrap = d[0] == 0 || d[1] == 0
            var r = row(k); var c = col(k)
            for (step in 1 until N) {
                r += d[0]; c += d[1]
                if (wrap) { r = (r + N) % N; c = (c + N) % N } else if (r !in 0 until N || c !in 0 until N) break
                val t = r * N + c
                if (block[t] != 0 || ownerOf(board[t]) == side) break
                if (ownerOf(board[t]) == 1 - side || !inHalf(side, t)) { open++; break }
            }
        }
        return open
    }

    // =================================================================  setting up, behind the screen

    fun canMountain(side: Int, c: Int) = phase == SETUP && side == placer && c in 0 until CELLS && inHalf(side, c) &&
        TERRAIN[c] == PLAIN && block[c] == 0 && board[c] == 0 && mountainsPlaced(side) < MOUNTAINS

    /** Sets a mountain on [c], or lifts the one there. */
    fun toggleMountain(side: Int, c: Int): Boolean {
        if (phase != SETUP || side != placer || c !in 0 until CELLS || !inHalf(side, c)) return false
        if (block[c] == MOUNTAIN) { block[c] = 0; return true }
        if (!canMountain(side, c)) return false
        block[c] = MOUNTAIN
        return true
    }

    fun canKeep(side: Int, c: Int) = phase == SETUP && side == placer && c in 0 until CELLS && inHalf(side, c) && row(c) != frontRow(side) &&
        TERRAIN[c] == PLAIN && block[c] == 0 && board[c] == 0

    /** Sets (or moves) the keep. Pieces left off the new home tiles go back to reserve. */
    fun placeKeep(side: Int, c: Int): Boolean {
        if (!canKeep(side, c)) return false
        if (keepAt[side] >= 0) block[keepAt[side]] = 0
        keepAt[side] = c; block[c] = KEEP
        for (x in 0 until CELLS) if (ownerOf(board[x]) == side && !isHome(side, x)) liftPiece(side, x)
        return true
    }

    fun canStand(side: Int, type: Int, c: Int) = phase == SETUP && side == placer && reserve[side][type] > 0 && board[c] == 0 && isHome(side, c)

    fun standPiece(side: Int, type: Int, c: Int): Boolean {
        if (!canStand(side, type, c)) return false
        board[c] = piece(side, type); reserve[side][type]--
        return true
    }

    fun liftPiece(side: Int, c: Int): Boolean {
        if (phase != SETUP || ownerOf(board[c]) != side) return false
        reserve[side][typeOf(board[c])]++; board[c] = 0
        return true
    }

    fun clearSetup(side: Int) {
        if (phase != SETUP) return
        for (c in 0 until CELLS) if (inHalf(side, c)) { if (ownerOf(board[c]) == side) liftPiece(side, c); block[c] = 0 }
        keepAt[side] = -1
    }

    fun setupDone(side: Int) = keepAt[side] >= 0 && mountainsPlaced(side) == MOUNTAINS

    /**
     * A sensible hidden setup for [side]: the keep a few rows back, mountains guarding it on careful levels,
     * and the home tiles filled with the king, the dragon and a mix of the army. [level] 0-5.
     */
    fun randomSetup(side: Int, rnd: Random, level: Int = 3) {
        // careful lords try a few layouts and keep the one that leaves the king least exposed
        val tries = when { level <= 1 -> 1; level <= 3 -> 40; else -> 80 }
        var best: Cyvasse? = null; var bestScore = Int.MAX_VALUE
        repeat(tries) {
            val c = copy(); c.oneSetup(side, rnd, level)
            val score = c.openLines(side) * 10 + (if (c.reserve[side][KING] > 0) 3 else 0) + rnd.nextInt(6)
            if (score < bestScore) { bestScore = score; best = c }
            if (bestScore < 10 && tries > 1 && it >= 8) return@repeat
        }
        val b = best ?: return
        b.board.copyInto(board); b.block.copyInto(block); b.keepAt.copyInto(keepAt); b.reserve[side].copyInto(reserve[side])
    }

    private fun oneSetup(side: Int, rnd: Random, level: Int) {
        val was = placer
        placer = side
        clearSetup(side)
        val back = if (side == 0) intArrayOf(7, 8) else intArrayOf(1, 2)
        val keepCells = (0 until CELLS).filter { canKeep(side, it) && (level <= 0 || (row(it) in back && col(it) in 2..7)) }
            .ifEmpty { (0 until CELLS).filter { canKeep(side, it) } }
        placeKeep(side, keepCells.random(rnd))
        val k = keepAt[side]
        val toward = if (side == 0) -1 else 1
        // careful lords shield the keep's face with mountains, so fewer home tiles are open to raiders
        if (level >= 2) {
            val guard = NEIGHBOURS[k].filter { row(it) == row(k) + toward && TERRAIN[it] == PLAIN }.shuffled(rnd)
            for (c in guard.take(if (level >= 4) 2 else 1)) toggleMountain(side, c)
        }
        var guard = 0
        while (mountainsPlaced(side) < MOUNTAINS && guard++ < 500) {
            val c = rnd.nextInt(CELLS)
            if (canMountain(side, c) && c !in NEIGHBOURS[k] && !(level >= 2 && row(c) == frontRow(side) && rnd.nextBoolean())) toggleMountain(side, c)
        }
        // stand pieces on the home tiles: the king somewhere among them (or, for the careful, hidden in reserve),
        // the strongest pieces round it
        val homes = homeTiles(side).toMutableList().also { it.shuffle(rnd) }
        val kingHidden = level >= 2 && rnd.nextInt(3) == 0
        if (homes.isNotEmpty() && !kingHidden) standPiece(side, KING, homes.removeAt(0))
        val wanted = mutableListOf(DRAGON, ELEPHANT, HEAVY_HORSE, LIGHT_HORSE, CROSSBOW, SPEARMAN, TREBUCHET, CATAPULT, RABBLE, LIGHT_HORSE, SPEARMAN)
        if (level <= 1) wanted.shuffle(rnd)
        val leave = if (level <= 1) rnd.nextInt(3) else 0
        for (t in wanted) {
            if (homes.size <= leave) break
            val c = homes.removeAt(homes.size - 1)
            if (!standPiece(side, t, c)) homes.add(c)
        }
        placer = was
    }

    /** The player behind the screen is finished: the other sets up, or the screen comes down. */
    fun ready(side: Int, coin: Boolean): Boolean {
        if (phase != SETUP || side != placer || !setupDone(side)) return false
        if (side == 0) placer = 1
        else { phase = BATTLE; first = if (coin) 1 else 0; turn = first }
        return true
    }

    // =================================================================  moves

    /** Can [side]'s piece moving from [from] take the enemy piece on [target]? An elephant needs another attacker beside it. */
    private fun canTake(side: Int, from: Int, target: Int, alsoGone: Int = -1): Boolean {
        val v = board[target]
        if (ownerOf(v) != 1 - side) return false
        if (typeOf(v) != ELEPHANT) return true
        for (n in NEIGHBOURS[target]) if (n != from && n != alsoGone && ownerOf(board[n]) == side) return true
        return false
    }

    fun legalMoves(): IntArray {
        if (phase != BATTLE) return IntArray(0)
        val s = turn
        val out = IntList()
        if (bonusRabble >= 0) {
            for (c in 0 until CELLS) if (c != bonusRabble && board[c] == piece(s, RABBLE)) pieceMoves(c, out)
            out.add(PASS)
            return out.toArray()
        }
        for (c in 0 until CELLS) if (ownerOf(board[c]) == s) pieceMoves(c, out)
        if (keepStands(s)) {
            val homes = homeTiles(s)
            for (t in TYPES) if (reserve[s][t] > 0) for (h in homes) if (board[h] == 0) out.add(mv(K_DEPLOY, t, h))
        }
        return out.toArray()
    }

    /** Every move of the piece on [from]. */
    fun pieceMoves(from: Int, out: IntList) {
        val v = board[from]; val s = ownerOf(v)
        when (typeOf(v)) {
            RABBLE -> steps(from, s, ORTHO, out)
            SPEARMAN -> {
                steps(from, s, ORTHO, out)
                for (n in NEIGHBOURS[from]) if (ownerOf(board[n]) == s) out.add(mv(K_SWAP, from, n))
            }
            CROSSBOW -> {
                steps(from, s, DIAG, out)
                val r = row(from); val k = col(from)
                for (f in FORESTS) {
                    if (board[f] != 0 || f == from) continue
                    val adjDiag = kotlin.math.abs(row(f) - r) == 1 && kotlin.math.abs(col(f) - k) == 1
                    if (!adjDiag) out.add(mv(K_MOVE, from, f))
                }
            }
            KING -> steps(from, s, ALL8, out)
            LIGHT_HORSE -> slide(from, s, ORTHO, out, wrap = true)
            HEAVY_HORSE -> slide(from, s, ORTHO, out, passFriends = true)
            ELEPHANT -> slide(from, s, ORTHO, out)
            DRAGON -> slide(from, s, ALL8, out, overMountains = true, flies = true)
            CATAPULT -> catapult(from, s, out)
            TREBUCHET -> trebuchet(from, s, out)
        }
    }

    private fun steps(from: Int, s: Int, dirs: Array<IntArray>, out: IntList) {
        val r = row(from); val k = col(from)
        for (d in dirs) {
            val nr = r + d[0]; val nc = k + d[1]
            if (nr !in 0 until N || nc !in 0 until N) continue
            val t = nr * N + nc
            if (block[t] != 0) continue
            val o = ownerOf(board[t])
            if (o == s) continue
            if (o == 1 - s && !canTake(s, from, t)) continue
            out.add(mv(K_MOVE, from, t))
        }
    }

    private fun slide(from: Int, s: Int, dirs: Array<IntArray>, out: IntList, wrap: Boolean = false, passFriends: Boolean = false,
                      overMountains: Boolean = false, flies: Boolean = false) {
        for (d in dirs) {
            var r = row(from); var k = col(from)
            for (step in 1 until N) {
                r += d[0]; k += d[1]
                if (wrap) { r = (r + N) % N; k = (k + N) % N } else if (r !in 0 until N || k !in 0 until N) break
                val t = r * N + k
                if (t == from) break
                if (block[t] != 0) { if (block[t] == MOUNTAIN && overMountains) continue else break }
                val v = board[t]
                val wet = TERRAIN[t] == WATER && !flies
                if (v != 0) {
                    if (ownerOf(v) == s) { if (passFriends && !wet) continue else break }
                    if (canTake(s, from, t)) out.add(mv(K_MOVE, from, t))
                    break
                }
                out.add(mv(K_MOVE, from, t))
                if (wet) break
            }
        }
    }

    /** Diagonal slides; on meeting an enemy it may take it, or take it and fly on to take a second one in line. */
    private fun catapult(from: Int, s: Int, out: IntList) {
        for (d in DIAG) {
            var r = row(from); var k = col(from)
            var first = -1
            while (true) {
                r += d[0]; k += d[1]
                if (r !in 0 until N || k !in 0 until N) break
                val t = r * N + k
                if (block[t] != 0) break
                val v = board[t]
                val wet = TERRAIN[t] == WATER
                if (first < 0) {
                    if (v == 0) { out.add(mv(K_MOVE, from, t)); if (wet) break; continue }
                    if (ownerOf(v) == s || !canTake(s, from, t)) break
                    out.add(mv(K_MOVE, from, t))
                    if (wet) break
                    first = t
                } else {
                    if (v == 0) { if (wet) break; continue }
                    if (ownerOf(v) == 1 - s && canTake(s, from, t, alsoGone = first)) out.add(mv(K_MOVE, from, t, first))
                    break
                }
            }
        }
    }

    /** Diagonal slides; when capturing it shoots over any pieces between (but not over mountains, keeps or water). */
    private fun trebuchet(from: Int, s: Int, out: IntList) {
        for (d in DIAG) {
            var r = row(from); var k = col(from)
            var clear = true
            while (true) {
                r += d[0]; k += d[1]
                if (r !in 0 until N || k !in 0 until N) break
                val t = r * N + k
                if (block[t] != 0) break
                val v = board[t]
                val wet = TERRAIN[t] == WATER
                if (v == 0) { if (clear) out.add(mv(K_MOVE, from, t)) }
                else { if (ownerOf(v) == 1 - s && canTake(s, from, t)) out.add(mv(K_MOVE, from, t)); clear = false }
                if (wet) break
            }
        }
    }

    fun isLegal(m: Int) = legalMoves().contains(m)

    /** The pieces a move would take (none for most moves). */
    fun victims(m: Int): List<Int> {
        if (kindOf(m) != K_MOVE) return emptyList()
        val out = ArrayList<Int>(2)
        if (viaOf(m) >= 0) out += viaOf(m)
        if (board[bOf(m)] != 0 && ownerOf(board[bOf(m)]) != ownerOf(board[aOf(m)])) out += bOf(m)
        return out
    }

    /** Plays [m] for the side to move. Returns false if it's not allowed here. */
    fun play(m: Int, check: Boolean = true): Boolean {
        if (phase != BATTLE) return false
        if (check && !isLegal(m)) return false
        val s = turn
        lastText = describe(m)
        val wasBonus = bonusRabble >= 0
        bonusRabble = -1
        keepFell = -1
        var eventful = false
        when (kindOf(m)) {
            K_MOVE -> {
                val a = aOf(m); val b = bOf(m)
                for (x in victims(m)) { capture(s, x); eventful = true }
                board[b] = board[a]; board[a] = 0
                if (!wasBonus && typeOf(board[b]) == RABBLE && winner < 0) bonusRabble = b
            }
            K_SWAP -> { val a = aOf(m); val b = bOf(m); val t = board[a]; board[a] = board[b]; board[b] = t }
            K_DEPLOY -> { board[bOf(m)] = piece(s, aOf(m)); reserve[s][aOf(m)]--; eventful = true }
            K_PASS -> Unit
        }
        lastMove = m; lastBy = s; plies++
        // two of our pieces on their home tiles: the enemy keep falls with everything in it
        val e = 1 - s
        if (winner < 0 && keepStands(e) && homeTiles(e).count { ownerOf(board[it]) == s } >= 2) {
            block[keepAt[e]] = 0; keepGone[e] = true; keepFell = e
            for (t in TYPES) { captured[s][t] += reserve[e][t]; reserve[e][t] = 0 }
            if (captured[s][KING] > 0) winner = s
            eventful = true
        }
        if (winner >= 0) { phase = OVER; bonusRabble = -1; return true }
        quiet = if (eventful) 0 else quiet + 1
        if (bonusRabble >= 0) {
            // only worth pausing for if another rabble can actually move
            val any = (0 until CELLS).any { c -> c != bonusRabble && board[c] == piece(s, RABBLE) && IntList().also { pieceMoves(c, it) }.size > 0 }
            if (!any) bonusRabble = -1
        }
        if (bonusRabble < 0) turn = e
        if (quiet >= QUIET_LIMIT) { winner = DRAWN; phase = OVER; return true }
        // a side with no move at all has lost (the search finds this itself, so it skips the check)
        if (check && legalMoves().isEmpty()) { winner = 1 - turn; phase = OVER }
        return true
    }

    private fun capture(s: Int, c: Int) {
        val t = typeOf(board[c])
        captured[s][t]++
        board[c] = 0
        if (t == KING) winner = s
    }

    /** "Light Horse e4 to e9, taking a Rabble". */
    fun describe(m: Int): String = when (kindOf(m)) {
        K_PASS -> "kept the second rabble still"
        K_DEPLOY -> "brought a ${NAMES[aOf(m)]} out to ${coord(bOf(m))}"
        K_SWAP -> "swapped a Spearman on ${coord(aOf(m))} with the ${NAMES[typeOf(board[bOf(m)])]} on ${coord(bOf(m))}"
        else -> {
            val who = NAMES[typeOf(board[aOf(m)])]
            val taken = victims(m).map { NAMES[typeOf(board[it])] }
            "moved a $who ${coord(aOf(m))} to ${coord(bOf(m))}" + when (taken.size) {
                0 -> ""
                1 -> ", taking ${if (taken[0] == "King") "the King" else "a " + taken[0]}"
                else -> ", taking a ${taken[0]} and a ${taken[1]}"
            }
        }
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
            for (m in moves) if (g.victims(m).any { Cyvasse.typeOf(g.board[it]) == Cyvasse.KING }) return m
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
            for (x in g.victims(m)) k += if (Cyvasse.typeOf(g.board[x]) == Cyvasse.KING) 10_000 else Cyvasse.VALUE[Cyvasse.typeOf(g.board[x])] * 10
            if (Cyvasse.kindOf(m) == Cyvasse.K_MOVE && g.isHome(1 - g.turn, Cyvasse.bOf(m))) k += 50
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
            // pressure on the enemy keep: one piece on a home tile is a real threat, two take it
            val e = 1 - side
            if (g.keepStands(e)) {
                val on = g.homeTiles(e).count { Cyvasse.ownerOf(g.board[it]) == side }
                if (on > 0) {
                    var theirs = 0
                    for (t in Cyvasse.TYPES) theirs += Cyvasse.VALUE[t] * g.reserve[e][t]
                    v += on * 14 + theirs / 6 + if (g.reserve[e][Cyvasse.KING] > 0) 120 else 0
                }
            }
            // a king alone in the open is a target; one guarded by its own pieces less so
            val k = (0 until Cyvasse.CELLS).firstOrNull { g.board[it] == Cyvasse.piece(side, Cyvasse.KING) }
            if (k != null) v += Cyvasse.NEIGHBOURS[k].count { Cyvasse.ownerOf(g.board[it]) == side } * 3 -
                Cyvasse.NEIGHBOURS[k].count { Cyvasse.ownerOf(g.board[it]) == e } * 12 - g.openLines(side) * 7
            score += sign * v
        }
        return score
    }
}
