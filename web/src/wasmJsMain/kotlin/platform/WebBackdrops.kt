// Platform layer (browser): backdrop art for the shared play screen.
package com.puresudoku.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo
import com.puresudoku.web.backdropArt
import com.puresudoku.web.drawableIsBitmap
import com.puresudoku.web.renderBackdrop

/** True when drawable [res] is a bitmap (pixel art), false for vector art. */
@Composable
fun isBitmapDrawable(res: Int): Boolean = drawableIsBitmap(res)

/** Page width divided by height. */
@Composable
fun screenAspect(): Float {
    val s = LocalWindowInfo.current.containerSize
    return s.width.toFloat() / s.height.coerceAtLeast(1)
}

/**
 * Draws backdrop [res] into pixels for the readable-text check. Art loads asynchronously in the
 * browser, so this is null until the picture has arrived.
 */
@Composable
fun rememberBackdropRenderer(res: Int): ((Int, Int, Int) -> IntArray?)? {
    val (bitmap, painter) = backdropArt(res)
    val ready = (bitmap?.width ?: 0) > 1 || (painter?.intrinsicSize?.width?.let { it.isFinite() && it > 1f } == true)
    if (!ready) return null
    return remember(res, bitmap, painter) { { _: Int, w: Int, h: Int -> renderBackdrop(bitmap, painter, w, h) } }
}
