// Platform layer (browser): a single-game site sets window.PG_ONLY in its index.html (kreat.puregames.app does).
package com.puresudoku.platform

@JsFun("() => (typeof window !== 'undefined' && window.PG_ONLY) ? String(window.PG_ONLY) : null")
private external fun pageOnlyGame(): String?

/** The one game a single-game site opens straight into (a GameKind name), or null for the whole app. */
val onlyGame: String? by lazy { pageOnlyGame() }
