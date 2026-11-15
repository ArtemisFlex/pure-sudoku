package com.puresudoku.core.learn

import kotlin.math.abs
import kotlin.random.Random

/**
 * Flashcards for early learners, ages 4 to 10. Each game deals a deck for one level: Easy is
 * pitched at a 4-year-old and Extreme at a 10-year-old. Every card carries its answer and a
 * worked explanation, step by step, so a child who gets it wrong (or taps the i button) can
 * see how it's done.
 */
data class FlashCard(
    /** The question, big on the card ("7 + 5 = ?", "dog", a sentence...). */
    val prompt: String,
    /** A line under the prompt: what to do ("How many apples?"). */
    val ask: String,
    /** Pictures (emoji) shown on the card; may be empty. */
    val picture: String = "",
    /** Multiple-choice answers; empty means the answer is typed on the number pad. */
    val choices: List<String> = emptyList(),
    val answer: String,
    /** How to work it out, one step per line. */
    val steps: List<String>,
    /** A special drawing: "div:dividend:divisor" (long division), "clock:h:m" (a clock face). */
    val visual: String = "",
)

enum class LearnGame { MATH, DIVISION, ALGEBRA, LETTERS, WORDS, READING, CLOCK }

object Learn {
    /** Ages each level is pitched at. */
    val AGES = listOf("Age 4", "Age 5", "Ages 6–7", "Age 8", "Age 9", "Age 10")

    fun deck(game: LearnGame, level: Int, rng: Random, size: Int = 10): List<FlashCard> {
        val out = ArrayList<FlashCard>()
        var guard = 0
        while (out.size < size && guard++ < size * 20) {
            val c = card(game, level, rng)
            if (out.none { it.prompt == c.prompt && it.visual == c.visual && it.ask == c.ask && it.answer == c.answer }) out += c
        }
        return out
    }

    fun card(game: LearnGame, level: Int, r: Random): FlashCard = when (game) {
        LearnGame.MATH -> MathCards.card(level, r)
        LearnGame.DIVISION -> DivisionCards.card(level, r)
        LearnGame.ALGEBRA -> AlgebraCards.card(level, r)
        LearnGame.LETTERS -> WordCards.letters(level, r)
        LearnGame.WORDS -> WordCards.words(level, r)
        LearnGame.READING -> ReadingCards.card(level, r)
        LearnGame.CLOCK -> ClockCards.card(level, r)
    }

    /** Shuffled choices: the answer plus distinct wrong ones. */
    fun choices(answer: String, wrong: Collection<String>, count: Int, r: Random): List<String> =
        (listOf(answer) + wrong.filter { it != answer }.distinct().shuffled(r).take(count - 1)).shuffled(r)

    /** Number choices near the answer. */
    fun numberChoices(answer: Int, count: Int, r: Random, spread: Int = 3, min: Int = 0): List<String> {
        val wrong = HashSet<Int>()
        var tries = 0
        while (wrong.size < count - 1 && tries++ < 200) {
            val w = answer + r.nextInt(-spread, spread + 1).let { if (it == 0) spread + 1 else it }
            if (w >= min && w != answer) wrong += w
        }
        return choices(answer.toString(), wrong.map { it.toString() }, count, r)
    }

    val COUNTABLE = listOf("🍎" to "apples", "⭐" to "stars", "🐟" to "fish", "🎈" to "balloons", "🐞" to "ladybugs", "🍓" to "strawberries", "🌸" to "flowers", "🐥" to "chicks")
}

// =====================================================================  math facts

object MathCards {
    fun card(level: Int, r: Random): FlashCard = when (level) {
        0 -> if (r.nextBoolean()) counting(r, 1, 10) else pictureSum(r, 5)
        1 -> if (r.nextBoolean()) add(r, 0, 10, 4, total = 10) else sub(r, 10, 4)
        2 -> when (r.nextInt(3)) { 0 -> add(r, 2, 20, 4, total = 20); 1 -> sub(r, 20, 4); else -> twoDigitAdd(r, carry = r.nextBoolean(), typed = false) }
        3 -> when (r.nextInt(4)) { 0, 1 -> times(r, 10); 2 -> divFact(r, 10); else -> twoDigitSub(r, typed = true) }
        4 -> when (r.nextInt(3)) { 0 -> multiDigitTimes(r, 2, 1); 1 -> threeDigitAddSub(r); else -> times(r, 12) }
        else -> when (r.nextInt(3)) { 0 -> multiDigitTimes(r, 2, 2); 1 -> multiDigitTimes(r, 3, 1); else -> orderOfOps(r) }
    }

    private fun counting(r: Random, lo: Int, hi: Int): FlashCard {
        val n = r.nextInt(lo, hi + 1)
        val (pic, name) = Learn.COUNTABLE[r.nextInt(Learn.COUNTABLE.size)]
        return FlashCard("How many?", "Count the $name", pic.repeat(n), Learn.numberChoices(n, 3, r, 2, 1), "$n",
            listOf("Point at each one as you count.", (1..n).joinToString(", "), "The last number you say is how many: $n."))
    }

    private fun pictureSum(r: Random, total: Int): FlashCard {
        val a = r.nextInt(1, total); val b = r.nextInt(1, total - a + 1)
        val (pic, name) = Learn.COUNTABLE[r.nextInt(Learn.COUNTABLE.size)]
        return FlashCard("$a + $b = ?", "Add the $name", pic.repeat(a) + "  +  " + pic.repeat(b), Learn.numberChoices(a + b, 3, r, 2, 1), "${a + b}",
            listOf("Count the first group: $a.", "Keep counting the second group: " + (a + 1..a + b).joinToString(", ") + ".", "Altogether there are ${a + b}."))
    }

