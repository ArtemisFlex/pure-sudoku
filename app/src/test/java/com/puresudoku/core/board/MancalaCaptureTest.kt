package com.puresudoku.core.board

import org.junit.Assert.assertEquals
import org.junit.Test

class MancalaCaptureTest {
    /** Player 2 (top row, pits 7..12) captures just like Player 1 when the last seed lands in an empty pit of theirs. */
    @Test fun playerTwoCaptures() {
        // Player 2 to move: one seed in pit 8 lands in empty pit 9; the pit opposite (12 - 9 = 3) holds 5.
        val pits = IntArray(14)
        pits[8] = 1; pits[3] = 5; pits[0] = 1; pits[12] = 1
        val g = Mancala(pits, turn = 1)
        g.sow(8)
        assertEquals("pit 9 emptied", 0, g.pits[9])
        assertEquals("opposite pit emptied", 0, g.pits[3])
        assertEquals("both go to Player 2's store", 6, g.pits[13])
        assertEquals("turn passes to Player 1", 0, g.turn)
    }

    @Test fun playerOneCaptures() {
        // Player 1: one seed in pit 1 lands in empty pit 2; opposite (12 - 2 = 10) holds 4.
        val pits = IntArray(14)
        pits[1] = 1; pits[10] = 4; pits[7] = 1; pits[5] = 1
        val g = Mancala(pits, turn = 0)
        g.sow(1)
        assertEquals(0, g.pits[2]); assertEquals(0, g.pits[10])
        assertEquals("both go to Player 1's store", 5, g.pits[6])
    }
}
