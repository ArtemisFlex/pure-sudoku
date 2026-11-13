package com.puresudoku.ui.games.realm

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.board.CAct
import com.puresudoku.core.board.AegConquest
import com.puresudoku.core.board.AegStewardship
import com.puresudoku.core.board.AegConquestAi
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.games.board.DuelSession
import com.puresudoku.ui.games.board.glyph
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.LessonStep
import com.puresudoku.ui.session.MiddleStat
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.tutorial.DemoStage
import com.puresudoku.ui.tutorial.DemoTargets
import com.puresudoku.ui.tutorial.TutorialStep
import com.puresudoku.ui.tutorial.demoTarget
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private typealias AC = AegConquest

// =====================================================================  session

class AegConquestSession(difficulty: Difficulty, val g: AegConquest) : DuelSession(GameKind.AEG_CONQUEST, difficulty) {
    var showFinalHistory by mutableStateOf(false); private set
    var selected by mutableIntStateOf(-1)
    var events by mutableStateOf<List<String>>(emptyList()); private set

    override val middleStat get() = MiddleStat.Counter("People", "${g.sw.people()}/${g.goal}")
    override val progress: Float get() =
        ((g.sw.people().toFloat() / g.goal).coerceAtMost(1f) * 0.5f + (g.rivals.count { !it.alive }.toFloat() / g.rivals.size.coerceAtLeast(1)) * 0.5f).coerceAtMost(0.95f)
    override val wonTitle get() = "Your civilization endures!"
    override val lostTitle get() = "Your people are gone"
    override val lostMessage get() = "Your civilization lasted until ${AC.SEASON[(g.season) % 4].lowercase()} of year ${g.year}. This game's save slot has been cleared."

    val myMove get() = outcome == Outcome.PLAYING && !heraldBusy && !g.over

    private fun flush() { if (g.log.isNotEmpty()) { events = (events + g.log).takeLast(5); g.log.clear() } }

    fun act(a: CAct): Boolean {
        if (!myMove) return false
        if (!g.legal(a)) { nudge("You can't do that right now"); return false }
        coachTip = null; coachFrom = -1
        val before = g.season
        g.apply(a, rng)
        val logs = g.log.toList(); flush(); bump(); changed()
        if (a is CAct.Attack) announce(if (logs.any { "took the" in it }) "Victory!" else "Your soldiers were thrown back", logs.joinToString("\n"), 2600, true, RI.KNIGHT)
        if (a == CAct.End && g.season != before) seasonCards(logs)
        if (logs.any { it.startsWith("Ruins!") }) announce("Ruins!", logs.first { it.startsWith("Ruins!") }.removePrefix("Ruins! "), 2000, true, RI.RELIC)
        if (g.over) whenQuiet { showFinalHistory = true }
        return true
    }

    /** The season's news: the harvest roll, the event card, then births and deaths. */
    private fun seasonCards(logs: List<String>) {
        val harvest = logs.firstOrNull { "harvest: rolled" in it } ?: ""
        announce("Harvest: ${g.lastDice[0]} + ${g.lastDice[1]} = ${g.lastHarvest}", harvest.substringAfter(": rolled ${g.lastHarvest}").removePrefix(", ").removePrefix(" ").ifEmpty { "An ordinary harvest" }, 1800, true, RI.DICE)
        val card = if (g.lastCard >= 0) g.CARDS[g.lastCard] else "A quiet season"
        val cardText = logs.firstOrNull { it.startsWith(card.substringBefore(' ')) || it.startsWith(card) } ?: logs.getOrNull(logs.indexOf(harvest) + 1) ?: ""
        announce(card, cardText.substringAfter(": ", cardText), 2600, true, RI.SCROLL)
        val rest = logs.filter { it != harvest && it != cardText && !it.startsWith("Games at") }
        if (rest.isNotEmpty()) announce("${AC.SEASON[(g.season + 3) % 4]} ends", rest.joinToString("\n"), 2200, true, RI.CROWN)
        announce("${g.seasonName}, year ${g.year}", "${g.sw.people()} people · ${g.res[AC.FOOD]} food · happiness ${g.happiness}", 1300, true, RI.SUN)
    }

    fun finishGame() {
        showFinalHistory = false
        if (g.won) win(listOf(BonusLine("People", g.pop * rules.basePoints / 2), BonusLine("Rivals felled", g.rivals.size * rules.basePoints * 3),
            BonusLine("Years", maxOf(0, 40 - g.year) * rules.basePoints / 4)))
        else lose()
    }

    fun tapTile(h: Int) { if (myMove) { selected = if (selected == h) -1 else h; bump() } }

    /** A road into [b] from whichever of your connected neighbours is nearest the port. */
    fun roadInto(b: Int) {
        val conn = g.connected()
        val a = g.map.hexNbrs[b].filter { conn[it] && b in g.roadTargets(it, conn) }.minByOrNull { g.hexDist(it, g.capital) } ?: run { nudge("Your roads don't reach here yet"); return }
        act(CAct.Road(a, b))
    }

    val prompt: String get() = when {
        outcome != Outcome.PLAYING -> ""
        heraldBusy -> "The season's news · tap a card to skip"
        selected < 0 -> "Tap a tile: build on your roads, or lay a road out into new land"
        else -> tileText(selected) + g.sw.notes(selected).joinToString("") { " · $it" }
    }

