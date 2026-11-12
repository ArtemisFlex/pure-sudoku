package com.puresudoku.ui.games.realm

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import com.puresudoku.core.board.AAct
import com.puresudoku.core.board.Aegwaspell
import com.puresudoku.core.board.AegwaspellAi
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

private typealias A = Aegwaspell

// =====================================================================  session

class AegwaspellSession(difficulty: Difficulty, val g: Aegwaspell) : DuelSession(GameKind.AEGWASPELL, difficulty) {
    var selected by mutableIntStateOf(-1)
    var events by mutableStateOf<List<String>>(emptyList()); private set

    override val middleStat get() = MiddleStat.Counter("People", "${g.pop}/${g.goal}")
    override val progress: Float get() =
        ((g.pop.toFloat() / g.goal).coerceAtMost(1f) * 0.5f + (g.rivals.count { !it.alive }.toFloat() / g.rivals.size.coerceAtLeast(1)) * 0.5f).coerceAtMost(0.95f)
    override val wonTitle get() = "Your civilization endures!"
    override val lostTitle get() = "Your people are gone"
    override val lostMessage get() = "Your civilization lasted until ${A.SEASON[(g.season) % 4].lowercase()} of year ${g.year}. This game's save slot has been cleared."

    val myMove get() = outcome == Outcome.PLAYING && !heraldBusy && !g.over

    private fun flush() { if (g.log.isNotEmpty()) { events = (events + g.log).takeLast(5); g.log.clear() } }

    fun act(a: AAct): Boolean {
        if (!myMove) return false
        if (!g.legal(a)) { nudge("You can't do that right now"); return false }
        coachTip = null; coachFrom = -1
        val before = g.season
        g.apply(a, rng)
        val logs = g.log.toList(); flush(); bump(); changed()
        if (a is AAct.Attack) announce(if (logs.any { "took the capital" in it }) "Victory!" else "Your army was thrown back", logs.joinToString("\n"), 2600, true, RI.KNIGHT)
        if (a == AAct.End && g.season != before) seasonCards(logs)
        if (logs.any { it.startsWith("Ruins!") }) announce("Ruins!", logs.first { it.startsWith("Ruins!") }.removePrefix("Ruins! "), 2000, true, RI.RELIC)
        if (g.over) whenQuiet { finishGame() }
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
        if (rest.isNotEmpty()) announce("${A.SEASON[(g.season + 3) % 4]} ends", rest.joinToString("\n"), 2200, true, RI.CROWN)
        announce("${g.seasonName}, year ${g.year}", "${g.pop} people · ${g.res[A.FOOD]} food · happiness ${g.happiness}", 1300, true, RI.SUN)
    }

    private fun finishGame() {
        if (g.won) win(listOf(BonusLine("People", g.pop * rules.basePoints / 2), BonusLine("Rivals felled", g.rivals.size * rules.basePoints * 3),
            BonusLine("Years", maxOf(0, 40 - g.year) * rules.basePoints / 4)))
        else lose()
    }

    fun tapTile(h: Int) { if (myMove) { selected = if (selected == h) -1 else h; bump() } }

    /** A road into [b] from whichever of your connected neighbours is nearest the port. */
    fun roadInto(b: Int) {
        val conn = g.connected()
        val a = g.map.hexNbrs[b].filter { conn[it] && b in g.roadTargets(it, conn) }.minByOrNull { g.hexDist(it, g.capital) } ?: run { nudge("Your roads don't reach here yet"); return }
        act(AAct.Road(a, b))
    }

    val prompt: String get() = when {
        outcome != Outcome.PLAYING -> ""
        heraldBusy -> "The season's news · tap a card to skip"
        selected < 0 -> "Tap a tile: build on your roads, or lay a road out into new land"
        else -> tileText(selected)
    }

