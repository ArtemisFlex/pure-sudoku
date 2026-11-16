package com.puresudoku.core.board

import com.puresudoku.core.board.Cyvasse.Companion.BATTLE
import com.puresudoku.core.board.Cyvasse.Companion.CATAPULT
import com.puresudoku.core.board.Cyvasse.Companion.CROSSBOW
import com.puresudoku.core.board.Cyvasse.Companion.DRAGON
import com.puresudoku.core.board.Cyvasse.Companion.FOREST
import com.puresudoku.core.board.Cyvasse.Companion.HEAVY_HORSE
import com.puresudoku.core.board.Cyvasse.Companion.KEEP
import com.puresudoku.core.board.Cyvasse.Companion.KING
import com.puresudoku.core.board.Cyvasse.Companion.K_MOVE
import com.puresudoku.core.board.Cyvasse.Companion.K_RANGED
import com.puresudoku.core.board.Cyvasse.Companion.LIGHT_HORSE
import com.puresudoku.core.board.Cyvasse.Companion.MOUNTAIN
import com.puresudoku.core.board.Cyvasse.Companion.NEIGHBOURS
import com.puresudoku.core.board.Cyvasse.Companion.OVER
import com.puresudoku.core.board.Cyvasse.Companion.PASS
import com.puresudoku.core.board.Cyvasse.Companion.RABBLE
import com.puresudoku.core.board.Cyvasse.Companion.SPEARMAN
import com.puresudoku.core.board.Cyvasse.Companion.TREBUCHET
import com.puresudoku.core.board.Cyvasse.Companion.TYPES
import com.puresudoku.core.board.Cyvasse.Companion.WATER
import com.puresudoku.core.board.Cyvasse.Companion.ALL8
import com.puresudoku.core.board.Cyvasse.Companion.aOf
import com.puresudoku.core.board.Cyvasse.Companion.bOf
import com.puresudoku.core.board.Cyvasse.Companion.kindOf
import com.puresudoku.core.board.Cyvasse.Companion.piece
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CyvasseTest {
    private fun battle() = Cyvasse.newSetup().also { it.phase = BATTLE; it.turn = 0 }
    private fun Cyvasse.move(from: Int, to: Int, kind: Int = K_MOVE) = legalMoves().firstOrNull { kindOf(it) == kind && aOf(it) == from && bOf(it) == to }

    @Test fun randomSetupHasAllTerrainAndPieces() {
        val g = Cyvasse.newSetup()
        g.randomSetup(0, Random(3))
        assertEquals(6, g.mountainsPlaced(0)); assertEquals(6, g.terrainPlaced(0, FOREST)); assertEquals(5, g.terrainPlaced(0, WATER))
        assertEquals(10, g.onBoard(0)); assertEquals(0, g.reserveCount(0)); assertTrue(g.setupDone(0))
        for (t in TYPES) assertEquals(1, g.board.count { it == piece(0,t) })
        assertEquals(0, g.board[g.keepAt[0]])
    }

    @Test fun specialTileSetupAndWaterDelay() {
        val g = Cyvasse.newSetup(); assertTrue(g.toggleMountain(0, 32)); assertTrue(g.toggleTerrain(0, WATER, 33)); assertTrue(g.toggleTerrain(0, FOREST, 34)); assertTrue(g.placeKeep(0, 35))
        assertFalse(g.standPiece(0, KING, 32)); assertTrue(g.standPiece(0, DRAGON, 32)); assertTrue(g.standPiece(0, KING, 33)); assertTrue(g.standPiece(0, RABBLE, 34)); assertFalse(g.standPiece(0, SPEARMAN, 35))
        assertEquals(1, g.waterWait[33]); g.phase = BATTLE; g.turn = 0
        assertNull(g.move(33, 41)); assertNotNull(g.move(34, 42)); assertTrue(g.play(g.move(34,42)!!)); assertEquals(0, g.waterWait[33])
        val restored = Cyvasse.fromJson(g.toJson()); assertEquals(0, restored.waterWait[33]); assertEquals(g.board.toList(), restored.board.toList())
    }

    @Test fun ranksSpearmenAndFriendlyFire() {
        val g = battle(); g.board[27] = piece(0, LIGHT_HORSE); g.board[28] = piece(1, SPEARMAN); g.board[35] = piece(1, CATAPULT); g.board[26] = piece(0, RABBLE)
        assertNull(g.move(27,28)); assertNull(g.move(27,35)); assertNotNull(g.move(27,26))
        val c = battle(); c.board[27] = piece(0, CROSSBOW); c.board[28] = piece(1, DRAGON)
        assertNotNull(c.move(27,28,K_RANGED))
    }

    @Test fun waterAndForestLimitMovement() {
        val g = battle(); g.board[27] = piece(0, LIGHT_HORSE)
        // Surround the only exit with mountains so movement cannot find an alternate path.
        for (n in NEIGHBOURS[27]) if (n != 28) g.block[n] = MOUNTAIN
        g.terrain[28] = WATER; assertNotNull(g.move(27,28)); assertNull(g.move(27,29)); assertTrue(g.play(g.move(27,28)!!)); assertEquals(1,g.waterWait[28])
        g.turn = 0; assertNull(g.move(28,29)); assertTrue(g.play(PASS)); assertEquals(0,g.waterWait[28])
        val f = battle(); f.board[27] = piece(0,LIGHT_HORSE); for (n in NEIGHBOURS[27]) if (n != 28) f.block[n] = MOUNTAIN
        f.terrain[28] = FOREST; assertNotNull(f.move(27,29)); assertNull(f.move(27,30))
    }

    @Test fun kingFortressSurvivesTwoAndDragonRuinsInOne() {
        val g = battle(); g.keepAt[1] = 27; g.block[27] = KEEP; g.board[27] = piece(1,KING); g.board[28] = piece(0,KING)
        repeat(2) { assertTrue(g.play(g.move(28,27)!!)); assertEquals(piece(1,KING),g.board[27]); g.turn = 0 }
        assertTrue(g.keepGone[1]); assertEquals(BATTLE,g.phase); assertTrue(g.play(g.move(28,27)!!)); assertEquals(OVER,g.phase); assertEquals(0,g.winner)
        val d = battle(); d.keepAt[1]=27; d.block[27]=KEEP; d.board[27]=piece(1,KING); d.board[28]=piece(0,DRAGON)
        assertTrue(d.play(d.move(28,27)!!)); assertTrue(d.keepGone[1]); assertEquals(piece(1,KING),d.board[27]); assertEquals(BATTLE,d.phase)
    }

    @Test fun rangedAndDragonRules() {
        val g = battle(); g.board[27]=piece(0,TREBUCHET); g.board[30]=piece(1,RABBLE)
        assertNull(g.move(27,30,K_RANGED)); g.facing[27] = ALL8.indexOfFirst { it[0] == 0 && it[1] == 1 } + 1
        assertNotNull(g.move(27,30,K_RANGED)); g.block[28]=MOUNTAIN; assertNull(g.move(27,30,K_RANGED))
        val d=battle(); d.board[27]=piece(0,DRAGON); d.board[29]=piece(1,RABBLE); d.terrain[29]=FOREST
        assertNull(d.move(27,29)); assertNotNull(d.move(27,30)); d.board[29]=piece(1,DRAGON); assertNull(d.move(27,30))
    }

    @Test fun noTurnLimitAndNoForcedLoss() {
        val g=battle(); g.board[0]=piece(0,KING); g.board[63]=piece(1,KING); g.quiet=10000
        assertTrue(g.play(g.move(0,1)!!)); assertEquals(BATTLE,g.phase); assertEquals(-1,g.winner)
        val stuck=battle(); stuck.board[0]=piece(0,KING); for(n in NEIGHBOURS[0]) stuck.block[n]=MOUNTAIN
        assertArrayEquals(intArrayOf(PASS),stuck.legalMoves()); assertTrue(stuck.play(PASS)); assertEquals(BATTLE,stuck.phase)
    }
}
