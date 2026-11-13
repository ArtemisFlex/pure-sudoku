package com.puresudoku.ui.games.realm

import androidx.compose.ui.graphics.Color
import com.puresudoku.core.GameKind
import com.puresudoku.core.board.AegConquest
import com.puresudoku.core.board.Kreashun
import com.puresudoku.core.board.Realm
import com.puresudoku.core.board.Veilguard
import com.puresudoku.core.board.Wisteria

/** The words and figures of each rulebook. */
internal object RulebookText {

    private class BB {
        val out = ArrayList<RB>()
        fun ch(t: String) { out += RB.Chapter(t) }
        fun drop(t: String) { out += RB.Para(t, drop = true) }
        fun p(t: String) { out += RB.Para(t) }
        fun bl(vararg t: String) { out += RB.Bullets(t.toList()) }
        fun note(t: String, x: String) { out += RB.Note(t, x) }
        fun warn(t: String, x: String) { out += RB.Note(t, x, warn = true) }
        fun fig(caption: String, vararg panels: Panel, height: Int = 180) { out += RB.Figure(panels.toList(), caption, height) }
        fun costs(vararg r: CostRow) { out += RB.Costs(r.toList()) }
        fun steps(vararg s: Pair<String, String>) { out += RB.Steps(s.toList()) }
        fun table(head: List<String>, vararg rows: List<String>) { out += RB.Table(head, rows.toList()) }
        fun spells(vararg s: Triple<RI, String, String>) { out += RB.Spells(s.toList()) }
        fun terrains() { out += RB.Terrains }
        fun trophies(vararg t: Triple<Int, String, String>) { out += RB.Trophies(t.toList()) }
        fun dice() { out += RB.Dice }
    }

    private fun book(logo: Int, accent: Color, motto: String, block: BB.() -> Unit) = Book(logo, accent, motto, BB().apply(block).out)

    private val LEVELS = listOf("Easy", "Medium", "Hard", "Expert", "Master", "Extreme")
    private const val TIMBER = 0; private const val ORE = 1; private const val GRAIN = 2; private const val BEASTS = 3; private const val MANA = 4

    // =================================================================  Eiseria (all three)

