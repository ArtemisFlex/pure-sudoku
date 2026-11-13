package com.puresudoku.ui.games.realm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.mutableFloatStateOf
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
import com.puresudoku.ui.theme.accent
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
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
import com.puresudoku.core.board.RealmGrid
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.games.board.CellTargets
import com.puresudoku.ui.games.board.AiWorker
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

    // ---------------- heralds: one card per rival action, shown in order

    private fun announce(icon: RI, title: String, detail: String = "", ms: Long = 1700, mine: Boolean = false) = announce(title, detail, ms, mine, icon)

    private fun spotWords(v: Int): String {
        val names = listOf("forest", "mountain", "fields", "meadow", "spires", "waste")
        val parts = g.map.vertHexes[v].filter { g.revealed[it] && g.terrain[it] != Realm.WASTE }.map { "${g.number[it]} ${names[g.terrain[it]]}" }
        return if (parts.isEmpty()) "On the edge of the unknown" else "Touching " + parts.joinToString(", ")
    }

    private fun rivalCard(a: RAct, tradeRate: Int, logs: List<String>) {
        val quiet = listOf("Rival bought", "Rival discarded", "Rival cast", "Rival sent a Knight", "Rival burned a road")
        val rest = logs.filter { l -> quiet.none { l.startsWith(it) } }
        fun detail(vararg first: String) = (first.toList() + rest).filter { it.isNotEmpty() }.joinToString("\n")
        when (a) {
            RAct.Roll -> announce(RI.DICE, "Rival rolls ${g.lastDie[0]} + ${g.lastDie[1]} = ${g.lastRoll}",
                detail(if (g.lastRoll == 7) (if (g.twist == Realm.ASH) "" else "The dragon wakes!") else if (rest.isEmpty()) "Nobody collects anything" else ""), 2000)
            is RAct.Village -> announce(RI.VILLAGE, "Rival builds a village", detail(spotWords(a.v)))
            is RAct.Tower -> announce(RI.TOWER, "Rival raises a tower", detail("It now collects double: " + spotWords(a.v).lowercase()))
            is RAct.Road -> announce(RI.ROAD, if (g.phase == Realm.SETUP || g.setupStep in 1..3) "Rival lays a road" else "Rival builds a road", detail())
            is RAct.Dragon -> announce(RI.DRAGON, "Rival sends the dragon to the ${Realm.TERRAIN[g.terrain[a.tile]].lowercase()}", detail())
            is RAct.Discard -> announce(RI.DRAGON, "Rival discards ${a.counts.sum()} cards", detail("Too many cards when the 7 came"))
            is RAct.Trade -> announce(RI.TRADE, "Rival trades with the merchant", detail("$tradeRate ${Realm.RES[a.give]} for 1 ${Realm.RES[a.get]}"))
            RAct.BuySpell -> announce(RI.SCROLL, "Rival learns a spell", detail("It's hidden until cast"))
            is RAct.Spell -> announce(spellIcon(a.kind), "Rival casts ${g.spellNames[a.kind]}", detail(g.spellText[a.kind]), 2000)
            RAct.End -> announce(RI.END, "Rival ends its turn", detail())
            is RAct.Offer -> {}
        }
    }

    private val lvl get() = RealmAi.level(aiDifficulty)

    override val middleStat get() = MiddleStat.Counter("Points", "${g.vp(0)}/${g.target}")
    override val progress: Float get() = (g.vp(0) / g.target.toFloat()).coerceAtMost(0.95f)
    override val wonTitle get() = "The realm is yours!"
    override val lostTitle get() = "Your rival rules the realm"
    override val lostMessage get() = "The rival reached ${g.vp(1)} points. This game's save slot has been cleared."

    val myMove get() = outcome == Outcome.PLAYING && !thinking && !heraldBusy && g.actor == 0 && offer == null && g.phase != Realm.OVER

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

    fun tileTargets(): List<Int> = when {
        myMove && g.phase == Realm.DRAGON -> g.dragonTiles()
        else -> emptyList()
    }

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

    fun tapTile(t: Int) {
        if (t !in tileTargets()) return
        act(RAct.Dragon(t))
    }

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
        whenQuiet { aiStep() }
    }

    private fun code(a: RAct): Int = when (a) {
        is RAct.Village -> a.v; is RAct.Tower -> a.v; is RAct.Road -> 1000 + a.e; is RAct.Dragon -> 2000 + a.tile
        is RAct.Spell -> if (a.kind == Realm.FIREBALL) 1000 + a.a else -1
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
            val rate = if (a is RAct.Trade) g.portRate(1, a.give) else 0
            g.apply(a, rng)
            val logs = g.log.toList(); flush(); changed()
            rivalCard(a, rate, logs)
            if (g.phase == Realm.OVER) { whenQuiet { finishGame() }; return@computer }
            if (g.actor == 0 && g.phase == Realm.ROLL) announce(RI.DICE, "Your turn", "Roll the dice", 1200, mine = true)
            else if (g.actor == 0 && g.phase == Realm.SETUP) announce(RI.VILLAGE, "Your turn", "Place your village", 1200, mine = true)
            else if (g.actor == 0 && g.phase == Realm.DISCARD) announce(RI.DRAGON, "You must discard", "You hold more than 7 cards", 1400, mine = true)
            whenQuiet { aiStep() }
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
        heraldBusy -> "Watch the rival's moves · tap a card to skip"
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
        is RAct.Spell -> "Cast ${g.spellNames[a.kind]}" + if (a.kind == Realm.HARVEST) " for ${Realm.RES[a.a]} and ${Realm.RES[a.b]}." else "."
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
            val g = Realm.fromState(arr)
            return RealmSession(kind, Difficulty.valueOf(o.getString("difficulty")), g).also { it.moves = o.getJSONObject("state").optInt("moves"); it.restoreCommon(o) }
        }
    }
}


// =====================================================================  game definitions

abstract class RealmDefBase(final override val kind: GameKind, private val twist: Int) : GameDef {
    override fun generate(d: Difficulty): Session = RealmSession(kind, d, Realm.newGame(twist, d, Random(System.nanoTime())))
    override fun restore(o: JSONObject): Session = RealmSession.restore(kind, o).also { it.resume() }
    override val fullBleed: Boolean get() = true
    override val hasHeader: Boolean get() = true