    private fun add(r: Random, lo: Int, hi: Int, n: Int, total: Int): FlashCard {
        val a = r.nextInt(lo, hi); val b = r.nextInt(0, total - a + 1)
        val steps = if (a + b <= 10) listOf("Start at the bigger number, ${maxOf(a, b)}.", "Count on ${minOf(a, b)} more: " + (maxOf(a, b) + 1..a + b).joinToString(", ").ifEmpty { "nothing to add" } + ".", "$a + $b = ${a + b}.")
        else listOf("Make a ten: ${maxOf(a, b)} needs ${10 - maxOf(a, b) % 10} more to reach ${(maxOf(a, b) / 10 + 1) * 10}.",
            "Take ${10 - maxOf(a, b) % 10} from ${minOf(a, b)}, which leaves ${minOf(a, b) - (10 - maxOf(a, b) % 10)}.",
            "${(maxOf(a, b) / 10 + 1) * 10} + ${minOf(a, b) - (10 - maxOf(a, b) % 10)} = ${a + b}.")
        return FlashCard("$a + $b = ?", "Add", choices = Learn.numberChoices(a + b, n, r), answer = "${a + b}", steps = steps)
    }

    private fun sub(r: Random, hi: Int, n: Int): FlashCard {
        val a = r.nextInt(1, hi + 1); val b = r.nextInt(0, a + 1)
        return FlashCard("$a − $b = ?", "Take away", choices = Learn.numberChoices(a - b, n, r), answer = "${a - b}",
            steps = listOf("Start at $a.", "Count back $b: " + (a - 1 downTo a - b).joinToString(", ").ifEmpty { "nothing to take away" } + ".", "$a − $b = ${a - b}.",
                "Check: ${a - b} + $b = $a."))
    }

    /** Column addition with carrying, worked digit by digit. */
    fun columnAddSteps(a: Int, b: Int): List<String> {
        val out = ArrayList<String>()
        val places = listOf("ones", "tens", "hundreds", "thousands")
        var carry = 0; var x = a; var y = b; var k = 0
        out += "Line the numbers up by place value: ones under ones."
        while (x > 0 || y > 0 || carry > 0) {
            val d = x % 10 + y % 10 + carry
            out += "${places.getOrElse(k) { "next" }.replaceFirstChar { it.uppercase() }}: ${x % 10} + ${y % 10}" + (if (carry > 0) " + $carry carried" else "") + " = $d" +
                if (d >= 10) ". Write ${d % 10}, carry 1." else ". Write $d."
            carry = d / 10; x /= 10; y /= 10; k++
        }
        out += "$a + $b = ${a + b}."
        return out
    }

    /** Column subtraction with borrowing. */
    fun columnSubSteps(a: Int, b: Int): List<String> {
        val out = ArrayList<String>()
        val places = listOf("ones", "tens", "hundreds", "thousands")
        val top = a.toString().reversed().map { it - '0' }.toMutableList()
        val bot = b.toString().reversed().map { it - '0' }
        out += "Line the numbers up by place value: ones under ones."
        for (k in top.indices) {
            val t = top[k]; val u = bot.getOrElse(k) { 0 }
            if (t < u) {
                var j = k + 1
                while (top[j] == 0) { top[j] = 9; j++ }
                top[j]--
                out += "${places[k].replaceFirstChar { it.uppercase() }}: $t is less than $u, so borrow 1 from the ${places[k + 1]}. Now ${t + 10} − $u = ${t + 10 - u}."
                top[k] = t + 10
            } else out += "${places[k].replaceFirstChar { it.uppercase() }}: $t − $u = ${t - u}."
        }
        out += "$a − $b = ${a - b}."
        out += "Check: ${a - b} + $b = $a."
        return out
    }

    private fun twoDigitAdd(r: Random, carry: Boolean, typed: Boolean): FlashCard {
        var a: Int; var b: Int
        do { a = r.nextInt(10, 90); b = r.nextInt(1, 100 - a) } while ((a % 10 + b % 10 >= 10) != carry)
        return FlashCard("$a + $b = ?", "Add", choices = if (typed) emptyList() else Learn.numberChoices(a + b, 4, r, 10), answer = "${a + b}", steps = columnAddSteps(a, b))
    }

    private fun twoDigitSub(r: Random, typed: Boolean): FlashCard {
        val a = r.nextInt(20, 100); val b = r.nextInt(10, a)
        return FlashCard("$a − $b = ?", "Subtract", choices = if (typed) emptyList() else Learn.numberChoices(a - b, 4, r, 10), answer = "${a - b}", steps = columnSubSteps(a, b))
    }

    private fun threeDigitAddSub(r: Random): FlashCard {
        val a = r.nextInt(120, 900); val b = r.nextInt(30, minOf(a, 999 - a).coerceAtLeast(31))
        return if (r.nextBoolean()) FlashCard("$a + $b = ?", "Add", answer = "${a + b}", steps = columnAddSteps(a, b))
        else FlashCard("$a − $b = ?", "Subtract", answer = "${a - b}", steps = columnSubSteps(a, b))
    }

    private fun times(r: Random, max: Int): FlashCard {
        val a = r.nextInt(1, max + 1); val b = r.nextInt(1, max + 1)
        val steps = mutableListOf("$a × $b means $b groups of $a (or $a groups of $b).")
        if (b <= 5) steps += "Skip-count by $a, $b times: " + (1..b).joinToString(", ") { "${a * it}" } + "."
        else steps += "Split it: $a × 5 = ${a * 5}, and $a × ${b - 5} = ${a * (b - 5)}.  ${a * 5} + ${a * (b - 5)} = ${a * b}."
        steps += "$a × $b = ${a * b}."
        return FlashCard("$a × $b = ?", "Multiply", answer = "${a * b}", steps = steps)
    }

