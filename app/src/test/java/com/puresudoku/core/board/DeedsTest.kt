package com.puresudoku.core.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DeedsTest {
    /** The AI plays whole games against itself on every level; each must end, with saves round-tripping on the way. */
    @Test fun aiGamesFinish() {
        val reasons = IntArray(4); var fells = 0; var rounds = 0; var gaps = 0; var towersBuilt = 0; var auctions = 0; var trades = 0; var offers = 0; var greats = 0; var outs = 0
        for (level in 0..5) for (seed in 0 until 25) {
            val rnd = Random(seed * 31 + level)
            var g = Deeds.newGame(level, rnd)
            val lords = 2 + seed % 3
            assertTrue(g.seats(lords, 0b1110))
            var steps = 0
            while (g.phase != Deeds.OVER) {
                val a = DeedsAi.next(g, rnd)
                val ok = g.perform(a, rnd)
                assertTrue("level $level seed $seed step $steps: illegal $a in phase ${g.phase} decision ${g.pType}", ok)
                for (l in g.log) { if ("wins the auction" in l) auctions++; if ("accepts!" in l) trades++; if ("offers" in l) offers++; if (listOf("Inferno!", "Second Wind!", "The Heist!", "Reign of Terror!").any { it in l }) greats++; if ("vanquished" in l) outs++ }
                g.log.clear()
                if (steps % 7 == 0) g = Deeds.fromJson(org.json.JSONObject(g.toJson().toString()))
                steps++
                assertTrue("game runs too long", steps < 20000)
                for (p in 0 until g.n) {
                    assertTrue("gold never negative", g.gold[p] >= 0)
                    assertTrue("health within bounds", g.hp[p] <= g.maxHp(p))
                }
            }
            reasons[g.endReason]++
            fells += g.felled.sum(); rounds += g.round; auctions += 0
            gaps += kotlin.math.abs(g.renown(0) - g.renown(1)); towersBuilt += (0 until g.n).sumOf { g.towerCount(it) }
        }
        println("Deed or Dungeon endings: vanquished ${reasons[1]}, four domains ${reasons[2]}, old King died ${reasons[3]}; fellings/game ${fells / 150.0}; rounds ${rounds / 150.0}; renown gap ${gaps / 150}; towers ${towersBuilt / 150.0}; auctions $auctions; offers $offers trades $trades; great deeds $greats; lords vanquished $outs")
    }

    @Test fun refusingHomageWoundsAndFellingSeizesADeed() {
        val rnd = Random(1)
        val g = Deeds.newGame(2, rnd)
        g.seats(2, 0); g.choose(Deeds.KNIGHT, 1); g.choose(Deeds.DRAGON, 2)
        // the Dragon owns two lands; the Knight owns one and is nearly dead
        g.owner[1] = 1; g.owner[3] = 1; g.towers[1] = 0; g.coin[1] = Deeds.COIN_CROWN
        g.owner[35] = 0
        g.hp[0] = 1
        g.pos[0] = 0
        g.phase = Deeds.ROLL
        // ride one tile onto the Dragon's land
        val m = Deeds::class.java.getDeclaredMethod("move", Int::class.java, Int::class.java).apply { isAccessible = true }
        m.invoke(g, 0, 1)
        assertEquals(Deeds.D_HOMAGE, g.pType)
        assertTrue(g.refuse())
        assertEquals("felled Knight lost its only deed to the Dragon", 1, g.owner[35])
        assertEquals("woke in the Dungeon", Deeds.DUNGEON_TILE, g.pos[0])
        assertEquals("healed to full", 12, g.hp[0])
    }

    @Test fun auctionSellsToHighestBidder() {
        val rnd = Random(3)
        val g = Deeds.newGame(2, rnd)
        g.seats(3, 0); g.choose(0, 1); g.choose(1, 2); g.choose(2, 3)
        g.lapped.fill(1)
        val m = Deeds::class.java.getDeclaredMethod("move", Int::class.java, Int::class.java).apply { isAccessible = true }
        m.invoke(g, 0, 1)
        assertEquals(Deeds.D_BUY, g.pType)
        assertTrue(g.pass())
        assertEquals(Deeds.AUCTION, g.phase)
        assertEquals(1, g.decider)
        assertTrue(g.bid(1))          // lord 2 bids 50
        assertTrue(g.fold())          // lord 3 drops
        assertTrue(g.bid(0))          // lord 1 bids 60
        assertTrue(g.fold())          // lord 2 drops
        assertEquals(Deeds.AFTER, g.phase)
        assertEquals(0, g.owner[1])
        assertEquals(Deeds.START_GOLD - 60, g.gold[0])
    }

    @Test fun noBuyingUntilYouPassTheSpire() {
        val rnd = Random(5)
        val g = Deeds.newGame(2, rnd)
        g.seats(2, 0); g.choose(0, 1); g.choose(1, 2)
        val m = Deeds::class.java.getDeclaredMethod("move", Int::class.java, Int::class.java).apply { isAccessible = true }
        // first lap: an unclaimed deed can't be bought, and stays unclaimed
        m.invoke(g, 0, 1)
        assertTrue(g.pType != Deeds.D_BUY); assertEquals(-1, g.owner[1])
        // round the board past the Spire, then it can
        g.phase = Deeds.ROLL; g.pos[0] = 34
        m.invoke(g, 0, 3)
        assertEquals(1, g.lapped[0]); assertEquals(Deeds.D_BUY, g.pType)
        // only lords who have passed the Spire may bid on what's passed
        assertTrue(g.pass())
        assertTrue(g.phase != Deeds.AUCTION || g.decider == 0)
        assertEquals(1, Deeds.fromJson(org.json.JSONObject(g.toJson().toString())).lapped[0])
    }

    @Test fun tradeMovesDeedsAndGold() {
        val rnd = Random(4)
        val g = Deeds.newGame(2, rnd)
        g.seats(2, 0); g.choose(0, 1); g.choose(1, 2)
        g.owner[1] = 0; g.owner[3] = 1; g.phase = Deeds.ROLL
        assertTrue(g.propose(Deeds.Offer(0, 1, emptyList(), listOf(3), 300, 0)))
        assertEquals(1, g.decider)
        assertTrue(g.accept())
        assertEquals(0, g.owner[3])
        assertEquals(Deeds.START_GOLD - 300, g.gold[0]); assertEquals(Deeds.START_GOLD + 300, g.gold[1])
        assertEquals(Deeds.ROLL, g.phase)
    }
}
