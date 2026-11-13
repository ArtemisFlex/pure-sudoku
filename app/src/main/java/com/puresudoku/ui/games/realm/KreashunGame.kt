package com.puresudoku.ui.games.realm

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import com.puresudoku.core.board.KAct
import com.puresudoku.core.board.Kreashun
import com.puresudoku.core.board.KreashunAi
import com.puresudoku.core.board.Realm
import com.puresudoku.core.board.RealmMap
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.games.board.CellTargets
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
import com.puresudoku.ui.tutorial.demoTarget
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.random.Random

internal val KSPELL_ICONS = listOf(RI.KNIGHT, RI.RELIC, RI.SHIELD, RI.MOON, RI.FIRE)

// =====================================================================  session

class KreashunSession(difficulty: Difficulty, val g: Kreashun) : DuelSession(GameKind.KREASHUN, difficulty) {
    enum class Mode { NONE, WALL, VILLAGE, TOWER, STRIKE, RALLY, SIEGE }

    var selected by mutableIntStateOf(-1)
    var mode by mutableStateOf(Mode.NONE)
    /** Board code of the rival's last action: corner, 1000 + edge, or 2000 + tile. */
    var lastAi by mutableIntStateOf(-1); private set
    var events by mutableStateOf<List<String>>(emptyList()); private set
    var rollId by mutableIntStateOf(0); private set

    private val lvl get() = KreashunAi.level(aiDifficulty)

    override val middleStat get() = MiddleStat.Counter("Points", "${g.vp(0)}/${g.target}")
    override val progress: Float get() = (g.vp(0) / g.target.toFloat()).coerceAtMost(0.95f)
    override val wonTitle get() = "Kreashun is yours!"
    override val lostTitle get() = "Your rival rules Kreashun"
    override val lostMessage get() = (if (g.vp(1) >= g.target) "The rival reached ${g.vp(1)} points." else "You lost every tile you held.") + " This game's save slot has been cleared."

    val myMove get() = outcome == Outcome.PLAYING && !thinking && !heraldBusy && g.actor == 0 && g.phase != Kreashun.OVER

    private fun flush() { if (g.log.isNotEmpty()) { events = (events + g.log).takeLast(4); g.log.clear() } }

    private fun act(a: KAct): Boolean {
        if (!g.legal(a)) { nudge("You can't do that right now"); return false }
        coachTip = null; coachFrom = -1
        g.apply(a, rng); flush(); bump(); changed()
        if (g.phase == Kreashun.OVER) { whenQuiet { finishGame() }; return true }
        if (g.actor == 1) aiStep()
        return true
    }

    fun vertexTargets(): List<Int> = when {
        !myMove -> emptyList()
        g.phase == Kreashun.SETUP -> if (g.setupVillage < 0) g.villageSpots(0) else emptyList()
        g.phase != Kreashun.MAIN -> emptyList()
        mode == Mode.VILLAGE -> if (g.can(0, Kreashun.COST_VILLAGE)) g.villageSpots(0) else emptyList()
        mode == Mode.TOWER -> if (g.can(0, Kreashun.COST_TOWER)) g.towerSpots(0) else emptyList()
        else -> emptyList()
    }

    fun edgeTargets(): List<Int> = when {
        !myMove -> emptyList()
        g.phase == Kreashun.SETUP -> if (g.setupVillage >= 0) g.wallSpots(0) else emptyList()
        g.phase == Kreashun.MAIN && mode == Mode.WALL && (g.freeWalls > 0 || g.can(0, Kreashun.COST_WALL)) -> g.wallSpots(0)
        else -> emptyList()
    }

    fun tileTargets(): List<Int> = when {
        !myMove || g.phase != Kreashun.MAIN -> emptyList()
        mode == Mode.STRIKE -> g.strikeTargets(0, selected)
        mode == Mode.RALLY -> g.rallySpots(0)
        mode == Mode.SIEGE -> g.siegeTargets(0)
        else -> emptyList()
    }

    fun tapVertex(v: Int) {
        if (!myMove) return
        when {
            g.phase == Kreashun.SETUP -> act(KAct.Village(v))
            mode == Mode.VILLAGE -> if (act(KAct.Village(v))) mode = Mode.NONE
            mode == Mode.TOWER -> if (act(KAct.Tower(v))) mode = Mode.NONE
        }
        bump()
    }

