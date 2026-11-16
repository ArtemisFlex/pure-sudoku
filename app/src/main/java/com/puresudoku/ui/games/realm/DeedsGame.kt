package com.puresudoku.ui.games.realm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import com.puresudoku.core.board.Deeds
import com.puresudoku.core.board.Deeds.Act
import com.puresudoku.core.board.DeedsAi
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.components.tableUpright
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.games.board.DuelSession
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.MiddleStat
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.tutorial.TutorialStep
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// =====================================================================  colours and art

/** Banner colour for each colour choice (Natural flies gold). */
internal val DEED_COLORS = listOf(Color(0xFFC9A227), Color(0xFF2E7BD6), Color(0xFFD93A3F), Color(0xFF2E9E57), Color(0xFFF29D1E), Color(0xFF8E54C9))
internal val DOMAIN_COLORS = listOf(
    Color(0xFF8B5A2B), Color(0xFF9CCC65), Color(0xFF2E7D32), Color(0xFF5E35B1),
    Color(0xFF00897B), Color(0xFF78909C), Color(0xFFC2185B), Color(0xFFF9A825),
)
private val HERO_ART = intArrayOf(com.puresudoku.R.drawable.rt_dragon, com.puresudoku.R.drawable.rt_knight_piece, com.puresudoku.R.drawable.rt_thief, com.puresudoku.R.drawable.rt_black_knight)
private val DOMAIN_ART = intArrayOf(
    com.puresudoku.R.drawable.rt_ag_swamp, com.puresudoku.R.drawable.rt_ag_farm, com.puresudoku.R.drawable.rt_timber_1, com.puresudoku.R.drawable.rt_ag_hills,
    com.puresudoku.R.drawable.rt_ag_fishery, com.puresudoku.R.drawable.rt_ag_quarry, com.puresudoku.R.drawable.rt_ag_town, com.puresudoku.R.drawable.rt_ag_capital,
)

/** A hero piece tinted in its colour (Natural keeps the painted colours). */
internal fun heroTint(c: Int): ColorFilter? = if (c <= 0) null else ColorFilter.tint(lerp(Color.White, DEED_COLORS[c], 0.42f), BlendMode.Modulate)

private class DeedArt(val heroes: List<ImageBitmap>, val domains: List<ImageBitmap>, val special: Map<Int, ImageBitmap>, val tower: ImageBitmap, val castle: ImageBitmap, val road: ImageBitmap)

@Composable
private fun rememberDeedArt(): DeedArt {
    @Composable fun img(id: Int) = ImageBitmap.imageResource(id)
    val heroes = HERO_ART.map { img(it) }
    val domains = DOMAIN_ART.map { img(it) }
    val special = mapOf(
        Deeds.SPIRE to img(com.puresudoku.R.drawable.rt_wonder_sky_temple),
        Deeds.DUNGEON to img(com.puresudoku.R.drawable.rt_ag_barracks),
        Deeds.ARENA to img(com.puresudoku.R.drawable.rt_ag_colosseum),
        Deeds.SEIZE to img(com.puresudoku.R.drawable.rt_black_knight),
        Deeds.FATE to img(com.puresudoku.R.drawable.rt_ui_scroll),
        Deeds.LOOT to img(com.puresudoku.R.drawable.rt_ui_relic),
        Deeds.TITHE to img(com.puresudoku.R.drawable.rt_ag_temple),
        Deeds.LEVY to img(com.puresudoku.R.drawable.rt_ui_crown),
        100 to img(com.puresudoku.R.drawable.rt_ag_mill),
        101 to img(com.puresudoku.R.drawable.rt_ag_mine),
    )
    val tower = img(com.puresudoku.R.drawable.rt_ag_tower)
    val castle = img(com.puresudoku.R.drawable.rt_wonder_citadel)
    val road = img(com.puresudoku.R.drawable.rt_road_neutral)
    return remember(heroes, domains, tower) { DeedArt(heroes, domains, special, tower, castle, road) }
}

// =====================================================================  session

class DeedsSession(difficulty: Difficulty, val g: Deeds) : DuelSession(GameKind.DEED_DUNGEON, difficulty) {
    var events by mutableStateOf<List<String>>(emptyList()); private set
    /** The tile whose details show in the middle of the board, or -1. */
    var selected by mutableIntStateOf(-1)

    override val tableSeat: Int? get() = if (localTwoPlayer) g.actor + 1 else null
    override fun localSide(actor: Int): Pair<String, Color> = "Player $actor" to seatColor(actor - 1)
    fun seatColor(p: Int): Color = g.color[p].let { if (it in DEED_COLORS.indices) DEED_COLORS[it] else if (p == 0) YOU else RIVAL }

    /** Whose lands and items the screen shows: your own seat online, the player to move otherwise. */
    val viewer: Int get() = if (online) onlineSeat - 1 else if (localTwoPlayer) g.actor else 0
    fun human(p: Int) = localTwoPlayer || p == 0
    fun who(p: Int): String = when {
        online -> if (p == onlineSeat - 1) "You" else "Opponent"
        localTwoPlayer -> "Player ${p + 1}"
        else -> if (p == 0) "You" else "Rival"
    }

    override val middleStat get() = MiddleStat.Counter("Round", "${g.round}/${g.maxRounds}")
    override val progress: Float get() = (g.round.toFloat() / g.maxRounds).coerceAtMost(0.95f)
    override val wonTitle get() = if (localTwoPlayer) "Player 1 is crowned!" else "You are King of Eiseria!"
    override val lostTitle get() = if (localTwoPlayer) "Player 2 is crowned!" else "The rival takes the throne"
    override val drawTitle get() = "Two lords, one throne"
    override val lostMessage get() = when (g.endReason) {
        1 -> "You were felled with no lands left. "
        2 -> "The rival held four whole domains. "
        3 -> "The old King died and the rival had more renown. "
        else -> ""
    } + "This game's save slot has been cleared."

    val myMove: Boolean get() = outcome == Outcome.PLAYING && !thinking && !heraldBusy && g.phase != Deeds.OVER && human(g.actor) && myOnlineTurn

    private fun flush() {
        if (g.log.isNotEmpty()) { events = (events + g.log).takeLast(40); g.log.clear() }
    }

