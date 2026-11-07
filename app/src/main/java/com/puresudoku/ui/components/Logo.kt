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

/** "PureSudoku" wordmark: "Pure" in accent blue, "Sudoku" in ink, as in the logo. */
@Composable
fun Wordmark(fontSize: TextUnit, modifier: Modifier = Modifier) {
    val colors = LocalBoardColors.current
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.entered)) { append("Pure") }
            withStyle(SpanStyle(color = colors.given)) { append("Sudoku") }
        },
        fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = fontSize,
        modifier = modifier,
    )
}
