package com.puresudoku.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Grid
import com.puresudoku.ui.ErrorFx
import com.puresudoku.ui.GameViewModel
import com.puresudoku.ui.Phase
import com.puresudoku.ui.PlacedFx
import com.puresudoku.ui.WaveFx
import com.puresudoku.ui.theme.LocalBoardColors
import com.puresudoku.ui.theme.Poppins
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun SudokuBoard(vm: GameViewModel, modifier: Modifier = Modifier) {
    val colors = LocalBoardColors.current
    @Suppress("UNUSED_VARIABLE") val version = vm.boardVersion // recompose on board changes
    val selected = vm.selected
    val selectedValue = if (selected >= 0) vm.value(selected) else 0
    val dimmed = vm.phase == Phase.LOST
    val dim = remember { Animatable(0f) }
    LaunchedEffect(dimmed) { dim.animateTo(if (dimmed) 1f else 0f, tween(700)) }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.boardBg)
            .drawWithContent {
                drawContent()
                val cell = size.width / 9f
                val thin = 1.dp.toPx()
                val thick = 2.2.dp.toPx()
                for (k in 1..8) {
                    if (k % 3 == 0) continue
                    val p = k * cell
                    drawLine(colors.thinLine, Offset(p, 0f), Offset(p, size.height), thin)
                    drawLine(colors.thinLine, Offset(0f, p), Offset(size.width, p), thin)
                }
                for (k in listOf(3, 6)) {
                    val p = k * cell
                    drawLine(colors.thickLine, Offset(p, 0f), Offset(p, size.height), thick)
                    drawLine(colors.thickLine, Offset(0f, p), Offset(size.width, p), thick)
                }
                drawRoundRect(
                    colors.thickLine, Offset(thick / 2, thick / 2),
                    Size(size.width - thick, size.height - thick),
                    CornerRadius(12.dp.toPx()), style = Stroke(thick),
                )
                if (dim.value > 0f) drawRect(colors.boardBg.copy(alpha = 0.55f * dim.value))
            },
    ) {
        val cellSize = maxWidth / 9
        Column(Modifier.fillMaxSize()) {
            for (r in 0 until 9) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (c in 0 until 9) {
                        val i = r * 9 + c
                        val related = selected >= 0 && i != selected && Grid.sees(i, selected)
                        val v = vm.value(i)
                        Cell(
                            index = i,
                            value = v,
                            notes = vm.notes(i),
                            given = vm.isGiven(i),
                            isSelected = i == selected,
                            isRelated = related,
                            isSameDigit = selectedValue != 0 && v == selectedValue && i != selected,
                            highlightNote = selectedValue,
                            size = cellSize,
                            placedFx = vm.placedFx,
                            waveFx = vm.waveFx,
                            errorFx = vm.errorFx,
                            onClick = { vm.select(i) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        FloatingPoints(vm.placedFx, cellSize)
    }
}

@Composable
private fun Cell(
    index: Int,
    value: Int,
    notes: Int,
    given: Boolean,
    isSelected: Boolean,
    isRelated: Boolean,
    isSameDigit: Boolean,
    highlightNote: Int,
    size: Dp,
    placedFx: PlacedFx?,
    waveFx: WaveFx?,
    errorFx: ErrorFx?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBoardColors.current
    val row = index / 9
    val col = index % 9

    // Board build-in: cells cascade in diagonally the first time the board appears.
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(((row + col) * 22).toLong())
        intro.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
    }

    // Pop when a number lands here.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(placedFx?.id) {
        if (placedFx != null && placedFx.cell == index) {
            pop.snapTo(0.3f)
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        }
    }

    // Completion ripple travelling outward from the last placed cell.
    val wave = remember { Animatable(0f) }
    LaunchedEffect(waveFx?.id) {
        if (waveFx != null && index in waveFx.cells) {
            val o = waveFx.origin
            val dist = max(abs(o / 9 - row), abs(o % 9 - col))
            kotlinx.coroutines.delay(dist * 45L)
            wave.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
            wave.animateTo(0f, tween(420, easing = LinearOutSlowInEasing))
        }
    }

    // Wrong guess: flash the digit in red and shake.
    val shake = remember { Animatable(0f) }
    val errorAlpha = remember { Animatable(0f) }
    var errorDigit by remember { mutableIntStateOf(0) }
    LaunchedEffect(errorFx?.id) {
        if (errorFx != null && errorFx.cell == index) {
            errorDigit = errorFx.digit
            errorAlpha.snapTo(1f)
            shake.animateTo(0f, keyframes {
                durationMillis = 380
                -10f at 50; 9f at 110; -7f at 170; 5f at 230; -2f at 300; 0f at 380
            })
            errorAlpha.animateTo(0f, tween(450))
            errorDigit = 0
        }
    }

    val bg by animateColorAsState(
        when {
            errorAlpha.value > 0f -> colors.errorBg
            isSelected -> colors.selected
            isSameDigit -> colors.sameDigit
            isRelated -> colors.related
            else -> colors.boardBg
        },
        animationSpec = tween(180), label = "cellBg",
    )

    val density = LocalDensity.current
    val digitSize = with(density) { (size * 0.58f).toSp() }
    val noteSize = with(density) { (size * 0.24f).toSp() }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = intro.value.coerceIn(0f, 1f)
                val s = 0.6f + 0.4f * intro.value
                scaleX = s; scaleY = s
            }
            .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            .background(bg)
            .drawWithContent {
                drawContent()
                if (wave.value > 0f) drawRect(colors.wave.copy(alpha = 0.85f * wave.value))
            }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            errorDigit != 0 -> Text(
                errorDigit.toString(),
                color = colors.error.copy(alpha = errorAlpha.value),
                fontSize = digitSize, fontWeight = FontWeight.Medium, fontFamily = Poppins,
            )
            value != 0 -> Text(
                value.toString(),
                color = if (given) colors.given else colors.entered,
                fontSize = digitSize, fontFamily = Poppins,
                fontWeight = if (given) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.graphicsLayer {
                    val s = pop.value * (1f + 0.18f * wave.value)
                    scaleX = s; scaleY = s
                },
            )
            notes != 0 -> NotesGrid(notes, highlightNote, noteSize)
        }
    }
}

