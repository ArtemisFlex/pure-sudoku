// Platform layer (Android): see AndroidPlatform.kt.
package com.puresudoku.ui.games.board

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** One background thread for every computer opponent; results come back on the main thread. */
object AiWorker {
    private val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "board-ai").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
    private val main = Handler(Looper.getMainLooper())

    /** Runs [work] off the main thread, then hands its result to [done] no sooner than [minMs] after starting. */
    fun <T> run(minMs: Long = 450, work: () -> T, done: (T) -> Unit) {
        val start = System.currentTimeMillis()
        pool.execute {
            val r = runCatching(work)
            val wait = (minMs - (System.currentTimeMillis() - start)).coerceAtLeast(0)
            main.postDelayed({ r.onSuccess(done) }, wait)
        }
    }

    /** A search job: on Android it simply runs on the AI thread (the browser can move it to a Web Worker). */
    @Suppress("UNUSED_PARAMETER")
    fun search(minMs: Long, kind: String, state: () -> String, seed: Long, job: () -> String, valid: (String) -> Boolean, done: (String) -> Unit) =
        run(minMs, job, done)

    fun later(ms: Long, block: () -> Unit) { main.postDelayed(block, ms) }
}
