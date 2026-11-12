package com.puresudoku.ui.games.realm

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.board.Realm
import com.puresudoku.core.board.RealmMap
import com.puresudoku.core.board.VOrder
import com.puresudoku.core.board.Veilguard
import com.puresudoku.core.board.VeilguardAi
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.games.board.DuelSession
import com.puresudoku.ui.games.board.coachRings
import com.puresudoku.ui.games.board.glyph
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.LessonStep
import com.puresudoku.ui.session.MiddleStat
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.tutorial.DemoStage
import com.puresudoku.ui.tutorial.DemoTargets
import com.puresudoku.ui.tutorial.TutorialStep
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.random.Random

internal val VSPELL_ICONS = listOf(RI.KNIGHT, RI.RELIC, RI.MOON, RI.FIRE, RI.LEY)

// =====================================================================  session

class VeilguardSession(difficulty: Difficulty, val g: Veilguard) : DuelSession(GameKind.VEILGUARD, difficulty) {
    enum class Mode { NONE, MARCH, WALL, RALLY, SIEGE }

    var selected by mutableIntStateOf(-1)
    var mode by mutableStateOf(Mode.NONE)
    var events by mutableStateOf<List<String>>(emptyList()); private set

    private val lvl get() = VeilguardAi.level(aiDifficulty)

    override val middleStat get() = MiddleStat.Counter("Round", "${g.round}/${Veilguard.ROUND_CAP}")
    override val progress: Float get() = (g.round / Veilguard.ROUND_CAP.toFloat()).coerceAtMost(0.95f)
    override val wonTitle get() = if (g.endReason == 1) "You found the Crown!" else "Veilguard is yours!"
    override val lostTitle get() = if (g.endReason == 1) "Your Crown was taken" else "The rival rules Veilguard"
    override val lostMessage get() = when (g.endReason) {
        2 -> "You lost your last land. "
        3 -> "Neither Crown fell in ${Veilguard.ROUND_CAP} rounds, and the rival led on points. "
        else -> ""
    } + "This game's save slot has been cleared."

    val myMove get() = outcome == Outcome.PLAYING && !thinking && !heraldBusy && g.phase != Veilguard.OVER &&
        (g.phase == Veilguard.PLAN || g.actor == 0)

    private fun flush() { if (g.log.isNotEmpty()) { events = (events + g.log).takeLast(6); g.log.clear() } }

    fun targets(): List<Int> = when {
        !myMove -> emptyList()
        g.phase == Veilguard.SETUP -> g.settleSpots(0)
        mode == Mode.MARCH && selected >= 0 -> g.map.hexNbrs[selected].filter { g.kind[it] != Veilguard.WATER }
        mode == Mode.WALL && selected >= 0 -> g.map.hexNbrs[selected].filter { n -> g.side(selected, n).let { it >= 0 && g.wall[it] < 0 } }
        mode == Mode.RALLY -> g.rallySpots(0)
        mode == Mode.SIEGE -> g.siegeTargets(0)
        else -> emptyList()
    }

    fun tapTile(h: Int) {
        if (!myMove) return
        when {
            g.phase == Veilguard.SETUP -> if (h in g.settleSpots(0)) { g.settle(h); flush(); bump(); changed(); aiSetup() }
            mode == Mode.MARCH -> { if (h in targets()) order(VOrder.March(selected, h)); mode = Mode.NONE }
            mode == Mode.WALL -> { if (h in targets()) order(VOrder.Wall(selected, h)); mode = Mode.NONE }
            mode == Mode.RALLY || mode == Mode.SIEGE -> {
                if (h in targets() && g.castSpell(0, if (mode == Mode.RALLY) Veilguard.RALLY else Veilguard.SIEGE, h)) changed()
                mode = Mode.NONE
            }
            else -> selected = if (selected == h) -1 else h
        }
        bump()
    }

    fun order(o: VOrder) {
        if (!myMove) return
        if (g.orders[0].size >= Veilguard.MAX_ORDERS) { nudge("You've given all ${Veilguard.MAX_ORDERS} orders. Cancel one first"); return }
        if (!g.place(0, o)) { nudge("You can't give that order"); return }
        coachTip = null; coachFrom = -1
        bump(); changed()
    }
    fun cancel(i: Int) { if (myMove) { g.cancel(0, i); bump(); changed() } }
    fun toggleMode(m: Mode) { if (myMove) { mode = if (mode == m) Mode.NONE else m; bump() } }
    fun trade(give: Int, get: Int) { if (myMove && g.trade(0, give, get)) { bump(); changed() } }
    fun buySpell() { if (myMove && g.buySpell(0)) { flush(); bump(); changed() } }
    fun uncast() { if (myMove) { g.uncast(0); bump(); changed() } }
    fun cast(kind: Int, a: Int = -1, b: Int = -1) {
        if (!myMove || !g.canCast(0, kind)) { nudge("You can't cast that now"); return }
        when (kind) {
            Veilguard.RALLY -> { mode = Mode.RALLY; bump() }
            Veilguard.SIEGE -> { mode = Mode.SIEGE; bump() }
            else -> if (g.castSpell(0, kind, a, b)) { bump(); changed() }
        }
    }
    fun castText(): String? {
        val c = g.cast[0]; if (c[0] < 0) return null
        return Veilguard.SPELLS[c[0]] + if (c[0] == Veilguard.RALLY || c[0] == Veilguard.SIEGE) " at ${g.tileName(c[1]).removePrefix("the ")}" else ""
    }

