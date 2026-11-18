package com.puresudoku.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
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
            Spacer(Modifier.height(44.dp))   // clear of the settings button
            com.puresudoku.ui.components.FavoritesCard(vm)
            Spacer(Modifier.height(18.dp))
            // a compact masthead: the logo with "Pure" stacked over "Games" beside it
            Row(verticalAlignment = Alignment.CenterVertically) {
                GamesLogo(64.dp, introDelayMs = 150)
                Spacer(Modifier.width(12.dp))
                Wordmark(28.sp, stacked = true)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${Games.all.size} games for your brain. Every one fresh.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            // the collections as a grid: two side by side on a phone, more on wider screens
            val cards = ArrayList<@Composable (Modifier) -> Unit>()
            cards += { m -> OriginalsCard(vm, m) }
            cards += { m -> TwoPlayerCard(vm, m) }
            (GameCategory.entries.filter { it != GameCategory.STRATEGY } + GameCategory.STRATEGY).forEachIndexed { idx, cat ->
                val games = Games.all.filter { it.kind.category == cat }
                if (games.isNotEmpty()) cards += { m -> CategoryCard(vm, cat, games, idx + 1, m) }
            }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.widthIn(max = 1100.dp).fillMaxWidth()) {
                val gap = 12.dp
                val cols = ((maxWidth + gap) / (220.dp + gap)).toInt().coerceIn(2, 4)
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (row in cards.chunked(cols)) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (card in row) card(Modifier.weight(1f))
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            LegalLinks()
            Spacer(Modifier.height(4.dp))
        }
        IconButton(
            onClick = { showAppearance = true },
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp),
        ) { Icon(Icons.Outlined.Settings, contentDescription = "Appearance") }
    }
    if (showAppearance) SettingsDialog(vm, onDismiss = { showAppearance = false })
}

