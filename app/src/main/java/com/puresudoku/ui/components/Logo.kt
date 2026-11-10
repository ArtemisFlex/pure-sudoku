package com.puresudoku.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.puresudoku.ui.theme.LocalBoardColors
import com.puresudoku.ui.theme.Poppins
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/** The logo's 3x3 grid: 5 . 3 / . 9 . / 2 . 8 */
private val LOGO_DIGITS = intArrayOf(5, 0, 3, 0, 9, 0, 2, 0, 8)

/**
 * The Pure Sudoku logo grid, drawn live so it can animate.
 *
 * @param highlight cell (0-8) shown solid blue, like the logo's 9; -1 for none.
 * @param softHighlight cell shown pale blue, like the logo's 8; -1 for none.
 * @param introDelayMs if >= 0, cells pop in one by one starting after this delay.
 */
@Composable
fun LogoGrid(
    size: Dp,
    modifier: Modifier = Modifier,
    highlight: Int = 4,
    softHighlight: Int = 8,
    showDigits: Boolean = true,
    introDelayMs: Long = -1,
) {
    val colors = LocalBoardColors.current
    val frame = colors.thickLine
    val frameW = size * 0.06f
    val lineW = size * 0.022f
    val radius = size * 0.19f

    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(colors.boardBg)
            .drawWithContent {
                drawContent()
                val fw = frameW.toPx()
                val inset = fw / 2
                val s = this.size.width - fw
                val cell = s / 3f
                for (k in 1..2) {
                    val p = inset + k * cell
                    drawLine(frame, Offset(p, inset), Offset(p, inset + s), lineW.toPx())
                    drawLine(frame, Offset(inset, p), Offset(inset + s, p), lineW.toPx())
                }
                val r = radius.toPx() - inset
                drawRoundRect(frame, Offset(inset, inset), Size(s, s), CornerRadius(r), style = Stroke(fw))
            }
            .padding(frameW),
    ) {
        val density = LocalDensity.current
        val digitSp: TextUnit = with(density) { ((size - frameW * 2) / 3 * 0.62f).toSp() }
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until 3) Row(Modifier.fillMaxWidth().weight(1f)) {
                for (col in 0 until 3) {
                    val k = row * 3 + col
                    LogoCell(
                        digit = if (showDigits) LOGO_DIGITS[k] else 0,
                        strong = k == highlight,
                        soft = k == softHighlight,
                        fontSize = digitSp,
                        introDelayMs = if (introDelayMs >= 0) introDelayMs + k * 70L else -1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LogoCell(digit: Int, strong: Boolean, soft: Boolean, fontSize: TextUnit, introDelayMs: Long, modifier: Modifier) {
    val colors = LocalBoardColors.current
    val bg by animateColorAsState(
        when {
            strong -> colors.entered
            soft -> colors.sameDigit
            else -> Color.Transparent
        },
        tween(220), label = "logoCell",
    )
    val pop = remember { Animatable(if (introDelayMs >= 0) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (introDelayMs >= 0) {
            delay(introDelayMs)
            pop.animateTo(1f, spring(0.45f, Spring.StiffnessMediumLow))
        }
    }
    Box(modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        if (digit != 0) {
            Text(
                digit.toString(),
                fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = fontSize,
                color = if (strong) colors.boardBg else colors.given,
                modifier = Modifier.graphicsLayer {
                    scaleX = pop.value; scaleY = pop.value
                    alpha = pop.value.coerceIn(0f, 1f)
                },
            )
        }
    }
}

/** "PureGames" wordmark: "Pure" in accent blue, "Games" in ink. */
@Composable
fun Wordmark(fontSize: TextUnit, modifier: Modifier = Modifier) {
    val colors = LocalBoardColors.current
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.entered)) { append("Pure") }
            withStyle(SpanStyle(color = colors.given)) { append("Games") }
        },
        fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = fontSize,
        modifier = modifier,
    )
}

// =====================================================================  PureGames logo

private object GamesLogoPaths {
    private fun p(d: String) = androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath()
    val tile = p("M39.12,31.44H68.88A7.68,7.68 0 0 1 76.56,39.12V68.88A7.68,7.68 0 0 1 68.88,76.56H39.12A7.68,7.68 0 0 1 31.44,68.88V39.12A7.68,7.68 0 0 1 39.12,31.44Z")
    val blueCell = p("M54,54H76.56V68.88A7.68,7.68 0 0 1 68.88,76.56H54V54Z")
    val seven = p("M60.6,59.4H70.2V61.5L65,71.2H62.2L67.2,61.7H60.6Z")
    val die = p("M38.6,36.2H46.8A2.4,2.4 0 0 1 49.2,38.6V46.8A2.4,2.4 0 0 1 46.8,49.2H38.6A2.4,2.4 0 0 1 36.2,46.8V38.6A2.4,2.4 0 0 1 38.6,36.2Z")
    val card = p("M62,36.2H68.6A1.6,1.6 0 0 1 70.2,37.8V47.6A1.6,1.6 0 0 1 68.6,49.2H62A1.6,1.6 0 0 1 60.4,47.6V37.8A1.6,1.6 0 0 1 62,36.2Z")
    val heart = p("M65.3,46.4L61.9,43C60.7,41.8 61.5,39.8 63.2,39.8C64.1,39.8 64.8,40.4 65.3,41.1C65.8,40.4 66.5,39.8 67.4,39.8C69.1,39.8 69.9,41.8 68.7,43Z")
    val letterA = p("M37.6,71.2L41.4,59.4H44.1L47.9,71.2H45.3L44.5,68.5H41L40.2,71.2ZM41.6,66.4H43.9L42.75,62.4Z").apply {
        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
    }
    /** Pip positions (offsets from the die centre) for faces 1..6. */
    val pips = listOf(
        listOf(0 to 0), listOf(-1 to -1, 1 to 1), listOf(-1 to -1, 0 to 0, 1 to 1), listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1),
        listOf(-1 to -1, 1 to -1, 0 to 0, -1 to 1, 1 to 1), listOf(-1 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 1 to 1),
    )
}