    /** Seal your orders: the rival chooses its own, then all are turned face up together. */
    fun reveal() {
        if (!myMove || g.phase != Veilguard.PLAN) return
        mode = Mode.NONE; selected = -1; coachTip = null; coachFrom = -1
        val snap = g.copy(); val l = lvl; val seed = rng.nextLong()
        computer(minMs = 700, think = { VeilguardAi.plan(snap, 1, l, Random(seed)) }) { plan ->
            for ((a, b) in plan.trades) g.trade(1, a, b)
            for (o in plan.orders) g.place(1, o)
            plan.cast?.let { g.castSpell(1, it[0], it[1], it[2]) }
            repeat(plan.buys) { g.buySpell(1) }
            val mine = g.orders[0].size; val theirs = g.orders[1].size
            moves++
            g.resolve(rng)
            val logs = g.log.toList(); flush(); bump(); changed()
            announce("The orders are revealed", "You gave $mine, the rival gave $theirs", 1300, true, RI.SCROLL)
            for (line in logs.take(7)) announce(line, "", 1700, mine(line), iconFor(line))
            if (g.phase == Veilguard.OVER) { whenQuiet { finishGame() }; return@computer }
            announce("Round ${g.round}", "Your lands have paid out. Give your orders", 1100, true, RI.CROWN)
        }
    }

    private fun mine(line: String) = line.startsWith("You") || line.startsWith("Your")

    private fun iconFor(line: String): RI = when {
        "Thief" in line || "thief" in line -> if ("Dagger" in line) RI.DAGGER else RI.THIEF
        "Crown" in line -> RI.CROWN
        "Siege Fire" in line -> RI.FIRE
        "Veil" in line -> RI.LEY
        "rallied" in line -> RI.KNIGHT
        "Harvest Moon" in line || "learned" in line -> RI.MOON
        "spy" in line -> RI.LEY
        "stormed" in line || "attacked" in line || "Armies" in line || "armies" in line || "marched" in line -> RI.KNIGHT
        "claimed" in line || "claim" in line -> RI.VILLAGE
        "raised" in line -> RI.TOWER
        "decoys" in line || "bluff" in line -> RI.SHIELD
        else -> RI.SCROLL
    }

    private fun aiSetup() {
        if (outcome != Outcome.PLAYING || g.phase != Veilguard.SETUP || g.actor != 1) return
        val snap = g.copy(); val l = lvl; val seed = rng.nextLong()
        computer(minMs = 520, think = { VeilguardAi.settle(snap, 1, l, Random(seed)) }) { h ->
            g.settle(h); val logs = g.log.toList(); flush(); bump(); changed()
            logs.lastOrNull()?.let { announce(it, "", 1300, false, RI.VILLAGE) }
            if (g.phase == Veilguard.PLAN) announce("Round 1", "Give up to ${Veilguard.MAX_ORDERS} secret orders, then reveal", 1500, true, RI.CROWN)
            else whenQuiet { aiSetup() }
        }
    }

    private fun finishGame() {
        when (g.winner) {
            0 -> win(listOf(BonusLine("Points", g.vp(0) * rules.basePoints * 2), BonusLine("Lands held", g.tiles(0) * rules.basePoints)) +
                if (g.endReason == 1) listOf(BonusLine("Crown taken", rules.basePoints * 10)) else emptyList())
            2 -> draw()
            else -> lose()
        }
    }

    val prompt: String get() = when {
        outcome != Outcome.PLAYING -> ""
        heraldBusy -> "Watch the orders play out · tap a card to skip"
        thinking -> "The rival is giving its secret orders…"
        g.phase == Veilguard.SETUP -> if (g.actor == 0) (if (g.crown[0] < 0) "Settle your first land. Your Crown hides there" else "Settle your second land") else "The rival is settling…"
        mode == Mode.MARCH -> "Tap a touching tile to march every banner there"
        mode == Mode.WALL -> "Tap a neighbouring tile to wall that side"
        mode == Mode.RALLY -> "Tap one of your tiles for the rallied knight"
        mode == Mode.SIEGE -> "Tap a rival tile next to yours to hurl Siege Fire"
        g.orders[0].size >= Veilguard.MAX_ORDERS -> "All orders given. Reveal them"
        selected >= 0 -> "Give an order for this tile (${Veilguard.MAX_ORDERS - g.orders[0].size} left)"
        else -> "Tap a tile to give it orders, then reveal"
    }

