package com.puresudoku.ui.components

import androidx.compose.ui.graphics.drawscope.scale

import androidx.compose.ui.graphics.drawscope.translate

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
 * Game icons in the style of the PureGames logo: a rounded tile, ink frame,
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
            GameKind.CROSSWORD -> crossword(colors, tm, o, inner)
            GameKind.WORD_SEARCH -> wordSearch(colors, tm, o, inner)
            GameKind.HAIKU -> haiku(colors, o, inner)
            GameKind.ALLITERATION -> letters(colors, tm, o, inner, listOf("B", "B", "K"), strong = 2)
            GameKind.SYNONYMS -> letters(colors, tm, o, inner, listOf("A", "=", "A"), strong = 1)
            GameKind.RHYME_TIME -> rhyme(colors, tm, o, inner)
            GameKind.WORD_LADDER -> ladder(colors, tm, o, inner)
            GameKind.WORD_WHEEL -> wheel(colors, tm, o, inner)
            GameKind.SOLITAIRE -> { mini(colors, tm, o, inner, 0.08f, 0.10f, "K♥", red = true); mini(colors, tm, o, inner, 0.26f, 0.26f, "Q♠"); mini(colors, tm, o, inner, 0.44f, 0.42f, "J♥", red = true, strong = true) }
            GameKind.BLACKJACK -> { mini(colors, tm, o, inner, 0.1f, 0.18f, "A♠"); mini(colors, tm, o, inner, 0.42f, 0.30f, "K♥", red = true, strong = true) }
            GameKind.GO_FISH -> { mini(colors, tm, o, inner, 0.06f, 0.24f, "7♣"); mini(colors, tm, o, inner, 0.26f, 0.2f, "7♦", red = true); mini(colors, tm, o, inner, 0.46f, 0.24f, "7♠", strong = true) }
            GameKind.CRAZY_EIGHTS -> { mini(colors, tm, o, inner, 0.1f, 0.26f, "5♣"); mini(colors, tm, o, inner, 0.42f, 0.16f, "8♥", strong = true) }
            GameKind.SCOUNDREL -> { mini(colors, tm, o, inner, 0.1f, 0.2f, "9♦", red = true); mini(colors, tm, o, inner, 0.42f, 0.28f, "Q♠", strong = true) }
            GameKind.CUTTLE -> { mini(colors, tm, o, inner, 0.1f, 0.26f, "5♥", red = true); mini(colors, tm, o, inner, 0.4f, 0.16f, "8♠", strong = true) }
            GameKind.PYRAMID -> pyramid(colors, o, inner)
            GameKind.REGICIDE -> mini(colors, tm, o, inner, 0.24f, 0.16f, "K♣", strong = true, big = true)
            GameKind.DUNGEON_SOLITAIRE -> { mini(colors, tm, o, inner, 0.1f, 0.22f, "A♣"); mini(colors, tm, o, inner, 0.4f, 0.22f, "K♦", red = true, strong = true) }
            GameKind.JAILBREAK -> jail(colors, tm, o, inner)
            GameKind.CHESS -> chessIcon(colors, o, inner)
            GameKind.CHECKERS -> checkersIcon(colors, o, inner)
            GameKind.GO -> stonesIcon(colors, o, inner, 4, listOf(5 to 1, 6 to 2, 9 to 2, 10 to 1, 6 to 1))
            GameKind.GOMOKU -> stonesIcon(colors, o, inner, 5, listOf(4 to 3, 8 to 3, 12 to 3, 16 to 3, 20 to 3, 7 to 2, 13 to 2))
            GameKind.SHOGI -> shogiIcon(colors, tm, o, inner)
            GameKind.XIANGQI -> { discIcon(colors, o, inner); label(tm, "帥", o + inner / 2, o + inner / 2, inner * 0.36f, colors.boardBg, center = true) }
            GameKind.BACKGAMMON -> backgammonIcon(colors, o, inner)
            GameKind.MANCALA -> mancalaIcon(colors, o, inner)
            GameKind.MAHJONG -> mahjongIcon(colors, tm, o, inner)
            GameKind.MORRIS -> morrisIcon(colors, o, inner)
            GameKind.TIC_TAC_TOE -> tttIcon(colors, o, inner)
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


