package com.puresudoku.ui.games.city

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Straight
import androidx.compose.material.icons.rounded.TurnLeft
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.UTurnLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.city.City
import com.puresudoku.core.city.StreetSmarts
import com.puresudoku.core.city.StreetSmarts.Help
import com.puresudoku.core.city.StreetSmarts.StopKind
import com.puresudoku.core.city.StreetSmarts.TurnDir
import com.puresudoku.platform.nowMillis
import com.puresudoku.platform.onMainLater
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.MiddleStat
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.tutorial.TutorialStep
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// =====================================================================  the session

/** The car gliding along one block, for the map to draw. */
class Glide(val from: Int, val to: Int, val start: Long, val driveMs: Long, val waitMs: Long)

class StreetSmartsSession(difficulty: Difficulty, val g: StreetSmarts) : Session(GameKind.STREET_SMARTS, difficulty) {
    var version by mutableIntStateOf(0); private set
    var glide by mutableStateOf<Glide?>(null); private set
    /** Destination pin bought as a hint for the current stop. */
    var pinFor by mutableIntStateOf(-1)
    /** Route line bought as a hint, shown until this time. */
    var routeUntil by mutableLongStateOf(0L)
    /** The turn bought as a hint at the current corner: edge id. */
    var hintEdge by mutableIntStateOf(-1)
    /** The leg's turn list, fixed when the stop became current (help level "Turn list"). */
    var legSteps by mutableStateOf<List<StreetSmarts.Step>>(emptyList()); private set
    /** The stop whose point-the-way challenge has been answered, and the true direction shown after. */
    var pointedFor by mutableIntStateOf(-1)
    var pointReveal by mutableIntStateOf(-1)

    override val usesStrikes = false
    override val middleStat: MiddleStat get() { version; return MiddleStat.Counter("Coins", "${g.coins}") }
    override val progress: Float get() = (g.deliveries % 6) / 6f
    override val wonTitle = "Shift complete"

    val busy get() = glide != null


    fun bump() { version++; score = g.earned; changed() }

    /** A new stop is current: fix its turn list and clear the hints. */
    /** The route the GPS is following for this leg. */
    var planned by mutableStateOf<List<Int>>(emptyList()); private set
    /** Different ways to the next stop, offered before you set off (full GPS and turn list). */
    var alts by mutableStateOf<List<List<Int>>>(emptyList()); private set
    var altPicked by mutableIntStateOf(0); private set

    private fun startLeg() {
        val s = g.current
        pinFor = -1; hintEdge = -1; routeUntil = 0; pointReveal = -1
        if (s == null) { planned = emptyList(); alts = emptyList(); legSteps = emptyList(); return }
        val target = g.city.places[s.place].edge
        alts = if (s.help <= Help.TURNS.ordinal) g.alternatives(g.at, target) else emptyList()
        altPicked = 0
        planned = alts.firstOrNull() ?: g.route(g.at, target)
        legSteps = g.steps(planned, g.at)
    }

    /** Choose one of the offered routes. */
    fun pickRoute(i: Int) {
        val r = alts.getOrNull(i) ?: return
        altPicked = i; planned = r; legSteps = g.steps(r, g.at); bump()
    }

    fun accept() {
        if (busy) return
        val o = g.offer ?: return
        g.accept()
        startLeg()
        val first = g.city.places[o.stops.first().place]
        announce(if (o.stops.first().kind == StopKind.HOME) "Shift over: head home" else "Order accepted",
            if (o.stops.first().kind == StopKind.HOME) "Find your way back home. Help: ${Help.entries[o.stops.first().help].label}."
            else "Pick up at ${first.name}, then ${o.stops.size - 1} drop${if (o.stops.size > 2) "s" else ""}.", 1800, mine = true)
        bump()
    }

    fun needsPoint(): Boolean {
        val s = g.current ?: return false
        return g.order != null && s.help >= 2 && pointedFor != s.place
    }

    fun point(dir: Int) {
        val s = g.current ?: return
        val diff = g.point(dir)
        pointedFor = s.place
        pointReveal = g.compassTo(s.place)
        announce(when (diff) { 0 -> "Spot on! +4 coins"; 1 -> "Close: +1 coin"; else -> "Not that way" },
            "${g.city.places[s.place].name} is ${COMPASS_NAMES[pointReveal]} of you.", 1600, mine = diff <= 1)
        bump()
    }

    fun turnAround() {
        if (busy) return
        if (g.turnAround()) { hintEdge = -1; nudge("U-turn: 12 seconds lost"); bump() }
    }

    fun drive(t: StreetSmarts.Turn) {
        if (busy) return
        val from = g.at
        val e = g.city.edges[t.edge]
        val stopBefore = g.current
        val following = planned.firstOrNull()
        val a = g.drive(t.edge)
        hintEdge = -1
        alts = emptyList()
        // off the planned route: the GPS finds a new one from here (that's a reroute)
        var rerouted = false
        val st = g.current
        if (st != null && st == stopBefore) {
            if (following != null && following != t.edge) {
                rerouted = st.help <= Help.TURNS.ordinal
                if (rerouted) g.order?.let { it.reroutes++ }
                planned = g.route(g.at, g.city.places[st.place].edge)
                if (st.help == Help.TURNS.ordinal) legSteps = g.steps(planned, g.at)
            } else planned = planned.drop(1)
        }
        val driveMs = (g.driveSeconds(e) * 28L).coerceIn(380, 1100)
        val waitMs = when { a.waited >= 10 -> 900L; a.waited > 0 -> 420L; else -> 0L }
        glide = Glide(from, g.at, nowMillis(), driveMs, waitMs)
        onMainLater(driveMs + waitMs) {
            glide = null
            if (rerouted) nudge("Rerouting… new route from ${g.city.streets[e.street].name}")
            else if (a.waited >= 10 && g.city.lights[g.at] != null) nudge("Red light: ${a.waited} s")
            for (s in a.reached) {
                val p = g.city.places[s.place]
                when (s.kind) {
                    StopKind.PICKUP -> announce("Picked up at ${p.name}", "Next: ${nextStopText()}", 1700, mine = true)
                    StopKind.DROP -> if (!a.orderDone) announce("Delivered to ${p.name}", "Next: ${nextStopText()}", 1700, mine = true)
                    StopKind.HOME -> {}
                }
            }
            if (a.orderDone) finishAnnouncements(a, stopBefore)
            if (g.current != stopBefore) startLeg()
            if (g.order == null && g.offer == null) { g.makeOffer(); checkDistrict() }
            bump()
        }
        bump()
    }