    fun tileText(h: Int): String {
        if (!g.revealed[h]) return "Unknown land, hidden in fog"
        if (!g.isLand(h)) return "The sea"
        val conn = g.connected()
        val b = g.building[h]
        val t = A.TERRAIN[g.terrain[h]].replaceFirstChar { it.uppercase() }
        return when {
            g.rivalLand[h] >= 0 -> { val r = g.rivals[g.rivalLand[h]]; "${r.name.replaceFirstChar { it.uppercase() }}: strength ${r.strength}" }
            b != A.NONE -> "$t · ${A.NAME[b]}" + if (A.WORKERS[b] > 0) (if (g.staffed()[h]) " · working" else " · idle: needs ${A.WORKERS[b]} workers") else ""
            conn[h] -> "$t · on your roads: build here"
            else -> "$t · lay a road here to reach it" + if (g.ruins[h]) " · ruins!" else ""
        }
    }

    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (!myMove) return null
        val snap = g.copy()
        return {
            when (val a = AegwaspellAi.next(snap, Random(7))) {
                is AAct.Build -> Triple("Build a ${A.NAME[a.type].lowercase()} on the ringed tile.", a.h, -1)
                is AAct.Road -> Triple("Lay a road into the ringed tile.", a.b, -1)
                is AAct.Upgrade -> Triple("Grow the ringed village into a town.", a.h, -1)
                is AAct.Train -> Triple("Train ${a.n} soldier${if (a.n > 1) "s" else ""} at your barracks.", -1, -1)
                is AAct.Scout -> Triple("Send scouts from the ringed tile.", a.h, -1)
                is AAct.Attack -> Triple("March on ${snap.rivals[a.r].name}: your army is strong enough.", snap.rivals[a.r].capital, -1)
                AAct.End -> Triple("Nothing more worth doing: end the season.", -1, -1)
            }
        }
    }

    override fun resume() { if (outcome == Outcome.PLAYING && g.over) finishGame() }

    override fun saveState(): JSONObject = JSONObject().put("s", JSONArray().also { a -> g.state().forEach { a.put(it) } }).put("moves", moves)

    companion object {
        fun restore(o: JSONObject): AegwaspellSession {
            val st = o.getJSONObject("state").getJSONArray("s")
            val g = Aegwaspell.fromState(IntArray(st.length()) { st.getInt(it) })
            return AegwaspellSession(Difficulty.valueOf(o.getString("difficulty")), g).also { it.moves = o.getJSONObject("state").optInt("moves"); it.restoreCommon(o) }
        }
    }
}

// =====================================================================  definition

object AegwaspellDef : GameDef {
    override val kind = GameKind.AEGWASPELL
    override val fullBleed: Boolean get() = true
    override val hasHeader: Boolean get() = true
    @Composable override fun Header(session: Session, actions: com.puresudoku.ui.games.HeaderActions) = RealmHeader(session, actions, "Aegwaspell · ")

    override fun generate(d: Difficulty): Session = AegwaspellSession(d, Aegwaspell.newGame(d, Random(System.nanoTime())))
    override fun restore(o: JSONObject): Session = AegwaspellSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Grow a civilization from a single port. Build one thing on each tile your roads reach, feed and house your people, and survive the seasons' dice and cards. Win with a great people once every rival capital has fallen."
    override fun background(session: Session) = com.puresudoku.R.drawable.bg_realm_ocean
    override fun levelInfo(d: Difficulty): String {
        val r = A.RADIUS[d.ordinal]
        return "${3 * r * (r + 1) + 1} tiles · ${A.RIVALS[d.ordinal]} rival civilizations · ${A.POP_GOAL[d.ordinal]} people to win"
    }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A small world and two slow rivals."
        Difficulty.MEDIUM -> "Rivals grow a little faster."
        Difficulty.HARD -> "A wider world with three rivals."
        Difficulty.EXPERT -> "Three rivals that grow quickly."
        Difficulty.MASTER -> "A vast world and four rivals."
        Difficulty.EXTREME -> "Four strong rivals and a great people to raise."
    }
    @Composable override fun Play(session: Session) = AegwaspellUi(session as AegwaspellSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override fun lesson(): Pair<Session, List<LessonStep>> {
        val s = AegwaspellSession(Difficulty.EASY, Aegwaspell.newGame(Difficulty.EASY, Random(777)))
        return s to listOf(
            LessonStep("Aegwaspell is a civilization game against the world itself. You begin with a port and a few people at the foot of a vast, fogged land."),
            LessonStep("Each tile can hold one building, and you can only build on tiles your roads reach. Tap a tile beside your port, then Road to reach it."),
            LessonStep("Farms on fields, ranches on meadows and fisheries on the coast feed your people: every person eats 1 food a season. In winter the farms lie fallow, so store food in granaries."),
            LessonStep("Villages and towns house your people. Buildings need workers: an idle one makes nothing. People are born when there's food to spare, room to live and a good mood."),
            LessonStep("Each season ends with the harvest dice (a low roll is a poor year, a high roll a bountiful one) and an event card: migrants, plague, bandits, raids, festivals and more."),
            LessonStep("Temples and the colosseum keep people happy. Unhappy people leave, and very unhappy people revolt."),
            LessonStep("Rival civilizations wait beyond the fog, and grow stronger each year. Build barracks, train soldiers, lay roads to their lands, and march on their capitals."),
            LessonStep("Win with ${A.POP_GOAL[0]} people (on Easy) once every rival capital has fallen. Lose if your last person dies. Good luck!"),
        )
    }

    override val tutorial: List<TutorialStep> get() = aegwaspellTutorial()
}

