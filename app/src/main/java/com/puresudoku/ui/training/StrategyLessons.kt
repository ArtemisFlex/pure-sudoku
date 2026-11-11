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