    fun tapEdge(e: Int) {
        if (!myMove) return
        act(KAct.Wall(e))
        if (g.phase == Kreashun.MAIN && g.freeWalls == 0 && !g.can(0, Kreashun.COST_WALL)) mode = Mode.NONE
        bump()
    }

    fun tapTile(h: Int) {
        if (!myMove || g.phase != Kreashun.MAIN) return
        if (mode == Mode.RALLY || mode == Mode.SIEGE) {
            if (h in tileTargets()) act(KAct.Spell(if (mode == Mode.RALLY) Kreashun.RALLY else Kreashun.SIEGE, h))
            mode = Mode.NONE
        } else if (mode == Mode.STRIKE) {
            if (h in tileTargets()) { act(KAct.Strike(selected, h)); if (g.knights.getOrElse(selected) { 0 } == 0) mode = Mode.NONE }
            else mode = Mode.NONE
        } else { mode = Mode.NONE; selected = if (selected == h) -1 else h }
        bump()
    }

    fun roll() { if (myMove && g.phase == Kreashun.ROLL) { rollId++; mode = Mode.NONE; act(KAct.Roll) } }
    fun castle(h: Int) { if (myMove) act(KAct.Castle(h)) }
    fun hire(h: Int) { if (myMove) act(KAct.Hire(h)) }
    fun trade(give: Int, get: Int) { if (myMove) act(KAct.Trade(give, get)) }
    fun buySpell() { if (myMove) act(KAct.BuySpell) }
    fun cast(kind: Int, a: Int = -1, b: Int = -1) {
        if (!myMove || !g.canPlay(0, kind)) { nudge("You can't cast that now"); return }
        when (kind) {
            Kreashun.RALLY -> { mode = Mode.RALLY; bump() }
            Kreashun.SIEGE -> { mode = Mode.SIEGE; bump() }
            Kreashun.STONE -> { if (act(KAct.Spell(kind))) { mode = Mode.WALL; bump() } }
            else -> act(KAct.Spell(kind, a, b))
        }
    }
    fun endTurn() { if (myMove && g.phase == Kreashun.MAIN) { mode = Mode.NONE; selected = -1; moves++; act(KAct.End) } }
    fun toggleMode(m: Mode) { if (myMove) { mode = if (mode == m) Mode.NONE else m; bump() } }

    private fun code(a: KAct): Int = when (a) {
        is KAct.Village -> a.v; is KAct.Tower -> a.v; is KAct.Wall -> 1000 + a.e
        is KAct.Castle -> 2000 + a.h; is KAct.Hire -> 2000 + a.h; is KAct.Strike -> 2000 + a.to
        is KAct.Spell -> if (a.kind == Kreashun.RALLY || a.kind == Kreashun.SIEGE) 2000 + a.a else -1
        else -> -1
    }

    private fun card(a: KAct, logs: List<String>) {
        val detail = logs.joinToString("\n")
        val (icon, title) = when (a) {
            is KAct.Village -> RI.VILLAGE to "Rival builds a village"
            is KAct.Tower -> RI.TOWER to "Rival raises a tower"
            is KAct.Wall -> RI.SHIELD to (if (logs.any { "captured" in it }) "Rival walls in a tile!" else "Rival raises a wall")
            is KAct.Castle -> RI.CROWN to "Rival builds a castle"
            is KAct.Hire -> RI.KNIGHT to "Rival hires a knight"
            is KAct.Strike -> RI.KNIGHT to "Rival's knight strikes!"
            is KAct.Trade -> RI.TRADE to "Rival trades 4 ${Realm.RES[a.give]} for 1 ${Realm.RES[a.get]}"
            KAct.Roll -> RI.DICE to "Rival rolls ${g.lastRoll}"
            KAct.End -> RI.END to "Rival ends its turn"
            KAct.BuySpell -> RI.SCROLL to "Rival learns a spell"
            is KAct.Spell -> KSPELL_ICONS[a.kind] to "Rival casts ${Kreashun.SPELLS[a.kind]}"
        }
        announce(title, detail, if (a is KAct.Strike || a == KAct.End) 2000 else 1500, false, icon)
    }

