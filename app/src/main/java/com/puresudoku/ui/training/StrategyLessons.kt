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
            "Walls guard one side of a tile: they stop a whole attack from that side, then break. Walls only keep out knights: the dragon smashes them.",
            "Every 5 turns (4 on Hard and Expert, 3 on Master and Extreme) the dragon flies straight to the leader's grandest tile and takes 1 of its health (village 1, tower 2, castle 3). The damage never heals; at 0 the tile is razed and lost.",
            "The dragon has 5 health. Only knights can hurt it, by striking or hunting it, and every knight dies doing so. Its slayer wins the Dragon Scale (+1 point) and the Black Dragon Knight: he never dies, flies over lakes, and strikes or leads a march from any tile you hold, once a turn.",
        ), listOf(
            listOf("Settle a rich tile with free land around it.", "Claim every turn early: more land, more income."),
            listOf("Raise a tower on your richest tile before the dragon comes.", "The dragon's damage never heals: send knights to end it before it razes your castle."),
            listOf("Wall the sides that face rival villages with knights.", "A village knight is cheap and strikes instantly: use them on weak neighbours."),
            listOf("Plot long marches to come round an unwalled side.", "Keep a garrison home: armies that meet cancel out."),
            listOf("Castles are worth 3 and take three hits: build them deep inside your land.", "Strike when the rival can't afford to retake the land."),
            listOf("Time your last upgrades so you win before the dragon or an army arrives.", "Break walls with Siege Fire right before your army lands."),
        )),
        GameKind.ASHFALL to Course(listOf(
            "Ashfall plays like Wisteria: claim land touching yours, raise villages (1 point), towers (2) and castles (3), hire knights and build walls. No dice: every tile pays every turn.",
            "There is no dragon. The volcano in the middle erupts every 4 turns, and the next eruption's lava flows are always shown on the board.",
            "Lava buries every tile in its path, buildings and all. Knights flee to a touching tile of yours with room. Lava never takes a player's last tile.",
            "Buried land is molten for 4 turns, then cooling ash anyone can claim, which pays nothing for 6 turns. Then it's rich ash, paying its yield plus 1 more of its main resource.",
            "Flows that reach the sea or a lake turn the water into new land. The goal is half again Wisteria's, so you may have to wait for the island to grow.",
            "A wall on a flow's path turns the lava aside and breaks. Quench saves a tile from the next eruption; Rift moves a flow. If the island can't grow any more, the leader wins.",
        ), listOf(
            listOf("Settle away from the volcano, on rich land.", "Look at the glowing flows before you claim."),
            listOf("Never build up a tile in a lava path.", "Claim cooling ash early: it ripens into the best land."),
            listOf("Wall the side a flow comes in by, to turn it.", "Keep knights next door to flee to."),
            listOf("Steer lava toward your rival with walls and Rift.", "Quench your castles when a flow heads their way."),
            listOf("Race for the new land at the coast as it cools.", "Plan for a long game: points come and go with the lava."),
            listOf("Count the sea left: when it's gone, the leader wins.", "Time your castles between eruptions."),
        )),
        GameKind.KREASHUN to Course(listOf(
            "Roll each turn: tiles with that number pay every village on their corners, towers twice. Tiles you hold pay your corner buildings double.",
            "Walls go on sides joined to your villages, walls or tiles. Wall every side of a tile, coast included, to capture it: whoever builds the closing wall takes it.",
            "Each tile you hold is worth a point. Fell a rival castle for one more. Reach the points goal to win; drop back to 0 after scoring and you lose.",
            "Held tiles can raise a castle (3 hit points) and hire knights: 2 on a bare tile, 3 in a castle.",
            "A knight strikes a touching rival tile: it breaks the wall on that side first. Broken walls are rubble and are never rebuilt.",
            "Once that side is open, a hit takes a tile with no castle, or knocks a point off a castle. A tile taken by force brings its walls, villages, towers and knights with it; one taken from a felled castle rebuilds its castle at half price.",
            "Spells cost ore, grain and beasts; cast one a turn, not the turn you bought it. Rally adds a knight, Stonewright gives two free walls, Harvest Moon two resources, Siege Fire knocks a point off a castle, and a Relic is a hidden point.",
        ), listOf(
            listOf("Settle on 6s and 8s, touching tiles you can wall in.", "Count the sides: a tile at 5 of 6 is a gift to whoever closes it."),
            listOf("Share walls: one wall closes a side of two tiles at once.", "Wall tiles your villages touch: they'll pay double."),
            listOf("Never leave a tile one wall from closed where the rival can reach it.", "Towers on held tiles pay four times: build them there."),
            listOf("Castle the tiles that touch the rival.", "Hire knights in pairs: one breaks the wall, the next takes the tile."),
            listOf("Strike bare tiles before the rival can castle them.", "A felled castle is a point and a half-price rebuild for you."),
            listOf("Watch the rival's only tiles: taking their last one wins.", "Plan the closing wall two turns ahead."),
        )),
        GameKind.VEILGUARD to Course(listOf(
            "Each round both players give up to three secret orders. They're revealed and carried out together: Crowns move, walls rise, spies report, armies march, claims land, then knights and buildings appear.",
            "Your Crown hides on one of your tiles. Take the rival's Crown to win; lose yours and you lose. There is no points goal: points only break a stalemate after 60 rounds. A Crown order slips your Crown to a touching tile you hold.",
            "Armies are banners. The rival sees how many, never which are knights and which are cheap decoys. Scout a tile to see the truth, and whether the Crown is there.",
            "An attack wins only if its knights outnumber the defenders, plus 1 for a tower, 2 for a castle, 2 if fortified and 2 through a wall.",
            "The Veilnight Thief steps toward the leader every round. On any land he stands on he steals, and every 5, 4 or 3 rounds (by level) he takes 1 health from the building there (village 1, tower 2, castle 3); the damage never heals, and at 0 the land is lost. Walls can't stop him. Every knight that fights him dies, but kill him and you win the Thief's Dagger (+1 point) and his service.",
            "Spells cost ore, grain and beasts and don't use an order. Cast one a round, in secret: Rally adds a knight, Harvest Moon two resources, Siege Fire burns 2 banners before marches, Veil wipes what the rival knows about you, and a Relic is a hidden point.",
        ), listOf(
            listOf("Settle rich land well apart: your Crown can only hide where you hold land.", "Claim every round early: more land, more hiding places."),
            listOf("Never leave your Crown's tile bare beside the rival.", "Decoys are cheap: stack a few where your Crown isn't."),
            listOf("Scout the rival's biggest stack: it's often guarding the Crown, or bluffing.", "Move your Crown the round after a rival spy is seen."),
            listOf("March where the rival just marched away from: armies leave tiles empty.", "Fortify when you expect an attack; it's cheap."),
            listOf("Stay level on points: the thief robs the leader.", "Kill the thief when he's weak: his service pays every round."),
            listOf("Bluff with a decoy march on one side and strike with knights on the other.", "Raise castles on tiles that aren't your Crown, to draw the rival's spies."),
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
