package com.puresudoku.ui.games.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.board.Cyvasse
import com.puresudoku.core.board.CyvasseAi
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.components.tableUpright
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.MiddleStat
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.tutorial.TutorialStep
import org.json.JSONObject
import kotlin.random.Random

// =====================================================================  session

class CyvasseSession(difficulty: Difficulty, val g: Cyvasse) : DuelSession(GameKind.CYVASSE, difficulty) {
    companion object {
        const val TOOL_MOUNTAIN = 0; const val TOOL_KEEP = 1; const val TOOL_PIECES = 2
        const val TOOL_WATER = 3; const val TOOL_FOREST = 4

        fun restore(o: JSONObject): CyvasseSession {
            val st = o.getJSONObject("state")
            return CyvasseSession(Difficulty.valueOf(o.getString("difficulty")), Cyvasse.fromJson(st.getJSONObject("g"))).also { s ->
                s.moves = st.optInt("moves"); s.localTwoPlayer = st.optBoolean("localTwoPlayer", false)
                s.handoffPending = st.optBoolean("handoffPending", false); s.tool = st.optInt("tool", TOOL_MOUNTAIN)
                s.message = st.optString("message", ""); s.restoreCommon(o)
            }
        }
    }

    /** Pass-and-play: the next player's screen stays covered until they're ready (between the two setups). */
    var handoffPending by mutableStateOf(false)
    /** The tile tapped: one of your pieces to move, or any piece to read about. */
    var selected by mutableIntStateOf(-1); private set
    /** A piece type picked from reserve, to stand on (or bring out to) a home tile. */
    var pickType by mutableIntStateOf(0); private set
    /** What a tap does behind the screen: set mountains, the keep, or pieces. */
    var tool by mutableIntStateOf(TOOL_WATER)
    /** Reserves are secret: shown only when their owner opens the tray. */
    var showReserve by mutableStateOf(false)
    var moveId by mutableIntStateOf(0); private set
    var rangedMode by mutableStateOf(true)

    val currentPlayer: Int get() = if (g.phase == Cyvasse.SETUP) g.placer else g.turn
    override val tableSeat: Int? get() = if (localTwoPlayer) currentPlayer + 1 else null
    override fun localSide(actor: Int) = if (actor == 1) "Alabaster" to Color(0xFFF4F1EA) else "Onyx" to Color(0xFF26262C)
    /** Whose army the screen shows: your own seat online, the player at the device otherwise. */
    val viewer: Int get() = if (online) onlineSeat - 1 else if (localTwoPlayer) currentPlayer else 0
    /**
     * Online guests see the board turned round, their half at the bottom. On a shared device it stays put
     * (table mode turns the screen for Onyx), so Onyx sets up on the top half.
     */
    val flipped: Boolean get() = online && onlineSeat == 2

    private val level get() = aiDifficulty.ordinal

    override val middleStat get() = MiddleStat.Counter("Taken", "${g.captured[0].sum()}")
    override val progress: Float get() = (g.captured[0].sum() / 20f).coerceAtMost(0.95f)
    override val wonTitle get() = if (localTwoPlayer) "Alabaster wins!" else "The rival king falls!"
    override val lostTitle get() = if (localTwoPlayer) "Onyx wins!" else "Your king is taken"
    override val lostMessage get() = "The rival took your king. This game's save slot has been cleared."
    override val drawMessage get() = "The game ended."

    val myMove: Boolean get() = outcome == Outcome.PLAYING && !thinking && !heraldBusy && g.phase != Cyvasse.OVER &&
        (localTwoPlayer || currentPlayer == 0) && (!localTwoPlayer || online || !handoffPending) && myOnlineTurn

    fun who(p: Int) = when {
        online -> if (p == onlineSeat - 1) "You" else "Your opponent"
        localTwoPlayer -> Cyvasse.sideName(p)
        p == 0 -> "You"
        else -> "The rival"
    }

    init { message = if (g.phase == Cyvasse.SETUP) "Place 5 waters, 6 forests, 6 mountains, a fortress and all 10 pieces" else "Your move" }

    fun acceptHandoff() { handoffPending = false; bump(); changed() }
    override fun skipHandoff() { handoffPending = false; bump() }

    // ---------------------------------------------------------------- behind the screen

    fun chooseTool(t: Int) {
        if (!myMove || g.phase != Cyvasse.SETUP) return
        tool = t; selected = -1
        if (t == TOOL_PIECES && pickType == 0) pickType = Cyvasse.TYPES.firstOrNull { g.reserve[currentPlayer][it] > 0 } ?: 0
        bump()
    }

    private fun tapSetup(c: Int) {
        val p = currentPlayer
        if (!Cyvasse.inHalf(p, c)) { nudge("Set up on your own half of the board"); return }
        when (tool) {
            TOOL_WATER, TOOL_FOREST -> {
                val t = if (tool == TOOL_WATER) Cyvasse.WATER else Cyvasse.FOREST
                if (!g.toggleTerrain(p, t, c)) { nudge("Choose an empty grass tile; each player has 5 water and 6 forest tiles"); return }
            }
            TOOL_MOUNTAIN -> {
                if (g.block[c] == Cyvasse.MOUNTAIN) { g.toggleMountain(p, c); bump(); changed(); return }
                if (g.mountainsPlaced(p) >= Cyvasse.MOUNTAINS) { nudge("All six mountains are set: tap one to lift it"); return }
                if (!g.toggleMountain(p, c)) { nudge("Mountains go on open grass"); return }
                if (g.mountainsPlaced(p) == Cyvasse.MOUNTAINS && g.keepAt[p] < 0) { tool = TOOL_KEEP; message = "Now set your fortress" }
            }
            TOOL_KEEP -> {
                if (!g.placeKeep(p, c)) {
                    nudge("The fortress goes on open grass")
                    return
                }
                tool = TOOL_PIECES
                pickType = Cyvasse.TYPES.firstOrNull { g.reserve[p][it] > 0 } ?: 0
                message = "Pick a piece below, then tap any eligible tile on your half."
            }
            else -> {
                if (Cyvasse.ownerOf(g.board[c]) == p) { pickType = Cyvasse.typeOf(g.board[c]); g.liftPiece(p, c); bump(); changed(); return }
                if (pickType == 0) { nudge("Pick a piece below first"); return }
                if (!g.standPiece(p, pickType, c)) { nudge("Choose an empty tile on your half. Only a dragon can start on a mountain; no unit starts in a fortress."); return }
                if (g.reserve[p][pickType] == 0) pickType = Cyvasse.TYPES.firstOrNull { g.reserve[p][it] > 0 } ?: 0
            }
        }
        bump(); changed()
    }