    fun orderText(o: VOrder): String = when (o) {
        is VOrder.March -> "March to ${g.tileName(o.to).removePrefix("the ")}"
        is VOrder.Muster -> (if (o.decoy) "Decoy" else "Knight") + " at ${g.tileName(o.h).removePrefix("the ")}"
        is VOrder.Build -> (if (g.level[o.h] <= 1) "Tower" else "Castle") + " at ${g.tileName(o.h).removePrefix("the ")}"
        is VOrder.Wall -> "Wall at ${g.tileName(o.a).removePrefix("the ")}"
        is VOrder.Fortify -> "Fortify ${g.tileName(o.h).removePrefix("the ")}"
        is VOrder.Claim -> "Claim ${g.tileName(o.h).removePrefix("the ")}"
        is VOrder.Scout -> "Scout ${g.tileName(o.h).removePrefix("the ")}"
        is VOrder.Crown -> "Crown to ${g.tileName(o.h).removePrefix("the ")}"
    }

    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (g.phase != Veilguard.PLAN) return null
        val snap = g.copy()
        return {
            val (_, orders) = VeilguardAi.plan(snap, 0, VeilguardAi.level(Difficulty.EXTREME), Random(7))
            val o = orders.firstOrNull { it !in snap.orders[0] }
            if (o == null) Triple("Your orders look good: reveal them.", -1, -1)
            else Triple("Try: " + orderText(o) + ".", when (o) {
                is VOrder.March -> o.to; is VOrder.Muster -> o.h; is VOrder.Build -> o.h; is VOrder.Wall -> o.a
                is VOrder.Fortify -> o.h; is VOrder.Claim -> o.h; is VOrder.Scout -> o.h; is VOrder.Crown -> o.h
            }, -1)
        }
    }

    override fun resume() {
        if (outcome != Outcome.PLAYING) return
        if (g.phase == Veilguard.OVER) { finishGame(); return }
        aiSetup()
    }

    override fun saveState(): JSONObject = JSONObject().put("s", JSONArray().also { a -> g.state().forEach { a.put(it) } }).put("moves", moves)

    companion object {
        fun restore(o: JSONObject): VeilguardSession {
            val st = o.getJSONObject("state").getJSONArray("s")
            val g = Veilguard.fromState(IntArray(st.length()) { st.getInt(it) })
            return VeilguardSession(Difficulty.valueOf(o.getString("difficulty")), g).also { it.moves = o.getJSONObject("state").optInt("moves"); it.restoreCommon(o) }
        }
    }
}

// =====================================================================  definition

object VeilguardDef : GameDef {
    override val kind = GameKind.VEILGUARD
    override val fullBleed: Boolean get() = true
    override val hasHeader: Boolean get() = true
    @Composable override fun Header(session: Session, actions: com.puresudoku.ui.games.HeaderActions) = RealmHeader(session, actions, "Veilguard · ")

    override fun generate(d: Difficulty): Session = VeilguardSession(d, Veilguard.newGame(d, Random(System.nanoTime())))
    override fun restore(o: JSONObject): Session = VeilguardSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Give secret orders, hide your Crown, and bluff with decoy banners. Take the rival's Crown to win at once. Beware the Veilnight Thief."
    override fun background(session: Session) = com.puresudoku.R.drawable.bg_realm_ocean
    override fun levelInfo(d: Difficulty): String {
        val g = Veilguard.newGame(d, Random(1))
        return "Take the rival's Crown · ${g.H} tiles · thief wrecks every ${Veilguard.SABOTAGE[d.ordinal]} rounds"
    }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A trusting rival that rarely bluffs."
        Difficulty.MEDIUM -> "Starts hiding its Crown and scouting yours."
        Difficulty.HARD -> "Plants decoys and slips its Crown away."
        Difficulty.EXPERT -> "Scouts often and strikes where you're weak."
        Difficulty.MASTER -> "A master of feints, on a big island."
        Difficulty.EXTREME -> "Its best deceptions, on the biggest island."
    }
    @Composable override fun Play(session: Session) = VeilguardUi(session as VeilguardSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override fun lesson(): Pair<Session, List<LessonStep>> {
        val s = VeilguardSession(Difficulty.EASY, Veilguard.newGame(Difficulty.EASY, Random(777)))
        return s to listOf(
            LessonStep("Veilguard is a game of secrets. Each round you and the rival give up to three hidden orders, then they're all revealed and carried out together."),
            LessonStep("Settle your first land: tap a glowing tile. Your Crown hides there. If the rival ever takes the tile holding your Crown, you lose at once.") { (it as VeilguardSession).g.tiles(0) >= 1 },
            LessonStep("Settle a second land.") { (it as VeilguardSession).g.phase != Veilguard.SETUP || it.outcome != Outcome.PLAYING },
            LessonStep("Your armies are banners. The rival sees how many, but not which are knights and which are cheap decoys. Use decoys to bluff."),
            LessonStep("Tap a tile and choose orders: March, Knight, Decoy, Build, Wall, Fortify, or slip your Crown to a touching tile you hold. Tap free land to Claim it, or a rival tile to Scout it."),
            LessonStep("Attacks win only if your knights outnumber the defenders, plus 1 for a tower or 2 for a castle, 2 if fortified and 2 through a wall."),
            LessonStep("The Veilnight Thief creeps one tile toward the leader every round. On any land he stands on he steals, and every few rounds he takes 1 health from the building there: village 1, tower 2, castle 3, never healing. Walls can't stop him. March knights onto him to fight him: each one dies wounding him."),
            LessonStep("Kill the thief and you win the Thief's Dagger (+1 point), and he robs and spies on your rival for you."),
            LessonStep("Spells cost ore, grain and beasts, and don't use an order. Cast one a round in secret: Rally, Harvest Moon, Siege Fire to burn banners before marches, or Veil to make the rival forget what it learned. Now give your orders and reveal them!"),
        )
    }

    override val tutorial: List<TutorialStep> get() = listOf(
        TutorialStep("Secret orders", "Both players give up to three hidden orders. Then they're revealed and played out together.") { active ->
            val s = remember { VeilguardSession(Difficulty.EASY, Veilguard.newGame(Difficulty.EASY, Random(5))) }
            DemoStage(active, script = { wait(2400) }) { t -> VeilguardUi(s, t) }
        },
        TutorialStep("Find the Crown", "Hide your Crown and bluff with decoys. Take the rival's Crown to win at once, and beware the Veilnight Thief: he robs and wrecks the leader's land.") { active ->
            val s = remember { VeilguardSession(Difficulty.EASY, Veilguard.newGame(Difficulty.EASY, Random(5))) }
            DemoStage(active, script = { wait(2400) }) { t -> VeilguardUi(s, t) }
        },
    )
}

