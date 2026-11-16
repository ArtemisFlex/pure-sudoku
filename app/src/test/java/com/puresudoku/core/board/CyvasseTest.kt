package com.puresudoku.core.board

import com.puresudoku.core.board.Cyvasse.Companion.BATTLE
import com.puresudoku.core.board.Cyvasse.Companion.CATAPULT
import com.puresudoku.core.board.Cyvasse.Companion.CROSSBOW
import com.puresudoku.core.board.Cyvasse.Companion.DRAGON
import com.puresudoku.core.board.Cyvasse.Companion.DRAWN
import com.puresudoku.core.board.Cyvasse.Companion.ELEPHANT
import com.puresudoku.core.board.Cyvasse.Companion.FOREST
import com.puresudoku.core.board.Cyvasse.Companion.H
import com.puresudoku.core.board.Cyvasse.Companion.HEAVY_HORSE
import com.puresudoku.core.board.Cyvasse.Companion.KEEP
import com.puresudoku.core.board.Cyvasse.Companion.KING
import com.puresudoku.core.board.Cyvasse.Companion.K_MOVE
import com.puresudoku.core.board.Cyvasse.Companion.K_PROMO
import com.puresudoku.core.board.Cyvasse.Companion.K_RANGED
import com.puresudoku.core.board.Cyvasse.Companion.K_VOLLEY
import com.puresudoku.core.board.Cyvasse.Companion.LIGHT_HORSE
import com.puresudoku.core.board.Cyvasse.Companion.MOUNTAIN
import com.puresudoku.core.board.Cyvasse.Companion.OVER
import com.puresudoku.core.board.Cyvasse.Companion.PLAIN
import com.puresudoku.core.board.Cyvasse.Companion.RABBLE
import com.puresudoku.core.board.Cyvasse.Companion.RIVER
import com.puresudoku.core.board.Cyvasse.Companion.SPEARMAN
import com.puresudoku.core.board.Cyvasse.Companion.TREBUCHET
import com.puresudoku.core.board.Cyvasse.Companion.W
import com.puresudoku.core.board.Cyvasse.Companion.WATER
import com.puresudoku.core.board.Cyvasse.Companion.aOf
import com.puresudoku.core.board.Cyvasse.Companion.bOf
import com.puresudoku.core.board.Cyvasse.Companion.kindOf
import com.puresudoku.core.board.Cyvasse.Companion.ownerOf
import com.puresudoku.core.board.Cyvasse.Companion.piece
import com.puresudoku.core.board.Cyvasse.Companion.typeOf
import com.puresudoku.core.board.Cyvasse.Companion.viaOf
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CyvasseTest {
    private fun at(r: Int, c: Int) = r * W + c
    /** An empty battlefield with both kings tucked away in corners, Alabaster to move. */
    private fun battle(): Cyvasse = Cyvasse.newSetup().also { g ->
        g.phase = BATTLE; g.turn = 0
        g.board[at(H - 1, W - 1)] = piece(0, KING); g.board[at(0, 0)] = piece(1, KING)
    }
    private fun Cyvasse.move(from: Int, to: Int, kind: Int = K_MOVE) = legalMoves().firstOrNull { kindOf(it) == kind && aOf(it) == from && bOf(it) == to }
    private fun Cyvasse.targets(from: Int) = legalMoves().filter { aOf(it) == from && kindOf(it) == K_MOVE }.map { bOf(it) }.toSet()

    @Test fun standardLayout() {
        val g = Cyvasse.standard()
        assertEquals(BATTLE, g.phase); assertEquals(0, g.turn)
        assertEquals(piece(0, KING), g.board[at(10, 5)]); assertEquals(piece(1, KING), g.board[at(0, 5)])
        for (c in 0 until W) {
            assertEquals(WATER, g.terrain[at(RIVER, c)])
            assertEquals(typeOf(g.board[at(10, c)]), typeOf(g.board[at(0, c)]))
            assertEquals(typeOf(g.board[at(9, c)]), typeOf(g.board[at(1, c)]))
        }
        assertEquals(at(7, 0), g.keepAt[0]); assertEquals(at(3, 0), g.keepAt[1])
        assertEquals(MOUNTAIN, g.block[at(6, 5)]); assertEquals(MOUNTAIN, g.block[at(4, 4)])
        assertEquals(20, g.onBoard(0)); assertEquals(20, g.onBoard(1)); assertEquals(0, g.reserveCount(0))
        assertTrue(g.legalMoves().isNotEmpty())
        // an old 8 by 8 save starts the new field
        val old = org.json.JSONObject().put("board", org.json.JSONArray(IntArray(64).toList()))
        assertEquals(piece(0, KING), Cyvasse.fromJson(old).board[at(10, 5)])
    }

    @Test fun neverCaptureYourOwnPieces() {
        val g = Cyvasse.standard()
        for (m in g.legalMoves()) for (v in g.victims(m)) assertNotEquals(0, ownerOf(g.board[v]))
        val h = battle(); h.board[at(6, 3)] = piece(0, KING - 1); h.board[at(6, 4)] = piece(0, RABBLE)
        assertNull(h.move(at(6, 3), at(6, 4)))
    }

    @Test fun kingMovesOneAndTakesAnything() {
        val g = battle(); g.board[at(10, 9)] = 0; g.board[at(8, 4)] = piece(0, KING)
        assertEquals(8, g.targets(at(8, 4)).size)
        g.board[at(7, 4)] = piece(1, ELEPHANT); assertNotNull(g.move(at(8, 4), at(7, 4)))
    }

    @Test fun kingCannotStepIntoCheckAndMustEscapeIt() {
        val g = battle()
        g.board[at(10, 9)] = 0; g.board[at(8, 4)] = piece(0, KING)
        g.board[at(2, 5)] = piece(1, DRAGON)
        // the dragon covers file f: the king may not step onto it
        assertNull(g.move(at(8, 4), at(8, 5))); assertNull(g.move(at(8, 4), at(7, 5)))
        assertNotNull(g.move(at(8, 4), at(8, 3)))
        g.board[at(2, 5)] = 0; g.board[at(2, 4)] = piece(1, DRAGON)
        assertTrue(g.inCheck(0))
        for (m in g.legalMoves()) { val c = g.copy(); c.play(m, check = false, settle = false); assertFalse(c.inCheck(0)) }
    }

    @Test fun checkmateEndsTheGameAndStalemateDraws() {
        val g = battle()
        g.board[at(10, 9)] = 0; g.board[at(10, 0)] = piece(0, KING)
        g.board[at(9, 4)] = piece(1, DRAGON); g.board[at(3, 3)] = piece(1, DRAGON)
        g.turn = 1
        // the dragon on e2 covers row 2; the other comes down to d1 and checks along the back row: mate
        val mate = g.move(at(3, 3), at(10, 3))!!
        assertTrue(g.play(mate))
        assertEquals(OVER, g.phase); assertEquals(1, g.winner)
        val s = battle()
        s.board[at(10, 9)] = 0; s.board[at(10, 0)] = piece(0, KING)
        s.board[at(6, 1)] = piece(1, DRAGON); s.turn = 1
        s.board[at(0, 0)] = 0; s.board[at(0, 9)] = piece(1, KING)
        // from b3 the dragon covers a2, b2 and b1 but not a1: no safe tile, yet not in check
        assertTrue(s.play(s.move(at(6, 1), at(8, 1))!!))
        assertEquals(OVER, s.phase); assertEquals(DRAWN, s.winner)
    }

    @Test fun catapultShootsDiagonallyAnyDistanceAndStepsStraight() {
        val g = battle(); g.board[at(10, 2)] = piece(0, CATAPULT); g.board[at(9, 3)] = piece(0, RABBLE); g.board[at(6, 6)] = piece(1, ELEPHANT)
        assertNotNull(g.move(at(10, 2), at(6, 6), K_RANGED))       // four tiles diagonally, over its own rabble
        g.board[at(6, 6)] = 0; g.board[at(10, 7)] = piece(1, RABBLE)
        assertNull(g.move(at(10, 2), at(10, 7), K_RANGED))          // never straight
        assertEquals(setOf(at(10, 1), at(10, 3), at(9, 2)), g.targets(at(10, 2)))   // one step straight, never diagonally
    }

    @Test fun trebuchetShootsStraightAnyDistance() {
        val t = battle(); t.board[at(10, 2)] = piece(0, TREBUCHET); t.board[at(9, 2)] = piece(0, RABBLE); t.board[at(2, 2)] = piece(1, RABBLE)
        // the river doesn't stop a shot; its own rabble is fired over
        assertNotNull(t.move(at(10, 2), at(2, 2), K_RANGED))
        t.board[at(8, 4)] = piece(1, RABBLE); assertNull(t.move(at(10, 2), at(8, 4), K_RANGED))
        t.terrain[at(10, 2)] = FOREST; assertNull(t.move(at(10, 2), at(2, 2), K_RANGED))
        assertEquals(setOf(at(10, 1), at(10, 3)), t.targets(at(10, 2)).filter { t.board[it] == 0 }.toSet())
    }

    @Test fun spearmanMovesTwoAnyWayAndTakesByLanding() {
        val g = battle(); g.board[at(8, 3)] = piece(0, SPEARMAN)
        assertNotNull(g.move(at(8, 3), at(6, 5)))
        assertNotNull(g.move(at(8, 3), at(6, 3)))
        g.board[at(7, 4)] = piece(1, RABBLE); assertNotNull(g.move(at(8, 3), at(7, 4)))
        assertTrue(g.play(g.move(at(8, 3), at(7, 4))!!)); assertEquals(piece(0, SPEARMAN), g.board[at(7, 4)])
    }

    @Test fun crossbowmanStepsStraightAndShootsDiagonalNeighbours() {
        val g = battle(); g.board[at(8, 4)] = piece(0, CROSSBOW)
        assertEquals(setOf(at(7, 4), at(9, 4), at(8, 3), at(8, 5)), g.targets(at(8, 4)))
        // stepping to e4 puts two enemies diagonally beside it: both are shot
        g.board[at(6, 3)] = piece(1, RABBLE); g.board[at(6, 5)] = piece(1, SPEARMAN)
        val m = g.move(at(8, 4), at(7, 4))!!
        assertEquals(setOf(at(6, 3), at(6, 5)), g.victims(m).toSet())
        assertTrue(g.play(m)); assertEquals(0, g.board[at(6, 3)]); assertEquals(0, g.board[at(6, 5)])
        // an enemy that lands diagonally beside it can be shot where it stands
        val h = battle(); h.board[at(8, 4)] = piece(0, CROSSBOW); h.board[at(7, 5)] = piece(1, RABBLE); h.board[at(7, 4)] = piece(1, RABBLE)
        val volley = h.legalMoves().first { kindOf(it) == K_VOLLEY && aOf(it) == at(8, 4) }
        assertEquals(listOf(at(7, 5)), h.victims(volley))
        assertTrue(h.play(volley)); assertEquals(0, h.board[at(7, 5)]); assertEquals(piece(1, RABBLE), h.board[at(7, 4)])
    }

    @Test fun lightHorseLeapsAndHeavyHorseIsBlocked() {
        val g = battle(); g.board[at(8, 4)] = piece(0, LIGHT_HORSE); g.board[at(7, 5)] = piece(1, RABBLE)
        assertTrue(at(6, 6) in g.targets(at(8, 4)))                // over the rabble
        assertTrue(at(7, 5) in g.targets(at(8, 4)))                // or onto it
        assertFalse(at(7, 4) in g.targets(at(8, 4)))               // never straight
        val h = battle(); h.board[at(9, 1)] = piece(0, HEAVY_HORSE); h.board[at(7, 3)] = piece(0, RABBLE)
        assertEquals(setOf(at(8, 0), at(8, 2), at(10, 0), at(10, 2)), h.targets(at(9, 1)))
        h.board[at(7, 3)] = 0
        assertTrue(at(6, 4) in h.targets(at(9, 1)))                // three tiles
    }

    @Test fun whoCanTakeElephantsAndHeavyHorses() {
        assertTrue(Cyvasse.beats(KING, ELEPHANT)); assertTrue(Cyvasse.beats(CATAPULT, ELEPHANT)); assertTrue(Cyvasse.beats(HEAVY_HORSE, ELEPHANT))
        assertFalse(Cyvasse.beats(SPEARMAN, ELEPHANT)); assertFalse(Cyvasse.beats(LIGHT_HORSE, ELEPHANT)); assertFalse(Cyvasse.beats(CROSSBOW, ELEPHANT)); assertFalse(Cyvasse.beats(RABBLE, ELEPHANT))
        assertFalse(Cyvasse.beats(RABBLE, HEAVY_HORSE)); assertTrue(Cyvasse.beats(SPEARMAN, HEAVY_HORSE))
        assertTrue(Cyvasse.beats(RABBLE, DRAGON))
        val g = battle(); g.board[at(8, 4)] = piece(0, RABBLE); g.board[at(7, 4)] = piece(1, ELEPHANT); g.board[at(7, 5)] = piece(1, HEAVY_HORSE)
        assertNull(g.move(at(8, 4), at(7, 4))); assertNull(g.move(at(8, 4), at(7, 5)))
    }

    @Test fun fortressHoldsOneAttackThenBecomesGrass() {
        val f = battle(); f.keepAt[1] = at(3, 3); f.block[at(3, 3)] = KEEP; f.board[at(3, 3)] = piece(1, RABBLE)
        f.board[at(4, 3)] = piece(0, SPEARMAN)
        assertTrue(f.play(f.move(at(4, 3), at(3, 3))!!))
        assertTrue(f.keepGone[1]); assertEquals(0, f.block[at(3, 3)]); assertEquals(PLAIN, f.terrain[at(3, 3)])
        assertEquals(piece(1, RABBLE), f.board[at(3, 3)]); assertEquals(1, f.captured[0][0])
        // a king inside holds out for two attacks...
        val k = battle(); k.board[at(0, 0)] = 0; k.keepAt[1] = at(3, 3); k.block[at(3, 3)] = KEEP; k.board[at(3, 3)] = piece(1, KING)
        k.board[at(4, 3)] = piece(0, SPEARMAN); assertTrue(k.play(k.move(at(4, 3), at(3, 3))!!))
        assertFalse(k.keepGone[1])
        // ...but a dragon brings it down at once
        val d = battle(); d.board[at(0, 0)] = 0; d.keepAt[1] = at(3, 3); d.block[at(3, 3)] = KEEP; d.board[at(3, 3)] = piece(1, KING)
        d.board[at(8, 3)] = piece(0, DRAGON); assertTrue(d.play(d.move(at(8, 3), at(3, 3))!!))
        assertTrue(d.keepGone[1]); assertEquals(piece(1, KING), d.board[at(3, 3)])
    }

    @Test fun dragonIsBlockedByMountainsPiecesAndEmptyFortresses() {
        val g = battle(); g.board[at(8, 3)] = piece(0, DRAGON)
        g.terrain[at(7, 3)] = WATER; g.terrain[at(6, 3)] = FOREST
        assertNotNull(g.move(at(8, 3), at(5, 3)))
        g.block[at(5, 3)] = MOUNTAIN; assertNull(g.move(at(8, 3), at(4, 3)))
        g.block[at(5, 3)] = 0; g.board[at(6, 3)] = piece(0, RABBLE); assertNull(g.move(at(8, 3), at(5, 3)))
        val f = battle(); f.board[at(8, 3)] = piece(0, DRAGON); f.keepAt[1] = at(6, 3); f.block[at(6, 3)] = KEEP
        assertNull(f.move(at(8, 3), at(6, 3))); assertNull(f.move(at(8, 3), at(5, 3)))
    }

    @Test fun rabbleIsPromotedOnTheEnemyBackRow() {
        val g = battle(); g.board[at(1, 4)] = piece(0, RABBLE); g.crossed[at(1, 4)] = 1
        val promos = g.legalMoves().filter { kindOf(it) == K_PROMO && aOf(it) == at(1, 4) && bOf(it) == at(0, 4) }
        assertEquals((SPEARMAN..DRAGON).toSet(), promos.map { viaOf(it) }.toSet())
        assertTrue(g.play(promos.first { viaOf(it) == DRAGON }))
        assertEquals(piece(0, DRAGON), g.board[at(0, 4)]); assertEquals(1, g.promoted[at(0, 4)])
        val restored = Cyvasse.fromJson(g.toJson()); assertEquals(1, restored.promoted[at(0, 4)])
    }

    @Test fun siegeEnginesStayHomeAndOutOfFortresses() {
        val g = battle(); g.board[at(6, 3)] = piece(0, CATAPULT)
        assertNull(g.move(at(6, 3), at(5, 3))); assertNotNull(g.move(at(6, 3), at(6, 4)))
        g.keepAt[0] = at(7, 3); g.block[at(7, 3)] = KEEP; assertNull(g.move(at(6, 3), at(7, 3)))
    }

    @Test fun theRiverHoldsAPieceATurnAndThereIsNoWayBack() {
        val g = Cyvasse.standard()
        for (c in 0 until W) assertEquals(WATER, g.terrain[at(RIVER, c)])
        val h = battle(); for (c in 0 until W) h.terrain[at(RIVER, c)] = WATER
        h.board[at(6, 3)] = piece(0, ELEPHANT)
        assertTrue(h.play(h.move(at(6, 3), at(5, 3))!!)); assertEquals(1, h.waterWait[at(5, 3)])
        h.turn = 0; assertTrue(h.legalMoves().none { aOf(it) == at(5, 3) })
        h.waterWait[at(5, 3)] = 0
        assertTrue(h.play(h.move(at(5, 3), at(4, 3))!!)); assertEquals(1, h.crossed[at(4, 3)]); h.turn = 0
        assertNull(h.move(at(4, 3), at(5, 3))); assertNotNull(h.move(at(4, 3), at(4, 4))); assertNotNull(h.move(at(4, 3), at(3, 3)))
    }

    @Test fun aiGamesFromTheStandardOpeningStayLegal() {
        var finished = 0
        for (seed in 0 until 4) {
            val rnd = Random(seed)
            var g = Cyvasse.standard()
            var plies = 0
            while (g.phase == BATTLE && plies < 100) {
                val m = CyvasseAi.best(g, if (seed % 2 == 0) 0 else 2, rnd)
                assertTrue("legal", g.isLegal(m))
                for (v in g.victims(m)) assertNotEquals(g.turn, ownerOf(g.board[v]))
                assertTrue(g.play(m))
                assertFalse("a move may not leave the mover in check", g.phase == BATTLE && g.inCheck(1 - g.turn))
                if (plies % 10 == 0) g = Cyvasse.fromJson(org.json.JSONObject(g.toJson().toString()))
                plies++
            }
            if (g.phase == OVER) finished++
        }
        println("Cyvasse AI games finished: $finished of 4")
    }
}
