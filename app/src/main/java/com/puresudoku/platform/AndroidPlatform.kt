// The Android side of the platform layer: the few things the shared game code needs that differ
// between Android and the browser. The browser build (web/) leaves this folder out and supplies
// its own versions with the same names, so nothing else in the app has to know which it runs on.
package com.puresudoku.platform

import android.os.Handler
import android.os.Looper

/** Wall-clock time in milliseconds since 1970. */
fun nowMillis(): Long = System.currentTimeMillis()

/** Runs [block] holding [lock], for caches shared between the UI and the AI thread. */
inline fun <T> locked(lock: Any, block: () -> T): T = synchronized(lock, block)

private val main by lazy { Handler(Looper.getMainLooper()) }

/** Runs [block] on the main thread after [ms] milliseconds. */
fun onMainLater(ms: Long, block: () -> Unit) { main.postDelayed(block, ms) }

/** An exception for control flow: no stack trace is captured, so throwing it is cheap. */
open class FastException : RuntimeException(null, null, false, false)