    private fun divFact(r: Random, max: Int): FlashCard {
        val b = r.nextInt(2, max + 1); val q = r.nextInt(1, max + 1); val a = b * q
        return FlashCard("$a ÷ $b = ?", "Divide", answer = "$q",
            steps = listOf("Division asks: how many groups of $b make $a?", "Think of the times table: $b × ? = $a.", "$b × $q = $a, so $a ÷ $b = $q."))
    }

    /** Long multiplication: partial products for each digit of the bottom number. */
    fun multiplySteps(a: Int, b: Int): List<String> {
        val out = ArrayList<String>()
        val digits = b.toString().reversed()
        out += "Write $a on top and $b underneath, lined up on the right."
        val parts = ArrayList<Int>()
        for ((k, ch) in digits.withIndex()) {
            val d = ch - '0'
            val place = listOf(1, 10, 100)[k]
            val part = a * d * place
            parts += part
            out += if (k == 0) "Multiply $a by the ones digit $d: $a × $d = ${a * d}."
            else "Multiply $a by the ${if (k == 1) "tens" else "hundreds"} digit $d: $a × $d = ${a * d}, then add ${if (k == 1) "a zero" else "two zeros"}: $part."
        }
        if (parts.size > 1) out += "Add the rows: " + parts.joinToString(" + ") + " = ${a * b}."
        out += "$a × $b = ${a * b}."
        return out
    }

    private fun multiDigitTimes(r: Random, topDigits: Int, bottomDigits: Int): FlashCard {
        val a = r.nextInt(pow10(topDigits - 1).coerceAtLeast(11), pow10(topDigits))
        val b = r.nextInt(if (bottomDigits == 1) 2 else 11, pow10(bottomDigits))
        return FlashCard("$a × $b = ?", "Multiply", answer = "${a * b}", steps = multiplySteps(a, b))
    }

    private fun orderOfOps(r: Random): FlashCard {
        val a = r.nextInt(2, 20); val b = r.nextInt(2, 10); val c = r.nextInt(2, 10)
        return if (r.nextBoolean()) FlashCard("$a + $b × $c = ?", "Order of operations", answer = "${a + b * c}",
            steps = listOf("Multiply and divide before you add and subtract.", "First: $b × $c = ${b * c}.", "Then: $a + ${b * c} = ${a + b * c}."))
        else FlashCard("($a + $b) × $c = ?", "Order of operations", answer = "${(a + b) * c}",
            steps = listOf("Brackets come first.", "Inside the brackets: $a + $b = ${a + b}.", "Then: ${a + b} × $c = ${(a + b) * c}."))
    }

    fun pow10(n: Int): Int { var p = 1; repeat(n) { p *= 10 }; return p }
}

// =====================================================================  long division

object DivisionCards {
    fun card(level: Int, r: Random): FlashCard = when (level) {
        0 -> share(r, 10)
        1 -> if (r.nextBoolean()) share(r, 20) else groups(r)
        2 -> long(r, 10, 99, 2, 9, remainder = false)
        3 -> long(r, 100, 999, 2, 9, remainder = false)
        4 -> long(r, 100, 999, 3, 9, remainder = true)
        else -> long(r, 1000, 9999, 11, 25, remainder = true)
    }

    private fun share(r: Random, max: Int): FlashCard {
        val k = r.nextInt(2, 5); val each = r.nextInt(1, max / k + 1); val n = k * each
        val (pic, name) = Learn.COUNTABLE[r.nextInt(Learn.COUNTABLE.size)]
        return FlashCard("$n ÷ $k = ?", "Share $n $name between $k friends. How many each?", pic.repeat(n), Learn.numberChoices(each, 3, r, 2, 1), "$each",
            listOf("Give one to each friend, then another, until none are left.", "Each friend ends up with $each.", "$n shared between $k is $each each: $n ÷ $k = $each."))
    }

    private fun groups(r: Random): FlashCard {
        val g = r.nextInt(2, 6); val q = r.nextInt(2, 6); val n = g * q
        return FlashCard("$n ÷ $g = ?", "How many groups of $g make $n?", choices = Learn.numberChoices(q, 4, r, 2, 1), answer = "$q",
            steps = listOf("Take away groups of $g until nothing is left: " + (n downTo 0 step g).joinToString(" → ") + ".", "That's $q groups.", "$n ÷ $g = $q."))
    }

    /** One step of the written method: bring down, divide, multiply, subtract. */
    class Step(val digitIndex: Int, val current: Int, val q: Int, val product: Int, val rest: Int)

    fun steps(dividend: Int, divisor: Int): List<Step> {
        val digits = dividend.toString()
        val out = ArrayList<Step>()
        var cur = 0
        for ((i, ch) in digits.withIndex()) {
            cur = cur * 10 + (ch - '0')
            val q = cur / divisor
            if (out.isEmpty() && q == 0 && i < digits.length - 1) continue   // keep bringing down until it fits
            out += Step(i, cur, q, q * divisor, cur - q * divisor)
            cur -= q * divisor
        }
        return out
    }

    fun explain(dividend: Int, divisor: Int): List<String> {
        val st = steps(dividend, divisor)
        val digits = dividend.toString()
        val lines = ArrayList<String>()
        lines += "Write $dividend inside the division bracket and $divisor outside."
        for ((k, s) in st.withIndex()) {
            if (k == 0) lines += if (s.digitIndex == 0) "Start with the first digit: ${s.current}." else "$divisor doesn't fit into the first ${if (s.digitIndex == 1) "digit" else "digits"}, so use ${s.current}."
            else lines += "Bring down the next digit, ${digits[s.digitIndex]}: that makes ${s.current}."
            lines += "How many $divisor${"s"} fit in ${s.current}? ${s.q}. Write ${s.q} on top."
            lines += "Multiply: ${s.q} × $divisor = ${s.product}. Subtract: ${s.current} − ${s.product} = ${s.rest}."
        }
        val q = dividend / divisor; val rem = dividend % divisor
        lines += if (rem == 0) "Nothing left over: $dividend ÷ $divisor = $q." else "Nothing left to bring down. $rem is the remainder: $dividend ÷ $divisor = $q R $rem."
        lines += "Check: $q × $divisor" + (if (rem > 0) " + $rem" else "") + " = $dividend."
        return lines
    }

