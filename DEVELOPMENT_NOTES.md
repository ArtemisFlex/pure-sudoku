# Development notes

## 2026-11-13 — Aegwaspell handoff

Requested: finish and test the unfinished Aegwaspell and Conquest/Stewardship changes, push to the pure-sudoku Git repository, and build an APK. The prior session also requested a list of wonder artwork and other needed art.

Found: the prior session stopped at the first compile after adding shared stewardship engine changes. Both screens lacked handling for the new coach actions and player controls for event choices, decrees, demolition and resting farms. The initial debug and release compilation failed.

Completed: added coach handling and player controls for both modes, accessible tile notes, chronicle and end-of-game legacy display, rulebook updates and scrollable action panels. Corrected the plague choice estimate and kept the requested minimum 4:1 port trade rate. Added focused automated tests.

Validation: release build and release lint passed; four unit tests passed for save/choice handling, demolition and wonder construction. The debug build was exercised on an Android emulator: both modes opened the stewardship panel, Conquest changed a decree, advanced a season and resolved a plague choice. The release APK installed and launched on that emulator. Two new test games were created in previously unused emulator save slots; earlier saves were not deleted. The release uses the same local debug signing certificate as the earlier root APK.

Still needs work: play through the full range of events, difficulty levels, combat, construction, save migration, end-of-game screens and balance; test on the actual phone. Six wonders use placeholder icons and need dedicated artwork. The generated CONQUEST lettering script from the previous session was in a temporary folder and is not part of this project.
