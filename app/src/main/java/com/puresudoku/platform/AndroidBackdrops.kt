// Platform layer (Android): backdrop art for the shared play screen.
package com.puresudoku.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** True when drawable [res] is a bitmap (pixel art), false for vector art. */
@Composable
fun isBitmapDrawable(res: Int): Boolean {
    val ctx = LocalContext.current
    return remember(res) {
        val v = android.util.TypedValue(); ctx.resources.getValue(res, v, true); v.string?.endsWith(".png") == true
    }
}

/** Screen width divided by height. */
@Composable
fun screenAspect(): Float {
    val cfg = LocalConfiguration.current
    return cfg.screenWidthDp.toFloat() / cfg.screenHeightDp.coerceAtLeast(1)
}

/**
 * Draws backdrop [res] into pixels for the readable-text check, or null while its art isn't ready
 * (on Android it always is). The returned function draws res cropped to cover w x h and returns ARGB.
 */
@Composable
fun rememberBackdropRenderer(res: Int): ((Int, Int, Int) -> IntArray?)? {
    val ctx = LocalContext.current
    return remember(ctx) { { r: Int, w: Int, h: Int -> renderBackdrop(ctx, r, w, h) } }
}

private fun renderBackdrop(ctx: android.content.Context, res: Int, w: Int, h: Int): IntArray? {
    val d = androidx.core.content.ContextCompat.getDrawable(ctx, res) ?: return null
    val iw = d.intrinsicWidth.takeIf { it > 0 } ?: w
    val ih = d.intrinsicHeight.takeIf { it > 0 } ?: h
    val s = maxOf(w.toFloat() / iw, h.toFloat() / ih)
    val dw = (iw * s).toInt(); val dh = (ih * s).toInt()
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh)
    d.draw(android.graphics.Canvas(bmp))
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    bmp.recycle()
    return px
}
