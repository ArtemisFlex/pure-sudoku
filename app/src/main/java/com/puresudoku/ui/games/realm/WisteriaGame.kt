package com.puresudoku.ui.games.realm

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
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
import com.puresudoku.core.board.WAct
import com.puresudoku.core.board.Wisteria
import com.puresudoku.core.board.WisteriaAi
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

// =====================================================================  session

internal val ASPELL_ICONS = listOf(RI.KNIGHT, RI.RELIC, RI.LEY, RI.MOON, RI.FIRE, RI.EARTH)

class WisteriaSession(difficulty: Difficulty, val g: Wisteria) : DuelSession(if (g.ashfall) GameKind.ASHFALL else GameKind.WISTERIA, difficulty) {
    enum class Mode { NONE, STRIKE, MARCH, WALL, RALLY, SIEGE, QUENCH, RIFT }

    var selected by mutableIntStateOf(-1)
    var mode by mutableStateOf(Mode.NONE)
    /** The strike or march being set up is the Black Dragon Knight's. */
    var withBlack by mutableStateOf(false); private set
    /** The march being planned, tile by tile (the last tile is the target). */
    var plan by mutableStateOf<List<Int>>(emptyList()); private set
    private val planEnd get() = plan.lastOrNull() ?: selected
    /** Board code of the rival's last action: tile, or 1000 + edge. */
    var lastAi by mutableIntStateOf(-1); private set
    var events by mutableStateOf<List<String>>(emptyList()); private set

    private val lvl get() = WisteriaAi.level(aiDifficulty)

    override val middleStat get() = MiddleStat.Counter("Points", "${g.vp(0)}/${g.target}")
    override val progress: Float get() = (g.vp(0) / g.target.toFloat()).coerceAtMost(0.95f)
    private val realmName get() = if (g.ashfall) "Ashfall" else "Wisteria"
    override val wonTitle get() = "$realmName is yours!"
    override val lostTitle get() = "Your rival rules $realmName"
    override val lostMessage get() = when (g.endReason) {
        1 -> "You lost your last land."
        2 -> "The island could grow no more, and the rival led with ${g.vp(1)} points."
        3 -> "The game ran out of turns with the rival ahead."
        else -> "The rival reached ${g.vp(1)} points."
    } + " This game's save slot has been cleared."

    val myMove get() = outcome == Outcome.PLAYING && !thinking && !heraldBusy && g.actor == 0 && g.phase != Wisteria.OVER

    private fun flush() { if (g.log.isNotEmpty()) { events = (events + g.log).takeLast(4); g.log.clear() } }

    private fun act(a: WAct): Boolean {
        if (!g.legal(a)) { nudge("You can't do that right now"); return false }
        coachTip = null; coachFrom = -1
        g.apply(a, rng)
        val logs = g.log.toList(); flush(); bump(); changed()
        if (g.ashfall && logs.any { it.startsWith("The volcano erupts") }) eruptionCard(logs)
        if (g.phase == Wisteria.OVER) { whenQuiet { finishGame() }; return true }
        if (g.actor == 1) aiStep()
        return true
    }

    /** The eruption's news, as one card. */
    private fun eruptionCard(logs: List<String>) {
        val from = logs.indexOfFirst { it.startsWith("The volcano erupts") }
        val lines = logs.drop(from + 1).filter { "lava" in it.lowercase() || "wall" in it || "island" in it }
        announce("The volcano erupts!", lines.take(6).joinToString("\n").ifEmpty { "The lava runs into empty land" }, 2600, true, RI.FIRE)
    }

    /** Targets that light up for the current mode. */
    fun targets(): List<Int> = when {
        !myMove -> emptyList()
        g.phase == Wisteria.SETUP -> g.settleSpots(0)
        g.phase != Wisteria.MAIN -> emptyList()
        mode == Mode.STRIKE -> if (withBlack) g.blackStrikeTargets(0, selected) else g.strikeTargets(0, selected)
        mode == Mode.MARCH -> (g.map.hexNbrs[planEnd].filter { withBlack || g.passable(it) } +
            (if (withBlack) g.blackMarchTargets(0, selected) else g.marchTargets(0, selected))).distinct()
        mode == Mode.WALL -> g.wallSpots(0).filter { it.first == selected }.map { it.second }
        mode == Mode.RALLY -> (0 until g.H).filter { g.owner[it] == 0 && g.knights[it] < Wisteria.CAPACITY[g.level[it]] }
        mode == Mode.SIEGE -> emptyList()
        mode == Mode.QUENCH -> (0 until g.H).filter { g.owner[it] == 0 && !g.quench[it] }
        mode == Mode.RIFT -> g.forecast.flatMap { it.toList() }.filter { g.kind[it] != Wisteria.SEA }.distinct()
        else -> g.claimable(0)
    }

    fun tapTile(h: Int) {
        if (!myMove) return
        when {
            g.phase == Wisteria.SETUP -> act(WAct.Settle(h))
            g.phase != Wisteria.MAIN -> {}
            mode == Mode.STRIKE -> if (h in targets()) { if (act(WAct.Strike(selected, h, withBlack))) mode = Mode.NONE } else mode = Mode.NONE
            mode == Mode.MARCH -> when {
                h == planEnd && plan.isNotEmpty() -> sendMarch()
                h in g.map.hexNbrs[planEnd] && (withBlack || g.passable(h)) -> plan = plan + h
                h in (if (withBlack) g.blackMarchTargets(0, selected) else g.marchTargets(0, selected)) -> g.path(planEnd, h, withBlack)?.let { plan = plan + it }
                else -> nudge("Plot the march one touching tile at a time")
            }
            mode == Mode.WALL -> if (h in targets()) { act(WAct.Wall(selected, h)); if (g.freeWalls == 0 && !g.can(0, Wisteria.COST_WALL)) mode = Mode.NONE } else mode = Mode.NONE
            mode == Mode.RALLY -> if (h in targets()) { if (act(WAct.Spell(Wisteria.RALLY, h))) mode = Mode.NONE } else mode = Mode.NONE
            mode == Mode.QUENCH -> { if (h in targets()) act(WAct.Spell(Wisteria.QUENCH, h)); mode = Mode.NONE }
            mode == Mode.RIFT -> { val f = g.flowAt(h); if (f >= 0) act(WAct.Spell(Wisteria.RIFT, f)); mode = Mode.NONE }
            else -> selected = if (selected == h) -1 else h
        }
        bump()
    }

