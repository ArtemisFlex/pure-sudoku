package com.puresudoku.core.board

import com.puresudoku.core.board.Cyvasse.Companion.BATTLE
import com.puresudoku.core.board.Cyvasse.Companion.CATAPULT
import com.puresudoku.core.board.Cyvasse.Companion.CROSSBOW
import com.puresudoku.core.board.Cyvasse.Companion.DRAGON
import com.puresudoku.core.board.Cyvasse.Companion.DRAWN
import com.puresudoku.core.board.Cyvasse.Companion.ELEPHANT
import com.puresudoku.core.board.Cyvasse.Companion.FOREST
import com.puresudoku.core.board.Cyvasse.Companion.HEAVY_HORSE
import com.puresudoku.core.board.Cyvasse.Companion.KEEP
import com.puresudoku.core.board.Cyvasse.Companion.KING
import com.puresudoku.core.board.Cyvasse.Companion.K_MOVE
import com.puresudoku.core.board.Cyvasse.Companion.K_PROMO
import com.puresudoku.core.board.Cyvasse.Companion.K_RANGED
import com.puresudoku.core.board.Cyvasse.Companion.LIGHT_HORSE
import com.puresudoku.core.board.Cyvasse.Companion.MOUNTAIN
import com.puresudoku.core.board.Cyvasse.Companion.N
import com.puresudoku.core.board.Cyvasse.Companion.OVER
import com.puresudoku.core.board.Cyvasse.Companion.RABBLE
import com.puresudoku.core.board.Cyvasse.Companion.SPEARMAN
import com.puresudoku.core.board.Cyvasse.Companion.TREBUCHET
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
    private fun at(r: Int, c: Int) = r * N + c
    /** An empty battlefield with both kings tucked away in corners, Alabaster to move. */
    private fun battle(): Cyvasse = Cyvasse.newSetup().also { g ->
        g.phase = BATTLE; g.turn = 0
        g.board[at(7, 7)] = piece(0, KING); g.board[at(0, 0)] = piece(1, KING)
    }
    private fun Cyvasse.move(from: Int, to: Int, kind: Int = K_MOVE) = legalMoves().firstOrNull { kindOf(it) == kind && aOf(it) == from && bOf(it) == to }

    @Test fun standardLayoutIsMirrored() {
        val g = Cyvasse.standard()
        assertEquals(BATTLE, g.phase); assertEquals(0, g.turn)
        assertEquals(piece(0, KING), g.board[at(7, 4)]); assertEquals(piece(1, KING), g.board[at(0, 4)])
        for (c in 0 until N) {
            assertEquals(typeOf(g.board[at(7, c)]), typeOf(g.board[at(0, c)]))
            assertEquals(g.block[at(4, c)], g.block[at(3, c)]); assertEquals(g.terrain[at(5, c)], g.terrain[at(2, c)])
        }
        assertEquals(at(6, 0), g.keepAt[0]); assertEquals(at(1, 0), g.keepAt[1])
        assertEquals(10, g.onBoard(0)); assertEquals(10, g.onBoard(1)); assertEquals(0, g.reserveCount(0))
        assertTrue(g.legalMoves().isNotEmpty())
    }

    @Test fun neverCaptureYourOwnPieces() {
        val g = Cyvasse.standard()
        for (m in g.legalMoves()) for (v in g.victims(m)) assertNotEquals(0, ownerOf(g.board[v]))
        val h = battle(); h.board[at(5, 3)] = piece(0, HEAVY_HORSE); h.board[at(5, 4)] = piece(0, RABBLE)
        assertNull(h.move(at(5, 3), at(5, 4)))
    }

    @Test fun kingCannotStepIntoCheckAndMustEscapeIt() {
        val g = battle()
        g.board[at(7, 7)] = 0; g.board[at(6, 4)] = piece(0, KING)
        g.board[at(4, 6)] = piece(1, CATAPULT) // fires along row 4? no: place a rook-like threat with the dragon
        g.board[at(4, 6)] = 0; g.board[at(2, 5)] = piece(1, DRAGON)
        // the dragon covers file f: the king may not step to f7 or f6
        assertNull(g.move(at(6, 4), at(6, 5))); assertNull(g.move(at(6, 4), at(5, 5)))
        assertNotNull(g.move(at(6, 4), at(6, 3)))
        // put the king in check: every legal move must end it
        g.board[at(2, 5)] = 0; g.board[at(2, 4)] = piece(1, DRAGON)
        assertTrue(g.inCheck(0))
        for (m in g.legalMoves()) { val c = g.copy(); c.play(m, check = false, settle = false); assertFalse(c.inCheck(0)) }
    }

    @Test fun checkmateEndsTheGameAndStalemateDraws() {
        val g = battle()
        g.board[at(7, 7)] = 0; g.board[at(7, 0)] = piece(0, KING)
        g.board[at(5, 1)] = piece(1, DRAGON); g.board[at(2, 3)] = piece(1, DRAGON)
        g.turn = 1
        // the dragon on b3 covers a2, b2 and b1; the other comes down to d1 and checks along the back row: mate
        val mate = g.move(at(2, 3), at(7, 3))!!
        assertTrue(g.play(mate))
        assertEquals(OVER, g.phase); assertEquals(1, g.winner)
        val s = battle()
        s.board[at(7, 7)] = 0; s.board[at(7, 0)] = piece(0, KING)
        s.board[at(4, 2)] = piece(1, DRAGON); s.turn = 1
        s.board[at(0, 0)] = 0; s.board[at(0, 7)] = piece(1, KING)
        // from c2 the dragon covers a2, b2 and b1 but not a1: no safe square, yet not in check
        assertTrue(s.play(s.move(at(4, 2), at(6, 2))!!))
        assertEquals(OVER, s.phase); assertEquals(DRAWN, s.winner)
    }

    @Test fun dragonIsBlockedByMountainsPiecesAndEmptyFortresses() {
        val g = battle(); g.board[at(5, 3)] = piece(0, DRAGON)
        g.terrain[at(4, 3)] = WATER; g.terrain[at(3, 3)] = FOREST
        assertNotNull(g.move(at(5, 3), at(2, 3)))                 // over water and forest
        g.block[at(2, 3)] = MOUNTAIN; assertNull(g.move(at(5, 3), at(1, 3)))
        g.block[at(2, 3)] = 0; g.board[at(3, 3)] = piece(0, RABBLE); assertNull(g.move(at(5, 3), at(2, 3)))
        val f = battle(); f.board[at(5, 3)] = piece(0, DRAGON); f.keepAt[1] = at(3, 3); f.block[at(3, 3)] = KEEP
        assertNull(f.move(at(5, 3), at(3, 3)))                    // an empty fortress: can't land
        assertNull(f.move(at(5, 3), at(2, 3)))                    // nor fly past it
        f.board[at(3, 3)] = piece(1, RABBLE); assertNotNull(f.move(at(5, 3), at(3, 3)))
        assertTrue(f.play(f.move(at(5, 3), at(3, 3))!!))
        assertTrue(f.keepGone[1]); assertEquals(1, f.captured[0][0]); assertEquals(piece(1, RABBLE), f.board[at(3, 3)])
    }

    @Test fun rangesAndFiringOverAllies() {
        val g = battle(); g.board[at(7, 2)] = piece(0, CATAPULT); g.board[at(6, 2)] = piece(0, RABBLE); g.board[at(3, 2)] = piece(1, ELEPHANT)
        assertNotNull(g.move(at(7, 2), at(3, 2), K_RANGED))       // four tiles, over its own rabble
        g.board[at(3, 2)] = 0; g.board[at(2, 2)] = piece(1, ELEPHANT); assertNull(g.move(at(7, 2), at(2, 2), K_RANGED))
        val t = battle(); t.board[at(7, 2)] = piece(0, TREBUCHET); t.board[at(4, 2)] = piece(1, RABBLE)
        assertNotNull(t.move(at(7, 2), at(4, 2), K_RANGED)); t.terrain[at(7, 2)] = FOREST; assertNull(t.move(at(7, 2), at(4, 2), K_RANGED))
        val x = battle(); x.board[at(6, 2)] = piece(0, CROSSBOW); x.board[at(5, 2)] = piece(0, RABBLE); x.board[at(4, 2)] = piece(1, RABBLE)
        assertNull(x.move(at(6, 2), at(4, 2), K_RANGED))          // a crossbow reaches one tile
        x.board[at(5, 3)] = piece(1, RABBLE); assertNotNull(x.move(at(6, 2), at(5, 3), K_RANGED))
    }

    @Test fun spearmanMovesTwoButAttacksDiagonally() {
        val g = battle(); g.board[at(6, 3)] = piece(0, SPEARMAN)
        assertNotNull(g.move(at(6, 3), at(4, 3)))
        g.board[at(5, 3)] = piece(1, RABBLE); assertNull(g.move(at(6, 3), at(5, 3)))
        g.board[at(5, 4)] = piece(1, RABBLE); assertNotNull(g.move(at(6, 3), at(5, 4)))
    }

    @Test fun rabbleIsPromotedOnTheEnemyBackRow() {
        val g = battle(); g.board[at(1, 4)] = piece(0, RABBLE); g.crossed[at(1, 4)] = 1
        val promos = g.legalMoves().filter { kindOf(it) == K_PROMO && aOf(it) == at(1, 4) && bOf(it) == at(0, 4) }
        assertEquals((SPEARMAN..DRAGON).toSet(), promos.map { viaOf(it) }.toSet())
        assertTrue(g.play(promos.first { viaOf(it) == DRAGON }))
        assertEquals(piece(0, DRAGON), g.board[at(0, 4)]); assertEquals(1, g.promoted[at(0, 4)])
        val restored = Cyvasse.fromJson(g.toJson()); assertEquals(1, restored.promoted[at(0, 4)])
    }

    @Test fun siegeEnginesNeverCrossTheRiverOrEnterAFortress() {
        val g = battle(); g.board[at(4, 3)] = piece(0, CATAPULT)
        assertNull(g.move(at(4, 3), at(3, 3))); assertNotNull(g.move(at(4, 3), at(4, 4)))
        g.keepAt[0] = at(5, 3); g.block[at(5, 3)] = KEEP; assertNull(g.move(at(4, 3), at(5, 3)))
        g.board[at(4, 4)] = piece(0, ELEPHANT); assertNotNull(g.move(at(4, 4), at(5, 3)))
    }

    @Test fun groundUnitsAcrossTheRiverDontStepBack() {
        val g = battle(); g.board[at(4, 3)] = piece(0, HEAVY_HORSE)
        assertTrue(g.play(g.move(at(4, 3), at(3, 3))!!))
        assertEquals(1, g.crossed[at(3, 3)]); g.turn = 0
        assertNull(g.move(at(3, 3), at(4, 3))); assertNull(g.move(at(3, 3), at(4, 4)))
        assertNotNull(g.move(at(3, 3), at(3, 4))); assertNotNull(g.move(at(3, 3), at(2, 3)))
    }

    @Test fun aiGamesFromTheStandardOpeningStayLegal() {
        var finished = 0
        for (seed in 0 until 6) {
            val rnd = Random(seed)
            var g = Cyvasse.standard()
            var plies = 0
            while (g.phase == BATTLE && plies < 120) {
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
        println("Cyvasse AI games finished: $finished of 6")
    }
}