    fun eiseria(kind: GameKind): Book {
        val twist = when (kind) { GameKind.REALM_DRAGON -> Realm.WAKE; GameKind.REALM_NIGHT -> Realm.NIGHT; else -> Realm.FOG }
        val (logo, accent, motto) = when (twist) {
            Realm.WAKE -> Triple(com.puresudoku.R.drawable.rt_logo_wyrm, Color(0xFFA32020), "The great dragon is waking. It grows with every seven, walks toward whoever leads, and only the boldest knights can drive it off.")
            Realm.NIGHT -> Triple(com.puresudoku.R.drawable.rt_logo_night, Color(0xFF3949AB), "Day and night turn over the island. Crystal spires blaze under the moon, and the dragon hunts in the dark.")
            else -> Triple(com.puresudoku.R.drawable.rt_logo, Color(0xFF2E6B45), "Beyond the known valleys the realm lies under mist. Settle, explore and build. The first to reach the points goal rules Eiseria.")
        }
        return book(logo, accent, motto) {
            // ---------------------------------------------------------------- I
            ch("The Realm")
            drop("Two rulers settle the island of Eiseria: you and the computer. You both build **villages** and **towers** where tiles meet, and lay **roads** between them. Every roll of the dice makes the land pay out. The first ruler to reach the **points goal** wins.")
            p(when (twist) {
                Realm.WAKE -> "This is **Wyrmrise**. A dragon grows stronger every time a 7 is rolled and walks toward whoever is winning. Knights can wound it, and whoever drives it off earns glory."
                Realm.NIGHT -> "This is **Nightfall**. Rounds switch between day and night. At night the crystal spires pay double, and the dragon raids whoever is leading."
                else -> "This is **Eiseria, the Unmapped Realm**. Most of the island starts hidden in fog. Exploring it pays you resources, and ruins hold free spells."
            })
            table(listOf("Level", "Points goal", "Island"),
                listOf("Easy", "10", "19 tiles"), listOf("Medium", "10", "22 tiles"), listOf("Hard", "11", "25 tiles"),
                listOf("Expert", "11", "28 tiles"), listOf("Master", "12", "31 tiles"), listOf("Extreme", "12", "37 tiles"))
            p("Bigger islands also have more Dragon's Wastes: 1 on Easy and Medium, 2 on Hard and Expert, 3 on Master and Extreme.")

            // ---------------------------------------------------------------- II
            ch("The Land")
            drop("The island is made of six kinds of tile. Each of the first five gives one resource. The Dragon's Waste gives nothing, and the dragon starts there.")
            terrains()
            p("Each resource tile carries a **number token** from 2 to 12. The dots under a number show how often it comes up: the more dots, the more often. **6 and 8** are printed in red. They come up most, so no two red numbers ever sit side by side.")
            p("The coast has seven **portals**. Two let you trade any 3 cards for 1. Each of the other five lets you trade 2 cards of one resource for 1.")

            // ---------------------------------------------------------------- III
            ch("Your Pieces")
            drop("Your pieces never sit inside a tile. They go on the board's lines and corners:")
            bl("**Villages** go on a corner, where up to three tiles meet. A village earns from every tile it touches.",
                "**Towers** replace one of your villages. A tower earns twice as much, and the village piece goes back to your supply.",
                "**Roads** go along an edge between two corners. They take you to new places to build.")
            fig("Your village (blue) sits where the forest, fields and mountain meet. It earns from all three tiles. A road runs from it, and a rival tower and road are nearby.",
                Panel(scene {
                    val a = hex(0, 0, TIMBER, 6); val b = hex(1, 0, GRAIN, 8); val c = hex(0, 1, ORE, 5)
                    village(a, 2); road(a, 1); road(a, 0)
                    tower(b, 2, 1); road(b, 2, 1); road(c, 1, 1)
                }))
            table(listOf("Piece", "Limit", "Worth"), listOf("Road", "15", "—"), listOf("Village", "5", "1 point"), listOf("Tower", "4", "2 points"))

            // ---------------------------------------------------------------- IV
            ch("Setting Up")
            drop("Before the first roll, each player places **two villages, each with a road** touching it. The order snakes: **you, rival, rival, you**.")
            fig("In setup, choose any open corner (the distance rule still applies), then lay a road from it.",
                Panel(scene { val a = hex(0, 0, TIMBER, 6); hex(1, 0, GRAIN, 8); hex(0, 1, ORE, 5); spot(a, 2) }, "1 · A village"),
                Panel(scene { val a = hex(0, 0, TIMBER, 6); hex(1, 0, GRAIN, 8); hex(0, 1, ORE, 5); village(a, 2); road(a, 1) }, "2 · A road beside it"),
                height = 140)
            note("Your head start", "Your **second** village pays you one card for each resource tile it touches, so you can build sooner.")

            // ---------------------------------------------------------------- V
            ch("A Turn")
            steps(
                "Roll the dice" to "Tap the dice. Every tile showing that number pays both players.",
                "Collect" to "Each of your villages on a matching tile earns 1 card, and each tower earns 2.",
                "Act" to "Build, trade, buy spells and cast one spell, in any order and as often as you can pay.",
                "End your turn" to "Your rival takes their turn.",
            )
            dice()
            p("On **Easy and Medium** the rolls come from a shuffled deck of all 36 dice results, so over time every number turns up as often as it should.")

            // ---------------------------------------------------------------- VI
            ch("The Harvest")
            drop("When a number is rolled, every tile with that number pays. **A village earns 1 card** and **a tower earns 2**. This happens for every one of your buildings touching the tile, on both players' turns.")
            fig("An 8 is rolled. Your village earns 1 grain, your tower earns 2, and the rival's village earns 1. If the dragon sits on the tile, it pays no one.",
                Panel(scene {
                    val g = hex(0, 0, GRAIN, 8, glow = Color(0xFFFFF59D)); hex(1, 0, TIMBER, 3); hex(0, 1, BEASTS, 10)
                    village(g, 0); tower(g, 2); village(g, 4, 1)
                    mark(g, "+1", GOOD, corner = 0, dy = 0.5f); mark(g, "+2", GOOD, corner = 2, dx = -0.45f, dy = -0.3f); mark(g, "+1", RIVAL, corner = 4, dx = 0.42f, dy = -0.3f)
                }, "Roll 8"),
                Panel(scene {
                    val g = hex(0, 0, GRAIN, 8); hex(1, 0, TIMBER, 3); hex(0, 1, BEASTS, 10)
                    village(g, 0); tower(g, 2); village(g, 4, 1); dragon(g, 0.85f)
                }, "Blocked by the dragon"))

            // ---------------------------------------------------------------- VII
            ch("Building")
            costs(
                CostRow("Road", Piece.ROAD, Realm.COST_ROAD, "Must join one of your roads or buildings."),
                CostRow("Village", Piece.VILLAGE, Realm.COST_VILLAGE, "On an open corner at the end of one of your roads."),
                CostRow("Tower", Piece.TOWER, Realm.COST_TOWER, "Replaces one of your villages."),
                CostRow("Spell", Piece.SPELL, Realm.COST_SPELL, "Draw the top card of the spell deck."),
            )
            p("**The distance rule.** A village can't go on a corner right next to another village or tower, whoever owns it. There must be at least one empty corner between them.")
            fig("Red crosses are too close to your village. Yellow rings are far enough, but after setup they still need one of your roads to reach them.",
                Panel(scene {
                    val a = hex(0, 0, BEASTS, 9); val b = hex(1, 0, TIMBER, 4); val c = hex(0, 1, GRAIN, 11)
                    village(a, 2); no(a, 1); no(a, 3); no(b, 3); spot(a, 0); spot(a, 4); spot(c, 2); spot(b, 0)
                }))
            p("**Roads** must join one of your own roads, villages or towers. A rival's village or tower blocks the way: you can't continue a road through it.")

            // ---------------------------------------------------------------- VIII
            ch("The Seven and the Dragon")
            drop("A roll of 7 pays nothing. Instead the dragon stirs:")
            steps(
                "Discard" to "Anyone holding **more than 7 cards** discards half of them, rounded down.",
                "Move the dragon" to "The player who rolled moves the dragon to any other tile. That tile pays nothing while the dragon is there.",
                "Steal" to "If the rival has a village or tower on that tile, the roller takes one of their cards at random.",
            )
            fig("You roll a 7 and send the dragon from the waste onto the rival's best ore tile, then steal a card.",
                Panel(scene {
                    val w = hex(-1, 0, T_WASTE); val a = hex(0, 0, ORE, 6); hex(1, 0, GRAIN, 9); hex(0, 1, TIMBER, 5)
                    village(a, 1, 1); village(a, 3, 0); dragon(a, 0.85f); arrow(w, a, color = GOLDEN, dashed = true)
                    mark(a, "Steal 1", BAD, corner = 1, dx = 0.1f, dy = 0.75f)
                }))
            note("Spend before you end", "If you hold a big hand, spend it before you end your turn, or the next 7 could cost you half of it.")
            if (twist == Realm.WAKE) p("In Wyrmrise each 7 also makes the dragon stronger. Its chapter explains how.")

            // ---------------------------------------------------------------- IX
            ch("Trading")
            drop("After rolling, you may trade with the bank as often as you like:")
            table(listOf("Trade with", "Rate"),
                listOf("The bank", "4 : 1"),
                listOf("A 3:1 portal", "3 : 1"),
                listOf("A resource portal", "2 : 1"))
            p("To use a portal, you need a village or tower on one of the two coast corners its rope is tied to. A 2:1 portal only takes the resource shown on it.")
            p("The rival may also **offer you a deal** on its turn. Read it and accept or refuse it. It only offers what it can pay.")

            // ---------------------------------------------------------------- X
            ch("Spells")
            drop("A spell costs **ore, grain and beasts** and is drawn from a deck of 25: 14 Knights, 5 Relics, 2 Earthshapers, 2 Harvest Moons and 2 Fireballs.")
            bl("You may cast **one spell per turn**.", "You can't cast a spell on the turn you bought it.", "Relics are never cast. They count as points straight away, hidden from your rival.")
            spells(
                Triple(RI.KNIGHT, "Knight", "Move the dragon to another tile and steal a card from the rival there. " + if (twist == Realm.WAKE) "It also wounds the dragon." else "Play three, and more than your rival, to hold the Mightiest Host."),
                Triple(RI.RELIC, "Relic", "A hidden treasure worth 1 point."),
                Triple(RI.EARTH, "Earthshaper", "Build two roads for free."),
                Triple(RI.MOON, "Harvest Moon", "Take any two resources from the supply."),
                Triple(RI.FIRE, "Fireball", "Burn one of your rival's roads that has a loose end: a road end touching none of their other pieces."),
            )

            // ---------------------------------------------------------------- XI
            ch("Glory and Points")
            table(listOf("Source", "Points"),
                listOf("Each village", "1"), listOf("Each tower", "2"),
                listOf("Great Road", "2"), listOf("Mightiest Host", "2"),
                listOf("Each Relic", "1"), listOf("Each Dragon's Waste claimed", "1"),
                *(if (twist == Realm.WAKE) arrayOf(listOf("Slaying the dragon", "2")) else emptyArray()))
            p("**The Great Road** goes to the first player with an unbroken line of **5 or more roads**. A rival who builds a longer line takes it away. A rival building on a corner breaks the line at that point.")
            fig("Five roads in a row along the top of the forest, fields and meadow: the Great Road is yours.",
                Panel(scene {
                    val a = hex(0, 0, TIMBER, 6); val b = hex(1, 0, GRAIN, 5); val c = hex(2, 0, BEASTS, 10)
                    village(a, 5); road(a, 5); road(a, 0); road(b, 5); road(b, 0); road(c, 5); road(c, 2, 1); village(c, 2, 1)
                    mark(b, "Great Road +2", GOLDEN, dy = 0.45f)
                }), height = 140)
            p("**The Mightiest Host** goes to the first player to cast **3 Knights**. A rival who casts more takes it away.")
            p("**Claim a Dragon's Waste.** Be the first to have **2 of your roads** on its sides and **a tower on one of its corners**. It's worth 1 point, and you draw a free spell.")
            fig("Two roads along the wasteland and a tower on its corner claim it.",
                Panel(scene {
                    val w = hex(0, 0, T_WASTE); hex(1, 0, ORE, 8); hex(1, -1, GRAIN, 4); hex(0, 1, BEASTS, 9)
                    road(w, 0); road(w, 1); tower(w, 2); mark(w, "+1 & a spell", GOLDEN, dy = 0.2f)
                }), height = 150)
            warn("Winning", "You win as soon as you reach the points goal **on your own turn**. Your rival's Relics are hidden, so they may have more points than it looks.")

            // ---------------------------------------------------------------- twist
            when (twist) {
                Realm.FOG -> {
                    ch("The Unmapped Realm")
                    drop("Fog always covers this realm. Only the middle of the island is known at the start: about a third of it on Easy, and only an eighth on Extreme. The dragon's waste is always visible.")
                    p("**Exploring.** When your road or village touches a corner of a fogged tile, the tile is revealed and you **take one of its resource**. Uncovering a Dragon's Waste pays nothing.")
                    fig("A road reaching into the mist reveals two tiles and pays one card for each.",
                        Panel(scene {
                            val a = hex(0, 0, TIMBER, 6); hex(1, 0, T_FOG); hex(1, -1, T_FOG, variant = 1); hex(0, -1, BEASTS, 5)
                            village(a, 5); road(a, 5); open(a, 0)
                        }, "Before"),
                        Panel(scene {
                            val a = hex(0, 0, TIMBER, 6); val b = hex(1, 0, ORE, 8); val n = hex(1, -1, MANA, 3); hex(0, -1, BEASTS, 5)
                            village(a, 5); road(a, 5); road(a, 0)
                            mark(b, "+1", GOOD, dy = 0.5f); mark(n, "+1", GOOD, dy = 0.5f)
                        }, "After"))
                    p("**Ruins.** Three to five hidden tiles hold ruins. Whoever uncovers one gets a **free spell**.")
                    p("**Ley stones.** Four violet stones stand on the coast. A stone counts as yours when one of your roads or buildings touches it. **Every two stones you touch give +1 mana** each time you roll.")
                    fig("Your road runs from one ley stone to another: +1 mana on every one of your rolls.",
                        Panel(scene {
                            val a = hex(0, 0, MANA, 9); val b = hex(1, 0, TIMBER, 5)
                            ley(a, 5); ley(b, 1); village(a, 4); road(a, 4); road(a, 5); road(a, 0); road(b, 5); road(b, 0)
                            mark(a, "+1 mana", Color(0xFF6A2FE0), side = 0, dx = 0.1f, dy = 0.75f)
                        }), height = 150)
                }
                Realm.WAKE -> {
                    ch("The Dragon's Wake")
                    drop("Here the dragon is alive and growing. Its **strength** starts at 3, and **every 7 makes it 1 stronger**, up to 6.")
                    bl("**It walks.** Each time a full round ends, the dragon moves to a neighbouring tile, choosing the one that hurts the leader most.",
                        "**It bites harder.** At strength 5 or more, a dragon move steals **2 cards** instead of 1.",
                        "**It can be driven off.** Every Knight you cast wounds it once. When its wounds reach its strength, it leaves for good, and whoever landed the last wound earns **2 points**.",
                        "After it's gone, a 7 still makes big hands discard, but nothing gets moved or stolen.")
                    fig("The dragon walks toward the leader. Two wounds so far against strength 4: two more Knights will drive it off.",
                        Panel(scene {
                            val d = hex(-1, 0, T_WASTE); val a = hex(0, 0, GRAIN, 8); hex(0, -1, TIMBER, 5); hex(-1, 1, ORE, 10)
                            tower(a, 1); village(a, 3); dragon(d, 0.9f); arrow(d, a, color = BAD, dashed = true)
                            mark(d, "Strength 4", BAD, dy = 0.7f); mark(a, "Leader", YOU, dy = 0.65f)
                        }, "It hunts the leader"),
                        Panel(scene {
                            val a = hex(0, 0, ORE, 6); hex(1, 0, GRAIN, 9); hex(0, 1, BEASTS, 4)
                            dragon(a, 0.9f); mark(a, "Wounds 2 of 4", BAD, dy = 0.72f)
                        }, "Knights wound it"))
                    p("**Wild dragons.** From Hard up, fog hides part of the island. Uncovering a wasteland can wake a wild dragon: one on Hard, two on Expert, and no limit on Master and Extreme. Wild dragons block their tiles and walk toward the leader each round. They all flee once the great dragon is driven off.")
                    note("Who leads?", "The dragon judges the lead by points you can see, so hidden Relics don't count. On a tie it hunts you.")
                }
                else -> {
                    ch("Night and Day")
                    drop("The rounds alternate. The first round is **day**, the next is **night**, and so on. The sky over the island shows which one it is.")
                    bl("**At night, crystal spires pay double:** 2 mana per village and 4 per tower.",
                        "**When night falls,** the dragon flies to the leader's most valuable tile. If the leader has a building there, the dragon burns one of their cards. It then stays on that tile and blocks it.")
                    fig("By day a spire pays 1 mana to your village. At night it pays 2, and the dragon raids the leader.",
                        Panel(scene { val m = hex(0, 0, MANA, 9); village(m, 0); mark(m, "+1", GOOD, corner = 0, dy = 0.55f) }, "Day"),
                        Panel(scene { night = true; val m = hex(0, 0, MANA, 9); village(m, 0); mark(m, "+2", GOOD, corner = 0, dy = 0.55f) }, "Night"),
                        Panel(scene { night = true; val a = hex(0, 0, GRAIN, 6); tower(a, 0); dragon(a, 0.8f); mark(a, "−1 card", BAD, dy = 0.72f) }, "Night raid"),
                        height = 130)
                    note("Timing", "If you're about to take the lead, try to do it on a day round. The night raid always goes after the leader.")
                }
            }
            if (twist != Realm.FOG) {
                ch("Fog on Harder Levels")
                drop("From **Hard** up, the outer tiles start hidden. Building next to a hidden tile reveals it. In this game, uncovering a tile pays nothing.")
            }

            // ---------------------------------------------------------------- tips
            ch("Advice from the Old Kings")
            bl(
                "Settle on 6s and 8s, and touch as many different resources as you can.",
                "Point your first roads at your next good village spot.",
                "Build beside a portal that matches what you produce a lot of.",
                "Ore and grain build towers. You'll want them by mid-game.",
                "Send the dragon onto your rival's best tile, especially one with a tower.",
                "Count your rival's points, and remember they may have hidden Relics.",
                when (twist) {
                    Realm.WAKE -> "Stay level with your rival early on: the dragon hunts whoever leads."
                    Realm.NIGHT -> "Use your night mana on roads and villages."
                    else -> "Explore with roads: every tile you reveal pays you a card."
                },
            )
        }
    }

