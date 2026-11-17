package com.puresudoku.ui.games.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.board.SeaBattle
import com.puresudoku.core.board.SeaBattleAi
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

class SeaBattleSession(difficulty: Difficulty, val g: SeaBattle) : DuelSession(GameKind.SEA_BATTLE, difficulty) {
    /** Pass-the-phone: the next player's screen stays covered until they're ready. */
    var handoffPending by mutableStateOf(false)
    /** Two players on one device: the shot has landed; the shooter sees the result, then passes the phone. */
    var awaitingPass by mutableStateOf(false)
    /** The ship being placed, and whether it runs down the grid. */
    var picked by mutableIntStateOf(0)
    var vertical by mutableStateOf(false)

    val currentPlayer: Int get() = if (g.phase == SeaBattle.PLACE) g.placer else g.turn
    override val tableSeat: Int? get() = if (localTwoPlayer) currentPlayer + 1 else null
    /** Whose fleet and shots the screen shows: your own seat online, the player at the device otherwise. */
    val viewer: Int get() = if (online) onlineSeat - 1 else if (localTwoPlayer) (if (awaitingPass) g.lastBy.coerceAtLeast(0) else currentPlayer) else 0
    fun acceptHandoff() { handoffPending = false; bump(); changed() }
    override fun skipHandoff() { handoffPending = false; bump() }

    private val level get() = aiDifficulty.ordinal

    override val middleStat get() = MiddleStat.Counter("Shots", "${g.shotsBy(0)}")
    override val progress: Float get() = (g.fleet.indices.count { g.sunk(1, it) }.toFloat() / g.fleet.size).coerceAtMost(0.95f)
    override val wonTitle get() = if (localTwoPlayer) "Player 1 rules the waves!" else "Enemy fleet sunk!"
    override val lostTitle get() = if (localTwoPlayer) "Player 2 rules the waves!" else "Your fleet is sunk"
    override val lostMessage get() = "The rival found every ship. This game's save slot has been cleared."

    val myMove: Boolean get() = outcome == Outcome.PLAYING && !thinking && g.phase != SeaBattle.OVER &&
        (localTwoPlayer || currentPlayer == 0) && (!localTwoPlayer || (!handoffPending && !awaitingPass)) && myOnlineTurn

    fun who(p: Int) = when { online -> if (p == onlineSeat - 1) "You" else "Your opponent"; localTwoPlayer -> "Player ${p + 1}"; p == 0 -> "You"; else -> "The rival" }

    init { if (g.phase == SeaBattle.PLACE) message = "Place your fleet: pick a ship, then tap the grid" }

    // ---------------------------------------------------------------- placing

    fun tapPlace(c: Int) {
        if (!myMove || g.phase != SeaBattle.PLACE) return
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
    fun scatter() { if (myMove && g.phase == SeaBattle.PLACE) { g.randomFleet(currentPlayer, rng); bump(); changed() } }
    fun clearFleet() { if (myMove && g.phase == SeaBattle.PLACE) { g.clear(currentPlayer); picked = 0; bump(); changed() } }

    fun ready() {
        if (!myMove || g.phase != SeaBattle.PLACE) return
        val p = currentPlayer
        if (!g.allPlaced(p)) { nudge("Place every ship first"); return }
        g.ready(p)
        if (!localTwoPlayer) {
            SeaBattleAi.place(g, 1, level, rng); g.ready(1)
            message = "Fire at the enemy's waters"
            announce("The rival hides its fleet", "Tap a square of the enemy's waters to fire", 1400, true)
        } else {
            picked = 0; vertical = false
            message = if (g.phase == SeaBattle.PLACE) "Player 2, place your fleet" else "Player 1, fire at the enemy's waters"
            handoffPending = !online
        }
        bump(); changed()
    }

    // ---------------------------------------------------------------- battle

    fun fire(c: Int) {
        if (!myMove || g.phase != SeaBattle.BATTLE) return
        val p = currentPlayer
        if (!g.canFire(p, c)) { nudge("You've already fired there"); return }
        val r = g.fire(p, c)
        moves++
        message = shotText(p, c, r)
        if (g.phase == SeaBattle.OVER) { bump(); changed(); finishGame(); return }
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
            r >= SeaBattle.R_SUNK -> "$who fired at $at: sank the ${g.name(r - SeaBattle.R_SUNK)}!"
            r == SeaBattle.R_HIT -> "$who fired at $at: hit!"
            else -> "$who fired at $at: miss"
        }
    }