/** Small print at the foot of the home page: privacy policy, terms and a contact address. */
@Composable
private fun LegalLinks() {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(
            "Privacy" to "https://puregames.app/privacy.html",
            "Terms" to "https://puregames.app/terms.html",
            "Contact" to "mailto:support@puregames.app",
        ).forEachIndexed { i, (label, link) ->
            if (i > 0) Text("·", fontSize = 11.sp, color = muted)
            Text(
                label, fontSize = 11.sp, color = muted,
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .clickable { runCatching { uri.openUri(link) } }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun SettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
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
                    if (!vm.backupsUseFolder) "Saves, stats and leaderboards are kept in this browser. Download a backup file to keep a copy or move to another device."
                    else if (vm.backupFolderName == null) "Not set up. Saves, stats and leaderboards are deleted if you uninstall."
                    else "Saved automatically to “${vm.backupFolderName}”" + if (vm.lastBackupAt > 0) "\nLast backup ${com.puresudoku.platform.formatDate(vm.lastBackupAt, "MMM d, h:mm a")}" else "",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = { vm.pickBackupFolder?.invoke() }) { Text(if (!vm.backupsUseFolder) "Download backup" else if (vm.backupFolderName == null) "Choose folder" else "Change folder") }
                    if (vm.backupFolderName != null) TextButton(onClick = vm::backupNow) { Text("Back up now") }
                }
                TextButton(onClick = { vm.pickBackupFile?.invoke() }) { Text("Restore from a backup file") }
                // the web app only: check for a new deploy
                com.puresudoku.platform.updateButtonLabel?.let { label ->
                    Spacer(Modifier.height(14.dp))
                    Text("Updates", fontWeight = FontWeight.SemiBold)
                    Text(com.puresudoku.platform.updateHelp, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    var updateNote by remember { mutableStateOf("") }
                    TextButton(onClick = { updateNote = "Checking…"; com.puresudoku.platform.checkForUpdate { updateNote = it } }) { Text(label) }
                    if (updateNote.isNotEmpty()) Text(updateNote, fontSize = 13.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun CategoryCard(vm: AppViewModel, cat: GameCategory, games: List<GameDef>, index: Int, modifier: Modifier) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(150L + index * 90L); enter.animateTo(1f, spring(0.7f, Spring.StiffnessMediumLow)) }
    val saved = games.sumOf { g -> vm.slots[g.kind]?.count { it != null } ?: 0 }
    Surface(
        onClick = { vm.openCategory(cat) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.height(CARD_HEIGHT).graphicsLayer {
            alpha = enter.value.coerceIn(0f, 1f)
            translationY = (1f - enter.value) * 60.dp.toPx()
        },
    ) {
        CollectionCardBody(games.take(3), cat.title, cat.blurb, "${games.size} game${if (games.size == 1) "" else "s"}" + if (saved > 0) " · $saved saved" else "")
    }
}

private val CARD_HEIGHT = 150.dp

/** A collection on the home grid: three of its games, its name, a short line about it, and a count. */
@Composable
private fun CollectionCardBody(games: List<GameDef>, title: String, blurb: String, count: String) {
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { for (g in games) g.Icon(32.dp) }
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(blurb, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text(count, fontSize = 11.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

private val twoPlayerGames get() = Games.all.filter { it.kind in com.puresudoku.core.localTwoPlayerGames }

/** The Two Players collection: every game two people can play, from board games to strategy. */
@Composable
private fun TwoPlayerCard(vm: AppViewModel, modifier: Modifier) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(150L); enter.animateTo(1f, spring(0.7f, Spring.StiffnessMediumLow)) }
    val games = twoPlayerGames
    Surface(
        onClick = vm::openTwoPlayer,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.height(CARD_HEIGHT).graphicsLayer {
            alpha = enter.value.coerceIn(0f, 1f)
            translationY = (1f - enter.value) * 60.dp.toPx()
        },
    ) {
        val show = listOf(com.puresudoku.core.GameKind.CHESS, com.puresudoku.core.GameKind.CRAZY_EIGHTS, com.puresudoku.core.GameKind.GO).map { Games.of(it) }
        CollectionCardBody(show, "Two Players", "Play a friend on one device or online", "${games.size} games")
    }
}

/** The games designed for PureGames by BCS Gaming, in the order they appear in the Originals list. */
private val originalKinds = listOf(
    com.puresudoku.core.GameKind.CYVASSE, com.puresudoku.core.GameKind.REALMFORGE, com.puresudoku.core.GameKind.REALM_DRAGON, com.puresudoku.core.GameKind.REALM_NIGHT, com.puresudoku.core.GameKind.REALM_ASH, com.puresudoku.core.GameKind.WISTERIA, com.puresudoku.core.GameKind.ASHFALL, com.puresudoku.core.GameKind.VEILGUARD,
    com.puresudoku.core.GameKind.KREASHUN, com.puresudoku.core.GameKind.AEGWASPELL, com.puresudoku.core.GameKind.AEG_CONQUEST, com.puresudoku.core.GameKind.DEED_DUNGEON, com.puresudoku.core.GameKind.CRATEBARGE,
)
private val originalGames get() = originalKinds.map { Games.of(it) }

/** The Original Games collection: games made for PureGames, from every corner of the app. */
@Composable
private fun OriginalsCard(vm: AppViewModel, modifier: Modifier) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(110L); enter.animateTo(1f, spring(0.7f, Spring.StiffnessMediumLow)) }
    val games = originalGames
    Surface(
        onClick = vm::openOriginals,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.height(CARD_HEIGHT).graphicsLayer {
            alpha = enter.value.coerceIn(0f, 1f)
            translationY = (1f - enter.value) * 60.dp.toPx()
        },
    ) {
        val show = listOf(com.puresudoku.core.GameKind.CYVASSE, com.puresudoku.core.GameKind.REALMFORGE, com.puresudoku.core.GameKind.AEGWASPELL).map { Games.of(it) }
        CollectionCardBody(show, "Original Games", "Designed by BCS Gaming, only in PureGames", "${games.size} games")
    }
}

/** Every original game in one list. */
@Composable
fun OriginalsScreen(vm: AppViewModel) {
    val games = originalGames
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar("Original Games", vm::back)
        LazyVerticalGrid(
            columns = DrawerColumns,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Games designed for PureGames by BCS Gaming. You won't find them anywhere else.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            itemsIndexed(games, key = { _, g -> g.kind.name }) { idx, g -> GameCard(vm, g, idx) }
        }
    }
}

/** Every two-player game in one list. */
@Composable
fun TwoPlayerScreen(vm: AppViewModel) {
    val games = twoPlayerGames
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar("Two Players", vm::back)
        LazyVerticalGrid(
            columns = DrawerColumns,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text(
                    "Each game here can be played with a friend on this device, with table mode for sitting across from each other, or online with a room code.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            itemsIndexed(games, key = { _, g -> g.kind.name }) { idx, g -> GameCard(vm, g, idx) }
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
            columns = DrawerColumns,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(games, key = { _, g -> g.kind.name }) { idx, g -> GameCard(vm, g, idx) }
        }
    }
}

/** Game drawers: never fewer than two columns (a phone fits two side by side), and as many 160dp cards as fit on wider screens. */
private val DrawerColumns = object : GridCells {
    override fun androidx.compose.ui.unit.Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val count = maxOf(2, (availableSize + spacing) / (160.dp.roundToPx() + spacing))
        val cell = (availableSize - spacing * (count - 1)) / count
        val extra = availableSize - spacing * (count - 1) - cell * count
        return List(count) { cell + if (it < extra) 1 else 0 }
    }
}

@Composable
private fun GameCard(vm: AppViewModel, g: GameDef, index: Int) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(120L + index * 60L); enter.animateTo(1f, spring(0.7f, Spring.StiffnessMediumLow)) }
    val saved = vm.slots[g.kind]?.count { it != null } ?: 0
    val solves = vm.stats[g.kind]?.values?.sumOf { it.wins } ?: 0
    Box(Modifier.fillMaxWidth().graphicsLayer {
        alpha = enter.value.coerceIn(0f, 1f)
        translationY = (1f - enter.value) * 50.dp.toPx()
    }) {
    Surface(
        onClick = { vm.openGame(g.kind) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val logo = if (g.kind == com.puresudoku.core.GameKind.CRATEBARGE) com.puresudoku.R.drawable.cratebarge_logo_v1
            else com.puresudoku.ui.components.gameLogoRes(g.kind)
        if ((g.kind.category == GameCategory.STRATEGY || g.kind == com.puresudoku.core.GameKind.CRATEBARGE || g.kind == com.puresudoku.core.GameKind.CYVASSE) && logo != 0) {
            // Strategy games are shown by their logo, centred and filling the card, over the skill it trains.
            Column(Modifier.fillMaxWidth().height(140.dp).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            g.Icon(44.dp)
            Spacer(Modifier.height(8.dp))
            Text(g.kind.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                g.kind.skill.uppercase(), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
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
    com.puresudoku.ui.components.FavoriteStar(vm, g.kind, Modifier.align(Alignment.TopEnd).padding(4.dp))
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
