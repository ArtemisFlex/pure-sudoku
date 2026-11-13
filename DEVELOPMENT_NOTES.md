# Development notes

## 2026-11-13 — Aegwaspell handoff

Requested: finish and test the unfinished Aegwaspell and Conquest/Stewardship changes, push to the pure-sudoku Git repository, and build an APK. The prior session also requested a list of wonder artwork and other needed art.

Found: the prior session stopped at the first compile after adding shared stewardship engine changes. Both screens lacked handling for the new coach actions and player controls for event choices, decrees, demolition and resting farms. The initial debug and release compilation failed.

Completed: added coach handling and player controls for both modes, accessible tile notes, chronicle and end-of-game legacy display, rulebook updates and scrollable action panels. Corrected the plague choice estimate and kept the requested minimum 4:1 port trade rate. Added focused automated tests.

Validation: release build and release lint passed; four unit tests passed for save/choice handling, demolition and wonder construction. The debug build was exercised on an Android emulator: both modes opened the stewardship panel, Conquest changed a decree, advanced a season and resolved a plague choice. The release APK installed and launched on that emulator. Two new test games were created in previously unused emulator save slots; earlier saves were not deleted. The release uses the same local debug signing certificate as the earlier root APK.

Still needs work: play through the full range of events, difficulty levels, combat, construction, save migration, end-of-game screens and balance; test on the actual phone. Six wonders use placeholder icons and need dedicated artwork. The generated CONQUEST lettering script from the previous session was in a temporary folder and is not part of this project.

Signing issue: the previously published GitHub build 63 has a different signing certificate from the local APK and local release build. An APK can update an installed app only when signing certificates match. Do not uninstall an existing phone app to work around this without first protecting its local saves. GitHub Actions currently uses the runner's debug signing key; a stable signing setup and phone signature check remain open.

## 2026-11-13 — Lighthouse, art and web request

Requested: restore the Lighthouse trade bonus; audit and create matching reusable assets for wonders, walls, roads, icons and UI across the strategy games; assess a browser gaming hub with mouse controls, local saves and leaderboards. The earlier 4:1 minimum interpretation was incorrect: the user confirmed that the completed Lighthouse should improve the trade rate.

In progress: restored the 3:1 Lighthouse rate in Conquest and updated the port display, coach, rulebook and test. Art and web feasibility audit underway.

Completed: restored 3:1 Conquest trade for a complete, staffed Lighthouse, updated the rate in the port, coach, AI and rulebook, and verified the change with the unit tests. Generated all six wonder images and wired them into both Aegwaspell boards and build controls. Generated shared wall, road, button and icon art; integrated neutral walls in Wisteria, Kreashun, Veilguard and their rulebook diagrams, neutral roads in both Aegwaspell modes, and thirteen action icons in shared controls. Full-resolution artwork and an integration inventory are in art/strategy-generated. Existing owner-color markers remain on walls.

Web assessment: a static browser port with local-only saves and leaderboards needs no game server; the existing Android app still needs a browser port. See WEB_PORT_ASSESSMENT.md. A shared leaderboard or automatic cross-device sync needs a service. No web deployment or domain changes were requested or performed.

Still needs work: verify the new sprites and owner cues in-game at phone and tablet sizes, complete remaining UI/art integration (including the alternative ornate button and detailed artwork), test variants and modes individually, and test a release APK on a device. Real-phone verification and web implementation/hosting remain open. A separate temporary emulator failed to boot and was removed; with the user's earlier restart authorization, the existing emulator was then used.

Verification: debug build, release build with release lint, and four unit tests passed. The expanded Lighthouse test confirms the rate stays 4:1 while unstaffed, becomes 3:1 when staffed, and actually executes a 3-for-1 trade. The generated source art was inspected. The debug APK was installed on the emulator and the Kreashun tutorial showed the new painted wall and icons with player colors; other variants and live wonder construction still need visual checks. The release APK has not been installed or published, and real-phone verification remains open.

## 2026-11-13 — Browser port investigation

