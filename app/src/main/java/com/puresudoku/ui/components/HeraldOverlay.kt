package com.puresudoku.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.Session

/**
 * Shows the computer's actions one card at a time over the game. While cards are showing,
 * the board can't be touched; tapping anywhere skips to the next card.
 */
@Composable
fun HeraldOverlay(session: Session, def: GameDef, modifier: Modifier = Modifier) {
    val h = session.herald
    if (h != null) Box(modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { session.skipHerald() })
    AnimatedContent(
        h, modifier = modifier.padding(horizontal = 14.dp, vertical = 10.dp), label = "herald",
        transitionSpec = { (slideInVertically { -it / 2 } + fadeIn(tween(200))) togetherWith fadeOut(tween(140)) },
    ) { card ->
        if (card == null) { Spacer(Modifier.height(1.dp)); return@AnimatedContent }
        val accent = if (card.mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
        val time = remember(card.id) { Animatable(1f) }
        LaunchedEffect(card.id) { time.animateTo(0f, tween(card.ms.toInt(), easing = LinearEasing)) }
        Surface(
            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth().border(2.dp, accent, RoundedCornerShape(16.dp)).clickable { session.skipHerald() },
        ) {
            Column {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { def.Icon(30.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (card.mine) "Your turn" else "Computer's move", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
                        Text(card.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                        if (card.detail.isNotEmpty()) Text(card.detail, fontSize = 13.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box(Modifier.fillMaxWidth(time.value).height(3.dp).background(accent))
            }
        }
    }
}
