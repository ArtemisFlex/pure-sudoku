package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.random.Random

/**
 * The Eiseria computer player. It picks one action at a time from the current position.
 * It only uses what it could see: hidden tiles (fog) count as an average unknown tile.
 */
object RealmAi {
    /**
     * [skill] 0..1 sharpens every choice; [noise] blurs spot values; [lazy] is the chance to end a turn early;
     * [offer]: 0 never offers deals, 1 fair 1-for-1, 2 generous 2-for-1.
     */
    class Level(val skill: Double, val noise: Double, val lazy: Double, val offer: Int, val fireball: Boolean, val trade: Double = 1.0)

    fun level(d: Difficulty) = when (d) {
        Difficulty.EASY -> Level(0.1, 7.0, 0.35, 2, false, 0.15)
        Difficulty.MEDIUM -> Level(0.3, 4.0, 0.15, 2, false, 0.5)
        Difficulty.HARD -> Level(0.5, 3.0, 0.08, 1, true, 0.7)
        Difficulty.EXPERT -> Level(0.7, 1.5, 0.03, 1, true, 0.9)
        Difficulty.MASTER -> Level(0.85, 0.6, 0.0, 1, true)
        Difficulty.EXTREME -> Level(1.0, 0.0, 0.0, 0, true)
    }

    private val BASE_W = doubleArrayOf(1.0, 1.05, 1.05, 0.9, 1.0)

    /** Expected cards per roll for [p], by resource (visible tiles only). */
    fun income(g: Realm, p: Int): DoubleArray {
        val out = DoubleArray(5)
        for (h in 0 until g.map.HEXES) {
            if (g.terrain[h] == Realm.WASTE || !g.revealed[h]) continue
            val blocked = g.blocked(h)
            for (v in g.map.hexVerts[h]) if (g.vOwner[v] == p) out[g.terrain[h]] += g.vLevel[v] * Realm.pips(g.number[h]) / 36.0 * (if (blocked) 0.3 else 1.0)
        }
        return out
    }

    private fun weights(g: Realm, p: Int): DoubleArray {
        val inc = income(g, p)
        return DoubleArray(5) { r -> BASE_W[r] * (if (inc[r] < 0.05) 1.5 else if (inc[r] < 0.15) 1.15 else 1.0) }
    }

    private fun tileValue(g: Realm, h: Int, w: DoubleArray): Double = when {
        !g.revealed[h] -> 2.4
        g.terrain[h] == Realm.WASTE -> 0.0
        else -> Realm.pips(g.number[h]) * w[g.terrain[h]] * (if (g.blocked(h)) 0.5 else 1.0) * ashFactor(g, h)
    }

    /** Ashfall: lava in the forecast, ruined land, and richer renewed land. */
    private fun ashFactor(g: Realm, h: Int): Double = if (g.twist != Realm.ASH) 1.0 else when {
        g.inDanger(h) -> 0.35
        g.scorched(h) -> 0.6
        g.stage[h] == 3 -> 1.3
        else -> 1.0
    }

    /** How much lava flow [i] would cost [p] more than their rival: pips times buildings on the tiles it buries. */
    fun flowHarm(g: Realm, p: Int, i: Int): Int = g.forecast[i].filter { g.fresh(it) }.sumOf { g.harm(it, p) - g.harm(it, 1 - p) }

    fun spotValue(g: Realm, p: Int, v: Int, w: DoubleArray = weights(g, p)): Double {
        var s = g.map.vertHexes[v].sumOf { tileValue(g, it, w) }
        val inc = income(g, p)
        val newTypes = g.map.vertHexes[v].filter { g.revealed[it] && g.terrain[it] != Realm.WASTE }.map { g.terrain[it] }.toSet().count { inc[it] < 0.05 }
        s += newTypes * 1.5
        val port = g.portAt(v)
        if (port == 5) s += 1.0 else if (port in 0..4) s += if (inc[port] > 0.15) 2.5 else 0.8
        if (g.twist == Realm.FOG && v in g.ley && g.leyVisible(v)) s += 1.5
        if (g.twist == Realm.FOG) s += g.map.vertHexes[v].count { !g.revealed[it] } * 0.4
        return s
    }

    private fun spotFree(g: Realm, v: Int) = g.vOwner[v] < 0 && g.map.vertNbrs[v].all { g.vOwner[it] < 0 }

