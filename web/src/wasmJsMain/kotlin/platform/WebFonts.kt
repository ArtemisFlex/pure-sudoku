// Platform layer (browser): see WebPlatform.kt.
package com.puresudoku.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/** Poppins, the typeface of the Pure Sudoku logo. Filled in by [loadPoppins] before the first frame. */
var Poppins: FontFamily = FontFamily.Default
    private set

/** Font files for text drawn straight onto canvases: Poppins bold first, then the fallbacks. */
var canvasFontBytes: List<ByteArray> = emptyList()
    private set

/**
 * The browser has no system fonts, so characters Poppins lacks (card suits, arrows, the
 * Chinese/Japanese names on Shogi and Xiangqi pieces, an emoji) come from small bundled
 * subsets of Noto. Preloading these families registers them as fallbacks for all text.
 */
var fallbackFamilies: List<FontFamily> = emptyList()
    private set

fun loadPoppins(medium: ByteArray, bold: ByteArray, fallbacks: List<ByteArray> = emptyList()) {
    canvasFontBytes = listOf(bold) + fallbacks
    Poppins = FontFamily(
        Font("poppins-medium-400", medium, FontWeight.Normal),
        Font("poppins-medium-500", medium, FontWeight.Medium),
        Font("poppins-bold-600", bold, FontWeight.SemiBold),
        Font("poppins-bold-700", bold, FontWeight.Bold),
    )
    // one family per font: fonts of the same weight and style within a family would replace each other
    fallbackFamilies = fallbacks.mapIndexed { i, b -> FontFamily(Font("fallback-$i", b)) }
}
