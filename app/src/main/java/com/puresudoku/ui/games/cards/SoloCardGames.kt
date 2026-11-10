package com.puresudoku.ui.games.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.ScoreRules
import com.puresudoku.core.Scoring
import com.puresudoku.core.cards.Cards
import com.puresudoku.core.cards.DungeonSolitaire
import com.puresudoku.core.cards.DungeonTurn
import com.puresudoku.core.cards.JOKER
import com.puresudoku.core.cards.Jailbreak
import com.puresudoku.core.cards.Regicide
import com.puresudoku.core.cards.Scoundrel
import com.puresudoku.core.ints
import com.puresudoku.core.toJson
import com.puresudoku.ui.components.CARD_RED
import com.puresudoku.ui.components.CardSlot
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.components.HSpace
import com.puresudoku.ui.components.PlayingCard
import com.puresudoku.ui.components.StatusBar
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.MiddleStat
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.theme.LocalBoardColors
import com.puresudoku.ui.theme.Poppins
import com.puresudoku.ui.tutorial.DemoStage
import com.puresudoku.ui.tutorial.DemoTargets
import com.puresudoku.ui.tutorial.TutorialStep
import com.puresudoku.ui.tutorial.demoTarget
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import kotlin.random.Random

/** A card that pops in when it first appears, and shakes on [fx]. */
@Composable
fun PopCard(card: Int, faceUp: Boolean, width: Dp, modifier: Modifier = Modifier, highlight: Boolean = false, dim: Boolean = false,
            fx: CardFx? = null, lift: Boolean = false, art: Int = 0, onTap: (() -> Unit)? = null) {
    val pop = remember(card) { Animatable(0.5f) }
    LaunchedEffect(card) { pop.animateTo(1f, spring(0.55f, Spring.StiffnessMediumLow)) }
    val shake = rememberShake(fx, card)
    PlayingCard(
        card, faceUp, width,
        modifier
            .offset { IntOffset(shake.dp.roundToPx(), if (lift) (-10).dp.roundToPx() else 0) }
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f) }
            .then(if (onTap != null) Modifier.clickable(remember { MutableInteractionSource() }, null) { onTap() } else Modifier),
        highlight = highlight, dim = dim, error = fx?.card == card && shake != 0f, art = art,
    )
}

private fun JSONArray.ints(): MutableList<Int> = MutableList(length()) { getInt(it) }
private fun List<Int>.json() = toIntArray().toJson()

@Composable
private fun Caption(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text, fontSize = 12.sp, color = color, textAlign = TextAlign.Center, lineHeight = 14.sp)

// =====================================================================
// Scoundrel
// =====================================================================

class ScoundrelSession(difficulty: Difficulty, val g: Scoundrel) : Session(GameKind.SCOUNDREL, difficulty) {
    val cfg = Scoundrel.config(difficulty)
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    var version by mutableIntStateOf(0); private set
    var choosing by mutableIntStateOf(-1); private set
    var message by mutableStateOf("Choose three of the four cards in this room."); private set
    var errorFx by mutableStateOf<CardFx?>(null); private set

    init { score = g.health }
    override val usesStrikes get() = false
    override val middleStat get() = MiddleStat.Counter("Health", "${g.health}/${g.maxHealth}")
    override val progress: Float get() = 1f - (g.dungeon.size + g.room.size) / 44f
    override val wonTitle get() = "Dungeon cleared!"
    override val lostTitle get() = "Slain in the dungeon"
    override val lostMessage get() = "Final score ${g.finalScore()}. This game's save slot has been cleared."

    fun tap(c: Int) {
        if (outcome != Outcome.PLAYING || c !in g.room) return
        if (Scoundrel.isMonster(c) && g.weapon >= 0) {
            if (!g.weaponUsable(c)) {
                message = "Your weapon is too dull for a ${Scoundrel.value(c)} (last kill ${Scoundrel.value(g.slain.last())}). Fighting barehanded."
                resolve(c, false); return
            }
            choosing = c; version++; return
        }
        resolve(c, true)
    }

    fun resolve(c: Int, weapon: Boolean) {
        choosing = -1
        if (Scoundrel.isPotion(c) && g.potionUsed) errorFx = cardFx(c)
        message = g.resolve(c, weapon)
        score = g.health
        version++
        when {
            g.dead -> { score = 0; finish(false) }
            g.cleared -> {
                val fs = g.finalScore()
                score = fs * cfg.basePoints
                val b = Scoring.finish(rules, elapsedMs, 0)
                finish(true, listOf(BonusLine("Time bonus", b.timeBonus)))
            }
            else -> changed()
        }
    }

    fun avoid() {
        if (outcome != Outcome.PLAYING) return
        if (!g.canAvoid()) {
            nudge(when {
                g.resolvedInRoom > 0 -> "You can only avoid a room before taking anything from it"
                g.avoidedLast -> "You can't avoid two rooms in a row"
                cfg.avoidLimit >= 0 && g.avoids >= cfg.avoidLimit -> "No avoids left on this level"
                else -> "You can't avoid this room"
            }); return
        }
        g.avoid(); choosing = -1; message = "You slip past. Those cards go to the bottom of the dungeon."; version++; changed()
    }

    fun cancelChoice() { choosing = -1; version++ }

