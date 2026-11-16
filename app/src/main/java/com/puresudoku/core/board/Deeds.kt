package com.puresudoku.core.board

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Deed or Dungeon: a medieval game of lands and lordship for two players.
 *
 * Walk a ring of 36 tiles around Eiseria with two dice. Buy deeds to the land you stop on. Every deed carries a
 * two-sided coin: the Crown face asks a visiting rival to pay homage in gold (or refuse and be struck by your
 * towers); the Blade face simply wounds them. You turn your coins only when you pass the Spire. Lose all your
 * health and you are felled: the lord who felled you seizes one of your deeds and you wake in the Dungeon.
 * A lord felled with no deeds left is vanquished. Hold four whole domains to be crowned at once; otherwise the
 * old King dies when the last round ends, and the lord with the most renown is crowned King of Eiseria.
 */
class Deeds {
    companion object {
        const val N = 36
        // tile kinds
        const val SPIRE = 0; const val DUNGEON = 1; const val ARENA = 2; const val SEIZE = 3; const val LAND = 4
        const val BRIDGE = 5; const val MILL = 6; const val FATE = 7; const val LOOT = 8; const val TITHE = 9; const val LEVY = 10
        const val DUNGEON_TILE = 9; const val ARENA_TILE = 18; const val SEIZE_TILE = 27

        // phases
        const val SETUP = 0; const val ROLL = 1; const val DECIDE = 2; const val AFTER = 3; const val OVER = 4

        // decisions waiting on the player to move
        const val D_NONE = 0; const val D_BUY = 1; const val D_HOMAGE = 2; const val D_BLADE = 3; const val D_DEBT = 4; const val D_ARENA = 5; const val D_CARD = 6

        // items kept from loot
        const val POTION = 0; const val KEY = 1; const val SHIELD = 2; const val WRIT = 3; const val SIEGE = 4; const val BANNER = 5
        val ITEM_NAME = listOf("Healing Potion", "Skeleton Key", "Tower Shield", "Forged Writ", "Siege Engine", "Royal Banner")
        val ITEM_TEXT = listOf(
            "Drink on your turn: heal 4.",
            "Walk out of the Dungeon for free.",
            "Kept until needed: blocks the next wounds you would take.",
            "Show it to skip one homage or one blade, just once.",
            "Topple a tower on the rival's strongest land.",
            "Turn one of your coins on your turn, without passing the Spire.",
        )

        // heroes
        const val DRAGON = 0; const val KNIGHT = 1; const val THIEF = 2; const val BLACK = 3
        val HERO = listOf("Dragon", "Knight", "Veilnight Thief", "Black Knight")
        val HERO_POWER = listOf(
            "Dragonfire: your Blade lands wound 1 more.",
            "Plate armour: 12 health, and +1 in trials by combat.",
            "Light fingers: you pay a quarter less homage.",
            "Siegemaster: your towers cost a quarter less.",
        )
        val COLOR = listOf("Natural", "Azure", "Crimson", "Emerald", "Amber", "Violet")

        const val COIN_CROWN = 0; const val COIN_BLADE = 1

        val KIND = intArrayOf(
            SPIRE, LAND, LOOT, LAND, BRIDGE, LAND, FATE, LAND, LAND,
            DUNGEON, LAND, MILL, LAND, LAND, BRIDGE, LAND, LOOT, LAND,
            ARENA, LAND, FATE, LAND, LAND, BRIDGE, LAND, MILL, LAND,
            SEIZE, LAND, TITHE, LAND, BRIDGE, FATE, LAND, LEVY, LAND,
        )
        /** Domain (colour group) of each land, -1 for everything else. */
        val GROUP = intArrayOf(
            -1, 0, -1, 0, -1, 1, -1, 1, 1,
            -1, 2, -1, 2, 2, -1, 3, -1, 3,
            -1, 4, -1, 4, 4, -1, 5, -1, 5,
            -1, 6, -1, 6, -1, -1, 7, -1, 7,
        )
        val PRICE = intArrayOf(
            0, 60, 0, 70, 200, 100, 0, 100, 120,
            0, 140, 150, 140, 160, 200, 180, 0, 200,
            0, 220, 0, 220, 240, 200, 260, 150, 280,
            0, 300, 0, 320, 200, 0, 350, 0, 400,
        )
        const val DOMAINS = 8
        val DOMAIN = listOf("Mudwallow", "Hearthvale", "Briarwood", "Ravenmoor", "Saltmarsh", "Stonecrest", "Highspire", "the Crown Lands")
        val TOWER_COST = intArrayOf(50, 50, 100, 100, 150, 150, 200, 200)
        /** Homage as a percentage of the land's price, by towers (4 = castle). */
        val RENT_PCT = intArrayOf(8, 35, 100, 220, 330)
        val BRIDGE_TOLL = intArrayOf(25, 50, 100, 200)

        private val LAND_NAMES = listOf(
            listOf("Pigsty Hollow", "Turnip Row", "Muckle Ford", "Ditchwater"),
            listOf("Millbrook", "Thatchwick", "Ploughmead", "Hayward Green", "Shepherd's Rest"),
            listOf("Wolfden", "Old Oakhurst", "Fernhollow", "Thornwick", "Hunter's Lodge"),
            listOf("Gallows Hill", "Crow's Perch", "Ravenscar", "Bleakfen"),
            listOf("Harbour Keep", "Fishwife Quay", "Tidewatch", "Gull Point", "Smuggler's Cove"),
            listOf("Quarry Gate", "Ironforge", "Granite Hall", "Anvil Rise"),
            listOf("Bishop's Close", "Cathedral Square", "Silver Abbey", "Chapel Row"),
            listOf("Royal Gardens", "Throne Hill", "Golden Keep", "Lion's Court"),
        )
        private val BRIDGE_NAMES = listOf("Ferryman's Bridge", "Troll Bridge", "Old Stone Bridge", "Kingsbridge", "Rope Bridge", "Drawbridge")
        val CORNER_NAME = mapOf(0 to "The Spire", 9 to "The Dungeon", 18 to "The Colosseum", 27 to "The Inquisitor")

        // Fate: events, mostly medieval and some ironic
        val FATE_TEXT = listOf(
            "A bard sings of your deeds. Badly. The crowd pays him to stop and he shares: collect 50 gold.",
            "The plague doctor sends his bill. You never had the plague. Pay 100 gold.",
            "The King summons you to the Spire. He has forgotten why. Advance to the Spire.",
            "Your horse throws you into a moat. The horse is fine. Lose 2 health.",
            "Witch trial! You floated. Straight to the Dungeon.",
            "A dragon ate your rival's sheep. Somehow they blame you. Pay them 50 gold.",
            "You win the tournament! Your rival pays you 50 gold for taking part.",
            "A pilgrimage to the Colosseum. Advance there.",
            "Royal census: pay 25 gold for every tower you own. The census-taker counts on his fingers.",
            "A shortcut through the woods! Advance to the next bridge.",
            "Wrong turn at the swamp. Go back 3 tiles.",
            "The village fool's cure actually worked. Heal 3.",
            "A jester flips every coin you own. He thinks it's hilarious.",
            "Peasant revolt! A tower on your strongest land burns down.",
            "You find a deed in a tavern. It's probably real. Claim the cheapest free land.",
            "Knighted by mistake: the King thought you were your cousin. Collect 150 gold.",
            "The tax collector's horse throws a shoe at your door. Pay 40 gold shoeing tax.",
            "Robbers on the King's road! The richer lord loses a tenth of their gold.",
        )
        val LOOT_TEXT = listOf(
            "A Healing Potion! Keep it: drink on your turn to heal 4.",
            "A Skeleton Key! Keep it to walk out of the Dungeon.",
            "A Tower Shield! Keep it: it blocks the next wounds you take.",
            "A Forged Writ! Keep it to skip one homage or one blade.",
            "A Siege Engine! Keep it to topple a rival tower.",
            "A Royal Banner! Keep it to turn one coin on your turn.",
            "A chest of gold! Collect 200 gold.",
            "A chest of... bees. Lose 1 health, then sell the honey for 60 gold.",
            "Mimic! The chest bites back. Lose 2 health.",
            "Your great-uncle leaves you 100 gold. He was a horse thief, so don't ask.",
            "Cursed gold: collect 150 gold, but lose 1 health.",
            "A map to buried treasure! You dig up 120 gold. It was your own savings.",
        )

        val OMEN = listOf("Clear Skies", "Harvest Festival", "Blood Moon", "Famine", "Royal Jubilee", "Truce of the Crown", "Tax Season", "Builders' Guild", "Plague Winter")
        val OMEN_TEXT = listOf(
            "Nothing unusual. Enjoy it while it lasts.",
            "Homage is half as much again.",
            "Every wound from a land strikes 1 harder.",
            "The Spire pays half.",
            "The Spire pays double.",
            "Every coin counts as its Crown face: no blades.",
            "Taxes and fines are doubled.",
            "Towers cost half.",
            "No healing at the Spire or in the Dungeon.",
        )
        const val OMEN_EVERY = 4

        /** Rounds before the old King dies, by level. */
        val ROUNDS = intArrayOf(18, 20, 24, 26, 30, 32)
        const val DOMAINS_TO_WIN = 4
        const val NEXT_SETTLE = 0; const val NEXT_MOVE = 1
        const val START_GOLD = 1500
        const val STIPEND = 200
        const val BRIBE = 50
        const val GOLD_PER_WOUND = 50

        fun newGame(level: Int, rnd: Random): Deeds {
            val g = Deeds()
            g.level = level.coerceIn(0, 5)
            g.maxRounds = ROUNDS[g.level]
            // every realm is named afresh
            val names = Array(N) { "" }
            for (d in 0 until DOMAINS) {
                val pool = LAND_NAMES[d].shuffled(rnd)
                var k = 0
                for (t in 0 until N) if (GROUP[t] == d) names[t] = pool[k++]
            }
            val br = BRIDGE_NAMES.shuffled(rnd); var b = 0
            for (t in 0 until N) when (KIND[t]) {
                BRIDGE -> names[t] = br[b++]
                MILL -> names[t] = if (t < 18) "The Windmill" else "The Silver Mine"
                FATE -> names[t] = "Fate"
                LOOT -> names[t] = "Loot"
                TITHE -> names[t] = "The Abbey's Tithe"
                LEVY -> names[t] = "The King's Levy"
                else -> CORNER_NAME[t]?.let { names[t] = it }
            }
            g.names = names
            g.fate = IntArray(FATE_TEXT.size) { it }.also { it.shuffle(rnd) }
            g.loot = IntArray(LOOT_TEXT.size) { it }.also { it.shuffle(rnd) }
            g.phase = SETUP
            return g
        }

        fun fromJson(o: JSONObject): Deeds {
            val g = Deeds()
            fun ia(k: String, n: Int) = o.optJSONArray(k).let { a -> IntArray(n) { a?.optInt(it) ?: 0 } }
            g.level = o.optInt("level"); g.maxRounds = o.optInt("maxRounds", ROUNDS[g.level])
            val na = o.getJSONArray("names"); g.names = Array(N) { na.getString(it) }
            g.owner = ia("owner", N); g.towers = ia("towers", N); g.coin = ia("coin", N); g.pawned = ia("pawned", N)
            g.pos = ia("pos", 2); g.gold = ia("gold", 2); g.hp = ia("hp", 2); g.hero = ia("hero", 2); g.color = ia("color", 2)
            g.jail = ia("jail", 2); g.items = arrayOf(ia("items0", 6), ia("items1", 6)); g.felled = ia("felled", 2)
            g.actor = o.optInt("actor"); g.round = o.optInt("round", 1); g.doubles = o.optInt("doubles"); g.phase = o.optInt("phase")
            g.pType = o.optInt("pType"); g.pAmount = o.optInt("pAmount"); g.pDmg = o.optInt("pDmg"); g.pTile = o.optInt("pTile", -1); g.pCard = o.optInt("pCard", -1)
            g.pTo = o.optInt("pTo", -1); g.pWhat = o.optString("pWhat", ""); g.pNext = o.optInt("pNext"); g.felledThisTurn = o.optBoolean("felledThisTurn")
            g.d1 = o.optInt("d1", 1); g.d2 = o.optInt("d2", 1); g.rollId = o.optInt("rollId"); g.spireCharge = o.optBoolean("spireCharge")
            g.again = o.optBoolean("again"); g.purse = o.optInt("purse"); g.omen = o.optInt("omen"); g.winner = o.optInt("winner", -1)
            g.endReason = o.optInt("endReason"); g.setupDone = o.optInt("setupDone"); g.lastText = o.optString("lastText", "")
            g.fate = o.optJSONArray("fate").let { a -> IntArray(a?.length() ?: 0) { a!!.getInt(it) } }
            g.loot = o.optJSONArray("loot").let { a -> IntArray(a?.length() ?: 0) { a!!.getInt(it) } }
            if (g.fate.isEmpty()) g.fate = IntArray(FATE_TEXT.size) { it }
            if (g.loot.isEmpty()) g.loot = IntArray(LOOT_TEXT.size) { it }
            g.fateAt = o.optInt("fateAt"); g.lootAt = o.optInt("lootAt")
            return g
        }
    }

