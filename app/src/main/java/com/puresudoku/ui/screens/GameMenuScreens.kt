package com.puresudoku.ui.screens

import androidx.compose.material3.FilterChip

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.formatTime
import com.puresudoku.data.SlotSummary
import com.puresudoku.ui.AppViewModel
import com.puresudoku.ui.components.Dot
import com.puresudoku.ui.components.HSpace
import com.puresudoku.ui.components.Strikes
import com.puresudoku.ui.games.Games
import com.puresudoku.ui.theme.accent
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- Game menu

@Composable
fun GameMenuScreen(vm: AppViewModel, kind: GameKind) {
    val def = Games.of(kind)
    val saved = vm.slots[kind]?.count { it != null } ?: 0
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar("", vm::back) {
            IconButton(onClick = { vm.openTutorial(kind) }) { Icon(Icons.Outlined.Info, contentDescription = "How to play") }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            def.Icon(104.dp)
            Spacer(Modifier.height(16.dp))
            Text(kind.title, style = MaterialTheme.typography.displayMedium, fontSize = 38.sp)
            Text(
                kind.skill.uppercase(), fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(kind.tagline, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (saved > 0) {
                    Button(onClick = { vm.openSlots(kind) }, Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Continue  ·  $saved saved", fontSize = 17.sp)
                    }
                    OutlinedButton(onClick = { vm.openLevelPicker(kind) }, Modifier.fillMaxWidth().height(56.dp)) {
                        Text("New game", fontSize = 17.sp)
                    }
                } else {
                    Button(onClick = { vm.openLevelPicker(kind) }, Modifier.fillMaxWidth().height(56.dp)) {
                        Text("New game", fontSize = 17.sp)
                    }
                }
                TextButton(onClick = { vm.openTutorial(kind) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    HSpace(8)
                    Text("How to play")
                }
            }
            Spacer(Modifier.height(24.dp))
            StatsPanel(vm, kind)
            Spacer(Modifier.height(24.dp))
            Leaderboard(vm, kind)
            Spacer(Modifier.height(24.dp))
            Records(vm, kind)
            val keep = remember(kind, vm.stats) { vm.keepsakes(kind) }
            if (keep.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                    Text(if (kind == GameKind.HAIKU) "Your haiku" else "Keepsakes", style = MaterialTheme.typography.titleLarge)
                    for (k in keep) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Text(k, Modifier.padding(16.dp), fontFamily = com.puresudoku.ui.theme.Poppins, lineHeight = 24.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Your totals for this game. */
@Composable
private fun StatsPanel(vm: AppViewModel, kind: GameKind) {
    val st = remember(kind, vm.stats) { vm.gameStats(kind) }
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        Text("Your stats", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        val tiles = listOf(
            "Played" to st.played.toString(), "Won" to st.wins.toString(), "Lost" to st.losses.toString(),
            "Win rate" to if (st.played == 0) "–" else "${st.winRate}%", "Streak" to st.currentStreak.toString(), "Best streak" to st.bestStreak.toString(),
            "Best score" to if (st.wins == 0) "–" else st.bestScore.toString(), "Fastest win" to if (st.fastestWinMs == 0L) "–" else formatTime(st.fastestWinMs),
            "Avg win" to if (st.wins == 0) "–" else formatTime(st.averageWinMs),
        )
        for (row in tiles.chunked(3)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, value) in row) Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Time played ${formatTime(st.totalTimeMs)}" + (if (st.draws > 0) " · ${st.draws} drawn" else "") + if (st.abandoned > 0) " · ${st.abandoned} abandoned" else "",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Top 10 wins, or every game you've finished (wins, losses and abandoned saves). */
@Composable
private fun Leaderboard(vm: AppViewModel, kind: GameKind) {
    val board = remember(kind, vm.stats) { vm.leaderboard(kind) }
    val history = remember(kind, vm.stats) { vm.history(kind) }
    var tab by remember { mutableIntStateOf(0) }
    var shown by remember { mutableIntStateOf(20) }
    val fmt = remember { java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()) }
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        Text("Your leaderboard", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Top scores") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("All games (${history.size})") })
        }
        Spacer(Modifier.height(6.dp))
        if (tab == 0) {
            if (board.isEmpty()) {
                Text("Win a game to claim the top spot.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            board.forEachIndexed { i, e ->
                val medal = when (i) { 0 -> Color(0xFFE8B931); 1 -> Color(0xFFAEB6C2); 2 -> Color(0xFFC98A4B); else -> MaterialTheme.colorScheme.surfaceVariant }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (i < 3) MaterialTheme.colorScheme.surface else Color.Transparent).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(50)).background(medal), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (i < 3) Color(0xFF1F2A37) else MaterialTheme.colorScheme.onSurface)
                    }
                    HSpace(12)
                    Text(e.score.toString(), Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Dot(e.difficulty.accent(), 8); HSpace(4)
                    Text(e.difficulty.label, Modifier.width(64.dp), fontSize = 13.sp)
                    Text(formatTime(e.timeMs), Modifier.width(56.dp), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text(fmt.format(java.util.Date(e.date)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            if (history.isEmpty()) {
                Text("Every game you finish, win or lose, will be listed here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            for (e in history.take(shown)) {
                val (bg, fg) = when (e.result) {
                    com.puresudoku.data.GameResult.Result.WON -> Color(0xFF2E7D32) to Color.White
                    com.puresudoku.data.GameResult.Result.LOST -> Color(0xFFC62828) to Color.White
                    com.puresudoku.data.GameResult.Result.DRAW -> Color(0xFF8D6E63) to Color.White
                    com.puresudoku.data.GameResult.Result.ABANDONED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.result.label, Modifier.width(78.dp).clip(RoundedCornerShape(50)).background(bg).padding(vertical = 3.dp),
                        color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    HSpace(10)
                    Text(e.score.toString(), Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Dot(e.difficulty.accent(), 8); HSpace(4)
                    Text(e.difficulty.label, Modifier.width(60.dp), fontSize = 13.sp)
                    Text(formatTime(e.timeMs), Modifier.width(54.dp), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text(fmt.format(java.util.Date(e.date)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (history.size > shown) TextButton(onClick = { shown += 30 }, Modifier.fillMaxWidth()) { Text("Show more") }
        }
    }
}

@Composable
private fun Records(vm: AppViewModel, kind: GameKind) {
    val stats = vm.stats[kind] ?: return
    val played = Difficulty.entries.filter { (stats[it]?.wins ?: 0) > 0 }
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        Text("Records", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (played.isEmpty()) {
            Text("Solve a puzzle to set your first time.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Header("Level", 1.4f); Header("Solves", 1f); Header("Best time", 1.2f); Header("Avg", 1.2f); Header("Best score", 1.3f)
        }
        for (d in played) {
            val s = stats.getValue(d)
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
private fun RowScope.Header(t: String, w: Float) =
    Text(t, Modifier.weight(w), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)

@Composable
private fun RowScope.Cell(t: String, w: Float) =
    Text(t, Modifier.weight(w), fontSize = 14.sp, fontFamily = FontFamily.Monospace)

// ---------------------------------------------------------------- Level picker

@Composable
fun LevelScreen(vm: AppViewModel, kind: GameKind) {
    val def = Games.of(kind)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar("New ${kind.title}", vm::back)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (vm.slots[kind]?.none { it == null } == true) {
                Text(
                    "All 3 ${kind.title} save slots are full — you'll pick one to replace.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                )
            }
            Difficulty.entries.forEachIndexed { idx, d ->
                val enter = remember { Animatable(0f) }
                LaunchedEffect(Unit) { delay(idx * 55L); enter.animateTo(1f, spring(0.7f, Spring.StiffnessMediumLow)) }
                val best = vm.stats[kind]?.get(d)?.bestTimeMs ?: 0L
                Surface(
                    onClick = { vm.chooseLevel(kind, d) },
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
                            Text(def.levelBlurb(d), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(def.levelInfo(d), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun SlotsScreen(vm: AppViewModel, kind: GameKind, replaceFor: Difficulty?) {
    var confirmDelete by remember { mutableIntStateOf(-1) }
    var confirmReplace by remember { mutableIntStateOf(-1) }
    val slots = vm.slots[kind].orEmpty()

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(if (replaceFor == null) "Saved ${kind.title}" else "Replace a save", vm::back)
        if (replaceFor != null) {
            Text(
                "All slots are full. Choose a game to replace with a new ${replaceFor.label} puzzle.",
                Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            slots.forEachIndexed { slot, snap ->
                val enter = remember { Animatable(0f) }
                LaunchedEffect(Unit) { delay(slot * 70L); enter.animateTo(1f, spring(0.7f, Spring.StiffnessMediumLow)) }
                Box(Modifier.graphicsLayer { alpha = enter.value.coerceIn(0f, 1f); translationY = (1f - enter.value) * 40.dp.toPx() }) {
                    if (snap == null) EmptySlot(slot) else SlotCard(
                        slot, snap,
                        onOpen = { if (replaceFor != null) confirmReplace = slot else vm.resume(kind, slot) },
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
                TextButton(onClick = { vm.deleteSlot(kind, confirmDelete); confirmDelete = -1 }) {
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
                TextButton(onClick = { val s = confirmReplace; confirmReplace = -1; vm.replaceSlot(kind, s, replaceFor) }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = -1 }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SlotCard(slot: Int, s: SlotSummary, onOpen: () -> Unit, onDelete: (() -> Unit)?) {
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
            LinearProgressIndicator(
                progress = { s.progress }, color = s.difficulty.accent(),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${(s.progress * 100).toInt()}%", fontFamily = FontFamily.Monospace)
                HSpace(16)
                Text("⏱ ${formatTime(s.elapsedMs)}", fontFamily = FontFamily.Monospace)
                HSpace(16)
                Text("★ ${s.score}", fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                if (s.usesStrikes) Strikes(s.strikes, size = 16)
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
fun GeneratingScreen(kind: GameKind, d: Difficulty) {
    val def = Games.of(kind)
    val t = rememberInfiniteTransition(label = "gen")
    val pulse by t.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
    val spin by t.animateFloat(-4f, 4f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "spin")
    Column(
        Modifier.fillMaxSize().safeDrawingPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse; rotationZ = spin }) { def.Icon(120.dp) }
        Spacer(Modifier.height(28.dp))
        Text("Building a fresh ${d.label} ${kind.title}", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("Every puzzle is brand new", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- shared

@Composable
fun TopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        actions()
    }
}