// =====================================================================  board

/** Terrain paintings, until Aegwaspell's own arrive: the Eiseria tiles, some tinted. */
/** Aegwaspell's own terrain paintings, by terrain (null: not painted yet, so an Eiseria tile stands in). */
internal class AegArt(val byTerrain: Map<Int, androidx.compose.ui.graphics.ImageBitmap>, val pieces: Map<Int, androidx.compose.ui.graphics.ImageBitmap>, val capital: androidx.compose.ui.graphics.ImageBitmap)

@Composable
private fun rememberAegArt(): AegArt {
    @Composable fun img(id: Int) = androidx.compose.ui.graphics.ImageBitmap.imageResource(id)
    return AegArt(mapOf(
        A.SWAMP to img(com.puresudoku.R.drawable.rt_ag_swamp),
        A.COAST to img(com.puresudoku.R.drawable.rt_ag_coast),
        A.PLAINS to img(com.puresudoku.R.drawable.rt_ag_plains),
        A.DESERT to img(com.puresudoku.R.drawable.rt_ag_desert),
        A.HILLS to img(com.puresudoku.R.drawable.rt_ag_hills),
    ), mapOf(
        A.PORT to img(com.puresudoku.R.drawable.rt_ag_port),
        A.VILLAGE to img(com.puresudoku.R.drawable.rt_ag_village),
        A.TOWN to img(com.puresudoku.R.drawable.rt_ag_town),
        A.FARM to img(com.puresudoku.R.drawable.rt_ag_farm),
        A.RANCH to img(com.puresudoku.R.drawable.rt_ag_ranch2),
        A.FISHERY to img(com.puresudoku.R.drawable.rt_ag_fishery),
        A.MILL to img(com.puresudoku.R.drawable.rt_ag_mill),
        A.QUARRY to img(com.puresudoku.R.drawable.rt_ag_quarry),
        A.MINE to img(com.puresudoku.R.drawable.rt_ag_mine),
        A.GRANARY to img(com.puresudoku.R.drawable.rt_ag_granary),
        A.MARKET to img(com.puresudoku.R.drawable.rt_ag_market),
        A.BARRACKS to img(com.puresudoku.R.drawable.rt_ag_barracks),
        A.TOWER to img(com.puresudoku.R.drawable.rt_ag_tower),
        A.TEMPLE to img(com.puresudoku.R.drawable.rt_ag_temple),
        A.COLOSSEUM to img(com.puresudoku.R.drawable.rt_ag_colosseum),
    ), img(com.puresudoku.R.drawable.rt_ag_capital))
}