    private fun long(r: Random, lo: Int, hi: Int, dLo: Int, dHi: Int, remainder: Boolean): FlashCard {
        var a: Int; var d: Int
        do { d = r.nextInt(dLo, dHi + 1); a = r.nextInt(lo, hi + 1) } while ((a % d != 0) != remainder || a / d < 2)
        val q = a / d; val rem = a % d
        val ans = if (rem == 0) "$q" else "$q R $rem"
        return FlashCard("$a ÷ $d = ?", if (remainder) "Long division. Type the answer and any remainder (R)." else "Long division", answer = ans,
            steps = explain(a, d), visual = "div:$a:$d")
    }
}

// =====================================================================  missing numbers and algebra

object AlgebraCards {
    fun card(level: Int, r: Random): FlashCard = when (level) {
        0 -> missingPicture(r)
        1 -> missing(r, 10, 4)
        2 -> if (r.nextBoolean()) missing(r, 20, 4) else missingTimes(r, 5)
        3 -> if (r.nextBoolean()) oneStepX(r, add = true) else oneStepX(r, add = false)
        4 -> twoStep(r)
        else -> if (r.nextBoolean()) bothSides(r) else brackets(r)
    }

    private fun missingPicture(r: Random): FlashCard {
        val total = r.nextInt(2, 6); val a = r.nextInt(1, total); val b = total - a
        val (pic, _) = Learn.COUNTABLE[r.nextInt(Learn.COUNTABLE.size)]
        return FlashCard("$a + ? = $total", "How many more make $total?", pic.repeat(a) + "  +  ❓  =  " + pic.repeat(total), Learn.numberChoices(b, 3, r, 2, 0), "$b",
            listOf("Count up from $a to $total: " + (a + 1..total).joinToString(", ") + ".", "That's $b more.", "$a + $b = $total."))
    }

    private fun missing(r: Random, max: Int, n: Int): FlashCard {
        val a = r.nextInt(0, max); val b = r.nextInt(0, max - a + 1); val c = a + b
        return when (r.nextInt(3)) {
            0 -> FlashCard("$a + ? = $c", "Find the missing number", choices = Learn.numberChoices(b, n, r), answer = "$b",
                steps = listOf("What do you add to $a to get $c?", "Take away: $c − $a = $b.", "Check: $a + $b = $c."))
            1 -> FlashCard("? + $b = $c", "Find the missing number", choices = Learn.numberChoices(a, n, r), answer = "$a",
                steps = listOf("What plus $b makes $c?", "Take away: $c − $b = $a.", "Check: $a + $b = $c."))
            else -> FlashCard("$c − ? = $a", "Find the missing number", choices = Learn.numberChoices(b, n, r), answer = "$b",
                steps = listOf("$c take away something leaves $a.", "The difference is $c − $a = $b.", "Check: $c − $b = $a."))
        }
    }

    private fun missingTimes(r: Random, max: Int): FlashCard {
        val a = r.nextInt(2, max + 1); val b = r.nextInt(2, 11)
        return FlashCard("? × $a = ${a * b}", "Find the missing number", choices = Learn.numberChoices(b, 4, r), answer = "$b",
            steps = listOf("What times $a makes ${a * b}?", "Divide: ${a * b} ÷ $a = $b.", "Check: $b × $a = ${a * b}."))
    }

    private fun oneStepX(r: Random, add: Boolean): FlashCard {
        return if (add) {
            val x = r.nextInt(2, 50); val a = r.nextInt(3, 40)
            FlashCard("x + $a = ${x + a}", "Solve for x", answer = "$x",
                steps = listOf("x has $a added to it. Undo that by taking $a away from both sides.", "x = ${x + a} − $a", "x = $x.", "Check: $x + $a = ${x + a}."))
        } else {
            val x = r.nextInt(2, 13); val a = r.nextInt(2, 10)
            FlashCard("${a}x = ${a * x}", "Solve for x", answer = "$x",
                steps = listOf("${a}x means $a times x. Undo it by dividing both sides by $a.", "x = ${a * x} ÷ $a", "x = $x.", "Check: $a × $x = ${a * x}."))
        }
    }

    private fun twoStep(r: Random): FlashCard {
        val x = r.nextInt(1, 13); val a = r.nextInt(2, 9); val b = r.nextInt(1, 20)
        val plus = r.nextBoolean()
        val rhs = if (plus) a * x + b else a * x - b
        val sign = if (plus) "+" else "−"
        return FlashCard("${a}x $sign $b = $rhs", "Solve for x", answer = "$x",
            steps = listOf(
                "Undo the ${if (plus) "adding" else "taking away"} first: ${if (plus) "subtract" else "add"} $b on both sides.",
                "${a}x = $rhs ${if (plus) "−" else "+"} $b = ${a * x}",
                "Now undo the times $a: divide both sides by $a.",
                "x = ${a * x} ÷ $a = $x.",
                "Check: $a × $x $sign $b = $rhs."))
    }

