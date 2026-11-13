package com.puresudoku.core.board

import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

/** What the stewardship rules need from an Aegwaspell game, in either mode. */
interface AegLand {
    val map: RealmGrid
    val H: Int
    val diff: Int
    val terrain: IntArray
    val building: IntArray
    val res: IntArray
    var pop: Int
    var happiness: Int
    val season: Int
    val capital: Int
    val log: ArrayList<String>
    fun connected(): BooleanArray
    /** Buildings with their crew at work. */
    fun staffed(): BooleanArray
    /** Soldiers who can fight off bandits. */
    fun armed(): Int
    fun loseSoldiers(n: Int)
    /** The strongest rival grows, after you refuse its envoy. */
    fun prideRival()
    /** A building's crew goes home (demolished or left to rest). */
    fun sendHome(h: Int)
}

/**
 * The stewardship rules shared by both Aegwaspell games: decrees, choices on the event cards, demolishing, tired soil,
 * children, town needs, neighbour bonuses, upkeep, wonders, the harvest forecast and the chronicle of your years.
 */
class AegStewardship(private val g: AegLand) {
    companion object {
        private val K = Aegwaspell

        // wonders: building types 16 to 21 in both games
        const val LIGHTHOUSE = 16; const val LIBRARY = 17; const val SKY_TEMPLE = 18; const val GARDENS = 19; const val CITADEL = 20; const val PALACE = 21
        val WONDERS = LIGHTHOUSE..PALACE
        val WONDER_NAME = listOf("Lighthouse", "Great Library", "Sky Temple", "Hanging Gardens", "Grand Citadel", "Royal Palace")
        val WONDER_COST = arrayOf(
            intArrayOf(0, 8, 12, 0, 8), intArrayOf(0, 10, 10, 0, 12), intArrayOf(0, 4, 16, 0, 10),
            intArrayOf(0, 12, 8, 0, 8), intArrayOf(0, 8, 16, 4, 6), intArrayOf(0, 12, 12, 0, 15),
        )
        val WONDER_WORKERS = intArrayOf(2, 3, 2, 3, 2, 2)
        val WONDER_LAND: Array<Set<Int>> = arrayOf(
            setOf(K.COAST), setOf(K.PLAINS, K.MEADOW, K.HILLS), setOf(K.HILLS, K.MOUNTAIN, K.PLAINS),
            setOf(K.FIELDS, K.MEADOW, K.PLAINS), setOf(K.HILLS, K.MOUNTAIN), setOf(K.PLAINS, K.FIELDS, K.MEADOW, K.COAST),
        )
        val WONDER_EFFECT = listOf(
            "Every fishery makes 2 more food and your port 2 more; Conquest port trades improve to 3:1" , "Poor harvests count as fair, and +2 mood each season",
            "+5 mood each season, and plague kills no one", "Every farm makes 2 more food, and more children are born",
            "Stronger defences against raids, and room for 8 more soldiers", "Homes for 20, double taxes, and +3 mood each season",
        )
        const val WONDER_SEASONS = 4

        /** Gold each building costs to keep, every season. */
        fun upkeep(b: Int) = when (b) { K.TOWN, K.BARRACKS, K.TEMPLE -> 1; K.COLOSSEUM -> 2; else -> 0 }

        // decrees
        const val RATIONS = 0; const val TAXES = 1; const val HOURS = 2
        val DECREE = listOf("Rations", "Taxes", "Work hours")
        val LEVELS = listOf(listOf("Half", "Full", "Feast"), listOf("Low", "Normal", "High"), listOf("Normal", "Long"))
        val LEVEL_HINT = listOf(
            listOf("people eat half, mood −5, no births", "people eat 1 food each", "people eat 1½, mood +4, more births"),
            listOf("no taxes, mood +2", "1 gold for every 10 people", "1 gold for every 5 people, mood −5"),
            listOf("as usual", "every working building makes 1 more, mood −4"),
        )

        /** Event cards that ask you to choose: title, question, and the two answers. */
        val CHOICES: Map<Int, List<String>> = mapOf(
            2 to listOf("Migrants", "Newcomers ask to settle in your lands.", "Welcome them", "Hire them for a season"),
            3 to listOf("Plague", "Sickness spreads through your homes.", "Quarantine the sick", "Let it run its course"),
            4 to listOf("Bandits", "Bandits demand payment on your roads.", "Pay them off", "Fight them"),
            5 to listOf("Merchant caravan", "A caravan stops at your port.", "Sell food", "Buy timber and stone"),
            6 to listOf("Festival", "Your people ask for a festival.", "Hold a feast", "Save the food"),
            13 to listOf("Envoy's demand", "A rival envoy demands tribute.", "Pay 4 gold", "Refuse"),
            18 to listOf("Unrest", "Your people grow restless.", "Hear their grievances", "Keep order"),
        )
        private const val REC = 7
    }

