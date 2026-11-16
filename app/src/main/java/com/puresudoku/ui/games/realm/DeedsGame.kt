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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.drawscope.clipRect
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
import com.puresudoku.ui.games.board.glyph
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
private val SEAT_COLORS = listOf(Color(0xFF2E7BD6), Color(0xFFD93A3F), Color(0xFF2E9E57), Color(0xFF8E54C9))
private val HERO_ART = intArrayOf(com.puresudoku.R.drawable.dd_piece_dragon, com.puresudoku.R.drawable.dd_piece_knight, com.puresudoku.R.drawable.dd_piece_thief, com.puresudoku.R.drawable.dd_piece_black_knight)
/** The light grey parts of each piece (cloak, banner, trim) that take the player's colour. */
private val HERO_MASK = intArrayOf(com.puresudoku.R.drawable.dd_piece_dragon_mask, com.puresudoku.R.drawable.dd_piece_knight_mask, com.puresudoku.R.drawable.dd_piece_thief_mask, com.puresudoku.R.drawable.dd_piece_black_knight_mask)
private val HERO_PORTRAIT = intArrayOf(com.puresudoku.R.drawable.dd_portrait_dragon, com.puresudoku.R.drawable.dd_portrait_knight, com.puresudoku.R.drawable.dd_portrait_thief, com.puresudoku.R.drawable.dd_portrait_black_knight)
/** A hero piece tinted in its colour (Natural keeps the painted colours). */
internal fun heroTint(c: Int): ColorFilter? = if (c <= 0) null else ColorFilter.tint(lerp(Color.White, DEED_COLORS[c], 0.85f), BlendMode.Modulate)

private class DeedArt(val heroes: List<ImageBitmap>, val masks: List<ImageBitmap>, val icons: Map<Int, ImageBitmap>,
    /** Each tile's background painting (one per colour set). */
    val land: List<ImageBitmap>)

/** One background painting per colour set, and one for each kind of special tile. */
private val DOMAIN_LAND = intArrayOf(
    com.puresudoku.R.drawable.rt_ag_swamp, com.puresudoku.R.drawable.rt_grain_1, com.puresudoku.R.drawable.rt_timber_1, com.puresudoku.R.drawable.rt_beasts_5,
    com.puresudoku.R.drawable.rt_ag_coast, com.puresudoku.R.drawable.rt_ore_1, com.puresudoku.R.drawable.rt_ag_hills, com.puresudoku.R.drawable.rt_mana_2,
)

private fun landFor(t: Int): Int {
    val d = Deeds.GROUP[t]
    if (d >= 0) return DOMAIN_LAND[d]
    return when (Deeds.KIND[t]) {
        Deeds.SPIRE -> com.puresudoku.R.drawable.rt_ag_plains
        Deeds.ARENA -> com.puresudoku.R.drawable.rt_ag_desert
        Deeds.SEIZE -> com.puresudoku.R.drawable.rt_af_embers
        Deeds.MARKET -> com.puresudoku.R.drawable.rt_grain_2
        Deeds.BRIDGE -> com.puresudoku.R.drawable.rt_beasts_4
        Deeds.MILL -> com.puresudoku.R.drawable.rt_beasts_2
        Deeds.FATE -> com.puresudoku.R.drawable.rt_mana_1
        Deeds.LOOT -> com.puresudoku.R.drawable.rt_waste_1
        Deeds.TITHE -> com.puresudoku.R.drawable.rt_beasts_3
        else -> com.puresudoku.R.drawable.rt_timber_4
    }
}

/**
 * Fills [r] with the painting inside a hexagonal landscape tile, zoomed so the tile is covered edge to edge:
 * the largest rectangle of the tile's shape that fits inside the hexagon's gold frame.
 */
private fun DrawScope.landscape(img: ImageBitmap, r: Rect, alpha: Float) {
    val w = img.width.toFloat(); val h = img.height.toFloat()
    val inR = w / 2f * 0.9f
    val outR = inR * 2f / kotlin.math.sqrt(3f)
    val k = r.height / r.width
    val a = minOf(inR, outR / (k + 1f / kotlin.math.sqrt(3f)))
    val b = a * k
    val cx = w / 2f; val cy = h / 2f
    drawImage(img, srcOffset = IntOffset((cx - a).toInt(), (cy - b).toInt()), srcSize = IntSize((2 * a).toInt().coerceAtLeast(1), (2 * b).toInt().coerceAtLeast(1)),
        dstOffset = IntOffset(r.left.toInt(), r.top.toInt()), dstSize = IntSize(kotlin.math.ceil(r.width).toInt().coerceAtLeast(1), kotlin.math.ceil(r.height).toInt().coerceAtLeast(1)),
        alpha = alpha, filterQuality = FilterQuality.Medium)
}

@Composable
private fun rememberDeedArt(): DeedArt {
    @Composable fun img(id: Int) = ImageBitmap.imageResource(id)
    val heroes = HERO_ART.map { img(it) }
    val masks = HERO_MASK.map { img(it) }
    // the special tiles keep the Eiseria paintings
    val icons = mapOf(
        Deeds.SPIRE to img(com.puresudoku.R.drawable.rt_wonder_sky_temple),
        Deeds.ARENA to img(com.puresudoku.R.drawable.rt_ag_colosseum),
        Deeds.SEIZE to img(com.puresudoku.R.drawable.dd_inquisitor),
        Deeds.BRIDGE to img(com.puresudoku.R.drawable.dd_bridge),
        200 to img(com.puresudoku.R.drawable.dd_dungeon),
        // full paintings for two corners
        300 to img(com.puresudoku.R.drawable.dd_corner_spire),
        318 to img(com.puresudoku.R.drawable.dd_corner_colosseum),
        Deeds.MARKET to img(com.puresudoku.R.drawable.rt_ag_market),
        Deeds.FATE to img(com.puresudoku.R.drawable.rt_ui_scroll),
        Deeds.LOOT to img(com.puresudoku.R.drawable.rt_ui_relic),
        Deeds.TITHE to img(com.puresudoku.R.drawable.rt_ag_temple),
        Deeds.LEVY to img(com.puresudoku.R.drawable.rt_ui_crown),
        100 to img(com.puresudoku.R.drawable.rt_ag_mill),
        101 to img(com.puresudoku.R.drawable.rt_ag_mine),
    )
    val ids = remember { List(Deeds.N) { landFor(it) } }
    val lands = ids.distinct().associateWith { img(it) }
    return remember(heroes, masks, lands) { DeedArt(heroes, masks, icons, ids.map { lands.getValue(it) }) }
}

// =====================================================================  session

class DeedsSession(difficulty: Difficulty, val g: Deeds) : DuelSession(GameKind.DEED_DUNGEON, difficulty) {
    var events by mutableStateOf<List<String>>(emptyList()); private set
    /** The tile whose details show in the middle of the board, or -1. */
    var selected by mutableIntStateOf(-1)

    fun human(p: Int) = g.ai[p] == 0
    /** Seats for the table-mode turn and online play: only people take turns at the screen. */
    override val tableSeat: Int? get() = if (localTwoPlayer && g.phase != Deeds.LOBBY && human(g.decider)) g.decider + 1 else null
    override fun localSide(actor: Int): Pair<String, Color> = who(actor - 1) to seatColor(actor - 1)
    fun seatColor(p: Int): Color = g.color[p].let { if (it in DEED_COLORS.indices) DEED_COLORS[it] else SEAT_COLORS[p % 4] }

    /** Whose lands and items the screen shows: your own seat online, the person to move otherwise. */
    val viewer: Int get() = when {
        online -> onlineSeat - 1
        human(g.decider) -> g.decider
        human(g.actor) -> g.actor
        else -> 0
    }
    fun who(p: Int): String = when {
        online -> if (p == onlineSeat - 1) "You" else "Opponent"
        !localTwoPlayer -> if (p == 0) "You" else if (g.n == 2) "Rival" else "Rival $p"
        human(p) -> "Player ${p + 1}"
        else -> "AI lord ${p + 1}"
    }