    fun tileText(h: Int): String {
        if (!g.revealed[h]) return "Unknown land, hidden in fog"
        if (!g.isLand(h)) return "The sea"
        val conn = g.connected()
        val b = g.building[h]
        val t = AC.TERRAIN[g.terrain[h]].replaceFirstChar { it.uppercase() }
        return when {
            g.rivalLand[h] >= 0 -> { val r = g.rivals[g.rivalLand[h]]; "${r.name.replaceFirstChar { it.uppercase() }}: ${g.rivalGuard[h]} soldiers" + if (r.capital == h) " · their capital (+2)" else "" }
            b != AC.NONE -> "$t · ${AC.NAME[b]}" + (if (AC.WORKERS[b] > 0) " · ${g.workers[h]}/${AC.WORKERS[b]} workers" + (if (g.staffed()[h]) "" else ", idle") else "") + if (g.guard[h] > 0) " · ${g.guard[h]} soldiers" else ""
            conn[h] -> "$t · on your roads: build here" + if (g.guard[h] > 0) " · ${g.guard[h]} soldiers" else ""
            else -> "$t · lay a road here to reach it" + if (g.ruins[h]) " · ruins!" else ""
        }
    }

    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (!myMove) return null
        val snap = g.copy()
        return {
            when (val a = AegConquestAi.next(snap, Random(7))) {
                is CAct.Build -> Triple("Build a ${AC.NAME[a.type].lowercase()} on the ringed tile.", a.h, -1)
                is CAct.Road -> Triple("Lay a road into the ringed tile.", a.b, -1)
                is CAct.Upgrade -> Triple("Grow the ringed village into a town.", a.h, -1)
                is CAct.Train -> Triple("Train ${a.n} soldier${if (a.n > 1) "s" else ""} at your barracks.", -1, -1)
                is CAct.Scout -> Triple("Send scouts from the ringed tile.", a.h, -1)
                is CAct.Attack -> Triple("Attack the ringed rival tile from your tile beside it: your soldiers outnumber theirs.", a.to, -1)
                is CAct.Assign -> Triple("Put an idle person to work at the ringed building.", a.h, -1)
                is CAct.Guard -> Triple("Post a soldier from your reserve on the ringed tile.", a.h, -1)
                is CAct.Trade -> Triple("Trade at your port: ${snap.tradeRate()} ${AC.RES[a.give]} for 1 ${AC.RES[a.get]}.", snap.capital, -1)
                is CAct.Choose -> Triple("Answer the event card.", -1, -1)
                is CAct.Decree -> Triple("Change your stewardship decree.", -1, -1)
                is CAct.Fallow -> Triple("Change whether the ringed farm rests.", a.h, -1)
                is CAct.Demolish -> Triple("Demolish the building on the ringed tile.", a.h, -1)
                CAct.End -> Triple("Nothing more worth doing: end the season.", -1, -1)
            }
        }
    }

    override fun resume() { if (outcome == Outcome.PLAYING && g.over) showFinalHistory = true }

    override fun saveState(): JSONObject = JSONObject().put("s", JSONArray().also { a -> g.state().forEach { a.put(it) } }).put("moves", moves)

    companion object {
        fun restore(o: JSONObject): AegConquestSession {
            val st = o.getJSONObject("state").getJSONArray("s")
            val g = AegConquest.fromState(IntArray(st.length()) { st.getInt(it) })
            return AegConquestSession(Difficulty.valueOf(o.getString("difficulty")), g).also { it.moves = o.getJSONObject("state").optInt("moves"); it.restoreCommon(o) }
        }
    }
}

// =====================================================================  definition

object AegConquestDef : GameDef {
    override val kind = GameKind.AEG_CONQUEST
    override val fullBleed: Boolean get() = true
    override val hasHeader: Boolean get() = true
    @Composable override fun Header(session: Session, actions: com.puresudoku.ui.games.HeaderActions) = RealmHeader(session, actions, "Conquest · ")

    override fun generate(d: Difficulty): Session = AegConquestSession(d, AegConquest.newGame(d, Random(System.nanoTime())))
    override fun restore(o: JSONObject): Session = AegConquestSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Aegwaspell where you place every worker and soldier yourself. Each tile is held by the power standing on it, your port trades 4 for 1, and from Hard up the rivals conquer each other, and you."
    override fun background(session: Session) = com.puresudoku.R.drawable.bg_realm_ocean
    override fun levelInfo(d: Difficulty): String {
        val r = AC.RADIUS[d.ordinal]
        return "${3 * r * (r + 1) + 1} tiles · ${AC.RIVALS[d.ordinal]} rival civilizations · ${AC.POP_GOAL[d.ordinal]} people to win"
    }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A small world and two slow rivals."
        Difficulty.MEDIUM -> "Rivals grow a little faster."
        Difficulty.HARD -> "Three rivals, and now they attack: you and each other."
        Difficulty.EXPERT -> "Three rivals that grow quickly."
        Difficulty.MASTER -> "A vast world and four rivals."
        Difficulty.EXTREME -> "Four strong rivals and a great people to raise."
    }
    @Composable override fun Play(session: Session) = AegConquestUi(session as AegConquestSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override fun lesson(): Pair<Session, List<LessonStep>> {
        val s = AegConquestSession(Difficulty.EASY, AegConquest.newGame(Difficulty.EASY, Random(777)))
        return s to listOf(
            LessonStep("Aegwaspell: Conquest is Aegwaspell where you place every worker and soldier yourself. You begin with a port and a few people at the foot of a vast, fogged land."),
            LessonStep("Each tile can hold one building, and you can only build on tiles your roads reach. Tap a tile beside your port, then Road to reach it."),
            LessonStep("Farms on fields, ranches on meadows and fisheries on the coast feed your people: every person eats 1 food a season. In winter the farms lie fallow, so store food in granaries."),
            LessonStep("Villages and towns house your people. Select a building and tap Add worker to staff it: it makes nothing until its whole crew is there. The badge on it shows its crew."),
            LessonStep("Each season ends with the harvest dice (a low roll is a poor year, a high roll a bountiful one) and an event card: migrants, plague, bandits, raids, festivals and more."),
            LessonStep("Temples and the colosseum keep people happy. Unhappy people leave, and very unhappy people revolt."),
            LessonStep("Train soldiers at a barracks, then select a tile and tap Post soldier. The banner over each tile is its power: soldiers, +3 for a watchtower, +2 for a port or capital."),
            LessonStep("Attack a rival tile from a guarded tile beside it: your soldiers plus a die against its power plus a die. From Hard up, the rivals attack too. Lose your port and you lose the game."),
            LessonStep("Select your port to trade: 4 of one good for 1 of another."),
            LessonStep("Win with ${AC.POP_GOAL[0]} people (on Easy) once every rival capital has fallen. Lose if your last person dies. Good luck!"),
        )
    }

    override val tutorial: List<TutorialStep> get() = aegConquestTutorial()
}

