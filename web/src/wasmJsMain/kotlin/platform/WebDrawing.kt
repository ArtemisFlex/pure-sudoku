// Platform layer (browser): see WebPlatform.kt.
package com.puresudoku.ui.games.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.skia.Data
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Paint
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.Typeface

private val glyphFace: Typeface? by lazy {
    com.puresudoku.ui.theme.poppinsBoldBytes?.let { FontMgr.default.makeFromData(Data.makeFromBytes(it)) }
}

/**
 * Draws text centred at [c] straight onto the canvas. The browser has no system fonts to fall back
 * on, so this uses the bundled Poppins (Latin letters and digits; CJK piece names need a CJK font).
 */
fun DrawScope.glyph(text: String, c: Offset, sizePx: Float, color: Color, bold: Boolean = true) {
    val face = glyphFace ?: return
    val font = Font(face, sizePx)
    val line = TextLine.make(text, font)
    val m = font.metrics
    val paint = Paint().apply { this.color = color.toArgb(); isAntiAlias = true }
    drawContext.canvas.nativeCanvas.drawTextLine(line, c.x - line.width / 2, c.y - (m.ascent + m.descent) / 2, paint)
}