    var level = 0
    var maxRounds = 24
    var names = Array(N) { "" }
    var owner = IntArray(N) { -1 }
    var towers = IntArray(N)
    var coin = IntArray(N)
    var pawned = IntArray(N)

    var pos = IntArray(2)
    var gold = intArrayOf(START_GOLD, START_GOLD)
    var hp = intArrayOf(10, 10)
    var hero = intArrayOf(-1, -1)
    var color = intArrayOf(-1, -1)
    /** Turns spent in the Dungeon so far, or -1 when free. */
    var jail = intArrayOf(-1, -1)
    var items = arrayOf(IntArray(6), IntArray(6))
    var felled = IntArray(2)

    var actor = 0
    var round = 1
    var doubles = 0
    var phase = SETUP
    var setupDone = 0
    var pType = D_NONE
    var pAmount = 0
    var pDmg = 0
    var pTile = -1
    var pCard = -1
    /** A pending debt: who is owed (-1 the purse), what for, and what happens once it's paid. */
    var pTo = -1
    var pWhat = ""
    var pNext = 0
    var d1 = 1
    var d2 = 1
    var rollId = 0
    /** Passed the Spire this turn: you may turn your coins until the turn ends. */
    var spireCharge = false
    /** Rolled doubles: roll again after this landing. */
    var again = false
    /** Taxes and fines wait in the Colosseum for whoever lands there. */
    var purse = 0
    var omen = 0
    var winner = -1
    /** 1 vanquished, 2 four domains, 3 the old King died (most renown). */
    var endReason = 0
    var fate = IntArray(0)
    var fateAt = 0
    var loot = IntArray(0)
    var lootAt = 0
    /** The last card or event shown, for the screen. */
    var lastText = ""