private fun DrawScope.crossword(c: BoardColors, tm: TextMeasurer, o: Float, inner: Float) {
    val cell = inner / 3
    cell(o, cell, 0, 2, c.thickLine); cell(o, cell, 2, 0, c.thickLine)
    cell(o, cell, 1, 1, c.entered)
    gridLines(c, o, inner, 3, inner * 0.012f)
    val grid = listOf("CA ", "ORE", " TO")
    for (r in 0..2) for (col in 0..2) {
        val ch = grid[r][col]
        if (ch != ' ') label(tm, "$ch", o + (col + 0.5f) * cell, o + (r + 0.5f) * cell, cell * 0.5f, if (r == 1 && col == 1) c.boardBg else c.given, center = true)
    }
}

private fun DrawScope.wordSearch(c: BoardColors, tm: TextMeasurer, o: Float, inner: Float) {
    val cell = inner / 3
    drawRoundRect(c.sameDigit, Offset(o + cell * 0.12f, o + cell * 1.14f), Size(inner - cell * 0.24f, cell * 0.72f), CornerRadius(cell * 0.36f))
    val grid = listOf("QZE", "OWL", "JRK")
    for (r in 0..2) for (col in 0..2) {
        label(tm, "${grid[r][col]}", o + (col + 0.5f) * cell, o + (r + 0.5f) * cell, cell * 0.48f, if (r == 1) c.given else c.note, center = true)
    }
}

private fun DrawScope.haiku(c: BoardColors, o: Float, inner: Float) {
    val counts = intArrayOf(5, 7, 5)
    for (r in 0..2) {
        val y = o + inner * (0.25f + r * 0.25f)
        val gap = inner / 8.5f
        val start = o + (inner - gap * (counts[r] - 1)) / 2
        for (k in 0 until counts[r]) drawCircle(if (r == 1) c.entered else c.thickLine, inner * 0.045f, Offset(start + k * gap, y))
    }
}

private fun DrawScope.letters(c: BoardColors, tm: TextMeasurer, o: Float, inner: Float, ls: List<String>, strong: Int) {
    val cell = inner / 3
    for ((k, l) in ls.withIndex()) {
        val pad = cell * 0.1f
        drawRoundRect(if (k == strong) c.entered else c.related, Offset(o + k * cell + pad, o + cell + pad), Size(cell - pad * 2, cell - pad * 2), CornerRadius(cell * 0.16f))
        label(tm, l, o + (k + 0.5f) * cell, o + 1.5f * cell, cell * 0.5f, if (k == strong) c.boardBg else c.given, center = true)
    }
}

private fun DrawScope.rhyme(c: BoardColors, tm: TextMeasurer, o: Float, inner: Float) {
    val cell = inner / 3
    for ((r, w) in listOf("CAT", "HAT").withIndex()) for (k in 0..2) {
        val pad = cell * 0.1f
        val y = o + cell * (0.5f + r)
        drawRoundRect(if (k > 0) (if (r == 0) c.entered else c.sameDigit) else c.related, Offset(o + k * cell + pad, y + pad), Size(cell - pad * 2, cell - pad * 2), CornerRadius(cell * 0.16f))
        label(tm, "${w[k]}", o + (k + 0.5f) * cell, y + cell / 2, cell * 0.48f, if (r == 0 && k > 0) c.boardBg else c.given, center = true)
    }
}