    override val middleStat get() = MiddleStat.Counter("Round", "${g.round}/${g.maxRounds}")
    override val progress: Float get() = (g.round.toFloat() / g.maxRounds).coerceAtMost(0.95f)
    override val wonTitle get() = if (localTwoPlayer) "${who(0)} is crowned!" else "You are King of Eiseria!"
    override val lostTitle get() = if (g.winner >= 0 && (localTwoPlayer || g.n > 2)) "${who(g.winner)} is crowned!" else "The rival takes the throne"
    override val drawTitle get() = "Two lords, one throne"
    override val lostMessage get() = when (g.endReason) {
        1 -> "Vanquished, with no lands left. "
        2 -> "${g.domainsToWin} whole domains won the crown. "
        3 -> "The old King died and another lord had more renown. "
        else -> ""
    } + "This game's save slot has been cleared."

    val myMove: Boolean get() = outcome == Outcome.PLAYING && !thinking && !heraldBusy && g.phase != Deeds.OVER && human(g.decider) && myOnlineTurn

    private fun flush() {
        if (g.log.isNotEmpty()) { events = (events + g.log).takeLast(40); g.log.clear() }
    }

    private val KEY_MOMENTS = listOf("coin lands", "felled", "Trial by combat", "wins and takes", "crowned", "omen", "now holds all", "Shield takes",
        "Siege Engine", "is dead", "wins the auction", "accepts", "turns the offer down", "Inferno", "Second Wind", "The Heist", "Reign of Terror", "vanquished")

    fun act(type: Int, arg: Int = -1, offer: Deeds.Offer? = null) {
        if (!myMove) { if (thinking || heraldBusy) nudge("Wait for the other lords to finish"); return }
        val who = g.actor
        if (!g.perform(Act(type, arg, offer), rng)) { nudge(refusal(type)); return }
        // "Roll again" after doubles is one tap
        if (type == Act.END && g.phase == Deeds.ROLL && g.actor == who) g.perform(Act(Act.ROLL), rng)
        if (type == Act.ROLL || type == Act.END || type == Act.CHOOSE) selected = -1
        coachTip = null
        moves++
        val lines = g.log.toList()
        flush(); bump(); changed()
        for (line in lines) if (KEY_MOMENTS.any { it in line }) announce(line, "", 1500, true, iconFor(line))
        if (g.phase == Deeds.OVER) { whenQuiet { finishGame() }; return }
        whenQuiet { aiTurn() }
    }

    private fun refusal(type: Int) = when (type) {
        Act.BUY -> "Not enough gold. Pawn a land or tear down a tower first"
        Act.PAY -> "Not enough gold. Raise funds, or refuse and take the wounds"
        Act.BUILD -> "Build evenly, on a whole domain with nothing pawned, and have the gold"
        Act.FLIP -> "Coins turn only after passing the Spire, or with a Royal Banner"
        Act.PAWN -> "Tear down the domain's towers before pawning"
        Act.BID -> "You haven't the gold for that bid"
        Act.PROPOSE -> "That offer isn't possible: lands with towers can't be traded"
        Act.SHOP -> "Not enough gold"
        else -> "You can't do that now"
    }

    // ---------------------------------------------------------------- the AI lords

    private var aiSteps = 0

    private fun aiTurn() {
        if (outcome != Outcome.PLAYING || g.phase == Deeds.OVER || human(g.decider)) { aiSteps = 0; return }
        if (++aiSteps > 400) { aiSteps = 0; return }
        val snap = g.copy(); val seed = rng.nextLong()
        val pause = when (g.phase) { Deeds.ROLL -> 600L; Deeds.DECIDE, Deeds.TRADE -> 700L; Deeds.AUCTION -> 420L; else -> 380L }
        computer(minMs = pause, think = { DeedsAi.next(snap, Random(seed)) }) { a ->
            var done = g.perform(a, rng)
            if (!done) for (t in listOf(Act.ACK, Act.DEBT, Act.BLADE, Act.REFUSE, Act.PASS, Act.DECLINE, Act.LEAVE, Act.FOLD, Act.REJECT, Act.ROLL, Act.END)) { if (g.perform(Act(t), rng)) { done = true; break } }
            val lines = g.log.toList()
            flush(); bump(); changed()
            for (line in lines) announce(line, "", if ("bids" in line || "drops out" in line) 650L else if (lines.size > 2) 1200L else 1500L, false, iconFor(line))
            if (g.phase == Deeds.OVER) { whenQuiet { finishGame() }; return@computer }
            whenQuiet { aiTurn() }
        }
    }

    internal fun iconFor(line: String): RI = when {
        "felled" in line || "falls" in line || "vanquished" in line -> RI.DAGGER
        "Inferno" in line -> RI.FIRE
        "Dungeon" in line || "Inquisitor" in line -> RI.BLACK_KNIGHT
        "Trial" in line || "arena" in line || "Colosseum" in line -> RI.KNIGHT
        "Blade" in line || "strike" in line -> RI.DAGGER
        "homage" in line -> RI.CROWN
        "auction" in line || "bids" in line || "bidding" in line -> RI.TRADE
        "trade" in line || "offer" in line || "accepts" in line -> RI.TRADE
        "Market" in line || "buys a" in line -> RI.RELIC
        "buys" in line || "claims" in line || "seizes" in line || "takes" in line || "Heist" in line -> RI.SCROLL
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
                (if (g.endReason != 3) listOf(BonusLine("Crowned early", rules.basePoints * 10)) else emptyList()) +
                (if (g.n > 2) listOf(BonusLine("Lords beaten", (g.n - 1) * rules.basePoints * 3)) else emptyList()))
            -2 -> draw()
            else -> lose()
        }
    }

    val prompt: String get() {
        val d = g.decider
        return when {
            outcome != Outcome.PLAYING -> ""
            heraldBusy -> "Tap a card to hurry things along"
            thinking -> "${who(d)} is deciding…"
            online && !myOnlineTurn -> if (g.phase == Deeds.SETUP) "Your opponent is choosing a champion" else "Your opponent's turn"
            g.phase == Deeds.LOBBY -> "Seat the lords"
            g.phase == Deeds.SETUP -> if (human(d)) "Choose your champion" else "${who(d)} chooses a champion…"
            !human(d) -> "${who(d)}'s turn"
            g.phase == Deeds.AUCTION -> "Auction for ${g.names[g.aTile]}: " + (if (g.aHigh >= 0) "${g.aBid} gold, held by ${who(g.aHigh)}" else "no bids yet")
            g.phase == Deeds.TRADE -> "${who(d)}: answer the offer"
            g.phase == Deeds.ROLL -> if (g.jail[g.actor] >= 0) "In the Dungeon: roll doubles, pay the jailer or use a key" else if (g.again) "Doubles! Roll again" else "Roll the dice"
            g.phase == Deeds.DECIDE -> when (g.pType) {
                Deeds.D_BUY -> "Buy ${g.names[g.pos[g.actor]]}, or send it to auction"
                Deeds.D_HOMAGE -> "${g.Title(g.owner[g.pos[g.actor]])} demands homage"
                Deeds.D_BLADE -> "The Blade! Take the wounds or show a writ"
                Deeds.D_DEBT -> "You owe more gold than you hold"
                Deeds.D_ARENA -> "Challenge a lord to trial by combat?"
                Deeds.D_MARKET -> "The Royal Market: buy one keepsake, or move on"
                else -> "Read the card"
            }
            g.spireCharge -> "You passed the Spire: turn your coins in Lands, then end your turn"
            else -> if (g.again && g.jail[g.actor] < 0) "Build, trade, then roll again" else "Build or trade, then end your turn"
        }
    }

    /** Tells the coach's choice in words. */
    fun describe(a: Act): String = when (a.type) {
        Act.ROLL -> "Roll the dice."
        Act.BUY -> "Buy this deed."
        Act.PASS -> "Let it go to auction: keep your gold."
        Act.PAY -> "Pay the homage: the wounds would cost you more."
        Act.REFUSE -> "Refuse homage and take the wounds."
        Act.BLADE -> "Take the wounds."
        Act.WRIT -> "Show your Forged Writ."
        Act.DEBT -> "Pay what you can."
        Act.CHALLENGE -> "Challenge ${who(a.arg)}: you have more to gain than to lose."
        Act.DECLINE -> "Leave the arena: the stakes are against you."
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
        Act.BID -> "Bid: it's worth more to you than this."
        Act.FOLD -> "Drop out: the price is past its worth."
        Act.PROPOSE -> "Offer a trade: ${a.offer?.let { g.offerText(it) } ?: ""}."
        Act.ACCEPT -> "Accept the trade."
        Act.REJECT -> "Turn the trade down."
        Act.SHOP -> "Buy a ${Deeds.ITEM_NAME[a.arg]}."
        Act.LEAVE -> "Save your gold."
        Act.DEED -> "Use ${Deeds.DEED_NAME[g.hero[g.actor].coerceAtLeast(0)]} now" + (if (a.arg >= 0) " on ${g.names[a.arg]}." else ".")
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
                // saves from before seats existed: the solo rival was always the AI
                if (!st.getJSONObject("g").has("n") && !s.localTwoPlayer) g.ai[1] = 1
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

    override fun generate(d: Difficulty): Session = DeedsSession(d, Deeds.newGame(d.ordinal, Random(Random.nextLong())))
    override fun restore(o: JSONObject): Session = DeedsSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Buy deeds, set each coin to Homage or Blade at the Spire, and fell your rivals to seize their lands. Become King of Eiseria."
    override fun background(session: Session) = com.puresudoku.R.drawable.bg_realm_ocean
    override fun levelInfo(d: Difficulty) = "2 to 4 lords · ${Deeds.ROUNDS[d.ordinal]} rounds · whole domains crown you at once"
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Careless rivals who buy on a whim."
        Difficulty.MEDIUM -> "Rivals who buy sensibly and start building towers."
        Difficulty.HARD -> "Rivals who block your domains and bleed you when you're weak."
        Difficulty.EXPERT -> "Rivals who turn their Blades the moment you falter."
        Difficulty.MASTER -> "Shrewd lords with a long reign to plan."
        Difficulty.EXTREME -> "Ruthless, patient, and very hard to topple."
    }
    @Composable override fun Play(session: Session) = DeedsUi(session as DeedsSession)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)
    override val tutorial: List<TutorialStep> get() = deedsTutorial()
}

