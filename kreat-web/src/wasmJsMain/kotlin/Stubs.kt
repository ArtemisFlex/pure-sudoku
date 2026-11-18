// Stand-ins for the few other games the shared app screens mention by name. The Kreat site never shows those
// games (its game list holds Kreat alone), so none of these is ever used; they only let the shared code compile
// without the other games' code, which keeps this build fast.
package com.puresudoku.ui.games.board

import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.ui.session.Outcome
import org.json.JSONObject

/** Cratebarge's session, as the app's save and resume code refers to it. */
abstract class CratebargeSession(difficulty: Difficulty) : DuelSession(GameKind.CRATEBARGE, difficulty) {
    class Profile { fun toJson() = JSONObject() }
    val profile = Profile()
    fun loadProfile(@Suppress("UNUSED_PARAMETER") o: JSONObject?) {}
    fun settleVoyage(@Suppress("UNUSED_PARAMETER") result: Outcome) {}
}
