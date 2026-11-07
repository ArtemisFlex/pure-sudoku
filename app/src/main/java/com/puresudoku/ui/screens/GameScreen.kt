package com.puresudoku.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.formatTime
import com.puresudoku.ui.GameViewModel
import com.puresudoku.ui.Phase
import com.puresudoku.ui.WinSummary
import com.puresudoku.ui.components.ActionRow
import com.puresudoku.ui.components.Confetti
import com.puresudoku.ui.components.Dot
import com.puresudoku.ui.components.HSpace
import com.puresudoku.ui.components.LogoGrid
import com.puresudoku.ui.components.NumberPad
import com.puresudoku.ui.components.StatusBar
import com.puresudoku.ui.components.SudokuBoard
import com.puresudoku.ui.theme.accent

@Composable
fun GameScreen(vm: GameViewModel) {
    val phase = vm.phase
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::goHome) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Home")
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Dot(vm.difficulty.accent())
                    HSpace(8)
                    Text(vm.difficulty.label, style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = { if (phase == Phase.PAUSED) vm.unpause() else vm.pause() }, enabled = phase == Phase.PLAYING || phase == Phase.PAUSED) {
                    Icon(if (phase == Phase.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = "Pause")
                }
            }
            StatusBar(vm.score, vm.strikes, vm.elapsedMs, Modifier.padding(horizontal = 6.dp, vertical = 10.dp))

            Box(Modifier.widthIn(max = 520.dp)) {
                SudokuBoard(vm)
                // Pause hides the board so the clock can't be cheated.
                AnimatedVisibility(phase == Phase.PAUSED, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .clickable(remember { MutableInteractionSource() }, null) { vm.unpause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Paused", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(formatTime(vm.elapsedMs), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = vm::unpause) { Text("Resume") }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            ActionRow(vm.canUndo, vm.notesMode, vm::undo, vm::erase, vm::toggleNotesMode)
            Spacer(Modifier.height(10.dp))
            @Suppress("UNUSED_VARIABLE") val v = vm.boardVersion
            NumberPad(vm::remaining, vm.notesMode, vm::input)
            Spacer(Modifier.height(14.dp))
        }

        // End-of-game overlays
        if (phase == Phase.WON) Confetti()
        AnimatedVisibility(
            phase == Phase.WON || phase == Phase.LOST,
            enter = fadeIn(tween(400, delayMillis = 650)),
            exit = fadeOut(),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
        AnimatedVisibility(
            phase == Phase.WON || phase == Phase.LOST,
            Modifier.align(Alignment.Center),
            enter = fadeIn(tween(300, delayMillis = 700)) +
                scaleIn(spring(0.6f, Spring.StiffnessMediumLow), initialScale = 0.7f) +
                slideInVertically(tween(450, delayMillis = 700)) { it / 4 },
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            val summary = vm.winSummary
            if (phase == Phase.WON && summary != null) WinCard(vm, summary) else LostCard(vm)
        }
    }
}

@Composable
private fun EndCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp, shadowElevation = 12.dp,
        modifier = Modifier.padding(28.dp).widthIn(max = 380.dp),
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun WinCard(vm: GameViewModel, s: WinSummary) {
    EndCard {
        LogoGrid(64.dp, introDelayMs = 750)
        Spacer(Modifier.height(12.dp))
        Text("Solved!", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))
        Text("SOLVE TIME", fontSize = 11.sp, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatTime(s.timeMs), fontSize = 44.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        if (s.record.newBestTime) Badge("New fastest ${vm.difficulty.label} time!")
        Spacer(Modifier.height(16.dp))
        StatLine("Time bonus", "+${s.bonus.timeBonus}")
        StatLine("Strikes left bonus", "+${s.bonus.livesBonus}")
        StatLine("Final score", s.score.toString(), bold = true)
        if (s.record.newBestScore) Badge("New high score!")
        Spacer(Modifier.height(10.dp))
        StatLine("Best time", formatTime(s.record.stats.bestTimeMs))
        StatLine("Average time", formatTime(s.record.stats.averageTimeMs))
        StatLine("${vm.difficulty.label} solves", s.record.stats.wins.toString())
        Spacer(Modifier.height(20.dp))
        EndButtons(vm)
    }
}

@Composable
private fun LostCard(vm: GameViewModel) {
    EndCard {
        Text("Game over", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Three strikes. This puzzle's save slot has been cleared.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        StatLine("Time played", formatTime(vm.elapsedMs))
        StatLine("Score", vm.score.toString(), bold = true)
        Spacer(Modifier.height(20.dp))
        EndButtons(vm)
    }
}

@Composable
private fun EndButtons(vm: GameViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = vm::goHome) { Text("Home") }
        Button(onClick = vm::playAgain) { Text("New ${vm.difficulty.label}") }
    }
}

@Composable
private fun StatLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSecondary,
        modifier = Modifier.padding(top = 6.dp)
            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