@Composable
private fun NotesGrid(notes: Int, highlight: Int, fontSize: androidx.compose.ui.unit.TextUnit) {
    val colors = LocalBoardColors.current
    Column(Modifier.fillMaxSize()) {
        for (r in 0 until 3) Row(Modifier.weight(1f).fillMaxWidth()) {
            for (c in 0 until 3) {
                val d = r * 3 + c + 1
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (notes and (1 shl d) != 0) {
                        Text(
                            d.toString(), fontSize = fontSize, lineHeight = fontSize,
                            color = if (d == highlight) colors.entered else colors.note,
                            fontWeight = if (d == highlight) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** "+25" drifting up from the cell that just scored. */
@Composable
private fun FloatingPoints(fx: PlacedFx?, cellSize: Dp) {
    if (fx == null || fx.points <= 0) return
    val colors = LocalBoardColors.current
    val progress = remember(fx.id) { Animatable(0f) }
    LaunchedEffect(fx.id) { progress.animateTo(1f, tween(900, easing = LinearOutSlowInEasing)) }
    if (progress.value >= 1f) return
    val r = fx.cell / 9
    val c = fx.cell % 9
    Box(
        Modifier
            .offset(x = cellSize * c - cellSize * 0.5f, y = cellSize * r - cellSize * 0.35f)
            .size(cellSize * 2, cellSize)
            .graphicsLayer {
                translationY = -progress.value * cellSize.toPx() * 0.9f
                alpha = 1f - progress.value
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "+${fx.points}", color = colors.entered,
            fontSize = 15.sp, fontWeight = FontWeight.Bold,
        )
    }
}