private fun DrawScope.ladder(c: BoardColors, tm: TextMeasurer, o: Float, inner: Float) {
    val cell = inner / 3
    val words = listOf("CAT", "COT", "DOT")
    for ((r, w) in words.withIndex()) for (k in 0..2) {
        val changed = (r == 1 && k == 1) || (r == 2 && k == 0)
        if (changed) cell(o, cell, r, k, if (r == 1) c.entered else c.sameDigit)
        label(tm, "${w[k]}", o + (k + 0.5f) * cell, o + (r + 0.5f) * cell, cell * 0.5f, if (r == 1 && k == 1) c.boardBg else c.given, center = true)
    }
    gridLines(c, o, inner, 3, inner * 0.012f)
}

private fun DrawScope.wheel(c: BoardColors, tm: TextMeasurer, o: Float, inner: Float) {
    val cx = o + inner / 2; val cy = o + inner / 2
    val rad = inner * 0.3f
    val letters = "PLANETS"
    for (k in 0 until 6) {
        val a = Math.toRadians(60.0 * k - 90).toFloat()
        val p = Offset(cx + rad * kotlin.math.cos(a), cy + rad * kotlin.math.sin(a))
        drawCircle(c.related, inner * 0.13f, p)
        label(tm, "${letters[k + 1]}", p.x, p.y, inner * 0.14f, c.given, center = true)
    }
    drawCircle(c.entered, inner * 0.15f, Offset(cx, cy))
    label(tm, "A", cx, cy, inner * 0.16f, c.boardBg, center = true)
}


/** A small playing card inside an icon; x/y are fractions of the inner area. */
private fun DrawScope.mini(c: BoardColors, tm: TextMeasurer, o: Float, inner: Float, x: Float, y: Float, text: String,
                           red: Boolean = false, strong: Boolean = false, big: Boolean = false) {
    val w = inner * (if (big) 0.52f else 0.42f); val h = w * 1.4f
    val tl = Offset(o + inner * x, o + inner * y)
    drawRoundRect(if (strong) c.entered else c.boardBg, tl, Size(w, h), CornerRadius(w * 0.14f))
    drawRoundRect(c.thickLine, tl, Size(w, h), CornerRadius(w * 0.14f), style = Stroke(inner * 0.025f))
    val col = if (strong) c.boardBg else if (red) Color(0xFFD03A3A) else c.given
    label(tm, text, tl.x + w / 2, tl.y + h / 2, w * 0.36f, col, center = true)
}

private fun DrawScope.pyramid(c: BoardColors, o: Float, inner: Float) {
    val w = inner * 0.19f; val h = w * 1.3f
    for (r in 0..2) for (k in 0..r) {
        val x = o + inner / 2 - (r + 1) * w * 0.55f + k * w * 1.1f
        val y = o + inner * 0.12f + r * h * 0.85f
        drawRoundRect(if (r == 0) c.entered else if (r == 2 && k == 1) c.sameDigit else c.boardBg, Offset(x, y), Size(w, h), CornerRadius(w * 0.15f))
        drawRoundRect(c.thickLine, Offset(x, y), Size(w, h), CornerRadius(w * 0.15f), style = Stroke(inner * 0.02f))
    }
}

private fun DrawScope.jail(c: BoardColors, tm: TextMeasurer, o: Float, inner: Float) {
    val cell = inner / 3
    for (r in 0..2) for (k in 0..2) {
        if (r == 1 && k == 1) continue
        val pad = cell * 0.14f
        drawRoundRect(if ((r + k) % 2 == 1) c.related else c.thinLine, Offset(o + k * cell + pad, o + r * cell + pad), Size(cell - pad * 2, cell - pad * 2), CornerRadius(cell * 0.14f))
    }
    val pad = cell * 0.06f
    drawRoundRect(c.entered, Offset(o + cell + pad, o + cell + pad), Size(cell - pad * 2, cell - pad * 2), CornerRadius(cell * 0.16f))
    label(tm, "★", o + cell * 1.5f, o + cell * 1.5f, cell * 0.55f, c.boardBg, center = true)
}


// ---------------------------------------------------------------- board games

private fun DrawScope.discIcon(c: BoardColors, o: Float, inner: Float) {
    drawCircle(c.entered, inner * 0.34f, Offset(o + inner / 2, o + inner / 2))
    drawCircle(c.boardBg, inner * 0.28f, Offset(o + inner / 2, o + inner / 2), style = Stroke(inner * 0.025f))
}

