package com.puresudoku.ui.games.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.board.Cratebarge
import com.puresudoku.core.board.CratebargeAi
import com.puresudoku.core.board.CrateProfile
import com.puresudoku.core.board.CrateSupply
import com.puresudoku.core.board.crateContracts
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.MiddleStat
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.tutorial.TutorialStep
import org.json.JSONObject
import kotlin.random.Random

// =====================================================================  session

class CratebargeSession(difficulty: Difficulty, val g: Cratebarge) : DuelSession(GameKind.CRATEBARGE, difficulty) {
    var profile by mutableStateOf(CrateProfile()); private set
    var contract by mutableIntStateOf(0); private set
    var issued = IntArray(CrateSupply.entries.size)
    var revealed = IntArray(g.cells)
    var decoy = false
    var selectedSupply by mutableStateOf<CrateSupply?>(null); private set
    var showShop by mutableStateOf(false)
    var introStep by mutableIntStateOf(0)
    var introAnswer by mutableIntStateOf(0)
    var introChoice by mutableIntStateOf(0)
    private var voyageSettled = false
    val contractInfo get() = crateContracts[contract]

    fun loadProfile(o: JSONObject?) { profile = CrateProfile.fromJson(o) }
    fun advanceIntro() { introStep++; if (introStep >= 7) { profile.introSeen = true; changed() }; bump(); changed() }
    fun chooseIntro(answer: Int) { if (introStep == 1) introAnswer = answer else introChoice = answer; advanceIntro() }
    fun selectContract(index: Int) {
        if (localTwoPlayer || g.phase != Cratebarge.PLACE || index !in 0..profile.unlocked || index !in crateContracts.indices) return
        contract = index; issued.fill(0)
        crateContracts[index].supplies.forEach { (s, count) -> issued[s.ordinal] = count }
        message = "${contractInfo.title}: ${contractInfo.description}"
        bump(); changed()
    }
    fun buySupply(s: CrateSupply) {
        if (profile.buy(s)) { message = "${s.title} added to your supplies."; profile = profile; bump(); changed() }
        else nudge("Not enough Dice")
    }
    fun supplyCount(s: CrateSupply) = issued[s.ordinal] + profile.stock[s.ordinal]
    private fun spendSupply(s: CrateSupply) {
        if (issued[s.ordinal] > 0) issued[s.ordinal]-- else profile.stock[s.ordinal]--
        selectedSupply = null; profile = profile
    }
    fun selectSupply(s: CrateSupply) {
        if (!myMove || localTwoPlayer || g.phase != Cratebarge.BATTLE || supplyCount(s) <= 0) return
        if (s == CrateSupply.DECOY) {
            if (decoy) { nudge("A decoy is already set"); return }
            if ((0 until g.cells).none { g.shots[1][it] == Cratebarge.NONE && g.shipAt(0, it) < 0 }) { nudge("No open water remains"); return }
            spendSupply(s); decoy = true; message = "Decoy set for the next enemy shot."; bump(); changed(); return
        }
        selectedSupply = if (selectedSupply == s) null else s
        message = when (selectedSupply) {
            CrateSupply.PLANKS -> "Tap a damaged section of your fleet."
            CrateSupply.BARREL -> "Tap the enemy grid to fire the barrel."
            CrateSupply.TELESCOPE -> "Tap the enemy grid to scout a tile."
            else -> "Choose a target."
        }
        bump(); changed()
    }
    fun ownGridTap(c: Int) {
        if (!myMove || selectedSupply != CrateSupply.PLANKS || g.phase != Cratebarge.BATTLE) return
        if (g.shots[1][c] != Cratebarge.HIT) { nudge("Choose a damaged boat section"); return }
        spendSupply(CrateSupply.PLANKS); g.shots[1][c] = Cratebarge.NONE
        message = "One boat section repaired."; bump(); changed()
    }
    private fun scout(c: Int): Boolean {
        if (g.shots[0][c] != Cratebarge.NONE || revealed[c] != 0) { nudge("That square is already known"); return false }
        spendSupply(CrateSupply.TELESCOPE)
        revealed[c] = if (g.shipAt(1, c) >= 0) 2 else 1
        message = if (revealed[c] == 2) "Telescope spotted a boat silhouette." else "Telescope found open water."
        bump(); changed(); return true
    }
    private fun fireBarrel(c: Int): Int {
        val n = g.size
        val options = listOfNotNull(c, (c-n).takeIf { c/n > 0 }, (c+n).takeIf { c/n < n-1 },
            (c-1).takeIf { c%n > 0 }, (c+1).takeIf { c%n < n-1 }).filter { g.shots[0][it] == Cratebarge.NONE }.take(3)
        if (options.isEmpty()) { nudge("Choose unknown enemy waters"); return -1 }
        spendSupply(CrateSupply.BARREL)
        var hits = 0
        for (cell in options) {
            if (g.phase != Cratebarge.BATTLE) break
            g.turn = 0
            if (g.fire(0, cell) != Cratebarge.R_MISS) hits++
        }
        return hits
    }
    fun settleVoyage(result: Outcome) {
        if (voyageSettled || localTwoPlayer || training) return
        voyageSettled = true
        if (result == Outcome.WON) {
            profile.dice += contractInfo.reward
            if (contract == profile.unlocked && contract < crateContracts.lastIndex) profile.unlocked++
        } else if (result == Outcome.LOST) profile.unlocked = 0
        profile = profile
    }

