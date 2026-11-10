package com.puresudoku.ui.training

import com.puresudoku.core.Difficulty
import com.puresudoku.core.cards.BlackjackRules
import com.puresudoku.core.cards.BlackjackTable
import com.puresudoku.core.cards.Cards
import com.puresudoku.core.cards.CrazyEights
import com.puresudoku.core.cards.Cuttle
import com.puresudoku.core.cards.DungeonSolitaire
import com.puresudoku.core.cards.GoFish
import com.puresudoku.core.cards.Jailbreak
import com.puresudoku.core.cards.Klondike
import com.puresudoku.core.cards.Pile
import com.puresudoku.core.cards.PyramidRules
import com.puresudoku.core.cards.Regicide
import com.puresudoku.core.cards.Scoundrel
import com.puresudoku.ui.games.cards.BlackjackSession
import com.puresudoku.ui.games.cards.CrazyEightsSession
import com.puresudoku.ui.games.cards.CuttleSession
import com.puresudoku.ui.games.cards.DungeonSession
import com.puresudoku.ui.games.cards.GoFishSession
import com.puresudoku.ui.games.cards.JailbreakSession
import com.puresudoku.ui.games.cards.PyramidSession
import com.puresudoku.ui.games.cards.RegicideSession
import com.puresudoku.ui.games.cards.ScoundrelSession
import com.puresudoku.ui.games.cards.SolitaireSession
import com.puresudoku.ui.session.LessonStep
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import kotlin.random.Random

/**
 * Step-by-step Easy lessons for the card games. Each one is a fixed deal (the first seed whose
 * opening shows the move being taught), so the coach can name the exact cards to play.
 */
object CardScripts {
    private val E = Difficulty.EASY
    private fun l(c: Int) = Cards.label(c)
    private fun over(s: Session) = s.outcome != Outcome.PLAYING

    /** Tries seeds in order until [make] returns a lesson. Deterministic, so every run is the same. */
    private fun <T> firstSeed(make: (Random) -> T?): T {
        for (seed in 1..2000) make(Random(seed * 7919L))?.let { return it }
        error("no lesson deal")
    }

    // ---------------------------------------------------------------- Solitaire

    fun solitaire(): Pair<Session, List<LessonStep>> = firstSeed { rng ->
        val k = Klondike.deal(E, rng)
        val top = { t: Int -> k.tableau[t].last() }
        val ace = (0 until 7).firstOrNull { Cards.rank(top(it)) == 1 } ?: return@firstSeed null
        var mv: Pair<Int, Int>? = null
        for (from in 1 until 7) for (to in 0 until 7) if (from != to && from != ace && to != ace && Cards.rank(top(from)) != 1 && mv == null && k.canTableau(top(from), to)) mv = from to to
        val (from, to) = mv ?: return@firstSeed null
        val a = top(ace); val c = top(from); val onto = top(to)
        val s = SolitaireSession(E, k)
        s to listOf(
            LessonStep("Get all 52 cards onto the four foundation piles at the top: one suit each, from Ace up to King. Face-down cards in the columns are what's blocking you."),
            LessonStep("Aces always go up first. Tap the ${l(a)} in column ${ace + 1}: it jumps to a foundation.") { x -> (x as SolitaireSession).k.foundations.any { a in it } },
            LessonStep("Columns build down in alternating colours. Tap the ${l(c)} in column ${from + 1} to put it on the ${l(onto)}.") { x -> c in (x as SolitaireSession).k.tableau[to] },
            LessonStep("That uncovered a face-down card, which is what really matters: every hidden card you turn over is a new option. Always prefer moves that flip a card."),
            LessonStep("Out of moves? Tap the deck at the top-left to turn over a card. On Easy you can go through it as many times as you like.") { x -> (x as SolitaireSession).k.waste.isNotEmpty() || over(x) },
            LessonStep("Only a King can fill an empty column. Don't rush every card to the foundations: a low card in a column can hold the next card you need. Now finish the game!"),
        )
    }

    // ---------------------------------------------------------------- Dungeon Delver (pyramid)

