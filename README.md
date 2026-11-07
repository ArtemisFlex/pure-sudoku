# Pure Sudoku

An offline, single-player Sudoku for Android (Kotlin + Jetpack Compose). No internet permission, no ads, no accounts.

## Get the APK on your phone (no computer needed)

1. Create an empty repository on GitHub and push this project to it (or let Claude push it for you).
2. The included workflow (`.github/workflows/build-apk.yml`) builds the APK automatically, in about 5 minutes.
3. On your phone, open the repository's **Releases** page, tap **PureSudoku.apk**, then open the download. Allow "Install unknown apps" for your browser when Android asks.

Every push builds a fresh release. You can also rebuild by hand from the Actions tab ("Build APK" → "Run workflow").

## Run it from Android Studio

1. Open this folder in **Android Studio** (Ladybug or newer). Let Gradle sync.
2. Pick a device or emulator and press **Run**.

Or from a terminal: `./gradlew installDebug` (device connected), or `./gradlew assembleRelease` for a release APK in `app/build/outputs/apk/release/`. The release build is signed with the debug key so it installs directly; use your own key before publishing anywhere.

Requires Android 8.0 (API 26) or newer.

## Features

- **Six difficulties**: Easy, Medium, Hard, Expert, Master, Extreme.
- **3 save slots**. Games autosave on every move, every few seconds, and when the app goes to the background. When all three are full, starting a new game asks which one to replace.
- **3-strike game over**. A wrong number is rejected, shakes red and costs a heart. The third strike ends the game and clears its slot.
- **Time to solve**. The clock runs only while you're playing (it stops when paused or when the app is in the background, and pausing hides the board). Each solve records your time; the home screen shows solves, best time, average time and best score per difficulty, and the win screen flags new records.
- **Scoring**: points per correct number (higher on harder levels) with a small streak bonus, a bonus for each row/column/box completed, a penalty for mistakes, and on finishing a time bonus (beat the par time) plus a bonus for strikes left.
- **Notes** (pencil marks), undo and erase for notes. Placing a number clears it from neighbouring notes.
- **Animations**: board build-in cascade, number pop, floating points, completion ripple across finished rows/columns/boxes, error shake, heart loss, win wave with confetti, animated screen transitions.

## Logo and fonts

The launcher icon is the grid from the Pure Sudoku logo, recoloured to the app theme (`res/drawable/ic_launcher_foreground.xml`, plus a monochrome version for Android 13 themed icons). In the app, the same grid is drawn live (`ui/components/Logo.kt`) so it can animate: digits pop in on the home screen, the highlight sweeps while a puzzle is built, and a small copy tops the win card. Text uses Poppins, the logo's typeface, licensed under the SIL Open Font License 1.1.

## The puzzle generator (`app/src/main/java/com/puresudoku/core`)

1. A new solved grid comes from randomized backtracking, then is scrambled with validity-preserving transforms (digit relabelling, row/column swaps within bands, band/stack swaps, transpose). Seeds come from `SecureRandom`.
2. Clues are removed in random order (mirrored pairs on the easier grades); a removal is kept only if the puzzle still has exactly **one** solution.
3. `LogicGrader` solves the puzzle like a person would and records the hardest technique required. A puzzle is accepted only if it genuinely needs that difficulty's techniques:

| Level   | Clues | Required techniques |
|---------|-------|---------------------|
| Easy    | 38–42 | singles only |
| Medium  | 32–36 | singles (up to locked candidates) |
| Hard    | 28–32 | locked candidates or pairs |
| Expert  | 25–29 | pairs, triples or X-Wing |
| Master  | 23–27 | triples/X-Wing up to Swordfish, XY-Wing, XYZ-Wing |
| Extreme | 20–25 | beyond all of the above |

In testing (360 puzzles, 60 per level) every puzzle was unique and landed in its grade, with generation averaging under 25 ms.
