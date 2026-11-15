package com.puresudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.puresudoku.ui.theme.PureSudokuTheme
import com.puresudoku.core.Difficulty
import com.puresudoku.core.board.AegConquest
import com.puresudoku.core.board.AegStewardship
import com.puresudoku.core.board.Aegwaspell
import com.puresudoku.core.board.TicTacToe
import com.puresudoku.core.board.Gomoku
import com.puresudoku.core.board.Chess
import com.puresudoku.core.board.Shogi
import com.puresudoku.core.board.Xiangqi
import com.puresudoku.ui.games.board.XiangqiSession
import com.puresudoku.ui.games.board.ShogiSession
import com.puresudoku.core.board.Checkers
import com.puresudoku.core.board.GoGame
import com.puresudoku.core.board.Backgammon
import com.puresudoku.ui.games.board.BackgammonSession
import com.puresudoku.core.board.Mancala
import com.puresudoku.core.board.Morris
import com.puresudoku.ui.games.board.MorrisSession
import com.puresudoku.ui.games.board.MancalaSession
import com.puresudoku.ui.games.board.GoSession
import com.puresudoku.ui.games.board.CheckersSession
import com.puresudoku.ui.games.board.ChessSession
import com.puresudoku.ui.games.board.TicTacToeSession
import com.puresudoku.ui.games.board.GomokuSession
import com.puresudoku.ui.session.Outcome
import android.widget.TextView
import com.puresudoku.core.board.Wisteria
import com.puresudoku.core.board.Realm
import com.puresudoku.core.cards.CrazyEights
import com.puresudoku.core.cards.Cards
import com.puresudoku.ui.games.cards.CrazyEightsSession
import com.puresudoku.core.board.Veilguard
import com.puresudoku.ui.games.realm.VeilguardSession
import com.puresudoku.core.GameKind
import com.puresudoku.ui.games.realm.RealmSession
import com.puresudoku.core.board.Kreashun
import com.puresudoku.ui.games.realm.WisteriaSession
import com.puresudoku.ui.games.realm.WisteriaUi
import com.puresudoku.ui.games.realm.KreashunSession
import com.puresudoku.ui.games.realm.KreashunUi
import com.puresudoku.ui.games.realm.VeilguardUi
import com.puresudoku.ui.games.realm.AegConquestSession
import com.puresudoku.ui.games.realm.AegConquestUi
import com.puresudoku.ui.games.realm.AegwaspellSession
import com.puresudoku.ui.games.realm.AegwaspellUi
import kotlin.random.Random