// =====================================================================  board geometry

/**
 * The rectangle of tile [t] on a board [w] wide: 9 tiles a side, corners half as big again. The Spire is top left and
 * the ride runs clockwise: along the top, down the right, back along the bottom and up the left.
 */
internal fun tileRect(t: Int, w: Float): Rect {
    val e = w / 11f; val c = e * 1.5f
    return when (t) {
        0 -> Rect(0f, 0f, c, c)
        in 1..8 -> Rect(c + (t - 1) * e, 0f, c + t * e, c)
        9 -> Rect(w - c, 0f, w, c)
        in 10..17 -> { val k = t - 9; Rect(w - c, c + (k - 1) * e, w, c + k * e) }
        18 -> Rect(w - c, w - c, w, w)
        in 19..26 -> { val k = t - 18; Rect(w - c - k * e, w - c, w - c - (k - 1) * e, w) }
        27 -> Rect(0f, w - c, c, w)
        else -> { val k = t - 27; Rect(0f, w - c - k * e, c, w - c - (k - 1) * e) }
    }
}

/** Where a tile's inner edge (toward the middle of the board) is: 0 top edge of the tile, 1 right, 2 bottom, 3 left. */
private fun innerEdge(t: Int) = when (t) { in 0..8 -> 2; in 9..17 -> 3; in 18..26 -> 0; else -> 1 }

private fun DrawScope.art(img: ImageBitmap, centre: Offset, box: Float, filter: ColorFilter? = null, alpha: Float = 1f) {
    val sc = box / maxOf(img.width, img.height)
    val w = (img.width * sc).toInt().coerceAtLeast(1); val h = (img.height * sc).toInt().coerceAtLeast(1)
    drawImage(img, dstOffset = IntOffset((centre.x - w / 2f).toInt(), (centre.y - h / 2f).toInt()), dstSize = IntSize(w, h), colorFilter = filter, alpha = alpha, filterQuality = FilterQuality.Medium)
}

/** A soft white radiance behind a piece or an icon so it stands out from the painting. */
private fun DrawScope.radiance(c: Offset, r: Float, strength: Float = 1f) {
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f * strength), Color.White.copy(alpha = 0.55f * strength), Color.White.copy(alpha = 0f)), c, r), r, c)
}

/** The two-sided coin, drawn flat: a crown on gold, or a sword on red, ringed in white and the owner's colour. */
private fun DrawScope.deedCoin(c: Offset, r: Float, face: Int, owner: Color, dim: Boolean = false) {
    val a = if (dim) 0.45f else 1f
    drawCircle(Color.Black.copy(alpha = 0.3f * a), r * 1.14f, c + Offset(0f, r * 0.12f))
    drawCircle(Color.White.copy(alpha = a), r * 1.12f, c)
    drawCircle(owner.copy(alpha = a), r, c)
    val inner = r * 0.74f
    drawCircle((if (face == Deeds.COIN_CROWN) Color(0xFFF2C14E) else Color(0xFFC62828)).copy(alpha = a), inner, c)
    drawCircle(INK.copy(alpha = 0.55f * a), inner, c, style = Stroke(r * 0.08f))
    if (face == Deeds.COIN_CROWN) {
        val u = inner * 0.62f
        val p = Path().apply {
            moveTo(c.x - u, c.y + u * 0.55f); lineTo(c.x - u, c.y - u * 0.35f); lineTo(c.x - u * 0.5f, c.y + u * 0.05f); lineTo(c.x, c.y - u * 0.65f)
            lineTo(c.x + u * 0.5f, c.y + u * 0.05f); lineTo(c.x + u, c.y - u * 0.35f); lineTo(c.x + u, c.y + u * 0.55f); close()
        }
        drawPath(p, Color(0xFF7A4A0C).copy(alpha = a))
    } else {
        val u = inner * 0.72f
        rotate(-45f, c) {
            drawLine(Color.White.copy(alpha = a), c - Offset(0f, u), c + Offset(0f, u * 0.5f), u * 0.26f, StrokeCap.Round)
            drawLine(Color(0xFFFFD54F).copy(alpha = a), c + Offset(-u * 0.45f, u * 0.42f), c + Offset(u * 0.45f, u * 0.42f), u * 0.22f, StrokeCap.Round)
            drawLine(Color(0xFFFFD54F).copy(alpha = a), c + Offset(0f, u * 0.5f), c + Offset(0f, u * 0.85f), u * 0.2f, StrokeCap.Round)
        }
    }
}

