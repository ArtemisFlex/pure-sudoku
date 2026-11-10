package com.puresudoku.ui.games.realm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.board.RAct
import com.puresudoku.core.board.Realm
import com.puresudoku.core.board.RealmAi
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
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

// =====================================================================  colours

internal val YOU = Color(0xFF2E9BEA)
internal val RIVAL = Color(0xFFE5484D)
internal val TERRAIN_COLOR = listOf(Color(0xFF2E7D4F), Color(0xFF8A8F98), Color(0xFFE2B84A), Color(0xFF9CCB5E), Color(0xFF8E6BD8), Color(0xFFD9B98A))
internal val TERRAIN_DARK = listOf(Color(0xFF1B5235), Color(0xFF5B6068), Color(0xFFB4892A), Color(0xFF6E9E3A), Color(0xFF5E40A6), Color(0xFFA9875A))
internal val RES_COLOR = listOf(Color(0xFF2E7D4F), Color(0xFF7B818C), Color(0xFFD9A62E), Color(0xFF7FB347), Color(0xFF8E6BD8))
internal val RES_SHORT = listOf("Timber", "Ore", "Grain", "Beasts", "Mana")

// =====================================================================  session

class RealmSession(kind: GameKind, difficulty: Difficulty, val g: Realm) : DuelSession(kind, difficulty) {
    enum class Mode { NONE, ROAD, VILLAGE, TOWER, FIREBALL }

    var mode by mutableStateOf(Mode.NONE)
    var offer by mutableStateOf<RAct.Offer?>(null); private set
    private var aiOffered = false
    /** Board code of the computer's last action: vertex, 100+edge or 200+tile. */
    var lastAi by mutableIntStateOf(-1); private set
    var rollId by mutableIntStateOf(0); private set
    var events by mutableStateOf<List<String>>(emptyList()); private set

    private val lvl get() = RealmAi.level(aiDifficulty)

    override val middleStat get() = MiddleStat.Counter("Points", "${g.vp(0)}/${g.target}")
    override val progress: Float get() = (g.vp(0) / g.target.toFloat()).coerceAtMost(0.95f)
    override val wonTitle get() = "The realm is yours!"
    override val lostTitle get() = "Your rival rules the realm"
    override val lostMessage get() = "The rival reached ${g.vp(1)} points. This game's save slot has been cleared."

    val myMove get() = outcome == Outcome.PLAYING && !thinking && g.actor == 0 && offer == null && g.phase != Realm.OVER

    fun vertexTargets(): List<Int> = when {
        !myMove -> emptyList()
        g.phase == Realm.SETUP && g.setupVillage < 0 -> g.villageSpots(0)
        g.phase == Realm.MAIN && g.freeRoads == 0 && mode == Mode.VILLAGE -> g.villageSpots(0)
        g.phase == Realm.MAIN && g.freeRoads == 0 && mode == Mode.TOWER -> g.towerSpots(0)
        else -> emptyList()
    }

    fun edgeTargets(): List<Int> = when {
        !myMove -> emptyList()
        g.phase == Realm.SETUP && g.setupVillage >= 0 -> g.roadSpots(0)
        g.phase == Realm.MAIN && (mode == Mode.ROAD || g.freeRoads > 0) -> g.roadSpots(0)
        g.phase == Realm.MAIN && mode == Mode.FIREBALL -> g.fireballTargets(0)
        else -> emptyList()
    }

    fun tileTargets(): List<Int> = if (myMove && g.phase == Realm.DRAGON) g.dragonTiles() else emptyList()

    private fun flush() {
        if (g.log.isNotEmpty()) { events = (events + g.log).takeLast(4); g.log.clear() }
    }

    private fun act(a: RAct): Boolean {
        if (!g.legal(a)) { nudge("You can't do that right now"); return false }
        coachTip = null; coachFrom = -1
        g.apply(a, rng); flush(); bump(); changed()
        if (g.phase == Realm.OVER) { finishGame(); return true }
        if (g.actor == 1) aiStep()
        return true
    }

    fun tapVertex(v: Int) {
        if (v !in vertexTargets()) return
        when {
            g.phase == Realm.SETUP -> act(RAct.Village(v))
            mode == Mode.VILLAGE -> if (act(RAct.Village(v))) mode = Mode.NONE
            mode == Mode.TOWER -> if (act(RAct.Tower(v))) mode = Mode.NONE
        }
    }

    fun tapEdge(e: Int) {
        if (e !in edgeTargets()) return
        if (mode == Mode.FIREBALL && g.freeRoads == 0) { if (act(RAct.Spell(Realm.FIREBALL, e))) mode = Mode.NONE; return }
        if (act(RAct.Road(e)) && g.freeRoads == 0 && !(g.can(0, Realm.COST_ROAD) && g.roadSpots(0).isNotEmpty())) mode = Mode.NONE
    }

    fun tapTile(t: Int) { if (t in tileTargets()) act(RAct.Dragon(t)) }

    fun roll() { if (myMove && g.phase == Realm.ROLL) { rollId++; mode = Mode.NONE; act(RAct.Roll) } }
    fun endTurn() { if (myMove && g.phase == Realm.MAIN) { mode = Mode.NONE; moves++; act(RAct.End) } }
    fun buySpell() { if (myMove) act(RAct.BuySpell) }
    fun trade(give: Int, get: Int) { if (myMove) act(RAct.Trade(give, get)) }
    fun discard(counts: IntArray) { if (myMove) act(RAct.Discard(counts)) }

    fun playSpell(kind: Int, a: Int = -1, b: Int = -1) {
        if (!myMove || !g.canPlay(0, kind)) { nudge("You can't cast that now"); return }
        if (kind == Realm.FIREBALL) { mode = Mode.FIREBALL; bump(); return }
        act(RAct.Spell(kind, a, b))
    }

