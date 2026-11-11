package com.puresudoku.ui.training

import com.puresudoku.core.GameKind

/** Coaching for the Eiseria strategy games. */
object StrategyLessons {
    private val basics = listOf(
        "Reach the points goal first. Villages are worth 1, towers 2, the Great Road (5+ roads in a line) 2, the Mightiest Host (3+ Knights) 2, and Relic spells 1 each.",
        "Each turn: roll, collect from tiles touching your buildings, then build, trade or cast a spell, and end your turn.",
        "Road = timber + mana. Village = timber, mana, grain, beasts. Tower = 3 ore + 2 grain. Spell = ore, grain, beasts.",
        "A 7 wakes the dragon: more than 7 cards and you lose half. The roller moves the dragon to block a tile and steal a card.",
        "Claim a Dragon's Waste: be first to build 2 roads around it and a tower on one of its corners for 1 point and a free spell.",
        "Harder levels bring bigger islands, more wastelands and thicker fog.",
    )

    private fun levels(twist: List<String>) = listOf(
        listOf("Settle on 6s and 8s: they're rolled most.", "Cover different resources so you can always build something.", twist[0]),
        listOf("Point your first roads at the next good village spot.", "Spend before you end your turn: big hands invite a 7.", twist[1]),
        listOf("Settle beside a portal that matches what you produce lots of.", "Ore and grain build towers: you'll need them mid-game.", twist[2]),
        listOf("Block the rival's best tile with the dragon, especially their towers.", "Race for the Great Road only if it doesn't stall your villages.", twist[3]),
        listOf("Count the rival's points, including hidden spells, before they spring a win.", "Knights do double duty: protect your tiles and earn the Host.", twist[4]),
        listOf("Plan two turns ahead: know which build each roll unlocks.", "Fireball a rival road to cut their Great Road or a key expansion.", twist[5]),
    )

    val all: Map<GameKind, Course> = mapOf(
        GameKind.REALMFORGE to Course(basics + "The outer realm starts hidden. Building next to fog reveals it and pays you one of its resource; some tiles hide ruins with free spells.", levels(listOf(
            "The middle tiles are known: settle there first, then explore.",
            "Explore with roads: every new tile pays you a card.",
            "Ley stones sit on the coast. Two touched by your roads give +1 mana every turn.",
            "Ruins hold free spells: explore the far edges early.",
            "Fog hides the rival's future land too: claim the best edges before they're seen.",
            "Mix exploring and building: a road that reveals and reaches a spot is worth double.",
        ))),
        GameKind.REALM_DRAGON to Course(basics + "The dragon gains strength on every 7 and walks one tile per round toward the leader. Each Knight wounds it; the final blow is worth 2 points. From Hard up, wild dragons wake in wastelands as the fog lifts, and flee only when the great dragon falls.", levels(listOf(
            "Stay level with the rival early: the dragon hunts whoever leads.",
            "Save a Knight for when the dragon sits on your best tile.",
            "Count its health: be the one who lands the last wound.",
            "Spread your villages so one dragon tile can't starve you.",
            "Once it's slain, 7s only cost cards: plan big hands around that.",
            "Time your lead: overtake just before you can finish the game.",
        ))),
        GameKind.WISTERIA to Course(listOf(
            "No dice: every tile you hold pays out at the start of your turn. Most give 1 each of three resources; some give 2 of two; the richest give 3 of one.",
            "Claim free land touching your territory. The price is a village's cost, plus extra for rich tiles, plus a card for every three tiles you hold.",
            "Villages are worth 1 point, towers 2, castles 3. First to the points goal wins. Water can't be held or crossed.",
            "Knights: a village's single knight strikes a touching rival tile at once. A tower's 2 or a castle's 3 march together along a route you plot, one tile per turn.",
            "Each knight burns a village or knocks a castle or tower down a level. Armies that meet fight: the bigger side keeps the difference.",
            "Walls guard one side of a tile: they stop a whole attack from that side, and hold the dragon back a turn, then break.",
            "Every 5 turns the dragon moves one tile toward the leader. It burns a village or knocks a tower down a level, then returns to its lair.",
        ), listOf(
            listOf("Settle a rich tile with free land around it.", "Claim every turn early: more land, more income."),
            listOf("Raise a tower on your richest tile before the dragon comes.", "Don't lead by too much too early: the dragon hunts the leader."),
            listOf("Wall the sides that face rival villages with knights.", "A village knight is cheap and strikes instantly: use them on weak neighbours."),
            listOf("Plot long marches to come round an unwalled side.", "Keep a garrison home: armies that meet cancel out."),
            listOf("Castles are worth 3 and take three hits: build them deep inside your land.", "Strike when the rival can't afford to retake the land."),
            listOf("Time your last upgrades so you win before the dragon or an army arrives.", "Break walls with Siege Fire right before your army lands."),
        )),
        GameKind.KREASHUN to Course(listOf(
            "Roll each turn: tiles with that number pay every village on their corners, towers twice. Tiles you hold pay your corner buildings double.",
            "Walls go on sides joined to your villages, walls or tiles. Wall every side of a tile, coast included, to capture it: whoever builds the closing wall takes it.",
            "Each tile you hold is worth a point. Fell a rival castle for one more. Reach the points goal to win; drop back to 0 after scoring and you lose.",
            "Held tiles can raise a castle (3 hit points) and hire knights: 2 on a bare tile, 3 in a castle.",
            "A knight strikes a touching rival tile: it breaks the wall on that side first. Broken walls are rubble and are never rebuilt.",
            "Once that side is open, a hit takes a tile with no castle, or knocks a point off a castle. A tile taken by force brings its walls, villages, towers and knights with it; one taken from a felled castle rebuilds its castle at half price.",
        ), listOf(
            listOf("Settle on 6s and 8s, touching tiles you can wall in.", "Count the sides: a tile at 5 of 6 is a gift to whoever closes it."),
            listOf("Share walls: one wall closes a side of two tiles at once.", "Wall tiles your villages touch: they'll pay double."),
            listOf("Never leave a tile one wall from closed where the rival can reach it.", "Towers on held tiles pay four times: build them there."),
            listOf("Castle the tiles that touch the rival.", "Hire knights in pairs: one breaks the wall, the next takes the tile."),
            listOf("Strike bare tiles before the rival can castle them.", "A felled castle is a point and a half-price rebuild for you."),
            listOf("Watch the rival's only tiles: taking their last one wins.", "Plan the closing wall two turns ahead."),
        )),
        GameKind.REALM_NIGHT to Course(basics + "Rounds alternate day and night. At night crystal spires pay double mana, and the dragon raids the leader's best tile.", levels(listOf(
            "Crystal spires are worth more here: settle one early.",
            "Use night mana for roads and villages.",
            "Expect a night raid if you lead: keep your hand small at night.",
            "Spread across tiles so one raid can't cripple you.",
            "Lead on the day round, not the night one, when you can choose.",
            "Time your winning build for your own turn after a night raid lands elsewhere.",
        ))),
    )
}
