package com.puresudoku.ui.games.realm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.puresudoku.core.Difficulty
import com.puresudoku.core.board.KAct
import com.puresudoku.core.board.Kreashun
import com.puresudoku.core.board.KreashunAi
import com.puresudoku.core.board.RAct
import com.puresudoku.core.board.Realm
import com.puresudoku.core.board.RealmAi
import com.puresudoku.core.board.VOrder
import com.puresudoku.core.board.Veilguard
import com.puresudoku.core.board.VeilguardAi
import com.puresudoku.core.board.WAct
import com.puresudoku.core.board.Wisteria
import com.puresudoku.core.board.WisteriaAi
import com.puresudoku.ui.tutorial.DemoScope
import com.puresudoku.ui.tutorial.DemoStage
import com.puresudoku.ui.tutorial.TutorialStep
import kotlin.random.Random

/*
 * How-to-play demos for the strategy games. Each one plays the real game: a finger taps the board and the
 * buttons to make the moves the computer's best player would make, while the rival answers as usual.
 * The demo board itself can't be touched.
 */

private val BEST = Difficulty.EXTREME

/** Waits until [cond] holds (the rival's turn and its herald cards can take a while), up to [ms]. */
private suspend fun DemoScope.waitFor(ms: Long = 16000, cond: () -> Boolean) {
    var t = 0L
    while (!cond() && t < ms) { wait(120); t += 120 }
}

// =====================================================================  Wisteria and Wisteria: Ashfall

/** Plays both sides with the computer until [done], so a demo can start mid-game. */
private fun warm(g: Wisteria, rng: Random, done: (Wisteria) -> Boolean): Wisteria {
    val lvl = WisteriaAi.level(Difficulty.MEDIUM)
    var steps = 0
    while (g.phase != Wisteria.OVER && !(g.actor == 0 && g.phase == Wisteria.MAIN && done(g)) && steps++ < 4000) {
        var a = WisteriaAi.next(g, lvl, rng)
        if (!g.legal(a)) a = if (g.phase == Wisteria.CHOICE) WAct.Skip else WAct.End
        g.apply(a, rng)
    }
    g.log.clear()
    return g
}

private suspend fun DemoScope.select(s: WisteriaSession, h: Int) {
    if (s.selected != h) tap("h-$h") { s.tapTile(h) }
}

/** Shows one of your moves with the finger. False when the turn is over or the move can't be shown. */
private suspend fun DemoScope.play(s: WisteriaSession, a: WAct): Boolean {
    val g = s.g
    when (a) {
        is WAct.Settle -> tap("h-${a.h}") { s.tapTile(a.h) }
        is WAct.Claim -> if (g.phase == Wisteria.CHOICE) tap("w-claim") { s.claim(a.h) } else { select(s, a.h); tap("b-claim") { s.claim(a.h) } }
        WAct.Skip -> tap("w-leave") { s.skip() }
        is WAct.Upgrade -> { select(s, a.h); tap(if (g.level[a.h] == 1) "b-tower" else "b-castle") { s.upgrade(a.h) } }
        is WAct.Hire -> { select(s, a.h); tap("b-knight") { s.hire(a.h) } }
        is WAct.Strike -> {
            if (a.black) return false
            select(s, a.from); tap("b-strike") { s.toggleMode(WisteriaSession.Mode.STRIKE) }; tap("h-${a.to}") { s.tapTile(a.to) }
        }
        is WAct.March -> {
            if (a.black) return false
            val t = a.route.last()
            select(s, a.from); tap("b-march") { s.toggleMode(WisteriaSession.Mode.MARCH) }
            tap("h-$t") { s.tapTile(t) }; wait(700); tap("h-$t") { s.tapTile(t) }
        }
        is WAct.Wall -> { select(s, a.a); tap("b-wall") { s.toggleMode(WisteriaSession.Mode.WALL) }; tap("h-${a.b}") { s.tapTile(a.b) } }
        is WAct.Trade -> tap("b-trade") { s.trade(a.give, a.get) }
        WAct.BuySpell -> tap("b-spell") { s.buySpell() }
        WAct.End -> { tap("b-end") { s.endTurn() }; return false }
        is WAct.Spell -> return false
    }
    return true
}

/** Up to [moves] of your best moves, one after another, then the end of your turn. */
private suspend fun DemoScope.playTurn(s: WisteriaSession, moves: Int, rng: Random) {
    waitFor { s.myMove }
    repeat(moves) {
        if (!s.myMove) return
        val a = WisteriaAi.next(s.g, WisteriaAi.level(BEST), rng)
        if (a == WAct.End) { tap("b-end") { s.endTurn() }; return }
        if (!play(s, a)) return
        wait(500)
    }
    if (s.myMove) tap("b-end") { s.endTurn() }
}

