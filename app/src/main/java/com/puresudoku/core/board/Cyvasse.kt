package com.puresudoku.core.board

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Cyvasse, PureGames' standard version, on a 10 by 11 field. Each army holds five rows; the middle row is a river of
 * water tiles. Both sides start from the same layout. Kings are in check like chess (no move may leave your own king
 * attackable; no legal move while in check is checkmate). Ground units that have crossed the river cannot step back
 * until they reach the enemy's back row. A rabble reaching the enemy's back row is promoted.
 */
class Cyvasse {
    companion object {
        /** Columns and rows. */
        const val W = 10
        const val H = 11
        const val CELLS = W * H
        /** The river row between the halves. */
        const val RIVER = H / 2
        const val SETUP = 0; const val BATTLE = 1; const val OVER = 2
        /** [winner] when the game ends without a victor. */
        const val DRAWN = 2
        /** A game drawn because only the two kings are left. */
        const val END_KINGS = 1
        /** A game drawn because the same position came up for the third time. */
        const val END_REPEAT = 2

        const val PLAIN = 0; const val WATER = 1; const val FOREST = 2
        const val MOUNTAIN = 1; const val KEEP = 2

        const val RABBLE = 1; const val SPEARMAN = 2; const val CROSSBOW = 3; const val LIGHT_HORSE = 4; const val HEAVY_HORSE = 5
        const val ELEPHANT = 6; const val CATAPULT = 7; const val TREBUCHET = 8; const val DRAGON = 9; const val KING = 10
        val TYPES = RABBLE..KING
        /** How many of each piece a side has (index = type). */
        val ARMY = intArrayOf(0, 6, 2, 2, 2, 2, 2, 1, 1, 1, 1)
        val ARMY_SIZE = ARMY.sum()
        /** What each player sets on their own half in an open-field game (with their fortress). */
        const val MOUNTAINS = 3
        const val WATERS = 0; const val FORESTS_PER_SIDE = 3

        val NAMES = arrayOf("", "Rabble", "Spearman", "Crossbowman", "Light Horse", "Heavy Horse", "Elephant", "Catapult", "Trebuchet", "Dragon", "King")
        val PLURALS = arrayOf("", "Rabble", "Spearmen", "Crossbowmen", "Light Horses", "Heavy Horses", "Elephants", "Catapults", "Trebuchets", "Dragons", "Kings")
        val MOVES_TEXT = arrayOf("", "Steps one tile forward or sideways, never back, and takes one tile diagonally forward. Promoted on the enemy's back row.",
            "Moves up to two tiles any way and takes only by landing on its victim.",
            "Steps one tile straight. As its move it can shoot one enemy diagonally beside it.",
            "Leaps one or two tiles diagonally, over pieces, and takes by landing. Water doesn't hold it.",
            "Rides up to three tiles diagonally; pieces block it and water ends the ride. Anything but a rabble can take it.",
            "Moves one tile any way. Only kings, dragons, heavy horses, elephants, catapults and trebuchets can take it.",
            "Steps one tile straight. Shoots any distance diagonally at the first piece in line. Never enters a fortress.",
            "Steps one tile straight. Shoots any distance straight at the first piece in line. Never enters a fortress.",
            "Flies any distance in a line over water, forest and mountains, and may land on a mountain; pieces and fortresses stop it. Cannot attack into forests. Its attack brings a fortress down and takes the piece inside, unless it's a king.",
            "Moves one tile any way and can take any piece. It can never be left in check; checkmate ends the game.")
        /** Rough worth of each piece, for the computer and the coach. */
        val VALUE = intArrayOf(0, 10, 22, 26, 28, 34, 40, 44, 46, 90, 0)

        /** New games start on plain grass. */
        val TERRAIN: IntArray = IntArray(CELLS)

        fun newSetup(): Cyvasse = Cyvasse().also { it.terrain.fill(PLAIN) }

        /** An open-field game: the standard armies and the river, with each player placing their own mountains, forests and fortress. */
        fun openField(): Cyvasse = standard().also { it.startOpenField() }

        /**
         * The standard field, top (Onyx's back row) to bottom (Alabaster's). Pieces: T trebuchet, H heavy horse,
         * L light horse, S spearman, D dragon, K king, C catapult, E elephant, X crossbowman, R rabble (rows above the
         * river are Onyx's). Terrain: f forest, ~ water, M mountain, F fortress, . grass.
         */
        val LAYOUT = arrayOf(
            "T H L S D K S L H C",
            "E X R R R R R R X E",
            ". . . . . . . . . .",
            "F . . . . . . . . f",
            "M f . . M f . . . f",
            "~ ~ ~ ~ ~ ~ ~ ~ ~ ~",
            "f f . . f M . . . M",
            "F . . . . . . . . f",
            ". . . . . . . . . .",
            "E X R R R R R R X E",
            "T H L S D K S L H C")
        private val LETTERS = mapOf('R' to RABBLE, 'S' to SPEARMAN, 'X' to CROSSBOW, 'L' to LIGHT_HORSE, 'H' to HEAVY_HORSE,
            'E' to ELEPHANT, 'C' to CATAPULT, 'T' to TREBUCHET, 'D' to DRAGON, 'K' to KING)

        fun standard(): Cyvasse {
            val g = Cyvasse()
            g.terrain.fill(PLAIN)
            for (r in 0 until H) {
                val cells = LAYOUT[r].split(' ')
                for (c in 0 until W) {
                    val i = r * W + c
                    val side = if (r < RIVER) 1 else 0
                    when (val ch = cells[c][0]) {
                        'f' -> g.terrain[i] = FOREST
                        '~' -> g.terrain[i] = WATER
                        'M' -> g.block[i] = MOUNTAIN
                        'F' -> { g.block[i] = KEEP; g.keepAt[side] = i }
                        '.' -> Unit
                        else -> g.board[i] = piece(side, LETTERS.getValue(ch))
                    }
                }
            }
            for (s in 0..1) g.reserve[s].fill(0)
            g.phase = BATTLE; g.turn = 0; g.first = 0
            return g
        }

        fun piece(side: Int, type: Int) = side * 10 + type
        fun ownerOf(v: Int) = if (v == 0) -1 else (v - 1) / 10
        fun typeOf(v: Int) = if (v == 0) 0 else (v - 1) % 10 + 1
        fun row(c: Int) = c / W
        fun col(c: Int) = c % W
        fun on(r: Int, c: Int) = r in 0 until H && c in 0 until W
        /** Alabaster (0) holds the rows below the river, Onyx (1) those above it; the river row is no one's. */
        fun inHalf(side: Int, c: Int) = if (side == 0) row(c) > RIVER else row(c) < RIVER
        fun coord(c: Int) = "${'a' + col(c)}${H - row(c)}"
        fun sideName(side: Int) = if (side == 0) "Alabaster" else "Onyx"

        val ORTHO = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
        val DIAG = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1))
        val ALL8 = ORTHO + DIAG

        fun neighbours(c: Int): List<Int> {
            val r = row(c); val k = col(c)
            val out = ArrayList<Int>(8)
            for (d in ALL8) { val nr = r + d[0]; val nc = k + d[1]; if (on(nr, nc)) out += nr * W + nc }
            return out
        }
        val NEIGHBOURS: Array<IntArray> = Array(CELLS) { neighbours(it).toIntArray() }
        /** The four diagonal neighbours: where a crossbowman shoots and a rabble takes. */
        val DIAGONALS: Array<IntArray> = Array(CELLS) { c -> DIAG.mapNotNull { d -> (row(c) + d[0] to col(c) + d[1]).takeIf { on(it.first, it.second) }?.let { it.first * W + it.second } }.toIntArray() }

        /** Whether a piece of [attacker]'s type may take one of [defender]'s (sides aside). */
        fun beats(attacker: Int, defender: Int): Boolean = when {
            attacker == 0 || defender == 0 -> false
            attacker == KING -> true
            defender == ELEPHANT -> attacker in intArrayOf(DRAGON, HEAVY_HORSE, ELEPHANT, TREBUCHET, CATAPULT)
            defender == HEAVY_HORSE || defender == DRAGON -> attacker != RABBLE
            else -> true
        }

        // ---- moves, packed in one Int: kind | a | b | via ----
        const val K_MOVE = 0; const val K_DEPLOY = 1; const val K_SWAP = 2; const val K_PASS = 3; const val K_RANGED = 4
        /** A rabble's move onto the enemy's back row; [via] holds the piece it becomes. */
        const val K_PROMO = 5
        /** What a rabble may become: anything but a king or another rabble. */
        val PROMOTIONS = SPEARMAN..DRAGON
        /** [a] is the start (or the piece type when bringing one out), [b] the end, [via] the last tile before it. */
        fun mv(kind: Int, a: Int, b: Int, via: Int = -1) = (kind shl 24) or (a shl 16) or (b shl 8) or (via + 1)
        fun kindOf(m: Int) = m ushr 24
        fun aOf(m: Int) = (m ushr 16) and 0xFF
        fun bOf(m: Int) = (m ushr 8) and 0xFF
        fun viaOf(m: Int) = (m and 0xFF) - 1
        val PASS = mv(K_PASS, 0, 0)

        fun fromJson(o: JSONObject): Cyvasse {
            // a game saved on the old 8 by 8 board can't carry over: start the new field instead
            if (o.optJSONArray("board")?.length() != CELLS) return standard()
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
            g.keepFell = o.optInt("keepFell", -1); g.stalemate = o.optBoolean("stalemate"); g.openField = o.optBoolean("open"); g.drawnBy = o.optInt("drawnBy")
            o.optJSONArray("seen")?.let { a -> for (i in 0 until a.length() / 2) g.seen[a.getInt(2 * i)] = a.getInt(2 * i + 1) }
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
    /** Water delay travels with the piece at each cell. */
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
    /** The game ended in stalemate ([winner] is whoever had taken more pieces, or [DRAWN]). */
    var stalemate = false
    /** Why a drawn game ended, when it wasn't a stalemate: [END_KINGS] or [END_REPEAT] (else 0). */
    var drawnBy = 0
    /**
     * The positions seen since the last capture, promotion or fallen fortress (which can never come back), with how
     * often each was seen: the same position a third time is a draw, as in chess.
     */
    val seen = HashMap<Int, Int>()
    /** Each player placed their own terrain and fortress before the battle. */
    var openField = false

    /** Clears the standard field's terrain (keeping the river and the armies) and opens the setup behind the screen. */
    fun startOpenField() {
        for (c in 0 until CELLS) { terrain[c] = if (row(c) == RIVER) WATER else PLAIN; block[c] = 0 }
        keepAt[0] = -1; keepAt[1] = -1; keepGone[0] = false; keepGone[1] = false; fortHits.fill(0)
        phase = SETUP; placer = 0; turn = 0; first = 0; openField = true
    }

    fun toJson(): JSONObject {
        fun a(x: IntArray) = JSONArray().also { j -> x.forEach { j.put(it) } }
        return JSONObject().put("board", a(board)).put("block", a(block)).put("terrain", a(terrain)).put("keepAt", a(keepAt))
            .put("gone0", keepGone[0]).put("gone1", keepGone[1]).put("waterWait", a(waterWait)).put("facing", a(facing)).put("fortHits", a(fortHits)).put("crossed", a(crossed)).put("promoted", a(promoted))
            .put("res0", a(reserve[0])).put("res1", a(reserve[1])).put("cap0", a(captured[0])).put("cap1", a(captured[1]))
            .put("phase", phase).put("placer", placer).put("turn", turn).put("first", first).put("winner", winner)
            .put("bonus", bonusRabble).put("quiet", quiet).put("plies", plies)
            .put("last", lastMove).put("lastBy", lastBy).put("lastText", lastText).put("keepFell", keepFell).put("stalemate", stalemate).put("open", openField).put("drawnBy", drawnBy)
            .put("seen", JSONArray().also { j -> for ((k, v) in seen) { j.put(k); j.put(v) } })
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
        g.lastMove = lastMove; g.lastBy = lastBy; g.lastText = lastText; g.keepFell = keepFell; g.stalemate = stalemate; g.openField = openField; g.drawnBy = drawnBy
        return g
    }

    fun keepStands(side: Int) = keepAt[side] >= 0 && !keepGone[side]

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
     * enemy's half, before anything of its own (or a mountain or keep) blocks them. A king in reserve has none.
     */
    fun openLines(side: Int): Int {
        val k = (0 until CELLS).firstOrNull { board[it] == piece(side, KING) } ?: return 0
        var open = 0
        for (d in ALL8) {
            var r = row(k); var c = col(k)
            while (true) {
                r += d[0]; c += d[1]
                if (!on(r, c)) break
                val t = r * W + c
                if (block[t] != 0 || ownerOf(board[t]) == side) break
                if (ownerOf(board[t]) == 1 - side || inHalf(1 - side, t)) { open++; break }
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

    /** Sets (or moves) the keep. */
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

    /** Clears [side]'s terrain and fortress (and, outside an open-field game, lifts its pieces). */
    fun clearSetup(side: Int) {
        if (phase != SETUP) return
        for (c in 0 until CELLS) if (inHalf(side, c)) { if (!openField && ownerOf(board[c]) == side) liftPiece(side, c); block[c] = 0; terrain[c] = PLAIN }
        keepAt[side] = -1
    }

    fun setupDone(side: Int) = keepAt[side] >= 0 && mountainsPlaced(side) == MOUNTAINS &&
        terrainPlaced(side, WATER) == WATERS && terrainPlaced(side, FOREST) == FORESTS_PER_SIDE && reserveCount(side) == 0

    /** Random complete setup: terrain, fortress and (if any wait in reserve) the army. */
    fun randomSetup(side: Int, rnd: Random, level: Int = 3) {
        val old = placer; placer = side; clearSetup(side)
        val cells = (0 until CELLS).filter { inHalf(side, it) && board[it] == 0 }.shuffled(rnd)
        var i = 0
        repeat(WATERS) { toggleTerrain(side, WATER, cells[i++]) }
        repeat(FORESTS_PER_SIDE) { toggleTerrain(side, FOREST, cells[i++]) }
        repeat(MOUNTAINS) { toggleMountain(side, cells[i++]) }
        placeKeep(side, cells[i++])
        val open = cells.filter { block[it] != MOUNTAIN && block[it] != KEEP }.shuffled(rnd)
        val army = TYPES.flatMap { t -> List(ARMY[t]) { t } }
        for ((t, c) in army.zip(open)) standPiece(side, t, c)
        placer = old
    }

    /** The player behind the screen is finished: the other sets up, or the screen comes down. */
    fun ready(side: Int, coin: Boolean): Boolean {
        if (phase != SETUP || side != placer || !setupDone(side)) return false
        if (side == 0) placer = 1
        // an open-field game keeps the standard rule: Alabaster moves first
        else { phase = BATTLE; first = if (coin && !openField) 1 else 0; turn = first }
        return true
    }

    // =================================================================  battle

    /** A standing (not ruined) fortress on [c], or -1. */
    fun fortAt(c: Int): Int = (0..1).firstOrNull { keepAt[it] == c && !keepGone[it] } ?: -1

    /** How far [side] has advanced on [c]'s row: 0 on its own back row, [H] - 1 on the enemy's. */
    fun progress(side: Int, c: Int) = if (side == 0) H - 1 - row(c) else row(c)

    /** The enemy's back row, where a rabble is promoted. */
    fun promotionRow(side: Int) = if (side == 0) 0 else H - 1

    private fun isSiege(type: Int) = type == CATAPULT || type == TREBUCHET

    /**
     * Whether the piece on [from] may capture the piece on [target]. Never your own. A piece in a forest can't attack
     * at all, and one in a forest can't be hit by catapults, trebuchets or dragons (others may still land on it).
     */
    fun canTake(from: Int, target: Int): Boolean {
        if (board[from] == 0 || board[target] == 0 || ownerOf(board[from]) == ownerOf(board[target])) return false
        if (terrain[from] == FOREST) return false
        val attacker = typeOf(board[from])
        if (terrain[target] == FOREST && (attacker == CATAPULT || attacker == TREBUCHET || attacker == DRAGON)) return false
        return beats(attacker, typeOf(board[target]))
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
        for (i in 0 until out.size) {
            val m = out[i]
            val kind = kindOf(m)
            if (kind == K_PASS) continue
            if (bOf(m) == k) return true
        }
        return false
    }

    fun pieceMoves(from: Int, out: IntList) {
        val type = typeOf(board[from]); if (type == 0 || waterWait[from] > 0) return
        val side = ownerOf(board[from])
        // once across the river, a ground unit only goes forward or sideways, until it has touched the enemy's back row
        val lock = type != DRAGON && crossed[from] == 1 && promoted[from] == 0
        fun back(t: Int) = lock && progress(side, t) < progress(side, from)
        val r0 = row(from); val c0 = col(from)
        when (type) {
            DRAGON -> {
                // straight lines, over water, forest and mountains (it may land on one); pieces and fortresses stop it
                for (d in ALL8) {
                    var r = r0; var c = c0; var lastEmpty = from
                    while (true) {
                        r += d[0]; c += d[1]
                        if (!on(r, c)) break
                        val t = r * W + c
                        val v = board[t]
                        if (fortAt(t) >= 0) {
                            // it may only come down on a fortress to attack the piece inside
                            if (v != 0 && canTake(from, t)) out.add(mv(K_MOVE, from, t, lastEmpty))
                            break
                        }
                        if (v != 0) {
                            if (canTake(from, t)) out.add(mv(K_MOVE, from, t, lastEmpty))
                            break
                        }
                        out.add(mv(K_MOVE, from, t, lastEmpty)); lastEmpty = t
                    }
                }
            }
            LIGHT_HORSE -> for (d in DIAG) {
                // one or two tiles diagonally, leaping whatever stands on the first, except a mountain or fortress
                var lastEmpty = from
                for (dist in 1..2) {
                    val r = r0 + d[0] * dist; val c = c0 + d[1] * dist
                    if (!on(r, c)) break
                    val t = r * W + c
                    if (block[t] == MOUNTAIN) break
                    if (!back(t)) {
                        if (board[t] == 0) out.add(mv(K_MOVE, from, t, lastEmpty))
                        else if (canTake(from, t)) out.add(mv(K_MOVE, from, t, lastEmpty))
                    }
                    if (fortAt(t) >= 0) break
                    if (board[t] == 0) lastEmpty = t
                }
            }
            HEAVY_HORSE -> for (d in DIAG) {
                // up to three tiles diagonally; it can't pass a piece, and water or a fortress ends the ride
                var lastEmpty = from
                for (dist in 1..3) {
                    val r = r0 + d[0] * dist; val c = c0 + d[1] * dist
                    if (!on(r, c)) break
                    val t = r * W + c
                    if (block[t] == MOUNTAIN) break
                    if (board[t] != 0) { if (!back(t) && canTake(from, t)) out.add(mv(K_MOVE, from, t, lastEmpty)); break }
                    if (!back(t)) out.add(mv(K_MOVE, from, t, lastEmpty))
                    if (fortAt(t) >= 0 || terrain[t] == WATER) break
                    lastEmpty = t
                }
            }
            CROSSBOW -> {
                for (d in ORTHO) {
                    val r = r0 + d[0]; val c = c0 + d[1]
                    if (!on(r, c)) continue
                    val t = r * W + c
                    if (block[t] == MOUNTAIN || board[t] != 0 || back(t)) continue
                    out.add(mv(K_MOVE, from, t, from))
                }
                // as its whole move, it may shoot one enemy diagonally beside it (not from a forest)
                for (t in DIAGONALS[from]) if (canTake(from, t)) out.add(mv(K_RANGED, from, t))
            }
            CATAPULT, TREBUCHET -> {
                // one step straight onto an empty tile, never into a fortress (water holds it a turn, as for any ground unit)
                for (d in ORTHO) {
                    val r = r0 + d[0]; val c = c0 + d[1]
                    if (!on(r, c)) continue
                    val t = r * W + c
                    if (block[t] != 0 || board[t] != 0 || back(t)) continue
                    out.add(mv(K_MOVE, from, t, from))
                }
                // shots: any distance, diagonally (catapult) or straight (trebuchet), at the first piece in line; not from a forest
                if (terrain[from] == FOREST) return
                for (d in if (type == CATAPULT) DIAG else ORTHO) {
                    var r = r0; var c = c0
                    while (true) {
                        r += d[0]; c += d[1]
                        if (!on(r, c)) break
                        val t = r * W + c
                        val v = board[t]
                        // a mountain stops the shot, though a dragon perched on it can be hit
                        if (block[t] == MOUNTAIN) { if (v != 0 && canTake(from, t)) out.add(mv(K_RANGED, from, t)); break }
                        if (v == 0) { if (fortAt(t) >= 0) break else continue }
                        if (canTake(from, t)) out.add(mv(K_RANGED, from, t))
                        break
                    }
                }
            }
            RABBLE -> {
                // one tile forward or sideways (never back) onto an empty tile; it takes one tile diagonally forward
                for (d in ORTHO) {
                    val r = r0 + d[0]; val c = c0 + d[1]
                    if (!on(r, c)) continue
                    val t = r * W + c
                    if (block[t] == MOUNTAIN || board[t] != 0 || progress(side, t) < progress(side, from)) continue
                    emitArrival(from, t, from, type, side) { out.add(it) }
                }
                for (t in DIAGONALS[from]) if (progress(side, t) > progress(side, from) && canTake(from, t)) emitArrival(from, t, from, type, side) { out.add(it) }
            }
            else -> {
                // elephant and king walk one tile any way; a spearman up to two
                val limit = if (type == SPEARMAN) 2 else 1
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
                        if (lock && progress(side, t) < progress(side, step.cell)) continue
                        val fort = fortAt(t) >= 0
                        val left = step.left - 1
                        if (board[t] != 0) {
                            if (canTake(from, t)) emitArrival(from, t, step.cell, type, side, ::emit)
                        } else {
                            emitArrival(from, t, step.cell, type, side, ::emit)
                            // a fortress or water ends the move
                            if (!fort && terrain[t] != WATER && left > 0 && left > best[t]) { best[t] = left; queue.add(Step(t, left)) }
                        }
                    }
                }
            }
        }
    }

    /** Adds the move from [from] to [t] (reached from [via]); a rabble reaching the enemy's back row becomes one move per promotion. */
    private fun emitArrival(from: Int, t: Int, via: Int, type: Int, side: Int, emit: (Int) -> Unit) {
        if (type == RABBLE && row(t) == promotionRow(side) && fortAt(t) < 0) {
            for (p in PROMOTIONS) emit(mv(K_PROMO, from, t, p))
        } else emit(mv(K_MOVE, from, t, via))
    }

    fun isLegal(m: Int) = legalMoves().contains(m)

    /** The enemy pieces [m] attacks (a piece in a fortress is attacked, though the fortress may save it). */
    fun victims(m: Int): List<Int> {
        val kind = kindOf(m); val a = aOf(m); val b = bOf(m)
        if (kind !in intArrayOf(K_MOVE, K_RANGED, K_PROMO) || board[a] == 0) return emptyList()
        return if (board[b] != 0 && ownerOf(board[b]) != ownerOf(board[a])) listOf(b) else emptyList()
    }

    /**
     * An attack on [b]: a standing fortress takes the blow (and may fall); otherwise the piece there is taken. A dragon
     * strikes twice at once: it brings the fortress down and takes the piece inside too, unless that's a king.
     * Returns true when the defender survives (the attacker stays outside).
     */
    private fun strike(s: Int, attackerType: Int, b: Int): Boolean {
        val victim = board[b]
        val fort = fortAt(b)
        if (fort >= 0) {
            fortHits[fort]++
            val dragon = attackerType == DRAGON
            // a fortress holds against one attack; with a king inside, two (but a dragon brings it down at once)
            val hitsNeeded = if (typeOf(victim) == KING && !dragon) 2 else 1
            if (fortHits[fort] < hitsNeeded) { lastText += "; the fortress at ${coord(b)} holds"; return true }
            keepGone[fort] = true; keepFell = fort; block[b] = 0; terrain[b] = PLAIN
            // a ruined fortress counts as a piece taken
            captured[s][0]++
            if (!dragon || typeOf(victim) == KING) { lastText += "; the fortress at ${coord(b)} falls and the defender survives"; return true }
            lastText += "; the fortress at ${coord(b)} falls"
        }
        val capturedSide = ownerOf(victim); val t = typeOf(victim)
        captured[1 - capturedSide][t]++; clearCell(b)
        if (t == KING) winner = 1 - capturedSide
        return false
    }

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
        val moverType = typeOf(board[a])
        val takenBefore = takenBy(0) + takenBy(1)
        when (kind) {
            K_RANGED -> if (board[b] != 0) strike(s, moverType, b)
            K_MOVE, K_PROMO -> {
                val repelled = board[b] != 0 && strike(s, moverType, b)
                val destination = if (repelled) viaOf(m).takeIf { it >= 0 && kind == K_MOVE } ?: a else b
                if (destination != a) {
                    val moving = board[a]; val wasCrossed = crossed[a]; val wasPromoted = promoted[a]
                    clearCell(a)
                    board[destination] = if (kind == K_PROMO) piece(s, viaOf(m)) else moving
                    // promoted, or having touched the enemy's back row: free to move back again
                    promoted[destination] = if (kind == K_PROMO || row(destination) == promotionRow(s)) 1 else wasPromoted
                    crossed[destination] = if (wasCrossed == 1 || inHalf(1 - s, destination)) 1 else 0
                    waterWait[destination] = if (terrain[destination] == WATER && typeOf(moving) != DRAGON && typeOf(moving) != LIGHT_HORSE) 1 else 0
                }
            }
        }
        lastMove = m; lastBy = s; plies++; bonusRabble = -1
        if (winner >= 0) { phase = OVER; return true }
        turn = 1 - s
        if (settle) {
            // endless games: two bare kings can never finish, nor can players who keep repeating themselves
            if ((0 until CELLS).none { board[it] != 0 && typeOf(board[it]) != KING }) {
                winner = DRAWN; drawnBy = END_KINGS; phase = OVER; lastText += ": only the kings are left, a draw"; return true
            }
            if (kind == K_PROMO || takenBy(0) + takenBy(1) != takenBefore) seen.clear()
            val key = positionKey()
            val times = (seen[key] ?: 0) + 1
            seen[key] = times
            if (times >= 3) { winner = DRAWN; drawnBy = END_REPEAT; phase = OVER; lastText += ": the same position for the third time, a draw"; return true }
        }
        if (settle && legalMoves().isEmpty()) {
            if (inCheck(turn)) { winner = s; lastText += ": checkmate" } else {
                // stalemate: whoever has taken more pieces wins; level, it's a draw
                stalemate = true; winner = stalemateWinner()
                lastText += if (winner == DRAWN) ": stalemate, with the same number of pieces taken: a draw" else ": stalemate; ${sideName(winner)} wins on pieces taken"
            }
            phase = OVER
        } else if (settle && inCheck(turn)) lastText += ": check"
        return true
    }

    /** The position as a number: pieces, fortresses, held pieces, crossings and whose move it is. */
    fun positionKey(): Int {
        var h = board.contentHashCode()
        h = h * 31 + block.contentHashCode(); h = h * 31 + waterWait.contentHashCode()
        h = h * 31 + crossed.contentHashCode(); h = h * 31 + promoted.contentHashCode()
        h = h * 31 + fortHits.contentHashCode(); return h * 31 + turn
    }

    /** How many pieces [side] has taken (a ruined fortress counts as one). */
    fun takenBy(side: Int) = captured[side].sum()
    fun stalemateWinner() = takenBy(0).compareTo(takenBy(1)).let { if (it > 0) 0 else if (it < 0) 1 else DRAWN }

    private fun clearCell(c: Int) { board[c] = 0; waterWait[c] = 0; facing[c] = 0; crossed[c] = 0; promoted[c] = 0 }

    private fun an(name: String) = (if (name.first().lowercaseChar() in "aeiou") "an " else "a ") + name

    fun describe(m: Int): String {
        val name = NAMES[typeOf(board[aOf(m)])]
        return when (kindOf(m)) {
            K_PASS -> "passes because no unit can move or attack"
            K_RANGED -> "fires ${an(name)} at the ${NAMES[typeOf(board[bOf(m)])]} on ${coord(bOf(m))}"
            K_PROMO -> "moves a Rabble from ${coord(aOf(m))} to ${coord(bOf(m))} and promotes it to a ${NAMES[viaOf(m)]}"
            else -> "moves ${an(name)} from ${coord(aOf(m))} to ${coord(bOf(m))}"
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