    val log = ArrayList<String>()

    fun toJson(): JSONObject {
        fun a(x: IntArray) = JSONArray().also { j -> x.forEach { j.put(it) } }
        return JSONObject()
            .put("level", level).put("maxRounds", maxRounds).put("names", JSONArray().also { j -> names.forEach { j.put(it) } })
            .put("owner", a(owner)).put("towers", a(towers)).put("coin", a(coin)).put("pawned", a(pawned))
            .put("pos", a(pos)).put("gold", a(gold)).put("hp", a(hp)).put("hero", a(hero)).put("color", a(color))
            .put("jail", a(jail)).put("items0", a(items[0])).put("items1", a(items[1])).put("felled", a(felled))
            .put("actor", actor).put("round", round).put("doubles", doubles).put("phase", phase).put("setupDone", setupDone)
            .put("pType", pType).put("pAmount", pAmount).put("pDmg", pDmg).put("pTile", pTile).put("pCard", pCard).put("pTo", pTo).put("pWhat", pWhat).put("pNext", pNext).put("felledThisTurn", felledThisTurn)
            .put("d1", d1).put("d2", d2).put("rollId", rollId).put("spireCharge", spireCharge).put("again", again)
            .put("purse", purse).put("omen", omen).put("winner", winner).put("endReason", endReason).put("lastText", lastText)
            .put("fate", a(fate)).put("fateAt", fateAt).put("loot", a(loot)).put("lootAt", lootAt)
    }

    fun copy(): Deeds = fromJson(toJson())

    // ------------------------------------------------------------------ facts

    fun other(p: Int) = 1 - p
    fun maxHp(p: Int) = if (hero[p] == KNIGHT) 12 else 10
    fun title(p: Int): String {
        val h = hero[p].coerceAtLeast(0)
        val sameHero = hero[0] == hero[1] && hero[0] >= 0
        val c = color[p]
        return "the " + (if (c > 0) COLOR[c] + " " else if (sameHero) "Natural " else "") + HERO[h]
    }
    fun Title(p: Int) = title(p).replaceFirstChar { it.uppercase() }
    fun isDeed(t: Int) = KIND[t] == LAND || KIND[t] == BRIDGE || KIND[t] == MILL
    fun deeds(p: Int) = (0 until N).filter { owner[it] == p }
    fun domainTiles(d: Int) = (0 until N).filter { GROUP[it] == d }
    fun fullDomain(t: Int): Boolean {
        val d = GROUP[t]; if (d < 0) return false
        val o = owner[t]; if (o < 0) return false
        return domainTiles(d).all { owner[it] == o }
    }
    fun domainsHeld(p: Int) = (0 until DOMAINS).count { d -> domainTiles(d).all { owner[it] == p } }
    fun bridgesOf(p: Int) = (0 until N).count { KIND[it] == BRIDGE && owner[it] == p && pawned[it] == 0 }
    fun millsOf(p: Int) = (0 until N).count { KIND[it] == MILL && owner[it] == p && pawned[it] == 0 }
    fun towerCount(p: Int) = deeds(p).sumOf { towers[it] }

    /** Cost of one tower here for its owner, with the omen and the Black Knight's discount. */
    fun towerCost(t: Int, p: Int = owner[t]): Int {
        var c = TOWER_COST[GROUP[t].coerceAtLeast(0)]
        if (omen == 7) c /= 2
        if (p >= 0 && hero[p] == BLACK) c = c * 3 / 4
        return c
    }

    /** Homage owed on [t] by [p] (before the Thief's discount), [dice] for mills. */
    fun homageOf(t: Int, dice: Int = d1 + d2): Int {
        val o = owner[t]; if (o < 0 || pawned[t] == 1) return 0
        var r = when (KIND[t]) {
            LAND -> {
                val base = PRICE[t] * RENT_PCT[towers[t]] / 100
                if (towers[t] == 0 && fullDomain(t)) base * 2 else base
            }
            BRIDGE -> BRIDGE_TOLL[(bridgesOf(o) - 1).coerceIn(0, 3)]
            MILL -> dice * if (millsOf(o) >= 2) 10 else 4
            else -> 0
        }
        if (omen == 1) r = r * 3 / 2
        return r
    }
    fun homageFor(t: Int, p: Int): Int { val r = homageOf(t); return if (hero[p] == THIEF) r * 3 / 4 else r }

    /** Wounds the towers strike if the visitor refuses homage. */
    fun refuseDmg(t: Int): Int {
        var d = when (KIND[t]) { LAND -> 1 + towers[t]; BRIDGE -> 1; else -> 1 }
        if (omen == 2) d++
        return d
    }

    /** Wounds the Blade face deals to a visitor. */
    fun bladeDmg(t: Int): Int {
        val o = owner[t]
        var d = when (KIND[t]) {
            LAND -> 2 + towers[t] + (if (fullDomain(t)) 1 else 0) + (if (towers[t] == 4) 1 else 0)
            BRIDGE -> bridgesOf(o).coerceAtLeast(1)
            MILL -> millsOf(o).coerceAtLeast(1)
            else -> 0
        }
        if (o >= 0 && hero[o] == DRAGON) d++
        if (omen == 2) d++
        return d
    }

