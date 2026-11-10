package com.puresudoku.ui.training

import com.puresudoku.core.GameKind

object CardLessons {
    val all: Map<GameKind, Course> = mapOf(
        GameKind.SOLITAIRE to Course(
            listOf(
                "Goal: move all 52 cards to the four foundations, building each suit up from Ace to King.",
                "In the seven tableau columns, build down in alternating colours (red 7 on black 8). Only Kings go into empty columns.",
                "Tap the stock to turn cards. Tap a card to send it to its best spot.",
            ),
            listOf(
                listOf("Always move Aces and Twos to the foundations right away.", "Turn over face-down cards whenever you can: they're the real goal.", "Prefer moves from the tableau over moves from the waste."),
                listOf("Free the column with the most hidden cards first.", "Don't empty a column unless a King is ready to fill it.", "Think before moving a card up to a foundation: it may be needed to hold a lower card."),
                listOf("With draw-three, the order of the stock repeats: remember what's buried.", "Choose which black 6 to move by which uncovers more.", "Keep both colours of a rank available when possible."),
                listOf("Count passes: on limited redeals, plan before cycling the stock.", "Build long sequences only when they unlock something.", "A King in an empty column should be the one that frees most cards."),
                listOf("Avoid moves that bury cards you'll need soon.", "Play the stock card only if it helps the tableau.", "Track the foundation gaps: which low cards are still missing?"),
                listOf("No undo on Extreme: pause and check every move.", "Look several moves ahead for chains of reveals.", "Sometimes the best move is to wait and cycle the stock."),
            ),
        ),
        GameKind.BLACKJACK to Course(
            listOf(
                "Beat the dealer by getting closer to 21 without going over. J/Q/K are worth 10; Aces are 1 or 11.",
                "Hit to take a card, Stand to stop. Double doubles your bet for one more card; Split a pair into two hands.",
                "The dealer must draw to 17. Finish the set number of hands with your goal in chips.",
            ),
            listOf(
                listOf("Stand on 17 or more.", "Always hit 11 or less: you can't bust.", "Watch the tip line: it shows the basic-strategy move."),
                listOf("Against a dealer 7 or higher, hit until 17.", "Against a dealer 2–6, stand on 12–16 and let the dealer bust.", "Double on 11 against most dealer cards."),
                listOf("Always split Aces and 8s; never split 10s or 5s.", "Soft hands (with an Ace as 11) can hit safely: hit soft 17 or less.", "Double soft 13–18 against a weak dealer card."),
                listOf("Bet small when the goal is near, bigger when you need to catch up.", "Don't take insurance-style gambles: play the odds.", "Stick to basic strategy; hunches cost chips."),
                listOf("6:5 blackjack pays less, so each hand matters: avoid big early bets.", "Doubles and splits are where you gain: use them correctly.", "Manage your bankroll across all hands."),
                listOf("With a high goal, plan your bet sizes from the start.", "Basic strategy plus smart betting is the whole game.", "Stay disciplined after a losing streak."),
            ),
        ),
        GameKind.GO_FISH to Course(
            listOf(
                "Ask a player for a rank you hold. If they have any, they hand them all over and you go again.",
                "If not, 'Go fish': draw from the pond. Draw the rank you asked for and you go again.",
                "Four of a rank is a book. Most books when the cards run out wins.",
            ),
            listOf(
                listOf("Ask for ranks you hold several of.", "Ask the player with the most cards: they're likelier to have it.", "Remember what others asked for: they hold that rank."),
                listOf("If someone asked for 7s, ask them for 7s back.", "Keep track of books already made: those ranks are gone.", "Ask for the same rank again if you fished a match."),
                listOf("Asking reveals your ranks: opponents remember too.", "Ask for a rank right after you draw it, before others learn.", "Count how many of a rank are out in the open."),
                listOf("Track who took cards from whom.", "Prefer ranks where you hold three: one ask completes a book.", "Mix your asks so you don't telegraph your hand."),
                listOf("Late game: the pond is small, so fishing is less likely to hit.", "Deduce hands from the ranks people ask for and never ask for.", "Use your memory more than luck."),
                listOf("Perfect-memory rivals: assume they know what you asked.", "Strike fast on ranks they've revealed.", "Keep a mental table of who holds what."),
            ),
        ),
        GameKind.CRAZY_EIGHTS to Course(
            listOf(
                "Play one card that matches the top card's suit or its rank.",
                "Eights are wild: play one on anything and call the suit everyone must follow.",
                "Can't play (or don't want to)? Tap the deck to draw. First to empty their hand wins.",
            ),
            listOf(
                listOf("Match the suit you have the most of, to keep options later.", "Save your eights: they're your escape when nothing else fits.", "Play high cards first: they're worth more against you if you lose."),
                listOf("Switch suits with a matching rank to escape a suit you lack.", "When you play an eight, call your strongest suit.", "Watch how many cards opponents hold."),
                listOf("Notice when an opponent draws: they lacked that suit.", "Change to a suit the next player lacks.", "Don't burn an eight when a normal card works."),
                listOf("When someone has one card, try to change the suit away from theirs.", "Hold a spare matching rank as a suit switch.", "Count eights played: four exist."),
                listOf("Plan your last two cards so they chain together.", "Track suits each opponent has drawn on.", "Sometimes drawing on purpose keeps a key card hidden."),
                listOf("Against sharp rivals, keep two suits alive in your hand.", "End with an eight for a guaranteed finish.", "Think one full turn around the table ahead."),
            ),
        ),
        GameKind.SCOUNDREL to Course(
            listOf(
                "Clubs and spades are monsters, diamonds are weapons, hearts are healing potions. Values: 2–10, J 11, Q 12, K 13, A 14.",
                "Each room shows four cards: deal with three, and the fourth carries into the next room.",
                "A weapon cuts a monster's damage by its value, but afterwards it can only fight monsters weaker than its last kill.",
            ),
            listOf(
                listOf("Pick up a good weapon early.", "Fight small monsters barehanded to save your weapon for big ones.", "Drink a potion when you've lost health; only one per room heals."),
                listOf("Fight monsters in descending order with your weapon to keep it useful.", "Avoid rooms full of big monsters if you can; you can't avoid twice in a row.", "Leave the worst card as the carry-over."),
                listOf("A weapon used on a small monster becomes nearly useless: think before using it.", "Swap to a new weapon when the old one is degraded.", "Track the deck: count big monsters left."),
                listOf("With few avoids, plan which rooms are worth skipping.", "Keep health above the next likely hit.", "Potions in a room with monsters: fight first, then heal."),
                listOf("Weapon management decides the game: save the big weapon for the Aces and Kings.", "Barehanded fights against 2–4 are cheap.", "The last rooms are tight: conserve potions for them."),
                listOf("No avoids and low health: every choice counts.", "Sequence each room: weapon, big monster, small monster, potion.", "Plan whole rooms, not single cards."),
            ),
        ),
        GameKind.CUTTLE to Course(
            listOf(
                "Race to 21 points. On your turn, draw or play one card.",
                "Number cards can be points, or scuttle (destroy) a smaller enemy point card, or a one-off effect. Royals stay on the table.",
                "Kings lower your goal, Queens protect your other cards, 8s let you see their hand, Jacks steal a point card.",
            ),
            listOf(
                listOf("Play big numbers for points early.", "Scuttle their biggest point card when you can.", "A King early makes your goal much closer."),
                listOf("Hold a 2 to counter their one-offs.", "Use a 5 to draw two cards when your hand is weak.", "A Queen protects your points from Jacks and scuttles."),
                listOf("An Ace wipes all point cards: use it when they're ahead.", "A 6 removes all royals: great against their Kings.", "Don't overextend into an Ace."),
                listOf("Count their possible counters before playing a one-off.", "Jacks swing games: steal their biggest point card.", "Keep your hand size up; options win."),
                listOf("Track the scrap pile: a 3 can bring back a key card.", "Time your 9 to bounce a royal back to their hand.", "Win with a sudden big play when they can't respond."),
                listOf("Plan around their counters and protections.", "Use 8s to plan exactly.", "Every card is points or tempo: choose wisely."),
            ),
        ),
        GameKind.PYRAMID to Course(
            listOf(
                "Remove pairs of uncovered cards that add up to 13. Kings (13) go alone; Queens are 12, Jacks 11, Aces 1.",
                "A card is uncovered when no card sits on top of it.",
                "Draw from the stock to find partners. Clear the pyramid to go a floor deeper.",
            ),
            listOf(
                listOf("Remove Kings as soon as they're uncovered.", "Pair cards from the pyramid before using stock cards.", "Look for 6+7, 5+8, 4+9: they're common."),
                listOf("Clear cards that cover the most others first.", "Don't waste stock cards on pairs the pyramid can make itself.", "Remember stock cards you passed."),
                listOf("Two cards of the same value block each other's partners: plan which to use.", "Keep track of how many 13-partners remain for each card.", "Use the waste card only when it frees something."),
                listOf("Count passes: limited redeals mean planning the stock.", "Prioritise the column with the deepest pile.", "Avoid pairing cards that leave dead ends."),
                listOf("Deeper floors: think the whole pyramid through before your first move.", "Hold partners for the hardest buried cards.", "Plan which stock cards you'll need."),
                listOf("Five floors: every pass matters.", "Map partners in advance: each buried card needs one.", "A good plan beats lucky draws."),
            ),
        ),
        GameKind.REGICIDE to Course(
            listOf(
                "Defeat twelve royals: Jacks, then Queens, then Kings. Play cards to deal damage equal to their value.",
                "Suit powers: ♥ heals your deck, ♦ draws cards, ♣ doubles damage, ♠ shields you. A royal ignores its own suit.",
                "If a royal survives, it strikes back: discard cards worth at least its attack, or you lose.",
            ),
            listOf(
                listOf("Use ♣ to double big attacks.", "Play ♠ to shrink the next counterattack.", "Kill exactly at 0 health to add the royal to your deck."),
                listOf("Combos: matching numbers up to 10 together, or an Ace with one card.", "Use ♦ to refill your hand before it gets thin.", "Keep high cards to survive counterattacks."),
                listOf("♥ returns discards to the deck: use it before the deck runs dry.", "Remember immunity: ♣ won't double against a club royal.", "Plan two turns: attack now, defend next."),
                listOf("Shield stacks: early spades protect against all later attacks on that royal.", "Exact kills give you strong royals to use later.", "Don't waste jesters early."),
                listOf("Count damage precisely; yielding can be right if you can absorb the hit.", "Manage hand size: a small hand loses to Kings.", "Sequence suit powers: draw, then attack."),
                listOf("No jesters: perfect play only.", "Use every exact kill you can.", "Plan the whole Jack, Queen and King gauntlet."),
            ),
        ),
        GameKind.DUNGEON_SOLITAIRE to Course(
            listOf(
                "Turn cards to explore the tomb. ♠ are monsters, ♦ traps, ♣ doors. Cards after an encounter are your actions.",
                "Beat an encounter with a card at least its value; lower cards hurt you.",
                "Aces are torches burning out (the fourth leaves you in the dark), Jacks are skills, Queens divine favour, Kings the tomb hoards.",
            ),
            listOf(
                listOf("Delve while your health is good; turn back before it's risky.", "Escaping takes one fewer turn than you delved.", "Jacks let you skip encounters: use them on dangerous ones."),
                listOf("Count torches: after three Aces, head out.", "Treasure only counts if you escape.", "Queens restore health: save them for emergencies."),
                listOf("Kings are the prize: go deeper only if you can come back.", "Drop treasure to flee if a fight would kill you.", "Watch your health against the biggest monsters left."),
                listOf("Plan the return: each turn back faces new encounters.", "Use skills on traps and doors as well as monsters.", "Two Kings needed: balance greed and safety."),
                listOf("Three Kings: deep delves are required; manage torches carefully.", "Keep a skill for the way out.", "The Scroll of Light saves you from darkness once."),
                listOf("All four Kings: delve deep, plan every skill.", "Risk is unavoidable; reduce it with careful timing.", "Escape the moment the fourth King is found."),
            ),
        ),
        GameKind.JAILBREAK to Course(
            listOf(
                "Match one black card against red cards that add up to it (A 1 … K 13) to clear them.",
                "The four sides are walls of six cards; clear any one wall to free the Joker.",
                "Stuck? Put a spare card on the Joker; the Joker's pile must be cleared too before it escapes.",
            ),
            listOf(
                listOf("Look for simple one-to-one matches first: black 7 and red 7.", "Focus on one wall at a time.", "Spare cards are your safety valve."),
                listOf("Use two or three reds to match a big black card.", "Plan which wall is easiest from the top cards.", "Don't fill the Joker pile too early."),
                listOf("Look under top cards: what will each match uncover?", "Corners hold two cards: clearing them helps matches.", "Count spare slots before using one."),
                listOf("Save small reds for making exact sums later.", "Big black cards need partners: plan them.", "Clear Joker pile cards as soon as possible."),
                listOf("Only two spare spaces: every spare is precious.", "Work out a full plan for one wall before starting.", "Match from corners to keep options open."),
                listOf("One spare space: near-perfect play needed.", "Every match should advance your chosen wall.", "Think three matches ahead."),
            ),
        ),
    )
}