    /** Pass-the-phone: the next player's screen stays covered until they're ready. */
    var handoffPending by mutableStateOf(false)
    /** Two players on one device: the shot has landed; the shooter sees the result, then passes the phone. */
    var awaitingPass by mutableStateOf(false)
    /** The ship being placed, and whether it runs down the grid. */
    var picked by mutableIntStateOf(0)
    var vertical by mutableStateOf(false)

    val currentPlayer: Int get() = if (g.phase == Cratebarge.PLACE) g.placer else g.turn
    override val tableSeat: Int? get() = if (localTwoPlayer) currentPlayer + 1 else null
    /** Whose fleet and shots the screen shows: your own seat online, the player at the device otherwise. */
    val viewer: Int get() = if (online) onlineSeat - 1 else if (localTwoPlayer) (if (awaitingPass) g.lastBy.coerceAtLeast(0) else currentPlayer) else 0
    fun acceptHandoff() { handoffPending = false; bump(); changed() }
    override fun skipHandoff() { handoffPending = false; bump() }

    private val level get() = aiDifficulty.ordinal

    override val middleStat get() = MiddleStat.Counter("Shots", "${g.shotsBy(0)}")
    override val progress: Float get() = (g.fleet.indices.count { g.sunk(1, it) }.toFloat() / g.fleet.size).coerceAtMost(0.95f)
    override val wonTitle get() = if (localTwoPlayer) "Player 1 rules the waves!" else if (training) "Enemy fleet sunk!" else "Victory! ${contractInfo.reward} Dice earned"
    override val lostTitle get() = if (localTwoPlayer) "Player 2 rules the waves!" else "Your fleet is sunk"
    override val lostMessage get() = "The rival found every ship. This game's save slot has been cleared."

    val myMove: Boolean get() = outcome == Outcome.PLAYING && !thinking && g.phase != Cratebarge.OVER &&
        (localTwoPlayer || currentPlayer == 0) && (!localTwoPlayer || (!handoffPending && !awaitingPass)) && myOnlineTurn

    fun who(p: Int) = when { online -> if (p == onlineSeat - 1) "You" else "Your opponent"; localTwoPlayer -> "Player ${p + 1}"; p == 0 -> "You"; else -> "The rival" }

    init { if (g.phase == Cratebarge.PLACE) message = "Place your fleet: pick a ship, then tap the grid" }

    // ---------------------------------------------------------------- placing

    fun tapPlace(c: Int) {
        if (!myMove || g.phase != Cratebarge.PLACE) return
        val p = currentPlayer
        val here = g.shipAt(p, c)
        if (here >= 0 && (g.start[p][picked] >= 0 || here == picked)) {
            // pick a placed ship back up
            g.remove(p, here); picked = here; vertical = g.vert[p][here] == 1; bump(); return
        }
        if (g.start[p][picked] >= 0) picked = g.fleet.indices.firstOrNull { g.start[p][it] < 0 } ?: return
        if (!g.place(p, picked, c, vertical)) { nudge("It doesn't fit there"); return }
        g.fleet.indices.firstOrNull { g.start[p][it] < 0 }?.let { picked = it }
        bump(); changed()
    }

    fun pick(i: Int) { if (myMove) { val p = currentPlayer; if (g.start[p][i] >= 0) { vertical = g.vert[p][i] == 1; g.remove(p, i) }; picked = i; bump() } }
    fun rotate() { if (myMove) { vertical = !vertical; bump() } }
    fun scatter() { if (myMove && g.phase == Cratebarge.PLACE) { g.randomFleet(currentPlayer, rng); bump(); changed() } }
    fun clearFleet() { if (myMove && g.phase == Cratebarge.PLACE) { g.clear(currentPlayer); picked = 0; bump(); changed() } }

    fun ready() {
        if (!myMove || g.phase != Cratebarge.PLACE) return
        val p = currentPlayer
        if (!g.allPlaced(p)) { nudge("Place every ship first"); return }
        g.ready(p)
        if (!localTwoPlayer) {
            CratebargeAi.place(g, 1, level, rng); g.ready(1)
            message = "Fire at the enemy's waters"
            announce("The rival hides its fleet", "Tap a square of the enemy's waters to fire", 1400, true)
        } else {
            picked = 0; vertical = false
            message = if (g.phase == Cratebarge.PLACE) "Player 2, place your fleet" else "Player 1, fire at the enemy's waters"
            handoffPending = !online
        }
        bump(); changed()
    }

    // ---------------------------------------------------------------- battle