    fun pyramid(): Pair<Session, List<LessonStep>> = firstSeed { rng ->
        val deal = PyramidRules.solvableDeal(PyramidRules.config(E).passes, rng)
        val bottom = (21..27).toList()
        val king = bottom.firstOrNull { Cards.rank(deal.pyramid[it]) == 13 } ?: return@firstSeed null
        var pair: Pair<Int, Int>? = null
        for (i in bottom) for (j in bottom) if (i < j && i != king && j != king && pair == null &&
            Cards.rank(deal.pyramid[i]) + Cards.rank(deal.pyramid[j]) == 13) pair = i to j
        val (i, j) = pair ?: return@firstSeed null
        val p = deal.pyramid
        val s = PyramidSession(E, listOf(deal))
        s to listOf(
            LessonStep("Clear the whole pyramid. Remove uncovered cards in pairs that add up to 13: Ace is 1, Jack 11, Queen 12. Kings are 13 on their own."),
            LessonStep("Start with the easy one: tap the ${l(p[king])} on the bottom row. A King clears alone.") { x -> (x as PyramidSession).isRemoved(king) },
            LessonStep("Now a pair: ${Cards.rank(p[i])} + ${Cards.rank(p[j])} = 13. Tap the ${l(p[i])}, then the ${l(p[j])}.") { x -> val q = x as PyramidSession; q.isRemoved(i) && q.isRemoved(j) },
            LessonStep("Clearing two neighbours uncovers the card above them. Look for pairs that free new cards before ones that don't."),
            LessonStep("When nothing pairs, tap the deck to draw. The top drawn card can pair with an uncovered card in the pyramid.") { x -> (x as PyramidSession).wasteTop >= 0 || over(x) },
            LessonStep("Each trip through the deck burns a torch: you have three. Plan which pairs to take so the deck still has partners for what's left. Clear the pyramid to win!"),
        )
    }

    // ---------------------------------------------------------------- Crazy Eights

    fun crazyEights(): Pair<Session, List<LessonStep>> = firstSeed { rng ->
        val g = CrazyEights.newGame(E, rng)
        val hand = g.hands[0]
        val eight = hand.firstOrNull { Cards.rank(it) == 8 } ?: return@firstSeed null
        val match = hand.firstOrNull { Cards.rank(it) != 8 && Cards.suit(it) == g.suit } ?: return@firstSeed null
        val size = hand.size
        val s = CrazyEightsSession(E, g)
        s to listOf(
            LessonStep("Be first to empty your hand. Each turn, play one card that matches the top card's suit or its number. Can't play? Draw from the deck."),
            LessonStep("The top card is ${l(g.top)}. Your ${l(match)} is the same suit, so it can go on it. Tap it.") { x ->
                val q = (x as CrazyEightsSession).g; match !in q.hands[0] || q.hands[0].size > size || over(x)
            },
            LessonStep("Your rival now has to match your card. If they draw, they had nothing that fits: remember that suit, they're short of it."),
            LessonStep("Your ${l(eight)} is wild: it goes on anything and you name the next suit. Save it for when you're stuck, then call the suit you hold most of."),
            LessonStep("Play high cards early and keep a spread of suits. Matching by number lets you switch suits on your rival. Go out first to win!"),
        )
    }

    // ---------------------------------------------------------------- Blackjack

    fun blackjack(): Pair<Session, List<LessonStep>> = firstSeed { rng ->
        val cfg = BlackjackRules.config(E)
        val shoe = BlackjackTable.shoe(cfg.decks, rng)
        val n = shoe.size
        val p1 = shoe[n - 1]; val up = shoe[n - 2]; val p2 = shoe[n - 3]; val hole = shoe[n - 4]
        val (tot, soft) = BlackjackRules.total(listOf(p1, p2))
        val upV = BlackjackRules.v(up)
        if (soft || tot !in 13..16 || upV !in 4..6 || Cards.rank(p1) == Cards.rank(p2)) return@firstSeed null
        if (BlackjackRules.isBlackjack(listOf(up, hole))) return@firstSeed null
        val s = BlackjackSession(E, BlackjackTable(cfg, shoe, rng), cfg.start, 0)
        s to listOf(
            LessonStep("Get closer to 21 than the dealer without going over. Number cards count their value, J Q K count 10, and an Ace is 1 or 11."),
            LessonStep("Leave the bet at 10 and tap Deal.") { x -> (x as BlackjackSession).round >= 1 },
            LessonStep("You have ${l(p1)} ${l(p2)} = $tot. The dealer shows ${l(up)}. A dealer showing 4, 5 or 6 must keep drawing to 17 and busts often. Don't risk busting yourself: tap Stand.") { x ->
                (x as BlackjackSession).phase != 1 || over(x)
            },
            LessonStep("That's basic strategy: the advice line under your hand shows the best play for every hand. Follow it until it feels natural."),
            LessonStep("Rules of thumb: always hit 11 or less, double on 10 or 11 when the dealer is weaker, split Aces and 8s. Reach the chip goal to win."),
        )
    }

