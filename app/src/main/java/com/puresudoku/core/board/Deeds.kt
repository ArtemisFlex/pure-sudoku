package com.puresudoku.core.board

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Deed or Dungeon: a medieval game of lands and lordship for two to four lords, each a person or an AI.
 *
 * Ride a ring of 36 tiles around Eiseria with two dice. Once past the Spire, buy deeds to the land you stop on, or let them go to auction.
 * Every deed carries a two-sided coin: the Crown face asks a visitor for homage in gold (refuse, and your towers
 * strike them); the Blade face simply wounds them. You turn your coins only when you pass the Spire. Lose all your
 * health and you are felled: the lord who felled you seizes your best deed and you wake in the Dungeon beneath the
 * Colosseum. A lord felled with no deeds left is vanquished. Trade deeds, buy keepsakes at the Royal Market, and use
 * your champion's great deed once a game. Hold enough whole domains to be crowned at once; otherwise the old King dies
 * when the last round ends, and the lord with the most renown is crowned King of Eiseria.
 */
class Deeds {
    /** A proposed exchange: [from] gives [give] deeds and [giveGold], and gets [get] deeds and [getGold] from [to]. */
    data class Offer(val from: Int, val to: Int, val give: List<Int>, val get: List<Int>, val giveGold: Int, val getGold: Int)

    /** One action a lord can take (the AI answers with these too). */
    data class Act(val type: Int, val arg: Int = -1, val offer: Offer? = null) {
        companion object {
            const val ROLL = 0; const val BUY = 1; const val PASS = 2; const val PAY = 3; const val REFUSE = 4; const val BLADE = 5
            const val WRIT = 6; const val DEBT = 7; const val CHALLENGE = 8; const val DECLINE = 9; const val ACK = 10; const val BUILD = 11
            const val SELL = 12; const val PAWN = 13; const val REDEEM = 14; const val FLIP = 15; const val POTION = 16; const val SIEGE = 17
            const val BRIBE = 18; const val KEY = 19; const val END = 20; const val CHOOSE = 21; const val SEATS = 22; const val BID = 23
            const val FOLD = 24; const val PROPOSE = 25; const val ACCEPT = 26; const val REJECT = 27; const val SHOP = 28; const val LEAVE = 29
            const val DEED = 30
        }
    }