    fun fire(c: Int) {
        if (!myMove || g.phase != Cratebarge.BATTLE) return
        val p = currentPlayer
        if (!g.canFire(p, c)) { nudge("You've already fired there"); return }
        if (!localTwoPlayer && selectedSupply == CrateSupply.TELESCOPE) { scout(c); return }
        if (!localTwoPlayer && selectedSupply == CrateSupply.PLANKS) { nudge("Tap your fleet to repair"); return }
        val barrel = !localTwoPlayer && selectedSupply == CrateSupply.BARREL
        val r = if (barrel) fireBarrel(c) else g.fire(p, c)
        if (r < 0) return
        moves++
        message = if (barrel) "Bomb Barrel: $r hits!" else shotText(p, c, r)
        if (!localTwoPlayer && contract > 0) {
            val hit = if (barrel) r > 0 else r != Cratebarge.R_MISS
            val reactions = listOf(
                "" to "",
                "You found timber in the fog. Interesting." to "The fog keeps better secrets than you read.",
                "Ha! A real crack in the hull. Hit harder." to "That shot could not break foam.",
                "Recorded. Your shot has merit." to "Denied. The blockade stands.",
                "Quick hands. I will move quicker." to "You are firing at yesterday's lantern.",
                "The Crownless Fleet acknowledges the wound." to "The drowned court is unmoved."
            )
            message += "\n${contractInfo.enemy}: \"${if (hit) reactions[contract].first else reactions[contract].second}\""
        }
        if (!localTwoPlayer && g.phase == Cratebarge.BATTLE && g.shotsBy(0) >= contractInfo.limit) {
            g.phase = Cratebarge.OVER; g.winner = 1; message = "Out of shots. The enemy fleet escapes."
        }
        if (g.phase == Cratebarge.OVER) { bump(); changed(); finishGame(); return }
        if (localTwoPlayer) {
            if (!online) awaitingPass = true
            bump(); changed()
            return
        }
        bump(); changed()
        rivalTurn()
    }

    /** Two players on one device: done looking at the shot, hand the phone over. */
    fun pass() {
        if (!awaitingPass) return
        awaitingPass = false; handoffPending = true
        message = "${who(g.turn)}, fire at the enemy's waters"
        bump(); changed()
    }

    private fun shotText(p: Int, c: Int, r: Int): String {
        val at = g.coord(c)
        val who = who(p)
        return when {
            r >= Cratebarge.R_SUNK -> "$who fired at $at: sank the ${g.name(r - Cratebarge.R_SUNK)}!"
            r == Cratebarge.R_HIT -> "$who fired at $at: hit!"
            else -> "$who fired at $at: miss"
        }
    }

    private fun rivalTurn() {
        if (localTwoPlayer || outcome != Outcome.PLAYING || g.phase != Cratebarge.BATTLE || g.turn != 1) return
        val snap = g.copy(); val l = level; val seed = rng.nextLong()
        val forceMiss = decoy; decoy = false
        computer(minMs = 750, think = {
            val safe = (0 until snap.cells).filter { snap.shots[1][it] == Cratebarge.NONE && snap.shipAt(0, it) < 0 }
            if (forceMiss && safe.isNotEmpty()) safe.random(Random(seed)) else CratebargeAi.shot(snap, 1, l, Random(seed))
        }) { c ->
            val r = g.fire(1, c)
            message = shotText(1, c, r)
            bump(); changed()
            announce(when { r >= Cratebarge.R_SUNK -> "Your ${g.name(r - Cratebarge.R_SUNK)} is sunk!"; r == Cratebarge.R_HIT -> "The rival hits at ${g.coord(c)}!"; else -> "The rival misses at ${g.coord(c)}" },
                if (g.phase == Cratebarge.OVER) "" else "Your turn to fire", if (r == Cratebarge.R_MISS) 900 else 1400, false)
            if (g.phase == Cratebarge.OVER) whenQuiet { finishGame() }
        }
    }

    private fun finishGame() {
        when (g.winner) {
            0 -> {
                val acc = g.hitsBy(0) * 100 / g.shotsBy(0).coerceAtLeast(1)
                win(listOf(BonusLine("Accuracy $acc%", acc * rules.basePoints / 5), BonusLine("Ships afloat", g.shipsLeft(0) * rules.basePoints * 2)))
            }
            else -> lose()
        }
    }

    override fun coachWork(): (() -> Triple<String, Int, Int>?)? {
        if (localTwoPlayer || g.phase != Cratebarge.BATTLE || g.turn != 0) return null
        val snap = g.copy()
        return { val c = CratebargeAi.shot(snap, 0, 5, Random(7)); Triple("Try ${snap.coord(c)}: ships are most likely to be there.", c, -1) }
    }

    override fun resume() {
        if (outcome != Outcome.PLAYING) return
        if (g.phase == Cratebarge.OVER) { finishGame(); return }
        rivalTurn()
    }

    override fun saveState(): JSONObject = JSONObject().put("g", g.toJson()).put("moves", moves).put("localTwoPlayer", localTwoPlayer)
        .put("handoffPending", handoffPending).put("awaitingPass", awaitingPass).put("message", message)
        .put("contract", contract).put("issued", org.json.JSONArray().also { a -> issued.forEach { a.put(it) } })
        .put("revealed", org.json.JSONArray().also { a -> revealed.forEach { a.put(it) } })
        .put("decoy", decoy).put("introStep", introStep).put("introAnswer", introAnswer).put("introChoice", introChoice)

