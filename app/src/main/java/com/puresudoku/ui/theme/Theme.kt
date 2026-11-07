package com.puresudoku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.puresudoku.R
import com.puresudoku.core.Difficulty

@Immutable
data class BoardColors(
    val boardBg: Color,
    val thinLine: Color,
    val thickLine: Color,
    val given: Color,
    val entered: Color,
    val note: Color,
    val related: Color,
    val sameDigit: Color,
    val selected: Color,
    val error: Color,
    val errorBg: Color,
    val wave: Color,
)

private val Ink = Color(0xFF1F2A37)
private val Navy = Color(0xFF1F3A5F)
private val Blue = Color(0xFF2F6FD6)
private val Amber = Color(0xFFF2B84B)
private val Red = Color(0xFFD64545)

private val LightBoard = BoardColors(
    boardBg = Color(0xFFFFFFFF),
    thinLine = Color(0xFFD9DEE5),
    thickLine = Color(0xFF3A4656),
    given = Ink,
    entered = Blue,
    note = Color(0xFF6B7684),
    related = Color(0xFFEEF2F8),
    sameDigit = Color(0xFFD5E3F8),
    selected = Color(0xFFBFD5F7),
    error = Red,
    errorBg = Color(0xFFFBE1E1),
    wave = Color(0xFFFFE8B0),
)

private val DarkBoard = BoardColors(
    boardBg = Color(0xFF1C2028),
    thinLine = Color(0xFF2E3440),
    thickLine = Color(0xFF8893A3),
    given = Color(0xFFE6EAF0),
    entered = Color(0xFF79A8F2),
    note = Color(0xFF8D97A5),
    related = Color(0xFF242A34),
    sameDigit = Color(0xFF2B3D5C),
    selected = Color(0xFF34507E),
    error = Color(0xFFFF7A7A),
    errorBg = Color(0xFF4A2326),
    wave = Color(0xFF5A4720),
)

val LocalBoardColors = staticCompositionLocalOf { LightBoard }

/** Accent per difficulty, used on cards and badges. */
fun Difficulty.accent(): Color = when (this) {
    Difficulty.EASY -> Color(0xFF3FA66B)
    Difficulty.MEDIUM -> Color(0xFF2F8FB5)
    Difficulty.HARD -> Color(0xFF3F67D6)
    Difficulty.EXPERT -> Color(0xFF7A4FD0)
    Difficulty.MASTER -> Color(0xFFD0532F)
    Difficulty.EXTREME -> Color(0xFFB81E4A)
}

/** Poppins, the typeface of the Pure Sudoku logo (SIL Open Font License). */
val Poppins = FontFamily(
    Font(R.font.poppins_medium, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_bold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private val Display = Poppins

private val AppTypography = Typography().run {
    copy(
        displayMedium = displayMedium.copy(fontFamily = Display, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    )
}


@Composable
fun PureSudokuTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF79A8F2), onPrimary = Color(0xFF0E1A2C),
            secondary = Amber, onSecondary = Color(0xFF2A1E00),
            background = Color(0xFF14171C), onBackground = Color(0xFFE6EAF0),
            surface = Color(0xFF1C2028), onSurface = Color(0xFFE6EAF0),
            surfaceVariant = Color(0xFF262B35), onSurfaceVariant = Color(0xFFA5AFBD),
            error = Color(0xFFFF7A7A),
        )
    } else {
        lightColorScheme(
            primary = Navy, onPrimary = Color.White,
            secondary = Amber, onSecondary = Color(0xFF2A1E00),
            background = Color(0xFFF6F3EE), onBackground = Ink,
            surface = Color.White, onSurface = Ink,
            surfaceVariant = Color(0xFFECE8E1), onSurfaceVariant = Color(0xFF5E6672),
            error = Red,
        )
    }
    CompositionLocalProvider(LocalBoardColors provides if (dark) DarkBoard else LightBoard) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