    @Composable
    override fun Header(session: Session, actions: com.puresudoku.ui.games.HeaderActions) {
        RealmHeader(session, actions, when (twist) { Realm.WAKE -> "Wyrmrise · "; Realm.NIGHT -> "Nightfall · "; Realm.ASH -> "Ashfall · "; else -> "" })
    }
    override fun levelInfo(d: Difficulty): String {
        val tiles = com.puresudoku.core.board.RealmMap.forLevel(d.ordinal).HEXES
        val wastes = intArrayOf(1, 1, 2, 2, 3, 3)[d.ordinal]
        val fog = twist == Realm.FOG || (d.ordinal >= 2 && twist != Realm.ASH)
        return "First to ${Realm.target(d)} · $tiles tiles · $wastes waste${if (wastes > 1) "s" else ""}" + (if (fog) " · fog" else "") +
            (if (twist == Realm.WAKE && Realm.WILD_CAP[d.ordinal] > 0) " · wild dragons" else "") + (if (twist == Realm.ASH) " · volcano" else "") + " · " + (if (d.ordinal <= 1) "fair dice" else "real dice")
    }
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
            Realm.ASH -> "In Ashfall a volcano replaces the dragon. Every 7 it erupts: a second die picks which way, and lava runs out in a straight row, burying the tiles in its path. They pay no one, you or your rival, until they recover, and roads the lava crosses burn. Recovered land is renewed, and pays every building on it one extra."
            Realm.WAKE -> "In Wyrmrise the dragon is a boss. Every 7 makes it stronger, and each round it walks one tile toward whoever leads. Each Knight wounds it; land the final blow and you earn 2 points."
            else -> "In Nightfall the rounds alternate. At night the crystal spires pay double mana, but the dragon raids whoever leads, landing on their best tile and burning a card."
        }
        return s to listOf(
            LessonStep("Win the realm by reaching ${g.target} points before your rival. Villages are worth 1, towers 2. Every tile shows a number: when the dice roll it, buildings on its corners collect that resource."),
            LessonStep("Big numbers matter. 6 and 8 (in red) are rolled most; 2 and 12 hardly ever. The dots under each number show how often. Tap a glowing corner that touches good numbers and different resources.") { (it as RealmSession).g.count(0, 1) >= 1 },
            LessonStep("Every village needs a road. Tap a glowing edge next to it, pointing toward open land.") { (it as RealmSession).g.roads(0) >= 1 },
            LessonStep("Your rival now places two villages. Then you place your second one, which pays out one of each resource around it straight away.") { val x = (it as RealmSession).g; x.count(0, 1) >= 2 && x.roads(0) >= 2 },
            LessonStep("Setup is done. Each turn starts with a roll. Tap Roll.") { (it as RealmSession).g.lastRoll > 0 },
            LessonStep("Costs: a road is timber + mana. A village is timber, mana, grain and beasts. A tower is 3 ore + 2 grain. A spell is ore, grain and beasts. Short of something? Trade 4 of one resource for 1 of another, or less at a portal you've settled."),
            LessonStep(if (twist == Realm.ASH) "A 7 makes the volcano erupt, and anyone holding more than 7 cards loses half. There's no dragon: Knight spells steal a card from your rival instead."
                else "A 7 wakes the dragon: anyone holding more than 7 cards loses half, and the roller moves the dragon to block a tile and steal a card. Knight spells chase it away."),
            LessonStep(twistText),
            LessonStep("The Dragon's Waste grows nothing, but it can be claimed: be first to build 2 roads around it and a tower on one of its corners for 1 point and a free spell."),
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
        eiseriaBuildStep(kind, twist),
    )
}

object RealmforgeDef : RealmDefBase(GameKind.REALMFORGE, Realm.FOG) {
    override fun hint(d: Difficulty) = "Settle villages on good numbers, build roads to explore the fog, and reach the points goal first. 7s wake the dragon."
    override fun background(session: Session) = com.puresudoku.R.drawable.bg_realm_ocean
}

object RealmDragonDef : RealmDefBase(GameKind.REALM_DRAGON, Realm.WAKE) {
    override fun hint(d: Difficulty) = "Build and trade to reach the points goal. The dragon grows on every 7 and walks toward the leader each round. Wound it with Knights; the final blow is worth 2 points."
    override fun background(session: Session) = com.puresudoku.R.drawable.bg_realm_ocean
}

object RealmAshDef : RealmDefBase(GameKind.REALM_ASH, Realm.ASH) {
    override fun hint(d: Difficulty) = "Every 7 the volcano erupts and a second die picks which way its lava runs, burying a straight row of tiles for both players. Settle between the rows, and build on the richer renewed land."
    override fun background(session: Session) = com.puresudoku.R.drawable.bg_realm_ocean
}

object RealmNightDef : RealmDefBase(GameKind.REALM_NIGHT, Realm.NIGHT) {
    override fun hint(d: Difficulty) = "Rounds alternate day and night. At night mana tiles pay double, but the dragon raids whoever leads. Reach the points goal first."
    override fun background(session: Session) = com.puresudoku.R.drawable.bg_realm_ocean
}

// =====================================================================  board geometry

/** Board layout: at zoom 1 the board fits the gap between [top] and [bottom] (px); zoomed, it spreads under everything. */
internal class RGeo(
    val m: RealmGrid, val w: Float, val h: Float, zoom: Float = 1f, pan: Offset = Offset.Zero, top: Float = 0f, bottom: Float = 0f,
    /** The extent the island art is fitted to (Ashfall: the island inside its ring of sea), and a shrink to leave room for that ring. */
    ext: Float = m.extent, artK: Float = 1f,
) {
    val fh = (h - top - bottom).coerceAtLeast(h * 0.35f)
    /** Island art scale (screen px per source px): the whole island fits the gap at zoom 1. */
    val art = min(w, fh) / 1150f * zoom * artK
    /** Hex size: the board's outer corners reach the island's buildable edge. */
    val unit = art * RealmMap.ISLAND_FIT / ext
    val cx = w / 2 + pan.x; val cy = top + fh / 2 + pan.y
    fun pt(x: Float, y: Float) = Offset(cx + x * unit, cy + y * unit)
    /** A point on the island picture (source px). */
    fun artPt(px: Float, py: Float) = Offset(cx + (px - RealmMap.ISLAND_CX) * art, cy + (py - RealmMap.ISLAND_CY) * art)
    fun vert(v: Int) = pt(m.vertX[v], m.vertY[v])
    fun hex(h: Int) = pt(m.hexX[h], m.hexY[h])
    fun edgeMid(e: Int) = (vert(m.edgeA[e]) + vert(m.edgeB[e])) / 2f
    fun portPos(e: Int): Offset { val m = edgeMid(e); val d = m - Offset(cx, cy); val l = d.getDistance(); return m + d / l * unit * 0.62f }
}

/**
 * A semi-transparent arrow from tile centre [a] to the next tile [b], with chevrons flowing along it.
 * [flow] runs 0..1 over and over to move them.
 */
internal fun DrawScope.flowArrow(a: Offset, b: Offset, u: Float, color: Color, flow: Float, alpha: Float = 0.6f) {
    val d = b - a; val l = d.getDistance(); if (l < 1f) return
    val dir = d / l; val nrm = Offset(-dir.y, dir.x)
    val s = a + dir * (u * 0.3f); val e = b - dir * (u * 0.3f)
    val w = u * 0.13f; val headL = u * 0.34f; val headW = u * 0.3f
    val hb = e - dir * headL
    fun Path.to(p: Offset) = lineTo(p.x, p.y)
    val body = Path().apply {
        val p0 = s + nrm * (w * 0.7f); moveTo(p0.x, p0.y)
        to(hb + nrm * w); to(hb + nrm * headW); to(e); to(hb - nrm * headW); to(hb - nrm * w); to(s - nrm * (w * 0.7f)); close()
    }
    drawPath(body, color.copy(alpha = alpha))
    drawPath(body, Color.Black.copy(alpha = alpha * 0.5f), style = Stroke(u * 0.025f))
    for (k in 0..1) {
        val t = (flow + k * 0.5f) % 1f
        val c = s + (e - s) * t
        val fade = kotlin.math.sin(t * Math.PI.toFloat())
        val chev = Path().apply {
            val p0 = c - dir * (u * 0.08f) + nrm * (w * 0.85f); moveTo(p0.x, p0.y)
            to(c + dir * (u * 0.06f)); to(c - dir * (u * 0.08f) - nrm * (w * 0.85f))
        }
        drawPath(chev, Color.White.copy(alpha = 0.85f * fade * alpha), style = Stroke(u * 0.045f, cap = StrokeCap.Round))
    }
}

