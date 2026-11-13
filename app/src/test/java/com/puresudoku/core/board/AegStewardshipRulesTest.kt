package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class AegStewardshipRulesTest {
    private class Land : AegLand {
        override val map = Aegwaspell.grid(2)
        override val H = map.HEXES
        override val diff = 0
        override val terrain = IntArray(H) { Aegwaspell.PLAINS }
        override val building = IntArray(H)
        override val res = IntArray(5) { 20 }
        override var pop = 30
        override var happiness = 50
        override var season = 0
        override val capital = H / 2
        override val log = arrayListOf<String>()
        val staff = BooleanArray(H) { true }
        override fun connected() = BooleanArray(H) { true }
        override fun staffed() = staff.copyOf()
        override fun armed() = 0
        override fun loseSoldiers(n: Int) {}
        override fun prideRival() {}
        override fun sendHome(h: Int) {}
    }

    @Test fun forecastNamesTheSeasonBeingHarvested() {
        val g = Land(); val sw = AegStewardship(g)
        sw.forecast[0] = 2; sw.forecast[1] = 2
        g.season = 2
        assertEquals("poor (4)", sw.forecastText())
        g.season = 3
        assertEquals("winter harvest", sw.forecastText())
        g.season = 4
        assertEquals("poor (4)", sw.forecastText())
    }

    @Test fun eachWonderRequiresCompletionAndStaffForItsActiveEffect() {
        val g = Land(); val sw = AegStewardship(g)
        for (w in AegStewardship.WONDERS) {
            val h = w - AegStewardship.LIGHTHOUSE
            g.building[h] = w
            sw.started(h)
            assertFalse(sw.active(w))
            sw.works[h] = 0
            assertTrue(sw.active(w))
            g.staff[h] = false
            assertFalse(sw.active(w))
            g.staff[h] = true
        }
        val active = sw.activeWonders()
        assertEquals(1f, sw.harvestShare(2, active))
        assertEquals(1.5, sw.birthFactor(active), 0.001)
        assertTrue(sw.housing() >= 20)
        val palace = AegStewardship.PALACE - AegStewardship.LIGHTHOUSE
        g.staff[palace] = false; assertEquals(0, sw.housingOf(palace))
        g.staff[palace] = true; assertEquals(20, sw.housingOf(palace))
        g.happiness = 40; g.res[Aegwaspell.GOLD] = 0
        sw.seasonMoodAndGold(active)
        assertEquals(50, g.happiness) // library + sky temple + palace
        assertEquals(6, g.res[Aegwaspell.GOLD]) // normal taxes doubled
        sw.choice = 3
        sw.choose(1, Random(1))
        assertEquals(30, g.pop) // active sky temple prevents plague deaths
    }

    @Test fun lighthouseAndGardensAddTheirAnnouncedFood() {
        val g = Land(); val sw = AegStewardship(g)
        val farm = g.capital; val port = g.map.hexNbrs[farm][0]
        val fishery = g.map.hexNbrs[port].first { it != farm }
        g.building[farm] = Aegwaspell.FARM; g.building[port] = Aegwaspell.PORT
        g.building[fishery] = Aegwaspell.FISHERY
        val none = BooleanArray(6); val active = BooleanArray(6)
        active[AegStewardship.LIGHTHOUSE - AegStewardship.LIGHTHOUSE] = true
        active[AegStewardship.GARDENS - AegStewardship.LIGHTHOUSE] = true
        for (h in listOf(farm, port, fishery)) {
            val before = IntArray(5); val after = IntArray(5)
            sw.adjust(h, before, false, none); sw.adjust(h, after, false, active)
            assertEquals(2, after[Aegwaspell.FOOD] - before[Aegwaspell.FOOD])
        }
    }

    @Test fun unfinishedSkyTempleDoesNotServeTown() {
        val g = Land(); val sw = AegStewardship(g)
        val town = g.capital; val sky = g.map.hexNbrs[town][0]
        g.building[town] = Aegwaspell.TOWN; g.building[sky] = AegStewardship.SKY_TEMPLE
        sw.started(sky)
        assertEquals(8, sw.housingOf(town))
        sw.works[sky] = 0
        assertEquals(15, sw.housingOf(town))
        g.staff[sky] = false
        assertEquals(8, sw.housingOf(town))
    }

    @Test fun choicesCannotGiveFullRewardWithoutTheListedCost() {
        val g = Land(); val sw = AegStewardship(g)
        sw.choice = 6; g.res[Aegwaspell.FOOD] = 5
        assertFalse(sw.canChoose(0)); sw.choose(0, Random(1))
        assertEquals(6, sw.choice); assertEquals(50, g.happiness)
        assertTrue(sw.canChoose(1)); sw.choose(1, Random(1))
        sw.choice = 18; g.res[Aegwaspell.GOLD] = 3
        assertFalse(sw.canChoose(0)); assertTrue(sw.canChoose(1))
    }

    @Test fun citadelDefenceSurvivesSaveRestoreAndTracksStaff() {
        val g = AegConquest.newGame(Difficulty.EASY, Random(21))
        val h = g.map.hexNbrs[g.capital].first { g.rivalLand[it] < 0 }
        g.road[g.side(g.capital, h)] = true
        g.building[h] = AegStewardship.CITADEL
        g.sw.works[h] = 0
        g.workers[h] = AegConquest.WORKERS[AegStewardship.CITADEL]
        val expected = g.defence(g.capital)
        assertEquals(g.guard[g.capital] + 6, expected)
        val loaded = AegConquest.fromState(g.state())
        assertEquals(expected, loaded.defence(loaded.capital))
        loaded.workers[h] = 0
        assertEquals(loaded.guard[loaded.capital] + 2, loaded.defence(loaded.capital))
    }
    @Test fun newGamesShowTheFirstHarvestInBothModes() {
        val a = Aegwaspell.newGame(Difficulty.EASY, Random(33))
        val c = AegConquest.newGame(Difficulty.EASY, Random(33))
        for (sw in listOf(a.sw, c.sw)) {
            val forecast = sw.forecast.sum()
            assertTrue(forecast in 2..12)
            assertNotEquals("unknown", sw.forecastText())
            assertEquals(forecast, sw.harvestDice(Random(42), IntArray(2)))
        }
    }

    @Test fun restingFarmsRecoverAndChildrenMatureAfterFourSeasons() {
        val g = Land(); val sw = AegStewardship(g); val farm = g.capital
        g.building[farm] = Aegwaspell.FARM; sw.tired[farm] = 8; sw.setFallow(farm)
        sw.endOfSeason(g.staffed()); assertEquals(5, sw.tired[farm])
        sw.endOfSeason(g.staffed()); assertEquals(2, sw.tired[farm])
        sw.kids[0] = 3
        repeat(3) { sw.growUp(); assertEquals(30, g.pop) }
        sw.growUp(); assertEquals(33, g.pop); assertEquals(0, sw.children())
    }

    @Test fun rationsAndUnpaidUpkeepHaveAdvertisedEffects() {
        val g = Land(); val sw = AegStewardship(g)
        sw.decree[AegStewardship.RATIONS] = 0
        assertEquals(15, sw.mouths()); assertEquals(0.0, sw.birthFactor(BooleanArray(6)), 0.001)
        sw.decree[AegStewardship.RATIONS] = 2
        assertEquals(45, sw.mouths())
        g.building[0] = Aegwaspell.COLOSSEUM; g.res[Aegwaspell.GOLD] = 0
        sw.decree[AegStewardship.RATIONS] = 1; sw.decree[AegStewardship.TAXES] = 0
        sw.seasonMoodAndGold(BooleanArray(6))
        assertEquals(48, g.happiness) // low tax +2, unpaid 2-gold upkeep -4
        assertTrue(g.log.any { it.contains("Upkeep unpaid") })
    }

}