private fun DrawScope.aegTile(g: Aegwaspell, h: Int, c: Offset, u: Float, art: RealmArt, own: AegArt) {
    val tw = (sqrt3 * u).toInt() + 2; val th = (2 * u).toInt() + 2
    val t = g.terrain[h]
    own.byTerrain[t]?.let { drawImage(it, dstOffset = IntOffset((c.x - tw / 2f).toInt(), (c.y - th / 2f).toInt()), dstSize = IntSize(tw, th), filterQuality = FilterQuality.Medium); return }
    val (list, tint) = when (t) {
        A.PLAINS -> art.byTerrain[3] to Color(0x22FFE082)
        A.FIELDS -> art.byTerrain[2] to Color.Transparent
        A.MEADOW -> art.byTerrain[3] to Color.Transparent
        A.FOREST -> art.byTerrain[0] to Color.Transparent
        A.HILLS -> art.byTerrain[1].subList(1, 2) to Color(0x2266BB6A)
        A.MOUNTAIN -> art.byTerrain[1].filterIndexed { i, _ -> i != 1 && i != 2 } to Color.Transparent
        A.SWAMP -> art.byTerrain[0].subList(2, 3) to Color(0x55354F2E)
        A.DESERT -> art.byTerrain[5] to Color(0x33FFCC66)
        A.COAST -> art.byTerrain[3] to Color(0x3342A5F5)
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

/** A building: its painted piece, or an icon until the painting arrives. */
private fun DrawScope.buildingIcon(b: Int, c: Offset, s: Float, art: RealmArt, idle: Boolean, own: AegArt) {
    own.pieces[b]?.let { img ->
        // fit the piece in the tile, wide ones by width and tall ones by height
        val big = if (b == A.PORT || b == A.TOWN || b == A.FARM || b == A.RANCH) 1.6f else 1.35f
        val k = s * big / maxOf(img.width, img.height); val w = img.width * k; val hh = img.height * k
        drawImage(img, dstOffset = IntOffset((c.x - w / 2).toInt(), (c.y - hh * 0.58f).toInt()), dstSize = IntSize(w.toInt(), hh.toInt()), filterQuality = FilterQuality.Medium,
            alpha = if (idle) 0.55f else 1f)
        if (idle) actionIcon(RI.END, c + Offset(s * 0.42f, s * 0.38f), s * 0.3f)
        return
    }
    drawCircle(Color.Black.copy(alpha = 0.3f), s * 0.56f, c + Offset(0f, s * 0.05f))
    drawCircle(if (b == A.PORT || b == A.TOWN) Color(0xFFF6E3A1) else Color(0xFFF8EED6), s * 0.52f, c)
    drawCircle(if (idle) Color(0xFF9E9E9E) else Color(0xFFC9972F), s * 0.52f, c, style = Stroke(s * 0.07f))
    fun res(r: Int) { val ic = art.icons[r]; val w = s * 0.62f; val h = w * ic.height / ic.width
        drawImage(ic, dstOffset = IntOffset((c.x - w / 2).toInt(), (c.y - h / 2).toInt()), dstSize = IntSize(w.toInt(), h.toInt()), filterQuality = FilterQuality.Medium) }
    when (b) {
        A.FARM -> res(2); A.RANCH -> res(3); A.MILL -> res(0); A.MINE -> res(1)
        else -> actionIcon(when (b) {
            A.PORT -> RI.TRADE; A.VILLAGE, A.TOWN -> RI.VILLAGE; A.FISHERY -> RI.LEY; A.QUARRY -> RI.EARTH; A.GRANARY -> RI.SCROLL
            A.MARKET -> RI.TRADE; A.BARRACKS -> RI.KNIGHT; A.TOWER -> RI.TOWER; A.TEMPLE -> RI.MOON; else -> RI.CROWN
        }, c, s * 0.62f)
    }
    if (b == A.TOWN) actionIcon(RI.CROWN, c + Offset(s * 0.38f, -s * 0.38f), s * 0.34f)
    if (idle) drawCircle(Color.White.copy(alpha = 0.45f), s * 0.52f, c)
}

@Composable
fun AegwaspellBoard(s: AegwaspellSession, modifier: Modifier = Modifier, fitTop: Float = 0f, fitBottom: Float = 0f) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val art = rememberRealmArt(); val own = rememberAegArt()
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
                drawLine(Color(0xFF3A2410), a, b, u * 0.2f, StrokeCap.Round)
                drawLine(Color(0xFFC9A46A), a, b, u * 0.13f, StrokeCap.Round)
            }
            // ruins, buildings and rival capitals
            for (h in vis) {
                if (!g.revealed[h]) continue
                val c = at(h)
                if (g.ruins[h]) actionIcon(RI.RELIC, c + Offset(u * 0.45f, -u * 0.35f), u * 0.4f)
                val b = g.building[h]
                if (b != A.NONE) buildingIcon(b, c, u * 0.9f, art, A.WORKERS[b] > 0 && !st[h], own)
                for (r in g.rivals) if (r.capital == h) {
                    if (r.alive) {
                        val img = own.capital; val k = u * 1.75f / maxOf(img.width, img.height)
                        drawImage(img, dstOffset = IntOffset((c.x - img.width * k / 2).toInt(), (c.y - img.height * k * 0.6f).toInt()), dstSize = IntSize((img.width * k).toInt(), (img.height * k).toInt()), filterQuality = FilterQuality.Medium)
                        val bc = c + Offset(0f, u * 0.62f)
                        drawCircle(Color(0xFF3A0A0A), u * 0.24f, bc); drawCircle(RIVAL, u * 0.24f, bc, style = Stroke(u * 0.04f))
                        glyph("${r.strength}", bc, u * 0.24f, Color.White)
                    } else actionIcon(RI.CROWN, c, u * 0.5f)
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
            when (r) { A.FOOD -> ResImg(2, 20.dp); A.TIMBER -> ResImg(0, 20.dp); A.ORE -> ResImg(1, 20.dp); else -> RIconOf(RES_ICON[r], 18.dp) }
            Spacer(Modifier.width(3.dp))
            InkText("$n", 16, bold = true)
        }
        InkText(A.RES[r].replaceFirstChar { it.uppercase() }, 10, color = INK_SOFT)
    }
}