internal fun nearest(o: Offset, cands: List<Int>, pos: (Int) -> Offset, maxD: Float): Int? =
    cands.minByOrNull { (pos(it) - o).getDistance() }?.takeIf { (pos(it) - o).getDistance() <= maxD }

// =====================================================================  drawing

internal fun DrawScope.hexPath(g: RGeo, h: Int, shrink: Float = 0.97f): Path {
    val c = g.hex(h)
    return Path().apply {
        g.m.hexVerts[h].forEachIndexed { k, v -> val p = c + (g.vert(v) - c) * shrink; if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
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

internal fun DrawScope.tower(p: Offset, u: Float, color: Color) {
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
fun RealmBoard(s: RealmSession, targets: DemoTargets?, modifier: Modifier = Modifier, fitTop: Float = 0f, fitBottom: Float = 0f) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "glow")
    val vT = s.vertexTargets(); val eT = s.edgeTargets(); val tT = s.tileTargets()
    val art = rememberRealmArt(); val ash = rememberAshArt()
    val flow by rememberInfiniteTransition(label = "flow").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = androidx.compose.animation.core.LinearEasing)), label = "flow")
    var zoom by remember(s) { mutableFloatStateOf(1f) }
    var pan by remember(s) { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.clipToBounds()) {
        val wDp = maxWidth; val hDp = maxHeight
        val wPx = with(LocalDensity.current) { wDp.toPx() }; val hPx = with(LocalDensity.current) { hDp.toPx() }
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
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    zoomBy(zoomChange, centroid)
                    pan = clampPan(pan + panChange, zoom)
                }
            }.pointerInput(s, vT, eT, tT) {
                detectTapGestures { o ->
                    val geo = RGeo(g.map, size.width.toFloat(), size.height.toFloat(), zoom, pan, fitTop, fitBottom)
                    val reach = maxOf(geo.unit * 0.55f, 18.dp.toPx())
                    nearest(o, vT, geo::vert, reach)?.let { s.tapVertex(it); return@detectTapGestures }
                    nearest(o, eT, geo::edgeMid, reach)?.let { s.tapEdge(it); return@detectTapGestures }
                    nearest(o, tT, geo::hex, geo.unit * 0.9f)?.let { s.tapTile(it) }
                }
            }) {
                val geo = RGeo(g.map, size.width, size.height, zoom, pan, fitTop, fitBottom); val u = geo.unit; val m = g.map
                // the painted island
                run {
                    val k = geo.art * 1254f / art.island.width
                    val o = geo.artPt(0f, 0f)
                    drawImage(art.island, dstOffset = IntOffset(o.x.toInt(), o.y.toInt()),
                        dstSize = IntSize((art.island.width * k).toInt(), (art.island.height * k).toInt()), filterQuality = FilterQuality.Medium)
                }
                // tiles: painted art, or fog
                val tw = (sqrt3 * u).toInt() + 2; val th = (2 * u).toInt() + 2
                for (h in 0 until m.HEXES) {
                    val c = geo.hex(h)
                    val img = when {
                        !g.revealed[h] -> art.fog[h % art.fog.size]
                        g.twist != Realm.ASH -> art.tile(g, h)
                        h == g.volcano -> ash.volcano
                        // ruined land cools a step each turn, then lies ashen, then is renewed as ordinary land
                        g.stage[h] == 1 -> when { g.heat[h] >= 4 -> ash.lava; g.heat[h] == 3 -> ash.pool; g.heat[h] == 2 -> ash.cracked; else -> ash.embers }
                        g.stage[h] == 2 -> ash.ash
                        g.terrain[h] == Realm.ORE -> { val l = art.byTerrain[1].filterIndexed { i, _ -> i != 2 }; l[(0 until h).count { g.terrain[it] == Realm.ORE } % l.size] }
                        else -> art.tile(g, h)
                    }
                    drawImage(img, dstOffset = IntOffset((c.x - tw / 2f).toInt(), (c.y - th / 2f).toInt()), dstSize = IntSize(tw, th), filterQuality = FilterQuality.Medium)
                }
                if (g.night) {
                    drawRect(Color(0xFF0B1030).copy(alpha = 0.42f))
                    for (h in 0 until m.HEXES) if (g.revealed[h] && g.terrain[h] == Realm.MANA) drawPath(hexPath(geo, h, 0.95f), Color(0xFFB388FF).copy(alpha = 0.22f))
                }
                // gold borders
                for (h in 0 until m.HEXES) {
                    val p = hexPath(geo, h, 1f)
                    drawPath(p, Color(0xFF4A3510), style = Stroke(u * 0.08f))
                    drawPath(p, Brush.linearGradient(listOf(Color(0xFFF6E3A1), Color(0xFFC9972F), Color(0xFFF2D27A)), start = geo.hex(h) - Offset(u, u), end = geo.hex(h) + Offset(u, u)), style = Stroke(u * 0.05f))
                }
                // portals: a badge on each painted pier, roped to the coast edge it serves
                for (k in g.portEdge.indices) {
                    val e = g.portEdge[k]
                    val dock = m.docks.indexOf(e)
                    val pp = if (dock >= 0) geo.artPt(RealmMap.DOCK_PX[dock], RealmMap.DOCK_PY[dock]) else geo.edgeMid(e) * 1f
                    val bs = maxOf(u * 0.55f, geo.art * 62f)
                    for (v in intArrayOf(m.edgeA[e], m.edgeB[e])) {
                        val q = geo.vert(v)
                        drawLine(Color(0xFF3A2410).copy(alpha = 0.7f), pp, q, bs * 0.12f, StrokeCap.Round)
                        drawLine(Color(0xFFF2D27A), pp, q, bs * 0.06f, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(bs * 0.16f, bs * 0.1f)))
                        drawCircle(Color(0xFFF2D27A), bs * 0.1f, q)
                    }
                    val t = g.portType[k]
                    val pin = if (t == 5) art.pinAny else art.pins[t]
                    drawCircle(Color.Black.copy(alpha = 0.35f), bs * 0.5f, pp + Offset(0f, bs * 0.06f))
                    drawImage(pin, dstOffset = IntOffset((pp.x - bs / 2).toInt(), (pp.y - bs / 2).toInt()), dstSize = IntSize(bs.toInt(), bs.toInt()), filterQuality = FilterQuality.Medium)
                    if (t != 5) {
                        val ic = art.icons[t]; val iw = bs * 0.55f; val ih = iw * ic.height / ic.width; val ic0 = pp + Offset(bs * 0.5f, -bs * 0.38f)
                        drawCircle(Color(0xFFF8EED6), iw * 0.62f, ic0); drawCircle(Color(0xFFC9972F), iw * 0.62f, ic0, style = Stroke(bs * 0.04f))
                        drawImage(ic, dstOffset = IntOffset((ic0.x - iw / 2).toInt(), (ic0.y - ih / 2).toInt()), dstSize = IntSize(iw.toInt(), ih.toInt()), filterQuality = FilterQuality.Medium)
                    }
                }
                // resource bubble and number token on every known tile
                for (h in 0 until m.HEXES) {
                    if (!g.revealed[h] || h == g.volcano) continue
                    val c = geo.hex(h); val path = hexPath(geo, h)
                    val t = g.terrain[h]
                    if (g.twist == Realm.ASH && g.stage[h] == 3) drawPath(hexPath(geo, h, 0.86f), Color(0xFFFFD54F).copy(alpha = 0.55f), style = Stroke(u * 0.05f))
                    if (g.twist == Realm.ASH && g.scorched(h)) {
                        // ruined or ashen: turns until it changes, in place of the resource bubble
                        val b = c + Offset(0f, -u * 0.3f)
                        drawCircle(Color(0xFF2A0F0A).copy(alpha = 0.85f), u * 0.2f, b)
                        drawCircle(if (g.stage[h] == 1) Color(0xFFFF7043) else Color(0xFFB0A89A), u * 0.2f, b, style = Stroke(u * 0.03f))
                        glyph("${g.heat[h]}", b, u * 0.2f, Color.White)
                    } else {
                    val bc = c + Offset(0f, if (t == Realm.WASTE) 0f else -u * 0.3f)
                    val br = u * (if (t == Realm.WASTE) 0.26f else 0.2f)
                    drawCircle(Color.Black.copy(alpha = 0.25f), br, bc + Offset(0f, u * 0.025f))
                    drawCircle(Color(0xFFF8EED6), br, bc)
                    drawCircle(Color(0xFFC9972F), br, bc, style = Stroke(u * 0.03f))
                    if (t != Realm.WASTE) {
                        val ic = art.icons[t]; val iw = br * 1.45f; val ih = iw * ic.height / ic.width
                        drawImage(ic, dstOffset = IntOffset((bc.x - iw / 2).toInt(), (bc.y - ih / 2).toInt()), dstSize = IntSize(iw.toInt(), ih.toInt()), filterQuality = FilterQuality.Medium)
                    } else {
                        val owner = g.wasteClaim[h]
                        actionIcon(RI.CROWN, bc, br * 1.3f)
                        if (owner >= 0) drawCircle(if (owner == 0) YOU else RIVAL, br, bc, style = Stroke(u * 0.06f))
                    }
                    }
                    if (g.number[h] > 0) {
                        val tc = c + Offset(0f, u * 0.2f)
                        drawCircle(Color.Black.copy(alpha = 0.25f), u * 0.27f, tc + Offset(0f, u * 0.03f))
                        drawCircle(Color(0xFFFFF4DE), u * 0.26f, tc)
                        val hot = g.number[h] == 6 || g.number[h] == 8
                        glyph("${g.number[h]}", tc - Offset(0f, u * 0.04f), u * 0.27f, if (hot) Color(0xFFC62828) else Color(0xFF2B2B2B))
                        val pips = Realm.pips(g.number[h])
                        for (k in 0 until pips) drawCircle(if (hot) Color(0xFFC62828) else Color(0xFF2B2B2B), u * 0.02f, tc + Offset((k - (pips - 1) / 2f) * u * 0.055f, u * 0.15f))
                    }
                    if (g.number[h] == g.lastRoll && g.lastRoll != 7 && g.phase != Realm.SETUP) drawPath(path, Color(0xFFFFF59D).copy(alpha = 0.9f), style = Stroke(u * 0.07f))
                }
                // Ashfall: where the last eruption's lava ran, and the die that sent it (the next way is a secret until it's rolled)
                if (g.twist == Realm.ASH) {
                    var prev = geo.hex(g.volcano)
                    for (t in g.lastFlow) { val p = geo.hex(t); flowArrow(prev, p, u, Color(0xFFFF6D00), flow, 0.45f); prev = p }
                    if (g.lastAshDie in 1..6) die(geo.hex(g.volcano) + Offset(u * 0.42f, u * 0.45f), u * 0.36f, g.lastAshDie, true)
                }
                // tile targets (dragon)
                for (h in tT) drawPath(hexPath(geo, h, 0.86f), Color(0xFFFF7043).copy(alpha = pulse), style = Stroke(u * 0.08f))
                // ley stones: violet glow, white heart
                for (v in g.ley) if (g.leyVisible(v)) {
                    val p = geo.vert(v)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFD1B3FF).copy(alpha = 0.9f), Color(0xFF9C5CFF).copy(alpha = 0.45f), Color.Transparent), center = p, radius = u * 0.42f), u * 0.42f, p)
                    val dia = Path().apply { moveTo(p.x, p.y - u * 0.2f); lineTo(p.x + u * 0.13f, p.y); lineTo(p.x, p.y + u * 0.2f); lineTo(p.x - u * 0.13f, p.y); close() }
                    drawPath(dia, Color(0xFF6A2FE0)); drawPath(dia, Color.White, style = Stroke(u * 0.025f))
                    val core = Path().apply { moveTo(p.x, p.y - u * 0.1f); lineTo(p.x + u * 0.065f, p.y); lineTo(p.x, p.y + u * 0.1f); lineTo(p.x - u * 0.065f, p.y); close() }
                    drawPath(core, Color.White)
                }
                // roads
                for (e in 0 until m.EDGES) {
                    val o = g.eOwner[e]; if (o < 0) continue
                    val a = geo.vert(m.edgeA[e]); val b = geo.vert(m.edgeB[e])
                    val img = if (o == 0) art.roadYou else art.roadRival
                    val mid = (a + b) / 2f; val len = (b - a).getDistance() * 0.74f; val hh = len * img.height / img.width
                    rotate(Math.toDegrees(kotlin.math.atan2((b - a).y.toDouble(), (b - a).x.toDouble())).toFloat(), mid) {
                        drawImage(img, dstOffset = IntOffset((mid.x - len / 2).toInt(), (mid.y - hh / 2).toInt()), dstSize = IntSize(len.toInt(), hh.toInt()), filterQuality = FilterQuality.Medium)
                    }
                }
                for (e in eT) {
                    val a = geo.vert(m.edgeA[e]); val b = geo.vert(m.edgeB[e]); val d = (b - a) * 0.18f
                    drawLine((if (s.mode == RealmSession.Mode.FIREBALL) Color(0xFFFF7043) else Color.White).copy(alpha = pulse), a + d, b - d, u * 0.12f, StrokeCap.Round,
                        pathEffect = if (s.mode == RealmSession.Mode.FIREBALL) null else PathEffect.dashPathEffect(floatArrayOf(u * 0.12f, u * 0.08f)))
                }
                // buildings
                for (v in 0 until m.VERTS) {
                    val o = g.vOwner[v]; if (o < 0) continue
                    val col = if (o == 0) YOU else RIVAL
                    if (g.vLevel[v] == 2) tower(geo.vert(v), u, col)
                    else {
                        val img = if (o == 0) art.houseYou else art.houseRival
                        val w = u * 0.5f; val h = w * img.height / img.width; val p = geo.vert(v)
                        drawImage(img, dstOffset = IntOffset((p.x - w / 2).toInt(), (p.y - h * 0.62f).toInt()), dstSize = IntSize(w.toInt(), h.toInt()), filterQuality = FilterQuality.Medium)
                    }
                }
                for (v in vT) drawCircle(Color.White.copy(alpha = pulse), u * 0.17f, geo.vert(v), style = Stroke(u * 0.07f))
                // dragon
                fun dragonAt(h: Int, scale: Float) {
                    val img = art.dragon; val hh = u * 1.4f * scale; val w = hh * img.width / img.height; val c = geo.hex(h)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFF2A1A).copy(alpha = 0.75f), Color(0xFFD50000).copy(alpha = 0.35f), Color.Transparent), center = c, radius = u * 0.9f * scale), u * 0.9f * scale, c)
                    drawImage(img, dstOffset = IntOffset((c.x - w / 2).toInt(), (c.y - hh / 2).toInt()), dstSize = IntSize(w.toInt(), hh.toInt()), filterQuality = FilterQuality.Medium)
                }
                if (!g.dragonGone) dragonAt(g.dragon, 1f)
                for (h in g.wild) dragonAt(h, 0.8f)
                // computer's last move
                val la = s.lastAi
                if (la >= 0) {
                    val p = when { la >= 2000 -> geo.hex(la - 2000); la >= 1000 -> geo.edgeMid(la - 1000); else -> geo.vert(la) }
                    drawCircle(RIVAL.copy(alpha = 0.8f), u * 0.34f, p, style = Stroke(u * 0.05f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.1f, u * 0.07f))))
                }
                val cf = s.coachFrom
                if (cf >= 0) coachRings(listOf(when { cf >= 2000 -> geo.hex(cf - 2000); cf >= 1000 -> geo.edgeMid(cf - 1000); else -> geo.vert(cf) }), u * 0.4f)
            }
            if (targets != null) {
                val geo = RGeo(g.map, wPx, hPx, 1f, Offset.Zero, fitTop, fitBottom)
                val r = geo.unit * 0.3f
                CellTargets(targets, (0 until g.map.VERTS).map { "v-$it" to Rect(geo.vert(it), r) } + (0 until g.map.EDGES).map { "e-$it" to Rect(geo.edgeMid(it), r) })
            }
        }
    }
}

