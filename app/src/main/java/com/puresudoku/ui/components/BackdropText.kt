package com.puresudoku.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

// Text over a game's backdrop, shared by the Android and browser play screens. Each platform
// supplies only how to draw a backdrop into pixels (render), which BackdropInks measures.

/**
 * Over a backdrop, game text keeps its own colours with a soft halo (or, on a busy scene, the
 * measured shadow), and [BackdropInk] is provided so text that sits directly on the scene can
 * call [com.puresudoku.ui.components.readable].
 */
@Composable
fun ShadowedText(backdrop: Int, aspect: Float, render: (res: Int, w: Int, h: Int) -> IntArray?, content: @Composable () -> Unit) {
    if (backdrop == 0) { content(); return }
    // A backdrop is fixed art that looks the same in light and dark mode, so the play area over
    // it uses one palette too: the light one, whose white panels and dark text read on any scene
    // once on-scene text goes through readable().
    com.puresudoku.ui.theme.PureSudokuTheme(dark = false) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            SceneText(backdrop, aspect, render, content)
        }
    }
}

@Composable
private fun SceneText(backdrop: Int, aspect: Float, render: (Int, Int, Int) -> IntArray?, content: @Composable () -> Unit) {
    val spec = remember(backdrop, aspect) { BackdropInks.of(backdrop, aspect, render) }
    // A soft halo on the far side of the ink: dark round white text, light round dark text.
    val opposite = if (spec.ink.luminance() > 0.5f) Color.Black else Color.White
    val halo = spec.shadow?.let { busyShadow(it) }
        ?: androidx.compose.ui.graphics.Shadow(opposite.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(0f, 1f), 8f)
    WithShadow(halo, MaterialTheme.colorScheme) {
        androidx.compose.runtime.CompositionLocalProvider(LocalBackdropInk provides spec) { content() }
    }
}

/** The top bar, stats and hint sit straight on the backdrop: they take its measured ink, and a shadow only when the scene is busy. */
@Composable
fun BackdropChrome(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) return content()
    val spec = LocalBackdropInk.current ?: return content()
    val scheme = MaterialTheme.colorScheme.copy(onBackground = spec.ink, onSurfaceVariant = spec.ink.copy(alpha = 0.85f))
    WithShadow(spec.shadow?.let { busyShadow(it) } ?: androidx.compose.ui.graphics.Shadow.None, scheme) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides spec.ink) {
            if (spec.scrim <= 0f) content() else {
                // Wash behind the bar, reaching up under the status bar and fading out below it.
                val wash = if (spec.ink.luminance() > 0.5f) Color.Black else Color.White
                Column(
                    Modifier.fillMaxWidth().drawBehind {
                        val up = 400f; val down = 24.dp.toPx()
                        drawRect(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                0f to wash.copy(alpha = spec.scrim), 0.75f to wash.copy(alpha = spec.scrim), 1f to Color.Transparent,
                                startY = -up, endY = size.height + down,
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(-200f, -up),
                            size = androidx.compose.ui.geometry.Size(size.width + 400f, size.height + up + down),
                        )
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { content() }
            }
        }
    }
}

/** A tight halo hugging each letter: reads like an outline on bright patches of a busy scene. */
private fun busyShadow(c: Color) = androidx.compose.ui.graphics.Shadow(c, androidx.compose.ui.geometry.Offset(0f, 1f), 5f)

@Composable
private fun WithShadow(halo: androidx.compose.ui.graphics.Shadow, scheme: androidx.compose.material3.ColorScheme, content: @Composable () -> Unit) {
    val t = MaterialTheme.typography
    fun androidx.compose.ui.text.TextStyle.h() = copy(shadow = halo)
    val typography = t.copy(
        displayLarge = t.displayLarge.h(), displayMedium = t.displayMedium.h(), displaySmall = t.displaySmall.h(),
        headlineLarge = t.headlineLarge.h(), headlineMedium = t.headlineMedium.h(), headlineSmall = t.headlineSmall.h(),
        titleLarge = t.titleLarge.h(), titleMedium = t.titleMedium.h(), titleSmall = t.titleSmall.h(),
        bodyLarge = t.bodyLarge.h(), bodyMedium = t.bodyMedium.h(), bodySmall = t.bodySmall.h(),
        labelLarge = t.labelLarge.h(), labelMedium = t.labelMedium.h(), labelSmall = t.labelSmall.h(),
    )
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = MaterialTheme.shapes) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.copy(shadow = halo),
        ) { content() }
    }
}