    fun scatter() {
        if (!myMove || g.phase != Cyvasse.SETUP) return
        g.randomSetup(currentPlayer, rng, 4); tool = TOOL_PIECES; pickType = Cyvasse.TYPES.firstOrNull { g.reserve[currentPlayer][it] > 0 } ?: 0
        message = "A setup is laid out. Change anything you like, then press Ready"
        bump(); changed()
    }

    fun clearSetup() {
        if (!myMove || g.phase != Cyvasse.SETUP) return
        g.clearSetup(currentPlayer); tool = TOOL_WATER; pickType = 0
        message = "Place 5 waters, 6 forests, 6 mountains, a fortress and all 10 pieces"
        bump(); changed()
    }

    fun ready() {
        if (!myMove || g.phase != Cyvasse.SETUP) return
        val p = currentPlayer
        if (!g.setupDone(p)) { nudge("Place 5 waters, 6 forests, 6 mountains, your fortress and all ten pieces first"); return }
        g.ready(p, coin = rng.nextBoolean())
        tool = TOOL_MOUNTAIN; pickType = 0; selected = -1; showReserve = false
        if (!localTwoPlayer) {
            CyvasseAi.setup(g, 1, level, rng); g.ready(1, coin = rng.nextBoolean())
            message = if (g.turn == 0) "The coin falls to you: your move" else "The coin falls to the rival"
            announce("The screen comes down", if (g.turn == 0) "The coin falls to you: you move first." else "The coin falls to the rival: it moves first.", 1600, true)
            bump(); changed()
            rivalTurn()
            return
        }
        if (g.phase == Cyvasse.SETUP) {
            message = "Onyx, set up behind the screen"
            handoffPending = !online
        } else {
            message = "The screen comes down. The coin falls to ${Cyvasse.sideName(g.turn)}: ${Cyvasse.sideName(g.turn)} moves first"
            handoffPending = !online && g.turn == 0
        }
        bump(); changed()
    }

    // ---------------------------------------------------------------- battle

    /** Moves the selected piece could make, keyed by the tile tapped to make them. */
    fun targets(): Map<Int, Int> {
        if (g.phase != Cyvasse.BATTLE) return emptyMap()
        if (selected < 0 || Cyvasse.ownerOf(g.board[selected]) != g.turn) return emptyMap()
        val out = HashMap<Int, Int>()
        for (m in g.legalMoves()) if (Cyvasse.kindOf(m) == Cyvasse.K_MOVE || Cyvasse.kindOf(m) == Cyvasse.K_RANGED) {
            if (Cyvasse.aOf(m) == selected) {
                val t = Cyvasse.bOf(m); val old = out[t]
                if (old == null || (rangedMode && Cyvasse.kindOf(m) == Cyvasse.K_RANGED) || (!rangedMode && Cyvasse.kindOf(m) == Cyvasse.K_MOVE)) out[t] = m
            }
        }
        return out
    }

    fun tap(c: Int) {
        if (c !in 0 until Cyvasse.CELLS) return
        if (g.phase == Cyvasse.SETUP) { if (myMove) tapSetup(c) else nudge(if (thinking) "The rival is setting up" else "Wait for your turn"); return }
        if (g.phase != Cyvasse.BATTLE) return
        val mine = myMove
        if (mine) {
            targets()[c]?.let { play(it); return }
        }
        if (pickType > 0) { pickType = 0 }
        val v = g.board[c]
        if (v == 0) { selected = -1; bump(); return }
        // any piece can be tapped to read what it does; your own can then move
        selected = if (selected == c) -1 else c
        if (mine && selected >= 0 && Cyvasse.ownerOf(v) == g.turn) {
            if (g.bonusRabble >= 0 && (Cyvasse.typeOf(v) != Cyvasse.RABBLE || c == g.bonusRabble)) nudge("Move a different rabble, or end your turn")
            else if (targets().isEmpty()) nudge("That ${Cyvasse.NAMES[Cyvasse.typeOf(v)]} can't move now")
        }
        bump()
    }

    fun pickReserve(t: Int) {
        if (g.phase == Cyvasse.SETUP) { if (myMove) { chooseTool(TOOL_PIECES); pickType = t; bump() }; return }
        return
    }

    fun endBonus() { if (myMove && g.isLegal(Cyvasse.PASS)) play(Cyvasse.PASS) }

    private fun play(m: Int) {
        val p = g.turn
        if (!g.play(m)) { nudge("That move isn't allowed"); return }
        moves++; moveId++; selected = -1; pickType = 0; clearCoach()
        if (g.turn != p) showReserve = false
        message = "${who(p)} ${g.lastText}"
        if (g.phase == Cyvasse.OVER) { bump(); changed(); finishGame(); return }

        if (g.bonusRabble >= 0) message += ". Move a second rabble, or end your turn"
        else if (localTwoPlayer) message += ". ${Cyvasse.sideName(g.turn)} to move"
        bump(); changed()
        if (!localTwoPlayer && g.turn == 1) rivalTurn()
    }

    override fun aiSearch(seed: Long): (() -> String)? {
        val snap = g.copy(); val l = level
        return { CyvasseAi.best(snap, l, Random(seed)).toString() }
    }