// =====================================================================  panels

internal val PANEL = RoundedCornerShape(14.dp)

@Composable
internal fun InkText(text: String, size: Int = 13, bold: Boolean = false, color: Color = INK, modifier: Modifier = Modifier, align: TextAlign? = null, lines: Int = Int.MAX_VALUE) {
    Text(text, fontSize = size.sp, lineHeight = (size + 3).sp, fontFamily = SERIF, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, color = color,
        modifier = modifier, textAlign = align, maxLines = lines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
}

@Composable
internal fun ResChip(r: Int, n: Int, modifier: Modifier = Modifier) {
    Column(modifier.parchment(PANEL).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ResImg(r, 24.dp)
            Spacer(Modifier.width(3.dp))
            InkText("$n", 19, bold = true, color = if (n == 0) INK_SOFT.copy(alpha = 0.6f) else INK)
        }
        InkText(RES_SHORT[r], 10, color = INK_SOFT)
    }
}

@Composable
internal fun CostIcons(cost: IntArray, size: Dp = 13.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        for (r in 0..4) repeat(cost[r]) { ResImg(r, size) }
    }
}

@Composable
internal fun BuildButton(label: String, icon: RI, cost: IntArray?, active: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.demoTarget(com.puresudoku.ui.tutorial.LocalDemoTargets.current, "b-" + label.substringBefore(' ').lowercase())
            .alpha(if (enabled || active) 1f else 0.45f).clip(shape)
            .then(if (active) Modifier.wood(shape) else Modifier.parchment(shape))
            .clickable(enabled = enabled || active, onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RIconOf(icon, 22.dp, if (active) PARCH_TOP else INK)
            Spacer(Modifier.width(4.dp))
            InkText(label, 13, bold = true, color = if (active) PARCH_TOP else INK)
        }
        if (cost != null) { Spacer(Modifier.height(2.dp)); CostIcons(cost) }
    }
}