    fun act(type: Int, arg: Int = -1) {
        if (!myMove) { if (thinking || heraldBusy) nudge("Wait for the rival to finish"); return }
        val who = g.actor
        if (!g.perform(Act(type, arg), rng)) { nudge(refusal(type)); return }
        // "Roll again" after doubles is one tap
        if (type == Act.END && g.phase == Deeds.ROLL && g.actor == who) g.perform(Act(Act.ROLL), rng)
        if (type == Act.ROLL || type == Act.END || type == Act.CHOOSE) selected = -1
        coachTip = null
        moves++
        val lines = g.log.toList()
        flush(); bump(); changed()
        for (line in lines) if (KEY_MOMENTS.any { it in line }) announce(line, "", 1500, true, iconFor(line))
        if (g.phase == Deeds.OVER) { finishGame(); return }
        aiTurn()
    }

    private val KEY_MOMENTS = listOf("coin lands", "felled", "Trial by combat", "wins and takes", "crowned", "omen", "now holds all", "Shield takes", "Siege Engine", "is dead")

    private fun refusal(type: Int) = when (type) {
        Act.BUY -> "Not enough gold. Pawn a land or tear down a tower first"
        Act.PAY -> "Not enough gold. Raise funds, or refuse and take the wounds"
        Act.BUILD -> "Build evenly, on a whole domain with nothing pawned, and have the gold"
        Act.FLIP -> "Coins turn only after passing the Spire, or with a Royal Banner"
        Act.PAWN -> "Tear down the domain's towers before pawning"
        else -> "You can't do that now"
    }

    // ---------------------------------------------------------------- the rival's turn

    private var aiSteps = 0

    private fun aiTurn() {
        if (localTwoPlayer || outcome != Outcome.PLAYING || g.phase == Deeds.OVER || g.actor != 1) { aiSteps = 0; return }
        if (++aiSteps > 120) { aiSteps = 0; return }
        val snap = g.copy(); val seed = rng.nextLong()
        val pause = when (g.phase) { Deeds.ROLL -> 650L; Deeds.DECIDE -> 750L; else -> 450L }
        computer(minMs = pause, think = { DeedsAi.next(snap, Random(seed)) }) { a ->
            var done = g.perform(a, rng)
            if (!done) for (t in listOf(Act.ACK, Act.DEBT, Act.BLADE, Act.REFUSE, Act.PASS, Act.DECLINE, Act.ROLL, Act.END)) { if (g.perform(Act(t), rng)) { done = true; break } }
            val lines = g.log.toList()
            flush(); bump(); changed()
            for (line in lines) announce(line, "", if (lines.size > 2) 1300 else 1600, false, iconFor(line))
            if (g.phase == Deeds.OVER) { whenQuiet { finishGame() }; return@computer }
            whenQuiet { aiTurn() }
        }
    }

    internal fun iconFor(line: String): RI = when {
        "felled" in line || "falls" in line -> RI.DAGGER
        "Dungeon" in line || "Inquisitor" in line -> RI.BLACK_KNIGHT
        "Trial" in line || "arena" in line || "Colosseum" in line -> RI.KNIGHT
        "Blade" in line || "strike" in line -> RI.DAGGER
        "homage" in line -> RI.CROWN
        "buys" in line || "claims" in line || "seizes" in line || "takes" in line -> RI.SCROLL
        "tower" in line || "castle" in line -> RI.TOWER
        "Fate" in line -> RI.SCROLL
        "Loot" in line -> RI.RELIC
        "Spire" in line -> RI.SUN
        "coin" in line -> RI.SHIELD
        "omen" in line -> RI.MOON
        "rolls" in line -> RI.DICE
        "Dragon" in line -> RI.DRAGON
        else -> RI.SCROLL
    }

    private fun finishGame() {
        when (g.winner) {
            0 -> win(listOf(BonusLine("Renown", g.renown(0) / 20 * rules.basePoints / 10), BonusLine("Lands held", g.deeds(0).size * rules.basePoints)) +
                if (g.endReason != 3) listOf(BonusLine("Crowned early", rules.basePoints * 10)) else emptyList())
            2 -> draw()
            else -> lose()
        }
    }

    val prompt: String get() = when {
        outcome != Outcome.PLAYING -> ""
        heraldBusy -> "Tap a card to hurry the rival along"
        thinking -> "The rival is deciding…"
        online && !myOnlineTurn -> if (g.phase == Deeds.SETUP) "Your opponent is choosing a champion" else "Your opponent's turn"
        g.phase == Deeds.SETUP -> if (human(g.actor)) "Choose your champion" else "The rival chooses a champion…"
        !human(g.actor) -> "The rival's turn"
        g.phase == Deeds.ROLL -> if (g.jail[g.actor] >= 0) "In the Dungeon: roll doubles, pay the jailer or use a key" else if (g.again) "Doubles! Roll again" else "Roll the dice"
        g.phase == Deeds.DECIDE -> when (g.pType) {
            Deeds.D_BUY -> "Buy ${g.names[g.pos[g.actor]]}?"
            Deeds.D_HOMAGE -> "${g.Title(g.owner[g.pos[g.actor]])} demands homage"
            Deeds.D_BLADE -> "The Blade! Take the wounds or show a writ"
            Deeds.D_DEBT -> "You owe more gold than you hold"
            Deeds.D_ARENA -> "Challenge your rival to trial by combat?"
            else -> "Read the card"
        }
        g.spireCharge -> "You passed the Spire: turn your coins in Lands, then end your turn"
        else -> if (g.again && g.jail[g.actor] < 0) "Build in Lands, then roll again" else "Build in Lands, then end your turn"
    }