    /** Best road to build toward a future village: (first edge, target vertex, score). */
    fun roadPlan(g: Realm, p: Int, rng: Random?, noise: Double): Triple<Int, Int, Double>? {
        if (g.roads(p) >= Realm.MAX_ROADS) return null
        val w = weights(g, p)
        val dist = IntArray(g.map.VERTS) { -1 }
        val first = IntArray(g.map.VERTS) { -1 }
        val queue = ArrayDeque<Int>()
        for (v in 0 until g.map.VERTS) {
            val mine = g.vOwner[v] == p || (g.vOwner[v] < 0 && g.map.vertEdges[v].any { g.eOwner[it] == p })
            if (mine) { dist[v] = 0; queue += v }
        }
        var best: Triple<Int, Int, Double>? = null
        while (queue.isNotEmpty()) {
            val v = queue.removeFirst()
            if (dist[v] >= 3) continue
            if (dist[v] > 0 && g.vOwner[v] >= 0) continue
            for (e in g.map.vertEdges[v]) {
                if (g.eOwner[e] >= 0) continue
                val u = g.map.other(e, v)
                if (dist[u] >= 0) continue
                dist[u] = dist[v] + 1
                first[u] = if (dist[v] == 0) e else first[v]
                queue += u
                if (spotFree(g, u)) {
                    val sc = spotValue(g, p, u, w) - 2.2 * dist[u] + (rng?.let { (it.nextDouble() - 0.5) * noise } ?: 0.0)
                    if (best == null || sc > best.third) best = Triple(first[u], u, sc)
                }
            }
        }
        return best
    }

    private fun missing(g: Realm, p: Int, cost: IntArray) = IntArray(5) { maxOf(0, cost[it] - g.res[p][it]) }

    /** Picks the next action for whoever must act. [offered]: the computer already proposed a deal this turn. */
    fun next(g: Realm, lvl: Level, rng: Random, offered: Boolean = false): RAct {
        val p = g.actor
        return when (g.phase) {
            Realm.SETUP -> setup(g, p, lvl, rng)
            Realm.ROLL -> if (knightWanted(g, p, lvl)) RAct.Spell(Realm.KNIGHT) else RAct.Roll
            Realm.DISCARD -> discard(g, p)
            Realm.DRAGON -> RAct.Dragon(dragonTile(g, p, lvl, rng))
            Realm.MAIN -> main(g, p, lvl, rng, offered)
            else -> RAct.End
        }
    }

    private fun setup(g: Realm, p: Int, lvl: Level, rng: Random): RAct {
        val w = weights(g, p)
        if (g.setupVillage < 0) {
            val v = g.villageSpots(p).maxBy { spotValue(g, p, it, w) + (rng.nextDouble() - 0.5) * lvl.noise }
            return RAct.Village(v)
        }
        val v = g.setupVillage
        val e = g.roadSpots(p).maxBy { e ->
            val u = g.map.other(e, v)
            g.map.vertNbrs[u].filter { spotFree(g, it) }.maxOfOrNull { spotValue(g, p, it, w) } ?: 0.0
        }
        return RAct.Road(e)
    }

    private fun knightWanted(g: Realm, p: Int, lvl: Level): Boolean {
        if (!g.canPlay(p, Realm.KNIGHT)) return false
        if (lvl.skill < 0.3) return !g.dragonGone && g.harm(g.dragon, p) >= 6
        if (g.twist == Realm.WAKE && !g.dragonGone && g.dragonWounds + 1 >= g.dragonHp) return true
        if (!g.dragonGone && g.harm(g.dragon, p) >= 4) return true
        val o = 1 - p
        if (lvl.skill >= 0.5 && g.hostHolder != p && g.knights[p] + 1 >= 3 && g.knights[p] + 1 > g.knights[o]) return true
        return false
    }

    private fun discard(g: Realm, p: Int): RAct {
        val need = g.discardNeed[p]
        val left = g.res[p].copyOf()
        val keep = if (g.towerSpots(p).isNotEmpty()) Realm.COST_TOWER else Realm.COST_VILLAGE
        val out = IntArray(5)
        repeat(need) {
            val r = (0..4).filter { left[it] > 0 }.maxBy { (left[it] - keep[it]) * 10 + left[it] }
            left[r]--; out[r]++
        }
        return RAct.Discard(out)
    }

    private fun dragonTile(g: Realm, p: Int, lvl: Level, rng: Random): Int {
        val o = 1 - p
        return g.dragonTiles().maxBy { t ->
            g.harm(t, o) * 3.0 - g.harm(t, p) * 4.0 + (if (o in g.ownersAt(t) && g.total(o) > 0) 2.0 else 0.0) + (rng.nextDouble() - 0.5) * lvl.noise * 2
        }
    }

