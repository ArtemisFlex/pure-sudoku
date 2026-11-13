package com.puresudoku.ui.games.realm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.puresudoku.core.board.AegStewardship

/** Player-facing controls for the rules shared by both Aegwaspell modes. */
@Composable
internal fun StewardshipDialog(sw: AegStewardship, cards: List<String>, onClose: () -> Unit, choose: (Int) -> Unit, decree: (Int, Int) -> Unit) {
    val choice = AegStewardship.CHOICES[sw.choice]
    AlertDialog(
        onDismissRequest = { if (choice == null) onClose() },
        title = { Text(choice?.get(0) ?: "Stewardship") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (choice != null) {
                    Text(choice[1])
                    for (option in 0..1) {
                        TextButton(onClick = { choose(option) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${choice[option + 2]} — ${sw.hint(option)}")
                        }
                    }
                } else {
                    Text("Next harvest: ${sw.forecastText()}")
                    Text("People: ${sw.people()} (${sw.children()} children)")
                    Text("Chronicle")
                    val history = sw.history(cards)
                    if (history.isEmpty()) Text("Your first year is still being written.") else history.forEach { Text(it) }
                    for (kind in 0..2) {
                        Text("${AegStewardship.DECREE[kind]}: ${AegStewardship.LEVELS[kind][sw.decree[kind]]}")
                        Text(AegStewardship.LEVEL_HINT[kind][sw.decree[kind]])
                        Row {
                            for (level in AegStewardship.LEVELS[kind].indices) {
                                TextButton(onClick = { decree(kind, level) }, enabled = sw.decree[kind] != level, modifier = Modifier.weight(1f)) {
                                    Text(AegStewardship.LEVELS[kind][level])
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { if (choice == null) TextButton(onClick = onClose) { Text("Close") } },
    )
}

@Composable
internal fun StewardshipHistoryDialog(sw: AegStewardship, cards: List<String>, won: Boolean, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Your civilization's chronicle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Legacy score: ${sw.legacy(won)}")
                sw.history(cards).forEach { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Continue") } },
    )
}