@Composable
private fun TwistChip(g: Realm) {
    val (icon, text) = when (g.twist) {
        Realm.FOG -> RI.LEY to "Ley power: you ${g.leyPower(0)} · rival ${g.leyPower(1)}"
        Realm.WAKE -> RI.DRAGON to (if (g.dragonGone) (if (g.slayer == 0) "You slew the dragon!" else "The rival slew the dragon") else "Dragon health ${g.dragonHp - g.dragonWounds} of ${g.dragonHp}" + if (g.wild.isNotEmpty()) " · ${g.wild.size} wild" else "")
        Realm.ASH -> RI.FIRE to "Volcano · erupts on a 7 · lava runs ${g.rowLength()} tile${if (g.rowLength() == 1) "" else "s"} · ${(0 until g.map.HEXES).count { g.scorched(it) }} ruined"
        else -> if (g.night) RI.MOON to "Night · mana pays double · the dragon hunts" else RI.SUN to "Day · night falls next round"
    }
    Row(Modifier.parchment(RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        RIconOf(icon, 18.dp)
        Spacer(Modifier.width(6.dp))
        InkText(text, 12, bold = true)
        if (g.twist == Realm.WAKE && !g.dragonGone) {
            Spacer(Modifier.width(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (k in 0 until g.dragonHp) Box(Modifier.size(8.dp).clip(CircleShape).background(if (k < g.dragonHp - g.dragonWounds) Color(0xFFB71C1C) else INK_SOFT.copy(alpha = 0.3f)))
            }
        }
    }
}

@Composable
internal fun PlayerBanner(name: String, vp: String, color: Color, active: Boolean, extra: String, modifier: Modifier = Modifier) {
    Row(modifier.parchment(PANEL, if (active) color else GOLD, if (active) 2.5.dp else 1.5.dp).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            RIconOf(RI.SHIELD, 30.dp, color)
            Text(vp, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = SERIF, modifier = Modifier.padding(bottom = 2.dp))
        }
        Spacer(Modifier.width(6.dp))
        Column {
            InkText(name, 13, bold = true)
            InkText(extra, 10, color = INK_SOFT, lines = 1)
        }
    }
}

@Composable
private fun ScoreBar(s: RealmSession) {
    val g = s.g
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val titles0 = listOfNotNull("Great Road".takeIf { g.roadHolder == 0 }, "Host".takeIf { g.hostHolder == 0 }).joinToString(" · ").ifEmpty { "${g.spells[0].sum()} spells" }
        val titles1 = listOfNotNull("Great Road".takeIf { g.roadHolder == 1 }, "Host".takeIf { g.hostHolder == 1 }).joinToString(" · ").ifEmpty { "${g.total(1)} cards · ${g.spells[1].sum()} spells" }
        PlayerBanner("You", "${g.vp(0)}", YOU, g.turn == 0 && g.phase != Realm.SETUP, titles0, Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RIconOf(RI.CROWN, 22.dp)
            Text("${g.target}", fontFamily = SERIF, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(0f, 1f), 4f)))
        }
        PlayerBanner("Rival", "${g.vp(1, false)}" + if (g.spells[1].sum() > 0) "+" else "", RIVAL, g.turn == 1 && g.phase != Realm.SETUP, titles1, Modifier.weight(1f))
    }
}

