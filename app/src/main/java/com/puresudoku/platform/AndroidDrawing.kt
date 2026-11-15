// Platform layer (Android): see AndroidPlatform.kt.
package com.puresudoku.ui.games.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas

/** Draws text centred at [c] using the platform canvas (for CJK glyphs on pieces). */
fun DrawScope.glyph(text: String, c: Offset, sizePx: Float, color: Color, bold: Boolean = true) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; textSize = sizePx
        this.color = android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }
    val fm = paint.fontMetrics
    drawContext.canvas.nativeCanvas.drawText(text, c.x, c.y - (fm.ascent + fm.descent) / 2, paint)
}