    fun tapEdge(e: Int) { if (myMove && mode == Mode.SIEGE && e in g.siegeTargets(0)) { if (act(WAct.Spell(Wisteria.SIEGE, e))) mode = Mode.NONE } }

    fun claim(h: Int) { if (myMove) act(WAct.Claim(h)) }
    fun skip() { if (myMove) act(WAct.Skip) }
    fun upgrade(h: Int) { if (myMove) act(WAct.Upgrade(h)) }
    fun hire(h: Int) { if (myMove) act(WAct.Hire(h)) }
    fun trade(give: Int, get: Int) { if (myMove) act(WAct.Trade(give, get)) }
    fun buySpell() { if (myMove) act(WAct.BuySpell) }
    fun endTurn() { if (myMove && g.phase == Wisteria.MAIN) { mode = Mode.NONE; moves++; act(WAct.End) } }
    fun toggleMode(m: Mode, black: Boolean = false) {
        if (!myMove) return
        mode = if (mode == m && withBlack == black) Mode.NONE else m
        withBlack = black; plan = emptyList(); bump()
    }

    val planValid get() = g.validRoute(0, selected, plan, withBlack)
    fun undoStep() { plan = plan.dropLast(1); bump() }
    fun sendMarch() {
        if (!planValid) { nudge("End the march on a rival tile or free land"); return }
        if (act(WAct.March(selected, plan, withBlack))) { mode = Mode.NONE; plan = emptyList() }
    }
    fun cast(kind: Int, a: Int = -1, b: Int = -1) {
        if (!myMove || !g.canPlay(0, kind)) { nudge("You can't cast that now"); return }
        when {
            g.ashfall && kind == Wisteria.QUENCH -> { mode = Mode.QUENCH; bump(); return }
            g.ashfall && kind == Wisteria.RIFT -> { mode = Mode.RIFT; bump(); return }
        }
        when (kind) {
            Wisteria.RALLY -> { mode = Mode.RALLY; bump() }
            Wisteria.SIEGE -> { mode = Mode.SIEGE; bump() }
            Wisteria.STONE -> { act(WAct.Spell(kind)); if (selected >= 0) mode = Mode.WALL }
            else -> act(WAct.Spell(kind, a, b))
        }
    }

    private fun code(a: WAct): Int = when (a) {
        is WAct.Settle -> a.h; is WAct.Claim -> a.h; is WAct.Upgrade -> a.h; is WAct.Hire -> a.h
        is WAct.Strike -> a.to; is WAct.March -> a.route.last(); is WAct.Wall -> 1000 + g.side(a.a, a.b)
        is WAct.Spell -> if (a.kind == Wisteria.SIEGE) 1000 + a.a else if (a.kind == Wisteria.RALLY || (g.ashfall && a.kind == Wisteria.QUENCH)) a.a
            else if (g.ashfall && a.kind == Wisteria.RIFT) g.forecast.getOrNull(a.a)?.firstOrNull() ?: -1 else -1
        else -> -1
    }

    private fun card(a: WAct, logs: List<String>) {
        val detail = logs.joinToString("\n")
        val (icon, title) = when (a) {
            is WAct.Settle -> RI.VILLAGE to "Rival settles"
            is WAct.Claim -> RI.VILLAGE to "Rival claims new land"
            is WAct.Upgrade -> RI.TOWER to "Rival raises a ${Wisteria.LEVEL_NAME[g.level[a.h]]}"
            is WAct.Hire -> RI.KNIGHT to "Rival hires a knight"
            is WAct.Strike -> if (a.black) RI.BLACK_KNIGHT to "The Black Dragon Knight strikes!" else RI.KNIGHT to "Rival's knight strikes!"
            is WAct.March -> if (a.black) RI.BLACK_KNIGHT to "The Black Dragon Knight rides out" else RI.KNIGHT to "Rival's army marches"
            is WAct.Wall -> RI.SHIELD to "Rival raises a wall"
            is WAct.Trade -> RI.TRADE to "Rival trades 4 ${Realm.RES[a.give]} for 1 ${Realm.RES[a.get]}"
            WAct.BuySpell -> RI.SCROLL to "Rival learns a spell"
            is WAct.Spell -> RI.BOOK to "Rival casts ${g.spellNames[a.kind]}"
            WAct.Skip -> RI.END to "Rival leaves the land empty"
            WAct.End -> RI.END to "Rival ends its turn"
        }
        announce(title, detail, if (a is WAct.Strike || a == WAct.End) 2000 else 1600, false, icon)
    }

    private fun aiStep() {
        if (outcome != Outcome.PLAYING || g.phase == Wisteria.OVER) return
        if (g.actor != 1) { bump(); return }
        val snap = g.copy(); val l = lvl; val seed = rng.nextLong()
        computer(minMs = 520, think = { WisteriaAi.next(snap, l, Random(seed)) }) { a0 ->
            if (g.actor != 1) return@computer
            var a = a0
            if (!g.legal(a)) a = if (g.phase == Wisteria.CHOICE) WAct.Skip else WAct.End
            if (a == WAct.End) moves++
            code(a).let { if (it >= 0) lastAi = it }
            g.apply(a, rng)
            val logs = g.log.toList(); flush(); changed()
            card(a, logs)
            if (g.ashfall && logs.any { it.startsWith("The volcano erupts") }) eruptionCard(logs)
            if (g.phase == Wisteria.OVER) { whenQuiet { finishGame() }; return@computer }
            if (g.actor == 0 && g.phase == Wisteria.MAIN && a == WAct.End) announce("Your turn", "Your lands have paid out", 1200, true, RI.CROWN)
            whenQuiet { aiStep() }
        }
    }

    private fun finishGame() {
        when (g.winner) {
            0 -> win(listOf(BonusLine("Points", g.vp(0) * rules.basePoints * 2), BonusLine("Lands held", g.tiles(0) * rules.basePoints)))
            2 -> draw()
            else -> lose()
        }
    }