    /** The coin face that actually applies (a truce turns every blade to its crown face). */
    fun face(t: Int) = if (omen == 5) COIN_CROWN else coin[t]

    fun renown(p: Int): Int = gold[p] + deeds(p).sumOf { (if (pawned[it] == 1) PRICE[it] / 2 else PRICE[it]) + towers[it] * TOWER_COST[GROUP[it].coerceAtLeast(0)] }

    fun stipend(): Int = when (omen) { 3 -> STIPEND / 2; 4 -> STIPEND * 2; else -> STIPEND }
    private fun fine(x: Int) = if (omen == 6) x * 2 else x
    private fun say(s: String) { log += s }

    // ------------------------------------------------------------------ setup

    fun canChoose(p: Int, h: Int, c: Int): Boolean = phase == SETUP && p == actor && h in 0..3 && c in COLOR.indices && color[other(p)] != c

    fun choose(h: Int, c: Int): Boolean {
        if (!canChoose(actor, h, c)) return false
        hero[actor] = h; color[actor] = c; hp[actor] = maxHp(actor)
        setupDone++
        say("${Title(actor)} takes the field.")
        if (setupDone >= 2) { actor = 0; phase = ROLL; say("Round 1. ${Title(0)} rolls first.") } else actor = other(actor)
        return true
    }

    // ------------------------------------------------------------------ rolling and moving

    val inDungeon: Boolean get() = jail[actor] >= 0
    fun canAct() = phase == ROLL || phase == AFTER

    fun roll(rnd: Random): Boolean {
        if (phase != ROLL) return false
        d1 = rnd.nextInt(1, 7); d2 = rnd.nextInt(1, 7); rollId++
        val p = actor
        val dbl = d1 == d2
        if (jail[p] >= 0) {
            if (dbl) {
                jail[p] = -1; say("${Title(p)} rolls doubles and slips out of the Dungeon!")
                again = false
                move(p, d1 + d2)
            } else {
                jail[p]++
                if (omen != 8) heal(p, 1, quiet = true)
                if (jail[p] >= 3) {
                    say("${Title(p)} has served three turns and must buy their way out.")
                    jail[p] = -1
                    again = false
                    owe(p, fine(BRIBE), -1, "the jailer's bribe", NEXT_MOVE)
                } else {
                    say("${Title(p)} rolls ${d1 + d2}, no doubles. Another night in the Dungeon (heals 1).")
                    phase = AFTER
                }
            }
            return true
        }
        say("${Title(p)} rolls $d1 + $d2" + if (dbl) ": doubles!" else ".")
        if (dbl) {
            doubles++
            if (doubles >= 3) {
                say("Three doubles in a row! ${Title(p)} is arrested for reckless riding.")
                doubles = 0; again = false
                toDungeon(p); phase = AFTER
                return true
            }
            again = true
        } else again = false
        move(p, d1 + d2)
        return true
    }

    /** Leave the Dungeon by paying the jailer, then roll as normal. */
    fun bribe(): Boolean {
        val p = actor
        if (phase != ROLL || jail[p] < 0 || gold[p] < fine(BRIBE)) return false
        pay(p, fine(BRIBE), -1); jail[p] = -1
        say("${Title(p)} bribes the jailer and walks free.")
        return true
    }

    fun useKey(): Boolean {
        val p = actor
        if (phase != ROLL || jail[p] < 0 || items[p][KEY] <= 0) return false
        items[p][KEY]--; jail[p] = -1
        say("${Title(p)} turns the Skeleton Key. The cell door creaks open.")
        return true
    }

    private fun move(p: Int, steps: Int) {
        val from = pos[p]
        val to = ((from + steps) % N + N) % N
        if (steps > 0 && to < from) passSpire(p)
        pos[p] = to
        land(p)
    }

    private fun moveTo(p: Int, to: Int, collect: Boolean = true) {
        if (collect && to <= pos[p] && to != pos[p]) passSpire(p)
        if (collect && to == 0 && pos[p] != 0) { /* counted above */ }
        pos[p] = to
        land(p)
    }

    private fun passSpire(p: Int) {
        val s = stipend()
        gold[p] += s
        if (omen != 8) heal(p, 1, quiet = true)
        spireCharge = true
        say("${Title(p)} passes the Spire: +$s gold" + (if (omen != 8) ", heals 1" else "") + ". Coins may be turned this turn.")
    }

    fun toDungeon(p: Int) {
        pos[p] = DUNGEON_TILE; jail[p] = 0
        if (p == actor) { again = false; doubles = 0 }
    }

    private fun heal(p: Int, n: Int, quiet: Boolean = false) {
        val before = hp[p]
        hp[p] = minOf(maxHp(p), hp[p] + n)
        if (!quiet && hp[p] > before) say("${Title(p)} heals ${hp[p] - before}.")
    }

    // ------------------------------------------------------------------ landing

    private fun land(p: Int) {
        val t = pos[p]
        pType = D_NONE; pTile = t
        when (KIND[t]) {
            SPIRE -> { gold[p] += 100; say("${Title(p)} lands right on the Spire: the King adds 100 gold.") ; settle() }
            DUNGEON -> { say("${Title(p)} peers into the Dungeon. Just visiting."); settle() }
            SEIZE -> {
                say("The Inquisitor seizes ${title(p)}! Off to the Dungeon.")
                toDungeon(p); settle()
            }
            ARENA -> {
                if (purse > 0) { say("${Title(p)} wins the Colosseum purse: $purse gold!"); gold[p] += purse; purse = 0 }
                else say("${Title(p)} enters the Colosseum. The purse is empty.")
                if (deeds(p).isNotEmpty() && deeds(other(p)).isNotEmpty()) decide(D_ARENA) else settle()
            }
            FATE -> { pCard = drawFate(); lastText = FATE_TEXT[pCard]; decide(D_CARD) }
            LOOT -> { pCard = 100 + drawLoot(); lastText = LOOT_TEXT[pCard - 100]; decide(D_CARD) }
            TITHE -> {
                val x = fine(gold[p] / 10)
                pay(p, x, -1); say("The Abbey takes its tithe: ${title(p)} pays $x gold to the Colosseum purse."); settle()
            }
            LEVY -> owe(p, fine(150), -1, "the King's Levy")
            else -> landDeed(p, t)
        }
    }

