// Platform layer (browser): the computer opponents' thinking, in a Web Worker.
//
// The page loads this same program a second time inside a Web Worker. A search request carries the
// game's save data and a seed; the worker restores the game, runs the identical search and returns
// the move as text. Until the worker is ready, or if it ever fails, the page does the search itself.
package com.puresudoku.ui.games.board

import com.puresudoku.platform.nowMillis
import com.puresudoku.platform.onMainLater

object AiWorker {
    private val pending = HashMap<Int, (String?) -> Unit>()
    private var nextId = 1
    private var ready = false
    private var started = false

    private fun ensureWorker() {
        if (started) return
        started = true
        runCatching {
            startWorker { msg ->
                if (msg == "ready") { ready = true; return@startWorker }
                val bar = msg.indexOf('|')
                val id = msg.substring(0, bar).toIntOrNull() ?: return@startWorker
                val body = msg.substring(bar + 1)
                pending.remove(id)?.invoke(if (body.startsWith("!")) null else body.drop(1))
            }
        }.onFailure { println("AI worker unavailable: $it") }
    }

    /** Runs [work] on the page's thread after the current frame (for small jobs such as the coach). */
    fun <T> run(minMs: Long = 450, work: () -> T, done: (T) -> Unit) {
        val start = nowMillis()
        onMainLater(30) {
            val r = runCatching(work)
            r.exceptionOrNull()?.let { println("AI error: $it") }
            val wait = (minMs - (nowMillis() - start)).coerceAtLeast(0)
            onMainLater(wait) { r.onSuccess(done) }
        }
    }

    /** A board-game search: in the worker when it's ready, otherwise here. */
    fun search(minMs: Long, kind: String, state: () -> String, seed: Long, job: () -> String, valid: (String) -> Boolean, done: (String) -> Unit) {
        ensureWorker()
        if (!ready) { run(minMs, job, done); return }
        val start = nowMillis()
        val id = nextId++
        pending[id] = { r ->
            // A move from the worker must still fit the live board; otherwise work it out here.
            val move = r?.takeIf { runCatching { valid(it) }.getOrDefault(false) } ?: job()
            onMainLater((minMs - (nowMillis() - start)).coerceAtLeast(0)) { done(move) }
        }
        postToWorker("$id|$kind|$seed|${state()}")
    }

    fun later(ms: Long, block: () -> Unit) = onMainLater(ms, block)

    /** Starts loading the worker early, so the first computer move already runs there. */
    fun prewarm() = ensureWorker()
}

// ---------------------------------------------------------------- the page side

@JsFun("""(onMessage) => {
    const w = new Worker(new URL('ai-worker.js', document.baseURI));
    w.onmessage = (e) => onMessage(String(e.data));
    w.onerror = (e) => { console.warn('AI worker error', e.message); };
    globalThis.__pgWorker = w;
}""")
private external fun startWorker(onMessage: (String) -> Unit)

@JsFun("(m) => globalThis.__pgWorker.postMessage(m)")
private external fun postToWorker(m: String)

// ---------------------------------------------------------------- the worker side

@JsFun("() => typeof WorkerGlobalScope !== 'undefined' && self instanceof WorkerGlobalScope")
external fun isWorkerContext(): Boolean

@JsFun("(handle) => { self.onmessage = (e) => self.postMessage(handle(String(e.data))); self.postMessage('ready'); }")
private external fun listenInWorker(handle: (String) -> String)

/** Worker entry point: answer search requests ("id|KIND|seed|save data") with "id|=move" or "id|!error". */
fun startAiWorker() {
    listenInWorker { msg ->
        val parts = msg.split('|', limit = 4)
        val id = parts[0]
        runCatching {
            val kind = com.puresudoku.core.GameKind.valueOf(parts[1])
            val session = com.puresudoku.ui.games.Games.of(kind).restore(org.json.JSONObject(parts[3])) as DuelSession
            val job = session.aiSearch(parts[2].toLong()) ?: error("no search for $kind")
            "$id|=" + job()
        }.getOrElse { "$id|!${it.message}" }
    }
}
