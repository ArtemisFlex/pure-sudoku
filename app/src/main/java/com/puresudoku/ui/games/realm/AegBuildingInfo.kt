package com.puresudoku.ui.games.realm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puresudoku.core.board.AegStewardship
import com.puresudoku.core.board.Aegwaspell as A

/** Building effects are shared by the two games except where explicitly noted. */
internal fun aegBuildingPurpose(b: Int, conquest: Boolean): String = when (b) {
    A.PORT -> if (conquest) "Your port houses 8 and produces 3 food, 1 timber and 1 gold per season. Trade goods here." else "Your port houses 8 and produces 3 food and 1 gold per season."
    A.VILLAGE -> "Houses 6 people. Upgrade it to a town using the Town action."
    A.TOWN -> "Houses up to 15 people and adds 10 food storage. A nearby market, temple or active Sky Temple is needed for all 15 homes."
    A.FARM -> "Produces 7 food on fields or 5 on plains before harvest and bonuses; produces no food in winter. Rest it to recover tired soil."
    A.RANCH -> "Produces 4 food per season, or 3 in winter, before bonuses."
    A.FISHERY -> "Produces 4 food per season, or 3 in winter, before bonuses. Beside a port: +1 food."
    A.MILL -> "Produces 3 timber in forest or 2 in swamp, before bonuses. An adjacent empty forest adds 1 timber."
    A.QUARRY -> "Produces 3 stone per season before bonuses. An adjacent mine adds 1 stone."
    A.MINE -> "Produces 2 ore and 1 gold per season before bonuses. An adjacent quarry adds 1 ore."
    A.GRANARY -> "Adds 40 food storage. Each adjacent farm gains 1 food outside winter."
    A.MARKET -> "Produces 3 gold per season before bonuses; an adjacent town adds 2 gold. It also helps towns within two tiles reach full housing."
    A.BARRACKS -> "Adds room for 8 soldiers and lets you train them. Training costs food, ore and gold."
    A.TOWER -> if (conquest) "Adds 3 defence to this tile against attacks." else "Adds 2 defence against raids."
    A.TEMPLE -> "Adds 4 mood per season, up to three temples; eases plague and unrest. Helps nearby towns and homes."
    A.COLOSSEUM -> "When staffed, has a 50% chance each season to add 12 mood. Also eases unrest."
    AegStewardship.LIGHTHOUSE -> if (conquest) "+2 food at every fishery and the port; improves port trades to 3:1 when complete and staffed." else "+2 food at every fishery and the port when complete and staffed."
    in AegStewardship.WONDERS -> AegStewardship.WONDER_EFFECT[b - AegStewardship.LIGHTHOUSE] + ". Requires completion and staffing."
    else -> ""
}

@Composable
internal fun AegBuildingInfo(
    tile: Int, building: Int, conquest: Boolean, staffed: Boolean, workers: Int?,
    connected: Boolean, housing: Int, construction: Int, resting: Boolean,
    defence: Int?, tradeRate: Int?, notes: List<String>,
) {
    val need = A.WORKERS[building]
    val status = when {
        construction > 0 -> "Under construction: $construction staffed season${if (construction == 1) "" else "s"} remaining."
        building in AegStewardship.WONDERS -> if (staffed) "Wonder active." else if (conquest && !connected) "Wonder inactive until the road connection and staffing are restored." else "Wonder inactive until staffed."
        else -> null
    }
    key(tile) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).parchment(PANEL).padding(horizontal = 10.dp, vertical = 5.dp)) {
            InkText("${A.NAME[building]} · Building details", 13, bold = true)
            Column(Modifier.heightIn(max = 102.dp).verticalScroll(rememberScrollState())) {
                InkText(aegBuildingPurpose(building, conquest), 12)
                val stats = mutableListOf<String>()
                if (building == A.PORT || building == A.VILLAGE || building == A.TOWN || building == AegStewardship.PALACE) stats += "Housing: $housing"
                if (need > 0) stats += if (workers != null) "Workers: $workers/$need" else "Workers needed: $need"
                if (need > 0) stats += if (resting) "Resting" else if (staffed) "Staffed" else "Not staffed"
                if (conquest && !connected) stats += "Road connection lost"
                if (defence != null) stats += "Tile defence: $defence"
                if (tradeRate != null) stats += "Trade rate: $tradeRate:1"
                val upkeep = AegStewardship.upkeep(building)
                if (upkeep > 0) stats += "Upkeep: $upkeep gold/season"
                if (stats.isNotEmpty()) InkText(stats.joinToString(" · "), 12, bold = true)
                if (status != null) InkText(status, 12)
                notes.filterNot { it.startsWith("building:") }.forEach { InkText("Tip: $it", 12) }
                if (building == A.FARM) InkText("Use Rest farm or Work farm below to manage the soil.", 12)
                if (conquest && need > 0 && connected) InkText("Use Add worker or Remove worker below to manage the crew.", 12)
            }
            InkText("Swipe details · Actions below", 10, color = INK_SOFT)
        }
    }
}