    private fun rivalTurn() {
        if (localTwoPlayer || outcome != Outcome.PLAYING || g.phase != SeaBattle.BATTLE || g.turn != 1) return
        val snap = g.copy(); val l = level; val seed = rng.nextLong()
        computer(minMs = 750, think = { SeaBattleAi.shot(snap, 1, l, Random(seed)) }) { c ->
            val r = g.fire(1, c)
            message = shotText(1, c, r)
            bump(); changed()
            announce(when { r >= SeaBattle.R_SUNK -> "Your ${g.name(r - SeaBattle.R_SUNK)} is sunk!"; r == SeaBattle.R_HIT -> "The rival hits at ${g.coord(c)}!"; else -> "The rival misses at ${g.coord(c)}" },
                if (g.phase == SeaBattle.OVER) "" else "Your turn to fire", if (r == SeaBattle.R_MISS) 900 else 1400, false)
            if (g.phase == SeaBattle.OVER) whenQuiet { finishGame() }
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
        if (localTwoPlayer || g.phase != SeaBattle.BATTLE || g.turn != 0) return null
        val snap = g.copy()
        return { val c = SeaBattleAi.shot(snap, 0, 5, Random(7)); Triple("Try ${snap.coord(c)}: ships are most likely to be there.", c, -1) }
    }

    override fun resume() {
        if (outcome != Outcome.PLAYING) return
        if (g.phase == SeaBattle.OVER) { finishGame(); return }
        rivalTurn()
    }

    override fun saveState(): JSONObject = JSONObject().put("g", g.toJson()).put("moves", moves).put("localTwoPlayer", localTwoPlayer)
        .put("handoffPending", handoffPending).put("awaitingPass", awaitingPass).put("message", message)

    companion object {
        fun restore(o: JSONObject): SeaBattleSession {
            val st = o.getJSONObject("state")
            return SeaBattleSession(Difficulty.valueOf(o.getString("difficulty")), SeaBattle.fromJson(st.getJSONObject("g"))).also { s ->
                s.moves = st.optInt("moves"); s.localTwoPlayer = st.optBoolean("localTwoPlayer", false)
                s.handoffPending = st.optBoolean("handoffPending", false); s.awaitingPass = st.optBoolean("awaitingPass", false)
                s.message = st.optString("message", ""); s.restoreCommon(o)
            }
        }
    }
}

// =====================================================================  definition

object SeaBattleDef : GameDef {
    override val kind = GameKind.SEA_BATTLE
    override fun generate(d: Difficulty): Session = SeaBattleSession(d, SeaBattle.forLevel(d.ordinal))
    override fun restore(o: JSONObject): Session = SeaBattleSession.restore(o).also { it.resume() }
    override fun hint(d: Difficulty) = "Hide your fleet, then take turns firing at the rival's waters. Sink every ship to win."
    override fun levelInfo(d: Difficulty) = SeaBattle.forLevel(d.ordinal).let { "${it.size}×${it.size} sea · ${it.fleet.size} ships" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "A small sea and a rival who fires wildly."
        Difficulty.MEDIUM -> "Finishes off a ship once it finds one."
        Difficulty.HARD -> "Hunts in a pattern and follows a line of hits."
        Difficulty.EXPERT -> "Works out where your ships can still hide."
        Difficulty.MASTER -> "Never wastes a shot, and spreads its ships apart."
        Difficulty.EXTREME -> "A cold, calculating admiral."
    }
    override fun background(session: Session) = com.puresudoku.R.drawable.bg_realm_ocean
    @Composable override fun Play(session: Session) = SeaBattleUi(session as SeaBattleSession)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    private fun demo(shots: Boolean): SeaBattleSession {
        val g = SeaBattle.forLevel(1)
        g.randomFleet(0, Random(4)); g.ready(0); g.randomFleet(1, Random(9)); g.ready(1)
        if (shots) {
            val rnd = Random(2)
            repeat(9) { if (g.phase == SeaBattle.BATTLE) g.fire(g.turn, SeaBattleAi.shot(g, g.turn, 4, rnd)) }
        }
        return SeaBattleSession(Difficulty.MEDIUM, g)
    }