Requested: investigate porting PureGames to the previously discussed browser hub rather than running a streamed Android emulator. Audited the Android catalog, game-core dependencies, asset sizes, input and backup format. A temporary copy of SudokuSolver initially failed Kotlin/JS compilation on two Java Integer calls; with Kotlin equivalents in the copy it compiled, and Node passed a known unique puzzle, exact solution and invalid-grid check. No Android app source was changed for this probe. Updated WEB_PORT_ASSESSMENT.md with viable approaches, limitations and an acceptance checklist. Outstanding: build and evaluate an actual browser strategy slice, prove save compatibility and offline behavior, review remaining art in-game, and configure and verify hosting only when requested.

## 2026-11-13 — Gameplay and artwork verification requested

Requested: set the web port aside; test the new game art, UI readability and alignment, gameplay, and every new Aegwaspell and Conquest buff and rule. Preserve existing saves.

In progress: auditing shared stewardship, wonder effects, game-state persistence and rendered controls; testing Android builds and emulator scenarios. Initial issues identified: harvest forecast seasonal label, Conquest Citadel defense after loading, and population counters excluding children.


Audit fixes: the first harvest forecast is now seeded in both games and labels the actual season; Conquest Citadel defense derives from the completed/staffed wonder after loading and when staffing changes; population counters include children. An unfinished or unstaffed Sky Temple no longer supplies town housing or adjacent happiness. Feast and unrest choices require their stated food or gold and are disabled when unaffordable. The Conquest coach no longer assigns a worker to a resting farm. Wonder effects are listed in the stewardship panel. The action list now identifies itself as scrollable, and stone uses its recognizable drawn resource symbol rather than the Earth spell artwork. Lighthouse still grants the requested staffed 3:1 Conquest trade rate.

Verification so far: 14 automated tests pass, including all six wonder activation gates, their targeted effects, the Lighthouse trade, choice costs, forecast, Citadel save/reload, population aging, resting farms, decrees and upkeep. One automated playthrough test exercises both coaches across all six difficulties and three random seeds, up to 180 actions per game, with periodic save reloads; this checks legal coach actions and is not a full human balance or victory-path test. The emulator displayed the actual Aegwaspell/Conquest panels, roads and wonder pieces and demonstrated action-list scrolling. A debug-only in-memory visual fixture is used without reading or overwriting player saves.

Still required: inspect each other strategy game and variant, all new icons and controls, accessibility and tablet/small-phone sizes; complete human playthroughs covering events, combat, save migration, end states and balance. Real-phone acceptance remains open. The previously noted local release signing mismatch remains unresolved; do not uninstall a player's app or overwrite their saves. Browser port work is deferred at the user's request.

Further fixes and verification: a completed Royal Palace now provides housing only while staffed, consistent with its other buffs; migrant arrival logs now use the number actually added. The final debug unit-test suite (14 tests), debug APK build, release APK build and release vital lint passed after those fixes. Full release lint was started but not completed. Emulator fixtures showed Wisteria, Veilguard and Kreashun wall segments on tile edges with blue/red owner cues, as well as both Aeg screens and the scroll instruction; this is controlled-scene visual QA, not end-to-end acceptance. The release APK is app/build/outputs/apk/release/app-release.apk; its signing compatibility issue remains as noted above. No existing emulator saves were cleared.

Remaining: the user asked for every art asset, UI control, variant, gameplay rule and update to be checked; this has not yet been exhaustively completed. In particular, individual variants, smaller and larger devices, every event and end state, real-device play and balance still need coverage. The changes have not been pushed.


## 2026-11-13 — Player and AI colors; commit, push and build

Requested: make ownership clear with blue player and red AI roads and walls where applicable, then commit, push and build the changes. Eiseria already uses blue and red road artwork; Aegwaspell roads connect the player settlement and have no competing AI-owned road system. The painted walls currently have thin color markers, so the wall surfaces will also be tinted for clearer ownership.

Completed: kept the generated painted wall image in Wisteria (including Ashfall), Kreashun and Veilguard and their rulebook diagrams, and tinted it blue or red by owner in addition to existing markers. A Wisteria emulator fixture displayed both colors; other variants need visual acceptance. Eiseria retains its existing owner-colored roads; Aegwaspell roads are player-only. All 14 debug unit tests, debug and release APK builds, and release vital lint passed after the change. Existing emulator saves were preserved. Full human playthroughs, visual review of all variants and device sizes, and stable release signing remain open.
