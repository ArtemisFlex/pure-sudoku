// Platform layer (Android): getting the newest version of the app.
package com.puresudoku.platform

import android.content.Context
import android.content.Intent
import android.net.Uri

/** The newest PureGames build, published by the GitHub build workflow on every push. */
private const val LATEST_APK = "https://github.com/ArtemisFlex/pure-sudoku/releases/latest/download/PureGames.apk"

object AppUpdate {
    internal var ctx: Context? = null
    /** Called once from the activity with the application context. */
    fun init(context: Context) { ctx = context.applicationContext }
}

/** What the Settings button says. */
val updateButtonLabel: String get() = "Download latest version"

/** A line under the button explaining what it does. */
val updateHelp: String get() = "Downloads the newest PureGames. Open the file when it finishes to install it over this one; your saves stay."

/** Fetches the newest version; [done] gets a short message for the player. */
fun checkForUpdate(done: (String) -> Unit) {
    val c = AppUpdate.ctx ?: run { done("Couldn't open the download. Try again."); return }
    runCatching {
        c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LATEST_APK)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        done("Downloading… open PureGames.apk when it finishes to install.")
    }.onFailure { done("Couldn't open the download. Try again.") }
}
