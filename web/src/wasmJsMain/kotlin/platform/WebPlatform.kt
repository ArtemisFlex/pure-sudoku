// The browser side of the platform layer (the Android side is app/.../platform/AndroidPlatform.kt).
package com.puresudoku.platform

import kotlinx.browser.window

@JsFun("() => Date.now()")
private external fun dateNow(): Double

/** Wall-clock time in milliseconds since 1970. */
fun nowMillis(): Long = dateNow().toLong()

/** The browser runs the game on one thread, so there is nothing to lock. */
inline fun <T> locked(@Suppress("UNUSED_PARAMETER") lock: Any, block: () -> T): T = block()

/** Runs [block] on the page's thread after [ms] milliseconds. */
fun onMainLater(ms: Long, block: () -> Unit) {
    window.setTimeout({ block(); null }, ms.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
}

/** An exception for control flow (the Android version skips the stack trace for speed). */
open class FastException : RuntimeException()
