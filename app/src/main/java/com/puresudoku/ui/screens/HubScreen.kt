package com.puresudoku.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.GameCategory
import com.puresudoku.data.ThemeMode
import com.puresudoku.ui.AppViewModel
import com.puresudoku.ui.components.HSpace
import com.puresudoku.ui.components.LogoGrid
import com.puresudoku.ui.components.GamesLogo
import com.puresudoku.ui.components.Wordmark
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.games.Games
import kotlinx.coroutines.delay

/** Landing page: the three collections. */
@Composable
fun HubScreen(vm: AppViewModel) {
    var showAppearance by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            GamesLogo(104.dp, introDelayMs = 150)
            Spacer(Modifier.height(16.dp))
            Wordmark(36.sp)
            Text(
                "${Games.all.size} games for your brain. Every one fresh.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            // Strategy games lead the list; the rest keep their usual order.
            (GameCategory.entries.filter { it != GameCategory.STRATEGY } + GameCategory.STRATEGY).forEachIndexed { idx, cat ->
                val games = Games.all.filter { it.kind.category == cat }
                if (games.isNotEmpty()) CategoryCard(vm, cat, games, idx)
                Spacer(Modifier.height(14.dp))
            }
            if (vm.backupFolderName == null) BackupNudge(vm) { showAppearance = true }
        }
        IconButton(
            onClick = { showAppearance = true },
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp),
        ) { Icon(Icons.Outlined.Settings, contentDescription = "Appearance") }
    }
    if (showAppearance) SettingsDialog(vm, onDismiss = { showAppearance = false })
}

/** Reminds you that records only live on this phone until a backup folder is set. */
@Composable
private fun BackupNudge(vm: AppViewModel, openSettings: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Keep your records safe", fontWeight = FontWeight.SemiBold)
            Text(
                "Your stats and leaderboards live only in the app. Choose a backup folder on your phone and they'll be kept there, even if you uninstall.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(onClick = { vm.pickBackupFolder?.invoke() }) { Text("Choose folder") }
                TextButton(onClick = { vm.pickBackupFile?.invoke() }) { Text("Restore a backup") }
            }
        }
    }
}

@Composable
fun SettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val fmt = remember { java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Text("Appearance", fontWeight = FontWeight.SemiBold)
                ThemeMode.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.updateThemeMode(m) }.padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = m == vm.themeMode, onClick = { vm.updateThemeMode(m) })
                        HSpace(8)
                        Text(m.label, fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Backup", fontWeight = FontWeight.SemiBold)
                Text(
                    if (vm.backupFolderName == null) "Not set up. Saves, stats and leaderboards are deleted if you uninstall."
                    else "Saved automatically to “${vm.backupFolderName}”" + if (vm.lastBackupAt > 0) "\nLast backup ${fmt.format(java.util.Date(vm.lastBackupAt))}" else "",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = { vm.pickBackupFolder?.invoke() }) { Text(if (vm.backupFolderName == null) "Choose folder" else "Change folder") }
                    if (vm.backupFolderName != null) TextButton(onClick = vm::backupNow) { Text("Back up now") }
                }
                TextButton(onClick = { vm.pickBackupFile?.invoke() }) { Text("Restore from a backup file") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun CategoryCard(vm: AppViewModel, cat: GameCategory, games: List<GameDef>, index: Int) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(150L + index * 90L); enter.animateTo(1f, spring(0.7f, Spring.StiffnessMediumLow)) }
    val saved = games.sumOf { g -> vm.slots[g.kind]?.count { it != null } ?: 0 }
    Surface(
        onClick = { vm.openCategory(cat) },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().graphicsLayer {
            alpha = enter.value.coerceIn(0f, 1f)
            translationY = (1f - enter.value) * 60.dp.toPx()
        },
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (g in games.take(5)) g.Icon(46.dp)
            }
            Spacer(Modifier.height(14.dp))
            Text(cat.title, style = MaterialTheme.typography.headlineSmall)
            Text(cat.blurb, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "${games.size} games" + if (saved > 0) " · $saved saved" else "",
                fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** One collection's games. */
@Composable
fun CategoryScreen(vm: AppViewModel, cat: GameCategory) {
    val games = Games.all.filter { it.kind.category == cat }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(cat.title, vm::back)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(games, key = { _, g -> g.kind.name }) { idx, g -> GameCard(vm, g, idx) }
        }
    }
}

@Composable
private fun GameCard(vm: AppViewModel, g: GameDef, index: Int) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(120L + index * 60L); enter.animateTo(1f, spring(0.7f, Spring.StiffnessMediumLow)) }
    val saved = vm.slots[g.kind]?.count { it != null } ?: 0
    val solves = vm.stats[g.kind]?.values?.sumOf { it.wins } ?: 0
    Surface(
        onClick = { vm.openGame(g.kind) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            alpha = enter.value.coerceIn(0f, 1f)
            translationY = (1f - enter.value) * 50.dp.toPx()
        },
    ) {
        val logo = com.puresudoku.ui.components.gameLogoRes(g.kind)
        if (g.kind.category == GameCategory.STRATEGY && logo != 0) {
            // Strategy games are shown by their logo, centred and filling the card, over the skill it trains.
            Column(Modifier.fillMaxWidth().height(180.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(logo), contentDescription = g.kind.title,
                    modifier = Modifier.weight(1f).fillMaxWidth(), contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
                Text(
                    g.kind.skill.uppercase(), fontSize = 12.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 16.sp,
                )
            }
            return@Surface
        }
        Column(Modifier.padding(16.dp)) {
            g.Icon(52.dp)
            Spacer(Modifier.height(12.dp))
            Text(g.kind.title, style = MaterialTheme.typography.titleLarge)
            Text(
                g.kind.skill.uppercase(), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    saved > 0 -> "$saved saved game${if (saved > 1) "s" else ""}"
                    solves > 0 -> "$solves solved"
                    else -> "New"
                },
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AppearanceDialog(current: ThemeMode, onPick: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
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