    override fun saveState(): JSONObject = JSONObject()
        .put("dungeon", g.dungeon.json()).put("room", g.room.json()).put("health", g.health).put("weapon", g.weapon)
        .put("slain", g.slain.json()).put("potion", g.potionUsed).put("avoidedLast", g.avoidedLast).put("avoids", g.avoids)
        .put("resolved", g.resolvedInRoom).put("last", g.lastCard)

    companion object {
        fun restore(o: JSONObject): ScoundrelSession {
            val d = Difficulty.valueOf(o.getString("difficulty")); val st = o.getJSONObject("state"); val cfg = Scoundrel.config(d)
            val g = Scoundrel(cfg.health, cfg.avoidLimit, st.getJSONArray("dungeon").ints(), st.getJSONArray("room").ints(), st.getInt("health"),
                st.getInt("weapon"), st.getJSONArray("slain").ints(), st.getBoolean("potion"), st.getBoolean("avoidedLast"), st.getInt("avoids"),
                st.getInt("resolved"), st.getInt("last"))
            return ScoundrelSession(d, g).also { it.restoreCommon(o) }
        }
    }
}

object ScoundrelDef : GameDef {
    /** A King or Ace monster in the room turns the hall into the beast's lair. */
    override fun background(session: Session): Int {
        val s = session as ScoundrelSession
        @Suppress("UNUSED_VARIABLE") val v = s.version
        return if (s.g.room.any { Scoundrel.isMonster(it) && Scoundrel.value(it) >= 13 }) com.puresudoku.R.drawable.bg_scoundrel_boss else com.puresudoku.R.drawable.bg_scoundrel
    }
    override val kind = GameKind.SCOUNDREL
    private val rng = Random(SecureRandom().nextLong())
    override fun generate(d: Difficulty): Session = ScoundrelSession(d, synchronized(rng) { Scoundrel.newGame(d, rng) })
    override fun restore(o: JSONObject): Session = ScoundrelSession.restore(o)
    override fun hint(d: Difficulty) = "♣♠ are monsters, ♦ weapons, ♥ potions. Resolve three cards per room. Reach the end of the deck alive."
    override fun levelInfo(d: Difficulty) = Scoundrel.config(d).let { "${it.health} health" + if (it.avoidLimit >= 0) " · ${it.avoidLimit} avoids" else "" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Extra health to learn the ropes."
        Difficulty.MEDIUM -> "A little extra health."
        Difficulty.HARD -> "The original rules."
        Difficulty.EXPERT -> "Only four rooms can be avoided."
        Difficulty.MASTER -> "Only two rooms can be avoided."
        Difficulty.EXTREME -> "18 health and no running away."
    }
    @Composable override fun Play(session: Session) = ScoundrelTable(session as ScoundrelSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    private fun demo(): ScoundrelSession {
        val c = Cards::of
        val g = Scoundrel(20, -1)
        g.room += listOf(c(5, 1), c(9, 3), c(7, 2), c(3, 0)); g.dungeon += listOf(c(10, 0), c(4, 1), c(6, 3))
        return ScoundrelSession(Difficulty.HARD, g)
    }

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Rooms of four", "Each room shows four cards. Deal with any three of them; the fourth carries into the next room. Spades and clubs are monsters, diamonds are weapons, hearts are healing potions.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = { s = demo(); wait(600); highlight(listOf("room"), 1600) }) { t -> ScoundrelTable(s, t) }
        },
        TutorialStep("Weapons and monsters", "Pick up a weapon, then fight: a monster hits you for its value minus your weapon's. A weapon can then only fight monsters weaker than the last one it beat. Or fight barehanded and take the full hit.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                tap("c-${Cards.of(5, 1)}") { s.tap(Cards.of(5, 1)) }
                wait(500)
                tap("c-${Cards.of(9, 3)}") { s.tap(Cards.of(9, 3)) }
                tap("use-weapon") { s.resolve(Cards.of(9, 3), true) }
                wait(1400)
            }) { t -> ScoundrelTable(s, t) }
        },
        TutorialStep("Heal and survive", "Drink one potion per room to heal (up to your maximum). You may avoid a whole room, but never two in a row. Reach the bottom of the dungeon alive to win.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                highlight(listOf("avoid"), 1000)
                tap("c-${Cards.of(7, 2)}") { s.tap(Cards.of(7, 2)) }
                wait(1400)
            }) { t -> ScoundrelTable(s, t) }
        },
    )
}

