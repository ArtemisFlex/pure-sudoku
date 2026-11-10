package com.puresudoku.ui.training

import com.puresudoku.core.GameKind

object BoardLessons {
    val all: Map<GameKind, Course> = mapOf(
        GameKind.CHESS to Course(
            listOf(
                "Pawn: one square forward (two on its first move), captures one square diagonally forward. Reaching the far rank promotes it.",
                "Knight: an L-shape (two then one), and it jumps over pieces. Bishop: any distance diagonally. Rook: any distance straight.",
                "Queen: any distance in any direction. King: one square any way; it must never be left in check. Castling moves king two squares and the rook beside it.",
                "Checkmate the enemy king (attacked with no escape) to win. Stalemate, repetition and 50 quiet moves are draws.",
            ),
            listOf(
                listOf("Control the centre with pawns (e4, d4).", "Develop knights and bishops before moving the queen.", "Castle early to keep your king safe.", "Count before you capture: is the piece defended?"),
                listOf("Piece values: pawn 1, knight 3, bishop 3, rook 5, queen 9.", "Don't move the same piece twice in the opening without a reason.", "Connect your rooks by clearing the back rank."),
                listOf("Forks: a knight attacking two pieces at once wins material.", "Pins: a piece can't move without exposing something bigger behind it.", "Check every capture and check for both sides each move."),
                listOf("Skewers: attack a big piece so it moves and exposes one behind.", "Put rooks on open files and the 7th rank.", "Trade when ahead, avoid trades when behind."),
                listOf("Weak squares and pawn structure decide middlegames.", "Activate your king in the endgame.", "Passed pawns must be pushed."),
                listOf("Calculate forcing lines: checks, captures, threats.", "Plan, don't just react: improve your worst piece.", "Know basic mates: king and rook vs king, king and queen vs king."),
            ),
        ),
        GameKind.CHECKERS to Course(
            listOf(
                "Men move one square diagonally forward onto dark squares.",
                "Jump over an enemy piece to an empty square behind it to capture. If you can jump, you must, and multi-jumps continue.",
                "Reach the far row to crown a king, which moves and jumps backwards too. Leave your opponent with no moves to win.",
            ),
            listOf(
                listOf("Keep your back row filled early to stop enemy kings.", "Move toward the centre: centre pieces are stronger.", "Check for forced jumps before every move."),
                listOf("Advance in pairs so pieces protect each other.", "Avoid leaving a piece where it can be jumped.", "A king is worth about two men."),
                listOf("Set up two-for-one trades with forced jumps.", "Control the double corner.", "Don't rush to crown if it costs pieces."),
                listOf("Count tempo: who runs out of safe moves first?", "Use forced captures to drag enemy pieces where you want.", "Keep your pieces connected."),
                listOf("Endgames: kings hunt men; trap enemy kings in corners.", "When ahead, trade pieces to simplify.", "Breakthroughs: sacrifice one to crown another."),
                listOf("Calculate multi-jump combinations several moves deep.", "Opposition decides king endgames.", "Patience: improve your position before striking."),
            ),
        ),
        GameKind.GO to Course(
            listOf(
                "Place stones on the crossings, one per turn. Stones don't move.",
                "A group's empty neighbouring points are its liberties. Fill a group's last liberty to capture it.",
                "You can't play a move that captures nothing and leaves your own group with no liberties, and you can't instantly retake a ko.",
                "When neither side can gain, both pass. Stones plus surrounded empty points are counted; White gets komi.",
            ),
            listOf(
                listOf("Start in the corners, then sides, then centre: corners are easiest to secure.", "Keep your stones connected.", "Count liberties before every fight."),
                listOf("Atari: a group with one liberty is in danger; save it or let it go.", "Capture stones that cut your groups apart.", "Don't play inside territory you already own."),
                listOf("Two eyes make a group alive forever.", "Make shape: the 'bamboo joint' and 'tiger's mouth' are strong.", "Pressure weak enemy groups while building your own territory."),
                listOf("Ladders: chase a stone in a zigzag only if no friendly stone breaks it.", "Nets capture stones that would escape a ladder.", "Big moves first: corners and sides are worth more early."),
                listOf("Sente: play moves that force a reply, then play elsewhere.", "Reduce large enemy areas instead of invading too deep.", "Endgame: count each move's value and play the biggest."),
                listOf("Life and death: learn the shapes that live and die.", "Influence turns into territory later; plan for it.", "Read sequences before playing; Go rewards careful reading."),
            ),
        ),
        GameKind.SHOGI to Course(
            listOf(
                "King 王 one step any way. Rook 飛 any distance straight; Bishop 角 any distance diagonally.",
                "Gold 金: one step any way except diagonally back. Silver 銀: one step diagonally or straight forward.",
                "Knight 桂: jumps two forward and one sideways. Lance 香: any distance straight forward. Pawn 歩: one step forward.",
                "Reaching the far three rows lets most pieces promote. Captured pieces go to your hand and can be dropped back in on your turn.",
            ),
            listOf(
                listOf("Build a castle: move your king to one side behind golds and silvers.", "Open your bishop's diagonal with a pawn move.", "Capture pieces you can drop later: hand pieces are powerful."),
                listOf("Promote rooks and bishops when you can: dragons and horses are huge.", "Don't leave pieces where a drop can attack two at once.", "A pawn drop is a cheap, strong threat."),
                listOf("Golds defend, silvers attack.", "Knight forks: a dropped knight can hit two pieces.", "Watch for drops behind your lines."),
                listOf("Use the hand: drops near the enemy king start attacks.", "Exchange pieces when it opens their king.", "Count attackers and defenders around each king."),
                listOf("Tsume: checkmate sequences with drops are the heart of shogi.", "Speed matters: the race to mate decides most games.", "Keep your castle intact until the endgame."),
                listOf("Calculate forcing mates with checks and drops.", "Sacrifice material to break the castle.", "Defend with drops when the attack is faster."),
            ),
        ),
        GameKind.XIANGQI to Course(
            listOf(
                "General 帥 and Advisors 仕 stay in the palace (the X-marked square). The general moves one step straight; advisors one step diagonally.",
                "Elephants 相 move exactly two diagonally, can't be blocked at the midpoint and never cross the river. Horses 傌 move one straight then one diagonally outward, blocked if the first step is occupied.",
                "Chariots 俥 move like rooks. Cannons 炮 move like rooks but capture by jumping exactly one piece. Soldiers 兵 move forward; after crossing the river they may also step sideways.",
                "Generals may never face each other on an open file. Checkmate or stalemate the enemy general to win.",
            ),
            listOf(
                listOf("Get your chariots out early: they're your strongest pieces.", "A central cannon (炮 to the middle file) is the classic opening.", "Develop horses so they're not blocked."),
                listOf("Piece values: chariot 9, cannon 4.5, horse 4, advisor and elephant 2, soldier 1 (2 across the river).", "Protect your horse's leg square.", "Keep advisors and elephants paired for defence."),
                listOf("Cannons need a screen: line up a piece to fire through.", "Horses get stronger in the centre; cannons weaker as pieces come off.", "Cross soldiers into enemy territory to add pressure."),
                listOf("Use the flying general rule to pin pieces on the centre file.", "Double chariots on an open file are deadly.", "Block horse legs to cut their mobility."),
                listOf("Exchange to simplify when ahead.", "Attack the palace with chariot plus horse.", "Late game: soldiers near the palace are worth a lot."),
                listOf("Calculate checking sequences; xiangqi mates come fast.", "Coordinate cannon and chariot for unstoppable checks.", "Defend accurately: one loose advisor can lose the game."),
            ),
        ),
        GameKind.BACKGAMMON to Course(
            listOf(
                "Roll two dice and move your white checkers that many points toward your home (bottom right). Doubles move four times.",
                "You can't land on a point with two or more black checkers. Landing on a single black checker hits it to the bar.",
                "A hit checker must re-enter before anything else moves. When all 15 are home, bear them off. First off wins.",
            ),
            listOf(
                listOf("Make points: two checkers on a point are safe.", "Avoid leaving single checkers (blots) where they can be hit.", "Opening 3-1: make your 5-point (8/5, 6/5)."),
                listOf("Hit blots when it's safe for you.", "Bring back checkers steadily; don't leave stragglers.", "Your 5-point and 7-point are the most valuable early."),
                listOf("Count shots: how many rolls hit your blot?", "Build a prime (several points in a row) to trap enemy checkers.", "Run your back checkers when the coast is clear."),
                listOf("Anchors: keep two checkers on a point in their home as a safe base.", "When ahead in the race, avoid contact and run.", "When behind, keep contact and look for hits."),
                listOf("Close your home board to trap a hit checker on the bar.", "Bear off safely: avoid blots when they still have checkers back.", "Pip count tells you who's ahead in the race."),
                listOf("Weigh risk vs reward each roll: hit, prime, or run.", "Gammons double the win: press when they're trapped.", "Plan bear-offs to minimise wasted pips."),
            ),
        ),
        GameKind.MANCALA to Course(
            listOf(
                "Tap one of your pits (bottom row) to pick up its seeds and drop one in each pit anticlockwise, including your store but not the computer's.",
                "If the last seed lands in your store, you go again.",
                "If it lands in an empty pit on your side, you capture it plus the seeds opposite. When a side empties, the game ends: most seeds wins.",
            ),
            listOf(
                listOf("Look for a pit whose seeds reach exactly your store: free turn.", "Play from the rightmost pits first to set up free turns.", "Grab captures when the opposite pit is full."),
                listOf("Chain free turns: one can set up another.", "Protect big piles from capture by keeping the pit opposite non-empty.", "Count seeds before you tap."),
                listOf("Hoard seeds in your rightmost pit for a late big move.", "Starve the computer's side to end the game when you're ahead.", "Watch what the computer can capture next."),
                listOf("Think two moves ahead: what does your move give them?", "Leave empty pits on your side as capture traps.", "Keep seeds on your side near the end."),
                listOf("Endgame counting: when a side empties, the rest go to its owner.", "Plan multi-move free-turn chains.", "Force them to open their big pits."),
                listOf("Calculate exact landing pits for both sides.", "Tempo matters: sometimes a small move is best.", "Play for the final seed count, not just captures."),
            ),
        ),
        GameKind.MAHJONG to Course(
            listOf(
                "Remove matching pairs of free tiles until the board is empty.",
                "A tile is free when nothing lies on it and its left or right side is open.",
                "Any flower matches any flower; any season matches any season. Other tiles must be identical.",
            ),
            listOf(
                listOf("Clear tiles on top of tall stacks first.", "Free the long rows from their ends.", "Use hints while you learn."),
                listOf("When three of a kind are free, choose the pair that frees more tiles.", "Don't remove both of a pair if the other two are still buried under each other.", "Watch for tiles stacked on their own partner."),
                listOf("Look under before you match: what does each removal free?", "Work on several areas at once to keep options open.", "Count how many of each tile are still in play."),
                listOf("Plan around buried matching tiles.", "Avoid locking a tile by removing its only free neighbour row too early.", "Save hints for real dead ends."),
                listOf("Only one shuffle: think before every match.", "Clear the highest layers early on big layouts.", "Track each set of four."),
                listOf("No help at all: plan carefully and match thoughtfully.", "The order of removal is everything.", "When two moves look equal, choose the one that frees more."),
            ),
        ),
        GameKind.GOMOKU to Course(
            listOf(
                "Take turns placing stones on the crossings. You play Black and go first.",
                "Five or more stones in an unbroken line (across, down or diagonal) wins.",
                "Tap once to preview, tap again to place.",
            ),
            listOf(
                listOf("Play near the centre and near your other stones.", "Block White's lines of three before they grow.", "Build in several directions at once."),
                listOf("An open three (empty at both ends) must be blocked right away.", "An open four can't be stopped: it wins.", "Keep your stones close together."),
                listOf("Make double threats: two open threes at once.", "Block on the side that also helps your own lines.", "Look for White's broken threes too (X_XX)."),
                listOf("Four-three: a four plus an open three wins.", "Defend by making your own four, forcing White to answer.", "Think about where White's next threat comes from."),
                listOf("Build shapes that create many potential lines.", "Use forcing moves (fours) to steer White's stones.", "Count threats for both sides each move."),
                listOf("VCF: win with a chain of continuous fours.", "Plan threat sequences several moves deep.", "Defend early; late defence is too late."),
            ),
        ),
        GameKind.MORRIS to Course(
            listOf(
                "Each player places nine men on the 24 points, one at a time.",
                "Three of your men in a row along a line is a mill: take one enemy man (not from a mill unless all are in mills).",
                "After placing, move men along lines to empty neighbouring points. With three men you may fly anywhere. Reduce the enemy to two men or leave it no moves.",
            ),
            listOf(
                listOf("Place men on points with four connections (the middle of each side).", "Block when Black has two in a line.", "Don't rush to form mills early; mobility matters."),
                listOf("Set up a mill that can open and close repeatedly.", "Keep your men able to move after placing ends.", "Take enemy men that block your mills."),
                listOf("A double mill (open one, close another each move) wins quickly.", "Watch Black's two-in-a-rows during placement.", "Don't get boxed in: count your free moves."),
                listOf("Plan the placing phase for the moving phase.", "Trap enemy men so they can't move.", "Take men that would complete Black's mills."),
                listOf("Flying endgame: three men are dangerous; block their lines.", "Keep pieces connected for defence.", "Force Black to break mills with nowhere good to go."),
                listOf("Read placement deeply: it decides the game.", "Create unstoppable double threats.", "Never give Black a free mill."),
            ),
        ),
        GameKind.TIC_TAC_TOE to Course(
            listOf(
                "Take turns marking squares; you are X.",
                "Three in a row across, down or diagonally wins the round.",
                "A match is up to five rounds and you alternate going first. Win more rounds than O.",
            ),
            listOf(
                listOf("Take the centre when you can.", "Always block O's two in a row.", "Look for your own two in a row first."),
                listOf("If O takes the centre, take a corner.", "Corners are stronger than edges.", "Check every line before you play."),
                listOf("Fork: create two ways to win at once.", "Opening corner, then opposite corner, can set up forks.", "Block O's fork setups early."),
                listOf("When O opens in a corner, take the centre.", "Avoid edges when O holds two corners.", "Perfect play on both sides always draws."),
                listOf("Force O's replies with threats to steer the game.", "Count rounds: going first is your chance to win.", "A draw against strong play is a fine result."),
                listOf("Against perfect play, draws are the best possible: aim for them.", "Never miss a block.", "Win on any slip O makes."),
            ),
        ),
    )
}