// =====================================================================  board

/** Terrain paintings, until Aegwaspell's own arrive: the Eiseria tiles, some tinted. */
/** Aegwaspell's own terrain paintings, by terrain (null: not painted yet, so an Eiseria tile stands in). */
internal class AegConquestArt(val byTerrain: Map<Int, androidx.compose.ui.graphics.ImageBitmap>, val pieces: Map<Int, androidx.compose.ui.graphics.ImageBitmap>, val capital: androidx.compose.ui.graphics.ImageBitmap)

@Composable
private fun rememberAegConquestArt(): AegConquestArt {
    @Composable fun img(id: Int) = androidx.compose.ui.graphics.ImageBitmap.imageResource(id)
    return AegConquestArt(mapOf(
        AC.SWAMP to img(com.puresudoku.R.drawable.rt_ag_swamp),
        AC.COAST to img(com.puresudoku.R.drawable.rt_ag_coast),
        AC.PLAINS to img(com.puresudoku.R.drawable.rt_ag_plains),
        AC.DESERT to img(com.puresudoku.R.drawable.rt_ag_desert),
        AC.HILLS to img(com.puresudoku.R.drawable.rt_ag_hills),
    ), mapOf(
        AC.PORT to img(com.puresudoku.R.drawable.rt_ag_port),
        AC.VILLAGE to img(com.puresudoku.R.drawable.rt_ag_village),
        AC.TOWN to img(com.puresudoku.R.drawable.rt_ag_town),
        AC.FARM to img(com.puresudoku.R.drawable.rt_ag_farm),
        AC.RANCH to img(com.puresudoku.R.drawable.rt_ag_ranch2),
        AC.FISHERY to img(com.puresudoku.R.drawable.rt_ag_fishery),
        AC.MILL to img(com.puresudoku.R.drawable.rt_ag_mill),
        AC.QUARRY to img(com.puresudoku.R.drawable.rt_ag_quarry),
        AC.MINE to img(com.puresudoku.R.drawable.rt_ag_mine),
        AC.GRANARY to img(com.puresudoku.R.drawable.rt_ag_granary),
        AC.MARKET to img(com.puresudoku.R.drawable.rt_ag_market),
        AC.BARRACKS to img(com.puresudoku.R.drawable.rt_ag_barracks),
        AC.TOWER to img(com.puresudoku.R.drawable.rt_ag_tower),
        AC.TEMPLE to img(com.puresudoku.R.drawable.rt_ag_temple),
        AC.COLOSSEUM to img(com.puresudoku.R.drawable.rt_ag_colosseum),
        AegStewardship.LIGHTHOUSE to img(com.puresudoku.R.drawable.rt_wonder_lighthouse),
        AegStewardship.LIBRARY to img(com.puresudoku.R.drawable.rt_wonder_library),
        AegStewardship.SKY_TEMPLE to img(com.puresudoku.R.drawable.rt_wonder_sky_temple),
        AegStewardship.GARDENS to img(com.puresudoku.R.drawable.rt_wonder_gardens),
        AegStewardship.CITADEL to img(com.puresudoku.R.drawable.rt_wonder_citadel),
        AegStewardship.PALACE to img(com.puresudoku.R.drawable.rt_wonder_palace),
    ), img(com.puresudoku.R.drawable.rt_ag_capital))
}

private fun DrawScope.aegTile(g: AegConquest, h: Int, c: Offset, u: Float, art: RealmArt, own: AegConquestArt) {
    val tw = (sqrt3 * u).toInt() + 2; val th = (2 * u).toInt() + 2
    val t = g.terrain[h]
    own.byTerrain[t]?.let { drawImage(it, dstOffset = IntOffset((c.x - tw / 2f).toInt(), (c.y - th / 2f).toInt()), dstSize = IntSize(tw, th), filterQuality = FilterQuality.Medium); return }
    val (list, tint) = when (t) {
        AC.PLAINS -> art.byTerrain[3] to Color(0x22FFE082)
        AC.FIELDS -> art.byTerrain[2] to Color.Transparent
        AC.MEADOW -> art.byTerrain[3] to Color.Transparent
        AC.FOREST -> art.byTerrain[0] to Color.Transparent
        AC.HILLS -> art.byTerrain[1].subList(1, 2) to Color(0x2266BB6A)
        AC.MOUNTAIN -> art.byTerrain[1].filterIndexed { i, _ -> i != 1 && i != 2 } to Color.Transparent
        AC.SWAMP -> art.byTerrain[0].subList(2, 3) to Color(0x55354F2E)
        AC.DESERT -> art.byTerrain[5] to Color(0x33FFCC66)
        AC.COAST -> art.byTerrain[3] to Color(0x3342A5F5)
        else -> return
    }
    val img = list[h % list.size]
    drawImage(img, dstOffset = IntOffset((c.x - tw / 2f).toInt(), (c.y - th / 2f).toInt()), dstSize = IntSize(tw, th), filterQuality = FilterQuality.Medium)
    if (tint != Color.Transparent) drawPath(hexAt(c, u, 0.97f), tint)
}

