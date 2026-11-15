package com.puresudoku.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

/**
 * The browser's way to draw a backdrop into pixels for the shared contrast check (BackdropInks):
 * the same cover-crop the screen uses, at a small size. The image must already be loaded, so the
 * play screen passes the loaded bitmap or painter in.
 */
fun renderBackdrop(bitmap: ImageBitmap?, painter: Painter?, w: Int, h: Int): IntArray? {
    val out = ImageBitmap(w, h)
    val canvas = Canvas(out)
    if (bitmap != null) {
        val s = maxOf(w.toFloat() / bitmap.width, h.toFloat() / bitmap.height)
        val dw = (bitmap.width * s).toInt(); val dh = (bitmap.height * s).toInt()
        canvas.drawImageRect(
            bitmap, IntOffset.Zero, IntSize(bitmap.width, bitmap.height),
            IntOffset((w - dw) / 2, (h - dh) / 2), IntSize(dw, dh), androidx.compose.ui.graphics.Paint(),
        )
    } else if (painter != null) {
        val iw = painter.intrinsicSize.width.takeIf { it.isFinite() && it > 0 } ?: w.toFloat()
        val ih = painter.intrinsicSize.height.takeIf { it.isFinite() && it > 0 } ?: h.toFloat()
        val s = maxOf(w / iw, h / ih)
        val dw = iw * s; val dh = ih * s
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
            drawContext.transform.translate((w - dw) / 2, (h - dh) / 2)
            with(painter) { draw(Size(dw, dh)) }
        }
    } else return null
    val px = IntArray(w * h)
    out.readPixels(px)
    return px
}

/** The loaded art for backdrop [res], as a bitmap or a vector painter. */
@Composable
fun backdropArt(res: Int): Pair<ImageBitmap?, Painter?> =
    if (drawableIsBitmap(res)) ImageBitmap.imageResource(res) to null else null to painterResource(res)