// =====================================================================  board

private class VeilArt(val water: ImageBitmap, val knight: ImageBitmap, val thief: ImageBitmap)

@Composable
private fun rememberVeilArt() = VeilArt(
    ImageBitmap.imageResource(com.puresudoku.R.drawable.rt_water),
    ImageBitmap.imageResource(com.puresudoku.R.drawable.rt_knight_piece),
    ImageBitmap.imageResource(com.puresudoku.R.drawable.rt_thief),
)

private val SHADOW = Color(0xFF7E57C2)

private fun DrawScope.vCastle(p: Offset, u: Float, color: Color) {
    val s = 0.24f * u
    for (dx in listOf(-1.05f, 1.05f)) {
        val tp = Path().apply {
            moveTo(p.x + dx * s - s * 0.38f, p.y + s); lineTo(p.x + dx * s - s * 0.38f, p.y - s * 1.2f); lineTo(p.x + dx * s, p.y - s * 1.75f)
            lineTo(p.x + dx * s + s * 0.38f, p.y - s * 1.2f); lineTo(p.x + dx * s + s * 0.38f, p.y + s); close()
        }
        drawPath(tp, Color.Black.copy(alpha = 0.35f), style = Stroke(s * 0.45f)); drawPath(tp, color); drawPath(tp, Color.White, style = Stroke(s * 0.12f))
    }
    tower(p, u * 1.05f, color)
}

/** A banner on a pole, in the owner's colour, with how many banners stand on the tile. */
private fun DrawScope.bannerStack(at: Offset, u: Float, color: Color, label: String) {
    val pole = u * 0.42f
    drawLine(Color(0xFF3A2A18), at, at - Offset(0f, pole), u * 0.04f, StrokeCap.Round)
    val top = at - Offset(0f, pole)
    val flag = Path().apply {
        moveTo(top.x, top.y); lineTo(top.x + u * 0.3f, top.y + u * 0.02f); lineTo(top.x + u * 0.22f, top.y + u * 0.11f)
        lineTo(top.x + u * 0.3f, top.y + u * 0.2f); lineTo(top.x, top.y + u * 0.2f); close()
    }
    drawPath(flag, Color.Black.copy(alpha = 0.35f), style = Stroke(u * 0.05f)); drawPath(flag, color)
    val b = at + Offset(u * 0.2f, -u * 0.04f)
    val w = u * (0.12f + 0.09f * label.length)
    drawRoundRect(Color(0xFF1B1430).copy(alpha = 0.88f), b - Offset(w / 2, u * 0.13f), Size(w, u * 0.26f), CornerRadius(u * 0.13f))
    glyph(label, b, u * 0.18f, Color.White)
}

private fun DrawScope.orderToken(c: Offset, u: Float, icon: RI) {
    drawCircle(Color.Black.copy(alpha = 0.35f), u * 0.21f, c + Offset(0f, u * 0.02f))
    drawCircle(Color(0xFFFFD54F), u * 0.2f, c)
    drawCircle(Color(0xFF3A2A18), u * 0.2f, c, style = Stroke(u * 0.025f))
    actionIcon(icon, c, u * 0.28f)
}

private fun orderIcon(o: VOrder) = when (o) {
    is VOrder.Muster -> if (o.decoy) RI.SHIELD else RI.KNIGHT; is VOrder.Build -> RI.TOWER; is VOrder.Fortify -> RI.SHIELD
    is VOrder.Claim -> RI.VILLAGE; is VOrder.Scout -> RI.LEY; is VOrder.Crown -> RI.CROWN; else -> RI.ROAD
}