private fun hexAt(c: Offset, u: Float, k: Float = 1f) = androidx.compose.ui.graphics.Path().apply {
    for (i in 0..5) {
        val a = Math.toRadians(60.0 * i - 90.0)
        val p = c + Offset((kotlin.math.cos(a) * u * k).toFloat(), (kotlin.math.sin(a) * u * k).toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/** Each building's painted piece, for the build buttons. */
private val BUILD_ART = mapOf(
    AC.PORT to com.puresudoku.R.drawable.rt_ag_port, AC.VILLAGE to com.puresudoku.R.drawable.rt_ag_village, AC.TOWN to com.puresudoku.R.drawable.rt_ag_town,
    AC.FARM to com.puresudoku.R.drawable.rt_ag_farm, AC.RANCH to com.puresudoku.R.drawable.rt_ag_ranch2, AC.FISHERY to com.puresudoku.R.drawable.rt_ag_fishery,
    AC.MILL to com.puresudoku.R.drawable.rt_ag_mill, AC.QUARRY to com.puresudoku.R.drawable.rt_ag_quarry, AC.MINE to com.puresudoku.R.drawable.rt_ag_mine,
    AC.GRANARY to com.puresudoku.R.drawable.rt_ag_granary, AC.MARKET to com.puresudoku.R.drawable.rt_ag_market, AC.BARRACKS to com.puresudoku.R.drawable.rt_ag_barracks,
    AC.TOWER to com.puresudoku.R.drawable.rt_ag_tower, AC.TEMPLE to com.puresudoku.R.drawable.rt_ag_temple, AC.COLOSSEUM to com.puresudoku.R.drawable.rt_ag_colosseum,
    AegStewardship.LIGHTHOUSE to com.puresudoku.R.drawable.rt_wonder_lighthouse,
    AegStewardship.LIBRARY to com.puresudoku.R.drawable.rt_wonder_library,
    AegStewardship.SKY_TEMPLE to com.puresudoku.R.drawable.rt_wonder_sky_temple,
    AegStewardship.GARDENS to com.puresudoku.R.drawable.rt_wonder_gardens,
    AegStewardship.CITADEL to com.puresudoku.R.drawable.rt_wonder_citadel,
    AegStewardship.PALACE to com.puresudoku.R.drawable.rt_wonder_palace,
)

/** A building: its painted piece, or an icon until the painting arrives. [flip] mirrors it (a fishery turns to face the sea). */
private fun DrawScope.buildingIcon(b: Int, c: Offset, s: Float, art: RealmArt, idle: Boolean, own: AegConquestArt, flip: Boolean = false) {
    own.pieces[b]?.let { img ->
        // fit the piece in the tile, wide ones by width and tall ones by height
        val big = if (b == AC.PORT || b == AC.TOWN || b == AC.FARM || b == AC.RANCH) 1.6f else 1.35f
        val k = s * big / maxOf(img.width, img.height); val w = img.width * k; val hh = img.height * k
        scale(if (flip) -1f else 1f, 1f, c) {
            drawImage(img, dstOffset = IntOffset((c.x - w / 2).toInt(), (c.y - hh * 0.58f).toInt()), dstSize = IntSize(w.toInt(), hh.toInt()), filterQuality = FilterQuality.Medium,
                alpha = if (idle) 0.55f else 1f)
        }
        if (idle) actionIcon(RI.END, c + Offset(s * 0.42f, s * 0.38f), s * 0.3f)
        return
    }
    drawCircle(Color.Black.copy(alpha = 0.3f), s * 0.56f, c + Offset(0f, s * 0.05f))
    drawCircle(if (b == AC.PORT || b == AC.TOWN) Color(0xFFF6E3A1) else Color(0xFFF8EED6), s * 0.52f, c)
    drawCircle(if (idle) Color(0xFF9E9E9E) else Color(0xFFC9972F), s * 0.52f, c, style = Stroke(s * 0.07f))
    fun res(r: Int) { val ic = art.icons[r]; val w = s * 0.62f; val h = w * ic.height / ic.width
        drawImage(ic, dstOffset = IntOffset((c.x - w / 2).toInt(), (c.y - h / 2).toInt()), dstSize = IntSize(w.toInt(), h.toInt()), filterQuality = FilterQuality.Medium) }
    when (b) {
        AC.FARM -> res(2); AC.RANCH -> res(3); AC.MILL -> res(0); AC.MINE -> res(1)
        else -> actionIcon(when (b) {
            AC.PORT -> RI.TRADE; AC.VILLAGE, AC.TOWN -> RI.VILLAGE; AC.FISHERY -> RI.LEY; AC.QUARRY -> RI.EARTH; AC.GRANARY -> RI.SCROLL
            AC.MARKET -> RI.TRADE; AC.BARRACKS -> RI.KNIGHT; AC.TOWER -> RI.TOWER; AC.TEMPLE -> RI.MOON; else -> RI.CROWN
        }, c, s * 0.62f)
    }
    if (b == AC.TOWN) actionIcon(RI.CROWN, c + Offset(s * 0.38f, -s * 0.38f), s * 0.34f)
    if (idle) drawCircle(Color.White.copy(alpha = 0.45f), s * 0.52f, c)
}

@Composable
fun AegConquestBoard(s: AegConquestSession, modifier: Modifier = Modifier, fitTop: Float = 0f, fitBottom: Float = 0f) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val art = rememberRealmArt(); val own = rememberAegConquestArt()
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "glow")
    var zoom by remember(s) { mutableFloatStateOf(1f) }
    // the camera starts on the port, in board units
    var cam by remember(s) { mutableStateOf(Offset(g.map.hexX[g.capital], g.map.hexY[g.capital] - 1.5f)) }
    BoxWithConstraints(modifier.clipToBounds()) {
        val wPx = with(LocalDensity.current) { maxWidth.toPx() }; val hPx = with(LocalDensity.current) { maxHeight.toPx() }
        val base = minOf(wPx, hPx) / 13f
        fun u() = base * zoom
        val mid = Offset(wPx / 2, fitTop + (hPx - fitTop - fitBottom) / 2)
        fun at(h: Int) = mid + (Offset(g.map.hexX[h], g.map.hexY[h]) - cam) * u()
        Canvas(Modifier.fillMaxSize().pointerInput(s) {
            detectTransformGestures { _, pan, z, _ ->
                zoom = (zoom * z).coerceIn(0.45f, 2.6f)
                cam -= pan / u()
                val e = g.map.extent
                cam = Offset(cam.x.coerceIn(-e, e), cam.y.coerceIn(-e, e))
            }
        }.pointerInput(s) {
            detectTapGestures { o ->
                val h = (0 until g.H).minBy { (at(it) - o).getDistance() }
                if ((at(h) - o).getDistance() < u() * 0.95f) s.tapTile(h)
            }
        }) {
            val u = u()
            val conn = g.connected()
            val st = g.staffed()
            val vis = (0 until g.H).filter { val p = at(it); p.x > -u * 2 && p.x < size.width + u * 2 && p.y > -u * 2 && p.y < size.height + u * 2 }
            // land, fog and sea
            for (h in vis) {
                val c = at(h)
                if (!g.revealed[h]) {
                    val f = art.fog[h % art.fog.size]; val tw = (sqrt3 * u).toInt() + 2; val th = (2 * u).toInt() + 2
                    drawImage(f, dstOffset = IntOffset((c.x - tw / 2f).toInt(), (c.y - th / 2f).toInt()), dstSize = IntSize(tw, th), filterQuality = FilterQuality.Low)
                } else if (g.isLand(h)) aegTile(g, h, c, u, art, own)
            }
            for (h in vis) if (g.revealed[h] && g.isLand(h)) {
                val c = at(h)
                if (conn[h]) drawPath(hexAt(c, u, 0.99f), YOU.copy(alpha = 0.14f))
                val rl = g.rivalLand[h]
                if (rl >= 0) drawPath(hexAt(c, u, 0.99f), RIVAL.copy(alpha = 0.26f))
                drawPath(hexAt(c, u), Color(0xFF4A3510), style = Stroke(u * 0.05f))
                drawPath(hexAt(c, u), Color(0xFFC9972F).copy(alpha = 0.8f), style = Stroke(u * 0.025f))
                if (conn[h]) drawPath(hexAt(c, u, 0.9f), YOU.copy(alpha = 0.7f), style = Stroke(u * 0.04f))
            }
            // roads, from tile to tile
            for (h in vis) for (n in g.map.hexNbrs[h]) if (n > h && g.road[g.side(h, n)]) {
                val a = at(h); val b = at(n)
                strategyRoad(art, a, b, u)
            }
            // ruins, buildings and rival capitals
            for (h in vis) {
                if (!g.revealed[h]) continue
                val c = at(h)
                if (g.ruins[h]) actionIcon(RI.RELIC, c + Offset(u * 0.45f, -u * 0.35f), u * 0.4f)
                val b = g.building[h]
                // the fishery's painting has the sea on its left: mirror it when the sea lies to the right
                val seaRight = b == AC.FISHERY && g.map.hexNbrs[h].filter { !g.isLand(it) }.let { s2 -> s2.isNotEmpty() && s2.sumOf { g.map.hexX[it].toDouble() } / s2.size > g.map.hexX[h] }
                if (b != AC.NONE) buildingIcon(b, c, u * 0.9f, art, AC.WORKERS[b] > 0 && !st[h], own, seaRight)
                for (r in g.rivals) if (r.capital == h) {
                    if (r.alive) {
                        val img = own.capital; val k = u * 1.75f / maxOf(img.width, img.height)
                        drawImage(img, dstOffset = IntOffset((c.x - img.width * k / 2).toInt(), (c.y - img.height * k * 0.6f).toInt()), dstSize = IntSize((img.width * k).toInt(), (img.height * k).toInt()), filterQuality = FilterQuality.Medium)
                    } else actionIcon(RI.CROWN, c, u * 0.5f)
                }
                // the tile's power, on a banner as in Veilguard: soldiers plus walls, yours in blue, a rival's in red
                val power = if (g.rivalLand[h] >= 0) g.rivalDefence(h) else if (conn[h]) g.defence(h) else 0
                if (power > 0 && (g.rivalLand[h] < 0 || g.revealed[h])) bannerStack(c + Offset(-u * 0.5f, u * 0.62f), u, if (g.rivalLand[h] >= 0) RIVAL else YOU, "$power")
                // workers at a building: how many of the crew are there
                val need = AC.WORKERS[b]
                if (need > 0) {
                    val wc = c + Offset(u * 0.5f, u * 0.5f)
                    drawCircle(Color(0xFF2A1A0A).copy(alpha = 0.85f), u * 0.22f, wc)
                    drawCircle(if (g.workers[h] >= need) Color(0xFF66BB6A) else Color(0xFFFFB74D), u * 0.22f, wc, style = Stroke(u * 0.035f))
                    glyph("${g.workers[h]}/$need", wc, u * 0.16f, Color.White)
                }
            }
            // what you can reach: a road into the tile you've selected
            val sel = s.selected
            if (sel >= 0) {
                val c = at(sel)
                drawPath(hexAt(c, u, 0.92f), Color(0xFFFFD54F), style = Stroke(u * 0.09f))
                if (!conn[sel] && g.map.hexNbrs[sel].any { conn[it] && sel in g.roadTargets(it, conn) })
                    drawPath(hexAt(c, u, 0.8f), Color.White.copy(alpha = pulse), style = Stroke(u * 0.06f))
            }
            val cf = s.coachFrom
            if (cf >= 0) drawCircle(Color(0xFF7CFF8A).copy(alpha = pulse), u * 0.7f, at(cf), style = Stroke(u * 0.08f))
        }
        com.puresudoku.ui.tutorial.LocalDemoTargets.current?.let { t ->
            com.puresudoku.ui.games.board.CellTargets(t, (0 until g.H).map { "h-$it" to Rect(at(it), u() * 0.45f) })
        }
    }
}

// =====================================================================  panels

private val RES_ICON = listOf(RI.SUN, RI.ROAD, RI.EARTH, RI.TOWER, RI.CROWN)

@Composable
private fun AegRes(r: Int, n: Int, modifier: Modifier = Modifier) {
    Column(modifier.parchment(PANEL).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (r) { AC.FOOD -> ResImg(2, 20.dp); AC.TIMBER -> ResImg(0, 20.dp); AC.ORE -> ResImg(1, 20.dp); AC.STONE -> RIcon(20.dp) { c, sz -> actionIcon(RI.EARTH, c, sz) }; else -> RIconOf(RES_ICON[r], 18.dp) }
            Spacer(Modifier.width(3.dp))
            InkText("$n", 16, bold = true)
        }
        InkText(AC.RES[r].replaceFirstChar { it.uppercase() }, 10, color = INK_SOFT)
    }
}