    val prompt: String get() = when {
        outcome != Outcome.PLAYING -> ""
        heraldBusy -> "Watch the rival's moves · tap a card to skip"
        thinking || g.actor == 1 -> "The rival is taking its turn…"
        g.phase == Wisteria.SETUP -> "Settle your first land: tap a glowing tile"
        g.phase == Wisteria.CHOICE -> "Claim the ruined land?"
        mode == Mode.STRIKE -> if (withBlack) "Black Dragon Knight: tap a touching rival tile to strike" else "Tap a touching rival tile to strike"
        mode == Mode.MARCH -> if (plan.isEmpty()) (if (withBlack) "Black Dragon Knight: plot his flight, lakes included, or tap the target" else "Plot the march: tap touching tiles in order, or tap the target for the shortest way")
            else "${plan.size} turn${if (plan.size > 1) "s" else ""} to march · tap the last tile again or Send"
        mode == Mode.WALL -> if (g.freeWalls > 0) "Stonewright: tap ${g.freeWalls} neighbouring tile${if (g.freeWalls > 1) "s" else ""} to wall that side" else "Tap a neighbouring tile to wall that side"
        mode == Mode.RALLY -> "Tap one of your tiles for the free knight"
        mode == Mode.SIEGE -> "Tap a rival wall to break it"
        mode == Mode.QUENCH -> "Tap one of your tiles to quench it"
        mode == Mode.RIFT -> "Tap a tile on a lava flow to shift that flow"
        selected >= 0 -> "Choose an action for this tile"
        else -> "Tap a tile, claim glowing land, or end your turn"
    }

    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (g.actor != 0 || g.phase == Wisteria.OVER) return null
        val snap = g.copy()
        return {
            val a = WisteriaAi.next(snap, WisteriaAi.level(Difficulty.EXTREME), Random(7))
            val text = when (a) {
                is WAct.Settle -> "Settle the ringed tile: rich, with room to grow."
                is WAct.Claim -> "Claim the ringed tile."
                is WAct.Upgrade -> "Upgrade the ringed tile: more points, and it takes more hits."
                is WAct.Hire -> "Hire a knight on the ringed tile."
                is WAct.Strike -> if (a.black) "Send the Black Dragon Knight against the ringed tile." else "Strike the ringed village with a knight from your touching village."
                is WAct.March -> if (a.black) "Send the Black Dragon Knight flying to the ringed tile." else "Send your garrison marching on the ringed tile."
                is WAct.Wall -> "Wall the side facing the ringed tile."
                is WAct.Trade -> "Trade 4 ${Realm.RES[a.give]} for 1 ${Realm.RES[a.get]}."
                WAct.BuySpell -> "Buy a spell."
                is WAct.Spell -> "Cast ${snap.spellNames[a.kind]}."
                WAct.Skip -> "Leave it: you can't afford to claim it."
                WAct.End -> "Nothing worth doing: end your turn."
            }
            val c = when (a) { is WAct.Wall -> a.b; else -> code(a) }
            Triple(text, c, -1)
        }
    }

    override fun resume() {
        if (outcome != Outcome.PLAYING) return
        if (g.phase == Wisteria.OVER) { finishGame(); return }
        if (g.actor == 1) aiStep()
    }

    override fun saveState(): JSONObject = JSONObject().put("s", JSONArray().also { a -> g.state().forEach { a.put(it) } }).put("moves", moves)

    companion object {
        fun restore(o: JSONObject): WisteriaSession {
            val st = o.getJSONObject("state").getJSONArray("s")
            val g = Wisteria.fromState(IntArray(st.length()) { st.getInt(it) })
            return WisteriaSession(Difficulty.valueOf(o.getString("difficulty")), g).also { it.moves = o.getJSONObject("state").optInt("moves"); it.restoreCommon(o) }
        }
    }
}

// =====================================================================  definition

object WisteriaDef : GameDef {
    override val kind = GameKind.WISTERIA
    override val fullBleed: Boolean get() = true
    override val hasHeader: Boolean get() = true
    @Composable override fun Header(session: Session, actions: com.puresudoku.ui.games.HeaderActions) = RealmHeader(session, actions, "Wisteria · ")

    override fun generate(d: Difficulty): Session = WisteriaSession(d, Wisteria.newGame(d, Random(System.nanoTime())))
    override fun restore(o: JSONObject): Session = WisteriaSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Claim land next to yours, raise towers and castles, and conquer your rival's tiles with knights. The dragon strikes the leader every few turns; slay it for the Black Dragon Knight."
    override fun background(session: Session) = com.puresudoku.R.drawable.bg_realm_ocean
    override fun levelInfo(d: Difficulty): String {
        val m = RealmMap.forLevel(d.ordinal)
        val g = Wisteria.newGame(d, Random(1))
        return "First to ${g.target} points · ${m.HEXES} tiles" + (if (d.ordinal >= 1) " · fog" else "") + " · dragon every ${g.dragonEvery} turns"
    }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A timid rival on a small, open island."
        Difficulty.MEDIUM -> "Expands steadily; the fog rolls in."
        Difficulty.HARD -> "Walls its borders and strikes your villages."
        Difficulty.EXPERT -> "Marches armies across the island."
        Difficulty.MASTER -> "Relentless, on a big island."
        Difficulty.EXTREME -> "Its best play, on the biggest island."
    }
    @Composable override fun Play(session: Session) = WisteriaUi(session as WisteriaSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override fun lesson(): Pair<Session, List<LessonStep>> {
        val s = WisteriaSession(Difficulty.EASY, Wisteria.newGame(Difficulty.EASY, Random(777)))
        return s to listOf(
            LessonStep("Wisteria is a war for land. Every tile you hold pays out every turn: most give 1 each of three resources, some pay 2 of two, and the richest pay 3 of one."),
            LessonStep("Pick your first land: tap a glowing tile. Rich tiles with lots of free land around them are best.") { (it as WisteriaSession).g.tiles(0) >= 1 },
            LessonStep("Claim more land touching yours: tap a glowing tile, then Claim. The price grows with your territory, and rich tiles cost extra.") { (it as WisteriaSession).g.tiles(0) >= 2 || it.outcome != Outcome.PLAYING },
            LessonStep("Villages are worth 1 point, towers 2, castles 3. They also have that much health against the dragon, and towers and castles survive a knight by dropping a level."),
            LessonStep("Knights: a village's knight can strike a touching rival tile at once. A tower's 2 knights or a castle's 3 march one tile per turn to any target, even far-off land to claim."),
            LessonStep("Walls guard one side of your tile: they stop a whole attack from that side, then break. They only keep out knights: the dragon smashes them."),
            LessonStep("The dragon sleeps in the middle. Every few turns (5 on Easy, down to 3 on Extreme) it flies straight to the leader's grandest tile, ringed in red, and takes 1 health. Damage never heals: at 0 the land is lost."),
            LessonStep("The dragon has 5 health. Only knights can hurt it, and each one dies wounding it. Whoever lands the last blow wins the Dragon Scale (+1 point) and the Black Dragon Knight, who never falls and flies over lakes. Good luck!"),
        )
    }

    override val tutorial: List<TutorialStep> get() = listOf(
        TutorialStep("Claim the land", "Settle a tile, then claim the land around it. Every tile you hold pays out every turn.") { active ->
            val s = remember { WisteriaSession(Difficulty.EASY, Wisteria.newGame(Difficulty.EASY, Random(5))) }
            DemoStage(active, script = { wait(2400) }) { t -> WisteriaUi(s, t) }
        },
        TutorialStep("Conquer", "Raise towers and castles, hire knights to strike or march, and wall your borders. First to the points goal wins.") { active ->
            val s = remember { WisteriaSession(Difficulty.EASY, Wisteria.newGame(Difficulty.EASY, Random(5))) }
            DemoStage(active, script = { wait(2400) }) { t -> WisteriaUi(s, t) }
        },
    )
}