    private fun bothSides(r: Random): FlashCard {
        val x = r.nextInt(1, 11); val a = r.nextInt(3, 8); val c = r.nextInt(1, a); val b = r.nextInt(1, 15)
        val d = a * x - b - c * x
        val dTxt = if (d >= 0) "+ $d" else "− ${abs(d)}"
        return FlashCard("${a}x − $b = ${c}x $dTxt", "Solve for x", answer = "$x",
            steps = listOf(
                "Get the x terms on one side: subtract ${c}x from both sides.",
                "${a - c}x − $b = $d",
                "Add $b to both sides: ${a - c}x = ${d + b}",
                "Divide by ${a - c}: x = ${(d + b)} ÷ ${a - c} = $x.",
                "Check: $a × $x − $b = ${a * x - b}, and $c × $x $dTxt = ${c * x + d}."))
    }

    private fun brackets(r: Random): FlashCard {
        val x = r.nextInt(1, 12); val a = r.nextInt(2, 6); val b = r.nextInt(1, 9)
        val rhs = a * (x + b)
        return FlashCard("$a(x + $b) = $rhs", "Solve for x", answer = "$x",
            steps = listOf("$a(x + $b) means $a times (x + $b).", "Divide both sides by $a: x + $b = ${rhs / a}.", "Subtract $b: x = ${rhs / a} − $b = $x.", "Check: $a × ($x + $b) = $a × ${x + b} = $rhs."))
    }
}

// =====================================================================  letters and words

object WordCards {
    /** Picture words for early readers: word to emoji. */
    val PICTURES = listOf(
        "ant" to "🐜", "apple" to "🍎", "ball" to "⚽", "bee" to "🐝", "bird" to "🐦", "boat" to "⛵", "book" to "📖", "bus" to "🚌",
        "cake" to "🎂", "car" to "🚗", "cat" to "🐱", "cow" to "🐮", "dog" to "🐶", "duck" to "🦆", "egg" to "🥚", "fish" to "🐟",
        "fox" to "🦊", "frog" to "🐸", "hat" to "🎩", "hen" to "🐔", "house" to "🏠", "key" to "🔑", "kite" to "🪁", "lion" to "🦁",
        "moon" to "🌙", "mouse" to "🐭", "owl" to "🦉", "pig" to "🐷", "ring" to "💍", "sun" to "🌞", "tree" to "🌳", "van" to "🚐",
        "web" to "🕸", "zebra" to "🦓", "bat" to "🦇", "box" to "📦", "bug" to "🐛", "map" to "🗺", "sock" to "🧦", "star" to "⭐",
        "ship" to "🚢", "shoe" to "👟", "cheese" to "🧀", "snake" to "🐍", "snail" to "🐌", "train" to "🚆", "crab" to "🦀", "drum" to "🥁",
        "grapes" to "🍇", "horse" to "🐴", "milk" to "🥛", "pear" to "🍐", "crown" to "👑", "robot" to "🤖", "spoon" to "🥄", "tiger" to "🐯",
        "whale" to "🐳", "nest" to "🪺", "lemon" to "🍋", "carrot" to "🥕", "rocket" to "🚀", "turtle" to "🐢", "penguin" to "🐧", "umbrella" to "☂",
        "violin" to "🎻", "yo-yo" to "🪀", "jam" to "🍯", "goat" to "🐐", "queen" to "👸", "octopus" to "🐙", "dinosaur" to "🦕",
    )
    private val CVC = PICTURES.filter { it.first.length == 3 && it.first.all { c -> c in 'a'..'z' } && it.first[1] in "aeiou" && it.first[0] !in "aeiou" && it.first[2] !in "aeiouy" }

    fun letters(level: Int, r: Random): FlashCard = when (level) {
        0 -> firstLetter(r, 3)
        1 -> if (r.nextBoolean()) firstLetter(r, 4) else pictureForLetter(r)
        2 -> lastLetter(r)
        3 -> missingVowel(r)
        4 -> spelling(r, SPELL_MID)
        else -> spelling(r, SPELL_HARD)
    }

    private fun firstLetter(r: Random, n: Int): FlashCard {
        val (w, pic) = PICTURES[r.nextInt(PICTURES.size)]
        val l = w[0].uppercaseChar().toString()
        val wrong = ('A'..'Z').map { it.toString() }.filter { it != l }
        return FlashCard(pic, "What letter does $w start with?", choices = Learn.choices(l, wrong, n, r), answer = l,
            steps = listOf("Say the word slowly: $w.", "Listen to the first sound.", "$w starts with ${l}: $l is for $w."))
    }

    private fun pictureForLetter(r: Random): FlashCard {
        val (w, pic) = PICTURES[r.nextInt(PICTURES.size)]
        val l = w[0].uppercaseChar()
        val others = PICTURES.filter { it.first[0].uppercaseChar() != l }.shuffled(r).take(3).map { it.second }
        return FlashCard("$l  ${l.lowercaseChar()}", "Which picture starts with $l?", choices = (others + pic).shuffled(r), answer = pic,
            steps = listOf("$l makes the sound you hear at the start of $w.", "$pic is $w: $l is for $w."))
    }

    private fun lastLetter(r: Random): FlashCard {
        val (w, pic) = PICTURES.filter { it.first.last() in 'a'..'z' }.let { it[r.nextInt(it.size)] }
        val l = w.last().uppercaseChar().toString()
        return FlashCard(pic, "What letter does $w end with?", choices = Learn.choices(l, ('A'..'Z').map { it.toString() }, 4, r), answer = l,
            steps = listOf("Say the word slowly: $w.", "Listen to the very last sound.", "$w ends with $l."))
    }

    private fun missingVowel(r: Random): FlashCard {
        val (w, pic) = CVC[r.nextInt(CVC.size)]
        val shown = "${w[0]} _ ${w[2]}"
        val v = w[1].toString()
        return FlashCard(pic, "Fill in the missing letter: $shown", choices = Learn.choices(v, listOf("a", "e", "i", "o", "u"), 4, r), answer = v,
            steps = listOf("The word is $w.", "Sound it out: ${w[0]} - ${w[1]} - ${w[2]}.", "The middle sound is ${w[1]}."))
    }

