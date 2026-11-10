package com.puresudoku.ui.training

import com.puresudoku.core.GameKind

object NumberLessons {
    val all: Map<GameKind, Course> = mapOf(
        GameKind.SUDOKU to Course(
            listOf(
                "The grid has 9 rows, 9 columns and 9 boxes (the 3×3 blocks). Each must end up holding 1–9 exactly once.",
                "Tap a cell, then a number. Use notes (pencil marks) to jot the numbers a cell could still be.",
                "A wrong number is a strike. Three strikes ends the game, so only place numbers you're sure of.",
            ),
            listOf(
                listOf(
                    "Full house: when a row, column or box has one empty cell, the missing number goes there.",
                    "Naked single: list what a cell's row, column and box already use. If only one number is left, that's it.",
                    "Hidden single: pick a number and a box. If only one cell in that box can take it, place it.",
                ),
                listOf(
                    "Scan by number: take 1, then 2, and so on, and look for hidden singles in every box.",
                    "Pointing pair: if a number's only spots in a box sit in one row, that row can't have it anywhere else outside the box.",
                    "Keep notes tidy: clear a note as soon as a number lands in the same row, column or box.",
                ),
                listOf(
                    "Naked pair: two cells in a unit with the same two candidates. Those two numbers can be removed from the rest of the unit.",
                    "Box/line reduction: if a number's only spots in a row all fall in one box, remove it from the rest of that box.",
                    "When stuck, fill in all notes for a box at once; pairs pop out.",
                ),
                listOf(
                    "Hidden pair: two numbers that can only go in the same two cells of a unit. Every other note in those two cells can go.",
                    "Naked and hidden triples work the same way with three cells and three numbers.",
                    "Work the most crowded units first: fewer empties means fewer possibilities.",
                ),
                listOf(
                    "X-Wing: if a number fits in only two cells in each of two rows, and they share the same two columns, remove it from the rest of those columns.",
                    "Swordfish is the same idea across three rows and three columns.",
                    "Look for bivalue cells (two candidates). They power chains and wings.",
                ),
                listOf(
                    "XY-Wing: a pivot cell AB sees two cells AC and BC. Any cell seeing both wings can't be C.",
                    "Simple coloring: follow a number through strong links (only two spots in a unit), alternating two colors. If a color clashes, the other color is true.",
                    "Patience: write every note, then hunt for the one pattern that breaks the deadlock.",
                ),
            ),
        ),
        GameKind.CAGE_MATH to Course(
            listOf(
                "Fill the grid so every row and column uses each number once (like Sudoku, but no boxes).",
                "Thick outlines are cages. The small clue says the target and operation: the cage's numbers must combine to make it.",
                "For − and ÷ cages (always two cells), take the larger number first: 6 and 2 make 3÷ or 4−.",
            ),
            listOf(
                listOf("Single-cell cages are free numbers: fill them first.", "A two-cell 3+ cage on a small grid can only be 1 and 2.", "Every row and column must use every number, so the last empty cell in a row is forced."),
                listOf("List the pairs that fit a cage: 5+ could be 1+4 or 2+3.", "If a cage sits entirely in one row, its numbers are taken out of the rest of that row.", "Big × targets narrow things fast: 12× in two cells is 3×4 or 2×6."),
                listOf("Combine cages: a row's numbers always sum to the same total, so a missing piece can be worked out.", "When two cages share a row and both need the same number, they can't both have it.", "Use notes for cage options and trim them as rows fill."),
                listOf("Prime factors help: a 7× cage must contain a 7 (and the rest 1s).", "In − cages, the difference tells you the gap: 5− on a 6×6 grid must be 1 and 6.", "Look for a cage whose only options all include one number: that number is in the cage."),
                listOf("Count totals: three cages filling a row must add up to the row total; use it to solve the odd cage out.", "Repeated digits are allowed in an L-shaped cage if they're not in the same row or column.", "Work cages that cross two rows and two columns: they have the most constraints."),
                listOf("Think in sets: a group of cages covering two whole rows must contain each number twice.", "Combine Sudoku-style pairs with cage maths: if two cells must be {2,5}, cages touching them lose those options.", "Stay calm on big grids; one forced cell usually breaks open a whole region."),
            ),
        ),
        GameKind.KAKURO to Course(
            listOf(
                "Kakuro is a crossword of sums. Each white run (across or down) must add up to the clue at its start.",
                "Use digits 1–9, and never repeat a digit within one run.",
                "Tap a cell, then a digit. Wrong digits cost a strike.",
            ),
            listOf(
                listOf("Learn the unique sums: 3 in two cells is 1+2; 4 is 1+3; 16 is 7+9; 17 is 8+9.", "Start where an across run and a down run with unique sums cross.", "A run's biggest digit can't exceed its clue."),
                listOf("Three cells: 6 is 1+2+3 and 24 is 7+8+9 — the only ways.", "Where two runs cross, the digit must be possible in both.", "Fill a run's last cell by subtracting the others from the clue."),
                listOf("Four cells: 10 is 1+2+3+4 and 30 is 6+7+8+9.", "If a run's options all contain a digit, that digit is somewhere in the run.", "Use notes for small candidate sets."),
                listOf("Rule out by size: in a 2-cell run of 11, a cell can't be 1 (it'd need 10).", "Odd/even reasoning: a 2-cell run with an odd sum needs one odd and one even.", "Check crossing totals to trim candidates."),
                listOf("Block sums: the total of all across clues in an area equals the total of the down clues; use it to pin a lonely cell.", "Long runs (7–9 cells) are about what's missing: 9 cells always total 45.", "Combine missing-digit logic with crossings."),
                listOf("Look for small isolated sections and solve them as closed systems.", "Chains of two-option cells force each other; follow them.", "Never guess: every Extreme puzzle has a logical route."),
            ),
        ),
        GameKind.PICTURE_LOGIC to Course(
            listOf(
                "Fill cells to reveal a hidden picture. The numbers beside each row and above each column are the lengths of its filled blocks, in order.",
                "Blocks are separated by at least one empty cell. Mark cells you know are empty with an X.",
                "A wrong fill is a strike.",
            ),
            listOf(
                listOf("A clue equal to the row length fills the whole row.", "A 0 (or empty clue) means the row is blank: cross it all out.", "Big clues overlap: a 7 in a 10-wide row fills the middle 4 cells."),
                listOf("Overlap works for several blocks too: push them all left, then all right, and fill where the same block overlaps.", "Once a block is complete, cap both ends with X.", "Switch between rows and columns often."),
                listOf("Edges help: a filled cell touching the edge starts the first block, so fill it to that block's length.", "Gaps too small for any remaining block must be empty.", "Count what's left after the solved blocks."),
                listOf("Glue: a filled cell near the edge forces the block to cover it, extending the fill.", "If two blocks could cover a cell, reason about which one can reach it.", "Work from the most constrained lines."),
                listOf("Split a line at known X's and solve each segment separately.", "Try matching clue blocks to segments by size: the biggest block only fits in the biggest gap.", "Track partial blocks: a fill of 3 inside a clue of 4 needs one more cell."),
                listOf("On large grids, solve by regions: finish a section completely before moving on.", "Use contradiction checks: if a fill would make a line impossible, it's an X.", "The picture itself often hints at symmetry, but always confirm with the clues."),
            ),
        ),
        GameKind.HASHI to Course(
            listOf(
                "Each circle (island) shows how many bridges must connect to it.",
                "Bridges run straight across or down, never cross other bridges, and two islands can share at most two bridges.",
                "All islands must end up joined into one connected network.",
            ),
            listOf(
                listOf("An island with one neighbour gives all its bridges to that neighbour.", "A 4 in a corner (two neighbours) needs double bridges to both.", "Tap between two islands to add a bridge; tap again for a second."),
                listOf("A 3 with two neighbours needs at least one bridge to each.", "A 6 with three neighbours needs at least one bridge to each; an 8 needs double bridges to all four.", "Finished islands block bridges that would cross their paths."),
                listOf("A 1 can't bridge to another 1 if that would cut them off from everything else.", "Two 2's joined by a double bridge would isolate themselves: don't.", "Look for islands with exactly enough neighbours."),
                listOf("Count a neighbour's spare capacity: if it can only take one more, you can't double to it.", "Bridges block lines of sight: placing one may leave an island with fewer neighbours.", "Keep the network in mind: every group must still reach the rest."),
                listOf("Isolation: avoid closing a group whose numbers are all satisfied while islands remain elsewhere.", "Use parity: an odd number with two neighbours needs a single to at least one.", "Work from the crowded parts of the map outward."),
                listOf("Think in components: list the separate groups and which bridges could still link them.", "When two options remain, one usually disconnects the map.", "Patience pays: Extreme maps have long chains of forced moves."),
            ),
        ),
        GameKind.GRID_RECALL to Course(
            listOf(
                "Numbers flash on the grid for a few seconds. Memorise them before they hide.",
                "Then tap each square and enter the number that was there.",
                "Wrong entries are strikes; on harder levels the order matters too.",
            ),
            listOf(
                listOf("Say the numbers to yourself in rows, like a phone number.", "Focus on positions first, numbers second.", "Start entering from the cell you remember best."),
                listOf("Group cells into shapes: an L, a diagonal, a corner.", "Link numbers to things: 7 looks like a flag, 0 like an egg.", "Go row by row when entering so you don't skip one."),
                listOf("Chunk in threes: 4-1-9 is easier than 4, 1, 9.", "Notice patterns: repeats, sums, ascending runs.", "Glance at the whole grid before zooming in."),
                listOf("Ordered recall: trace the path the numbers appeared in, like a dot-to-dot.", "Build a story: 'the 8 went right, the 3 went down'.", "Don't rush the first entry; the rest flows from it."),
                listOf("Memory palace: assign each corner of the grid a place you know and 'leave' numbers there.", "Use rhythm: tap the beat of the sequence as it plays.", "Spend the early seconds on the hardest cells."),
                listOf("Combine shape, order and story for each number.", "Rehearse once silently before the grid hides.", "Stay relaxed: tension drains short-term memory."),
            ),
        ),
        GameKind.CODE_BREAKER to Course(
            listOf(
                "A hidden code of coloured pegs is set. Fill a row with your guess and tap Guess.",
                "● means right colour in the right place. ○ means the colour is in the code but somewhere else.",
                "Crack the code before you run out of rows.",
            ),
            listOf(
                listOf("Open with two colours in pairs (red red blue blue) to test them at once.", "If you get no marks at all, those colours are out completely.", "Keep the ● colours where they are next time."),
                listOf("Change one thing at a time when you need to know what moved the score.", "Use ○ results to try the colour in a new position.", "Count total marks to know how many of each colour are in the code."),
                listOf("Plan guesses that split the remaining possibilities, not just ones that might win.", "Re-read all earlier rows before every guess: every row must still be consistent.", "Repeated colours are allowed if the level says so: test them."),
                listOf("Track each colour's possible positions in your head or on a mental grid.", "A guess with a known-wrong colour can isolate a position.", "Aim to finish within five guesses."),
                listOf("Use consistent guesses only: never play something an earlier row rules out.", "Think about which guess gives the most different possible answers.", "With many colours, find which colours are in first, then positions."),
                listOf("Full logic: list the codes still possible and pick the guess that leaves the fewest in the worst case.", "Stay systematic; one careless guess can cost two rows.", "Practice turns this into intuition."),
            ),
        ),
        GameKind.LUMEN to Course(
            listOf(
                "Tap a tile to flip it and its neighbours (the pattern depends on the level). Lit becomes dark and dark becomes lit.",
                "Goal: switch every light off.",
                "Order doesn't matter, and tapping a tile twice undoes it.",
            ),
            listOf(
                listOf("Chase the lights: go row by row, tapping the tile below every lit tile.", "When only the bottom row is lit, you'll need a pattern for the top row: experiment.", "Fewer taps score more."),
                listOf("Light chasing always cleans every row but the last.", "Remember which top-row taps fixed which bottom patterns.", "Never tap the same tile twice in a solution."),
                listOf("Each tap is either in the solution or not: think in on/off, not order.", "Work from a corner; corners flip fewest tiles.", "Undo by tapping again if you get lost."),
                listOf("Diagonal patterns (Cross) affect only one colour of a checkerboard: solve the two colours separately.", "Chase with diagonals the same way, one row at a time.", "Look for symmetric light shapes: their solutions are often symmetric too."),
                listOf("King patterns flip eight neighbours: chase lights one row down and to the side.", "Build a small table of top-row fixes for each bottom pattern you meet.", "Count taps: the minimum is the challenge."),
                listOf("Think of it as maths: each light is a sum of taps, modulo 2.", "Try solving with the fewest taps first, then confirm.", "Patience beats random tapping every time."),
            ),
        ),
        GameKind.SLIDING_TILES to Course(
            listOf(
                "Slide tiles into the empty space to put them back in order: 1, 2, 3… left to right, top to bottom.",
                "Only tiles next to the gap can move. Tap one (or a row/column in line with the gap) to slide.",
                "Fewer moves score more.",
            ),
            listOf(
                listOf("Solve the top row first, then the next row; never disturb finished rows.", "Bring a tile toward its home by moving the gap around it.", "The last two tiles of a row need a small trick: park one below, then swing both in."),
                listOf("Solve the first row, then the first column, shrinking the puzzle each time.", "Move the gap, not the tile: think 'where must the gap go next'.", "Rotate three tiles around a 2×2 block to reposition them."),
                listOf("For the last two tiles of a row, place them in reverse below, then rotate in.", "Keep finished tiles locked in your mind; don't break them.", "The final 2×3 area is a rotation puzzle: cycle it."),
                listOf("Bigger boards: alternate rows and columns to keep the remaining area square.", "Plan two moves ahead: where will the gap end up?", "Avoid back-and-forth moves; they waste count."),
                listOf("Learn the 3-tile cycle cold: it fixes most endgames.", "If the last tiles are swapped, re-solve the 2×3 area rather than forcing it.", "Speed comes from fewer wasted gap moves."),
                listOf("On large boards, solve outer layers like an onion.", "Use the gap's path efficiently: the shortest route around the target tile.", "Count your moves and aim to beat your best."),
            ),
        ),
    )
}