object AshfallDef : GameDef {
    override val kind = GameKind.ASHFALL
    override val fullBleed: Boolean get() = true
    override val hasHeader: Boolean get() = true
    @Composable override fun Header(session: Session, actions: com.puresudoku.ui.games.HeaderActions) = RealmHeader(session, actions, "Ashfall · ")

    override fun generate(d: Difficulty): Session = WisteriaSession(d, Wisteria.newAshfall(d, Random(System.nanoTime())))
    override fun restore(o: JSONObject): Session = WisteriaSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Wisteria on a living island. The volcano erupts every 4 turns and buries what lies in its glowing lava paths. Ash ripens into rich land, and lava that reaches the sea builds new land."
    override fun background(session: Session) = com.puresudoku.R.drawable.bg_realm_ocean
    override fun levelInfo(d: Difficulty): String {
        val g = Wisteria.newAshfall(d, Random(1))
        return "First to ${g.target} points · ${RealmMap.forLevel(d.ordinal).HEXES} tiles · up to ${Wisteria.MAX_FLOWS[d.ordinal]} lava flows"
    }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A timid rival and a sleepy volcano."
        Difficulty.MEDIUM -> "Claims the ash as it cools."
        Difficulty.HARD -> "Walls the lava toward you."
        Difficulty.EXPERT -> "Marches armies between eruptions."
        Difficulty.MASTER -> "Relentless, with five lava flows."
        Difficulty.EXTREME -> "Its best play, on the biggest island."
    }
    @Composable override fun Play(session: Session) = WisteriaUi(session as WisteriaSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    override fun lesson(): Pair<Session, List<LessonStep>> {
        val s = WisteriaSession(Difficulty.EASY, Wisteria.newAshfall(Difficulty.EASY, Random(777)))
        return s to listOf(
            LessonStep("Ashfall plays like Wisteria: every tile you hold pays out every turn. Claim land, raise towers and castles, and send knights against your rival."),
            LessonStep("Pick your first land: tap a glowing tile, away from the volcano and its glowing lava paths.") { (it as WisteriaSession).g.tiles(0) >= 1 },
            LessonStep("The volcano erupts every 4 turns; its number counts down. The glowing paths are the next eruption's lava flows: everything on them is buried, buildings and all."),
            LessonStep("Buried land is molten, then cooling ash anyone can claim. Once ripe it's rich ash, the best land there is. Flows that reach the sea build new land."),
            LessonStep("The flows grow longer and more numerous as the volcano wakes. A wall turns lava aside; Quench saves a tile; Rift moves a flow."),
            LessonStep("The points goal is high, so the game is long. If the sea fills up and the island can't grow, the leader wins. Good luck!"),
        )
    }

    override val tutorial: List<TutorialStep> get() = listOf(
        TutorialStep("The living island", "The volcano erupts every few turns. Its glowing lava paths bury whatever lies on them.") { active ->
            val s = remember { WisteriaSession(Difficulty.EASY, Wisteria.newAshfall(Difficulty.EASY, Random(5))) }
            DemoStage(active, script = { wait(2400) }) { t -> WisteriaUi(s, t) }
        },
        TutorialStep("Ash and new land", "Ash ripens into the richest land, and lava that reaches the sea builds new land to claim.") { active ->
            val s = remember { WisteriaSession(Difficulty.EASY, Wisteria.newAshfall(Difficulty.EASY, Random(5))) }
            DemoStage(active, script = { wait(2400) }) { t -> WisteriaUi(s, t) }
        },
    )
}

// =====================================================================  board

internal class WisteriaArt(val water: ImageBitmap, val knight: ImageBitmap, val black: ImageBitmap)
internal class AshArt(val volcano: ImageBitmap, val crater: ImageBitmap, val lava: ImageBitmap, val cooling: ImageBitmap, val ash: ImageBitmap, val rich: ImageBitmap)

@Composable
private fun rememberWisteriaArt() = WisteriaArt(
    ImageBitmap.imageResource(com.puresudoku.R.drawable.rt_water),
    ImageBitmap.imageResource(com.puresudoku.R.drawable.rt_knight_piece),
    ImageBitmap.imageResource(com.puresudoku.R.drawable.rt_black_knight),
)

@Composable
private fun rememberAshArt() = AshArt(
    ImageBitmap.imageResource(com.puresudoku.R.drawable.rt_af_volcano),
    ImageBitmap.imageResource(com.puresudoku.R.drawable.rt_af_crater),
    ImageBitmap.imageResource(com.puresudoku.R.drawable.rt_af_lava),
    ImageBitmap.imageResource(com.puresudoku.R.drawable.rt_af_cooling),
    ImageBitmap.imageResource(com.puresudoku.R.drawable.rt_af_ash),
    ImageBitmap.imageResource(com.puresudoku.R.drawable.rt_af_ashrich),
)

/** The board's geometry: Ashfall fits the island art to the island, leaving its ring of sea beyond it. */
private fun wGeo(g: Wisteria, w: Float, h: Float, zoom: Float, pan: Offset, top: Float, bottom: Float) =
    if (g.ashfall) RGeo(g.map, w, h, zoom, pan, top, bottom, RealmMap.forLevel(g.diff).extent, 0.92f)
    else RGeo(g.map, w, h, zoom, pan, top, bottom)

