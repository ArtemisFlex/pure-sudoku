package com.puresudoku.core.cards

import com.puresudoku.core.Difficulty
import kotlin.random.Random

// =====================================================================
// Klondike solitaire
// =====================================================================

sealed interface Pile {
    data object Stock : Pile
    data object Waste : Pile
    data class Foundation(val i: Int) : Pile
    data class Tableau(val i: Int) : Pile
}

class Klondike(
    val drawCount: Int,
    /** How many times the waste may be turned back into the stock (-1 = unlimited). */
    val redeals: Int,
    val stock: MutableList<Int> = mutableListOf(),
    val waste: MutableList<Int> = mutableListOf(),
    val foundations: List<MutableList<Int>> = List(4) { mutableListOf() },
    val tableau: List<MutableList<Int>> = List(7) { mutableListOf() },
    /** Face-down cards at the bottom of each tableau pile. */
    val down: IntArray = IntArray(7),
    var redealsUsed: Int = 0,
) {
    companion object {
        data class Config(val draw: Int, val redeals: Int, val undo: Boolean, val basePoints: Int, val parSeconds: Int)

        fun config(d: Difficulty) = when (d) {
            Difficulty.EASY -> Config(1, -1, true, 10, 360)
            Difficulty.MEDIUM -> Config(1, 2, true, 12, 420)
            Difficulty.HARD -> Config(3, -1, true, 15, 480)
            Difficulty.EXPERT -> Config(3, 2, true, 20, 540)
            Difficulty.MASTER -> Config(3, 1, true, 25, 600)
            Difficulty.EXTREME -> Config(3, 0, false, 35, 600)
        }

        fun deal(d: Difficulty, rng: Random): Klondike {
            val cfg = config(d)
            val k = Klondike(cfg.draw, cfg.redeals)
            val deck = Cards.deck(rng)
            for (i in 0 until 7) {
                for (j in 0..i) k.tableau[i] += deck.removeAt(deck.lastIndex)
                k.down[i] = i
            }
            k.stock += deck
            return k
        }
    }

    fun copy() = Klondike(drawCount, redeals, stock.toMutableList(), waste.toMutableList(),
        foundations.map { it.toMutableList() }, tableau.map { it.toMutableList() }, down.copyOf(), redealsUsed)

    val won get() = foundations.all { it.size == 13 }
    fun faceUp(t: Int, k: Int) = k >= down[t]

    fun canRedeal() = stock.isEmpty() && waste.isNotEmpty() && (redeals < 0 || redealsUsed < redeals)

    /** Turns cards from stock to waste, or the waste back over. Returns false if impossible. */
    fun draw(): Boolean {
        if (stock.isNotEmpty()) {
            repeat(minOf(drawCount, stock.size)) { waste += stock.removeAt(stock.lastIndex) }
            return true
        }
        if (!canRedeal()) return false
        stock += waste.reversed(); waste.clear(); redealsUsed++
        return true
    }

    fun canFoundation(card: Int, f: Int): Boolean {
        val pile = foundations[f]
        return if (pile.isEmpty()) Cards.rank(card) == 1
        else Cards.suit(pile.last()) == Cards.suit(card) && Cards.rank(card) == Cards.rank(pile.last()) + 1
    }

    fun canTableau(card: Int, t: Int): Boolean {
        val pile = tableau[t]
        if (pile.isEmpty()) return Cards.rank(card) == 13
        val top = pile.last()
        return Cards.red(top) != Cards.red(card) && Cards.rank(card) == Cards.rank(top) - 1
    }

    /** Cards that would move if you picked up [from] at [index]. */
    fun run(from: Pile, index: Int): List<Int>? = when (from) {
        Pile.Waste -> waste.lastOrNull()?.let { listOf(it) }
        is Pile.Foundation -> foundations[from.i].lastOrNull()?.let { listOf(it) }
        is Pile.Tableau -> {
            val p = tableau[from.i]
            if (index in p.indices && faceUp(from.i, index)) p.subList(index, p.size).toList() else null
        }
        Pile.Stock -> null
    }

    fun legal(from: Pile, index: Int, to: Pile): Boolean {
        val cards = run(from, index) ?: return false
        if (from == to) return false
        return when (to) {
            is Pile.Foundation -> cards.size == 1 && canFoundation(cards[0], to.i)
            is Pile.Tableau -> canTableau(cards[0], to.i)
            else -> false
        }
    }

    /** Moves cards; returns true if a hidden card was turned over. */
    fun move(from: Pile, index: Int, to: Pile): Boolean {
        val cards = run(from, index)!!
        when (from) {
            Pile.Waste -> waste.removeAt(waste.lastIndex)
            is Pile.Foundation -> foundations[from.i].removeAt(foundations[from.i].lastIndex)
            is Pile.Tableau -> repeat(cards.size) { tableau[from.i].removeAt(tableau[from.i].lastIndex) }
            Pile.Stock -> Unit
        }
        when (to) {
            is Pile.Foundation -> foundations[to.i] += cards
            is Pile.Tableau -> tableau[to.i] += cards
            else -> Unit
        }
        if (from is Pile.Tableau) {
            val t = from.i
            if (tableau[t].isNotEmpty() && down[t] >= tableau[t].size) { down[t] = tableau[t].size - 1; return true }
            if (tableau[t].isEmpty()) down[t] = 0
        }
        return false
    }

    /** All destinations for picking up [from]/[index], foundations first. */
    fun destinations(from: Pile, index: Int): List<Pile> {
        val out = ArrayList<Pile>()
        for (f in 0 until 4) if (legal(from, index, Pile.Foundation(f))) out += Pile.Foundation(f)
        for (t in 0 until 7) if (legal(from, index, Pile.Tableau(t))) out += Pile.Tableau(t)
        return out
    }

    /** Any move that makes real progress, or a card to draw. */
    fun hasMoves(): Boolean {
        if (stock.isNotEmpty() || canRedeal()) return true
        if (waste.isNotEmpty() && destinations(Pile.Waste, 0).isNotEmpty()) return true
        for (t in 0 until 7) {
            val p = tableau[t]
            for (k in p.indices) {
                if (!faceUp(t, k)) continue
                for (d in destinations(Pile.Tableau(t), k)) {
                    // moving a whole pile from one empty column to another isn't progress
                    if (d is Pile.Tableau && k == 0 && down[t] == 0 && Cards.rank(p[0]) == 13) continue
                    // moving a run onto another pile is progress only if it reveals something
                    if (d is Pile.Tableau && k > down[t]) continue
                    return true
                }
            }
        }
        return false
    }
}

