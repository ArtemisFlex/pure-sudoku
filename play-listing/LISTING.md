# PureGames: Google Play listing

Everything Play Console asks for, in the order it asks. Images are in this folder.

| Item | Value |
|---|---|
| Package ID | `app.puregames` (permanent once uploaded) |
| Version | 1.0 (version code 3; codes 1–2 were earlier builds) |
| Min / target Android | 8.0 (API 26) / Android 16 (API 36, required by Play) |
| Bundle | `app/build/outputs/bundle/release/app-release.aab` |

---

## Main store listing

**App name** (30 max)

```
PureGames: 60 Brain Games
```

**Short description** (80 max)

```
Sudoku, chess, solitaire, word games and strategy. No ads, no tracking.
```

**Full description** (4000 max)

```
Sixty games in one app, with no ads, no accounts and no tracking. Every puzzle is freshly made, so you never run out, and almost everything works offline.

NUMBER & LOGIC
Sudoku, Kakuro, Cage Math, Picture Logic (nonograms), Hashi bridges, Code Breaker, Lumen lights-out, Sliding Tiles and Grid Recall.

WORD GAMES
Crossword, Word Search, Word Wheel, Word Ladder, Rhyme Time, Synonyms & Antonyms, Alliteration, and Haiku, where you write real 5-7-5 poems.

CARD GAMES
Classic Solitaire, Blackjack, Go Fish and Crazy Eights, plus card adventures: Scoundrel's dungeon crawl, Dungeon Delver's pyramid floors, Regicide, Dungeon Solitaire, Cuttle and Joker Jailbreak.

BOARD GAMES
Chess, Checkers, Go, Shogi, Xiangqi, Backgammon, Mancala, Mahjong, Gomoku, Nine Men's Morris, Tic-Tac-Toe, Sea Battle and Cratebarge, plus Kreat, an original war game across a river of mountains and forests, with painted pieces.

STRATEGY
Build, trade and conquer on fantasy islands: Eiseria and its Wyrmrise, Nightfall and Ashfall variants, Wisteria and Wisteria: Ashfall, Kreashun, Veilguard, Deed or Dungeon, and Aegwaspell, a civilization game about feeding your people through the seasons, with its Conquest variant.

BRAIN TRAINING
Street Smarts: deliver around a big city while the GPS slowly lets go, until you know the streets by heart.

EARLY LEARNING (AGES 4–10)
Math flashcards, long division, missing numbers, letter sounds, word cards, reading practice and telling the time, with every step shown.

PLAY YOUR WAY
• Difficulty levels from gentle to fiendish
• Training wheels mode coaches you through your first games
• Play a friend on one phone, or online with a four-letter room code
• Stats, streaks, best times and a personal leaderboard for every game
• Pin up to 16 favourites to your home screen
• Light and dark themes
• Back up your progress to a folder you choose

PRIVATE BY DESIGN
No ads. No in-app purchases. No sign-up. No analytics. Your games stay on your phone.
```

**Graphics**

| Asset | File | Spec |
|---|---|---|
| App icon | `icon-512.png` | 512×512 PNG, full bleed (Play rounds the corners) |
| Feature graphic | `feature-graphic-1024x500.png` | 1024×500 |
| Phone screenshots | `phone-screenshots/01…08` | 8 × 1080×1920 (9:16) |

Tablet screenshots are optional and the app is phone-portrait, so skip them.

## Store settings

- **App or game:** Game
- **Category:** Puzzle
- **Tags** (pick up to 5): Brain games, Board, Card, Puzzle, Strategy
- **Email:** support@puregames.app (set via API; needs mail forwarding)
- **Website:** https://puregames.app
- **Privacy policy:** https://puregames.app/privacy.html (live)
- **Terms of service:** https://puregames.app/terms.html (not asked for by Play; linked from the app home page)

## App content

### Privacy policy
`https://puregames.app/privacy.html`

### Ads
**No**, the app does not contain ads.

### App access
**All functionality is available without special access.** No login.

### Content rating (IARC questionnaire)
Category: **Game**. Answer honestly; these are the facts:

| Question | Answer | Why |
|---|---|---|
| Violence | Yes: fantasy, non-realistic, no blood | Pieces are captured; card monsters are fought (pixel art, no gore) |
| Fear / horror | No | |
| Sexuality, language, drugs | No | |
| **Simulated gambling** | **Yes** | Blackjack with play chips (no real money, nothing to buy) |
| Users interact | Yes | Online play with a friend by room code (moves only, **no chat**) |
| Shares location | No | |
| Digital purchases | No | |

Simulated gambling usually lands around PEGI 12 / ESRB Teen. That matters for the target audience question below.

### Target audience and content
**You have to decide this one.** The Early Learning section is made for ages 4–10, but:

- **If you include ages under 13:** the Families policy applies, and it **forbids simulated gambling**. Blackjack would have to go (or be hidden), and the IARC rating must drop accordingly. You'd also need to keep the online feature compliant (it already has no chat or profiles).
- **If you choose 13+ only:** simpler, but Google may still flag the app as "appealing to children" because of Early Learning. You'd answer that it's designed for teens and adults, with learning tools for parents to use with their children.

My recommendation: **13 and over**, with "Not designed for children" plus the note above. Revisit if Google pushes back.

### News app
No.

### Health apps
No.

### Government apps
No.

### Financial features
None.

### Data safety

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS) |
| Do you provide a way for users to request that their data is deleted? | **No:** data deletes itself within 2 hours. Say so in the free-text field if asked. |

Data types, for **"Play a friend online"** only:

| Data type | Collected | Shared | Ephemeral | Required? | Purpose |
|---|---|---|---|---|---|
| App activity → **Other user-generated content** (game moves) | Yes | No | Yes | Optional (only when playing online) | App functionality |

Everything else (location, personal info, identifiers, contacts, photos, health, financial, etc.): **not collected**.

Note: "shared" means handed to a third party. Vercel and Upstash process the data on your behalf (service providers), which Play doesn't count as sharing.

## Release

**Track:** **Production.** This is an organization developer account, so the 12-tester, 14-day closed test rule for new personal accounts doesn't apply.

**Release name:** `1.0`

**Release notes** (500 max)

```
<en-US>
Welcome to PureGames: 60 games in one app, with no ads and no tracking. Sudoku, chess, solitaire, word games, card adventures, deep strategy and early learning for kids. Play solo, pass the phone to a friend, or play online with a room code.
</en-US>
```

## Before you submit

1. **Upload key.** Create it once (Android Studio: Build → Generate Signed App Bundle → Create new). Then create `keystore.properties` in the project root (it's git-ignored):
   ```
   storeFile=C:/path/to/puregames-upload.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```
   Then run `./gradlew :app:bundleRelease` and upload `app/build/outputs/bundle/release/app-release.aab`. Back up the `.jks` and its passwords. Choose **"Let Google manage and protect your app signing key"** when asked.
2. **Privacy policy:** live at https://puregames.app/privacy.html (contact support@puregames.app).
3. **Versioning.** Each upload needs a higher `versionCode` in `app/build.gradle.kts`.