    /** Seasons until a demolished tile's rubble is cleared. */
    val rubble = IntArray(g.H)
    /** How tired each farm's soil is, 0 to 12: every 4 is one less food. */
    val tired = IntArray(g.H)
    /** Farms left to rest: they make nothing, and their soil recovers. */
    val fallow = BooleanArray(g.H)
    /** Seasons of building left on a wonder (0: finished). */
    val works = IntArray(g.H)
    /** Children by age in seasons: they eat, but work only once grown. */
    val kids = IntArray(4)
    val decree = intArrayOf(1, 1, 0)
    /** Next season's harvest dice, already rolled (0: not yet). */
    val forecast = intArrayOf(0, 0)
    /** The event card waiting for your choice (-1: none). */
    var choice = -1
    /** This year's most notable moment, for the chronicle. */
    var yearNote = -1
    /** One record a year: year, people, tiles, buildings, mood, wonders, note. */
    val chronicle = ArrayList<Int>()

    private val winter get() = g.season % 4 == 3
    fun children() = kids.sum()
    fun people() = g.pop + children()

    // ---------------------------------------------------------------- buildings and land

    fun isWonder(b: Int) = b in WONDERS
    fun wonderAt(h: Int) = isWonder(g.building[h])
    /** Wonders finished and at work. */
    fun activeWonders(st: BooleanArray = g.staffed()): BooleanArray {
        val out = BooleanArray(WONDERS.count())
        for (h in 0 until g.H) if (isWonder(g.building[h]) && works[h] == 0 && st[h]) out[g.building[h] - LIGHTHOUSE] = true
        return out
    }
    fun active(w: Int, st: BooleanArray = g.staffed()) = activeWonders(st)[w - LIGHTHOUSE]
    fun wondersDone() = (0 until g.H).count { isWonder(g.building[it]) && works[it] == 0 }

    /** A town needs a temple or a market within two tiles to house all 15. */
    fun townServed(h: Int): Boolean {
        val staffed = g.staffed()
        return (0 until g.H).any { o -> (g.building[o] == K.TEMPLE || g.building[o] == K.MARKET ||
            (g.building[o] == SKY_TEMPLE && works[o] == 0 && staffed[o])) && hexDist(o, h) <= 2 }
    }
    fun housingOf(h: Int): Int = when (val b = g.building[h]) {
        K.TOWN -> if (townServed(h)) 15 else 8
        PALACE -> if (works[h] == 0 && g.staffed()[h]) 20 else 0
        else -> if (b < K.HOUSING.size) K.HOUSING[b] else 0
    }
    fun housing() = (0 until g.H).sumOf { housingOf(it) }

    private fun hexDist(a: Int, b: Int): Int {
        val dq = g.map.hexQ[a] - g.map.hexQ[b]; val dr = g.map.hexR[a] - g.map.hexR[b]
        return (kotlin.math.abs(dq) + kotlin.math.abs(dr) + kotlin.math.abs(dq + dr)) / 2
    }
    private fun beside(h: Int, vararg types: Int) = g.map.hexNbrs[h].any { g.building[it] in types }
    private fun besideTemple(h: Int): Boolean {
        val staffed = g.staffed()
        return g.map.hexNbrs[h].any { g.building[it] == K.TEMPLE ||
            (g.building[it] == SKY_TEMPLE && works[it] == 0 && staffed[it]) }
    }