/** Debug-only deterministic visual fixture. It never reads or writes player saves. */
class ArtQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.getStringExtra("mode")) {
            "local-slot-cancel-check" -> {
                val result = runCatching {
                    val vm = com.puresudoku.platform.AndroidAppViewModel(application).vm
                    val kind = GameKind.GO_FISH
                    vm.openGame(kind)
                    val field = com.puresudoku.ui.AppViewModel::class.java.getDeclaredField("slots\$delegate")
                    field.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val state = field.get(vm) as androidx.compose.runtime.MutableState<Map<GameKind, List<com.puresudoku.data.SlotSummary?>>>
                    val fake = com.puresudoku.data.SlotSummary(Difficulty.EASY, 0L, 0, 0, false, 0f)
                    state.value = state.value + (kind to listOf(fake, fake, fake))
                    vm.startLocalTwoPlayer(kind)
                    check(vm.screen is com.puresudoku.ui.Screen.Slots && vm.choosingLocalTwoPlayer)
                    vm.back()
                    check(vm.screen == com.puresudoku.ui.Screen.Menu(kind) && !vm.choosingLocalTwoPlayer)
                    vm.openLevelPicker(kind)
                    check(vm.screen == com.puresudoku.ui.Screen.PickLevel(kind) && !vm.choosingLocalTwoPlayer)
                    "PASS: cancelling a full-slot local match clears pending mode"
                }.getOrElse { "FAIL: ${it.stackTraceToString()}" }
                setContentView(TextView(this).apply { text = result; textSize = 20f }); return
            }
            "backgammon-undo-check" -> {
                val view = TextView(this).apply { text = "Running undo check…"; textSize = 20f }
                setContentView(view)
                val result = runCatching {
                    val game = Backgammon().apply { rolled = intArrayOf(1, 2); dice += listOf(1, 2) }
                    val session = BackgammonSession(Difficulty.EASY, game).apply { localTwoPlayer = true; resume() }
                    val seq = game.sequences(dedupe = false).first { it.size == 2 }
                    for (step in seq) { session.tapPoint(step.from); session.tapPoint(step.to) }
                    check(!session.needRoll) // A completed turn must not offer another roll before handoff.
                    val restored = BackgammonSession.restore(session.toJson()); restored.resume()
                    check(restored.localTwoPlayer && restored.g.turn == 1 && restored.g.dice.isEmpty())
                    check(session.canUndo); session.undo(); check(game.turn == 0 && game.dice.isNotEmpty())
                    val normalGame = Backgammon().apply { rolled = intArrayOf(1, 2); dice += listOf(1, 2) }
                    val normal = BackgammonSession(Difficulty.EASY, normalGame).apply { localTwoPlayer = true; resume() }
                    val normalSeq = normalGame.sequences(dedupe = false).first { it.size == 2 }
                    for (step in normalSeq) { normal.tapPoint(step.from); normal.tapPoint(step.to) }
                    session to normal
                }
                result.onFailure { view.text = "FAIL: ${it.stackTraceToString()}" }.onSuccess { (session, normal) ->
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        view.text = if (session.g.turn == 0 && session.g.dice.isNotEmpty() && normal.g.turn == 1 && normal.g.dice.isEmpty()) "PASS: undo retains the turn; a completed turn advances" else "FAIL: Backgammon turn timing"
                    }, 900)
                }
                return
            }
            "passplay-check" -> {
                val result = runCatching {
                    val t = TicTacToeSession(Difficulty.EASY, TicTacToe()); t.localTwoPlayer = true
                    t.tap(0); check(t.g.turn == 2 && !t.thinking)
                    val restored = TicTacToeSession.restore(t.toJson()); restored.resume()
                    check(restored.localTwoPlayer && restored.g.turn == 2 && !restored.thinking)
                    restored.tap(3); check(restored.g.cells[3] == 2 && restored.g.turn == 1)
                    val g = GomokuSession(Difficulty.EASY, Gomoku()); g.localTwoPlayer = true
                    g.tap(0); g.tap(0); check(g.g.turn == 2 && !g.thinking)
                    val gr = GomokuSession.restore(g.toJson()); gr.resume()
                    check(gr.localTwoPlayer && gr.g.turn == 2 && !gr.thinking)
                    for (i in listOf(15, 1, 16, 2, 17, 3, 18, 4)) { gr.tap(i); gr.tap(i) }
                    check(gr.outcome == Outcome.WON && gr.wonTitle == "Player 1 wins!")
                    val c = ChessSession(Difficulty.EASY, Chess.start()); c.localTwoPlayer = true
                    c.tap(52); c.tap(36); check(c.g.turn == -1 && !c.thinking)
                    val cr = ChessSession.restore(c.toJson()); cr.resume()
                    check(cr.localTwoPlayer && cr.g.turn == -1 && !cr.thinking)
                    cr.tap(12); cr.tap(28); check(cr.g.turn == 1 && cr.g.b[28] < 0)
                    val ck = CheckersSession(Difficulty.EASY, Checkers()); ck.localTwoPlayer = true
                    ck.g.legalMoves().first().let { ck.tap(it.from); ck.tap(it.to) }
                    check(ck.g.turn == -1 && !ck.thinking)
                    val ckr = CheckersSession.restore(ck.toJson()); ckr.resume()
                    check(ckr.localTwoPlayer && ckr.g.turn == -1 && !ckr.thinking)
                    ckr.g.legalMoves().first().let { ckr.tap(it.from); ckr.tap(it.to) }; check(ckr.g.turn == 1)
                    val go = GoSession(Difficulty.EASY, GoGame(9)); go.localTwoPlayer = true
                    go.tap(0); go.tap(0); check(go.g.turn == 2 && !go.thinking)
                    val gor = GoSession.restore(go.toJson()); gor.resume()
                    check(gor.localTwoPlayer && gor.g.turn == 2 && !gor.thinking)
                    gor.pass(); check(gor.g.turn == 1 && gor.g.passes == 1 && !gor.thinking)
                    val endedGo = GoSession(Difficulty.EASY, GoGame(9).apply { passes = 2 }); endedGo.localTwoPlayer = true
                    check(endedGo.localActor == null)
                    val sh = ShogiSession(Difficulty.EASY, Shogi()); sh.localTwoPlayer = true
                    val sm = sh.g.legalMoves().first { !Shogi.isDrop(it) }
                    sh.tap(Shogi.src(sm)); sh.tap(Shogi.dst(sm)); check(sh.g.turn == -1 && !sh.thinking)
                    val shr = ShogiSession.restore(sh.toJson()); shr.resume()
                    check(shr.localTwoPlayer && shr.g.turn == -1 && !shr.thinking)
                    val sm2 = shr.g.legalMoves().first { !Shogi.isDrop(it) }
                    shr.tap(Shogi.src(sm2)); shr.tap(Shogi.dst(sm2)); check(shr.g.turn == 1)
                    val drop = Shogi(); drop.turn = -1; drop.hands[1][Shogi.SILVER] = 1
                    val sd = ShogiSession(Difficulty.EASY, drop); sd.localTwoPlayer = true
                    val dm = sd.g.legalMoves().first { Shogi.isDrop(it) && Shogi.src(it) - Shogi.DROP == Shogi.SILVER }
                    sd.tapHand(Shogi.SILVER); sd.tap(Shogi.dst(dm)); check(sd.g.hands[1][Shogi.SILVER] == 0 && sd.g.turn == 1)
                    val x = XiangqiSession(Difficulty.EASY, Xiangqi()); x.localTwoPlayer = true
                    x.g.legalMoves().first().let { x.tap(Xiangqi.src(it)); x.tap(Xiangqi.dst(it)) }; check(x.g.turn == -1 && !x.thinking)
                    val xr = XiangqiSession.restore(x.toJson()); xr.resume(); check(xr.localTwoPlayer && xr.g.turn == -1 && !xr.thinking)
                    xr.g.legalMoves().first().let { xr.tap(Xiangqi.src(it)); xr.tap(Xiangqi.dst(it)) }; check(xr.g.turn == 1)
                    val m = MancalaSession(Difficulty.EASY, Mancala()); m.localTwoPlayer = true
                    m.tap(0); check(m.g.turn == 1 && !m.thinking)
                    val mr = MancalaSession.restore(m.toJson()); mr.resume(); check(mr.localTwoPlayer && mr.g.turn == 1 && !mr.thinking)
                    mr.tap(7); check(mr.moves == 2 && !mr.thinking)
                    val mo = MorrisSession(Difficulty.EASY, Morris()); mo.localTwoPlayer = true
                    mo.tap(0); check(mo.g.turn == 2 && !mo.thinking)
                    val mor = MorrisSession.restore(mo.toJson()); mor.resume(); check(mor.localTwoPlayer && mor.g.turn == 2 && !mor.thinking)
                    mor.tap(1); check(mor.g.turn == 1 && mor.g.board[1] == 2)
                    val mill = Morris().apply { board[0] = 2; board[1] = 2; board[9] = 1; board[12] = 1; inHand[2] = 7; inHand[1] = 7; turn = 2 }
                    val ms = MorrisSession(Difficulty.EASY, mill); ms.localTwoPlayer = true
                    ms.tap(2); check(ms.removing != null); ms.tap(9); check(ms.g.board[9] == 0 && ms.g.turn == 1)
                    val bg = BackgammonSession(Difficulty.EASY, Backgammon().apply { turn = 1 }); bg.localTwoPlayer = true
                    check(bg.needRoll); bg.roll(); check(bg.g.dice.isNotEmpty() && !bg.thinking)
                    val bgr = BackgammonSession.restore(bg.toJson()); bgr.resume(); check(bgr.localTwoPlayer && bgr.g.turn == 1 && !bgr.thinking)
                    val from = bgr.movableFrom().first(); val step = bgr.options(from).first()
                    bgr.tapPoint(from); bgr.tapPoint(step.to); check(bgr.g.dice.size < bg.g.dice.size)
                    val w = WisteriaSession(Difficulty.EASY, Wisteria.newGame(Difficulty.EASY, Random(42))); w.localTwoPlayer = true
                    check(w.handoffPending && !w.myMove); w.acceptHandoff(); val spot = w.g.settleSpots(0).first(); w.tapTile(spot)
                    check(w.g.actor == 1 && w.handoffPending && !w.thinking)
                    val wr = WisteriaSession.restore(w.toJson()); wr.resume(); check(wr.localTwoPlayer && wr.handoffPending && !wr.thinking)
                    wr.acceptHandoff(); wr.tapTile(wr.g.settleSpots(1).first()); check(wr.g.actor == 0 && wr.handoffPending)
                    wr.acceptHandoff(); wr.endTurn(); check(wr.g.actor == 1 && wr.handoffPending && !wr.thinking)
                    wr.acceptHandoff(); wr.g.res[1][0] = 10; val r0 = wr.g.res[0][0]
                    wr.trade(0, 1); check(wr.g.res[1][0] == 6 && wr.g.res[0][0] == r0); wr.g.res[1].fill(10)
                    val wSpells0 = wr.g.spells[0].sum(); wr.buySpell()
                    check(wr.g.spells[1].sum() == 1 && wr.g.spells[0].sum() == wSpells0)
                    wr.endTurn(); check(wr.g.actor == 0 && wr.handoffPending && !wr.thinking)
                    val ks = KreashunSession(Difficulty.EASY, Kreashun.newGame(Difficulty.EASY, Random(42))); ks.localTwoPlayer = true
                    check(ks.handoffPending && !ks.myMove); ks.acceptHandoff()
                    ks.tapVertex(ks.g.villageSpots(0).first()); check(ks.g.actor == 0 && !ks.handoffPending)
                    ks.tapEdge(ks.g.wallSpots(0).first()); check(ks.g.actor == 1 && ks.handoffPending && !ks.thinking)
                    val kr = KreashunSession.restore(ks.toJson()); kr.resume(); check(kr.localTwoPlayer && kr.handoffPending && !kr.thinking)
                    kr.acceptHandoff(); kr.tapVertex(kr.g.villageSpots(1).first()); kr.tapEdge(kr.g.wallSpots(1).first()); check(!kr.thinking)
                    val kMainGame = Kreashun.newGame(Difficulty.EASY, Random(51)); kMainGame.phase = Kreashun.MAIN; kMainGame.turn = 1; kMainGame.res[1].fill(10)
                    val kMain = KreashunSession(Difficulty.EASY, kMainGame); kMain.localTwoPlayer = true; kMain.acceptHandoff()
                    kMain.trade(0, 1); check(kMain.g.res[1][0] == 6 && kMain.g.res[0][0] == 0)
                    kMain.buySpell(); check(kMain.g.spells[1].sum() == 1 && kMain.g.spells[0].sum() == 0)
                    val rs = RealmSession(GameKind.REALMFORGE, Difficulty.EASY, Realm.newGame(Realm.FOG, Difficulty.EASY, Random(42))); rs.localTwoPlayer = true
                    check(rs.handoffPending && !rs.myMove); rs.acceptHandoff()
                    rs.tapVertex(rs.g.villageSpots(0).first()); rs.tapEdge(rs.g.roadSpots(0).first())
                    check(rs.g.actor == 1 && rs.handoffPending && !rs.thinking)
                    val rr = RealmSession.restore(GameKind.REALMFORGE, rs.toJson()); rr.resume(); check(rr.localTwoPlayer && rr.handoffPending && !rr.thinking)
                    rr.acceptHandoff(); rr.tapVertex(rr.g.villageSpots(1).first()); rr.tapEdge(rr.g.roadSpots(1).first()); check(!rr.thinking)
                    val seven = Realm.newGame(Realm.FOG, Difficulty.EASY, Random(17)); seven.phase = Realm.DISCARD; seven.turn = 0
                    seven.discardNeed[0] = 4; seven.discardNeed[1] = 4; seven.res[0][0] = 8; seven.res[1][0] = 8
                    val ds = RealmSession(GameKind.REALMFORGE, Difficulty.EASY, seven); ds.localTwoPlayer = true; ds.acceptHandoff()
                    ds.discard(intArrayOf(4,0,0,0,0)); check(ds.currentPlayer == 1 && ds.handoffPending && ds.g.res[0][0] == 4 && ds.g.res[1][0] == 8)
                    ds.acceptHandoff(); ds.discard(intArrayOf(4,0,0,0,0)); check(ds.g.res[1][0] == 4 && ds.currentPlayer == 0 && ds.handoffPending)
                    val realmMainGame = Realm.newGame(Realm.FOG, Difficulty.EASY, Random(50)); realmMainGame.phase = Realm.MAIN; realmMainGame.turn = 1; realmMainGame.res[1].fill(10)
                    val realmMain = RealmSession(GameKind.REALMFORGE, Difficulty.EASY, realmMainGame); realmMain.localTwoPlayer = true; realmMain.acceptHandoff()
                    val rate = realmMain.g.portRate(1, 0); realmMain.trade(0, 1)
                    check(realmMain.g.res[1][0] == 10 - rate && realmMain.g.res[0][0] == 0)
                    realmMain.buySpell(); check(realmMain.g.spells[1].sum() == 1 && realmMain.g.spells[0].sum() == 0)
                    val vs = VeilguardSession(Difficulty.EASY, Veilguard.newGame(Difficulty.EASY, Random(42))); vs.localTwoPlayer = true
                    while (vs.g.phase == Veilguard.SETUP) { if (vs.handoffPending) vs.acceptHandoff(); val p = vs.currentPlayer; vs.tapTile(vs.g.settleSpots(p).first()) }
                    check(vs.handoffPending && vs.currentPlayer == 0 && vs.startingCrownPlayer == 0); vs.acceptHandoff()
                    val crown0 = (0 until vs.g.H).first { vs.g.owner[it] == 0 && it != vs.g.crown[0] }
                    vs.tapTile(crown0); check(vs.g.crown[0] == crown0 && vs.handoffPending && vs.currentPlayer == 1)
                    val choiceSaved = VeilguardSession.restore(vs.toJson()); choiceSaved.resume()
                    check(choiceSaved.startingCrownPlayer == 1 && choiceSaved.handoffPending && choiceSaved.g.crown[0] == crown0)
                    vs.acceptHandoff(); val crown1 = (0 until vs.g.H).first { vs.g.owner[it] == 1 && it != vs.g.crown[1] }
                    vs.tapTile(crown1); check(vs.g.crown[1] == crown1 && vs.startingCrownPlayer == -1 && vs.handoffPending && vs.currentPlayer == 0)
                    vs.acceptHandoff(); vs.g.res[0].fill(10); val tile0 = (0 until vs.g.H).first { vs.g.owner[it] == 0 }
                    val firstOrder = com.puresudoku.core.board.VOrder.Muster(tile0, false)
                    vs.order(firstOrder); check(vs.g.orders[0].contains(firstOrder)); vs.cancel(0); check(vs.g.orders[0].isEmpty())
                    vs.buySpell(); check(vs.g.spells[0].sum() == 1 && vs.events.none { it.startsWith("You learned ") })
                    vs.reveal()
                    check(vs.handoffPending && vs.currentPlayer == 1 && !vs.thinking)
                    val vr = VeilguardSession.restore(vs.toJson()); vr.resume(); check(vr.localTwoPlayer && vr.currentPlayer == 1 && vr.handoffPending && !vr.thinking)
                    vr.acceptHandoff(); vr.g.res[1].fill(10); val tile1 = (0 until vr.g.H).first { vr.g.owner[it] == 1 }
                    val secondOrder = com.puresudoku.core.board.VOrder.Muster(tile1, false)
                    vr.order(secondOrder); check(vr.g.orders[1].contains(secondOrder) && vr.g.orders[0].isEmpty())
                    vr.reveal(); check(vr.currentPlayer == 0 && vr.handoffPending && !vr.thinking && vr.g.knights[tile1] > 0)
                    val safeEvents = com.puresudoku.ui.games.realm.localPublicEvents(listOf("You learned Relic", "Ruins! You found a Fireball spell", "Your Crown was moved in secret to the hills", "Your Crown fled to the hills", "Your spy at the forest: 2 knights, 1 decoy", "The rival built a village"))
                    check(safeEvents == listOf("Player 1 learned a spell", "Player 1 found ruins and a spell", "The rival built a village"))
                    for ((kind, twist) in listOf(GameKind.REALMFORGE to Realm.FOG, GameKind.REALM_DRAGON to Realm.WAKE, GameKind.REALM_NIGHT to Realm.NIGHT, GameKind.REALM_ASH to Realm.ASH)) {
                        val variant = RealmSession(kind, Difficulty.EASY, Realm.newGame(twist, Difficulty.EASY, Random(52))); variant.localTwoPlayer = true
                        variant.acceptHandoff(); variant.tapVertex(variant.g.villageSpots(0).first()); variant.tapEdge(variant.g.roadSpots(0).first())
                        check(variant.handoffPending && variant.currentPlayer == 1)
                        val savedVariant = RealmSession.restore(kind, variant.toJson()); savedVariant.resume()
                        check(savedVariant.localTwoPlayer && savedVariant.handoffPending && savedVariant.currentPlayer == 1 && !savedVariant.thinking)
                        savedVariant.acceptHandoff(); savedVariant.tapVertex(savedVariant.g.villageSpots(1).first()); savedVariant.tapEdge(savedVariant.g.roadSpots(1).first())
                        while (savedVariant.g.phase == Realm.SETUP) {
                            if (savedVariant.handoffPending) savedVariant.acceptHandoff()
                            val player = savedVariant.currentPlayer
                            savedVariant.tapVertex(savedVariant.g.villageSpots(player).first()); savedVariant.tapEdge(savedVariant.g.roadSpots(player).first())
                        }
                        check(!savedVariant.thinking && savedVariant.g.phase == Realm.ROLL)
                        if (savedVariant.handoffPending) savedVariant.acceptHandoff()
                        savedVariant.roll(); check(!savedVariant.thinking)
                    }
                    val ash = WisteriaSession(Difficulty.EASY, Wisteria.newAshfall(Difficulty.EASY, Random(53))); ash.localTwoPlayer = true
                    ash.acceptHandoff(); ash.tapTile(ash.g.settleSpots(0).first()); check(ash.handoffPending && ash.currentPlayer == 1)
                    val ashRestored = WisteriaSession.restore(ash.toJson()); ashRestored.resume()
                    check(ashRestored.localTwoPlayer && ashRestored.g.ashfall && ashRestored.currentPlayer == 1 && ashRestored.handoffPending && !ashRestored.thinking)
                    ashRestored.acceptHandoff(); ashRestored.tapTile(ashRestored.g.settleSpots(1).first()); check(!ashRestored.thinking)
                    val ce = CrazyEightsSession(Difficulty.EASY, CrazyEights.newGame(Difficulty.EASY, Random(10))); ce.localTwoPlayer = true
                    check(ce.handoffPending); ce.acceptHandoff()
                    var playable = ce.g.playable(0); while (playable.isEmpty()) { ce.tapDeck(); playable = ce.g.playable(0) }
                    val card = playable.first(); ce.tapCard(card); if (Cards.rank(card) == 8) ce.chooseSuit(0)
                    check(ce.handoffPending && ce.g.turn == 1 && !ce.thinking)
                    val cer = CrazyEightsSession.restore(ce.toJson()); cer.resume(); check(cer.localTwoPlayer && cer.g.turn == 1 && cer.handoffPending && !cer.thinking)
                    val fishGame = com.puresudoku.core.cards.GoFish(2, 0, Random(11))
                    val fishCard1 = Cards.of(7, 0); val fishCard2 = Cards.of(5, 1)
                    fishGame.hands[0] += fishCard1; fishGame.hands[1] += fishCard2
                    fishGame.deck += listOf(Cards.of(3, 0), Cards.of(9, 0))
                    val fish = com.puresudoku.ui.games.cards.GoFishSession(Difficulty.EASY, fishGame); fish.localTwoPlayer = true
                    check(fish.handoffPending); fish.acceptHandoff(); fish.pick(fishCard1); fish.ask(1)
                    check(fish.g.turn == 1 && fish.handoffPending && fish.log.none { it.contains(Cards.label(Cards.of(9, 0))) })
                    val fishRestored = com.puresudoku.ui.games.cards.GoFishSession.restore(fish.toJson())
                    check(fishRestored.localTwoPlayer && fishRestored.handoffPending && fishRestored.currentPlayer == 1)
                    fishRestored.acceptHandoff(); fishRestored.pick(fishCard2); fishRestored.ask(0)
                    check(fishRestored.g.turn == 0 && fishRestored.handoffPending)
                    val soloFishGame = com.puresudoku.core.cards.GoFish(2, 0, Random(30)); soloFishGame.turn = 1
                    soloFishGame.hands[0] += Cards.of(4, 0); soloFishGame.hands[1] += Cards.of(6, 0); soloFishGame.deck += Cards.of(9, 0)
                    val soloFish = com.puresudoku.ui.games.cards.GoFishSession(Difficulty.EASY, soloFishGame); soloFish.step()
                    check(soloFish.version > 0 && !soloFish.localTwoPlayer)
                    val soloCuttleGame = com.puresudoku.core.cards.Cuttle(Random(31)); soloCuttleGame.turn = 1
                    soloCuttleGame.hands[0] += Cards.of(3, 0); soloCuttleGame.hands[1] += Cards.of(6, 1); soloCuttleGame.deck += Cards.of(8, 0)
                    val soloCuttle = com.puresudoku.ui.games.cards.CuttleSession(Difficulty.EASY, soloCuttleGame); soloCuttle.step()
                    check(soloCuttle.version > 0 && !soloCuttle.localTwoPlayer)
                    val cuttleGame = com.puresudoku.core.cards.Cuttle(Random(12))
                    val five = Cards.of(5, 0); val two = Cards.of(2, 1)
                    cuttleGame.hands[0] += listOf(five, Cards.of(6, 0)); cuttleGame.hands[1] += listOf(two, Cards.of(7, 0))
                    cuttleGame.deck += listOf(Cards.of(9, 0), Cards.of(10, 0))
                    val cuttle = com.puresudoku.ui.games.cards.CuttleSession(Difficulty.EASY, cuttleGame); cuttle.localTwoPlayer = true
                    cuttle.acceptHandoff(); cuttle.select(five); cuttle.play(cuttle.options.first { it is com.puresudoku.core.cards.CMove.OneOff })
                    check(cuttle.currentPlayer == 1 && cuttle.handoffPending)
                    val cuttleRestored = com.puresudoku.ui.games.cards.CuttleSession.restore(cuttle.toJson())
                    check(cuttleRestored.localTwoPlayer && cuttleRestored.currentPlayer == 1 && cuttleRestored.handoffPending)
                    cuttleRestored.acceptHandoff(); cuttleRestored.counter(two); check(cuttleRestored.currentPlayer == 0 && cuttleRestored.handoffPending)
                    cuttleRestored.acceptHandoff(); cuttleRestored.counter(null); check(cuttleRestored.currentPlayer == 1 && cuttleRestored.handoffPending)
                    cuttleRestored.acceptHandoff(); cuttleRestored.drawCard(); check(cuttleRestored.currentPlayer == 0 && cuttleRestored.handoffPending)
                    // Exercise Player 2 strategy victory handling using deterministic near-win states.
                    val realmEndGame = Realm.newGame(Realm.FOG, Difficulty.EASY, Random(61)).apply { phase = Realm.MAIN; turn = 1; res[1].fill(10); spells[1][Realm.RELIC] = target }
                    val realmEnd = RealmSession(GameKind.REALMFORGE, Difficulty.EASY, realmEndGame); realmEnd.localTwoPlayer = true; realmEnd.acceptHandoff(); realmEnd.trade(0, 1)
                    check(realmEnd.outcome == Outcome.LOST && realmEnd.lostTitle == "Player 2 wins!")
                    val wEndGame = Wisteria.newGame(Difficulty.EASY, Random(62)).apply { phase = Wisteria.MAIN; turn = 1; res[1].fill(10); spells[1][Wisteria.RELIC] = target }
                    val wEnd = WisteriaSession(Difficulty.EASY, wEndGame); wEnd.localTwoPlayer = true; wEnd.acceptHandoff(); wEnd.trade(0, 1)
                    check(wEnd.outcome == Outcome.LOST && wEnd.lostTitle == "Player 2 wins!")
                    val kEndGame = Kreashun.newGame(Difficulty.EASY, Random(63)).apply { phase = Kreashun.MAIN; turn = 1; res[1].fill(10); spells[1][Kreashun.RELIC] = target }
                    val kEnd = KreashunSession(Difficulty.EASY, kEndGame); kEnd.localTwoPlayer = true; kEnd.acceptHandoff(); kEnd.trade(0, 1)
                    check(kEnd.outcome == Outcome.LOST && kEnd.lostTitle == "Player 2 wins!")
                    val vEndGame = Veilguard.newGame(Difficulty.EASY, Random(64)).apply {
                        phase = Veilguard.PLAN; owner.fill(-1); level.fill(0)
                        val h = (0 until H).first { kind[it] == Veilguard.LAND && it != thief }
                        owner[h] = 1; level[h] = 1; crown[0] = -1; crown[1] = h
                    }
                    val vEnd = VeilguardSession(Difficulty.EASY, vEndGame); vEnd.localTwoPlayer = true; vEnd.planningPlayer = 1
                    vEnd.acceptHandoff(); vEnd.reveal()
                    check(vEnd.outcome == Outcome.LOST && vEnd.lostTitle == "Player 2 wins!")
                    // Complete a real checkmate for the second local player.
                    val mate = ChessSession(Difficulty.EASY, Chess.start()); mate.localTwoPlayer = true
                    for ((from, to) in listOf(53 to 45, 12 to 28, 54 to 38, 3 to 39)) { mate.tap(from); mate.tap(to) }
                    check(mate.outcome == Outcome.LOST && mate.lostTitle == "Player 2 wins!")
                    // Finish each two-seat card game from a legal near-end position.
                    val fishEnd = com.puresudoku.core.cards.GoFish(2, 0, Random(21)); fishEnd.turn = 1
                    fishEnd.books[0] += (1..6); fishEnd.books[1] += (7..12)
                    fishEnd.hands[0] += Cards.of(13, 0); fishEnd.hands[1] += listOf(Cards.of(13, 1), Cards.of(13, 2), Cards.of(13, 3))
                    val fEnd = com.puresudoku.ui.games.cards.GoFishSession(Difficulty.EASY, fishEnd); fEnd.localTwoPlayer = true
                    fEnd.acceptHandoff(); fEnd.pick(Cards.of(13, 1)); fEnd.ask(0)
                    check(fEnd.outcome == Outcome.LOST && fEnd.lostTitle == "Player 2 wins!")
                    val cuttleEnd = com.puresudoku.core.cards.Cuttle(Random(22)); cuttleEnd.turn = 1
                    cuttleEnd.points[1] += com.puresudoku.core.cards.PointCard(Cards.of(10, 0))
                    cuttleEnd.points[1] += com.puresudoku.core.cards.PointCard(Cards.of(9, 0))
                    val lastPoints = Cards.of(2, 0); cuttleEnd.hands[1] += lastPoints; cuttleEnd.hands[0] += Cards.of(3, 1)
                    val cuEnd = com.puresudoku.ui.games.cards.CuttleSession(Difficulty.EASY, cuttleEnd); cuEnd.localTwoPlayer = true
                    cuEnd.acceptHandoff(); cuEnd.select(lastPoints); cuEnd.play(cuEnd.options.first { it is com.puresudoku.core.cards.CMove.Points })
                    check(cuEnd.outcome == Outcome.LOST && cuEnd.lostTitle == "Player 2 wins!")
                    val eightsEnd = CrazyEights(2, 0.0, Random(23)); eightsEnd.turn = 1; eightsEnd.discard += Cards.of(4, 0); eightsEnd.suit = 0
                    val lastEight = Cards.of(4, 1); eightsEnd.hands[1] += lastEight; eightsEnd.hands[0] += Cards.of(5, 1)
                    val eEnd = CrazyEightsSession(Difficulty.EASY, eightsEnd); eEnd.localTwoPlayer = true
                    eEnd.acceptHandoff(); eEnd.tapCard(lastEight)
                    check(eEnd.outcome == Outcome.LOST && eEnd.lostTitle == "Player 2 wins!")
                    "PASS: ten board games, strategy variants, and three card games handoff/save"
                }.getOrElse { "FAIL: ${it.stackTraceToString()}" }
                setContentView(TextView(this).apply { text = result; textSize = 20f }); return
            }
            "wisteria-local" -> {
                val g = Wisteria.newGame(Difficulty.EASY, Random(44))
                val s = WisteriaSession(Difficulty.EASY, g); s.localTwoPlayer = true
                setContent { PureSudokuTheme { WisteriaUi(s, null) } }; return
            }
            "wisteria" -> {
                val g = Wisteria.newGame(Difficulty.EASY, Random(44))
                val homes = (0 until g.H).filter { g.passable(it) && it != g.lair }
                for (p in 0..1) { val h = homes[if (p == 0) 2 else homes.size - 3]; g.owner[h] = p; g.level[h] = 1 }
                for (p in 0..1) g.wallSpots(p).take(3).forEach { (a, b) -> g.wall[g.side(a, b)] = p }
                setContent { PureSudokuTheme { WisteriaUi(WisteriaSession(Difficulty.EASY, g), null) } }; return
            }
            "kreashun" -> {
                val g = Kreashun.newGame(Difficulty.EASY, Random(44))
                for (p in 0..1) { val h = if (p == 0) 2 else g.H - 3; g.owner[h] = p
                    g.map.hexNbrs[h].take(3).forEach { n -> val edge = g.side(h, n); if (edge >= 0) g.wall[edge] = p }
                }
                setContent { PureSudokuTheme { KreashunUi(KreashunSession(Difficulty.EASY, g), null) } }; return
            }
            "veilguard" -> {
                val g = Veilguard.newGame(Difficulty.EASY, Random(44))
                for (p in 0..1) { val h = if (p == 0) 2 else g.H - 3; g.owner[h] = p; g.level[h] = 1 }
                for (p in 0..1) for (h in (0 until g.H).filter { g.owner[it] == p }.take(1))
                    g.map.hexNbrs[h].take(3).forEach { n -> val edge = g.side(h, n); if (edge >= 0) g.wall[edge] = p }
                setContent { PureSudokuTheme { VeilguardUi(VeilguardSession(Difficulty.EASY, g), null) } }; return
            }
        }
        val conquest = intent.getBooleanExtra("conquest", false)
        val catalog = intent.getBooleanExtra("catalog", false)
        if (conquest) {
            val g = AegConquest.newGame(Difficulty.EASY, Random(44))
            g.rivalLand.fill(-1); g.road.fill(false); g.revealed.fill(true); g.terrain.fill(AegConquest.PLAINS)
            g.building.fill(AegConquest.NONE); g.building[g.capital] = AegConquest.PORT
            val tiles = (0 until g.H).filter { it != g.capital }.sortedBy { g.hexDist(it, g.capital) }
            AegStewardship.WONDERS.forEachIndexed { n, w -> if (!catalog) { g.building[tiles[n]] = w; g.workers[tiles[n]] = AegConquest.WORKERS[w] } }
            for (target in tiles.take(8)) {
                var h = target
                while (h != g.capital) { val next = g.map.hexNbrs[h].minBy { g.hexDist(it, g.capital) }; g.road[g.side(h, next)] = true; h = next }
            }
            g.building[tiles[6]] = AegConquest.TOWN; g.pop = 80; g.res.fill(100)
            val session = AegConquestSession(Difficulty.EASY, g); session.selected = if (intent.getBooleanExtra("buildingDetails", false)) tiles[0] else tiles[7]
            setContent { PureSudokuTheme { AegConquestUi(session, null) } }
        } else {
            val g = Aegwaspell.newGame(Difficulty.EASY, Random(44))
            g.rivalLand.fill(-1); g.road.fill(false); g.revealed.fill(true); g.terrain.fill(Aegwaspell.PLAINS)
            g.building.fill(Aegwaspell.NONE); g.building[g.capital] = Aegwaspell.PORT
            val tiles = (0 until g.H).filter { it != g.capital }.sortedBy { g.hexDist(it, g.capital) }
            AegStewardship.WONDERS.forEachIndexed { n, w -> if (!catalog) g.building[tiles[n]] = w }
            for (target in tiles.take(8)) {
                var h = target
                while (h != g.capital) { val next = g.map.hexNbrs[h].minBy { g.hexDist(it, g.capital) }; g.road[g.side(h, next)] = true; h = next }
            }
            g.building[tiles[6]] = Aegwaspell.TOWN; g.pop = 80; g.res.fill(100)
            val session = AegwaspellSession(Difficulty.EASY, g); session.selected = if (intent.getBooleanExtra("buildingDetails", false)) tiles[0] else tiles[7]
            setContent { PureSudokuTheme { AegwaspellUi(session, null) } }
        }
    }
}
