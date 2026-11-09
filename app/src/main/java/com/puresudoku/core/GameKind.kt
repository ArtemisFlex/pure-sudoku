package com.puresudoku.core

enum class GameCategory(val title: String) { LOGIC("Logic & Numbers"), WORDS("Words & Literature") }

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
}