@Composable
private fun ScoundrelTable(s: ScoundrelSession, targets: DemoTargets?) {
    com.puresudoku.ui.components.ProvideCardTheme(com.puresudoku.ui.components.CardThemes.SCOUNDREL) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val colors = LocalBoardColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("♥ ${g.health}", fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = CARD_RED)
            HSpace(10)
            LinearProgressIndicator(progress = { g.health / g.maxHealth.toFloat() }, color = CARD_RED,
                modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)))
            HSpace(10)
            Text("${g.dungeon.size} left", fontSize = 13.sp, color = colors.note)
        }
        Spacer(Modifier.height(14.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cw = min((maxWidth - 30.dp) / 4, 92.dp)
            Row(Modifier.fillMaxWidth().demoTarget(targets, "room"), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                for (c in g.room) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PopCard(c, true, cw, Modifier.demoTarget(targets, "c-$c"), highlight = s.choosing == c, fx = s.errorFx, art = CardArt.scoundrel(c)) { s.tap(c) }
                    Spacer(Modifier.height(4.dp))
                    Caption(when {
                        Scoundrel.isPotion(c) -> "Potion +${Scoundrel.value(c)}"
                        Scoundrel.isWeapon(c) -> "Weapon ${Scoundrel.value(c)}"
                        else -> "Monster ${Scoundrel.value(c)}"
                    })
                }
            }
        }
        if (s.choosing >= 0) {
            val m = s.choosing
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { s.resolve(m, true) }, modifier = Modifier.demoTarget(targets, "use-weapon")) { Text("Weapon (take ${g.damage(m, true)})") }
                OutlinedButton(onClick = { s.resolve(m, false) }) { Text("Barehanded (take ${g.damage(m, false)})") }
            }
        }
        Spacer(Modifier.height(12.dp))
        TableMessage(s.message)
        Spacer(Modifier.height(14.dp))
        Text("WEAPON", fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = colors.note)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.height(cardHeightFor(78.dp) + 4.dp)) {
            if (g.weapon < 0) CardSlot(78.dp, label = "—")
            else {
                PopCard(g.weapon, true, 78.dp, art = CardArt.scoundrel(g.weapon))
                g.slain.forEachIndexed { i, m -> PopCard(m, true, 78.dp, Modifier.offset(x = 26.dp * (i + 1)), art = CardArt.scoundrel(m)) }
            }
        }
        if (g.weapon >= 0) Caption(if (g.slain.isEmpty()) "Fresh: can fight anything" else "Can fight monsters below ${Scoundrel.value(g.slain.last())}")
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = s::avoid, enabled = s.outcome == Outcome.PLAYING, modifier = Modifier.demoTarget(targets, "avoid")) {
            Text(if (g.canAvoid()) "Avoid this room" else "Can't avoid now")
        }
        Spacer(Modifier.height(12.dp))
    }
    }
}

private fun cardHeightFor(w: Dp) = w * 1.4f

// =====================================================================
// Regicide (solo)
// =====================================================================

class RegicideSession(difficulty: Difficulty, val g: Regicide) : Session(GameKind.REGICIDE, difficulty) {
    val cfg = Regicide.config(difficulty)
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    var version by mutableIntStateOf(0); private set
    val selected = mutableStateListOf<Int>()
    var message by mutableStateOf("Play cards to damage the enemy. Its attack hits back if it survives."); private set
    var hitFx by mutableStateOf<CardFx?>(null); private set

    override val usesStrikes get() = false
    override val middleStat get() = MiddleStat.Counter("Royals", "${g.defeated}/12")
    override val progress: Float get() = g.defeated / 12f
    override val wonTitle get() = "Long live the Queen!"
    override val lostTitle get() = "Defeated"
    override val lostMessage get() = "You beat ${g.defeated} of 12 royals. This game's save slot has been cleared."

    fun toggle(c: Int) {
        if (outcome != Outcome.PLAYING) return
        if (c in selected) selected.remove(c) else selected.add(c)
        version++
    }

    val preview: String
        get() {
            if (g.suffer > 0) {
                val sum = selected.sumOf { Regicide.value(it) }
                return "Discard cards worth at least ${g.suffer} (selected $sum)"
            }
            if (selected.isEmpty()) return ""
            if (!g.valid(selected)) return "Not a legal play: one card, an Ace with one card, or matching numbers adding to 10 or less"
            val atk = selected.sumOf { Regicide.value(it) }
            val suits = selected.map { Cards.suit(it) }.toSet() - g.immune
            val dmg = if (0 in suits) atk * 2 else atk
            val parts = suits.sorted().filter { it != 0 }.map { "${Cards.suitSymbol(it)} ${Regicide.power(it)} $atk" }
            return "Attack $atk → $dmg damage" + if (parts.isNotEmpty()) " · " + parts.joinToString(" · ") else ""
        }

    fun play() {
        if (outcome != Outcome.PLAYING || g.suffer > 0) return
        if (!g.valid(selected)) { selected.lastOrNull()?.let { hitFx = cardFx(it) }; nudge("That's not a legal play"); return }
        val before = g.defeated
        message = g.play(selected.toList())
        selected.clear()
        score += if (g.defeated > before) rules.basePoints * 10 else rules.basePoints
        after()
    }

    fun yieldTurn() {
        if (outcome != Outcome.PLAYING || g.suffer > 0) return
        g.yieldTurn(); selected.clear(); message = "You hold back. The enemy strikes for ${g.suffer}."
        after()
    }

    fun takeHit() {
        if (outcome != Outcome.PLAYING || g.suffer == 0) return
        if (selected.sumOf { Regicide.value(it) } < g.suffer) { nudge("Those cards are only worth ${selected.sumOf { Regicide.value(it) }} — you need ${g.suffer}"); return }
        g.takeHit(selected.toList()); selected.clear(); message = "You weather the blow."
        after()
    }

    fun jester() {
        if (outcome != Outcome.PLAYING) return
        if (g.jesters == 0) { nudge("No jesters left"); return }
        g.useJester(); selected.clear(); message = "A jester shuffles your fortunes: a fresh hand."
        after()
    }

    private fun after() {
        version++
        when {
            g.won -> {
                val grade = cfg.jesters - g.jestersUsed
                finish(true, listOf(BonusLine("Unused jesters", grade * rules.basePoints * 20), BonusLine("Time bonus", Scoring.finish(rules, elapsedMs, 0).timeBonus)))
            }
            g.lost -> finish(false)
            else -> changed()
        }
    }