    // =================================================================  Wisteria

    fun wisteria(): Book = book(com.puresudoku.R.drawable.rt_logo_wisteria, Color(0xFF7B4FB8),
        "No dice decide your fortune here. The land pays whoever holds it, and land is held by castle, wall and sword.") {
        ch("The Contest")
        drop("Wisteria is a war for land. You and the computer **claim whole tiles**, build them up from village to tower to castle, and send knights to burn your rival's holdings. There are **no dice**. Every tile you hold pays you at the start of each of your turns.")
        p("Each tile scores its level: **a village is worth 1 point, a tower 2 and a castle 3**. The first player to reach the points goal wins.")
        table(listOf("Level", "Points goal", "Island"),
            listOf("Easy", "12", "19 tiles"), listOf("Medium", "14", "22 tiles"), listOf("Hard", "15", "25 tiles"),
            listOf("Expert", "17", "28 tiles"), listOf("Master", "17", "31 tiles"), listOf("Extreme", "21", "37 tiles"))
        p("Harder levels add more lakes (1 to 5) and barren wastes (up to 2).")
        warn("Other ways to end", "If you lose your last tile and have no army marching, you lose at once. If the game reaches 200 turns, the leader wins.")

        ch("The Land")
        drop("Every land tile pays a fixed **yield** each turn, shown on it:")
        fig("Most tiles are common. Rich and rare tiles pay more, but cost more to claim. Lakes can't be held or crossed.",
            Panel(scene { hex(0, 0, TIMBER, yields = intArrayOf(1, 1, 0, 0, 1)) }, "Common\n1 of 3"),
            Panel(scene { hex(0, 0, GRAIN, yields = intArrayOf(0, 0, 2, 2, 0)) }, "Rich\n2 of 2"),
            Panel(scene { hex(0, 0, MANA, yields = intArrayOf(0, 0, 0, 0, 3)) }, "Rare\n3 of 1"),
            Panel(scene { hex(0, 0, T_WATER) }, "Lake\nno one"),
            height = 100)
        bl("**Barren wastes** can be held and count for points, but pay nothing.",
            "**The Dragon's Lair** sits in the middle. No one can hold it.",
            "**Fog** (from Medium up): only the lair and the tiles around it are visible at first. Taking a tile reveals its neighbours.")

        ch("Setting Up")
        drop("Each player **settles one land tile**, you first. It starts as a village. You can't settle beside the lair or next to your rival.")

        ch("A Turn")
        steps(
            "Income" to "Every tile you hold pays its yield.",
            "Armies march" to "Each of your marching armies moves one tile along its route.",
            "Act" to "Claim land, raise buildings, hire knights, strike, build walls, trade and cast one spell, in any order.",
            "End your turn" to "Every 5, 4 or 3 turns depending on the level, counting both players' turns, the dragon strikes.",
        )

        ch("Claiming Land")
        drop("Claim any free, visible tile **touching one you hold**. It becomes your village.")
        fig("Your village (blue) can claim either glowing tile next to it.",
            Panel(scene {
                val a = hex(0, 0, TIMBER, owner = 0, level = 1); val b = hex(1, 0, GRAIN, glow = Color(0xFFFFF59D)); hex(0, 1, BEASTS, glow = Color(0xFFFFF59D))
                hex(1, -1, ORE, owner = 1, level = 1); hex(-1, 1, T_WATER)
                arrow(a, b, color = YOU)
            }), height = 160)
        p("**The price** is a village's cost (timber, grain, beasts and mana), plus:")
        bl("**For rich tiles,** each resource the tile yields 2 or 3 of adds 1 or 2 more of that resource.",
            "**For every 3 tiles you already hold,** add one more card, cycling timber, ore, grain, beasts, mana.")
        costs(CostRow("Claim a tile", Piece.CLAIM, intArrayOf(1, 0, 1, 1, 1), "Base price. Rich tiles and a big realm cost more."))

        ch("Building Up")
        costs(
            CostRow("Tower", Piece.TOWER, Wisteria.COST_TOWER, "Raise a village. 2 points; holds 2 knights."),
            CostRow("Castle", Piece.CASTLE, Wisteria.COST_CASTLE, "Raise a tower. 3 points; holds 3 knights."),
            CostRow("Knight", Piece.KNIGHT, Wisteria.COST_KNIGHT, "Hire onto a tile with room."),
            CostRow("Wall", Piece.WALL, Wisteria.COST_WALL, "Guards one side of your tile."),
            CostRow("Spell", Piece.SPELL, Wisteria.COST_SPELL, "Draw the top spell."),
        )
        fig("Each level is worth more points and holds more knights.",
            Panel(scene { hex(0, 0, TIMBER, owner = 0, level = 1, knights = 1) }, "Village\n1 pt · 1 knight"),
            Panel(scene { hex(0, 0, ORE, owner = 0, level = 2, knights = 2) }, "Tower\n2 pts · 2 knights"),
            Panel(scene { hex(0, 0, GRAIN, owner = 0, level = 3, knights = 3) }, "Castle\n3 pts · 3 knights"),
            height = 110)

        ch("Knights and War")
        drop("Knights fight in two ways, depending on where they stand.")
        p("**A village's knight strikes at once.** It hits a rival tile touching its village straight away, and the knight is spent.")
        p("**A tower's or castle's garrison marches.** Once it is **full** (2 or 3 knights), you can plot a route for all of them across any land, up to 24 tiles. Lakes block the way, unless the Black Dragon Knight leads them. The army walks one tile at the start of each of your turns and attacks when it reaches the end. The target can be a rival tile, or free land far from your borders.")
        fig("Left: a village knight strikes the rival next door. Right: a full tower garrison marches the long way round.",
            Panel(scene {
                val a = hex(0, 0, TIMBER, owner = 0, level = 1, knights = 1); val b = hex(1, 0, GRAIN, owner = 1, level = 1)
                arrow(a, b, color = YOU)
            }, "Strike"),
            Panel(scene {
                val a = hex(0, 0, ORE, owner = 0, level = 2, knights = 2); val x = hex(1, 0, BEASTS); val y = hex(2, -1, TIMBER); val t = hex(2, 0, GRAIN, owner = 1, level = 1)
                hex(1, 1, T_WATER); arrow(a, x, y, t, color = YOU, dashed = true)
            }, "March"),
            height = 150)
        p("**Each knight that lands a hit** knocks a castle down to a tower, or a tower down to a village, or **burns a village**. Burnt land becomes free. If you can pay its price, you may **claim it at once**.")
        p("**Armies that meet** on the same tile fight. Knights cancel out one for one, and the bigger army marches on with what's left.")

        ch("Walls")
        drop("A wall (timber and mana) guards **one side of a tile you hold**. When an attack comes across that side, the wall **stops the whole attack, then breaks**. You can build a new wall there later.")
        fig("The rival's knight strikes across your walled side. The wall stops the whole attack and breaks.",
            Panel(scene {
                val a = hex(0, 0, TIMBER, owner = 0, level = 2); val b = hex(1, 0, GRAIN, owner = 1, level = 1, knights = 1)
                wall(a, 1, 0); arrow(b, a, color = RIVAL); mark(a, "Held!", GOOD, side = 1, dy = 0.55f)
            }), height = 150)
        p("Walls keep out **knights only**. The dragon smashes any wall in its way. Walls may face a lake too.")

        ch("The Dragon")
        drop("One dragon lives in the lair at the middle of the island. It has **5 health**. Every few turns (counting both players') it flies **straight to the leader's grandest tile** (their best castle, tower or village, the nearest if there are several) and attacks it at once. Lakes and walls don't stop it: **it smashes any wall in its way**.")
        table(listOf("Level", "Dragon strikes every"), listOf("Easy, Medium", "5 turns"), listOf("Hard, Expert", "4 turns"), listOf("Master, Extreme", "3 turns"))
        p("**Every building has health:** a village 1, a tower 2 and a castle 3. Each dragon attack takes **1 health**, and that damage **never heals**. A building keeps its points while it stands, but when its health runs out it is **razed and the land is lost**.")
        bl("The board marks the dragon's **target** with a red ring, and shows each damaged building's remaining health.",
            "It destroys any army standing on its target, then flies back to its lair.",
            "Rival knights still knock a damaged building down a level as usual, and finish it off once its health runs out.",
            "If neither player leads, it stays home.")
        fig("The dragon's three visits to a castle: it loses 1 health each time, and is razed on the third.",
            Panel(scene { val d = hex(0, 0, T_LAIR); val y = hex(1, 0, MANA, owner = 0, level = 3); dragon(d, 0.8f); arrow(d, y, color = BAD); mark(y, "3 to 2", BAD, dy = 0.62f) }, "First visit"),
            Panel(scene { val d = hex(0, 0, T_LAIR); val y = hex(1, 0, MANA, owner = 0, level = 3); dragon(d, 0.8f); arrow(d, y, color = BAD); mark(y, "2 to 1", BAD, dy = 0.62f) }, "Second"),
            Panel(scene { val d = hex(0, 0, T_LAIR); val y = hex(1, 0, MANA); dragon(d, 0.8f); arrow(d, y, color = BAD); mark(y, "Razed!", BAD, dy = 0.0f) }, "Third"),
            height = 110)
        note("Knights can fight it", "A village knight touching the lair can **strike the dragon**, and a full tower or castle garrison can **march to hunt it**. A hunting party follows the dragon if it flies off, as far as land allows. **Each knight wounds it once and always dies**, even the one that lands the killing blow. Only knights can hurt it.")
        fig("The leader's grandest tile is their tower. The dragon flies straight there and smashes the wall in its way.",
            Panel(scene {
                val l = hex(0, 0, T_LAIR); val n = hex(1, 0, GRAIN); val y = hex(2, 0, TIMBER, owner = 0, level = 2)
                hex(0, 1, BEASTS); hex(-1, 0, MANA, owner = 1, level = 1); hex(0, -1, ORE); hex(1, -1, T_WATER); hex(-1, 1, TIMBER)
                rubble(y, 4); dragon(l, 0.9f); arrow(l, n, y, color = BAD, dashed = true); mark(y, "Leader", YOU, dy = 0.62f)
            }), height = 170)

        ch("The Dragon Slayer")
        drop("Whoever's knight lands the **last blow** slays the dragon for good. It never returns, and its slayer wins two prizes:")
        trophies(
            Triple(com.puresudoku.R.drawable.rt_dragon_scale, "The Dragon Scale", "Worn forever: +1 point that can never be lost."),
            Triple(com.puresudoku.R.drawable.rt_black_knight, "The Black Dragon Knight", "A champion who never falls, and flies over lakes."),
        )
        p("**The Black Dragon Knight** waits in reserve. Once per turn you may send him out from **any tile you hold**:")
        bl("**Dark Strike:** he hits a touching rival tile at once, like a village knight, then returns.",
            "**Dark March:** he sets off along a route you plot, **flying over lakes**. If he leaves a tower or castle whose garrison is full, the whole garrison marches with him.",
            "He lands a hit like any knight, but **never dies**: not to walls, battles or anything else. When his army's work is done, he returns to reserve.")
        ch("Spells")
        drop("Spells cost **ore, grain and beasts**. You may cast one per turn, but not on the turn you bought it. The deck holds 10 Rally, 5 Relic, 3 Stonewright, 3 Harvest Moon and 4 Siege Fire.")
        spells(
            Triple(RI.KNIGHT, "Rally", "A free knight joins one of your tiles that has room."),
            Triple(RI.RELIC, "Relic", "A hidden treasure worth 1 point."),
            Triple(RI.SHIELD, "Stonewright", "Raise two walls for free."),
            Triple(RI.MOON, "Harvest Moon", "Take any two resources from the supply."),
            Triple(RI.FIRE, "Siege Fire", "Break one of your rival's walls anywhere."),
        )

        ch("Trading")
        drop("At any time on your turn, trade **4 of one resource for 1 of another** with the bank. There are no portals in Wisteria.")

        ch("Advice from the Old Kings")
        bl("Settle a rich tile with free land around it, and claim every turn early on.",
            "Raise a tower on your richest tile before the dragon arrives.",
            "Don't lead by too much too early: the dragon hunts the leader.",
            "The dragon's damage never heals: spread your points over several tiles, or send knights to end it.",
            "Wall the sides that face rival villages with knights.",
            "Plot long marches that come round an unwalled side.",
            "Castles are worth 3 and take three hits: build them deep inside your land.",
            "Cast Siege Fire just before your army lands.")
    }

