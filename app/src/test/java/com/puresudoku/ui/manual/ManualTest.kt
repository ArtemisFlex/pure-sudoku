package com.puresudoku.ui.manual

import com.puresudoku.core.GameKind
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualTest {
    @Test fun everyGameHasAManual() {
        for (k in GameKind.entries) {
            if (com.puresudoku.ui.games.realm.Rulebooks.has(k)) continue
            val secs = ManualText.of(k)
            assertTrue("${k.name} manual is too short (${secs.size} sections)", secs.size >= 3)
        }
    }
}