    private fun rivalTurn() {
        if (localTwoPlayer || outcome != Outcome.PLAYING || g.phase != Cyvasse.BATTLE || g.turn != 1) return
        computerSearch(minMs = 650, valid = { it.toIntOrNull()?.let(g::isLegal) == true }) { text ->
            val m = text.toIntOrNull()?.takeIf(g::isLegal) ?: g.legalMoves().firstOrNull() ?: return@computerSearch
            val took = g.victims(m).map { Cyvasse.typeOf(g.board[it]) }
            g.play(m)
            moves++; moveId++
            message = "The rival ${g.lastText}"
            bump(); changed()
            when {
                Cyvasse.KING in took -> Unit
                g.keepFell >= 0 -> announce("Fortress ruined", "The defender survives the attack.", 1800, false)
                took.isNotEmpty() -> announce("The rival takes your ${took.joinToString(" and ") { Cyvasse.NAMES[it] }}", g.lastText.replaceFirstChar { it.uppercase() }, 1500, false)
            }
            if (g.phase == Cyvasse.OVER) { whenQuiet { finishGame() }; return@computerSearch }
            if (g.turn == 1) rivalTurn()
        }
    }

    private fun finishGame() {
        when (g.winner) {
            0 -> {
                val left = g.onBoard(0) + g.reserveCount(0)
                win(listOf(BonusLine("Army left: $left", left * rules.basePoints / 2), BonusLine(if (g.keepStands(0)) "Keep standing" else "Keep fallen", if (g.keepStands(0)) rules.basePoints * 3 else 0)))
            }
            1 -> lose()
            else -> Unit
        }
    }

    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (localTwoPlayer || g.phase != Cyvasse.BATTLE || g.turn != 0) return null
        val snap = g.copy()
        return {
            val m = CyvasseAi.best(snap, 4, Random(7))
            val from = when (Cyvasse.kindOf(m)) { Cyvasse.K_MOVE, Cyvasse.K_RANGED -> Cyvasse.aOf(m); else -> -1 }
            val to = if (Cyvasse.kindOf(m) == Cyvasse.K_PASS) -1 else Cyvasse.bOf(m)
            Triple("Try this: ${snap.describe(m)}.", from, to)
        }
    }

    override fun resume() {
        if (outcome != Outcome.PLAYING) return
        if (g.phase == Cyvasse.OVER) { finishGame(); return }
        rivalTurn()
    }

    override fun saveState(): JSONObject = JSONObject().put("g", g.toJson()).put("moves", moves).put("localTwoPlayer", localTwoPlayer)
        .put("handoffPending", handoffPending).put("tool", tool).put("message", message)
}

// =====================================================================  definition

object CyvasseDef : GameDef {
    override val kind = GameKind.CYVASSE
    override fun generate(d: Difficulty): Session = CyvasseSession(d, Cyvasse.newSetup())
    override fun restore(o: JSONObject): Session = CyvasseSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Set your terrain, keep and pieces behind the screen; then take the rival king."
    override fun levelInfo(d: Difficulty) = CyvasseAi.depthFor(d.ordinal).let { "Rival looks $it move${if (it > 1) "s" else ""} ahead" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A green lordling: wild moves."
        Difficulty.MEDIUM -> "Grabs whatever it can reach."
        Difficulty.HARD -> "Guards its king and answers your threats."
        Difficulty.EXPERT -> "Weighs exchanges and guards its king."
        Difficulty.MASTER -> "Plans three moves deep."
        Difficulty.EXTREME -> "A Volantene master: four moves deep."
    }
    @Composable override fun Play(session: Session) = CyvasseUi(session as CyvasseSession)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    /** The shared opening, for the how-to-play cards. */
    private fun demo(select: Int = -1): CyvasseSession {
        val g = Cyvasse.newSetup()
        g.randomSetup(0, Random(11), 4); g.ready(0, false); g.randomSetup(1, Random(5), 4); g.ready(1, false)
        val s = CyvasseSession(Difficulty.MEDIUM, g)
        if (select >= 0) s.tap(select)
        return s
    }

    override val tutorial: List<TutorialStep> get() = listOf(
        TutorialStep("Behind the screen", "Place 5 water, 6 forest, 6 mountain and a fortress tile, then all ten pieces on your half. Only a dragon may start on a mountain; no piece starts in a fortress.") { _ ->
            val s = remember { demo() }
            CyvasseBoard(s, Modifier.fillMaxWidth().padding(16.dp))
        },
        TutorialStep("Move and capture", "Move or attack with one piece each turn. Pieces can capture their own rank, the next rank above, or any lower rank; every piece can capture a king.") { _ ->
            val s = remember {
                demo().also { d -> (0 until Cyvasse.CELLS).firstOrNull { d.g.board[it] == Cyvasse.piece(0, Cyvasse.DRAGON) }?.let { d.tap(it) } }
            }
            CyvasseBoard(s, Modifier.fillMaxWidth().padding(16.dp))
        },
        TutorialStep("Every piece is different", "Horses can turn as they move. Crossbowmen, catapults and trebuchets can fire; water stops a unit and keeps it still next turn. Forest reduces movement range by one.") { _ ->
            val s = remember {
                demo().also { d -> (0 until Cyvasse.CELLS).firstOrNull { d.g.board[it] == Cyvasse.piece(0, Cyvasse.LIGHT_HORSE) }?.let { d.tap(it) } }
            }
            CyvasseBoard(s, Modifier.fillMaxWidth().padding(16.dp))
        },
        TutorialStep("Keep and king", "A fortress absorbs one successful attack for an occupant, or two for a king, before it is ruined. Capture the king to win; there is no turn limit.") { _ ->
            val s = remember { demo() }
            CyvasseBoard(s, Modifier.fillMaxWidth().padding(16.dp))
        },
    )
}

