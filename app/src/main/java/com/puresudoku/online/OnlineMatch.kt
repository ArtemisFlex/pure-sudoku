package com.puresudoku.online

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.puresudoku.core.GameKind
import com.puresudoku.platform.ONLINE_API
import com.puresudoku.platform.PeerLink
import com.puresudoku.platform.httpRequest
import com.puresudoku.platform.nowMillis
import com.puresudoku.platform.onMainLater
import org.json.JSONObject

/** Games that can be played online with a room code: every game with two-player pass-and-play. */
val onlineGames get() = com.puresudoku.core.localTwoPlayerGames

/** Games where each player has cards or plans the other mustn't see: covered during the other's turn. */
val hiddenInfoGames = setOf(
    GameKind.GO_FISH, GameKind.CRAZY_EIGHTS, GameKind.CUTTLE, GameKind.REALMFORGE, GameKind.REALM_DRAGON, GameKind.REALM_NIGHT,
    GameKind.REALM_ASH, GameKind.WISTERIA, GameKind.ASHFALL, GameKind.KREASHUN, GameKind.VEILGUARD,
    // nothing secret here, but the board stays readable (tap tiles) while you wait
    GameKind.DEED_DUNGEON,
    GameKind.SEA_BATTLE, GameKind.CRATEBARGE,
    // setups behind the screen and reserves are secret; the board stays readable (tap pieces) while you wait
    GameKind.CYVASSE,
)

/** Boards with plain round pieces, turned round for the guest so their side is at the bottom. */
val turnForGuest = setOf(GameKind.CHECKERS, GameKind.GO, GameKind.GOMOKU, GameKind.MORRIS)

/**
 * One online game between two devices.
 *
 * The host gets a 4-letter room code from the room-code service and shares it; the guest types it in.
 * The two devices swap their connection details through the service, then talk directly (WebRTC).
 * If a direct link can't be made, or drops, messages go through the service instead (a few bytes
 * per move, picked up every second or two). The host plays seat 1, the guest seat 2.
 */
object OnlineMatch {
    enum class Stage { IDLE, SETTING_UP, WAITING, JOINING, CONNECTING, PLAYING, ERROR }

    var stage by mutableStateOf(Stage.IDLE); private set
    var code by mutableStateOf(""); private set
    var error by mutableStateOf(""); private set
    var kind: GameKind? = null; private set
    var seat = 0; private set
    /** True while messages go through the room-code service instead of directly. */
    var viaService by mutableStateOf(false); private set

    /** Set by the app: a connection is ready, start the game for [kind] in [seat]. */
    var onStart: (GameKind, Int) -> Unit = { _, _ -> }
    /** Set by the game: a message from the other player. */
    var onMessage: (String) -> Unit = {}

    /** Sent in place of connection details by a device that can't make direct links. */
    private const val NO_LINK = "-"

    private var gen = 0
    private var direct = false
    private var sent = 0
    private var lastSeen = 0
    private var serviceFrom = 0
    private var started = 0L

    // ------------------------------------------------------------------ starting

    /** Opens a room for [k] and waits for a friend to join with its code. */
    fun host(k: GameKind) {
        reset(); kind = k; seat = 1
        stage = Stage.SETTING_UP
        listen()
        PeerLink.host()
    }

    /** Joins the room with [roomCode]. */
    fun join(roomCode: String) {
        reset(); seat = 2
        code = roomCode.trim().uppercase()
        stage = Stage.JOINING
        val g = gen
        get("op=offer&code=$code") { status, o ->
            if (g != gen) return@get
            when {
                status == 404 -> fail("No game with that code. Check the letters, or ask your friend for a new code.")
                o == null -> fail(offlineText(status))
                else -> {
                    kind = GameKind.entries.firstOrNull { it.name == o.optString("kind") }
                    if (kind == null) { fail("That game isn't in this version of the app. Try updating."); return@get }
                    listen()
                    val offer = o.getString("offer")
                    if (offer == NO_LINK) onPeer("answer", NO_LINK) else PeerLink.join(offer)
                }
            }
        }
    }

    /** Leaves the game or the room; the other player is told if a game was on. */
    fun leave() {
        if (stage == Stage.PLAYING) send("bye")
        reset()
    }

    private fun reset() {
        gen++
        PeerLink.close()
        stage = Stage.IDLE; code = ""; error = ""; kind = null; seat = 0
        viaService = false; direct = false; sent = 0; lastSeen = 0; serviceFrom = 0
        onMessage = {}
    }

    private fun fail(text: String) { PeerLink.close(); error = text; stage = Stage.ERROR }