    private fun landDeed(p: Int, t: Int) {
        val o = owner[t]
        when {
            o < 0 -> decide(D_BUY)
            o == p -> { say("${Title(p)} rests at their own ${names[t]}."); settle() }
            pawned[t] == 1 -> { say("${names[t]} is pawned to the moneylender. Nothing is owed."); settle() }
            face(t) == COIN_CROWN -> { pAmount = homageFor(t, p); pDmg = refuseDmg(t); decide(D_HOMAGE) }
            else -> {
                pDmg = bladeDmg(t)
                if (items[p][WRIT] > 0 || items[p][SHIELD] > 0) decide(D_BLADE)
                else { say("The Blade! ${names[t]}'s guards strike ${title(p)} for ${pDmg}."); wound(p, pDmg, o); settle() }
            }
        }
    }

    private fun decide(type: Int) { pType = type; phase = DECIDE }

    /** The landing is dealt with: carry on to manage your lands and end the turn. */
    private fun settle() {
        pType = D_NONE
        if (phase != OVER) phase = AFTER
    }

    // ------------------------------------------------------------------ paying and bleeding

    private fun pay(p: Int, x: Int, to: Int) {
        val v = minOf(x, gold[p])
        gold[p] -= v
        if (to >= 0) gold[to] += v else purse += v
    }

    /** What follows a paid debt: carry on, or (leaving the Dungeon) move by the dice. */
    private fun next(p: Int, n: Int) { if (n == NEXT_MOVE && phase != OVER && !felledThisTurn) move(p, d1 + d2) else settle() }

    /** [p] owes [x]: paid at once if they can, otherwise they choose to raise funds or pay the shortfall in blood. */
    private fun owe(p: Int, x: Int, to: Int, what: String, then: Int = NEXT_SETTLE) {
        if (gold[p] >= x || p != actor) {
            bleedPay(p, x, to, what); next(p, then); return
        }
        pAmount = x; pTo = to; pWhat = what; pNext = then
        decide(D_DEBT)
    }

    /** Wounds owed for the gold [p] can't pay. */
    fun shortfallWounds(p: Int, x: Int) = if (gold[p] >= x) 0 else (x - gold[p] + GOLD_PER_WOUND - 1) / GOLD_PER_WOUND

    private fun bleedPay(p: Int, x: Int, to: Int, what: String) {
        val w = shortfallWounds(p, x)
        val paid = minOf(gold[p], x)
        pay(p, paid, to)
        if (w > 0) { say("${Title(p)} can pay only $paid gold for $what and takes $w wound${if (w == 1) "" else "s"} instead."); wound(p, w, to) }
        else say("${Title(p)} pays $x gold for $what.")
    }

    /** Settle the pending debt: pay what you can, wounds for the rest. */
    fun payDebt(): Boolean {
        if (phase != DECIDE || pType != D_DEBT) return false
        val p = actor
        pType = D_NONE
        bleedPay(p, pAmount, pTo, pWhat)
        if (phase == OVER) return true
        next(p, pNext)
        if (phase == DECIDE && pType == D_NONE) settle()
        return true
    }

    var felledThisTurn = false

    /** [n] wounds to [p], dealt by [by] (-1 = the realm). Felled at 0 health. */
    fun wound(p: Int, n: Int, by: Int) {
        if (n <= 0) return
        if (items[p][SHIELD] > 0) {
            items[p][SHIELD]--
            say("${Title(p)}'s Tower Shield takes the blow!")
            return
        }
        hp[p] -= n
        if (hp[p] <= 0) fell(p, by)
    }

    private fun fell(p: Int, by: Int) {
        felled[p]++
        val mine = deeds(p)
        if (mine.isEmpty()) {
            hp[p] = 0
            say("${Title(p)} falls with no lands left to lose. ${Title(other(p))} is crowned King of Eiseria!")
            end(other(p), 1)
            return
        }
        val q = other(p)
        if (by == q) {
            val t = mine.maxBy { spoilValue(q, it) }
            owner[t] = q
            if (pawned[t] == 0 && towers[t] >= 0) { /* towers and coin come with it */ }
            say("${Title(p)} is felled! ${Title(q)} seizes ${names[t]}" + (if (towers[t] > 0) " and its ${towerWord(towers[t])}" else "") + ".")
        } else {
            val t = mine.minBy { PRICE[it] + towers[it] * 100 }
            owner[t] = -1; towers[t] = 0; pawned[t] = 0; coin[t] = COIN_CROWN
            say("${Title(p)} is felled! ${names[t]} returns to the realm.")
        }
        hp[p] = maxHp(p)
        toDungeon(p)
        if (p == actor) felledThisTurn = true
        say("${Title(p)} wakes in the Dungeon, healed.")
    }

    fun towerWord(n: Int) = if (n >= 4) "castle" else if (n == 1) "tower" else "$n towers"

    /** How much [q] would like to take [t]. */
    fun spoilValue(q: Int, t: Int): Int {
        var v = PRICE[t] + towers[t] * TOWER_COST[GROUP[t].coerceAtLeast(0)]
        val d = GROUP[t]
        if (d >= 0) {
            val tiles = domainTiles(d)
            if (tiles.all { it == t || owner[it] == q }) v += 400
            if (tiles.all { owner[it] == owner[t] }) v += 150
        }
        if (KIND[t] == BRIDGE) v += 40 * bridgesOf(q)
        if (pawned[t] == 1) v /= 2
        return v
    }

    // ------------------------------------------------------------------ decisions

    fun buy(rnd: Random): Boolean {
        if (phase != DECIDE || pType != D_BUY) return false
        val p = actor; val t = pos[p]
        if (gold[p] < PRICE[t]) return false
        gold[p] -= PRICE[t]; owner[t] = p
        coin[t] = rnd.nextInt(2)
        say("${Title(p)} buys the deed to ${names[t]} for ${PRICE[t]} gold. The coin lands on its ${if (coin[t] == COIN_CROWN) "Crown" else "Blade"}!")
        if (fullDomain(t)) say("${Title(p)} now holds all of ${DOMAIN[GROUP[t]]}!")
        settle(); checkCoronation()
        return true
    }

    fun pass(): Boolean {
        if (phase != DECIDE || pType != D_BUY) return false
        say("${Title(actor)} leaves ${names[pos[actor]]} unclaimed.")
        settle(); return true
    }

    fun payHomage(): Boolean {
        if (phase != DECIDE || pType != D_HOMAGE) return false
        val p = actor; val t = pos[p]
        if (gold[p] < pAmount) return false
        pay(p, pAmount, owner[t])
        say("${Title(p)} kneels and pays ${pAmount} gold homage to ${title(owner[t])}.")
        settle(); return true
    }

    fun refuse(): Boolean {
        if (phase != DECIDE || pType != D_HOMAGE) return false
        val p = actor; val t = pos[p]
        say("${Title(p)} refuses homage! The towers of ${names[t]} strike for ${pDmg}.")
        wound(p, pDmg, owner[t])
        settle(); return true
    }

