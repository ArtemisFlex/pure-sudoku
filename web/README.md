# PureGames for the browser

This folder builds the Android app's own code (game rules, AI, every screen) for the web with
Kotlin/Wasm and Compose Multiplatform. Nothing is copied: `build.gradle.kts` compiles
`../app/src/main/java` directly.

## How the sharing works

- `app/.../platform/` holds the few things that differ on Android (clock, threads, fonts, storage,
  backups, backdrop rendering). The web build leaves that folder out and uses `src/wasmJsMain/kotlin/platform/`,
  which declares the same names for the browser.
- `org/json/Json.kt` stands in for Android's `org.json`; `R.drawable` ids are generated from the app's
  art at build time (`generateR`), and the art itself is served as Compose resources.
- Saves, records and settings live in the browser's local storage. Backups are files the player
  downloads and loads again from Settings.
- Board-game AI searches run in a Web Worker (`ai-worker.js` loads this same program a second time).
  A search is described by the game's save data plus a seed, so the worker reruns it exactly; the page
  falls back to searching itself if the worker isn't ready or its move doesn't fit the board.
- The browser has no system fonts, so `fonts/` bundles small subsets of Noto (SIL Open Font License,
  `fonts/OFL-Noto.txt`) for card suits, arrows, symbols, an emoji and the Chinese/Japanese piece names.
  Add characters there (fontTools `pyftsubset`) if the app starts using new symbols.

## Build and run

```bash
cd web
../gradlew -p . wasmJsBrowserDevelopmentRun        # local dev server
../gradlew -p . wasmJsBrowserDistribution          # production files in build/dist/wasmJs/productionExecutable
```

## Deploy

The production folder is a static site (it includes `vercel.json` for caching). It is deployed to the
`puregames` Vercel project, served at https://puregames.app (and www.puregames.app):

```bash
vercel deploy build/dist/wasmJs/productionExecutable --prod --scope formappteam
```
