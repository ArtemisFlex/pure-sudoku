package com.puresudoku.core.city

import com.puresudoku.core.city.City.Kind
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Street Smarts: deliver orders around Harbor City while the GPS slowly lets go.
 *
 * Every stop has a level of help, from full turn-by-turn directions down to just a name. Help for a
 * place drops as you show you know the way there (clean, quick trips without hints) and rises again
 * if you get lost, so each place is practised until it sticks, like spaced repetition. Your overall
 * experience caps how little help you can get, unlocks new districts and brings longer,
 * multi-stop orders, rush jobs and road works. There is no end: the city just keeps teaching you.
 */
class StreetSmarts(val city: City = City.harbor) {

    // ---------------------------------------------------------------- help levels

    enum class Help(val label: String, val blurb: String) {
        ROUTE("Full GPS", "The route is drawn and every turn is announced."),
        TURNS("Turn list", "No route line: a list of turns by street name."),
        ADDRESS("Address", "Just the address. Street names show on the map."),
        NAME("Name only", "Just the place's name. Street names show near you."),
        MEMORY("Memory", "Just the name. No street names on the map: read the signs."),
    }

    /** The least help your experience allows, by deliveries completed. */
    fun helpCap(): Int = when { deliveries < 4 -> 0; deliveries < 12 -> 1; deliveries < 26 -> 2; deliveries < 45 -> 3; else -> 4 }

    /** Help for a stop: the less familiar the place, the more help (never less than [helpCap] allows). */
    fun helpFor(place: Int): Int = min(helpCap(), familiarity.getOrElse(place) { 0 })

    // ---------------------------------------------------------------- the player

    var coins = 20
    var earned = 0                       // coins earned in total (the score)
    var deliveries = 0
    var cleanTrips = 0
    val familiarity = HashMap<Int, Int>() // place id -> 0..4: how well you know the way there
    val visits = HashMap<Int, Int>()
    var car = CarStyle.SEDAN.ordinal
    var paint = 0
    val owned = mutableSetOf(CarStyle.SEDAN.ordinal * 100 + 0)
    var bestStreak = 0
    var streak = 0
    var newsDistrictRing = 3

    val level: Int get() = deliveries / 6
    /** Rings of the city open to you: downtown first, then outward. */
    val ring: Int get() = min(City.N / 2, 3 + level)

    fun unlocked(n: Int) = city.ring(n) <= ring
    fun unlockedPlace(p: City.Place) = unlocked(city.edges[p.edge].a) && unlocked(city.edges[p.edge].b)

    // ---------------------------------------------------------------- the car and the clock

    var at = 0                 // the intersection the car is at
    var cameBy = -1            // the edge it arrived on (-1: parked)
    var clock = 8 * 3600       // seconds since midnight: shifts start at 8:00
    val closed = HashSet<Int>() // road works: closed edge ids

    /** Can the car leave [at] along edge [e]? */
    fun canDrive(e: City.Edge, from: Int = at) =
        e.id !in closed && city.allows(e, from) && unlocked(city.other(e, from))

    /** The turns available here, with what each is called from this heading. */
    fun options(): List<Turn> {
        val res = ArrayList<Turn>()
        val inEdge = if (cameBy >= 0) city.edges[cameBy] else null
        for (id in city.touching[at]) {
            val e = city.edges[id]
            if (!canDrive(e)) continue
            val dir = turnOf(inEdge, at, e)
            if (dir == TurnDir.UTURN && city.touching[at].count { canDrive(city.edges[it]) } > 1) continue   // U-turns only at dead ends
            res += Turn(e.id, dir, city.streets[e.street].name)
        }
        return res.sortedBy { it.dir.ordinal }
    }

    enum class TurnDir(val label: String) { LEFT("Left"), STRAIGHT("Straight"), RIGHT("Right"), UTURN("Turn around"), START("Go") }
    class Turn(val edge: Int, val dir: TurnDir, val street: String)

    /** Heading in degrees on screen (0 = east, 90 = south) of leaving [from] along [e]. */
    fun heading(e: City.Edge, from: Int): Double {
        val to = city.other(e, from)
        return atan2((city.nodeY[to] - city.nodeY[from]).toDouble(), (city.nodeX[to] - city.nodeX[from]).toDouble()) * 180 / kotlin.math.PI
    }