    fun toggleMode(m: Mode) {
        if (!myMove || g.phase != Realm.MAIN) return
        val cost = when (m) { Mode.ROAD -> Realm.COST_ROAD; Mode.VILLAGE -> Realm.COST_VILLAGE; Mode.TOWER -> Realm.COST_TOWER; else -> null }
        if (cost != null && mode != m && !g.can(0, cost)) { nudge("Not enough resources for that"); return }
        val spots = when (m) { Mode.ROAD -> g.roadSpots(0); Mode.VILLAGE -> g.villageSpots(0); Mode.TOWER -> g.towerSpots(0); else -> listOf(0) }
        if (mode != m && spots.isEmpty()) { nudge(if (m == Mode.VILLAGE) "No free corner on your roads: villages need two empty edges between them" else "Nowhere to build that"); return }
        mode = if (mode == m) Mode.NONE else m
        bump()
    }

    fun answerOffer(accept: Boolean) {
        val o = offer ?: return
        offer = null
        if (accept && g.legal(o)) { g.apply(o, rng); events = (events + "You took the rival's deal").takeLast(4); g.log.clear() }
        else events = (events + "You turned down the deal").takeLast(4)
        bump(); changed()
        aiStep()
    }

    private fun code(a: RAct): Int = when (a) {
        is RAct.Village -> a.v; is RAct.Tower -> a.v; is RAct.Road -> 100 + a.e; is RAct.Dragon -> 200 + a.tile
        is RAct.Spell -> if (a.kind == Realm.FIREBALL) 100 + a.a else -1
        else -> -1
    }

    private fun aiStep() {
        if (outcome != Outcome.PLAYING || g.phase == Realm.OVER || offer != null) return
        if (g.actor != 1) { bump(); return }
        val snap = g.copy(); val l = lvl; val seed = rng.nextLong(); val off = aiOffered
        computer(minMs = if (g.phase == Realm.ROLL || g.phase == Realm.SETUP) 700 else 520, think = { RealmAi.next(snap, l, Random(seed), off) }) { a0 ->
            if (g.actor != 1) return@computer
            var a = a0
            if (!g.legal(a)) a = when (g.phase) { Realm.ROLL -> RAct.Roll; Realm.MAIN -> RAct.End; else -> RealmAi.next(g, RealmAi.level(Difficulty.EXTREME), Random(1), true) }
            if (a is RAct.Offer) { aiOffered = true; offer = a; return@computer }
            if (a == RAct.End) { aiOffered = false; moves++ }
            if (a == RAct.Roll) rollId++
            code(a).let { if (it >= 0) lastAi = it }
            g.apply(a, rng); flush(); changed()
            if (g.phase == Realm.OVER) { finishGame(); return@computer }
            aiStep()
        }
    }

    private fun finishGame() {
        if (g.winner == 0) win(listOf(BonusLine("Victory points", g.vp(0) * rules.basePoints * 2), BonusLine("Margin", maxOf(0, g.vp(0) - g.vp(1)) * rules.basePoints * 3)))
        else lose()
    }

    /** What the screen should ask of you right now. */
    val prompt: String get() = when {
        outcome != Outcome.PLAYING -> ""
        offer != null -> "The rival offers a deal"
        thinking || g.actor == 1 -> when (g.phase) {
            Realm.SETUP -> "The rival is choosing where to settle…"
            Realm.DISCARD -> "The rival is discarding…"
            else -> "The rival is taking its turn…"
        }
        g.phase == Realm.SETUP -> if (g.setupVillage < 0) (if (g.setupStep == 0) "Place your first village on a glowing corner" else "Place your second village. It pays out one of each resource around it")
            else "Now build a road from that village"
        g.phase == Realm.ROLL -> "Your turn: roll the dice"
        g.phase == Realm.DISCARD -> "A 7! You hold too many cards: discard ${g.discardNeed[0]}"
        g.phase == Realm.DRAGON -> "Move the dragon: tap a tile"
        g.freeRoads > 0 -> "Earthshaper: place ${g.freeRoads} free road${if (g.freeRoads > 1) "s" else ""}"
        mode == Mode.ROAD -> "Tap a glowing edge for your road"
        mode == Mode.VILLAGE -> "Tap a glowing corner for your village"
        mode == Mode.TOWER -> "Tap one of your villages to raise a tower"
        mode == Mode.FIREBALL -> "Tap a rival road to burn"
        else -> "Build, trade or cast, then end your turn"
    }

    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (g.actor != 0 || offer != null || g.phase == Realm.OVER) return null
        val snap = g.copy()
        return {
            val a = RealmAi.next(snap, RealmAi.level(Difficulty.EXTREME), Random(7), true)
            Triple(describe(a, snap), code(a), -1)
        }
    }

    private fun describe(a: RAct, s: Realm): String = when (a) {
        RAct.Roll -> "Roll the dice."
        RAct.End -> "Nothing more worth doing: end your turn and keep your cards."
        RAct.BuySpell -> "Buy a spell card: Knights, secret points and handy magic."
        is RAct.Village -> "Build a village on the ringed corner."
        is RAct.Tower -> "Turn the ringed village into a tower: it collects double."
        is RAct.Road -> if (s.phase == Realm.SETUP) "Point your road toward open land." else "Build a road on the ringed edge, toward a good village spot."
        is RAct.Dragon -> "Put the dragon on the ringed tile: it hurts your rival most."
        is RAct.Trade -> "Trade ${s.portRate(0, a.give)} ${Realm.RES[a.give]} for 1 ${Realm.RES[a.get]}."
        is RAct.Spell -> "Cast ${Realm.SPELLS[a.kind]}" + if (a.kind == Realm.HARVEST) " for ${Realm.RES[a.a]} and ${Realm.RES[a.b]}." else "."
        is RAct.Discard -> "Discard: " + (0..4).filter { a.counts[it] > 0 }.joinToString(", ") { "${a.counts[it]} ${Realm.RES[it]}" } + "."
        is RAct.Offer -> ""
    }

    override fun resume() {
        if (outcome != Outcome.PLAYING) return
        if (g.phase == Realm.OVER) { finishGame(); return }
        if (g.actor == 1) aiStep()
    }

    override fun saveState(): JSONObject = JSONObject().put("s", JSONArray().also { a -> g.state().forEach { a.put(it) } }).put("moves", moves)

    companion object {
        fun restore(kind: GameKind, o: JSONObject): RealmSession {
            val st = o.getJSONObject("state").getJSONArray("s")
            val arr = IntArray(st.length()) { st.getInt(it) }
            val g = Realm(arr[0], arr[1]).also { it.load(arr) }
            return RealmSession(kind, Difficulty.valueOf(o.getString("difficulty")), g).also { it.moves = o.getJSONObject("state").optInt("moves"); it.restoreCommon(o) }
        }
    }
}