    private fun nextStopText(): String = g.current?.let { if (it.help <= 2) g.city.places[it.place].name + " (" + g.city.address(g.city.places[it.place]) + ")" else g.city.places[it.place].name } ?: ""

    private fun finishAnnouncements(a: StreetSmarts.Arrival, last: StreetSmarts.Stop?) {
        if (last?.kind == StopKind.HOME) {
            announce("Home! Shift complete", "+${a.bonus} coins. Tomorrow brings new road works.", 2200, mine = true)
        } else {
            val tip = if (a.tip >= 0) "+${a.tip} tip" else "${a.tip} for lateness"
            announce("Delivered! +${a.pay + a.tip + a.routeBonus} coins", "Pay ${a.pay}, $tip." +
                if (a.routeBonus > 0) " Shortest-path bonus +${a.routeBonus}!" else "", 2200, mine = true)
            val km = { m: Int -> "${m / 1000}.${(m % 1000) / 100} km" }
            if (a.shortest > 0) announce(if (a.meters <= a.shortest * 103 / 100) "You took the shortest path" else "Your route: ${km(a.meters)}",
                "Shortest possible: ${km(a.shortest)}" + if (a.reroutes > 0) " · ${a.reroutes} reroute${if (a.reroutes > 1) "s" else ""}" else "", 1800, mine = true)
        }
        for (id in a.learned) {
            val h = g.helpFor(id)
            announce("You're learning the way", "${g.city.places[id].name}: next time, ${Help.entries[h].label.lowercase()}.", 1700, mine = true)
        }
    }

    private fun checkDistrict() {
        if (g.ring > g.newsDistrictRing) {
            g.newsDistrictRing = g.ring
            announce("The city grows", "New streets open further from downtown. Longer routes and new customers await.", 2200)
        }
    }

    fun hint(h: StreetSmarts.Hint) {
        val s = g.current ?: return
        if (!g.buyHint(h)) { nudge("Not enough coins"); return }
        when (h) {
            StreetSmarts.Hint.NEXT_TURN -> hintEdge = g.route(g.at, g.city.places[s.place].edge).firstOrNull() ?: -1
            StreetSmarts.Hint.PIN -> pinFor = s.place
            StreetSmarts.Hint.ROUTE -> { planned = g.route(g.at, g.city.places[s.place].edge); routeUntil = nowMillis() + 6000 }
        }
        bump()
    }

    fun shop(style: Int? = null, paint: Int? = null) {
        val ok = (style?.let { g.buyStyle(it) } ?: true) && (paint?.let { g.buyPaint(it) } ?: true)
        if (!ok) nudge("Not enough coins")
        bump()
    }

    // last, so every property above exists before the first leg is planned
    init { score = g.earned; startLeg() }

    override fun saveState(): JSONObject = JSONObject().put("g", g.state()).put("pointed", pointedFor)

    companion object {
        val COMPASS_NAMES = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")
    }
}

// =====================================================================  the game definition

object StreetSmartsDef : GameDef {
    override val kind = GameKind.STREET_SMARTS
    override val singleLevel = true
    override val fullBleed = true
    override val hasHeader = true
    @Composable override fun Header(session: Session, actions: com.puresudoku.ui.games.HeaderActions) = StreetHeader(session as StreetSmartsSession, actions)

    override fun generate(d: Difficulty): Session = StreetSmartsSession(d, StreetSmarts.newGame(nowMillis()))
    override fun restore(o: JSONObject): Session {
        val st = o.getJSONObject("state")
        val g = StreetSmarts().apply { load(st.getString("g")) }
        return StreetSmartsSession(Difficulty.valueOf(o.getString("difficulty")), g).also { it.pointedFor = st.optInt("pointed", -1); it.restoreCommon(o) }
    }
    override fun levelInfo(d: Difficulty) = "One endless career"
    override fun levelBlurb(d: Difficulty) = "The city grows and the GPS fades as you learn."
    override fun hint(d: Difficulty) = "The better you know the way, the less the GPS helps."

    @Composable override fun Play(session: Session) = StreetSmartsUi(session as StreetSmartsSession)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Drive the city", "You're a delivery driver in Harbor City. At each corner, choose left, straight or right. Street signs tell you where you are; red lights and stop signs cost time.") { _ ->
            DemoMap(Help.ROUTE)
        },
        TutorialStep("The GPS lets go", "At first the route is drawn for you. Then you get a list of turns, then an address, then just a name. Each place gets less help as you prove you know the way, and more if you get lost.") { _ ->
            DemoMap(Help.NAME)
        },
        TutorialStep("Coins, hints and the garage", "Quick, clean deliveries earn tips. Spend coins on hints when you're stuck, or on new cars and paint. The city grows as you do: there is no end.") { _ ->
            DemoMap(Help.ADDRESS)
        },
    )
}

@Composable
private fun DemoMap(help: Help) {
    val g = remember { StreetSmarts.newGame(3).also { it.accept() } }
    Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp))) {
        StreetMap(g, null, help, routeShown = help == Help.ROUTE, pinPlace = -1, hintEdge = -1, glideProgress = { null }, modifier = Modifier.fillMaxSize())
    }
}

// =====================================================================  colours, Google-Maps style

private class MapColors(
    val land: Color, val block: Color, val park: Color, val water: Color, val minor: Color, val minorEdge: Color,
    val major: Color, val majorEdge: Color, val label: Color, val labelHalo: Color, val fog: Color, val route: Color, val routeEdge: Color,
    val poi: Color, val closed: Color,
)

private val LIGHT_MAP = MapColors(
    land = Color(0xFFF1F3F4), block = Color(0xFFE8EAED), park = Color(0xFFCEEAD6), water = Color(0xFFAADAFF), minor = Color(0xFFFFFFFF), minorEdge = Color(0xFFDADCE0),
    major = Color(0xFFFFF2AF), majorEdge = Color(0xFFF2CB6C), label = Color(0xFF5F6368), labelHalo = Color(0xFFFFFFFF), fog = Color(0xCCE8EAED),
    route = Color(0xFF1A73E8), routeEdge = Color(0xFF185ABC), poi = Color(0xFF7A8189), closed = Color(0xFFE8710A),
)
private val DARK_MAP = MapColors(
    land = Color(0xFF242F3E), block = Color(0xFF2B3544), park = Color(0xFF263C3F), water = Color(0xFF17263C), minor = Color(0xFF38414E), minorEdge = Color(0xFF212A37),
    major = Color(0xFF746855), majorEdge = Color(0xFF1F2835), label = Color(0xFF9CA5B3), labelHalo = Color(0xFF17263C), fog = Color(0xCC1B2330),
    route = Color(0xFF8AB4F8), routeEdge = Color(0xFF4A7BD0), poi = Color(0xFF8D96A3), closed = Color(0xFFF29900),
)