    // =================================================================  Kreashun

    fun aegwaspell(): Book = book(com.puresudoku.R.drawable.rt_logo_aegwaspell, Color(0xFF2F6B3A),
        "A ship, a handful of souls and an unknown shore. Everything after that is yours to build, or to lose.") {
        ch("A People of Your Own")
        drop("Aegwaspell is played **alone, against the world**. You land at a port at the foot of a vast land hidden in fog, with six people and a few supplies. Grow them into a great civilization.")
        warn("Winning and losing", "**You win** when every rival civilization's capital has fallen **and** your people number the goal. **You lose** the moment your last person dies, to famine, plague, raids or revolt.")
        table(listOf("Level", "World", "Rivals", "People to win"),
            listOf("Easy", "91 tiles", "2", "35"), listOf("Medium", "91 tiles", "2", "45"), listOf("Hard", "127 tiles", "3", "55"),
            listOf("Expert", "127 tiles", "3", "65"), listOf("Master", "169 tiles", "4", "75"), listOf("Extreme", "169 tiles", "4", "90"))

        ch("Roads and Land")
        drop("You can only build on tiles **your roads reach**. Tap a tile beside your lands and lay a **road** into it: 1 timber, or more into hills, mountains and swamp. Each new road lifts the fog around it, and roads into **ruins** turn up treasure.")
        p("**Each tile holds one building**, and the land decides which: farms on fields, ranches on meadows, fisheries on the coast, lumber mills in forests, quarries in hills and mines in mountains.")

        ch("Buildings")
        bl("**Village** (3 timber, 1 stone): homes for 6. Grow it into a **Town** (4 timber, 4 stone, 2 gold) for 15.",
            "**Farm** (2 timber): 7 food on fields, 5 on plains, times the harvest. Nothing in winter. 2 workers.",
            "**Ranch** (3 timber) and **Fishery** (2 timber): 4 food, 3 in winter. 2 and 1 workers.",
            "**Lumber mill** (1 timber, 1 stone): 3 timber. **Quarry** (2 timber): 3 stone. **Mine** (3 timber, 2 stone): 2 ore and 1 gold.",
            "**Granary** (3 timber, 2 stone): stores 40 more food. **Market** (3 timber, 3 stone): 3 gold.",
            "**Barracks** (4 timber, 3 stone, 1 gold): room for 8 soldiers. **Watchtower** (2 timber, 2 stone): helps fight off raids.",
            "**Temple** (3 stone, 2 gold): lifts the mood every season and eases plague. **Colosseum** (2 timber, 5 stone, 3 gold): roll a die each season; on 4 or more, the games lift the mood a lot.")
        p("**Workers.** Buildings need people at work. If you have too few people, the last buildings stand **idle** and make nothing: food buildings are staffed first.")

        ch("The Seasons")
        steps(
            "Your turn" to "Lay roads, build, train soldiers, scout and march, as long as you can pay.",
            "The harvest dice" to "2 to 4 is a poor year (farms make half), 10 to 12 a bountiful one (half again).",
            "The event card" to "Migrants, plague, bandits, raids, festivals, floods, gold veins, merchants and more.",
            "Eating" to "Every person and soldier eats 1 food. Short of food, people starve. Food beyond your storage spoils.",
            "Growth" to "With food to spare, room in your homes and a good mood, children are born.",
        )

        ch("Stewardship")
        p("Open Stewardship to set rations, taxes and work hours, see the next harvest forecast and read your chronicle. Event cards may ask you to choose an answer before taking another action.")
        p("Select a farm to rest it and restore tired soil. Neighbouring buildings can improve production, and towns need a nearby market or temple to house their full population. Children eat but become workers only after a year.")
        p("Select a building to demolish it: half its construction cost is returned, the crew goes home, and rubble prevents rebuilding until the next season. Tearing down homes lowers mood. The port cannot be demolished.")
        p("After building a town, you may construct each wonder once on suitable land. Wonders require workers and take four working seasons. Their benefits include better food production, mood, defence and housing. Buildings may also require seasonal gold upkeep.")

        ch("The Mood")
        drop("Happiness runs from 0 to 100 and settles toward the middle. Temples, festivals and the colosseum lift it; famine, homelessness and unrest lower it.")
        bl("**60 or more:** your people grow quickly.", "**Under 30:** people leave.", "**Under 10:** they revolt and burn a building.")

        ch("Rivals and War")
        drop("Two to four **rival civilizations** wait beyond the fog. Each has a **strength** that grows over the years, and they raid you from the third year on.")
        p("**Soldiers** cost 2 food, 1 ore and 1 gold each, and come from your people. Barracks hold 8 each. Lay roads until they touch a rival's land, select it, and **attack with every soldier**: your soldiers plus two dice against their strength plus two dice. Win and their capital falls: its people join you and its gold is yours.")

        ch("Advice from the Old Stewards")
        bl("Build a farm on the fields beside your port first.",
            "Store food before winter: farms make nothing then.",
            "Don't build more than you can staff: idle buildings make nothing.",
            "A temple early keeps your people from leaving.",
            "Watchtowers and a few soldiers stop raids and bandits.",
            "Strike a rival while it's still weak: they grow every year.")
    }