@Composable
private fun AegCost(cost: IntArray) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (r in 0..4) if (cost[r] > 0) {
            InkText("${cost[r]}", 11, bold = true)
            when (r) { AC.FOOD -> ResImg(2, 12.dp); AC.TIMBER -> ResImg(0, 12.dp); AC.ORE -> ResImg(1, 12.dp); AC.STONE -> RIcon(12.dp) { c, sz -> actionIcon(RI.EARTH, c, sz) }; else -> RIconOf(RES_ICON[r], 11.dp) }
        }
    }
}

@Composable
private fun AegButton(label: String, icon: RI, cost: IntArray?, enabled: Boolean, art: Int? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.demoTargetOf("b-" + label.substringBefore(' ').lowercase()).alpha(if (enabled) 1f else 0.45f).clip(shape).parchment(shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (art != null) {
            // a building: its picture, its name, then its cost
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(art), contentDescription = label, modifier = Modifier.height(40.dp).width(54.dp))
            InkText(label, 12, bold = true)
        } else Row(verticalAlignment = Alignment.CenterVertically) { RIconOf(icon, 18.dp); Spacer(Modifier.width(4.dp)); InkText(label, 13, bold = true) }
        if (cost != null && cost.any { it > 0 }) { Spacer(Modifier.height(1.dp)); AegCost(cost) }
    }
}