    // ---------------------------------------------------------------- Go Fish

    fun goFish(): Pair<Session, List<LessonStep>> = firstSeed { rng ->
        val g = GoFish.newGame(E, rng)
        val counts = g.hands[0].groupingBy { Cards.rank(it) }.eachCount()
        val pairRank = counts.entries.firstOrNull { it.value == 2 }?.key ?: return@firstSeed null
        val s = GoFishSession(E, g)
        val start = s.log.size
        s to listOf(
            LessonStep("Collect books: all four cards of one rank. On your turn, ask another player for a rank you already hold. If they have any, they hand them all over."),
            LessonStep("You hold two ${Cards.plural(pairRank)}, so you're halfway to a book. Tap one of your ${Cards.plural(pairRank)}, then tap your rival to ask.") { x ->
                (x as GoFishSession).log.size > start || x.g.turn != 0 || over(x)
            },
            LessonStep("If they had some, you ask again. If not, you go fish from the deck. Watch what your rival asks for: they must hold that rank, so ask them for it later."),
            LessonStep("Ask for ranks where you hold the most cards first, and remember who just took what. Most books wins!"),
        )
    }

    // ---------------------------------------------------------------- Cuttle

    fun cuttle(): Pair<Session, List<LessonStep>> = firstSeed { rng ->
        val g = Cuttle.newGame(rng)
        val best = g.hands[0].filter { Cards.rank(it) in 2..10 }.maxByOrNull { Cards.rank(it) } ?: return@firstSeed null
        if (Cards.rank(best) < 7) return@firstSeed null
        val s = CuttleSession(E, g)
        s to listOf(
            LessonStep("Race to 21 points. Each turn, draw a card or play one. Number cards played as points add their value to your score."),
            LessonStep("Your ${l(best)} is your biggest number. Tap it and choose to play it for points.") { x -> (x as CuttleSession).g.score(0) > 0 || over(x) },
            LessonStep("Your rival can scuttle a point card: play a bigger number on it to destroy both. Keep an eye on which of your points are exposed."),
            LessonStep("Numbers can also be one-off effects instead: an Ace wipes all points, a 2 cancels a one-off, a 5 draws two. A King lowers your target, a Queen protects your cards."),
            LessonStep("Build points early, scuttle their biggest card when you can, and keep a 2 ready to stop their tricks. First to the goal wins!"),
        )
    }

    // ---------------------------------------------------------------- Scoundrel

    fun scoundrel(): Pair<Session, List<LessonStep>> = firstSeed { rng ->
        val g = Scoundrel.newGame(E, rng)
        val weapons = g.room.filter { Scoundrel.isWeapon(it) }
        if (weapons.size != 1) return@firstSeed null
        val w = weapons[0]
        val m = g.room.filter { Scoundrel.isMonster(it) && Scoundrel.value(it) > Scoundrel.value(w) && Scoundrel.value(it) <= 10 }.minByOrNull { Scoundrel.value(it) }
            ?: return@firstSeed null
        if (g.room.none { Scoundrel.isPotion(it) }) return@firstSeed null
        val potion = g.room.first { Scoundrel.isPotion(it) }
        val s = ScoundrelSession(E, g)
        val dmg = Scoundrel.value(m) - Scoundrel.value(w)
        s to listOf(
            LessonStep("Survive the dungeon. Each room shows four cards: ♣ and ♠ are monsters, ♦ are weapons, ♥ are healing potions. Each card's number is its strength. Face three of the four; the last one stays for the next room."),
            LessonStep("Arm yourself first. Tap the ${l(w)} to pick up a weapon of strength ${Scoundrel.value(w)}.") { x -> (x as ScoundrelSession).g.weapon == w },
            LessonStep("Tap the ${l(m)} and fight it with your weapon. The weapon soaks up ${Scoundrel.value(w)}, so you only take $dmg damage instead of ${Scoundrel.value(m)}.") { x ->
                m !in (x as ScoundrelSession).g.room || over(x)
            },
            LessonStep("Weapons dull: after a kill, a weapon can only fight monsters weaker than its last victim. Kill big monsters first, then the small ones."),
            LessonStep("The ${l(potion)} heals ${Scoundrel.value(potion)}, but only one potion per room works, so drink when you're hurt. You can also run from a whole room, but not twice in a row. Clear the dungeon to win!"),
        )
    }

