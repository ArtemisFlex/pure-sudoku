// Platform layer (browser): online play's two connections.
//
// The direct device-to-device link is the shared rtc.js (WebRTC), loaded beside the page.
// The room-code service is on the same site, reached with fetch.
package com.puresudoku.platform

/** Where the room-code service lives (same site as the page). */
const val ONLINE_API = "/api/room"

@JsFun("""(method, url, body, done) => {
    const init = { method, cache: 'no-store' };
    if (body !== null) { init.body = body; init.headers = { 'Content-Type': 'application/json' }; }
    fetch(url, init).then(r => r.text().then(t => done(r.status, t))).catch(() => done(0, ''));
}""")
private external fun jsFetch(method: String, url: String, body: String?, done: (Int, String) -> Unit)

/** A request to the room-code service; [done] gets (status, body), status 0 if offline. */
fun httpRequest(method: String, url: String, body: String?, done: (Int, String) -> Unit) = jsFetch(method, url, body, done)

@JsFun("""(done) => {
    if (globalThis.PgRtc) return done();
    const s = document.createElement('script'); s.src = 'rtc.js'; s.onload = () => done(); s.onerror = () => done();
    document.head.appendChild(s);
}""")
private external fun loadRtc(done: () -> Unit)

@JsFun("(f) => globalThis.PgRtc && PgRtc.setListener((t, d) => f(String(t), String(d)))")
private external fun rtcListen(f: (String, String) -> Unit)
@JsFun("() => !!globalThis.PgRtc") private external fun rtcLoaded(): Boolean
@JsFun("() => globalThis.PgRtc ? PgRtc.host() : 0") private external fun rtcHost()
@JsFun("(o) => globalThis.PgRtc ? PgRtc.join(o) : 0") private external fun rtcJoin(o: String)
@JsFun("(s) => globalThis.PgRtc ? PgRtc.answer(s) : 0") private external fun rtcAnswer(s: String)
@JsFun("(t) => { if (globalThis.PgRtc) PgRtc.send(t); }") private external fun rtcSend(t: String)
@JsFun("() => { if (globalThis.PgRtc) PgRtc.close(); }") private external fun rtcClose()

/** The direct link to the other player's device. Events arrive as (type, data). */
object PeerLink {
    private var loaded = false
    private val queued = ArrayList<() -> Unit>()
    private var listener: (String, String) -> Unit = { _, _ -> }

    fun setListener(f: (String, String) -> Unit) { listener = f }

    private fun run(block: () -> Unit) {
        if (loaded) { block(); return }
        queued += block
        if (queued.size == 1) loadRtc {
            loaded = true
            rtcListen { t, d -> listener(t, d) }
            queued.forEach { it() }; queued.clear()
        }
    }

    // If the helper couldn't load, report it like a device without direct links.
    fun host() = run { if (rtcLoaded()) rtcHost() else listener("error", "") }
    fun join(offer: String) = run { if (rtcLoaded()) rtcJoin(offer) else listener("error", "") }
    fun answer(sdp: String) = run { rtcAnswer(sdp) }
    fun send(text: String) = run { rtcSend(text) }
    fun close() { if (loaded) rtcClose() }
}