@Composable
private fun Modifier.demoTargetOf(key: String): Modifier = this.demoTarget(com.puresudoku.ui.tutorial.LocalDemoTargets.current, key)

private val BUILD_ICON = mapOf(
    AC.VILLAGE to RI.VILLAGE, AC.FARM to RI.SUN, AC.RANCH to RI.MOON, AC.FISHERY to RI.LEY, AC.MILL to RI.ROAD, AC.QUARRY to RI.EARTH,
    AC.MINE to RI.TOWER, AC.GRANARY to RI.SCROLL, AC.MARKET to RI.TRADE, AC.BARRACKS to RI.KNIGHT, AC.TOWER to RI.TOWER, AC.TEMPLE to RI.MOON, AC.COLOSSEUM to RI.CROWN,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AegConquestUi(s: AegConquestSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val full = targets == null
    var topH by remember { mutableIntStateOf(0) }
    var botH by remember { mutableIntStateOf(0) }
    var showStewardship by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    if (s.showFinalHistory) StewardshipHistoryDialog(g.sw, g.CARDS, g.won) { s.finishGame() }

    if (s.myMove && (showStewardship || g.sw.choice >= 0)) {
        StewardshipDialog(g.sw, g.CARDS, { showStewardship = false }, { s.act(CAct.Choose(it)) }, { kind, level -> s.act(CAct.Decree(kind, level)) })
    }

    Box(Modifier.fillMaxSize()) {
        AegConquestBoard(s, Modifier.fillMaxSize(), topH.toFloat(), botH.toFloat())
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topH = it.height }
                .then(if (full) Modifier.statusBarsPadding().padding(top = 54.dp) else Modifier).padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerBanner("${g.sw.people()}/${g.housing()}", "${g.sw.people()}", YOU, true, "${g.idle()} idle · ${g.soldiers} reserve · ${g.allSoldiers()} soldiers", Modifier.weight(1f))
                PlayerBanner("Mood ${g.happiness}", "${g.happiness}", if (g.happiness >= 60) Color(0xFF43A047) else if (g.happiness >= 30) GOLD else RIVAL, false,
                    "${g.rivalsLeft} of ${g.rivals.size} rivals left", Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.parchment(RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                RIconOf(if (g.winter) RI.MOON else RI.SUN, 18.dp); Spacer(Modifier.width(6.dp))
                val net = g.yearlyFood() - g.eats()
                InkText("${g.seasonName}, year ${g.year} · goal ${g.goal} people · food ${if (net >= 0) "+" else ""}$net a season", 12, bold = true)
            }
        }

        HeraldCard(s, Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { botH.toDp() } + 6.dp).padding(horizontal = 12.dp))

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { botH = it.height }
                .then(if (full) Modifier.navigationBarsPadding() else Modifier).padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).parchment(PANEL).padding(horizontal = 10.dp, vertical = 3.dp)) {
                InkText(s.events.lastOrNull() ?: "Your ship has landed. A new people begins…", 12, color = INK_SOFT, lines = 2)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (r in 0..4) AegRes(r, g.res[r], Modifier.weight(1f))
            }
            Spacer(Modifier.height(5.dp))
            PromptRibbon(s.prompt, s.heraldBusy)
            Spacer(Modifier.height(5.dp))
            if (s.myMove && s.selected >= 0 && s.selected == g.capital && g.canTrade()) { PortTrade(s); Spacer(Modifier.height(5.dp)) }
            val actionsScroll = rememberScrollState()
            if (s.myMove && (actionsScroll.canScrollForward || actionsScroll.canScrollBackward)) InkText("Swipe for more actions", 10, color = INK_SOFT)
            if (s.myMove) FlowRow(
                Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(actionsScroll).padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                AegButton("Stewardship", RI.SCROLL, null, true) { showStewardship = true }
                val h = s.selected
                val conn = g.connected()
                if (h >= 0 && g.revealed[h] && g.isLand(h)) {
                    for (t in g.buildable(h, conn)) AegButton(AC.NAME[t], BUILD_ICON[t] ?: RI.VILLAGE, AC.COST[t], g.can(AC.COST[t]), BUILD_ART[t]) { s.act(CAct.Build(h, t)) }
                    if (g.building[h] == AC.VILLAGE) AegButton("Town", RI.VILLAGE, AC.COST[AC.TOWN], g.can(AC.COST[AC.TOWN]), BUILD_ART[AC.TOWN]) { s.act(CAct.Upgrade(h)) }
                    // workers: you choose who works where
                    if (conn[h] && AC.WORKERS[g.building[h]] > 0) {
                        AegButton("Add worker ${g.workers[h]}/${AC.WORKERS[g.building[h]]}", RI.VILLAGE, null, g.legal(CAct.Assign(h, 1))) { s.act(CAct.Assign(h, 1)) }
                        AegButton("Remove worker", RI.END, null, g.legal(CAct.Assign(h, -1))) { s.act(CAct.Assign(h, -1)) }
                    }
                    if (g.building[h] == AC.BARRACKS || (g.count(AC.BARRACKS) > 0 && h == g.capital)) {
                        AegButton("Train soldier", RI.KNIGHT, AC.SOLDIER_COST, g.legal(CAct.Train(1))) { s.act(CAct.Train(1)) }
                    }
                    // soldiers: post them from the reserve onto any tile your roads reach
                    if (conn[h]) {
                        AegButton("Post soldier (${g.guard[h]})", RI.KNIGHT, null, g.soldiers > 0) { s.act(CAct.Guard(h, 1)) }
                        if (g.guard[h] > 0) AegButton("Recall soldier", RI.END, null, true) { s.act(CAct.Guard(h, -1)) }
                    }
                    if (!conn[h] && g.rivalLand[h] < 0) {
                        val a = g.map.hexNbrs[h].firstOrNull { conn[it] && h in g.roadTargets(it, conn) }
                        if (a != null) AegButton("Road", RI.ROAD, g.roadCost(h), g.can(g.roadCost(h))) { s.roadInto(h) }
                    }
                    if (conn[h] && g.scoutable(h)) AegButton("Scout", RI.LEY, AC.SCOUT_COST, g.can(AC.SCOUT_COST)) { s.act(CAct.Scout(h)) }
                    val r = g.rivalLand[h]
                    if (r >= 0) g.attackFrom(h, conn).takeIf { it >= 0 }?.let { from ->
                        AegButton("Attack: ${g.guard[from]} vs ${g.rivalDefence(h)}", RI.KNIGHT, null, true) { s.act(CAct.Attack(from, h)) }
                    }
                }
                if (h >= 0 && g.sw.canDemolish(h, conn)) AegButton("Demolish", RI.END, null, true) { s.act(CAct.Demolish(h)) }
                if (h >= 0 && conn[h] && g.building[h] == AC.FARM) AegButton(if (g.sw.fallow[h]) "Work farm" else "Rest farm", RI.SUN, null, true) { s.act(CAct.Fallow(h)) }
                AegButton("End season", RI.END, null, true) { s.selected = -1; s.act(CAct.End) }
            } else Spacer(Modifier.height(44.dp))
            Spacer(Modifier.height(if (full) 10.dp else 4.dp))
        }
    }
}