    fun aegConquest(): Book = book(com.puresudoku.R.drawable.rt_logo_aeg_conquest, Color(0xFF8E1B24),
        "Land is not given. It is held, tile by tile, by the people you put there.") {
        ch("Conquest and Stewardship")
        drop("Aegwaspell: Conquest is Aegwaspell where **nothing is left to chance but the dice of battle**. You choose who works each building and which tiles your soldiers guard, and every tile is won or lost on the strength standing on it.")
        warn("Winning and losing", "**You win** when every rival capital has fallen **and** your people number the goal. **You lose** when your last person dies, or when a rival **storms your port**.")
        table(listOf("Level", "Rivals", "People to win", "Rivals attack"),
            listOf("Easy", "2", "35", "No"), listOf("Medium", "2", "45", "No"), listOf("Hard", "3", "55", "Yes"),
            listOf("Expert", "3", "65", "Yes"), listOf("Master", "4", "75", "Yes"), listOf("Extreme", "4", "90", "Yes"))

        ch("Workers")
        drop("A new building takes what idle people it can. After that, **you place every worker**: select a building and tap **Add worker** or **Remove worker**. A building makes nothing until its whole crew is there.")
        p("The badge on each building shows its crew, such as **1/2**: green when full, amber when short. The top banner shows how many people stand idle.")

        ch("Soldiers")
        drop("Train soldiers at a barracks: 2 food, 1 ore and 1 gold each, taken from your idle people. They join your **reserve**.")
        p("Select any tile on your roads and tap **Post soldier** to station one there, or **Recall soldier** to bring one back to the reserve.")
        p("**Tile power** flies on a banner over each tile: blue for yours, red for a rival's. It is the soldiers on the tile, **+3** for a watchtower, **+2** for your port and **+2** for a rival capital.")

        ch("Battle, Tile by Tile")
        steps(
            "Choose" to "Select a rival tile that touches one of your guarded tiles, and tap Attack.",
            "Roll" to "Your soldiers on that tile plus a die, against the tile's power plus a die.",
            "Win" to "The tile is yours: survivors march in and a road joins it. Take a capital and that civilization falls; its people join you and its gold is yours.",
            "Lose" to "Half your attackers fall, and you wound theirs.",
        )
        p("**From Hard up, the rivals fight too.** They post soldiers on their borders, spread into free land, and attack any tile, yours or another rival's, where they outnumber the defence. Rivals can conquer each other. A burned building's crew is lost, and if your port falls, so do you.")

        ch("The Port Market")
        drop("While your port stands, ships call. Select your port, pick a good to give and then a good to get: **${AegConquest.TRADE_RATE} of one for 1 of another**, as often as you like. A completed, staffed Lighthouse improves this to **3 for 1**.")

        ch("Everything Else")
        p("Roads, buildings, stewardship, the seasons, event choices and the mood work as in Aegwaspell. Use Stewardship for decrees, forecasts and your chronicle; select a farm to rest it or a building to demolish it. Towns unlock wonders. Your port also brings in 1 timber a season.")

        ch("Advice from the Old Generals")
        bl("Build a lumber mill early, and fill every farm's crew before anything else.",
            "Guard the tiles that touch a rival, not the ones deep inside your land.",
            "A watchtower on the border is worth three soldiers.",
            "Trade your surplus at the port for the stone and ore that barracks and mines need.",
            "Watch the red banners: rivals strike where they outnumber you by two or more.",
            "Let rivals wear each other down, then take the weaker capital.")
    }

    fun eiseriaAshfall(): Book = book(com.puresudoku.R.drawable.rt_logo_eiseria_ash, Color(0xFFB23A12),
        "The mountain does not care whose village stands below it. When it wakes, the whole realm goes hungry.") {
        ch("The Realm Aflame")
        drop("Eiseria: Ashfall is Eiseria with **a volcano in place of the dragon**. You and the computer settle villages on the corners of tiles, lay roads and raise towers. Each turn you roll the dice, and every tile showing that number pays the buildings on its corners. The first to the points goal wins.")
        p("Tiles here belong to no one. When lava buries a tile, **it stops paying everyone** who has a building on it, you and your rival alike.")
        table(listOf("Level", "Points goal"), listOf("Easy and Medium", "10"), listOf("Hard and Expert", "11"), listOf("Master and Extreme", "12"))

        ch("Building")
        costs(
            CostRow("Road", Piece.ROAD, Realm.COST_ROAD, "Joins your corners. 5 or more in a row is the Great Road (2 points)."),
            CostRow("Village", Piece.VILLAGE, Realm.COST_VILLAGE, "On a free corner of your road, two edges from any other building. 1 point."),
            CostRow("Tower", Piece.TOWER, Realm.COST_TOWER, "Raise a village. 2 points, and it collects double."),
            CostRow("Spell", Piece.SPELL, Realm.COST_SPELL, "Draw the top spell."),
        )
        p("Trade 4 of one resource for 1 of another, or better at a portal you've settled. On a 7 nothing pays, and anyone holding more than 7 cards discards half.")

        ch("The Volcano")
        drop("The volcano stands in the middle of the island. **Every time a 7 is rolled, it erupts.**")
        p("**The die decides.** No one knows where the lava will go until it erupts. Then **a second die is rolled**: each face is one of the volcano's six sides, and the lava runs out that way **in a straight row**. Afterwards, glowing arrows show where it ran.")
        p("**The volcano wakes.** At first the lava only reaches the tile beside the volcano. Every other eruption the row runs **one tile further**, up to 6.")
        bl("Lava **buries every fresh tile** in its path. Buildings on its corners survive, but the tile **pays no one** until it recovers.",
            "Lava **burns every road it crosses**, yours or your rival's.",
            "Lava never buries a tile that's still ruined or ashen: it runs over it to reach fresh ground. Wastelands are never buried.")

        ch("From Lava to Riches")
        steps(
            "Ruined" to "For 4 turns the tile pays nothing. It cools a step each turn: fresh lava, a lava pool, cracked lava, then smouldering rock.",
            "Ashen" to "For 1 turn it's a white ashen waste, still paying nothing.",
            "Renewed" to "Then it's reborn with a golden edge: when its number is rolled, every building on it collects one extra. A later eruption can bury it again.",
        )

        ch("Spells")
        drop("Spells cost **ore, grain and beasts**. Cast one per turn, but not on the turn you bought it. The deck holds 14 Knights, 5 Relics, 2 Earthshapers, 2 Harvest Moons and 2 Fireballs.")
        spells(
            Triple(RI.KNIGHT, "Knight", "Steal a card from your rival. Three or more Knights, and the most, earns the Mightiest Host (2 points)."),
            Triple(RI.RELIC, "Relic", "A hidden treasure worth 1 point."),
            Triple(RI.EARTH, "Earthshaper", "Build two roads for free."),
            Triple(RI.MOON, "Harvest Moon", "Take any two resources from the supply."),
            Triple(RI.FIRE, "Fireball", "Burn one of your rival's roads that has a loose end."),
        )

        ch("Advice from the Ash-Wardens")
        bl("Tiles between the volcano's six rows are never hit: settle on their corners.",
            "Share tiles with your rival: when lava takes them, you both lose.",
            "Keep your roads off the six rows out of the volcano.",
            "Renewed land is rich: build on its corners.",
            "Spread out: one roll can silence a whole row of tiles.")
    }

