package com.puresudoku.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.GameKind
import com.puresudoku.ui.AppViewModel
import com.puresudoku.ui.MAX_FAVORITES
import com.puresudoku.ui.games.Games

private val STAR = Color(0xFFF5B400)

/** A star that pins or unpins a game to the home screen's favourites. */
@Composable
fun FavoriteStar(vm: AppViewModel, kind: GameKind, modifier: Modifier = Modifier) {
    var full by remember { mutableStateOf(false) }
    val on = vm.isFavorite(kind)
    IconButton(onClick = { if (!vm.toggleFavorite(kind)) full = true }, modifier = modifier) {
        Icon(
            if (on) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = if (on) "Unpin ${kind.title} from favourites" else "Pin ${kind.title} to favourites",
            tint = if (on) STAR else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (full) AlertDialog(
        onDismissRequest = { full = false },
        confirmButton = { TextButton(onClick = { full = false }) { Text("OK") } },
        title = { Text("Favourites are full") },
        text = { Text("You can pin up to $MAX_FAVORITES games. Unpin one first: tap its star, or long-press it on the home screen.") },
    )
}

/** The pinned games, four to a row (up to 4×4), at the top of the home screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesCard(vm: AppViewModel, modifier: Modifier = Modifier) {
    val favs = vm.favorites
    var unpin by remember { mutableStateOf<GameKind?>(null) }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, contentDescription = null, tint = STAR, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(6.dp))
                Text("Favourites", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (favs.isNotEmpty()) Text("${favs.size}/$MAX_FAVORITES", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            if (favs.isEmpty()) {
                Text(
                    "Tap the star on any game to pin it here. Up to $MAX_FAVORITES games, one tap away.",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
                return@Column
            }
            for (row in favs.chunked(4)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (k in row) Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .combinedClickable(onClick = { vm.openGame(k) }, onLongClick = { unpin = k })
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Games.of(k).Icon(52.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            k.title, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                    repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
    unpin?.let { k ->
        AlertDialog(
            onDismissRequest = { unpin = null },
            confirmButton = { TextButton(onClick = { vm.toggleFavorite(k); unpin = null }) { Text("Unpin") } },
            dismissButton = { TextButton(onClick = { unpin = null }) { Text("Keep") } },
            title = { Text("Unpin ${k.title}?") },
            text = { Text("It stays in its collection, and you can pin it again any time.") },
        )
    }
}