    companion object {
        fun restore(o: JSONObject): CratebargeSession {
            val st = o.getJSONObject("state")
            return CratebargeSession(Difficulty.valueOf(o.getString("difficulty")), Cratebarge.fromJson(st.getJSONObject("g"))).also { s ->
                s.moves = st.optInt("moves"); s.localTwoPlayer = st.optBoolean("localTwoPlayer", false)
                s.handoffPending = st.optBoolean("handoffPending", false); s.awaitingPass = st.optBoolean("awaitingPass", false)
                s.message = st.optString("message", "")
                s.contract = st.optInt("contract", 0).coerceIn(crateContracts.indices)
                st.optJSONArray("issued")?.let { a -> s.issued = IntArray(CrateSupply.entries.size) { a.optInt(it, 0).coerceAtLeast(0) } }
                st.optJSONArray("revealed")?.let { a -> s.revealed = IntArray(s.g.cells) { a.optInt(it, 0).coerceIn(0, 2) } }
                s.decoy = st.optBoolean("decoy"); s.introStep = st.optInt("introStep").coerceIn(0, 7)
                s.introAnswer = st.optInt("introAnswer"); s.introChoice = st.optInt("introChoice")
                s.restoreCommon(o)
            }
        }
    }
}

// =====================================================================  definition

object CratebargeDef : GameDef {
    override val kind = GameKind.CRATEBARGE
    override fun generate(d: Difficulty): Session = CratebargeSession(d, Cratebarge.forLevel(d.ordinal))
    override fun restore(o: JSONObject): Session = CratebargeSession.restore(o)
    override fun hint(d: Difficulty) = "Hide your fleet, then take turns firing at the rival's waters. Sink every ship to win."
    override fun levelInfo(d: Difficulty) = Cratebarge.forLevel(d.ordinal).let { "${it.size}×${it.size} sea · ${it.fleet.size} ships" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A rival who fires wildly."
        Difficulty.MEDIUM -> "Finishes off a ship once it finds one."
        Difficulty.HARD -> "Hunts in a pattern and follows a line of hits."
        Difficulty.EXPERT -> "Works out where your ships can still hide."
        Difficulty.MASTER -> "Never wastes a shot, and spreads its ships apart."
        Difficulty.EXTREME -> "A cold, calculating admiral."
    }
    override fun background(session: Session) = com.puresudoku.R.drawable.cratebarge_menu
    @Composable override fun Play(session: Session) = CratebargeUi(session as CratebargeSession)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    private fun demo(shots: Boolean): CratebargeSession {
        val g = Cratebarge.forLevel(1)
        g.randomFleet(0, Random(4)); g.ready(0); g.randomFleet(1, Random(9)); g.ready(1)
        if (shots) {
            val rnd = Random(2)
            repeat(9) { if (g.phase == Cratebarge.BATTLE) g.fire(g.turn, CratebargeAi.shot(g, g.turn, 4, rnd)) }
        }
        return CratebargeSession(Difficulty.MEDIUM, g)
    }

    override val tutorial: List<TutorialStep> get() = listOf(
        TutorialStep("Hide your fleet", "Place each ship on your grid: pick a ship, tap where it starts, and Rotate to turn it. Random scatters them for you.") { _ ->
            val s = remember { demo(false) }
            CratebargeGrid(s, owner = 0, mine = true, modifier = Modifier.fillMaxWidth().padding(24.dp))
        },
        TutorialStep("Fire!", "Take turns firing at a square of the enemy's waters. White rings are misses, fire is a hit. Hit every square of a ship to sink it.") { _ ->
            val s = remember { demo(true) }
            CratebargeGrid(s, owner = 1, mine = false, modifier = Modifier.fillMaxWidth().padding(24.dp))
        },
        TutorialStep("Sink them all", "Sink the whole enemy fleet before they sink yours. After a hit, try the squares around it to find which way the ship runs.") { _ ->
            val s = remember { demo(true) }
            CratebargeGrid(s, owner = 0, mine = true, modifier = Modifier.fillMaxWidth().padding(24.dp))
        },
    )
}

// =====================================================================  drawing

/** The Eiseria water painting, cropped from inside its hexagon frame. */
@Composable
private fun rememberWater(): ImageBitmap = ImageBitmap.imageResource(com.puresudoku.R.drawable.cratebarge_ocean)

private fun DrawScope.water(img: ImageBitmap, r: Rect) {
    drawImage(img, dstOffset = IntOffset(r.left.toInt(), r.top.toInt()),
        dstSize = IntSize(r.width.toInt().coerceAtLeast(1), r.height.toInt().coerceAtLeast(1)), filterQuality = FilterQuality.Medium)
}

