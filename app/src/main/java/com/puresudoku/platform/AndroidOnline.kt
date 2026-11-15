// Platform layer (Android): online play's two connections.
//
// The direct device-to-device link runs the shared assets/rtc.js (WebRTC) inside a hidden WebView,
// so Android and the browser use exactly the same code and no extra library is needed.
// The room-code service is reached with plain HTTPS requests on a background thread.
package com.puresudoku.platform

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/** Where the room-code service lives. */
const val ONLINE_API = "https://puregames.app/api/room"

private val mainThread by lazy { Handler(Looper.getMainLooper()) }

/** A request to the room-code service; [done] gets (status, body) on the main thread, status 0 if offline. */
fun httpRequest(method: String, url: String, body: String?, done: (Int, String) -> Unit) {
    Thread {
        val result = runCatching {
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 8000; c.readTimeout = 10000
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            code to text
        }.getOrElse { 0 to "" }
        mainThread.post { done(result.first, result.second) }
    }.start()
}

/** The direct link to the other player's device. Events arrive on the main thread as (type, data). */
object PeerLink {
    @SuppressLint("StaticFieldLeak")
    private var web: WebView? = null
    private var ctx: Context? = null
    private var ready = false
    private val queued = ArrayList<String>()
    private var listener: (String, String) -> Unit = { _, _ -> }

    /** Called once from the activity with the application context. */
    fun init(context: Context) { ctx = context.applicationContext }

    fun setListener(f: (String, String) -> Unit) { listener = f }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun ensure() {
        if (web != null) return
        val c = ctx ?: return
        web = WebView(c).apply {
            settings.javaScriptEnabled = true
            addJavascriptInterface(object {
                @JavascriptInterface fun on(type: String, data: String) { mainThread.post { listener(type, data) } }
                @JavascriptInterface fun loaded() { mainThread.post { ready = true; queued.forEach { evaluateJavascript(it, null) }; queued.clear() } }
            }, "PgBridge")
            val js = c.assets.open("rtc.js").bufferedReader().use { it.readText() }
            val page = "<!doctype html><script>$js\nPgRtc.setListener((t,d)=>PgBridge.on(t,d));PgBridge.loaded();</script>"
            // An https origin makes this a secure context, which WebRTC needs.
            loadDataWithBaseURL("https://puregames.app/", page, "text/html", "utf-8", null)
        }
    }

    private fun run(js: String) {
        ensure()
        val w = web ?: return
        if (ready) w.evaluateJavascript(js, null) else queued += js
    }

    private fun q(s: String) = JSONObject.quote(s)

    fun host() = run("PgRtc.host()")
    fun join(offer: String) = run("PgRtc.join(${q(offer)})")
    fun answer(sdp: String) = run("PgRtc.answer(${q(sdp)})")
    fun send(text: String) = run("PgRtc.send(${q(text)})")
    fun close() { if (web != null) run("PgRtc.close()") }
}