@Composable
fun VeilguardBoard(s: VeilguardSession, modifier: Modifier = Modifier, fitTop: Float = 0f, fitBottom: Float = 0f) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "glow")
    val art = rememberRealmArt(); val vart = rememberVeilArt()
    val lit = s.targets()
    var zoom by remember(s) { mutableFloatStateOf(1f) }
    var pan by remember(s) { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.clipToBounds()) {
        val wPx = with(LocalDensity.current) { maxWidth.toPx() }; val hPx = with(LocalDensity.current) { maxHeight.toPx() }
        val fitH = (hPx - fitTop - fitBottom).coerceAtLeast(hPx * 0.35f)
        val home = Offset(wPx / 2, fitTop + fitH / 2)
        fun clampPan(p: Offset, z: Float): Offset {
            if (z <= 1.001f) return Offset.Zero
            val r = min(wPx, fitH) / 2f * z
            val mx = (r - wPx / 2f + wPx * 0.3f).coerceAtLeast(0f); val my = (r - hPx / 2f + hPx * 0.3f).coerceAtLeast(0f)
            return Offset(p.x.coerceIn(-mx, mx), p.y.coerceIn(-my, my))
        }
        fun zoomBy(k: Float, focus: Offset = home) {
            val nz = (zoom * k).coerceIn(1f, 4f); val f = focus - home
            pan = clampPan((pan - f) * (nz / zoom) + f, nz); zoom = nz
        }
        Canvas(Modifier.fillMaxSize().pointerInput(s) {
            detectTransformGestures { centroid, panChange, zoomChange, _ -> zoomBy(zoomChange, centroid); pan = clampPan(pan + panChange, zoom) }
        }.pointerInput(s, lit, s.mode) {
            detectTapGestures { o ->
                val geo = RGeo(g.map, size.width.toFloat(), size.height.toFloat(), zoom, pan, fitTop, fitBottom)
                nearest(o, (0 until g.H).toList(), geo::hex, geo.unit * 0.95f)?.let { s.tapTile(it) }
            }
        }) {
            val geo = RGeo(g.map, size.width, size.height, zoom, pan, fitTop, fitBottom); val u = geo.unit; val m = g.map
            run {
                val k = geo.art * 1254f / art.island.width; val o = geo.artPt(0f, 0f)
                drawImage(art.island, dstOffset = IntOffset(o.x.toInt(), o.y.toInt()), dstSize = IntSize((art.island.width * k).toInt(), (art.island.height * k).toInt()), filterQuality = FilterQuality.Medium)
            }
            // tiles
            for (h in 0 until g.H) {
                val c = geo.hex(h); val tw = (sqrt3 * u).toInt() + 2; val th = (2 * u).toInt() + 2
                val img = when {
                    g.kind[h] == Veilguard.WATER -> vart.water
                    g.kind[h] != Veilguard.LAND -> art.byTerrain[5][0]
                    else -> { val list = art.byTerrain[g.art[h]]; list[(0 until h).count { g.kind[it] == Veilguard.LAND && g.art[it] == g.art[h] } % list.size] }
                }
                drawImage(img, dstOffset = IntOffset((c.x - tw / 2f).toInt(), (c.y - th / 2f).toInt()), dstSize = IntSize(tw, th), filterQuality = FilterQuality.Medium)
                if (g.kind[h] == Veilguard.HIDEOUT) drawPath(hexPath(geo, h, 1f), Color(0xFF1B0F2E).copy(alpha = 0.55f))
                if (g.kind[h] == Veilguard.BARREN) drawPath(hexPath(geo, h, 1f), Color(0xFF3B2A1A).copy(alpha = 0.35f))
            }
            // owner tint and gold borders
            for (h in 0 until g.H) {
                val o = g.owner[h]
                if (o >= 0) drawPath(hexPath(geo, h, 0.99f), (if (o == 0) YOU else RIVAL).copy(alpha = 0.16f))
                val p = hexPath(geo, h, 1f)
                drawPath(p, Color(0xFF4A3510), style = Stroke(u * 0.08f))
                drawPath(p, Brush.linearGradient(listOf(Color(0xFFF6E3A1), Color(0xFFC9972F), Color(0xFFF2D27A)), start = geo.hex(h) - Offset(u, u), end = geo.hex(h) + Offset(u, u)), style = Stroke(u * 0.05f))
                if (o >= 0) drawPath(hexPath(geo, h, 0.9f), if (o == 0) YOU else RIVAL, style = Stroke(u * 0.07f))
            }
            // yields, buildings, banners, crowns
            for (h in 0 until g.H) {
                val c = geo.hex(h)
                if (g.kind[h] == Veilguard.LAND) {
                    val ys = (0..4).filter { g.yields[h * 5 + it] > 0 }
                    val bw = u * 0.36f; val y0 = c.y - u * 0.55f
                    val totalW = ys.size * bw + (ys.size - 1) * u * 0.04f
                    val pill = Rect(c.x - totalW / 2 - u * 0.06f, y0 - bw / 2 - u * 0.03f, c.x + totalW / 2 + u * 0.06f, y0 + bw / 2 + u * 0.03f)
                    drawRoundRect(Color(0xFFF8EED6).copy(alpha = 0.92f), pill.topLeft, pill.size, CornerRadius(u * 0.2f))
                    drawRoundRect(Color(0xFFC9972F), pill.topLeft, pill.size, CornerRadius(u * 0.2f), style = Stroke(u * 0.025f))
                    ys.forEachIndexed { i, r ->
                        val cx = c.x - totalW / 2 + bw / 2 + i * (bw + u * 0.04f)
                        val ic = art.icons[r]; val iw = bw * 0.82f; val ih = iw * ic.height / ic.width
                        drawImage(ic, dstOffset = IntOffset((cx - iw / 2).toInt(), (y0 - ih / 2).toInt()), dstSize = IntSize(iw.toInt(), ih.toInt()), filterQuality = FilterQuality.Medium)
                        val n = g.yields[h * 5 + r]
                        if (n > 1) glyph("×$n", Offset(cx + bw * 0.42f, y0 + bw * 0.32f), u * 0.17f, Color(0xFF3A2A18))
                    }
                }
                val o = g.owner[h]; if (o < 0) continue
                val col = if (o == 0) YOU else RIVAL
                when (g.level[h]) {
                    1 -> { val img = if (o == 0) art.houseYou else art.houseRival; val w = u * 0.55f; val hh = w * img.height / img.width
                        drawImage(img, dstOffset = IntOffset((c.x - w / 2).toInt(), (c.y - hh * 0.55f).toInt()), dstSize = IntSize(w.toInt(), hh.toInt()), filterQuality = FilterQuality.Medium) }
                    2 -> tower(c + Offset(0f, u * 0.05f), u * 1.1f, col)
                    3 -> vCastle(c + Offset(0f, u * 0.05f), u * 1.1f, col)
                }
                // damaged buildings: their health, which never heals
                if (g.scar[h] > 0) {
                    val lv = g.level[h]; val r = u * 0.085f; val gap = r * 2.6f
                    for (k in 0 until lv) {
                        val p = c + Offset((k - (lv - 1) / 2f) * gap, -u * 0.3f)
                        drawCircle(Color(0xFF2A0606), r * 1.3f, p)
                        drawCircle(if (k < g.hp(h)) Color(0xFFE53935) else Color(0xFF5A4A4A), r, p)
                    }
                }
                val n = g.banners(h)
                if (n > 0) {
                    val label = when {
                        o == 0 -> "${g.knights[h]}+${g.decoys[h]}"
                        g.seenRound[0][h] == g.round - 1 && g.seenRound[0][h] >= 0 -> "${g.seenKnights[0][h]}+${g.seenDecoys[0][h]}"
                        else -> "$n"
                    }
                    bannerStack(c + Offset(-u * 0.42f, u * 0.62f), u, col, label)
                }
                // your Crown, and where you last saw the rival's
                val crownHere = (o == 0 && g.crown[0] == h) || (o == 1 && g.seenCrown[0] == h)
                if (crownHere) {
                    val p = c + Offset(u * 0.42f, u * 0.3f)
                    drawCircle(Color(0xFF1B1430).copy(alpha = 0.85f), u * 0.2f, p)
                    drawCircle(Color(0xFFF6E3A1), u * 0.2f, p, style = Stroke(u * 0.03f))
                    actionIcon(RI.CROWN, p, u * 0.3f)
                    if (o == 1) glyph("?", p + Offset(u * 0.16f, -u * 0.14f), u * 0.18f, Color(0xFFFF8A80))
                }
            }
            // walls
            for (e in 0 until m.EDGES) {
                val o = g.wall[e]; if (o < 0) continue
                val a = geo.vert(m.edgeA[e]); val b = geo.vert(m.edgeB[e]); val d = (b - a) * 0.08f
                drawLine(Color(0xFF2B2B2B), a + d, b - d, u * 0.2f, StrokeCap.Round)
                drawLine(Color(0xFFB8B2A6), a + d, b - d, u * 0.14f, StrokeCap.Round)
                drawLine(if (o == 0) YOU else RIVAL, a + d, b - d, u * 0.05f, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.08f, u * 0.06f)))
            }
            // your secret orders
            for (o in g.orders[0]) when (o) {
                is VOrder.March -> {
                    val a = geo.hex(o.from); val b = geo.hex(o.to); val d = b - a; val l = d.getDistance(); val dir = d / l
                    val end = b - dir * u * 0.3f
                    drawLine(Color.Black.copy(alpha = 0.45f), a, end, u * 0.13f, StrokeCap.Round)
                    drawLine(Color(0xFFFFD54F), a, end, u * 0.07f, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.14f, u * 0.08f)))
                    val nrm = Offset(-dir.y, dir.x)
                    val head = Path().apply { moveTo(b.x - dir.x * u * 0.15f, b.y - dir.y * u * 0.15f); val bb = end - dir * u * 0.1f
                        lineTo(bb.x + nrm.x * u * 0.16f, bb.y + nrm.y * u * 0.16f); lineTo(bb.x - nrm.x * u * 0.16f, bb.y - nrm.y * u * 0.16f); close() }
                    drawPath(head, Color(0xFFFFD54F))
                }
                is VOrder.Wall -> { val e = g.side(o.a, o.b); if (e >= 0) drawLine(Color(0xFFFFD54F).copy(alpha = 0.6f + 0.4f * pulse), geo.vert(m.edgeA[e]), geo.vert(m.edgeB[e]), u * 0.16f, StrokeCap.Round) }
                else -> {
                    val h = when (o) { is VOrder.Muster -> o.h; is VOrder.Build -> o.h; is VOrder.Fortify -> o.h; is VOrder.Claim -> o.h; is VOrder.Scout -> o.h; is VOrder.Crown -> o.h; else -> -1 }
                    if (h >= 0) {
                        val same = g.orders[0].filter { x -> x !is VOrder.March && x !is VOrder.Wall }.let { l -> l.indexOf(o) to l }
                        orderToken(geo.hex(h) + Offset(u * (-0.4f + 0.42f * (same.first % 3)), -u * 0.05f), u, orderIcon(o))
                    }
                }
            }
            // the Veilnight Thief: where he's heading, then the thief himself
            g.thiefPlan()?.let { (goal, _) ->
                drawCircle((if (g.thiefWild) SHADOW else if (g.thiefOwner == 0) YOU else RIVAL).copy(alpha = 0.35f + 0.5f * pulse), u * 0.62f, geo.hex(goal),
                    style = Stroke(u * 0.07f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.16f, u * 0.1f))))
            }
            run {
                val c = geo.hex(g.thief); val img = vart.thief; val hh = u * 1.15f; val w = hh * img.width / img.height
                val glow = if (g.thiefWild) SHADOW else if (g.thiefOwner == 0) YOU else RIVAL
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.7f), glow.copy(alpha = 0.25f), Color.Transparent), center = c, radius = u * 0.85f), u * 0.85f, c)
                drawImage(img, dstOffset = IntOffset((c.x - w / 2).toInt(), (c.y - hh * 0.55f).toInt()), dstSize = IntSize(w.toInt(), hh.toInt()), filterQuality = FilterQuality.Medium)
                if (g.thiefWild) {
                    val bw = u * 0.8f; val bh = u * 0.12f; val bt = Offset(c.x - bw / 2, c.y - u * 0.76f)
                    drawRoundRect(Color(0xFF1B0F2E), bt - Offset(u * 0.03f, u * 0.03f), Size(bw + u * 0.06f, bh + u * 0.06f), CornerRadius(bh))
                    drawRoundRect(Color(0xFFB388FF), bt, Size(bw * g.thiefHp / Veilguard.THIEF_HP, bh), CornerRadius(bh / 2))
                    for (k in 1 until Veilguard.THIEF_HP) drawLine(Color(0xFF1B0F2E), bt + Offset(bw * k / Veilguard.THIEF_HP, 0f), bt + Offset(bw * k / Veilguard.THIEF_HP, bh), u * 0.015f)
                }
            }
            // highlights and selection
            for (h in lit) drawPath(hexPath(geo, h, 0.84f), (if (s.mode == VeilguardSession.Mode.MARCH) Color(0xFFFF7043) else Color.White).copy(alpha = pulse), style = Stroke(u * 0.07f))
            val cf = s.coachFrom
            if (cf >= 0) coachRings(listOf(geo.hex(cf)), u * 0.5f)
            if (s.selected >= 0) drawPath(hexPath(geo, s.selected, 0.94f), Color(0xFFFFD54F), style = Stroke(u * 0.09f))
        }
    }
}

