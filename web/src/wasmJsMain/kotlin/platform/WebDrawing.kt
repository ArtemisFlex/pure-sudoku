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

/** Poppins bold, then the bundled fallbacks (symbols, CJK piece names...). */
private val faces: List<Typeface> by lazy {
    com.puresudoku.ui.theme.canvasFontBytes.mapNotNull { runCatching { FontMgr.default.makeFromData(Data.makeFromBytes(it)) }.getOrNull() }
}

/** The first face that has every character of [text], or the first face. */
private fun faceFor(text: String): Typeface? {
    val all = faces
    return all.firstOrNull { f -> f.getUTF32Glyphs(text.codePointsList()).none { it.toInt() == 0 } } ?: all.firstOrNull()
}

private fun String.codePointsList(): IntArray {
    val out = ArrayList<Int>()
    var i = 0
    while (i < length) {
        val c = this[i]
        if (c.isHighSurrogate() && i + 1 < length) { out += ((c.code - 0xD800) shl 10) + (this[i + 1].code - 0xDC00) + 0x10000; i += 2 }
        else { out += c.code; i++ }
    }
    return out.toIntArray()
}

/** Draws text centred at [c] straight onto the canvas, with the bundled fonts (the browser has none of its own). */
fun DrawScope.glyph(text: String, c: Offset, sizePx: Float, color: Color, bold: Boolean = true) {
    val face = faceFor(text) ?: return
    val font = Font(face, sizePx)
    val line = TextLine.make(text, font)
    val m = font.metrics
    val paint = Paint().apply { this.color = color.toArgb(); isAntiAlias = true }
    drawContext.canvas.nativeCanvas.drawTextLine(line, c.x - line.width / 2, c.y - (m.ascent + m.descent) / 2, paint)
}
