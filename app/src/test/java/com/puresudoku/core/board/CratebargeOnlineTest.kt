package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.online.hiddenInfoGames
import com.puresudoku.online.onlineGames
import com.puresudoku.ui.games.board.CratebargeSession
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CratebargeOnlineTest {
    private fun restore(s: CratebargeSession, seat: Int): CratebargeSession =
        CratebargeSession.restore(JSONObject(s.toJson().toString())).also { it.localTwoPlayer = true; it.onlineSeat = seat }

    @Test fun bothSeatsPlaceAndTakeTurnsAcrossSyncedSnapshots() {
        assertTrue(GameKind.CRATEBARGE in onlineGames)
        assertTrue(GameKind.CRATEBARGE in hiddenInfoGames)
        val host = CratebargeSession(Difficulty.EASY, Cratebarge.forLevel(0)).also { it.localTwoPlayer = true; it.onlineSeat = 1 }
        assertTrue(host.myMove); assertEquals(0, host.viewer)
        host.scatter(); host.ready()
        assertFalse(host.myMove); assertEquals(2, host.tableSeat)
        val guest = restore(host, 2)
        assertEquals(1, guest.viewer); assertTrue(guest.myMove)
        guest.scatter(); guest.ready()
        assertEquals(Cratebarge.BATTLE, guest.g.phase); assertFalse(guest.myMove)
        val hostTurn = restore(guest, 1)
        assertTrue(hostTurn.myMove); assertEquals(0, hostTurn.viewer)
        val target = hostTurn.g.shipCells(1, 0).first()
        hostTurn.fire(target)
        assertFalse(hostTurn.myMove)
        val guestTurn = restore(hostTurn, 2)
        assertTrue(guestTurn.myMove); assertEquals(Cratebarge.HIT, guestTurn.g.shots[0][target])
        assertEquals(1, guestTurn.viewer)
        val reply = guestTurn.g.shipCells(0, 0).first()
        guestTurn.fire(reply)
        val hostAgain = restore(guestTurn, 1)
        assertTrue(hostAgain.myMove); assertEquals(Cratebarge.HIT, hostAgain.g.shots[1][reply])
    }
}