    private val SPELL_MID = listOf("friend" to listOf("frend", "freind", "frieand"), "school" to listOf("skool", "shcool", "scool"), "because" to listOf("becuase", "becaus", "beacause"),
        "people" to listOf("peeple", "poeple", "peopel"), "said" to listOf("sed", "siad", "saed"), "again" to listOf("agen", "agian", "agin"),
        "there" to listOf("thair", "ther", "thear"), "night" to listOf("nite", "nigth", "niht"), "laugh" to listOf("laff", "lauf", "lagh"),
        "would" to listOf("wood", "woud", "wuld"), "water" to listOf("warter", "wotter", "watter"), "light" to listOf("lite", "ligth", "lihgt"))
    private val SPELL_HARD = listOf("necessary" to listOf("neccessary", "necesary", "nessesary"), "separate" to listOf("seperate", "separete", "sepparate"),
        "definitely" to listOf("definately", "definitly", "defanitely"), "beautiful" to listOf("beutiful", "beatiful", "beautifull"),
        "different" to listOf("diffrent", "differant", "diferent"), "February" to listOf("Febuary", "Februery", "Feburary"),
        "library" to listOf("libary", "liberry", "librery"), "probably" to listOf("probly", "probabley", "propably"),
        "receive" to listOf("recieve", "receeve", "resieve"), "favourite" to listOf("favrite", "faverite", "favorit"),
        "surprise" to listOf("suprise", "surprize", "serprise"), "temperature" to listOf("temprature", "temperture", "tempreture"))

    private fun spelling(r: Random, bank: List<Pair<String, List<String>>>): FlashCard {
        val (w, bad) = bank[r.nextInt(bank.size)]
        return FlashCard("Which is spelled right?", "Pick the correct spelling", choices = (bad + w).shuffled(r), answer = w,
            steps = listOf("Look closely at each letter.", "The right spelling is: $w.", tipFor(w)))
    }

    private fun tipFor(w: String): String = when (w) {
        "friend" -> "Tip: a friend is there to the END: fri-END."
        "because" -> "Tip: Big Elephants Can Always Understand Small Elephants."
        "said" -> "Tip: said has an 'ai' in the middle."
        "separate" -> "Tip: there's 'a rat' in sep-A-RAT-e."
        "necessary" -> "Tip: one Collar, two Sleeves: one c, two s's."
        "receive" -> "Tip: i before e, except after c: re-c-EI-ve."
        "definitely" -> "Tip: it has 'finite' in the middle: de-FINITE-ly."
        "library" -> "Tip: say both r's: li-BRA-ry."
        else -> "Tip: say it slowly in parts, and picture the word."
    }

    fun words(level: Int, r: Random): FlashCard = when (level) {
        0 -> pictureForWord(r, CVC, 3)
        1 -> pictureForWord(r, PICTURES.filter { it.first.length <= 5 }, 4)
        2 -> sightWord(r)
        3 -> opposite(r)
        4 -> tense(r)
        else -> meaning(r)
    }

    private fun pictureForWord(r: Random, bank: List<Pair<String, String>>, n: Int): FlashCard {
        val (w, pic) = bank[r.nextInt(bank.size)]
        val others = PICTURES.filter { it.first != w }.shuffled(r).take(n - 1).map { it.second }
        return FlashCard(w, "Read the word. Which picture is it?", choices = (others + pic).shuffled(r), answer = pic,
            steps = listOf("Sound out each letter: " + w.toList().joinToString(" - "), "Blend the sounds together: $w.", "$pic is a $w."))
    }

    private val SIGHT = listOf("I ___ a cat." to ("have" to listOf("has", "is", "go")), "We ___ to the park." to ("went" to listOf("was", "want", "when")),
        "She ___ my friend." to ("is" to listOf("are", "am", "be")), "They ___ playing." to ("are" to listOf("is", "am", "was")),
        "Can you ___ me?" to ("help" to listOf("helps", "here", "hold")), "The dog ___ fast." to ("runs" to listOf("run", "running", "ran's")),
        "I can ___ a bird." to ("see" to listOf("sea", "saw", "seed")), "Put it ___ the box." to ("in" to listOf("on", "at", "is")),
        "Look ___ the sky!" to ("at" to listOf("on", "of", "it")), "My hat ___ red." to ("is" to listOf("are", "am", "be")),
        "I ___ six years old." to ("am" to listOf("is", "are", "an")), "Where ___ you going?" to ("are" to listOf("is", "am", "was")))

    private fun sightWord(r: Random): FlashCard {
        val (s, a) = SIGHT[r.nextInt(SIGHT.size)]
        val (right, wrong) = a
        return FlashCard(s, "Which word fits?", choices = (wrong + right).shuffled(r), answer = right,
            steps = listOf("Read the sentence with each word in the gap.", "Only one sounds right: \"${s.replace("___", right)}\""))
    }

    private val OPPOSITES = listOf("big" to "small", "hot" to "cold", "up" to "down", "happy" to "sad", "fast" to "slow", "open" to "closed", "day" to "night",
        "full" to "empty", "light" to "dark", "old" to "new", "wet" to "dry", "loud" to "quiet", "high" to "low", "soft" to "hard", "push" to "pull", "first" to "last")

    private fun opposite(r: Random): FlashCard {
        val (a, b) = OPPOSITES[r.nextInt(OPPOSITES.size)].let { if (r.nextBoolean()) it else it.second to it.first }
        val wrong = OPPOSITES.flatMap { listOf(it.first, it.second) }.filter { it != a && it != b }
        return FlashCard(a, "What is the opposite of $a?", choices = Learn.choices(b, wrong, 4, r), answer = b,
            steps = listOf("An opposite means the other end: as different as it can be.", "The opposite of $a is $b."))
    }