    /** Tells the coach's choice in words. */
    fun describe(a: Act): String = when (a.type) {
        Act.ROLL -> "Roll the dice."
        Act.BUY -> "Buy this deed."
        Act.PASS -> "Leave this one: keep your gold."
        Act.PAY -> "Pay the homage: the wounds would cost you more."
        Act.REFUSE -> "Refuse homage and take the wounds."
        Act.BLADE -> "Take the wounds."
        Act.WRIT -> "Show your Forged Writ."
        Act.DEBT -> "Pay what you can."
        Act.CHALLENGE -> "Challenge them: you have more to gain than to lose."
        Act.DECLINE -> "Leave the arena: the stakes favour them."
        Act.ACK -> "Carry out the card."
        Act.BUILD -> "Raise a tower at ${g.names[a.arg]}."
        Act.SELL -> "Tear down a tower at ${g.names[a.arg]}."
        Act.PAWN -> "Pawn ${g.names[a.arg]} to raise gold."
        Act.REDEEM -> "Buy back ${g.names[a.arg]}."
        Act.FLIP -> "Turn the coin of ${g.names[a.arg]} to its ${if (g.coin[a.arg] == Deeds.COIN_CROWN) "Blade" else "Crown"}."
        Act.POTION -> "Drink your Healing Potion."
        Act.SIEGE -> "Use your Siege Engine."
        Act.BRIBE -> "Pay the jailer: the board is still open."
        Act.KEY -> "Use your Skeleton Key."
        Act.END -> "End your turn."
        else -> "Choose any champion you like."
    }

    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (!myMove) return null
        val snap = g.copy().also { it.level = 5 }
        return { val a = DeedsAi.next(snap, Random(7)); Triple(describe(a), a.arg, -1) }
    }

    override fun resume() {
        if (outcome != Outcome.PLAYING) return
        if (g.phase == Deeds.OVER) { finishGame(); return }
        aiTurn()
    }

    override fun saveState(): JSONObject = JSONObject().put("g", g.toJson()).put("moves", moves).put("localTwoPlayer", localTwoPlayer)
        .put("events", JSONArray().also { a -> events.takeLast(12).forEach { a.put(it) } })

    companion object {
        fun restore(o: JSONObject): DeedsSession {
            val st = o.getJSONObject("state")
            val g = Deeds.fromJson(st.getJSONObject("g"))
            return DeedsSession(Difficulty.valueOf(o.getString("difficulty")), g).also { s ->
                s.moves = st.optInt("moves"); s.localTwoPlayer = st.optBoolean("localTwoPlayer", false)
                val ev = st.optJSONArray("events"); if (ev != null) s.events = List(ev.length()) { ev.getString(it) }
                s.restoreCommon(o)
            }
        }
    }
}

// =====================================================================  definition

object DeedsDef : GameDef {
    override val kind = GameKind.DEED_DUNGEON
    override val fullBleed: Boolean get() = true
    override val hasHeader: Boolean get() = true
    @Composable override fun Header(session: Session, actions: com.puresudoku.ui.games.HeaderActions) = RealmHeader(session, actions, "Deed or Dungeon · ")

    override fun generate(d: Difficulty): Session = DeedsSession(d, Deeds.newGame(d.ordinal, Random(Random.nextLong()))).also { it.resume() }
    override fun restore(o: JSONObject): Session = DeedsSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Buy deeds, set each coin to Homage or Blade at the Spire, and fell your rival to seize their lands. Become King of Eiseria."
    override fun background(session: Session) = com.puresudoku.R.drawable.bg_realm_ocean
    override fun levelInfo(d: Difficulty) = "${Deeds.ROUNDS[d.ordinal]} rounds · 4 whole domains crowns you at once"
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A careless rival who buys on a whim."
        Difficulty.MEDIUM -> "Buys sensibly and starts building towers."
        Difficulty.HARD -> "Blocks your domains and bleeds you when you're weak."
        Difficulty.EXPERT -> "Turns its Blades the moment you falter."
        Difficulty.MASTER -> "A shrewd lord with a long reign to plan."
        Difficulty.EXTREME -> "Ruthless, patient, and very hard to topple."
    }
    @Composable override fun Play(session: Session) = DeedsUi(session as DeedsSession)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)
    override val tutorial: List<TutorialStep> get() = deedsTutorial()
}

// =====================================================================  board geometry

/** The rectangle of tile [t] on a board [w] wide: 9 tiles a side, corners half as big again. */
internal fun tileRect(t: Int, w: Float): Rect {
    val e = w / 11f; val c = e * 1.5f
    return when (t) {
        0 -> Rect(w - c, w - c, w, w)
        in 1..8 -> Rect(w - c - t * e, w - c, w - c - (t - 1) * e, w)
        9 -> Rect(0f, w - c, c, w)
        in 10..17 -> { val k = t - 9; Rect(0f, w - c - k * e, c, w - c - (k - 1) * e) }
        18 -> Rect(0f, 0f, c, c)
        in 19..26 -> { val k = t - 18; Rect(c + (k - 1) * e, 0f, c + k * e, c) }
        27 -> Rect(w - c, 0f, w, c)
        else -> { val k = t - 27; Rect(w - c, c + (k - 1) * e, w, c + k * e) }
    }
}

/** Which side of the board a tile sits on: 0 bottom, 1 left, 2 top, 3 right (corners count with the side they start). */
private fun side(t: Int) = when (t) { in 0..8 -> 0; in 9..17 -> 1; in 18..26 -> 2; else -> 3 }

private fun DrawScope.art(img: ImageBitmap, centre: Offset, box: Float, filter: ColorFilter? = null, alpha: Float = 1f) {
    val sc = box / maxOf(img.width, img.height)
    val w = (img.width * sc).toInt().coerceAtLeast(1); val h = (img.height * sc).toInt().coerceAtLeast(1)
    drawImage(img, dstOffset = IntOffset((centre.x - w / 2f).toInt(), (centre.y - h / 2f).toInt()), dstSize = IntSize(w, h), colorFilter = filter, alpha = alpha, filterQuality = FilterQuality.Medium)
}

/** The two-sided coin: a crown on gold, or a blade on steel, ringed in the owner's colour. */
internal fun DrawScope.deedCoin(c: Offset, r: Float, face: Int, owner: Color, dim: Boolean = false) {
    val a = if (dim) 0.45f else 1f
    drawCircle(Color.Black.copy(alpha = 0.35f * a), r * 1.08f, c + Offset(0f, r * 0.12f))
    drawCircle(owner.copy(alpha = a), r, c)
    val inner = r * 0.78f
    drawCircle(Brush.radialGradient(if (face == Deeds.COIN_CROWN) listOf(Color(0xFFFFE082), Color(0xFFC9972F)) else listOf(Color(0xFFEF6C6C), Color(0xFF8E1B1B)), c - Offset(inner * 0.3f, inner * 0.3f), inner * 1.6f), inner, c, alpha = a)
    if (face == Deeds.COIN_CROWN) {
        val u = inner * 0.62f
        val p = Path().apply {
            moveTo(c.x - u, c.y + u * 0.55f); lineTo(c.x - u, c.y - u * 0.35f); lineTo(c.x - u * 0.5f, c.y + u * 0.05f); lineTo(c.x, c.y - u * 0.65f)
            lineTo(c.x + u * 0.5f, c.y + u * 0.05f); lineTo(c.x + u, c.y - u * 0.35f); lineTo(c.x + u, c.y + u * 0.55f); close()
        }
        drawPath(p, Color(0xFF8D5A12).copy(alpha = a))
    } else {
        val u = inner * 0.7f
        rotate(-45f, c) {
            drawLine(Color.White.copy(alpha = a), c - Offset(0f, u), c + Offset(0f, u * 0.55f), u * 0.26f, StrokeCap.Round)
            drawLine(Color(0xFFFFD54F).copy(alpha = a), c + Offset(-u * 0.45f, u * 0.42f), c + Offset(u * 0.45f, u * 0.42f), u * 0.22f, StrokeCap.Round)
            drawLine(Color(0xFFFFD54F).copy(alpha = a), c + Offset(0f, u * 0.5f), c + Offset(0f, u * 0.85f), u * 0.2f, StrokeCap.Round)
        }
    }
}

