package com.puresudoku.online

import com.puresudoku.core.GameKind

/** A link always opens the public web app, including when the sender uses Android. */
data class GameInvite(val kind: GameKind, val code: String) {
    fun url(): String = "https://puregames.app/?game=${kind.name}&room=$code"

    companion object {
        fun parse(game: String?, room: String?): GameInvite? {
            val kind = GameKind.entries.firstOrNull { it.name == game && it in onlineGames } ?: return null
            val code = room?.uppercase() ?: return null
            if (!code.matches(Regex("[A-Z2-9]{4}"))) return null
            return GameInvite(kind, code)
        }
    }
}
