package com.puresudoku.ui.games.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.puresudoku.ui.tutorial.demoTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.ui.session.FxIds

/** Where one card sits on a table this frame. */
class Placed(
    val card: Int, val x: androidx.compose.ui.unit.Dp, val y: androidx.compose.ui.unit.Dp, val faceUp: Boolean, val z: Float,
    val highlight: Boolean = false, val dim: Boolean = false, val tag: String? = null, val onTap: (() -> Unit)? = null, val art: Int = 0,
)

/**
 * Draws every card from one keyed list, so a card keeps its identity (and glides)
 * when it moves from one pile to another.
 */
@Composable
fun CardLayer(cards: List<Placed>, width: androidx.compose.ui.unit.Dp, fx: CardFx?, targets: com.puresudoku.ui.tutorial.DemoTargets?,
              from: androidx.compose.ui.unit.DpOffset? = null) {
    cards.forEachIndexed { i, p ->
        androidx.compose.runtime.key(p.card) {
            com.puresudoku.ui.components.MovingCard(
                p.card, p.faceUp, width, androidx.compose.ui.unit.DpOffset(p.x, p.y), from = from, delayMs = if (from != null) i * 18 else 0, z = p.z,
                highlight = p.highlight, dim = p.dim, shake = rememberShake(fx, p.card), art = p.art,
                modifier = Modifier
                    .then(if (p.tag != null) Modifier.demoTargetOrNot(targets, p.tag) else Modifier)
                    .then(if (p.onTap != null) Modifier.clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { p.onTap.invoke() } else Modifier),
            )
        }
    }
}

private fun Modifier.demoTargetOrNot(t: com.puresudoku.ui.tutorial.DemoTargets?, key: String) = this.demoTarget(t, key)

/** A card (or thing) that should shake red: [id] changes every time. */
data class CardFx(val id: Long, val card: Int)

fun cardFx(card: Int) = CardFx(FxIds.next(), card)

/** Shake offset in dp for [card] when [fx] targets it. */
@Composable
fun rememberShake(fx: CardFx?, card: Int): Float {
    val a = remember(card) { Animatable(0f) }
    LaunchedEffect(fx?.id) {
        if (fx != null && fx.card == card) a.animateTo(0f, keyframes { durationMillis = 380; -10f at 50; 9f at 110; -6f at 170; 3f at 240; 0f at 380 })
    }
    return a.value
}

/** One-line status message under a card table. */
@Composable
fun TableMessage(text: String, modifier: Modifier = Modifier) {
    if (text.isEmpty()) return
    Text(
        text, modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 6.dp),
        fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface,
    )
}
