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


## 2026-11-13 — Local two-player pass-and-play

Requested: make every versus game playable by two people passing one phone on their turns, using minimal UI changes and preserving the AI engines. Audit game models, hidden information, turn flow, persistence and results before enabling each game. In progress; no game should advertise support until both players can play legally, save/resume works, and private information is protected.

Clarification: exclude Aegwaspell and Aegwaspell: Conquest. Include equal-sided versus games such as Eiseria and its variants, Wisteria, Kreashun, Veilguard, chess, checkers, Go, shogi and other 1v1 games. This is local pass-and-play, not remote play.

In progress: implemented a separate local match entry for Tic-Tac-Toe, Gomoku, Chess, Checkers and Go. Both sides can act, local mode and the current turn survive reload, AI replies are suppressed only in local matches, and local results do not enter solo leaderboards/history. Debug APK and 14 existing unit tests pass. An emulator debug fixture checked each game can move, save/reload on player 2, and accept player 2’s move or pass; Gomoku player 1 winner was checked. The Tic-Tac-Toe menu and alternating moves were also exercised on the emulator, creating a new test save in an empty slot. The remaining eligible games, full match and end-state coverage, privacy screens for hidden information, and real-device tests are still required. No release or push of this incomplete feature.

Additional progress: Shogi, Xiangqi and Mancala have local controls and persisted mode, and the debug fixture passed player-2 moves and reload for all eight enabled board games, including Shogi player-2 hand drops. The saved-game list labels local matches as Two players. The debug APK and existing unit tests passed. Remaining eligible games, full match coverage and all physical-device acceptance remain open.

Further progress: Nine Men’s Morris and Backgammon now have local controls and persisted mode. The Android emulator fixture passed player-2 moves after reload in all ten standard versus board games, including a Shogi player-2 drop, Morris player-2 mill/capture, Go pass and Backgammon player-2 dice move. The full set of 14 existing unit tests and debug build passed. Strategy games and equal-sided versus card games remain unconverted; they must not expose other players’ hidden cards or resources during handoff. Full end-to-end match, device and accessibility testing remains open. Changes are local and uncommitted.

Latest verification: the debug APK built successfully and the emulator fixture passed local turn/save restoration in Tic-Tac-Toe, Gomoku, Chess, Checkers, Go, Shogi, Xiangqi, Mancala, Morris and Backgammon. Targeted checks also covered Shogi player-2 drops, Morris player-2 mills and a Backgammon player-2 dice move. These checks do not establish complete match, screen, privacy or device acceptance. Wisteria, Ashfall, Eiseria variants, Kreashun, Veilguard and eligible versus card games remain to be implemented and verified.

Wisteria work in progress: added actor-aware controls and a privacy handoff screen, but did not expose a local-mode menu entry. Emulator fixture verified both setup placements, handoff state and reload without triggering AI; complete privacy, turn actions, variants and full games remain unverified. The debug build and existing unit tests still pass. Eiseria variants, Kreashun, Veilguard and eligible card games remain unimplemented.

Strategy prototypes: Wisteria and Kreashun now have actor-aware controls and private handoff screens in progress, but no menu entry; the emulator fixture passed their setup handoffs and reloads, and Wisteria player-2 trade and end-turn checks. The Wisteria privacy cover was visually checked. The latest debug build and unit tests pass. More game actions, both full matches, Ashfall, Eiseria variants, Veilguard and eligible versus card games remain to be implemented and validated.

Eiseria prototype now also uses an actor-aware handoff and player-specific resources, trades, spells and discard controls; its mode remains hidden. The emulator fixture passed initial setup, player-2 move and save/reload for Eiseria, Wisteria and Kreashun, along with ten board games. All existing unit tests and the debug build passed. The Eiseria seven/discard sequence, full game actions, all variants, private-information audit and real-device tests remain open. Veilguard and eligible card games remain unimplemented.

Veilguard prototype: added local planning-seat persistence and separate private handoff phases, including viewer-relative crowns, decoys and orders. It remains hidden from the menu. Emulator fixture passed setup, both planning handoffs, reload on player 2 and round resolution without the AI. Debug build and existing unit tests passed; detailed privacy, all orders/spells, full matches and device testing remain open. A first compile failed from duplicate debug imports; corrected and rebuilt successfully.