    companion object {
        const val N = 36
        const val MAX = 4
        // tile kinds
        const val SPIRE = 0; const val ARENA = 2; const val SEIZE = 3; const val LAND = 4
        const val BRIDGE = 5; const val MILL = 6; const val FATE = 7; const val LOOT = 8; const val TITHE = 9; const val LEVY = 10; const val MARKET = 11
        /** The Spire is top left; the Inquisitor top right; the Colosseum, with the Dungeon beneath it, bottom right; the Royal Market bottom left. */
        const val SEIZE_TILE = 9; const val ARENA_TILE = 18; const val DUNGEON_TILE = 18; const val MARKET_TILE = 27

        // phases
        const val LOBBY = 5; const val SETUP = 0; const val ROLL = 1; const val DECIDE = 2; const val AFTER = 3; const val OVER = 4
        const val AUCTION = 6; const val TRADE = 7

        // decisions waiting on the player to move
        const val D_NONE = 0; const val D_BUY = 1; const val D_HOMAGE = 2; const val D_BLADE = 3; const val D_DEBT = 4; const val D_ARENA = 5; const val D_CARD = 6; const val D_MARKET = 7

        // items kept from loot
        const val POTION = 0; const val KEY = 1; const val SHIELD = 2; const val WRIT = 3; const val SIEGE = 4; const val BANNER = 5
        val ITEM_NAME = listOf("Healing Potion", "Skeleton Key", "Tower Shield", "Forged Writ", "Siege Engine", "Royal Banner")
        val ITEM_TEXT = listOf(
            "Drink on your turn: heal 4.",
            "Walk out of the Dungeon for free.",
            "Kept until needed: blocks the next wounds you would take.",
            "Show it to skip one homage or one blade, just once.",
            "Topple a tower on a rival's strongest land.",
            "Turn one of your coins on your turn, without passing the Spire.",
        )
        val ITEM_PRICE = intArrayOf(80, 60, 120, 150, 150, 100)

        // champions
        const val DRAGON = 0; const val KNIGHT = 1; const val THIEF = 2; const val BLACK = 3
        val HERO = listOf("Dragon", "Knight", "Veilnight Thief", "Black Knight")
        val HERO_POWER = listOf(
            "Dragonfire: your Blade lands wound 1 more.",
            "Plate armour: 12 health, and +1 in trials by combat.",
            "Light fingers: you pay a quarter less homage.",
            "Siegemaster: your towers cost a quarter less.",
        )
        val DEED_NAME = listOf("Inferno", "Second Wind", "The Heist", "Reign of Terror")
        val DEED_TEXT = listOf(
            "Once a game: burn every tower on one rival land.",
            "Once a game: heal to full and take up a Tower Shield.",
            "Once a game: steal a rival's unfortified deed worth up to 240 gold.",
            "Once a game: every rival lord takes 2 wounds.",
        )
        val COLOR = listOf("Natural", "Azure", "Crimson", "Emerald", "Amber", "Violet")

        const val COIN_CROWN = 0; const val COIN_BLADE = 1

        val KIND = intArrayOf(
            SPIRE, LAND, LOOT, LAND, BRIDGE, LAND, FATE, LAND, LAND,
            SEIZE, LAND, MILL, LAND, LAND, BRIDGE, LAND, LOOT, LAND,
            ARENA, LAND, FATE, LAND, LAND, BRIDGE, LAND, MILL, LAND,
            MARKET, LAND, TITHE, LAND, BRIDGE, FATE, LAND, LEVY, LAND,
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

        /** Eiseria's lands, always the same. */
        val NAMES = arrayOf(
            "The Spire", "Pigsty Hollow", "Loot", "Turnip Row", "Ferryman's Bridge", "Millbrook", "Fate", "Thatchwick", "Ploughmead",
            "The Inquisitor", "Wolfden", "The Windmill", "Old Oakhurst", "Thornwick", "Troll Bridge", "Gallows Hill", "Loot", "Ravenscar",
            "The Colosseum", "Harbour Keep", "Fate", "Fishwife Quay", "Tidewatch", "Old Stone Bridge", "Quarry Gate", "The Silver Mine", "Ironforge",
            "The Royal Market", "Bishop's Close", "The Abbey's Tithe", "Cathedral Square", "Kingsbridge", "Fate", "Throne Hill", "The King's Levy", "Golden Keep",
        )

        // Fate: events, mostly medieval and some ironic
        val FATE_TEXT = listOf(
            "A bard sings of your deeds. Badly. The crowd pays him to stop and he shares: collect 50 gold.",
            "The plague doctor sends his bill. You never had the plague. Pay 100 gold.",
            "The King summons you to the Spire. He has forgotten why. Advance to the Spire.",
            "Your horse throws you into a moat. The horse is fine. Lose 2 health.",
            "Witch trial! You floated. Down to the Dungeon.",
            "A dragon ate the other lords' sheep. Somehow they blame you. Pay each of them 40 gold.",
            "You win the tournament! Every other lord pays you 40 gold for taking part.",
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
            "Robbers on the King's road! The richest lord loses a tenth of their gold.",
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
        const val NEXT_SETTLE = 0; const val NEXT_MOVE = 1
        const val START_GOLD = 1500
        const val STIPEND = 200
        const val BRIBE = 50
        const val GOLD_PER_WOUND = 50
        val BID_STEPS = intArrayOf(10, 50, 100)

        fun newGame(level: Int, rnd: Random): Deeds {
            val g = Deeds()
            g.level = level.coerceIn(0, 5)
            g.maxRounds = ROUNDS[g.level]
            g.fate = IntArray(FATE_TEXT.size) { it }.also { it.shuffle(rnd) }
            g.loot = IntArray(LOOT_TEXT.size) { it }.also { it.shuffle(rnd) }
            g.phase = LOBBY
            return g
        }

        private fun ia(o: JSONObject, k: String, n: Int, def: Int = 0) = o.optJSONArray(k).let { a -> IntArray(n) { if (a != null && it < a.length()) a.optInt(it, def) else def } }

        fun fromJson(o: JSONObject): Deeds {
            val g = Deeds()
            g.level = o.optInt("level"); g.maxRounds = o.optInt("maxRounds", ROUNDS[g.level]); g.n = o.optInt("n", 2)
            g.owner = ia(o, "owner", N, -1); g.towers = ia(o, "towers", N); g.coin = ia(o, "coin", N); g.pawned = ia(o, "pawned", N)
            g.pos = ia(o, "pos", MAX); g.gold = ia(o, "gold", MAX); g.hp = ia(o, "hp", MAX); g.hero = ia(o, "hero", MAX, -1); g.color = ia(o, "color", MAX, -1)
            g.jail = ia(o, "jail", MAX, -1); g.felled = ia(o, "felled", MAX); g.out = ia(o, "out", MAX); g.ai = ia(o, "ai", MAX); g.used = ia(o, "used", MAX)
            g.items = Array(MAX) { ia(o, "items$it", 6) }
            g.actor = o.optInt("actor"); g.round = o.optInt("round", 1); g.doubles = o.optInt("doubles"); g.phase = o.optInt("phase")
            g.pType = o.optInt("pType"); g.pAmount = o.optInt("pAmount"); g.pDmg = o.optInt("pDmg"); g.pTile = o.optInt("pTile", -1); g.pCard = o.optInt("pCard", -1)
            g.pTo = o.optInt("pTo", -1); g.pWhat = o.optString("pWhat", ""); g.pNext = o.optInt("pNext"); g.felledThisTurn = o.optBoolean("felledThisTurn")
            g.d1 = o.optInt("d1", 1); g.d2 = o.optInt("d2", 1); g.rollId = o.optInt("rollId"); g.spireCharge = o.optBoolean("spireCharge")
            g.lapped = ia(o, "lapped", MAX, if (o.has("lapped")) 0 else 1)
            g.again = o.optBoolean("again"); g.purse = o.optInt("purse"); g.omen = o.optInt("omen"); g.winner = o.optInt("winner", -1)
            g.endReason = o.optInt("endReason"); g.lastText = o.optString("lastText", "")
            g.fate = o.optJSONArray("fate").let { a -> IntArray(a?.length() ?: 0) { a!!.getInt(it) } }
            g.loot = o.optJSONArray("loot").let { a -> IntArray(a?.length() ?: 0) { a!!.getInt(it) } }
            if (g.fate.isEmpty()) g.fate = IntArray(FATE_TEXT.size) { it }
            if (g.loot.isEmpty()) g.loot = IntArray(LOOT_TEXT.size) { it }
            g.fateAt = o.optInt("fateAt"); g.lootAt = o.optInt("lootAt")
            g.aTile = o.optInt("aTile", -1); g.aBid = o.optInt("aBid"); g.aHigh = o.optInt("aHigh", -1); g.aTurn = o.optInt("aTurn"); g.aIn = ia(o, "aIn", MAX)
            g.offer = o.optJSONObject("offer")?.let { t ->
                fun list(k: String) = t.optJSONArray(k).let { a -> List(a?.length() ?: 0) { a!!.getInt(it) } }
                Offer(t.getInt("from"), t.getInt("to"), list("give"), list("get"), t.optInt("giveGold"), t.optInt("getGold"))
            }
            g.tradeBack = o.optInt("tradeBack", AFTER); g.offersThisTurn = o.optInt("offersThisTurn")
            g.dealt = o.optInt("dealt")
            return g
        }
    }

    var level = 0
    var maxRounds = 24
    /** Lords at the table, 2 to 4. */
    var n = 2
    val names: Array<String> get() = NAMES
    var owner = IntArray(N) { -1 }
    var towers = IntArray(N)
    var coin = IntArray(N)
    var pawned = IntArray(N)

    var pos = IntArray(MAX)
    var gold = IntArray(MAX) { START_GOLD }
    var hp = IntArray(MAX) { 10 }
    var hero = IntArray(MAX) { -1 }
    var color = IntArray(MAX) { -1 }
    /** Turns spent in the Dungeon so far, or -1 when free. */
    var jail = IntArray(MAX) { -1 }
    var items = Array(MAX) { IntArray(6) }
    var felled = IntArray(MAX)
    /** 1 once a lord is vanquished. */
    var out = IntArray(MAX)
    /** 1 for an AI lord. */
    var ai = IntArray(MAX)
    /** 1 once a lord has used their champion's great deed. */
    var used = IntArray(MAX)

    var actor = 0
    var round = 1
    var doubles = 0
    var phase = LOBBY
    /** How many champions have been chosen so far. */
    var dealt = 0
    var pType = D_NONE
    var pAmount = 0
    var pDmg = 0
    var pTile = -1
    var pCard = -1
    /** A pending debt: who is owed (-1 the purse), what for, and what happens once it's paid. */
    var pTo = -1
    var pWhat = ""
    var pNext = 0
    var felledThisTurn = false
    var d1 = 1
    var d2 = 1
    var rollId = 0
    /** Passed the Spire this turn: you may turn your coins until the turn ends. */
    var spireCharge = false
    /** 1 once a lord has passed (or landed on) the Spire: only then may they buy deeds or bid at auction, like Monopoly's first lap. */
    var lapped = IntArray(MAX)
    /** Rolled doubles: roll again after this landing. */
    var again = false
    /** Taxes and fines wait in the Colosseum for whoever lands there. */
    var purse = 0
    var omen = 0
    /** The new King, -1 while the game goes on, -2 for a tie. */
    var winner = -1
    /** 1 the others vanquished, 2 enough whole domains, 3 the old King died (most renown). */
    var endReason = 0
    var fate = IntArray(0)
    var fateAt = 0
    var loot = IntArray(0)
    var lootAt = 0
    /** The last card shown, for the screen. */
    var lastText = ""

    // auction
    var aTile = -1
    var aBid = 0
    var aHigh = -1
    var aTurn = 0
    var aIn = IntArray(MAX)

    // trading
    var offer: Offer? = null
    var tradeBack = AFTER
    var offersThisTurn = 0

    val log = ArrayList<String>()

    fun toJson(): JSONObject {
        fun a(x: IntArray) = JSONArray().also { j -> x.forEach { j.put(it) } }
        fun l(x: List<Int>) = JSONArray().also { j -> x.forEach { j.put(it) } }
        val o = JSONObject()
            .put("level", level).put("maxRounds", maxRounds).put("n", n)
            .put("owner", a(owner)).put("towers", a(towers)).put("coin", a(coin)).put("pawned", a(pawned))
            .put("pos", a(pos)).put("gold", a(gold)).put("hp", a(hp)).put("hero", a(hero)).put("color", a(color))
            .put("jail", a(jail)).put("felled", a(felled)).put("out", a(out)).put("ai", a(ai)).put("used", a(used))
            .put("actor", actor).put("round", round).put("doubles", doubles).put("phase", phase).put("dealt", dealt)
            .put("pType", pType).put("pAmount", pAmount).put("pDmg", pDmg).put("pTile", pTile).put("pCard", pCard)
            .put("pTo", pTo).put("pWhat", pWhat).put("pNext", pNext).put("felledThisTurn", felledThisTurn)
            .put("d1", d1).put("d2", d2).put("rollId", rollId).put("spireCharge", spireCharge).put("lapped", a(lapped)).put("again", again)
            .put("purse", purse).put("omen", omen).put("winner", winner).put("endReason", endReason).put("lastText", lastText)
            .put("fate", a(fate)).put("fateAt", fateAt).put("loot", a(loot)).put("lootAt", lootAt)
            .put("aTile", aTile).put("aBid", aBid).put("aHigh", aHigh).put("aTurn", aTurn).put("aIn", a(aIn))
            .put("tradeBack", tradeBack).put("offersThisTurn", offersThisTurn)
        for (p in 0 until MAX) o.put("items$p", a(items[p]))
        offer?.let { t -> o.put("offer", JSONObject().put("from", t.from).put("to", t.to).put("give", l(t.give)).put("get", l(t.get)).put("giveGold", t.giveGold).put("getGold", t.getGold)) }
        return o
    }

    fun copy(): Deeds = fromJson(toJson())

    // ------------------------------------------------------------------ facts

    val players: List<Int> get() = (0 until n).toList()
    val alive: List<Int> get() = (0 until n).filter { out[it] == 0 }
    fun rivals(p: Int) = alive.filter { it != p }
    fun maxHp(p: Int) = if (hero[p] == KNIGHT) 12 else 10
    /** Whoever must act now: the lord whose turn it is, or the one bidding or answering an offer. */
    val decider: Int get() = when (phase) { AUCTION -> aTurn; TRADE -> offer?.to ?: actor; else -> actor }

    fun title(p: Int): String {
        val h = hero[p]
        if (h < 0) return "lord ${p + 1}"
        val c = color[p]
        val twin = (0 until n).any { it != p && hero[it] == h }
        return "the " + (if (c > 0) COLOR[c] + " " else if (twin) "Natural " else "") + HERO[h]
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
    /** Whole domains that crown a lord at once: four between two lords, three among more. */
    val domainsToWin: Int get() = if (n <= 2) 4 else 3
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

    /** Homage owed on [t] (before the Thief's discount), [dice] for mills. */
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
        var d = if (KIND[t] == LAND) 1 + towers[t] else 1
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

    fun deedValue(t: Int) = (if (pawned[t] == 1) PRICE[t] / 2 else PRICE[t]) + towers[t] * TOWER_COST[GROUP[t].coerceAtLeast(0)]
    fun renown(p: Int): Int = gold[p] + deeds(p).sumOf { deedValue(it) }

    fun stipend(): Int = when (omen) { 3 -> STIPEND / 2; 4 -> STIPEND * 2; else -> STIPEND }
    private fun fine(x: Int) = if (omen == 6) x * 2 else x
    private fun say(s: String) { log += s }

    // ------------------------------------------------------------------ the table and the champions

    /** Seat the lords: [count] of them, with [aiMask] bit k set when seat k is an AI. Seat 0 is always a person. */
    fun seats(count: Int, aiMask: Int): Boolean {
        if (phase != LOBBY || count !in 2..MAX) return false
        n = count
        for (p in 0 until MAX) ai[p] = if (p in 1 until n && (aiMask shr p) and 1 == 1) 1 else 0
        phase = SETUP; actor = 0; dealt = 0
        return true
    }

    fun colorFree(p: Int, c: Int) = (0 until n).none { it != p && color[it] == c }
    fun canChoose(p: Int, h: Int, c: Int): Boolean = phase == SETUP && p == actor && h in 0..3 && c in COLOR.indices && colorFree(p, c)

    fun choose(h: Int, c: Int): Boolean {
        if (!canChoose(actor, h, c)) return false
        hero[actor] = h; color[actor] = c; hp[actor] = maxHp(actor)
        dealt++
        say("${Title(actor)} takes the field.")
        if (dealt >= n) { actor = 0; phase = ROLL; say("Round 1. ${Title(0)} rolls first.") } else actor++
        return true
    }

    // ------------------------------------------------------------------ rolling and moving

    val inDungeon: Boolean get() = jail[actor] >= 0

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

    private fun moveTo(p: Int, to: Int) {
        if (to < pos[p]) passSpire(p)
        pos[p] = to
        land(p)
    }

    private fun passSpire(p: Int) {
        val s = stipend()
        gold[p] += s
        if (omen != 8) heal(p, 1, quiet = true)
        spireCharge = true; lapped[p] = 1
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
            SPIRE -> { gold[p] += 100; lapped[p] = 1; say("${Title(p)} lands right on the Spire: the King adds 100 gold."); settle() }
            SEIZE -> {
                say("The Inquisitor seizes ${title(p)}! Down to the Dungeon.")
                toDungeon(p); settle()
            }
            ARENA -> {
                if (purse > 0) { say("${Title(p)} wins the Colosseum purse: $purse gold!"); gold[p] += purse; purse = 0 }
                else say("${Title(p)} enters the Colosseum. The purse is empty.")
                if (deeds(p).isNotEmpty() && rivals(p).any { deeds(it).isNotEmpty() }) decide(D_ARENA) else settle()
            }
            MARKET -> { say("${Title(p)} rides into the Royal Market."); decide(D_MARKET) }
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
            o < 0 && lapped[p] == 0 -> { say("${Title(p)} hasn't passed the Spire yet, so ${names[t]} can't be bought this time."); settle() }
            o < 0 -> decide(D_BUY)
            o == p -> { say("${Title(p)} rests at their own ${names[t]}."); settle() }
            pawned[t] == 1 -> { say("${names[t]} is pawned to the moneylender. Nothing is owed."); settle() }
            face(t) == COIN_CROWN -> { pAmount = homageFor(t, p); pDmg = refuseDmg(t); decide(D_HOMAGE) }
            else -> {
                pDmg = bladeDmg(t)
                if (items[p][WRIT] > 0 || items[p][SHIELD] > 0) decide(D_BLADE)
                else { say("The Blade! ${names[t]}'s guards strike ${title(p)} for $pDmg."); wound(p, pDmg, o); settle() }
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

    private fun next(p: Int, n: Int) { if (n == NEXT_MOVE && phase != OVER && !felledThisTurn) move(p, d1 + d2) else settle() }

    /** [p] owes [x]: paid at once if they can, otherwise they choose to raise funds or pay the shortfall in blood. */
    private fun owe(p: Int, x: Int, to: Int, what: String, then: Int = NEXT_SETTLE) {
        if (gold[p] >= x || p != actor) { bleedPay(p, x, to, what); next(p, then); return }
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

    /** [n] wounds to [p], dealt by [by] (-1 = the realm). Felled at 0 health. */
    fun wound(p: Int, n: Int, by: Int) {
        if (n <= 0 || out[p] == 1) return
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
            hp[p] = 0; out[p] = 1; jail[p] = -1
            purse += gold[p]; gold[p] = 0
            say("${Title(p)} falls with no lands left to lose, and is vanquished.")
            val left = alive
            if (left.size == 1) { say("${Title(left[0])} is the last lord standing, and is crowned King of Eiseria!"); end(left[0], 1) }
            else if (p == actor) felledThisTurn = true
            return
        }
        if (by >= 0 && by != p && out[by] == 0) {
            val t = mine.maxBy { spoilValue(by, it) }
            owner[t] = by
            say("${Title(p)} is felled! ${Title(by)} seizes ${names[t]}" + (if (towers[t] > 0) " and its ${towerWord(towers[t])}" else "") + ".")
        } else {
            val t = mine.minBy { PRICE[it] + towers[it] * 100 }
            owner[t] = -1; towers[t] = 0; pawned[t] = 0; coin[t] = COIN_CROWN
            say("${Title(p)} is felled! ${names[t]} returns to the realm.")
        }
        hp[p] = maxHp(p)
        toDungeon(p)
        if (p == actor) felledThisTurn = true
        say("${Title(p)} wakes in the Dungeon beneath the Colosseum, healed.")
        checkCoronation()
    }

    fun towerWord(n: Int) = if (n >= 4) "castle" else if (n == 1) "tower" else "$n towers"

    /** How much [q] would like to own [t]. */
    fun spoilValue(q: Int, t: Int): Int {
        var v = PRICE[t] + towers[t] * TOWER_COST[GROUP[t].coerceAtLeast(0)]
        val d = GROUP[t]
        if (d >= 0) {
            val tiles = domainTiles(d)
            if (tiles.all { it == t || owner[it] == q }) v += 400
            else if (tiles.count { owner[it] == q } >= 1) v += 80
            if (owner[t] >= 0 && owner[t] != q && tiles.all { owner[it] == owner[t] }) v += 150
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
        gold[p] -= PRICE[t]
        grant(p, t, rnd, "buys the deed to ${names[t]} for ${PRICE[t]} gold")
        settle(); checkCoronation()
        return true
    }

    private fun grant(p: Int, t: Int, rnd: Random, how: String) {
        owner[t] = p
        coin[t] = rnd.nextInt(2)
        say("${Title(p)} $how. The coin lands on its ${if (coin[t] == COIN_CROWN) "Crown" else "Blade"}!")
        if (fullDomain(t)) say("${Title(p)} now holds all of ${DOMAIN[GROUP[t]]}!")
    }

    /** Pass on a deed: it goes to auction among every lord. */
    fun pass(): Boolean {
        if (phase != DECIDE || pType != D_BUY) return false
        val t = pos[actor]
        say("${Title(actor)} passes on ${names[t]}. To auction!")
        pType = D_NONE
        aTile = t; aBid = 0; aHigh = -1
        // only lords who have passed the Spire may bid
        for (p in 0 until MAX) aIn[p] = if (p < n && out[p] == 0 && lapped[p] == 1) 1 else 0
        aTurn = nextIn(actor)
        phase = AUCTION
        auctionCheck()
        return true
    }

    private fun nextIn(from: Int): Int {
        var q = from
        repeat(MAX) { q = (q + 1) % n; if (aIn[q] == 1 && q != aHigh) return q }
        return if (aHigh >= 0) aHigh else from
    }

    /** The smallest raise the bidder may make. */
    fun minBid() = aBid + BID_STEPS[0]

    fun canBid(step: Int): Boolean = phase == AUCTION && step in BID_STEPS.indices && gold[aTurn] >= aBid + BID_STEPS[step]

    fun bid(step: Int): Boolean {
        if (!canBid(step)) return false
        aBid += BID_STEPS[step]; aHigh = aTurn
        say("${Title(aTurn)} bids $aBid gold.")
        val bidder = aTurn
        aTurn = nextIn(bidder)
        auctionCheck()
        return true
    }

    fun fold(): Boolean {
        if (phase != AUCTION) return false
        aIn[aTurn] = 0
        say("${Title(aTurn)} drops out of the bidding.")
        aTurn = nextIn(aTurn)
        auctionCheck()
        return true
    }

    private var auctionRnd: Random = Random(0)

    private fun auctionCheck() {
        val inside = (0 until n).filter { aIn[it] == 1 }
        val done = inside.isEmpty() || (inside.size == 1 && inside[0] == aHigh) || (aHigh >= 0 && inside.all { it == aHigh })
        if (!done) return
        val t = aTile
        if (aHigh >= 0) {
            gold[aHigh] -= aBid
            grant(aHigh, t, auctionRnd, "wins the auction for ${names[t]} at $aBid gold")
        } else say("Nobody bids. ${names[t]} stays unclaimed.")
        aTile = -1; aHigh = -1; aBid = 0
        phase = AFTER; pType = D_NONE
        checkCoronation()
    }

    fun payHomage(): Boolean {
        if (phase != DECIDE || pType != D_HOMAGE) return false
        val p = actor; val t = pos[p]
        if (gold[p] < pAmount) return false
        pay(p, pAmount, owner[t])
        say("${Title(p)} kneels and pays $pAmount gold homage to ${title(owner[t])}.")
        settle(); return true
    }

    fun refuse(): Boolean {
        if (phase != DECIDE || pType != D_HOMAGE) return false
        val p = actor; val t = pos[p]
        say("${Title(p)} refuses homage! The towers of ${names[t]} strike for $pDmg.")
        wound(p, pDmg, owner[t])
        settle(); return true
    }

    fun takeBlade(): Boolean {
        if (phase != DECIDE || pType != D_BLADE) return false
        val p = actor; val t = pos[p]
        say("The Blade! ${names[t]}'s guards strike ${title(p)} for $pDmg.")
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

    fun canChallenge(q: Int) = phase == DECIDE && pType == D_ARENA && q != actor && q in alive && deeds(q).isNotEmpty()

    fun challenge(q: Int, rnd: Random): Boolean {
        if (!canChallenge(q)) return false
        val p = actor
        var a: Int; var b: Int
        val bonusA = if (hero[p] == KNIGHT) 1 else 0
        val bonusB = if (hero[q] == KNIGHT) 1 else 0
        do { a = rnd.nextInt(1, 7) + rnd.nextInt(1, 7) + bonusA; b = rnd.nextInt(1, 7) + rnd.nextInt(1, 7) + bonusB } while (a == b)
        val win = if (a > b) p else q
        val lose = if (win == p) q else p
        say("Trial by combat! ${Title(p)} rolls $a, ${title(q)} rolls $b.")
        val spoils = deeds(lose).maxByOrNull { spoilValue(win, it) }
        if (spoils != null) {
            owner[spoils] = win
            say("${Title(win)} wins and takes ${names[spoils]}" + (if (towers[spoils] > 0) " with its ${towerWord(towers[spoils])}" else "") + "!")
        }
        wound(lose, 2, win)
        if (phase != OVER) { settle(); checkCoronation() }
        return true
    }

    fun decline(): Boolean {
        if (phase != DECIDE || pType != D_ARENA) return false
        say("${Title(actor)} waves to the crowd and leaves the arena.")
        settle(); return true
    }

    fun canShop(i: Int) = phase == DECIDE && pType == D_MARKET && i in 0..5 && gold[actor] >= ITEM_PRICE[i]
    fun shop(i: Int): Boolean {
        if (!canShop(i)) return false
        gold[actor] -= ITEM_PRICE[i]; items[actor][i]++
        say("${Title(actor)} buys a ${ITEM_NAME[i]} for ${ITEM_PRICE[i]} gold.")
        settle(); return true
    }
    fun leave(): Boolean {
        if (phase != DECIDE || pType != D_MARKET) return false
        say("${Title(actor)} haggles, sighs, and buys nothing.")
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
        val p = actor
        say("Fate: " + FATE_TEXT[c])
        when (c) {
            0 -> { gold[p] += 50; settle() }
            1 -> owe(p, fine(100), -1, "the plague doctor")
            2 -> { passSpire(p); pos[p] = 0; settle() }
            3 -> { wound(p, 2, -1); if (phase != OVER) settle() }
            4 -> { toDungeon(p); settle() }
            5 -> {
                val others = rivals(p)
                if (gold[p] >= 40 * others.size) { for (q in others) pay(p, 40, q); say("${Title(p)} pays ${40 * others.size} gold for the sheep."); settle() }
                else owe(p, 40 * others.size, -1, "the eaten sheep")
            }
            6 -> { for (q in rivals(p)) bleedPay(q, 40, p, "the tournament"); if (phase != OVER) settle() }
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
                if (t != null) { grant(p, t, rnd, "claims ${names[t]}"); checkCoronation() }
                else say("Every land is already claimed. The deed is a forgery after all.")
                if (phase != OVER) settle()
            }
            15 -> { gold[p] += 150; settle() }
            16 -> owe(p, fine(40), -1, "the shoeing tax")
            17 -> {
                val rich = alive.maxBy { gold[it] }
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
        if (phase != OVER) settle()
    }

    // ------------------------------------------------------------------ managing your lands

    private fun mayManage(p: Int) = (phase == ROLL || phase == AFTER || phase == DECIDE) && p == actor

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
        return towers[t] >= domainTiles(GROUP[t]).maxOf { towers[it] }
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

    fun siegeTarget(): Int = rivals(actor).flatMap { deeds(it) }.filter { towers[it] > 0 }.maxByOrNull { towers[it] * 1000 + PRICE[it] } ?: -1
    fun canSiege() = mayManage(actor) && phase != DECIDE && items[actor][SIEGE] > 0 && siegeTarget() >= 0
    fun siege(): Boolean {
        if (!canSiege()) return false
        val t = siegeTarget()
        items[actor][SIEGE]--; towers[t]--
        say("${Title(actor)}'s Siege Engine topples a tower at ${names[t]}!")
        return true
    }

    // ------------------------------------------------------------------ the champion's great deed

    /** Lands the great deed can be aimed at, or empty for one that needs no aim. */
    fun deedTargets(p: Int = actor): List<Int> = when (hero[p]) {
        DRAGON -> rivals(p).flatMap { deeds(it) }.filter { towers[it] > 0 }
        THIEF -> rivals(p).flatMap { deeds(it) }.filter { towers[it] == 0 && PRICE[it] <= 240 && (GROUP[it] < 0 || domainTiles(GROUP[it]).none { d -> towers[d] > 0 }) }
        else -> emptyList()
    }
    fun deedAims(p: Int = actor) = hero[p] == DRAGON || hero[p] == THIEF

    fun canGreatDeed(t: Int = -1): Boolean {
        val p = actor
        if (!(phase == ROLL || phase == AFTER) || used[p] == 1 || hero[p] < 0) return false
        return when (hero[p]) {
            DRAGON, THIEF -> t in deedTargets(p)
            KNIGHT -> true
            else -> rivals(p).isNotEmpty()
        }
    }

    fun greatDeed(t: Int): Boolean {
        if (!canGreatDeed(t)) return false
        val p = actor
        used[p] = 1
        when (hero[p]) {
            DRAGON -> { val was = towers[t]; towers[t] = 0; say("Inferno! ${Title(p)} burns ${names[t]}: its ${towerWord(was)} fall${if (was == 1) "s" else ""} in flames.") }
            KNIGHT -> { hp[p] = maxHp(p); items[p][SHIELD]++; say("Second Wind! ${Title(p)} rises at full health and takes up a Tower Shield.") }
            THIEF -> { val from = owner[t]; owner[t] = p; say("The Heist! ${Title(p)} steals the deed to ${names[t]} from ${title(from)}.") }
            BLACK -> {
                say("Reign of Terror! ${Title(p)}'s riders strike every rival lord for 2.")
                for (q in rivals(p)) if (phase != OVER) wound(q, 2, p)
            }
        }
        if (phase != OVER) checkCoronation()
        return true
    }

    // ------------------------------------------------------------------ trading

    /** An offer is fair game when both sides own what they put up, nothing traded has towers, and both can pay. */
    fun validOffer(o: Offer): Boolean {
        if (o.from != actor || o.to == o.from || o.to !in alive) return false
        if (o.give.isEmpty() && o.get.isEmpty()) return false
        if (o.give.any { owner[it] != o.from || towers[it] > 0 } || o.get.any { owner[it] != o.to || towers[it] > 0 }) return false
        if (o.giveGold < 0 || o.getGold < 0 || o.giveGold > gold[o.from] || o.getGold > gold[o.to]) return false
        return true
    }

    fun canPropose() = (phase == ROLL || phase == AFTER) && offersThisTurn < 3

    fun propose(o: Offer): Boolean {
        if (!canPropose() || !validOffer(o)) return false
        offersThisTurn++
        offer = o; tradeBack = phase; phase = TRADE
        say("${Title(o.from)} offers ${title(o.to)} a trade: ${offerText(o)}.")
        return true
    }

    fun offerText(o: Offer): String {
        fun side(d: List<Int>, gld: Int) = (d.map { names[it] } + if (gld > 0) listOf("$gld gold") else emptyList()).ifEmpty { listOf("nothing") }.joinToString(", ")
        return side(o.give, o.giveGold) + " for " + side(o.get, o.getGold)
    }

    fun accept(): Boolean {
        val o = offer ?: return false
        if (phase != TRADE) return false
        offer = null; phase = tradeBack
        if (!validOfferNow(o)) { say("The trade falls through."); return true }
        for (t in o.give) owner[t] = o.to
        for (t in o.get) owner[t] = o.from
        gold[o.from] += o.getGold - o.giveGold; gold[o.to] += o.giveGold - o.getGold
        say("${Title(o.to)} accepts! The deeds change hands.")
        checkCoronation()
        return true
    }

    private fun validOfferNow(o: Offer) = o.give.all { owner[it] == o.from && towers[it] == 0 } && o.get.all { owner[it] == o.to && towers[it] == 0 } &&
        gold[o.from] >= o.giveGold && gold[o.to] >= o.getGold

    fun reject(): Boolean {
        val o = offer ?: return false
        if (phase != TRADE) return false
        offer = null; phase = tradeBack
        say("${Title(o.to)} turns the offer down.")
        return true
    }

    // ------------------------------------------------------------------ the turn's end

    fun canEnd() = phase == AFTER

    fun endTurn(rnd: Random): Boolean {
        if (phase != AFTER) return false
        spireCharge = false
        pTile = -1
        offersThisTurn = 0
        if (again && jail[actor] < 0 && !felledThisTurn && out[actor] == 0) {
            again = false
            phase = ROLL
            say("${Title(actor)} rides on.")
            return true
        }
        felledThisTurn = false
        again = false; doubles = 0
        if (checkCoronation()) return true
        var q = actor
        do {
            q = (q + 1) % n
            if (q == 0) {
                round++
                if (round > maxRounds) { kingDies(); return true }
                if ((round - 1) % OMEN_EVERY == 0) {
                    var o: Int
                    do { o = rnd.nextInt(OMEN.size) } while (o == omen)
                    omen = o
                    say("A new omen: ${OMEN[o]}. ${OMEN_TEXT[o]}")
                }
            }
        } while (out[q] == 1)
        actor = q
        phase = ROLL
        return true
    }

    private fun checkCoronation(): Boolean {
        if (phase == OVER) return true
        for (p in alive) if (domainsHeld(p) >= domainsToWin) {
            say("${Title(p)} holds $domainsToWin whole domains and is crowned King of Eiseria!")
            end(p, 2); return true
        }
        return false
    }

    private fun kingDies() {
        val ranked = alive.sortedByDescending { renown(it) }
        say("The old King is dead! Renown: " + ranked.joinToString(", ") { "${title(it)} ${renown(it)}" } + ".")
        val best = renown(ranked[0])
        end(if (ranked.size > 1 && renown(ranked[1]) == best) -2 else ranked[0], 3)
    }

    private fun end(w: Int, reason: Int) {
        winner = w; endReason = reason; phase = OVER; pType = D_NONE
    }

    fun perform(a: Act, rnd: Random): Boolean {
        auctionRnd = rnd
        return when (a.type) {
            Act.ROLL -> roll(rnd); Act.BUY -> buy(rnd); Act.PASS -> pass(); Act.PAY -> payHomage(); Act.REFUSE -> refuse()
            Act.BLADE -> takeBlade(); Act.WRIT -> useWrit(); Act.DEBT -> payDebt(); Act.CHALLENGE -> challenge(a.arg, rnd); Act.DECLINE -> decline()
            Act.ACK -> ack(rnd); Act.BUILD -> build(a.arg); Act.SELL -> sell(a.arg); Act.PAWN -> pawn(a.arg); Act.REDEEM -> redeem(a.arg)
            Act.FLIP -> flip(a.arg); Act.POTION -> potion(); Act.SIEGE -> siege(); Act.BRIBE -> bribe(); Act.KEY -> useKey()
            Act.END -> endTurn(rnd); Act.CHOOSE -> choose(a.arg / 10, a.arg % 10); Act.SEATS -> seats(a.arg / 100, a.arg % 100)
            Act.BID -> bid(a.arg); Act.FOLD -> fold(); Act.PROPOSE -> a.offer?.let { propose(it) } ?: false
            Act.ACCEPT -> accept(); Act.REJECT -> reject(); Act.SHOP -> shop(a.arg); Act.LEAVE -> leave(); Act.DEED -> greatDeed(a.arg)
            else -> false
        }
    }
}

/** The AI lords: greedy about land, careful with their health, sharper on harder levels. */
object DeedsAi {
    private fun A(t: Int, arg: Int = -1) = Deeds.Act(t, arg)

    private fun reserve(g: Deeds) = intArrayOf(40, 80, 120, 150, 180, 200)[g.level] + g.round * 4
    private fun hpVal(g: Deeds) = intArrayOf(40, 50, 60, 70, 75, 80)[g.level]

    /** The next action for whoever must act ([Deeds.decider]). */
    fun next(g: Deeds, rnd: Random): Deeds.Act {
        val p = g.decider; val lv = g.level
        val sloppy = lv == 0 && rnd.nextInt(100) < 25
        when (g.phase) {
            Deeds.LOBBY -> return A(Deeds.Act.SEATS, 200 + 0b10)
            Deeds.SETUP -> {
                val taken = (0 until g.n).filter { it != p }.map { g.hero[it] }
                val hero = (0..3).filter { it !in taken }.ifEmpty { (0..3).toList() }.random(rnd)
                val col = (1 until Deeds.COLOR.size).filter { g.colorFree(p, it) }.random(rnd)
                return A(Deeds.Act.CHOOSE, hero * 10 + col)
            }
            Deeds.AUCTION -> return auction(g, p, rnd)
            Deeds.TRADE -> return A(if (acceptable(g, g.offer!!)) Deeds.Act.ACCEPT else Deeds.Act.REJECT)
            Deeds.DECIDE -> return decide(g, p, lv, rnd, sloppy)
        }
        val reserve = reserve(g)
        // manage before rolling or ending
        if (g.canPotion() && g.hp[p] <= g.maxHp(p) - 4) return A(Deeds.Act.POTION)
        if (g.canSiege() && lv >= 1) return A(Deeds.Act.SIEGE)
        greatDeed(g, p, lv)?.let { return it }
        if (g.phase == Deeds.AFTER && g.spireCharge) {
            for (t in g.deeds(p)) { val want = wantFace(g, p, t, lv); if (g.coin[t] != want) return A(Deeds.Act.FLIP, t) }
        }
        for (t in g.deeds(p).sortedByDescending { Deeds.PRICE[it] }) if (g.canRedeem(t) && g.gold[p] - g.redeemCost(t) >= reserve + 300) return A(Deeds.Act.REDEEM, t)
        val buildable = g.deeds(p).filter { g.canBuild(it) && g.gold[p] - g.towerCost(it, p) >= reserve + (if (lv <= 1) 150 else 60) }
        if (buildable.isNotEmpty() && !sloppy) return A(Deeds.Act.BUILD, buildable.maxBy { Deeds.PRICE[it] })
        if (lv >= 1 && g.canPropose() && g.offersThisTurn == 0) tradeIdea(g, p, rnd)?.let { return Deeds.Act(Deeds.Act.PROPOSE, offer = it) }
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

    private fun greatDeed(g: Deeds, p: Int, lv: Int): Deeds.Act? {
        if (g.used[p] == 1 || lv == 0) return null
        return when (g.hero[p]) {
            Deeds.DRAGON -> g.deedTargets(p).filter { g.towers[it] >= 2 }.maxByOrNull { g.towers[it] * 1000 + Deeds.PRICE[it] }?.let { A(Deeds.Act.DEED, it) }
            Deeds.THIEF -> g.deedTargets(p).maxByOrNull { g.spoilValue(p, it) }?.takeIf { g.spoilValue(p, it) >= 220 || g.round > g.maxRounds - 3 }?.let { A(Deeds.Act.DEED, it) }
            Deeds.KNIGHT -> if (g.hp[p] <= g.maxHp(p) - 6) A(Deeds.Act.DEED) else null
            else -> if (g.rivals(p).any { g.hp[it] <= 2 && g.items[it][Deeds.SHIELD] == 0 } || g.round > g.maxRounds - 2) A(Deeds.Act.DEED) else null
        }
    }

    private fun wantFace(g: Deeds, p: Int, t: Int, lv: Int): Int {
        if (lv == 0) return g.coin[t]
        val blade = g.bladeDmg(t)
        val rent = g.homageOf(t, 7)
        val rivals = g.rivals(p)
        return when {
            rivals.any { g.hp[it] <= blade * 2 + (if (lv >= 3) 2 else 0) && g.deeds(it).isNotEmpty() } -> Deeds.COIN_BLADE
            g.hero[p] == Deeds.DRAGON && rent < 40 -> Deeds.COIN_BLADE
            lv >= 3 && rivals.all { g.gold[it] < 200 } -> Deeds.COIN_BLADE
            else -> Deeds.COIN_CROWN
        }
    }

    private fun raise(g: Deeds, p: Int): Deeds.Act? {
        g.deeds(p).filter { g.canSell(it) }.minByOrNull { Deeds.PRICE[it] }?.let { return A(Deeds.Act.SELL, it) }
        g.deeds(p).filter { g.canPawn(it) }.minByOrNull { (if (g.fullDomain(it)) 1000 else 0) + Deeds.PRICE[it] }?.let { return A(Deeds.Act.PAWN, it) }
        return null
    }

    private fun auction(g: Deeds, p: Int, rnd: Random): Deeds.Act {
        val t = g.aTile
        val worth = (g.spoilValue(p, t) * intArrayOf(80, 95, 105, 115, 120, 125)[g.level] / 100) + rnd.nextInt(0, 21)
        val room = g.gold[p] - reserve(g) / 2
        val cap = minOf(worth, room)
        // jump while the price is far below what it's worth, then creep up
        val step = when { cap - g.aBid > 220 -> 2; cap - g.aBid > 90 -> 1; else -> 0 }
        for (s in step downTo 0) if (g.aBid + Deeds.BID_STEPS[s] <= cap && g.canBid(s)) return A(Deeds.Act.BID, s)
        return A(Deeds.Act.FOLD)
    }

    /** Would [o].to take this offer? */
    fun acceptable(g: Deeds, o: Deeds.Offer): Boolean {
        val q = o.to
        var gain = o.giveGold - o.getGold
        gain += o.give.sumOf { g.spoilValue(q, it) }
        gain -= o.get.sumOf { Deeds.PRICE[it] + (if (g.fullDomain(it)) 150 else 0) }
        // never hand a rival the last land of a domain cheaply
        for (t in o.get) {
            val d = Deeds.GROUP[t]
            if (d >= 0 && g.domainTiles(d).all { it == t || g.owner[it] == o.from || it in o.get }) gain -= 450
        }
        return gain > intArrayOf(-40, 0, 40, 60, 80, 100)[g.level]
    }

    /** A gold-for-deed offer that would finish one of [p]'s domains. */
    private fun tradeIdea(g: Deeds, p: Int, rnd: Random): Deeds.Offer? {
        // at most one offer every few rounds, so people aren't pestered
        if ((g.round + p) % 3 != 0 || rnd.nextInt(100) >= 50) return null
        for (d in 0 until Deeds.DOMAINS) {
            val tiles = g.domainTiles(d)
            val missing = tiles.filter { g.owner[it] != p }
            if (missing.size != 1 || tiles.count { g.owner[it] == p } < 1) continue
            val t = missing[0]; val o = g.owner[t]
            if (o < 0 || o == p || g.towers[t] > 0 || g.out[o] == 1) continue
            val price = Deeds.PRICE[t] * 2 + 150 + g.level * 30
            if (g.gold[p] - price < reserve(g)) continue
            val offer = Deeds.Offer(p, o, emptyList(), listOf(t), price, 0)
            if (g.validOffer(offer)) return offer
        }
        return null
    }

    private fun decide(g: Deeds, p: Int, lv: Int, rnd: Random, sloppy: Boolean): Deeds.Act {
        val t = g.pos[p]
        val reserve = reserve(g); val hpVal = hpVal(g)
        when (g.pType) {
            Deeds.D_BUY -> {
                val price = Deeds.PRICE[t]
                if (g.gold[p] < price) return A(Deeds.Act.PASS)
                val d = Deeds.GROUP[t]
                val completes = d >= 0 && g.domainTiles(d).all { it == t || g.owner[it] == p }
                val blocks = d >= 0 && g.domainTiles(d).any { g.owner[it] >= 0 && g.owner[it] != p }
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
                val targets = g.rivals(p).filter { g.canChallenge(it) }
                if (sloppy && targets.isNotEmpty()) return A(Deeds.Act.CHALLENGE, targets.random(rnd))
                val loss = g.deeds(p).maxOfOrNull { d -> g.rivals(p).maxOf { g.spoilValue(it, d) } } ?: 0
                val best = targets.maxByOrNull { q -> (g.deeds(q).maxOfOrNull { g.spoilValue(p, it) } ?: 0) + (if (g.hero[q] == Deeds.KNIGHT) -120 else 0) }
                if (best != null && g.hp[p] > 2) {
                    val gain = g.deeds(best).maxOf { g.spoilValue(p, it) }
                    val edge = (if (g.hero[p] == Deeds.KNIGHT) 120 else 0) - (if (g.hero[best] == Deeds.KNIGHT) 120 else 0)
                    if (gain + edge > loss + 50) return A(Deeds.Act.CHALLENGE, best)
                }
                return A(Deeds.Act.DECLINE)
            }
            Deeds.D_MARKET -> {
                val want = when {
                    g.hp[p] <= 5 && g.gold[p] >= 400 -> Deeds.POTION
                    g.items[p][Deeds.SHIELD] == 0 && g.gold[p] >= 700 -> Deeds.SHIELD
                    g.items[p][Deeds.WRIT] == 0 && g.gold[p] >= 900 -> Deeds.WRIT
                    else -> -1
                }
                return if (want >= 0 && g.canShop(want) && !sloppy) A(Deeds.Act.SHOP, want) else A(Deeds.Act.LEAVE)
            }
            else -> return A(Deeds.Act.ACK)
        }
    }
}
