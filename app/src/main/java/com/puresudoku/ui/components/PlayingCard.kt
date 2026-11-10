package com.puresudoku.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.puresudoku.core.cards.Cards
import com.puresudoku.core.cards.JOKER
import com.puresudoku.ui.theme.LocalBoardColors
import com.puresudoku.ui.theme.Poppins

val CARD_RED = Color(0xFFD03A3A)
val CARD_INK = Color(0xFF1F2A37)
private val CARD_FACE = Color(0xFFFFFFFF)

/** Card height for a given width (standard 5:7). */
fun cardHeight(w: Dp) = w * 1.4f

/**
 * A playing card. Flips with a turn animation when [faceUp] changes.
 * [highlight] outlines it (selected/target); [dim] fades it.
 */
@Composable
fun PlayingCard(
    card: Int,
    faceUp: Boolean,
    width: Dp,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    dim: Boolean = false,
    error: Boolean = false,
    /** Optional drawable shown as the card's centre art. */
    art: Int = 0,
) {
    val colors = LocalBoardColors.current
    val turn by animateFloatAsState(if (faceUp) 0f else 180f, tween(260, easing = FastOutSlowInEasing), label = "flip")
    val h = cardHeight(width)
    val density = LocalDensity.current
    val showFace = turn < 90f
    Box(
        modifier
            .size(width, h)
            .graphicsLayer {
                rotationY = turn
                cameraDistance = 14f * density.density
                alpha = if (dim) 0.45f else 1f
            },
    ) {
        if (showFace) Face(card, width, highlight, error, art)
        else Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) { Back(width, highlight) }
    }
}

@Composable
private fun Face(card: Int, width: Dp, highlight: Boolean, error: Boolean, art: Int) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(width * 0.1f)
    val density = LocalDensity.current
    val small = with(density) { (width * 0.27f).toSp() }
    val big = with(density) { (width * 0.5f).toSp() }
    Box(
        Modifier.fillMaxSize().clip(shape).background(if (error) Color(0xFFFFE1E1) else CARD_FACE)
            .border(if (highlight) 3.dp else 1.dp, if (highlight) colors.entered else Color(0xFFB9C0CA), shape),
    ) {
        if (art != 0) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(art), null,
                Modifier.align(Alignment.Center).padding(top = width * 0.2f).size(width * 0.84f),
            )
        }
        if (card == JOKER) {
            if (art == 0) Text("★", Modifier.align(Alignment.Center), fontSize = big, color = colors.entered, fontWeight = FontWeight.Bold)
            Text("JOKER", Modifier.align(Alignment.TopStart).padding(start = width * 0.07f, top = 1.dp), fontSize = small * 0.6f, color = colors.entered, fontWeight = FontWeight.Bold, fontFamily = Poppins)
            return@Box
        }
        val col = if (Cards.red(card)) CARD_RED else CARD_INK
        val r = Cards.rankLabel(Cards.rank(card)); val s = Cards.suitSymbol(Cards.suit(card))
        Text(r, Modifier.align(Alignment.TopStart).padding(start = width * 0.07f), fontSize = small, lineHeight = small, color = col, fontWeight = FontWeight.Bold, fontFamily = Poppins)
        Text(s, Modifier.align(Alignment.TopEnd).padding(end = width * 0.07f, top = 1.dp), fontSize = small * 0.85f, lineHeight = small, color = col)
        if (art == 0) Text(
            if (Cards.rank(card) in 11..13) r else s,
            Modifier.align(Alignment.Center).padding(top = width * 0.12f),
            fontSize = if (Cards.rank(card) in 11..13) big * 0.8f else big, color = col, fontWeight = FontWeight.Bold, fontFamily = Poppins,
        )
    }
}

@Composable
fun Back(width: Dp, highlight: Boolean = false) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(width * 0.1f)
    Box(Modifier.fillMaxSize().clip(shape).background(colors.entered).border(if (highlight) 3.dp else 1.dp, if (highlight) colors.wave else Color(0x33000000), shape)) {
        Canvas(Modifier.fillMaxSize().padding(width * 0.1f)) {
            val step = size.width / 3
            for (k in 1..2) {
                drawLine(Color.White.copy(alpha = 0.35f), Offset(k * step, 0f), Offset(k * step, size.height), 2f)
            }
            var y = step
            while (y < size.height) { drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 2f); y += step }
            drawRoundRect(Color.White.copy(alpha = 0.6f), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
        }
    }
}

/** An empty pile outline. */
@Composable
fun CardSlot(width: Dp, modifier: Modifier = Modifier, label: String = "", highlight: Boolean = false) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(width * 0.1f)
    val density = LocalDensity.current
    Box(
        modifier.size(width, cardHeight(width)).clip(shape)
            .border(if (highlight) 3.dp else 1.5.dp, if (highlight) colors.entered else colors.thinLine, shape),
        contentAlignment = Alignment.Center,
    ) { if (label.isNotEmpty()) Text(label, fontSize = with(density) { (width * 0.3f).toSp() }, color = colors.note) }
}

/**
 * A card that glides to [target] whenever it changes (and flies in from [from] the
 * first time it appears). Use inside a Box; [z] orders overlapping cards.
 */
@Composable
fun MovingCard(
    card: Int,
    faceUp: Boolean,
    width: Dp,
    target: DpOffset,
    from: DpOffset? = null,
    delayMs: Int = 0,
    z: Float = 0f,
    highlight: Boolean = false,
    dim: Boolean = false,
    error: Boolean = false,
    shake: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val anim = remember(card) { Animatable(from ?: target, DpOffset.VectorConverter) }
    val first = remember(card) { booleanArrayOf(true) }
    LaunchedEffect(target) {
        // only the very first flight (the deal) is staggered
        if (first[0] && delayMs > 0 && anim.value != target) kotlinx.coroutines.delay(delayMs.toLong())
        first[0] = false
        anim.animateTo(target, tween(240, easing = FastOutSlowInEasing))
    }
    PlayingCard(
        card, faceUp, width,
        modifier.zIndex(z).offset { IntOffset((anim.value.x + shake.dp).roundToPx(), anim.value.y.roundToPx()) },
        highlight = highlight, dim = dim, error = error,
    )
}