    override fun saveState(): JSONObject = JSONObject()
        .put("castle", g.castle.json()).put("tavern", g.tavern.json()).put("discard", g.discard.json()).put("hand", g.hand.json())
        .put("played", g.played.json()).put("enemy", g.enemy).put("damage", g.damage).put("shield", g.shield).put("suffer", g.suffer)
        .put("jesters", g.jesters).put("used", g.jestersUsed).put("defeated", g.defeated)

    companion object {
        fun restore(o: JSONObject): RegicideSession {
            val d = Difficulty.valueOf(o.getString("difficulty")); val st = o.getJSONObject("state"); val cfg = Regicide.config(d)
            val g = Regicide(cfg.hand, st.getInt("jesters"), Random(SecureRandom().nextLong()))
            g.castle += st.getJSONArray("castle").ints(); g.tavern += st.getJSONArray("tavern").ints(); g.discard += st.getJSONArray("discard").ints()
            g.hand += st.getJSONArray("hand").ints(); g.played += st.getJSONArray("played").ints()
            g.enemy = st.getInt("enemy"); g.damage = st.getInt("damage"); g.shield = st.getInt("shield"); g.suffer = st.getInt("suffer")
            g.jestersUsed = st.getInt("used"); g.defeated = st.getInt("defeated")
            return RegicideSession(d, g).also { it.restoreCommon(o) }
        }
    }
}

object RegicideDef : GameDef {
    /** Gatehouse for the Jacks, royal hall for the Queens, throne room for the Kings. */
    override fun background(session: Session): Int {
        val s = session as RegicideSession
        @Suppress("UNUSED_VARIABLE") val v = s.version
        return when (if (s.g.enemy >= 0) Cards.rank(s.g.enemy) else 13) {
            11 -> com.puresudoku.R.drawable.bg_regicide_jack
            12 -> com.puresudoku.R.drawable.bg_regicide_queen
            else -> com.puresudoku.R.drawable.bg_regicide_king
        }
    }
    override val kind = GameKind.REGICIDE
    private val rng = Random(SecureRandom().nextLong())
    override fun generate(d: Difficulty): Session = RegicideSession(d, synchronized(rng) { Regicide.newGame(d, rng) })
    override fun restore(o: JSONObject): Session = RegicideSession.restore(o)
    override fun hint(d: Difficulty) = "Select cards and Play to attack. ♥ heals your deck, ♦ draws, ♣ doubles damage, ♠ shields. Survive each counterattack."
    override fun levelInfo(d: Difficulty) = Regicide.config(d).let { "hand ${it.hand} · ${it.jesters} jester${if (it.jesters == 1) "" else "s"}" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Bigger hand, three jesters."
        Difficulty.MEDIUM -> "The official solo rules."
        Difficulty.HARD -> "Only one jester."
        Difficulty.EXPERT -> "No jesters at all."
        Difficulty.MASTER -> "Smaller hand, one jester."
        Difficulty.EXTREME -> "Smaller hand, no jesters."
    }
    @Composable override fun Play(session: Session) = RegicideTable(session as RegicideSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    private fun demo(): RegicideSession {
        val c = Cards::of
        val g = Regicide(8, 2, Random(1))
        g.castle += listOf(c(11, 0), c(11, 2), c(12, 1)); g.enemy = c(11, 3)
        g.hand += listOf(c(8, 0), c(4, 1), c(4, 2), c(9, 3), c(1, 0), c(6, 1), c(2, 0), c(10, 2))
        g.tavern += (0 until 40).map { (it * 7) % 52 }.filter { Cards.rank(it) in 2..10 && it !in g.hand }.distinct()
        return RegicideSession(Difficulty.MEDIUM, g)
    }

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Attack the royal", "Twelve corrupted royals wait: Jacks, then Queens, then Kings. Select a card and tap Play to deal its value in damage. You can play matching numbers together (up to 10), or an Ace with one other card.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                tap("h-${Cards.of(4, 1)}") { s.toggle(Cards.of(4, 1)) }
                tap("h-${Cards.of(4, 2)}") { s.toggle(Cards.of(4, 2)) }
                wait(700)
                tap("play") { s.play() }
                wait(1400)
            }) { t -> RegicideTable(s, t) }
        },
        TutorialStep("Suit powers", "Each suit has a power: ♥ shuffles discards back into your deck, ♦ draws cards, ♣ doubles damage and ♠ shields you from the counterattack. A royal ignores the power of its own suit.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                highlight(listOf("enemy"), 1200)
                tap("h-${Cards.of(8, 0)}") { s.toggle(Cards.of(8, 0)) }
                wait(500); highlight(listOf("preview"), 1300)
                tap("play") { s.play() }
                wait(1400)
            }) { t -> RegicideTable(s, t) }
        },
        TutorialStep("Survive the counterattack", "If the royal survives, it strikes: discard cards worth at least its attack, or you lose. Defeat it with exactly its health and it joins your deck. Jesters give you a fresh hand.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                tap("h-${Cards.of(2, 0)}") { s.toggle(Cards.of(2, 0)) }
                tap("play") { s.play() }
                wait(700)
                tap("h-${Cards.of(10, 2)}") { s.toggle(Cards.of(10, 2)) }
                tap("take") { s.takeHit() }
                wait(1400)
            }) { t -> RegicideTable(s, t) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegicideTable(s: RegicideSession, targets: DemoTargets?) {
    com.puresudoku.ui.components.ProvideCardTheme(com.puresudoku.ui.components.CardThemes.REGICIDE) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val colors = LocalBoardColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        if (g.enemy >= 0) Row(Modifier.demoTarget(targets, "enemy"), verticalAlignment = Alignment.CenterVertically) {
            PopCard(g.enemy, true, 96.dp, art = CardArt.regicide(g.enemy))
            HSpace(16)
            Column(Modifier.widthIn(max = 200.dp)) {
                Text("${Cards.rankName(Cards.rank(g.enemy))} of ${Cards.suitName(Cards.suit(g.enemy))}", fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Text("Health ${g.health}/${Regicide.health(g.enemy)}", fontSize = 13.sp)
                LinearProgressIndicator(progress = { g.health / Regicide.health(g.enemy).toFloat() }, color = CARD_RED,
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)))
                Spacer(Modifier.height(4.dp))
                Text("Attack ${Regicide.attack(g.enemy)}" + (if (g.shield > 0) " − ${g.shield} shield = ${g.enemyAttack}" else ""), fontSize = 13.sp)
                Text("Immune to ${Cards.suitSymbol(g.immune)} ${Regicide.power(g.immune)}", fontSize = 13.sp, color = colors.note)
            }
        }
        Spacer(Modifier.height(8.dp))
        Caption("Royals left ${g.castle.size + (if (g.enemy >= 0) 1 else 0)} · Deck ${g.tavern.size} · Discard ${g.discard.size} · Jesters " + "★".repeat(g.jesters))
        if (g.played.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy((-24).dp)) { for (c in g.played) PopCard(c, true, 52.dp, art = CardArt.regicide(c)) }
        }
        Spacer(Modifier.height(10.dp))
        TableMessage(s.message)
        Spacer(Modifier.height(10.dp))
        val prev = s.preview
        Text(prev.ifEmpty { if (g.suffer > 0) "" else "Select cards to attack" }, fontSize = 14.sp, textAlign = TextAlign.Center,
            color = if (g.suffer > 0) colors.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.demoTarget(targets, "preview").padding(horizontal = 8.dp))
        Spacer(Modifier.height(10.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in g.hand.sortedBy { Regicide.value(it) * 4 + Cards.suit(it) }) {
                PopCard(c, true, 58.dp, Modifier.demoTarget(targets, "h-$c"), highlight = c in s.selected, lift = c in s.selected, fx = s.hitFx, art = CardArt.regicide(c)) { s.toggle(c) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (g.suffer > 0) {
                Button(onClick = s::takeHit, modifier = Modifier.demoTarget(targets, "take")) { Text("Discard to survive") }
            } else {
                Button(onClick = s::play, enabled = s.selected.isNotEmpty(), modifier = Modifier.demoTarget(targets, "play")) { Text("Play") }
                OutlinedButton(onClick = s::yieldTurn) { Text("Yield") }
            }
            if (g.jesters > 0) OutlinedButton(onClick = s::jester) { Text("Jester ★") }
        }
        Spacer(Modifier.height(12.dp))
    }
    }
}