// =====================================================================  panels

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VeilguardUi(s: VeilguardSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val full = targets == null
    var topH by remember { mutableIntStateOf(0) }
    var botH by remember { mutableIntStateOf(0) }
    var showTrade by remember { mutableStateOf(false) }
    var showSpells by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        VeilguardBoard(s, Modifier.fillMaxSize(), topH.toFloat(), botH.toFloat())
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topH = it.height }
                .then(if (full) Modifier.statusBarsPadding().padding(top = 54.dp) else Modifier).padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerBanner("You", "${g.vp(0)}", YOU, g.phase == Veilguard.PLAN, (if (g.thiefOwner == 0) "Dagger · " else "") + "${g.tiles(0)} lands", Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RIconOf(RI.CROWN, 22.dp)
                    Text("vs", fontFamily = SERIF, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(0f, 1f), 4f)))
                }
                PlayerBanner("Rival", "${g.vp(1, false)}" + if (g.spells[1].sum() > 0) "+" else "", RIVAL, s.thinking, (if (g.thiefOwner == 1) "Dagger · " else "") + "${g.tiles(1)} lands · ${g.total(1)} cards", Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.parchment(RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                RIconOf(if (g.thiefWild) RI.THIEF else RI.DAGGER, 18.dp)
                Spacer(Modifier.width(6.dp))
                val head = when (g.thiefPlan()?.let { g.owner[it.first] }) { 0 -> " · after you"; 1 -> " · after the rival"; else -> "" }
                InkText(
                    (if (g.phase == Veilguard.PLAN) "Round ${g.round} · " else "") +
                        when { g.thiefWild -> "Thief ${g.thiefHp}/${Veilguard.THIEF_HP} · " + (if (g.sabotageIn <= 0) "sabotage ready" else "sabotage in ${g.sabotageIn}") + head; g.thiefOwner == 0 -> "Your thief robs the rival"; else -> "The rival's thief robs you" },
                    12, bold = true,
                )
            }
        }

        HeraldCard(s, Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { botH.toDp() } + 6.dp).padding(horizontal = 12.dp))

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { botH = it.height }
                .then(if (full) Modifier.navigationBarsPadding() else Modifier).padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).parchment(PANEL).padding(horizontal = 10.dp, vertical = 3.dp)) {
                InkText(s.events.lastOrNull() ?: "The veil falls over the island…", 12, color = INK_SOFT, lines = 2)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (r in 0..4) ResChip(r, g.res[0][r], Modifier.weight(1f))
            }
            Spacer(Modifier.height(5.dp))
            PromptRibbon(s.prompt, s.thinking || s.heraldBusy)
            Spacer(Modifier.height(5.dp))
            if (s.myMove && g.phase == Veilguard.PLAN) {
                // the orders you've given, face down to the rival
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 0 until Veilguard.MAX_ORDERS) {
                        val o = g.orders[0].getOrNull(i)
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .then(if (o != null) Modifier.parchment(RoundedCornerShape(10.dp), Color(0xFFC9972F), 1.5.dp).clickable { s.cancel(i) }
                                else Modifier.background(Color.Black.copy(alpha = 0.35f)))
                                .padding(horizontal = 6.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (o == null) InkText("Order ${i + 1}", 11, color = Color.White.copy(alpha = 0.7f))
                            else { InkText(s.orderText(o), 11, bold = true, lines = 2, modifier = Modifier.weight(1f)); InkText("✕", 12, color = INK_SOFT) }
                        }
                    }
                }
                s.castText()?.let { txt ->
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp)).parchment(RoundedCornerShape(10.dp), Color(0xFF7B4FB0), 1.5.dp)
                            .clickable { s.uncast() }.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { RIconOf(VSPELL_ICONS[g.cast[0][0]], 18.dp); Spacer(Modifier.width(6.dp)); InkText("Spell: $txt", 11, bold = true); Spacer(Modifier.width(6.dp)); InkText("✕", 12, color = INK_SOFT) }
                }
                Spacer(Modifier.height(5.dp))
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val h = s.selected
                    val room = g.orders[0].size < Veilguard.MAX_ORDERS
                    if (h >= 0 && g.owner[h] == 0) {
                        BuildButton("March", RI.KNIGHT, null, s.mode == VeilguardSession.Mode.MARCH, room && g.banners(h) > 0 && g.orders[0].none { it is VOrder.March && it.from == h }) { s.toggleMode(VeilguardSession.Mode.MARCH) }
                        BuildButton("Knight", RI.KNIGHT, Veilguard.COST_KNIGHT, false, g.canOrder(0, VOrder.Muster(h, false))) { s.order(VOrder.Muster(h, false)) }
                        BuildButton("Decoy", RI.SHIELD, Veilguard.COST_DECOY, false, g.canOrder(0, VOrder.Muster(h, true))) { s.order(VOrder.Muster(h, true)) }
                        if (g.level[h] < 3) BuildButton(if (g.level[h] == 1) "Tower" else "Castle", RI.TOWER, g.upgradeCost(h), false, g.canOrder(0, VOrder.Build(h))) { s.order(VOrder.Build(h)) }
                        BuildButton("Wall", RI.SHIELD, Veilguard.COST_WALL, s.mode == VeilguardSession.Mode.WALL, room && g.can(0, Veilguard.COST_WALL)) { s.toggleMode(VeilguardSession.Mode.WALL) }
                        BuildButton("Fortify", RI.SHIELD, Veilguard.COST_FORTIFY, false, g.canOrder(0, VOrder.Fortify(h))) { s.order(VOrder.Fortify(h)) }
                        if (g.crown[0] != h && g.crown[0] >= 0 && h in g.map.hexNbrs[g.crown[0]]) BuildButton("Crown here", RI.CROWN, null, false, g.canOrder(0, VOrder.Crown(h))) { s.order(VOrder.Crown(h)) }
                    }
                    if (h >= 0 && g.owner[h] < 0 && g.ownable(h) && g.map.hexNbrs[h].any { g.owner[it] == 0 })
                        BuildButton("Claim", RI.VILLAGE, g.claimCost(0, h), false, g.canOrder(0, VOrder.Claim(h))) { s.order(VOrder.Claim(h)) }
                    if (h >= 0 && g.owner[h] == 1) BuildButton("Scout", RI.LEY, Veilguard.COST_SCOUT, false, g.canOrder(0, VOrder.Scout(h))) { s.order(VOrder.Scout(h)) }
                    BuildButton("Spell", RI.SCROLL, Veilguard.COST_SPELL, false, g.can(0, Veilguard.COST_SPELL) && g.deck.isNotEmpty()) { s.buySpell() }
                    BuildButton("Spells ${g.spells[0].sum()}", RI.BOOK, null, false, g.spells[0].sum() > 0) { showSpells = true }
                    BuildButton("Trade", RI.TRADE, null, false, true) { showTrade = true }
                    BuildButton("Reveal orders", RI.END, null, false, true) { s.reveal() }
                }
            } else Spacer(Modifier.height(44.dp))
            Spacer(Modifier.height(if (full) 10.dp else 4.dp))
        }
    }
    if (showTrade) VTradeDialog(s) { showTrade = false }
    if (showSpells) SpellbookDialog(Veilguard.SPELLS, Veilguard.SPELL_TEXT, VSPELL_ICONS, g.spells[0], g.fresh[0],
        if (g.cast[0][0] >= 0) "You're already casting a spell this round." else "One spell a round, cast when the orders are revealed.", Veilguard.RELIC, Veilguard.HARVEST,
        { g.canCast(0, it) }, { k, a, b -> s.cast(k, a, b) }) { showSpells = false }
}

@Composable
private fun VTradeDialog(s: VeilguardSession, onDismiss: () -> Unit) {
    val g = s.g
    var give by remember { mutableIntStateOf(-1) }
    var get by remember { mutableIntStateOf(-1) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = PARCH_TOP, titleContentColor = INK, textContentColor = INK,
        icon = { RIconOf(RI.TRADE, 32.dp) },
        title = { Text("The shadow market", fontFamily = SERIF, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                InkText("You give 4", 13, bold = true); Spacer(Modifier.height(4.dp))
                ResPicker(give, { r -> "${g.res[0][r]}" }, { r -> g.res[0][r] >= 4 }) { give = it; if (get == it) get = -1 }
                Spacer(Modifier.height(10.dp))
                InkText("You get 1", 13, bold = true); Spacer(Modifier.height(4.dp))
                ResPicker(get, { null }, { r -> r != give }) { get = it }
            }
        },
        confirmButton = { WoodButton("Trade 4 for 1", null, enabled = give >= 0 && get >= 0 && g.res[0][give] >= 4) { s.trade(give, get); if (g.res[0][give] < 4) give = -1 } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done", color = INK, fontFamily = SERIF, fontWeight = FontWeight.Bold) } },
    )
}
