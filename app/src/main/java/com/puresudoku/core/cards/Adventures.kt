package com.puresudoku.core.cards

import com.puresudoku.core.Difficulty
import kotlin.random.Random

const val JOKER = 52

// =====================================================================
// Regicide (solo) — Badgers from Mars
// =====================================================================

class Regicide(val maxHand: Int, var jesters: Int, val rng: Random) {
    val castle = mutableListOf<Int>()
    val tavern = mutableListOf<Int>()   // index 0 = top
    val discard = mutableListOf<Int>()
    val hand = mutableListOf<Int>()
    val played = mutableListOf<Int>()
    var enemy = -1
    var damage = 0
    var shield = 0
    /** > 0 while you must discard cards worth at least this much. */
    var suffer = 0
    var jestersUsed = 0
    var defeated = 0
    var lost = false
    val log = mutableListOf<String>()

    companion object {
        data class Config(val hand: Int, val jesters: Int, val basePoints: Int, val parSeconds: Int)
        fun config(d: Difficulty) = when (d) {
            Difficulty.EASY -> Config(9, 3, 10, 900)
            Difficulty.MEDIUM -> Config(8, 2, 15, 900)
            Difficulty.HARD -> Config(8, 1, 25, 900)
            Difficulty.EXPERT -> Config(8, 0, 35, 900)
            Difficulty.MASTER -> Config(7, 1, 50, 900)
            Difficulty.EXTREME -> Config(7, 0, 75, 900)
        }

        fun newGame(d: Difficulty, rng: Random): Regicide {
            val cfg = config(d)
            val g = Regicide(cfg.hand, cfg.jesters, rng)
            for (rank in listOf(11, 12, 13)) g.castle += (0..3).map { Cards.of(rank, it) }.shuffled(rng)
            g.tavern += (0 until 52).filter { Cards.rank(it) <= 10 }.shuffled(rng)
            g.draw(g.maxHand)
            g.nextEnemy()
            return g
        }

        /** Attack value of a card: companions 1, numbers face value, J 10, Q 15, K 20. */
        fun value(c: Int) = when (Cards.rank(c)) { 11 -> 10; 12 -> 15; 13 -> 20; else -> Cards.rank(c) }
        fun health(e: Int) = when (Cards.rank(e)) { 11 -> 20; 12 -> 30; else -> 40 }
        fun attack(e: Int) = when (Cards.rank(e)) { 11 -> 10; 12 -> 15; else -> 20 }
        fun power(s: Int) = when (s) { 0 -> "double damage"; 1 -> "draw cards"; 2 -> "heal the deck"; else -> "shield" }
    }

    val won get() = enemy < 0 && castle.isEmpty() && !lost
    val health get() = if (enemy < 0) 0 else health(enemy) - damage
    val enemyAttack get() = if (enemy < 0) 0 else maxOf(0, attack(enemy) - shield)
    val immune get() = if (enemy < 0) -1 else Cards.suit(enemy)

    fun draw(n: Int): Int {
        var k = 0
        while (k < n && hand.size < maxHand && tavern.isNotEmpty()) { hand += tavern.removeAt(0); k++ }
        return k
    }

    private fun nextEnemy() {
        enemy = if (castle.isEmpty()) -1 else castle.removeAt(0)
        damage = 0; shield = 0
    }

    /** Is this set of cards a legal play? */
    fun valid(cards: List<Int>): Boolean {
        if (cards.isEmpty() || !hand.containsAll(cards)) return false
        if (cards.size == 1) return true
        val aces = cards.count { Cards.rank(it) == 1 }
        if (cards.size == 2 && aces >= 1) return true
        if (aces > 0) return false
        val r = Cards.rank(cards[0])
        return r <= 10 && cards.all { Cards.rank(it) == r } && cards.sumOf { value(it) } <= 10
    }

    fun play(cards: List<Int>): String {
        require(suffer == 0 && valid(cards))
        val attack = cards.sumOf { value(it) }
        val suits = cards.map { Cards.suit(it) }.toSet() - immune
        hand.removeAll(cards); played += cards
        val notes = mutableListOf<String>()
        if (2 in suits) {
            discard.shuffle(rng)
            val n = minOf(attack, discard.size)
            repeat(n) { tavern += discard.removeAt(discard.lastIndex) }
            if (n > 0) notes += "healed $n cards into the deck"
        }
        if (1 in suits) { val n = draw(attack); if (n > 0) notes += "drew $n" }
        if (3 in suits) { shield += attack; notes += "shield $shield" }
        val dmg = if (0 in suits) attack * 2 else attack
        damage += dmg
        notes.add(0, "dealt $dmg")
        if (immune in cards.map { Cards.suit(it) }) notes += "${Cards.suitName(immune)} power blocked"
        if (damage >= health(enemy)) {
            val exact = damage == health(enemy)
            if (exact) tavern.add(0, enemy) else discard += enemy
            notes += if (exact) "exact kill — ${Cards.label(enemy)} joins your deck" else "defeated ${Cards.label(enemy)}"
            discard += played; played.clear()
            defeated++
            nextEnemy()
        } else {
            beginSuffer()
        }
        return notes.joinToString(", ")
    }

