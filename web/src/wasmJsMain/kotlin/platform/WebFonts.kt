// Platform layer (browser): see WebPlatform.kt.
package com.puresudoku.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/** Poppins, the typeface of the Pure Sudoku logo. Filled in by [loadPoppins] before the first frame. */
var Poppins: FontFamily = FontFamily.Default
    private set

/** The raw Poppins bold file, also used for text drawn straight onto canvases. */
var poppinsBoldBytes: ByteArray? = null
    private set

fun loadPoppins(medium: ByteArray, bold: ByteArray) {
    poppinsBoldBytes = bold
    Poppins = FontFamily(
        Font("poppins-medium-400", medium, FontWeight.Normal),
        Font("poppins-medium-500", medium, FontWeight.Medium),
        Font("poppins-bold-600", bold, FontWeight.SemiBold),
        Font("poppins-bold-700", bold, FontWeight.Bold),
    )
}
