package com.puresudoku.core.board

import com.puresudoku.core.Difficulty
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class AegPlaythroughTest {
    @Test fun coachesProduceLegalMovesAcrossDifficultiesAndSaveReloads() {
        for (difficulty in Difficulty.entries) for (seed in 1..3) {
            val rng = Random(seed)
            var a = Aegwaspell.newGame(difficulty, rng)
            var c = AegConquest.newGame(difficulty, rng)
            repeat(180) { turn ->
                if (!a.over) {
                    val action = AegwaspellAi.next(a, rng)
                    assertTrue("Aegwaspell $difficulty seed $seed step $turn: $action", a.legal(action))
                    a.apply(action, rng)
                    if (turn % 30 == 0) a = Aegwaspell.fromState(a.state())
                }
                if (!c.over) {
                    val action = AegConquestAi.next(c, rng)
                    assertTrue("Conquest $difficulty seed $seed step $turn: $action", c.legal(action))
                    c.apply(action, rng)
                    if (turn % 30 == 0) c = AegConquest.fromState(c.state())
                }
            }
        }
    }
}
