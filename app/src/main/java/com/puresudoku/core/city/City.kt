package com.puresudoku.core.city

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Harbor City: the one city of Street Smarts. It is generated from a fixed seed, so it is the same
 * in every save and on every device: the point of the game is to learn it. Streets run on a slightly
 * wavy grid of 100 m blocks, crossed by the Silver River (bridged in a few places) and the Grand
 * Diagonal. Some blocks are parks, some streets are one-way or dead ends, major crossings have
 * traffic lights with their own timings, and quieter ones have stop signs.
 */
class City private constructor() {
    companion object {
        const val N = 29                // intersections per side
        const val CENTER = 14           // downtown is the middle of the grid
        const val BLOCK = 100f          // metres between intersections
        const val STOP_WAIT = 4         // seconds lost at a stop sign
        const val SEED = 20261114L
        /** Every place in the city: home, landmarks, shops, eateries and regular customers. */
        const val PLACES = 100

        /** The city, built once. */
        val harbor: City by lazy { City().also { it.build(Random(SEED)) } }

        val AVENUES = listOf(
            "Alder", "Birch", "Cedar", "Dogwood", "Elm", "Fir", "Grove", "Hawthorn", "Ivy", "Juniper",
            "Kingsley", "Laurel", "Maple", "Nightingale", "Oak", "Poplar", "Quarry", "Rowan", "Spruce", "Thistle",
            "Union", "Vine", "Willow", "Yarrow", "Zephyr", "Ash", "Beacon", "Clover", "Dune",
        )
        private val MAJOR_AVE = mapOf(0 to "Harbor Blvd", 4 to "Lincoln Ave", 8 to "Market Ave", 12 to "Central Ave", 16 to "Park Ave", 20 to "Station Ave", 24 to "Hill Ave", 28 to "Ridge Blvd")
        private val MAJOR_ST = mapOf(0 to "North Rd", 4 to "College St", 8 to "Main St", 12 to "Bridge St", 16 to "River Rd", 20 to "Mill St", 24 to "Garden St", 28 to "South Rd")

        fun ordinal(n: Int) = "$n" + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }
    }

    // ---------------------------------------------------------------- streets and intersections

    enum class Axis { NS, EW, DIAG }
    class Street(val id: Int, val name: String, val axis: Axis, val major: Boolean)

    /**
     * A block of street between two intersections. [oneWay] is 0 (both ways), 1 (only [a]→[b])
     * or -1 (only [b]→[a]).
     */
    class Edge(val id: Int, val a: Int, val b: Int, val street: Int, val length: Float, val speed: Float, val oneWay: Int, val bridge: Boolean)

    class Light(val cycle: Int, val nsGreen: Int, val offset: Int)

    val nodeX = FloatArray(N * N)
    val nodeY = FloatArray(N * N)
    val streets = ArrayList<Street>()
    val edges = ArrayList<Edge>()
    /** Outgoing connections per intersection: edge ids (direction rules checked separately). */
    val touching = Array(N * N) { ArrayList<Int>() }
    val lights = arrayOfNulls<Light>(N * N)
    /** All-way stop at this intersection (plus two-way stops where a minor road meets a major one). */
    val allWayStop = BooleanArray(N * N)
    val parkCells = HashSet<Int>()            // block cells (x + y*N, top-left corner) drawn as parks
    val places = ArrayList<Place>()
    var home = 0; private set                 // the driver's home: a place id
    private val avenueOf = IntArray(N)        // street id of each vertical line
    private val streetOf = IntArray(N)        // street id of each horizontal line
    var diagonal = -1; private set

    fun node(x: Int, y: Int) = y * N + x
    fun gx(n: Int) = n % N
    fun gy(n: Int) = n / N
    fun alive(n: Int) = touching[n].isNotEmpty()

    /** The river runs between rows; this is its row position at grid x. */
    fun riverRow(x: Float) = 15.4f + 3.2f * sin(x / 4.6f) + 1.1f * sin(x / 1.9f + 1f)

    fun other(e: Edge, from: Int) = if (e.a == from) e.b else e.a

    /** Can a car drive edge [e] leaving intersection [from]? (One-way rules only.) */
    fun allows(e: Edge, from: Int) = e.oneWay == 0 || (e.oneWay == 1 && e.a == from) || (e.oneWay == -1 && e.b == from)

    fun axisOf(e: Edge) = streets[e.street].axis

    // ---------------------------------------------------------------- districts

    /** Ring distance from downtown: districts unlock ring by ring. */
    fun ring(n: Int) = max(abs(gx(n) - CENTER), abs(gy(n) - CENTER))

    fun districtOf(n: Int): String {
        val x = gx(n) - CENTER; val y = gy(n) - CENTER
        if (max(abs(x), abs(y)) <= 3) return "Downtown"
        val south = gy(n) > riverRow(gx(n).toFloat())
        val ang = atan2(y.toDouble(), x.toDouble()) * 180 / kotlin.math.PI
        return when {
            max(abs(x), abs(y)) <= 7 -> if (south) "Riverside" else when { ang < -112 -> "Old Town"; ang < -67 -> "College Hill"; ang < 0 -> "Market Quarter"; else -> "Old Town" }
            south -> when { x < -4 -> "Westfield"; x > 4 -> "Harbor"; else -> "Southpark" }
            else -> when { x < -4 -> "Northgate"; x > 4 -> "Eastmarket"; else -> "Hillcrest" }
        }
    }

    // ---------------------------------------------------------------- building

    private fun build(rng: Random) {
        // intersections: a clean grid, so every street runs straight
        for (y in 0 until N) for (x in 0 until N) {
            nodeX[node(x, y)] = x * BLOCK
            nodeY[node(x, y)] = y * BLOCK
        }
        // street names: avenues run north-south, streets east-west
        for (x in 0 until N) {
            val major = x % 4 == 0
            avenueOf[x] = streets.size
            streets += Street(streets.size, MAJOR_AVE[x] ?: (AVENUES[x] + " Ave"), Axis.NS, major)
        }
        for (y in 0 until N) {
            val major = y % 4 == 0
            streetOf[y] = streets.size
            streets += Street(streets.size, MAJOR_ST[y] ?: (ordinal(y + 1) + " St"), Axis.EW, major)
        }
        diagonal = streets.size
        streets += Street(diagonal, "Grand Diagonal", Axis.DIAG, true)

        // parks: blocks with no streets through them
        val parks = listOf(9 to 9, 18 to 6, 6 to 21, 21 to 22, 13 to 4, 3 to 11, 24 to 13)
        val bigParks = setOf(9 to 9)    // City Park: 3x3 blocks
        for ((px, py) in parks) {
            val size = if ((px to py) in bigParks) 3 else if (rng.nextBoolean()) 2 else 1
            for (dx in 0 until size) for (dy in 0 until size) parkCells += (px + dx) + (py + dy) * N
        }
        // a block of street goes when there is park on both sides of it (cell = its top-left corner)
        fun inPark(x1: Int, y1: Int, x2: Int, y2: Int): Boolean =
            if (y1 == y2) y1 > 0 && (min(x1, x2) + (y1 - 1) * N in parkCells) && (min(x1, x2) + y1 * N in parkCells)
            else x1 > 0 && (x1 - 1 + min(y1, y2) * N in parkCells) && (x1 + min(y1, y2) * N in parkCells)

        fun riverBetween(x: Int, y: Int) = floor(riverRow(x.toFloat())).toInt() == y   // river crosses (x,y)-(x,y+1)
        val bridges = setOf(2, 8, 12, 16, 20, 26)

        fun add(a: Int, b: Int, street: Int, oneWay: Int = 0, bridge: Boolean = false) {
            val s = streets[street]
            val len = hypot(nodeX[a] - nodeX[b], nodeY[a] - nodeY[b])
            val speed = when { s.axis == Axis.DIAG -> 16.7f; s.major -> 13.9f; else -> 8.3f }   // 60 / 50 / 30 km/h
            val e = Edge(edges.size, a, b, street, len, speed, oneWay, bridge)
            edges += e
            touching[a] += e.id; touching[b] += e.id
        }

        for (y in 0 until N) for (x in 0 until N - 1) {
            if (inPark(x, y, x + 1, y)) continue
            // one-way pairs downtown: odd minor streets alternate east and west
            val oneWay = if (y % 4 != 0 && y % 2 == 1 && abs(y - CENTER) <= 9) (if (y % 4 == 1) 1 else -1) else 0
            add(node(x, y), node(x + 1, y), streetOf[y], oneWay)
        }
        for (x in 0 until N) for (y in 0 until N - 1) {
            if (inPark(x, y, x, y + 1)) continue
            if (riverBetween(x, y) && x !in bridges) continue
            val oneWay = if (x % 4 != 0 && x % 4 == 2 && abs(x - CENTER) <= 9) (if (x % 8 == 2) 1 else -1) else 0
            add(node(x, y), node(x, y + 1), avenueOf[x], oneWay, bridge = riverBetween(x, y))
        }
        // the Grand Diagonal, from the north-west hills to the harbor
        for (i in 5 until 23) add(node(i, i), node(i + 1, i + 1), diagonal, bridge = floor(riverRow(i + 0.5f)).toInt() == i)

        // dead ends: cut a few quiet blocks, keeping every intersection reachable both ways
        val minor = edges.filter { !streets[it.street].major && it.oneWay == 0 && !it.bridge }.shuffled(rng).take(55)
        for (e in minor) {
            touching[e.a].remove(e.id); touching[e.b].remove(e.id)
            val deadEndOk = touching[e.a].size >= 1 && touching[e.b].size >= 1 && stronglyConnected()
            if (!deadEndOk) { touching[e.a] += e.id; touching[e.b] += e.id }
        }
        // any leftover one-way trap: make it two-way
        while (!stronglyConnected()) {
            val fix = edges.firstOrNull { it.oneWay != 0 && it.id in touching[it.a] } ?: break
            edges[fix.id] = Edge(fix.id, fix.a, fix.b, fix.street, fix.length, fix.speed, 0, fix.bridge)
        }

        // traffic lights where major roads cross, each with its own timing
        for (n in 0 until N * N) {
            if (!alive(n)) continue
            val majors = touching[n].map { streets[edges[it].street] }.filter { it.major }.map { it.axis }.toSet()
            if (majors.size >= 2) {
                val cycle = listOf(50, 60, 70, 80, 90)[rng.nextInt(5)]
                lights[n] = Light(cycle, (cycle * (0.4f + rng.nextFloat() * 0.2f)).toInt(), rng.nextInt(cycle))
            } else if (majors.isEmpty() && touching[n].size >= 3 && rng.nextInt(3) == 0) allWayStop[n] = true
        }

        buildPlaces(rng)
    }

    /** Every intersection can reach every other one, obeying one-way streets. */
    private fun stronglyConnected(): Boolean {
        val start = (0 until N * N).first { alive(it) }
        fun reach(forward: Boolean): Int {
            val seen = BooleanArray(N * N); val q = ArrayDeque<Int>(); q += start; seen[start] = true; var c = 1
            while (q.isNotEmpty()) {
                val n = q.removeFirst()
                for (id in touching[n]) {
                    val e = edges[id]; val m = other(e, n)
                    val ok = if (forward) allows(e, n) else allows(e, m)
                    if (ok && !seen[m]) { seen[m] = true; c++; q += m }
                }
            }
            return c
        }
        val total = (0 until N * N).count { alive(it) }
        return reach(true) == total && reach(false) == total
    }

    // ---------------------------------------------------------------- places

    enum class Kind(val label: String, val letter: String, val pickup: Boolean) {
        HOME("Home", "H", false),
        RESTAURANT("Restaurant", "R", true),
        CAFE("Café", "C", true),
        GROCER("Grocer", "G", true),
        SHOP("Shop", "S", true),
        PHARMACY("Pharmacy", "+", true),
        LANDMARK("Landmark", "L", false),
        CUSTOMER("Customer", "•", false),
    }

    /** A place on one side of a block of street. [number] is its house number on that street. */
    class Place(val id: Int, val name: String, val kind: Kind, val edge: Int, val left: Boolean, val number: Int)

    fun address(p: Place): String = "${p.number} ${streets[edges[p.edge].street].name}"
    fun placeNode(p: Place): Int = edges[p.edge].a
    /** Where a place sits on the map: the middle of its block, nudged to its side of the street. */
    fun placeXY(p: Place): Pair<Float, Float> {
        val e = edges[p.edge]
        val mx = (nodeX[e.a] + nodeX[e.b]) / 2; val my = (nodeY[e.a] + nodeY[e.b]) / 2
        val dx = nodeX[e.b] - nodeX[e.a]; val dy = nodeY[e.b] - nodeY[e.a]; val l = hypot(dx, dy)
        val s = if (p.left) 1 else -1
        return (mx + dy / l * 26f * s) to (my - dx / l * 26f * s)
    }

    private fun buildPlaces(rng: Random) {
        val restaurants = listOf("Rosa's Pizzeria", "Golden Wok", "Taco Loco", "Sakura Sushi", "The Burger Barn", "Pho Saigon", "Olive & Vine", "Curry House", "Bella Pasta",
            "Smokehouse BBQ", "Seoul Kitchen", "Le Petit Bistro", "Falafel King", "Dragon Dumplings", "Harbor Fish Shack", "Mama's Diner", "Noodle Bar", "Tandoor Palace",
            "Green Bowl", "Crispy Chicken Co.", "Pasta Fresca", "Sunrise Pancakes", "El Mariachi", "Bangkok Street", "The Steak Room")
        val cafes = listOf("Bean There Café", "Morning Grind", "The Daily Cup", "Copper Kettle", "Brew & Bloom", "Moonbeam Coffee", "The Reading Room Café", "Steam Café")
        val grocers = listOf("FreshMart", "Corner Grocer", "Green Valley Market", "Village Foods", "Harbor Market", "Sunny Produce")
        val shops = listOf("Petal & Stem Florist", "Page Turner Books", "Bolt Hardware", "Tinker Toys", "Paws Pet Supply", "Vinyl Vault", "Bake My Day", "The Cheese Board",
            "Velvet Boutique", "Spokes Bike Shop", "Lumen Electronics", "Sweet Tooth Candy")
        val pharmacies = listOf("CityCare Pharmacy", "Wellness Drugs", "Northgate Pharmacy")
        val landmarks = listOf("City Hall", "Clock Tower", "Central Library", "Harbor Lighthouse", "Union Station", "Riverside Stadium", "Museum of Art", "Saint Mary's Hospital",
            "Fire Station 3", "Old Mill", "Observatory", "Botanical Garden", "Grand Theater", "Police HQ", "High School", "Farmers' Pavilion")
        val first = listOf("Maya", "Leo", "Priya", "Sam", "Ava", "Noah", "Zoe", "Omar", "Ivy", "Theo", "Lena", "Kai", "Nora", "Eli", "Ruby", "Jonah", "Mila", "Ezra",
            "Hana", "Felix", "Iris", "Hugo", "June", "Arlo", "Sofia", "Milo", "Esme", "Ravi", "Tess", "Diego", "Wren", "Otto", "Lucia", "Abe", "Clara", "Finn")
        val last = listOf("Patel", "Nguyen", "Henderson", "Okafor", "Garcia", "Kim", "Rossi", "Johansson", "Cohen", "Murphy", "Tanaka", "Silva", "Novak", "Haddad",
            "Brooks", "Fischer", "Moreau", "Kowalski", "Reyes", "Lindqvist", "Adeyemi", "Walsh", "Ortiz", "Chen", "Dubois", "Sato", "Bianchi", "Larsen")

        val usable = edges.filter { it.id in touching[it.a] && !it.bridge && streets[it.street].axis != Axis.DIAG }.shuffled(rng)
        val taken = HashSet<Pair<Int, Boolean>>()
        var cursor = 0
        fun spot(pred: (Edge) -> Boolean): Pair<Edge, Boolean> {
            while (true) {
                val e = usable[cursor++ % usable.size]
                val left = rng.nextBoolean()
                if ((e.id to left) !in taken && pred(e)) { taken += e.id to left; return e to left }
            }
        }
        fun number(e: Edge, left: Boolean): Int {
            val along = if (streets[e.street].axis == Axis.NS) min(gy(e.a), gy(e.b)) else min(gx(e.a), gx(e.b))
            return (along + 1) * 100 + 10 + rng.nextInt(80) / 2 * 2 + if (left) 1 else 0
        }
        fun place(name: String, kind: Kind, pred: (Edge) -> Boolean = { true }) {
            val (e, left) = spot(pred)
            places += Place(places.size, name, kind, e.id, left, number(e, left))
        }

        // home: a quiet street just off downtown
        place("Home", Kind.HOME) { ring(it.a) == 2 && !streets[it.street].major }
        home = 0
        // landmarks spread over every district; shops and restaurants mostly along main roads
        for (n in landmarks) place(n, Kind.LANDMARK) { ring(it.a) in 0..13 }
        val nearCentreFirst: (List<String>, Kind, Boolean) -> Unit = { names, kind, onMajor ->
            names.forEachIndexed { i, n ->
                val maxRing = 3 + i * 11 / names.size
                place(n, kind) { ring(it.a) <= maxRing && (!onMajor || streets[it.street].major || rng.nextInt(3) == 0) }
            }
        }
        nearCentreFirst(restaurants.take(20), Kind.RESTAURANT, true)
        nearCentreFirst(cafes, Kind.CAFE, true)
        nearCentreFirst(grocers.take(5), Kind.GROCER, true)
        nearCentreFirst(shops.take(10), Kind.SHOP, true)
        nearCentreFirst(pharmacies, Kind.PHARMACY, true)
        // regular customers: households on quiet streets, in every district
        val names = HashSet<String>()
        var k = 0
        // regulars fill the city up to exactly PLACES places, so it can be learned by heart
        while (places.size < PLACES) {
            val nm = first[k % first.size] + " " + last[(k * 7 + k / first.size) % last.size]; k++
            if (!names.add(nm)) continue
            val maxRing = 2 + (names.size - 1) * 12 / 37
            place(nm, Kind.CUSTOMER) { ring(it.a) <= maxRing && !streets[it.street].major }
        }
    }

    /** The intersection nearest a point. */
    fun nearestNode(x: Float, y: Float): Int {
        var best = -1; var bd = Float.MAX_VALUE
        for (n in 0 until N * N) if (alive(n)) {
            val d = hypot(nodeX[n] - x, nodeY[n] - y)
            if (d < bd) { bd = d; best = n }
        }
        return best
    }
}