private fun DrawScope.chessIcon(c: BoardColors, o: Float, inner: Float) {
    val cell = inner / 2
    cell(o, cell, 0, 1, c.sameDigit); cell(o, cell, 1, 0, c.sameDigit)
    val sc = inner * 0.0078f
    val path = com.puresudoku.ui.games.board.ChessArt.knight
    translate(o + inner * 0.11f, o + inner * 0.08f) {
        scale(sc, sc, Offset.Zero) {
            drawPath(path, c.entered)
            drawPath(path, c.thickLine, style = Stroke(5f))
        }
    }
}

private fun DrawScope.checkersIcon(c: BoardColors, o: Float, inner: Float) {
    val cell = inner / 3
    for (r in 0..2) for (k in 0..2) if ((r + k) % 2 == 1) cell(o, cell, r, k, c.sameDigit)
    for ((r, k, strong) in listOf(Triple(0, 1, false), Triple(1, 0, true), Triple(2, 1, true), Triple(1, 2, false))) {
        val ctr = Offset(o + (k + 0.5f) * cell, o + (r + 0.5f) * cell)
        drawCircle(if (strong) c.entered else c.given, cell * 0.36f, ctr)
        drawCircle(c.boardBg, cell * 0.22f, ctr, style = Stroke(cell * 0.06f))
    }
}

private fun DrawScope.stonesIcon(c: BoardColors, o: Float, inner: Float, n: Int, stones: List<Pair<Int, Int>>) {
    val step = inner / (n + 1)
    for (k in 1..n) {
        drawLine(c.thickLine, Offset(o + step, o + k * step), Offset(o + n * step, o + k * step), inner * 0.018f)
        drawLine(c.thickLine, Offset(o + k * step, o + step), Offset(o + k * step, o + n * step), inner * 0.018f)
    }
    for ((i, kind) in stones) {
        val ctr = Offset(o + (i % n + 1) * step, o + (i / n + 1) * step)
        when (kind) {
            1 -> drawCircle(c.given, step * 0.42f, ctr)
            2 -> { drawCircle(c.boardBg, step * 0.42f, ctr); drawCircle(c.thickLine, step * 0.42f, ctr, style = Stroke(inner * 0.02f)) }
            else -> drawCircle(c.entered, step * 0.42f, ctr)
        }
    }
}

private fun DrawScope.shogiIcon(c: BoardColors, tm: TextMeasurer, o: Float, inner: Float) {
    val cx = o + inner / 2; val w = inner * 0.56f; val h = inner * 0.68f; val top = o + inner * 0.14f
    val p = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx, top); lineTo(cx + w * 0.38f, top + h * 0.18f); lineTo(cx + w / 2, top + h); lineTo(cx - w / 2, top + h); lineTo(cx - w * 0.38f, top + h * 0.18f); close()
    }
    drawPath(p, c.entered); drawPath(p, c.thickLine, style = Stroke(inner * 0.025f))
    label(tm, "王", cx, top + h * 0.6f, inner * 0.3f, c.boardBg, center = true)
}

private fun DrawScope.backgammonIcon(c: BoardColors, o: Float, inner: Float) {
    val w = inner / 5
    for (k in 0..4) {
        val x = o + k * w
        val tri = androidx.compose.ui.graphics.Path().apply { moveTo(x + w * 0.08f, o + inner * 0.06f); lineTo(x + w * 0.92f, o + inner * 0.06f); lineTo(x + w / 2, o + inner * 0.52f); close() }
        drawPath(tri, if (k % 2 == 0) c.sameDigit else c.related)
    }
    for ((k, n) in listOf(0 to 2, 2 to 1, 4 to 3)) for (j in 0 until n) {
        drawCircle(if (k == 2) c.entered else c.given, w * 0.4f, Offset(o + (k + 0.5f) * w, o + inner * 0.94f - w * 0.45f - j * w * 0.78f))
    }
}