Further verification: a deterministic Eiseria seven/discard fixture passed handoff between both players and back to the turn owner, with each deduction applied to the correct resources. The debug build, existing unit tests, and ten-board/four-strategy fixture pass. Strategy modes remain hidden until full action, variants and privacy verification is complete; versus card games remain unimplemented.

Requested: every local two-player game should briefly prompt “Hand phone to Player 1/2” at each handoff; hidden information must remain covered until the receiving player is ready. Implementation and verification in progress.

Handoff progress: added a full-screen Ready prompt for the ten enabled local board games and updated the wording on hidden strategy and Crazy Eights prototypes. The Android emulator showed Player 1 on resume and Player 2 after a Tic-Tac-Toe move; the board was covered until Ready. The emulator fixture passed the ten board games, four strategy prototypes, and Crazy Eights turn/save checks; the debug build and existing unit tests passed before a follow-up adjustment to avoid interrupting Tic-Tac-Toe round results. Further full-match, hidden-information, variant, card-game, accessibility, and device checks remain open. Strategy and card prototypes remain unavailable from the menu, and all changes remain local and uncommitted.

Follow-up validation: debug APK assembly and all existing unit tests passed after the Tic-Tac-Toe round-result handoff adjustment. This last adjustment has not yet been exercised on the emulator.

Continue request: complete remaining equal-sided local games and handoff prompts, including privacy and variant testing. Go Fish and Cuttle conversion and strategy validation are in progress; Aegwaspell remains excluded.

Card conversion progress: Go Fish and Cuttle now have in-progress actor-aware controls and private Ready screens; neither is exposed in the menu yet. Added deterministic handoff/save scenarios, including a Cuttle counter chain. An initial compile exposed required resume implementations and a method-name conflict with the duel base class; both were corrected and the build is being rerun. Full gameplay, privacy, AI regression and device checks remain open.

Card handoff fixture: emulator passed Go Fish and Cuttle save/resume and player changes, including Cuttle counter and response, alongside Crazy Eights and the previously covered games; debug build and unit tests passed. Added local-mode menu entries for the three versus card games and all eight named strategy games/variants, pending the next build and deeper acceptance. Privacy audit found player-one spell names and Veilguard crown/scouting details in shared event messages; added a local-play filter and a targeted fixture check. The full strategy variants, private screens and game endings remain under test; Aegwaspell remains excluded.

Strategy-variant fixture passed initial handoffs/save restoration for all four Eiseria variants and Wisteria: Ashfall, plus the previous checks. Actual Go Fish menu testing exposed that newly displayed two-player buttons were rejected by an older launcher allowlist; replaced the duplicated allowlists with one shared game list. Fix and actual launch require rebuilding and retesting.

Launcher fix verified: latest debug APK and unit tests passed; on the emulator Go Fish opened through its Two players menu, displayed Player 1 Ready, allowed asks, then displayed Player 2 Ready and only Player 2 cards. Its public log did not reveal the prior player’s drawn card. A new Go Fish local test save was created in an empty slot. The full card/strategy matches and physical-device acceptance remain open.

Emulator UI validation: Cuttle launched from the menu and covered Player 2’s hand after Player 1 drew; Wisteria launched from the menu, covered the initial board, and displayed the Player 2 handoff after Player 1 settled. New local Cuttle and Wisteria test saves were created in empty slots, preserving existing saves. Updated the strategy header to say Two players instead of the Easy AI setting; that change requires rebuilding. Full match, end-state, secret-information and physical-device validation remain open.

Extended emulator fixture passed all four Eiseria variants through all setup placements and first roll, plus Veilguard player-one order/cancellation/spell and player-two order/resolution across a restored handoff. The latest debug build and unit tests passed. Corrected Eiseria spellbook freshness notice for Player 2; that change needs another build. Full matches, device testing and broader privacy audit remain open.

Go end-of-game review found that the second pass could trigger a handoff while scoring and was not immediately persisted. The local handoff now stops when both players pass, and each pass is saved before scoring starts. Added a fixture assertion; rebuild and emulator verification are pending.

Latest verification: debug build and unit tests passed; emulator fixture passed a real Player 2 chess checkmate, Player 2 wins in Go Fish, Cuttle and Crazy Eights, and the assertion that Go does not hand off after both passes. The Go second-pass save and Player 2 spellbook notice were rebuilt. Strategy end states, full matches, additional privacy review and physical-device acceptance remain open.

