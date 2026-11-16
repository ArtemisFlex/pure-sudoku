// Platform layer (Android): the app has no update button; updates are for the web app (see WebUpdate.kt).
package com.puresudoku.platform

/** What the Settings update button says, or null to leave the Updates section out. */
val updateButtonLabel: String? get() = null

val updateHelp: String get() = ""

fun checkForUpdate(done: (String) -> Unit) { done("") }