    /** Wonders you may start here: each once, and only once you have a town. */
    fun wondersFor(h: Int): List<Int> =
        if ((0 until g.H).none { g.building[it] == K.TOWN }) emptyList()
        else WONDERS.filter { w -> g.terrain[h] in WONDER_LAND[w - LIGHTHOUSE] && (0 until g.H).none { g.building[it] == w } }

    /** A tile that can't work this season: left to rest. */
    fun resting(h: Int) = fallow[h]

    /**
     * What neighbours, decrees, wonders and the soil add to (or take from) the staffed building on [h], into [out].
     * [wa] is [activeWonders].
     */
    fun adjust(h: Int, out: IntArray, cold: Boolean, wa: BooleanArray) {
        val b = g.building[h]
        val long = if (decree[HOURS] == 1) 1 else 0
        when (b) {
            K.FARM -> if (!cold) {
                var f = long - minOf(3, tired[h] / 4)
                if (beside(h, K.GRANARY)) f += 1
                if (wa[GARDENS - LIGHTHOUSE]) f += 2
                out[K.FOOD] += f
            }
            K.RANCH -> out[K.FOOD] += long
            K.FISHERY -> out[K.FOOD] += long + (if (beside(h, K.PORT)) 1 else 0) + (if (wa[0]) 2 else 0)
            K.PORT -> if (wa[0]) out[K.FOOD] += 2
            K.MILL -> out[K.TIMBER] += long + if (g.map.hexNbrs[h].any { g.terrain[it] == K.FOREST && g.building[it] == K.NONE }) 1 else 0
            K.QUARRY -> out[K.STONE] += long + if (beside(h, K.MINE)) 1 else 0
            K.MINE -> out[K.ORE] += long + if (beside(h, K.QUARRY)) 1 else 0
            K.MARKET -> out[K.GOLD] += long + if (beside(h, K.TOWN)) 2 else 0
        }
    }

    /** Short notes on a tile, for the tile panel. */
    fun notes(h: Int): List<String> {
        val b = g.building[h]; val n = ArrayList<String>()
        if (rubble[h] > 0) n += "rubble: clear next season"
        if (isWonder(b) && works[h] > 0) n += "building: ${works[h]} season${if (works[h] > 1) "s" else ""} left"
        if (b == K.FARM) { if (fallow[h]) n += "resting" else if (tired[h] >= 4) n += "tired soil −${minOf(3, tired[h] / 4)}"; if (beside(h, K.GRANARY)) n += "+1 by the granary" }
        if (b == K.FISHERY && beside(h, K.PORT)) n += "+1 by the port"
        if (b == K.MILL && g.map.hexNbrs[h].any { g.terrain[it] == K.FOREST && g.building[it] == K.NONE }) n += "+1 by open forest"
        if (b == K.QUARRY && beside(h, K.MINE) || b == K.MINE && beside(h, K.QUARRY)) n += "+1 by the ${if (b == K.MINE) "quarry" else "mine"}"
        if (b == K.MARKET && beside(h, K.TOWN)) n += "+2 by the town"
        if (b == K.TOWN && !townServed(h)) n += "needs a temple or market nearby to house 15"
        if ((b == K.VILLAGE || b == K.TOWN) && besideTemple(h)) n += "+1 mood by the temple"
        return n
    }

    // ---------------------------------------------------------------- food, mood, gold

    /** Food your people eat this season, by the rations decree. */
    fun mouths() = ceil(people() * (when (decree[RATIONS]) { 0 -> 0.5; 2 -> 1.5; else -> 1.0 })).toInt()

    fun birthFactor(wa: BooleanArray) = (when (decree[RATIONS]) { 0 -> 0.0; 2 -> 1.5; else -> 1.0 }) * (if (wa[GARDENS - LIGHTHOUSE]) 1.5 else 1.0)