/** A warship seen from above, [len] squares long, filling [r] (bow to the right, or downward when [down]). */
private fun DrawScope.ship(r: Rect, len: Int, down: Boolean, sunk: Boolean) {
    val cell = if (down) r.width else r.height
    val L = len * cell
    val c = r.center
    rotate(if (down) 90f else 0f, c) {
        val x0 = c.x - L / 2 + cell * 0.08f; val x1 = c.x + L / 2 - cell * 0.04f
        val hh = cell * 0.34f
        val hull = Path().apply {
            moveTo(x0 + hh, c.y - hh)
            lineTo(x1 - cell * 0.55f, c.y - hh)
            quadraticTo(x1 - cell * 0.1f, c.y - hh * 0.6f, x1, c.y)
            quadraticTo(x1 - cell * 0.1f, c.y + hh * 0.6f, x1 - cell * 0.55f, c.y + hh)
            lineTo(x0 + hh, c.y + hh)
            quadraticTo(x0, c.y + hh, x0, c.y)
            quadraticTo(x0, c.y - hh, x0 + hh, c.y - hh)
            close()
        }
        drawPath(hull, Color.Black.copy(alpha = 0.3f), style = Stroke(cell * 0.12f))
        drawPath(hull, Brush.verticalGradient(if (sunk) listOf(Color(0xFF6D4C41), Color(0xFF3E2723)) else listOf(Color(0xFFB0BEC5), Color(0xFF607D8B)), c.y - hh, c.y + hh))
        drawLine(Color.White.copy(alpha = if (sunk) 0.15f else 0.45f), Offset(x0 + hh, c.y), Offset(x1 - cell * 0.5f, c.y), cell * 0.05f)
        // a turret on each square but the bow
        for (k in 0 until len - 1) {
            val tc = Offset(c.x - L / 2 + cell * (k + 0.5f), c.y)
            drawCircle(if (sunk) Color(0xFF4E342E) else Color(0xFF455A64), cell * 0.17f, tc)
            drawLine(if (sunk) Color(0xFF4E342E) else Color(0xFF37474F), tc, tc + Offset(cell * 0.3f, 0f), cell * 0.06f)
        }
        drawPath(hull, Color(0xFF263238), style = Stroke(cell * 0.05f))
    }
}

private fun DrawScope.missMark(c: Offset, cell: Float) {
    drawCircle(Color.White.copy(alpha = 0.9f), cell * 0.24f, c, style = Stroke(cell * 0.07f))
    drawCircle(Color.White.copy(alpha = 0.9f), cell * 0.07f, c)
}

