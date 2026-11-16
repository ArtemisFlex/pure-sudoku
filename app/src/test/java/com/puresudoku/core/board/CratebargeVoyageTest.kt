package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import com.puresudoku.ui.games.board.CratebargeSession
import com.puresudoku.ui.session.Outcome
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CratebargeVoyageTest {
    @Test fun profilePurchasesAndSettlementPersistWithoutDuplicatePayout() {
        val s = CratebargeSession(Difficulty.EASY, Cratebarge.forLevel(0))
        s.loadProfile(JSONObject().put("dice", 30))
        s.buySupply(CrateSupply.TELESCOPE)
        assertEquals(14, s.profile.dice)
        assertEquals(1, s.profile.stock[CrateSupply.TELESCOPE.ordinal])
        s.buySupply(CrateSupply.BARREL)
        assertEquals(14, s.profile.dice)
        s.settleVoyage(Outcome.WON); s.settleVoyage(Outcome.WON)
        assertEquals(22, s.profile.dice)
        assertEquals(1, s.profile.unlocked)
        val restored = CrateProfile.fromJson(JSONObject(s.profile.toJson().toString()))
        assertEquals(22, restored.dice); assertEquals(1, restored.stock[CrateSupply.TELESCOPE.ordinal])
        assertEquals(1, restored.unlocked)
    }

    @Test fun issuedSuppliesAndScoutingSurviveSavedGame() {
        val g = Cratebarge.forLevel(0)
        val s = CratebargeSession(Difficulty.EASY, g)
        s.loadProfile(JSONObject().put("unlocked", 2))
        s.selectContract(2)
        assertEquals(1, s.supplyCount(CrateSupply.TELESCOPE))
        g.randomFleet(0, Random(1)); assertTrue(g.ready(0))
        g.randomFleet(1, Random(2)); assertTrue(g.ready(1))
        s.selectSupply(CrateSupply.TELESCOPE); s.fire(0)
        assertEquals(0, s.supplyCount(CrateSupply.TELESCOPE))
        assertEquals(0, g.shotsBy(0)); assertNotEquals(0, s.revealed[0]); assertEquals(0, g.turn)
        val saved = JSONObject(s.toJson().toString())
        val restored = CratebargeSession.restore(saved)
        assertEquals(2, restored.contract); assertEquals(1, restored.supplyCount(CrateSupply.BARREL))
        assertEquals(s.revealed[0], restored.revealed[0]); assertEquals(0, restored.g.shotsBy(0))
    }

    @Test fun lostVoyageResetsProgressButKeepsDice() {
        val s = CratebargeSession(Difficulty.EASY, Cratebarge.forLevel(0))
        s.loadProfile(JSONObject().put("dice", 12).put("unlocked", 3))
        s.settleVoyage(Outcome.LOST)
        assertEquals(0, s.profile.unlocked); assertEquals(12, s.profile.dice)
    }

    @Test fun barrelRepairsAndDecoyHaveTheirSpecifiedEffects() {
        val g = Cratebarge.forLevel(0)
        val s = CratebargeSession(Difficulty.EASY, g)
        s.loadProfile(JSONObject().put("unlocked", 5))
        s.selectContract(5)
        g.randomFleet(0, Random(5)); assertTrue(g.ready(0))
        g.randomFleet(1, Random(6)); assertTrue(g.ready(1))
        val damaged = g.shipCells(0, 0).first()
        g.shots[1][damaged] = Cratebarge.HIT
        s.selectSupply(CrateSupply.PLANKS); s.ownGridTap(damaged)
        assertEquals(Cratebarge.NONE, g.shots[1][damaged])
        assertEquals(0, s.supplyCount(CrateSupply.PLANKS))
        s.selectSupply(CrateSupply.DECOY)
        assertTrue(s.decoy); assertEquals(0, s.supplyCount(CrateSupply.DECOY))
        val targets = g.shipCells(1, 0).take(1)
        for (ship in g.fleet.indices) for (cell in g.shipCells(1, ship)) if (cell !in targets) g.shots[0][cell] = Cratebarge.HIT
        s.selectSupply(CrateSupply.BARREL); s.fire(targets[0])
        assertEquals(Cratebarge.OVER, g.phase)
        assertEquals(0, g.winner)
        assertEquals(0, s.supplyCount(CrateSupply.BARREL))
        assertTrue(targets.all { g.shots[0][it] == Cratebarge.HIT })
    }
}