    override val tutorial: List<TutorialStep> get() = listOf(
        TutorialStep("Hide your fleet", "Place each ship on your grid: pick a ship, tap where it starts, and Rotate to turn it. Random scatters them for you.") { _ ->
            val s = remember { demo(false) }
            SeaGrid(s, owner = 0, mine = true, modifier = Modifier.fillMaxWidth().padding(24.dp))
        },
        TutorialStep("Fire!", "Take turns firing at a square of the enemy's waters. White rings are misses, fire is a hit. Hit every square of a ship to sink it.") { _ ->
            val s = remember { demo(true) }
            SeaGrid(s, owner = 1, mine = false, modifier = Modifier.fillMaxWidth().padding(24.dp))
        },
        TutorialStep("Sink them all", "Sink the whole enemy fleet before they sink yours. After a hit, try the squares around it to find which way the ship runs.") { _ ->
            val s = remember { demo(true) }
            SeaGrid(s, owner = 0, mine = true, modifier = Modifier.fillMaxWidth().padding(24.dp))
        },
    )
}

// =====================================================================  drawing

/** The Eiseria water painting, cropped from inside its hexagon frame. */
@Composable
private fun rememberWater(): ImageBitmap = ImageBitmap.imageResource(com.puresudoku.R.drawable.rt_water)

private fun DrawScope.water(img: ImageBitmap, r: Rect) {
    // the largest square inside the hexagon's frame
    val w = img.width.toFloat(); val h = img.height.toFloat()
    val a = w * 0.33f
    drawImage(img, srcOffset = IntOffset((w / 2 - a).toInt(), (h / 2 - a).toInt()), srcSize = IntSize((2 * a).toInt(), (2 * a).toInt()),
        dstOffset = IntOffset(r.left.toInt(), r.top.toInt()), dstSize = IntSize(r.width.toInt().coerceAtLeast(1), r.height.toInt().coerceAtLeast(1)), filterQuality = FilterQuality.Medium)
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
fun SeaGrid(s: SeaBattleSession, owner: Int, mine: Boolean, modifier: Modifier = Modifier, onTap: ((Int) -> Unit)? = null) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    val img = rememberWater()
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
            ship(r, g.fleet[i], g.vert[owner][i] == 1, sunk)
        }
        // the other side's shots at these waters
        val shots = g.shots[1 - owner]
        for (c in 0 until g.cells) {
            val at = Offset(m + (c % n + 0.5f) * cell, m + (c / n + 0.5f) * cell)
            when (shots[c]) { SeaBattle.MISS -> missMark(at, cell); SeaBattle.HIT -> hitMark(at, cell) }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeaBattleUi(s: SeaBattleSession) {
    @Suppress("UNUSED_VARIABLE") val ver = s.version
    val g = s.g
    if (s.localTwoPlayer && s.handoffPending && !s.online) {
        com.puresudoku.ui.components.HandoffScreen(s.currentPlayer + 1, onReady = { s.acceptHandoff() })
        return
    }
    val me = s.viewer
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val big = minOf(maxWidth, 420.dp)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (g.phase == SeaBattle.PLACE) {
                Label(if (s.localTwoPlayer) "Player ${me + 1}: hide your fleet" else "Hide your fleet")
                SeaGrid(s, owner = me, mine = true, modifier = Modifier.width(big), onTap = { s.tapPlace(it) })
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    for (i in g.fleet.indices) {
                        val placed = g.start[me][i] >= 0
                        val on = i == s.picked && !placed
                        Text("${g.name(i)} · ${g.fleet[i]}", Modifier.clip(RoundedCornerShape(50))
                            .background(if (on) MaterialTheme.colorScheme.primary else if (placed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                            .clickable { s.pick(i) }.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilledTonalButton(onClick = s::rotate) { Text(if (s.vertical) "Down ↓" else "Across →") }
                    androidx.compose.material3.FilledTonalButton(onClick = s::scatter) { Text("Random") }
                    androidx.compose.material3.FilledTonalButton(onClick = s::clearFleet) { Text("Clear") }
                }
                Spacer(Modifier.height(6.dp))
                Button(onClick = s::ready, enabled = g.allPlaced(me)) { Text("Ready") }
            } else {
                Label("Enemy waters · ${g.shipsLeft(1 - me)} of ${g.fleet.size} ships afloat")
                SeaGrid(s, owner = 1 - me, mine = false, modifier = Modifier.width(big), onTap = { s.fire(it) })
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Label("Your fleet")
                        SeaGrid(s, owner = me, mine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1f).padding(top = 28.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).padding(10.dp)) {
                        for (i in g.fleet.indices) {
                            val lost = g.sunk(me, i)
                            Text((if (lost) "✕ " else "● ") + g.name(i) + " (${g.fleet[i]})", fontSize = 13.sp,
                                color = if (lost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (lost) FontWeight.Normal else FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(6.dp))
                        val acc = g.hitsBy(me) * 100 / g.shotsBy(me).coerceAtLeast(1)
                        Text("${g.shotsBy(me)} shots · $acc% hit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (s.message.isNotEmpty()) Text(s.message, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
            if (s.awaitingPass && !s.online) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = s::pass) { Text("Pass to Player ${g.turn + 1}") }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, Modifier.padding(vertical = 4.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
}