    fun turnOf(inEdge: City.Edge?, node: Int, out: City.Edge): TurnDir {
        if (inEdge == null) return TurnDir.START
        val inH = heading(inEdge, city.other(inEdge, node))
        var d = heading(out, node) - inH
        while (d > 180) d -= 360; while (d <= -180) d += 360
        return when { abs(d) > 150 -> TurnDir.UTURN; d > 30 -> TurnDir.RIGHT; d < -30 -> TurnDir.LEFT; else -> TurnDir.STRAIGHT }
    }

    /** Seconds the car waits on arriving at [n] along [e] at time [t]: red lights and stop signs. */
    fun waitAt(n: Int, e: City.Edge, t: Int): Int {
        val light = city.lights[n]
        if (light != null) {
            val ns = city.axisOf(e) != City.Axis.EW
            val p = ((t + light.offset) % light.cycle + light.cycle) % light.cycle
            val green = if (ns) p < light.nsGreen else p >= light.nsGreen
            if (green) return 0
            return if (ns) light.cycle - p else light.nsGreen - p
        }
        val major = city.streets[e.street].major
        val crossesMajor = city.touching[n].any { city.streets[city.edges[it].street].major && city.edges[it].street != e.street }
        return if (city.allWayStop[n] || (!major && crossesMajor)) City.STOP_WAIT else 0
    }

    /** True when the light at [n] is green for traffic along [e] right now. */
    fun greenFor(n: Int, e: City.Edge, t: Int = clock) = city.lights[n] == null || waitAt(n, e, t) == 0

    fun driveSeconds(e: City.Edge) = (e.length / e.speed).roundToInt()

    // ---------------------------------------------------------------- routing

    /** Fastest path from [from] (heading in on [via]) to drive along edge [target]: the edges in order. */
    fun route(from: Int, target: Int, startTime: Int = clock): List<Int> {
        val n = City.N * City.N
        val best = IntArray(n) { Int.MAX_VALUE }
        val prev = IntArray(n) { -1 }
        best[from] = 0
        val open = ArrayList<Int>(); open += from
        val t = city.edges[target]
        var done = -1; var doneCost = Int.MAX_VALUE
        while (open.isNotEmpty()) {
            val u = open.minBy { best[it] }; open.remove(u)
            if (best[u] >= doneCost) break
            // reaching either end of the target block and driving it completes the trip
            if ((u == t.a || u == t.b) && canDrive(t, u)) {
                val c = best[u] + driveSeconds(t)
                if (c < doneCost) { doneCost = c; done = u }
            }
            for (id in city.touching[u]) {
                val e = city.edges[id]
                if (!canDrive(e, u)) continue
                val v = city.other(e, u)
                val avgWait = city.lights[v]?.let { it.cycle / 4 } ?: if (city.allWayStop[v]) City.STOP_WAIT else 1
                val c = best[u] + driveSeconds(e) + avgWait
                if (c < best[v]) { best[v] = c; prev[v] = id; if (v !in open) open += v }
            }
        }
        if (done < 0) return emptyList()
        val path = ArrayList<Int>()
        var v = done
        while (v != from) { val id = prev[v]; path += id; v = city.other(city.edges[id], v) }
        path.reverse()
        path += target
        return path
    }

    /** The intersection a path ends at. */
    fun endOf(path: List<Int>, from: Int): Int { var n = from; for (id in path) n = city.other(city.edges[id], n); return n }

    /** Expected seconds for a path, with typical waits. */
    fun pathSeconds(path: List<Int>, from: Int): Int {
        var n = from; var s = 0
        for (id in path) { val e = city.edges[id]; s += driveSeconds(e); n = city.other(e, n); s += city.lights[n]?.let { it.cycle / 4 } ?: 0 }
        return s
    }

    /** The turn-by-turn steps of a path: each street you turn onto. */
    fun steps(path: List<Int>, from: Int, firstIn: Int = cameBy): List<Step> {
        val out = ArrayList<Step>()
        var n = from; var inE = if (firstIn >= 0) city.edges[firstIn] else null
        var meters = 0f
        for ((i, id) in path.withIndex()) {
            val e = city.edges[id]
            val dir = turnOf(inE, n, e)
            val sameStreet = inE != null && inE.street == e.street && dir == TurnDir.STRAIGHT
            if (!sameStreet && i > 0) { out += Step(dir, city.streets[e.street].name, meters.roundToInt(), n); meters = 0f }
            else if (i == 0) out += Step(dir, city.streets[e.street].name, 0, n)
            meters += e.length
            n = city.other(e, n); inE = e
        }
        return out
    }