// =====================================================================  the board

@Composable
private fun DeedsBoard(s: DeedsSession, modifier: Modifier = Modifier, interactive: Boolean = true) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val art = rememberDeedArt()
    // pieces hop tile by tile toward where they now stand
    val shown = remember(s) { intArrayOf(g.pos[0], g.pos[1]) }
    var tick by remember(s) { mutableIntStateOf(0) }
    for (p in 0..1) {
        val target = g.pos[p]
        LaunchedEffect(s, p, target, g.rollId) {
            val dist = ((target - shown[p]) % Deeds.N + Deeds.N) % Deeds.N
            if (dist in 1..12) repeat(dist) { kotlinx.coroutines.delay(110); shown[p] = (shown[p] + 1) % Deeds.N; tick++ }
            else if (shown[p] != target) { kotlinx.coroutines.delay(150); shown[p] = target; tick++ }
        }
    }
    BoxWithConstraints(modifier) {
        val sidePx = with(LocalDensity.current) { minOf(maxWidth, maxHeight).toPx() }
        val sideDp = with(LocalDensity.current) { sidePx.toDp() }
        Box(Modifier.size(sideDp).align(Alignment.Center)) {
            Canvas(Modifier.fillMaxSize().then(if (interactive) Modifier.pointerInput(s) {
                detectTapGestures { o ->
                    val w = size.width.toFloat()
                    val t = (0 until Deeds.N).firstOrNull { tileRect(it, w).contains(o) } ?: -1
                    s.selected = if (t == s.selected) -1 else t
                }
            } else Modifier)) {
                @Suppress("UNUSED_EXPRESSION") tick
                val w = size.width
                val e = w / 11f; val c = e * 1.5f
                // the realm in the middle
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF2E4A2B), Color(0xFF1C3320))), Offset(c, c), Size(w - 2 * c, w - 2 * c), CornerRadius(e * 0.2f))
                for (t in 0 until Deeds.N) drawTile(s, art, t, tileRect(t, w), e)
                // the current lord's tile glows
                if (g.phase != Deeds.SETUP && g.phase != Deeds.OVER) {
                    val r = tileRect(shown[g.actor], w)
                    drawRect(s.seatColor(g.actor), r.topLeft, r.size, style = Stroke(e * 0.09f))
                }
                if (s.selected >= 0) { val r = tileRect(s.selected, w); drawRect(Color(0xFFFFD54F), r.topLeft, r.size, style = Stroke(e * 0.12f)) }
                // the pieces
                if (g.phase != Deeds.SETUP || g.hero[0] >= 0) for (p in 0..1) {
                    if (g.hero[p] < 0) continue
                    val r = tileRect(shown[p], w)
                    val both = shown[0] == shown[1]
                    val off = if (!both) Offset.Zero else Offset((if (p == 0) -1 else 1) * e * 0.22f, (if (p == 0) 1 else -1) * e * 0.12f)
                    val ctr = r.center + off
                    drawOval(Color.Black.copy(alpha = 0.4f), Offset(ctr.x - e * 0.32f, ctr.y + e * 0.18f), Size(e * 0.64f, e * 0.2f))
                    drawCircle(s.seatColor(p).copy(alpha = 0.85f), e * 0.3f, ctr + Offset(0f, e * 0.27f), style = Stroke(e * 0.06f))
                    art(art.heroes[g.hero[p]], ctr - Offset(0f, e * 0.08f), e * (if (both) 0.8f else 0.95f), heroTint(g.color[p]))
                }
            }
            // the middle of the board: tile details, or the dice and the news
            val inner = sideDp / 11f * 1.5f
            Box(Modifier.offset(inner, inner).size(sideDp - inner * 2).padding(6.dp)) {
                if (s.selected >= 0) TileInfo(s, s.selected, Modifier.fillMaxSize()) else CentrePanel(s, Modifier.fillMaxSize())
            }
        }
    }
}

private fun DrawScope.drawTile(s: DeedsSession, art: DeedArt, t: Int, r: Rect, e: Float) {
    val g = s.g
    val kind = Deeds.KIND[t]
    drawRect(Brush.verticalGradient(listOf(PARCH_TOP, PARCH_BOT), r.top, r.bottom), r.topLeft, r.size)
    drawRect(INK.copy(alpha = 0.7f), r.topLeft, r.size, style = Stroke(1.2f))
    val sd = side(t)
    val corner = t % 9 == 0
    // the domain band on the board's inner edge
    val band = e * 0.26f
    val bandRect = when (sd) {
        0 -> Rect(r.left, r.top, r.right, r.top + band)
        1 -> Rect(r.right - band, r.top, r.right, r.bottom)
        2 -> Rect(r.left, r.bottom - band, r.right, r.bottom)
        else -> Rect(r.left, r.top, r.left + band, r.bottom)
    }
    val owner = g.owner[t]
    // art sits toward the outer edge, the coin by the band
    val outer = when (sd) { 0 -> Offset(0f, e * 0.12f); 1 -> Offset(-e * 0.12f, 0f); 2 -> Offset(0f, -e * 0.12f); else -> Offset(e * 0.12f, 0f) }
    when {
        corner -> art.special[kind]?.let { art(it, r.center, minOf(r.width, r.height) * 0.86f) }
        kind == Deeds.LAND -> {
            drawRect(DOMAIN_COLORS[Deeds.GROUP[t]], bandRect.topLeft, bandRect.size)
            art(art.domains[Deeds.GROUP[t]], r.center + outer, e * 0.78f)
        }
        kind == Deeds.BRIDGE -> {
            drawRect(Color(0xFF3F6E8C), bandRect.topLeft, bandRect.size)
            val ctr = r.center + outer
            drawRoundRect(Color(0xFF5B8FB0), Offset(ctr.x - e * 0.38f, ctr.y - e * 0.22f), Size(e * 0.76f, e * 0.44f), CornerRadius(e * 0.08f))
            val vertical = sd == 1 || sd == 3
            rotate(if (vertical) 90f else 0f, ctr) {
                drawImage(art.road, dstOffset = IntOffset((ctr.x - e * 0.5f).toInt(), (ctr.y - e * 0.1f).toInt()), dstSize = IntSize((e).toInt().coerceAtLeast(1), (e * 0.2f).toInt().coerceAtLeast(1)), filterQuality = FilterQuality.Medium)
            }
        }
        kind == Deeds.MILL -> {
            drawRect(Color(0xFF6D4C41), bandRect.topLeft, bandRect.size)
            art.special[if (t < 18) 100 else 101]?.let { art(it, r.center + outer, e * 0.8f) }
        }
        else -> art.special[kind]?.let { art(it, r.center, e * 0.72f) }
    }
    if (owner >= 0) {
        val bc = bandRect.center
        if (g.pawned[t] == 1) drawRect(Color.Black.copy(alpha = 0.35f), r.topLeft, r.size)
        // towers stand on the band, the coin beside them
        val n = g.towers[t]
        val coinAt = when (sd) { 0 -> Offset(r.center.x, r.top + band + e * 0.18f); 1 -> Offset(r.right - band - e * 0.18f, r.center.y); 2 -> Offset(r.center.x, r.bottom - band - e * 0.18f); else -> Offset(r.left + band + e * 0.18f, r.center.y) }
        drawRect(s.seatColor(owner), bandRect.topLeft, bandRect.size, style = Stroke(e * 0.07f))
        if (n == 4) art(art.castle, bc, e * 0.42f)
        else if (n > 0) {
            for (k in 0 until n) {
                val f = (k - (n - 1) / 2f) * e * 0.26f
                art(art.tower, bc + if (sd == 0 || sd == 2) Offset(f, 0f) else Offset(0f, f), e * 0.3f)
            }
        }
        deedCoin(coinAt, e * 0.17f, g.face(t), s.seatColor(owner), dim = g.pawned[t] == 1)
    }
}

