package com.puresudoku.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.puresudoku.ui.components.Wordmark
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.games.Games
import kotlinx.coroutines.delay

/** Home: every game as a card. */
@Composable
fun HubScreen(vm: AppViewModel) {
    var showAppearance by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    LogoGrid(96.dp, introDelayMs = 150)
                    Spacer(Modifier.height(16.dp))
                    Wordmark(36.sp)
                    Text(
                        "${Games.all.size} games for your brain. Every one fresh.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
            }
            for (cat in GameCategory.entries) {
                val games = Games.all.filter { it.kind.category == cat }
                if (games.isEmpty()) continue
                item(span = { GridItemSpan(maxLineSpan) }, key = "h-${cat.name}") {
                    Text(cat.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
                }
                itemsIndexed(games, key = { _, g -> g.kind.name }) { idx, g -> GameCard(vm, g, idx) }
            }
        }
        IconButton(
            onClick = { showAppearance = true },
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp),
        ) { Icon(Icons.Outlined.Settings, contentDescription = "Appearance") }
    }
    if (showAppearance) AppearanceDialog(vm.themeMode, onPick = vm::updateThemeMode, onDismiss = { showAppearance = false })
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