    fun yieldTurn() { require(suffer == 0); beginSuffer() }

    private fun beginSuffer() {
        suffer = enemyAttack
        if (suffer > 0 && hand.sumOf { value(it) } < suffer && jesters == 0) lost = true
    }

    fun canSurvive() = hand.sumOf { value(it) } >= suffer

    fun takeHit(cards: List<Int>) {
        require(suffer > 0 && cards.sumOf { value(it) } >= suffer && hand.containsAll(cards))
        hand.removeAll(cards); discard += cards
        suffer = 0
    }

    /** Solo jester: discard your hand and draw a fresh one. */
    fun useJester() {
        require(jesters > 0)
        discard += hand; hand.clear()
        draw(maxHand)
        jesters--; jestersUsed++
        if (suffer > 0 && !canSurvive() && jesters == 0) lost = true
    }
}

// =====================================================================
// Dungeon Solitaire: Tomb of Four Kings — Matthew Lowes
// =====================================================================

class DungeonTurn(val cards: MutableList<Int> = mutableListOf(), var encounter: Int = -1, var result: Int = 0) // result 0 open, 1 won, -1 lost

class DungeonSolitaire(val maxHp: Int, val kingsNeeded: Int, val rng: Random) {
    val deck = mutableListOf<Int>()     // top = last
    var hp = maxHp
    var torches = 0
    val hand = mutableListOf<Int>()      // skills (jacks) and treasure
    val delve = mutableListOf<DungeonTurn>()
    val retreat = mutableListOf<DungeonTurn>()
    var retreating = false
    var retreatGoal = 0
    var current: DungeonTurn? = null
    var favor = false
    var escaped = false
    var dead = false
    var lostInDark = false
    val log = mutableListOf<String>()

    companion object {
        data class Config(val hp: Int, val kings: Int, val basePoints: Int, val parSeconds: Int)
        fun config(d: Difficulty) = when (d) {
            Difficulty.EASY -> Config(12, 0, 10, 600)
            Difficulty.MEDIUM -> Config(10, 0, 15, 600)
            Difficulty.HARD -> Config(9, 1, 25, 600)
            Difficulty.EXPERT -> Config(9, 2, 35, 600)
            Difficulty.MASTER -> Config(9, 3, 50, 600)
            Difficulty.EXTREME -> Config(9, 4, 75, 600)
        }

        fun newGame(d: Difficulty, rng: Random): DungeonSolitaire {
            val cfg = config(d)
            val g = DungeonSolitaire(cfg.hp, cfg.kings, rng)
            g.deck += ((0 until 52).filter { !(Cards.suit(it) == 2 && Cards.rank(it) in 2..10) } + JOKER).shuffled(rng)
            return g
        }

        fun kind(e: Int) = when (Cards.suit(e)) { 3 -> "monster"; 1 -> "trap"; else -> "door" }
        fun isTreasure(c: Int) = c == JOKER || Cards.rank(c) == 13 || (Cards.suit(c) == 1 && Cards.rank(c) in 2..10)
        fun treasureValue(c: Int) = when { c == JOKER -> 6; Cards.rank(c) == 13 -> 10; else -> Cards.rank(c) }
        fun skillName(c: Int) = when (Cards.suit(c)) { 3 -> "Go Berserk"; 1 -> "Disarm Mechanism"; 0 -> "Pick Lock"; else -> "Dodge Blow" }
    }

    val over get() = escaped || dead || lostInDark
    val kings get() = hand.count { it != JOKER && Cards.rank(it) == 13 }
    val treasure get() = hand.filter { isTreasure(it) }.sumOf { treasureValue(it) }
    fun has(skillSuit: Int) = hand.any { it != JOKER && Cards.rank(it) == 11 && Cards.suit(it) == skillSuit }
    private fun spend(skillSuit: Int) { hand.removeAll { it != JOKER && Cards.rank(it) == 11 && Cards.suit(it) == skillSuit } }

    val turnOpen get() = current?.result == 0
    /** Whether you're choosing to delve on or turn back. */
    val choosing get() = !over && current == null && delve.isNotEmpty() && !retreating