// =====================================================================  the middle of the board

@Composable
private fun Dice(a: Int, b: Int, rollId: Int, size: Dp) {
    val shake = remember(rollId) { Animatable(0f) }
    LaunchedEffect(rollId) { if (rollId > 0) shake.animateTo(0f, keyframes { durationMillis = 480; 18f at 70; -14f at 160; 9f at 260; -4f at 360 }) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((i, v) in listOf(a, b).withIndex()) {
            Canvas(Modifier.size(size)) {
                rotate(shake.value * if (i == 0) 1 else -1) {
                    val r = this.size.minDimension
                    drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(r * 0.04f, r * 0.08f), Size(r * 0.96f, r * 0.96f), CornerRadius(r * 0.18f))
                    drawRoundRect(Brush.linearGradient(listOf(Color(0xFFFFF8E1), Color(0xFFE6CFA0))), Offset.Zero, Size(r * 0.96f, r * 0.96f), CornerRadius(r * 0.18f))
                    drawRoundRect(INK.copy(alpha = 0.6f), Offset.Zero, Size(r * 0.96f, r * 0.96f), CornerRadius(r * 0.18f), style = Stroke(r * 0.04f))
                    val pips = when (v) {
                        1 -> listOf(1 to 1); 2 -> listOf(0 to 0, 2 to 2); 3 -> listOf(0 to 0, 1 to 1, 2 to 2)
                        4 -> listOf(0 to 0, 2 to 0, 0 to 2, 2 to 2); 5 -> listOf(0 to 0, 2 to 0, 1 to 1, 0 to 2, 2 to 2)
                        else -> listOf(0 to 0, 2 to 0, 0 to 1, 2 to 1, 0 to 2, 2 to 2)
                    }
                    for ((x, y) in pips) drawCircle(Color(0xFF3A2A18), r * 0.09f, Offset(r * (0.2f + x * 0.28f), r * (0.2f + y * 0.28f)))
                }
            }
        }
    }
}

@Composable
private fun CentrePanel(s: DeedsSession, modifier: Modifier) {
    val g = s.g
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DEED or DUNGEON", fontFamily = SERIF, fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFFF6E3A1), letterSpacing = 1.sp)
            InkText("Round ${g.round} of ${g.maxRounds}" + if (g.purse > 0) " · Purse ${g.purse}" else "", 11, color = PARCH_TOP.copy(alpha = 0.85f))
            Spacer(Modifier.height(4.dp))
            Row(Modifier.parchment(RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                RIconOf(RI.MOON, 14.dp); Spacer(Modifier.width(4.dp))
                InkText(Deeds.OMEN[g.omen] + ": " + Deeds.OMEN_TEXT[g.omen], 10, bold = true, lines = 2)
            }
        }
        if (g.phase != Deeds.SETUP) Dice(g.d1, g.d2, g.rollId, 44.dp)
        InkText("Tap any tile to read it", 10, color = PARCH_TOP.copy(alpha = 0.7f))
    }
}

