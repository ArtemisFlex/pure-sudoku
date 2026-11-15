// Platform layer (browser): see WebPlatform.kt.
package com.puresudoku.ui.games.board

import com.puresudoku.platform.nowMillis
import com.puresudoku.platform.onMainLater

/**
 * The computer opponent's thinking. The browser page has one thread, so the work runs on it, after
 * the frame showing the player's move has been drawn. (A Web Worker could take it off the page later.)
 */
object AiWorker {
    fun <T> run(minMs: Long = 450, work: () -> T, done: (T) -> Unit) {
        val start = nowMillis()
        onMainLater(30) {
            val r = runCatching(work)
            r.exceptionOrNull()?.let { println("AI error: $it") }
            val wait = (minMs - (nowMillis() - start)).coerceAtLeast(0)
            onMainLater(wait) { r.onSuccess(done) }
        }
    }

    fun later(ms: Long, block: () -> Unit) = onMainLater(ms, block)
}