// =====================================================================  game definitions

abstract class RealmDefBase(final override val kind: GameKind, private val twist: Int) : GameDef {
    override fun generate(d: Difficulty): Session = RealmSession(kind, d, Realm.newGame(twist, d, Random(System.nanoTime())))
    override fun restore(o: JSONObject): Session = RealmSession.restore(kind, o).also { it.resume() }
    override fun levelInfo(d: Difficulty) = "First to ${Realm.target(d)} points · " + if (d.ordinal <= 1) "fair dice deck" else "real dice"
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A careless rival who offers generous deals."
        Difficulty.MEDIUM -> "Builds sensibly and still trades kindly."
        Difficulty.HARD -> "Races for roads and uses the dragon on you."
        Difficulty.EXPERT -> "A sharp builder with fair trades."
        Difficulty.MASTER -> "Careful, quick and hard to stop."
        Difficulty.EXTREME -> "Its best play, and it never deals with you."
    }
    @Composable override fun Play(session: Session) = RealmUi(session as RealmSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override fun lesson(): Pair<Session, List<LessonStep>> {
        val s = RealmSession(kind, Difficulty.EASY, Realm.newGame(twist, Difficulty.EASY, Random(4242 + twist)))
        val g = s.g
        val twistText = when (twist) {
            Realm.FOG -> "This is the Unmapped Realm: the outer tiles are hidden in fog. Building next to one reveals it and gives you one of its resource. Some hide ruins with a free spell, and violet ley stones sit on the coast: touch two with your roads for +1 mana every turn."
            Realm.WAKE -> "In Dragon's Wake the dragon is a boss. Every 7 makes it stronger, and each round it walks one tile toward whoever leads. Each Knight wounds it; land the final blow and you earn 2 points."
            else -> "In Night & Day the rounds alternate. At night the crystal spires pay double mana, but the dragon raids whoever leads, landing on their best tile and burning a card."
        }
        return s to listOf(
            LessonStep("Win the realm by reaching ${g.target} points before your rival. Villages are worth 1, towers 2. Every tile shows a number: when the dice roll it, buildings on its corners collect that resource."),
            LessonStep("Big numbers matter. 6 and 8 (in red) are rolled most; 2 and 12 hardly ever. The dots under each number show how often. Tap a glowing corner that touches good numbers and different resources.") { (it as RealmSession).g.count(0, 1) >= 1 },
            LessonStep("Every village needs a road. Tap a glowing edge next to it, pointing toward open land.") { (it as RealmSession).g.roads(0) >= 1 },
            LessonStep("Your rival now places two villages. Then you place your second one, which pays out one of each resource around it straight away.") { val x = (it as RealmSession).g; x.count(0, 1) >= 2 && x.roads(0) >= 2 },
            LessonStep("Setup is done. Each turn starts with a roll. Tap Roll.") { (it as RealmSession).g.lastRoll > 0 },
            LessonStep("Costs: a road is timber + mana. A village is timber, mana, grain and beasts. A tower is 3 ore + 2 grain. A spell is ore, grain and beasts. Short of something? Trade 4 of one resource for 1 of another, or less at a portal you've settled."),
            LessonStep("A 7 wakes the dragon: anyone holding more than 7 cards loses half, and the roller moves the dragon to block a tile and steal a card. Knight spells chase it away."),
            LessonStep(twistText),
            LessonStep("Spread out early, then turn villages into towers. The longest road (5+) and the most Knights (3+) are worth 2 points each. Good luck!"),
        )
    }

    override val tutorial: List<TutorialStep> get() = listOf(
        TutorialStep("Settle the realm", "Tap a glowing corner to place a village, then an edge for its road. Dice rolls pay out resources from the numbered tiles next to your buildings.") { active ->
            var s by remember { mutableStateOf(RealmSession(kind, Difficulty.EASY, Realm.newGame(twist, Difficulty.EASY, Random(9)))) }
            DemoStage(active, script = {
                s.cancelThinking(); s = RealmSession(kind, Difficulty.EASY, Realm.newGame(twist, Difficulty.EASY, Random(9))); wait(600)
                val v = RealmAi.next(s.g, RealmAi.level(Difficulty.EXTREME), Random(1)) as RAct.Village
                tap("v-${v.v}") { s.tapVertex(v.v) }; wait(500)
                val e = RealmAi.next(s.g, RealmAi.level(Difficulty.EXTREME), Random(1)) as RAct.Road
                tap("e-${e.e}") { s.tapEdge(e.e) }; wait(1600)
            }) { t -> RealmUi(s, t) }
        },
        TutorialStep("Build and trade", "Spend resources on roads, villages, towers and spells. Trade 4 for 1 with the merchant, or better at a portal. First to the points goal wins.") { active ->
            var s by remember { mutableStateOf(RealmSession(kind, Difficulty.EASY, Realm.newGame(twist, Difficulty.EASY, Random(9)))) }
            DemoStage(active, script = { s.cancelThinking(); wait(500); highlight(listOf("build"), 1800) }) { t -> RealmUi(s, t) }
        },
    )
}