// =====================================================================
// Dungeon Solitaire: Tomb of Four Kings
// =====================================================================

class DungeonSession(difficulty: Difficulty, val g: DungeonSolitaire) : Session(GameKind.DUNGEON_SOLITAIRE, difficulty) {
    val cfg = DungeonSolitaire.config(difficulty)
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    var version by mutableIntStateOf(0); private set

    override val usesStrikes get() = false
    override val middleStat get() = MiddleStat.Counter("Kings", "${g.kings}/4")
    override val progress: Float get() = if (g.retreating) 0.5f + 0.5f * g.retreat.size / g.retreatGoal.coerceAtLeast(1) else minOf(0.5f, g.delve.size / 12f)
    override val wonTitle get() = if (g.kings == 4) "All four kings!" else "Escaped the tomb!"
    override val lostTitle get() = when { g.dead -> "Fallen in the tomb"; g.lostInDark -> "Lost in the dark"; else -> "Not enough kings" }
    override val lostMessage get() = if (g.escaped) "You escaped with ${g.kings} king${if (g.kings == 1) "" else "s"}, but this level needs ${cfg.kings}." else "Treasure is lost with you. This game's save slot has been cleared."

    private fun after() {
        version++
        score = g.treasure * rules.basePoints / 2
        when {
            g.escaped && g.wonGame -> finish(true, listOf(BonusLine("Kings bonus", g.kings * rules.basePoints * 10), BonusLine("Time bonus", Scoring.finish(rules, elapsedMs, 0).timeBonus)))
            g.over -> finish(false)
            else -> changed()
        }
    }

    fun flip() { if (outcome == Outcome.PLAYING) { g.flip(); after() } }
    fun turnBack() { if (outcome == Outcome.PLAYING && g.choosing) { g.turnBack(); after() } }
    fun skill() { if (outcome == Outcome.PLAYING) { g.useSkill(); after() } }
    fun drop(c: Int) { if (outcome == Outcome.PLAYING) { g.dropTreasure(c); after() } }

    override fun saveState(): JSONObject {
        fun turns(l: List<DungeonTurn>) = JSONArray().also { a -> l.forEach { t -> a.put(JSONObject().put("c", t.cards.json()).put("e", t.encounter).put("r", t.result)) } }
        return JSONObject().put("deck", g.deck.json()).put("hp", g.hp).put("torches", g.torches).put("hand", g.hand.json())
            .put("delve", turns(g.delve)).put("retreat", turns(g.retreat)).put("retreating", g.retreating).put("goal", g.retreatGoal)
            .put("log", JSONArray(g.log.takeLast(6)))
    }