    private fun damage(n: Int) {
        if (n <= 0) return
        if (hp - n <= 0 && has(2)) { spend(2); log += "Dodge Blow saved you from $n damage"; return }
        hp -= n
        log += "Took $n damage"
        if (hp <= 0) { dead = true; log += "You have fallen in the tomb" }
    }

    private fun torch(c: Int) {
        torches++
        log += "A torch burns out (${Cards.label(c)})"
        if (torches >= 4) {
            if (hand.contains(JOKER)) {
                hand.remove(JOKER); torches--; deck.add(0, c)
                log += "The Scroll of Light shows the way"
            } else { lostInDark = true; log += "Your last torch is gone. Lost in the dark" }
        }
    }

    /** Turns the next card. */
    fun flip() {
        if (over) return
        if (current == null) { current = DungeonTurn(); favor = false; (if (retreating) retreat else delve) += current!! }
        val t = current!!
        if (deck.isEmpty()) { lostInDark = true; log += "The deck ran out. Lost in the dark"; return }
        val c = deck.removeAt(deck.lastIndex)
        t.cards += c
        if (c == JOKER) { log += "Found the Scroll of Light"; return }
        val r = Cards.rank(c)
        when {
            r == 1 -> torch(c)
            r == 11 -> { t.cards.remove(c); hand += c; log += "Gained a skill: ${skillName(c)}" }
            r == 12 -> if (t.encounter < 0) { favor = true; log += "Divine favor: the next encounter is won" } else win("Divine favor wins the encounter")
            r == 13 -> log += "A tomb hoard: ${Cards.label(c)}!"
            t.encounter < 0 -> {
                t.encounter = c
                log += "Encounter: a ${kind(c)} (${r})"
                if (favor) win("Divine favor wins the encounter")
            }
            else -> action(r)
        }
    }

    private fun action(a: Int) {
        val t = current!!
        val e = Cards.rank(t.encounter)
        when (kind(t.encounter)) {
            "monster" -> if (a >= e) win("Slew the monster") else damage(e - a)
            "trap" -> if (a >= e) win("Passed the trap") else { damage(e - a); lose("The trap caught you") }
            else -> if (a >= e) win("Opened the door") else {
                val n = e - a
                log += "The door holds. $n cards are lost"
                repeat(n) { if (deck.isNotEmpty() && !over) { val x = deck.removeAt(deck.lastIndex); if (x != JOKER && Cards.rank(x) == 1) torch(x) } }
                lose("The door stays sealed")
            }
        }
    }

    private fun win(msg: String) {
        val t = current!!
        t.result = 1
        val loot = t.cards.filter { isTreasure(it) }
        hand += loot
        log += msg + if (loot.isNotEmpty()) ". Treasure: ${loot.joinToString { if (it == JOKER) "Scroll" else Cards.label(it) }}" else ""
        endTurn()
    }

    private fun lose(msg: String) {
        current!!.result = -1
        log += msg
        endTurn()
    }

    private fun endTurn() {
        current = null
        if (over) return
        if (retreating && retreat.size >= retreatGoal) { escaped = true; log += "You escaped the tomb!" }
    }

    /** Skills usable right now. */
    fun usableSkill(): Int? {
        val t = current ?: return null
        if (t.result != 0 || t.encounter < 0) return null
        val s = when (kind(t.encounter)) { "monster" -> 3; "trap" -> 1; else -> 0 }
        return if (has(s)) s else null
    }

    fun useSkill() {
        val s = usableSkill() ?: return
        spend(s)
        win(when (s) { 3 -> "You went berserk"; 1 -> "Disarmed the trap"; else -> "Picked the lock" })
    }

    /** Treasure that could buy off the current monster. */
    fun dropOptions(): List<Int> {
        val t = current ?: return emptyList()
        if (t.result != 0 || t.encounter < 0 || kind(t.encounter) != "monster") return emptyList()
        return hand.filter { isTreasure(it) && treasureValue(it) >= Cards.rank(t.encounter) }
    }

    fun dropTreasure(c: Int) {
        hand.remove(c)
        current!!.result = -1
        log += "Dropped ${if (c == JOKER) "the Scroll" else Cards.label(c)} to escape the monster"
        endTurn()
    }

    fun turnBack() {
        retreating = true
        retreatGoal = maxOf(0, delve.size - 1)
        log += "You turn back toward the surface"
        if (retreatGoal == 0) { escaped = true; log += "You escaped the tomb!" }
    }

    val wonGame get() = escaped && kings >= kingsNeeded
}

// =====================================================================
// Joker Jailbreak
// =====================================================================

class Jailbreak(val jokerCap: Int, val rng: Random) {
    /** 0..3 walls (N, E, S, W; 6 cards), 4..7 corners (2 cards). Top = last, face up. */
    val stacks = List(8) { mutableListOf<Int>() }
    val joker = mutableListOf<Int>()
    val remainder = mutableListOf<Int>()

