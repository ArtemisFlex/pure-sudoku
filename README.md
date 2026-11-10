# PureGames

Nine offline, single-player brain games for Android (Kotlin + Jetpack Compose). No internet permission, no ads, no accounts.

| Game | Skill | What you do |
|---|---|---|
| Sudoku | Logic | Fill the grid so every row, column and box holds 1–9 |
| Cage Math | Arithmetic | Latin square where every outlined cage hits its target with + − × ÷ |
| Kakuro | Arithmetic | Crossword of sums; no repeats within a run |
| Picture Logic | Deduction | Nonogram: row/column clues reveal a hidden picture |
| Hashi | Logic | Connect islands with the right number of bridges, no crossings |
| Grid Recall | Memory | Memorize numbers on a grid, then put them back (in order from Expert up) |
| Code Breaker | Reasoning | Crack a hidden colour code from exact/near clues |
| Lumen | Puzzle | Lights-out: switch every light off in as few moves as possible |
| Sliding Tiles | Spatial | Slide the tiles back into order, 3×3 up to 6×6 |

## Get the APK on your phone

Every push to `main` builds an APK with GitHub Actions (`.github/workflows/build-apk.yml`) and publishes it on the **Releases** page. Open the latest release on your phone, tap **PureGames.apk**, and allow "Install unknown apps" for your browser when asked. Installing over an older version keeps your saves.

To build locally: open the folder in Android Studio and press Run, or run `./gradlew assembleRelease` (APK lands in `app/build/outputs/apk/release/`). The release build is signed with the debug key so it installs directly; use your own key before publishing anywhere. Requires Android 8.0 (API 26) or newer.

## Shared features

- **Six levels per game**: Easy, Medium, Hard, Expert, Master, Extreme.
- **3 save slots per game**, saved on every move, every few seconds and when the app goes to the background. Saves from the original Pure Sudoku carry over.
- **3-strike game over** wherever mistakes are possible (Code Breaker uses a guess limit; Lumen and Sliding Tiles count moves against par).
- **Time to solve** with best and average times, solve counts and best scores per game and level.
- **Scoring**: points for correct moves with a streak bonus, bonuses for completing lines/cages/islands/rounds, and on finishing a time bonus plus hearts-left, guesses-left or par bonus.
- **How to play**: every game has an animated card deck. Each card plays a scripted clip on the real game board (a finger shows each tap and drag) next to the instruction it illustrates. It opens automatically the first time you play a game and from the ⓘ button in the game's top bar at any time.
- **Appearance**: System / Light / Dark, from the gear on the home screen.

## Puzzle generators (`app/src/main/java/com/puresudoku/core`)

Every puzzle is generated fresh on the device, and every logic puzzle is checked to have exactly one answer.

- **Sudoku**: random solved grid (randomized backtracking + symmetry scrambles), clues removed while the solution stays unique, graded by the human techniques required.
- **Cage Math**: random Latin square, random cages and operations, exact solver; any cell where two answers differ is split off as a given until the answer is unique.
- **Kakuro**: random symmetric layouts (runs of 2+ cells, all white cells connected), random fills, run-sum-pruned solver; ambiguity is repaired with a new fill or a revealed cell.
- **Picture Logic**: random noise smoothed into shapes, adjusted until line logic alone solves it — so it never needs a guess.
- **Hashi**: networks grown bridge by bridge with extra loop bridges, kept only when a propagating solver proves one solution.
- **Grid Recall / Code Breaker**: random patterns and codes from a secure seed.
- **Lumen**: boards built by random presses (always solvable); par is the true minimum, found by solving the press equations over GF(2).
- **Sliding Tiles**: random solvable arrangements (parity-checked) or scrambles; exact par on 3×3 by breadth-first search.

Text uses Poppins (SIL Open Font License 1.1).
