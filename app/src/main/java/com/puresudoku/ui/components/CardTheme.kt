package com.puresudoku.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import com.puresudoku.ui.theme.Poppins

enum class BackPattern { GRID, LATTICE, WAVES, DOTS, BRICKS, BARS, BANDS, CROWNS, RINGS }

/** How one game's deck looks: face, ink per suit (♣ ♦ ♥ ♠), back and lettering. */
data class CardTheme(
    val face: Color,
    val edge: Color,
    /** Ink for ♣, ♦, ♥, ♠. */
    val suits: List<Color>,
    val back: Color,
    val backInk: Color,
    val pattern: BackPattern,
    val font: FontFamily = Poppins,
    val joker: Color = Color(0xFF6A3FB0),
) {
    fun ink(suit: Int) = suits[suit]
}

object CardThemes {
    private fun c(v: Long) = Color(v)
    private fun two(red: Long, black: Long) = listOf(c(black), c(red), c(red), c(black))

    val CLASSIC = CardTheme(c(0xFFFFFFFF), c(0xFFB9C0CA), two(0xFFD03A3A, 0xFF1F2A37), c(0xFF3B6FD8), c(0xFFFFFFFF), BackPattern.GRID)

    /** Solitaire: crisp tournament deck with a navy lattice back. */
    val SOLITAIRE = CardTheme(c(0xFFFFFFFF), c(0xFFB0B8C4), two(0xFFC62828, 0xFF14213D), c(0xFF1B3A6B), c(0xFFDCE6F5), BackPattern.LATTICE)

    /** Blackjack: casino cream with a burgundy and gold back. */
    val BLACKJACK = CardTheme(c(0xFFFFFBF0), c(0xFFCDB98A), two(0xFFB71C1C, 0xFF111111), c(0xFF7A1424), c(0xFFE8C26A), BackPattern.LATTICE, FontFamily.Serif)

    /** Go Fish: bright sea colours, a back of waves. */
    val GO_FISH = CardTheme(c(0xFFF2FBFF), c(0xFF9CCFE6), listOf(c(0xFF00897B), c(0xFFF4511E), c(0xFFE91E63), c(0xFF1A3E8C)),
        c(0xFF1E88C8), c(0xFFBFE9FF), BackPattern.WAVES)

    /** Scoundrel: dungeon parchment; monsters in shadow, weapons in steel, potions in blood. */
    val SCOUNDREL = CardTheme(c(0xFFEFE3C8), c(0xFF9C8458), listOf(c(0xFF2B2234), c(0xFF3D5A80), c(0xFFA4161A), c(0xFF2B2234)),
        c(0xFF4A2E1E), c(0xFFC9A26B), BackPattern.BRICKS, FontFamily.Serif)

    /** Cuttle: deep-sea ink; magenta and indigo. */
    val CUTTLE = CardTheme(c(0xFFF6F2FB), c(0xFFB9A8D6), two(0xFFC2185B, 0xFF283593), c(0xFF35204F), c(0xFFB48AE6), BackPattern.RINGS)

    /** Dungeon Delver: cold stone with ember red. */
    val DELVER = CardTheme(c(0xFFE9E6EE), c(0xFF8F88A0), two(0xFFB23A2A, 0xFF2E2A3A), c(0xFF3A3446), c(0xFF948AA0), BackPattern.BRICKS, FontFamily.Serif)

    /** Regicide: ivory and gold, each suit in the colour of its power. */
    val REGICIDE = CardTheme(c(0xFFFFF8E7), c(0xFFD4B26A), listOf(c(0xFF2E7D32), c(0xFFC77700), c(0xFFC62828), c(0xFF283593)),
        c(0xFF4B1E6B), c(0xFFF2C24B), BackPattern.CROWNS, FontFamily.Serif)

    /** Dungeon Solitaire: tomb bone; spade monsters, golden diamonds, door-brown clubs. */
    val TOMB = CardTheme(c(0xFFE9DFC7), c(0xFFA0895E), listOf(c(0xFF6D4C41), c(0xFFB8860B), c(0xFFA4161A), c(0xFF3B2F4A)),
        c(0xFF5C4630), c(0xFFD9B97A), BackPattern.BANDS, FontFamily.Serif, joker = c(0xFFB8860B))