    private fun aiStep() {
        if (outcome != Outcome.PLAYING || g.phase == Kreashun.OVER) return
        if (g.actor != 1) { bump(); return }
        val snap = g.copy(); val l = lvl; val seed = rng.nextLong()
        computer(minMs = 480, think = { KreashunAi.next(snap, l, Random(seed)) }) { a0 ->
            if (g.actor != 1) return@computer
            var a = a0
            if (!g.legal(a)) a = when (g.phase) {
                Kreashun.ROLL -> KAct.Roll
                Kreashun.SETUP -> if (g.setupVillage < 0) KAct.Village(g.villageSpots(1).first()) else KAct.Wall(g.wallSpots(1).first())
                else -> KAct.End
            }
            if (a == KAct.End) moves++
            code(a).let { if (it >= 0) lastAi = it }
            g.apply(a, rng)
            val logs = g.log.toList(); flush(); changed()
            card(a, logs)
            if (g.phase == Kreashun.OVER) { whenQuiet { finishGame() }; return@computer }
            if (g.actor == 0 && g.phase == Kreashun.ROLL && a == KAct.End) announce("Your turn", "Roll the dice", 1100, true, RI.CROWN)
            whenQuiet { aiStep() }
        }
    }

    private fun finishGame() {
        when (g.winner) {
            0 -> win(listOf(BonusLine("Points", g.vp(0) * rules.basePoints * 2), BonusLine("Castles felled", g.felled[0] * rules.basePoints * 3)))
            2 -> draw()
            else -> lose()
        }
    }

    val prompt: String get() = when {
        outcome != Outcome.PLAYING -> ""
        heraldBusy -> "Watch the rival's moves · tap a card to skip"
        thinking || g.actor == 1 -> "The rival is taking its turn…"
        g.phase == Kreashun.SETUP -> if (g.setupVillage < 0) "Place a village: tap a glowing corner" else "Place a wall beside it: tap a glowing side"
        g.phase == Kreashun.ROLL -> "Roll the dice"
        mode == Mode.WALL -> (if (g.freeWalls > 0) "${g.freeWalls} free wall${if (g.freeWalls > 1) "s" else ""} · " else "") + "Tap a glowing side to wall it · close every side of a tile to capture it"
        mode == Mode.VILLAGE -> "Tap a glowing corner for your village"
        mode == Mode.TOWER -> "Tap one of your villages to make it a tower"
        mode == Mode.STRIKE -> "Tap a touching rival tile to strike"
        mode == Mode.RALLY -> "Tap one of your tiles for the rallied knight"
        mode == Mode.SIEGE -> "Tap a rival castle to hurl Siege Fire"
        selected >= 0 && g.owner[selected] == 0 -> "Build a castle, hire knights, or strike"
        selected >= 0 -> "${g.closedSides(selected)} of 6 sides walled"
        else -> "Build walls to capture tiles, or end your turn"
    }

    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (g.actor != 0 || g.phase == Kreashun.OVER) return null
        val snap = g.copy()
        return {
            val a = KreashunAi.next(snap, KreashunAi.level(Difficulty.EXTREME), Random(7))
            val text = when (a) {
                is KAct.Village -> "Build a village on the ringed corner."
                is KAct.Tower -> "Make the ringed village a tower: it pays double."
                is KAct.Wall -> if (snap.wouldCapture(a.e).isNotEmpty()) "Wall the ringed side: it closes a tile and captures it." else "Wall the ringed side."
                is KAct.Castle -> "Build a castle on the ringed tile."
                is KAct.Hire -> "Hire a knight on the ringed tile."
                is KAct.Strike -> "Strike the ringed rival tile with a knight."
                is KAct.Trade -> "Trade 4 ${Realm.RES[a.give]} for 1 ${Realm.RES[a.get]}."
                KAct.Roll -> "Roll the dice."
                KAct.End -> "Nothing worth doing: end your turn."
                KAct.BuySpell -> "Buy a spell."
                is KAct.Spell -> "Cast ${Kreashun.SPELLS[a.kind]}."
            }
            Triple(text, code(a), -1)
        }
    }

    override fun resume() {
        if (outcome != Outcome.PLAYING) return
        if (g.phase == Kreashun.OVER) { finishGame(); return }
        if (g.actor == 1) aiStep()
    }

    override fun saveState(): JSONObject = JSONObject().put("s", JSONArray().also { a -> g.state().forEach { a.put(it) } }).put("moves", moves)

    companion object {
        fun restore(o: JSONObject): KreashunSession {
            val st = o.getJSONObject("state").getJSONArray("s")
            val g = Kreashun.fromState(IntArray(st.length()) { st.getInt(it) })
            return KreashunSession(Difficulty.valueOf(o.getString("difficulty")), g).also { it.moves = o.getJSONObject("state").optInt("moves"); it.restoreCommon(o) }
        }
    }
}

