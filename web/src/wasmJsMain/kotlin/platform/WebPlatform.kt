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

@JsFun("() => performance.now()")
private external fun perfNow(): Double

/** A clock for measuring durations that never jumps when the wall clock is changed. */
fun monotonicMillis(): Long = perfNow().toLong()

/** Dispatcher for storage work: the browser has one thread. */
val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher get() = kotlinx.coroutines.Dispatchers.Default

@JsFun("(ms) => { const d = new Date(ms); return [d.getFullYear(), d.getMonth(), d.getDate(), d.getHours(), d.getMinutes()].join(',') }")
private external fun dateParts(ms: Double): String

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** [ms] since 1970 as local date text. Supports the patterns the app uses: MMM, d, yyyy, h, mm, a. */
fun formatDate(ms: Long, pattern: String): String {
    val (y, mo, d, h, mi) = dateParts(ms.toDouble()).split(',').map { it.toInt() }
    val h12 = if (h % 12 == 0) 12 else h % 12
    return pattern
        .replace("yyyy", "\u0001").replace("MMM", "\u0002").replace("mm", "\u0003")
        .replace("d", "$d").replace("h", "$h12").replace("a", if (h < 12) "AM" else "PM")
        .replace("\u0001", "$y").replace("\u0002", MONTHS[mo]).replace("\u0003", mi.toString().padStart(2, '0'))
}

/** An exception for control flow (the Android version skips the stack trace for speed). */
open class FastException : RuntimeException()