    /** Next season's harvest, as words. */
    fun forecastText(): String {
        if (g.season % 4 == 3) return "winter harvest"
        val d = forecast.sum()
        return if (d == 0) "unknown" else when { d <= 4 -> "poor ($d)"; d >= 10 -> "bountiful ($d)"; else -> "fair ($d)" }
    }

    /** Roll the first forecast at the start of a new game. */
    fun seedForecast(rng: Random) {
        if (forecast[0] == 0) { forecast[0] = rng.nextInt(1, 7); forecast[1] = rng.nextInt(1, 7) }
    }

    /** Takes this season's harvest from the forecast and rolls the next one. Returns the dice total. */
    fun harvestDice(rng: Random, lastDice: IntArray): Int {
        seedForecast(rng)
        lastDice[0] = forecast[0]; lastDice[1] = forecast[1]
        forecast[0] = rng.nextInt(1, 7); forecast[1] = rng.nextInt(1, 7)
        return lastDice[0] + lastDice[1]
    }
    /** The harvest's share: a poor year halves the farms (never with the Great Library), a bountiful one adds half again. */
    fun harvestShare(dice: Int, wa: BooleanArray) = when { dice <= 4 && !wa[LIBRARY - LIGHTHOUSE] -> 0.5f; dice >= 10 -> 1.5f; else -> 1f }

    /** The season's mood from decrees, temples beside homes and wonders; taxes in, upkeep out. */
    fun seasonMoodAndGold(wa: BooleanArray) {
        var mood = when (decree[RATIONS]) { 0 -> -5; 2 -> 4; else -> 0 } + when (decree[TAXES]) { 0 -> 2; 2 -> -5; else -> 0 } + (if (decree[HOURS] == 1) -4 else 0)
        mood += minOf(4, (0 until g.H).count { (g.building[it] == K.VILLAGE || g.building[it] == K.TOWN) && besideTemple(it) })
        if (wa[SKY_TEMPLE - LIGHTHOUSE]) mood += 5
        if (wa[PALACE - LIGHTHOUSE]) mood += 3
        if (wa[LIBRARY - LIGHTHOUSE]) mood += 2
        val tax = (when (decree[TAXES]) { 1 -> people() / 10; 2 -> people() / 5; else -> 0 }) * (if (wa[PALACE - LIGHTHOUSE]) 2 else 1)
        val owe = (0 until g.H).sumOf { if (works[it] == 0) upkeep(g.building[it]) else 0 }
        g.res[K.GOLD] += tax
        val paid = minOf(owe, g.res[K.GOLD]); g.res[K.GOLD] -= paid
        if (paid < owe) { mood -= minOf(8, 2 * (owe - paid)); g.log += "Upkeep unpaid: ${owe - paid} gold short, and your people notice" }
        if (tax > 0 || owe > 0) g.log += "Taxes +$tax gold, upkeep −$paid gold"
        g.happiness += mood
    }

    // ---------------------------------------------------------------- the season's turn

    /** Children a year old come of age; the rest grow a season. Call at the start of the season's end. */
    fun growUp() {
        val grown = kids[3]
        for (i in 3 downTo 1) kids[i] = kids[i - 1]
        kids[0] = 0
        if (grown > 0) { g.pop += grown; g.log += "$grown children came of age, ready for work" }
    }

    /** After the harvest: soil tires or rests, rubble clears, wonders rise. */
    fun endOfSeason(st: BooleanArray) {
        for (h in 0 until g.H) {
            if (rubble[h] > 0) rubble[h]--
            if (g.building[h] == K.FARM) {
                if (fallow[h]) tired[h] = maxOf(0, tired[h] - 3)
                else if (st[h] && !winter) tired[h] = minOf(12, tired[h] + 1)
            } else { tired[h] = maxOf(0, tired[h] - 1); fallow[h] = false }
            if (isWonder(g.building[h]) && works[h] > 0 && st[h]) {
                works[h]--
                if (works[h] == 0) { val w = g.building[h] - LIGHTHOUSE; g.log += "The ${WONDER_NAME[w]} is complete! ${WONDER_EFFECT[w]}"; yearNote = 2000 + w }
            }
        }
    }