    fun takeBlade(): Boolean {
        if (phase != DECIDE || pType != D_BLADE) return false
        val p = actor; val t = pos[p]
        say("The Blade! ${names[t]}'s guards strike ${title(p)} for ${pDmg}.")
        wound(p, pDmg, owner[t])
        settle(); return true
    }

    fun useWrit(): Boolean {
        if (phase != DECIDE || (pType != D_HOMAGE && pType != D_BLADE)) return false
        val p = actor
        if (items[p][WRIT] <= 0) return false
        items[p][WRIT]--
        say("${Title(p)} waves a Forged Writ. The guards squint, shrug, and wave them through.")
        settle(); return true
    }

    fun challenge(rnd: Random): Boolean {
        if (phase != DECIDE || pType != D_ARENA) return false
        val p = actor; val q = other(p)
        var a: Int; var b: Int
        val bonusA = if (hero[p] == KNIGHT) 1 else 0
        val bonusB = if (hero[q] == KNIGHT) 1 else 0
        do { a = rnd.nextInt(1, 7) + rnd.nextInt(1, 7) + bonusA; b = rnd.nextInt(1, 7) + rnd.nextInt(1, 7) + bonusB } while (a == b)
        val win = if (a > b) p else q
        val lose = other(win)
        say("Trial by combat! ${Title(p)} rolls $a, ${title(q)} rolls $b.")
        val spoils = deeds(lose).maxByOrNull { spoilValue(win, it) }
        if (spoils != null) {
            owner[spoils] = win
            say("${Title(win)} wins and takes ${names[spoils]}" + (if (towers[spoils] > 0) " with its ${towerWord(towers[spoils])}" else "") + "!")
        }
        lastText = "Trial by combat: $a against $b. ${Title(win)} wins" + (spoils?.let { " ${names[it]}" } ?: "") + "!"
        wound(lose, 2, win)
        if (phase != OVER) { settle(); checkCoronation() }
        return true
    }

    fun decline(): Boolean {
        if (phase != DECIDE || pType != D_ARENA) return false
        say("${Title(actor)} waves to the crowd and leaves the arena.")
        settle(); return true
    }

    /** Read the card and carry it out. */
    fun ack(rnd: Random): Boolean {
        if (phase != DECIDE || pType != D_CARD) return false
        val c = pCard; pCard = -1; pType = D_NONE
        if (c >= 100) applyLoot(c - 100) else applyFate(c, rnd)
        if (phase == DECIDE && pType == D_NONE) settle()
        return true
    }

    private fun drawFate(): Int { if (fateAt >= fate.size) { fate.shuffle(); fateAt = 0 }; return fate[fateAt++] }
    private fun drawLoot(): Int { if (lootAt >= loot.size) { loot.shuffle(); lootAt = 0 }; return loot[lootAt++] }

    private fun applyFate(c: Int, rnd: Random) {
        val p = actor; val q = other(p)
        say("Fate: " + FATE_TEXT[c])
        when (c) {
            0 -> { gold[p] += 50; settle() }
            1 -> owe(p, fine(100), -1, "the plague doctor")
            2 -> { passSpire(p); pos[p] = 0; settle() }
            3 -> { wound(p, 2, -1); settle() }
            4 -> { toDungeon(p); settle() }
            5 -> owe(p, 50, q, "the eaten sheep")
            6 -> { bleedPay(q, 50, p, "the tournament"); settle() }
            7 -> moveTo(p, ARENA_TILE)
            8 -> { val x = fine(25 * towerCount(p)); if (x == 0) settle() else owe(p, x, -1, "the census") }
            9 -> { var t = (pos[p] + 1) % N; while (KIND[t] != BRIDGE) t = (t + 1) % N; moveTo(p, t) }
            10 -> { pos[p] = (pos[p] - 3 + N) % N; land(p) }
            11 -> { heal(p, 3); settle() }
            12 -> {
                val mine = deeds(p)
                for (t in mine) coin[t] = 1 - coin[t]
                if (mine.isNotEmpty()) say("Every coin of ${title(p)} turns over.")
                settle()
            }
            13 -> {
                val t = deeds(p).filter { towers[it] > 0 }.maxByOrNull { towers[it] * 1000 + PRICE[it] }
                if (t != null) { towers[t]--; say("A tower at ${names[t]} burns down.") } else say("${Title(p)} has no towers. The peasants go home disappointed.")
                settle()
            }
            14 -> {
                val t = (0 until N).filter { isDeed(it) && owner[it] < 0 }.minByOrNull { PRICE[it] }
                if (t != null) {
                    owner[t] = p; coin[t] = rnd.nextInt(2)
                    say("${Title(p)} claims ${names[t]}! The coin lands on its ${if (coin[t] == COIN_CROWN) "Crown" else "Blade"}.")
                    checkCoronation()
                } else say("Every land is already claimed. The deed is a forgery after all.")
                settle()
            }
            15 -> { gold[p] += 150; settle() }
            16 -> owe(p, fine(40), -1, "the shoeing tax")
            17 -> {
                val rich = if (gold[0] >= gold[1]) 0 else 1
                val x = gold[rich] / 10
                pay(rich, x, -1); say("${Title(rich)} loses $x gold to robbers. It ends up in the Colosseum purse, somehow.")
                settle()
            }
            else -> settle()
        }
    }

    private fun applyLoot(c: Int) {
        val p = actor
        say("Loot: " + LOOT_TEXT[c])
        when (c) {
            in 0..5 -> items[p][c]++
            6 -> gold[p] += 200
            7 -> { gold[p] += 60; wound(p, 1, -1) }
            8 -> wound(p, 2, -1)
            9 -> gold[p] += 100
            10 -> { gold[p] += 150; wound(p, 1, -1) }
            11 -> gold[p] += 120
        }
        settle()
    }

    // ------------------------------------------------------------------ managing your lands

    private fun mayManage(p: Int) = phase != OVER && phase != SETUP && p == actor

    fun canBuild(t: Int): Boolean {
        val p = actor
        if (!mayManage(p) || phase == DECIDE || KIND[t] != LAND || owner[t] != p || towers[t] >= 4) return false
        if (!fullDomain(t)) return false
        val tiles = domainTiles(GROUP[t])
        if (tiles.any { pawned[it] == 1 }) return false
        if (towers[t] > tiles.minOf { towers[it] }) return false
        return gold[p] >= towerCost(t, p)
    }