@Composable
private fun TileInfo(s: DeedsSession, t: Int, modifier: Modifier) {
    val g = s.g
    val kind = Deeds.KIND[t]
    Column(modifier.clip(RoundedCornerShape(10.dp)).parchment(RoundedCornerShape(10.dp)).clickable { s.selected = -1 }.verticalScroll(rememberScrollState()).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (Deeds.GROUP[t] >= 0) { Box(Modifier.size(12.dp).clip(CircleShape).background(DOMAIN_COLORS[Deeds.GROUP[t]])); Spacer(Modifier.width(5.dp)) }
            InkText(g.names[t], 15, bold = true, modifier = Modifier.weight(1f))
            InkText("✕", 13, color = INK_SOFT)
        }
        val lines = ArrayList<String>()
        when (kind) {
            Deeds.SPIRE -> lines += listOf("Pass it: +${g.stipend()} gold and heal 1, and you may turn your coins that turn.", "Land right on it for 100 gold more.")
            Deeds.DUNGEON -> lines += listOf("Just visiting, unless you're locked in.", "Locked in: roll doubles, pay the jailer ${Deeds.BRIBE}, or use a Skeleton Key. Each night inside heals 1. After three tries you must pay.")
            Deeds.ARENA -> lines += listOf("Land here to win the purse: ${g.purse} gold from taxes and fines.", "Then you may challenge your rival to trial by combat. Both roll two dice; the winner takes the loser's best deed and the loser takes 2 wounds.")
            Deeds.SEIZE -> lines += "The Inquisitor drags you straight to the Dungeon."
            Deeds.FATE -> lines += "Draw a Fate card: fortune, misfortune, and the occasional jester."
            Deeds.LOOT -> lines += "Open a chest: gold, keepsakes like potions and writs, or a mimic."
            Deeds.TITHE -> lines += "The Abbey takes a tenth of your gold for the Colosseum purse."
            Deeds.LEVY -> lines += "Pay 150 gold to the Colosseum purse."
            else -> {
                val d = Deeds.GROUP[t]
                if (d >= 0) lines += "Domain: ${Deeds.DOMAIN[d].replaceFirstChar { it.uppercase() }} (${g.domainTiles(d).size} lands)"
                lines += "Deed price: ${Deeds.PRICE[t]} gold"
                val o = g.owner[t]
                if (o < 0) lines += "Unclaimed."
                else {
                    lines += "Lord: ${g.Title(o)} (${s.who(o)})" + if (g.pawned[t] == 1) " · pawned" else ""
                    lines += "Coin: ${if (g.face(t) == Deeds.COIN_CROWN) "Crown (homage)" else "Blade (wounds)"}" + if (g.omen == 5 && g.coin[t] == Deeds.COIN_BLADE) " · Blade held by the truce" else ""
                    if (kind == Deeds.LAND && g.towers[t] > 0) lines += "Fortified: ${g.towerWord(g.towers[t])}"
                }
                when (kind) {
                    Deeds.LAND -> {
                        lines += "Homage: " + (0..4).joinToString(" · ") { k -> (if (k == 0) "bare " else if (k == 4) "castle " else "$k: ") + (Deeds.PRICE[t] * Deeds.RENT_PCT[k] / 100) } + " (bare doubles on a whole domain)"
                        lines += "Tower: ${g.towerCost(t, if (o >= 0) o else s.viewer)} gold each, up to 3, then a castle."
                    }
                    Deeds.BRIDGE -> lines += "Toll by bridges held: " + Deeds.BRIDGE_TOLL.joinToString(" · ")
                    Deeds.MILL -> lines += "Homage: dice × 4, or × 10 with both the mill and the mine."
                }
                if (o >= 0 && g.pawned[t] == 0) {
                    lines += "Now: homage ${g.homageOf(t, 7)}${if (kind == Deeds.MILL) " (on a 7)" else ""}, or refuse and take ${g.refuseDmg(t)}. Blade: ${g.bladeDmg(t)} wounds."
                }
            }
        }
        for (l in lines) { Spacer(Modifier.height(3.dp)); InkText(l, 11) }
    }
}

// =====================================================================  players

@Composable
private fun HeroImg(hero: Int, color: Int, size: Dp, modifier: Modifier = Modifier) {
    if (hero < 0) { Box(modifier.size(size)); return }
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(HERO_ART[hero]), null, modifier.size(size),
        contentScale = androidx.compose.ui.layout.ContentScale.Fit, colorFilter = heroTint(color))
}

@Composable
private fun PlayerCard(s: DeedsSession, p: Int, modifier: Modifier) {
    val g = s.g
    val active = g.actor == p && g.phase != Deeds.OVER
    val col = s.seatColor(p)
    Row(modifier.parchment(PANEL, if (active) col else GOLD, if (active) 2.5.dp else 1.dp).padding(horizontal = 5.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(col.copy(alpha = 0.18f)).border(1.5.dp, col, CircleShape), contentAlignment = Alignment.Center) {
            HeroImg(g.hero[p], g.color[p], 36.dp)
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            InkText(s.who(p) + if (g.jail[p] >= 0) " · in dungeon" else "", 12, bold = true, lines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFE0B23A)))
                Spacer(Modifier.width(2.dp))
                InkText("${g.gold[p]}", 12, bold = true)
                Spacer(Modifier.width(6.dp))
                InkText("♥ ${g.hp[p]}/${g.maxHp(p)}", 12, bold = true, color = if (g.hp[p] <= 3) Color(0xFFB71C1C) else Color(0xFF8E2424))
            }
            // health bar
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(INK_SOFT.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(g.hp[p].coerceAtLeast(0) / g.maxHp(p).toFloat()).height(4.dp).background(Color(0xFFC62828)))
            }
            val kept = g.items[p].sum()
            InkText("${g.deeds(p).size} deeds · ${g.domainsHeld(p)} dom." + if (kept > 0) " · $kept loot" else "", 10, color = INK_SOFT, lines = 1)
        }
    }
}


// =====================================================================  the screen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeedsUi(s: DeedsSession) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    var showLands by remember(s) { mutableStateOf(false) }
    if (g.phase == Deeds.SETUP && s.myMove) { ChampionPicker(s); return }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 52.dp).navigationBarsPadding().padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                PlayerCard(s, 0, Modifier.weight(1f))
                PlayerCard(s, 1, Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val board = minOf(maxWidth - 8.dp, maxHeight - 64.dp).coerceAtLeast(160.dp)
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    // table mode turns the words to face Player 2; the board stays put on the table
                    DeedsBoard(s, Modifier.size(board).tableUpright())
                    Spacer(Modifier.height(4.dp))
                    Chronicle(s, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            PromptRibbon((if (s.localTwoPlayer && !s.online) "Player ${g.actor + 1} · " else "") + s.prompt, s.thinking || s.heraldBusy)
            Spacer(Modifier.height(5.dp))
            Actions(s) { showLands = true }
        }
        HeraldCard(s, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 70.dp).padding(horizontal = 12.dp))
        if (s.myMove && g.phase == Deeds.DECIDE && g.pType == Deeds.D_CARD) CardDialog(s)
        if (showLands) LandsDialog(s) { showLands = false }
    }
}

