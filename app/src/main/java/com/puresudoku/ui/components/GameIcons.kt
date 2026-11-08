package com.puresudoku.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.GameKind
import com.puresudoku.ui.theme.BoardColors
import com.puresudoku.ui.theme.LocalBoardColors
import com.puresudoku.ui.theme.Poppins

/**
 * Game icons in the style of the Pure Puzzles logo: a rounded tile, ink frame,
 * one strong blue accent and one pale blue accent.
 */
@Composable
fun GameIcon(kind: GameKind, size: Dp, modifier: Modifier = Modifier) {
    val colors = LocalBoardColors.current
    val tm = rememberTextMeasurer()
    Canvas(modifier.size(size)) {
        val s = this.size.width
        val frame = s * 0.06f
        val r = s * 0.19f
        drawRoundRect(colors.boardBg, cornerRadius = CornerRadius(r))
        val inner = s - frame * 2
        val o = frame
        when (kind) {
            GameKind.SUDOKU -> Unit
            GameKind.CAGE_MATH -> cage(colors, tm, o, inner)
            GameKind.KAKURO -> kakuro(colors, tm, o, inner)
            GameKind.PICTURE_LOGIC -> picture(colors, o, inner)
            GameKind.HASHI -> hashi(colors, tm, o, inner)
            GameKind.GRID_RECALL -> recall(colors, tm, o, inner)
            GameKind.CODE_BREAKER -> code(colors, o, inner)
            GameKind.LUMEN -> lumen(colors, o, inner)
            GameKind.SLIDING_TILES -> sliding(colors, tm, o, inner)
        }
        drawRoundRect(
            colors.thickLine, Offset(frame / 2, frame / 2), Size(s - frame, s - frame),
            CornerRadius(r - frame / 2), style = Stroke(frame),
        )
    }
}

private fun DrawScope.gridLines(c: BoardColors, o: Float, inner: Float, n: Int, w: Float = inner * 0.022f) {
    val cell = inner / n
    for (k in 1 until n) {
        drawLine(c.thickLine, Offset(o + k * cell, o), Offset(o + k * cell, o + inner), w)
        drawLine(c.thickLine, Offset(o, o + k * cell), Offset(o + inner, o + k * cell), w)
    }
}

private fun DrawScope.label(tm: TextMeasurer, text: String, x: Float, y: Float, px: Float, color: Color, center: Boolean = false) {
    val style = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = (px / density / fontScale).sp, color = color)
    val layout = tm.measure(text, style)
    val tl = if (center) Offset(x - layout.size.width / 2f, y - layout.size.height / 2f) else Offset(x, y)
    drawText(layout, topLeft = tl)
}

private fun DrawScope.cell(o: Float, cell: Float, r: Int, c: Int, color: Color) =
    drawRect(color, Offset(o + c * cell, o + r * cell), Size(cell, cell))

private fun DrawScope.cage(c: BoardColors, tm: TextMeasurer, o: Float, inner: Float) {
    val cell = inner / 3
    cell(o, cell, 0, 0, c.entered); cell(o, cell, 1, 0, c.entered); cell(o, cell, 1, 1, c.entered)
    cell(o, cell, 2, 2, c.sameDigit)
    gridLines(c, o, inner, 3, inner * 0.012f)
    // thick cage outline around the L shape
    val t = inner * 0.05f
    val p = listOf(
        Offset(o, o), Offset(o + cell, o), Offset(o + cell, o + cell), Offset(o + 2 * cell, o + cell),
        Offset(o + 2 * cell, o + 2 * cell), Offset(o, o + 2 * cell), Offset(o, o),
    )
    for (k in 0 until p.size - 1) drawLine(c.thickLine, p[k], p[k + 1], t, StrokeCap.Square)
    label(tm, "6+", o + cell * 0.12f, o + cell * 0.06f, cell * 0.42f, c.boardBg)
    label(tm, "3", o + cell * 2.5f, o + cell * 2.5f, cell * 0.62f, c.given, center = true)
}

private fun DrawScope.kakuro(c: BoardColors, tm: TextMeasurer, o: Float, inner: Float) {
    val cell = inner / 3
    for ((r, col) in listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0, 2 to 0)) cell(o, cell, r, col, c.thickLine)
    // diagonals in clue cells
    for ((r, col) in listOf(0 to 1, 0 to 2, 1 to 0, 2 to 0)) {
        drawLine(c.boardBg.copy(alpha = 0.5f), Offset(o + col * cell, o + r * cell), Offset(o + (col + 1) * cell, o + (r + 1) * cell), inner * 0.012f)
    }
    cell(o, cell, 1, 1, c.entered)
    cell(o, cell, 2, 2, c.sameDigit)
    gridLines(c, o, inner, 3, inner * 0.012f)
    label(tm, "9", o + cell * 1.5f, o + cell * 1.5f, cell * 0.62f, c.boardBg, center = true)
    label(tm, "4", o + cell * 2.5f, o + cell * 2.5f, cell * 0.62f, c.given, center = true)
}