// =====================================================================  definition

object KreashunDef : GameDef {
    override val kind = GameKind.KREASHUN
    override val fullBleed: Boolean get() = true
    override val hasHeader: Boolean get() = true
    @Composable override fun Header(session: Session, actions: com.puresudoku.ui.games.HeaderActions) = RealmHeader(session, actions, "Kreashun · ")

    override fun generate(d: Difficulty): Session = KreashunSession(d, Kreashun.newGame(d, Random(System.nanoTime())))
    override fun restore(o: JSONObject): Session = KreashunSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Wall every side of a tile to capture it: the closing wall takes it. Captured tiles pay double, raise castles and send knights against touching rival tiles."
    override fun background(session: Session) = com.puresudoku.R.drawable.bg_realm_ocean
    override fun levelInfo(d: Difficulty): String {
        val m = RealmMap.forLevel(d.ordinal)
        return "First to ${Kreashun.newGame(d, Random(1)).target} points · ${m.HEXES} tiles"
    }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A careless rival on a small island."
        Difficulty.MEDIUM -> "Walls steadily and strikes now and then."
        Difficulty.HARD -> "Never leaves you a tile to close."
        Difficulty.EXPERT -> "Hunts your castles with knights."
        Difficulty.MASTER -> "Relentless, on a big island."
        Difficulty.EXTREME -> "Its best play, on the biggest island."
    }
    @Composable override fun Play(session: Session) = KreashunUi(session as KreashunSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override fun lesson(): Pair<Session, List<LessonStep>> {
        val s = KreashunSession(Difficulty.EASY, Kreashun.newGame(Difficulty.EASY, Random(777)))
        return s to listOf(
            LessonStep("Kreashun is a war of walls. Place two villages on corners, each with a wall beside it. Villages collect from the tiles they touch when those numbers are rolled.") { (it as KreashunSession).g.phase != Kreashun.SETUP },
            LessonStep("Roll each turn, then build. Walls go on sides joined to your villages, walls or tiles. Wall every side of a tile, coast included, to capture it: whoever builds the closing wall takes it, so don't leave a tile one wall from closed."),
            LessonStep("Each tile you hold is worth a point, and pays your villages and towers on its corners double."),
            LessonStep("On a tile you hold you can build a castle (3 hit points) and hire knights. A knight strikes a touching rival tile: it breaks the wall on that side first, then takes a tile with no castle, or knocks a point off a castle."),
            LessonStep("Broken walls are rubble and can't be rebuilt. A tile taken by force brings its walls, villages, towers and knights with it. Fell a castle for a bonus point, and rebuild it there for half price."),
            LessonStep("Spells cost ore, grain and beasts. Cast one a turn, but not on the turn you bought it: Rally adds a knight, Stonewright gives two free walls, Harvest Moon two resources, Siege Fire knocks a point off a castle, and a Relic is a hidden point."),
            LessonStep("Reach the points goal to win. If you lose every point after you've scored, you lose. Good luck!"),
        )
    }

    override val tutorial: List<TutorialStep> get() = kreashunTutorial()
}

// =====================================================================  board

private fun DrawScope.kCastle(p: Offset, u: Float, color: Color) {
    val s = 0.2f * u
    for (dx in listOf(-1.05f, 1.05f)) {
        val tp = Path().apply {
            moveTo(p.x + dx * s - s * 0.38f, p.y + s); lineTo(p.x + dx * s - s * 0.38f, p.y - s * 1.2f); lineTo(p.x + dx * s, p.y - s * 1.75f)
            lineTo(p.x + dx * s + s * 0.38f, p.y - s * 1.2f); lineTo(p.x + dx * s + s * 0.38f, p.y + s); close()
        }
        drawPath(tp, Color.Black.copy(alpha = 0.35f), style = Stroke(s * 0.45f)); drawPath(tp, color); drawPath(tp, Color.White, style = Stroke(s * 0.12f))
    }
    tower(p, u * 0.9f, color)
}