Further emulator fixture passed Player 2 trading and spell purchases in Wisteria, Kreashun and Eiseria without changing Player 1’s resources or spell inventory. Added deterministic Player 2 near-victory scenarios for the three strategy engines; those additions require a rebuild and emulator run. A transient Kotlin daemon startup warning occurred during the successful build; Gradle retried and completed.

A Backgammon review found that a pending automatic end-of-turn timer could ignore an undo and end the turn prematurely. Added a turn/roll/remaining-moves recheck and an asynchronous emulator regression fixture; build and verification are pending. Strategy Player 2 victory checks for Eiseria, Wisteria, Kreashun and Veilguard passed in controlled end positions.

Backgammon save review also found that exiting just after the final die could restore the same player with a fresh roll. Persisted the pending turn completion so reload advances to the correct player; undo clears it. A restored winning position is finalized after the session attaches. Added both cases to the in-progress regression fixture; verification is pending.

Backgammon regression passed on the Android emulator: after two dice moves, a restored saved turn advances to Player 2; undoing the final move keeps Player 1’s remaining die after the automatic timer fires. Debug build and unit tests passed. A release APK build and release vital lint are running.

Release APK assembly and release vital lint passed with the local two-player changes. Added debug-only checks for normal Backgammon automatic advancement and solo Go Fish/Cuttle opponent turns; these new test cases still need a debug rebuild and emulator run. Release APK signing uses the existing local debug key and may not update the differently signed GitHub build, as previously noted.

Save-slot review found that backing out of a full-slot replacement for a two-player match could leave the pending mode set and start a later solo selection as two-player. Cancel now clears the mode and returns to the game menu; replacement text identifies a two-player match. Rebuild and validation are pending.

Backgammon emulator regression now verifies both that undo keeps the die and that an uninterrupted completed turn advances. The wider fixture also passed solo Go Fish/Cuttle opponent-step checks. Added a debug-only full-slot cancellation test using an in-memory slot list; it does not replace saved games. Rebuild and execution are pending.

Full-slot cancellation emulator fixture passed with simulated in-memory slots; no saved games were replaced. Latest debug and release builds, unit tests and release vital lint passed. Open Veilguard fairness issue: in the existing setup order, Player 2 can observe Player 1’s first settlement, which initially contains the Crown; requested user preference on a local-only private Crown choice before changing starting rules.

Release validation: the newly built release APK installed over the emulator debug build without clearing data, launched, and resumed the existing local Go Fish test save at its Player 2 handoff. This is emulator validation, not physical-phone acceptance. The existing GitHub signing mismatch may prevent updating a differently signed phone install.

For local Veilguard only, added a private starting-Crown selection after both players settle their two lands, with a handoff between choices and saved progress. This removes the setup-order advantage while leaving the AI game and engine unchanged. The selection and restoration fixture and full build are pending.

Veilguard local UI was exercised on the Android emulator through all four settlements, both private starting-Crown selections and both handoffs; an existing save was preserved and a test save was created in an empty slot. The prompt was clarified to explain that the Crown is chosen after settling, and settlement messages now name the player. The latest text changes need rebuilding.

Release emulator end-state check: a new local Go test game was started in an empty slot; both players passed, scoring completed without a spurious handoff, and Player 2’s result was shown. Veilguard’s starting-Crown choice and save/reload fixture passed after implementation; the visible local setup and both choices were also exercised. The updated combined release build and vital lint are finishing. Human full-length matches and physical-device acceptance remain open.

Final release check: the updated release APK installed on the emulator without clearing data and resumed the local Veilguard test save after both Crown choices; the chosen Player 1 Crown appeared on the selected land while Player 2’s Crown remained hidden. The complete debug and release builds, 14 existing unit tests, release vital lint, local handoff/restore fixture, Backgammon timing fixture and full-slot cancellation fixture passed. Remaining: full-length human matches, broad accessibility/device-size checks, and physical-phone acceptance. The local signing key still differs from the earlier GitHub APK certificate.

Local pass-and-play implementation is available in the 21 eligible versus games, with handoff prompts and persisted turns; Aegwaspell and Aegwaspell: Conquest are excluded. The existing AI engines were not changed. Local Veilguard adds private starting-Crown choices to avoid exposing the first settler’s initial Crown. Changes are ready for Git delivery; full-length human matches, accessibility/device-size review and physical-phone acceptance remain open.