    // ---------------------------------------------------------------- Regicide

    fun regicide(): Pair<Session, List<LessonStep>> = firstSeed { rng ->
        val g = Regicide.newGame(E, rng)
        val immune = g.immune
        if (immune == 0) return@firstSeed null
        val club = g.hand.filter { Cards.suit(it) == 0 && Cards.rank(it) in 5..10 }.maxByOrNull { Cards.rank(it) } ?: return@firstSeed null
        if (Regicide.value(club) * 2 >= Regicide.health(g.enemy)) return@firstSeed null
        val s = RegicideSession(E, g)
        var hit = false
        s to listOf(
            LessonStep("Defeat all twelve royals: Jacks, then Queens, then Kings. Play cards to damage the enemy. If it survives, it strikes back and you discard cards worth at least its attack."),
            LessonStep("The enemy is the ${l(g.enemy)}: ${Regicide.health(g.enemy)} health, attack ${Regicide.attack(g.enemy)}. It's immune to ${Cards.suitName(immune)} powers. Clubs deal double damage: select the ${l(club)} and tap Play for ${Regicide.value(club) * 2} damage.") { x ->
                val q = (x as RegicideSession).g; q.damage > 0 || q.defeated > 0 || over(x)
            },
            LessonStep("It survived and strikes back. Select cards from your hand worth at least its attack, then tap Take hit. Spades you play lower its attack for the rest of the fight.") { x ->
                val q = (x as RegicideSession).g
                if (q.suffer > 0) hit = true
                (hit && q.suffer == 0) || q.defeated > 0 || over(x)
            },
            LessonStep("The other suits: ♥ shuffles discards back into the deck, ♦ draws you cards. Beat an enemy with exactly its health and it joins your deck as an ally."),
            LessonStep("Jesters give you a fresh hand when you're stuck. Keep cards worth enough to survive the next hit before you attack. Defeat all twelve to win!"),
        )
    }

    // ---------------------------------------------------------------- Dungeon Solitaire

    fun dungeon(): Pair<Session, List<LessonStep>> {
        val g = DungeonSolitaire.newGame(E, Random(20261110))
        val s = DungeonSession(E, g)
        return s to listOf(
            LessonStep("Raid the tomb and escape alive with its Kings. Each turn you flip cards: ♠ are monsters, ♦ traps, ♣ locked doors. The card after an encounter is your action."),
            LessonStep("Tap the deck to delve into the first room.") { x -> (x as DungeonSession).g.delve.isNotEmpty() || x.g.current != null || over(x) },
            LessonStep("Beat an encounter with an action card at least as high as it. Lower cards hurt you for the difference. Keep flipping until the room is done.") { x ->
                (x as DungeonSession).g.choosing || over(x)
            },
            LessonStep("Now choose: delve deeper, or turn back. Aces are torches burning out: after the fourth you're lost in the dark. Jacks are skills, Queens heal, Kings are the prize."),
            LessonStep("Turning back takes you through new encounters, so leave while your health and torches allow. Escape with enough Kings to win!"),
        )
    }

    // ---------------------------------------------------------------- Joker Jailbreak

    fun jailbreak(): Pair<Session, List<LessonStep>> = firstSeed { rng ->
        val g = Jailbreak.newGame(E, rng)
        val srcs = g.anyMatch() ?: return@firstSeed null
        if (srcs.size !in 2..3 || 8 in srcs) return@firstSeed null
        val cards = srcs.associateWith { g.cardAt(it)!! }
        val black = cards.values.first { !Cards.red(it) }
        val reds = cards.values.filter { Cards.red(it) }
        val s = JailbreakSession(E, g)
        s to listOf(
            LessonStep("Free the Joker by clearing any one of the four walls. Clear cards by matching one black card against red cards that add up to it: Ace 1, Jack 11, Queen 12, King 13."),
            LessonStep("The ${l(black)} is worth ${Cards.rank(black)}. ${reds.joinToString(" + ") { l(it) }} adds up to the same. Tap all of them to clear them.") { x ->
                val q = (x as JailbreakSession).g; cards.all { (src, c) -> q.cardAt(src) != c } || over(x)
            },
            LessonStep("Corners hold two cards, walls hold six. Focus on one wall and keep taking cards off it."),
            LessonStep("Stuck? Tap the spare pile to put a card on the Joker. Careful: that pile must be cleared too before the Joker escapes, and it only holds ${Jailbreak.config(E).cap}."),
        )
    }
}
