package com.puresudoku.ui.manual

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.ui.games.Games
import com.puresudoku.ui.theme.accent

/** Opens the game's manual: the illustrated rulebook for strategy games, the written manual for the rest. */
@Composable
fun GameManual(kind: GameKind, onClose: () -> Unit) {
    if (com.puresudoku.ui.games.realm.Rulebooks.has(kind)) com.puresudoku.ui.games.realm.RulebookScreen(kind, onClose)
    else ManualScreen(kind, onClose)
}

/** A full-screen, scrolling manual: the rules, then the levels, then two-player play where it applies. */
@Composable
fun ManualScreen(kind: GameKind, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val def = remember(kind) { Games.of(kind) }
    val sections = remember(kind) { ManualText.of(kind) }
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(cs.background).clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close the manual")
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("MANUAL", fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                    Text(kind.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                        Text(kind.skill.uppercase(), fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(kind.tagline, style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(sections) { sec -> Section(sec) }
                if (!def.singleLevel) item { Levels(kind) }
                if (kind in com.puresudoku.core.localTwoPlayerGames) item { TwoPlayers() }
                if (kind in com.puresudoku.online.onlineGames) item { Online() }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun Section(sec: ManualSection) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(sec.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.primary)
        for (blk in sec.body) when (blk) {
            is MB.P -> Text(blk.text, fontSize = 15.sp, lineHeight = 22.sp)
            is MB.B -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (it in blk.items) Row {
                    Text("•", fontSize = 15.sp, lineHeight = 22.sp, color = cs.primary, modifier = Modifier.width(16.dp))
                    Text(it, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Levels(kind: GameKind) {
    val def = Games.of(kind)
    val cs = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Levels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.primary)
        Surface(shape = RoundedCornerShape(14.dp), color = cs.surfaceVariant.copy(alpha = 0.6f)) {
            Column(Modifier.padding(vertical = 4.dp)) {
                for ((i, d) in Difficulty.entries.withIndex()) {
                    if (i > 0) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 6.dp).size(9.dp).clip(CircleShape).background(d.accent()))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${d.label} · ${def.levelInfo(d)}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(def.levelBlurb(d), fontSize = 13.sp, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TwoPlayers() {
    Section(ManualSection("Two players on one device", listOf(
        MB.P("Choose \"Two players · one device\" on the game's menu to play a friend instead of the computer. A coloured bar shows whose turn it is."),
        MB.P("Hand-off mode: pass the device across each turn. Games with hidden cards cover the screen between turns so nobody peeks."),
        MB.P("Table mode: lay the device flat between you. On the second player's turn the words and buttons turn round to face them, while the board stays put. Switch modes in the pause screen."),
    )))
}

@Composable
private fun Online() {
    Section(ManualSection("Play a friend online", listOf(
        MB.P("Tap \"Play a friend online\" on the game's menu. One of you taps Host a game and gets a 4-letter code; send it by text or any app. The other types it in and taps Join. The host goes first."),
        MB.P("The two devices connect directly. On some networks that isn't possible, and moves go through PureGames instead: you'll see a note under the board. Either way, nothing about you is stored, and codes expire after 15 minutes."),
        MB.P("Leaving an online game hands your opponent the win. Online games aren't saved and don't count in your stats."),
    )))
}