object RealmforgeDef : RealmDefBase(GameKind.REALMFORGE, Realm.FOG) {
    override fun hint(d: Difficulty) = "Settle villages on good numbers, build roads to explore the fog, and reach the points goal first. 7s wake the dragon."
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_realm_fog
}

object RealmDragonDef : RealmDefBase(GameKind.REALM_DRAGON, Realm.WAKE) {
    override fun hint(d: Difficulty) = "Build and trade to reach the points goal. The dragon grows on every 7 and walks toward the leader each round. Wound it with Knights; the final blow is worth 2 points."
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_realm_dragon
}

object RealmNightDef : RealmDefBase(GameKind.REALM_NIGHT, Realm.NIGHT) {
    override fun hint(d: Difficulty) = "Rounds alternate day and night. At night mana tiles pay double, but the dragon raids whoever leads. Reach the points goal first."
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_realm_night
}

// =====================================================================  board geometry

private class RGeo(val w: Float, val h: Float) {
    val unit = min(w / 10.4f, h / 9.6f)
    val cx = w / 2; val cy = h / 2
    fun pt(x: Float, y: Float) = Offset(cx + x * unit, cy + y * unit)
    fun vert(v: Int) = pt(RealmMap.vertX[v], RealmMap.vertY[v])
    fun hex(h: Int) = pt(RealmMap.hexX[h], RealmMap.hexY[h])
    fun edgeMid(e: Int) = (vert(RealmMap.edgeA[e]) + vert(RealmMap.edgeB[e])) / 2f
    fun portPos(e: Int): Offset { val m = edgeMid(e); val d = m - Offset(cx, cy); val l = d.getDistance(); return m + d / l * unit * 0.62f }
}

private fun nearest(o: Offset, cands: List<Int>, pos: (Int) -> Offset, maxD: Float): Int? =
    cands.minByOrNull { (pos(it) - o).getDistance() }?.takeIf { (pos(it) - o).getDistance() <= maxD }

// =====================================================================  drawing

private fun DrawScope.hexPath(g: RGeo, h: Int, shrink: Float = 0.97f): Path {
    val c = g.hex(h)
    return Path().apply {
        RealmMap.hexVerts[h].forEachIndexed { k, v -> val p = c + (g.vert(v) - c) * shrink; if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        close()
    }
}

private fun DrawScope.terrainArt(t: Int, c: Offset, u: Float) {
    val dark = TERRAIN_DARK[t]
    when (t) {
        Realm.TIMBER -> for ((dx, dy) in listOf(-0.32f to -0.30f, 0.0f to -0.42f, 0.30f to -0.28f)) {
            val p = c + Offset(dx * u, dy * u)
            drawPath(Path().apply { moveTo(p.x, p.y - 0.22f * u); lineTo(p.x + 0.13f * u, p.y + 0.05f * u); lineTo(p.x - 0.13f * u, p.y + 0.05f * u); close() }, dark)
            drawLine(Color(0xFF4E342E), p + Offset(0f, 0.05f * u), p + Offset(0f, 0.11f * u), 0.04f * u)
        }
        Realm.ORE -> {
            for ((dx, s) in listOf(-0.18f to 0.30f, 0.16f to 0.24f)) {
                val p = c + Offset(dx * u, -0.32f * u)
                drawPath(Path().apply { moveTo(p.x, p.y - s * u); lineTo(p.x + s * u, p.y + 0.12f * u); lineTo(p.x - s * u, p.y + 0.12f * u); close() }, dark)
                drawPath(Path().apply { moveTo(p.x, p.y - s * u); lineTo(p.x + s * 0.3f * u, p.y - s * 0.6f * u); lineTo(p.x - s * 0.3f * u, p.y - s * 0.6f * u); close() }, Color.White.copy(alpha = 0.85f))
            }
        }
        Realm.GRAIN -> for (k in -2..2) {
            val x = c.x + k * 0.12f * u
            drawLine(dark, Offset(x, c.y - 0.58f * u), Offset(x, c.y - 0.22f * u), 0.035f * u, StrokeCap.Round)
            drawOval(dark, Offset(x - 0.035f * u, c.y - 0.62f * u), Size(0.07f * u, 0.16f * u))
        }
        Realm.BEASTS -> {
            val p = c + Offset(0f, -0.36f * u)
            drawPath(Path().apply {
                moveTo(p.x - 0.32f * u, p.y + 0.02f * u); lineTo(p.x - 0.05f * u, p.y - 0.06f * u); lineTo(p.x + 0.04f * u, p.y - 0.22f * u)
                lineTo(p.x + 0.1f * u, p.y - 0.04f * u); lineTo(p.x + 0.3f * u, p.y + 0.0f * u); lineTo(p.x + 0.04f * u, p.y + 0.1f * u); close()
            }, dark)
            drawCircle(dark, 0.06f * u, p + Offset(0.3f * u, -0.04f * u))
        }
        Realm.MANA -> for ((dx, s) in listOf(-0.18f to 0.18f, 0.02f to 0.26f, 0.22f to 0.15f)) {
            val p = c + Offset(dx * u, -0.34f * u)
            drawPath(Path().apply { moveTo(p.x, p.y - s * u); lineTo(p.x + s * 0.38f * u, p.y); lineTo(p.x, p.y + s * 0.5f * u); lineTo(p.x - s * 0.38f * u, p.y); close() }, Color(0xFFD7C8FF))
            drawPath(Path().apply { moveTo(p.x, p.y - s * u); lineTo(p.x + s * 0.38f * u, p.y); lineTo(p.x, p.y + s * 0.5f * u); close() }, dark.copy(alpha = 0.6f))
        }
        else -> {
            drawLine(dark, c + Offset(-0.3f * u, -0.4f * u), c + Offset(-0.05f * u, -0.25f * u), 0.03f * u)
            drawLine(dark, c + Offset(-0.05f * u, -0.25f * u), c + Offset(0.2f * u, -0.45f * u), 0.03f * u)
            drawCircle(Color(0xFFEDE0C8), 0.06f * u, c + Offset(0.25f * u, -0.2f * u))
        }
    }
}

private fun DrawScope.village(p: Offset, u: Float, color: Color) {
    val s = 0.2f * u
    val path = Path().apply {
        moveTo(p.x - s, p.y + s * 0.8f); lineTo(p.x - s, p.y - s * 0.1f); lineTo(p.x, p.y - s); lineTo(p.x + s, p.y - s * 0.1f); lineTo(p.x + s, p.y + s * 0.8f); close()
    }
    drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(s * 0.5f))
    drawPath(path, color)
    drawPath(path, Color.White, style = Stroke(s * 0.16f))
}