    /** Joker Jailbreak: cell-block grey, orange and black, a back of bars. */
    val JAILBREAK = CardTheme(c(0xFFECEFF1), c(0xFF90A4AE), two(0xFFE64A19, 0xFF212121), c(0xFF455A64), c(0xFFB0BEC5), BackPattern.BARS, FontFamily.Monospace, joker = c(0xFF7B1FA2))
}

val LocalCardTheme = staticCompositionLocalOf { CardThemes.CLASSIC }

@Composable
fun ProvideCardTheme(theme: CardTheme, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalCardTheme provides theme, content = content)

/** Draws the back pattern inside the card's inset area. */
fun DrawScope.drawBackPattern(p: BackPattern, ink: Color) {
    val a = ink.copy(alpha = 0.45f)
    val w = size.width; val h = size.height
    val s = w / 4
    when (p) {
        BackPattern.GRID -> {
            for (k in 1..3) drawLine(a, Offset(k * s, 0f), Offset(k * s, h), 2f)
            var y = s; while (y < h) { drawLine(a, Offset(0f, y), Offset(w, y), 2f); y += s }
        }
        BackPattern.LATTICE -> {
            var x = -h; while (x < w + h) { drawLine(a, Offset(x, 0f), Offset(x + h, h), 2f); drawLine(a, Offset(x + h, 0f), Offset(x, h), 2f); x += s }
        }
        BackPattern.WAVES -> {
            var y = s / 2
            while (y < h) {
                var x = 0f
                while (x < w) { drawArc(a, 180f, 180f, false, Offset(x, y - s / 4), androidx.compose.ui.geometry.Size(s, s / 2), style = Stroke(2.5f)); x += s }
                y += s * 0.7f
            }
        }
        BackPattern.DOTS -> {
            var y = s / 2; var row = 0
            while (y < h) { var x = if (row % 2 == 0) s / 2 else s; while (x < w) { drawCircle(a, s / 7, Offset(x, y)); x += s }; y += s / 2; row++ }
        }
        BackPattern.BRICKS -> {
            val bh = s * 0.6f; var y = 0f; var row = 0
            while (y < h) {
                drawLine(a, Offset(0f, y), Offset(w, y), 2f)
                var x = if (row % 2 == 0) 0f else s / 2
                while (x < w) { drawLine(a, Offset(x, y), Offset(x, minOf(h, y + bh)), 2f); x += s }
                y += bh; row++
            }
        }
        BackPattern.BARS -> {
            for (k in 1..4) drawLine(a, Offset(k * w / 5, 0f), Offset(k * w / 5, h), w / 18)
            drawLine(a, Offset(0f, h * 0.2f), Offset(w, h * 0.2f), w / 22)
            drawLine(a, Offset(0f, h * 0.8f), Offset(w, h * 0.8f), w / 22)
        }
        BackPattern.BANDS -> {
            var y = s / 2; var k = 0
            while (y < h) { if (k % 3 == 1) drawRect(a, Offset(0f, y), androidx.compose.ui.geometry.Size(w, s / 4)) else drawLine(a, Offset(0f, y), Offset(w, y), 2f); y += s / 2; k++ }
        }
        BackPattern.CROWNS -> {
            val cw = w * 0.5f; val ch = cw * 0.55f; val x0 = (w - cw) / 2; val y0 = (h - ch) / 2
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(x0, y0 + ch); lineTo(x0, y0); lineTo(x0 + cw * 0.25f, y0 + ch * 0.5f); lineTo(x0 + cw * 0.5f, y0 - ch * 0.15f)
                lineTo(x0 + cw * 0.75f, y0 + ch * 0.5f); lineTo(x0 + cw, y0); lineTo(x0 + cw, y0 + ch); close()
            }
            drawPath(path, ink.copy(alpha = 0.8f))
            var x = -h; while (x < w + h) { drawLine(a.copy(alpha = 0.2f), Offset(x, 0f), Offset(x + h, h), 2f); x += s }
        }
        BackPattern.RINGS -> {
            val c = Offset(w / 2, h / 2)
            var r = s / 2; while (r < h) { drawCircle(a, r, c, style = Stroke(2.5f)); r += s / 2 }
        }
    }
    drawRoundRect(ink.copy(alpha = 0.7f), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f), style = Stroke(3f))
}