/** The port's market: pick a good to give ([AC.TRADE_RATE] of it), then a good to get (1). */
@Composable
private fun PortTrade(s: AegConquestSession) {
    val g = s.g
    var give by remember { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).parchment(PANEL).padding(horizontal = 10.dp, vertical = 5.dp)) {
        InkText(if (give < 0) "Port trade: give ${g.tradeRate()} of…" else "…for 1 of:", 12, bold = true)
        Spacer(Modifier.height(3.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0..4) {
                val on = if (give < 0) g.res[r] >= g.tradeRate() else true
                val shape = RoundedCornerShape(10.dp)
                Box(Modifier.weight(1f).demoTargetOf("t-$r").alpha(if (on) 1f else 0.4f).clip(shape)
                    .then(Modifier.background(if (r == give) GOLD.copy(alpha = 0.35f) else Color.Transparent, shape))
                    .then(if (on) Modifier.clickable {
                        if (give < 0) give = r else if (r == give) give = -1 else { s.act(CAct.Trade(give, r)); if (g.res[give] < g.tradeRate()) give = -1 }
                    } else Modifier).padding(vertical = 3.dp), contentAlignment = Alignment.Center) { AegRes(r, g.res[r]) }
            }
        }
        if (give >= 0) InkText("Tap ${AC.RES[give]} again to cancel", 11, color = INK_SOFT)
    }
}