private fun DrawScope.tower(p: Offset, u: Float, color: Color) {
    val s = 0.22f * u
    val path = Path().apply {
        moveTo(p.x - s, p.y + s); lineTo(p.x - s, p.y - s * 1.1f); lineTo(p.x - s * 0.55f, p.y - s * 1.1f); lineTo(p.x - s * 0.55f, p.y - s * 0.8f)
        lineTo(p.x - s * 0.15f, p.y - s * 0.8f); lineTo(p.x - s * 0.15f, p.y - s * 1.1f); lineTo(p.x + s * 0.25f, p.y - s * 1.1f); lineTo(p.x + s * 0.25f, p.y - s * 0.8f)
        lineTo(p.x + s * 0.6f, p.y - s * 0.8f); lineTo(p.x + s * 0.6f, p.y - s * 1.1f); lineTo(p.x + s, p.y - s * 1.1f); lineTo(p.x + s, p.y + s); close()
    }
    drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(s * 0.5f))
    drawPath(path, color)
    drawPath(path, Color.White, style = Stroke(s * 0.15f))
    drawRect(Color.White.copy(alpha = 0.9f), Offset(p.x - s * 0.18f, p.y - s * 0.2f), Size(s * 0.36f, s * 0.5f))
}

private fun DrawScope.drawDragon(c: Offset, u: Float, alpha: Float) {
    val s = 0.36f * u
    val body = Path().apply {
        moveTo(c.x - s * 1.1f, c.y + s * 0.2f); quadraticTo(c.x - s * 0.2f, c.y - s * 0.1f, c.x + s * 0.6f, c.y - s * 0.3f)
        lineTo(c.x + s * 1.0f, c.y - s * 0.55f); lineTo(c.x + s * 0.95f, c.y - s * 0.2f); quadraticTo(c.x + s * 0.2f, c.y + s * 0.3f, c.x - s * 1.1f, c.y + s * 0.2f); close()
    }
    val wing = Path().apply {
        moveTo(c.x - s * 0.1f, c.y - s * 0.05f); lineTo(c.x - s * 0.6f, c.y - s * 1.1f); lineTo(c.x - s * 0.2f, c.y - s * 0.85f)
        lineTo(c.x + s * 0.05f, c.y - s * 1.2f); lineTo(c.x + s * 0.25f, c.y - s * 0.8f); lineTo(c.x + s * 0.35f, c.y - s * 0.15f); close()
    }
    drawCircle(Color(0xFFFF7043).copy(alpha = 0.35f * alpha), s * 1.4f, c)
    drawPath(wing, Color(0xFF7F1D1D).copy(alpha = alpha)); drawPath(body, Color(0xFFB71C1C).copy(alpha = alpha))
    drawCircle(Color(0xFFFFEB3B).copy(alpha = alpha), s * 0.07f, Offset(c.x + s * 0.8f, c.y - s * 0.38f))
}