// =====================================================================
// Pyramid: Dungeon Delver
// =====================================================================

/** One floor: a 28-card pyramid (row by row, top first) and a 24-card stock. */
class PyramidDeal(val pyramid: IntArray, val stock: IntArray)

object PyramidRules {
    data class Config(val floors: Int, val passes: Int, val basePoints: Int, val parSeconds: Int)

    fun config(d: Difficulty) = when (d) {
        Difficulty.EASY -> Config(1, 3, 10, 240)
        Difficulty.MEDIUM -> Config(2, 3, 15, 420)
        Difficulty.HARD -> Config(2, 2, 25, 480)
        Difficulty.EXPERT -> Config(3, 2, 35, 660)
        Difficulty.MASTER -> Config(4, 2, 50, 900)
        Difficulty.EXTREME -> Config(5, 2, 75, 1200)
    }

    fun row(i: Int): Int { var r = 0; while ((r + 1) * (r + 2) / 2 <= i) r++; return r }
    fun index(r: Int, k: Int) = r * (r + 1) / 2 + k
    /** The two cards covering position [i], or empty for the bottom row. */
    fun children(i: Int): IntArray {
        val r = row(i); val k = i - index(r, 0)
        return if (r == 6) IntArray(0) else intArrayOf(index(r + 1, k), index(r + 1, k + 1))
    }

    fun exposed(i: Int, removed: Long) = (removed and (1L shl i)) == 0L && children(i).all { (removed and (1L shl it)) != 0L }

    /** Can this deal be cleared with [passes] trips through the stock? Depth-first search with memo. */
    fun solvable(deal: PyramidDeal, passes: Int, budget: Int = 250_000): Boolean? {
        val seen = HashSet<Long>()
        var nodes = 0
        val full = (1L shl 28) - 1
        fun value(c: Int) = Cards.rank(c)
        fun rec(removed: Long, stockGone: Int, pos: Int, pass: Int): Boolean {
            if (removed == full) return true
            if (++nodes > budget) throw IllegalStateException()
            val key = removed or (stockGone.toLong() shl 28) or (pos.toLong() shl 52) or (pass.toLong() shl 58)
            if (!seen.add(key)) return false
            // waste top = last drawn stock card not removed
            var w = -1
            for (s in pos - 1 downTo 0) if (stockGone and (1 shl s) == 0) { w = s; break }
            val ex = (0 until 28).filter { exposed(it, removed) }
            // kings alone
            for (i in ex) if (value(deal.pyramid[i]) == 13 && rec(removed or (1L shl i), stockGone, pos, pass)) return true
            if (w >= 0 && value(deal.stock[w]) == 13 && rec(removed, stockGone or (1 shl w), pos, pass)) return true
            // pairs making 13
            for (a in ex.indices) for (b in a + 1 until ex.size) {
                val i = ex[a]; val j = ex[b]
                if (value(deal.pyramid[i]) + value(deal.pyramid[j]) == 13 && j !in children(i) && i !in children(j) &&
                    rec(removed or (1L shl i) or (1L shl j), stockGone, pos, pass)) return true
            }
            if (w >= 0) for (i in ex) if (value(deal.pyramid[i]) + value(deal.stock[w]) == 13 &&
                rec(removed or (1L shl i), stockGone or (1 shl w), pos, pass)) return true
            // draw / turn the waste over
            if (pos < 24) return rec(removed, stockGone, pos + 1, pass)
            if (pass + 1 < passes) return rec(removed, stockGone, 0, pass + 1)
            return false
        }
        return try { rec(0, 0, 0, 0) } catch (e: IllegalStateException) { null }
    }