    private val TENSES = listOf("run" to "ran", "eat" to "ate", "go" to "went", "see" to "saw", "swim" to "swam", "make" to "made", "take" to "took",
        "write" to "wrote", "sing" to "sang", "fly" to "flew", "think" to "thought", "buy" to "bought", "jump" to "jumped", "play" to "played", "catch" to "caught", "sleep" to "slept")

    private fun tense(r: Random): FlashCard {
        val (now, past) = TENSES[r.nextInt(TENSES.size)]
        val wrong = listOf("${now}ed", "${now}s", "${now}ing", TENSES[r.nextInt(TENSES.size)].second)
        return FlashCard("Today I $now. Yesterday I ___.", "Which word fits?", choices = Learn.choices(past, wrong, 4, r), answer = past,
            steps = listOf("Yesterday means it already happened: the past tense.", if (past == now + "ed" || past == now + "d") "Most words just add -ed: $now → $past." else "$now is a special word: its past tense is $past, not ${now}ed.",
                "Yesterday I $past."))
    }

    private val MEANINGS = listOf("enormous" to "very big", "tiny" to "very small", "furious" to "very angry", "delighted" to "very happy", "exhausted" to "very tired",
        "ancient" to "very old", "rapid" to "very fast", "silent" to "making no sound", "brave" to "not afraid", "fragile" to "easy to break",
        "curious" to "wanting to know", "generous" to "happy to share", "gloomy" to "dark and sad", "soaked" to "very wet", "timid" to "shy", "vast" to "very wide")

    private fun meaning(r: Random): FlashCard {
        val (w, m) = MEANINGS[r.nextInt(MEANINGS.size)]
        return FlashCard(w, "What does $w mean?", choices = Learn.choices(m, MEANINGS.map { it.second }, 4, r), answer = m,
            steps = listOf("Try each meaning in a sentence: \"The ___ dragon...\"", "$w means $m."))
    }
}

// =====================================================================  reading practice

object ReadingCards {
    private val NAMES = listOf("Sam", "Mia", "Leo", "Ava", "Max", "Zoe", "Ben", "Lily", "Omar", "Ruby", "Jack", "Ella")
    private val COLORS = listOf("red", "blue", "green", "yellow", "purple", "orange", "pink", "brown")
    private val THINGS = listOf("ball" to "⚽", "hat" to "🎩", "kite" to "🪁", "car" to "🚗", "book" to "📖", "train" to "🚆", "boat" to "⛵", "drum" to "🥁")
    private val ANIMALS = listOf("cat" to "🐱", "dog" to "🐶", "pig" to "🐷", "duck" to "🦆", "frog" to "🐸", "cow" to "🐮", "hen" to "🐔", "fox" to "🦊")
    private val PLACES = listOf("the park", "the beach", "school", "the farm", "the zoo", "the shop", "the library", "the lake")
    private val ACTIONS = listOf("sat" to "sitting", "ran" to "running", "slept" to "sleeping", "jumped" to "jumping", "swam" to "swimming", "hid" to "hiding")