private fun DrawScope.hitMark(c: Offset, cell: Float) {
    val burst = Path().apply {
        for (k in 0 until 16) {
            val a = k * kotlin.math.PI / 8; val rr = if (k % 2 == 0) cell * 0.42f else cell * 0.2f
            val x = c.x + kotlin.math.cos(a).toFloat() * rr; val y = c.y + kotlin.math.sin(a).toFloat() * rr
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(burst, Color(0xFFE53935))
    drawCircle(Color(0xFFFFCA28), cell * 0.16f, c)
    drawPath(burst, Color(0xFF7F0000), style = Stroke(cell * 0.04f))
}

/**
 * One side's waters. [owner]'s ships show when [mine] (or once sunk); the marks are the other side's shots at them.
 * [onTap] gets the square tapped.
 */
@Composable
fun CratebargeGrid(s: CratebargeSession, owner: Int, mine: Boolean, modifier: Modifier = Modifier, onTap: ((Int) -> Unit)? = null) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val img = rememberWater()
    val boats = listOf(
        ImageBitmap.imageResource(com.puresudoku.R.drawable.cratebarge_flagbarge),
        ImageBitmap.imageResource(com.puresudoku.R.drawable.cratebarge_cargo_ark),
        ImageBitmap.imageResource(com.puresudoku.R.drawable.cratebarge_ferry),
        ImageBitmap.imageResource(com.puresudoku.R.drawable.cratebarge_skiff),
        ImageBitmap.imageResource(com.puresudoku.R.drawable.cratebarge_cutter),
        ImageBitmap.imageResource(com.puresudoku.R.drawable.cratebarge_dinghy),
    )
    val n = g.size
    Canvas(modifier.aspectRatio(1f).then(if (onTap != null) Modifier.pointerInput(s, owner) {
        detectTapGestures { o ->
            val side = size.width.toFloat(); val m = side / (n + 0.6f) * 0.6f; val cell = (side - m) / n
            val col = ((o.x - m) / cell).toInt(); val row = ((o.y - m) / cell).toInt()
            if (o.x >= m && o.y >= m && col in 0 until n && row in 0 until n) onTap(row * n + col)
        }
    } else Modifier)) {
        val side = size.width
        val m = side / (n + 0.6f) * 0.6f
        val cell = (side - m) / n
        val grid = Rect(m, m, side, side)
        drawRoundRect(Color(0xFF0D2A3A), Offset.Zero, Size(side, side), CornerRadius(cell * 0.3f))
        water(img, grid)
        for (k in 0..n) {
            drawLine(Color.White.copy(alpha = 0.28f), Offset(m + k * cell, m), Offset(m + k * cell, side), 1.2f)
            drawLine(Color.White.copy(alpha = 0.28f), Offset(m, m + k * cell), Offset(side, m + k * cell), 1.2f)
        }
        for (k in 0 until n) {
            glyph("${'A' + k}", Offset(m + (k + 0.5f) * cell, m * 0.5f), m * 0.62f, Color.White)
            glyph("${k + 1}", Offset(m * 0.5f, m + (k + 0.5f) * cell), m * 0.55f, Color.White)
        }
        // ships: your own always; the enemy's only once sunk
        for (i in g.fleet.indices) {
            val sq = g.shipCells(owner, i)
            if (sq.isEmpty()) continue
            val sunk = g.sunk(owner, i)
            if (!mine && !sunk) continue
            val a = sq.first(); val b = sq.last()
            val r = Rect(m + (a % n) * cell, m + (a / n) * cell, m + (b % n + 1) * cell, m + (b / n + 1) * cell)
            val down = g.vert[owner][i] == 1
            val targetWidth = if (down) r.height else r.width
            val targetHeight = if (down) r.width else r.height
            rotate(if (down) 90f else 0f, r.center) {
                drawImage(boats[i], dstOffset = IntOffset((r.center.x - targetWidth / 2).toInt(), (r.center.y - targetHeight / 2).toInt()),
                    dstSize = IntSize(targetWidth.toInt().coerceAtLeast(1), targetHeight.toInt().coerceAtLeast(1)),
                    alpha = if (sunk) 0.55f else 1f, filterQuality = FilterQuality.Medium)
            }
        }
        if (!mine && !s.localTwoPlayer) for (c in 0 until g.cells) {
            if (s.revealed[c] == 0 || g.shots[0][c] != Cratebarge.NONE) continue
            val at = Offset(m + (c % n + 0.5f) * cell, m + (c / n + 0.5f) * cell)
            drawCircle(if (s.revealed[c] == 2) Color(0xFFFFD54F) else Color.White, cell * 0.25f, at, style = Stroke(cell * 0.09f))
        }
        // the other side's shots at these waters
        val shots = g.shots[1 - owner]
        for (c in 0 until g.cells) {
            val at = Offset(m + (c % n + 0.5f) * cell, m + (c / n + 0.5f) * cell)
            when (shots[c]) { Cratebarge.MISS -> missMark(at, cell); Cratebarge.HIT -> hitMark(at, cell) }
        }
        if (g.lastShot >= 0 && g.lastBy == 1 - owner) {
            val c = g.lastShot
            drawRect(Color(0xFFFFD54F), Offset(m + (c % n) * cell, m + (c / n) * cell), Size(cell, cell), style = Stroke(cell * 0.08f))
        }
        if (s.coachFrom >= 0 && !mine) {
            val c = s.coachFrom
            drawCircle(Color(0xFFFFC107), cell * 0.42f, Offset(m + (c % n + 0.5f) * cell, m + (c / n + 0.5f) * cell), style = Stroke(cell * 0.08f))
        }
    }
}

// =====================================================================  the screen

@Composable
fun CratebargeUi(s: CratebargeSession) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    if (s.localTwoPlayer && s.handoffPending && !s.online) {
        com.puresudoku.ui.components.HandoffScreen(s.currentPlayer + 1, onReady = { s.acceptHandoff() })
        return
    }
    val me = s.viewer
    var ownWaters by remember(s) { mutableStateOf(false) }
    var showShips by remember(s) { mutableStateOf(false) }
    var showContracts by remember(s) { mutableStateOf(false) }
    var showSupplies by remember(s) { mutableStateOf(false) }
    var showDialogue by remember(s) { mutableStateOf(false) }
    if (!s.localTwoPlayer && !s.profile.introSeen) CrateIntro(s)
    if (!s.localTwoPlayer && s.showShop) CrateShop(s)
    if (showShips) CrateShipPicker(s, me) { showShips = false }
    if (showContracts) CrateContractPicker(s) { showContracts = false }
    if (showSupplies) CrateSupplyPicker(s, onPick = { item ->
        s.selectSupply(item)
        if (item == CrateSupply.PLANKS && s.selectedSupply == item) ownWaters = true
        else if (item == CrateSupply.BARREL || item == CrateSupply.TELESCOPE) ownWaters = false
        showSupplies = false
    }, onDismiss = { showSupplies = false })
    if (showDialogue) androidx.compose.material3.AlertDialog(
        onDismissRequest = { showDialogue = false }, title = { Text(if (s.localTwoPlayer) "Battle report" else s.contractInfo.enemy) },
        text = { Text(s.message) }, confirmButton = { androidx.compose.material3.TextButton(onClick = { showDialogue = false }) { Text("Close") } },
    )

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (g.phase == Cratebarge.PLACE) {
            if (!s.localTwoPlayer) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.TextButton(onClick = { showContracts = true }, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(s.contractInfo.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    androidx.compose.material3.TextButton(onClick = { s.showShop = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Shop · ${s.profile.dice} Dice", maxLines = 1)
                    }
                }
            }
            val ship = s.picked.coerceIn(g.fleet.indices)
            androidx.compose.material3.OutlinedButton(onClick = { showShips = true }, modifier = Modifier.fillMaxWidth().height(42.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), RoundedCornerShape(50)),
                enabled = s.myMove, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("${g.name(ship)} · ${g.fleet[ship]} spaces   (${g.start[me].count { it >= 0 }}/${g.fleet.size} placed)",
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            }
            Text(if (s.localTwoPlayer) "Player ${me + 1}: hide your fleet" else "Tap your waters to place the selected ship",
                fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)).padding(3.dp))
        } else {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.TextButton(onClick = { ownWaters = false }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text(if (!ownWaters) "● Enemy" else "Enemy", maxLines = 1) }
                androidx.compose.material3.TextButton(onClick = { ownWaters = true }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text(if (ownWaters) "● Your fleet" else "Your fleet", maxLines = 1) }
                if (!s.localTwoPlayer) androidx.compose.material3.TextButton(onClick = { showSupplies = true },
                    enabled = s.myMove, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Supplies", maxLines = 1) }
            }
            val detail = if (ownWaters) "Your fleet · ${g.shipsLeft(me)} of ${g.fleet.size} afloat"
                else "Enemy fleet · ${g.shipsLeft(1 - me)} of ${g.fleet.size} afloat"
            val remaining = if (!s.localTwoPlayer) " · ${(s.contractInfo.limit - g.shotsBy(0)).coerceAtLeast(0)} shots left" else ""
            Text(detail + remaining, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)).padding(3.dp))
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight).coerceAtLeast(0.dp)
            if (g.phase == Cratebarge.PLACE) {
                CratebargeGrid(s, owner = me, mine = true, modifier = Modifier.size(side), onTap = { s.tapPlace(it) })
            } else if (ownWaters) {
                CratebargeGrid(s, owner = me, mine = true, modifier = Modifier.size(side), onTap = { s.ownGridTap(it) })
            } else {
                CratebargeGrid(s, owner = 1 - me, mine = false, modifier = Modifier.size(side), onTap = { s.fire(it) })
            }
        }

        if (g.phase == Cratebarge.PLACE) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.TextButton(onClick = s::rotate, modifier = Modifier.weight(1f), enabled = s.myMove,
                    contentPadding = PaddingValues(0.dp)) { Text(if (s.vertical) "Down ↓" else "Across →", fontSize = 12.sp, maxLines = 1) }
                androidx.compose.material3.TextButton(onClick = s::scatter, modifier = Modifier.weight(1f), enabled = s.myMove,
                    contentPadding = PaddingValues(0.dp)) { Text("Random", fontSize = 12.sp, maxLines = 1) }
                androidx.compose.material3.TextButton(onClick = s::clearFleet, modifier = Modifier.weight(1f), enabled = s.myMove,
                    contentPadding = PaddingValues(0.dp)) { Text("Clear", fontSize = 12.sp, maxLines = 1) }
                Button(onClick = s::ready, modifier = Modifier.weight(1f), enabled = s.myMove && g.allPlaced(me),
                    contentPadding = PaddingValues(0.dp)) { Text("Ready", fontSize = 12.sp, maxLines = 1) }
            }
        }
        if (s.message.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 3.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)).clickable { showDialogue = true }.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (!s.localTwoPlayer && g.phase == Cratebarge.BATTLE && g.lastShot >= 0) {
                    val portrait = when (s.contract) {
                        1 -> com.puresudoku.R.drawable.cratebarge_enemy_fog_corsair_admiral
                        2 -> com.puresudoku.R.drawable.cratebarge_enemy_reefbreaker_raider
                        3 -> com.puresudoku.R.drawable.cratebarge_enemy_ironclad_toll_captain
                        4 -> com.puresudoku.R.drawable.cratebarge_enemy_storm_lantern_privateer
                        5 -> com.puresudoku.R.drawable.cratebarge_enemy_crownless_fleet_warden
                        else -> if (g.lastResult == Cratebarge.R_MISS) com.puresudoku.R.drawable.cratebarge_captain_guarded else com.puresudoku.R.drawable.cratebarge_captain_attack
                    }
                    androidx.compose.foundation.Image(painter = androidx.compose.ui.res.painterResource(portrait), contentDescription = s.contractInfo.enemy, modifier = Modifier.size(38.dp))
                }
                Text(s.message, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                    lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
            }
        }
        if (s.awaitingPass && !s.online) Button(onClick = s::pass, modifier = Modifier.height(42.dp)) { Text("Pass to Player ${g.turn + 1}") }
    }
}