// =====================================================================  how to play

internal fun aegConquestTutorial(): List<TutorialStep> {
    fun fresh() = AegConquestSession(Difficulty.EASY, AegConquest.newGame(Difficulty.EASY, Random(42)))
    suspend fun com.puresudoku.ui.tutorial.DemoScope.play(s: AegConquestSession, moves: Int) {
        val rng = Random(3)
        var n = 0
        while (n < moves) {
            var t = 0L; while (!s.myMove && t < 12000) { wait(120); t += 120 }
            if (!s.myMove) return
            val a = AegConquestAi.next(s.g, rng)
            when (a) {
                is CAct.Build -> { tap("h-${a.h}") { s.selected = a.h }; tap("b-" + AC.NAME[a.type].substringBefore(' ').lowercase()) { s.act(a) } }
                is CAct.Road -> { tap("h-${a.b}") { s.selected = a.b }; tap("b-road") { s.act(a) } }
                is CAct.Upgrade -> { tap("h-${a.h}") { s.selected = a.h }; tap("b-town") { s.act(a) } }
                is CAct.Scout -> { tap("h-${a.h}") { s.selected = a.h }; tap("b-scout") { s.act(a) } }
                is CAct.Assign -> { tap("h-${a.h}") { s.selected = a.h }; tap(if (a.d > 0) "b-add" else "b-remove") { s.act(a) } }
                is CAct.Guard -> { tap("h-${a.h}") { s.selected = a.h }; tap(if (a.d > 0) "b-post" else "b-recall") { s.act(a) } }
                is CAct.Trade -> { tap("h-${s.g.capital}") { s.selected = s.g.capital }; tap("t-${a.give}") { }; tap("t-${a.get}") { s.act(a) } }
                is CAct.Attack -> { tap("h-${a.to}") { s.selected = a.to }; tap("b-attack") { s.act(a) } }
                is CAct.Train -> { val b = (0 until s.g.H).first { s.g.building[it] == AC.BARRACKS }; tap("h-$b") { s.selected = b }; repeat(a.n) { tap("b-train") { s.act(CAct.Train(1)) } } }
                else -> { tap("b-end") { s.selected = -1; s.act(CAct.End) } }
            }
            n++; wait(450)
        }
    }
    return listOf(
        TutorialStep("Roads and buildings", "Lay roads out from your port to reach new tiles. Each tile you reach can hold one building: farms, mills, villages and more.") { active ->
            var s by remember { mutableStateOf(fresh()) }
            DemoStage(active, script = { s = fresh(); wait(600); play(s, 7); wait(1500) }) { t -> AegConquestUi(s, t) }
        },
        TutorialStep("Workers, soldiers and battle", "Staff each building yourself, post soldiers on the tiles you need to hold, and take rival land tile by tile. The banners show each tile's power.") { active ->
            var s by remember { mutableStateOf(fresh()) }
            DemoStage(active, script = { s = fresh(); wait(600); play(s, 14); wait(1500) }) { t -> AegConquestUi(s, t) }
        },
    )
}

/** A banner on a pole, in the owner's colour, with the tile's power (as in Veilguard). */
private fun DrawScope.bannerStack(at: Offset, u: Float, color: Color, label: String) {
    val pole = u * 0.42f
    drawLine(Color(0xFF3A2A18), at, at - Offset(0f, pole), u * 0.04f, androidx.compose.ui.graphics.StrokeCap.Round)
    val top = at - Offset(0f, pole)
    val flag = androidx.compose.ui.graphics.Path().apply {
        moveTo(top.x, top.y); lineTo(top.x + u * 0.3f, top.y + u * 0.02f); lineTo(top.x + u * 0.22f, top.y + u * 0.11f)
        lineTo(top.x + u * 0.3f, top.y + u * 0.2f); lineTo(top.x, top.y + u * 0.2f); close()
    }
    drawPath(flag, Color.Black.copy(alpha = 0.35f), style = Stroke(u * 0.05f)); drawPath(flag, color)
    val b = at + Offset(u * 0.2f, -u * 0.04f)
    val w = u * (0.12f + 0.09f * label.length)
    drawRoundRect(Color(0xFF1B1430).copy(alpha = 0.88f), b - Offset(w / 2, u * 0.13f), androidx.compose.ui.geometry.Size(w, u * 0.26f), androidx.compose.ui.geometry.CornerRadius(u * 0.13f))
    glyph(label, b, u * 0.18f, Color.White)
}