/** The latest happenings, newest at the bottom. */
@Composable
private fun Chronicle(s: DeedsSession, modifier: Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(s.events.size) { scroll.animateScrollTo(scroll.maxValue) }
    Column(modifier.clip(PANEL).background(Color.Black.copy(alpha = 0.45f)).verticalScroll(scroll).padding(horizontal = 8.dp, vertical = 4.dp)) {
        if (s.events.isEmpty()) InkText("The realm awaits its new lord. Tap any tile to read it.", 12, color = PARCH_TOP)
        for ((i, line) in s.events.withIndex()) InkText("• $line", 12, color = if (i == s.events.lastIndex) Color.White else PARCH_TOP.copy(alpha = 0.75f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(s: DeedsSession, openLands: () -> Unit) {
    val g = s.g
    val p = g.actor
    val mine = s.myMove
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 44.dp).alpha(if (mine) 1f else 0.55f),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (!mine) return@FlowRow
        val t = g.pos[p]
        when (g.phase) {
            Deeds.ROLL -> {
                if (g.jail[p] >= 0) {
                    WoodButton("Roll for doubles", RI.DICE) { s.act(Act.ROLL) }
                    if (g.gold[p] >= Deeds.BRIBE) WoodButton("Bribe ${Deeds.BRIBE}", RI.TRADE) { s.act(Act.BRIBE) }
                    if (g.items[p][Deeds.KEY] > 0) WoodButton("Use key", RI.RELIC) { s.act(Act.KEY) }
                } else WoodButton(if (g.again) "Roll again" else "Roll", RI.DICE) { s.act(Act.ROLL) }
                WoodButton("Lands", RI.SCROLL, onClick = openLands)
            }
            Deeds.DECIDE -> when (g.pType) {
                Deeds.D_BUY -> {
                    WoodButton("Buy · ${Deeds.PRICE[t]}", RI.SCROLL, enabled = g.gold[p] >= Deeds.PRICE[t]) { s.act(Act.BUY) }
                    WoodButton("Leave it", null) { s.act(Act.PASS) }
                    if (g.gold[p] < Deeds.PRICE[t]) WoodButton("Raise gold", RI.TRADE, onClick = openLands)
                }
                Deeds.D_HOMAGE -> {
                    WoodButton("Pay ${g.pAmount}", RI.CROWN, enabled = g.gold[p] >= g.pAmount) { s.act(Act.PAY) }
                    WoodButton("Refuse · ${g.pDmg} ♥", RI.FIRE) { s.act(Act.REFUSE) }
                    if (g.items[p][Deeds.WRIT] > 0) WoodButton("Show writ", RI.SCROLL) { s.act(Act.WRIT) }
                    if (g.gold[p] < g.pAmount) WoodButton("Raise gold", RI.TRADE, onClick = openLands)
                }
                Deeds.D_BLADE -> {
                    WoodButton("Take ${g.pDmg} ♥" + if (g.items[p][Deeds.SHIELD] > 0) " (shield blocks)" else "", RI.SHIELD) { s.act(Act.BLADE) }
                    if (g.items[p][Deeds.WRIT] > 0) WoodButton("Show writ", RI.SCROLL) { s.act(Act.WRIT) }
                }
                Deeds.D_DEBT -> {
                    val w = g.shortfallWounds(p, g.pAmount)
                    InkText("${g.pWhat.replaceFirstChar { it.uppercase() }}: ${g.pAmount} gold, you hold ${g.gold[p]}.", 12, color = Color.White, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    WoodButton(if (w > 0) "Pay ${g.gold[p]} + $w ♥" else "Pay ${g.pAmount}", RI.FIRE) { s.act(Act.DEBT) }
                    WoodButton("Raise gold", RI.TRADE, onClick = openLands)
                }
                Deeds.D_ARENA -> {
                    WoodButton("Trial by combat", RI.KNIGHT) { s.act(Act.CHALLENGE) }
                    WoodButton("Leave", null) { s.act(Act.DECLINE) }
                }
                else -> WoodButton("Read the card", RI.SCROLL) { s.act(Act.ACK) }
            }
            Deeds.AFTER -> {
                WoodButton(if (g.again && g.jail[p] < 0) "Roll again" else "End turn", if (g.again && g.jail[p] < 0) RI.DICE else RI.END) { s.act(Act.END) }
                WoodButton(if (g.spireCharge) "Lands & coins" else "Lands", RI.SCROLL, onClick = openLands)
            }
        }
        if (g.canPotion()) WoodButton("Potion", RI.MOON) { s.act(Act.POTION) }
        if (g.canSiege()) WoodButton("Siege", RI.FIRE) { s.act(Act.SIEGE) }
    }
}

@Composable
private fun CardDialog(s: DeedsSession) {
    val g = s.g
    val loot = g.pCard >= 100
    androidx.compose.ui.window.Dialog(onDismissRequest = {}, properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).parchment(RoundedCornerShape(16.dp), GOLD, 3.dp).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RIconOf(if (loot) RI.RELIC else RI.SCROLL, 54.dp)
            Spacer(Modifier.height(6.dp))
            InkText(if (loot) "Loot" else "Fate", 22, bold = true)
            Spacer(Modifier.height(8.dp))
            InkText(g.lastText, 16, align = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            WoodButton(if (loot) "Take it" else "So be it", null) { s.act(Act.ACK) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LandsDialog(s: DeedsSession, close: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val p = if (s.myMove) g.actor else s.viewer
    val canAct = s.myMove && p == g.actor
    AlertDialog(
        onDismissRequest = close,
        containerColor = PARCH_TOP,
        title = { InkText("${s.who(p)}: lands and keepsakes", 18, bold = true) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InkText("Gold ${g.gold[p]} · ♥ ${g.hp[p]}/${g.maxHp(p)} · renown ${g.renown(p)}", 12, color = INK_SOFT)
                if (g.spireCharge && canAct) InkText("You passed the Spire: tap a coin to turn it.", 12, bold = true, color = Color(0xFF8D5A12))
                else if (g.items[p][Deeds.BANNER] > 0 && canAct) InkText("A Royal Banner lets you turn one coin.", 12, bold = true, color = Color(0xFF8D5A12))
                else InkText("Coins turn only on a turn you pass the Spire.", 11, color = INK_SOFT)
                Spacer(Modifier.height(6.dp))
                val items = (0 until 6).filter { g.items[p][it] > 0 }
                for (i in items) InkText("${Deeds.ITEM_NAME[i]}${if (g.items[p][i] > 1) " ×${g.items[p][i]}" else ""}: ${Deeds.ITEM_TEXT[i]}", 11)
                if (items.isNotEmpty()) Spacer(Modifier.height(6.dp))
                val mine = g.deeds(p).sortedBy { it }
                if (mine.isEmpty()) InkText("No deeds yet. Land on an unclaimed tile to buy one.", 13)
                for (t in mine) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.45f)).padding(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val d = Deeds.GROUP[t]
                            Box(Modifier.size(10.dp).clip(CircleShape).background(if (d >= 0) DOMAIN_COLORS[d] else Color(0xFF5B8FB0)))
                            Spacer(Modifier.width(5.dp))
                            Column(Modifier.weight(1f)) {
                                InkText(g.names[t], 13, bold = true, lines = 1)
                                InkText((if (g.fullDomain(t)) "Whole domain · " else "") + (if (g.towers[t] > 0) g.towerWord(g.towers[t]) + " · " else "") + (if (g.pawned[t] == 1) "pawned" else "homage ${g.homageOf(t, 7)} · blade ${g.bladeDmg(t)}"), 10, color = INK_SOFT, lines = 1)
                            }
                            Canvas(Modifier.size(30.dp).clip(CircleShape).clickable(enabled = canAct && g.canFlip(t)) { s.act(Act.FLIP, t) }) {
                                deedCoin(center, size.minDimension * 0.42f, g.coin[t], s.seatColor(p), dim = canAct && !g.canFlip(t) && false)
                            }
                        }
                        if (canAct) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (Deeds.KIND[t] == Deeds.LAND && g.fullDomain(t) && g.towers[t] < 4) SmallBtn(if (g.towers[t] == 3) "Castle ${g.towerCost(t, p)}" else "Tower ${g.towerCost(t, p)}", g.canBuild(t)) { s.act(Act.BUILD, t) }
                            if (g.towers[t] > 0) SmallBtn("Tear down +${Deeds.TOWER_COST[Deeds.GROUP[t]] / 2}", g.canSell(t)) { s.act(Act.SELL, t) }
                            if (g.pawned[t] == 0) SmallBtn("Pawn +${Deeds.PRICE[t] / 2}", g.canPawn(t)) { s.act(Act.PAWN, t) }
                            else SmallBtn("Redeem ${g.redeemCost(t)}", g.canRedeem(t)) { s.act(Act.REDEEM, t) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Close", color = INK, fontFamily = SERIF, fontWeight = FontWeight.Bold) } },
    )
}

@Composable
private fun SmallBtn(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(50)).wood(RoundedCornerShape(50)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = PARCH_TOP, fontFamily = SERIF, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

// =====================================================================  choosing a champion

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChampionPicker(s: DeedsSession) {
    val g = s.g
    val p = g.actor
    val taken = g.color[1 - p]
    var hero by remember(s, p) { mutableIntStateOf(p % 4) }
    var col by remember(s, p) { mutableIntStateOf((if (p == 0) 0 else 2).let { if (it == taken) (it + 1) % Deeds.COLOR.size else it }) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(top = 52.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().parchment(PANEL).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            InkText(if (s.localTwoPlayer) "${s.who(p)}, choose your champion" else "Choose your champion", 20, bold = true, align = TextAlign.Center)
            InkText("Win deeds, fell your rival and be crowned King of Eiseria.", 12, color = INK_SOFT, align = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(150.dp).clip(CircleShape).background(DEED_COLORS[col].copy(alpha = 0.18f)).border(3.dp, DEED_COLORS[col], CircleShape), contentAlignment = Alignment.Center) {
                HeroImg(hero, col, 130.dp)
            }
            Spacer(Modifier.height(6.dp))
            InkText((if (col > 0) Deeds.COLOR[col] + " " else "") + Deeds.HERO[hero], 18, bold = true)
            InkText(Deeds.HERO_POWER[hero], 13, align = TextAlign.Center)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (h in 0..3) {
                Column(
                    Modifier.weight(1f).clip(PANEL).parchment(PANEL, if (h == hero) DEED_COLORS[col] else GOLD, if (h == hero) 3.dp else 1.dp).clickable { hero = h }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HeroImg(h, col, 58.dp)
                    InkText(Deeds.HERO[h], 11, bold = true, align = TextAlign.Center, lines = 2)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        InkText("Banner colour", 14, bold = true, color = Color.White)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in Deeds.COLOR.indices) {
                val free = c != taken
                Column(Modifier.alpha(if (free) 1f else 0.3f).clickable(enabled = free) { col = c }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(DEED_COLORS[c]).border(if (c == col) 3.dp else 1.dp, if (c == col) Color.White else INK, CircleShape))
                    InkText(Deeds.COLOR[c], 10, color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        WoodButton("Ride out", RI.CROWN) { s.act(Act.CHOOSE, hero * 10 + col) }
    }
}

// =====================================================================  how to play

private fun deedsDemo(): DeedsSession {
    val g = Deeds.newGame(1, Random(2026))
    g.choose(Deeds.KNIGHT, 1); g.choose(Deeds.DRAGON, 2)
    for (t in listOf(1, 3, 5, 7, 8, 14)) g.owner[t] = 0
    for (t in listOf(19, 21, 22, 25, 31)) g.owner[t] = 1
    g.towers[5] = 1; g.towers[7] = 1; g.towers[8] = 2
    g.towers[19] = 2; g.towers[21] = 2; g.towers[22] = 2
    g.coin[19] = Deeds.COIN_BLADE; g.coin[21] = Deeds.COIN_BLADE; g.coin[3] = Deeds.COIN_BLADE
    g.pos[0] = 6; g.pos[1] = 20; g.d1 = 4; g.d2 = 2; g.rollId = 1
    g.log.clear()
    return DeedsSession(Difficulty.MEDIUM, g)
}

private fun deedsStep(title: String, body: String, select: Int) = TutorialStep(title, body) { _ ->
    val s = remember { deedsDemo().also { it.selected = select } }
    DeedsBoard(s, Modifier.fillMaxWidth().height(320.dp), interactive = false)
}

private fun deedsTutorial(): List<TutorialStep> = listOf(
    deedsStep("Roll and ride", "Choose a champion, then roll two dice and ride around Eiseria. Doubles roll again; three doubles in a row land you in the Dungeon.", -1),
    deedsStep("Buy deeds", "Stop on unclaimed land to buy its deed. Lands come in coloured domains: hold a whole domain to double its homage and raise towers, then a castle.", 8),
    deedsStep("The two-sided coin", "Every deed has a coin, tossed when you buy it. Crown: visitors pay homage in gold, or refuse and your towers strike them. Blade: visitors simply take wounds. You turn your coins only on a turn you pass the Spire.", 19),
    deedsStep("Felled!", "Lose all your health and you're felled: the lord who felled you seizes your best deed, and you wake healed in the Dungeon. Felled with no deeds left, you're vanquished.", 21),
    deedsStep("Fate, loot and the Colosseum", "Fate cards bring fortune and folly. Loot brings gold or keepsakes: potions, keys, shields, writs, siege engines and banners. Taxes fill the Colosseum purse; land there to win it and dare a trial by combat for a deed.", 18),
    deedsStep("Become King", "Hold four whole domains to be crowned at once. Otherwise the old King dies at the last round, and the lord with the most renown (gold, deeds and towers) takes the throne. Omens change every few rounds.", 0),
)
