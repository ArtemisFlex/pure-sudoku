package com.puresudoku.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/**
 * How screen text should be drawn over one backdrop, worked out from the picture itself:
 * whichever of white or near-black contrasts more with the scene behind the text, plus a
 * shadow in the opposite colour only when enough of the scene is busy enough to fight it.
 *
 * [scene] is the hardest luminance the ink has to read against in the area text sits on: the
 * bright end of the scene for white ink, the dark end for dark ink, so bright patches on a
 * dark scene (a moon, a glow) count rather than being averaged away.
 *
 * [scrim] is how strong a soft wash (in the shadow colour) behind the top bar must be for the
 * ink to hold 3:1 there even over the brightest part of the scene; 0 for most backdrops.
 */
data class BackdropInk(val ink: Color, val shadow: Color?, val scene: Float, val scrim: Float = 0f) {
    /**
     * [c] as is when it already reads on this scene; otherwise the least shift toward [ink]
     * that reaches [MIN_TEXT_CONTRAST], so accent hues survive where they can.
     */
    fun readable(c: Color): Color {
        val solid = c.copy(alpha = 1f)
        // A see-through colour shows partly as the scene behind it.
        val seen = solid.luminance() * c.alpha + scene * (1 - c.alpha)
        if (reads(seen)) return c
        var t = 0.125f
        while (t < 1f) {
            val m = lerp(solid, ink, t)
            if (reads(m.luminance())) return m
            t += 0.125f
        }
        return ink
    }

    /**
     * Text reads when it sits on the ink's side of the scene (lighter on dark scenes, darker on
     * light ones) with enough contrast. Without the side check, navy text on a dark scene would
     * pass against a bright patch it rarely actually sits on.
     */
    private fun reads(l: Float): Boolean {
        val inkL = ink.luminance()
        val sameSide = if (inkL > scene) l > scene else l < scene
        return sameSide && contrast(l, scene) >= MIN_TEXT_CONTRAST
    }

    private fun contrast(a: Float, b: Float) = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)

    companion object { const val MIN_TEXT_CONTRAST = 4.5f }
}

/** The ink chosen for the current backdrop, or null when the screen has none. */
val LocalBackdropInk = androidx.compose.runtime.staticCompositionLocalOf<BackdropInk?> { null }

/** This colour, adjusted to read on the current backdrop when text sits directly on it. */
@Composable @ReadOnlyComposable
fun Color.readable(): Color = LocalBackdropInk.current?.readable(this) ?: this

/** Ink for default-coloured text that sits directly on the backdrop. */
@Composable @ReadOnlyComposable
fun backdropInk(): Color = LocalBackdropInk.current?.ink ?: androidx.compose.material3.LocalContentColor.current

object BackdropInks {
    private val DARK = Color(0xFF15131A)
    private val cache = HashMap<Int, BackdropInk>()

    /** The ink is chosen for the top part of the screen, where the bar and most prompts sit. */
    private const val TEXT_REGION = 0.6f
    /** Below this contrast ratio a pixel is "fighting" the ink. */
    private const val MIN_CONTRAST = 3.0
    /** If more than this share of pixels fight the ink, add a shadow. */
    private const val BUSY_SHARE = 0.08
    /** Share of the screen the top bar covers. */
    private const val TOP_SHARE = 0.25f
    /** Brightest scene white ink holds 3:1 against; darkest scene dark ink does. */
    private const val SCRIM_WHITE_MAX = 0.30
    private const val SCRIM_DARK_MIN = 0.12

    /** [render] draws backdrop [res] cropped to cover a w x h image and returns its ARGB pixels, row by row. */
    fun of(res: Int, aspect: Float, render: (res: Int, w: Int, h: Int) -> IntArray?): BackdropInk = com.puresudoku.platform.locked(cache) {
        cache.getOrPut(res) { analyse(res, aspect, render) }
    }

    private fun analyse(res: Int, aspect: Float, render: (Int, Int, Int) -> IntArray?): BackdropInk {
        val all = sample(res, aspect, render) ?: return BackdropInk(DARK, Color.White, 0.8f)
        // Pixels are row-major, so the top of the screen is the front of the array.
        val lum = all.copyOfRange(0, (all.size * TEXT_REGION).toInt())
        val mean = lum.average()
        // Game text can sit anywhere (buttons and labels near the bottom too), so the worst case
        // that readable() works against is taken over the whole screen.
        val sorted = all.sortedArray()
        // Pick the ink with the better contrast against the average scene.
        val darkL = DARK.luminance().toDouble()
        val white = contrast(1.0, mean) >= contrast(darkL, mean)
        val inkL = if (white) 1.0 else darkL
        val fighting = lum.count { contrast(inkL, it) < MIN_CONTRAST }.toDouble() / lum.size
        val shadow = if (fighting > BUSY_SHARE) (if (white) Color.Black else Color.White) else null
        // The hardest spot the ink must read against: bright end for white ink, dark end for dark ink.
        val worst = if (white) sorted[(sorted.size * 0.9).toInt().coerceAtMost(sorted.lastIndex)] else sorted[(sorted.size * 0.1).toInt()]
        // The top bar (title, stats, hint) covers about the top quarter. Where a bright patch sits
        // behind it (a moon on a dark scene), wash it just enough for 3:1, capped so the art stays.
        val top = all.copyOfRange(0, (all.size * TOP_SHARE).toInt().coerceIn(1, all.size)).sortedArray()
        // 95th percentile: a small moon behind one stat still counts.
        val scrim = if (white) {
            val hot = top[(top.size * 0.95).toInt().coerceAtMost(top.lastIndex)]
            if (hot > SCRIM_WHITE_MAX) (1 - SCRIM_WHITE_MAX / hot).toFloat() else 0f
        } else {
            val dim = top[(top.size * 0.05).toInt()]
            if (dim < SCRIM_DARK_MIN) ((SCRIM_DARK_MIN - dim) / (1 - dim)).toFloat() else 0f
        }
        return BackdropInk(if (white) Color.White else DARK, shadow, worst.toFloat(), scrim.coerceAtMost(0.5f).takeIf { it > 0.05f } ?: 0f)
    }

    /** Relative luminance of each pixel in the text region, from a small render of the backdrop as it is cropped on screen. */
    private fun sample(res: Int, aspect: Float, render: (Int, Int, Int) -> IntArray?): DoubleArray? = runCatching {
        val w = 60; val h = (w / aspect).toInt().coerceIn(60, 200)
        val px = render(res, w, h) ?: return null
        DoubleArray(px.size) { i ->
            val p = px[i]
            0.2126 * lin((p shr 16) and 0xFF) + 0.7152 * lin((p shr 8) and 0xFF) + 0.0722 * lin(p and 0xFF)
        }
    }.getOrNull()

    private fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }
    private fun contrast(a: Double, b: Double) = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

/** An outlined button whose label and border read on the current backdrop; unchanged elsewhere. */
@Composable
fun BackdropOutlinedButton(
    onClick: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val label = scheme.primary.readable()
    androidx.compose.material3.OutlinedButton(
        onClick = onClick, modifier = modifier, enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = label, disabledContentColor = label.copy(alpha = 0.45f),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outline.readable().copy(alpha = if (enabled) 1f else 0.4f)),
        content = content,
    )
}