// =====================================================================  drawing
// Flat and quiet: plain coloured tiles, and every piece, mountain, tree and keep drawn as a simple shape.

@Composable
private fun cyvasseArt(type: Int, side: Int): ImageBitmap {
    val id = if (side == 0) when (type) {
        Cyvasse.RABBLE -> com.puresudoku.R.drawable.cyvasse_ivory_rabble
        Cyvasse.SPEARMAN -> com.puresudoku.R.drawable.cyvasse_ivory_spearman
        Cyvasse.CROSSBOW -> com.puresudoku.R.drawable.cyvasse_ivory_crossbowman
        Cyvasse.LIGHT_HORSE -> com.puresudoku.R.drawable.cyvasse_ivory_light_horse
        Cyvasse.HEAVY_HORSE -> com.puresudoku.R.drawable.cyvasse_ivory_heavy_horse
        Cyvasse.ELEPHANT -> com.puresudoku.R.drawable.cyvasse_ivory_elephant
        Cyvasse.CATAPULT -> com.puresudoku.R.drawable.cyvasse_ivory_catapult
        Cyvasse.TREBUCHET -> com.puresudoku.R.drawable.cyvasse_ivory_trebuchet
        Cyvasse.DRAGON -> com.puresudoku.R.drawable.cyvasse_ivory_dragon
        else -> com.puresudoku.R.drawable.cyvasse_ivory_king
    } else when (type) {
        Cyvasse.RABBLE -> com.puresudoku.R.drawable.cyvasse_onyx_rabble
        Cyvasse.SPEARMAN -> com.puresudoku.R.drawable.cyvasse_onyx_spearman
        Cyvasse.CROSSBOW -> com.puresudoku.R.drawable.cyvasse_onyx_crossbowman
        Cyvasse.LIGHT_HORSE -> com.puresudoku.R.drawable.cyvasse_onyx_light_horse
        Cyvasse.HEAVY_HORSE -> com.puresudoku.R.drawable.cyvasse_onyx_heavy_horse
        Cyvasse.ELEPHANT -> com.puresudoku.R.drawable.cyvasse_onyx_elephant
        Cyvasse.CATAPULT -> com.puresudoku.R.drawable.cyvasse_onyx_catapult
        Cyvasse.TREBUCHET -> com.puresudoku.R.drawable.cyvasse_onyx_trebuchet
        Cyvasse.DRAGON -> com.puresudoku.R.drawable.cyvasse_onyx_dragon
        else -> com.puresudoku.R.drawable.cyvasse_onyx_king
    }
    return ImageBitmap.imageResource(id)
}

private fun DrawScope.paintCyvasse(image: ImageBitmap, r: Rect) {
    drawImage(image, dstOffset = IntOffset(r.left.toInt(), r.top.toInt()),
        dstSize = IntSize(r.width.toInt().coerceAtLeast(1), r.height.toInt().coerceAtLeast(1)), filterQuality = FilterQuality.Medium)
}

private val GRASS_A = Color(0xFFE4E8CF)
private val GRASS_B = Color(0xFFD8DEBF)
private val WATER_TILE = Color(0xFFA9CCDD)
private val FOREST_TILE = Color(0xFFC3D3A6)
private val MOUNTAIN_TILE = Color(0xFFD3CEC3)
private val IVORY = Color(0xFFF7F1E3)
private val IVORY_INK = Color(0xFF4A3F33)
private val ONYX = Color(0xFF34313B)
private val ONYX_INK = Color(0xFFEDE6D6)
private val GOLD = Color(0xFFB8963A)
private val SILVER = Color(0xFF9AA0AA)

/** Runs [block] in a 100 x 100 box fitted to [r]. */
private inline fun DrawScope.inBox(r: Rect, block: DrawScope.() -> Unit) {
    val k = r.width / 100f
    translate(r.left, r.top) { scale(k, k, Offset.Zero) { block() } }
}

private fun p(build: Path.() -> Unit) = Path().apply(build)

/** A piece: a plain ivory or onyx disc with its emblem. */
private fun DrawScope.cyvassePiece(type: Int, side: Int, c: Offset, r: Float, alpha: Float = 1f) {
    val face = if (side == 0) IVORY else ONYX
    val ink = if (side == 0) IVORY_INK else ONYX_INK
    drawCircle(Color.Black.copy(alpha = 0.18f * alpha), r, c + Offset(0f, r * 0.08f))
    drawCircle(face, r, c, alpha = alpha)
    drawCircle(if (side == 0) GOLD else SILVER, r * 0.94f, c, alpha = alpha, style = Stroke(r * 0.08f))
    emblem(type, ink, if (side == 0) IVORY else ONYX, Rect(c.x - r * 0.66f, c.y - r * 0.66f, c.x + r * 0.66f, c.y + r * 0.66f), alpha)
}

