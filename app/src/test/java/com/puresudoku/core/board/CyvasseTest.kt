package com.puresudoku.core.board

import com.puresudoku.core.board.Cyvasse.Companion.BATTLE
import com.puresudoku.core.board.Cyvasse.Companion.CATAPULT
import com.puresudoku.core.board.Cyvasse.Companion.CROSSBOW
import com.puresudoku.core.board.Cyvasse.Companion.DRAGON
import com.puresudoku.core.board.Cyvasse.Companion.ELEPHANT
import com.puresudoku.core.board.Cyvasse.Companion.HEAVY_HORSE
import com.puresudoku.core.board.Cyvasse.Companion.KING
import com.puresudoku.core.board.Cyvasse.Companion.K_DEPLOY
import com.puresudoku.core.board.Cyvasse.Companion.K_MOVE
import com.puresudoku.core.board.Cyvasse.Companion.K_SWAP
import com.puresudoku.core.board.Cyvasse.Companion.LIGHT_HORSE
import com.puresudoku.core.board.Cyvasse.Companion.MOUNTAIN
import com.puresudoku.core.board.Cyvasse.Companion.RABBLE
import com.puresudoku.core.board.Cyvasse.Companion.SPEARMAN
import com.puresudoku.core.board.Cyvasse.Companion.TREBUCHET
import com.puresudoku.core.board.Cyvasse.Companion.mv
import com.puresudoku.core.board.Cyvasse.Companion.piece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CyvasseTest {
    /** An empty battle with Alabaster to move; no keeps, so nothing can be brought out. */
    private fun empty(): Cyvasse {
        val g = Cyvasse()
        for (s in 0..1) g.reserve[s].fill(0)
        g.phase = BATTLE; g.turn = 0
        return g
    }

    private fun Cyvasse.targets(from: Int) = legalMoves().filter { Cyvasse.kindOf(it) != K_DEPLOY && Cyvasse.aOf(it) == from }.map { Cyvasse.bOf(it) }.toSet()

    @Test fun setupRules() {
        val g = Cyvasse()
        assertFalse("no keep in the row by the screen", g.placeKeep(0, 55))
        assertTrue(g.placeKeep(0, 84))
        assertFalse("not on another side's half", g.toggleMountain(0, 20))
        assertFalse("not on water", g.toggleMountain(0, 55))
        for (c in intArrayOf(60, 66, 69)) assertTrue(g.toggleMountain(0, c))
        assertFalse("only three mountains", g.toggleMountain(0, 70))
        assertTrue("pieces stand on home tiles", g.standPiece(0, KING, 94))
        assertFalse("but not elsewhere", g.standPiece(0, DRAGON, 70))
        assertEquals(0, g.reserve[0][KING])
        // moving the keep sends pieces off the new home tiles back to reserve
        assertTrue(g.placeKeep(0, 81))
        assertEquals(1, g.reserve[0][KING])
        assertTrue(g.ready(0, coin = false))
        assertFalse("Onyx isn't finished", g.ready(1, coin = false))
        g.randomSetup(1, Random(3)); assertTrue(g.ready(1, coin = true))
        assertEquals(BATTLE, g.phase); assertEquals(1, g.turn)
    }

    @Test fun movement() {
        val g = empty()
        // a light horse rides off the left edge and on from the right
        g.board[50] = piece(0, LIGHT_HORSE)
        assertTrue(59 in g.targets(50))
        // water stops everything but the dragon
        g.board[50] = 0; g.board[53] = piece(0, ELEPHANT)   // (5,3); water at (5,5)
        assertTrue(55 in g.targets(53)); assertFalse(56 in g.targets(53)); assertFalse(57 in g.targets(53))
        g.board[53] = 0; g.board[52] = piece(0, DRAGON)
        assertTrue(57 in g.targets(52))
        // the dragon flies over mountains; others are stopped
        g.block[54] = MOUNTAIN
        assertTrue(55 in g.targets(52)); assertFalse(54 in g.targets(52))
        g.board[52] = piece(0, ELEPHANT)
        assertTrue(g.targets(52).none { it in 54..59 })
        // heavy horses ride past their own pieces
        g.block[54] = 0; g.board[52] = 0
        g.board[90] = piece(0, HEAVY_HORSE); g.board[80] = piece(0, RABBLE)
        assertTrue(70 in g.targets(90)); assertFalse(80 in g.targets(90))
        // crossbowmen leap into any empty forest
        g.board[95] = piece(0, CROSSBOW)
        assertTrue(Cyvasse.FORESTS.all { it in g.targets(95) })
    }

    @Test fun elephantNeedsTwoAttackers() {
        val g = empty()
        g.board[44 - 20] = piece(1, ELEPHANT)   // (2,4)
        g.board[64 - 20] = piece(0, LIGHT_HORSE) // (4,4) below it, in line
        assertFalse(24 in g.targets(44))
        g.board[25] = piece(0, RABBLE)            // beside the elephant
        assertTrue(24 in g.targets(44))
    }

    @Test fun catapultTakesTwoAndTrebuchetShootsOver() {
        val g = empty()
        g.board[99] = piece(0, CATAPULT)
        g.board[88] = piece(1, RABBLE); g.board[66] = piece(1, SPEARMAN)
        val double = mv(K_MOVE, 99, 66, 88)
        assertTrue(g.isLegal(double)); assertTrue(g.isLegal(mv(K_MOVE, 99, 88)))
        g.play(double)
        assertEquals(piece(0, CATAPULT), g.board[66]); assertEquals(0, g.board[88]); assertEquals(2, g.captured[0].sum())

        val t = empty()
        t.board[90] = piece(0, TREBUCHET); t.board[81] = piece(0, RABBLE); t.board[72] = piece(1, RABBLE); t.board[63] = piece(1, KING)
        assertTrue(t.isLegal(mv(K_MOVE, 90, 63)))
        assertFalse("no plain move past a piece", 54 in t.targets(90))
        t.play(mv(K_MOVE, 90, 63))
        assertEquals(0, t.winner); assertEquals(Cyvasse.OVER, t.phase)
    }

    @Test fun rabbleBonusAndSpearmanSwap() {
        val g = empty()
        g.board[90] = piece(0, RABBLE); g.board[93] = piece(0, RABBLE); g.board[96] = piece(0, SPEARMAN); g.board[97] = piece(0, KING)
        g.board[5] = piece(1, KING)
        assertTrue(g.play(mv(K_MOVE, 90, 80)))
        assertEquals("still Alabaster's turn", 0, g.turn)
        assertFalse("not the same rabble again", g.isLegal(mv(K_MOVE, 80, 70)))
        assertTrue(g.play(mv(K_MOVE, 93, 83)))
        assertEquals(1, g.turn)
        g.turn = 0
        assertTrue(g.play(mv(K_SWAP, 96, 97)))
        assertEquals(piece(0, KING), g.board[96])
    }

    @Test fun takingTwoHomeTilesBringsTheKeepDown() {
        val g = empty()
        g.keepAt[1] = 14; g.block[14] = Cyvasse.KEEP
        g.reserve[1][DRAGON] = 1; g.reserve[1][KING] = 1
        g.board[13] = piece(0, RABBLE); g.board[35] = piece(0, LIGHT_HORSE); g.board[90] = piece(0, KING)
        g.board[0] = piece(1, RABBLE)
        // one piece on a home tile isn't enough
        assertTrue(g.keepStands(1))
        g.play(mv(K_MOVE, 35, 25))
        assertFalse(g.keepStands(1))
        assertEquals("the king was in the keep", 0, g.winner)
        assertEquals(1, g.captured[0][DRAGON])
    }

    /** From Medium up the computer sees a rider lined up on its king and gets out of the way. */
    @Test fun searchDodgesThreatToKing() {
        for (level in 1..5) {
            val g = empty()
            g.turn = 1
            g.board[3] = piece(1, KING); g.board[9] = piece(1, RABBLE)
            g.board[93] = piece(0, LIGHT_HORSE); g.board[99] = piece(0, KING)
            // Alabaster's horse on d1 rides down off the edge and onto d10: Onyx must move or shield the king
            val m = CyvasseAi.best(g.copy(), level, Random(level))
            g.play(m)
            assertTrue("level $level: ${g.lastText}", g.legalMoves().none { mm -> g.victims(mm).any { Cyvasse.typeOf(g.board[it]) == KING } })
        }
    }

    @Test fun carefulSetupsShieldTheKing() {
        repeat(10) { seed ->
            val g = Cyvasse()
            CyvasseAi.setup(g, 0, 3, Random(seed)); g.ready(0, false)
            assertEquals(0, g.openLines(0))
            CyvasseAi.setup(g, 1, 4, Random(seed + 50))
            assertEquals(0, g.openLines(1))
        }
    }

    /** Computer against computer at every level: every move legal, games end, saves round-trip. */
    @Test fun aiGamesFinish() {
        var decided = 0
        for (level in 0..3) repeat(3) { seed ->
            val rnd = Random(seed * 31 + level)
            var g = Cyvasse()
            CyvasseAi.setup(g, 0, level, rnd); assertTrue(g.ready(0, rnd.nextBoolean()))
            CyvasseAi.setup(g, 1, level, rnd); assertTrue(g.ready(1, rnd.nextBoolean()))
            var guard = 0
            while (g.phase == BATTLE && guard < 400) {
                val m = CyvasseAi.best(g.copy(), level, rnd)
                assertTrue("legal: ${g.describe(m)}", g.play(m))
                if (guard % 17 == 0) g = Cyvasse.fromJson(org.json.JSONObject(g.toJson().toString()))
                guard++
            }
            if (g.phase == Cyvasse.OVER && g.winner in 0..1) decided++
        }
        assertTrue("most games are won by someone ($decided of 12)", decided >= 8)
    }

    /** A stronger computer beats a weaker one most of the time. */
    @Test fun strongerLevelsWin() {
        var strong = 0; var games = 0
        repeat(6) { seed ->
            val rnd = Random(seed + 100)
            val g = Cyvasse()
            CyvasseAi.setup(g, 0, 3, rnd); g.ready(0, false)
            CyvasseAi.setup(g, 1, 3, rnd); g.ready(1, seed % 2 == 0)
            val levels = if (seed % 2 == 0) intArrayOf(3, 0) else intArrayOf(0, 3)
            var guard = 0
            while (g.phase == BATTLE && guard++ < 400) g.play(CyvasseAi.best(g.copy(), levels[g.turn], rnd))
            games++
            if (g.winner in 0..1 && levels[g.winner] == 3) strong++
        }
        assertTrue("Expert beat Easy $strong of $games", strong >= 5)
    }
}
