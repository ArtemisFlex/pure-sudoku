package com.puresudoku.core

enum class GameCategory(val title: String, val blurb: String) {
    LOGIC("Mathematics & Numbers", "Sudoku, Kakuro, logic and memory puzzles"),
    WORDS("Language & Words", "Crosswords, poetry, vocabulary and wordplay"),
    CARDS("Card Games", "Solitaires, dungeon crawls and duels with a 52-card deck"),
}

/** Every game in Pure Puzzles. Order here is the order on the home screen. */
enum class GameKind(val title: String, val skill: String, val tagline: String, val category: GameCategory = GameCategory.LOGIC) {
    SUDOKU("Sudoku", "Logic", "Fill the grid so every row, column and box holds 1–9."),
    CAGE_MATH("Cage Math", "Arithmetic", "Latin squares where every cage hits its target."),
    KAKURO("Kakuro", "Arithmetic", "A crossword of sums. No repeats in a run."),
    PICTURE_LOGIC("Picture Logic", "Deduction", "Use the number clues to reveal a hidden picture."),
    HASHI("Hashi", "Logic", "Connect every island with the right number of bridges."),
    GRID_RECALL("Grid Recall", "Memory", "Memorize the numbers, then put them back."),
    CODE_BREAKER("Code Breaker", "Reasoning", "Crack the hidden code from the clues you earn."),
    LUMEN("Lumen", "Puzzle", "Switch every light off in as few moves as you can."),
    SLIDING_TILES("Sliding Tiles", "Spatial", "Slide the tiles back into order."),
    CROSSWORD("Crossword", "Vocabulary", "Solve the clues to fill the grid.", GameCategory.WORDS),
    WORD_SEARCH("Word Search", "Focus", "Find every hidden word in the letter grid.", GameCategory.WORDS),
    HAIKU("Haiku", "Poetry", "Write haiku in lines of 5, 7 and 5 syllables.", GameCategory.WORDS),
    ALLITERATION("Alliteration", "Sound", "Spot the word that breaks the pattern of sounds.", GameCategory.WORDS),
    SYNONYMS("Synonyms & Antonyms", "Meaning", "Pick the word that means the same, or the opposite.", GameCategory.WORDS),
    RHYME_TIME("Rhyme Time", "Sound", "Find the true rhyme, not the look-alike.", GameCategory.WORDS),
    WORD_LADDER("Word Ladder", "Wordplay", "Change one letter at a time to reach the goal word.", GameCategory.WORDS),
    WORD_WHEEL("Word Wheel", "Wordplay", "Make words from seven letters, always using the centre.", GameCategory.WORDS),
    SOLITAIRE("Solitaire", "Patience", "Classic Klondike: build the suits from Ace to King.", GameCategory.CARDS),
    BLACKJACK("Blackjack", "Odds", "Beat the dealer to 21 and grow your chips.", GameCategory.CARDS),
    GO_FISH("Go Fish", "Memory", "Ask for cards, collect books of four.", GameCategory.CARDS),
    SCOUNDREL("Scoundrel", "Roguelike", "Fight your way through a dungeon made of cards.", GameCategory.CARDS),
    CUTTLE("Cuttle", "Combat", "Race to 21 points while wrecking your rival's.", GameCategory.CARDS),
    PYRAMID("Dungeon Delver", "Patience", "Pyramid solitaire, floor by floor into the deep.", GameCategory.CARDS),
    REGICIDE("Regicide", "Strategy", "Defeat the twelve corrupted royals, solo.", GameCategory.CARDS),
    DUNGEON_SOLITAIRE("Dungeon Solitaire", "Adventure", "Raid the Tomb of Four Kings and escape alive.", GameCategory.CARDS),
    JAILBREAK("Joker Jailbreak", "Patience", "Break down a wall to set the Joker free.", GameCategory.CARDS),
}