@Composable
private fun AegCost(cost: IntArray) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (r in 0..4) if (cost[r] > 0) {
            InkText("${cost[r]}", 11, bold = true)
            when (r) { A.FOOD -> ResImg(2, 12.dp); A.TIMBER -> ResImg(0, 12.dp); A.ORE -> ResImg(1, 12.dp); else -> RIconOf(RES_ICON[r], 11.dp) }
        }
    }
}

@Composable
private fun AegButton(label: String, icon: RI, cost: IntArray?, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.demoTargetOf("b-" + label.substringBefore(' ').lowercase()).alpha(if (enabled) 1f else 0.45f).clip(shape).parchment(shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { RIconOf(icon, 18.dp); Spacer(Modifier.width(4.dp)); InkText(label, 13, bold = true) }
        if (cost != null && cost.any { it > 0 }) { Spacer(Modifier.height(1.dp)); AegCost(cost) }
    }
}

@Composable
private fun Modifier.demoTargetOf(key: String): Modifier = this.demoTarget(com.puresudoku.ui.tutorial.LocalDemoTargets.current, key)

private val BUILD_ICON = mapOf(
    A.VILLAGE to RI.VILLAGE, A.FARM to RI.SUN, A.RANCH to RI.MOON, A.FISHERY to RI.LEY, A.MILL to RI.ROAD, A.QUARRY to RI.EARTH,
    A.MINE to RI.TOWER, A.GRANARY to RI.SCROLL, A.MARKET to RI.TRADE, A.BARRACKS to RI.KNIGHT, A.TOWER to RI.TOWER, A.TEMPLE to RI.MOON, A.COLOSSEUM to RI.CROWN,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AegwaspellUi(s: AegwaspellSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val full = targets == null
    var topH by remember { mutableIntStateOf(0) }
    var botH by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        AegwaspellBoard(s, Modifier.fillMaxSize(), topH.toFloat(), botH.toFloat())
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topH = it.height }
                .then(if (full) Modifier.statusBarsPadding().padding(top = 54.dp) else Modifier).padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerBanner("${g.pop}/${g.housing()}", "${g.pop}", YOU, true, "people · ${g.soldiers} soldiers", Modifier.weight(1f))
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
            if (s.myMove) FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val h = s.selected
                val conn = g.connected()
                if (h >= 0 && g.revealed[h] && g.isLand(h)) {
                    for (t in g.buildable(h, conn)) AegButton(A.NAME[t], BUILD_ICON[t] ?: RI.VILLAGE, A.COST[t], g.can(A.COST[t])) { s.act(AAct.Build(h, t)) }
                    if (g.building[h] == A.VILLAGE) AegButton("Town", RI.VILLAGE, A.COST[A.TOWN], g.can(A.COST[A.TOWN])) { s.act(AAct.Upgrade(h)) }
                    if (g.building[h] == A.BARRACKS || (g.count(A.BARRACKS) > 0 && h == g.capital)) {
                        val room = g.soldierCap() - g.soldiers
                        AegButton("Train soldier", RI.KNIGHT, A.SOLDIER_COST, room > 0 && g.legal(AAct.Train(1))) { s.act(AAct.Train(1)) }
                    }
                    if (!conn[h] && g.rivalLand[h] < 0) {
                        val a = g.map.hexNbrs[h].firstOrNull { conn[it] && h in g.roadTargets(it, conn) }
                        if (a != null) AegButton("Road", RI.ROAD, g.roadCost(h), g.can(g.roadCost(h))) { s.roadInto(h) }
                    }
                    if (conn[h] && g.scoutable(h)) AegButton("Scout", RI.LEY, A.SCOUT_COST, g.can(A.SCOUT_COST)) { s.act(AAct.Scout(h)) }
                    val r = g.rivalLand[h]
                    if (r >= 0 && r in g.attackable(conn)) AegButton("Attack with ${g.soldiers}", RI.KNIGHT, null, g.soldiers > 0) { s.act(AAct.Attack(r)) }
                }
                AegButton("End season", RI.END, null, true) { s.selected = -1; s.act(AAct.End) }
            } else Spacer(Modifier.height(44.dp))
            Spacer(Modifier.height(if (full) 10.dp else 4.dp))
        }
    }
}

