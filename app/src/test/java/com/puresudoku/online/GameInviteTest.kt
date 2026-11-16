package com.puresudoku.online

import com.puresudoku.core.GameKind
import org.junit.Assert.*
import org.junit.Test

class GameInviteTest {
    @Test fun validInviteHasDirectLinkAndNormalizesCode() {
        val invite = GameInvite.parse("CYVASSE", "a2bc")!!
        assertEquals(GameKind.CYVASSE, invite.kind)
        assertEquals("A2BC", invite.code)
        assertEquals("https://puregames.app/?game=CYVASSE&room=A2BC", invite.url())
    }

    @Test fun malformedAndUnsupportedInvitesAreRejected() {
        assertNull(GameInvite.parse("SUDOKU", "ABCD"))
        assertNull(GameInvite.parse("CYVASSE", "A1BC"))
        assertNull(GameInvite.parse("CYVASSE", "ABCD&extra=1"))
        assertNull(GameInvite.parse("NOT_A_GAME", "ABCD"))
        assertNull(GameInvite.parse(null, null))
    }
}
