// Stand-in for online play (see Stubs.kt): the Kreat test arena is you against the computer only, so
// the room-code service, the device-to-device link and the online screen are left out of this build.
package com.puresudoku.online

import com.puresudoku.core.GameKind

/** No game is played online here. */
val onlineGames: Set<GameKind> get() = emptySet()
/** Kreat's setup behind the screen is secret from the other player. */
val hiddenInfoGames = setOf(GameKind.CYVASSE)
val turnForGuest = emptySet<GameKind>()

object OnlineMatch {
    enum class Stage { IDLE, SETTING_UP, WAITING, JOINING, CONNECTING, PLAYING, ERROR }
    val stage = Stage.IDLE
    val code = ""
    val error = ""
    val kind: GameKind? = null
    val seat = 0
    val viaService = false
    var onStart: (GameKind, Int) -> Unit = { _, _ -> }
    var onMessage: (String) -> Unit = {}
    fun host(@Suppress("UNUSED_PARAMETER") k: GameKind) {}
    fun join(@Suppress("UNUSED_PARAMETER") roomCode: String, @Suppress("UNUSED_PARAMETER") expectedKind: GameKind? = null) {}
    fun leave() {}
    fun send(@Suppress("UNUSED_PARAMETER") payload: String) {}
}

data class GameInvite(val kind: GameKind, val code: String) {
    fun url(): String = ""
    companion object { fun parse(@Suppress("UNUSED_PARAMETER") game: String?, @Suppress("UNUSED_PARAMETER") room: String?): GameInvite? = null }
}