@Composable
fun KreashunBoard(s: KreashunSession, targets: DemoTargets?, modifier: Modifier = Modifier, fitTop: Float = 0f, fitBottom: Float = 0f) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "glow")
    val art = rememberRealmArt()
    val knightImg = ImageBitmap.imageResource(com.puresudoku.R.drawable.rt_knight_piece)
    val vT = s.vertexTargets(); val eT = s.edgeTargets(); val tT = s.tileTargets()
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
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().pointerInput(s) {
                detectTransformGestures { centroid, panChange, zoomChange, _ -> zoomBy(zoomChange, centroid); pan = clampPan(pan + panChange, zoom) }
            }.pointerInput(s, vT, eT, tT, s.mode) {
                detectTapGestures { o ->
                    val geo = RGeo(g.map, size.width.toFloat(), size.height.toFloat(), zoom, pan, fitTop, fitBottom)
                    val reach = maxOf(geo.unit * 0.55f, 18.dp.toPx())
                    nearest(o, vT, geo::vert, reach)?.let { s.tapVertex(it); return@detectTapGestures }
                    nearest(o, eT, geo::edgeMid, reach)?.let { s.tapEdge(it); return@detectTapGestures }
                    nearest(o, (0 until g.H).toList(), geo::hex, geo.unit * 0.95f)?.let { s.tapTile(it) }
                }
            }) {
                val geo = RGeo(g.map, size.width, size.height, zoom, pan, fitTop, fitBottom); val u = geo.unit; val m = g.map
                run {
                    val k = geo.art * 1254f / art.island.width; val o = geo.artPt(0f, 0f)
                    drawImage(art.island, dstOffset = IntOffset(o.x.toInt(), o.y.toInt()), dstSize = IntSize((art.island.width * k).toInt(), (art.island.height * k).toInt()), filterQuality = FilterQuality.Medium)
                }
                // tiles
                val tw = (sqrt3 * u).toInt() + 2; val th = (2 * u).toInt() + 2
                for (h in 0 until g.H) {
                    val c = geo.hex(h); val t = g.terrain[h]
                    val list = art.byTerrain[t]; val img = list[(0 until h).count { g.terrain[it] == t } % list.size]
                    drawImage(img, dstOffset = IntOffset((c.x - tw / 2f).toInt(), (c.y - th / 2f).toInt()), dstSize = IntSize(tw, th), filterQuality = FilterQuality.Medium)
                }
                // owner tint and gold borders
                for (h in 0 until g.H) {
                    val o = g.owner[h]
                    if (o >= 0) drawPath(hexPath(geo, h, 0.99f), (if (o == 0) YOU else RIVAL).copy(alpha = 0.22f))
                    val p = hexPath(geo, h, 1f)
                    drawPath(p, Color(0xFF4A3510), style = Stroke(u * 0.08f))
                    drawPath(p, Brush.linearGradient(listOf(Color(0xFFF6E3A1), Color(0xFFC9972F), Color(0xFFF2D27A)), start = geo.hex(h) - Offset(u, u), end = geo.hex(h) + Offset(u, u)), style = Stroke(u * 0.05f))
                    if (o >= 0) drawPath(hexPath(geo, h, 0.9f), if (o == 0) YOU else RIVAL, style = Stroke(u * 0.07f))
                }
                // resource bubble and number token; castles and knights on held tiles
                for (h in 0 until g.H) {
                    val c = geo.hex(h); val t = g.terrain[h]
                    val held = g.owner[h] >= 0
                    if (t != Kreashun.WASTE) {
                        val bc = c + Offset(if (held) -u * 0.38f else 0f, -u * 0.3f); val br = u * 0.18f
                        drawCircle(Color.Black.copy(alpha = 0.25f), br, bc + Offset(0f, u * 0.025f))
                        drawCircle(Color(0xFFF8EED6), br, bc); drawCircle(Color(0xFFC9972F), br, bc, style = Stroke(u * 0.03f))
                        val ic = art.icons[t]; val iw = br * 1.45f; val ih = iw * ic.height / ic.width
                        drawImage(ic, dstOffset = IntOffset((bc.x - iw / 2).toInt(), (bc.y - ih / 2).toInt()), dstSize = IntSize(iw.toInt(), ih.toInt()), filterQuality = FilterQuality.Medium)
                        val tc = c + Offset(if (held) -u * 0.38f else 0f, u * 0.2f)
                        drawCircle(Color.Black.copy(alpha = 0.25f), u * 0.25f, tc + Offset(0f, u * 0.03f))
                        drawCircle(Color(0xFFFFF4DE), u * 0.24f, tc)
                        val hot = g.number[h] == 6 || g.number[h] == 8
                        glyph("${g.number[h]}", tc - Offset(0f, u * 0.04f), u * 0.25f, if (hot) Color(0xFFC62828) else Color(0xFF2B2B2B))
                        val pips = Realm.pips(g.number[h])
                        for (k in 0 until pips) drawCircle(if (hot) Color(0xFFC62828) else Color(0xFF2B2B2B), u * 0.018f, tc + Offset((k - (pips - 1) / 2f) * u * 0.05f, u * 0.14f))
                        if (g.number[h] == g.lastRoll && g.phase == Kreashun.MAIN) drawPath(hexPath(geo, h), Color(0xFFFFF59D).copy(alpha = 0.9f), style = Stroke(u * 0.07f))
                    }
                    if (!held) {
                        // walled-sides counter on tiles still up for grabs
                        val n = g.closedSides(h)
                        if (n > 0) {
                            val bp = c + Offset(u * 0.42f, -u * 0.05f)
                            drawCircle(Color(0xFF2B2B2B).copy(alpha = 0.8f), u * 0.17f, bp)
                            glyph("$n/6", bp, u * 0.15f, if (n == 5) Color(0xFFFFD54F) else Color.White)
                        }
                        continue
                    }
                    val col = if (g.owner[h] == 0) YOU else RIVAL
                    if (g.castle[h] > 0) {
                        val cp = c + Offset(u * 0.22f, -u * 0.08f)
                        kCastle(cp, u, col)
                        for (k in 0 until Kreashun.CASTLE_HP) {
                            val hp = cp + Offset((k - 1) * u * 0.14f, u * 0.3f)
                            drawCircle(Color.Black.copy(alpha = 0.5f), u * 0.06f, hp)
                            drawCircle(if (k < g.castle[h]) Color(0xFFE53935) else Color(0xFF5D4037), u * 0.045f, hp)
                        }
                    } else if (g.halfCastle[h]) glyph("½", c + Offset(u * 0.22f, -u * 0.1f), u * 0.24f, Color.White)
                    val kn = g.knights[h]
                    if (kn > 0) {
                        val kh = u * 0.42f; val kw = kh * knightImg.width / knightImg.height; val base = c.y + u * 0.8f
                        for (i in 0 until kn) {
                            val x = c.x + u * 0.22f + (i - (kn - 1) / 2f) * kw * 0.9f
                            drawImage(knightImg, dstOffset = IntOffset((x - kw / 2).toInt(), (base - kh).toInt()), dstSize = IntSize(kw.toInt(), kh.toInt()), filterQuality = FilterQuality.Medium)
                        }
                    }
                }
                // targets
                for (h in tT) drawPath(hexPath(geo, h, 0.86f), Color(0xFFFF7043).copy(alpha = pulse), style = Stroke(u * 0.08f))
                val la = s.lastAi
                if (la >= 0) {
                    val p = when { la >= 2000 -> geo.hex(la - 2000); la >= 1000 -> geo.edgeMid(la - 1000); else -> geo.vert(la) }
                    drawCircle(RIVAL.copy(alpha = 0.8f), u * 0.36f, p, style = Stroke(u * 0.05f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.1f, u * 0.07f))))
                }
                val cf = s.coachFrom
                if (cf >= 0) coachRings(listOf(when { cf >= 2000 -> geo.hex(cf - 2000); cf >= 1000 -> geo.edgeMid(cf - 1000); else -> geo.vert(cf) }), u * 0.4f)
                // rubble, walls and wall targets sit above everything but the selected-tile highlight
                for (e in 0 until m.EDGES) {
                    val a = geo.vert(m.edgeA[e]); val b = geo.vert(m.edgeB[e]); val d = (b - a) * 0.1f
                    if (g.rubble[e]) {
                        drawLine(Color(0xFF3E2723), a + d, b - d, u * 0.14f, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.07f, u * 0.09f)))
                        drawLine(Color(0xFF8D8478), a + d, b - d, u * 0.08f, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.07f, u * 0.09f)))
                        continue
                    }
                    val o = g.wall[e]; if (o < 0) continue
                    strategyWall(art, a + d, b - d, u, if (o == 0) YOU else RIVAL)
                    drawLine(if (o == 0) YOU else RIVAL, a + d, b - d, u * 0.05f, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.08f, u * 0.06f)))
                }
                for (e in eT) {
                    val a = geo.vert(m.edgeA[e]); val b = geo.vert(m.edgeB[e]); val d = (b - a) * 0.18f
                    val closes = g.phase == Kreashun.MAIN && g.wouldCapture(e).isNotEmpty()
                    drawLine((if (closes) Color(0xFFFFD54F) else Color.White).copy(alpha = pulse), a + d, b - d, u * 0.12f, StrokeCap.Round,
                        pathEffect = if (closes) null else PathEffect.dashPathEffect(floatArrayOf(u * 0.12f, u * 0.08f)))
                }
                // villages and towers sit on wall ends, so they draw over the walls
                for (v in 0 until m.VERTS) {
                    val o = g.vOwner[v]; if (o < 0) continue
                    val col = if (o == 0) YOU else RIVAL
                    if (g.vLevel[v] == 2) tower(geo.vert(v), u * 0.95f, col)
                    else {
                        val img = if (o == 0) art.houseYou else art.houseRival
                        val w = u * 0.5f; val hh = w * img.height / img.width; val p = geo.vert(v)
                        drawImage(img, dstOffset = IntOffset((p.x - w / 2).toInt(), (p.y - hh * 0.62f).toInt()), dstSize = IntSize(w.toInt(), hh.toInt()), filterQuality = FilterQuality.Medium)
                    }
                }
                for (v in vT) drawCircle(Color.White.copy(alpha = pulse), u * 0.17f, geo.vert(v), style = Stroke(u * 0.07f))
                if (s.selected >= 0) drawPath(hexPath(geo, s.selected, 0.94f), Color(0xFFFFD54F), style = Stroke(u * 0.09f))
            }
            if (targets != null) {
                val geo = RGeo(g.map, wPx, hPx, 1f, Offset.Zero, fitTop, fitBottom)
                val r = geo.unit * 0.3f
                CellTargets(targets, (0 until g.map.VERTS).map { "v-$it" to Rect(geo.vert(it), r) } + (0 until g.map.EDGES).map { "e-$it" to Rect(geo.edgeMid(it), r) } +
                    (0 until g.H).map { "h-$it" to Rect(geo.hex(it), geo.unit * 0.45f) })
            }
        }
    }
}

