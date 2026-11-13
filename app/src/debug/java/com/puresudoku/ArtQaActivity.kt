package com.puresudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.puresudoku.ui.theme.PureSudokuTheme
import com.puresudoku.core.Difficulty
import com.puresudoku.core.board.AegConquest
import com.puresudoku.core.board.AegStewardship
import com.puresudoku.core.board.Aegwaspell
import com.puresudoku.core.board.Wisteria
import com.puresudoku.core.board.Kreashun
import com.puresudoku.core.board.Veilguard
import com.puresudoku.ui.games.realm.WisteriaSession
import com.puresudoku.ui.games.realm.WisteriaUi
import com.puresudoku.ui.games.realm.KreashunSession
import com.puresudoku.ui.games.realm.KreashunUi
import com.puresudoku.ui.games.realm.VeilguardSession
import com.puresudoku.ui.games.realm.VeilguardUi
import com.puresudoku.ui.games.realm.AegConquestSession
import com.puresudoku.ui.games.realm.AegConquestUi
import com.puresudoku.ui.games.realm.AegwaspellSession
import com.puresudoku.ui.games.realm.AegwaspellUi
import kotlin.random.Random

/** Debug-only deterministic visual fixture. It never reads or writes player saves. */
class ArtQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.getStringExtra("mode")) {
            "wisteria" -> {
                val g = Wisteria.newGame(Difficulty.EASY, Random(44))
                val homes = (0 until g.H).filter { g.passable(it) && it != g.lair }
                for (p in 0..1) { val h = homes[if (p == 0) 2 else homes.size - 3]; g.owner[h] = p; g.level[h] = 1 }
                for (p in 0..1) g.wallSpots(p).take(3).forEach { (a, b) -> g.wall[g.side(a, b)] = p }
                setContent { PureSudokuTheme { WisteriaUi(WisteriaSession(Difficulty.EASY, g), null) } }; return
            }
            "kreashun" -> {
                val g = Kreashun.newGame(Difficulty.EASY, Random(44))
                for (p in 0..1) { val h = if (p == 0) 2 else g.H - 3; g.owner[h] = p
                    g.map.hexNbrs[h].take(3).forEach { n -> val edge = g.side(h, n); if (edge >= 0) g.wall[edge] = p }
                }
                setContent { PureSudokuTheme { KreashunUi(KreashunSession(Difficulty.EASY, g), null) } }; return
            }
            "veilguard" -> {
                val g = Veilguard.newGame(Difficulty.EASY, Random(44))
                for (p in 0..1) { val h = if (p == 0) 2 else g.H - 3; g.owner[h] = p; g.level[h] = 1 }
                for (p in 0..1) for (h in (0 until g.H).filter { g.owner[it] == p }.take(1))
                    g.map.hexNbrs[h].take(3).forEach { n -> val edge = g.side(h, n); if (edge >= 0) g.wall[edge] = p }
                setContent { PureSudokuTheme { VeilguardUi(VeilguardSession(Difficulty.EASY, g), null) } }; return
            }
        }
        val conquest = intent.getBooleanExtra("conquest", false)
        val catalog = intent.getBooleanExtra("catalog", false)
        if (conquest) {
            val g = AegConquest.newGame(Difficulty.EASY, Random(44))
            g.rivalLand.fill(-1); g.road.fill(false); g.revealed.fill(true); g.terrain.fill(AegConquest.PLAINS)
            g.building.fill(AegConquest.NONE); g.building[g.capital] = AegConquest.PORT
            val tiles = (0 until g.H).filter { it != g.capital }.sortedBy { g.hexDist(it, g.capital) }
            AegStewardship.WONDERS.forEachIndexed { n, w -> if (!catalog) { g.building[tiles[n]] = w; g.workers[tiles[n]] = AegConquest.WORKERS[w] } }
            for (target in tiles.take(8)) {
                var h = target
                while (h != g.capital) { val next = g.map.hexNbrs[h].minBy { g.hexDist(it, g.capital) }; g.road[g.side(h, next)] = true; h = next }
            }
            g.building[tiles[6]] = AegConquest.TOWN; g.pop = 80; g.res.fill(100)
            val session = AegConquestSession(Difficulty.EASY, g); session.selected = tiles[7]
            setContent { PureSudokuTheme { AegConquestUi(session, null) } }
        } else {
            val g = Aegwaspell.newGame(Difficulty.EASY, Random(44))
            g.rivalLand.fill(-1); g.road.fill(false); g.revealed.fill(true); g.terrain.fill(Aegwaspell.PLAINS)
            g.building.fill(Aegwaspell.NONE); g.building[g.capital] = Aegwaspell.PORT
            val tiles = (0 until g.H).filter { it != g.capital }.sortedBy { g.hexDist(it, g.capital) }
            AegStewardship.WONDERS.forEachIndexed { n, w -> if (!catalog) g.building[tiles[n]] = w }
            for (target in tiles.take(8)) {
                var h = target
                while (h != g.capital) { val next = g.map.hexNbrs[h].minBy { g.hexDist(it, g.capital) }; g.road[g.side(h, next)] = true; h = next }
            }
            g.building[tiles[6]] = Aegwaspell.TOWN; g.pop = 80; g.res.fill(100)
            val session = AegwaspellSession(Difficulty.EASY, g); session.selected = tiles[7]
            setContent { PureSudokuTheme { AegwaspellUi(session, null) } }
        }
    }
}