/** The emblem of each piece, drawn in one ink colour (with [face] for cut-outs) in a 100 x 100 box. */
private fun DrawScope.emblem(type: Int, ink: Color, face: Color, r: Rect, alpha: Float) = inBox(r) {
    fun l(x0: Float, y0: Float, x1: Float, y1: Float, w: Float = 8f) = drawLine(ink, Offset(x0, y0), Offset(x1, y1), w, StrokeCap.Round, alpha = alpha)
    fun fill(path: Path) = drawPath(path, ink, alpha = alpha)
    val horse = p {
        moveTo(30f, 92f); lineTo(34f, 64f); quadraticTo(24f, 60f, 18f, 52f); lineTo(14f, 42f); quadraticTo(16f, 34f, 26f, 33f)
        lineTo(38f, 30f); quadraticTo(44f, 16f, 56f, 12f); lineTo(60f, 4f); lineTo(65f, 12f); quadraticTo(82f, 20f, 84f, 46f)
        quadraticTo(86f, 70f, 76f, 92f); close()
    }
    when (type) {
        Cyvasse.RABBLE -> { // a pitchfork
            l(50f, 92f, 50f, 34f, 9f); l(30f, 34f, 70f, 34f, 8f)
            l(30f, 34f, 30f, 10f, 7f); l(50f, 34f, 50f, 8f, 7f); l(70f, 34f, 70f, 10f, 7f)
        }
        Cyvasse.SPEARMAN -> { // a spear
            l(50f, 94f, 50f, 30f, 8f)
            fill(p { moveTo(50f, 4f); lineTo(38f, 32f); lineTo(62f, 32f); close() })
            l(36f, 40f, 64f, 40f, 6f)
        }
        Cyvasse.CROSSBOW -> { // a crossbow with a bolt
            drawPath(p { moveTo(12f, 50f); quadraticTo(50f, 18f, 88f, 50f) }, ink, alpha = alpha, style = Stroke(8f, cap = StrokeCap.Round))
            l(12f, 50f, 50f, 62f, 3f); l(88f, 50f, 50f, 62f, 3f)
            l(50f, 32f, 50f, 94f, 10f)
            fill(p { moveTo(50f, 4f); lineTo(42f, 18f); lineTo(58f, 18f); close() }); l(50f, 14f, 50f, 40f, 5f)
        }
        Cyvasse.LIGHT_HORSE -> { // a horse's head
            fill(horse)
            drawCircle(face, 4f, Offset(46f, 30f), alpha = alpha)
        }
        Cyvasse.HEAVY_HORSE -> { // an armoured horse's head: a plume and a barding band
            fill(horse)
            drawCircle(face, 4f, Offset(46f, 30f), alpha = alpha)
            drawLine(face, Offset(36f, 72f), Offset(82f, 72f), 6f, alpha = alpha)
            fill(p { moveTo(60f, 8f); quadraticTo(78f, -2f, 94f, 10f); quadraticTo(78f, 8f, 68f, 18f); close() })
        }
        Cyvasse.ELEPHANT -> { // a war elephant in profile
            fill(p {
                moveTo(30f, 40f); quadraticTo(36f, 22f, 62f, 24f); quadraticTo(90f, 26f, 90f, 52f); lineTo(90f, 86f); lineTo(78f, 86f); lineTo(76f, 68f)
                lineTo(54f, 68f); lineTo(52f, 86f); lineTo(40f, 86f); lineTo(40f, 62f); quadraticTo(26f, 60f, 22f, 70f); quadraticTo(16f, 88f, 8f, 86f)
                quadraticTo(14f, 80f, 16f, 64f); quadraticTo(16f, 42f, 30f, 40f); close()
            })
            drawCircle(face, 4f, Offset(34f, 44f), alpha = alpha)
        }
        Cyvasse.CATAPULT -> { // a wheeled catapult, arm cocked
            l(14f, 70f, 86f, 70f, 9f)
            drawCircle(ink, 10f, Offset(28f, 84f), alpha = alpha, style = Stroke(6f)); drawCircle(ink, 10f, Offset(72f, 84f), alpha = alpha, style = Stroke(6f))
            l(32f, 68f, 50f, 44f, 7f); l(68f, 68f, 50f, 44f, 7f)
            l(46f, 66f, 82f, 18f, 7f); drawCircle(ink, 9f, Offset(84f, 14f), alpha = alpha)
        }
        Cyvasse.TREBUCHET -> { // an A-frame with a long arm and a counterweight
            l(26f, 92f, 50f, 40f, 7f); l(74f, 92f, 50f, 40f, 7f); l(16f, 92f, 84f, 92f, 7f)
            l(16f, 58f, 90f, 16f, 6f)
            drawRect(ink, Offset(6f, 54f), Size(18f, 20f), alpha = alpha)
            l(90f, 16f, 88f, 40f, 3f); drawCircle(ink, 6f, Offset(88f, 44f), alpha = alpha)
        }
        Cyvasse.DRAGON -> { // a dragon seen from above, wings spread
            fill(p { moveTo(50f, 34f); lineTo(6f, 18f); quadraticTo(16f, 34f, 8f, 46f); quadraticTo(22f, 44f, 22f, 58f); quadraticTo(36f, 50f, 46f, 60f); close() })
            fill(p { moveTo(50f, 34f); lineTo(94f, 18f); quadraticTo(84f, 34f, 92f, 46f); quadraticTo(78f, 44f, 78f, 58f); quadraticTo(64f, 50f, 54f, 60f); close() })
            drawOval(ink, Offset(42f, 26f), Size(16f, 44f), alpha = alpha)
            drawCircle(ink, 9f, Offset(50f, 18f), alpha = alpha)
            drawPath(p { moveTo(50f, 68f); quadraticTo(60f, 82f, 46f, 90f); quadraticTo(38f, 94f, 42f, 98f) }, ink, alpha = alpha, style = Stroke(6f, cap = StrokeCap.Round))
        }
        Cyvasse.KING -> { // a crown
            fill(p { moveTo(16f, 72f); lineTo(12f, 30f); lineTo(32f, 50f); lineTo(50f, 20f); lineTo(68f, 50f); lineTo(88f, 30f); lineTo(84f, 72f); close() })
            drawRect(ink, Offset(16f, 77f), Size(68f, 11f), alpha = alpha)
            for (x in listOf(12f, 50f, 88f)) drawCircle(ink, 6f, Offset(x, if (x == 50f) 16f else 26f), alpha = alpha)
        }
    }
}

/** A mountain: two plain peaks. */
private fun DrawScope.mountainShape(r: Rect) = inBox(r) {
    drawPath(p { moveTo(8f, 84f); lineTo(38f, 26f); lineTo(58f, 62f); lineTo(70f, 44f); lineTo(94f, 84f); close() }, Color(0xFF8F887C))
    drawPath(p { moveTo(31f, 40f); lineTo(38f, 26f); lineTo(45f, 40f); close() }, Color.White.copy(alpha = 0.85f))
}