    private fun offlineText(status: Int) =
        if (status == 0) "Can't reach the internet. Check your connection and try again." else "Online play isn't available right now. Please try again later."

    // ------------------------------------------------------------------ the direct link

    private fun listen() {
        val g = gen
        PeerLink.setListener { type, data -> if (g == gen) onPeer(type, data) }
    }

    private fun onPeer(type: String, data: String) {
        when (type) {
            "offer" -> post(JSONObject().put("op", "create").put("kind", kind!!.name).put("offer", data)) { status, o ->
                val c = o?.optString("code").orEmpty()
                if (c.isEmpty()) { fail(offlineText(status)); return@post }
                code = c; stage = Stage.WAITING; started = nowMillis()
                pollAnswer(gen)
            }
            "answer" -> post(JSONObject().put("op", "answer").put("code", code).put("answer", data)) { status, o ->
                if (o == null) { fail(if (status == 404) "That game has closed. Ask your friend for a new code." else offlineText(status)); return@post }
                if (data == NO_LINK) begin(viaService = true) else connecting()
            }
            "open" -> { direct = true; if (stage == Stage.CONNECTING) begin() }
            "msg" -> receive(data)
            "close", "fail" -> if (stage == Stage.PLAYING) { direct = false; useService() } else if (stage == Stage.CONNECTING) begin(viaService = true)
            // This device can't make direct links: play through the service instead.
            "error" -> when (stage) {
                Stage.SETTING_UP -> onPeer("offer", NO_LINK)
                Stage.JOINING -> onPeer("answer", NO_LINK)
                else -> Unit
            }
        }
    }

    private fun pollAnswer(g: Int) {
        if (g != gen || stage != Stage.WAITING) return
        if (nowMillis() - started > 15 * 60_000L) { fail("Nobody joined in 15 minutes. Start a new game to get a fresh code."); return }
        get("op=answer&code=$code") { _, o ->
            if (g != gen || stage != Stage.WAITING) return@get
            val a = o?.optString("answer").orEmpty()
            when {
                a == NO_LINK -> begin(viaService = true)
                a.isNotEmpty() && a != "null" -> { PeerLink.answer(a); connecting() }
                else -> onMainLater(2000) { pollAnswer(g) }
            }
        }
    }

    /** Wait a little for the direct link; if it doesn't open, play through the service. */
    private fun connecting() {
        stage = Stage.CONNECTING
        val g = gen
        onMainLater(12_000) { if (g == gen && stage == Stage.CONNECTING) begin(viaService = true) }
    }

    private fun begin(viaService: Boolean = false) {
        if (viaService) useService()
        stage = Stage.PLAYING
        onStart(kind!!, seat)
    }

    // ------------------------------------------------------------------ messages

    /** Sends [payload] to the other player. */
    fun send(payload: String) {
        val frame = "${++sent}|$payload"
        if (direct) PeerLink.send(frame)
        if (viaService || !direct) post(JSONObject().put("op", "send").put("code", code).put("seat", seat).put("msg", frame)) { _, _ -> }
    }

    private fun receive(frame: String) {
        val bar = frame.indexOf('|')
        val n = frame.substring(0, maxOf(0, bar)).toIntOrNull() ?: return
        if (n <= lastSeen) return          // already had it the other way
        lastSeen = n
        val payload = frame.substring(bar + 1)
        val deliver = onMessage
        // The other player left: nothing more will come, so stop listening.
        if (payload == "bye") { gen++; PeerLink.close(); stage = Stage.IDLE }
        deliver(payload)
    }

    private fun useService() {
        if (viaService) return
        viaService = true
        pollService(gen)
    }

    private fun pollService(g: Int) {
        if (g != gen) return
        get("op=recv&code=$code&seat=$seat&from=$serviceFrom") { _, o ->
            if (g != gen) return@get
            val a = o?.optJSONArray("msgs")
            if (a != null) { serviceFrom += a.length(); for (i in 0 until a.length()) receive(a.getString(i)) }
            onMainLater(1500) { pollService(g) }
        }
    }

    // ------------------------------------------------------------------ the room-code service

    private fun get(query: String, done: (Int, JSONObject?) -> Unit) =
        httpRequest("GET", "$ONLINE_API?$query", null) { s, body -> done(s, if (s in 200..299) runCatching { JSONObject(body) }.getOrNull() else null) }

    private fun post(o: JSONObject, done: (Int, JSONObject?) -> Unit) =
        httpRequest("POST", ONLINE_API, o.toString()) { s, body -> done(s, if (s in 200..299) runCatching { JSONObject(body) }.getOrNull() else null) }
}
