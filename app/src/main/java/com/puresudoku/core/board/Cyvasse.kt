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
        /** How far a catapult or trebuchet shoots. */
        const val SIEGE_RANGE = 5
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
        /** A small army: one of every piece. */
        val ONE_EACH = intArrayOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)

        // ---- the ways to play (the field and the armies); every one keeps the river across the middle
        /** The fixed layout below. */
        const val V_STANDARD = 0
        /** One of each piece; each side places its terrain and its pieces anywhere on its half, behind a screen. */
        const val V_OPEN = 1
        /** The standard armies with no mountains or forests: only the river. */
        const val V_CLEAR = 2
        /** One of each piece, all on the back row: no front line of rabble. */
        const val V_BACK_ROW = 3
        /** The standard field and army, but each side places its own pieces anywhere on its half, behind a screen. */
        const val V_MUSTER = 4
        /** The standard armies on new terrain each game: mountains and forests set at random, the same on both sides. */
        const val V_WILD = 5
        val VARIANTS = intArrayOf(V_STANDARD, V_OPEN, V_CLEAR, V_BACK_ROW, V_MUSTER, V_WILD)
        val VARIANT_NAMES = arrayOf("Standard", "Open field", "Oops no terrain", "Back row", "Muster", "Wild field")
        val VARIANT_TEXT = arrayOf(
            "The classic layout. 20 pieces a side: a back row of nobles, with elephants, crossbowmen and six rabble in front. The mountains and forests are the same every game.",
            "One of each piece, 10 a side. Behind a screen, each side places 3 mountains on white grass, 3 forests on green grass and all 10 pieces anywhere on its own half.",
            "The standard armies with no mountains or forests. The river still crosses the middle: it ends a move, and pieces that cross it can't step back.",
            "One of each piece, all 10 lined up on the back row: no front line of rabble. The standard mountains and forests, with a long open march between the armies.",
            "The standard 20-piece army and terrain, but you choose where every piece starts. Behind a screen, each side places its whole army anywhere on its own half.",
            "The standard armies on a new field every game: 3 mountains and 3 forests set at random on each side, mirrored so neither side has an edge.",
        )
        /** How many of each piece a side has in [variant]. */
        fun armyFor(variant: Int) = if (variant == V_OPEN || variant == V_BACK_ROW) ONE_EACH else ARMY

        /** A new game of [variant], ready for the first move (or for setting up behind the screen). */
        fun start(variant: Int, rnd: Random = Random.Default): Cyvasse = when (variant) {
            V_OPEN -> openField()
            V_CLEAR -> clearField()
            V_BACK_ROW -> standard().also { it.lineUpBackRow() }
            V_MUSTER -> standard().also { it.startMuster() }
            V_WILD -> standard().also { it.wildTerrain(rnd) }
            else -> standard()
        }

        /** Whether a fresh game of [variant] is set up behind a screen before the battle. */
        fun hasSetup(variant: Int) = variant == V_OPEN || variant == V_MUSTER
        /** What each player sets on their own half in an open-field game. */
        const val MOUNTAINS = 3
        const val WATERS = 0; const val FORESTS_PER_SIDE = 3

        val NAMES = arrayOf("", "Rabble", "Spearman", "Crossbowman", "Light Horse", "Heavy Horse", "Elephant", "Catapult", "Trebuchet", "Dragon", "King")
        val PLURALS = arrayOf("", "Rabble", "Spearmen", "Crossbowmen", "Light Horses", "Heavy Horses", "Elephants", "Catapults", "Trebuchets", "Dragons", "Kings")
        val MOVES_TEXT = arrayOf("", "Steps one tile forward or sideways, never back, and takes one tile diagonally forward. Promoted on the enemy's back row.",
            "Moves up to two tiles any way and takes only by landing on its victim.",
            "Steps one tile straight. As its move it can shoot one enemy diagonally beside it.",
            "Leaps one or two tiles diagonally, over pieces, and takes by landing. Water doesn't hold it.",
            "Rides up to three tiles diagonally; pieces block it and water ends the ride.",
            "Moves one tile any way.",
            "Moves up to two tiles straight. Shoots up to five tiles diagonally at the first piece in line, then must reload for a turn.",
            "Moves up to two tiles straight. Shoots up to five tiles straight at the first piece in line, then must reload for a turn.",
            "Flies any distance in a line over water, forest and mountains, and may land on a mountain; pieces stop it. Cannot attack into forests.",
            "Moves one tile any way and can take any piece. It can never be left in check; checkmate ends the game.")
        /** Rough worth of each piece, for the computer and the coach. */
        val VALUE = intArrayOf(0, 10, 22, 26, 28, 34, 40, 44, 46, 90, 0)

        /** New games start on plain grass. */
        val TERRAIN: IntArray = IntArray(CELLS)

        fun newSetup(): Cyvasse = Cyvasse().also { it.terrain.fill(PLAIN) }

        /** An open-field game: the standard armies and the river, with each player placing their own mountains and forests. */
        fun openField(): Cyvasse = standard().also { it.startOpenField() }

        /** A clear field: the standard armies on open ground, with no mountains, forests or river water at all. */
        fun clearField(): Cyvasse = standard().also { it.clearTerrain() }

        /**
         * The standard field, top (Onyx's back row) to bottom (Alabaster's). Pieces: T trebuchet, H heavy horse,
         * L light horse, S spearman, D dragon, K king, C catapult, E elephant, X crossbowman, R rabble (rows above the
         * river are Onyx's). Terrain: f forest, ~ water, M mountain, F fortress, . grass.
         */
        val LAYOUT = arrayOf(
            "T H L S D K S L H C",
            "E X R R R R R R X E",
            ". . . . . . . . . .",
            ". . . . . . . . . f",
            "f M . . f M . . f .",
            "~ ~ ~ ~ ~ ~ ~ ~ ~ ~",
            "f . f . f M . . . M",
            ". . . . . . . . . f",
            ". . . . . . . . . .",
            "E X R R R R R R X E",
            "T H L S D K S L H C")
        /** The back row for [V_BACK_ROW], left to right, as Alabaster sees it. */
        val BACK_ROW = intArrayOf(TREBUCHET, HEAVY_HORSE, LIGHT_HORSE, SPEARMAN, DRAGON, KING, ELEPHANT, CROSSBOW, RABBLE, CATAPULT)
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
        /** Matches the alternating board colors: odd squares are the light grass. */
        fun lightGrass(c: Int) = (row(c) + col(c)) % 2 != 0
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

        /** Whether a piece of [attacker]'s type may take one of [defender]'s (sides aside): every piece can take every piece. */
        fun beats(attacker: Int, defender: Int): Boolean = attacker != 0 && defender != 0

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
            g.keepFell = o.optInt("keepFell", -1); g.stalemate = o.optBoolean("stalemate"); g.drawnBy = o.optInt("drawnBy")
            g.variant = o.optInt("variant", if (o.optBoolean("open")) V_OPEN else if (o.optBoolean("clear")) V_CLEAR else V_STANDARD)
            o.optJSONArray("seen")?.let { a -> for (i in 0 until a.length() / 2) g.seen[a.getInt(2 * i)] = a.getInt(2 * i + 1) }
            ia("waterWait", CELLS).copyInto(g.waterWait); ia("facing", CELLS).copyInto(g.facing); ia("reload", CELLS).copyInto(g.reload)
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
    /** 1 for a catapult or trebuchet that fired on its side's last turn: it can't fire again this turn (it may move). */
    val reload = IntArray(CELLS)
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
    /** Which way to play: [V_STANDARD], [V_OPEN] and so on. */
    var variant = V_STANDARD
    /** Each player places their own terrain and pieces before the battle. */
    val openField get() = variant == V_OPEN
    /** No mountains or forests: only the river. */
    val clearField get() = variant == V_CLEAR
    /** Each side places mountains and forests behind the screen. */
    val placesTerrain get() = variant == V_OPEN
    /** Each side places its pieces behind the screen. */
    val placesPieces get() = variant == V_OPEN || variant == V_MUSTER
    /** How many pieces a side starts with. */
    val armySize get() = armyFor(variant).sum()

    /** Bare ground everywhere but the river, which always crosses the middle row. */
    private fun bareField() {
        for (c in 0 until CELLS) { terrain[c] = if (row(c) == RIVER) WATER else PLAIN; block[c] = 0 }
        keepAt[0] = -1; keepAt[1] = -1; keepGone[0] = false; keepGone[1] = false; fortHits.fill(0)
    }

    /** Takes every piece off the board into its side's reserve of [army]. */
    private fun liftArmies(army: IntArray) {
        for (c in 0 until CELLS) clearCell(c)
        for (s in 0..1) army.copyInto(reserve[s])
    }

    /** Strips the mountains and forests; the river stays and still holds pieces as always. */
    fun clearTerrain() { bareField(); variant = V_CLEAR }

    /** A bare field with the river and one of each piece in reserve, set up behind the screen. */
    fun startOpenField() {
        bareField(); liftArmies(ONE_EACH)
        phase = SETUP; placer = 0; turn = 0; first = 0; variant = V_OPEN
    }

    /** The standard field with every piece in reserve, to place behind the screen. */
    fun startMuster() {
        liftArmies(ARMY)
        phase = SETUP; placer = 0; turn = 0; first = 0; variant = V_MUSTER
    }

    /** One of each piece, all on each side's back row. */
    fun lineUpBackRow() {
        liftArmies(IntArray(11))
        for (k in 0 until W) {
            board[(H - 1) * W + k] = piece(0, BACK_ROW[k])
            board[k] = piece(1, BACK_ROW[k])
        }
        variant = V_BACK_ROW
    }

    /** New mountains (white grass) and forests (green grass) in each side's three free rows, mirrored across the river. */
    fun wildTerrain(rnd: Random) {
        bareField()
        val free = ((RIVER + 1)..(RIVER + 3)).flatMap { r -> (0 until W).map { r * W + it } }.shuffled(rnd)
        fun mirror(c: Int) = (H - 1 - row(c)) * W + col(c)
        for (c in free.filter { lightGrass(it) }.take(MOUNTAINS)) { block[c] = MOUNTAIN; block[mirror(c)] = MOUNTAIN }
        for (c in free.filter { !lightGrass(it) }.take(FORESTS_PER_SIDE)) { terrain[c] = FOREST; terrain[mirror(c)] = FOREST }
        variant = V_WILD
    }

    /** Makes this game an exact copy of [o]. */
    fun loadFrom(o: Cyvasse) {
        o.board.copyInto(board); o.block.copyInto(block); o.terrain.copyInto(terrain); o.keepAt.copyInto(keepAt)
        o.waterWait.copyInto(waterWait); o.reload.copyInto(reload); o.facing.copyInto(facing); o.fortHits.copyInto(fortHits)
        o.crossed.copyInto(crossed); o.promoted.copyInto(promoted)
        keepGone[0] = o.keepGone[0]; keepGone[1] = o.keepGone[1]
        for (s in 0..1) { o.reserve[s].copyInto(reserve[s]); o.captured[s].copyInto(captured[s]) }
        phase = o.phase; placer = o.placer; turn = o.turn; first = o.first; winner = o.winner
        bonusRabble = o.bonusRabble; quiet = o.quiet; plies = o.plies
        lastMove = o.lastMove; lastBy = o.lastBy; lastText = o.lastText; keepFell = o.keepFell; stalemate = o.stalemate
        drawnBy = o.drawnBy; variant = o.variant
        seen.clear(); seen.putAll(o.seen)
    }

    fun toJson(): JSONObject {
        fun a(x: IntArray) = JSONArray().also { j -> x.forEach { j.put(it) } }
        return JSONObject().put("board", a(board)).put("block", a(block)).put("terrain", a(terrain)).put("keepAt", a(keepAt))
            .put("gone0", keepGone[0]).put("gone1", keepGone[1]).put("waterWait", a(waterWait)).put("reload", a(reload)).put("facing", a(facing)).put("fortHits", a(fortHits)).put("crossed", a(crossed)).put("promoted", a(promoted))
            .put("res0", a(reserve[0])).put("res1", a(reserve[1])).put("cap0", a(captured[0])).put("cap1", a(captured[1]))
            .put("phase", phase).put("placer", placer).put("turn", turn).put("first", first).put("winner", winner)
            .put("bonus", bonusRabble).put("quiet", quiet).put("plies", plies)
            .put("last", lastMove).put("lastBy", lastBy).put("lastText", lastText).put("keepFell", keepFell).put("stalemate", stalemate).put("variant", variant).put("drawnBy", drawnBy)
            .put("seen", JSONArray().also { j -> for ((k, v) in seen) { j.put(k); j.put(v) } })
    }

    fun copy(): Cyvasse {
        val g = Cyvasse()
        board.copyInto(g.board); block.copyInto(g.block); terrain.copyInto(g.terrain); keepAt.copyInto(g.keepAt)
        waterWait.copyInto(g.waterWait); reload.copyInto(g.reload); facing.copyInto(g.facing); fortHits.copyInto(g.fortHits)
        crossed.copyInto(g.crossed); promoted.copyInto(g.promoted)
        g.keepGone[0] = keepGone[0]; g.keepGone[1] = keepGone[1]
        for (s in 0..1) { reserve[s].copyInto(g.reserve[s]); captured[s].copyInto(g.captured[s]) }
        g.phase = phase; g.placer = placer; g.turn = turn; g.first = first; g.winner = winner
        g.bonusRabble = bonusRabble; g.quiet = quiet; g.plies = plies
        g.lastMove = lastMove; g.lastBy = lastBy; g.lastText = lastText; g.keepFell = keepFell; g.stalemate = stalemate; g.variant = variant; g.drawnBy = drawnBy
        return g
    }

    fun keepStands(side: Int) = keepAt[side] >= 0 && !keepGone[side]

    fun mountainsPlaced(side: Int) = (0 until CELLS).count { block[it] == MOUNTAIN && inHalf(side, it) }
    fun terrainPlaced(side: Int, type: Int) = (0 until CELLS).count { inHalf(side, it) && terrain[it] == type }

    fun toggleTerrain(side: Int, type: Int, c: Int): Boolean {
        if (phase != SETUP || side != placer || type !in WATER..FOREST || c !in 0 until CELLS || !inHalf(side, c)) return false
        if (block[c] != 0 || board[c] != 0) return false
        if (terrain[c] == type) { terrain[c] = PLAIN; return true }
        if (type == FOREST && lightGrass(c)) return false
        if (type == WATER && row(c) != RIVER) return false
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
        lightGrass(c) && terrain[c] == PLAIN && block[c] == 0 && board[c] == 0 && mountainsPlaced(side) < MOUNTAINS

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

    /** Clears what [side] has placed behind the screen: its pieces, and its terrain where it sets that too. */
    fun clearSetup(side: Int) {
        if (phase != SETUP) return
        for (c in 0 until CELLS) if (inHalf(side, c)) {
            if (placesPieces && ownerOf(board[c]) == side) liftPiece(side, c)
            if (placesTerrain) { block[c] = 0; terrain[c] = PLAIN }
        }
        if (placesTerrain) keepAt[side] = -1
    }

    /** [side]'s mountains and forests are all set (always true where the terrain is fixed). */
    fun terrainDone(side: Int) = !placesTerrain || (mountainsPlaced(side) == MOUNTAINS && terrainPlaced(side, FOREST) == FORESTS_PER_SIDE)

    fun setupDone(side: Int) = terrainDone(side) && reserveCount(side) == 0

    /** Random complete setup: terrain (where the sides set it) and the army. */
    fun randomSetup(side: Int, rnd: Random, @Suppress("UNUSED_PARAMETER") level: Int = 3) {
        val old = placer; placer = side; clearSetup(side)
        if (placesTerrain) {
            val cells = (0 until CELLS).filter { inHalf(side, it) && board[it] == 0 }.shuffled(rnd)
            cells.filter { !lightGrass(it) }.take(FORESTS_PER_SIDE).forEach { toggleTerrain(side, FOREST, it) }
            cells.filter { lightGrass(it) }.take(MOUNTAINS).forEach { toggleMountain(side, it) }
        }
        placeArmy(side, rnd)
        placer = old
    }

    /**
     * Stands every piece [side] still holds, in a sensible formation: siege engines and the king at the back (the king
     * near the middle), riders and elephants behind the line, rabble and spearmen in front.
     */
    fun placeArmy(side: Int, rnd: Random) {
        val old = placer; placer = side
        // rows forward from the back row (0) toward the river (4), by piece type
        val depth = intArrayOf(0, 3, 3, 2, 1, 1, 1, 0, 0, 1, 0)
        for (t in intArrayOf(KING, TREBUCHET, CATAPULT, DRAGON, ELEPHANT, HEAVY_HORSE, LIGHT_HORSE, CROSSBOW, SPEARMAN, RABBLE)) {
            while (reserve[side][t] > 0) {
                val free = (0 until CELLS).filter { canStand(side, t, it) }
                if (free.isEmpty()) break
                val near = free.minOf { kotlin.math.abs(progress(side, it) - depth[t]) }
                var pool = free.filter { kotlin.math.abs(progress(side, it) - depth[t]) == near }
                if (t == KING) pool.filter { col(it) in 3..6 }.takeIf { it.isNotEmpty() }?.let { pool = it }
                standPiece(side, t, pool.random(rnd))
            }
        }
        placer = old
    }

    /** The player behind the screen is finished: the other sets up, or the screen comes down. */
    fun ready(side: Int, coin: Boolean): Boolean {
        if (phase != SETUP || side != placer || !setupDone(side)) return false
        if (side == 0) placer = 1
        // a game set up behind the screen keeps the standard rule: Alabaster moves first
        else { phase = BATTLE; first = if (coin && !placesPieces) 1 else 0; turn = first }
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
     * Whether the piece on [from] may capture the piece on [target]. Never your own. A piece in a forest can't be hit
     * by catapults, trebuchets or dragons (others may still land on it, and it attacks normally from there).
     */
    fun canTake(from: Int, target: Int): Boolean {
        if (board[from] == 0 || board[target] == 0 || ownerOf(board[from]) == ownerOf(board[target])) return false
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
                // up to two tiles straight onto empty ground; water ends the move
                for (d in ORTHO) {
                    var via = from
                    for (dist in 1..2) {
                        val r = r0 + d[0] * dist; val c = c0 + d[1] * dist
                        if (!on(r, c)) break
                        val t = r * W + c
                        if (block[t] != 0 || board[t] != 0) break
                        if (!back(t)) out.add(mv(K_MOVE, from, t, via))
                        if (terrain[t] == WATER) break
                        via = t
                    }
                }
                // shots: up to SIEGE_RANGE tiles, diagonally (catapult) or straight (trebuchet), at the first piece in line;
                // a siege engine that fired last turn is still reloading
                if (reload[from] > 0) return
                for (d in if (type == CATAPULT) DIAG else ORTHO) {
                    var r = r0; var c = c0
                    for (dist in 1..SIEGE_RANGE) {
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
        // a siege engine that fired last turn has reloaded once this turn is played
        for (c in 0 until CELLS) if (ownerOf(board[c]) == s && reload[c] > 0) reload[c]--
        val moverType = typeOf(board[a])
        val takenBefore = takenBy(0) + takenBy(1)
        when (kind) {
            K_RANGED -> { if (board[b] != 0) strike(s, moverType, b); if (moverType == CATAPULT || moverType == TREBUCHET) reload[a] = 1 }
            K_MOVE, K_PROMO -> {
                val repelled = board[b] != 0 && strike(s, moverType, b)
                val destination = if (repelled) viaOf(m).takeIf { it >= 0 && kind == K_MOVE } ?: a else b
                if (destination != a) {
                    val moving = board[a]; val wasCrossed = crossed[a]; val wasPromoted = promoted[a]; val wasReloading = reload[a]
                    clearCell(a)
                    board[destination] = if (kind == K_PROMO) piece(s, viaOf(m)) else moving
                    // promoted, or having touched the enemy's back row: free to move back again
                    promoted[destination] = if (kind == K_PROMO || row(destination) == promotionRow(s)) 1 else wasPromoted
                    crossed[destination] = if (wasCrossed == 1 || inHalf(1 - s, destination)) 1 else 0
                    reload[destination] = wasReloading
                    // water ends a move, but no longer holds the piece for a turn
                    waterWait[destination] = 0
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
        h = h * 31 + block.contentHashCode(); h = h * 31 + waterWait.contentHashCode(); h = h * 31 + reload.contentHashCode()
        h = h * 31 + crossed.contentHashCode(); h = h * 31 + promoted.contentHashCode()
        h = h * 31 + fortHits.contentHashCode(); return h * 31 + turn
    }

    /** How many pieces [side] has taken (a ruined fortress counts as one). */
    fun takenBy(side: Int) = captured[side].sum()
    fun stalemateWinner() = takenBy(0).compareTo(takenBy(1)).let { if (it > 0) 0 else if (it < 0) 1 else DRAWN }

    private fun clearCell(c: Int) { board[c] = 0; waterWait[c] = 0; reload[c] = 0; facing[c] = 0; crossed[c] = 0; promoted[c] = 0 }

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