internal fun wisteriaTutorial(ashfall: Boolean): List<TutorialStep> {
    fun fresh(seed: Int) = if (ashfall) Wisteria.newAshfall(Difficulty.EASY, Random(seed)) else Wisteria.newGame(Difficulty.EASY, Random(seed))
    fun session(g: Wisteria) = WisteriaSession(Difficulty.EASY, g)
    val steps = ArrayList<TutorialStep>()
    steps += TutorialStep("Claim the land", "Settle a tile, then claim the land touching it. Every tile you hold pays out every turn.") { active ->
        var s by remember { mutableStateOf(session(fresh(5))) }
        DemoStage(active, script = {
            s.cancelThinking(); s = session(fresh(5)); wait(500)
            val rng = Random(1)
            play(s, WisteriaAi.next(s.g, WisteriaAi.level(BEST), rng))
            playTurn(s, 2, rng)
            waitFor { s.myMove }
            playTurn(s, 2, rng)
            wait(1500)
        }) { t -> WisteriaUi(s, t) }
    }
    steps += TutorialStep("Build and conquer", "Raise towers and castles, hire knights to strike or march, and wall your borders. First to the points goal wins.") { active ->
        var s by remember { mutableStateOf(session(warm(fresh(11), Random(11)) { it.turns >= 14 })) }
        DemoStage(active, script = {
            s.cancelThinking(); s = session(warm(fresh(11), Random(11)) { it.turns >= 14 }); wait(500)
            val rng = Random(2)
            playTurn(s, 4, rng)
            waitFor { s.myMove }
            playTurn(s, 4, rng)
            wait(1500)
        }) { t -> WisteriaUi(s, t) }
    }
    if (ashfall) steps += TutorialStep("The volcano erupts", "At the end of every turn a die picks which way the lava runs, burying a straight row of tiles. Ruined land cools, then is renewed, richer than before.") { active ->
        fun ready() = session(warm(fresh(21), Random(21)) { it.eruptions >= 6 && it.eruptIn == 1 })
        var s by remember { mutableStateOf(ready()) }
        DemoStage(active, script = {
            s.cancelThinking(); s = ready(); wait(500)
            highlight(listOf("b-end"), 1400)
            playTurn(s, 1, Random(3))
            wait(5000)
        }) { t -> WisteriaUi(s, t) }
    }
    return steps
}

// =====================================================================  Kreashun

private fun warm(g: Kreashun, rng: Random, turns: Int): Kreashun {
    val lvl = KreashunAi.level(Difficulty.MEDIUM)
    var steps = 0
    while (g.phase != Kreashun.OVER && !(g.actor == 0 && g.phase == Kreashun.ROLL && g.turns >= turns) && steps++ < 6000) {
        var a = KreashunAi.next(g, lvl, rng)
        if (!g.legal(a)) a = if (g.phase == Kreashun.ROLL) KAct.Roll else KAct.End
        g.apply(a, rng)
    }
    g.log.clear()
    return g
}

private suspend fun DemoScope.play(s: KreashunSession, a: KAct): Boolean {
    val g = s.g
    suspend fun select(h: Int) { if (s.selected != h) tap("h-$h") { s.tapTile(h) } }
    when (a) {
        KAct.Roll -> tap("w-roll") { s.roll() }
        is KAct.Village -> if (g.phase == Kreashun.SETUP) tap("v-${a.v}") { s.tapVertex(a.v) }
            else { tap("b-village") { s.toggleMode(KreashunSession.Mode.VILLAGE) }; tap("v-${a.v}") { s.tapVertex(a.v) } }
        is KAct.Tower -> { tap("b-tower") { s.toggleMode(KreashunSession.Mode.TOWER) }; tap("v-${a.v}") { s.tapVertex(a.v) } }
        is KAct.Wall -> if (g.phase == Kreashun.SETUP) tap("e-${a.e}") { s.tapEdge(a.e) }
            else { if (s.mode != KreashunSession.Mode.WALL) tap("b-wall") { s.toggleMode(KreashunSession.Mode.WALL) }; tap("e-${a.e}") { s.tapEdge(a.e) } }
        is KAct.Castle -> { select(a.h); tap("b-castle") { s.castle(a.h) } }
        is KAct.Hire -> { select(a.h); tap("b-knight") { s.hire(a.h) } }
        is KAct.Strike -> { select(a.from); tap("b-strike") { s.toggleMode(KreashunSession.Mode.STRIKE) }; tap("h-${a.to}") { s.tapTile(a.to) } }
        is KAct.Trade -> tap("b-trade") { s.trade(a.give, a.get) }
        KAct.BuySpell -> tap("b-spell") { s.buySpell() }
        KAct.End -> { tap("b-end") { s.endTurn() }; return false }
        is KAct.Spell -> return false
    }
    return true
}