    private fun main(g: Realm, p: Int, lvl: Level, rng: Random, offered: Boolean): RAct {
        val o = 1 - p
        val w = weights(g, p)
        if (g.freeRoads > 0) {
            val plan = roadPlan(g, p, null, 0.0)
            return RAct.Road(plan?.first ?: g.roadSpots(p).first())
        }
        // Spells first if they set up a build or swing a title.
        if (knightWanted(g, p, lvl)) return RAct.Spell(Realm.KNIGHT)

        val towers = g.towerSpots(p)
        if (towers.isNotEmpty() && g.can(p, Realm.COST_TOWER))
            return RAct.Tower(towers.maxBy { v -> g.map.vertHexes[v].sumOf { tileValue(g, it, w) } +
                g.map.vertHexes[v].count { h -> g.terrain[h] == Realm.WASTE && g.wasteClaim[h] < 0 && g.claimProgress(h, p).first >= 2 } * 8.0 })
        val spots = g.villageSpots(p)
        if (spots.isNotEmpty() && g.can(p, Realm.COST_VILLAGE))
            return RAct.Village(spots.maxBy { spotValue(g, p, it, w) + (rng.nextDouble() - 0.5) * lvl.noise })

        if (rng.nextDouble() < lvl.lazy) return RAct.End

        val plan = if (g.count(p, 1) < Realm.MAX_VILLAGES || g.roads(p) < 6) roadPlan(g, p, rng, lvl.noise) else null
        // goal: what we save up for
        val goal = when {
            towers.isNotEmpty() && (g.res[p][Realm.ORE] >= 1 || g.res[p][Realm.GRAIN] >= 2 || spots.isEmpty()) -> Realm.COST_TOWER
            spots.isNotEmpty() -> Realm.COST_VILLAGE
            plan != null -> Realm.COST_ROAD
            towers.isNotEmpty() -> Realm.COST_TOWER
            else -> Realm.COST_SPELL
        }

        if (g.canPlay(p, Realm.HARVEST)) {
            val m = missing(g, p, goal)
            if (m.sum() in 1..2) {
                val list = (0..4).flatMap { r -> List(m[r]) { r } } + listOf(Realm.ORE, Realm.GRAIN)
                return RAct.Spell(Realm.HARVEST, list[0], list[1])
            }
        }
        if (g.canPlay(p, Realm.EARTH) && plan != null) return RAct.Spell(Realm.EARTH)
        if (g.twist == Realm.ASH && g.canPlay(p, Realm.FIREBALL)) {
            g.forecast.indices.maxByOrNull { flowHarm(g, p, it) }?.takeIf { flowHarm(g, p, it) >= 4 }?.let { return RAct.Spell(Realm.FIREBALL, it) }
        }
        if (g.twist != Realm.ASH && lvl.fireball && g.canPlay(p, Realm.FIREBALL) && g.longestRoad(o) >= 4) {
            val before = g.longestRoad(o)
            val t = g.fireballTargets(p).maxBy { e -> val c = g.copy(); c.eOwner[e] = -1; before - c.longestRoad(o) }
            return RAct.Spell(Realm.FIREBALL, t)
        }

        if (goal === Realm.COST_ROAD || (plan != null && spots.isEmpty() && goal !== Realm.COST_TOWER)) {
            if (plan != null && g.can(p, Realm.COST_ROAD)) return RAct.Road(plan.first)
        }
        // long-road race for strong players once villages are placed
        if (lvl.skill >= 0.5 && g.can(p, Realm.COST_ROAD) && g.roads(p) < Realm.MAX_ROADS && spots.isEmpty() && plan == null && g.roadHolder != p &&
            g.longestRoad(p) + 2 >= g.longestRoad(o)) {
            val e = g.roadSpots(p).maxByOrNull { e -> val c = g.copy(); c.eOwner[e] = p; c.longestRoad(p) }
            if (e != null) return RAct.Road(e)
        }

        // trade toward the goal when the surplus covers every missing card
        val miss = missing(g, p, goal)
        if (miss.sum() > 0) {
            val spare = IntArray(5) { r -> maxOf(0, g.res[p][r] - goal[r]) / g.portRate(p, r) }
            if (spare.sum() >= miss.sum() && rng.nextDouble() < lvl.trade) {
                val give = (0..4).filter { spare[it] > 0 }.maxBy { g.res[p][it] - goal[it] }
                val get = (0..4).first { miss[it] > 0 }
                return RAct.Trade(give, get)
            }
            if (p == 1 && !offered && lvl.offer > 0 && miss.sum() == 1 && g.vp(0, false) < g.target - 2) {
                val want = (0..4).first { miss[it] > 0 }
                val k = if (lvl.offer == 2) 2 else 1
                val giveR = (0..4).filter { it != want && g.res[p][it] - goal[it] >= k }.maxByOrNull { g.res[p][it] - goal[it] }
                if (giveR != null && g.res[0][want] >= 1)
                    return RAct.Offer(IntArray(5).also { it[giveR] = k }, IntArray(5).also { it[want] = 1 })
            }
        }
        if (g.can(p, Realm.COST_SPELL) && g.deck.isNotEmpty() && (goal === Realm.COST_SPELL || g.total(p) >= 7)) return RAct.BuySpell
        if (plan != null && g.can(p, Realm.COST_ROAD) && g.total(p) >= 6) return RAct.Road(plan.first)
        // too many cards and a 7 might come: spend on a road
        return RAct.End
    }
}
