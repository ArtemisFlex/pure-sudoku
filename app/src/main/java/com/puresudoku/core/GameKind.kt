package com.puresudoku.core

/** Every game in Pure Puzzles. Order here is the order on the home screen. */
enum class GameKind(val title: String, val skill: String, val tagline: String) {
    SUDOKU("Sudoku", "Logic", "Fill the grid so every row, column and box holds 1–9."),
    CAGE_MATH("Cage Math", "Arithmetic", "Latin squares where every cage hits its target."),
    KAKURO("Kakuro", "Arithmetic", "A crossword of sums. No repeats in a run."),
    PICTURE_LOGIC("Picture Logic", "Deduction", "Use the number clues to reveal a hidden picture."),
    HASHI("Hashi", "Logic", "Connect every island with the right number of bridges."),
    GRID_RECALL("Grid Recall", "Memory", "Memorize the numbers, then put them back."),
    CODE_BREAKER("Code Breaker", "Reasoning", "Crack the hidden code from the clues you earn."),
    LUMEN("Lumen", "Puzzle", "Switch every light off in as few moves as you can."),
    SLIDING_TILES("Sliding Tiles", "Spatial", "Slide the tiles back into order."),
}