    fun ashfall(): Book = book(com.puresudoku.R.drawable.rt_logo_ashfall, Color(0xFFB23A12),
        "The mountain gives and the mountain takes. What it buries today, it pays back richer tomorrow, to whoever is still standing.") {
        ch("The Eruption")
        drop("Ashfall is Wisteria on a living island. You claim tiles, raise villages, towers and castles, and send knights against your rival, exactly as in Wisteria. But **there is no dragon**. In the middle stands **a volcano**, and every few turns it erupts, burying whatever lies in the path of its lava.")
        p("Each tile scores its level: **a village 1 point, a tower 2, a castle 3**. The goal is **half again Wisteria's**, so games are long on purpose. You may have to wait for the lava to build new land before you can reach it.")
        table(listOf("Level", "Points goal", "Island"),
            listOf("Easy", "18", "19 tiles"), listOf("Medium", "22", "22 tiles"), listOf("Hard", "23", "25 tiles"),
            listOf("Expert", "25", "28 tiles"), listOf("Master", "26", "31 tiles"), listOf("Extreme", "31", "37 tiles"))
        warn("Other ways to end", "Lose your last tile and you lose. If the **sea and the lakes are all filled** so the island can grow no more, the game is **locked** and the points leader wins. After 300 turns, the leader wins too.")

        ch("The Island and the Sea")
        drop("The island sits in **a ring of open sea**. No one can hold or cross the sea, but lava that reaches it cools into **brand-new land**.")
        bl("**The volcano** sits in the middle. No one can hold or cross it.",
            "**Ruined land** (lava, a lava pool, cracked lava or smouldering rock) can't be held or crossed. A number shows the turns until it cools.",
            "**The ashen waste** can be claimed, but pays nothing yet. Its number counts down the turns until it's renewed.",
            "**Renewed land** looks like ordinary land with a golden edge, and is the best on the island: it pays its yield **plus 1 more of its main resource** every turn.",
            "There is **no fog**: the whole island is always in view.")

        ch("The Volcano")
        drop("The volcano erupts **at the end of every turn**, yours and your rival's.")
        p("**The die decides.** No one knows where the lava will go until it erupts. Then **a die is rolled**: each face is one of the volcano's six sides, and the lava runs out that way **in a straight row**. Afterwards, glowing arrows show where it ran, and the die shows what was rolled.")
        p("**The volcano wakes.** At first the lava only reaches the tile beside the volcano. Every other eruption the row runs **one tile further**. Lava with length to spare when it reaches the coast spills into the sea beside the row's end.")
        bl("Lava **buries every fresh tile in its path**: villages, towers and castles are lost, and the land becomes molten lava.",
            "**Lava never buries land that is still ruined or ashen.** Flows run across it, unharmed, to reach fresh ground, and only fresh tiles count toward a flow's length.",
            "Knights on a buried tile **flee to a touching tile of yours** that has room. Any that can't are lost, and so is any army standing there.",
            "Where a flow reaches **the sea or a lake**, it stops and the water becomes **new land**.",
            "Lava **never buries a player's last tile**: it cools at the edge.",
            "Lava never buries land that is **still ruined or ashen**: it runs over it.")

        ch("From Lava to Riches")
        steps(
            "Ruined" to "For 4 turns no one can hold or cross the tile. It cools a step each turn: fresh lava, a lava pool, cracked lava, then smouldering rock.",
            "Ashen" to "Then for 1 turn it's a white ashen waste. Anyone may claim it, but it pays nothing yet.",
            "Renewed" to "Then it's reborn as ordinary land, with a golden edge: it pays its yield plus 1 more of its main resource, every turn. Renewed land is fresh ground again, so a later eruption can bury it.",
        )
        p("Being forced out today means rich land later. New land from the sea goes through the same steps.")

        ch("Turning the Lava")
        drop("You can't choose where the lava goes, but you can stand in its way.")
        p("**Walls stop lava.** Walls here are heavy stone and cost more: 2 timber, 2 ore and 1 mana. **No wall may face the volcano itself**, so the lava can always leave it. When lava reaches a side with a wall on it, the wall **breaks** and the lava stops there, sparing the tile and everything beyond it. A wall still keeps out knights as in Wisteria.")
        p("**Quench** cools one of your tiles so the next eruption can't bury it: the lava stops there.")

        ch("Land, Buildings and Knights")
        drop("Everything else works as in Wisteria. Each player settles one tile to start, but not beside the volcano.")
        costs(
            CostRow("Claim a tile", Piece.CLAIM, intArrayOf(1, 0, 1, 1, 1), "Any free tile touching yours, ash included. Rich tiles and a big realm cost more."),
            CostRow("Tower", Piece.TOWER, Wisteria.COST_TOWER, "Raise a village. 2 points; holds 2 knights."),
            CostRow("Castle", Piece.CASTLE, Wisteria.COST_CASTLE, "Raise a tower. 3 points; holds 3 knights."),
            CostRow("Knight", Piece.KNIGHT, Wisteria.COST_KNIGHT, "Hire onto a tile with room."),
            CostRow("Wall", Piece.WALL, Wisteria.ASH_COST_WALL, "Guards one side of your tile, against knights and lava. Never on a side facing the volcano."),
            CostRow("Spell", Piece.SPELL, Wisteria.COST_SPELL, "Draw the top spell."),
        )
        p("A village's knight strikes a touching rival tile at once. A full tower or castle garrison marches along a route you plot. Lava, the sea and the volcano block marches.")

        ch("Spells")
        drop("Spells cost **ore, grain and beasts**. You may cast one per turn, but not on the turn you bought it. The deck holds 10 Rally, 4 Relic, 5 Quench, 4 Harvest Moon and 2 Siege Fire.")
        spells(
            Triple(RI.KNIGHT, "Rally", "A free knight joins one of your tiles that has room."),
            Triple(RI.RELIC, "Relic", "A hidden treasure worth 1 point."),
            Triple(RI.LEY, "Quench", "Cool one of your tiles: the next eruption can't bury it."),
            Triple(RI.MOON, "Harvest Moon", "Take any two resources from the supply."),
            Triple(RI.FIRE, "Siege Fire", "Break one of your rival's walls, to let knights or lava in."),
        )

        ch("Trading")
        drop("At any time on your turn, trade **4 of one resource for 1 of another** with the bank.")

        ch("Advice from the Ash-Wardens")
        bl("Tiles between the volcano's six rows are never hit: they're the safest place for castles.",
            "Claim the ashen waste early. It's cheap now and rich once renewed.",
            "As the rows lengthen, they reach the coast and build new land: be ready to claim it.",
            "A wall on the side facing the volcano along a row stops the lava there.",
            "Quench your best castle when a flow is heading for it.",
            "The goal is high: plan for a long game, and rebuild as the island changes.")
    }