// =====================================================================  how to play

internal fun aegwaspellTutorial(): List<TutorialStep> {
    fun fresh() = AegwaspellSession(Difficulty.EASY, Aegwaspell.newGame(Difficulty.EASY, Random(42)))
    suspend fun com.puresudoku.ui.tutorial.DemoScope.play(s: AegwaspellSession, moves: Int) {
        val rng = Random(3)
        var n = 0
        while (n < moves) {
            var t = 0L; while (!s.myMove && t < 12000) { wait(120); t += 120 }
            if (!s.myMove) return
            val a = AegwaspellAi.next(s.g, rng)
            when (a) {
                is AAct.Build -> { tap("h-${a.h}") { s.selected = a.h }; tap("b-" + A.NAME[a.type].substringBefore(' ').lowercase()) { s.act(a) } }
                is AAct.Road -> { tap("h-${a.b}") { s.selected = a.b }; tap("b-road") { s.act(a) } }
                is AAct.Upgrade -> { tap("h-${a.h}") { s.selected = a.h }; tap("b-town") { s.act(a) } }
                is AAct.Scout -> { tap("h-${a.h}") { s.selected = a.h }; tap("b-scout") { s.act(a) } }
                else -> { tap("b-end") { s.selected = -1; s.act(AAct.End) } }
            }
            n++; wait(450)
        }
    }
    return listOf(
        TutorialStep("Roads and buildings", "Lay roads out from your port to reach new tiles. Each tile you reach can hold one building: farms, mills, villages and more.") { active ->
            var s by remember { mutableStateOf(fresh()) }
            DemoStage(active, script = { s = fresh(); wait(600); play(s, 7); wait(1500) }) { t -> AegwaspellUi(s, t) }
        },
        TutorialStep("Seasons, dice and cards", "End each season with the harvest dice and an event card. Feed and house your people, keep them happy, and grow strong enough to take the rival capitals.") { active ->
            var s by remember { mutableStateOf(fresh()) }
            DemoStage(active, script = { s = fresh(); wait(600); play(s, 14); wait(1500) }) { t -> AegwaspellUi(s, t) }
        },
    )
}