private fun kindColor(k: City.Kind): Color = when (k) {
    City.Kind.HOME -> Color(0xFF1A73E8)
    City.Kind.RESTAURANT -> Color(0xFFE8710A)
    City.Kind.CAFE -> Color(0xFF8D6E63)
    City.Kind.GROCER -> Color(0xFF188038)
    City.Kind.SHOP -> Color(0xFF1A73E8)
    City.Kind.PHARMACY -> Color(0xFFD93025)
    City.Kind.LANDMARK -> Color(0xFF7B1FA2)
    City.Kind.CUSTOMER -> Color(0xFF5F6368)
}

// =====================================================================  the map

/**
 * Harbor City, drawn like a street map. North is always up (it helps your sense of direction).
 * How much it labels depends on [help]: every street name up to "Address", only the streets
 * around you at "Name only", and none at "Memory" (read the street sign instead).
 */
@Composable
private fun StreetMap(
    g: StreetSmarts, s: StreetSmartsSession?, help: Help, routeShown: Boolean, pinPlace: Int, hintEdge: Int,
    planned: List<Int> = emptyList(), alts: List<List<Int>> = emptyList(), altPicked: Int = 0,
    glideProgress: () -> Pair<Glide, Float>?, modifier: Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val mc = if (dark) DARK_MAP else LIGHT_MAP
    val tm = rememberTextMeasurer()
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val city = g.city
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().background(mc.land).pointerInput(Unit) {
            detectTransformGestures { _, p, z, _ -> zoom = (zoom * z).coerceIn(0.35f, 3f); pan += p }
        }) {
            // the car: between two corners while it drives
            val gp = glideProgress()
            val (cx, cy) = if (gp != null) {
                val (gl, f) = gp
                (city.nodeX[gl.from] + (city.nodeX[gl.to] - city.nodeX[gl.from]) * f) to (city.nodeY[gl.from] + (city.nodeY[gl.to] - city.nodeY[gl.from]) * f)
            } else city.nodeX[g.at] to city.nodeY[g.at]
            val scale = size.minDimension / 620f * zoom
            fun sx(x: Float) = (x - cx) * scale + size.width / 2 + pan.x
            fun sy(y: Float) = (y - cy) * scale + size.height / 2 + pan.y
            fun p(n: Int) = Offset(sx(city.nodeX[n]), sy(city.nodeY[n]))
            val viewR = hypot(size.width, size.height) / scale / 2 + 120f
            val camX = cx - pan.x / scale; val camY = cy - pan.y / scale
            fun near(x: Float, y: Float) = abs(x - camX) < viewR && abs(y - camY) < viewR

            // blocks, parks and the river
            val bl = City.BLOCK
            for (cell in city.parkCells) {
                val x = cell % City.N; val y = cell / City.N
                drawRect(mc.park, Offset(sx(x * bl), sy(y * bl)), Size(bl * scale, bl * scale))
            }
            val river = Path()
            for (i in -2..City.N * 4 + 4) {
                val x = i / 4f
                val pt = Offset(sx(x * bl), sy((city.riverRow(x) + 0.5f) * bl))
                if (i == -2) river.moveTo(pt.x, pt.y) else river.lineTo(pt.x, pt.y)
            }
            drawPath(river, mc.water, style = Stroke(62f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // roads: casing first, then the fill, minor under major
            // passes: 0 minor casing, 1 minor fill, 2 major casing, 3 major fill
            for (pass in 0..3) for (e in city.edges) {
                if (e.id !in city.touching[e.a]) continue
                val major = city.streets[e.street].major
                if (major != (pass >= 2)) continue
                if (!near(city.nodeX[e.a], city.nodeY[e.a]) && !near(city.nodeX[e.b], city.nodeY[e.b])) continue
                val casing = pass % 2 == 0
                val w = (if (major) 15f else 10f) * scale + if (casing) 2.2f * density else 0f
                val col = when { casing -> if (major) mc.majorEdge else mc.minorEdge; major -> mc.major; else -> mc.minor }
                drawLine(col, p(e.a), p(e.b), w, StrokeCap.Round)
            }
            // locked districts: greyed out
            val r = g.ring
            val lo = (City.CENTER - r - 0.5f) * bl; val hi = (City.CENTER + r + 0.5f) * bl
            drawRect(mc.fog, Offset(0f, 0f), Size(size.width, max(0f, sy(lo))))
            drawRect(mc.fog, Offset(0f, sy(hi)), Size(size.width, max(0f, size.height - sy(hi))))
            drawRect(mc.fog, Offset(0f, sy(lo)), Size(max(0f, sx(lo)), sy(hi) - sy(lo)))
            drawRect(mc.fog, Offset(sx(hi), sy(lo)), Size(max(0f, size.width - sx(hi)), sy(hi) - sy(lo)))

            // one-way arrows, road works
            for (e in city.edges) {
                if (e.id !in city.touching[e.a] || !near(city.nodeX[e.a], city.nodeY[e.a])) continue
                val a = p(e.a); val b = p(e.b); val mid = (a + b) / 2f
                if (e.id in g.closed) { drawLine(mc.closed, a + (b - a) * 0.3f, a + (b - a) * 0.7f, 5f * scale, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(6f * scale, 5f * scale))) }
                if (e.oneWay != 0 && scale > 0.9f) {
                    val (f, t) = if (e.oneWay == 1) a to b else b to a
                    val d = (t - f); val l = d.getDistance(); if (l < 1f) continue
                    val u = d / l; val n = Offset(-u.y, u.x); val s2 = 4.5f * scale
                    drawLine(mc.label.copy(alpha = 0.6f), mid - u * s2 + n * s2, mid + u * s2, 1.4f * density)
                    drawLine(mc.label.copy(alpha = 0.6f), mid - u * s2 - n * s2, mid + u * s2, 1.4f * density)
                }
            }

            // the route: drawn at full help, or for a few seconds as a hint
            val cur = g.current
            fun routeLine(path: List<Int>): Path {
                var n = g.at
                val line = Path(); line.moveTo(sx(cx), sy(cy))
                for (id in path) { n = city.other(city.edges[id], n); line.lineTo(p(n).x, p(n).y) }
                return line
            }
            // other ways there, in grey, while you choose
            if (cur != null && alts.size > 1) for ((i, alt) in alts.withIndex()) if (i != altPicked) {
                val line = routeLine(alt)
                drawPath(line, Color(0xFF80868B), style = Stroke(9f * scale + 2 * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(line, Color(0xFFBDC1C6), style = Stroke(9f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (cur != null && (routeShown || alts.size > 1)) {
                val line = routeLine(planned.ifEmpty { g.route(g.at, city.places[cur.place].edge) })
                drawPath(line, mc.routeEdge, style = Stroke(9f * scale + 2 * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(line, mc.route, style = Stroke(9f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (hintEdge >= 0) {
                val e = city.edges[hintEdge]
                drawLine(mc.route, p(g.at), p(city.other(e, g.at)), 9f * scale, StrokeCap.Round)
            }

            // traffic lights (coloured for your direction) and stop signs
            val axisNow = if (g.cameBy >= 0) city.axisOf(city.edges[g.cameBy]) else City.Axis.NS
            for (n in 0 until City.N * City.N) {
                if (!city.alive(n) || !near(city.nodeX[n], city.nodeY[n])) continue
                val c = p(n)
                val light = city.lights[n]
                if (light != null) {
                    val probe = city.touching[n].map { city.edges[it] }.firstOrNull { city.axisOf(it) == axisNow } ?: city.edges[city.touching[n][0]]
                    val green = g.greenFor(n, probe)
                    val u = 3.2f * density.coerceAtMost(scale * 1.6f + density * 0.5f)
                    val box = c + Offset(5f * scale, -15f * scale)
                    drawRoundRect(Color(0xFF202124), box, Size(u * 2.2f, u * 6.2f), androidx.compose.ui.geometry.CornerRadius(u * 0.7f))
                    val lamp = { i: Int, col: Color, lit: Boolean -> drawCircle(if (lit) col else col.copy(alpha = 0.22f), u * 0.75f, box + Offset(u * 1.1f, u * (1.1f + i * 2f))) }
                    lamp(0, Color(0xFFEA4335), !green); lamp(1, Color(0xFFFBBC04), false); lamp(2, Color(0xFF34A853), green)
                    // seconds until it changes, for the lights around you
                    if (hypot(city.nodeX[n] - city.nodeX[g.at], city.nodeY[n] - city.nodeY[g.at]) < 260f) {
                        val ph = ((g.clock + light.offset) % light.cycle + light.cycle) % light.cycle
                        val ns = city.axisOf(probe) != City.Axis.EW
                        val left = when { ns && ph < light.nsGreen -> light.nsGreen - ph; ns -> light.cycle - ph; ph >= light.nsGreen -> light.cycle - ph; else -> light.nsGreen - ph }
                        label(tm, "${left}s", box + Offset(u * 1.1f, u * 7.6f), 9f, if (green) Color(0xFF34A853) else Color(0xFFEA4335), mc.labelHalo, 0f)
                    }
                } else if (city.allWayStop[n]) {
                    drawOctagon(c + Offset(8f * scale, -8f * scale), 6f * scale)
                }
            }

            // places: pins, names only when the help level allows
            val showNames = help.ordinal <= Help.ADDRESS.ordinal
            val labelled = ArrayList<Offset>()   // place names already drawn, so they never pile up
            for (pl in city.places) {
                val (px, py) = city.placeXY(pl)
                if (!near(px, py) || !g.unlockedPlace(pl)) continue
                val isPin = pl.id == pinPlace
                val isTarget = cur != null && pl.id == cur.place && help.ordinal <= Help.TURNS.ordinal
                val public = pl.kind != City.Kind.CUSTOMER && pl.kind != City.Kind.HOME
                if (!public && !isPin && !isTarget && !(pl.kind == City.Kind.HOME && help.ordinal <= Help.ADDRESS.ordinal)) continue
                val c = Offset(sx(px), sy(py))
                if (isPin || isTarget) { drawPin(c, scale, Color(0xFFEA4335)); continue }
                if (help == Help.MEMORY && pl.kind != City.Kind.LANDMARK) continue
                drawCircle(Color.White, 5.2f * scale, c)
                drawCircle(kindColor(pl.kind), 4.2f * scale, c)
                val nameOk = showNames || (help == Help.NAME && pl.kind == City.Kind.LANDMARK)
                val at = c + Offset(0f, 9f * scale)
                val clear = labelled.none { abs(it.x - at.x) < 110f * density && abs(it.y - at.y) < 16f * density }
                if (nameOk && scale > 0.75f && clear) { label(tm, pl.name, at, 9.5f, kindColor(pl.kind).copy(alpha = 0.95f), mc.labelHalo, 0f); labelled += at }
            }

            // street names along the streets (fewer as the GPS lets go)
            if (scale > 0.5f) {
                val seen = HashSet<Int>()
                for (e in city.edges) {
                    if (e.id !in city.touching[e.a]) continue
                    val mx = (city.nodeX[e.a] + city.nodeX[e.b]) / 2; val my = (city.nodeY[e.a] + city.nodeY[e.b]) / 2
                    if (!near(mx, my)) continue
                    val st = city.streets[e.street]
                    // one label per street every few blocks
                    val along = if (st.axis == City.Axis.NS) city.gy(e.a) else city.gx(e.a)
                    if (along % 3 != 1 || !seen.add(e.street * 100 + along)) continue
                    val ang = when (st.axis) { City.Axis.NS -> -90f; City.Axis.EW -> 0f; City.Axis.DIAG -> 45f }
                    label(tm, st.name, Offset(sx(mx), sy(my)), if (st.major) 10f else 9f, mc.label, mc.labelHalo, ang)
                }
            }

            // the car
            val heading = if (gp != null) {
                val (gl, _) = gp
                kotlin.math.atan2(city.nodeY[gl.to] - city.nodeY[gl.from], city.nodeX[gl.to] - city.nodeX[gl.from]) * 180f / kotlin.math.PI.toFloat()
            } else if (g.cameBy >= 0) g.heading(city.edges[g.cameBy], city.other(city.edges[g.cameBy], g.at)).toFloat() else -90f
            drawCar(Offset(sx(cx), sy(cy)), heading, density, Color(g.paints[g.paint]), StreetSmarts.CarStyle.entries[g.car])
        }
        // pinch to zoom, drag to pan; one button brings the car back to the middle
        if (zoom != 1f || pan != Offset.Zero) Box(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) {
            MapButton(Icons.Rounded.MyLocation, "Recentre") { pan = Offset.Zero; zoom = 1f }
        }
    }
}

@Composable
private fun MapButton(icon: ImageVector, desc: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, desc, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface) }
    }
}

private fun DrawScope.label(tm: TextMeasurer, text: String, at: Offset, sizeSp: Float, color: Color, halo: Color, angle: Float) {
    val style = TextStyle(fontSize = sizeSp.sp, color = color, fontWeight = FontWeight.Medium,
        shadow = androidx.compose.ui.graphics.Shadow(halo, Offset.Zero, 6f))
    val l = tm.measure(text, style)
    rotate(angle, at) { drawText(l, topLeft = Offset(at.x - l.size.width / 2f, at.y - l.size.height / 2f)) }
}

private fun DrawScope.drawPin(at: Offset, scale: Float, color: Color) {
    val r = 7f * scale
    val path = Path().apply {
        moveTo(at.x, at.y)
        cubicTo(at.x - r * 1.2f, at.y - r * 1.4f, at.x - r * 1.1f, at.y - r * 2.9f, at.x, at.y - r * 2.9f)
        cubicTo(at.x + r * 1.1f, at.y - r * 2.9f, at.x + r * 1.2f, at.y - r * 1.4f, at.x, at.y)
        close()
    }
    drawPath(path, color)
    drawCircle(Color(0xFF8C1C13), r * 0.42f, Offset(at.x, at.y - r * 2.05f))
}

private fun DrawScope.drawOctagon(c: Offset, r: Float) {
    val path = Path()
    for (i in 0 until 8) {
        val a = (i * 45 + 22.5) * kotlin.math.PI / 180
        val pt = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
        if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
    }
    path.close()
    drawPath(path, Color(0xFFD93025))
    drawPath(path, Color.White, style = Stroke(r * 0.18f))
}

/**
 * A top-down vehicle facing along [heading] degrees, in the player's [paint]: body, glass, roof,
 * wheels, mirrors, headlights and taillights. Each style has its own shape.
 */
private fun DrawScope.drawCar(c: Offset, heading: Float, density: Float, paint: Color, style: StreetSmarts.CarStyle, glow: Boolean = true) {
    val d = density
    val (len, wid) = when (style) {
        StreetSmarts.CarStyle.SCOOTER -> 20f to 8f
        StreetSmarts.CarStyle.HATCH -> 26f to 14f
        StreetSmarts.CarStyle.SUV -> 31f to 16f
        StreetSmarts.CarStyle.VAN -> 34f to 15f
        StreetSmarts.CarStyle.PICKUP -> 33f to 15f
        StreetSmarts.CarStyle.TRUCK -> 40f to 16f
        else -> 30f to 14f
    }.let { (l, w) -> l * d to w * d }
    val hx = len / 2; val hy = wid / 2
    val ink = Color(0xFF202124)
    val glass = Color(0xFF9CC3F5)
    val dark = lerpColor(paint, Color.Black, 0.28f)
    val light = lerpColor(paint, Color.White, 0.25f)
    fun rr(l: Float, t: Float, r: Float, b: Float, rear: Float, front: Float) = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(l, t, r, b,
            topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(rear), topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(front),
            bottomRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(front), bottomLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(rear)))
    }
    fun quad(x1: Float, w1: Float, x2: Float, w2: Float) = Path().apply { moveTo(x1, -w1); lineTo(x2, -w2); lineTo(x2, w2); lineTo(x1, w1); close() }
    // a soft glow under the car so it stands out on the map
    if (glow) drawCircle(Color(0x334285F4), len * 0.8f, c)
    rotate(heading, c) {
        translate(c.x, c.y) {
            if (style == StreetSmarts.CarStyle.SCOOTER) {
                drawRoundRect(Color(0x55000000), Offset(-hx + d, -hy + d), Size(len, wid), androidx.compose.ui.geometry.CornerRadius(hy))
                drawRoundRect(ink, Offset(-hx, -hy * 0.45f), Size(len * 0.26f, hy * 0.9f), androidx.compose.ui.geometry.CornerRadius(hy * 0.4f))   // back wheel
                drawRoundRect(ink, Offset(hx - len * 0.26f, -hy * 0.45f), Size(len * 0.26f, hy * 0.9f), androidx.compose.ui.geometry.CornerRadius(hy * 0.4f))
                drawPath(rr(-hx * 0.75f, -hy, hx * 0.7f, hy, hy, hy * 0.6f), paint)
                drawLine(ink, Offset(hx * 0.45f, -hy * 1.35f), Offset(hx * 0.45f, hy * 1.35f), 1.6f * d, StrokeCap.Round)   // handlebars
                drawOval(dark, Offset(-hx * 0.55f, -hy * 0.6f), Size(len * 0.38f, hy * 1.2f))                                 // seat
                drawCircle(light, hy * 0.75f, Offset(hx * 0.05f, 0f))                                                        // helmet
                drawCircle(Color(0xFFFFF59D), hy * 0.3f, Offset(hx * 0.92f, 0f))
                return@translate
            }
            // shadow and wheels
            drawPath(rr(-hx + d * 1.2f, -hy + d * 1.2f, hx + d * 1.2f, hy + d * 1.2f, wid * 0.3f, wid * 0.35f), Color(0x55000000))
            val wl = len * 0.17f; val ww = wid * 0.16f
            for (x in listOf(-hx + len * 0.12f, hx - len * 0.12f - wl)) for (sgn in listOf(-1f, 1f))
                drawRoundRect(ink, Offset(x, if (sgn < 0) -hy - ww * 0.35f else hy - ww * 0.65f), Size(wl, ww), androidx.compose.ui.geometry.CornerRadius(ww * 0.4f))
            when (style) {
                StreetSmarts.CarStyle.TRUCK -> {
                    val split = hx - len * 0.27f
                    drawPath(rr(-hx, -hy, split - d, hy, wid * 0.08f, wid * 0.08f), Color(0xFFF1F3F4))          // white box
                    drawRect(Color(0xFFDADCE0), Offset(-hx, -d * 0.5f), Size(split - d + hx, d))                  // roof seam
                    drawPath(rr(split, -hy, hx, hy, wid * 0.12f, wid * 0.3f), paint)                              // cab
                    drawPath(quad(split + len * 0.08f, hy * 0.8f, hx - len * 0.05f, hy * 0.72f), glass)
                }
                StreetSmarts.CarStyle.VAN -> {
                    drawPath(rr(-hx, -hy, hx, hy, wid * 0.18f, wid * 0.42f), paint)
                    drawPath(quad(hx - len * 0.24f, hy * 0.82f, hx - len * 0.08f, hy * 0.72f), glass)             // windscreen
                    drawPath(rr(-hx + len * 0.06f, -hy * 0.78f, hx - len * 0.26f, hy * 0.78f, wid * 0.1f, wid * 0.1f), light)   // long roof
                    drawLine(dark, Offset(-hx + len * 0.4f, -hy), Offset(-hx + len * 0.4f, hy), d)                // sliding door
                }
                StreetSmarts.CarStyle.PICKUP -> {
                    drawPath(rr(-hx, -hy, hx, hy, wid * 0.15f, wid * 0.38f), paint)
                    drawRoundRect(Color(0xFF3C4043), Offset(-hx + len * 0.05f, -hy * 0.72f), Size(len * 0.42f, hy * 1.44f), androidx.compose.ui.geometry.CornerRadius(wid * 0.06f))   // open bed
                    drawPath(quad(hx - len * 0.3f, hy * 0.8f, hx - len * 0.14f, hy * 0.72f), glass)
                    drawRect(light, Offset(-hx + len * 0.5f, -hy * 0.76f), Size(len * 0.18f, hy * 1.52f))         // cab roof
                }
                StreetSmarts.CarStyle.SUV -> {
                    drawPath(rr(-hx, -hy, hx, hy, wid * 0.2f, wid * 0.3f), paint)
                    drawPath(quad(hx - len * 0.3f, hy * 0.86f, hx - len * 0.15f, hy * 0.76f), glass)
                    drawPath(quad(-hx + len * 0.1f, hy * 0.78f, -hx + len * 0.18f, hy * 0.84f), glass)
                    drawRect(light, Offset(-hx + len * 0.18f, -hy * 0.84f), Size(len * 0.52f, hy * 1.68f))
                    for (k in 1..3) drawLine(dark, Offset(-hx + len * (0.18f + k * 0.13f), -hy * 0.7f), Offset(-hx + len * (0.18f + k * 0.13f), hy * 0.7f), d * 0.8f)   // roof rack
                }
                StreetSmarts.CarStyle.SPORTS -> {
                    drawPath(rr(-hx, -hy, hx, hy, wid * 0.32f, wid * 0.5f), paint)
                    drawPath(quad(-hx + len * 0.3f, hy * 0.7f, -hx + len * 0.48f, hy * 0.8f), glass)                // windscreen, set well back
                    drawRect(dark, Offset(-hx + len * 0.12f, -hy * 0.7f), Size(len * 0.18f, hy * 1.4f))             // cockpit
                    for (sgn in listOf(-1f, 1f)) drawLine(Color.White.copy(alpha = 0.85f), Offset(-hx + d, sgn * hy * 0.2f), Offset(hx - d, sgn * hy * 0.2f), d * 1.3f)   // racing stripes
                }
                else -> {   // sedan, hatchback, cab
                    val hatch = style == StreetSmarts.CarStyle.HATCH
                    drawPath(rr(-hx, -hy, hx, hy, wid * (if (hatch) 0.22f else 0.3f), wid * 0.42f), paint)
                    drawPath(quad(hx - len * 0.36f, hy * 0.84f, hx - len * 0.2f, hy * 0.74f), glass)                // windscreen
                    val rearStart = if (hatch) -hx + len * 0.06f else -hx + len * 0.16f
                    drawPath(quad(rearStart, hy * 0.72f, rearStart + len * 0.1f, hy * 0.8f), glass)                // rear window
                    drawRect(light, Offset(rearStart + len * 0.1f, -hy * 0.8f), Size(hx - len * 0.36f - rearStart - len * 0.1f, hy * 1.6f))   // roof
                    if (style == StreetSmarts.CarStyle.TAXI) {
                        drawRoundRect(Color(0xFFFBBC04), Offset(-len * 0.08f, -hy * 0.32f), Size(len * 0.16f, hy * 0.64f), androidx.compose.ui.geometry.CornerRadius(d))
                        for (k in 0..3) drawRect(ink, Offset(-len * 0.08f + k * len * 0.04f, -hy * 0.32f + (k % 2) * hy * 0.32f), Size(len * 0.04f, hy * 0.32f))
                    }
                }
            }
            // mirrors, lights and outline
            if (style != StreetSmarts.CarStyle.TRUCK) for (sgn in listOf(-1f, 1f))
                drawOval(dark, Offset(hx - len * 0.36f, if (sgn < 0) -hy - d * 1.6f else hy - d * 0.4f), Size(d * 2.6f, d * 2f))
            for (sgn in listOf(-1f, 1f)) {
                drawRoundRect(Color(0xFFFFF59D), Offset(hx - d * 2.4f, sgn * hy * 0.62f - d * 1.2f), Size(d * 2f, d * 2.4f), androidx.compose.ui.geometry.CornerRadius(d))
                drawRoundRect(Color(0xFFE53935), Offset(-hx + d * 0.4f, sgn * hy * 0.62f - d * 1.2f), Size(d * 1.6f, d * 2.4f), androidx.compose.ui.geometry.CornerRadius(d * 0.6f))
            }
            drawPath(rr(-hx, -hy, hx, hy, wid * 0.25f, wid * 0.35f), ink.copy(alpha = 0.55f), style = Stroke(d))
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

// =====================================================================  the screen

@Composable
fun StreetSmartsUi(s: StreetSmartsSession) {
    s.version
    val g = s.g
    val stop = g.current
    val help = stop?.let { Help.entries[it.help] } ?: Help.ROUTE
    var showHints by remember { mutableStateOf(false) }
    var showGarage by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(nowMillis()) }
    // animate the car while it drives, and the route hint while it lasts
    LaunchedEffect(s.glide, s.routeUntil) {
        while (s.glide != null || nowMillis() < s.routeUntil) { withFrameMillis { now = nowMillis() } }
        now = nowMillis()
    }
    // the map fills the screen; the GPS floats on top and the controls float at the bottom
    Box(Modifier.fillMaxSize()) {
        StreetMap(
            g, s, help,
            routeShown = (help == Help.ROUTE || now < s.routeUntil) && s.glide == null,
            pinPlace = s.pinFor, hintEdge = s.hintEdge, planned = s.planned, alts = s.alts, altPicked = s.altPicked,
            glideProgress = gp@{
                val gl = s.glide ?: return@gp null
                val f = ((now - gl.start).toFloat() / gl.driveMs).coerceIn(0f, 1f)
                gl to f
            },
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 10.dp)) {
            Spacer(Modifier.height(54.dp))   // room for the floating header
            GpsPanel(s, help)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                StreetSign(g, Modifier.align(Alignment.TopStart).padding(top = 8.dp))
                StatusChip(g, Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp))
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shadowElevation = 6.dp) {
                Box(Modifier.padding(8.dp)) { Controls(s, onHints = { showHints = true }, onGarage = { showGarage = true }) }
            }
            Spacer(Modifier.height(6.dp))
        }
        com.puresudoku.ui.components.HeraldOverlay(s, StreetSmartsDef, Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 54.dp))
    }
    if (showHints) HintsDialog(s) { showHints = false }
    if (showGarage) GarageDialog(s) { showGarage = false }
}

/** A minimal floating bar: back, coins and pause, over the map. */
@Composable
private fun StreetHeader(s: StreetSmartsSession, a: com.puresudoku.ui.games.HeaderActions) {
    s.version
    Box(Modifier.fillMaxWidth().height(50.dp)) {
        MapButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.align(Alignment.CenterStart), onClick = a.back)
        Surface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
            Text("${s.g.coins} coins · Lv ${s.g.level + 1}", Modifier.padding(horizontal = 14.dp, vertical = 7.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MapButton(Icons.Outlined.Info, "How to play", onClick = a.info)
            MapButton(if (a.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, "Pause", enabled = a.canPause, onClick = a.pause)
        }
    }
}

/** The GPS: how much it tells you depends on the help level for this stop. */
@Composable
private fun GpsPanel(s: StreetSmartsSession, help: Help) {
    val g = s.g
    val stop = g.current
    val offer = g.offer
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFF0B57D0), shadowElevation = 4.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            val white = Color.White
            if (stop == null) {
                Text(if (offer != null) "New order" else "Waiting for orders…", color = white, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (offer != null) {
                    val first = offer.stops.first()
                    Text(
                        if (first.kind == StopKind.HOME) "Shift over. Drive home." else
                            "Pick up at ${g.city.places[first.place].name}, ${offer.stops.size - 1} drop${if (offer.stops.size > 2) "s" else ""} · pays ${offer.pay}+ coins" + if (offer.rush) " · RUSH" else "",
                        color = white.copy(alpha = 0.9f), fontSize = 13.sp,
                    )
                }
                return@Column
            }
            val p = g.city.places[stop.place]
            val verb = when (stop.kind) { StopKind.PICKUP -> "Pick up at"; StopKind.DROP -> "Deliver to"; StopKind.HOME -> "Drive home" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(help.label.uppercase(), color = white.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                val o = g.order
                if (o != null) Text("${clockText(g.clock)} · budget ${o.budget / 60}:${(o.budget % 60).toString().padStart(2, '0')} · used ${o.driven / 60}:${(o.driven % 60).toString().padStart(2, '0')}",
                    color = white.copy(alpha = 0.75f), fontSize = 10.sp)
            }
            when (help) {
                Help.ROUTE -> {
                    val path = s.planned.ifEmpty { g.route(g.at, p.edge) }
                    val steps = g.steps(path, g.at)
                    val first = steps.firstOrNull()
                    val then = steps.getOrNull(1)
                    Text(
                        when {
                            path.size <= 1 -> "${p.name} is on this block"
                            first == null -> "Continue"
                            first.dir == TurnDir.START -> "Head ${compassLabel(g.heading(g.city.edges[path[0]], g.at)).lowercase()} on ${first.street}"
                            first.dir == TurnDir.UTURN -> "Turn around"
                            first.dir != TurnDir.STRAIGHT -> "${first.dir.label} onto ${first.street}"
                            then == null -> "Continue on ${first.street}"
                            else -> "In ${then.metersBefore} m: ${then.dir.label.lowercase()} onto ${then.street}"
                        },
                        color = white, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    )
                    Text("$verb ${p.name} · ${g.city.address(p)}", color = white.copy(alpha = 0.9f), fontSize = 13.sp)
                }
                Help.TURNS -> {
                    Text("$verb ${p.name} · ${g.city.address(p)}", color = white, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    val list = s.legSteps.mapIndexed { i, st ->
                        if (i == 0 && (st.dir == TurnDir.START || st.dir == TurnDir.STRAIGHT)) "Head ${legHeading(g, s)} on ${st.street}" else "${st.dir.label} on ${st.street}"
                    }.joinToString("  ·  ")
                    Text(if (list.isEmpty()) "It's on this street." else list, color = white.copy(alpha = 0.92f), fontSize = 13.sp, lineHeight = 17.sp,
                        modifier = Modifier.heightIn(max = 70.dp).verticalScroll(rememberScrollState()))
                }
                Help.ADDRESS -> {
                    Text("$verb ${p.name}", color = white, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(g.city.address(p) + " · " + g.city.districtOf(g.city.edges[p.edge].a), color = white.copy(alpha = 0.9f), fontSize = 13.sp)
                }
                Help.NAME, Help.MEMORY -> {
                    Text("$verb ${p.name}", color = white, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(if (help == Help.NAME) "From memory: no address." else "From memory: no address, and only landmarks are marked.", color = white.copy(alpha = 0.85f), fontSize = 13.sp)
                }
            }
            if (s.alts.size > 1) RouteChoices(s)
            val more = g.order?.stops?.filter { !it.done }?.drop(1)
            if (!more.isNullOrEmpty()) Text("Then: " + more.joinToString(", ") { g.city.places[it.place].name }, color = white.copy(alpha = 0.75f), fontSize = 12.sp)
        }
    }
}

/** The compass direction the leg's turn list starts out in. */
private fun legHeading(g: StreetSmarts, s: StreetSmartsSession): String {
    val first = s.legSteps.firstOrNull() ?: return "out"
    s.planned.firstOrNull()?.let { id -> val pe = g.city.edges[id]; if (pe.a == g.at || pe.b == g.at) return compassLabel(g.heading(pe, g.at)).lowercase() }
    val e = g.city.touching[first.node].map { g.city.edges[it] }.firstOrNull { g.city.streets[it.street].name == first.street && g.canDrive(it, first.node) } ?: return "out"
    return compassLabel(g.heading(e, first.node)).lowercase()
}

/** The ways to the next stop, like a map app: time, distance and the main road. Tap one to follow it. */
@Composable
private fun RouteChoices(s: StreetSmartsSession) {
    val g = s.g
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((i, r) in s.alts.withIndex()) {
            val secs = g.pathSeconds(r, g.at); val m = g.pathMeters(r)
            val via = r.groupingBy { g.city.edges[it].street }.eachCount().maxByOrNull { it.value }?.key?.let { g.city.streets[it].name } ?: ""
            val picked = i == s.altPicked
            Surface(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { s.pickRoute(i) }, shape = RoundedCornerShape(10.dp),
                color = if (picked) Color.White else Color.White.copy(alpha = 0.16f)) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                    val ink = if (picked) Color(0xFF0B57D0) else Color.White
                    Text("${secs / 60}:${(secs % 60).toString().padStart(2, '0')}" + if (i == 0) " · fastest" else "", color = ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("${m / 1000}.${(m % 1000) / 100} km · via $via", color = ink, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

private fun clockText(t: Int): String {
    val h = (t / 3600) % 24; val m = (t / 60) % 60
    return "${if (h % 12 == 0) 12 else h % 12}:${m.toString().padStart(2, '0')} ${if (h < 12) "AM" else "PM"}"
}

/** The green street sign at your corner: always there, like in real life. */
@Composable
private fun StreetSign(g: StreetSmarts, modifier: Modifier) {
    val names = g.city.touching[g.at].map { g.city.streets[g.city.edges[it].street].name }.distinct()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (n in names.take(3)) Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF1E6B3A), shadowElevation = 2.dp) {
            Text(n, Modifier.border(1.dp, Color.White, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusChip(g: StreetSmarts, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shadowElevation = 2.dp) {
        Text("Lv ${g.level + 1} · ${g.deliveries} delivered · ${g.city.districtOf(g.at)}",
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** What you can do now: accept an order, point the way, or pick a turn. */
@Composable
private fun Controls(s: StreetSmartsSession, onHints: () -> Unit, onGarage: () -> Unit) {
    val g = s.g
    val enabled = !s.busy
    when {
        g.offer != null && g.order == null -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = s::accept, enabled = enabled, modifier = Modifier.weight(1f).height(52.dp)) {
                Text(if (g.offer?.stops?.first()?.kind == StopKind.HOME) "Head home" else "Accept order", fontSize = 16.sp)
            }
            OutlinedButton(onClick = onGarage, modifier = Modifier.height(52.dp)) { Icon(Icons.Rounded.DirectionsCar, null); Spacer(Modifier.width(4.dp)); Text("Garage") }
        }
        s.needsPoint() -> PointTheWay(s)
        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val opts = g.options()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in opts) {
                    val hinted = t.edge == s.hintEdge
                    val icon = when (t.dir) { TurnDir.LEFT -> Icons.Rounded.TurnLeft; TurnDir.RIGHT -> Icons.Rounded.TurnRight; TurnDir.UTURN -> Icons.Rounded.UTurnLeft; else -> Icons.Rounded.Straight }
                    val heading = if (t.dir == TurnDir.START) compassLabel(g.heading(g.city.edges[t.edge], g.at)) else t.dir.label
                    Surface(
                        Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled) { s.drive(t) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (hinted) Color(0xFF0B57D0) else MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(if (hinted) 2.dp else 0.dp, Color(0xFF0B57D0)),
                    ) {
                        Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            val tint = if (hinted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            if (t.dir == TurnDir.START) Text(heading, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tint) else Icon(icon, heading, tint = tint)
                            Text(t.street, fontSize = 11.sp, lineHeight = 12.sp, maxLines = 2, textAlign = TextAlign.Center, color = tint)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onHints, enabled = g.order != null, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Lightbulb, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Hints") }
                OutlinedButton(onClick = s::turnAround, enabled = enabled && g.cameBy >= 0 && opts.none { it.dir == TurnDir.UTURN }, modifier = Modifier.weight(1f)) { Text("U-turn") }
                OutlinedButton(onClick = onGarage, modifier = Modifier.weight(1f)) { Text("Garage") }
            }
        }
    }
}

private fun compassLabel(heading: Double): String {
    val d = ((heading + 90 + 360) % 360)          // 0 = north on the map
    return listOf("North", "North-east", "East", "South-east", "South", "South-west", "West", "North-west")[((d + 22.5) / 45).toInt() % 8]
}

/** Sense of direction: before driving off, point toward the stop. */
@Composable
private fun PointTheWay(s: StreetSmartsSession) {
    val p = s.g.current?.let { s.g.city.places[it.place] } ?: return
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Which way is ${p.name}?", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("Point the way for bonus coins, then drive.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        val grid = listOf(listOf(7, 0, 1), listOf(6, -1, 2), listOf(5, 4, 3))
        val short = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        for (row in grid) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in row) {
                if (d < 0) { Box(Modifier.size(54.dp, 38.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.DirectionsCar, null) }; continue }
                OutlinedButton(onClick = { s.point(d) }, modifier = Modifier.size(54.dp, 38.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(short[d], fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun HintsDialog(s: StreetSmartsSession, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Hints") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You have ${s.g.coins} coins. Hints cost coins, and a trip that needed one teaches you less.", fontSize = 13.sp)
                for (h in StreetSmarts.Hint.entries) OutlinedButton(onClick = { s.hint(h); close() }, enabled = s.g.coins >= h.cost, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) { Text(h.label, fontWeight = FontWeight.SemiBold); Text(h.blurb, fontSize = 11.sp) }
                    Text("${h.cost}", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Close") } },
    )
}

@Composable
private fun GarageDialog(s: StreetSmartsSession, close: () -> Unit) {
    val g = s.g
    s.version
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Garage · ${g.coins} coins") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Vehicles", fontWeight = FontWeight.SemiBold)
                for (c in StreetSmarts.CarStyle.entries) {
                    val owned = g.ownsStyle(c.ordinal) || c.price == 0
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { s.shop(style = c.ordinal) }
                        .background(if (g.car == c.ordinal) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(76.dp, 36.dp)) { drawCar(center, 0f, density * 1.7f, Color(g.paints[g.paint]), c, glow = false) }
                        Spacer(Modifier.width(8.dp))
                        Text(c.label, Modifier.weight(1f))
                        Text(if (g.car == c.ordinal) "Driving" else if (owned) "Owned" else "${c.price}", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Paint", fontWeight = FontWeight.SemiBold)
                for (row in g.paints.indices.chunked(5)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in row) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(g.paints[i])).border(if (g.paint == i) 3.dp else 1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable { s.shop(paint = i) })
                        Text(if (g.ownsPaint(i) || g.paintPrice(i) == 0) "✓" else "${g.paintPrice(i)}", fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Career: ${g.deliveries} deliveries · ${g.cleanTrips} clean trips · best streak ${g.bestStreak} · ${g.familiarity.count { it.value >= 3 }} places known by heart", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Done") } },
    )
}