    fun deal(rng: Random): PyramidDeal {
        val d = Cards.deck(rng)
        return PyramidDeal(d.take(28).toIntArray(), d.drop(28).toIntArray())
    }

    /** A deal the solver proves can be cleared. */
    fun solvableDeal(passes: Int, rng: Random): PyramidDeal {
        repeat(4000) {
            val d = deal(rng)
            if (solvable(d, passes) == true) return d
        }
        error("no solvable pyramid")
    }
}

// =====================================================================
// Scoundrel (Zach Gage & Kurt Bieg)
// =====================================================================

class Scoundrel(
    val maxHealth: Int,
    /** How many rooms you may avoid in total (-1 = no limit beyond the "not twice in a row" rule). */
    val avoidLimit: Int,
    val dungeon: MutableList<Int> = mutableListOf(),
    val room: MutableList<Int> = mutableListOf(),
    var health: Int = maxHealth,
    var weapon: Int = -1,
    /** Monsters slain with the current weapon, in order. */
    val slain: MutableList<Int> = mutableListOf(),
    var potionUsed: Boolean = false,
    var avoidedLast: Boolean = false,
    var avoids: Int = 0,
    var resolvedInRoom: Int = 0,
    var lastCard: Int = -1,
) {
    companion object {
        data class Config(val health: Int, val avoidLimit: Int, val basePoints: Int, val parSeconds: Int)

        fun config(d: Difficulty) = when (d) {
            Difficulty.EASY -> Config(26, -1, 10, 300)
            Difficulty.MEDIUM -> Config(23, -1, 15, 300)
            Difficulty.HARD -> Config(20, -1, 25, 300)
            Difficulty.EXPERT -> Config(20, 4, 35, 300)
            Difficulty.MASTER -> Config(20, 2, 50, 300)
            Difficulty.EXTREME -> Config(18, 0, 75, 300)
        }

        /** Standard deck minus jokers, red face cards and red aces: 44 cards. */
        fun newGame(d: Difficulty, rng: Random): Scoundrel {
            val cfg = config(d)
            val s = Scoundrel(cfg.health, cfg.avoidLimit)
            s.dungeon += (0 until 52).filter { c -> !(Cards.red(c) && (Cards.rank(c) == 1 || Cards.rank(c) >= 11)) }.shuffled(rng)
            s.fillRoom()
            return s
        }

        /** Ace counts 14. */
        fun value(c: Int) = if (Cards.rank(c) == 1) 14 else Cards.rank(c)
        fun isMonster(c: Int) = Cards.suit(c) == 0 || Cards.suit(c) == 3
        fun isWeapon(c: Int) = Cards.suit(c) == 1
        fun isPotion(c: Int) = Cards.suit(c) == 2
    }

    val dead get() = health <= 0
    val cleared get() = dungeon.isEmpty() && room.isEmpty()

    fun fillRoom() {
        while (room.size < 4 && dungeon.isNotEmpty()) room += dungeon.removeAt(0)
        resolvedInRoom = 0
        potionUsed = false
    }

    fun canAvoid() = resolvedInRoom == 0 && room.size == 4 && !avoidedLast && (avoidLimit < 0 || avoids < avoidLimit) && dungeon.isNotEmpty()

    fun avoid() {
        dungeon += room; room.clear()
        avoidedLast = true; avoids++
        fillRoom()
    }

    /** Can the equipped weapon be used on this monster? */
    fun weaponUsable(m: Int) = weapon >= 0 && (slain.isEmpty() || value(m) < value(slain.last()))

    fun damage(m: Int, useWeapon: Boolean) = if (useWeapon) maxOf(0, value(m) - value(weapon)) else value(m)

    /** Resolves a room card. Returns a short description of what happened. */
    fun resolve(card: Int, useWeapon: Boolean): String {
        room.remove(card)
        lastCard = card
        val msg = when {
            isPotion(card) -> if (potionUsed) "Only one potion per room — this one fizzles" else {
                val before = health
                health = minOf(maxHealth, health + value(card)); potionUsed = true
                "Healed ${health - before}"
            }
            isWeapon(card) -> { weapon = card; slain.clear(); "Equipped a ${value(card)} weapon" }
            else -> {
                val use = useWeapon && weaponUsable(card)
                val dmg = damage(card, use)
                health -= dmg
                if (use) slain += card
                if (dmg == 0) "Slain without a scratch" else "Took $dmg damage"
            }
        }
        resolvedInRoom++
        avoidedLast = false
        if (!dead && room.size <= 1 && dungeon.isNotEmpty()) fillRoom()
        return msg
    }

    /** Official scoring: health left (plus a final potion at full health), or minus remaining monsters if you died. */
    fun finalScore(): Int = if (dead) {
        health - (dungeon + room).filter { isMonster(it) }.sumOf { value(it) }
    } else {
        health + if (lastCard >= 0 && isPotion(lastCard) && health == maxHealth) value(lastCard) else 0
    }
}
