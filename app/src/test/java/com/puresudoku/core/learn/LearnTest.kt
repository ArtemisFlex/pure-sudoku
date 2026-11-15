package com.puresudoku.core.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LearnTest {
    @Test fun everyDeckIsWellFormed() {
        for (game in LearnGame.entries) for (level in 0..5) for (seed in 1..30) {
            val deck = Learn.deck(game, level, Random(seed))
            assertEquals("$game L$level deck size", 10, deck.size)
            for (c in deck) {
                val where = "$game L$level: ${c.prompt} / ${c.ask}"
                assertTrue("steps: $where", c.steps.isNotEmpty())
                if (c.choices.isNotEmpty()) {
                    assertTrue("answer among choices: $where -> ${c.answer} in ${c.choices}", c.answer in c.choices)
                    assertEquals("distinct choices: $where ${c.choices}", c.choices.size, c.choices.toSet().size)
                    assertTrue("at least 3 choices: $where", c.choices.size >= 3)
                }
            }
        }
    }

    @Test fun longDivisionAnswersAndStepsAgree() {
        val r = Random(5)
        repeat(300) {
            val c = Learn.card(LearnGame.DIVISION, 2 + r.nextInt(4), r)
            val (a, d) = c.visual.removePrefix("div:").split(':').map { it.toInt() }
            val expect = if (a % d == 0) "${a / d}" else "${a / d} R ${a % d}"
            assertEquals(c.prompt, expect, c.answer)
            // the quotient digits from the written method spell the answer
            val q = DivisionCards.steps(a, d).joinToString("") { "${it.q}" }.trimStart('0').ifEmpty { "0" }
            assertEquals(c.prompt, "${a / d}", q)
            assertEquals(c.prompt, a % d, DivisionCards.steps(a, d).last().rest)
        }
    }

    @Test fun algebraAnswersSolveTheEquation() {
        val r = Random(9)
        repeat(300) {
            val level = 3 + r.nextInt(3)
            val c = Learn.card(LearnGame.ALGEBRA, level, r)
            val x = c.answer.toInt()
            // plug x back into the equation, both sides
            val (lhs, rhs) = c.prompt.split(" = ")
            assertEquals(c.prompt, eval(lhs, x), eval(rhs, x))
        }
    }

    @Test fun clockAnswersMatchTheFace() {
        val r = Random(3)
        repeat(200) {
            val level = r.nextInt(5)
            val c = Learn.card(LearnGame.CLOCK, level, r)
            val (h, m) = c.visual.removePrefix("clock:").split(':').map { it.toInt() }
            assertEquals(ClockCards.timeText(h, m), c.answer)
        }
    }

    /** A tiny evaluator for the equation shapes the cards use: 3x + 5, 2(x + 4), 5x − 7. */
    private fun eval(e: String, x: Int): Int {
        val s = e.replace("−", "-").replace(" ", "")
        Regex("^(\\d+)\\(x([+-]\\d+)\\)$").matchEntire(s)?.let { return it.groupValues[1].toInt() * (x + it.groupValues[2].toInt()) }
        var total = 0
        for (term in Regex("[+-]?[^+-]+").findAll(s).map { it.value }) {
            total += when {
                term.endsWith("x") -> (term.dropLast(1).let { if (it == "" || it == "+") 1 else if (it == "-") -1 else it.toInt() }) * x
                else -> term.toInt()
            }
        }
        return total
    }
}