    /** A record for the chronicle, once a year and at the end. */
    fun record(year: Int) {
        val conn = g.connected()
        chronicle += listOf(year, people(), conn.count { it }, (0 until g.H).count { g.building[it] != K.NONE }, g.happiness, wondersDone(), yearNote)
        yearNote = -1
    }

    fun legacy(won: Boolean) = people() + 2 * (0 until g.H).count { g.building[it] != K.NONE } + 15 * wondersDone() + g.happiness / 5 + (if (won) 50 else 0)

    /** The chronicle, a line a year. [cards] are the event cards' names. */
    fun history(cards: List<String>): List<String> = chronicle.chunked(REC).filter { it.size == REC }.map { r ->
        val note = when {
            r[6] in 1000 until 2000 -> { val c = (r[6] - 1000) / 2; val o = (r[6] - 1000) % 2; CHOICES[c]?.let { "${it[0]}: ${it[2 + o].lowercase()}" } ?: cards.getOrNull(c) ?: "" }
            r[6] >= 2000 -> "The ${WONDER_NAME[r[6] - 2000]} was finished"
            else -> ""
        }
        "Year ${r[0]} · ${r[1]} people on ${r[2]} tiles, ${r[3]} buildings, mood ${r[4]}" + (if (r[5] > 0) ", ${r[5]} wonder${if (r[5] > 1) "s" else ""}" else "") + if (note.isNotEmpty()) " · $note" else ""
    }

    // ---------------------------------------------------------------- your orders

    fun canDemolish(h: Int, conn: BooleanArray) = conn[h] && g.building[h] != K.NONE && g.building[h] != K.PORT
    /** Half the building's cost comes back. */
    fun refund(b: Int, cost: IntArray) = IntArray(5) { cost[it] / 2 }

    fun demolish(h: Int, cost: IntArray) {
        val b = g.building[h]
        val back = refund(b, cost)
        for (r in 0..4) g.res[r] += back[r]
        g.sendHome(h)
        g.building[h] = K.NONE; rubble[h] = 1; fallow[h] = false; works[h] = 0
        val sad = when { b == K.TOWN -> 8; b == K.VILLAGE -> 4; isWonder(b) -> 10; else -> 0 }
        g.happiness = (g.happiness - sad).coerceIn(0, 100)
        g.log += "You tore down the ${nameOf(b)}" + (if (back.sum() > 0) ", salvaging " + (0..4).filter { back[it] > 0 }.joinToString(", ") { "${back[it]} ${K.RES[it]}" } else "") +
            (if (sad > 0) ". Your people mourn their homes" else "")
    }

    private fun nameOf(b: Int) = if (isWonder(b)) WONDER_NAME[b - LIGHTHOUSE] else K.NAME[b].lowercase()

    fun setFallow(h: Int) {
        fallow[h] = !fallow[h]
        if (fallow[h]) { g.sendHome(h); g.log += "Your farm on the ${K.TERRAIN[g.terrain[h]]} rests this year" } else g.log += "Your farm on the ${K.TERRAIN[g.terrain[h]]} is worked again"
    }

    fun setDecree(k: Int, level: Int) {
        decree[k] = level
        g.log += "Decree: ${DECREE[k].lowercase()} set to ${LEVELS[k][level].lowercase()}"
    }

    /** Starts a wonder's building on [h], after it's placed. */
    fun started(h: Int) { if (isWonder(g.building[h])) works[h] = WONDER_SEASONS }

    // ---------------------------------------------------------------- choices

    private fun migrants() = 2 + g.diff / 2 + 2

    /** Choices that promise a fixed cost require the full amount. */
    fun canChoose(opt: Int): Boolean = opt in 0..1 && choice >= 0 && when {
        choice == 6 && opt == 0 -> g.res[K.FOOD] >= 6
        choice == 18 && opt == 0 -> g.res[K.GOLD] >= 4
        else -> true
    }

