package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class AegStewardshipTest {
    @Test fun originalModePersistsDecreesAndPendingChoice() {
        val game = Aegwaspell.newGame(Difficulty.EASY, Random(12))
        game.apply(AAct.Decree(AegStewardship.RATIONS, 0), Random(1))
        game.sw.choice = 2
        val loaded = Aegwaspell.fromState(game.state())
        assertEquals(0, loaded.sw.decree[AegStewardship.RATIONS])
        assertEquals(2, loaded.sw.choice)
        assertFalse(loaded.legal(AAct.End))
        assertTrue(loaded.legal(AAct.Choose(1)))
        loaded.apply(AAct.Choose(1), Random(1))
        assertEquals(-1, loaded.sw.choice)
        assertTrue(loaded.legal(AAct.End))
    }

    @Test fun conquestPersistsStewardshipAndKeepsPortRate() {
        val game = AegConquest.newGame(Difficulty.HARD, Random(14))
        game.apply(CAct.Decree(AegStewardship.TAXES, 2), Random(1))
        game.sw.choice = 5
        game.sw.kids[1] = 3
        val loaded = AegConquest.fromState(game.state())
        assertEquals(2, loaded.sw.decree[AegStewardship.TAXES])
        assertEquals(3, loaded.sw.children())
        assertFalse(loaded.legal(CAct.End))
        loaded.apply(CAct.Choose(0), Random(1))
        assertEquals(-1, loaded.sw.choice)
        assertEquals(4, loaded.tradeRate())
    }
    @Test fun demolitionReturnsHalfCostAndRubbleClearsNextSeason() {
        val game = Aegwaspell.newGame(Difficulty.EASY, Random(19))
        val h = game.capital
        game.building[h] = Aegwaspell.FARM
        val timber = game.res[Aegwaspell.TIMBER]
        game.sw.demolish(h, Aegwaspell.COST[Aegwaspell.FARM])
        assertEquals(Aegwaspell.NONE, game.building[h])
        assertEquals(timber + 1, game.res[Aegwaspell.TIMBER])
        assertEquals(1, game.sw.rubble[h])
        game.sw.endOfSeason(BooleanArray(game.H))
        assertEquals(0, game.sw.rubble[h])
    }

    @Test fun wonderConstructionRequiresFourStaffedSeasons() {
        val game = AegConquest.newGame(Difficulty.EASY, Random(20))
        val h = game.map.hexNbrs[game.capital].first { game.rivalLand[it] < 0 }
        game.road[game.side(game.capital, h)] = true
        game.building[h] = AegStewardship.LIGHTHOUSE
        game.sw.started(h)
        repeat(2) { game.sw.endOfSeason(BooleanArray(game.H)) }
        assertEquals(4, game.sw.works[h])
        val staffed = BooleanArray(game.H) { it == h }
        repeat(3) { game.sw.endOfSeason(staffed) }
        assertEquals(1, game.sw.works[h])
        game.sw.endOfSeason(staffed)
        assertEquals(0, game.sw.works[h])
        assertEquals(1, game.sw.wondersDone())
        assertEquals(4, game.tradeRate()) // Finished but unstaffed.
        game.workers[h] = AegConquest.WORKERS[AegStewardship.LIGHTHOUSE]
        assertEquals(3, game.tradeRate())
        game.res[AegConquest.FOOD] = 3
        game.res[AegConquest.GOLD] = 0
        assertTrue(game.legal(CAct.Trade(AegConquest.FOOD, AegConquest.GOLD)))
        game.apply(CAct.Trade(AegConquest.FOOD, AegConquest.GOLD), Random(1))
        assertEquals(0, game.res[AegConquest.FOOD])
        assertEquals(1, game.res[AegConquest.GOLD])
        assertEquals(0, AegConquest.fromState(game.state()).sw.works[h])
    }
}