    class Step(val dir: TurnDir, val street: String, val metersBefore: Int, val node: Int)

    // ---------------------------------------------------------------- orders

    enum class StopKind { PICKUP, DROP, HOME }
    class Stop(val place: Int, val kind: StopKind, var help: Int, var done: Boolean = false)

    class Order(val stops: List<Stop>, val pay: Int, val rush: Boolean, val started: Int, val budget: Int) {
        var hintsUsed = 0
        var pointed = false
        var driven = 0      // seconds on the road for this order
    }

    var order: Order? = null
    var offer: Order? = null
    var jobsThisShift = 0
    var rng = Random(1)

    val current: Stop? get() = order?.stops?.firstOrNull { !it.done }

    /** Builds the next order: shifts end with the drive home. */
    fun makeOffer() {
        val r = rng
        if (jobsThisShift >= shiftLength()) {
            val home = city.places[city.home]
            val stop = Stop(home.id, StopKind.HOME, helpFor(home.id))
            offer = Order(listOf(stop), 0, false, clock, 0)
            return
        }
        val pickups = city.places.filter { it.kind.pickup && unlockedPlace(it) }
        val drops = city.places.filter { (it.kind == Kind.CUSTOMER || it.kind == Kind.LANDMARK) && unlockedPlace(it) }
        val stops = ArrayList<Stop>()
        val pick = pickups[r.nextInt(pickups.size)]
        stops += Stop(pick.id, StopKind.PICKUP, helpFor(pick.id))
        val count = when { level >= 8 && r.nextInt(3) == 0 -> 3; level >= 3 && r.nextInt(3) == 0 -> 2; else -> 1 }
        // regulars come back: half the time, a place you've been to but don't know well yet
        val due = drops.filter { (visits[it.id] ?: 0) > 0 && (familiarity[it.id] ?: 0) < 4 }
        val chosen = HashSet<Int>()
        repeat(count) {
            val pool = if (due.isNotEmpty() && r.nextBoolean()) due else drops
            val far = pool.filter { it.id !in chosen }.sortedBy { -dist(it, pick) }
            // longer trips as you grow: pick from the farther part of the list
            val span = max(1, far.size * (40 - min(level, 6) * 5) / 100)
            val p = far.take(max(span, 1)).let { if (level < 1) far.reversed().take(max(1, far.size / 3)) else it }.let { it[r.nextInt(it.size)] }
            chosen += p.id
            stops += Stop(p.id, StopKind.DROP, helpFor(p.id))
        }
        val rush = level >= 2 && r.nextInt(4) == 0
        var est = 0; var n = at
        for (s in stops) { val p = route(n, city.places[s.place].edge); est += pathSeconds(p, n); n = endOf(p, n) }
        val pay = 8 + est / 15 + stops.size * 4 + if (rush) 10 else 0
        offer = Order(stops, pay, rush, clock, max(60, (est * (if (rush) 1.35 else 1.8)).toInt()))
    }

    private fun dist(a: City.Place, b: City.Place): Float {
        val (ax, ay) = city.placeXY(a); val (bx, by) = city.placeXY(b)
        return kotlin.math.hypot(ax - bx, ay - by)
    }

    fun shiftLength() = 3 + min(level, 4)

    /** Road works appear from level 2: a few closed blocks, changing each shift. */
    fun newShiftRoadWorks() {
        closed.clear()
        if (level < 2) return
        val pool = city.edges.filter { it.id in city.touching[it.a] && unlocked(it.a) && unlocked(it.b) && !it.bridge }
        repeat(min(2 + level / 2, 8)) {
            val e = pool[rng.nextInt(pool.size)]
            closed += e.id
            // never cut the city in two
            if (route(at, city.places[city.home].edge).isEmpty()) closed -= e.id
        }
    }

    fun accept() {
        val o = offer ?: return
        offer = null
        order = o
    }

    // ---------------------------------------------------------------- driving

    class Arrival(val waited: Int, val reached: List<Stop>, val orderDone: Boolean, val pay: Int, val tip: Int, val bonus: Int, val learned: List<Int>)