private fun DrawScope.castle(p: Offset, u: Float, color: Color) {
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

private fun DrawScope.wTile(g: Wisteria, h: Int, geo: RGeo, art: RealmArt, wart: WisteriaArt, ash: AshArt) {
    val u = geo.unit; val c = geo.hex(h)
    val tw = (sqrt3 * u).toInt() + 2; val th = (2 * u).toInt() + 2
    if (g.kind[h] == Wisteria.SEA) return
    val img = when {
        !g.revealed[h] -> art.fog[h % art.fog.size]
        g.ashfall && g.kind[h] == Wisteria.LAIR -> if (g.eruptIn <= 2) ash.volcano else ash.crater
        g.kind[h] == Wisteria.LAVA -> if (g.heat[h] > 2) ash.lava else ash.cooling
        g.kind[h] == Wisteria.ASH -> if (g.heat[h] > 0) ash.ash else ash.rich
        g.kind[h] == Wisteria.WATER -> wart.water
        g.kind[h] != Wisteria.LAND -> art.byTerrain[5][0]
        else -> { val list = art.byTerrain[g.art[h]]; list[(0 until h).count { g.kind[it] == Wisteria.LAND && g.art[it] == g.art[h] } % list.size] }
    }
    drawImage(img, dstOffset = IntOffset((c.x - tw / 2f).toInt(), (c.y - th / 2f).toInt()), dstSize = IntSize(tw, th), filterQuality = FilterQuality.Medium)
    if (g.revealed[h] && g.kind[h] == Wisteria.BARREN) drawPath(hexPath(geo, h, 1f), Color(0xFF3B2A1A).copy(alpha = 0.35f))
}

@Composable
fun WisteriaBoard(s: WisteriaSession, targets: DemoTargets?, modifier: Modifier = Modifier, fitTop: Float = 0f, fitBottom: Float = 0f) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "glow")
    val art = rememberRealmArt(); val wart = rememberWisteriaArt(); val ash = rememberAshArt()
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
                val geo = wGeo(g, size.width.toFloat(), size.height.toFloat(), zoom, pan, fitTop, fitBottom)
                if (s.mode == WisteriaSession.Mode.SIEGE) {
                    nearest(o, g.siegeTargets(0), geo::edgeMid, geo.unit * 0.6f)?.let { s.tapEdge(it) }
                    return@detectTapGestures
                }
                nearest(o, (0 until g.H).filter { g.kind[it] != Wisteria.SEA }, geo::hex, geo.unit * 0.95f)?.let { s.tapTile(it) }
            }
        }) {
            val geo = wGeo(g, size.width, size.height, zoom, pan, fitTop, fitBottom); val u = geo.unit; val m = g.map
            run {
                val k = geo.art * 1254f / art.island.width; val o = geo.artPt(0f, 0f)
                drawImage(art.island, dstOffset = IntOffset(o.x.toInt(), o.y.toInt()), dstSize = IntSize((art.island.width * k).toInt(), (art.island.height * k).toInt()), filterQuality = FilterQuality.Medium)
            }
            for (h in 0 until g.H) wTile(g, h, geo, art, wart, ash)
            // owner tint and gold borders
            for (h in 0 until g.H) {
                if (g.kind[h] == Wisteria.SEA) continue
                val o = g.owner[h]
                if (o >= 0) drawPath(hexPath(geo, h, 0.99f), (if (o == 0) YOU else RIVAL).copy(alpha = 0.16f))
                val p = hexPath(geo, h, 1f)
                drawPath(p, Color(0xFF4A3510), style = Stroke(u * 0.08f))
                drawPath(p, Brush.linearGradient(listOf(Color(0xFFF6E3A1), Color(0xFFC9972F), Color(0xFFF2D27A)), start = geo.hex(h) - Offset(u, u), end = geo.hex(h) + Offset(u, u)), style = Stroke(u * 0.05f))
                if (o >= 0) drawPath(hexPath(geo, h, 0.9f), if (o == 0) YOU else RIVAL, style = Stroke(u * 0.07f))
            }
            // tile contents: yields, building, knights
            for (h in 0 until g.H) {
                if (!g.revealed[h]) continue
                val c = geo.hex(h)
                if (g.kind[h] == Wisteria.LAVA || (g.kind[h] == Wisteria.ASH && g.heat[h] > 0)) {
                    // molten or cooling: turns until it can be claimed, or until it pays
                    val b = c + Offset(0f, -u * 0.5f)
                    drawCircle(Color(0xFF2A0F0A).copy(alpha = 0.85f), u * 0.2f, b)
                    drawCircle(if (g.kind[h] == Wisteria.LAVA) Color(0xFFFF7043) else Color(0xFFB0A89A), u * 0.2f, b, style = Stroke(u * 0.03f))
                    glyph("${g.heat[h]}", b, u * 0.2f, Color.White)
                }
                if (g.kind[h] == Wisteria.LAND || (g.kind[h] == Wisteria.ASH && g.heat[h] == 0)) {
                    val ys = (0..4).filter { g.pays(h, it) > 0 }
                    val bw = u * 0.36f; val y0 = c.y - u * 0.55f
                    val totalW = ys.size * bw + (ys.size - 1) * u * 0.04f
                    val pill = androidx.compose.ui.geometry.Rect(c.x - totalW / 2 - u * 0.06f, y0 - bw / 2 - u * 0.03f, c.x + totalW / 2 + u * 0.06f, y0 + bw / 2 + u * 0.03f)
                    drawRoundRect(Color(0xFFF8EED6).copy(alpha = 0.92f), pill.topLeft, pill.size, androidx.compose.ui.geometry.CornerRadius(u * 0.2f))
                    drawRoundRect(Color(0xFFC9972F), pill.topLeft, pill.size, androidx.compose.ui.geometry.CornerRadius(u * 0.2f), style = Stroke(u * 0.025f))
                    ys.forEachIndexed { i, r ->
                        val cx = c.x - totalW / 2 + bw / 2 + i * (bw + u * 0.04f)
                        val ic = art.icons[r]; val iw = bw * 0.82f; val ih = iw * ic.height / ic.width
                        drawImage(ic, dstOffset = IntOffset((cx - iw / 2).toInt(), (y0 - ih / 2).toInt()), dstSize = IntSize(iw.toInt(), ih.toInt()), filterQuality = FilterQuality.Medium)
                        val n = g.pays(h, r)
                        if (n > 1) glyph("×$n", Offset(cx + bw * 0.42f, y0 + bw * 0.32f), u * 0.17f, Color(0xFF3A2A18))
                    }
                }
                val o = g.owner[h]
                if (o >= 0) {
                    val col = if (o == 0) YOU else RIVAL
                    when (g.level[h]) {
                        1 -> { val img = if (o == 0) art.houseYou else art.houseRival; val w = u * 0.6f; val hh = w * img.height / img.width
                            drawImage(img, dstOffset = IntOffset((c.x - w / 2).toInt(), (c.y - hh * 0.55f).toInt()), dstSize = IntSize(w.toInt(), hh.toInt()), filterQuality = FilterQuality.Medium) }
                        2 -> tower(c + Offset(0f, u * 0.05f), u * 1.2f, col)
                        3 -> castle(c + Offset(0f, u * 0.05f), u * 1.2f, col)
                    }
                    val k = g.knights[h]
                    if (k > 0) {
                        val kh = u * 0.48f; val kw = kh * wart.knight.width / wart.knight.height; val base = c.y + u * 0.78f
                        for (i in 0 until k) {
                            val x = c.x + (i - (k - 1) / 2f) * kw * 0.95f
                            drawImage(wart.knight, dstOffset = IntOffset((x - kw / 2).toInt(), (base - kh).toInt()), dstSize = IntSize(kw.toInt(), kh.toInt()), filterQuality = FilterQuality.Medium)
                        }
                    }
                }
            }
            // armies on the march
            fun route(start: Int, tiles: List<Int>, col: Color, numbers: Boolean) {
                var prev = geo.hex(start)
                tiles.forEachIndexed { i, t ->
                    val p = geo.hex(t)
                    drawLine(Color.Black.copy(alpha = 0.4f), prev, p, u * 0.11f, StrokeCap.Round)
                    drawLine(col, prev, p, u * 0.06f, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.14f, u * 0.1f)))
                    if (numbers) { drawCircle(col, u * 0.15f, p); glyph("${i + 1}", p, u * 0.17f, Color.White) }
                    prev = p
                }
                tiles.lastOrNull()?.let { drawCircle(col, u * 0.24f, geo.hex(it), style = Stroke(u * 0.06f)) }
            }
            if (s.mode == WisteriaSession.Mode.MARCH && s.selected >= 0) route(s.selected, s.plan, Color(0xFFFFD54F), true)
            for (a in g.armies) {
                val from = geo.hex(a.pos); val col = if (a.owner == 0) YOU else RIVAL
                route(a.pos, a.route, col.copy(alpha = 0.85f), false)
                val b = from + Offset(u * 0.38f, -u * 0.05f)
                drawCircle(Color.Black.copy(alpha = 0.35f), u * 0.27f, b + Offset(0f, u * 0.03f)); drawCircle(col, u * 0.27f, b)
                val img = if (a.black) wart.black else wart.knight
                val kh = u * (if (a.black) 0.62f else 0.46f); val kw = kh * img.width / img.height
                drawImage(img, dstOffset = IntOffset((b.x - kw / 2).toInt(), (b.y - kh * 0.62f).toInt()), dstSize = IntSize(kw.toInt(), kh.toInt()), filterQuality = FilterQuality.Medium)
                glyph("${a.size}", b + Offset(u * 0.2f, u * 0.17f), u * 0.18f, Color.White)
            }
            // dragon, with its health bar
            if (g.dragonAlive) {
                val c = geo.hex(g.dragon); val img = art.dragon; val hh = u * 1.4f; val w = hh * img.width / img.height
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFF2A1A).copy(alpha = 0.75f), Color(0xFFD50000).copy(alpha = 0.35f), Color.Transparent), center = c, radius = u * 0.9f), u * 0.9f, c)
                drawImage(img, dstOffset = IntOffset((c.x - w / 2).toInt(), (c.y - hh / 2).toInt()), dstSize = IntSize(w.toInt(), hh.toInt()), filterQuality = FilterQuality.Medium)
                val bw = u * 1.1f; val bh = u * 0.13f; val bt = Offset(c.x - bw / 2, c.y - u * 0.78f)
                drawRoundRect(Color(0xFF2A0606), bt - Offset(u * 0.03f, u * 0.03f), androidx.compose.ui.geometry.Size(bw + u * 0.06f, bh + u * 0.06f), androidx.compose.ui.geometry.CornerRadius(bh))
                drawRoundRect(Color(0xFFE53935), bt, androidx.compose.ui.geometry.Size(bw * g.dragonHp / Wisteria.DRAGON_HP, bh), androidx.compose.ui.geometry.CornerRadius(bh / 2))
                for (k in 1 until Wisteria.DRAGON_HP) drawLine(Color(0xFF2A0606), bt + Offset(bw * k / Wisteria.DRAGON_HP, 0f), bt + Offset(bw * k / Wisteria.DRAGON_HP, bh), u * 0.015f)
                val tb = c + Offset(u * 0.45f, u * 0.48f)
                drawCircle(Color(0xFF3A0A0A), u * 0.2f, tb); drawCircle(Color(0xFFFF7043), u * 0.2f, tb, style = Stroke(u * 0.03f))
                glyph("${g.dragonTimer}", tb, u * 0.2f, Color.White)
            }
            // the dragon's next target
            g.dragonPlan()?.let { (_, t) ->
                drawCircle(Color(0xFFFF3D00).copy(alpha = 0.35f + 0.5f * pulse), u * 0.62f, geo.hex(t), style = Stroke(u * 0.07f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.16f, u * 0.1f))))
            }
            // Ashfall: the coming eruption's lava flows, and quenched ground
            if (g.ashfall) {
                for (f in g.forecast) {
                    var prev = geo.hex(g.lair)
                    for (t in f) {
                        val p = geo.hex(t)
                        drawPath(hexPath(geo, t, 0.9f), Color(0xFFFF5722).copy(alpha = 0.12f + 0.18f * pulse))
                        drawLine(Color(0xFF3A0A00).copy(alpha = 0.6f), prev, p, u * 0.16f, StrokeCap.Round)
                        drawLine(Color(0xFFFFA726).copy(alpha = 0.55f + 0.4f * pulse), prev, p, u * 0.09f, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.18f, u * 0.1f)))
                        if (g.kind[t] == Wisteria.SEA) drawPath(hexPath(geo, t, 0.92f), Color(0xFFFFCC80).copy(alpha = 0.5f + 0.4f * pulse), style = Stroke(u * 0.05f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.14f, u * 0.1f))))
                        prev = p
                    }
                }
                for (h in 0 until g.H) if (g.quench[h]) drawPath(hexPath(geo, h, 0.82f), Color(0xFF4FC3F7), style = Stroke(u * 0.07f))
                // the volcano's countdown
                val vc = geo.hex(g.lair) + Offset(u * 0.45f, u * 0.48f)
                drawCircle(Color(0xFF3A0A0A), u * 0.22f, vc); drawCircle(Color(0xFFFF7043), u * 0.22f, vc, style = Stroke(u * 0.03f))
                glyph("${g.eruptIn}", vc, u * 0.22f, Color.White)
            }
            // damaged buildings: their health, which never heals
            for (h in 0 until g.H) if (g.owner[h] >= 0 && g.scar[h] > 0) {
                val c = geo.hex(h); val n = g.level[h]; val r = u * 0.085f; val gap = r * 2.6f
                for (k in 0 until n) {
                    val p = c + Offset((k - (n - 1) / 2f) * gap, -u * 0.3f)
                    drawCircle(Color(0xFF2A0606), r * 1.3f, p)
                    drawCircle(if (k < g.hp(h)) Color(0xFFE53935) else Color(0xFF5A4A4A), r, p)
                }
            }
            // highlights
            val warm = s.mode == WisteriaSession.Mode.STRIKE || s.mode == WisteriaSession.Mode.MARCH
            for (h in lit) drawPath(hexPath(geo, h, 0.84f), (if (warm) Color(0xFFFF7043) else Color.White).copy(alpha = pulse), style = Stroke(u * 0.07f))
            val la = s.lastAi
            if (la >= 0) {
                val p = if (la >= 1000) geo.edgeMid(la - 1000) else geo.hex(la)
                drawCircle(RIVAL.copy(alpha = 0.8f), u * 0.45f, p, style = Stroke(u * 0.05f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.1f, u * 0.07f))))
            }
            val cf = s.coachFrom
            if (cf >= 0) coachRings(listOf(if (cf >= 1000) geo.edgeMid(cf - 1000) else geo.hex(cf)), u * 0.5f)
            // walls sit above everything but the selected-tile highlight, so they always show
            for (e in 0 until m.EDGES) {
                val o = g.wall[e]; if (o < 0) continue
                val a = geo.vert(m.edgeA[e]); val b = geo.vert(m.edgeB[e]); val d = (b - a) * 0.08f
                drawLine(Color(0xFF2B2B2B), a + d, b - d, u * 0.2f, StrokeCap.Round)
                drawLine(Color(0xFFB8B2A6), a + d, b - d, u * 0.14f, StrokeCap.Round)
                drawLine(if (o == 0) YOU else RIVAL, a + d, b - d, u * 0.05f, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.08f, u * 0.06f)))
            }
            if (s.mode == WisteriaSession.Mode.SIEGE) for (e in g.siegeTargets(0)) drawCircle(Color(0xFFFF7043).copy(alpha = pulse), u * 0.22f, geo.edgeMid(e), style = Stroke(u * 0.06f))
            // selected tile, on top of everything
            if (s.selected >= 0) drawPath(hexPath(geo, s.selected, 0.94f), Color(0xFFFFD54F), style = Stroke(u * 0.09f))
        }
    }
}