private suspend fun DemoScope.playTurn(s: KreashunSession, moves: Int, rng: Random) {
    waitFor { s.myMove }
    repeat(moves + 1) {
        if (!s.myMove) return
        val a = KreashunAi.next(s.g, KreashunAi.level(BEST), rng)
        if (!play(s, a)) return
        wait(450)
    }
    if (s.myMove && s.g.phase == Kreashun.MAIN) tap("b-end") { s.endTurn() }
}

internal fun kreashunTutorial(): List<TutorialStep> = listOf(
    TutorialStep("Wall it in", "Place villages on corners, each with a wall beside it. Close every side of a tile with walls to capture it.") { active ->
        var s by remember { mutableStateOf(KreashunSession(Difficulty.EASY, Kreashun.newGame(Difficulty.EASY, Random(5)))) }
        DemoStage(active, script = {
            s.cancelThinking(); s = KreashunSession(Difficulty.EASY, Kreashun.newGame(Difficulty.EASY, Random(5))); wait(500)
            val rng = Random(1)
            repeat(2) { waitFor { s.myMove }; play(s, KreashunAi.next(s.g, KreashunAi.level(BEST), rng)); wait(400) }
            waitFor { s.myMove }
            repeat(2) { waitFor { s.myMove }; play(s, KreashunAi.next(s.g, KreashunAi.level(BEST), rng)); wait(400) }
            playTurn(s, 3, rng)
            wait(1500)
        }) { t -> KreashunUi(s, t) }
    },
    TutorialStep("Storm the castles", "Captured tiles raise castles and hire knights. Knights break the walls and take your rival's land.") { active ->
        fun ready() = KreashunSession(Difficulty.EASY, warm(Kreashun.newGame(Difficulty.EASY, Random(13)), Random(13), 24))
        var s by remember { mutableStateOf(ready()) }
        DemoStage(active, script = {
            s.cancelThinking(); s = ready(); wait(500)
            val rng = Random(2)
            playTurn(s, 4, rng)
            playTurn(s, 4, rng)
            wait(1500)
        }) { t -> KreashunUi(s, t) }
    },
)

// =====================================================================  Veilguard

private fun warm(g: Veilguard, rng: Random, rounds: Int): Veilguard {
    val lvl = VeilguardAi.level(Difficulty.MEDIUM)
    while (g.phase == Veilguard.SETUP) g.settle(VeilguardAi.settle(g, g.actor, lvl, rng))
    while (g.phase == Veilguard.PLAN && g.round < rounds) {
        for (p in 0..1) {
            val pl = VeilguardAi.plan(g, p, lvl, rng)
            for ((a, b) in pl.trades) g.trade(p, a, b)
            for (o in pl.orders) g.place(p, o)
        }
        g.resolve(rng)
    }
    g.log.clear()
    return g
}

/** Gives the best orders with the finger, then reveals them. */
private suspend fun DemoScope.giveOrders(s: VeilguardSession, rng: Random) {
    waitFor { s.myMove && s.g.phase == Veilguard.PLAN }
    val g = s.g
    val plan = VeilguardAi.plan(g, 0, VeilguardAi.level(BEST), rng)
    for ((a, b) in plan.trades) s.trade(a, b)
    suspend fun select(h: Int) { if (s.selected != h) tap("h-$h") { s.tapTile(h) } }
    for (o in plan.orders) {
        when (o) {
            is VOrder.March -> { select(o.from); tap("b-march") { s.toggleMode(VeilguardSession.Mode.MARCH) }; tap("h-${o.to}") { s.tapTile(o.to) } }
            is VOrder.Muster -> { select(o.h); tap(if (o.decoy) "b-decoy" else "b-knight") { s.order(o) } }
            is VOrder.Build -> { select(o.h); tap(if (g.level[o.h] == 1) "b-tower" else "b-castle") { s.order(o) } }
            is VOrder.Wall -> { select(o.a); tap("b-wall") { s.toggleMode(VeilguardSession.Mode.WALL) }; tap("h-${o.b}") { s.tapTile(o.b) } }
            is VOrder.Fortify -> { select(o.h); tap("b-fortify") { s.order(o) } }
            is VOrder.Claim -> { select(o.h); tap("b-claim") { s.order(o) } }
            is VOrder.Scout -> { select(o.h); tap("b-scout") { s.order(o) } }
            is VOrder.Crown -> { select(o.h); tap("b-crown") { s.order(o) } }
        }
        // let go of the tile so the board shows again between orders
        wait(500); s.selected = -1
    }
    tap("b-reveal") { s.reveal() }
}