@Composable
internal fun WoodButton(text: String, icon: RI?, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(modifier.demoTarget(com.puresudoku.ui.tutorial.LocalDemoTargets.current, "w-" + text.substringBefore(' ').lowercase()).alpha(if (enabled) 1f else 0.5f).clip(shape).wood(shape).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { RIconOf(icon, 22.dp, PARCH_TOP); Spacer(Modifier.width(8.dp)) }
        Text(text, color = PARCH_TOP, fontFamily = SERIF, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
internal fun PromptRibbon(text: String, busy: Boolean) {
    Row(Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(10.dp)).wood(RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (busy) { androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), color = PARCH_TOP, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
        Text(text, color = PARCH_TOP, fontFamily = SERIF, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RealmUi(s: RealmSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val full = targets == null
    var showTrade by remember { mutableStateOf(false) }
    var showSpells by remember { mutableStateOf(false) }
    var topH by remember { mutableIntStateOf(0) }
    var botH by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val shake = remember(s.rollId) { Animatable(0f) }
    LaunchedEffect(s.rollId) { shake.animateTo(0f, keyframes { durationMillis = 420; 14f at 60; -10f at 140; 6f at 220; -3f at 320 }) }

    Box(Modifier.fillMaxSize()) {
        RealmBoard(s, targets, Modifier.fillMaxSize(), topH.toFloat(), botH.toFloat())

        // top edge: both players and the version's twist
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topH = it.height }
                .then(if (full) Modifier.statusBarsPadding().padding(top = 54.dp) else Modifier).padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScoreBar(s)
            Spacer(Modifier.height(4.dp))
            TwistChip(g)
        }

        HeraldCard(s, Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { botH.toDp() } + 6.dp).padding(horizontal = 12.dp))

        // bottom edge: dice and chronicle, your resources, and what you can do
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { botH = it.height }
                .then(if (full) Modifier.navigationBarsPadding() else Modifier).padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).parchment(PANEL).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (g.lastRoll > 0) {
                    Canvas(Modifier.size(50.dp, 24.dp)) {
                        val d = size.height * 0.92f
                        die(Offset(d / 2 + shake.value, size.height / 2), d, g.lastDie[0], g.lastRoll == 7)
                        die(Offset(d * 1.65f - shake.value, size.height / 2), d, g.lastDie[1], g.lastRoll == 7)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                InkText(s.events.lastOrNull() ?: "The chronicle of the realm begins…", 12, color = INK_SOFT, modifier = Modifier.weight(1f), lines = 2)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (r in 0..4) ResChip(r, g.res[0][r], Modifier.weight(1f))
            }
            Spacer(Modifier.height(5.dp))
            PromptRibbon(s.prompt, s.thinking || s.heraldBusy || (g.actor == 1 && s.outcome == Outcome.PLAYING && s.offer == null))
            Spacer(Modifier.height(5.dp))
            val offer = s.offer
            when {
                offer != null -> Column(Modifier.padding(horizontal = 12.dp).parchment(PANEL).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    InkText("The rival offers a deal", 14, bold = true)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InkText("You get ", 13); CostIcons(offer.give, 20.dp)
                        InkText("   for ", 13); CostIcons(offer.want, 20.dp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WoodButton("Refuse", null) { s.answerOffer(false) }
                        WoodButton("Deal", RI.TRADE) { s.answerOffer(true) }
                    }
                }
                s.myMove && g.phase == Realm.DISCARD -> DiscardPanel(s)
                s.myMove && g.phase == Realm.ROLL -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (g.canPlay(0, Realm.KNIGHT)) BuildButton("Knight first", RI.KNIGHT, null, false, true) { s.playSpell(Realm.KNIGHT) }
                    WoodButton("Roll", RI.DICE, Modifier.demoTarget(targets, "roll")) { s.roll() }
                }
                s.myMove && g.phase == Realm.MAIN -> FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp).demoTarget(targets, "build"),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val free = g.freeRoads > 0
                    BuildButton("Road", RI.ROAD, Realm.COST_ROAD, s.mode == RealmSession.Mode.ROAD || free, !free && g.can(0, Realm.COST_ROAD)) { s.toggleMode(RealmSession.Mode.ROAD) }
                    BuildButton("Village", RI.VILLAGE, Realm.COST_VILLAGE, s.mode == RealmSession.Mode.VILLAGE, !free && g.can(0, Realm.COST_VILLAGE)) { s.toggleMode(RealmSession.Mode.VILLAGE) }
                    BuildButton("Tower", RI.TOWER, Realm.COST_TOWER, s.mode == RealmSession.Mode.TOWER, !free && g.can(0, Realm.COST_TOWER)) { s.toggleMode(RealmSession.Mode.TOWER) }
                    BuildButton("Spell", RI.SCROLL, Realm.COST_SPELL, false, !free && g.can(0, Realm.COST_SPELL) && g.deck.isNotEmpty()) { s.buySpell() }
                    BuildButton("Trade", RI.TRADE, null, false, !free) { showTrade = true }
                    BuildButton("Spells ${g.spells[0].sum()}", RI.BOOK, null, s.mode == RealmSession.Mode.FIREBALL, g.spells[0].sum() > 0) { showSpells = true }
                    BuildButton("End turn", RI.END, null, false, !free) { s.endTurn() }
                }
                else -> Spacer(Modifier.height(44.dp))
            }
        }
    }
    if (showTrade) TradeDialog(s) { showTrade = false }
    if (showSpells) SpellsDialog(s) { showSpells = false }
}