/** A small tree for forest tiles. */
private fun DrawScope.treeShape(r: Rect) = inBox(r) {
    drawLine(Color(0xFF7A6A52), Offset(50f, 66f), Offset(50f, 84f), 6f)
    drawPath(p { moveTo(50f, 18f); lineTo(70f, 50f); lineTo(60f, 50f); lineTo(74f, 70f); lineTo(26f, 70f); lineTo(40f, 50f); lineTo(30f, 50f); close() }, Color(0xFF7E9A62))
}

/** A keep: a tower with battlements, in its side's colours. */
private fun DrawScope.keepShape(r: Rect, side: Int) = inBox(r) {
    val face = if (side == 0) IVORY else ONYX
    val edge = if (side == 0) GOLD else SILVER
    val tower = p {
        moveTo(22f, 90f); lineTo(22f, 30f); lineTo(30f, 30f); lineTo(30f, 18f); lineTo(42f, 18f); lineTo(42f, 30f); lineTo(58f, 30f)
        lineTo(58f, 18f); lineTo(70f, 18f); lineTo(70f, 30f); lineTo(78f, 30f); lineTo(78f, 90f); close()
    }
    drawPath(tower, face)
    drawPath(tower, edge, style = Stroke(5f))
    drawPath(p { moveTo(42f, 90f); lineTo(42f, 70f); quadraticTo(50f, 60f, 58f, 70f); lineTo(58f, 90f); close() }, edge)
}

/**
 * The battlefield: plain grass, forest and water tiles, mountains and keeps, the pieces, and (while setting up)
 * the screen over the rival's half.
 */
@Composable
fun CyvasseBoard(s: CyvasseSession, modifier: Modifier = Modifier, onTap: ((Int) -> Unit)? = null) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val n = Cyvasse.N
    val flip = s.flipped
    fun v(i: Int) = if (flip) Cyvasse.CELLS - 1 - i else i
    val anim = remember(s.moveId) { Animatable(0f) }
    LaunchedEffect(s.moveId) { anim.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
    val targets = if (g.phase == Cyvasse.BATTLE) s.targets() else emptyMap()
    val me = s.viewer
    val setup = g.phase == Cyvasse.SETUP
    val frameArt = ImageBitmap.imageResource(com.puresudoku.R.drawable.cyvasse_frame)
    val grassArt = ImageBitmap.imageResource(com.puresudoku.R.drawable.cyvasse_grass)
    val waterArt = ImageBitmap.imageResource(com.puresudoku.R.drawable.cyvasse_water)
    val forestArt = ImageBitmap.imageResource(com.puresudoku.R.drawable.cyvasse_forest)
    val mountainArt = ImageBitmap.imageResource(com.puresudoku.R.drawable.cyvasse_mountain)
    val keepArt = ImageBitmap.imageResource(com.puresudoku.R.drawable.cyvasse_keep)
    val ivoryArt = Cyvasse.TYPES.map { cyvasseArt(it, 0) }
    val onyxArt = Cyvasse.TYPES.map { cyvasseArt(it, 1) }
    Canvas(modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).then(if (onTap != null) Modifier.pointerInput(s) {
        detectTapGestures { o ->
            val margin = size.width * 0.101f
            val cell = (size.width - 2 * margin) / n.toFloat()
            if (o.x < margin || o.y < margin || o.x >= size.width - margin || o.y >= size.height - margin) return@detectTapGestures
            val col = ((o.x - margin) / cell).toInt(); val row = ((o.y - margin) / cell).toInt()
            onTap(v(row * n + col))
        }
    } else Modifier)) {
        val margin = size.width * 0.101f
        val boardSize = size.width - 2 * margin
        val cs = boardSize / n
        fun rectOf(cell: Int): Rect { val d = v(cell); return Rect(margin + (d % n) * cs, margin + (d / n) * cs, margin + (d % n + 1) * cs, margin + (d / n + 1) * cs) }
        fun inset(r: Rect, f: Float) = Rect(r.left + r.width * f, r.top + r.height * f, r.right - r.width * f, r.bottom - r.height * f)
        paintCyvasse(frameArt, Rect(0f, 0f, size.width, size.height))
        // the rival's half is hidden while setting up
        fun hidden(cell: Int) = setup && !Cyvasse.inHalf(me, cell)
        for (c in 0 until Cyvasse.CELLS) {
            val r = rectOf(c)
            val mountain = g.block[c] == Cyvasse.MOUNTAIN && !hidden(c)
            val tile = when { mountain -> mountainArt; g.terrain[c] == Cyvasse.WATER -> waterArt; g.terrain[c] == Cyvasse.FOREST -> forestArt; else -> grassArt }
            paintCyvasse(tile, r)
        }
        for (k in 0..n) {
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(margin + k * cs, margin), Offset(margin + k * cs, margin + boardSize), 1f)
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(margin, margin + k * cs), Offset(margin + boardSize, margin + k * cs), 1f)
        }
        // the line between the halves
        drawLine(Color(0xFF8C7A55).copy(alpha = 0.5f), Offset(margin, margin + n / 2 * cs), Offset(margin + boardSize, margin + n / 2 * cs), cs * 0.03f)
        // the last move
        if (g.lastMove >= 0 && g.phase != Cyvasse.SETUP) {
            val m = g.lastMove
            val cells = when (Cyvasse.kindOf(m)) { Cyvasse.K_MOVE, Cyvasse.K_RANGED -> listOf(Cyvasse.aOf(m), Cyvasse.bOf(m)); Cyvasse.K_DEPLOY -> listOf(Cyvasse.bOf(m)); else -> emptyList() }
            for (c in cells) { val r = rectOf(c); drawRect(Color(0xFFF3E08A).copy(alpha = 0.6f), r.topLeft, r.size) }
        }
        if (s.selected >= 0) { val r = rectOf(s.selected); drawRect(Color(0xFF7FB2E5).copy(alpha = 0.55f), r.topLeft, r.size) }
        // keeps
        for (side in 0..1) {
            val k = g.keepAt[side]
            if (k < 0 || hidden(k)) continue
            val r = rectOf(k)
            paintCyvasse(keepArt, r)
            if (g.keepGone[side]) drawRect(Color.Black.copy(alpha = 0.55f), r.topLeft, r.size)
            drawCircle(if (side == 0) IVORY else Color(0xFF20232D), cs * 0.095f, r.topLeft + Offset(cs * 0.12f, cs * 0.12f))
        }
        // pieces (the one that just moved slides in)
        val lm = g.lastMove
        for (c in 0 until Cyvasse.CELLS) {
            val pc = g.board[c]
            if (pc == 0 || hidden(c)) continue
            var center = rectOf(c).center
            if (anim.value < 1f && lm >= 0 && Cyvasse.kindOf(lm) == Cyvasse.K_MOVE && Cyvasse.bOf(lm) == c) {
                val from = rectOf(Cyvasse.aOf(lm)).center
                // a light horse that rode round the edge just appears
                if (kotlin.math.abs(from.x - center.x) < size.width / 2 && kotlin.math.abs(from.y - center.y) < size.height / 2) center = from + (center - from) * anim.value
            }
            val art = (if (Cyvasse.ownerOf(pc) == 0) ivoryArt else onyxArt)[Cyvasse.typeOf(pc) - 1]
            paintCyvasse(art, Rect(center.x - cs * 0.47f, center.y - cs * 0.47f, center.x + cs * 0.47f, center.y + cs * 0.47f))
            if (!setup && g.waterWait[c] > 0) glyph("1", center + Offset(cs * 0.32f, -cs * 0.30f), cs * 0.24f, Color.White)
        }
        // where the chosen piece can go
        for ((t, m) in targets) {
            val c = rectOf(t).center
            when {
                Cyvasse.kindOf(m) == Cyvasse.K_SWAP -> drawCircle(Color(0xFF4A86C5), cs * 0.46f, c, style = Stroke(cs * 0.07f))
                g.victims(m).isNotEmpty() -> {
                    drawCircle(Color(0xFFC94A3F), cs * 0.46f, c, style = Stroke(cs * 0.07f))

                }
                else -> drawCircle(Color.Black.copy(alpha = 0.28f), cs * 0.13f, c)
            }
        }
        coachRings(listOf(s.coachFrom, s.coachTo).filter { it >= 0 }.map { rectOf(it).center }, cs * 0.47f)
        // the screen over the rival's half
        if (setup) {
            val h = n / 2 * cs
            val top = margin + if (hidden(v(0))) 0f else h
            drawRect(Color(0xFF6B5B45), Offset(margin, top), Size(boardSize, h))
            drawRect(Color(0xFF8C7A55), Offset(margin, if (top == margin) top + h - cs * 0.06f else top), Size(boardSize, cs * 0.06f))
            glyph("The screen", Offset(size.width / 2, top + h / 2), cs * 0.5f, Color(0xFFF3EAD6))
        }
        // coordinates: letters along the bottom, numbers up the left (row 1 is Alabaster's back row)
        val label = Color(0xFF6B5B45).copy(alpha = 0.75f)
        for (k in 0 until n) {
            val letter = Cyvasse.col(v((n - 1) * n + k))
            val number = Cyvasse.N - Cyvasse.row(v(k * n))
            if (!hidden(v((n - 1) * n + k))) glyph("${'a' + letter}", Offset(margin + k * cs + cs * 0.88f, margin + boardSize - cs * 0.12f), cs * 0.18f, label)
            if (!hidden(v(k * n))) glyph("$number", Offset(margin + cs * 0.13f, margin + k * cs + cs * 0.14f), cs * 0.18f, label)
        }
    }
}

