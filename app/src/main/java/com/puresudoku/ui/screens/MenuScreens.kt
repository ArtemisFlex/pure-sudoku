package com.puresudoku.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameSnapshot
import com.puresudoku.core.formatTime
import com.puresudoku.data.ThemeMode
import com.puresudoku.ui.GameViewModel
import com.puresudoku.ui.progress
import com.puresudoku.ui.components.Dot
import com.puresudoku.ui.components.HSpace
import com.puresudoku.ui.components.LogoGrid
import com.puresudoku.ui.components.Wordmark
import com.puresudoku.ui.components.Strikes
import com.puresudoku.ui.theme.accent
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- Home

@Composable
fun HomeScreen(vm: GameViewModel) {
    var showAppearance by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        LogoGrid(132.dp, introDelayMs = 150)
        Spacer(Modifier.height(22.dp))
        Wordmark(40.sp)
        Text("Six levels. Every puzzle fresh.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))

        val saved = vm.slots.count { it != null }
        Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (saved > 0) {
                Button(onClick = vm::openSlots, Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Continue  ·  $saved saved", fontSize = 17.sp)
                }
                OutlinedButton(onClick = vm::openDifficultyPicker, Modifier.fillMaxWidth().height(56.dp)) {
                    Text("New game", fontSize = 17.sp)
                }
            } else {
                Button(onClick = vm::openDifficultyPicker, Modifier.fillMaxWidth().height(56.dp)) {
                    Text("New game", fontSize = 17.sp)
                }
            }
        }
        Spacer(Modifier.height(36.dp))
        Records(vm)
    }
    IconButton(
        onClick = { showAppearance = true },
        modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp),
    ) {
        Icon(Icons.Outlined.Settings, contentDescription = "Appearance")
    }
    }
    if (showAppearance) AppearanceDialog(vm.themeMode, onPick = vm::updateThemeMode, onDismiss = { showAppearance = false })
}