/** A plain tower in the owner's colour: [b] is the middle of its foot, [h] its height. */
private fun DrawScope.flatTower(b: Offset, h: Float, color: Color) {
    val w = h * 0.5f
    val outline = Color(0xFF1B140C)
    val body = Path().apply {
        val l = b.x - w / 2; val r = b.x + w / 2; val top = b.y - h
        val m = w / 5f
        moveTo(l, b.y); lineTo(l, top)
        lineTo(l + m, top); lineTo(l + m, top + m); lineTo(l + 2 * m, top + m); lineTo(l + 2 * m, top)
        lineTo(l + 3 * m, top); lineTo(l + 3 * m, top + m); lineTo(l + 4 * m, top + m); lineTo(l + 4 * m, top)
        lineTo(r, top); lineTo(r, b.y); close()
    }
    drawPath(body, Color.Black.copy(alpha = 0.25f), style = Stroke(h * 0.14f))
    drawPath(body, color)
    drawRect(Color.White.copy(alpha = 0.28f), Offset(b.x - w / 2, b.y - h + w / 5f), Size(w * 0.22f, h - w / 5f))
    drawPath(body, outline, style = Stroke(h * 0.06f))
    // the door
    val dw = w * 0.36f; val dh = h * 0.3f
    drawRoundRect(outline, Offset(b.x - dw / 2, b.y - dh), Size(dw, dh), CornerRadius(dw / 2, dw / 2))
}

/** A plain castle in the owner's colour: two towers and a taller keep behind a wall, with a gold pennant. */
private fun DrawScope.flatCastle(b: Offset, h: Float, color: Color) {
    flatTower(b + Offset(-h * 0.38f, 0f), h * 0.72f, color)
    flatTower(b + Offset(h * 0.38f, 0f), h * 0.72f, color)
    flatTower(b, h, color)
    val top = b.y - h
    drawLine(Color(0xFF1B140C), Offset(b.x, top), Offset(b.x, top - h * 0.3f), h * 0.05f)
    drawPath(Path().apply { moveTo(b.x, top - h * 0.3f); lineTo(b.x + h * 0.26f, top - h * 0.23f); lineTo(b.x, top - h * 0.15f); close() }, Color(0xFFF2C14E))
}

/** A small dark label with white writing, for prices and corner names. */
private fun DrawScope.tag(text: String, c: Offset, h: Float, bg: Color = Color(0xE6201510)) {
    val w = h * (0.55f + 0.5f * text.length)
    drawRoundRect(bg, Offset(c.x - w / 2, c.y - h / 2), Size(w, h), CornerRadius(h / 2))
    glyph(text, c, h * 0.72f, Color.White)
}

// =====================================================================  the board

@Composable
private fun DeedsBoard(s: DeedsSession, modifier: Modifier = Modifier, interactive: Boolean = true) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val art = rememberDeedArt()
    // pieces hop tile by tile toward where they now stand
    val shown = remember(s) { IntArray(Deeds.MAX) { g.pos[it] } }
    var tick by remember(s) { mutableIntStateOf(0) }
    for (p in 0 until Deeds.MAX) {
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
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF2E4A2B), Color(0xFF1C3320))), Offset(c, c), Size(w - 2 * c, w - 2 * c), CornerRadius(e * 0.2f))
                for (t in 0 until Deeds.N) drawTile(s, art, t, tileRect(t, w), e)
                if (g.phase != Deeds.SETUP && g.phase != Deeds.LOBBY && g.phase != Deeds.OVER) {
                    val r = tileRect(shown[g.actor], w)
                    drawRect(Color.White, r.topLeft, r.size, style = Stroke(e * 0.12f))
                    drawRect(s.seatColor(g.actor), r.topLeft, r.size, style = Stroke(e * 0.07f))
                }
                if (g.phase == Deeds.AUCTION && g.aTile >= 0) { val r = tileRect(g.aTile, w); drawRect(Color(0xFFFFD54F), r.topLeft, r.size, style = Stroke(e * 0.1f)) }
                if (s.selected >= 0) { val r = tileRect(s.selected, w); drawRect(Color(0xFFFFD54F), r.topLeft, r.size, style = Stroke(e * 0.12f)) }
                // the pieces, each on a white radiance
                val here = (0 until g.n).filter { g.hero[it] >= 0 && g.out[it] == 0 }
                for (p in here) {
                    val r = tileRect(shown[p], w)
                    val mates = here.filter { shown[it] == shown[p] }
                    val k = mates.indexOf(p); val m = mates.size
                    val off = when (m) {
                        1 -> Offset.Zero
                        2 -> Offset((if (k == 0) -1 else 1) * e * 0.24f, 0f)
                        else -> Offset((if (k % 2 == 0) -1 else 1) * e * 0.24f, (if (k < 2) -1 else 1) * e * 0.26f)
                    }
                    val box = e * when (m) { 1 -> 1.0f; 2 -> 0.78f; else -> 0.62f }
                    val ctr = r.center + off
                    // a bright white halo with a soft edge, so the piece reads on any painting
                    val gr = box * 0.7f
                    drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0f)), ctr, gr * 1.15f), gr * 1.15f, ctr)
                    for (k in 0 until 12) {
                        val ang = k * kotlin.math.PI.toFloat() / 6f
                        val dir = Offset(kotlin.math.cos(ang), kotlin.math.sin(ang))
                        drawLine(Color.White.copy(alpha = 0.9f), ctr + dir * (gr * 0.55f), ctr + dir * (gr * (if (k % 2 == 0) 1.05f else 0.85f)), e * 0.035f, StrokeCap.Round)
                    }
                    drawCircle(Brush.radialGradient(listOf(Color.White, Color.White, Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0f)), ctr, gr), gr, ctr)
                    drawOval(Color.Black.copy(alpha = 0.35f), Offset(ctr.x - box * 0.3f, ctr.y + box * 0.3f), Size(box * 0.6f, box * 0.16f))
                    drawCircle(s.seatColor(p), box * 0.32f, ctr + Offset(0f, box * 0.3f), style = Stroke(e * 0.06f))
                    art(art.heroes[g.hero[p]], ctr - Offset(0f, box * 0.06f), box)
                    heroTint(g.color[p])?.let { art(art.masks[g.hero[p]], ctr - Offset(0f, box * 0.06f), box, it) }
                }
            }
            val inner = sideDp / 11f * 1.5f
            Box(Modifier.offset(inner, inner).size(sideDp - inner * 2).padding(6.dp)) {
                if (s.selected >= 0) TileInfo(s, s.selected, Modifier.fillMaxSize()) else CentrePanel(s, Modifier.fillMaxSize())
            }
        }
    }
}

private val CORNER_LABEL = mapOf(0 to "SPIRE", 9 to "INQUISITOR", 18 to "COLOSSEUM", 27 to "MARKET")

/**
 * How far a tile's contents turn so they face the edge of the board, like a printed board:
 * the bottom row reads upright, the top row upside down, the sides sideways, and the corners on the diagonal.
 */
private fun tileAngle(t: Int): Float = when (t) {
    0 -> 135f; in 1..8 -> 180f; 9 -> -135f; in 10..17 -> -90f
    18 -> -45f; in 19..26 -> 0f; 27 -> 45f; else -> 90f
}

