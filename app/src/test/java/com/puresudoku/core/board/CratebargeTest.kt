package com.puresudoku.core.board

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import kotlin.random.Random

class CratebargeTest {
    @Test fun originalFleetAndPlacementSurviveSave() {
        for (level in 0..5) {
            val g = Cratebarge.forLevel(level)
            assertEquals(10, g.size)
            assertArrayEquals(intArrayOf(5, 4, 3, 3, 2, 2), g.fleet)
            assertEquals("Crownwake Flagbarge", g.name(0))
            assertEquals("Barrelbug Dinghy", g.name(5))
            g.randomFleet(0, Random(level)); assertTrue(g.ready(0))
            g.randomFleet(1, Random(level + 20)); assertTrue(g.ready(1))
            val restored = Cratebarge.fromJson(JSONObject(g.toJson().toString()))
            assertEquals(Cratebarge.BATTLE, restored.phase)
            for (p in 0..1) {
                val cells = restored.fleet.indices.flatMap { restored.shipCells(p, it) }
                assertEquals(19, cells.size)
                assertEquals(19, cells.toSet().size)
            }
        }
    }

    @Test fun fleetBattleReachesWinnerWithLegalShots() {
        val g = Cratebarge.forLevel(2)
        val rnd = Random(42)
        for (p in 0..1) { CratebargeAi.place(g, p, 2, rnd); assertTrue(g.ready(p)) }
        var turns = 0
        while (g.phase == Cratebarge.BATTLE) {
            val p = g.turn
            assertTrue(g.fire(p, CratebargeAi.shot(g, p, 2, rnd)) >= 0)
            assertTrue(++turns <= 200)
        }
        assertEquals(0, g.shipsLeft(1 - g.winner))
    }
}