    companion object {
        data class Config(val cap: Int, val basePoints: Int, val parSeconds: Int)
        fun config(d: Difficulty) = when (d) {
            Difficulty.EASY -> Config(5, 10, 300)
            Difficulty.MEDIUM -> Config(4, 15, 300)
            Difficulty.HARD -> Config(3, 25, 300)
            Difficulty.EXPERT -> Config(3, 35, 240)
            Difficulty.MASTER -> Config(2, 50, 240)
            Difficulty.EXTREME -> Config(1, 75, 240)
        }

        fun deal(cap: Int, rng: Random): Jailbreak {
            val g = Jailbreak(cap, rng)
            val deck = Cards.deck(rng)
            for (w in 0..3) repeat(6) { g.stacks[w] += deck.removeAt(deck.lastIndex) }
            for (c in 4..7) repeat(2) { g.stacks[c] += deck.removeAt(deck.lastIndex) }
            g.remainder += deck
            return g
        }

        /** A deal the solver proves can be won. */
        fun newGame(d: Difficulty, rng: Random): Jailbreak {
            val cap = config(d).cap
            repeat(3000) {
                val g = deal(cap, rng)
                if (g.copy().solvable() == true) return g
            }
            return deal(cap, rng)
        }

        fun value(c: Int) = Cards.rank(c)
    }

    /** Open cards: (source, card) where source 0..7 is a stack and 8 is the joker pile. */
    fun open(): List<Pair<Int, Int>> = stacks.mapIndexedNotNull { i, s -> s.lastOrNull()?.let { i to it } } +
        listOfNotNull(joker.lastOrNull()?.let { 8 to it })

    fun cardAt(src: Int) = if (src == 8) joker.lastOrNull() else stacks[src].lastOrNull()

    /** One black card against red cards that add up to it. */
    fun validMatch(sources: Set<Int>): Boolean {
        val cards = sources.mapNotNull { cardAt(it) }
        if (cards.size != sources.size || cards.size < 2) return false
        val black = cards.filter { !Cards.red(it) }
        val red = cards.filter { Cards.red(it) }
        return black.size == 1 && red.isNotEmpty() && red.sumOf { value(it) } == value(black[0])
    }

    fun match(sources: Set<Int>) {
        require(validMatch(sources))
        for (s in sources) if (s == 8) joker.removeAt(joker.lastIndex) else stacks[s].removeAt(stacks[s].lastIndex)
    }

    fun canPlaceRemainder() = remainder.isNotEmpty() && joker.size < jokerCap
    fun placeRemainder() { require(canPlaceRemainder()); joker += remainder.removeAt(remainder.lastIndex) }

    val freed get() = joker.isEmpty() && (0..3).any { stacks[it].isEmpty() }

    fun anyMatch(): Set<Int>? {
        val o = open().map { it.first }
        for (mask in 1 until (1 shl o.size)) {
            val set = o.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
            if (set.size >= 2 && validMatch(set)) return set
        }
        return null
    }

    val stuck get() = !freed && anyMatch() == null && !canPlaceRemainder()

    fun copy(): Jailbreak {
        val g = Jailbreak(jokerCap, rng)
        for (i in 0..7) g.stacks[i] += stacks[i]
        g.joker += joker; g.remainder += remainder
        return g
    }

    private fun key(): String = stacks.joinToString(",") { it.size.toString() } + "|" + remainder.size + "|" + joker.joinToString(",")

    /** Depth-first search over every match and placement (null if it took too long). */
    fun solvable(budget: Int = 60_000): Boolean? {
        val seen = HashSet<String>()
        var nodes = 0
        fun all(): List<Set<Int>> {
            val o = open().map { it.first }
            val out = ArrayList<Set<Int>>()
            for (mask in 1 until (1 shl o.size)) {
                val set = o.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
                if (set.size >= 2 && validMatch(set)) out += set
            }
            return out
        }
        fun rec(): Boolean {
            if (freed) return true
            if (++nodes > budget) throw IllegalStateException()
            if (!seen.add(key())) return false
            for (m in all()) {
                val removed = m.map { src -> src to (if (src == 8) joker.removeAt(joker.lastIndex) else stacks[src].removeAt(stacks[src].lastIndex)) }
                val ok = rec()
                for ((src, c) in removed.reversed()) if (src == 8) joker += c else stacks[src] += c
                if (ok) return true
            }
            if (canPlaceRemainder()) {
                joker += remainder.removeAt(remainder.lastIndex)
                val ok = rec()
                remainder += joker.removeAt(joker.lastIndex)
                if (ok) return true
            }
            return false
        }
        return try { rec() } catch (e: IllegalStateException) { null }
    }
}