/** One small piece token, for the reserve tray and the captured row. */
@Composable
private fun PieceChip(type: Int, side: Int, size: Dp) {
    val art = cyvasseArt(type, side)
    Canvas(Modifier.size(size)) { paintCyvasse(art, Rect(0f, 0f, this.size.width, this.size.height)) }
}

// =====================================================================  the screen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CyvasseUi(s: CyvasseSession) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    if (s.localTwoPlayer && s.handoffPending && !s.online) {
        com.puresudoku.ui.components.HandoffScreen(s.currentPlayer + 1, onReady = { s.acceptHandoff() },
            color = if (s.currentPlayer == 0) Color(0xFF8C7A55) else Color(0xFF2A2631))
        return
    }
    val me = s.viewer
    val foe = 1 - me
    val setup = g.phase == Cyvasse.SETUP
    // everything here sits on light panels, so text is the theme's ink rather than the backdrop's white
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val big = minOf(maxWidth, 460.dp, (maxHeight - 220.dp).coerceAtLeast(180.dp))
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            if (setup) {
                Label(if (s.localTwoPlayer || s.online) "${Cyvasse.sideName(g.placer)}: set up behind the screen" else "You are Alabaster: set up behind the screen")
            } else {
                // the rival's side of the table: what it has left unseen, and what it has taken
                SideRow(s, foe, Modifier.width(big))
            }
            Box(Modifier.width(big).tableUpright()) {
                CyvasseBoard(s, Modifier.fillMaxWidth(), onTap = { s.tap(it) })
            }
            if (!setup) SideRow(s, me, Modifier.width(big))
            Spacer(Modifier.height(4.dp))
            val sel = s.selected.takeIf { it >= 0 }?.let { g.board[it] }?.takeIf { it != 0 }
            val about = sel?.let { Cyvasse.typeOf(it) } ?: s.pickType.takeIf { it > 0 }
            if (about != null) {
                Text("${Cyvasse.NAMES[about]}: ${Cyvasse.MOVES_TEXT[about]}", fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.width(big).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).padding(horizontal = 10.dp, vertical = 6.dp))
                if (!setup && sel != null && Cyvasse.typeOf(sel) == Cyvasse.TREBUCHET) {
                    val dir = g.facing[s.selected] - 1
                    val arrow = if (dir in Cyvasse.ALL8.indices) {
                        val d = Cyvasse.ALL8[dir]; when (d[0] to d[1]) {
                            -1 to 0 -> "↑"; 1 to 0 -> "↓"; 0 to -1 -> "←"; 0 to 1 -> "→"
                            -1 to -1 -> "↖"; -1 to 1 -> "↗"; 1 to -1 -> "↙"; else -> "↘"
                        }
                    } else "not aimed yet"
                    Text("Trebuchet direction: $arrow", fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
            }
            if (setup) SetupControls(s, Modifier.width(big))
            else if (sel != null && Cyvasse.typeOf(sel) in listOf(Cyvasse.CROSSBOW, Cyvasse.CATAPULT, Cyvasse.TREBUCHET) && s.myMove) {
                OutlinedButton(onClick = { s.rangedMode = !s.rangedMode }) { Text(if (s.rangedMode) "Attack: ranged (tap to use melee)" else "Attack: melee (tap to use ranged)") }
            }
            Spacer(Modifier.height(6.dp))
            TurnBanner(if (s.thinking) (if (setup) "The rival is setting up…" else "The rival is thinking…") else s.message.ifEmpty { "Your move" }, s.thinking)
            if (!setup && s.myMove && g.legalMoves().contentEquals(intArrayOf(Cyvasse.PASS))) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = s::endBonus) { Text("Pass turn (no available moves)") }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    }
}