    /** What each answer to the waiting card will do, in a few words. */
    fun hint(opt: Int): String {
        val sky = active(SKY_TEMPLE)
        return when (choice) {
            2 -> if (opt == 0) "+${migrants()} people (homes for ${maxOf(0, housing() - people())})" else "+4 timber, +2 stone"
            3 -> if (sky) (if (opt == 0) "no deaths, mood −6" else "no deaths") else if (opt == 0) "about ${g.pop / 30} die, mood −6" else "about ${if ((0 until g.H).any { g.building[it] == K.TEMPLE }) g.pop / 24 else maxOf(1, g.pop / 12)} die"
            4 -> if (opt == 0) "lose ${g.res[K.GOLD] / 4} gold" else if (g.armed() >= 3) "your ${g.armed()} soldiers win: +3 gold" else "a die plus your soldiers must beat 5"
            5 -> if (opt == 0) { val f = minOf(4, g.res[K.FOOD]); "$f food for ${(3 + 3 * (0 until g.H).count { g.building[it] == K.MARKET }) * f / 4} gold" } else "5 gold for 4 timber and 4 stone"
            6 -> if (opt == 0) (if (canChoose(0)) "−6 food, +12 mood" else "needs 6 food") else "mood −2"
            13 -> if (opt == 0) (if (g.res[K.GOLD] >= 4) "−4 gold" else "you can't: mood −8") else "the strongest rival grows; mood +4"
            18 -> if (opt == 0) (if (canChoose(0)) "−4 gold, +4 mood" else "needs 4 gold") else if (g.armed() >= 3) "mood −3: your soldiers keep the peace" else "mood −8"
            else -> ""
        }
    }

    /** Answers the waiting card. */
    fun choose(opt: Int, rng: Random) {
        val c = choice; if (!canChoose(opt)) return
        choice = -1; yearNote = 1000 + c * 2 + opt
        val r = g.res; val sky = active(SKY_TEMPLE)
        val temple = (0 until g.H).any { g.building[it] == K.TEMPLE }
        g.log += when (c) {
            2 -> if (opt == 0) { val arrivals = migrants(); g.pop += arrivals; "Migrants: $arrivals newcomers joined your people" } else { r[K.TIMBER] += 4; r[K.STONE] += 2; "Migrants worked a season for you: +4 timber, +2 stone" }
            3 -> {
                val n = if (sky) 0 else if (opt == 0) g.pop / 30 else if (temple) g.pop / 24 else maxOf(1, g.pop / 12)
                g.pop -= n; if (opt == 0) g.happiness -= 6
                "Plague: $n of your people died" + if (opt == 0) " (the quarantine held it back)" else ""
            }
            4 -> if (opt == 0) { val gold = r[K.GOLD] / 4; r[K.GOLD] -= gold; "You paid the bandits $gold gold" }
                else {
                    val win = g.armed() >= 3 || rng.nextInt(1, 7) + g.armed() > 5
                    if (win) { r[K.GOLD] += 3; "Your soldiers drove off the bandits and took 3 gold" }
                    else { val gold = r[K.GOLD] / 2; val f = r[K.FOOD] / 4; r[K.GOLD] -= gold; r[K.FOOD] -= f; g.loseSoldiers(1); "The bandits won: they took $gold gold and $f food" }
                }
            5 -> if (opt == 0) { val f = minOf(4, r[K.FOOD]); val gold = (3 + 3 * (0 until g.H).count { g.building[it] == K.MARKET }) * f / 4; r[K.FOOD] -= f; r[K.GOLD] += gold; "You sold $f food for $gold gold" }
                else if (r[K.GOLD] >= 5) { r[K.GOLD] -= 5; r[K.TIMBER] += 4; r[K.STONE] += 4; "You bought 4 timber and 4 stone for 5 gold" } else "You had too little gold to buy anything"
            6 -> if (opt == 0) { val f = minOf(r[K.FOOD], 6); r[K.FOOD] -= f; g.happiness += 12; "Festival: your people feast (+12 mood)" } else { g.happiness -= 2; "You saved the food. Your people grumble" }
            13 -> if (opt == 0) { if (r[K.GOLD] >= 4) { r[K.GOLD] -= 4; "You paid 4 gold in tribute" } else { g.happiness -= 8; "You couldn't pay the tribute: your people despair" } }
                else { g.prideRival(); g.happiness += 4; "You refused the envoy. Your people are proud, but the rival arms" }
            18 -> if (opt == 0) { val gold = minOf(4, r[K.GOLD]); r[K.GOLD] -= gold; g.happiness += 4; "You heard their grievances" }
                else { g.happiness -= if (g.armed() >= 3) 3 else 8; "You kept order" }
            else -> ""
        }
        g.happiness = g.happiness.coerceIn(0, 100)
    }