private fun DrawScope.drawTile(s: DeedsSession, art: DeedArt, t: Int, r: Rect, e: Float) {
    val g = s.g
    val kind = Deeds.KIND[t]
    val corner = t % 9 == 0
    val owner = g.owner[t]
    // the set's painting at half strength over parchment, so the pieces and towers stand out
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFBF3E0), Color(0xFFF0E2C2)), r.top, r.bottom), r.topLeft, r.size)
    // the Spire and the Colosseum show their own paintings edge to edge; the rest show their set's landscape
    val corner_painting = art.icons[300 + t]
    if (corner && corner_painting != null) drawImage(corner_painting, dstOffset = IntOffset(r.left.toInt(), r.top.toInt()),
        dstSize = IntSize(kotlin.math.ceil(r.width).toInt(), kotlin.math.ceil(r.height).toInt()), filterQuality = FilterQuality.Medium)
    else landscape(art.land[t], r, 0.5f)
    val c = r.center
    if (corner) {
        val m = minOf(r.width, r.height)
        clipRect(r.left, r.top, r.right, r.bottom) {
            rotate(tileAngle(t), c) {
                when (kind) {
                    // the Colosseum's painting, with the Dungeon's entrance over it
                    Deeds.ARENA -> { art(art.icons.getValue(200), c - Offset(0f, m * 0.06f), m * 0.58f); tag("DUNGEON", c + Offset(0f, m * 0.24f), e * 0.22f) }
                    Deeds.SPIRE -> tag("SPIRE", c + Offset(0f, m * 0.24f), e * 0.22f)
                    else -> {
                        art.icons[kind]?.let { art(it, c - Offset(0f, m * 0.08f), m * 0.62f) }
                        CORNER_LABEL[t]?.let { tag(it, c + Offset(0f, m * 0.22f), e * 0.22f) }
                    }
                }
            }
        }
        drawRect(Color(0xFF6E5636), r.topLeft, r.size, style = Stroke(1.5f))
        return
    }
    if (owner >= 0) {
        if (g.pawned[t] == 1) drawRect(Color.Black.copy(alpha = 0.25f), r.topLeft, r.size)
        // the owner's colour round the tile
        drawRect(s.seatColor(owner), Offset(r.left + e * 0.04f, r.top + e * 0.04f), Size(r.width - e * 0.08f, r.height - e * 0.08f), style = Stroke(e * 0.08f))
    }
    drawRect(Color(0xFF6E5636), r.topLeft, r.size, style = Stroke(1.5f))
    // everything else is laid out upright in the tile's own frame (band at its top, toward the middle of the board), then turned
    val depth = maxOf(r.width, r.height)
    val L = Rect(c.x - e / 2, c.y - depth / 2, c.x + e / 2, c.y + depth / 2)
    val band = e * 0.26f
    val bandRect = Rect(L.left, L.top, L.right, L.top + band)
    val field = Rect(L.left, L.top + band, L.right, L.bottom)
    rotate(tileAngle(t), c) {
        when (kind) {
            Deeds.LAND -> drawRect(DOMAIN_COLORS[Deeds.GROUP[t]], bandRect.topLeft, bandRect.size)
            Deeds.BRIDGE -> drawRect(Color(0xFF3F6E8C), bandRect.topLeft, bandRect.size)
            Deeds.MILL -> drawRect(Color(0xFF6D4C41), bandRect.topLeft, bandRect.size)
        }
        if (owner >= 0) drawRect(s.seatColor(owner), bandRect.topLeft, bandRect.size, style = Stroke(e * 0.04f))
        if (kind != Deeds.LAND && owner < 0) {
            val priced = g.isDeed(t)
            // with a price to show, the picture takes the top of the tile and the price sits below it
            val at = if (priced) Offset(field.center.x, field.top + field.height * 0.36f) else field.center
            val box = field.width * (if (priced) 0.86f else 0.9f)
            when (kind) {
                Deeds.MILL -> art(art.icons.getValue(if (t < 18) 100 else 101), at, box)
                else -> art.icons[kind]?.let { art(it, at, box) }
            }
        }
        if (g.isDeed(t) && owner < 0) {
            val at = if (kind == Deeds.LAND) field.center else Offset(field.center.x, field.top + field.height * 0.8f)
            tag("${Deeds.PRICE[t]}", at, e * 0.3f)
        }
        if (owner >= 0) {
            val oc = s.seatColor(owner)
            val n = g.towers[t]
            val coinAt = if (n == 0) field.center else Offset(field.center.x, field.top + field.height * 0.26f)
            val foot = Offset(field.center.x, field.bottom - field.height * 0.1f)
            // as tall as fits, but a row of towers (or a castle) must stay inside the tile
            val span = if (n == 4) 1.3f else 0.5f + (n - 1).coerceAtLeast(0) * 0.56f
            val th = minOf(field.height * 0.5f, e * 0.5f, field.width * 0.82f / span)
            if (n == 4) flatCastle(foot, th * 1.05f, oc)
            else for (k in 0 until n) flatTower(foot + Offset((k - (n - 1) / 2f) * th * 0.56f, 0f), th, oc)
            deedCoin(coinAt, e * 0.2f, g.face(t), oc, dim = g.pawned[t] == 1)
            if (g.pawned[t] == 1) tag("PAWNED", Offset(field.center.x, coinAt.y + e * 0.3f), e * 0.2f)
        }
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
        if (g.phase == Deeds.AUCTION && g.aTile >= 0) {
            Column(Modifier.clip(PANEL).parchment(PANEL, Color(0xFFC9972F), 2.dp).padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                InkText("Auction", 12, color = INK_SOFT)
                InkText(g.names[g.aTile], 15, bold = true)
                InkText(if (g.aHigh >= 0) "${g.aBid} gold · ${s.who(g.aHigh)}" else "No bids yet", 13, bold = true)
            }
        } else if (g.phase != Deeds.SETUP && g.phase != Deeds.LOBBY) Dice(g.d1, g.d2, g.rollId, 44.dp)
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
            Deeds.ARENA -> lines += listOf(
                "Land here to win the purse: ${g.purse} gold from taxes and fines.",
                "Then you may challenge a lord to trial by combat. Both roll two dice; the winner takes the loser's best deed and the loser takes 2 wounds.",
                "The Dungeon lies beneath. Locked in: roll doubles, pay the jailer ${Deeds.BRIBE}, or use a Skeleton Key. Each night inside heals 1. After three tries you must pay.",
            )
            Deeds.SEIZE -> lines += "The Inquisitor drags you down to the Dungeon beneath the Colosseum."
            Deeds.MARKET -> lines += "Buy one keepsake: " + (0..5).joinToString(", ") { "${Deeds.ITEM_NAME[it]} ${Deeds.ITEM_PRICE[it]}" } + "."
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
                if (kind == Deeds.LAND) lines += "Tower: ${g.towerCost(t, if (o >= 0) o else s.viewer)} gold each, up to 3, then a castle (whole domain only)."
            }
        }
        for (l in lines) { Spacer(Modifier.height(3.dp)); InkText(l, 11) }
        if (g.isDeed(t)) { Spacer(Modifier.height(6.dp)); HomageTable(s, t) }
    }
}