private fun DrawScope.mancalaIcon(c: BoardColors, o: Float, inner: Float) {
    for (r in 0..1) for (k in 0..2) {
        val ctr = Offset(o + inner * (0.2f + k * 0.3f), o + inner * (0.32f + r * 0.36f))
        drawOval(c.sameDigit, ctr - Offset(inner * 0.12f, inner * 0.13f), Size(inner * 0.24f, inner * 0.26f))
        for (j in 0 until (r * 3 + k) % 4 + 1) drawCircle(if (r == 1 && k == 1) c.entered else c.given, inner * 0.035f, ctr + Offset((j % 2 - 0.5f) * inner * 0.08f, (j / 2 - 0.5f) * inner * 0.08f))
    }
}

private fun DrawScope.mahjongIcon(c: BoardColors, tm: TextMeasurer, o: Float, inner: Float) {
    val w = inner * 0.44f; val h = w * 1.3f
    for ((k, strong) in listOf(0 to false, 1 to true)) {
        val tl = Offset(o + inner * (0.14f + k * 0.28f), o + inner * (0.22f - k * 0.08f))
        drawRoundRect(c.thickLine, tl + Offset(inner * 0.03f, inner * 0.04f), Size(w, h), CornerRadius(w * 0.16f))
        drawRoundRect(if (strong) c.entered else c.boardBg, tl, Size(w, h), CornerRadius(w * 0.16f))
        drawRoundRect(c.thickLine, tl, Size(w, h), CornerRadius(w * 0.16f), style = Stroke(inner * 0.022f))
        label(tm, if (strong) "中" else "東", tl.x + w / 2, tl.y + h / 2, w * 0.5f, if (strong) c.boardBg else c.given, center = true)
    }
}

private fun DrawScope.morrisIcon(c: BoardColors, o: Float, inner: Float) {
    for (k in 0..2) {
        val m = inner * (0.12f + k * 0.13f)
        drawRect(c.thickLine, Offset(o + m, o + m), Size(inner - 2 * m, inner - 2 * m), style = Stroke(inner * 0.02f))
    }
    drawLine(c.thickLine, Offset(o + inner / 2, o + inner * 0.12f), Offset(o + inner / 2, o + inner * 0.38f), inner * 0.02f)
    drawLine(c.thickLine, Offset(o + inner / 2, o + inner * 0.62f), Offset(o + inner / 2, o + inner * 0.88f), inner * 0.02f)
    for ((x, y, strong) in listOf(Triple(0.12f, 0.12f, true), Triple(0.5f, 0.12f, true), Triple(0.88f, 0.12f, true), Triple(0.38f, 0.62f, false), Triple(0.62f, 0.38f, false))) {
        drawCircle(if (strong) c.entered else c.given, inner * 0.075f, Offset(o + inner * x, o + inner * y))
    }
}

private fun DrawScope.tttIcon(c: BoardColors, o: Float, inner: Float) {
    gridLines(c, o, inner, 3)
    val cell = inner / 3
    fun x(r: Int, k: Int, col: Color) {
        val a = Offset(o + k * cell + cell * 0.25f, o + r * cell + cell * 0.25f); val b = Offset(o + (k + 1) * cell - cell * 0.25f, o + (r + 1) * cell - cell * 0.25f)
        drawLine(col, a, b, cell * 0.12f, StrokeCap.Round); drawLine(col, Offset(b.x, a.y), Offset(a.x, b.y), cell * 0.12f, StrokeCap.Round)
    }
    x(0, 0, c.entered); x(1, 1, c.entered); x(2, 2, c.entered)
    drawCircle(c.given, cell * 0.26f, Offset(o + 2.5f * cell, o + 0.5f * cell), style = Stroke(cell * 0.1f))
    drawCircle(c.given, cell * 0.26f, Offset(o + 0.5f * cell, o + 2.5f * cell), style = Stroke(cell * 0.1f))
}