@Composable
fun RealmBoard(s: RealmSession, targets: DemoTargets?, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "glow")
    val vT = s.vertexTargets(); val eT = s.edgeTargets(); val tT = s.tileTargets()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val wDp = maxWidth; val hDp = min(maxHeight.value, maxWidth.value * 0.93f).dp
        Box(Modifier.size(wDp, hDp)) {
            Canvas(Modifier.size(wDp, hDp).pointerInput(s, vT, eT, tT) {
                detectTapGestures { o ->
                    val geo = RGeo(size.width.toFloat(), size.height.toFloat())
                    nearest(o, vT, geo::vert, geo.unit * 0.55f)?.let { s.tapVertex(it); return@detectTapGestures }
                    nearest(o, eT, geo::edgeMid, geo.unit * 0.55f)?.let { s.tapEdge(it); return@detectTapGestures }
                    nearest(o, tT, geo::hex, geo.unit * 0.9f)?.let { s.tapTile(it) }
                }
            }) {
                val geo = RGeo(size.width, size.height); val u = geo.unit
                // sea
                drawCircle(Brush.radialGradient(listOf(Color(0xFF3B8FC2), Color(0xFF1C4E7A)), center = Offset(geo.cx, geo.cy), radius = u * 5.4f), u * 5.15f, Offset(geo.cx, geo.cy))
                // portals
                for (k in 0 until 9) {
                    val e = g.portEdge[k]; val pp = geo.portPos(e)
                    for (v in intArrayOf(RealmMap.edgeA[e], RealmMap.edgeB[e])) drawLine(Color(0xFFE8D5B5), pp, geo.vert(v), u * 0.06f, StrokeCap.Round)
                    val col = if (g.portType[k] == 5) Color(0xFFF4E9D8) else RES_COLOR[g.portType[k]]
                    drawCircle(Color(0xFF6A3FB5), u * 0.3f, pp)
                    drawCircle(col, u * 0.24f, pp)
                    glyph(if (g.portType[k] == 5) "3:1" else "2:1", pp, u * 0.2f, if (g.portType[k] == 5) Color(0xFF3B2A5A) else Color.White)
                }
                // tiles
                for (h in 0 until RealmMap.HEXES) {
                    val c = geo.hex(h); val path = hexPath(geo, h)
                    if (!g.revealed[h]) {
                        drawPath(path, Brush.radialGradient(listOf(Color(0xFFF1F3F5), Color(0xFFB7C0C9)), center = c, radius = u))
                        for ((dx, dy, r) in listOf(Triple(-0.3f, 0.1f, 0.3f), Triple(0.15f, -0.1f, 0.36f), Triple(0.35f, 0.2f, 0.25f))) drawCircle(Color.White.copy(alpha = 0.7f), r * u, c + Offset(dx * u, dy * u))
                        glyph("?", c + Offset(0f, 0.05f * u), u * 0.5f, Color(0xFF8A96A3))
                        continue
                    }
                    val t = g.terrain[h]
                    drawPath(path, Brush.radialGradient(listOf(TERRAIN_COLOR[t].copy(alpha = 1f), TERRAIN_DARK[t]), center = c - Offset(0f, u * 0.3f), radius = u * 1.2f))
                    terrainArt(t, c, u)
                    if (g.night) drawPath(path, Color(0xFF0D1333).copy(alpha = if (t == Realm.MANA) 0.15f else 0.32f))
                    if (g.number[h] > 0) {
                        val tc = c + Offset(0f, u * 0.2f)
                        drawCircle(Color.Black.copy(alpha = 0.25f), u * 0.31f, tc + Offset(0f, u * 0.03f))
                        drawCircle(Color(0xFFFFF4DE), u * 0.3f, tc)
                        val hot = g.number[h] == 6 || g.number[h] == 8
                        glyph("${g.number[h]}", tc - Offset(0f, u * 0.05f), u * 0.3f, if (hot) Color(0xFFC62828) else Color(0xFF2B2B2B))
                        val pips = Realm.pips(g.number[h])
                        for (k in 0 until pips) drawCircle(if (hot) Color(0xFFC62828) else Color(0xFF2B2B2B), u * 0.022f, tc + Offset((k - (pips - 1) / 2f) * u * 0.06f, u * 0.17f))
                    }
                    if (g.number[h] == g.lastRoll && g.lastRoll != 7 && g.phase != Realm.SETUP) drawPath(path, Color(0xFFFFF59D).copy(alpha = 0.9f), style = Stroke(u * 0.07f))
                }
                // tile targets (dragon)
                for (h in tT) drawPath(hexPath(geo, h, 0.86f), Color(0xFFFF7043).copy(alpha = pulse), style = Stroke(u * 0.08f))
                // ley stones
                for (v in g.ley) if (g.leyVisible(v)) {
                    val p = geo.vert(v)
                    drawCircle(Color(0xFFB388FF).copy(alpha = 0.35f), u * 0.26f, p)
                    drawPath(Path().apply { moveTo(p.x, p.y - u * 0.17f); lineTo(p.x + u * 0.11f, p.y); lineTo(p.x, p.y + u * 0.17f); lineTo(p.x - u * 0.11f, p.y); close() }, Color(0xFF7C4DFF))
                }
                // roads
                for (e in 0 until RealmMap.EDGES) {
                    val o = g.eOwner[e]; if (o < 0) continue
                    val a = geo.vert(RealmMap.edgeA[e]); val b = geo.vert(RealmMap.edgeB[e]); val d = (b - a) * 0.14f
                    drawLine(Color.Black.copy(alpha = 0.45f), a + d, b - d, u * 0.2f, StrokeCap.Round)
                    drawLine(if (o == 0) YOU else RIVAL, a + d, b - d, u * 0.13f, StrokeCap.Round)
                }
                for (e in eT) {
                    val a = geo.vert(RealmMap.edgeA[e]); val b = geo.vert(RealmMap.edgeB[e]); val d = (b - a) * 0.18f
                    drawLine((if (s.mode == RealmSession.Mode.FIREBALL) Color(0xFFFF7043) else Color.White).copy(alpha = pulse), a + d, b - d, u * 0.12f, StrokeCap.Round,
                        pathEffect = if (s.mode == RealmSession.Mode.FIREBALL) null else PathEffect.dashPathEffect(floatArrayOf(u * 0.12f, u * 0.08f)))
                }
                // buildings
                for (v in 0 until RealmMap.VERTS) {
                    val o = g.vOwner[v]; if (o < 0) continue
                    val col = if (o == 0) YOU else RIVAL
                    if (g.vLevel[v] == 2) tower(geo.vert(v), u, col) else village(geo.vert(v), u, col)
                }
                for (v in vT) drawCircle(Color.White.copy(alpha = pulse), u * 0.17f, geo.vert(v), style = Stroke(u * 0.07f))
                // dragon
                if (!g.dragonGone) drawDragon(geo.hex(g.dragon) + Offset(0f, -u * 0.05f), u, 1f)
                // computer's last move
                val la = s.lastAi
                if (la >= 0) {
                    val p = when { la >= 200 -> geo.hex(la - 200); la >= 100 -> geo.edgeMid(la - 100); else -> geo.vert(la) }
                    drawCircle(RIVAL.copy(alpha = 0.8f), u * 0.34f, p, style = Stroke(u * 0.05f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.1f, u * 0.07f))))
                }
                val cf = s.coachFrom
                if (cf >= 0) coachRings(listOf(when { cf >= 200 -> geo.hex(cf - 200); cf >= 100 -> geo.edgeMid(cf - 100); else -> geo.vert(cf) }), u * 0.4f)
            }
            if (targets != null) {
                val geo = with(LocalDensity.current) { RGeo(wDp.toPx(), hDp.toPx()) }
                val r = geo.unit * 0.3f
                CellTargets(targets, (0 until RealmMap.VERTS).map { "v-$it" to Rect(geo.vert(it), r) } + (0 until RealmMap.EDGES).map { "e-$it" to Rect(geo.edgeMid(it), r) })
            }
        }
    }
}