@Composable
private fun CrateShipPicker(s: CratebargeSession, player: Int, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose a ship") },
        text = { Column { s.g.fleet.indices.forEach { i ->
            androidx.compose.material3.TextButton(onClick = { s.pick(i); onDismiss() }, modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("${s.g.name(i)} · ${s.g.fleet[i]} spaces${if (s.g.start[player][i] >= 0) " · placed" else ""}", maxLines = 1)
            }
        } } }, confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun CrateContractPicker(s: CratebargeSession, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose a voyage") },
        text = { Column { crateContracts.forEachIndexed { index, contract ->
            androidx.compose.material3.TextButton(onClick = { s.selectContract(index); onDismiss() }, enabled = index <= s.profile.unlocked,
                modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("${contract.title} · ${contract.limit} shots${if (index > s.profile.unlocked) " · locked" else ""}", maxLines = 1)
            }
        }; Text("Win to unlock the next voyage. A loss resets progress; Dice and supplies stay.", fontSize = 12.sp) } },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun CrateSupplyPicker(s: CratebargeSession, onPick: (CrateSupply) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss, title = { Text("Battle supplies") },
        text = { Column { CrateSupply.entries.forEach { item ->
            androidx.compose.material3.TextButton(onClick = { onPick(item) }, enabled = s.supplyCount(item) > 0 && s.myMove,
                modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Column(Modifier.fillMaxWidth()) { Text("${item.title} · ${s.supplyCount(item)} available", fontWeight = FontWeight.Bold); Text(item.description, fontSize = 12.sp) }
            }
        } } }, confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun Label(text: String) {
    Text(text, Modifier.padding(vertical = 4.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
}

@Composable
private fun CrateShop(s: CratebargeSession) {
    androidx.compose.material3.AlertDialog(onDismissRequest = { s.showShop = false }, title = { Text("Captain Cratebarge's Shop · ${s.profile.dice} Dice") },
        text = { Column {
            Text("Win voyages to earn Dice. Supplies stay until used.", fontSize = 12.sp)
            CrateSupply.entries.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val icon = when (item) {
                        CrateSupply.BARREL -> com.puresudoku.R.drawable.cratebarge_supply_barrel
                        CrateSupply.TELESCOPE -> com.puresudoku.R.drawable.cratebarge_supply_telescope
                        CrateSupply.PLANKS -> com.puresudoku.R.drawable.cratebarge_supply_planks
                        CrateSupply.DECOY -> com.puresudoku.R.drawable.cratebarge_supply_decoy
                    }
                    androidx.compose.foundation.Image(painter = androidx.compose.ui.res.painterResource(icon), contentDescription = null, modifier = Modifier.size(36.dp))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("${item.title} · ${item.cost} Dice · owned ${s.profile.stock[item.ordinal]}", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.description, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    androidx.compose.material3.TextButton(onClick = { s.buySupply(item) }, enabled = s.profile.dice >= item.cost) { Text("Buy") }
                }
            }
        } }, confirmButton = { androidx.compose.material3.TextButton(onClick = { s.showShop = false }) { Text("Back to fleet") } })
}

@Composable
private fun CrateIntro(s: CratebargeSession) {
    val text = when (s.introStep) {
        0 -> "Patch that hole before the deck learns it can be sky! No, not with rope, you soaked turnip. Planks. A hole in a ship wants wood."
        1 -> "What do you want? I am busy. My fleet got torn up by a... well, that is not important."
        2 -> if (s.introAnswer == 0) "Luck? Luck is what bad captains name their mistakes. If you are standing on my deck, make yourself useful or make yourself smaller." else "A green hand offering help to me? Captain of the largest merchant fleet still worth naming? Hm. Bold words usually sink. Sometimes they sail."
        3 -> "You think you can navigate through that fog better than Captain Cratebarge? Ha. That is either courage or head damage. Lucky for you, I respect both at sea."
        4 -> if (s.introChoice == 0) "Good. Pride with a straight spine. I can work with that. Beat me on my own waters and I will know the fleet has found a commander worth following." else "Well is where nervous captains drown their orders. Speak cleaner than that at sea. Beat me anyway, and I may forgive the wobble."
        5 -> "First rule of sea battle: hide what matters and find what the enemy hides. Place your fleet on the grid with space, direction, and a captain who can count squares."
        else -> "When battle starts, fire at enemy waters. A miss teaches where not to shoot. A hit teaches where to keep shooting. Use tools and special shots when the fog gets smug. Then you may brag. Briefly. In my presence."
    }
    androidx.compose.material3.AlertDialog(onDismissRequest = {}, title = { Text("Captain Cratebarge") }, text = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val pose = when (s.introStep) {
                0 -> com.puresudoku.R.drawable.cratebarge_captain_yelling
                1 -> com.puresudoku.R.drawable.cratebarge_captain_guarded
                2 -> if (s.introAnswer == 0) com.puresudoku.R.drawable.cratebarge_captain_angry else com.puresudoku.R.drawable.cratebarge_captain_confused
                4 -> if (s.introChoice == 0) com.puresudoku.R.drawable.cratebarge_captain_happy else com.puresudoku.R.drawable.cratebarge_captain_guarded
                6 -> com.puresudoku.R.drawable.cratebarge_captain_excited
                else -> com.puresudoku.R.drawable.cratebarge_captain
            }
            androidx.compose.foundation.Image(painter = androidx.compose.ui.res.painterResource(pose), contentDescription = "Captain Cratebarge", modifier = Modifier.height(120.dp))
            Text(text)
        } },
        confirmButton = { when (s.introStep) {
            1 -> Column {
                androidx.compose.material3.TextButton(onClick = { s.chooseIntro(0) }) { Text("That's unfortunate. Good luck with that.") }
                androidx.compose.material3.TextButton(onClick = { s.chooseIntro(1) }) { Text("I think I can help you with your problem.") }
            }
            3 -> Column {
                androidx.compose.material3.TextButton(onClick = { s.chooseIntro(0) }) { Text("Yes.") }
                androidx.compose.material3.TextButton(onClick = { s.chooseIntro(1) }) { Text("Well...") }
            }
            else -> androidx.compose.material3.TextButton(onClick = s::advanceIntro) { Text(if (s.introStep == 6) "To the fleet" else "Continue") }
        } })
}
