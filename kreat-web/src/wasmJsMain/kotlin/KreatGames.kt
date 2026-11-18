// The Kreat test site's game list: Kreat alone (it replaces the app's GameRegistry.kt).
package com.puresudoku.ui.games

import com.puresudoku.core.GameKind

object Games {
    val all: List<GameDef> by lazy { listOf(com.puresudoku.ui.games.board.CyvasseDef) }

    fun of(kind: GameKind): GameDef = all.first { it.kind == kind }
}