    companion object {
        fun restore(o: JSONObject): DungeonSession {
            val d = Difficulty.valueOf(o.getString("difficulty")); val st = o.getJSONObject("state"); val cfg = DungeonSolitaire.config(d)
            val g = DungeonSolitaire(cfg.hp, cfg.kings, Random(SecureRandom().nextLong()))
            g.deck += st.getJSONArray("deck").ints(); g.hp = st.getInt("hp"); g.torches = st.getInt("torches"); g.hand += st.getJSONArray("hand").ints()
            fun turns(a: JSONArray) = List(a.length()) { k -> a.getJSONObject(k).let { DungeonTurn(it.getJSONArray("c").ints(), it.getInt("e"), it.getInt("r")) } }
            g.delve += turns(st.getJSONArray("delve")); g.retreat += turns(st.getJSONArray("retreat"))
            g.retreating = st.getBoolean("retreating"); g.retreatGoal = st.getInt("goal")
            st.optJSONArray("log")?.let { a -> for (k in 0 until a.length()) g.log += a.getString(k) }
            return DungeonSession(d, g).also { it.restoreCommon(o) }
        }
    }
}

object DungeonDef : GameDef {
    override fun background(session: Session): Int {
        val s = session as DungeonSession
        @Suppress("UNUSED_VARIABLE") val v = s.version
        return if (s.g.retreating) com.puresudoku.R.drawable.bg_tomb_escape else com.puresudoku.R.drawable.bg_tomb
    }
    override val kind = GameKind.DUNGEON_SOLITAIRE
    private val rng = Random(SecureRandom().nextLong())
    override fun generate(d: Difficulty): Session = DungeonSession(d, synchronized(rng) { DungeonSolitaire.newGame(d, rng) })
    override fun restore(o: JSONObject): Session = DungeonSession.restore(o)
    override fun hint(d: Difficulty) = "Turn cards to face what lies ahead. ♠ monsters, ♦ traps, ♣ doors. Find the four Kings, then turn back and escape."
    override fun levelInfo(d: Difficulty) = DungeonSolitaire.config(d).let { "${it.hp} health · escape with ${it.kings}+ kings" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Extra health; just get out alive."
        Difficulty.MEDIUM -> "A little extra health; escape to win."
        Difficulty.HARD -> "Official health; bring back a king."
        Difficulty.EXPERT -> "Escape with two kings."
        Difficulty.MASTER -> "Escape with three kings."
        Difficulty.EXTREME -> "All four kings or nothing."
    }
    @Composable override fun Play(session: Session) = DungeonTable(session as DungeonSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    private fun demo(): DungeonSession {
        val c = Cards::of
        val g = DungeonSolitaire(9, 0, Random(2))
        // top of deck is the end of the list: a monster 6, then an action 4 (2 damage), then a 9 (slain), then a King
        g.deck += listOf(c(5, 1), c(3, 0), c(13, 0), c(9, 0), c(4, 1), c(6, 3))
        return DungeonSession(Difficulty.EASY, g)
    }

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Turn the cards", "Each turn, flip cards until you meet an encounter: ♠ monster, ♦ trap or ♣ door. The cards after it are your actions: one at least as high beats it. Lower cards hurt you.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                tap("flip") { s.flip() }; wait(600)
                tap("flip") { s.flip() }; wait(600)
                tap("flip") { s.flip() }; wait(1400)
            }) { t -> DungeonTable(s, t) }
        },
        TutorialStep("Treasure, torches, skills", "Win an encounter to keep its treasure: ♦ cards and the Kings (tomb hoards). Aces are torches burning out; the fourth leaves you lost in the dark. Jacks are skills, Queens divine favours.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); repeat(3) { s.flip() }
                wait(500); highlight(listOf("stats"), 1500)
            }) { t -> DungeonTable(s, t) }
        },
        TutorialStep("Escape", "Between turns, delve deeper or turn back. Escaping takes one fewer turn than you delved. Die or run out of light and the treasure is lost; escape with enough Kings to win.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); repeat(3) { s.flip() }
                wait(500); highlight(listOf("back"), 1400)
            }) { t -> DungeonTable(s, t) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DungeonTable(s: DungeonSession, targets: DemoTargets?) {
    com.puresudoku.ui.components.ProvideCardTheme(com.puresudoku.ui.components.CardThemes.TOMB) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val colors = LocalBoardColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().demoTarget(targets, "stats"), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("HEALTH", "♥ ${g.hp}", CARD_RED)
            Stat("TORCHES", "🔥".repeat(maxOf(0, 4 - g.torches - 1)) + "·".repeat(minOf(3, g.torches)), colors.given)
            Stat("KINGS", "${g.kings}/4", colors.given)
            Stat("TREASURE", "${g.treasure}", colors.given)
        }
        Spacer(Modifier.height(10.dp))
        Caption(if (g.retreating) "Retreat: ${g.retreat.size} of ${g.retreatGoal} turns to escape" else "Delved ${g.delve.size} turn${if (g.delve.size == 1) "" else "s"} deep")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (t in g.delve + g.retreat) {
                val e = t.encounter
                Box(Modifier.size(28.dp, 38.dp).clip(RoundedCornerShape(5.dp)).background(when (t.result) { 1 -> colors.sameDigit; -1 -> colors.errorBg; else -> colors.related }),
                    contentAlignment = Alignment.Center) {
                    Text(if (e >= 0) Cards.label(e) else "…", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.given)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val cur = g.current ?: (if (g.retreating) g.retreat else g.delve).lastOrNull()
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
            for (c in cur?.cards.orEmpty()) PopCard(c, true, 66.dp, highlight = c == cur?.encounter, art = CardArt.dungeon(c, c == cur?.encounter))
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            for (line in g.log.takeLast(3)) Text(line, fontSize = 13.sp, textAlign = TextAlign.Center, color = if (line == g.log.lastOrNull()) colors.given else colors.note)
        }
        Spacer(Modifier.height(10.dp))
        if (g.hand.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in g.hand) Row(
                    Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface).padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(CardArt.dungeon(c, false).takeIf { it != 0 } ?: CardArt.JESTER), null, Modifier.size(20.dp))
                    HSpace(4)
                    Text(if (c == JOKER) "Scroll of Light" else if (Cards.rank(c) == 11) DungeonSolitaire.skillName(c) else Cards.label(c), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (g.choosing) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = s::flip, modifier = Modifier.demoTarget(targets, "flip")) { Text("Delve deeper") }
                OutlinedButton(onClick = s::turnBack, modifier = Modifier.demoTarget(targets, "back")) { Text("Turn back (${maxOf(0, g.delve.size - 1)} to escape)") }
            }
        } else {
            Button(onClick = s::flip, enabled = s.outcome == Outcome.PLAYING, modifier = Modifier.demoTarget(targets, "flip")) { Text("Turn a card") }
            g.usableSkill()?.let { su ->
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = s::skill) { Text("Use ${DungeonSolitaire.skillName(Cards.of(11, su))}") }
            }
            for (c in g.dropOptions()) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { s.drop(c) }) { Text("Drop ${if (c == JOKER) "the Scroll" else Cards.label(c)} to flee") }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
    }
}

