package com.puresudoku.ui.manual

import com.puresudoku.core.GameKind
import com.puresudoku.core.GameKind.*

/** One block of a manual section: a paragraph or a bulleted list. */
sealed class MB {
    class P(val text: String) : MB()
    class B(val items: List<String>) : MB()
}

class ManualSection(val title: String, val body: List<MB>)

/**
 * The written manual for every game that doesn't have an illustrated rulebook.
 * The manual screen adds the game's levels and, where it applies, two-player play.
 */
object ManualText {

    private class S {
        val out = ArrayList<ManualSection>()
        private var cur: ArrayList<MB>? = null
        fun sec(title: String, block: S.() -> Unit) { val b = ArrayList<MB>(); cur = b; block(); out += ManualSection(title, b); cur = null }
        fun p(t: String) { cur!! += MB.P(t) }
        fun b(vararg t: String) { cur!! += MB.B(t.toList()) }
    }

    private fun m(block: S.() -> Unit) = S().apply(block).out

    // shared pieces ---------------------------------------------------------------------------

    private fun S.hearts(what: String = "A wrong entry") = sec("Strikes") {
        p("$what is never placed: it flashes red and costs one of your three hearts. Lose all three and the game is over.")
        p("Hearts left at the end add to your bonus, so careful play pays.")
    }

    private fun S.saving() = sec("Saving, score and time") {
        p("Your game saves itself as you play. Up to three games of each kind are kept, so you can leave and come back from the game's menu.")
        p("Points come from correct moves, with bonuses for finishing quickly and cleanly. Your best scores, times and streaks are on the game's menu.")
        p("Pause hides the game and stops the clock.")
    }

    private fun S.flashcards(subject: String, how: List<String>) {
        sec("The goal") { p("Ten cards a round. Work out each card and tap the answer, or type it on the number pad. $subject") }
        sec("How each level works") { b(*how.toTypedArray()) }
        sec("The i button") {
            p("Every card has an i button. Tap it to see how to work the card out, one step at a time.")
            p("If an answer is wrong, the steps open by themselves so the right way is learned straight away.")
        }
        sec("Stars") {
            p("A card answered right the first time earns a star. Collect stars round after round; the round summary shows how many were right first time.")
            p("Each level matches an age, from Easy (age 4) to Extreme (age 10). Move up a level when a round feels easy.")
        }
        sec("For grown-ups") {
            p("There's no timer pressure and no lives: a wrong answer simply shows the steps. Sit with younger children and read the cards aloud together.")
        }
    }

    // the manuals ----------------------------------------------------------------------------