@Composable
private fun AppearanceDialog(current: ThemeMode, onPick: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appearance") },
        text = {
            Column {
                ThemeMode.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(m) }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = m == current, onClick = { onPick(m) })
                        HSpace(8)
                        Text(m.label, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun Records(vm: GameViewModel) {
    val played = Difficulty.entries.filter { (vm.stats[it]?.wins ?: 0) > 0 }
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        Text("Records", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (played.isEmpty()) {
            Text("Solve a puzzle to set your first time.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else RecordsTable(vm, played)
    }
}

@Composable
private fun RecordsTable(vm: GameViewModel, played: List<Difficulty>) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Header("Level", 1.4f); Header("Solves", 1f); Header("Best time", 1.2f); Header("Avg", 1.2f); Header("Best score", 1.3f)
        }
        for (d in played) {
            val s = vm.stats.getValue(d)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1.4f), verticalAlignment = Alignment.CenterVertically) {
                    Dot(d.accent(), 8); HSpace(6); Text(d.label, fontSize = 14.sp)
                }
                Cell(s.wins.toString(), 1f)
                Cell(formatTime(s.bestTimeMs), 1.2f)
                Cell(formatTime(s.averageTimeMs), 1.2f)
                Cell(s.bestScore.toString(), 1.3f)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Header(t: String, w: Float) =
    Text(t, Modifier.weight(w), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(t: String, w: Float) =
    Text(t, Modifier.weight(w), fontSize = 14.sp, fontFamily = FontFamily.Monospace)

// ---------------------------------------------------------------- Difficulty picker

private fun Difficulty.blurb() = when (this) {
    Difficulty.EASY -> "Relaxed. Singles only."
    Difficulty.MEDIUM -> "Fewer clues; look for hidden singles."
    Difficulty.HARD -> "Needs locked candidates or pairs."
    Difficulty.EXPERT -> "Pairs and triples, maybe an X-Wing."
    Difficulty.MASTER -> "Swordfish, XY-Wings, sparse grids."
    Difficulty.EXTREME -> "Beyond standard techniques. Good luck."
}

@Composable
fun DifficultyScreen(vm: GameViewModel) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar("New game", vm::back)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (vm.slots.none { it == null }) {
                Text(
                    "All 3 save slots are full — you'll pick one to replace.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                )
            }
            Difficulty.entries.forEachIndexed { idx, d ->
                val enter = remember { Animatable(0f) }
                LaunchedEffect(Unit) { delay(idx * 55L); enter.animateTo(1f, spring(0.7f, Spring.StiffnessMediumLow)) }
                val best = vm.stats[d]?.bestTimeMs ?: 0L
                Surface(
                    onClick = { vm.chooseDifficulty(d) },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().graphicsLayer {
                        alpha = enter.value.coerceIn(0f, 1f)
                        translationX = (1f - enter.value) * 80.dp.toPx()
                    },
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp, 42.dp).clip(RoundedCornerShape(50)).background(d.accent()))
                        HSpace(16)
                        Column(Modifier.weight(1f)) {
                            Text(d.label, style = MaterialTheme.typography.titleLarge)
                            Text(d.blurb(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${d.minClues}–${d.maxClues} clues", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (best > 0) Text("Best ${formatTime(best)}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Save slots

@Composable
fun SlotsScreen(vm: GameViewModel, replaceFor: Difficulty?) {
    var confirmDelete by remember { mutableStateOf(-1) }
    var confirmReplace by remember { mutableStateOf(-1) }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(if (replaceFor == null) "Saved games" else "Replace a save", vm::back)
        if (replaceFor != null) {
            Text(
                "All slots are full. Choose a game to replace with a new ${replaceFor.label} puzzle.",
                Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            vm.slots.forEachIndexed { slot, snap ->
                val enter = remember { Animatable(0f) }
                LaunchedEffect(Unit) { delay(slot * 70L); enter.animateTo(1f, spring(0.7f, Spring.StiffnessMediumLow)) }
                Box(Modifier.graphicsLayer { alpha = enter.value.coerceIn(0f, 1f); translationY = (1f - enter.value) * 40.dp.toPx() }) {
                    if (snap == null) EmptySlot(slot) else SlotCard(
                        slot, snap,
                        onOpen = { if (replaceFor != null) confirmReplace = slot else vm.resume(slot) },
                        onDelete = if (replaceFor == null) ({ confirmDelete = slot }) else null,
                    )
                }
            }
        }
    }

    if (confirmDelete >= 0) {
        AlertDialog(
            onDismissRequest = { confirmDelete = -1 },
            title = { Text("Delete this game?") },
            text = { Text("Slot ${confirmDelete + 1} will be cleared. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteSlot(confirmDelete); confirmDelete = -1 }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = -1 }) { Text("Cancel") } },
        )
    }
    if (confirmReplace >= 0 && replaceFor != null) {
        AlertDialog(
            onDismissRequest = { confirmReplace = -1 },
            title = { Text("Replace slot ${confirmReplace + 1}?") },
            text = { Text("The saved game will be lost and a new ${replaceFor.label} puzzle will start there.") },
            confirmButton = {
                TextButton(onClick = { val s = confirmReplace; confirmReplace = -1; vm.replaceSlot(s, replaceFor) }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = -1 }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SlotCard(slot: Int, s: GameSnapshot, onOpen: () -> Unit, onDelete: (() -> Unit)?) {
    Surface(onClick = onOpen, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SLOT ${slot + 1}", fontSize = 11.sp, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HSpace(10)
                Dot(s.difficulty.accent(), 8); HSpace(6)
                Text(s.difficulty.label, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (onDelete != null) IconButton(onClick = onDelete, Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            val p = s.progress()
            LinearProgressIndicator(
                progress = { p }, color = s.difficulty.accent(),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${(p * 100).toInt()}%", fontFamily = FontFamily.Monospace)
                HSpace(16)
                Text("⏱ ${formatTime(s.elapsedMs)}", fontFamily = FontFamily.Monospace)
                HSpace(16)
                Text("★ ${s.score}", fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                Strikes(s.strikes, size = 16)
            }
        }
    }
}

@Composable
private fun EmptySlot(slot: Int) {
    Box(
        Modifier.fillMaxWidth().height(96.dp)
            .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("Slot ${slot + 1} · empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- Generating

@Composable
fun GeneratingScreen(d: Difficulty) {
    val t = rememberInfiniteTransition(label = "gen")
    val phase by t.animateFloat(0f, 9f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Column(
        Modifier.fillMaxSize().safeDrawingPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The logo grid, with its highlight sweeping cell to cell while the puzzle is built.
        val order = intArrayOf(0, 1, 2, 5, 8, 7, 6, 3, 4)
        LogoGrid(120.dp, highlight = order[phase.toInt().coerceIn(0, 8)], softHighlight = -1)
        Spacer(Modifier.height(28.dp))
        Text("Building a fresh ${d.label} puzzle", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Unique solution · graded by technique",
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------- shared

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}
