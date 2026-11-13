# PureGames browser hub assessment

Requested destination: gaming.brattoncreativesolutions.com. This assessment does not configure DNS or publish a site.

The current project is an Android application using Kotlin, Android APIs and Jetpack Compose. It is not a website that can simply be uploaded to a host. The game engines and assets can inform a web port, but the screens, input, platform services and persistence need browser-compatible implementations. Audit and port games in small groups, and validate their rules and saved-game format against Android; do not assume all Android code is portable unchanged.

A static HTTPS host and DNS configuration are sufficient to distribute a single-player web hub. The game runs in the browser: no dedicated game server or server-side user database is needed for local-only games and local leaderboards. Browser data is tied to the origin and browser profile; scores are not a common leaderboard and do not automatically follow the player to another browser or device.

Use IndexedDB for saves and local statistics, request persistent storage where available, and provide explicit export/import of a versioned backup file. Chrome's file picker can support access to a user-selected file with permission; ordinary downloadable backup and upload should remain available. Storage can still be lost when users clear site data, change devices or profiles, or lose a device, so a backup is important. A service worker can cache the app for offline use after the initial visit; updates and save migrations need testing.

Mouse and touch require deliberate support for click targets, dragging, zooming, hover, focus and keyboard accessibility. Test on desktop Chrome and mobile browsers. Shared online rankings, account synchronization, multiplayer or authoritative anti-cheat would require an online service and a different data model.

References: https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria ; https://developer.chrome.com/docs/capabilities/web-apis/file-system-access ; https://developer.android.com/kotlin/multiplatform

## Project-specific findings — 2026-11-13

The Android catalog lists 48 games in five categories, including ten strategy titles and variants. Current source contains 37 Kotlin files under core and 64 under UI. The app module is Android-only, built with Kotlin 2.0.21 and Android Jetpack Compose. The core folder is relatively separated from UI, but some of it imports Java or Android-provided APIs. This makes shared rules promising, not automatically portable.

Concrete blockers include `Integer.bitCount` and `Integer.numberOfTrailingZeros` in SudokuSolver; `SecureRandom` in multiple generators; `java.util.BitSet` in QuickGames; `java.io.Reader` in word data; `org.json` in core/session serialization; `Math.toRadians`, `synchronized` and `System.currentTimeMillis` in various board engines. Strategy screens use Android resource identifiers and Compose drawing and gesture APIs; the app's manifest currently fixes portrait orientation. These areas need portability work and input/layout testing, rather than assuming a whole-module move will compile.

Existing persistence is valuable: `SaveRepository` uses Android SharedPreferences, has three slots per game, records/history, and exports JSON with `app`, `version`, `savedAt`, and typed `items`; accepted identifiers include PureGames and Pure Puzzles. Import currently replaces all local preferences. Browser import should inspect and validate the file, preview what can be imported, avoid destructive replacement by default, and demonstrate compatibility with real exported test fixtures before promising transfer of Android progress. Some preference values are themselves JSON strings, so the outer typed record is not the whole schema.

The current nodpi drawable directory contains 121 files totaling approximately 7.5 MB; `app/src/main/assets` contains six files totaling approximately 1.6 MB (measured in the current uncommitted working tree). These are not a web download-size estimate: an actual build must measure compressed bundles and lazy-loaded assets.

## Feasibility probe

A temporary standalone Kotlin/JS project was built outside the repository using a copy of the existing SudokuSolver and the project's Kotlin 2.0.21. The unchanged source failed to compile due to two Java `Integer` methods. In the temporary copy only, replacing those with Kotlin `countOneBits()` and `countTrailingZeroBits()` allowed compilation to a production JavaScript executable. Running it with Node passed a known unique puzzle, checked the exact expected solution, and rejected an invalid puzzle. The project source was not changed for this test. This shows a small isolated rules engine can be adapted; it is not a browser UI, an offline test, a benchmark, or proof that the other 47 games can be ported without further work. The probe used the older Kotlin JS plugin for a quick experiment; a real project should evaluate a supported Kotlin Multiplatform target rather than treating the probe as production scaffolding.

## Candidate paths

1. Kotlin Multiplatform shared rules plus a browser-native web UI: retain and port game logic once, expose stable interfaces to a web shell, and create responsive browser screens. It avoids maintaining duplicate game rules but still requires UI work and clear Kotlin/JS interoperability.
2. Compose Multiplatform for Android and web: potentially share portions of the existing Compose UI as well as logic. This is a meaningful migration, not a switch on the existing Gradle build: Android resources, context, lifecycle, storage, and platform APIs must be replaced or abstracted. Official Kotlin documentation describes web support as beta; check current compatibility and validate mouse/touch, browser performance, accessibility and bundle size with a working slice before choosing it.
3. Rewrite the game rules and screens as a conventional web application: flexible and browser-native but creates a second rules implementation, with ongoing parity and maintenance risk across the game's large catalog.

Recommended: prototype path 1 against path 2 in an isolated project, with a simple Sudoku rules slice followed immediately by one strategy-game slice using the existing hex board and assets. Decide based on tested compatibility, input fidelity, accessibility, performance, and real maintenance effort. Keep the current Android checkout and uncommitted work intact. If a route cannot share a given feature safely, record that and port it explicitly rather than silently creating divergent rules.

## Acceptance for the first strategy slice

- One named strategy game with its actual Kotlin rules, assets, AI and tutorial; deterministic Android/browser rule-parity fixtures for actions, turns and scoring.
- Desktop mouse click, drag/pan, wheel or equivalent zoom, touch on mobile, and keyboard access where practical.
- New game, three slots, resume, personal scores and versioned import/export; validate a real Android backup fixture without overwriting existing data.
- Local HTTPS-origin test, fresh install, cached offline reload, app update with existing save, and recovery from denied storage or cleared site data.
- Browser console errors, asset load time and device responsiveness checked in current desktop Chrome and mobile browsers. Local verification is not a public deployment or phone acceptance.
- Only after the slice works: choose the deployment setup, point the requested subdomain, and verify its HTTPS and update behavior. Shared online scoreboards or synchronization remain separate features.

Additional official references: https://kotlinlang.org/docs/multiplatform/faq.html ; https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html ; https://developer.android.com/codelabs/kmp-get-started ; https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API/Using_Service_Workers