/** What a visitor owes on deed [t] at each stage: homage in gold, wounds for refusing it, and wounds from the Blade. */
@Composable
private fun HomageTable(s: DeedsSession, t: Int) {
    val g = s.g
    val o = g.owner[t]
    val p = Deeds.PRICE[t]
    val rows: List<List<String>>
    var now = -1
    when (Deeds.KIND[t]) {
        Deeds.LAND -> {
            val gold = listOf(p * 8 / 100, p * 8 / 100 * 2, p * 35 / 100, p * 100 / 100, p * 220 / 100, p * 330 / 100)
            rows = listOf("Bare", "Full set", "1 tower", "2 towers", "3 towers", "Castle").mapIndexed { k, label ->
                listOf(label, "${gold[k]}", "${maxOf(1, k)}", "${listOf(2, 3, 4, 5, 6, 8)[k]}")
            }
            if (o >= 0) now = if (g.towers[t] == 0) (if (g.fullDomain(t)) 1 else 0) else g.towers[t] + 1
        }
        Deeds.BRIDGE -> {
            rows = (1..4).map { n -> listOf("$n bridge${if (n > 1) "s" else ""} held", "${Deeds.BRIDGE_TOLL[n - 1]}", "1", "$n") }
            if (o >= 0) now = (g.bridgesOf(o) - 1).coerceIn(0, 3)
        }
        else -> {
            rows = listOf(listOf("Mill or mine", "dice × 4", "1", "1"), listOf("Both held", "dice × 10", "1", "2"))
            if (o >= 0) now = if (g.millsOf(o) >= 2) 1 else 0
        }
    }
    val head = listOf("", "Homage", "Refuse", "Blade")
    val weights = listOf(1.5f, 1.1f, 1f, 1f)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, GOLD, RoundedCornerShape(8.dp))) {
        Row(Modifier.fillMaxWidth().background(WOOD).padding(vertical = 3.dp)) {
            for ((k, h) in head.withIndex()) Text(h, Modifier.weight(weights[k]).padding(horizontal = 4.dp), color = PARCH_TOP, fontFamily = SERIF, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                textAlign = if (k == 0) TextAlign.Start else TextAlign.End, maxLines = 1, softWrap = false)
        }
        for ((r, row) in rows.withIndex()) {
            val bg = when { r == now -> Color(0xFFF2C14E).copy(alpha = 0.55f); r % 2 == 0 -> Color.White.copy(alpha = 0.45f); else -> Color.Transparent }
            Row(Modifier.fillMaxWidth().background(bg).padding(vertical = 2.dp)) {
                for ((k, cell) in row.withIndex()) Text(cell, Modifier.weight(weights[k]).padding(horizontal = 4.dp), color = INK, fontFamily = SERIF,
                    fontWeight = if (r == now || k == 0) FontWeight.Bold else FontWeight.Medium, fontSize = 11.sp, textAlign = if (k == 0) TextAlign.Start else TextAlign.End, maxLines = 1, softWrap = false)
            }
        }
    }
    Spacer(Modifier.height(3.dp))
    InkText("Homage is gold; Refuse and Blade are wounds. " + (if (now >= 0) "Highlighted: what it asks now. " else "") + "A Dragon's blades wound 1 more; the Thief pays a quarter less homage; omens can change these.", 10, color = INK_SOFT)
}

// =====================================================================  players

@Composable
private fun HeroImg(hero: Int, color: Int, size: Dp, modifier: Modifier = Modifier) {
    if (hero < 0) { Box(modifier.size(size)); return }
    Box(modifier.size(size)) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(HERO_ART[hero]), null, Modifier.size(size), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        heroTint(color)?.let {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(HERO_MASK[hero]), null, Modifier.size(size), contentScale = androidx.compose.ui.layout.ContentScale.Fit, colorFilter = it)
        }
    }
}

@Composable
private fun Portrait(hero: Int, size: Dp, modifier: Modifier = Modifier) {
    if (hero < 0) { Box(modifier.size(size)); return }
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(HERO_PORTRAIT[hero]), null, modifier.size(size), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
}