// =====================================================================
// Joker Jailbreak
// =====================================================================

class JailbreakSession(difficulty: Difficulty, val g: Jailbreak) : Session(GameKind.JAILBREAK, difficulty) {
    val cfg = Jailbreak.config(difficulty)
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    var version by mutableIntStateOf(0); private set
    val selected = mutableStateListOf<Int>()   // sources 0..8
    var errorFx by mutableStateOf<CardFx?>(null); private set

    override val usesStrikes get() = false
    override val middleStat get() = MiddleStat.Counter("Left", "${g.stacks.sumOf { it.size } + g.joker.size}")
    override val progress: Float get() = 1f - (0..3).minOf { g.stacks[it].size } / 6f
    override val wonTitle get() = "The Joker is free!"
    override val lostTitle get() = "Locked in"
    override val lostMessage get() = "No more matches are possible. This game's save slot has been cleared."

    val summary: String
        get() {
            val cards = selected.mapNotNull { g.cardAt(it) }
            val black = cards.filter { !Cards.red(it) }; val red = cards.filter { Cards.red(it) }
            if (cards.isEmpty()) return "Tap a black card and red cards that add up to it"
            return "Black ${black.joinToString("+") { Jailbreak.value(it).toString() }.ifEmpty { "?" }}  vs  red ${red.joinToString("+") { Jailbreak.value(it).toString() }.ifEmpty { "?" }}" +
                if (red.isNotEmpty()) " = ${red.sumOf { Jailbreak.value(it) }}" else ""
        }

    fun tap(src: Int) {
        if (outcome != Outcome.PLAYING) return
        val c = g.cardAt(src) ?: return
        if (src in selected) { selected.remove(src); version++; return }
        if (!Cards.red(c) && selected.any { g.cardAt(it)?.let { x -> !Cards.red(x) } == true }) {
            errorFx = cardFx(c); nudge("Use just one black card, matched against red cards"); return
        }
        selected += src
        val cards = selected.mapNotNull { g.cardAt(it) }
        val black = cards.firstOrNull { !Cards.red(it) }
        val redSum = cards.filter { Cards.red(it) }.sumOf { Jailbreak.value(it) }
        if (black != null && redSum > Jailbreak.value(black)) {
            errorFx = cardFx(c); nudge("Red ${redSum} is more than the black ${Jailbreak.value(black)}"); selected.remove(src)
        } else if (g.validMatch(selected.toSet())) {
            g.match(selected.toSet()); selected.clear()
            score += rules.basePoints * cards.size
        }
        version++
        after()
    }

    fun place() {
        if (outcome != Outcome.PLAYING) return
        if (!g.canPlaceRemainder()) { nudge(if (g.remainder.isEmpty()) "The spare pile is empty" else "The Joker can only hold ${g.jokerCap} cards"); return }
        g.placeRemainder(); selected.clear(); version++; after()
    }

    private fun after() {
        when {
            g.freed -> finish(true, listOf(BonusLine("Spare cards left", g.remainder.size * rules.basePoints), BonusLine("Time bonus", Scoring.finish(rules, elapsedMs, 0).timeBonus)))
            g.stuck -> finish(false)
            else -> changed()
        }
    }

    override fun saveState(): JSONObject = JSONObject()
        .put("stacks", JSONArray().also { a -> g.stacks.forEach { a.put(it.json()) } })
        .put("joker", g.joker.json()).put("rem", g.remainder.json())

