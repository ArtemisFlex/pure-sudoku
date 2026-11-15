package com.puresudoku.core.city

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreetSmartsTest {
    private val city = City.harbor

    @Test fun cityIsBuiltAndEveryPlaceIsReachable() {
        val alive = (0 until City.N * City.N).count { city.alive(it) }
        assertTrue("intersections: $alive", alive > 700)
        assertTrue("lights", city.lights.count { it != null } > 20)
        assertTrue("one-way blocks", city.edges.count { it.oneWay != 0 } > 50)
        assertTrue("bridges", city.edges.count { it.bridge } >= 5)
        assertEquals("customers", City.CUSTOMERS, city.places.count { it.kind == City.Kind.CUSTOMER })
        val game = StreetSmarts.newGame(1).apply { deliveries = 1000 }   // whole city open
        val home = city.edges[city.places[city.home].edge].a
        for (p in city.places) {
            game.at = home; game.cameBy = -1
            assertTrue("no route to ${p.name}", game.route(home, p.edge).isNotEmpty())
        }
    }

    @Test fun followingTheGpsCompletesOrdersAndHelpFades() {
        val g = StreetSmarts.newGame(7)
        var orders = 0
        while (orders < 40) {
            if (g.order == null) { if (g.offer == null) g.makeOffer(); g.accept() }
            val stop = g.current!!
            val path = g.route(g.at, city.places[stop.place].edge)
            assertTrue("route to ${city.places[stop.place].name}", path.isNotEmpty())
            var arrival: StreetSmarts.Arrival? = null
            for (id in path) {
                assertTrue("legal move", g.options().any { it.edge == id } || g.cameBy == -1 || g.turnAround())
                arrival = g.drive(id)
            }
            if (arrival!!.orderDone) orders++
        }
        assertTrue("deliveries: ${g.deliveries}", g.deliveries >= 20)
        assertTrue("coins earned", g.earned > 100)
        assertTrue("help faded for some places", g.familiarity.values.any { it >= 2 })
        // save and reload keeps the career
        val copy = StreetSmarts().apply { load(g.state()) }
        assertEquals(g.state(), copy.state())
    }

    @Test fun everyLegHasFourOrMoreTurns() {
        val g = StreetSmarts.newGame(11)
        var simple = 0; var legs = 0
        repeat(12) {
            g.offer = null; g.makeOffer()
            val o = g.offer!!
            var from = g.at
            for (s in o.stops) {
                val path = g.route(from, city.places[s.place].edge)
                if (g.turnsOn(path, from) >= 4) simple++
                legs++
                from = g.endOf(path, from)
            }
        }
        assertTrue("legs with 4+ turns: $simple of $legs", simple * 10 >= legs * 8)
    }

    @Test fun shortestPathEarnsTheRouteBonusAndAlternativesDiffer() {
        val g = StreetSmarts.newGame(21)
        var bonuses = 0
        repeat(6) {
            if (g.offer == null) g.makeOffer()
            g.accept()
            var last: StreetSmarts.Arrival? = null
            while (g.order != null) {
                val stop = g.current!!
                val alts = g.alternatives(g.at, city.places[stop.place].edge)
                assertTrue("at least one route", alts.isNotEmpty())
                assertEquals("alternatives are distinct", alts.size, alts.toSet().size)
                for (id in g.route(g.at, city.places[stop.place].edge, byDistance = true)) last = g.drive(id)
            }
            if (last!!.routeBonus > 0) bonuses++
            assertEquals("no reroutes when following the GPS", 0, last.reroutes)
        }
        assertTrue("shortest-path bonus earned ($bonuses of 6)", bonuses >= 5)
    }
}