    /** The steward's answer to the waiting card. */
    fun aiChoice(): Int = when (choice) {
        2 -> if (housing() - people() >= 2 && g.res[K.FOOD] > 10) 0 else 1
        3 -> if (g.pop >= 30) 0 else 1
        4 -> if (g.armed() >= 3) 1 else 0
        5 -> if (g.res[K.FOOD] > mouths() * 2 + 4) 0 else 1
        6 -> if (g.res[K.FOOD] > mouths() + 6 && g.happiness < 70) 0 else 1
        13 -> if (g.res[K.GOLD] >= 4) 0 else 1
        18 -> if (g.res[K.GOLD] >= 6) 0 else 1
        else -> 0
    }

    /**
     * The steward's stewardship order, if any: [0, opt] to choose, [1, kind, level] a decree, [2, h] a farm to rest or
     * work again. Null when nothing needs changing.
     */
    fun aiOrder(foodNet: Int, foodCap: Int): IntArray? {
        if (choice >= 0) return intArrayOf(0, aiChoice())
        val food = g.res[K.FOOD]
        val rations = when { food < mouths() && foodNet < 0 -> 0; food >= foodCap - 6 && g.happiness < 75 -> 2; else -> 1 }
        if (rations != decree[RATIONS]) return intArrayOf(1, RATIONS, rations)
        val taxes = when { g.happiness < 40 -> 0; g.happiness > 65 && g.res[K.GOLD] < 20 -> 2; else -> 1 }
        if (taxes != decree[TAXES]) return intArrayOf(1, TAXES, taxes)
        val hours = if (g.happiness > 72) 1 else 0
        if (hours != decree[HOURS]) return intArrayOf(1, HOURS, hours)
        for (h in 0 until g.H) if (g.building[h] == K.FARM) {
            if (fallow[h] && tired[h] == 0) return intArrayOf(2, h)
            if (!fallow[h] && tired[h] >= 8 && foodNet > 6) return intArrayOf(2, h)
        }
        return null
    }

    // ---------------------------------------------------------------- save

    fun state(): IntArray {
        val o = ArrayList<Int>()
        rubble.forEach { o += it }; tired.forEach { o += it }; fallow.forEach { o += if (it) 1 else 0 }; works.forEach { o += it }
        kids.forEach { o += it }; decree.forEach { o += it }; forecast.forEach { o += it }; o += choice; o += yearNote
        o += chronicle
        return o.toIntArray()
    }

    fun load(s: IntArray) {
        val H = g.H; var i = 0
        for (h in 0 until H) rubble[h] = s[i++]
        for (h in 0 until H) tired[h] = s[i++]
        for (h in 0 until H) fallow[h] = s[i++] == 1
        for (h in 0 until H) works[h] = s[i++]
        for (k in 0 until 4) kids[k] = s[i++]
        for (k in 0 until 3) decree[k] = s[i++]
        forecast[0] = s[i++]; forecast[1] = s[i++]; choice = s[i++]; yearNote = s[i++]
        chronicle.clear(); while (i < s.size) chronicle += s[i++]
    }
}
