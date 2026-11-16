package com.puresudoku.core.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SeaBattleTest {
    /** Each level's AI sinks a fleet; stronger levels need fewer shots. Saves round-trip along the way. */
    @Test fun aiSinksFleetsFasterOnHarderLevels() {
        val avg = DoubleArray(6)
        for (level in 0..5) {
            var total = 0
            repeat(60) { seed ->
                val rnd = Random(seed * 13 + level)
                var g = SeaBattle.forLevel(level)
                SeaBattleAi.place(g, 0, level, rnd); assertTrue(g.ready(0))
                SeaBattleAi.place(g, 1, level, rnd); assertTrue(g.ready(1))
                var guard = 0
                while (g.phase == SeaBattle.BATTLE) {
                    val p = g.turn
                    val c = SeaBattleAi.shot(g, p, level, rnd)
                    assertTrue("legal shot", g.fire(p, c) >= 0)
                    if (guard % 9 == 0) g = SeaBattle.fromJson(org.json.JSONObject(g.toJson().toString()))
                    assertTrue(++guard < 1000)
                }
                total += g.shotsBy(g.winner)
                assertEquals(0, g.shipsLeft(1 - g.winner))
            }
            avg[level] = total / 60.0
        }
        println("Sea Battle shots to win by level: " + avg.joinToString { "%.1f".format(it) })
        assertTrue("hunt-and-target beats random", avg[1] < 85)
        assertTrue("the probability map beats hunting", avg[4] < avg[1])
    }

    @Test fun sinkingAndWinning() {
        val g = SeaBattle(5, intArrayOf(2))
        assertTrue(g.place(0, 0, 0, false)); assertTrue(g.ready(0))
        assertTrue(g.place(1, 0, 6, true)); assertTrue(g.ready(1))
        assertEquals(SeaBattle.R_MISS, g.fire(0, 0))
        assertEquals(SeaBattle.R_MISS, g.fire(1, 24))
        assertEquals(SeaBattle.R_HIT, g.fire(0, 6))
        assertEquals(-1, g.fire(0, 11))       // not their turn
        g.fire(1, 23)
        assertEquals(SeaBattle.R_SUNK + 0, g.fire(0, 11))
        assertEquals(0, g.winner); assertEquals(SeaBattle.OVER, g.phase)
    }
}