@Composable
private fun DiscardPanel(s: RealmSession) {
    val g = s.g
    val need = g.discardNeed[0]
    var pick by remember(g.lastRoll, s.rollId) { mutableStateOf(IntArray(5)) }
    Column(Modifier.padding(horizontal = 10.dp).parchment(PANEL).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RIconOf(RI.DRAGON, 20.dp)
            Spacer(Modifier.width(6.dp))
            InkText((if (g.twist == Realm.ASH) "A 7! " else "The dragon wakes! ") + "Discard ${pick.sum()} of $need", 14, bold = true)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0..4) {
                Column(Modifier.clip(RoundedCornerShape(10.dp)).background(RES_COLOR[r].copy(alpha = 0.15f))
                    .clickable { if (pick[r] < g.res[0][r] && pick.sum() < need) pick = pick.copyOf().also { it[r]++ } }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    ShieldImg(r, 34.dp)
                    InkText("${g.res[0][r] - pick[r]}", 15, bold = true)
                    InkText(if (pick[r] > 0) "−${pick[r]}" else " ", 12, bold = true, color = RIVAL, modifier = Modifier.clickable(enabled = pick[r] > 0) { pick = pick.copyOf().also { it[r]-- } })
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WoodButton("Reset", null) { pick = IntArray(5) }
            WoodButton("Discard", null, enabled = pick.sum() == need) { s.discard(pick) }
        }
    }
}

@Composable
internal fun ResPicker(selected: Int, label: (Int) -> String?, enabled: (Int) -> Boolean, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0..4) {
            val on = selected == r; val ok = enabled(r)
            Column(Modifier.weight(1f).alpha(if (ok) 1f else 0.35f).clip(RoundedCornerShape(10.dp))
                .background(if (on) RES_COLOR[r].copy(alpha = 0.45f) else RES_COLOR[r].copy(alpha = 0.12f))
                .border(if (on) 2.dp else 0.dp, if (on) GOLD else Color.Transparent, RoundedCornerShape(10.dp))
                .clickable(enabled = ok) { onPick(r) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ShieldImg(r, 34.dp)
                InkText(RES_SHORT[r], 10, color = INK_SOFT)
                label(r)?.let { InkText(it, 12, bold = true) }
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
        onDismissRequest = onDismiss, containerColor = PARCH_TOP, titleContentColor = INK, textContentColor = INK,
        icon = { RIconOf(RI.TRADE, 32.dp) },
        title = { Text("The travelling merchant", fontFamily = SERIF, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                InkText("You give", 13, bold = true)
                Spacer(Modifier.height(4.dp))
                ResPicker(give, { r -> "${g.portRate(0, r)} : 1 (${g.res[0][r]})" }, { r -> g.res[0][r] >= g.portRate(0, r) }) { give = it; if (get == it) get = -1 }
                Spacer(Modifier.height(10.dp))
                InkText("You get 1", 13, bold = true)
                Spacer(Modifier.height(4.dp))
                ResPicker(get, { null }, { r -> r != give }) { get = it }
                Spacer(Modifier.height(8.dp))
                InkText("Settle beside a portal on the coast to trade 3:1 (any) or 2:1 (its resource).", 12, color = INK_SOFT)
            }
        },
        confirmButton = {
            WoodButton(if (give >= 0) "Trade ${g.portRate(0, give)} for 1" else "Trade", null, enabled = give >= 0 && get >= 0 && give != get && g.res[0][give] >= g.portRate(0, give)) {
                s.trade(give, get); if (g.res[0][give] < g.portRate(0, give)) give = -1
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done", color = INK, fontFamily = SERIF, fontWeight = FontWeight.Bold) } },
    )
}

@Composable
private fun SpellsDialog(s: RealmSession, onDismiss: () -> Unit) {
    val g = s.g
    var harvest by remember { mutableStateOf<List<Int>>(emptyList()) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = PARCH_TOP, titleContentColor = INK, textContentColor = INK,
        icon = { RIconOf(RI.BOOK, 32.dp) },
        title = { Text("Your spellbook", fontFamily = SERIF, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (g.spellPlayed) InkText("You've already cast a spell this turn.", 12, color = INK_SOFT)
                for (k in 0..4) {
                    val n = g.spells[0][k]; if (n == 0) continue
                    Column(Modifier.padding(vertical = 4.dp).fillMaxWidth().parchment(PANEL).padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RIconOf(spellIcon(k), 30.dp)
                            Spacer(Modifier.width(8.dp))
                            InkText("${g.spellNames[k]} ×$n", 15, bold = true, modifier = Modifier.weight(1f))
                            if (k != Realm.RELIC && k != Realm.HARVEST) WoodButton("Cast", null, enabled = g.canPlay(0, k)) { s.playSpell(k); onDismiss() }
                        }
                        InkText(g.spellText[k], 12, color = INK_SOFT)
                        if (k == Realm.HARVEST && g.canPlay(0, k)) {
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (r in 0..4) {
                                    val cnt = harvest.count { it == r }
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(RES_COLOR[r].copy(alpha = 0.12f + 0.3f * cnt))
                                        .clickable { harvest = (harvest + r).takeLast(2) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        ResImg(r, 22.dp)
                                        InkText(if (cnt > 0) "×$cnt" else " ", 11, bold = true)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            WoodButton("Cast Harvest Moon", RI.MOON, enabled = harvest.size == 2) { s.playSpell(Realm.HARVEST, harvest[0], harvest[1]); onDismiss() }
                        }
                        if (g.fresh[k] >= n && g.turn == 0) InkText("Learned this turn: castable next turn", 11, bold = true, color = GOLD)
                    }
                }
                if (g.spells[0].sum() == 0) InkText("No spells yet. Buy one with ore, grain and beasts.", 13)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = INK, fontFamily = SERIF, fontWeight = FontWeight.Bold) } },
    )
}

/** The rival's current action, as a card over the board. Tap to skip ahead. */
@Composable
internal fun HeraldCard(s: Session, modifier: Modifier = Modifier) {
    androidx.compose.animation.AnimatedContent(
        s.herald, modifier = modifier, label = "herald",
        transitionSpec = {
            (androidx.compose.animation.slideInVertically { it / 2 } + androidx.compose.animation.fadeIn(tween(220))) togetherWith
                androidx.compose.animation.fadeOut(tween(150))
        },
    ) { h ->
        if (h == null) { Spacer(Modifier.height(1.dp)); return@AnimatedContent }
        val accent = if (h.mine) YOU else RIVAL
        val time = remember(h.id) { Animatable(1f) }
        LaunchedEffect(h.id) { time.animateTo(0f, tween(h.ms.toInt(), easing = androidx.compose.animation.core.LinearEasing)) }
        Column(Modifier.fillMaxWidth().clip(PANEL).parchment(PANEL, accent, 2.dp).clickable { s.skipHerald() }) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)).border(1.5.dp, accent, CircleShape), contentAlignment = Alignment.Center) {
                    RIconOf(h.art as? RI ?: RI.DICE, 32.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    InkText(h.title, 16, bold = true)
                    if (h.detail.isNotEmpty()) InkText(h.detail, 13, color = INK_SOFT)
                }
            }
            Box(Modifier.fillMaxWidth(time.value).height(3.dp).background(accent))
        }
    }
}

// =====================================================================  painted board art

internal val sqrt3 = kotlin.math.sqrt(3f)