    fun build(t: Int): Boolean {
        if (!canBuild(t)) return false
        val p = actor
        gold[p] -= towerCost(t, p); towers[t]++
        say(if (towers[t] == 4) "${Title(p)} raises a castle at ${names[t]}!" else "${Title(p)} raises a tower at ${names[t]} (${towers[t]}).")
        return true
    }

    fun canSell(t: Int): Boolean {
        val p = actor
        if (!mayManage(p) || owner[t] != p || towers[t] <= 0) return false
        val tiles = domainTiles(GROUP[t])
        return towers[t] >= tiles.maxOf { towers[it] }
    }

    fun sell(t: Int): Boolean {
        if (!canSell(t)) return false
        val p = actor
        val back = TOWER_COST[GROUP[t]] / 2
        towers[t]--; gold[p] += back
        say("${Title(p)} tears down a tower at ${names[t]} for $back gold.")
        return true
    }

    fun canPawn(t: Int): Boolean {
        val p = actor
        if (!mayManage(p) || owner[t] != p || pawned[t] == 1) return false
        if (GROUP[t] >= 0 && domainTiles(GROUP[t]).any { towers[it] > 0 }) return false
        return true
    }

    fun pawn(t: Int): Boolean {
        if (!canPawn(t)) return false
        val p = actor
        pawned[t] = 1; gold[p] += PRICE[t] / 2
        say("${Title(p)} pawns ${names[t]} to the moneylender for ${PRICE[t] / 2} gold.")
        return true
    }

    fun redeemCost(t: Int) = PRICE[t] * 55 / 100
    fun canRedeem(t: Int): Boolean {
        val p = actor
        return mayManage(p) && phase != DECIDE && owner[t] == p && pawned[t] == 1 && gold[p] >= redeemCost(t)
    }

    fun redeem(t: Int): Boolean {
        if (!canRedeem(t)) return false
        val p = actor
        gold[p] -= redeemCost(t); pawned[t] = 0
        say("${Title(p)} buys back ${names[t]} for ${redeemCost(t)} gold.")
        return true
    }

    /** Coins turn only after passing the Spire this turn, or with a Royal Banner. */
    fun canFlip(t: Int): Boolean {
        val p = actor
        return mayManage(p) && phase != DECIDE && owner[t] == p && (spireCharge || items[p][BANNER] > 0)
    }

    fun flip(t: Int): Boolean {
        if (!canFlip(t)) return false
        val p = actor
        if (!spireCharge) items[p][BANNER]--
        coin[t] = 1 - coin[t]
        say("${Title(p)} turns the coin of ${names[t]} to its ${if (coin[t] == COIN_CROWN) "Crown" else "Blade"}.")
        return true
    }

    fun canPotion() = mayManage(actor) && items[actor][POTION] > 0 && hp[actor] < maxHp(actor)
    fun potion(): Boolean {
        if (!canPotion()) return false
        items[actor][POTION]--; heal(actor, 4)
        return true
    }

    fun siegeTarget(): Int = deeds(other(actor)).filter { towers[it] > 0 }.maxByOrNull { towers[it] * 1000 + PRICE[it] } ?: -1
    fun canSiege() = mayManage(actor) && phase != DECIDE && items[actor][SIEGE] > 0 && siegeTarget() >= 0
    fun siege(): Boolean {
        if (!canSiege()) return false
        val t = siegeTarget()
        items[actor][SIEGE]--; towers[t]--
        say("${Title(actor)}'s Siege Engine topples a tower at ${names[t]}!")
        return true
    }

    // ------------------------------------------------------------------ the turn's end

    fun canEnd() = phase == AFTER

    fun endTurn(rnd: Random): Boolean {
        if (phase != AFTER) return false
        spireCharge = false
        felledThisTurn = false
        pTile = -1
        if (again && jail[actor] < 0) {
            again = false
            phase = ROLL
            say("${Title(actor)} rides on.")
            return true
        }
        again = false; doubles = 0
        if (checkCoronation()) return true
        actor = other(actor)
        if (actor == 0) {
            round++
            if (round > maxRounds) { kingDies(); return true }
            if ((round - 1) % OMEN_EVERY == 0) {
                var o: Int
                do { o = rnd.nextInt(OMEN.size) } while (o == omen)
                omen = o
                say("A new omen: ${OMEN[o]}. ${OMEN_TEXT[o]}")
            }
        }
        phase = ROLL
        return true
    }

    private fun checkCoronation(): Boolean {
        for (p in 0..1) if (domainsHeld(p) >= DOMAINS_TO_WIN && phase != OVER) {
            say("${Title(p)} holds $DOMAINS_TO_WIN whole domains and is crowned King of Eiseria!")
            end(p, 2); return true
        }
        return false
    }

    private fun kingDies() {
        val a = renown(0); val b = renown(1)
        say("The old King is dead! Renown: ${title(0)} $a, ${title(1)} $b.")
        end(if (a > b) 0 else if (b > a) 1 else 2, 3)
    }

    private fun end(w: Int, reason: Int) {
        winner = w; endReason = reason; phase = OVER; pType = D_NONE
    }

    /** One of the player's possible actions, as chosen by the AI. */
    data class Act(val type: Int, val arg: Int = -1) {
        companion object {
            const val ROLL = 0; const val BUY = 1; const val PASS = 2; const val PAY = 3; const val REFUSE = 4; const val BLADE = 5
            const val WRIT = 6; const val DEBT = 7; const val CHALLENGE = 8; const val DECLINE = 9; const val ACK = 10; const val BUILD = 11
            const val SELL = 12; const val PAWN = 13; const val REDEEM = 14; const val FLIP = 15; const val POTION = 16; const val SIEGE = 17
            const val BRIBE = 18; const val KEY = 19; const val END = 20; const val CHOOSE = 21
        }
    }

    fun perform(a: Act, rnd: Random): Boolean = when (a.type) {
        Act.ROLL -> roll(rnd); Act.BUY -> buy(rnd); Act.PASS -> pass(); Act.PAY -> payHomage(); Act.REFUSE -> refuse()
        Act.BLADE -> takeBlade(); Act.WRIT -> useWrit(); Act.DEBT -> payDebt(); Act.CHALLENGE -> challenge(rnd); Act.DECLINE -> decline()
        Act.ACK -> ack(rnd); Act.BUILD -> build(a.arg); Act.SELL -> sell(a.arg); Act.PAWN -> pawn(a.arg); Act.REDEEM -> redeem(a.arg)
        Act.FLIP -> flip(a.arg); Act.POTION -> potion(); Act.SIEGE -> siege(); Act.BRIBE -> bribe(); Act.KEY -> useKey()
        Act.END -> endTurn(rnd); Act.CHOOSE -> choose(a.arg / 10, a.arg % 10)
        else -> false
    }
}

