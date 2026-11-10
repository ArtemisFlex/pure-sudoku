package com.puresudoku.ui.games.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser

/** Staunton-style chess silhouettes drawn in a 100 x 100 box. */
object ChessArt {
    private fun p(d: String) = PathParser().parsePathString(d).toPath()

    private val base = "M22,92 L78,92 Q80,84 72,82 L28,82 Q20,84 22,92 Z"
    val pawn = p("M50,18 A12,12 0 1,1 49.9,18 Z M40,40 Q50,46 60,40 L58,48 Q66,52 64,58 L58,58 Q60,70 68,80 L32,80 Q40,70 42,58 L36,58 Q34,52 42,48 Z $base")
    val rook = p("M28,16 L36,16 L36,24 L44,24 L44,16 L56,16 L56,24 L64,24 L64,16 L72,16 L72,34 L64,40 L66,72 L74,80 L26,80 L34,72 L36,40 L28,34 Z $base")
    val knight = p("M30,80 Q32,62 44,52 Q36,54 30,58 Q22,60 20,52 Q22,44 34,36 Q40,24 50,18 L52,10 L58,18 Q74,24 78,46 Q82,64 74,80 Z $base")
    val bishop = p("M50,8 A5,5 0 1,1 49.9,8 Z M50,16 Q68,30 64,48 Q60,58 56,60 L58,70 Q64,74 66,80 L34,80 Q36,74 42,70 L44,60 Q40,58 36,48 Q32,30 50,16 Z $base")
    val queen = p("M20,24 A5,5 0 1,1 19.9,24 Z M35,16 A5,5 0 1,1 34.9,16 Z M50,12 A5,5 0 1,1 49.9,12 Z M65,16 A5,5 0 1,1 64.9,16 Z M80,24 A5,5 0 1,1 79.9,24 Z " +
        "M22,30 L34,54 L36,22 L46,50 L50,18 L54,50 L64,22 L66,54 L78,30 L70,64 Q64,70 66,80 L34,80 Q36,70 30,64 Z $base")
    val king = p("M46,4 L54,4 L54,12 L62,12 L62,20 L54,20 L54,28 Q70,30 74,42 Q76,56 64,66 Q62,74 68,80 L32,80 Q38,74 36,66 Q24,56 26,42 Q30,30 46,28 L46,20 L38,20 L38,12 L46,12 Z $base")
    /** Interior detail lines. */
    val bishopSlit = p("M54,30 L44,42")
    val knightEye = p("M58,30 A3,3 0 1,1 57.9,30 Z")
    val knightMane = p("M58,22 Q70,34 70,56")
    val kingBand = p("M34,66 L66,66")
    val queenBand = p("M32,64 L68,64")

    fun shape(t: Int) = when (t) { 1 -> pawn; 2 -> knight; 3 -> bishop; 4 -> rook; 5 -> queen; else -> king }

    /** Draws a chess piece of type [t] (1..6) filling [rect]. */
    fun DrawScope.drawChessPiece(t: Int, white: Boolean, rect: Rect, alpha: Float = 1f) {
        val s = rect.width / 100f
        translate(rect.left, rect.top) {
            scale(s, s, Offset.Zero) {
                val body = shape(t)
                val fill = if (white) Brush.verticalGradient(listOf(Color(0xFFFFFBF0), Color(0xFFE6D9BD)))
                else Brush.verticalGradient(listOf(Color(0xFF4A4552), Color(0xFF1E1B24)))
                val ink = if (white) Color(0xFF3A2E22) else Color(0xFF0E0C12)
                val detail = if (white) Color(0xFF8A7458) else Color(0xFFB9B2C4)
                drawPath(body, Color.Black.copy(alpha = 0.22f * alpha), style = Stroke(9f))
                drawPath(body, fill, alpha = alpha)
                drawPath(body, ink, alpha = alpha, style = Stroke(3.2f))
                val d = Stroke(2.6f)
                when (t) {
                    3 -> drawPath(bishopSlit, detail, alpha = alpha, style = d)
                    2 -> { drawPath(knightEye, detail, alpha = alpha); drawPath(knightMane, detail, alpha = alpha, style = d) }
                    6 -> drawPath(kingBand, detail, alpha = alpha, style = d)
                    5 -> drawPath(queenBand, detail, alpha = alpha, style = d)
                }
            }
        }
    }
}