    fun of(kind: GameKind): List<ManualSection> = when (kind) {
        SUDOKU -> m {
            sec("The goal") { p("Fill every empty square so that each row, each column and each 3×3 box holds the numbers 1 to 9 exactly once. Every puzzle has exactly one solution, and it can always be reached by logic, never guessing.") }
            sec("Playing") {
                b("Tap an empty square to select it, then tap a number below.",
                    "The square's row, column and box light up, and every copy of the selected number is highlighted.",
                    "Numbers that are used up (all nine placed) fade from the number pad.")
            }
            sec("Notes") {
                p("Turn on Notes to pencil small candidates into a square. Tap a number again to remove it. Placing a real number clears that number from the notes in its row, column and box.")
            }
            hearts()
            sec("Solving techniques") {
                b("Single position: a number that fits in only one square of a row, column or box goes there.",
                    "Single candidate: a square where only one number is still possible.",
                    "Pairs: two squares in a unit that can only hold the same two numbers rule those numbers out of the rest of the unit.",
                    "Pointing: if a number's spots in a box all lie in one row or column, it can't go elsewhere in that row or column.",
                    "On Master and Extreme, expect to need pairs, triples and X-wings.")
            }
            saving()
        }
        STREET_SMARTS -> m {
            sec("The idea") { p("You drive deliveries around Harbor City. The GPS starts out showing you everything and slowly lets go as you learn the streets, until you're finding places from memory. The career never ends: the city grows as you do.") }
            sec("Driving") {
                b("At each corner, tap a direction: left, straight, right or turn around. The buttons show the street you'd turn onto.",
                    "Street signs at the top-left show where you are.",
                    "Red lights and stop signs cost seconds; one-way streets only go one way (watch the arrows); some blocks are closed for road works.",
                    "Pinch to zoom and drag to look around the map. The recentre button brings the car back to the middle.")
            }
            sec("Orders") {
                p("Accept an order, drive to the pickup, then to each drop. The order shows its pay and a time budget. A shift is a few orders and ends with the drive home.")
                p("Routes start at four or five turns and grow longer as you level up. When the GPS offers more than one route, pick one: the shortest earns a route bonus. Leave the route and the GPS reroutes you.")
            }
            sec("How much the GPS helps") {
                b("Route: the way is drawn on the map.",
                    "Turns: a list of turns, no line.",
                    "Address: just the street and number.",
                    "Name: only the place's name.",
                    "Memory: the name, and only landmarks are marked on the map.")
                p("Each place has its own help level. Quick, clean trips make a place familiar so it gets less help next time; getting lost brings help back.")
            }
            sec("Coins, hints and the garage") {
                p("Fast, clean deliveries earn coins and tips. Spend coins on hints when you're stuck (a peek at the route, the next turn, or the address), or in the garage on new vehicles and paint.")
                p("Every few deliveries you level up: more of the city opens and orders get longer.")
            }
            sec("Point the way") { p("Before some drives you're asked which way the place is from you. Point the right compass direction for bonus coins.") }
        }
        LEARN_MATH -> m { flashcards("Each card is a sum, from counting pictures to long multiplication.", listOf(
            "Easy (age 4): count pictures and add small groups.", "Medium (age 5): adding and taking away up to 10.",
            "Hard (ages 6–7): adding and subtracting up to 20, and two-digit sums.", "Expert (age 8): times tables, division facts and two-digit subtraction.",
            "Master (age 9): bigger multiplication and three-digit sums.", "Extreme (age 10): long multiplication and order of operations.")) }
        LEARN_DIVISION -> m { flashcards("Each card asks you to share or divide.", listOf(
            "Easy (age 4): share things out fairly between friends.", "Medium (age 5): sharing and making equal groups.",
            "Hard (ages 6–7): long division, two digits by one.", "Expert (age 8): long division, three digits by one.",
            "Master (age 9): long division with remainders.", "Extreme (age 10): four digits by two-digit numbers, with remainders.")) }
        LEARN_ALGEBRA -> m { flashcards("Each card hides a number: find it, or solve for x.", listOf(
            "Easy (age 4): picture puzzles, how many more?", "Medium (age 5): missing numbers up to 10.",
            "Hard (ages 6–7): missing numbers in sums and times tables.", "Expert (age 8): solve for x in one step.",
            "Master (age 9): two-step equations.", "Extreme (age 10): x on both sides, and brackets.")) }
        LEARN_LETTERS -> m { flashcards("Look at the picture, say the word out loud, and listen for its letters.", listOf(
            "Easy (age 4): the first letter of a picture word.", "Medium (age 5): first letters and matching pictures.",
            "Hard (ages 6–7): last letters.", "Expert (age 8): fill in the missing vowel.",
            "Master (age 9): spell tricky everyday words.", "Extreme (age 10): spell hard words correctly.")) }
        LEARN_WORDS -> m { flashcards("Read the word or sentence and pick the answer.", listOf(
            "Easy (age 4): read three-letter words.", "Medium (age 5): read short picture words.",
            "Hard (ages 6–7): pick the word that fits the sentence.", "Expert (age 8): opposites.",
            "Master (age 9): past tense, what happened yesterday.", "Extreme (age 10): what big words mean.")) }
        LEARN_READING -> m { flashcards("Read the short story on the card, then answer the question about it.", listOf(
            "Easy (age 4): read a short sentence.", "Medium (age 5): a sentence and a question.",
            "Hard (ages 6–7): two sentences: who, what, where.", "Expert (age 8): a short story: when, who, what.",
            "Master (age 9): read between the lines: why and how.", "Extreme (age 10): order of events and the main idea.")) }
        LEARN_CLOCK -> m { flashcards("Read the clock face: the short hand points to the hour, the long hand to the minutes.", listOf(
            "Easy (age 4): o'clock.", "Medium (age 5): o'clock and half past.",
            "Hard (ages 6–7): quarter past and quarter to.", "Expert (age 8): every five minutes.",
            "Master (age 9): any minute.", "Extreme (age 10): how much later, time that passes.")) }
        CAGE_MATH -> m {
            sec("The goal") { p("Fill the grid so that every row and every column holds each number once (a 4×4 grid uses 1–4, a 6×6 grid 1–6, and so on), and every cage's numbers make its target.") }
            sec("Cages") {
                b("Thick outlines group squares into cages. The small label is the target and the operation.",
                    "+ : the cage's numbers add up to the target.", "× : they multiply to the target.",
                    "− : a two-square cage; the larger minus the smaller makes the target.", "÷ : a two-square cage; the larger divided by the smaller makes the target.",
                    "One-square cages are filled in for you. Numbers may repeat inside a cage as long as they aren't in the same row or column.")
            }
            sec("Notes") { p("Turn on Notes to pencil in the numbers a square could be. Placing a number clears it from notes in the same row and column.") }
            hearts()
            sec("Tips") {
                b("Start with small cages and extreme targets: a 2-square +3 cage must be 1 and 2.",
                    "Big multiplication targets have few factorisations; list them.",
                    "Each row adds to the same total (1+2+…+n): use it to find the last square of a row.")
            }
            saving()
        }
        KAKURO -> m {
            sec("The goal") { p("Fill every white square with a number from 1 to 9 so that each run of white squares adds up to its clue, with no number repeated within a run.") }
            sec("Reading the clues") {
                b("A number in the top-right of a dark square is the total of the white run to its right.",
                    "A number in the bottom-left of a dark square is the total of the white run below it.",
                    "Every white square belongs to one run across and one run down.")
            }
            sec("Notes") { p("Turn on Notes to pencil in candidates. Placing a number clears it from notes in the same runs.") }
            hearts()
            sec("Tips") {
                b("Some sums have only one set of numbers: 3 in two squares is 1+2; 4 in two is 1+3; 16 in two is 7+9; 17 in two is 8+9.",
                    "6 in three squares is always 1+2+3; 24 in three is always 7+8+9.",
                    "Where a run across and a run down meet, the square must suit both.")
            }
            saving()
        }
        PICTURE_LOGIC -> m {
            sec("The goal") { p("Shade the squares that make up a hidden picture, using the number clues beside every row and above every column.") }
            sec("Reading the clues") {
                p("Each clue lists the runs of filled squares in that line, in order. \"3 1\" means a run of three filled squares, at least one empty square, then a single filled square.")
            }
            sec("Playing") {
                b("Tap a square to fill it, or drag along a row or column to fill several.",
                    "Switch to ✕ to mark squares you know are empty.",
                    "When a line is complete, its leftover squares are marked empty for you.")
            }
            hearts("Filling a square that should be empty, or marking one that should be filled,")
            sec("Tips") {
                b("Overlap: in a 10-wide row, a run of 7 must cover the middle 4 squares however it's placed.",
                    "A clue of 0 or an empty clue means the whole line is empty.",
                    "Work back and forth between rows and columns: each fill gives the crossing line new information.")
            }
            saving()
        }
        HASHI -> m {
            sec("The goal") { p("Connect every island with bridges so that each island has exactly the number of bridges shown on it, and all the islands form one connected network.") }
            sec("Bridges") {
                b("Drag from one island to another, or tap one and then the other, to build a bridge.",
                    "Bridges run only straight across or straight down, and can't cross other bridges or islands.",
                    "Connect the same pair again for a double bridge; two is the most between any pair.")
            }
            hearts("A bridge that isn't part of the solution")
            sec("Tips") {
                b("An island showing 8 has four neighbours and needs double bridges to all of them.",
                    "A 1 next to another 1 can't connect to it if those two would be cut off from everything else.",
                    "Look for islands whose number equals twice their number of neighbours.")
            }
            saving()
        }
        GRID_RECALL -> m {
            sec("The goal") { p("Numbers appear on a grid for a few seconds. Remember each number and where it was, then put them all back.") }
            sec("Playing") {
                b("When the numbers hide, tap a square where a number was, then tap that number below.",
                    "From Expert up, the numbers flash one at a time: put them back in the same order.",
                    "Every couple of rounds adds another number. Clear every round to win.")
            }
            hearts("A wrong square or number")
            sec("Memory tips") {
                b("Group numbers into shapes: a line of three, a corner pair.",
                    "Say them in a rhythm: \"top row: 4, 7; middle: 2\".",
                    "Link numbers to positions you know, like a phone keypad.")
            }
            saving()
        }
        CODE_BREAKER -> m {
            sec("The goal") { p("A secret code of coloured, numbered symbols is hidden. Work it out within the allowed number of guesses.") }
            sec("Making a guess") { b("Tap colours to fill the slots of your guess.", "Tap a filled slot to clear it.", "Tap Guess when the row is full.") }
            sec("Reading the clues") {
                b("● a solid dot: one symbol is the right colour in the right place.",
                    "○ a hollow dot: one symbol is in the code, but in a different place.",
                    "The dots don't say which symbols they mean: that's what you work out.")
            }
            sec("Levels") {
                p("Easier codes have no repeated symbols. From Hard, symbols can repeat. On Master and Extreme every guess must fit all the clues you've seen so far; a guess that couldn't be the code is turned away.")
            }
            sec("Tips") {
                b("Open with guesses that test many different colours.",
                    "Change one thing at a time when you need to pin down a position.",
                    "Fewer guesses score more.")
            }
            saving()
        }
        LUMEN -> m {
            sec("The goal") { p("Turn every light off in as few taps as you can.") }
            sec("Playing") {
                b("Tapping a tile flips it and the tiles directly above, below, left and right: lit tiles go dark, dark tiles light up.",
                    "On Master, a tap flips the tile and its four diagonal neighbours instead.",
                    "On Extreme, a tap flips the tile and all eight around it.",
                    "Undo a tap or reset the board whenever you like.")
            }
            sec("Par") { p("Par is the fewest taps that can clear the board. Match it for full points.") }
            sec("Tips") {
                b("Tapping the same tile twice undoes it, so each tile needs at most one tap.",
                    "The order of taps doesn't matter, only which tiles are tapped.",
                    "Chase the lights down: clear the top row by tapping the squares below each lit one, then the next row, and so on. What's left on the bottom row tells you which top-row taps to make first.")
            }
            saving()
        }
        SLIDING_TILES -> m {
            sec("The goal") { p("Put the tiles in order 1, 2, 3… left to right, top to bottom, with the empty space in the bottom-right corner.") }
            sec("Playing") {
                b("Tap or swipe a tile in the same row or column as the empty space and it slides in, pushing any tiles between.",
                    "Tiles elsewhere can't move: they shake.",
                    "Tiles already in their final spot turn pale blue.")
            }
            sec("Moves and par") { p("Every tile that slides counts as a move. On 3×3 boards par is the fewest moves possible.") }
            sec("Method") {
                b("Solve the top row first, then the left column, so the puzzle shrinks to a smaller one.",
                    "Place the last two tiles of a row together: put them one below the other, then rotate them in.",
                    "Finish the final 2×3 or 2×2 by cycling the tiles around the gap.")
            }
            saving()
        }
        CROSSWORD -> m {
            sec("The goal") { p("Solve every clue to fill the grid.") }
            sec("Playing") {
                b("Tap a square to select its word; the clue appears above the keyboard.",
                    "Tap the same square again to switch between across and down.",
                    "Type letters: the cursor moves along the word and finished words light up.")
            }
            hearts("A wrong letter")
            sec("Reveal") { p("Stuck? Reveal fills one square for a few points but no heart.") }
            sec("Tips") { b("Fill the short words first: they give letters to the long ones.", "Plurals often end in S; past tenses in ED.") }
            saving()
        }
        WORD_SEARCH -> m {
            sec("The goal") { p("Find every word in the list hidden in the letter grid.") }
            sec("Playing") { b("Drag from a word's first letter to its last to circle it.", "Found words are crossed off the list.", "Circling letters that aren't a word just shakes: there's no penalty.") }
            sec("Directions") { p("Easy uses only across and down. Medium adds one diagonal, Hard all forward diagonals, and from Expert up words can also run backwards.") }
            sec("Tips") { b("Scan for a rare letter in the word (Q, Z, X, J) rather than its first letter.", "Look for double letters, which stand out.") }
            saving()
        }
        HAIKU -> m {
            sec("The goal") { p("Write haiku: three-line poems of 5, 7 and 5 syllables, using the word tiles in the tray.") }
            sec("Playing") {
                b("Tap word tiles to add them to the highlighted line. The meter counts syllables for you.",
                    "Tap a placed word to take it back.",
                    "When all three lines are full, tap Finish poem. Your poems are kept on the game's menu.")
            }
            sec("The rules by level") {
                b("Easy: any words, just get the syllables right.",
                    "Medium: use at least one season word (tinted tiles).",
                    "Hard: a season word, and each word only once.",
                    "Expert to Extreme: fewer tiles, and starred must-use words that every poem has to include.")
                p("A tile that would make a line too long shakes and isn't placed.")
            }
            sec("Writing tips") { b("Put an image in each line: something seen, heard or felt.", "Haiku often turn on a contrast between the first lines and the last.") }
        }
        ALLITERATION -> m {
            sec("The goal") { p("In each group of words, all but one start with the same sound. Tap the one that doesn't.") }
            sec("Sound, not spelling") {
                p("It's the sound that counts. \"Photo\" alliterates with \"fern\", but \"city\" doesn't with \"cat\". From Expert up, words share a letter but not a sound to trip you up.")
            }
            hearts("A wrong pick")
            sec("Winning") { p("Clear every round. A wrong pick shows the right answer so you learn it.") }
            saving()
        }
        SYNONYMS -> m {
            sec("The goal") { p("Each round shows a word. Read the instruction, then tap the right choice.") }
            sec("Two kinds of round") { b("Same meaning as: tap the word that means about the same.", "Opposite of: tap the word that means the reverse.") }
            hearts("A wrong answer")
            sec("Winning") { p("Get through every round. Wrong answers show the right word so you learn it. Higher levels use rarer words and more choices.") }
            saving()
        }
        RHYME_TIME -> m {
            sec("The goal") { p("Tap the word that truly rhymes with the one shown: its ending must sound the same.") }
            sec("Sound, not spelling") { p("Look-alike endings are traps. \"Bear\" rhymes with \"hair\", not with \"near\". Say the words in your head and trust your ear.") }
            hearts("A wrong answer")
            sec("Winning") { p("Finish every round. Higher levels add more choices, look-alike traps and rarer words.") }
            saving()
        }
        WORD_LADDER -> m {
            sec("The goal") { p("Turn the top word into the goal word by changing one letter at a time. Every step must be a real word.") }
            sec("Playing") {
                b("Tap a letter in your current word, then pick a new letter on the keyboard.",
                    "Letters that already match the goal turn blue.",
                    "Undo steps back a rung for free.")
            }
            hearts("A made-up word")
            sec("Par") { p("Par is the shortest possible ladder. Reach the goal in as few steps as you can for the most points.") }
            sec("Tips") { b("Vowels are the most flexible letters to change.", "Sometimes you must move away from the goal to get around a dead end.") }
            saving()
        }
        WORD_WHEEL -> m {
            sec("The goal") { p("Make words from the seven letters on the wheel. Find the target share of all the possible words to win.") }
            sec("Rules") {
                b("Words must be four or more letters long.", "Every word must use the centre letter.",
                    "Letters may be used more than once.", "Tap letters to spell, then Enter.")
            }
            sec("Pangrams") { p("A word that uses all seven letters is a pangram, worth a big bonus. Every wheel has at least one.") }
            sec("No penalties") { p("Words that are too short, miss the centre letter, or aren't in the list just shake. Experiment freely.") }
            sec("Tips") { b("Try common endings: -ING, -ED, -ER, -LY.", "Add S to words you've already found.") }
            saving()
        }
        SOLITAIRE -> m {
            sec("The goal") { p("Move all 52 cards onto the four foundation piles at the top, one suit each, from Ace up to King.") }
            sec("The layout") {
                b("Seven columns (the tableau): each has one face-up card on top of face-down cards.",
                    "The deck (stock) at the top-left, and the waste pile beside it.",
                    "Four foundation piles at the top-right.")
            }
            sec("Moving cards") {
                b("Tap a card and it jumps to the best place it can go.",
                    "In the columns, build downward in alternating colours: a red 6 on a black 7.",
                    "Move a face-up run together as one.",
                    "When a face-down card is uncovered it turns over.",
                    "Only a King (or a run starting with one) can fill an empty column.")
            }
            sec("The deck") { p("Tap the deck to turn cards over (one or three at a time by level). When it's empty, tap again to turn the waste back over; higher levels limit how many passes you get.") }
            sec("Undo and giving up") { p("Undo steps back a move (not on Extreme). Stuck for good? The flag ends the game.") }
            sec("Tips") { b("Uncover face-down cards as early as you can.", "Don't rush to empty a column unless a King is ready to fill it.", "Keep low cards available for the foundations.") }
            saving()
        }
        PYRAMID -> m {
            sec("The goal") { p("Clear the pyramid of cards on each floor and descend through the dungeon. Clear every floor to win.") }
            sec("Pairs of 13") {
                b("Clear two uncovered cards whose values add up to 13: tap one, then the other.",
                    "Ace = 1, number cards their value, Jack = 11, Queen = 12.",
                    "Kings are worth 13 and clear on their own.",
                    "A card is uncovered when no card overlaps it from the row below.")
            }
            sec("The deck and torches") {
                p("Tap the deck to turn a card. The top card of the turned pile can pair with a pyramid card or with the next deck card.")
                p("When the deck runs out, a torch lets you search it again. Each floor gives a few torches by level.")
            }
            sec("Tips") { b("Clear cards that are covering many others first.", "Don't spend a pyramid card on the deck if another deck card could take it later.") }
            saving()
        }
        BLACKJACK -> m {
            sec("The goal") { p("Play the set number of hands against the dealer and finish with at least the goal in chips.") }
            sec("Card values") { b("Number cards count their value.", "Jack, Queen and King count 10.", "An Ace counts 1 or 11, whichever is better. A hand with an Ace counted as 11 is \"soft\".") }
            sec("A hand") {
                b("Choose your bet and deal. You and the dealer get two cards; one of the dealer's stays hidden.",
                    "Hit to take another card; Stand to stop. Go over 21 and you bust, losing the bet.",
                    "The dealer then reveals and draws until reaching 17 or more (on Hard and up the dealer also hits a soft 17).",
                    "Closer to 21 than the dealer wins; a tie (push) returns your bet.")
            }
            sec("Special moves") {
                b("Blackjack: an Ace and a ten-value card as your first two cards pays 3:2 (only 6:5 on Master and Extreme).",
                    "Double: double your bet and take exactly one more card.",
                    "Split: a pair can be split into two hands, each with its own bet.")
            }
            sec("Strategy tips") {
                b("Stand on 17 or more; stand on 13–16 when the dealer shows 2–6.",
                    "Hit 12–16 when the dealer shows 7 or higher.",
                    "Double 11 against anything but an Ace; double 10 against 2–9.",
                    "Always split Aces and 8s; never split 10s or 5s.")
                p("On the easier levels, strategy tips appear as you play.")
            }
            saving()
        }
        GO_FISH -> m {
            sec("The goal") { p("Collect books: all four cards of the same rank. When every book has been made, the player with the most wins.") }
            sec("Your turn") {
                b("Tap a card in your hand to choose its rank (you can only ask for ranks you hold), then tap a player to ask.",
                    "If they have any cards of that rank, they hand them all over and you go again.",
                    "If not: Go fish! Draw from the pond. If you draw the rank you asked for you go again; otherwise the turn passes.",
                    "A complete set of four is laid down as a book at once.")
            }
            sec("Memory") { p("Opponents remember what's been asked for, more reliably on harder levels. You should too: a player who asked for 7s still has a 7.") }
            saving()
        }
        CRAZY_EIGHTS -> m {
            sec("The goal") { p("Be the first to play every card in your hand.") }
            sec("Your turn") {
                b("Play one card that matches the top card of the discard pile by suit or by rank.",
                    "An 8 is wild: play it on anything and call the suit everyone must follow next.",
                    "Can't play, or would rather not? Tap the deck to draw a card.")
            }
            sec("Tips") { b("Save your eights for when you're stuck.", "Change the suit to one your opponents seem to lack.", "Get rid of high cards early.") }
            saving()
        }
        SCOUNDREL -> m {
            sec("The goal") { p("Fight through a dungeon made from a deck of cards and reach the bottom with health left.") }
            sec("The cards") {
                b("♠ Spades and ♣ clubs are monsters, as strong as their value (J 11, Q 12, K 13, A 14).",
                    "♦ Diamonds are weapons, as strong as their value.",
                    "♥ Hearts are healing potions.")
            }
            sec("Rooms") {
                p("Each room shows four cards. Deal with any three of them; the fourth carries into the next room.")
                p("You may avoid a whole room (its cards go to the bottom of the dungeon), but never two rooms in a row, and higher levels limit how many rooms you can avoid at all.")
            }
            sec("Fighting") {
                b("Pick up a weapon to equip it (your old one is thrown away).",
                    "Fighting with a weapon: the monster hits you for its value minus the weapon's.",
                    "Afterwards that weapon can only fight monsters weaker than the last one it beat.",
                    "Or fight barehanded and take the monster's full value as damage.")
            }
            sec("Healing") { p("Drink a potion to heal by its value, up to your starting health. Only one potion per room heals; a second is wasted.") }
            sec("Tips") { b("Use a strong weapon on a mid-sized monster first, then step down to smaller ones.", "Keep potions for when you'll need them.") }
            saving()
        }
        CUTTLE -> m {
            sec("The goal") { p("Be the first to have point cards worth 21 or more in front of you. Each King you hold lowers your goal.") }
            sec("Your turn") { p("Draw a card, or play one card from your hand in one of these ways. Tap a card to see what it can do.") }
            sec("Number cards (Ace to 10)") {
                b("For points: lay it in front of you for its value (Ace = 1).",
                    "To scuttle: play it on an opponent's point card of lower value (or equal value and higher suit) to destroy both.",
                    "As a one-off: scrap it for its effect, then it goes to the scrap pile.")
            }
            sec("One-off effects") {
                b("Ace: scrap every point card on the table.", "2: scrap a royal or glasses 8, or cancel another one-off.",
                    "3: take any card from the scrap pile.", "4: your opponent discards two cards.", "5: draw two cards.",
                    "6: scrap every royal and glasses 8.", "7: play the top card of the deck at once.", "9: return a royal or glasses 8 to its owner's hand.")
                p("When a one-off is played, the other player may answer with a 2 to cancel it, and that 2 can be cancelled by another 2.")
            }
            sec("Royals and eights") {
                b("Jack: steal one of the opponent's point cards.", "Queen: protects your other cards from being targeted.",
                    "King: lowers your goal (to 14, 10, 5, then 0).", "8 as glasses: lay it sideways to play with the opponent's hand face up.")
            }
            saving()
        }
        REGICIDE -> m {
            sec("The goal") { p("Defeat all twelve corrupted royals: the four Jacks, then the four Queens, then the four Kings. It's a solo game.") }
            sec("The royals") { b("Jacks: 20 health, 10 attack.", "Queens: 30 health, 15 attack.", "Kings: 40 health, 20 attack.") }
            sec("A turn") {
                b("Play: select a card (or a combo) and tap Play to attack for its value.",
                    "Combos: two to four cards of the same number totalling 10 or less, or an Ace with one other card.",
                    "Suit powers trigger (below).", "If the royal survives, it strikes back: discard cards worth at least its attack value, or you lose.",
                    "You may hold back (yield) instead of playing, and go straight to the counterattack.")
            }
            sec("Suit powers") {
                b("♥ Hearts: shuffle that many cards from the discard pile back into the deck.",
                    "♦ Diamonds: draw that many cards.",
                    "♣ Clubs: deal double damage.",
                    "♠ Spades: shield, lowering the royal's attack for the rest of the fight.")
                p("A royal ignores the power of its own suit (unless a Jester was played this fight).")
            }
            sec("Defeating a royal") {
                p("Bring it to exactly 0 health and it joins the top of your deck. Overkill it and it goes to the discard pile.")
                p("Jesters refresh your whole hand. How many you get, and how many cards you hold, depends on the level.")
            }
            saving()
        }
        DUNGEON_SOLITAIRE -> m {
            sec("The goal") { p("Delve into the Tomb of Four Kings, gather treasure, and escape alive with enough Kings for your level.") }
            sec("Encounters") {
                b("Each turn, flip cards until you meet an encounter: ♠ monster, ♦ trap or ♣ door.",
                    "The cards flipped after it are your actions: one at least as high as the encounter beats it.",
                    "Lower cards hurt you (or, for doors, cost time).")
            }
            sec("Treasure and helpers") {
                b("Beat an encounter to keep its treasure: ♦ cards, and the Kings (the tomb's hoards).",
                    "Aces are torches. When the fourth Ace burns out you're lost in the dark.",
                    "Jacks are skills; Queens are divine favours that help you through.")
            }
            sec("Escaping") { p("Between turns, choose to delve deeper or turn back. Escaping takes one turn fewer than you spent delving. Die, or lose the light, and all treasure is lost.") }
            saving()
        }
        JAILBREAK -> m {
            sec("The goal") { p("Break down any one wall of cards to set the Joker free.") }
            sec("The layout") { p("The Joker sits in the middle. Four walls of six cards (north, east, south and west) surround it; the corners hold two cards each.") }
            sec("Black beats red") {
                b("Match one black card against red cards that add up to the same value (Ace 1 … King 13).",
                    "Example: a black 9 against a red 4 and a red 5.",
                    "Matched cards leave and uncover the cards beneath.")
            }
            sec("Spare cards") { p("Stuck? Put a spare card on the Joker. It can hold only a few (by level), and every card on it must be cleared before the Joker can escape.") }
            sec("Winning") { p("Empty any one wall, with the Joker clear, and the Joker goes free.") }
            saving()
        }
        CHESS -> m {
            sec("The goal") { p("Checkmate the opposing king: attack it so that it has no way out. White moves first; against the computer you play White.") }
            sec("How the pieces move") {
                b("King: one square in any direction.",
                    "Queen: any distance along a row, column or diagonal.",
                    "Rook: any distance along a row or column.",
                    "Bishop: any distance along a diagonal.",
                    "Knight: an L shape, two squares one way and one to the side. It jumps over pieces.",
                    "Pawn: one square forward, or two from its starting square. It captures one square diagonally forward.")
                p("Pieces (except the knight) can't jump over others. Capture by moving onto an enemy piece.")
            }
            sec("Special moves") {
                b("Castling: move the king two squares toward a rook and the rook hops over it. Allowed only if neither has moved, the squares between are empty, and the king isn't in check, doesn't pass through check and doesn't land in check.",
                    "En passant: if a pawn moves two squares and lands beside your pawn, your pawn may capture it as if it had moved one, on the very next move only.",
                    "Promotion: a pawn reaching the far row becomes a queen, rook, bishop or knight. You'll be asked which.")
            }
            sec("Check, checkmate and draws") {
                b("Check: the king is attacked. You must get out of check at once: move the king, block, or capture the attacker.",
                    "Checkmate: in check with no way out. The game is over.",
                    "Stalemate: the side to move has no legal move but isn't in check. It's a draw.",
                    "Also drawn: the same position three times, fifty moves each with no capture or pawn move, or too few pieces left to checkmate.")
            }
            sec("Playing") { p("Tap a piece to see the squares it can reach, then tap a square. The last move is highlighted. Captured pieces are shown above and below the board.") }
            sec("Tips") {
                b("Control the centre with pawns and pieces early.", "Develop knights and bishops before moving the queen out.",
                    "Castle early to keep the king safe.", "Before each move, check what your opponent's last move threatens.")
            }
            saving()
        }
        CHECKERS -> m {
            sec("The goal") { p("Capture all of your opponent's pieces, or leave them with no legal move.") }
            sec("Moving") {
                b("Pieces sit on the dark squares. Against the computer you are Red, moving up the board.",
                    "A man moves one square diagonally forward.",
                    "A king moves one square diagonally forward or backward.")
            }
            sec("Jumping") {
                b("Capture by jumping diagonally over an enemy piece onto the empty square beyond it.",
                    "Jumping is compulsory: if you can jump, you must.",
                    "After a jump, keep jumping with the same piece while more jumps are open.",
                    "Men jump forward only; kings jump both ways.")
            }
            sec("Kings") { p("A man that reaches the far row is crowned king. If it gets there by jumping, its move ends there.") }
            sec("Tips") { b("Keep your back row filled as long as you can: it stops enemy kings.", "Trade pieces when you're ahead.", "Use forced jumps to drag the opponent into a trap.") }
            saving()
        }
        GO -> m {
            sec("The goal") { p("Surround more of the board than your opponent. Black plays first; against the computer you are Black.") }
            sec("Stones and liberties") {
                b("Take turns placing a stone on an empty crossing. Tap once to preview, tap again to place.",
                    "Stones never move once placed.",
                    "A stone's liberties are the empty points next to it (not diagonally). Stones of one colour that touch form a group and share their liberties.",
                    "Fill a group's last liberty and it's captured: removed from the board.")
            }
            sec("Rules that stop endless play") {
                b("Suicide: you can't place a stone where it would have no liberties, unless it captures something.",
                    "Ko: you can't immediately recapture a single stone if that would repeat the previous position. Play elsewhere first.")
            }
            sec("Ending and scoring") {
                b("When there's nothing useful left to do, tap Pass. Two passes in a row end the game.",
                    "Dead stones (ones that can't escape capture) are removed.",
                    "Each side scores its stones on the board plus the empty points it surrounds (area scoring).",
                    "White gets 7.5 points of komi to make up for moving second.")
            }
            sec("Tips") { b("Corners are easiest to claim, then sides, then the centre.", "A group needs two separate eyes (enclosed empty points) to live forever.", "Don't chase small captures while your opponent takes big areas.") }
            saving()
        }
        SHOGI -> m {
            sec("The goal") { p("Checkmate the opposing king. Captured pieces change sides and can be dropped back onto the board as your own.") }
            sec("How the pieces move") {
                b("King: one square in any direction.",
                    "Rook: any distance along a row or column.",
                    "Bishop: any distance diagonally.",
                    "Gold general: one square in any direction except diagonally backward.",
                    "Silver general: one square diagonally, or straight forward.",
                    "Knight: two forward and one to the side (only forward), jumping over pieces.",
                    "Lance: any distance straight forward.",
                    "Pawn: one square straight forward, and it captures the same way.")
            }
            sec("Promotion") {
                b("The far three rows are the promotion zone. A piece that moves into, within or out of it may promote (you'll be asked).",
                    "Pawn, lance, knight and silver promote to move like a gold general.",
                    "Promoted rook (dragon) also moves one square diagonally; promoted bishop (horse) also moves one square orthogonally.",
                    "Gold generals and kings don't promote. A piece that could never move again (a pawn on the last row, say) must promote.")
            }
            sec("Drops") {
                b("Captured pieces go to your hand below the board, unpromoted.",
                    "Instead of moving, you may drop one onto any empty square.",
                    "No two unpromoted pawns of yours in the same column.",
                    "A piece can't be dropped where it could never move (a pawn or lance on the last row, a knight on the last two).")
            }
            sec("Tips") { b("Keep your king in a castle of golds and silvers.", "Pieces in hand are powerful: don't rush to trade.", "Pawn drops are great for blocking and attacking.") }
            saving()
        }
        XIANGQI -> m {
            sec("The goal") { p("Checkmate the opposing general. Red moves first; against the computer you play Red at the bottom.") }
            sec("The board") { p("Pieces stand on the points, not in the squares. A river runs across the middle. Each side has a palace: the 3×3 area marked with an X.") }
            sec("How the pieces move") {
                b("General: one point along a line, never leaving the palace.",
                    "Advisor: one point diagonally, never leaving the palace.",
                    "Elephant: exactly two points diagonally, can't jump the middle point and can't cross the river.",
                    "Horse: one point straight then one diagonally outward; blocked if a piece stands on the first point.",
                    "Chariot: any distance along a line, like a rook.",
                    "Cannon: moves like a chariot, but captures only by jumping exactly one piece (of either side) on the way.",
                    "Soldier: one point forward. Once across the river it may also step sideways. It never moves back.")
            }
            sec("Special rules") {
                b("Flying general: the two generals may never face each other on an open file with nothing between them.",
                    "A player with no legal move loses: there's no stalemate draw.")
            }
            sec("Tips") { b("Get the chariots into open files early.", "A cannon behind a screen piece is a constant threat.", "Keep advisors and elephants home to guard the general.") }
            saving()
        }
        CYVASSE -> m {
            sec("The goal") {
                p("Take the rival's king. Cyvasse is the battle game played across Westeros and Essos in A Song of Ice and Fire. George R. R. Martin never wrote down its rules, so PureGames plays the best-known fan rules: Cyvasse, rules adapted by A. Marston Croft (Ill Gotten Games), the version on Tabletopia and BoardGameGeek.")
            }
            sec("The armies") {
                p("Alabaster (light) and Onyx (dark) each have 3 rabble, 3 spearmen, 3 crossbowmen, 3 light horses, 2 heavy horses, 2 elephants, a catapult, a trebuchet, a dragon and a king, plus a keep and 3 mountains. Against the computer you play Alabaster.")
            }
            sec("The board") {
                p("A 10×10 field split into two halves of five rows. Six water tiles and six forest tiles are always in the same places, laid out alike for both sides. (The published rules don't fix a board size or where the water and forests lie, so this board is PureGames' own.)")
            }
            sec("Setting up behind the screen") {
                b("A screen hides each half while it is set up: Alabaster first, then Onyx.",
                    "Set your 3 mountains anywhere on open grass in your half.",
                    "Set your keep on open grass in your half, but not in the row next to the screen.",
                    "The tiles around the keep (not off the board or under a mountain) are your home tiles. Stand any of your pieces on them, one per tile. The rest stay hidden in reserve.",
                    "Random lays out a sensible setup for you; Clear starts again; Ready lowers your side of the screen.",
                    "When both sides are ready the screen comes down and a coin decides who moves first.")
            }
            sec("Taking turns") {
                b("Each turn, move one piece, or bring one piece out of reserve onto an empty home tile. You can only bring pieces out while your keep stands, and never onto an enemy piece.",
                    "Land on an enemy piece to take it.",
                    "Pieces can't move through other pieces, mountains or a keep.",
                    "Any piece but the dragon that moves into water stops there.")
            }
            sec("The pieces") {
                b("Rabble: one tile across or along. After moving a rabble you may move a second, different rabble (or tap End turn).",
                    "Spearman: one tile across or along, or swap places with a friendly piece beside it.",
                    "Crossbowman: one tile diagonally, or leap straight into any empty forest tile.",
                    "Light Horse: any distance across or along, and it may ride off one edge of the board and on again from the opposite edge.",
                    "Heavy Horse: any distance across or along, riding past its own pieces.",
                    "Elephant: any distance across or along. It can't be taken unless another enemy piece is standing beside it.",
                    "Catapult: any distance diagonally. It may take two enemy pieces in one move, the first and then the next one in line beyond it.",
                    "Trebuchet: any distance diagonally. When it captures, it shoots over any pieces in the way (not over mountains, keeps or water).",
                    "Dragon: any distance in any of the eight directions, flying over mountains and water.",
                    "King: one tile in any direction.")
            }
            sec("The keep") {
                p("Stand your pieces on two of the rival's home tiles at once and its keep falls. The keep and every piece still in its reserve are captured. If the king was waiting in that reserve, the game is won.")
            }
            sec("Winning") {
                b("Take the rival king on the board, or in the reserve of a fallen keep.",
                    "A side with no move at all loses.",
                    "After ${com.puresudoku.core.board.Cyvasse.QUIET_LIMIT} moves in a row without a capture or a piece brought out, the game is drawn.")
            }
            sec("Playing on the screen") {
                b("Tap any piece to read what it does. Tap one of yours to see where it can go: dots are moves, red rings are captures (the catapult's first victim is ringed too), blue rings are spearman swaps.",
                    "Your reserve is secret: tap Reserve to open it, pick a piece, then tap a gold home tile.",
                    "On one device, each player sets up with the screen covered between them, and your reserve only opens on your own turn.")
            }
            sec("Tips") {
                b("Hide your king. Any open line to it, even one that wraps round the edge for a light horse, can be a capture on the first move.",
                    "Mountains beside your keep take away home tiles the enemy could stand on.",
                    "A king left in reserve is safe until your keep falls, and then it is lost with it.",
                    "Keep a piece or two near home: two raiders on your home tiles take the keep and everything in it.",
                    "Pair your attackers: elephants only fall to two pieces working together.")
            }
            saving()
        }
        BACKGAMMON -> m {
            sec("The goal") { p("Move all fifteen of your checkers around the board into your home and bear them off before your opponent does.") }
            sec("Moving") {
                b("Tap Roll. Each die is one move: move one checker that many points toward your home (bottom-right for White).",
                    "You may use both dice on one checker, or one die each on two.",
                    "Doubles play four times.",
                    "You can't land on a point holding two or more enemy checkers.",
                    "You must use both dice if you can. Only the moves that are allowed are highlighted.")
            }
            sec("Hitting and the bar") {
                b("A single checker on a point is a blot. Land on an enemy blot to hit it: it goes to the bar.",
                    "A player with a checker on the bar must bring it back in, into the opponent's home board, before moving anything else.")
            }
            sec("Bearing off") { p("Once all fifteen of your checkers are in your home board, you can bear them off: a die removes a checker from the matching point, or from the highest point if the die is bigger than any point you occupy.") }
            sec("Winning") { b("First to bear off all fifteen wins.", "A gammon (the loser bore off none) counts double; a backgammon (the loser also still has a checker on the bar or in the winner's home) counts triple.") }
            sec("Tips") { b("Make points (two or more checkers) in your home to block the opponent.", "Avoid leaving blots where the opponent can hit them easily.") }
            saving()
        }
        MANCALA -> m {
            sec("The goal") { p("Capture more seeds than your opponent. This is Kalah with four seeds in each pit.") }
            sec("The board") { p("Each side has six pits and a store. Your pits are the bottom row; your store is on the right.") }
            sec("Sowing") {
                b("Tap one of your pits: pick up all its seeds and drop them one by one into the following pits, anticlockwise.",
                    "You sow into your own store but skip your opponent's.",
                    "If the last seed lands in your store, you go again.",
                    "If the last seed lands in an empty pit on your side, you capture it plus all the seeds in the pit opposite.")
            }
            sec("The end") { p("When one side's pits are all empty, the game ends. Seeds left in pits go to the store on their side. Most seeds wins.") }
            sec("Tips") { b("Count seeds: a pit with as many seeds as it is pits away from your store gives a free turn.", "Keep the opponent's side short of seeds to set up captures.") }
            saving()
        }
        MAHJONG -> m {
            sec("The goal") { p("Clear every tile from the layout by removing matching pairs. This is Mahjong solitaire, a matching game.") }
            sec("Free tiles") {
                b("Only free tiles can be picked.",
                    "A tile is free when nothing lies on top of it and its left or right side is open.")
            }
            sec("Matching") {
                b("Tap two identical free tiles to remove them.",
                    "Any flower matches any flower; any season matches any season.")
            }
            sec("Hints and shuffles") { p("Hint lights up a pair; Shuffle re-deals the tiles that are left. Each level allows a different number.") }
            sec("Tips") { b("Free tiles that are blocking long rows first.", "When all four of a kind are free, remove them in pairs; with three free, choose carefully.", "Work on tall stacks early.") }
            saving()
        }
        GOMOKU -> m {
            sec("The goal") { p("Get five of your stones in an unbroken line across, down or diagonally. Black moves first; against the computer you are Black.") }
            sec("Playing") { b("Stones go on the line crossings.", "Tap once to preview, tap the same spot again to place.", "Stones never move.") }
            sec("Threats") {
                b("An open three (three in a row with both ends empty) threatens an open four next.",
                    "An open four (four in a row with both ends empty) can't be stopped.",
                    "Block your opponent's open threes before they become fours.")
            }
            sec("Tips") { b("Build where your lines cross, so one stone makes two threats.", "Stay near the centre early on.") }
            saving()
        }
        MORRIS -> m {
            sec("The goal") { p("Reduce your opponent to two men, or leave them with no legal move.") }
            sec("Placing") { p("Each player has nine men. Take turns placing them on empty points.") }
            sec("Moving") {
                b("Once all men are placed, move one man along a line to a free neighbouring point.",
                    "A player down to three men may fly: move a man to any free point.")
            }
            sec("Mills") {
                p("Three of your men in a row along a line form a mill. Each time you form one, remove one of your opponent's men.")
                p("Men inside a mill are safe, unless every enemy man is in a mill.")
                p("You can open a mill by moving a man out, then close it again later to remove another man.")
            }
            sec("Tips") { b("Keep your men mobile: blocked men can lose the game.", "A man that can swing between two mills removes a piece every turn.") }
            saving()
        }
        TIC_TAC_TOE -> m {
            sec("The goal") { p("Get three in a row (across, down or diagonal) to win a round. A match is up to five rounds; win more rounds than your opponent.") }
            sec("Playing") { b("Tap an empty square to place your mark. X goes first in the first round, and the first move alternates each round.", "A full board with no line is a drawn round.") }
            sec("Tips") {
                b("Start in a corner or the centre.", "Block any line where your opponent has two.",
                    "Make a fork: one move that creates two threats at once.")
                p("With perfect play every round is a draw, so on Extreme a draw is a fine result.")
            }
            saving()
        }
        CRATEBARGE -> m {
            sec("The goal") { p("Place your six ships on a 10 by 10 sea. Take turns firing; sink the whole rival fleet first.") }
            sec("Your fleet") { p("Crownwake Flagbarge (5), Ironbelly Cargo Ark (4), Thornplate Ferry (3), Moonhook Skiff (3), Pipknife Cutter (2), and Barrelbug Dinghy (2).") }
            sec("Playing") { p("Pick a ship, rotate it and tap your grid to place it, or use Random. Once both fleets are ready, tap the enemy grid to fire. Hits and misses are marked; a ship sinks when all its spaces are hit.") }
            sec("Voyages and shop") { p("Win contracts to earn Cratebarge Dice and unlock the next challenge. A loss resets voyage progression, but your Dice and purchased supplies remain. The Captain's shop sells a Bomb Barrel, Telescope, Patchwork Planks and Decoy Buoy. Contract-issued supplies last only for that battle.") }
            sec("Two players") { p("Use pass-and-play on one device or play online using a room code. Each player places a fleet and takes turns firing. Contracts, Dice and supplies are for solo voyages.") }
            saving()
        }
        SEA_BATTLE -> m {
            sec("The goal") { p("Sink every ship in the rival's fleet before they sink yours.") }
            sec("Hiding your fleet") {
                b("Pick a ship, then tap the square where it starts. Across / Down turns it before you place it.",
                    "Tap a placed ship to pick it back up. Random scatters your whole fleet; Clear starts again.",
                    "Ships can't overlap or hang off the edge. Press Ready when every ship is placed.")
            }
            sec("Firing") {
                b("Take turns firing at one square of the enemy's waters.",
                    "A white ring is a miss; fire is a hit. When every square of a ship is hit, it sinks and is shown.",
                    "Your own grid shows where the rival has fired at you.")
            }
            sec("The fleet") {
                b("Easy: an 8×8 sea with a Battleship (4), Cruiser (3), Submarine (3) and Destroyer (2).",
                    "Medium and up: a 10×10 sea with a Carrier (5), Battleship (4), Cruiser (3), Submarine (3) and Destroyer (2).")
            }
            sec("Two players") {
                p("On one device, each player hides their fleet in turn with the screen covered between. After each shot you see the result, then pass the phone. Online, each player sees only their own fleet.")
            }
            sec("Tips") {
                b("After a hit, try the squares around it; after two, follow the line.",
                    "While hunting, fire on every other square like a chessboard: no ship can slip between.",
                    "Don't line your ships up along the edges or pack them together: that's where good rivals look.")
            }
            saving()
        }
        DEED_DUNGEON -> m {
            sec("The goal") {
                p("Become King of Eiseria. Hold enough whole domains (four with two lords, three with more) and you are crowned at once. Vanquish every other lord and the crown is yours. Otherwise the old King dies at the end of the last round, and the lord with the most renown (gold, plus deeds and towers at their cost) takes the throne.")
            }
            sec("The lords") {
                p("Two to four lords play. Alone, you ride against one to three AI rivals. With friends on one device, each seat is a person or an AI lord. Online games are two people.")
            }
            sec("Your champion") {
                p("Choose the Dragon, the Knight, the Veilnight Thief or the Black Knight, in its natural colours or a banner colour. Each has a lasting power and one great deed a game:")
                b("Dragon: your Blade lands wound 1 more. Inferno: burn every tower on one rival land.",
                    "Knight: 12 health instead of 10, and +1 in trials by combat. Second Wind: heal to full and take up a Tower Shield.",
                    "Veilnight Thief: you pay a quarter less homage. The Heist: steal a rival's unfortified deed worth up to 240 gold.",
                    "Black Knight: your towers cost a quarter less. Reign of Terror: every rival lord takes 2 wounds.")
            }
            sec("The board") {
                p("Eiseria never changes. The Spire is top left and you ride clockwise. The Inquisitor stands top right, the Colosseum (with the Dungeon beneath it) bottom right, and the Royal Market bottom left. Eight coloured domains, four bridges, the Windmill and the Silver Mine lie between.")
            }
            sec("Your turn") {
                b("Roll two dice and ride. Doubles roll again; a third double in a row sends you to the Dungeon.",
                    "Passing the Spire pays you 200 gold, heals 1, and lets you turn your coins that turn. Landing right on it pays 100 more.",
                    "Stop on an unclaimed deed (its price is on its tag) to buy it, or send it to auction.",
                    "Before rolling or ending your turn you may open Lands to build, tear down, pawn, redeem and turn coins; offer trades; use your great deed; and use keepsakes.")
            }
            sec("Auctions") {
                p("Pass on a deed and every lord may bid for it, in turn, raising by 10, 50 or 100 gold, or dropping out. The last bidder standing wins it.")
            }
            sec("Trading") {
                p("On your turn, offer any lord deeds and gold for their deeds and gold. They accept or decline. Lands with towers can't be traded; tear the towers down first. Up to three offers a turn.")
            }
            sec("The two-sided coin") {
                p("Every deed carries a coin, tossed when you win it.")
                b("Crown (homage): a visitor pays homage in gold. They may refuse, and your towers strike them instead: 1 wound, plus 1 per tower.",
                    "Blade: a visitor simply takes wounds: 2, plus 1 for a whole domain, plus 1 per tower, plus 1 more for a castle. Bridges, the mill and the mine wound 1 for each one you hold.",
                    "You turn your coins only on a turn you pass the Spire (or with a Royal Banner). Choose Crowns to grow rich and Blades to bring your rivals down.")
            }
            sec("Health, wounds and the Dungeon") {
                b("Gold you owe but can't pay becomes wounds: 1 for every 50 gold short. Raise funds first by pawning or tearing down towers if you'd rather.",
                    "At 0 health you're felled. The lord who felled you seizes your best deed, towers and all (felled by the realm, your cheapest deed returns to it). You wake healed in the Dungeon beneath the Colosseum.",
                    "Felled with no deeds left, you're vanquished and out of the game.",
                    "In the Dungeon: roll doubles, pay the jailer 50, or use a Skeleton Key. Each night inside heals 1. After three tries you must pay.")
            }
            sec("Domains, towers and castles") {
                b("Lands come in eight coloured domains of two or three. Hold all of one to double its bare homage.",
                    "On a whole domain with nothing pawned you may raise towers, evenly across it: up to three per land, then a castle.",
                    "Pawn a deed to the moneylender for half its price (tear down the domain's towers first). Pawned land asks nothing. Redeem it for a little more.")
            }
            sec("Fate, loot, the Market and the Colosseum") {
                b("Fate cards bring fortune and folly, from bards to witch trials.",
                    "Loot chests hold gold, mimics, or keepsakes: Healing Potion, Skeleton Key, Tower Shield, Forged Writ (skip one homage or blade), Siege Engine (topple a rival tower) and Royal Banner (turn a coin).",
                    "The Royal Market sells one keepsake per visit.",
                    "Taxes, fines and bribes fill the Colosseum purse. Land on the Colosseum to win it, then you may challenge any lord to trial by combat: both roll two dice, the winner takes the loser's best deed and the loser takes 2 wounds.",
                    "The Inquisitor sends you straight down to the Dungeon.")
            }
            sec("Omens") {
                p("Every four rounds a new omen rules the realm: a Harvest Festival, a Blood Moon, Famine, a Royal Jubilee, a Truce of the Crown that stills every blade, Tax Season, a Builders' Guild, or a Plague Winter with no healing.")
            }
            sec("Tips") {
                b("Buy early, then finish domains: a whole domain doubles homage and opens towers.",
                    "Use auctions: a deed nobody else wants can go cheap.",
                    "Trade for the last land of a domain, but never hand a rival theirs cheaply.",
                    "Watch your rivals' health: when one is low, pass the Spire and turn your coins to Blades.",
                    "Save your great deed for the moment it swings the game.")
            }
            saving()
        }
        else -> m {
            sec("How to play") { p(kind.tagline) }
            saving()
        }
    }
}