    fun kreashun(): Book = book(com.puresudoku.R.drawable.rt_logo_kreashun, Color(0xFF8D4F1F),
        "Stone by stone you ring the land. Close the last gap and it is yours. Then hold it against the knights at your gate.") {
        ch("The Siege")
        drop("Kreashun joins Eiseria's dice and villages with Wisteria's walls and knights. **Wall in a tile on every side to capture it.** Then raise a castle on it, hire knights, and storm your rival's land.")
        p("**Points:** 1 for each tile you hold, plus 1 for each rival castle you fell, plus any **Relics** you hold. There is **no dragon**.")
        table(listOf("Level", "Points goal", "Island"),
            listOf("Easy", "6", "19 tiles"), listOf("Medium", "7", "22 tiles"), listOf("Hard", "9", "25 tiles"),
            listOf("Expert", "10", "28 tiles"), listOf("Master", "11", "31 tiles"), listOf("Extreme", "13", "37 tiles"))
        warn("You can also lose", "Once you've scored, falling back to **0 points** loses the game. If the game reaches 400 turns, whoever has more points wins.")

        ch("The Land")
        drop("The island is the same as Eiseria's: five resource tiles, each with a number token, and a few Dragon's Wastes.")
        terrains()
        p("Wastelands pay nothing, but they **can be captured**, and each counts as a point.")

        ch("Your Pieces")
        bl("**Villages** go on corners and earn from the tiles they touch. You may have up to **5**.",
            "**Towers** replace a village and earn twice as much. You may have up to **4**.",
            "**Walls** go on edges. They do the job of Eiseria's roads and also close tiles in. There's no limit on walls.",
            "**Castles** and **knights** stand on tiles you've captured.")
        fig("Your village with two walls beside it, and a rival tower with its own wall.",
            Panel(scene {
                val a = hex(0, 0, TIMBER, 6); val b = hex(1, 0, GRAIN, 8); hex(0, 1, ORE, 5)
                village(a, 2); wall(a, 1, 0); wall(a, 2, 0); tower(b, 0, 1); wall(b, 0, 1)
            }), height = 160)

        ch("Setting Up")
        drop("Each player places **two villages, each with a wall touching it**. The order snakes: **you, rival, rival, you**. Your **second** village pays one card for each resource tile it touches.")

        ch("A Turn")
        steps(
            "Roll the dice" to "Tiles showing that number pay both players. **A 7 pays nothing**: no one discards and nothing is stolen.",
            "Act" to "Build, raise castles, hire knights, strike, trade, buy spells and cast one spell, in any order.",
            "End your turn" to "Your rival takes their turn.",
        )
        dice()

        ch("The Harvest")
        drop("A village earns 1 card and a tower earns 2, as in Eiseria. On a tile **you hold**, your buildings on its corners earn **double**.")
        fig("The same 8 pays twice as much once the tile is yours.",
            Panel(scene { val g = hex(0, 0, GRAIN, 8); village(g, 0); tower(g, 3); mark(g, "+1", GOOD, corner = 0, dy = 0.5f); mark(g, "+2", GOOD, corner = 3, dy = -0.48f) }, "Not held"),
            Panel(scene { val g = hex(0, 0, GRAIN, 8, owner = 0); village(g, 0); tower(g, 3); mark(g, "+2", GOOD, corner = 0, dy = 0.5f); mark(g, "+4", GOOD, corner = 3, dy = -0.48f) }, "Held: double!"),
            height = 130)

        ch("Walls and Villages")
        costs(
            CostRow("Wall", Piece.WALL, Kreashun.COST_WALL, "On an open edge joined to your village, wall or tile."),
            CostRow("Village", Piece.VILLAGE, Kreashun.COST_VILLAGE, "On a corner touching one of your walls."),
            CostRow("Tower", Piece.TOWER, Kreashun.COST_TOWER, "Replaces one of your villages."),
        )
        p("A wall must touch one of your **villages or towers**, continue from one of your **walls** through an empty corner, or lie along a **tile you hold**. A rival's building on a corner blocks the way.")
        p("Villages follow the **distance rule**: there must be at least one empty corner between any two buildings.")
        fig("From your village and wall, any glowing edge can take your next wall.",
            Panel(scene {
                val a = hex(0, 0, BEASTS, 9); val b = hex(1, 0, TIMBER, 4); hex(0, 1, GRAIN, 10)
                village(a, 2); wall(a, 1, 0); open(a, 2); open(b, 3); open(a, 0); open(b, 5)
            }), height = 160)

        ch("Capturing Land")
        drop("A tile is captured the moment **all six of its sides are closed**. Walls of either colour count, and so does rubble. Coast sides need walls too. **Whoever builds the closing wall takes the tile**, even if most of the walls are the rival's.")
        fig("A tile on 5 of 6 is a gift to whoever builds the last wall. The board shows each free tile's count.",
            Panel(scene { val h = hex(0, 0, GRAIN, 6); wall(h, 0, 1); wall(h, 1, 1); wall(h, 2, 0); wall(h, 3, 0); mark(h, "4/6", INK, dy = -0.5f) }, "4 of 6"),
            Panel(scene { val h = hex(0, 0, GRAIN, 6); wall(h, 0, 1); wall(h, 1, 1); wall(h, 2, 0); wall(h, 3, 0); wall(h, 4, 0); open(h, 5); mark(h, "5/6", Color(0xFFB8860B), dy = -0.5f) }, "5 of 6: danger"),
            Panel(scene { val h = hex(0, 0, GRAIN, 6, owner = 0); for (k in 0..5) wall(h, k, if (k < 2) 1 else 0); mark(h, "+1", GOOD, dy = -0.5f) }, "Closed: yours"),
            height = 115)
        note("Two at once", "A wall between two tiles closes a side of both. One wall can capture both tiles.")

        ch("Castles")
        costs(
            CostRow("Castle", Piece.CASTLE, Kreashun.COST_CASTLE, "On a tile you hold. 3 hit points."),
            CostRow("Half-price castle", Piece.CASTLE, Kreashun.COST_CASTLE_HALF, "On a tile taken from a felled castle (marked ½)."),
        )
        p("A castle protects its tile: knights must knock off all **3 hit points** before they can take it. It also lets the tile hold **3 knights** instead of 2.")

        ch("Knights and Strikes")
        costs(CostRow("Knight", Piece.KNIGHT, Kreashun.COST_KNIGHT, "Hire onto a tile you hold: 2 per tile, 3 with a castle."))
        drop("Spend a knight to **strike a rival tile touching** the knight's tile. Each strike does one thing, in this order:")
        steps(
            "Break the wall" to "If there's a wall on the side between the two tiles, it breaks into **rubble**, whoever owns it. Rubble is never rebuilt, but it still counts as a closed side.",
            "Hit the castle" to "If the side is open and the tile has a castle, the castle loses **1 hit point**.",
            "Take the tile" to "If there's no castle, or the castle falls, the tile is **yours**. Felling a castle earns you **1 extra point** that you keep.",
        )
        fig("Your two knights break the wall, then pound the castle. Three more hits fell it, and the tile is yours with a half-price rebuild.",
            Panel(scene {
                val a = hex(0, 0, TIMBER, owner = 0, knights = 2); val b = hex(0, 1, ORE, owner = 1, castle = 3)
                wall(a, 2, 1); arrow(a, b, color = YOU)
            }, "1 · Wall breaks"),
            Panel(scene {
                val a = hex(0, 0, TIMBER, owner = 0, knights = 1); val b = hex(0, 1, ORE, owner = 1, castle = 2)
                rubble(a, 2); arrow(a, b, color = YOU)
            }, "2 · Castle hit"),
            Panel(scene {
                hex(0, 0, TIMBER, owner = 0); val b = hex(0, 1, ORE, owner = 0, half = true)
                rubble(b, 5); mark(b, "+2", GOOD, dy = 0.55f)
            }, "Felled: tile and +1"),
            height = 170)

        ch("Spoils of War")
        drop("When you take a tile by force, everything of your rival's on it changes sides: their **walls** on its sides, their **villages and towers** on its corners, and the **knights** standing on it.")
        fig("The rival's walls, village and knight all turn blue.",
            Panel(scene {
                val h = hex(0, 0, BEASTS, 9, owner = 1, knights = 1)
                wall(h, 0, 1); wall(h, 1, 1); wall(h, 2, 0); wall(h, 3, 1); rubble(h, 4); wall(h, 5, 1); village(h, 0, 1)
            }, "Before"),
            Panel(scene {
                val h = hex(0, 0, BEASTS, 9, owner = 0, knights = 1)
                for (k in listOf(0, 1, 2, 3, 5)) wall(h, k, 0); rubble(h, 4); village(h, 0, 0)
            }, "After"),
            height = 125)

        ch("Spells")
        drop("Spells cost **ore, grain and beasts**. You may cast one per turn, but not on the turn you bought it. The deck holds 10 Rally, 3 Relic, 3 Stonewright, 3 Harvest Moon and 4 Siege Fire.")
        spells(
            Triple(RI.KNIGHT, "Rally", "A free knight joins one of your tiles that has room."),
            Triple(RI.RELIC, "Relic", "A hidden treasure worth 1 point. It doesn't count as land: losing every tile still loses."),
            Triple(RI.SHIELD, "Stonewright", "Raise two walls for free. If one closes a tile, you capture it."),
            Triple(RI.MOON, "Harvest Moon", "Take any two resources from the supply."),
            Triple(RI.FIRE, "Siege Fire", "Knock 1 health off a rival castle that has 2 or more left. Only knights can fell it."),
        )

        ch("Trading")
        drop("At any time on your turn, trade **4 of one resource for 1 of another** with the bank. There are no portals.")

        ch("Advice from the Old Kings")
        bl("Settle on 6s and 8s, touching tiles you can wall in.",
            "Count the sides: never leave a tile one wall from closed where your rival can reach it.",
            "Walls between tiles close a side of both. Use them to work on two tiles at once.",
            "Towers on tiles you hold pay four times over.",
            "Build castles on the tiles that face your rival.",
            "Hire knights in pairs: one breaks the wall and the next takes the tile.",
            "Strike bare tiles before your rival can put castles on them.",
            "Watch your rival's last tiles: taking them all can win the game outright.")
    }

    // =================================================================  Veilguard