// =====================================================================  panels

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WisteriaUi(s: WisteriaSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val full = targets == null
    var topH by remember { mutableIntStateOf(0) }
    var botH by remember { mutableIntStateOf(0) }
    var showTrade by remember { mutableStateOf(false) }
    var showSpells by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        WisteriaBoard(s, targets, Modifier.fillMaxSize(), topH.toFloat(), botH.toFloat())
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topH = it.height }
                .then(if (full) Modifier.statusBarsPadding().padding(top = 54.dp) else Modifier).padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerBanner("You", "${g.vp(0)}", YOU, g.turn == 0 && g.phase != Wisteria.SETUP,
                    (if (g.slayer == 0) "Dragon Scale · " else "") + "${g.tiles(0)} lands · ${g.spells[0].sum()} spells", Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RIconOf(RI.CROWN, 22.dp)
                    Text("${g.target}", fontFamily = SERIF, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(0f, 1f), 4f)))
                }
                PlayerBanner("Rival", "${g.vp(1, false)}" + if (g.spells[1].sum() > 0) "+" else "", RIVAL, g.turn == 1 && g.phase != Wisteria.SETUP,
                    (if (g.slayer == 1) "Dragon Scale · " else "") + "${g.tiles(1)} lands · ${g.total(1)} cards", Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.parchment(RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (g.ashfall) {
                    RIconOf(RI.FIRE, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    val n = g.forecast.size
                    InkText((if (g.eruptIn <= 1) "Eruption next turn" else "Eruption in ${g.eruptIn} turns") + " · $n flow${if (n == 1) "" else "s"} · ${g.growthLeft()} sea left", 12, bold = true)
                } else if (g.dragonAlive) {
                    RIconOf(RI.DRAGON, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    val lead = g.leader()
                    InkText("Dragon ${g.dragonHp}/${Wisteria.DRAGON_HP} · moves in ${g.dragonTimer}" + when (lead) { 0 -> " · hunting you"; 1 -> " · hunting the rival"; else -> "" }, 12, bold = true)
                } else {
                    RIconOf(RI.SCALE, 20.dp)
                    Spacer(Modifier.width(4.dp))
                    RIconOf(RI.BLACK_KNIGHT, 20.dp)
                    Spacer(Modifier.width(6.dp))
                    val away = g.armies.any { it.black }
                    InkText((if (g.slayer == 0) "You slew the dragon" else "The rival slew the dragon") +
                        when { away -> " · Black Knight riding"; g.turn == g.slayer && g.blackUsed -> " · Black Knight rests"; else -> " · Black Knight ready" }, 12, bold = true)
                }
            }
        }

        HeraldCard(s, Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { botH.toDp() } + 6.dp).padding(horizontal = 12.dp))

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { botH = it.height }
                .then(if (full) Modifier.navigationBarsPadding() else Modifier).padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).parchment(PANEL).padding(horizontal = 10.dp, vertical = 3.dp)) {
                InkText(s.events.lastOrNull() ?: if (g.ashfall) "The mountain rumbles…" else "The chronicle of Wisteria begins…", 12, color = INK_SOFT, lines = 2)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (r in 0..4) ResChip(r, g.res[0][r], Modifier.weight(1f))
            }
            Spacer(Modifier.height(5.dp))
            PromptRibbon(s.prompt, s.thinking || s.heraldBusy || (g.actor == 1 && s.outcome == Outcome.PLAYING))
            Spacer(Modifier.height(5.dp))
            when {
                s.myMove && g.phase == Wisteria.CHOICE -> Column(Modifier.padding(horizontal = 12.dp).parchment(PANEL).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    InkText("Your knights took ${g.tileName(g.pending)}. Claim it?", 14, bold = true)
                    Spacer(Modifier.height(4.dp)); CostIcons(g.claimCost(0, g.pending), 18.dp); Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WoodButton("Leave it", null) { s.skip() }
                        WoodButton("Claim", RI.VILLAGE) { s.claim(g.pending) }
                    }
                }
                s.myMove && g.phase == Wisteria.MAIN -> FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val h = s.selected
                    if (s.mode == WisteriaSession.Mode.MARCH) {
                        WoodButton("Cancel", null) { s.toggleMode(WisteriaSession.Mode.MARCH) }
                        WoodButton("Undo step", null, enabled = s.plan.isNotEmpty()) { s.undoStep() }
                        WoodButton(if (s.plan.isEmpty()) "Send" else "Send · ${s.plan.size} turn${if (s.plan.size > 1) "s" else ""}", RI.KNIGHT, enabled = s.planValid) { s.sendMarch() }
                        return@FlowRow
                    }
                    if (h >= 0 && g.owner[h] < 0 && h in g.claimable(0)) BuildButton("Claim", RI.VILLAGE, g.claimCost(0, h), false, g.can(0, g.claimCost(0, h))) { s.claim(h) }
                    if (h >= 0 && g.owner[h] == 0) {
                        if (g.level[h] < 3) BuildButton(if (g.level[h] == 1) "Tower" else "Castle", RI.TOWER, g.upgradeCost(h), false, g.canUpgrade(0, h)) { s.upgrade(h) }
                        BuildButton("Knight ${g.knights[h]}/${Wisteria.CAPACITY[g.level[h]]}", RI.KNIGHT, Wisteria.COST_KNIGHT, false, g.canHire(0, h)) { s.hire(h) }
                        if (g.level[h] == 1) BuildButton("Strike", RI.KNIGHT, null, s.mode == WisteriaSession.Mode.STRIKE && !s.withBlack, g.strikeTargets(0, h).isNotEmpty()) { s.toggleMode(WisteriaSession.Mode.STRIKE) }
                        else BuildButton("March", RI.KNIGHT, null, s.mode == WisteriaSession.Mode.MARCH && !s.withBlack, g.marchTargets(0, h).isNotEmpty()) { s.toggleMode(WisteriaSession.Mode.MARCH) }
                        if (g.slayer == 0) {
                            BuildButton("Dark Strike", RI.BLACK_KNIGHT, null, s.mode == WisteriaSession.Mode.STRIKE && s.withBlack, g.blackStrikeTargets(0, h).isNotEmpty()) { s.toggleMode(WisteriaSession.Mode.STRIKE, black = true) }
                            val esc = g.escort(h)
                            BuildButton(if (esc > 0) "Dark March +$esc" else "Dark March", RI.BLACK_KNIGHT, null, s.mode == WisteriaSession.Mode.MARCH && s.withBlack, g.blackMarchTargets(0, h).isNotEmpty()) { s.toggleMode(WisteriaSession.Mode.MARCH, black = true) }
                        }
                        BuildButton("Wall", RI.SHIELD, if (g.freeWalls > 0) null else Wisteria.COST_WALL, s.mode == WisteriaSession.Mode.WALL,
                            g.wallSpots(0).any { it.first == h } && (g.freeWalls > 0 || g.can(0, Wisteria.COST_WALL))) { s.toggleMode(WisteriaSession.Mode.WALL) }
                    }
                    BuildButton("Spell", RI.SCROLL, Wisteria.COST_SPELL, false, g.can(0, Wisteria.COST_SPELL) && g.deck.isNotEmpty()) { s.buySpell() }
                    BuildButton("Trade", RI.TRADE, null, false, true) { showTrade = true }
                    BuildButton("Spells ${g.spells[0].sum()}", RI.BOOK, null, false, g.spells[0].sum() > 0) { showSpells = true }
                    BuildButton("End turn", RI.END, null, false, g.freeWalls == 0) { s.endTurn() }
                }
                else -> Spacer(Modifier.height(44.dp))
            }
            Spacer(Modifier.height(if (full) 10.dp else 4.dp))
        }
    }
    if (showTrade) WTradeDialog(s) { showTrade = false }
    if (showSpells) {
        if (g.ashfall) SpellbookDialog(g.spellNames, g.spellText, ASPELL_ICONS, g.spells[0], g.fresh,
            if (g.spellPlayed) "You've already cast a spell this turn." else null, Wisteria.RELIC, Wisteria.HARVEST,
            { g.canPlay(0, it) }, { k, a, b -> s.cast(k, a, b) }) { showSpells = false }
        else WSpellsDialog(s) { showSpells = false }
    }
}

