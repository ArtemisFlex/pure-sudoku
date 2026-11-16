// Platform layer (browser): getting the newest version of the site.
package com.puresudoku.platform

/**
 * Asks the service worker to look for a new deploy. If one is found it downloads in the background and the page
 * reloads into it as soon as it's ready (see index.html). [done] gets "downloading", "current", "offline" or "none".
 */
@JsFun("""(done) => {
    if (!('serviceWorker' in navigator)) { done('none'); return; }
    navigator.serviceWorker.getRegistration().then((reg) => {
        if (!reg) { done('none'); return; }
        let found = false;
        reg.addEventListener('updatefound', () => { found = true; done('downloading'); }, { once: true });
        reg.update().then(() => {
            if (found) return;
            if (reg.installing || reg.waiting) done('downloading'); else done('current');
        }).catch(() => done('offline'));
    }).catch(() => done('none'));
}""")
private external fun swUpdate(done: (String) -> Unit)

/** What the Settings button says. */
val updateButtonLabel: String get() = "Check for updates"

/** A line under the button explaining what it does. */
val updateHelp: String get() = "PureGames checks for a new version by itself and reloads when one is ready. Your saves stay."

/** Looks for a newer version of the site; [done] gets a short message for the player. */
fun checkForUpdate(done: (String) -> Unit) = swUpdate { r ->
    done(when (r) {
        "downloading" -> "A new version is downloading. PureGames will reload in a moment."
        "current" -> "You have the latest version."
        "offline" -> "Couldn't check: you seem to be offline."
        else -> "Updates aren't available in this browser."
    })
}