/**
 * The PureGames logo, drawn live: a die, a playing card, a letter tile and a number in a
 * 2x2 tile. With [introDelayMs] >= 0 the pieces arrive one by one (the die tumbles and rolls,
 * the card flips in, the letter pops, the number cell fills) and the die re-rolls now and then.
 */
@Composable
fun GamesLogo(size: Dp, modifier: Modifier = Modifier, introDelayMs: Long = -1) {
    val colors = LocalBoardColors.current
    val animate = introDelayMs >= 0
    val die = remember { Animatable(if (animate) 0f else 1f) }
    val card = remember { Animatable(if (animate) 0f else 1f) }
    val letter = remember { Animatable(if (animate) 0f else 1f) }
    val fill = remember { Animatable(if (animate) 0f else 1f) }
    val roll = remember { Animatable(0f) }
    val face = remember { androidx.compose.runtime.mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        delay(introDelayMs)
        kotlinx.coroutines.coroutineScope {
            launch {
                launch { die.animateTo(1f, spring(0.5f, Spring.StiffnessLow)) }
                repeat(7) { face.intValue = (face.intValue % 6) + 1; delay(70) }
                face.intValue = 5
            }
            launch { delay(160); card.animateTo(1f, spring(0.55f, Spring.StiffnessMediumLow)) }
            launch { delay(320); letter.animateTo(1f, spring(0.4f, Spring.StiffnessMediumLow)) }
            launch { delay(480); fill.animateTo(1f, tween(380)) }
        }
        // every few seconds the die tumbles to a new face and back to five
        while (true) {
            delay(5200)
            launch { roll.snapTo(0f); roll.animateTo(1f, tween(620)) }
            repeat(8) { face.intValue = (face.intValue % 6) + 1; delay(75) }
            face.intValue = 5
        }
    }
    val ink = colors.given
    val frame = colors.thickLine
    val paper = colors.boardBg
    val blue = colors.entered
    val red = CARD_RED
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val k = this.size.width / 45.12f
        withTransformLeft(k) {
            val P = GamesLogoPaths
            drawPath(P.tile, paper)
            // number cell fills from the bottom
            clipRect(54f, 76.56f - 22.56f * fill.value, 76.56f, 76.56f) { drawPath(P.blueCell, blue) }
            val s7 = fill.value.coerceIn(0f, 1f)
            scale(s7, s7, Offset(65.28f, 65.28f)) { drawPath(P.seven, paper) }
            drawLine(frame, Offset(54f, 31.44f), Offset(54f, 76.56f), 1.06f)
            drawLine(frame, Offset(31.44f, 54f), Offset(76.56f, 54f), 1.06f)
            drawPath(P.tile, frame, style = Stroke(2.88f))
            // die
            val dv = die.value
            if (dv > 0.01f) {
                rotate(-12f - (1f - dv) * 300f - roll.value * 360f, Offset(42.72f, 42.72f)) {
                    scale(dv, dv, Offset(42.72f, 42.72f)) {
                        drawPath(P.die, paper); drawPath(P.die, ink, style = Stroke(1.3f))
                        for ((dx, dy) in P.pips[face.intValue - 1]) drawCircle(ink, 1.15f, Offset(42.7f + dx * 3.3f, 42.7f + dy * 3.3f))
                    }
                }
            }
            // card flips in
            val cv = card.value
            if (cv > 0.01f) {
                rotate(12f, Offset(65.28f, 42.72f)) {
                    scale(cv.coerceIn(0f, 1.2f), 1f, Offset(65.28f, 42.72f)) {
                        drawPath(P.card, paper); drawPath(P.card, ink, style = Stroke(1.2f))
                        if (cv > 0.5f) drawPath(P.heart, red)
                    }
                }
            }
            // letter pops
            val lv = letter.value
            if (lv > 0.01f) scale(lv, lv, Offset(42.72f, 65.28f)) { drawPath(P.letterA, ink) }
        }
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.withTransformLeft(k: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    scale(k, k, Offset.Zero) { translate(-31.44f, -31.44f) { block() } }
}