internal class RealmArt(
    val island: ImageBitmap, val fog: List<ImageBitmap>, val byTerrain: List<List<ImageBitmap>>,
    val dragon: ImageBitmap, val houseYou: ImageBitmap, val houseRival: ImageBitmap, val roadYou: ImageBitmap, val roadRival: ImageBitmap,
    val pinAny: ImageBitmap, val pins: List<ImageBitmap>, val icons: List<ImageBitmap>, val stoneWall: ImageBitmap, val stoneRoad: ImageBitmap,
) {
    /** Each tile shows its own painting: the n-th tile of a terrain gets that terrain's n-th variant. */
    fun tile(g: Realm, h: Int): ImageBitmap {
        val t = g.terrain[h]
        val nth = (0 until h).count { g.terrain[it] == t }
        val list = byTerrain[t]
        return list[nth % list.size]
    }
}

@Composable
internal fun rememberRealmArt(): RealmArt {
    @Composable fun img(id: Int) = ImageBitmap.imageResource(id)
    return RealmArt(
        img(com.puresudoku.R.drawable.rt_island),
        listOf(img(com.puresudoku.R.drawable.rt_fog_1), img(com.puresudoku.R.drawable.rt_fog_2)),
        listOf(
            listOf(img(com.puresudoku.R.drawable.rt_timber_1), img(com.puresudoku.R.drawable.rt_timber_2), img(com.puresudoku.R.drawable.rt_timber_3), img(com.puresudoku.R.drawable.rt_timber_4)),
            listOf(img(com.puresudoku.R.drawable.rt_ore_1), img(com.puresudoku.R.drawable.rt_ore_2), img(com.puresudoku.R.drawable.rt_ore_3), img(com.puresudoku.R.drawable.rt_ore_4)),
            listOf(img(com.puresudoku.R.drawable.rt_grain_1), img(com.puresudoku.R.drawable.rt_grain_2)),
            listOf(img(com.puresudoku.R.drawable.rt_beasts_1), img(com.puresudoku.R.drawable.rt_beasts_2), img(com.puresudoku.R.drawable.rt_beasts_3), img(com.puresudoku.R.drawable.rt_beasts_4), img(com.puresudoku.R.drawable.rt_beasts_5)),
            listOf(img(com.puresudoku.R.drawable.rt_mana_1), img(com.puresudoku.R.drawable.rt_mana_2), img(com.puresudoku.R.drawable.rt_mana_3)),
            listOf(img(com.puresudoku.R.drawable.rt_waste_1)),
        ),
        img(com.puresudoku.R.drawable.rt_dragon), img(com.puresudoku.R.drawable.rt_house_b), img(com.puresudoku.R.drawable.rt_house_r),
        img(com.puresudoku.R.drawable.rt_road_b), img(com.puresudoku.R.drawable.rt_road_r),
        img(com.puresudoku.R.drawable.rt_pin_any),
        listOf(img(com.puresudoku.R.drawable.rt_pin_timber), img(com.puresudoku.R.drawable.rt_pin_ore), img(com.puresudoku.R.drawable.rt_pin_grain), img(com.puresudoku.R.drawable.rt_pin_beasts), img(com.puresudoku.R.drawable.rt_pin_mana)),
        listOf(img(com.puresudoku.R.drawable.rt_ic_timber), img(com.puresudoku.R.drawable.rt_ic_ore), img(com.puresudoku.R.drawable.rt_ic_grain), img(com.puresudoku.R.drawable.rt_ic_beasts), img(com.puresudoku.R.drawable.rt_ic_mana)),
        img(com.puresudoku.R.drawable.rt_wall_neutral), img(com.puresudoku.R.drawable.rt_road_neutral),
    )
}

/** Painted wall segment tinted to show ownership; callers also draw their owner marker. */
internal fun DrawScope.strategyWall(art: RealmArt, a: Offset, b: Offset, u: Float, owner: Color) {
    val d = b - a
    val len = d.getDistance()
    if (len < 1f) return
    val mid = (a + b) / 2f
    val height = u * 0.24f
    rotate(Math.toDegrees(kotlin.math.atan2(d.y.toDouble(), d.x.toDouble())).toFloat(), mid) {
        drawImage(art.stoneWall, dstOffset = IntOffset((mid.x - len / 2).toInt(), (mid.y - height / 2).toInt()),
            dstSize = IntSize(maxOf(1, len.toInt()), maxOf(1, height.toInt())),
            colorFilter = ColorFilter.tint(owner.copy(alpha = 0.65f), BlendMode.SrcAtop), filterQuality = FilterQuality.Medium)
    }
}

/** Painted road connecting land tiles. */
internal fun DrawScope.strategyRoad(art: RealmArt, a: Offset, b: Offset, u: Float) {
    val d = b - a; val len = d.getDistance()
    if (len < 1f) return
    val mid = (a + b) / 2f; val height = u * 0.2f
    rotate(Math.toDegrees(kotlin.math.atan2(d.y.toDouble(), d.x.toDouble())).toFloat(), mid) {
        drawImage(art.stoneRoad, dstOffset = IntOffset((mid.x - len / 2).toInt(), (mid.y - height / 2).toInt()),
            dstSize = IntSize(maxOf(1, len.toInt()), maxOf(1, height.toInt())), filterQuality = FilterQuality.Medium)
    }
}

/** A navy round button with a gold ring, matching the painted info and pause buttons. */
@Composable
internal fun RoundArtButton(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier.size(42.dp).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF2D4466), Color(0xFF0F1D33))))
            .border(3.dp, Brush.linearGradient(listOf(Color(0xFFF6E3A1), Color(0xFFB8872E))), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Eiseria's slim themed top bar: back, version and level, info, pause. */
@Composable
internal fun RealmHeader(session: Session, actions: com.puresudoku.ui.games.HeaderActions, version: String) {
        Box(Modifier.fillMaxWidth().height(52.dp)) {
            RoundArtButton(Modifier.align(Alignment.CenterStart), onClick = actions.back) {
                RIcon(20.dp) { c, sz ->
                    drawLine(PARCH_TOP, c + Offset(sz * 0.35f, 0f), c - Offset(sz * 0.35f, 0f), sz * 0.14f, StrokeCap.Round)
                    drawLine(PARCH_TOP, c - Offset(sz * 0.35f, 0f), c + Offset(-sz * 0.05f, -sz * 0.3f), sz * 0.14f, StrokeCap.Round)
                    drawLine(PARCH_TOP, c - Offset(sz * 0.35f, 0f), c + Offset(-sz * 0.05f, sz * 0.3f), sz * 0.14f, StrokeCap.Round)
                }
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.parchment(RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(session.difficulty.accent()))
                    Spacer(Modifier.width(5.dp))
                    Text((if (session.training) "Training · " else "") + version + session.difficulty.label, fontFamily = SERIF, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = INK)
                }
            }
            Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(42.dp).clip(CircleShape).clickable(onClick = actions.info)) { ArtImg(com.puresudoku.R.drawable.rt_btn_info, 42.dp) }
                Box(Modifier.size(42.dp).clip(CircleShape).alpha(if (actions.canPause) 1f else 0.5f).clickable(enabled = actions.canPause, onClick = actions.pause)) {
                    ArtImg(com.puresudoku.R.drawable.rt_btn_pause, 42.dp)
                }
            }
        }
    }