@Composable
private fun WTradeDialog(s: WisteriaSession, onDismiss: () -> Unit) {
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

@Composable
private fun WSpellsDialog(s: WisteriaSession, onDismiss: () -> Unit) {
    val g = s.g
    var harvest by remember { mutableStateOf<List<Int>>(emptyList()) }
    val icons = listOf(RI.KNIGHT, RI.RELIC, RI.SHIELD, RI.MOON, RI.FIRE)
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
                            RIconOf(icons[k], 30.dp); Spacer(Modifier.width(8.dp))
                            InkText("${Wisteria.SPELLS[k]} ×$n", 15, bold = true, modifier = Modifier.weight(1f))
                            if (k != Wisteria.RELIC && k != Wisteria.HARVEST) WoodButton("Cast", null, enabled = g.canPlay(0, k)) { s.cast(k); onDismiss() }
                        }
                        InkText(Wisteria.SPELL_TEXT[k], 12, color = INK_SOFT)
                        if (k == Wisteria.HARVEST && g.canPlay(0, k)) {
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (r in 0..4) {
                                    val cnt = harvest.count { it == r }
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(RES_COLOR[r].copy(alpha = 0.12f + 0.3f * cnt))
                                        .clickable { harvest = (harvest + r).takeLast(2) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        ResImg(r, 22.dp); InkText(if (cnt > 0) "×$cnt" else " ", 11, bold = true)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            WoodButton("Cast Harvest Moon", RI.MOON, enabled = harvest.size == 2) { s.cast(Wisteria.HARVEST, harvest[0], harvest[1]); onDismiss() }
                        }
                    }
                }
                if (g.spells[0].sum() == 0) InkText("No spells yet. Buy one with ore, grain and beasts.", 13)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = INK, fontFamily = SERIF, fontWeight = FontWeight.Bold) } },
    )
}