private fun DrawScope.picture(c: BoardColors, o: Float, inner: Float) {
    val n = 5
    val cell = inner / n
    val heart = listOf("01010", "11111", "11111", "01110", "00100")
    for (r in 0 until n) for (col in 0 until n) if (heart[r][col] == '1') cell(o, cell, r, col, if (r == 1 && col == 1) c.sameDigit else c.entered)
    gridLines(c, o, inner, n, inner * 0.012f)
}

private fun DrawScope.hashi(c: BoardColors, tm: TextMeasurer, o: Float, inner: Float) {
    val pts = listOf(Offset(0.22f, 0.24f), Offset(0.78f, 0.24f), Offset(0.22f, 0.78f), Offset(0.78f, 0.78f))
    fun p(k: Int) = Offset(o + pts[k].x * inner, o + pts[k].y * inner)
    val w = inner * 0.035f
    drawLine(c.thickLine, p(0), p(1), w)
    val off = Offset(inner * 0.045f, 0f)
    drawLine(c.thickLine, p(0) - off, p(2) - off, w); drawLine(c.thickLine, p(0) + off, p(2) + off, w)
    drawLine(c.thickLine, p(2), p(3), w)
    val nums = listOf("3", "1", "3", "1")
    for (k in 0..3) {
        val fill = when (k) { 0 -> c.entered; 3 -> c.sameDigit; else -> c.boardBg }
        drawCircle(fill, inner * 0.15f, p(k))
        drawCircle(c.thickLine, inner * 0.15f, p(k), style = Stroke(inner * 0.035f))
        label(tm, nums[k], p(k).x, p(k).y, inner * 0.17f, if (k == 0) c.boardBg else c.given, center = true)
    }
}

private fun DrawScope.recall(c: BoardColors, tm: TextMeasurer, o: Float, inner: Float) {
    val cell = inner / 3
    cell(o, cell, 0, 2, c.entered); cell(o, cell, 1, 0, c.sameDigit); cell(o, cell, 2, 1, c.sameDigit)
    gridLines(c, o, inner, 3)
    label(tm, "7", o + cell * 2.5f, o + cell * 0.5f, cell * 0.62f, c.boardBg, center = true)
    label(tm, "?", o + cell * 0.5f, o + cell * 1.5f, cell * 0.62f, c.given, center = true)
    label(tm, "?", o + cell * 1.5f, o + cell * 2.5f, cell * 0.62f, c.given, center = true)
}

private fun DrawScope.code(c: BoardColors, o: Float, inner: Float) {
    val rows = 3
    val rowH = inner / rows
    val pegColors = listOf(c.entered, c.sameDigit, c.thickLine, c.wave)
    val seq = listOf(listOf(0, 1, 2, 3), listOf(1, 0, 3, 2), listOf(0, 0, 1, 3))
    for (r in 0 until rows) {
        val cy = o + rowH * (r + 0.5f)
        for (k in 0 until 3) drawCircle(pegColors[seq[r][k]], inner * 0.085f, Offset(o + inner * (0.16f + k * 0.22f), cy))
        for (k in 0 until 2) drawCircle(if (k <= r) c.thickLine else c.thinLine, inner * 0.04f, Offset(o + inner * (0.8f + (k % 2) * 0.1f), cy))
        if (r < rows - 1) drawLine(c.thinLine, Offset(o, o + rowH * (r + 1)), Offset(o + inner, o + rowH * (r + 1)), inner * 0.012f)
    }
}

private fun DrawScope.lumen(c: BoardColors, o: Float, inner: Float) {
    val cell = inner / 3
    for (r in 0..2) for (col in 0..2) {
        val lit = (r == 1) || (col == 1)
        val pad = cell * 0.12f
        drawRoundRect(
            if (r == 1 && col == 1) c.entered else if (lit) c.wave else c.related,
            Offset(o + col * cell + pad, o + r * cell + pad), Size(cell - pad * 2, cell - pad * 2), CornerRadius(cell * 0.18f),
        )
    }
}

private fun DrawScope.sliding(c: BoardColors, tm: TextMeasurer, o: Float, inner: Float) {
    val cell = inner / 3
    var k = 1
    for (r in 0..2) for (col in 0..2) {
        if (r == 2 && col == 2) continue
        val pad = cell * 0.08f
        val strong = k == 5
        drawRoundRect(
            if (strong) c.entered else if (k == 8) c.sameDigit else c.related,
            Offset(o + col * cell + pad, o + r * cell + pad), Size(cell - pad * 2, cell - pad * 2), CornerRadius(cell * 0.16f),
        )
        label(tm, "$k", o + (col + 0.5f) * cell, o + (r + 0.5f) * cell, cell * 0.5f, if (strong) c.boardBg else c.given, center = true)
        k++
    }
}