    fun veilguard(): Book = book(com.puresudoku.R.drawable.rt_logo_veilguard, Color(0xFF8E1B2C),
        "Every banner may be a lie, and every crown may be a decoy. Trust only what your spies have seen.") {
        ch("The Hidden War")
        drop("Veilguard is a game of secrets on the Eiseria islands. Each round you and the rival give **secret orders**, then turn them face up and carry them out **at the same time**. Nobody sees what the other plans until it happens.")
        warn("Take the Crown", "Each player hides a **Crown** on one of their tiles. **Take the tile holding the rival's Crown and you win at once.** There is no points goal. If you lose all your land, you lose too.")
        p("Each tile you hold scores its level: **a village 1 point, a tower 2, a castle 3**. There is **no round limit**: the war goes on until a Crown falls. Points decide who the Veilnight Thief hunts.")
        table(listOf("Level", "Thief steals", "Thief wrecks"),
            listOf("Easy", "1 a round", "every 5 rounds"), listOf("Medium", "1 a round", "every 5 rounds"), listOf("Hard", "2 a round", "every 4 rounds"),
            listOf("Expert", "2 a round", "every 4 rounds"), listOf("Master", "2 a round", "every 3 rounds"), listOf("Extreme", "3 a round", "every 3 rounds"))

        ch("The Land")
        drop("The land works as in Wisteria: **every tile you hold pays its yield** at the start of each round, with no dice. Lakes can't be held or crossed. In the middle sits **the thief's hideout**, which no one can hold.")

        ch("Setting Up")
        drop("You settle a tile, then the rival settles two, then you settle your second. Your **first tile holds your Crown** and starts with **2 knights and 1 decoy**. Your second starts with 1 knight. You can't settle beside the hideout or next to the rival.")

        ch("Banners and Decoys")
        drop("Armies are shown as **banners**. You see your own banners as knights + decoys. Of the rival's you only see **how many**.")
        fig("Your tile shows 2 knights + 1 decoy. The rival's shows 3 banners: they could all be knights, or all decoys.",
            Panel(scene { val a = hex(0, 0, TIMBER, owner = 0, level = 1, knights = 2); mark(a, "2+1", YOU, dy = 0.62f) }, "Yours"),
            Panel(scene { val b = hex(0, 0, ORE, owner = 1, level = 2); mark(b, "3 banners?", RIVAL, dy = 0.62f) }, "The rival's"),
            height = 110)
        bl("**Knights** fight. **Decoys** are cheap and do nothing at all, except look like knights.",
            "When banners fight, every decoy in the battle is exposed and lost.",
            "A tile holds 4 banners as a village, 6 as a tower and 8 as a castle.")

        ch("Secret Orders")
        drop("Each round give up to **3 orders**. Tap a tile and choose; tap an order to take it back. Each is paid for when you give it. Then press **Reveal orders**.")
        costs(
            CostRow("March", Piece.KNIGHT, null, "Every banner on the tile moves to a touching tile: reinforce, take free land, or attack."),
            CostRow("Knight", Piece.KNIGHT, Veilguard.COST_KNIGHT, "A new knight on the tile."),
            CostRow("Decoy", Piece.KNIGHT, Veilguard.COST_DECOY, "A decoy banner on the tile."),
            CostRow("Tower", Piece.TOWER, Veilguard.COST_TOWER, "Raise a village."),
            CostRow("Castle", Piece.CASTLE, Veilguard.COST_CASTLE, "Raise a tower."),
            CostRow("Wall", Piece.WALL, Veilguard.COST_WALL, "Guards one side of your tile."),
            CostRow("Fortify", Piece.WALL, Veilguard.COST_FORTIFY, "+2 defence on the tile this round."),
            CostRow("Claim", Piece.CLAIM, null, "Take a free tile touching yours, as in Wisteria. If both players claim it, both fail."),
            CostRow("Scout", Piece.SPELL, Veilguard.COST_SCOUT, "See a rival tile's true knights and decoys, and whether the Crown is there."),
            CostRow("Crown", Piece.CASTLE, null, "Slip your Crown to a touching tile you hold, unseen. It can only creep one tile a round."),
        )
        p("Buying a spell and casting one **don't use an order**. See Spells.")

        ch("The Reveal")
        drop("When the orders are turned up, they happen in this order:")
        steps(
            "Crowns move" to "Before anything else. A Crown can slip away from an attack in the same round.",
            "Walls and fortifications" to "Walls rise and fortified tiles brace.",
            "Siege Fire" to "Burns banners before anyone moves.",
            "Spies report" to "Each scout sees the tile as it is now.",
            "Armies march" to "All at once. Armies crossing the same border meet on the road first. A stack that marches leaves its tile empty.",
            "Claims" to "Free land is claimed.",
            "Knights, decoys and buildings" to "Rally and Harvest Moon pay out, then new banners and buildings appear, if the tile is still yours.",
            "The thief moves" to "Then Veil takes effect, and the new round's income is paid.",
        )

        ch("Battle")
        drop("An attack **wins only if its knights outnumber the defence**: the defending knights, plus **1 for a tower**, **2 for a castle**, **2 if fortified**, and **2 if it comes through a wall** (the wall breaks).")
        fig("4 knights against a castle with 1 knight: 1 + 2 = 3 defence. The attack wins with 3 knights left.",
            Panel(scene {
                val a = hex(0, 0, TIMBER, owner = 0, level = 1, knights = 3); val b = hex(1, 0, GRAIN, owner = 1, level = 3, knights = 1)
                arrow(a, b, color = YOU); mark(a, "4", YOU, dy = 0.62f); mark(b, "3 defence", RIVAL, dy = 0.62f)
            }), height = 150)
        bl("**If it wins,** the defenders fall and the tile is taken, dropping one level. The attackers that outlived the defenders move in.",
            "**If it fails,** every attacking knight falls, and they take that many defenders with them.",
            "**Marching into free land** takes it for free, if a knight gets there. Decoys alone just go home.")

        ch("The Veilnight Thief")
        trophies(Triple(com.puresudoku.R.drawable.rt_thief, "The Veilnight Thief", "Steps toward the leader every round, robbing and wrecking whatever land he stands on."))
        drop("The thief starts in the hideout. **Every round he takes one step** toward the leader's richest tile, ringed on the board. Whenever he stands on someone's land, he **steals** from them. **Every few rounds** (5 on Easy and Medium, 4 on Hard and Expert, 3 on Master and Extreme) he also **sabotages the building** he stands on; the board counts down to his next sabotage.")
        p("**Every building has health:** a village 1, a tower 2 and a castle 3. Each sabotage takes **1 health**, and the damage **never heals**. A building keeps its points while it stands, but at 0 health it is **razed and the land is lost**. If your Crown was there, it flees to your strongest tile.")
        bl("He **sneaks past walls** without breaking them.",
            "He has **4 health**. March knights onto his tile to fight him: **each knight wounds him once, and dies**.",
            "If neither player leads, he waits.")

        ch("The Thief's Dagger")
        drop("Whoever lands the last blow wins two prizes:")
        trophies(
            Triple(com.puresudoku.R.drawable.rt_dagger, "The Thief's Dagger", "Worn forever: +1 point that can never be lost."),
            Triple(com.puresudoku.R.drawable.rt_thief, "The Thief, your ally", "He never dies, and robs and spies on your rival."),
        )
        p("From then on he works for you. Each round he steps toward your rival's richest tile, and when he stands on their land he **steals for you** and **spies on the tile**, showing you its true banners and whether the Crown hides there.")

        ch("Spells")
        drop("Spells cost **ore, grain and beasts**, and buying one is not an order. Cast **one spell a round**, chosen in secret with your orders. It takes effect at the reveal. You can't cast a spell in the round you bought it, and you can take a spell back until you reveal. The deck holds 8 Rally, 2 Relic, 3 Harvest Moon, 4 Siege Fire and 4 Veil.")
        spells(
            Triple(RI.KNIGHT, "Rally", "A free knight joins one of your tiles at the reveal."),
            Triple(RI.RELIC, "Relic", "A hidden treasure worth 1 point. The rival sees only a + beside your score."),
            Triple(RI.MOON, "Harvest Moon", "Take any two resources at the reveal."),
            Triple(RI.FIRE, "Siege Fire", "Burn up to 2 banners on a rival tile next to yours, before anyone marches. Knights and decoys burn alike."),
            Triple(RI.LEY, "Veil", "The rival forgets everything it has learned about you: its spy reports and where it saw your Crown."),
        )

        ch("Trading")
        drop("At any time while giving orders, trade **4 of one resource for 1 of another** at the shadow market.")

        ch("Advice from the Spymasters")
        bl("Never leave your Crown's tile bare beside the rival.",
            "Stack decoys where your Crown isn't, to draw the rival's knights.",
            "Scout the rival's biggest stack: it's either guarding the Crown, or bluffing.",
            "A stack that marches leaves its tile empty: strike where the rival just left.",
            "Move your Crown the round after a rival spy is seen, or cast Veil.",
            "Siege Fire the tile you're about to storm.",
            "The thief robs and wrecks the leader: kill him early and he robs for you.")
    }
}