// =====================================================================  panels

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KreashunUi(s: KreashunSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val full = targets == null
    var topH by remember { mutableIntStateOf(0) }
    var botH by remember { mutableIntStateOf(0) }
    var showTrade by remember { mutableStateOf(false) }
    var showSpells by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        KreashunBoard(s, targets, Modifier.fillMaxSize(), topH.toFloat(), botH.toFloat())
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topH = it.height }
                .then(if (full) Modifier.statusBarsPadding().padding(top = 54.dp) else Modifier).padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerBanner("You", "${g.vp(0)}", YOU, g.turn == 0 && g.phase != Kreashun.SETUP, "${g.tiles(0)} tiles · ${g.spells[0].sum()} spells", Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RIconOf(RI.CROWN, 22.dp)
                    Text("${g.target}", fontFamily = SERIF, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(0f, 1f), 4f)))
                }
                PlayerBanner("Rival", "${g.vp(1, false)}" + if (g.spells[1].sum() > 0) "+" else "", RIVAL, g.turn == 1 && g.phase != Kreashun.SETUP, "${g.tiles(1)} tiles · ${g.total(1)} cards", Modifier.weight(1f))
            }
        }

        HeraldCard(s, Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { botH.toDp() } + 6.dp).padding(horizontal = 12.dp))

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { botH = it.height }
                .then(if (full) Modifier.navigationBarsPadding() else Modifier).padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).parchment(PANEL).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (g.lastRoll > 0) {
                    Canvas(Modifier.size(50.dp, 24.dp)) {
                        val d = size.height * 0.92f
                        die(Offset(d / 2, size.height / 2), d, g.lastDie[0], g.lastRoll == 7)
                        die(Offset(d * 1.65f, size.height / 2), d, g.lastDie[1], g.lastRoll == 7)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                InkText(s.events.lastOrNull() ?: "The chronicle of Kreashun begins…", 12, color = INK_SOFT, modifier = Modifier.weight(1f), lines = 2)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (r in 0..4) ResChip(r, g.res[0][r], Modifier.weight(1f))
            }
            Spacer(Modifier.height(5.dp))
            PromptRibbon(s.prompt, s.thinking || s.heraldBusy || (g.actor == 1 && s.outcome == Outcome.PLAYING))
            Spacer(Modifier.height(5.dp))
            when {
                s.myMove && g.phase == Kreashun.ROLL -> WoodButton("Roll", RI.DICE, Modifier.demoTarget(targets, "roll")) { s.roll() }
                s.myMove && g.phase == Kreashun.MAIN -> FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val h = s.selected
                    if (h >= 0 && g.owner[h] == 0) {
                        if (g.castle[h] == 0) BuildButton(if (g.halfCastle[h]) "Castle ½" else "Castle", RI.CROWN, g.castleCost(h), false, g.canCastle(0, h)) { s.castle(h) }
                        BuildButton("Knight ${g.knights[h]}/${g.capacity(h)}", RI.KNIGHT, Kreashun.COST_KNIGHT, false, g.canHire(0, h)) { s.hire(h) }
                        BuildButton("Strike", RI.KNIGHT, null, s.mode == KreashunSession.Mode.STRIKE, g.strikeTargets(0, h).isNotEmpty()) { s.toggleMode(KreashunSession.Mode.STRIKE) }
                    }
                    BuildButton("Wall", RI.SHIELD, Kreashun.COST_WALL, s.mode == KreashunSession.Mode.WALL, g.can(0, Kreashun.COST_WALL) && g.wallSpots(0).isNotEmpty()) { s.toggleMode(KreashunSession.Mode.WALL) }
                    BuildButton("Village", RI.VILLAGE, Kreashun.COST_VILLAGE, s.mode == KreashunSession.Mode.VILLAGE, g.can(0, Kreashun.COST_VILLAGE) && g.villageSpots(0).isNotEmpty()) { s.toggleMode(KreashunSession.Mode.VILLAGE) }
                    BuildButton("Tower", RI.TOWER, Kreashun.COST_TOWER, s.mode == KreashunSession.Mode.TOWER, g.can(0, Kreashun.COST_TOWER) && g.towerSpots(0).isNotEmpty()) { s.toggleMode(KreashunSession.Mode.TOWER) }
                    BuildButton("Spell", RI.SCROLL, Kreashun.COST_SPELL, false, g.can(0, Kreashun.COST_SPELL) && g.deck.isNotEmpty() && g.freeWalls == 0) { s.buySpell() }
                    BuildButton("Spells ${g.spells[0].sum()}", RI.BOOK, null, false, g.spells[0].sum() > 0) { showSpells = true }
                    BuildButton("Trade", RI.TRADE, null, false, true) { showTrade = true }
                    BuildButton("End turn", RI.END, null, false, true) { s.endTurn() }
                }
                else -> Spacer(Modifier.height(44.dp))
            }
            Spacer(Modifier.height(if (full) 10.dp else 4.dp))
        }
    }
    if (showTrade) KTradeDialog(s) { showTrade = false }
    if (showSpells) SpellbookDialog(Kreashun.SPELLS, Kreashun.SPELL_TEXT, KSPELL_ICONS, g.spells[0], g.fresh,
        if (g.spellPlayed) "You've already cast a spell this turn." else null, Kreashun.RELIC, Kreashun.HARVEST,
        { g.canPlay(0, it) }, { k, a, b -> s.cast(k, a, b) }) { showSpells = false }
}

@Composable
private fun KTradeDialog(s: KreashunSession, onDismiss: () -> Unit) {
    val g = s.g
    var give by remember { mutableIntStateOf(-1) }
    var get by remember { mutableIntStateOf(-1) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = PARCH_TOP, titleContentColor = INK, textContentColor = INK,
        icon = { RIconOf(RI.TRADE, 32.dp) },
        title = { Text("The travelling merchant", fontFamily = SERIF, fontWeight = FontWeight.Bold) },
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
