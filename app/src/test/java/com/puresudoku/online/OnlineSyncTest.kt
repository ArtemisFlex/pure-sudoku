package com.puresudoku.online

import com.puresudoku.core.Difficulty
import com.puresudoku.ui.games.Games
import com.puresudoku.ui.games.board.DuelSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Online play sends the whole game after each turn; the other device must rebuild exactly the same game. */
class OnlineSyncTest {
    @Test fun everyOnlineGameSurvivesTheTrip() {
        for (kind in onlineGames) {
            val s = Games.of(kind).generate(Difficulty.EASY) as DuelSession
            s.localTwoPlayer = true
            val sent = s.toJson()
            val got = Games.of(kind).restore(org.json.JSONObject(sent.toString())) as DuelSession
            assertTrue("$kind keeps pass-and-play", got.localTwoPlayer)
            assertEquals("$kind state", sent.getJSONObject("state").toString(), got.toJson().getJSONObject("state").toString())
            assertEquals("$kind seat to move", s.tableSeat, got.tableSeat)
        }
    }
}