// =====================================================================  panels

@Composable
private fun ResChip(r: Int, n: Int, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(RES_COLOR[r]))
        Spacer(Modifier.width(4.dp))
        Column {
            Text(RES_SHORT[r], fontSize = 10.sp, lineHeight = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$n", fontSize = 15.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CostDots(cost: IntArray) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (r in 0..4) repeat(cost[r]) { Box(Modifier.size(7.dp).clip(CircleShape).background(RES_COLOR[r])) }
    }
}

@Composable
private fun BuildButton(label: String, cost: IntArray?, active: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = when { active -> MaterialTheme.colorScheme.onPrimary; enabled -> MaterialTheme.colorScheme.onSurface; else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) })
            if (cost != null) { Spacer(Modifier.height(3.dp)); CostDots(cost) }
        }
    }
}

@Composable
private fun TwistChip(g: Realm) {
    val text = when (g.twist) {
        Realm.FOG -> "Ley power ${g.leyPower(0)} · rival ${g.leyPower(1)}"
        Realm.WAKE -> if (g.dragonGone) (if (g.slayer == 0) "You slew the dragon" else "The rival slew the dragon") else "Dragon: ${g.dragonHp - g.dragonWounds} of ${g.dragonHp} health"
        else -> if (g.night) "☾ Night · mana ×2, dragon hunts" else "☀ Day · night comes next round"
    }
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ScoreBar(s: RealmSession) {
    val g = s.g
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.puresudoku.ui.games.board.ScoreChip("You", "${g.vp(0)}", YOU, g.turn == 0 && g.phase != Realm.SETUP)
        com.puresudoku.ui.games.board.ScoreChip("Rival", "${g.vp(1, false)}${if (g.spells[1][Realm.RELIC] > 0 || g.spells[1].sum() > 0) "+?" else ""}", RIVAL, g.turn == 1 && g.phase != Realm.SETUP)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text("Goal ${g.target} · rival holds ${g.total(1)} cards", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            val titles = listOfNotNull(
                g.roadHolder.takeIf { it >= 0 }?.let { "Great Road: ${if (it == 0) "you" else "rival"}" },
                g.hostHolder.takeIf { it >= 0 }?.let { "Host: ${if (it == 0) "you" else "rival"}" },
            )
            if (titles.isNotEmpty()) Text(titles.joinToString(" · "), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RealmUi(s: RealmSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    var showTrade by remember { mutableStateOf(false) }
    var showSpells by remember { mutableStateOf(false) }
    val shake = remember(s.rollId) { Animatable(0f) }
    LaunchedEffect(s.rollId) { shake.animateTo(0f, keyframes { durationMillis = 420; 14f at 60; -10f at 140; 6f at 220; -3f at 320 }) }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        ScoreBar(s)
        Spacer(Modifier.height(4.dp))
        TwistChip(g)
        RealmBoard(s, targets, Modifier.fillMaxWidth().weight(1f))
        // dice + events
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (g.lastRoll > 0) {
                for (d in g.lastDie) {
                    Box(Modifier.padding(end = 4.dp).size(26.dp).clip(RoundedCornerShape(6.dp)).background(if (g.lastRoll == 7) Color(0xFFFFCDD2) else Color(0xFFFFFBF0))
                        .border(1.dp, Color(0xFF555555), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                        Text("$d", fontWeight = FontWeight.Bold, color = Color(0xFF222222), modifier = Modifier.padding(start = shake.value.coerceAtLeast(0f).dp))
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(s.events.takeLast(2).joinToString("\n"), fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0..4) ResChip(r, g.res[0][r], Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        com.puresudoku.ui.games.board.TurnBanner(s.prompt, s.thinking || (g.actor == 1 && s.outcome == Outcome.PLAYING && s.offer == null))
        Spacer(Modifier.height(6.dp))
        val offer = s.offer
        when {
            offer != null -> Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(horizontal = 12.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    fun words(c: IntArray) = (0..4).filter { c[it] > 0 }.joinToString(" + ") { "${c[it]} ${Realm.RES[it]}" }
                    Text("Rival offers ${words(offer.give)} for your ${words(offer.want)}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { s.answerOffer(false) }) { Text("No") }
                    Button(onClick = { s.answerOffer(true) }) { Text("Deal") }
                }
            }
            s.myMove && g.phase == Realm.DISCARD -> DiscardPanel(s)
            s.myMove && g.phase == Realm.ROLL -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (g.canPlay(0, Realm.KNIGHT)) OutlinedButton(onClick = { s.playSpell(Realm.KNIGHT) }) { Text("Knight first") }
                Button(onClick = s::roll, modifier = Modifier.demoTarget(targets, "roll")) { Text("Roll") }
            }
            s.myMove && g.phase == Realm.MAIN -> FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp).demoTarget(targets, "build"),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val free = g.freeRoads > 0
                BuildButton("Road", Realm.COST_ROAD, s.mode == RealmSession.Mode.ROAD || free, !free && g.can(0, Realm.COST_ROAD)) { s.toggleMode(RealmSession.Mode.ROAD) }
                BuildButton("Village", Realm.COST_VILLAGE, s.mode == RealmSession.Mode.VILLAGE, !free && g.can(0, Realm.COST_VILLAGE)) { s.toggleMode(RealmSession.Mode.VILLAGE) }
                BuildButton("Tower", Realm.COST_TOWER, s.mode == RealmSession.Mode.TOWER, !free && g.can(0, Realm.COST_TOWER)) { s.toggleMode(RealmSession.Mode.TOWER) }
                BuildButton("Spell", Realm.COST_SPELL, false, !free && g.can(0, Realm.COST_SPELL) && g.deck.isNotEmpty()) { s.buySpell() }
                BuildButton("Trade", null, false, !free) { showTrade = true }
                BuildButton("Spells ${g.spells[0].sum()}", null, s.mode == RealmSession.Mode.FIREBALL, g.spells[0].sum() > 0) { showSpells = true }
                BuildButton("End turn", null, false, !free) { s.endTurn() }
            }
            else -> Spacer(Modifier.height(44.dp))
        }
        Spacer(Modifier.height(if (targets == null) 10.dp else 4.dp))
    }
    if (showTrade) TradeDialog(s) { showTrade = false }
    if (showSpells) SpellsDialog(s) { showSpells = false }
}

@Composable
private fun DiscardPanel(s: RealmSession) {
    val g = s.g
    val need = g.discardNeed[0]
    var pick by remember(g.lastRoll, s.rollId) { mutableStateOf(IntArray(5)) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(horizontal = 10.dp)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Tap resources to discard: ${pick.sum()} of $need", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0..4) {
                    Column(Modifier.clip(RoundedCornerShape(10.dp)).background(RES_COLOR[r].copy(alpha = 0.18f))
                        .clickable { if (pick[r] < g.res[0][r] && pick.sum() < need) pick = pick.copyOf().also { it[r]++ } }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(RES_SHORT[r], fontSize = 10.sp)
                        Text("${g.res[0][r] - pick[r]}", fontWeight = FontWeight.Bold)
                        if (pick[r] > 0) Text("−${pick[r]}", fontSize = 11.sp, color = RIVAL, modifier = Modifier.clickable { pick = pick.copyOf().also { it[r]-- } })
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                TextButton(onClick = { pick = IntArray(5) }) { Text("Reset") }
                Button(enabled = pick.sum() == need, onClick = { s.discard(pick) }) { Text("Discard") }
            }
        }
    }
}

@Composable
private fun TradeDialog(s: RealmSession, onDismiss: () -> Unit) {
    val g = s.g
    var give by remember { mutableIntStateOf(-1) }
    var get by remember { mutableIntStateOf(-1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trade with the merchant") },
        text = {
            Column {
                Text("Give", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (r in 0..4) {
                        val rate = g.portRate(0, r); val ok = g.res[0][r] >= rate
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (give == r) RES_COLOR[r] else RES_COLOR[r].copy(alpha = if (ok) 0.2f else 0.06f))
                            .clickable(enabled = ok) { give = r }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(RES_SHORT[r], fontSize = 10.sp, color = if (give == r) Color.White else Color.Unspecified)
                            Text("$rate:1", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (give == r) Color.White else Color.Unspecified)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Get 1", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (r in 0..4) {
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (get == r) RES_COLOR[r] else RES_COLOR[r].copy(alpha = 0.2f))
                            .clickable(enabled = r != give) { get = r }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(RES_SHORT[r], fontSize = 10.sp, color = if (get == r) Color.White else Color.Unspecified)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Settle next to a portal to trade 3:1 (any) or 2:1 (its resource).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(enabled = give >= 0 && get >= 0 && give != get && g.res[0][give] >= g.portRate(0, give), onClick = {
                s.trade(give, get); if (g.res[0][give] < g.portRate(0, give)) give = -1
            }) { Text(if (give >= 0) "Trade ${g.portRate(0, give)} for 1" else "Trade") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SpellsDialog(s: RealmSession, onDismiss: () -> Unit) {
    val g = s.g
    var harvest by remember { mutableStateOf<List<Int>>(emptyList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your spells") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (g.spellPlayed) Text("You've already cast a spell this turn.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (k in 0..4) {
                    val n = g.spells[0][k]; if (n == 0) continue
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${Realm.SPELLS[k]} ×$n", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (k != Realm.RELIC && k != Realm.HARVEST) FilledTonalButton(enabled = g.canPlay(0, k), onClick = { s.playSpell(k); onDismiss() }) { Text("Cast") }
                        }
                        Text(Realm.SPELL_TEXT[k], fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (k == Realm.HARVEST && g.canPlay(0, k)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                                for (r in 0..4) Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(RES_COLOR[r].copy(alpha = 0.25f + 0.35f * harvest.count { it == r }))
                                    .clickable { harvest = (harvest + r).takeLast(2) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(RES_SHORT[r] + if (harvest.count { it == r } > 0) " ×${harvest.count { it == r }}" else "", fontSize = 10.sp)
                                }
                            }
                            Button(enabled = harvest.size == 2, onClick = { s.playSpell(Realm.HARVEST, harvest[0], harvest[1]); onDismiss() }) { Text("Cast Harvest Moon") }
                        }
                        if (n > 0 && g.fresh[k] >= n && g.turn == 0) Text("Bought this turn: castable next turn", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (g.spells[0].sum() == 0) Text("No spells yet. Buy one for ore, grain and beasts.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