@Composable
private fun PlayerCard(s: DeedsSession, p: Int, modifier: Modifier) {
    val g = s.g
    val active = g.decider == p && g.phase != Deeds.OVER
    val col = s.seatColor(p)
    Row(modifier.alpha(if (g.out[p] == 1) 0.45f else 1f).parchment(PANEL, if (active) col else GOLD, if (active) 2.5.dp else 1.dp).padding(horizontal = 5.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).border(2.dp, col, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Portrait(g.hero[p], 40.dp)
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            InkText(s.who(p) + when { g.out[p] == 1 -> " · vanquished"; g.jail[p] >= 0 -> " · in dungeon"; else -> "" }, 12, bold = true, lines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFE0B23A)))
                Spacer(Modifier.width(2.dp))
                InkText("${g.gold[p]}", 12, bold = true)
                Spacer(Modifier.width(6.dp))
                InkText("♥ ${g.hp[p]}/${g.maxHp(p)}", 12, bold = true, color = if (g.hp[p] <= 3) Color(0xFFB71C1C) else Color(0xFF8E2424))
            }
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(INK_SOFT.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(g.hp[p].coerceAtLeast(0) / g.maxHp(p).toFloat()).height(4.dp).background(Color(0xFFC62828)))
            }
            val kept = g.items[p].sum()
            InkText("${g.deeds(p).size} deeds · ${g.domainsHeld(p)} dom." + (if (kept > 0) " · $kept loot" else "") + if (g.used[p] == 0 && g.hero[p] >= 0) " · ★" else "", 10, color = INK_SOFT, lines = 1)
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
    var showTrade by remember(s) { mutableStateOf(false) }
    var showDeed by remember(s) { mutableStateOf(false) }
    // online games always seat two people
    if (s.online && g.phase == Deeds.LOBBY) LaunchedEffect(s) { if (s.onlineSeat == 1) s.act(Act.SEATS, 200) }
    if (g.phase == Deeds.LOBBY && !s.online) { Lobby(s); return }
    if (g.phase == Deeds.SETUP && s.myMove) { ChampionPicker(s); return }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 52.dp).navigationBarsPadding().padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            for (row in (0 until g.n).chunked(2)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (p in row) PlayerCard(s, p, Modifier.weight(1f))
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(2.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val board = minOf(maxWidth - 8.dp, maxHeight - 56.dp).coerceAtLeast(160.dp)
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    // table mode turns the words to face Player 2; the board stays put on the table
                    DeedsBoard(s, Modifier.size(board).tableUpright())
                    Spacer(Modifier.height(4.dp))
                    Chronicle(s, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            PromptRibbon((if (s.localTwoPlayer && !s.online && g.phase != Deeds.OVER) "${s.who(g.decider)} · " else "") + s.prompt, s.thinking || s.heraldBusy)
            Spacer(Modifier.height(5.dp))
            Actions(s, openLands = { showLands = true }, openTrade = { showTrade = true }, openDeed = { showDeed = true })
        }
        HeraldCard(s, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 70.dp).padding(horizontal = 12.dp))
        if (s.myMove && g.phase == Deeds.DECIDE && g.pType == Deeds.D_CARD) CardDialog(s)
        if (s.myMove && g.phase == Deeds.TRADE) OfferDialog(s)
        if (showLands) LandsDialog(s) { showLands = false }
        if (showTrade) TradeDialog(s) { showTrade = false }
        if (showDeed) DeedDialog(s) { showDeed = false }
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
private fun Actions(s: DeedsSession, openLands: () -> Unit, openTrade: () -> Unit, openDeed: () -> Unit) {
    val g = s.g
    val p = g.decider
    val mine = s.myMove
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 44.dp).alpha(if (mine) 1f else 0.55f),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (!mine) return@FlowRow
        val t = g.pos[p]
        val manage = g.phase == Deeds.ROLL || g.phase == Deeds.AFTER
        when (g.phase) {
            Deeds.ROLL -> {
                if (g.jail[p] >= 0) {
                    WoodButton("Roll for doubles", RI.DICE) { s.act(Act.ROLL) }
                    if (g.gold[p] >= Deeds.BRIBE) WoodButton("Bribe ${Deeds.BRIBE}", RI.TRADE) { s.act(Act.BRIBE) }
                    if (g.items[p][Deeds.KEY] > 0) WoodButton("Use key", RI.RELIC) { s.act(Act.KEY) }
                } else WoodButton(if (g.again) "Roll again" else "Roll", RI.DICE) { s.act(Act.ROLL) }
            }
            Deeds.DECIDE -> when (g.pType) {
                Deeds.D_BUY -> {
                    WoodButton("Buy · ${Deeds.PRICE[t]}", RI.SCROLL, enabled = g.gold[p] >= Deeds.PRICE[t]) { s.act(Act.BUY) }
                    WoodButton("Auction", RI.TRADE) { s.act(Act.PASS) }
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
                    for (q in g.rivals(p)) if (g.canChallenge(q)) WoodButton("Fight ${s.who(q)}", RI.KNIGHT) { s.act(Act.CHALLENGE, q) }
                    WoodButton("Leave", null) { s.act(Act.DECLINE) }
                }
                Deeds.D_MARKET -> {
                    for (i in 0..5) SmallBtn("${Deeds.ITEM_NAME[i]} ${Deeds.ITEM_PRICE[i]}", g.canShop(i)) { s.act(Act.SHOP, i) }
                    WoodButton("Move on", null) { s.act(Act.LEAVE) }
                }
                else -> WoodButton("Read the card", RI.SCROLL) { s.act(Act.ACK) }
            }
            Deeds.AUCTION -> {
                for (k in Deeds.BID_STEPS.indices) WoodButton("Bid ${g.aBid + Deeds.BID_STEPS[k]}", if (k == 0) RI.TRADE else null, enabled = g.canBid(k)) { s.act(Act.BID, k) }
                WoodButton("Drop out", null) { s.act(Act.FOLD) }
            }
            Deeds.AFTER -> {
                WoodButton(if (g.again && g.jail[p] < 0) "Roll again" else "End turn", if (g.again && g.jail[p] < 0) RI.DICE else RI.END) { s.act(Act.END) }
            }
        }
        if (manage || g.phase == Deeds.DECIDE && g.pType != Deeds.D_CARD) {
            if (manage) WoodButton(if (g.spireCharge) "Lands & coins" else "Lands", RI.SCROLL, onClick = openLands)
            if (manage && g.canPropose() && g.rivals(p).isNotEmpty()) WoodButton("Trade", RI.TRADE, onClick = openTrade)
            if (manage && g.used[p] == 0 && g.hero[p] >= 0) {
                val can = if (g.deedAims(p)) g.deedTargets(p).isNotEmpty() else g.canGreatDeed()
                WoodButton(Deeds.DEED_NAME[g.hero[p]], RI.CROWN, enabled = can) { if (g.deedAims(p)) openDeed() else s.act(Act.DEED) }
            }
            if (g.canPotion()) WoodButton("Potion", RI.MOON) { s.act(Act.POTION) }
            if (g.canSiege()) WoodButton("Siege", RI.FIRE) { s.act(Act.SIEGE) }
        }
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

/** An offer waiting on the lord it was made to. */
@Composable
private fun OfferDialog(s: DeedsSession) {
    val g = s.g
    val o = g.offer ?: return
    androidx.compose.ui.window.Dialog(onDismissRequest = {}, properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).parchment(RoundedCornerShape(16.dp), s.seatColor(o.from), 3.dp).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RIconOf(RI.TRADE, 48.dp)
            InkText("${s.who(o.to)}: an offer from ${s.who(o.from)}", 18, bold = true, align = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            InkText("You would get", 12, color = INK_SOFT)
            InkText((o.give.map { g.names[it] } + if (o.giveGold > 0) listOf("${o.giveGold} gold") else emptyList()).ifEmpty { listOf("nothing") }.joinToString(", "), 15, bold = true, align = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            InkText("You would give", 12, color = INK_SOFT)
            InkText((o.get.map { g.names[it] } + if (o.getGold > 0) listOf("${o.getGold} gold") else emptyList()).ifEmpty { listOf("nothing") }.joinToString(", "), 15, bold = true, align = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WoodButton("Accept", RI.CROWN) { s.act(Act.ACCEPT) }
                WoodButton("Decline", null) { s.act(Act.REJECT) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TradeDialog(s: DeedsSession, close: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val me = g.actor
    var partner by remember { mutableIntStateOf(g.rivals(me).firstOrNull() ?: -1) }
    val give = remember { mutableStateListOf<Int>() }
    val get = remember { mutableStateListOf<Int>() }
    var giveGold by remember { mutableIntStateOf(0) }
    var getGold by remember { mutableIntStateOf(0) }
    if (partner < 0) { close(); return }
    val offer = Deeds.Offer(me, partner, give.toList(), get.toList(), giveGold, getGold)
    AlertDialog(
        onDismissRequest = close,
        containerColor = PARCH_TOP,
        title = { InkText("Offer a trade", 18, bold = true) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InkText("With", 12, color = INK_SOFT)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (q in g.rivals(me)) SmallBtn(s.who(q) + if (q == partner) " ✓" else "", true) { partner = q; get.clear(); getGold = 0 }
                }
                Spacer(Modifier.height(8.dp))
                InkText("You give", 13, bold = true)
                DeedPicks(s, g.deeds(me), give)
                GoldStepper(giveGold, g.gold[me]) { giveGold = it }
                Spacer(Modifier.height(8.dp))
                InkText("You get from ${s.who(partner)}", 13, bold = true)
                DeedPicks(s, g.deeds(partner), get)
                GoldStepper(getGold, g.gold[partner]) { getGold = it }
                Spacer(Modifier.height(6.dp))
                InkText("Lands with towers can't be traded. You may make up to 3 offers a turn.", 11, color = INK_SOFT)
            }
        },
        confirmButton = { TextButton(enabled = g.validOffer(offer), onClick = { close(); s.act(Act.PROPOSE, offer = offer) }) { Text("Offer", color = INK, fontFamily = SERIF, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel", color = INK_SOFT, fontFamily = SERIF) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeedPicks(s: DeedsSession, deeds: List<Int>, picked: MutableList<Int>) {
    val g = s.g
    if (deeds.isEmpty()) { InkText("No deeds.", 12, color = INK_SOFT); return }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (t in deeds) {
            val on = t in picked
            val free = g.towers[t] == 0
            Row(
                Modifier.alpha(if (free) 1f else 0.4f).clip(RoundedCornerShape(50)).background(if (on) Color(0xFF4E321C) else Color.White.copy(alpha = 0.6f))
                    .clickable(enabled = free) { if (on) picked.remove(t) else picked.add(t) }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val d = Deeds.GROUP[t]
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (d >= 0) DOMAIN_COLORS[d] else Color(0xFF5B8FB0)))
                Spacer(Modifier.width(4.dp))
                Text(g.names[t], color = if (on) PARCH_TOP else INK, fontFamily = SERIF, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GoldStepper(v: Int, max: Int, set: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InkText("Gold", 12, color = INK_SOFT)
        Spacer(Modifier.width(8.dp))
        SmallBtn("−50", v > 0) { set(maxOf(0, v - 50)) }
        InkText("  $v  ", 14, bold = true)
        SmallBtn("+50", v + 50 <= max) { set(minOf(max, v + 50)) }
    }
}

/** Aim the Dragon's Inferno or the Thief's Heist. */
@Composable
private fun DeedDialog(s: DeedsSession, close: () -> Unit) {
    val g = s.g
    val p = g.actor
    val h = g.hero[p].coerceAtLeast(0)
    AlertDialog(
        onDismissRequest = close,
        containerColor = PARCH_TOP,
        title = { InkText(Deeds.DEED_NAME[h], 18, bold = true) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InkText(Deeds.DEED_TEXT[h], 13)
                Spacer(Modifier.height(8.dp))
                for (t in g.deedTargets(p)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.5f))
                        .clickable { close(); s.act(Act.DEED, t) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(s.seatColor(g.owner[t])))
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            InkText(g.names[t], 13, bold = true)
                            InkText(s.who(g.owner[t]) + if (g.towers[t] > 0) " · ${g.towerWord(g.towers[t])}" else " · ${Deeds.PRICE[t]} gold", 11, color = INK_SOFT)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Not yet", color = INK, fontFamily = SERIF, fontWeight = FontWeight.Bold) } },
    )
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
                if (g.hero[p] >= 0) InkText("${Deeds.DEED_NAME[g.hero[p]]}: " + if (g.used[p] == 1) "used" else Deeds.DEED_TEXT[g.hero[p]], 11, color = INK_SOFT)
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
                            Box(Modifier.size(34.dp).clip(CircleShape).background(s.seatColor(p)).clickable(enabled = canAct && g.canFlip(t)) { s.act(Act.FLIP, t) }, contentAlignment = Alignment.Center) {
                                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(if (g.coin[t] == Deeds.COIN_CROWN) com.puresudoku.R.drawable.dd_coin_crown else com.puresudoku.R.drawable.dd_coin_blade), null, Modifier.size(30.dp))
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
    Box(Modifier.alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(50)).wood(RoundedCornerShape(50)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, color = PARCH_TOP, fontFamily = SERIF, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

// =====================================================================  seating the lords

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Lobby(s: DeedsSession) {
    val local = s.localTwoPlayer
    var count by remember(s) { mutableIntStateOf(2) }
    // bit k set: seat k is an AI lord
    var aiMask by remember(s) { mutableIntStateOf(if (local) 0 else 0b1110) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(top = 52.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().parchment(PANEL).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            InkText("Seat the lords", 22, bold = true)
            InkText(if (local) "Two to four lords. Each seat is a person passing the device, or an AI lord." else "Ride against one, two or three AI rivals.", 13, color = INK_SOFT, align = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            InkText("Lords at the table", 14, bold = true)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in 2..4) Box(
                    Modifier.size(52.dp).clip(CircleShape).then(if (k == count) Modifier.wood(CircleShape) else Modifier.parchment(CircleShape)).clickable { count = k },
                    contentAlignment = Alignment.Center,
                ) { Text("$k", fontFamily = SERIF, fontWeight = FontWeight.Black, fontSize = 22.sp, color = if (k == count) PARCH_TOP else INK) }
            }
            Spacer(Modifier.height(12.dp))
            for (p in 0 until count) {
                val isAi = p > 0 && (aiMask shr p) and 1 == 1
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.5f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(SEAT_COLORS[p]))
                    Spacer(Modifier.width(8.dp))
                    InkText(if (!local) (if (p == 0) "You" else if (count == 2) "Rival" else "Rival $p") else "Seat ${p + 1}", 15, bold = true, modifier = Modifier.weight(1f))
                    if (local && p > 0) {
                        SmallBtn("Person" + if (!isAi) " ✓" else "", true) { aiMask = aiMask and (1 shl p).inv() }
                        Spacer(Modifier.width(4.dp))
                        SmallBtn("AI lord" + if (isAi) " ✓" else "", true) { aiMask = aiMask or (1 shl p) }
                    } else InkText(if (p == 0) "Person" else "AI lord", 13, color = INK_SOFT)
                }
            }
            Spacer(Modifier.height(6.dp))
            InkText(if (count == 2) "Four whole domains crown a lord at once." else "Three whole domains crown a lord at once.", 12, color = INK_SOFT, align = TextAlign.Center)
        }
        Spacer(Modifier.height(14.dp))
        WoodButton("Choose champions", RI.CROWN) { s.act(Act.SEATS, count * 100 + (aiMask and ((1 shl count) - 1))) }
    }
}

// =====================================================================  choosing a champion

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChampionPicker(s: DeedsSession) {
    val g = s.g
    val p = g.actor
    var hero by remember(s, p) { mutableIntStateOf(p % 4) }
    var col by remember(s, p) { mutableIntStateOf((0 until Deeds.COLOR.size).map { (it + p * 2) % Deeds.COLOR.size }.first { g.colorFree(p, it) }) }
    val people = (0 until g.n).count { s.human(it) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(top = 52.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().parchment(PANEL).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            InkText(if (people > 1) "${s.who(p)}, choose your champion" else "Choose your champion", 20, bold = true, align = TextAlign.Center)
            InkText("Win deeds, fell your rivals and be crowned King of Eiseria.", 12, color = INK_SOFT, align = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(150.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0.7f), DEED_COLORS[col].copy(alpha = 0.25f)))).border(3.dp, DEED_COLORS[col], CircleShape), contentAlignment = Alignment.Center) {
                HeroImg(hero, col, 130.dp)
            }
            Spacer(Modifier.height(6.dp))
            InkText((if (col > 0) Deeds.COLOR[col] + " " else "") + Deeds.HERO[hero], 18, bold = true)
            InkText(Deeds.HERO_POWER[hero], 13, align = TextAlign.Center)
            InkText("Great deed, once a game: " + Deeds.DEED_NAME[hero] + ". " + Deeds.DEED_TEXT[hero].removePrefix("Once a game: ").replaceFirstChar { it.uppercase() }, 12, color = INK_SOFT, align = TextAlign.Center)
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
                val free = g.colorFree(p, c)
                Column(Modifier.alpha(if (free) 1f else 0.3f).clickable(enabled = free) { col = c }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(DEED_COLORS[c]).border(if (c == col) 3.dp else 1.dp, if (c == col) Color.White else INK, CircleShape))
                    InkText(Deeds.COLOR[c], 10, color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        WoodButton("Ride out", RI.CROWN, enabled = g.colorFree(p, col)) { s.act(Act.CHOOSE, hero * 10 + col) }
    }
}

// =====================================================================  how to play

private fun deedsDemo(): DeedsSession {
    val g = Deeds.newGame(1, Random(2026))
    g.seats(3, 0b110)
    g.choose(Deeds.KNIGHT, 1); g.choose(Deeds.DRAGON, 2); g.choose(Deeds.THIEF, 3)
    for (t in listOf(1, 3, 5, 7, 8, 14)) g.owner[t] = 0
    for (t in listOf(19, 21, 22, 25, 31)) g.owner[t] = 1
    for (t in listOf(28, 30)) g.owner[t] = 2
    g.towers[5] = 1; g.towers[7] = 1; g.towers[8] = 2
    g.towers[19] = 3; g.towers[21] = 3; g.towers[22] = 4
    g.pawned[14] = 1
    g.coin[19] = Deeds.COIN_BLADE; g.coin[21] = Deeds.COIN_BLADE; g.coin[3] = Deeds.COIN_BLADE
    g.pos[0] = 6; g.pos[1] = 20; g.pos[2] = 33; g.d1 = 4; g.d2 = 2; g.rollId = 1
    g.log.clear()
    return DeedsSession(Difficulty.MEDIUM, g)
}

private fun deedsStep(title: String, body: String, select: Int) = TutorialStep(title, body) { _ ->
    val s = remember { deedsDemo().also { it.selected = select } }
    DeedsBoard(s, Modifier.fillMaxWidth().height(320.dp), interactive = false)
}

private fun deedsTutorial(): List<TutorialStep> = listOf(
    deedsStep("Roll and ride", "Seat two to four lords, people or AI, and choose your champions. Roll two dice and ride clockwise from the Spire. Doubles roll again; three doubles in a row land you in the Dungeon.", -1),
    deedsStep("Buy, or auction", "Stop on unclaimed land to buy its deed at the price on its tag. Pass, and it goes to auction among every lord. Hold a whole domain to double its homage and raise towers, then a castle.", 8),
    deedsStep("The two-sided coin", "Every deed has a coin, tossed when you win it. Crown: visitors pay homage in gold, or refuse and your towers strike them. Blade: visitors simply take wounds. You turn your coins only on a turn you pass the Spire.", 19),
    deedsStep("Felled!", "Lose all your health and you're felled: the lord who felled you seizes your best deed, and you wake in the Dungeon beneath the Colosseum. Felled with no deeds left, you're vanquished.", 21),
    deedsStep("Trades and great deeds", "Offer deeds and gold to any lord on your turn. Each champion has one great deed a game: the Dragon's Inferno, the Knight's Second Wind, the Thief's Heist and the Black Knight's Reign of Terror.", 28),
    deedsStep("Fate, loot, market, Colosseum", "Fate brings fortune and folly; Loot brings gold or keepsakes; the Royal Market sells them. Taxes fill the Colosseum purse: land there to win it and dare a trial by combat for a deed.", 18),
    deedsStep("Become King", "Hold enough whole domains (four with two lords, three with more) to be crowned at once. Otherwise the old King dies at the last round, and the lord with the most renown takes the throne.", 0),
)