    companion object {
        fun restore(o: JSONObject): JailbreakSession {
            val d = Difficulty.valueOf(o.getString("difficulty")); val st = o.getJSONObject("state")
            val g = Jailbreak(Jailbreak.config(d).cap, Random(1))
            val a = st.getJSONArray("stacks"); for (i in 0..7) g.stacks[i] += a.getJSONArray(i).ints()
            g.joker += st.getJSONArray("joker").ints(); g.remainder += st.getJSONArray("rem").ints()
            return JailbreakSession(d, g).also { it.restoreCommon(o) }
        }
    }
}

object JailbreakDef : GameDef {
    override fun background(session: Session) = com.puresudoku.R.drawable.vbg_jailbreak
    override val kind = GameKind.JAILBREAK
    private val rng = Random(SecureRandom().nextLong())
    override fun generate(d: Difficulty): Session = JailbreakSession(d, synchronized(rng) { Jailbreak.newGame(d, rng) })
    override fun restore(o: JSONObject): Session = JailbreakSession.restore(o)
    override fun hint(d: Difficulty) = "Tap a black card and red cards that add up to it to clear them. Empty one wall (N, E, S or W) to free the Joker."
    override fun levelInfo(d: Difficulty) = "Joker holds ${Jailbreak.config(d).cap}"
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Lots of room on the Joker."
        Difficulty.MEDIUM -> "A roomy Joker."
        Difficulty.HARD -> "The standard three."
        Difficulty.EXPERT -> "Three spaces, less time."
        Difficulty.MASTER -> "Only two spare spaces."
        Difficulty.EXTREME -> "One spare space."
    }
    @Composable override fun Play(session: Session) = JailbreakTable(session as JailbreakSession, null)
    @Composable override fun Icon(size: Dp) = GameIcon(kind, size)

    private fun demo(): JailbreakSession {
        val c = Cards::of
        val g = Jailbreak(3, Random(1))
        val tops = listOf(c(9, 0), c(4, 1), c(5, 2), c(7, 3), c(2, 1), c(12, 0), c(3, 2), c(11, 2))
        val rest = (0 until 52).filter { it !in tops }.shuffled(Random(9)).toMutableList()
        for (i in 0..3) { repeat(5) { g.stacks[i] += rest.removeAt(0) }; g.stacks[i] += tops[i] }
        for (i in 4..7) { g.stacks[i] += rest.removeAt(0); g.stacks[i] += tops[i] }
        g.remainder += rest
        return JailbreakSession(Difficulty.HARD, g)
    }

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("Black beats red", "Match one black card against red cards that add up to the same value (A 1 … K 13): a black 9 against a red 4 and a red 5. Matched cards leave, uncovering the next.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo(); wait(500)
                tap("j-0") { s.tap(0) }; tap("j-1") { s.tap(1) }; tap("j-2") { s.tap(2) }
                wait(1400)
            }) { t -> JailbreakTable(s, t) }
        },
        TutorialStep("Free the Joker", "The four sides (N, E, S, W) are walls of six cards; the corners only hold two. Empty any one wall to set the Joker free.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = { s = demo(); wait(500); highlight(listOf("j-0"), 900); highlight(listOf("j-1"), 900); highlight(listOf("j-2"), 900); highlight(listOf("j-3"), 900) }) { t -> JailbreakTable(s, t) }
        },
        TutorialStep("Spare cards", "Stuck? Put a spare card on the Joker (it can hold only a few). They can be matched too, but must all be cleared before the Joker escapes.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = { s = demo(); wait(500); tap("spare") { s.place() }; wait(1400) }) { t -> JailbreakTable(s, t) }
        },
    )
}

@Composable
private fun JailbreakTable(s: JailbreakSession, targets: DemoTargets?) {
    com.puresudoku.ui.components.ProvideCardTheme(com.puresudoku.ui.components.CardThemes.JAILBREAK) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val g = s.g
    val colors = LocalBoardColors.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(s.summary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val cw = min(maxWidth / 3.6f, (maxHeight / 3.4f) / 1.4f).coerceAtMost(96.dp)
            // grid positions: NW N NE / W J E / SW S SE
            val grid = listOf(listOf(7, 0, 4), listOf(3, 8, 1), listOf(6, 2, 5))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in grid) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (src in row) Box(Modifier.demoTarget(targets, "j-$src"), contentAlignment = Alignment.Center) {
                        val c = g.cardAt(src)
                        val count = if (src == 8) g.joker.size else g.stacks[src].size
                        if (c != null) PopCard(c, true, cw, highlight = src in s.selected, fx = s.errorFx) { s.tap(src) }
                        else if (src == 8) PopCard(JOKER, true, cw, art = CardArt.JESTER)
                        else CardSlot(cw, label = if (src < 4) "✓" else "")
                        if (count > 0) Text(
                            if (src == 8) "$count/${g.jokerCap}" else "$count",
                            Modifier.align(Alignment.BottomEnd).padding(2.dp).clip(CircleShape).background(if (src < 4) colors.entered else colors.note).padding(horizontal = 6.dp),
                            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = s::place, modifier = Modifier.demoTarget(targets, "spare")) {
            Text("Put a spare card on the Joker (${g.remainder.size} left)")
        }
        Spacer(Modifier.height(if (targets == null) 14.dp else 2.dp))
    }
    }
}