internal fun veilguardTutorial(): List<TutorialStep> = listOf(
    TutorialStep("Secret orders", "Settle two lands: your Crown hides in the first. Each round give up to three secret orders, then reveal them together with the rival's.") { active ->
        var s by remember { mutableStateOf(VeilguardSession(Difficulty.EASY, Veilguard.newGame(Difficulty.EASY, Random(5)))) }
        DemoStage(active, script = {
            s.cancelThinking(); s = VeilguardSession(Difficulty.EASY, Veilguard.newGame(Difficulty.EASY, Random(5))); wait(500)
            val rng = Random(1)
            repeat(2) {
                waitFor { s.myMove && s.g.phase == Veilguard.SETUP && s.g.actor == 0 }
                if (s.g.phase == Veilguard.SETUP) { val h = VeilguardAi.settle(s.g, 0, VeilguardAi.level(BEST), rng); tap("h-$h") { s.tapTile(h) } }
            }
            giveOrders(s, rng)
            wait(6000)
        }) { t -> VeilguardUi(s, t) }
    },
    TutorialStep("Find the Crown", "Banners may be knights or decoys. Scout to learn the truth, march on the rival's Crown, and take it to win.") { active ->
        fun ready() = VeilguardSession(Difficulty.EASY, warm(Veilguard.newGame(Difficulty.EASY, Random(17)), Random(17), 8))
        var s by remember { mutableStateOf(ready()) }
        DemoStage(active, script = {
            s.cancelThinking(); s = ready(); wait(500)
            val rng = Random(2)
            giveOrders(s, rng)
            wait(4000)
            giveOrders(s, rng)
            wait(5000)
        }) { t -> VeilguardUi(s, t) }
    },
)

// =====================================================================  Eiseria (all four)

private fun warm(g: Realm, rng: Random, rounds: Int): Realm {
    val lvl = RealmAi.level(Difficulty.MEDIUM)
    var steps = 0
    while (g.phase != Realm.OVER && !(g.actor == 0 && g.phase == Realm.ROLL && g.round >= rounds) && steps++ < 6000) {
        var a = RealmAi.next(g, lvl, rng, true)
        if (a is RAct.Offer || !g.legal(a)) a = when (g.phase) { Realm.ROLL -> RAct.Roll; Realm.MAIN -> RAct.End; else -> RealmAi.next(g, lvl, rng, true) }
        g.apply(a, rng)
    }
    g.log.clear()
    return g
}

/** Your turn, shown with the finger: roll, deal with a 7, then build. */
private suspend fun DemoScope.playTurn(s: RealmSession, moves: Int, rng: Random) {
    waitFor { s.myMove }
    val g = s.g
    var shown = 0
    while (s.myMove && shown < moves + 3) {
        val a = RealmAi.next(g, RealmAi.level(BEST), rng, true)
        when {
            a == RAct.Roll -> tap("w-roll") { s.roll() }
            a is RAct.Discard -> s.discard(a.counts)
            a is RAct.Dragon -> s.tapTile(a.tile)
            a is RAct.Road -> { if (s.mode != RealmSession.Mode.ROAD && g.freeRoads == 0) tap("b-road") { s.toggleMode(RealmSession.Mode.ROAD) }; tap("e-${a.e}") { s.tapEdge(a.e) } }
            a is RAct.Village -> { tap("b-village") { s.toggleMode(RealmSession.Mode.VILLAGE) }; tap("v-${a.v}") { s.tapVertex(a.v) } }
            a is RAct.Tower -> { tap("b-tower") { s.toggleMode(RealmSession.Mode.TOWER) }; tap("v-${a.v}") { s.tapVertex(a.v) } }
            a is RAct.Trade -> tap("b-trade") { s.trade(a.give, a.get) }
            a == RAct.BuySpell -> tap("b-spell") { s.buySpell() }
            else -> { tap("b-end") { s.endTurn() }; return }
        }
        shown++
        wait(500)
    }
    if (s.myMove && g.phase == Realm.MAIN) tap("b-end") { s.endTurn() }
}

internal fun eiseriaBuildStep(kind: com.puresudoku.core.GameKind, twist: Int): TutorialStep =
    TutorialStep("Roll, build and trade", "Roll each turn: tiles with that number pay the buildings on their corners. Spend it on roads, villages and towers. First to the points goal wins.") { active ->
        fun ready() = RealmSession(kind, Difficulty.EASY, warm(Realm.newGame(twist, Difficulty.EASY, Random(9)), Random(9), 5))
        var s by remember { mutableStateOf(ready()) }
        DemoStage(active, script = {
            s.cancelThinking(); s = ready(); wait(500)
            val rng = Random(4)
            playTurn(s, 3, rng)
            playTurn(s, 3, rng)
            wait(1500)
        }) { t -> RealmUi(s, t) }
    }