/** A side of the table: its name, reserve count and keep, and the pieces it has taken. */
@Composable
private fun SideRow(s: CyvasseSession, side: Int, modifier: Modifier) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val mine = side == s.viewer
    Row(modifier.padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val name = when { s.online || s.localTwoPlayer -> Cyvasse.sideName(side); mine -> "You"; else -> "Rival" }
        val turn = g.phase == Cyvasse.BATTLE && g.turn == side
        Text((if (turn) "▶ " else "") + name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(if (!g.keepStands(side)) "fortress ruined" else if (g.fortHits[side] > 0) "fortress weakened" else "fortress intact", fontSize = 12.sp,
            color = if (g.keepStands(side)) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
        Spacer(Modifier.weight(1f))
        // what this side has taken
        val taken = Cyvasse.TYPES.filter { g.captured[side][it] > 0 }
        if (taken.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
            for (t in taken.sortedByDescending { Cyvasse.VALUE[it] }.take(6)) {
                PieceChip(t, 1 - side, 20.dp)
                if (g.captured[side][t] > 1) Text("${g.captured[side][t]}", fontSize = 10.sp)
            }
            if (taken.size > 6) Text("+", fontSize = 11.sp)
        }
    }
}

/** Your reserve pieces: the pieces still to stand during setup, or (in battle) the ones to bring out, kept covered until opened. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PieceTray(s: CyvasseSession, side: Int, modifier: Modifier) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (t in Cyvasse.TYPES) {
            val n = g.reserve[side][t]
            if (n == 0) continue
            val on = s.pickType == t
            Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)).clickable { s.pickReserve(t) }.padding(start = 2.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                PieceChip(t, side, 28.dp)
                Spacer(Modifier.width(2.dp))
                Text("×$n", fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ReserveTray(s: CyvasseSession, modifier: Modifier) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val me = s.viewer
    if (g.reserveCount(me) == 0 || !g.keepStands(me)) return
    // on a shared device the reserve is your secret: only show it on your own turn, when you open it
    val myTurn = !s.localTwoPlayer || s.online || g.turn == me
    if (!myTurn) return
    if (!s.showReserve) {
        FilledTonalButton(onClick = { s.showReserve = true }) { Text("Reserve (${g.reserveCount(me)}): tap to look") }
        return
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PieceTray(s, me, Modifier.fillMaxWidth())
        androidx.compose.material3.TextButton(onClick = { s.showReserve = false }) { Text("Hide reserve") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupControls(s: CyvasseSession, modifier: Modifier) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val p = s.currentPlayer
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (!s.myMove) return@Column
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolChip("Water ${g.terrainPlaced(p, Cyvasse.WATER)}/5", s.tool == CyvasseSession.TOOL_WATER) { s.chooseTool(CyvasseSession.TOOL_WATER) }
            ToolChip("Forests ${g.terrainPlaced(p, Cyvasse.FOREST)}/6", s.tool == CyvasseSession.TOOL_FOREST) { s.chooseTool(CyvasseSession.TOOL_FOREST) }
            ToolChip("Mountains ${g.mountainsPlaced(p)}/${Cyvasse.MOUNTAINS}", s.tool == CyvasseSession.TOOL_MOUNTAIN) { s.chooseTool(CyvasseSession.TOOL_MOUNTAIN) }
            ToolChip(if (g.keepAt[p] >= 0) "Fortress ✓" else "Fortress", s.tool == CyvasseSession.TOOL_KEEP) { s.chooseTool(CyvasseSession.TOOL_KEEP) }
            ToolChip("Pieces", s.tool == CyvasseSession.TOOL_PIECES) { s.chooseTool(CyvasseSession.TOOL_PIECES) }
        }
        Spacer(Modifier.height(6.dp))
        if (s.tool == CyvasseSession.TOOL_PIECES) {
            PieceTray(s, p, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = s::scatter) { Text("Random") }
            FilledTonalButton(onClick = s::clearSetup) { Text("Clear") }
            Button(onClick = s::ready, enabled = g.setupDone(p)) { Text("Ready") }
        }
    }
}

@Composable
private fun ToolChip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(text, Modifier.clip(RoundedCornerShape(50))
        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
}

@Composable
private fun Label(text: String) {
    Text(text, Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)).padding(horizontal = 10.dp, vertical = 4.dp),
        fontWeight = FontWeight.Bold, fontSize = 14.sp)
}