/** The rival lord: greedy about land, careful with its health, sharper on harder levels. */
object DeedsAi {
    private fun A(t: Int, arg: Int = -1) = Deeds.Act(t, arg)

    fun next(g: Deeds, rnd: Random): Deeds.Act {
        val p = g.actor; val q = g.other(p); val lv = g.level
        val sloppy = lv == 0 && rnd.nextInt(100) < 25
        val reserve = intArrayOf(40, 80, 120, 150, 180, 200)[lv] + g.round * 4
        val hpVal = intArrayOf(40, 50, 60, 70, 75, 80)[lv]
        when (g.phase) {
            Deeds.SETUP -> {
                val hero = (0..3).filter { it != g.hero[q] }.random(rnd)
                val col = (1 until Deeds.COLOR.size).filter { it != g.color[q] }.random(rnd)
                return A(Deeds.Act.CHOOSE, hero * 10 + col)
            }
            Deeds.DECIDE -> return decide(g, p, q, lv, rnd, sloppy, reserve, hpVal)
        }
        // manage before rolling or ending
        if (g.canPotion() && g.hp[p] <= g.maxHp(p) - 4) return A(Deeds.Act.POTION)
        if (g.canSiege() && lv >= 1) return A(Deeds.Act.SIEGE)
        if (g.phase == Deeds.AFTER && g.spireCharge) {
            for (t in g.deeds(p)) { val want = wantFace(g, p, q, t, lv); if (g.coin[t] != want) return A(Deeds.Act.FLIP, t) }
        }
        for (t in g.deeds(p).sortedByDescending { Deeds.PRICE[it] }) if (g.canRedeem(t) && g.gold[p] - g.redeemCost(t) >= reserve + 300) return A(Deeds.Act.REDEEM, t)
        val buildable = g.deeds(p).filter { g.canBuild(it) && g.gold[p] - g.towerCost(it, p) >= reserve + (if (lv <= 1) 150 else 60) }
        if (buildable.isNotEmpty() && !sloppy) return A(Deeds.Act.BUILD, buildable.maxBy { Deeds.PRICE[it] })
        if (g.phase == Deeds.ROLL) {
            if (g.jail[p] >= 0) {
                if (g.items[p][Deeds.KEY] > 0) return A(Deeds.Act.KEY)
                val early = (0 until Deeds.N).count { g.isDeed(it) && g.owner[it] < 0 } > 6
                if ((early || g.hp[p] >= g.maxHp(p) - 1) && g.gold[p] >= Deeds.BRIBE + reserve) return A(Deeds.Act.BRIBE)
            }
            return A(Deeds.Act.ROLL)
        }
        return A(Deeds.Act.END)
    }

    private fun wantFace(g: Deeds, p: Int, q: Int, t: Int, lv: Int): Int {
        if (lv == 0) return g.coin[t]
        val blade = g.bladeDmg(t)
        val rent = g.homageOf(t, 7)
        return when {
            g.hp[q] <= blade * 2 + (if (lv >= 3) 2 else 0) && g.deeds(q).isNotEmpty() -> Deeds.COIN_BLADE
            g.hero[p] == Deeds.DRAGON && rent < 40 -> Deeds.COIN_BLADE
            lv >= 3 && g.gold[q] < 200 -> Deeds.COIN_BLADE
            else -> Deeds.COIN_CROWN
        }
    }

    private fun raise(g: Deeds, p: Int): Deeds.Act? {
        g.deeds(p).filter { g.canSell(it) }.minByOrNull { Deeds.PRICE[it] }?.let { return A(Deeds.Act.SELL, it) }
        g.deeds(p).filter { g.canPawn(it) }.minByOrNull { (if (g.fullDomain(it)) 1000 else 0) + Deeds.PRICE[it] }?.let { return A(Deeds.Act.PAWN, it) }
        return null
    }

    private fun decide(g: Deeds, p: Int, q: Int, lv: Int, rnd: Random, sloppy: Boolean, reserve: Int, hpVal: Int): Deeds.Act {
        val t = g.pos[p]
        when (g.pType) {
            Deeds.D_BUY -> {
                val price = Deeds.PRICE[t]
                if (g.gold[p] < price) return A(Deeds.Act.PASS)
                val d = Deeds.GROUP[t]
                val completes = d >= 0 && g.domainTiles(d).all { it == t || g.owner[it] == p }
                val blocks = d >= 0 && g.domainTiles(d).any { g.owner[it] == q }
                if (sloppy) return A(if (rnd.nextBoolean()) Deeds.Act.BUY else Deeds.Act.PASS)
                return A(if (g.gold[p] - price >= reserve || ((completes || (blocks && lv >= 2)) && g.gold[p] - price >= 0)) Deeds.Act.BUY else Deeds.Act.PASS)
            }
            Deeds.D_HOMAGE -> {
                val amt = g.pAmount; val dmg = g.pDmg
                if (g.items[p][Deeds.WRIT] > 0 && (amt >= 150 || g.hp[p] <= dmg)) return A(Deeds.Act.WRIT)
                val deadly = g.hp[p] - dmg <= 0 || (g.hp[p] - dmg <= 2 && lv >= 2)
                if (g.gold[p] >= amt && (deadly || amt <= dmg * hpVal || sloppy)) return A(Deeds.Act.PAY)
                if (g.gold[p] < amt && deadly) raise(g, p)?.let { return it }
                return A(Deeds.Act.REFUSE)
            }
            Deeds.D_BLADE -> return A(if (g.items[p][Deeds.WRIT] > 0 && (g.pDmg >= 3 || g.hp[p] <= g.pDmg)) Deeds.Act.WRIT else Deeds.Act.BLADE)
            Deeds.D_DEBT -> {
                val w = g.shortfallWounds(p, g.pAmount)
                if (g.hp[p] - w <= (if (lv >= 2) 2 else 0)) raise(g, p)?.let { return it }
                return A(Deeds.Act.DEBT)
            }
            Deeds.D_ARENA -> {
                if (sloppy) return A(if (rnd.nextBoolean()) Deeds.Act.CHALLENGE else Deeds.Act.DECLINE)
                val gain = g.deeds(q).maxOfOrNull { g.spoilValue(p, it) } ?: 0
                val loss = g.deeds(p).maxOfOrNull { g.spoilValue(q, it) } ?: 0
                val edge = (if (g.hero[p] == Deeds.KNIGHT) 120 else 0) - (if (g.hero[q] == Deeds.KNIGHT) 120 else 0)
                val safe = g.hp[p] > 2
                return A(if (safe && gain + edge > loss + 50) Deeds.Act.CHALLENGE else Deeds.Act.DECLINE)
            }
            else -> return A(Deeds.Act.ACK)
        }
    }
}