    /** Drives one block along [edgeId]. Returns what happened when the car reached the next intersection. */
    fun drive(edgeId: Int): Arrival {
        val e = city.edges[edgeId]
        val to = city.other(e, at)
        var secs = driveSeconds(e)
        val waited = waitAt(to, e, clock + secs)
        secs += waited
        clock += secs
        order?.let { it.driven += secs }
        at = to; cameBy = edgeId
        // stops reached: driving their block counts, in order
        val o = order ?: return Arrival(waited, emptyList(), false, 0, 0, 0, emptyList())
        val reached = ArrayList<Stop>()
        for (s in o.stops) {
            if (s.done) continue
            if (city.places[s.place].edge == edgeId) { s.done = true; reached += s; visits[s.place] = (visits[s.place] ?: 0) + 1 } else break
        }
        if (o.stops.all { it.done }) return finishOrder(o, waited, reached)
        return Arrival(waited, reached, false, 0, 0, 0, emptyList())
    }

    /** A U-turn in the middle of the block (allowed anywhere, but it costs time). */
    fun turnAround(): Boolean {
        if (cameBy < 0) return false
        val e = city.edges[cameBy]
        if (!canDrive(e, at)) return false
        cameBy = -1
        clock += 12
        order?.let { it.driven += 12 }
        return true
    }

    private fun finishOrder(o: Order, waited: Int, reached: List<Stop>): Arrival {
        order = null
        val ratio = o.driven.toDouble() / max(1, o.budget)
        val clean = o.hintsUsed == 0 && ratio <= 1.0
        // learning: clean trips make places familiar (less help next time); struggles bring help back
        val learned = ArrayList<Int>()
        for (s in o.stops) {
            val f = familiarity[s.place] ?: 0
            familiarity[s.place] = when {
                clean -> min(4, f + 1).also { if (it > f) learned += s.place }
                ratio > 1.6 || o.hintsUsed >= 2 -> max(0, f - 1)
                else -> f
            }
        }
        if (o.stops.first().kind == StopKind.HOME) {
            jobsThisShift = 0
            val bonus = if (clean) 15 + 5 * o.stops.first().help else 5
            coins += bonus; earned += bonus
            newShiftRoadWorks()
            clock = 8 * 3600 + (clock / 86400 + 1) * 86400   // a new day
            return Arrival(waited, reached, true, 0, 0, bonus, learned)
        }
        deliveries += o.stops.count { it.kind == StopKind.DROP }
        jobsThisShift++
        if (clean) { cleanTrips++; streak++; bestStreak = max(bestStreak, streak) } else streak = 0
        val tip = when { ratio <= 0.7 -> o.pay * 6 / 10; ratio <= 1.0 -> o.pay * 3 / 10; ratio <= 1.5 -> 0; else -> -o.pay / 3 } +
            o.stops.sumOf { it.help } * 2 - o.hintsUsed * 2
        val paid = max(3, o.pay + tip)
        coins += paid; earned += paid
        return Arrival(waited, reached, true, o.pay, paid - o.pay, 0, learned)
    }

    // ---------------------------------------------------------------- sense of direction

    /** Which way is [place] from the car, as one of 8 compass points (0 = north, clockwise)? */
    fun compassTo(place: Int): Int {
        val (px, py) = city.placeXY(city.places[place])
        val dx = px - city.nodeX[at]; val dy = py - city.nodeY[at]
        val deg = (atan2(dx.toDouble(), -dy.toDouble()) * 180 / kotlin.math.PI + 360) % 360
        return ((deg + 22.5) / 45).toInt() % 8
    }

    /** The point-the-way challenge: +4 coins for the right direction, +1 for a neighbouring one. */
    fun point(dir: Int): Int {
        val o = order ?: return 0
        val s = current ?: return 0
        o.pointed = true
        val diff = abs(dir - compassTo(s.place)).let { min(it, 8 - it) }
        val c = when (diff) { 0 -> 4; 1 -> 1; else -> 0 }
        coins += c; earned += c
        return diff
    }

    // ---------------------------------------------------------------- hints and the shop

    enum class Hint(val label: String, val cost: Int, val blurb: String) {
        NEXT_TURN("Next turn", 4, "Which way to go at this corner."),
        PIN("Show on map", 8, "Drop a pin on the destination."),
        ROUTE("Show route", 15, "Draw the whole route for a few seconds."),
    }

    fun buyHint(h: Hint): Boolean {
        val o = order ?: return false
        if (coins < h.cost) return false
        coins -= h.cost
        o.hintsUsed++
        return true
    }