    fun card(level: Int, r: Random): FlashCard {
        val name = NAMES[r.nextInt(NAMES.size)]
        val name2 = NAMES.filter { it != name }[r.nextInt(NAMES.size - 1)]
        val color = COLORS[r.nextInt(COLORS.size)]
        val (thing, thingPic) = THINGS[r.nextInt(THINGS.size)]
        val (animal, animalPic) = ANIMALS[r.nextInt(ANIMALS.size)]
        val place = PLACES[r.nextInt(PLACES.size)]
        val (did, _) = ACTIONS[r.nextInt(ACTIONS.size)]
        fun find(word: String, text: String) = listOf("Find the word \"$word\" in the story.", "Read the sentence it is in: \"${sentenceWith(text, word)}\"")
        return when (level) {
            0 -> {
                val text = "The $animal $did."
                FlashCard(text, "Which picture shows the $animal?", choices = Learn.choices(animalPic, ANIMALS.map { it.second }, 3, r), answer = animalPic,
                    steps = listOf("Read each word: " + text.removeSuffix(".").split(" ").joinToString(" · "), "The story is about a $animal.", "$animalPic is a $animal."))
            }
            1 -> {
                val text = "$name has a $color $thing."
                FlashCard(text, "What color is the $thing?", choices = Learn.choices(color, COLORS, 4, r), answer = color,
                    steps = find(thing, text) + "The word before \"$thing\" tells its color: $color.")
            }
            2 -> {
                val text = "$name went to $place. $name saw a $animal there."
                if (r.nextBoolean()) FlashCard(text, "Where did $name go?", choices = Learn.choices(place, PLACES, 4, r), answer = place,
                    steps = find("went", text) + "$name went to $place.")
                else FlashCard(text, "What did $name see?", choices = Learn.choices("a $animal", ANIMALS.map { "a ${it.first}" }, 4, r), answer = "a $animal",
                    steps = find("saw", text) + "$name saw a $animal.")
            }
            3 -> {
                val day = listOf("Monday", "Tuesday", "Saturday", "Sunday")[r.nextInt(4)]
                val text = "On $day, $name and $name2 went to $place. They took a $color $thing. $name2 played with the $thing all day."
                when (r.nextInt(3)) {
                    0 -> FlashCard(text, "When did they go?", choices = Learn.choices(day, listOf("Monday", "Tuesday", "Saturday", "Sunday", "Friday"), 4, r), answer = day,
                        steps = find(day, text) + "Words like \"on $day\" tell you when.")
                    1 -> FlashCard(text, "Who played with the $thing?", choices = Learn.choices(name2, NAMES, 4, r), answer = name2,
                        steps = find("played", text) + "$name2 played with the $thing.")
                    else -> FlashCard(text, "What did they take?", choices = Learn.choices("a $color $thing", THINGS.map { "a $color ${it.first}" }, 4, r), answer = "a $color $thing",
                        steps = find("took", text) + "They took a $color $thing.")
                }
            }
            4 -> {
                val stories = listOf(
                    Triple("Dark clouds filled the sky. $name grabbed an umbrella before going to $place.", "Why did $name take an umbrella?", "It looked like rain" to listOf("It was sunny", "To play a game", "It was a gift")),
                    Triple("$name's tummy rumbled. It was lunchtime, and $name hadn't eaten since breakfast.", "How does $name feel?", "Hungry" to listOf("Sleepy", "Cold", "Scared")),
                    Triple("$name yawned and rubbed both eyes. The clock said nine o'clock at night.", "What will $name do soon?", "Go to sleep" to listOf("Eat breakfast", "Go to school", "Play outside")),
                    Triple("$name dropped the $thing and it broke. Tears rolled down $name's cheeks.", "How does $name feel?", "Sad" to listOf("Happy", "Excited", "Hungry")),
                    Triple("$name put on a coat, a hat and gloves. Snow was falling outside.", "What is the weather like?", "Cold and snowy" to listOf("Hot and sunny", "Warm and windy", "Rainy and warm")),
                )
                val (text, q, a) = stories[r.nextInt(stories.size)]
                FlashCard(text, q, choices = (a.second + a.first).shuffled(r), answer = a.first,
                    steps = listOf("The story doesn't say it straight out: look for clues.", "Clues: \"${text.split(". ").first().trimEnd('.')}\".", "So the best answer is: ${a.first}."))
            }
            else -> {
                val text = "First, $name mixed flour, eggs and milk in a bowl. Next, $name poured the mix into a hot pan. After a few minutes, $name flipped it over. " +
                    "Finally, $name shared the warm pancakes with $name2."
                when (r.nextInt(3)) {
                    0 -> FlashCard(text, "What did $name do right after pouring the mix?", choices = listOf("Flipped it over", "Mixed the flour", "Shared the pancakes", "Washed the bowl").shuffled(r), answer = "Flipped it over",
                        steps = listOf("Order words help: First, Next, After, Finally.", "\"Next\" was pouring; \"After a few minutes\" comes right after: $name flipped it over."))
                    1 -> FlashCard(text, "What is the story mostly about?", choices = listOf("Making pancakes", "Going to $place", "A broken $thing", "A lost $animal").shuffled(r), answer = "Making pancakes",
                        steps = listOf("The main idea is what most of the sentences are about.", "Every step is about cooking pancakes: making pancakes."))
                    else -> FlashCard(text, "Who got to eat pancakes with $name?", choices = Learn.choices(name2, NAMES.filter { it != name }, 4, r), answer = name2,
                        steps = find("shared", text) + "$name shared the pancakes with $name2.")
                }
            }
        }
    }

    private fun sentenceWith(text: String, word: String): String =
        text.split(Regex("(?<=[.!?])\\s+")).firstOrNull { it.contains(word) } ?: text
}

// =====================================================================  telling time

object ClockCards {
    fun timeText(h: Int, m: Int) = "$h:${m.toString().padStart(2, '0')}"

    fun card(level: Int, r: Random): FlashCard {
        val h = r.nextInt(1, 13)
        val m = when (level) {
            0 -> 0
            1 -> listOf(0, 30)[r.nextInt(2)]
            2 -> listOf(0, 15, 30, 45)[r.nextInt(4)]
            3 -> r.nextInt(12) * 5
            else -> r.nextInt(60)
        }
        if (level == 5) {
            val add = listOf(15, 20, 25, 30, 35, 40, 45, 50, 75, 90)[r.nextInt(10)]
            val total = h * 60 + m + add
            val h2 = ((total / 60 - 1) % 12) + 1; val m2 = total % 60
            val ans = timeText(h2, m2)
            val wrong = listOf(timeText(h2, (m2 + 10) % 60), timeText((h2 % 12) + 1, m2), timeText(h, (m + add) % 60), timeText(h2, (m2 + 50) % 60))
            return FlashCard("What time will it be in $add minutes?", "The clock shows ${timeText(h, m)}", choices = Learn.choices(ans, wrong, 4, r), answer = ans,
                steps = listOf("Start at ${timeText(h, m)}.", if (m + add >= 60) "Count on to the next hour: ${60 - m} minutes gets you to ${timeText((h % 12) + 1, 0)}." else "Add the minutes: $m + $add = ${m + add}.",
                    if (m + add >= 60) "That leaves ${add - (60 - m)} more minutes: ${ans}." else "It will be $ans.") , visual = "clock:$h:$m")
        }
        val ans = timeText(h, m)
        val wrong = listOf(timeText((h % 12) + 1, m), timeText(h, (m + 30) % 60), timeText(if (h == 1) 12 else h - 1, m), timeText(m / 5 % 12 + 1, (h * 5) % 60))
        val minuteHand = if (m == 0) "The long minute hand points straight up at 12: that means o'clock." else "The long minute hand points at ${if (m % 5 == 0) "the ${if (m == 0) 12 else m / 5}" else "just past the ${m / 5}"}. Count by fives: ${m} minutes."
        return FlashCard("What time is it?", "Read the clock", choices = Learn.choices(ans, wrong, if (level == 0) 3 else 4, r), answer = ans,
            steps = listOf("The short hand shows the hour. It points ${if (m >= 30) "past the $h" else "at the $h"}: $h o'clock.", minuteHand,
                "So the time is $ans" + when (m) { 0 -> " ($h o'clock)."; 15 -> " (quarter past $h)."; 30 -> " (half past $h)."; 45 -> " (quarter to ${(h % 12) + 1})."; else -> "." }),
            visual = "clock:$h:$m")
    }
}