    enum class CarStyle(val label: String, val price: Int) { SEDAN("Sedan", 0), HATCH("Hatchback", 60), VAN("Delivery van", 120), PICKUP("Pickup", 160), SCOOTER("Scooter", 90), TAXI("Cab", 220) }
    val paints = listOf(0xFF1A73E8, 0xFFD93025, 0xFF188038, 0xFFF9AB00, 0xFF7B1FA2, 0xFF202124, 0xFFFFFFFF, 0xFFE8710A, 0xFF12B5CB, 0xFFFF6D9E)
    fun paintPrice(i: Int) = if (i == 0) 0 else 25 + i * 5
    fun owns(style: Int, paintIx: Int) = (style * 100 + paintIx) in owned || (CarStyle.entries[style].price == 0 && paintPrice(paintIx) == 0)
    fun ownsStyle(style: Int) = owned.any { it / 100 == style }
    fun ownsPaint(paintIx: Int) = owned.any { it % 100 == paintIx }

    fun buyStyle(style: Int): Boolean {
        if (ownsStyle(style)) { car = style; return true }
        val price = CarStyle.entries[style].price
        if (coins < price) return false
        coins -= price; owned += style * 100 + paint; car = style
        return true
    }

    fun buyPaint(ix: Int): Boolean {
        if (ownsPaint(ix)) { paint = ix; return true }
        val price = paintPrice(ix)
        if (coins < price) return false
        coins -= price; owned += car * 100 + ix; paint = ix
        return true
    }

    // ---------------------------------------------------------------- saving

    fun state(): String = buildString {
        fun kv(m: Map<Int, Int>) = m.entries.joinToString(";") { "${it.key}:${it.value}" }
        append(listOf(coins, earned, deliveries, cleanTrips, car, paint, bestStreak, streak, at, cameBy, clock, jobsThisShift, newsDistrictRing).joinToString(","))
        append("|"); append(kv(familiarity)); append("|"); append(kv(visits)); append("|"); append(owned.joinToString(","))
        append("|"); append(closed.joinToString(","))
        append("|"); append(orderText(order)); append("|"); append(orderText(offer))
    }

    private fun orderText(o: Order?) = if (o == null) "" else
        listOf(o.pay, if (o.rush) 1 else 0, o.started, o.budget, o.hintsUsed, if (o.pointed) 1 else 0, o.driven).joinToString(",") + "/" +
            o.stops.joinToString(";") { "${it.place}:${it.kind.ordinal}:${it.help}:${if (it.done) 1 else 0}" }

    private fun orderFrom(t: String): Order? {
        if (t.isEmpty()) return null
        val (h, s) = t.split('/')
        val v = h.split(',').map { it.toInt() }
        val stops = s.split(';').map { st -> st.split(':').map { it.toInt() }.let { Stop(it[0], StopKind.entries[it[1]], it[2], it[3] == 1) } }
        return Order(stops, v[0], v[1] == 1, v[2], v[3]).also { it.hintsUsed = v[4]; it.pointed = v[5] == 1; it.driven = v[6] }
    }

    fun load(s: String) {
        val parts = s.split('|')
        val v = parts[0].split(',').map { it.toInt() }
        coins = v[0]; earned = v[1]; deliveries = v[2]; cleanTrips = v[3]; car = v[4]; paint = v[5]; bestStreak = v[6]; streak = v[7]
        at = v[8]; cameBy = v[9]; clock = v[10]; jobsThisShift = v[11]; newsDistrictRing = v[12]
        fun kv(t: String, into: HashMap<Int, Int>) { into.clear(); if (t.isNotEmpty()) t.split(';').forEach { val (k, x) = it.split(':'); into[k.toInt()] = x.toInt() } }
        kv(parts[1], familiarity); kv(parts[2], visits)
        owned.clear(); if (parts[3].isNotEmpty()) owned += parts[3].split(',').map { it.toInt() }
        closed.clear(); if (parts[4].isNotEmpty()) closed += parts[4].split(',').map { it.toInt() }
        order = orderFrom(parts[5]); offer = orderFrom(parts[6])
        rng = Random(clock.toLong() * 31 + deliveries)
    }

    companion object {
        /** A new career: parked at home at 8:00 on day one. */
        fun newGame(seed: Long): StreetSmarts = StreetSmarts().apply {
            rng = Random(seed)
            val home = city.places[city.home]
            val e = city.edges[home.edge]
            at = e.a; cameBy = -1
            makeOffer()
        }
    }
}
